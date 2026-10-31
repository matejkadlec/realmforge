"""Apotheosis's data, fitted to Realmforge (ADR 0063, architecture 19.4, ``schema/apotheosis.py``).

1. ``rf ids apotheosis`` downloads the jar ``pack/mods/apotheosis.pw.toml`` names (sha512-verified, cached in
   ``.cache/``) and writes ``tools/id_universe/apotheosis-<version>.json``: its affixes, gems, tier augments, rarities
   and rarity overrides as Apotheosis ships them, the ids of its recipes, and its license's text (committed;
   validation and ``rf gen`` read only this file). The jar carries no license file, so the text comes from
   Apotheosis's repository at the commit the release was built from (``SOURCE_COMMIT``).
2. ``fit`` applies ``content/``'s changes to that snapshot: first an entry's own change (switched off, or fields
   replaced), then the stat remaps, then the rarity overrides, which read the fitted rarities. Every entry that
   changed becomes a file at Apotheosis's own path; a switched-off one becomes a file NeoForge skips
   (``neoforge:never``), which Placebo, Apotheosis's data loader, logs at trace level only. ``validate.py`` reports
   ``fit``'s problems, ``generate/apotheosis.py`` writes its files.

Apotheosis is MIT-licensed (its code and the data its generators write; the jar's metadata keeps its assets, none of
which we copy, All Rights Reserved). MIT's one condition is that its notice goes with every copy, so ``rf gen`` writes
the snapshot's license text next to the copied data in the jar, ``META-INF/third_party/apotheosis/`` (RLM-524).
"""

from __future__ import annotations

import copy
import difflib
import hashlib
import json
import re
import tomllib
import urllib.request
import zipfile
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any

from realmforge_tools.content import Content, Entry
from realmforge_tools.formats.jsonfmt import dump_json
from realmforge_tools.problems import Problem
from realmforge_tools.project import RepoPaths
from realmforge_tools.schema.apotheosis import (
    CURIOS_LOOT_CATEGORIES,
    LOOT_CATEGORIES,
    RARITIES,
    ApotheosisEntryDef,
    ApotheosisRarityOverrideDef,
    ApotheosisStatRemapDef,
)

# The snapshot's kinds: Apotheosis's folders below data/<namespace>/apotheosis/.
RARITY_OVERRIDE = "rarity_override"
KINDS = ("affixes", "gems", "tier_augments", "rarities", RARITY_OVERRIDE)
# The kinds whose entries grant attributes, and so meet the stat remaps.
REMAPPED_KINDS = ("affixes", "gems", "tier_augments")

PACK_METAFILE = "pack/mods/apotheosis.pw.toml"
SNAPSHOT_GLOB = "apotheosis-*.json"
SOURCE = "https://github.com/Shadows-of-Fire/Apotheosis"
# The commit Apotheosis 9.1.0 for Minecraft 26.1.2 was built from: update it with an Apotheosis update in the pack.
SOURCE_COMMIT = "f2e7686"
LICENSE_URL = f"https://raw.githubusercontent.com/Shadows-of-Fire/Apotheosis/{SOURCE_COMMIT}/LICENSE"
LICENSE_SPDX = "MIT"

# What a switched-off entry or a removed recipe becomes: NeoForge skips a file whose conditions fail.
DISABLED: dict[str, Any] = {"neoforge:conditions": [{"type": "neoforge:never"}]}

ATTRIBUTE = "apotheosis:attribute"
MULTI_ATTRIBUTE = "apotheosis:multi_attribute"
ALL_STATS = "apotheosis:all_stats"
ADD_MULTIPLIED_BASE = "add_multiplied_base"
ADD_VALUE = "add_value"
AFFIX_RULE = "apotheosis:affix"
CHANCED_RULE = "apotheosis:chanced"

_DATA_FILE = re.compile(
    r"^data/(?P<ns>[a-z0-9_.-]+)/apotheosis/(?P<kind>[a-z_]+)/(?P<path>[a-z0-9_/]+)\.json$"
)
_RECIPE_FILE = re.compile(r"^data/(?P<ns>[a-z0-9_.-]+)/recipe/(?P<path>[a-z0-9_/]+)\.json$")


