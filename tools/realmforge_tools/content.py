"""Loading ``content/`` into validated models (step 1 of validation: schema)."""

from __future__ import annotations

import json
import re
from dataclasses import dataclass
from pathlib import Path
from typing import Any

from pydantic import BaseModel, ValidationError

from realmforge_tools.problems import Problem, ValidationFailed
from realmforge_tools.project import RepoPaths
from realmforge_tools.schema import (
    CONTENT_TYPES,
    WORLDGEN_DIR,
    CurveDef,
    ItemDef,
    StatDef,
    StatRuleDef,
    TextGroupDef,
)
from realmforge_tools.schema.worldgen import WORLDGEN_REGISTRIES
from realmforge_tools.yamlio import YamlLoadError, load_yaml

# Files allowed in content/ besides YAML entries.
_ALLOWED_OTHER_FILES = {"README.md"}
_WORLDGEN_ID = re.compile(r"^[a-z][a-z0-9_]*$")


@dataclass(frozen=True)
class Entry[M: BaseModel]:
    """A content entry and the repository-relative file it came from."""

    source: str
    model: M


@dataclass(frozen=True)
class WorldgenFile:
    """A worldgen JSON file from ``content/worldgen/<directory>/<id>.json``, copied byte for byte."""

    source: str
    directory: str
    id: str
    data: dict[str, Any]
    raw: bytes


@dataclass(frozen=True)
class Content:
    """All content, sorted by id within each type."""

    items: tuple[Entry[ItemDef], ...]
    curves: tuple[Entry[CurveDef], ...]
    stats: tuple[Entry[StatDef], ...]
    stat_rules: tuple[Entry[StatRuleDef], ...]
    texts: tuple[Entry[TextGroupDef], ...]
    worldgen: tuple[WorldgenFile, ...]

    def entries(self, type_dir: str) -> tuple[Entry, ...]:
        """The entries of the YAML content type in ``content/<type_dir>/``."""
        return getattr(self, type_dir)


def load_content(paths: RepoPaths) -> Content:
    """Load and schema-validate every file in ``content/``. Raises ValidationFailed listing all problems."""
    problems: list[Problem] = []
    loaded: dict[str, list[Entry]] = {name: [] for name in CONTENT_TYPES}
    worldgen: list[WorldgenFile] = []

    for file in sorted(p for p in paths.content.rglob("*") if p.is_file()):
        rel = paths.rel(file)
        type_dir = file.relative_to(paths.content).parts[0]
        if file.name in _ALLOWED_OTHER_FILES:
            continue
        if type_dir == WORLDGEN_DIR:
            loaded_file = _load_worldgen(file, rel, paths, problems)
            if loaded_file is not None:
                worldgen.append(loaded_file)
            continue
        if file.suffix == ".yml":
            problems.append(Problem(rel, "", "use the .yaml extension, not .yml"))
            continue
        if file.suffix != ".yaml":
            problems.append(Problem(rel, "", "unexpected file in content/ (only .yaml entries are allowed)"))
            continue
        if type_dir not in CONTENT_TYPES or file.parent.parent != paths.content:
            known = ", ".join(f"content/{name}/" for name in CONTENT_TYPES)
            problems.append(Problem(rel, "", f"unknown content location; entries go directly into {known}"))
            continue

        entry = _load_entry(file, rel, CONTENT_TYPES[type_dir], problems)
        if entry is not None:
            loaded[type_dir].append(entry)

    if problems:
        raise ValidationFailed(problems)

    def by_id(entries: list[Entry]) -> tuple[Entry, ...]:
        return tuple(sorted(entries, key=lambda e: (e.model.id, e.source)))

    return Content(
        items=by_id(loaded["items"]),
        curves=by_id(loaded["curves"]),
        stats=by_id(loaded["stats"]),
        stat_rules=by_id(loaded["stat_rules"]),
        texts=by_id(loaded["texts"]),
        worldgen=tuple(sorted(worldgen, key=lambda f: (f.directory, f.id))),
    )


def _load_entry(file: Path, rel: str, model: type[BaseModel], problems: list[Problem]) -> Entry | None:
    try:
        data = load_yaml(file)
    except YamlLoadError as e:
        problems.append(Problem(rel, f"line {e.line}" if e.line else "", str(e)))
        return None
    if not isinstance(data, dict):
        problems.append(Problem(rel, "", "expected a mapping at the top level"))
        return None
    try:
        return Entry(rel, model.model_validate(data))
    except ValidationError as e:
        for err in e.errors():
            location = ".".join(str(part) for part in err["loc"])
            problems.append(Problem(rel, location, err["msg"]))
        return None


def _load_worldgen(file: Path, rel: str, paths: RepoPaths, problems: list[Problem]) -> WorldgenFile | None:
    parts = file.relative_to(paths.content / WORLDGEN_DIR).parts
    if len(parts) != 2 or parts[0] not in WORLDGEN_REGISTRIES:
        known = ", ".join(f"content/{WORLDGEN_DIR}/{name}/" for name in WORLDGEN_REGISTRIES)
        problems.append(Problem(rel, "", f"unknown worldgen location; files go directly into {known}"))
        return None
    if file.suffix != ".json":
        problems.append(
            Problem(rel, "", "unexpected file in content/worldgen/ (only .json files are allowed)")
        )
        return None
    if not _WORLDGEN_ID.match(file.stem):
        problems.append(Problem(rel, "", f"file name {file.stem!r} is not a lowercase snake_case id"))
        return None
    raw = file.read_bytes()
    try:
        data = json.loads(raw.decode("utf-8"))
    except (UnicodeDecodeError, json.JSONDecodeError) as e:
        problems.append(Problem(rel, "", f"invalid JSON: {e}"))
        return None
    if not isinstance(data, dict):
        problems.append(Problem(rel, "", "expected a JSON object at the top level"))
        return None
    return WorldgenFile(rel, parts[0], file.stem, data, raw)
