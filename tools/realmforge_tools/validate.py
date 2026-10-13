"""Content validation, in order: schema (content.py), our own ids, references against the ID universe."""

from __future__ import annotations

import copy
import difflib
from pathlib import PurePosixPath

from realmforge_tools.content import Content, load_content
from realmforge_tools.id_universe import IdUniverse, load_vanilla
from realmforge_tools.problems import Problem, ValidationFailed
from realmforge_tools.project import MOD_ID, RepoPaths
from realmforge_tools.schema import CONTENT_TYPES, DATAPACK_TYPES
from realmforge_tools.schema.text import text_constant
from realmforge_tools.schema.worldgen import WORLDGEN_REGISTRIES, data_path

ITEM_REGISTRY = "minecraft:item"
ATTRIBUTE_REGISTRY = "minecraft:attribute"
CREATIVE_TAB_REGISTRY = "minecraft:creative_mode_tab"
DIMENSION_TYPE_REGISTRY = "minecraft:dimension_type"

# YAML content type -> the registry its ids live in.
REGISTRY_OF_TYPE: dict[str, str] = {
    "items": ITEM_REGISTRY,
    "stats": ATTRIBUTE_REGISTRY,
    **{t.directory: f"{MOD_ID}:{t.registry}" for t in DATAPACK_TYPES},
}

# Translation key prefixes other content types generate (`item.realmforge.<id>`, `curve.realmforge.<id>.name`,
# `attribute.realmforge.<id>`): a text group with one of these ids could produce the same key as an entry of that type.
RESERVED_TEXT_GROUPS: dict[str, str] = {
    "item": "content/items/",
    "attribute": "content/stats/",
    **{t.registry: f"content/{t.directory}/" for t in DATAPACK_TYPES},
}


def load_and_validate(paths: RepoPaths) -> tuple[Content, IdUniverse]:
    """Load content and run every validation step. Raises ValidationFailed with all problems found."""
    content = load_content(paths)
    universe = universe_with_content(load_vanilla(paths), content)
    _add_core_mod_worldgen(universe, paths)
    problems = validate_content(content, universe) + _check_worldgen_clashes(content, paths)
    if problems:
        raise ValidationFailed(problems)
    return content, universe


def universe_with_content(vanilla: IdUniverse, content: Content) -> IdUniverse:
    """The vanilla universe plus every id our content defines."""
    universe = copy.deepcopy(vanilla)
    for type_dir, registry in REGISTRY_OF_TYPE.items():
        for entry in content.entries(type_dir):
            universe.add(registry, f"{MOD_ID}:{entry.model.id}")
    for file in content.worldgen:
        universe.add(WORLDGEN_REGISTRIES[file.directory], f"{MOD_ID}:{file.id}")
    return universe


def validate_content(content: Content, universe: IdUniverse) -> list[Problem]:
    problems: list[Problem] = []
    problems += _check_ids(content)
    problems += _check_references(content, universe)
    problems += _check_stat_rules(content, universe)
    problems += _check_worldgen_references(content, universe)
    problems += _check_text_groups(content)
    return problems


def _check_ids(content: Content) -> list[Problem]:
    problems: list[Problem] = []
    for type_dir in CONTENT_TYPES:
        kind = type_dir.removesuffix("s")
        seen: dict[str, str] = {}
        for entry in content.entries(type_dir):
            entry_id = entry.model.id
            stem = PurePosixPath(entry.source).stem
            if entry_id != stem:
                problems.append(
                    Problem(entry.source, "id", f"{entry_id!r} must equal the file name {stem!r}")
                )
            if entry_id in seen:
                problems.append(
                    Problem(
                        entry.source, "id", f"duplicate {kind} id {entry_id!r} (also in {seen[entry_id]})"
                    )
                )
            seen.setdefault(entry_id, entry.source)
    return problems


def _check_references(content: Content, universe: IdUniverse) -> list[Problem]:
    problems: list[Problem] = []
    for entry in content.items:
        for i, tab in enumerate(entry.model.creative_tabs):
            if not universe.contains(CREATIVE_TAB_REGISTRY, tab):
                problems.append(
                    Problem(
                        entry.source, f"creative_tabs.{i}", _unknown(tab, CREATIVE_TAB_REGISTRY, universe)
                    )
                )
    return problems