@dataclass(frozen=True)
class Snapshot:
    """Apotheosis's data as one release ships it: entries per kind by id, and its recipes' ids."""

    version: str
    sha512: str
    entries: dict[str, dict[str, dict[str, Any]]]
    recipes: frozenset[str]
    license_text: str

    def entry(self, kind: str, id_: str) -> dict[str, Any] | None:
        return self.entries.get(kind, {}).get(id_)


def snapshot_files(paths: RepoPaths) -> list[Path]:
    return sorted(paths.id_universe.glob(SNAPSHOT_GLOB))


def load_snapshot(paths: RepoPaths) -> Snapshot:
    files = snapshot_files(paths)
    if len(files) != 1:
        raise FileNotFoundError(
            f"expected one {paths.rel(paths.id_universe)}/{SNAPSHOT_GLOB}, found {len(files)}; "
            "run `uv run rf ids apotheosis`"
        )
    data = json.loads(files[0].read_text(encoding="utf-8"))
    return Snapshot(
        version=data["version"],
        sha512=data["jar"]["sha512"],
        entries={kind: data[kind] for kind in KINDS},
        recipes=frozenset(data["recipes"]),
        license_text=data["license"]["text"],
    )


def pack_jar(paths: RepoPaths) -> dict[str, str]:
    """The Apotheosis jar the pack installs: its file name, download URL and sha512."""
    meta = tomllib.loads((paths.root / PACK_METAFILE).read_text(encoding="utf-8"))
    download = meta["download"]
    if download["hash-format"] != "sha512":
        raise ValueError(f"{PACK_METAFILE}: expected a sha512 hash, not {download['hash-format']}")
    return {"file": meta["filename"], "url": download["url"], "sha512": download["hash"]}


def extract(paths: RepoPaths) -> Path:
    """``rf ids apotheosis``: download the pack's Apotheosis jar and write its snapshot, replacing an older one."""
    jar = pack_jar(paths)
    cached = paths.cache / "apotheosis" / jar["file"]
    if not cached.exists() or _sha512(cached.read_bytes()) != jar["sha512"]:
        with urllib.request.urlopen(jar["url"]) as response:
            data = response.read()
        if _sha512(data) != jar["sha512"]:
            raise RuntimeError(f"{jar['file']}: sha512 mismatch against {PACK_METAFILE}")
        cached.parent.mkdir(parents=True, exist_ok=True)
        cached.write_bytes(data)
    with urllib.request.urlopen(LICENSE_URL) as response:
        license_text = response.read().decode("utf-8")
    snapshot = build_snapshot(cached, jar, license_text)
    for old in snapshot_files(paths):
        old.unlink()
    target = paths.id_universe / f"apotheosis-{snapshot['version']}.json"
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_bytes(dump_json(snapshot))
    return target


def build_snapshot(jar_path: Path, jar: dict[str, str], license_text: str) -> dict[str, Any]:
    entries: dict[str, dict[str, Any]] = {kind: {} for kind in KINDS}
    recipes: set[str] = set()
    with zipfile.ZipFile(jar_path) as archive:
        version = _mod_version(archive.read("META-INF/neoforge.mods.toml").decode("utf-8"))
        for name in sorted(archive.namelist()):
            if match := _DATA_FILE.match(name):
                if match["kind"] in entries:
                    entries[match["kind"]][f"{match['ns']}:{match['path']}"] = json.loads(archive.read(name))
            elif match := _RECIPE_FILE.match(name):
                recipes.add(f"{match['ns']}:{match['path']}")
    return {
        "mod": "apotheosis",
        "version": version,
        "jar": {"file": jar["file"], "sha512": jar["sha512"]},
        "source": SOURCE,
        "license": {"spdx": LICENSE_SPDX, "source": LICENSE_URL, "text": license_text},
        "generated_by": "uv run rf ids apotheosis",
        **{kind: dict(sorted(entries[kind].items())) for kind in KINDS},
        "recipes": sorted(recipes),
    }


