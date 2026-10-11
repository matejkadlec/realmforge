"""Content validation, in order: schema (content.py), our own ids, references against the ID universe."""

from __future__ import annotations

import copy
import difflib
from pathlib import PurePosixPath

from realmforge_tools.content import Content, load_content
from realmforge_tools.id_universe import IdUniverse, load_vanilla
from realmforge_tools.problems import Problem, ValidationFailed
from realmforge_tools.project import MOD_ID, RepoPaths

ITEM_REGISTRY = "minecraft:item"
CREATIVE_TAB_REGISTRY = "minecraft:creative_mode_tab"


def load_and_validate(paths: RepoPaths) -> tuple[Content, IdUniverse]:
    """Load content and run every validation step. Raises ValidationFailed with all problems found."""
    content = load_content(paths)
    universe = universe_with_content(load_vanilla(paths), content)
    problems = validate_content(content, universe)
    if problems:
        raise ValidationFailed(problems)
    return content, universe


def universe_with_content(vanilla: IdUniverse, content: Content) -> IdUniverse:
    """The vanilla universe plus every id our content defines."""
    universe = copy.deepcopy(vanilla)
    for entry in content.items:
        universe.add(ITEM_REGISTRY, f"{MOD_ID}:{entry.model.id}")
    return universe


def validate_content(content: Content, universe: IdUniverse) -> list[Problem]:
    problems: list[Problem] = []
    problems += _check_ids(content)
    problems += _check_references(content, universe)
    return problems


def _check_ids(content: Content) -> list[Problem]:
    problems: list[Problem] = []
    seen: dict[str, str] = {}
    for entry in content.items:
        item_id = entry.model.id
        stem = PurePosixPath(entry.source).stem
        if item_id != stem:
            problems.append(Problem(entry.source, "id", f"{item_id!r} must equal the file name {stem!r}"))
        if item_id in seen:
            problems.append(
                Problem(entry.source, "id", f"duplicate item id {item_id!r} (also in {seen[item_id]})")
            )
        seen.setdefault(item_id, entry.source)
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


def _unknown(id_: str, registry: str, universe: IdUniverse) -> str:
    message = f"unknown id {id_!r} in registry {registry}"
    close = difflib.get_close_matches(id_, universe.registries.get(registry, set()), n=3)
    if close:
        message += f" (did you mean: {', '.join(close)}?)"
    return message
