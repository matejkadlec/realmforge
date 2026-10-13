"""``rf rooms import``: source files -> normalized rooms with draft sidecars (A4b, ADR 0028).

Each source file (``.nbt``, ``.schem``, ``.litematic``; a folder imports every one below it) becomes
``<target>/<name>.nbt``, the normalized template, and ``<target>/<name>.yaml``, a draft sidecar that records the
source and leaves pools and tags for retagging (A4c). Re-importing keeps an existing sidecar's pools, tags and
weight.

A rules file (``--rules``, YAML, the ``ImportRules`` model) adapts a source to our ids:

- ``blocks``: a source block -> a block state. A bare id keeps the source's properties (a custom stair becomes a
  vanilla stair facing the same way); an id with ``[...]`` replaces them. The block's own block entity data is
  dropped unless ``block_entities`` maps its id.
- ``block_entities``: a source block entity id -> ours; the data is kept under the new id.
- ``connectors`` and ``pools``: jigsaw names (``name`` and ``target``) and jigsaw pools -> ours.
- ``entities.keep``: entity ids to keep; every other entity is dropped.

The template keeps the source's data version, and the game upgrades the room from it when it loads. An id that was
renamed since that version is not in the ID universe: map it in ``blocks`` like a custom block.

A room is written only when it validates (``rooms/check.py``); otherwise the import lists the problems, so the
rules can be extended and the import run again.
"""

from __future__ import annotations

import json
import os
import re
from collections import Counter
from dataclasses import dataclass, field, replace
from pathlib import Path
from typing import Any

from pydantic import BaseModel, ConfigDict, Field, ValidationError

from realmforge_tools.id_universe import IdUniverse
from realmforge_tools.project import RepoPaths
from realmforge_tools.rooms.check import check_structure
from realmforge_tools.rooms.sources import FORMATS, read_source
from realmforge_tools.rooms.structure import (
    JIGSAW,
    BlockState,
    Structure,
    StructureBlock,
    StructureError,
    split_state_text,
    write_structure,
)
from realmforge_tools.schema.room import REVIEW_FILE, RoomDef, RoomSource
from realmforge_tools.yamlio import YamlLoadError, load_yaml

_JIGSAW_NAMES = ("name", "target")
_UNSAFE = re.compile(r"[^a-z0-9_]+")


class RoomImportError(ValueError):
    """A rules file or a source that cannot be imported."""


class _Strict(BaseModel):
    model_config = ConfigDict(extra="forbid", frozen=True)


class EntityRules(_Strict):
    keep: list[str] = Field(default_factory=list, description="Entity ids to keep; all others are dropped.")


class ImportRules(_Strict):
    blocks: dict[str, str] = Field(default_factory=dict)
    block_entities: dict[str, str] = Field(default_factory=dict)
    connectors: dict[str, str] = Field(default_factory=dict)
    pools: dict[str, str] = Field(default_factory=dict)
    entities: EntityRules = Field(default_factory=EntityRules)


@dataclass
class ImportedRoom:
    source: Path
    template: Path
    sidecar: Path
    structure: Structure
    written: bool
    notes: list[str] = field(default_factory=list)
    problems: list[str] = field(default_factory=list)


def load_rules(path: Path | None) -> ImportRules:
    if path is None:
        return ImportRules()
    try:
        return ImportRules.model_validate(load_yaml(path) or {})
    except YamlLoadError as e:
        raise RoomImportError(f"{path}{f':{e.line}' if e.line else ''}: {e}") from e
    except ValidationError as e:
        problems = "; ".join(f"{'.'.join(map(str, err['loc']))}: {err['msg']}" for err in e.errors())
        raise RoomImportError(f"{path}: {problems}") from e


def source_files(sources: list[Path]) -> list[tuple[Path, Path]]:
    """(file, path below its source folder) for every room file: a file stands for itself, a folder for every
    room file below it."""
    found: list[tuple[Path, Path]] = []
    for source in sources:
        if source.is_dir():
            found += [
                (file, file.relative_to(source))
                for file in sorted(source.rglob("*"))
                if file.is_file() and file.suffix.lower() in FORMATS
            ]
        elif source.is_file():
            found.append((source, Path(source.name)))
        else:
            raise RoomImportError(f"{source}: no such file or folder")
    return found


def room_name(relative: Path) -> Path:
    """A source path below its folder -> the room path: lowercase snake_case segments, no suffix."""
    parts = [_safe(part) for part in (*relative.parent.parts, relative.stem)]
    return Path(*parts)


def import_rooms(
    sources: list[Path], target: Path, rules: ImportRules, universe: IdUniverse, paths: RepoPaths
) -> list[ImportedRoom]:
    stems: dict[Path, Path] = {}
    for file, relative in source_files(sources):
        stem = target / room_name(relative)
        if stem in stems:
            raise RoomImportError(f"{file} and {stems[stem]} would both become {stem}.nbt")
        stems[stem] = file
    return [import_room(file, stem, rules, universe, paths) for stem, file in stems.items()]