def _mod_version(mods_toml: str) -> str:
    for mod in tomllib.loads(mods_toml)["mods"]:
        if mod["modId"] == "apotheosis":
            return mod["version"]
    raise ValueError("the jar's neoforge.mods.toml names no apotheosis mod")


def _sha512(data: bytes) -> str:
    return hashlib.sha512(data).hexdigest()


def data_path(kind: str, id_: str) -> str:
    """An entry's file below ``data/``: ``<namespace>/apotheosis/<kind>/<path>.json``."""
    namespace, path = id_.split(":", 1)
    return f"{namespace}/apotheosis/{kind}/{path}.json"


def recipe_path(id_: str) -> str:
    """A recipe's file below ``data/``: ``<namespace>/recipe/<path>.json``."""
    namespace, path = id_.split(":", 1)
    return f"{namespace}/recipe/{path}.json"


@dataclass
class Fitted:
    """The entries ``content/`` changes, as files below ``data/``, and what stood in the way."""

    files: dict[str, dict[str, Any]] = field(default_factory=dict)
    problems: list[Problem] = field(default_factory=list)


def entry_changes(content: Content) -> list[Entry[ApotheosisEntryDef]]:
    return [
        *content.apotheosis_affixes,
        *content.apotheosis_gems,
        *content.apotheosis_tier_augments,
        *content.apotheosis_rarities,
    ]


def fit(content: Content, snapshot: Snapshot) -> Fitted:
    fitted = Fitted()
    changed: dict[tuple[str, str], dict[str, Any]] = {}
    sources: dict[tuple[str, str], str] = {}
    for entry in entry_changes(content):
        model = entry.model
        key = (model.KIND, model.target)
        original = snapshot.entry(*key)
        if original is None:
            fitted.problems.append(
                Problem(entry.source, model.TARGET_FIELD, _unknown_entry(model.KIND, model.target, snapshot))
            )
            continue
        if key in sources:
            fitted.problems.append(
                Problem(
                    entry.source, model.TARGET_FIELD, f"{model.target} is already changed by {sources[key]}"
                )
            )
            continue
        sources[key] = entry.source
        changed[key] = DISABLED if model.disabled else _replaced(entry, original, fitted.problems)

    remaps = {entry.model.source: entry for entry in content.apotheosis_stat_remaps}
    granted: set[str] = set()
    for kind in REMAPPED_KINDS:
        for id_, original in snapshot.entries[kind].items():
            data = changed.get((kind, id_), original)
            if data is DISABLED or not remaps:
                continue
            result = _remap(kind, copy.deepcopy(data), remaps, granted, fitted.problems)
            if result != data:
                changed[(kind, id_)] = result
            if result is not DISABLED:
                for where in _leftovers(result, remaps):
                    remap = remaps[where[1]]
                    fitted.problems.append(
                        Problem(
                            remap.source,
                            "source",
                            f"{kind} {id_} names {where[1]} at {where[0]}, where no stat remap applies",
                        )
                    )
    for source, entry in remaps.items():
        if source not in granted:
            fitted.problems.append(
                Problem(entry.source, "source", f"no Apotheosis affix, gem or tier augment grants {source}")
            )

    fitted.problems += _switched_off_exclusions(snapshot, changed, sources)
    for (kind, id_), data in sorted(changed.items()):
        fitted.files[data_path(kind, id_)] = data
    for entry in content.apotheosis_rarity_overrides:
        override = _rarity_override(entry, snapshot, changed, fitted)
        if override is not None:
            fitted.files[override_path(override["category"])] = override
    return fitted


def _switched_off_exclusions(
    snapshot: Snapshot, changed: dict[tuple[str, str], dict[str, Any]], sources: dict[tuple[str, str], str]
) -> list[Problem]:
    """An affix that stays on and names a switched-off one in its exclusive set: Apotheosis then logs "contains the
    unknown affix ... in its exclusive set" at ERROR on every load."""
    problems: list[Problem] = []
    for id_, original in snapshot.entries["affixes"].items():
        data = changed.get(("affixes", id_), original)
        if data is DISABLED:
            continue
        for named in data.get("definition", {}).get("exclusive_set", []):
            if changed.get(("affixes", named)) is DISABLED:
                problems.append(
                    Problem(
                        sources.get(("affixes", id_))
                        or sources.get(("affixes", named), "content/apotheosis_affixes/"),
                        "replace.definition.exclusive_set",
                        f"{id_} names {named}, which is switched off, in its exclusive set: replace its definition "
                        "without it",
                    )
                )
    return problems