def _check_stat_rules(content: Content, universe: IdUniverse) -> list[Problem]:
    """A rule reads one primary stat, which no other rule reads, and writes only secondary stats and vanilla
    attributes (architecture 8.3). An add_value amount on one of our stats takes its unit, percent for a share; a
    vanilla attribute takes either, as its unit is not catalogued (knockback resistance is a fraction)."""
    problems: list[Problem] = []
    stats = {f"{MOD_ID}:{entry.model.id}": entry.model for entry in content.stats}
    rule_of_stat: dict[str, str] = {}
    for entry in content.stat_rules:
        rule = entry.model
        stat = stats.get(rule.stat)
        if not universe.contains(ATTRIBUTE_REGISTRY, rule.stat):
            problems.append(Problem(entry.source, "stat", _unknown(rule.stat, ATTRIBUTE_REGISTRY, universe)))
        elif stat is None or stat.kind != "primary":
            problems.append(
                Problem(
                    entry.source, "stat", f"{rule.stat} is not a primary stat (content/stats/, kind: primary)"
                )
            )
        elif rule.stat in rule_of_stat:
            problems.append(
                Problem(
                    entry.source, "stat", f"{rule.stat} already has a stat rule ({rule_of_stat[rule.stat]})"
                )
            )
        rule_of_stat.setdefault(rule.stat, entry.source)

        for path, modifier in rule.modifiers():
            location = f"{path}.attribute"
            target = stats.get(modifier.attribute)
            if not universe.contains(ATTRIBUTE_REGISTRY, modifier.attribute):
                problems.append(
                    Problem(
                        entry.source, location, _unknown(modifier.attribute, ATTRIBUTE_REGISTRY, universe)
                    )
                )
            elif target is not None and target.kind == "primary":
                problems.append(
                    Problem(
                        entry.source,
                        location,
                        f"stat rules write secondary stats and vanilla attributes, not the primary stat "
                        f"{modifier.attribute}",
                    )
                )
            elif modifier.operation == "add_value" and target is not None:
                share = target.percent
                if share and not modifier.is_percent:
                    problems.append(
                        Problem(
                            entry.source,
                            f"{path}.amount",
                            f"{modifier.attribute} is a share: write the amount as amount_percent",
                        )
                    )
                elif not share and modifier.is_percent:
                    problems.append(
                        Problem(
                            entry.source,
                            f"{path}.amount_percent",
                            f"{modifier.attribute} counts points: add_value takes amount, not amount_percent",
                        )
                    )
    return problems


def _check_text_groups(content: Content) -> list[Problem]:
    """Text groups must not reuse another type's key prefix, and their Java constants must be unique."""
    problems: list[Problem] = []
    constants: dict[str, str] = {}
    for entry in content.texts:
        group = entry.model.id
        if group in RESERVED_TEXT_GROUPS:
            problems.append(
                Problem(
                    entry.source,
                    "id",
                    f"{group!r} is the translation key prefix of {RESERVED_TEXT_GROUPS[group]}; "
                    "name the group after what the texts are (key, message, ...)",
                )
            )
        for name in entry.model.texts:
            constant = text_constant(group, name)
            if constant in constants:
                problems.append(
                    Problem(
                        entry.source,
                        f"texts.{name}",
                        f"Java constant {constant} is also generated for {constants[constant]}",
                    )
                )
            constants.setdefault(constant, f"{entry.source} (texts.{name})")
    return problems


def _check_worldgen_references(content: Content, universe: IdUniverse) -> list[Problem]:
    """The references Python can check cheaply; the game decodes the rest when a world loads."""
    problems: list[Problem] = []
    for file in content.worldgen:
        if file.directory != "dimension":
            continue
        dimension_type = file.data.get("type")
        if dimension_type is None:
            problems.append(Problem(file.source, "type", "a dimension needs a type (a dimension_type id)"))
        elif isinstance(dimension_type, str) and not universe.contains(
            DIMENSION_TYPE_REGISTRY, dimension_type
        ):
            problems.append(
                Problem(file.source, "type", _unknown(dimension_type, DIMENSION_TYPE_REGISTRY, universe))
            )
    return problems


def _core_mod_worldgen(paths: RepoPaths) -> dict[str, set[str]]:
    """Worldgen entries the core mod ships in its own resources (realmforge:instances, ADR 0019), by directory."""
    found: dict[str, set[str]] = {}
    data_root = paths.mod_resources / "data" / MOD_ID
    for directory in WORLDGEN_REGISTRIES:
        folder = data_root / data_path(directory)
        if folder.is_dir():
            found[directory] = {file.stem for file in folder.glob("*.json")}
    return found


def _add_core_mod_worldgen(universe: IdUniverse, paths: RepoPaths) -> None:
    for directory, ids in _core_mod_worldgen(paths).items():
        for id_ in ids:
            universe.add(WORLDGEN_REGISTRIES[directory], f"{MOD_ID}:{id_}")


def _check_worldgen_clashes(content: Content, paths: RepoPaths) -> list[Problem]:
    core = _core_mod_worldgen(paths)
    return [
        Problem(
            file.source,
            "",
            f"{MOD_ID}:{file.id} already exists in the core mod's resources "
            f"(mod/src/main/resources/data/{MOD_ID}/{data_path(file.directory)}/{file.id}.json)",
        )
        for file in content.worldgen
        if file.id in core.get(file.directory, set())
    ]


def _unknown(id_: str, registry: str, universe: IdUniverse) -> str:
    message = f"unknown id {id_!r} in registry {registry}"
    close = difflib.get_close_matches(id_, universe.registries.get(registry, set()), n=3)
    if close:
        message += f" (did you mean: {', '.join(close)}?)"
    return message