def import_room(
    file: Path, stem: Path, rules: ImportRules, universe: IdUniverse, paths: RepoPaths
) -> ImportedRoom:
    """Import one file as ``<stem>.nbt`` plus ``<stem>.yaml``."""
    template, sidecar = stem.with_suffix(".nbt"), stem.with_suffix(".yaml")
    try:
        source = read_source(file)
    except (StructureError, OSError) as e:
        raise RoomImportError(f"{file}: {e}") from e
    structure, notes = apply_rules(source.structure, rules)
    result = ImportedRoom(file, template, sidecar, structure, False, [*source.notes, *notes])
    if stem.name == REVIEW_FILE.removesuffix(".yaml"):
        result.problems.append("'review' is reserved for the gallery's review.yaml; rename the room")
    result.problems += [f"{where}: {message}" for where, message in check_structure(structure, universe)]
    if result.problems:
        return result

    previous = _existing_sidecar(sidecar)
    room = RoomDef(
        pools=previous.pools if previous else [],
        tags=previous.tags if previous else [],
        weight=previous.weight if previous else 1,
        source=RoomSource(file=file.name, format=source.format, data_version=structure.data_version),
    )
    stem.parent.mkdir(parents=True, exist_ok=True)
    template.write_bytes(write_structure(structure))
    sidecar.write_text(sidecar_text(room, sidecar, paths), encoding="utf-8")
    result.written = True
    return result


def apply_rules(structure: Structure, rules: ImportRules) -> tuple[Structure, list[str]]:
    """The structure with the rules applied, and what they changed."""
    remapped: Counter[str] = Counter()
    dropped_data: Counter[str] = Counter()
    unmapped_jigsaw: set[str] = set()
    blocks: list[StructureBlock] = []
    for block in structure.blocks:
        state, data = block.state, block.nbt
        if state.name in rules.blocks:
            rule = rules.blocks[state.name]
            target = BlockState.parse(rule)
            state = target if "[" in rule else BlockState(target.name, state.properties)
            remapped[block.state.name] += 1
        entity_id = block.block_entity_id
        if data is not None and entity_id in rules.block_entities:
            data = {**data, "id": rules.block_entities[entity_id]}
        elif data is not None and state.name != block.state.name:
            dropped_data[block.state.name] += 1
            data = None
        if state.name == JIGSAW and data is not None:
            data = _map_jigsaw(data, rules, unmapped_jigsaw, remapped)
        blocks.append(StructureBlock(block.pos, state, data))

    kept = tuple(e for e in structure.entities if e.id in rules.entities.keep)
    dropped_entities = Counter(e.id or "?" for e in structure.entities if e.id not in rules.entities.keep)

    notes = [f"remapped {count} x {name} to {rules.blocks[name]}" for name, count in sorted(remapped.items())]
    notes += [
        f"dropped the block entity data of {count} x {name} (map its id in block_entities to keep it)"
        for name, count in sorted(dropped_data.items())
    ]
    notes += [f"dropped {count} x entity {id_}" for id_, count in sorted(dropped_entities.items())]
    if unmapped_jigsaw:
        notes.append(f"jigsaw names and pools left as they are: {', '.join(sorted(unmapped_jigsaw))}")
    return replace(structure, blocks=tuple(blocks), entities=kept), notes


def _map_jigsaw(
    data: dict[str, Any], rules: ImportRules, unmapped: set[str], remapped: Counter[str]
) -> dict[str, Any]:
    """The jigsaw's names and pool through ``connectors`` and ``pools``, and the block it leaves behind
    (``final_state``) through ``blocks``, like any other block."""
    mapped = dict(data)
    final_state = mapped.get("final_state")
    if isinstance(final_state, str):
        name, properties, block_data = split_state_text(final_state)
        if name in rules.blocks:
            rule = rules.blocks[name]
            mapped["final_state"] = rule + block_data if "[" in rule else rule + properties + block_data
            remapped[name] += 1
    for key in _JIGSAW_NAMES:
        value = mapped.get(key)
        if isinstance(value, str):
            mapped[key] = rules.connectors.get(value, value)
            if value not in rules.connectors and not value.startswith("minecraft:"):
                unmapped.add(value)
    pool = mapped.get("pool")
    if isinstance(pool, str):
        mapped["pool"] = rules.pools.get(pool, pool)
        if pool not in rules.pools and not pool.startswith("minecraft:"):
            unmapped.add(pool)
    return mapped


def sidecar_text(room: RoomDef, sidecar: Path, paths: RepoPaths) -> str:
    lines = []
    if sidecar.resolve().is_relative_to(paths.root.resolve()):
        schema = os.path.relpath(paths.schemas / "rooms.schema.json", sidecar.parent).replace(os.sep, "/")
        lines.append(f"# yaml-language-server: $schema={schema}")
    source = room.source
    lines += [
        "# Imported by `rf rooms import`; pools and tags come with retagging (A4c, architecture 15.4).",
        _yaml_list("pools", room.pools),
        _yaml_list("tags", room.tags),
        f"weight: {room.weight}",
    ]
    if source is not None:
        version = "null" if source.data_version is None else str(source.data_version)
        lines += [
            "source:",
            f"  file: {json.dumps(source.file, ensure_ascii=False)}",
            f"  format: {source.format}",
            f"  data_version: {version}",
        ]
    return "\n".join(lines) + "\n"


def _yaml_list(key: str, values: list[str]) -> str:
    if not values:
        return f"{key}: []"
    return f"{key}:" + "".join(f"\n  - {json.dumps(value)}" for value in values)


def _existing_sidecar(path: Path) -> RoomDef | None:
    if not path.exists():
        return None
    try:
        return RoomDef.model_validate(load_yaml(path) or {})
    except (YamlLoadError, ValidationError) as e:
        raise RoomImportError(
            f"{path}: the existing sidecar cannot be read, fix or delete it first: {e}"
        ) from e


def _safe(name: str) -> str:
    safe = _UNSAFE.sub("_", name.lower()).strip("_")
    if not safe:
        raise RoomImportError(f"{name!r} gives no usable room name")
    return safe