def override_path(category: str) -> str:
    """A loot category's rarity override below ``data/``. Apotheosis reads the category from the file's path, so
    ``apotheosis:helmet``'s is ``apotheosis/apotheosis/rarity_override/apotheosis/helmet.json``, where Apotheosis's own
    override for it would be (``RarityOverrideRegistry#validateItem``)."""
    namespace, path = category.split(":", 1)
    return f"apotheosis/apotheosis/{RARITY_OVERRIDE}/{namespace}/{path}.json"


def _rarity_override(
    entry: Entry[ApotheosisRarityOverrideDef],
    snapshot: Snapshot,
    changed: dict[tuple[str, str], dict[str, Any]],
    fitted: Fitted,
) -> dict[str, Any] | None:
    """The category's rarity override: Apotheosis's own, or none, with the rules of every rarity listed taken from it
    or from the fitted rarity, capped to the slots the entry allows. None when the entry cannot be fitted."""
    model = entry.model
    category = model.category
    if category in CURIOS_LOOT_CATEGORIES:
        fitted.problems.append(
            Problem(entry.source, "category", f"{category} exists only with Curios, which the pack lacks")
        )
        return None
    if category not in LOOT_CATEGORIES:
        fitted.problems.append(
            Problem(
                entry.source,
                "category",
                f"unknown loot category {category!r}; Apotheosis has {', '.join(LOOT_CATEGORIES)}",
            )
        )
        return None
    path = override_path(category)
    if path in fitted.files:
        fitted.problems.append(Problem(entry.source, "category", f"{category} already has a rarity override"))
        return None
    own = next(
        (data for data in snapshot.entries[RARITY_OVERRIDE].values() if data.get("category") == category),
        None,
    )
    overrides: dict[str, Any] = copy.deepcopy(own["overrides"]) if own else {}
    for rarity, limits in model.max_slots.items():
        if rarity not in RARITIES:
            fitted.problems.append(
                Problem(
                    entry.source,
                    f"max_slots.{rarity}",
                    f"unknown rarity {rarity!r}; Apotheosis has {', '.join(RARITIES)}",
                )
            )
            continue
        rules = overrides.get(rarity)
        if rules is None:
            rules = changed.get(("rarities", rarity), snapshot.entry("rarities", rarity) or {}).get(
                "rules", []
            )
        capped = copy.deepcopy(rules)
        for kind, limit in limits.items():
            location = f"max_slots.{rarity}.{kind}"
            slots = [n for n, rule in enumerate(capped) if _slot_kind(rule) == kind]
            if any(_names_kind(rule, kind) for rule in capped if _slot_kind(rule) is None):
                fitted.problems.append(
                    Problem(
                        entry.source,
                        location,
                        f"{category}'s rules at {rarity} name {kind} inside another rule",
                    )
                )
            elif len(slots) <= limit:
                fitted.problems.append(
                    Problem(
                        entry.source,
                        location,
                        f"{category} has {len(slots)} {kind} slot(s) at {rarity}: nothing to drop for at most {limit}",
                    )
                )
            else:
                chanced = [n for n in slots if capped[n]["type"] == CHANCED_RULE]
                plain = [n for n in slots if capped[n]["type"] != CHANCED_RULE]
                dropped = set((chanced[::-1] + plain[::-1])[: len(slots) - limit])
                capped = [rule for n, rule in enumerate(capped) if n not in dropped]
        overrides[rarity] = capped
    ordered = {rarity: overrides[rarity] for rarity in RARITIES if rarity in overrides}
    return {**(own or {}), "category": category, "overrides": ordered}


def _slot_kind(rule: Any) -> str | None:
    """The affix kind of a slot, an affix rule or a chanced one, or None for any other rule."""
    if not isinstance(rule, dict):
        return None
    if rule.get("type") == CHANCED_RULE:
        return _slot_kind(rule.get("rule"))
    if rule.get("type") == AFFIX_RULE:
        return rule.get("affix_type")
    return None


def _names_kind(value: Any, kind: str) -> bool:
    if isinstance(value, dict):
        return value.get("affix_type") == kind or any(_names_kind(item, kind) for item in value.values())
    if isinstance(value, list):
        return any(_names_kind(item, kind) for item in value)
    return False


def _unknown_entry(kind: str, id_: str, snapshot: Snapshot) -> str:
    message = f"Apotheosis {snapshot.version} has no {kind} entry {id_}"
    close = difflib.get_close_matches(id_, snapshot.entries.get(kind, {}), n=3)
    if close:
        message += f" (did you mean: {', '.join(close)}?)"
    return message


def _replaced(
    entry: Entry[ApotheosisEntryDef], original: dict[str, Any], problems: list[Problem]
) -> dict[str, Any]:
    data = copy.deepcopy(original)
    for name, value in entry.model.replace.items():
        if name not in original:
            fields = ", ".join(original)
            problems.append(
                Problem(
                    entry.source,
                    f"replace.{name}",
                    f"{entry.model.target} has no field {name} (it has {fields})",
                )
            )
        elif _json_kind(value) != _json_kind(original[name]):
            problems.append(
                Problem(
                    entry.source,
                    f"replace.{name}",
                    f"{name} is {_json_kind(original[name])} in {entry.model.target}, not {_json_kind(value)}",
                )
            )
        else:
            data[name] = value
    return data


def _json_kind(value: Any) -> str:
    if isinstance(value, bool):
        return "a boolean"
    if isinstance(value, int | float):
        return "a number"
    if isinstance(value, str):
        return "a string"
    if isinstance(value, list):
        return "a list"
    if isinstance(value, dict):
        return "an object"
    return "null"


def _remap(
    kind: str,
    data: dict[str, Any],
    remaps: dict[str, Entry[ApotheosisStatRemapDef]],
    granted: set[str],
    problems: list[Problem],
) -> dict[str, Any]:
    """The entry with every remapped stat it grants replaced, or switched off (DISABLED)."""
    if kind == "affixes":
        if data.get("type") == ATTRIBUTE and data.get("attribute") in remaps:
            remap = remaps[data["attribute"]].model
            granted.add(remap.source)
            if remap.off:
                return DISABLED
            _retarget(data, "attribute", "operation", "values", remap)
        return data
    if kind == "tier_augments":
        modifier = data.get("modifier")
        if (
            data.get("type") == ATTRIBUTE
            and isinstance(modifier, dict)
            and modifier.get("attribute") in remaps
        ):
            remap = remaps[modifier["attribute"]].model
            granted.add(remap.source)
            if remap.off:
                return DISABLED
            _retarget(modifier, "attribute", "operation", "value", remap)
        return data
    bonuses = []
    for bonus in data.get("bonuses", []):
        kept = _remap_bonus(bonus, remaps, granted, problems)
        if kept is not None:
            bonuses.append(kept)
    if "bonuses" in data:
        if not bonuses:
            return DISABLED
        data["bonuses"] = bonuses
    return data


def _remap_bonus(
    bonus: dict[str, Any],
    remaps: dict[str, Entry[ApotheosisStatRemapDef]],
    granted: set[str],
    problems: list[Problem],
) -> dict[str, Any] | None:
    """A gem bonus with its remapped stats replaced, or None when a stat switched off drops it."""
    kind = bonus.get("type")
    if kind == ATTRIBUTE and bonus.get("attribute") in remaps:
        remap = remaps[bonus["attribute"]].model
        granted.add(remap.source)
        if remap.off:
            return None
        _retarget(bonus, "attribute", "operation", "values", remap)
    elif kind == MULTI_ATTRIBUTE:
        modifiers = [m for m in bonus.get("modifiers", []) if m.get("attribute") in remaps]
        granted.update(m["attribute"] for m in modifiers)
        if any(remaps[m["attribute"]].model.off for m in modifiers):
            return None
        for modifier in modifiers:
            _retarget(modifier, "attribute", "operation", "values", remaps[modifier["attribute"]].model)
    elif kind == ALL_STATS:
        attributes = []
        for attribute in bonus.get("attributes", []):
            if attribute not in remaps:
                attributes.append(attribute)
                continue
            remap = remaps[attribute]
            granted.add(attribute)
            if remap.model.off:
                continue
            if remap.model.source_base is not None and bonus.get("operation") == ADD_MULTIPLIED_BASE:
                problems.append(
                    Problem(
                        remap.source,
                        "source_base",
                        f"an all-stats bonus applies one operation to every stat, so {attribute}'s "
                        f"{ADD_MULTIPLIED_BASE} cannot become {ADD_VALUE}",
                    )
                )
            attributes.append(remap.model.target)
        if not attributes:
            return None
        bonus["attributes"] = attributes
    return bonus


def _retarget(
    holder: dict[str, Any], attribute: str, operation: str, values: str, remap: ApotheosisStatRemapDef
) -> None:
    """Point ``holder``'s attribute at the remap's target. With a ``source_base``, an add_multiplied_base modifier
    becomes an add_value of its values times the source's base, so it adds what it added to the source."""
    holder[attribute] = remap.target
    if remap.source_base is not None and holder.get(operation) == ADD_MULTIPLIED_BASE:
        holder[operation] = ADD_VALUE
        holder[values] = _scaled(holder[values], remap.source_base)


def _scaled(value: Any, factor: float) -> Any:
    """Every number in ``value`` (a number, or values per rarity or purity with min, max and step) times ``factor``,
    rounded to 9 decimals so the JSON stays readable."""
    if isinstance(value, bool):
        return value
    if isinstance(value, int | float):
        return round(value * factor, 9)
    if isinstance(value, dict):
        return {key: _scaled(item, factor) for key, item in value.items()}
    if isinstance(value, list):
        return [_scaled(item, factor) for item in value]
    return value


def _leftovers(
    value: Any, remaps: dict[str, Entry[ApotheosisStatRemapDef]], path: str = ""
) -> list[tuple[str, str]]:
    """Every (path, stat) where ``value`` still names a remapped stat."""
    if isinstance(value, str):
        return [(path or ".", value)] if value in remaps else []
    if isinstance(value, dict):
        return [
            found
            for key, item in value.items()
            for found in _leftovers(item, remaps, f"{path}.{key}" if path else key)
        ]
    if isinstance(value, list):
        return [found for n, item in enumerate(value) for found in _leftovers(item, remaps, f"{path}[{n}]")]
    return []


def recipe_removals(content: Content) -> dict[str, dict[str, Any]]:
    """Each removed recipe's file below ``data/``."""
    return {
        recipe_path(recipe): DISABLED for entry in content.recipe_removals for recipe in entry.model.recipes
    }


def generated_files(content: Content, snapshot: Snapshot) -> dict[str, bytes]:
    """Every Apotheosis entry ``content/`` changes and every recipe it removes, below ``data/``."""
    files = {path: dump_json(data) for path, data in fit(content, snapshot).files.items()}
    files.update({path: dump_json(data) for path, data in recipe_removals(content).items()})
    return files


def notices(snapshot: Snapshot) -> dict[str, bytes]:
    """Apotheosis's ``LICENSE`` and our ``NOTICE``, below ``META-INF/third_party/apotheosis/``, for a jar that carries
    its data."""
    notice = (
        f"The files below data/apotheosis/ in this jar are Apotheosis's own data, version {snapshot.version}\n"
        f"({SOURCE}), each with Realmforge's changes to it. A switched-off entry or recipe is\n"
        "a file whose neoforge:never condition makes the game skip it. Apotheosis is MIT-licensed: see LICENSE.\n"
    )
    return {"LICENSE": snapshot.license_text.encode("utf-8"), "NOTICE": notice.encode("utf-8")}
