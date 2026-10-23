"""``rf rooms import``: source files -> normalized rooms with draft sidecars (A4b, ADR 0028).

Each source file (``.nbt``, ``.schem``, ``.litematic``; a folder imports every one below it) becomes
``<target>/<name>.nbt``, the normalized template, and ``<target>/<name>.yaml``, a draft sidecar that records the
source and leaves pools and tags for retagging (A4c). Re-importing keeps an existing sidecar's pools, tags, weight
and room rules (``KEPT_RULES``). A file that a ``review.yaml`` rejects (the gallery's record, in the target folder, a folder below it or,
inside ``content/rooms/``, a folder above it; the nearest decides) is skipped, so a dropped room does not come back.

A rules file (``--rules``, YAML, the ``ImportRules`` model) adapts a source to our ids:

- ``blocks``: a source block -> a block state. A bare id keeps the source's properties (a custom stair becomes a
  vanilla stair facing the same way); an id with ``[...]`` replaces them. The block's own block entity data is
  dropped unless ``block_entities`` maps its id.
- ``block_entities``: a source block entity id -> ours; the data is kept under the new id.
- ``connectors`` and ``pools``: jigsaw names (``name`` and ``target``) and jigsaw pools -> ours.
- ``jigsaws``: ``interior: drop`` turns every jigsaw inside the room (one that pulls in a prop, a mob or another
  sub-piece) into the block it leaves behind (its ``final_state``); ``normalize: true`` gives every connector (a
  jigsaw on the boundary, pointing out) our neutral names: ``realmforge:door`` on the sides,
  ``realmforge:shaft`` up and down, and the pool ``minecraft:empty``, since the sidecar names the pools.
- ``loot_tables``: a container's loot table -> ours or a vanilla one, or ``null`` to drop the reference (the
  container stays empty until the dungeon fills it). ``<namespace>:*`` matches every table of a namespace.
- ``entities.keep``: entity ids to keep; every other entity is dropped.
- ``include`` and ``exclude``: which files of a source folder are rooms, as ``fnmatch`` patterns on the path below
  it (``dungeon/rooms/*.nbt``; ``*`` also matches ``/``). A file is imported when it matches an ``include`` (all
  files without one) and no ``exclude``.
- ``max_size``: a file whose room is bigger on any axis (a whole wing of the source's dungeon rather than a room) is
  left out like an excluded one.
- ``dress``: block variety on the floor, walls and ceiling of plain rooms, and decorations (``rooms/dress.py``).
- ``data_version``: the data version of the files that state none (a Sponge v1 schematic, a template some tool
  wrote), when the source is known to be saved with it.

A new sidecar gets draft slot tags (``rooms/slots.py``); a re-import keeps the sidecar's tags.

The template keeps the source's data version, and the game upgrades the room from it when it loads. An id that was
renamed since that version is not in the ID universe: map it in ``blocks`` like a custom block. A room without a
data version is refused: the game would read it as version 500 and run every upgrade since on it.

A room is written only when it validates (``rooms/check.py``); otherwise the import lists the problems, so the
rules can be extended and the import run again.
"""

from __future__ import annotations

import json
import os
import re
from collections import Counter
from dataclasses import dataclass, field, replace
from fnmatch import fnmatchcase
from pathlib import Path
from typing import Any, Literal

from pydantic import BaseModel, ConfigDict, Field, ValidationError, model_validator

from realmforge_tools.id_universe import IdUniverse
from realmforge_tools.project import RepoPaths
from realmforge_tools.rooms.check import LOOT_TABLE, check_structure, connector_face
from realmforge_tools.rooms.dress import DressRules, dress
from realmforge_tools.rooms.slots import slot_tags
from realmforge_tools.rooms.sources import FORMATS, read_source
from realmforge_tools.rooms.structure import (
    AIR,
    JIGSAW,
    BlockState,
    Pos,
    Structure,
    StructureBlock,
    StructureError,
    split_state_text,
    write_structure,
)
from realmforge_tools.schema.room import REVIEW_FILE, ROOMS_DIR, ReviewFile, RoomDef, RoomSource
from realmforge_tools.yamlio import YamlLoadError, load_yaml

_JIGSAW_NAMES = ("name", "target")
_UNSAFE = re.compile(r"[^a-z0-9_]+")
# The neutral connector names ``jigsaws.normalize`` gives every connector, and the pool it leaves them.
DOOR = "realmforge:door"
SHAFT = "realmforge:shaft"
EMPTY_POOL = "minecraft:empty"
# Sidecar fields a re-import keeps besides pools, tags and weight: how dungeons treat the room.
KEPT_RULES = ("max_per_layout", "near_start", "explosion_breaks", "entry")
# The seed a container keeps next to its loot table (RandomizableContainer.LOOT_TABLE_SEED_TAG).
_LOOT_TABLE_SEED = "LootTableSeed"


class RoomImportError(ValueError):
    """A rules file or a source that cannot be imported."""


class _Strict(BaseModel):
    model_config = ConfigDict(extra="forbid", frozen=True)


class EntityRules(_Strict):
    keep: list[str] = Field(default_factory=list, description="Entity ids to keep; all others are dropped.")


class JigsawRules(_Strict):
    interior: Literal["keep", "drop"] = Field(
        default="keep",
        description="drop: a jigsaw inside the room (a prop, a mob, a sub-piece) becomes its final_state.",
    )
    normalize: bool = Field(
        default=False,
        description="Name every connector realmforge:door (sides) or realmforge:shaft (up, down), pool "
        "minecraft:empty.",
    )


class PlacedBlocks(_Strict):
    """A box of one block state set into a room after its dressing, such as a fall cushion (RLM-276)."""

    model_config = ConfigDict(extra="forbid", frozen=True, populate_by_name=True)
    start: tuple[int, int, int] = Field(
        alias="from", description="One corner of the box, in the room's template."
    )
    to: tuple[int, int, int] = Field(description="The opposite corner, included.")
    block: str = Field(
        description="The block state, e.g. minecraft:stone_brick_slab[type=bottom,waterlogged=true]."
    )


class ImportRules(_Strict):
    blocks: dict[str, str] = Field(default_factory=dict)
    block_entities: dict[str, str] = Field(default_factory=dict)
    connectors: dict[str, str] = Field(default_factory=dict)
    pools: dict[str, str] = Field(default_factory=dict)
    jigsaws: JigsawRules = Field(default_factory=JigsawRules)
    loot_tables: dict[str, str | None] = Field(default_factory=dict)
    entities: EntityRules = Field(default_factory=EntityRules)
    include: list[str] = Field(default_factory=list)
    exclude: list[str] = Field(default_factory=list)
    max_size: int | None = Field(default=None, ge=1, description="Leave out rooms bigger on any axis.")
    dress: DressRules | None = None
    place: dict[str, list[PlacedBlocks]] = Field(
        default_factory=dict,
        description="Blocks set into rooms after their dressing, by the room's path below the source folder.",
    )
    data_version: int | None = Field(
        default=None,
        ge=1,
        description="The data version of the files that state none, for a source known to be saved with it.",
    )

    @model_validator(mode="after")
    def _normalize_replaces_names(self) -> ImportRules:
        if self.jigsaws.normalize and (self.connectors or self.pools):
            raise ValueError("jigsaws.normalize names every connector itself; leave connectors and pools out")
        return self

    def excludes(self, relative: Path) -> bool:
        """Whether the file at ``relative`` below its source folder is left out."""
        path = relative.as_posix()
        if self.include and not any(fnmatchcase(path, pattern) for pattern in self.include):
            return True
        return any(fnmatchcase(path, pattern) for pattern in self.exclude)

    def loot_table(self, table: str) -> tuple[bool, str | None]:
        """(mapped, the table to write or None to drop it) for a container's loot table."""
        for key in (table, f"{table.partition(':')[0]}:*"):
            if key in self.loot_tables:
                return True, self.loot_tables[key]
        return False, table


@dataclass
class ImportedRoom:
    source: Path
    template: Path
    sidecar: Path
    structure: Structure | None
    """None for a file that is not read: the review rejects it, the rules leave it out, or it is corrupt."""
    written: bool
    notes: list[str] = field(default_factory=list)
    problems: list[str] = field(default_factory=list)
    rejected_by: Path | None = None
    """The ``review.yaml`` that rejects the room; the file is skipped."""
    excluded: bool = False
    """The rules (``include``, ``exclude``, ``max_size``) leave the file out; it is skipped."""


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
    sources: list[Path],
    target: Path,
    rules: ImportRules,
    universe: IdUniverse,
    paths: RepoPaths,
    *,
    ours: bool = False,
) -> list[ImportedRoom]:
    """Import every room file. Inside ``content/rooms/``, third-party rooms must go to ``third_party/<source>/``
    (where validation requires the source's notices and ``rf gen`` ships them), and only rooms marked ``ours`` may
    go anywhere else."""
    check_target(target, paths, ours=ours)
    stems: dict[Path, Path] = {}
    results: list[ImportedRoom] = []
    for file, relative in source_files(sources):
        stem = target / room_name(relative)
        if rules.excludes(relative):
            results.append(_skipped(file, stem, excluded=True))
            continue
        if stem in stems:
            raise RoomImportError(f"{file} and {stems[stem]} would both become {stem}.nbt")
        stems[stem] = file
    rejected = rejected_rooms(target, paths)
    for stem, file in stems.items():
        review = rejected.get(stem.resolve())
        if review is not None:
            results.append(_skipped(file, stem, rejected_by=review))
        else:
            name = stem.relative_to(target).as_posix()
            results.append(import_room(file, stem, rules, universe, paths, ours=ours, name=name))
    return results


def _skipped(
    file: Path, stem: Path, *, rejected_by: Path | None = None, excluded: bool = False
) -> ImportedRoom:
    template, sidecar = stem.with_suffix(".nbt"), stem.with_suffix(".yaml")
    return ImportedRoom(file, template, sidecar, None, False, rejected_by=rejected_by, excluded=excluded)


def rejected_rooms(target: Path, paths: RepoPaths) -> dict[Path, Path]:
    """The rooms below ``target`` that a review rejects: resolved path without suffix -> its ``review.yaml``. The
    reviews are the target's own, those of the folders below it (the gallery reviews a subfolder on its own) and,
    inside ``content/rooms/``, those of the folders above it up to ``content/rooms/`` itself; each keys rooms by their
    path below its folder, and the nearest decision counts."""
    folder = target.resolve()
    rooms = (paths.content / ROOMS_DIR).resolve()
    folders = [review.parent for review in folder.rglob(REVIEW_FILE)] if folder.is_dir() else []
    if folder.is_relative_to(rooms):
        folders += [parent for parent in folder.parents if parent.is_relative_to(rooms)]
    # Deepest first: a review nearer to the room decides before one further up.
    folders.sort(key=lambda reviewed: len(reviewed.parts), reverse=True)
    decisions: dict[Path, tuple[str, Path]] = {}
    for reviewed in folders:
        review = reviewed / REVIEW_FILE
        if not review.is_file():
            continue
        for key, decision in _review_decisions(review).items():
            decisions.setdefault(reviewed / key, (decision, review))
    return {stem: review for stem, (decision, review) in decisions.items() if decision == "reject"}


def _review_decisions(path: Path) -> dict[str, str]:
    try:
        return ReviewFile.model_validate(load_yaml(path) or {}).root
    except YamlLoadError as e:
        raise RoomImportError(f"{path}{f':{e.line}' if e.line else ''}: {e}") from e
    except ValidationError as e:
        problems = "; ".join(f"{'.'.join(map(str, err['loc']))}: {err['msg']}" for err in e.errors())
        raise RoomImportError(f"{path}: {problems}") from e


def check_target(target: Path, paths: RepoPaths, *, ours: bool) -> None:
    rooms = (paths.content / ROOMS_DIR).resolve()
    resolved = target.resolve()
    if not resolved.is_relative_to(rooms):
        return  # outside content/rooms/: nothing ships from there
    parts = resolved.relative_to(rooms).parts
    third_party = len(parts) >= 2 and parts[0] == "third_party"
    if not ours and not third_party:
        raise RoomImportError(
            f"{target}: third-party rooms go to content/rooms/third_party/<source>/, next to the source's LICENSE "
            "and NOTICE; pass --ours for rooms we built ourselves"
        )
    if ours and parts[:1] == ("third_party",):
        raise RoomImportError(f"{target}: rooms of our own do not go into third_party/")


def import_room(
    file: Path,
    stem: Path,
    rules: ImportRules,
    universe: IdUniverse,
    paths: RepoPaths,
    *,
    ours: bool = False,
    name: str | None = None,
) -> ImportedRoom:
    """Import one file as ``<stem>.nbt`` plus ``<stem>.yaml``. ``name`` is the room's path below the import
    target, which the draft slot tags read (default: the file's name)."""
    template, sidecar = stem.with_suffix(".nbt"), stem.with_suffix(".yaml")
    try:
        source = read_source(file)
    except OSError as e:
        raise RoomImportError(f"{file}: {e}") from e
    except StructureError as e:
        # A broken file in a source (some jars ship one) fails like any room; the rules can exclude it.
        return ImportedRoom(file, template, sidecar, None, False, problems=[f"not a usable room file: {e}"])
    if rules.max_size is not None and max(source.structure.size) > rules.max_size:
        return ImportedRoom(file, template, sidecar, None, False, excluded=True)
    structure, notes = apply_rules(source.structure, rules)
    previous = _existing_sidecar(sidecar)
    tags = previous.tags if previous else slot_tags(structure, name or stem.name)
    if rules.dress is not None and not rules.dress.excludes(name or stem.name):
        structure, dressed = dress(structure, rules.dress, name=name or stem.name, tags=tags)
        notes += dressed
    placing: list[str] = []
    for placed in rules.place.get(name or stem.name, []):
        structure, outside = place_blocks(structure, placed)
        notes.append(f"placed {placed.block} from {_fmt(placed.start)} to {_fmt(placed.to)}")
        placing += [f"place: {_fmt(pos)} lies outside the room" for pos in outside]
    result = ImportedRoom(file, template, sidecar, structure, False, [*source.notes, *notes])
    result.problems += placing
    if stem.name == REVIEW_FILE.removesuffix(".yaml"):
        result.problems.append("'review' is reserved for the gallery's review.yaml; rename the room")
    result.problems += [f"{where}: {message}" for where, message in check_structure(structure, universe)]
    if result.problems:
        return result

    room = RoomDef(
        pools=previous.pools if previous else [],
        tags=tags,
        weight=previous.weight if previous else 1,
        **(previous.model_dump(include=set(KEPT_RULES)) if previous else {}),
        source=RoomSource(
            file=file.name, format=source.format, data_version=structure.data_version, ours=ours
        ),
    )
    stem.parent.mkdir(parents=True, exist_ok=True)
    template.write_bytes(write_structure(structure))
    sidecar.write_text(sidecar_text(room, sidecar, paths), encoding="utf-8")
    result.written = True
    return result


def place_blocks(structure: Structure, placed: PlacedBlocks) -> tuple[Structure, list[Pos]]:
    """The structure with the box of ``placed`` set to its block (block entity data there is dropped), and the
    positions of the box that lie outside the room."""
    state = BlockState.parse(placed.block)
    box = [
        (x, y, z)
        for x in range(min(placed.start[0], placed.to[0]), max(placed.start[0], placed.to[0]) + 1)
        for y in range(min(placed.start[1], placed.to[1]), max(placed.start[1], placed.to[1]) + 1)
        for z in range(min(placed.start[2], placed.to[2]), max(placed.start[2], placed.to[2]) + 1)
    ]
    inside = {pos for pos in box if all(0 <= c < s for c, s in zip(pos, structure.size, strict=True))}
    blocks = [block for block in structure.blocks if block.pos not in inside]
    blocks += [StructureBlock(pos, state) for pos in sorted(inside)]
    return replace(structure, blocks=tuple(blocks)), [pos for pos in box if pos not in inside]


def _fmt(pos: Pos) -> str:
    return " ".join(map(str, pos))


def apply_rules(structure: Structure, rules: ImportRules) -> tuple[Structure, list[str]]:
    """The structure with the rules applied, and what they changed."""
    remapped: Counter[str] = Counter()
    dropped_data: Counter[str] = Counter()
    unmapped_jigsaw: set[str] = set()
    dropped_jigsaws: Counter[str] = Counter()
    normalized = 0
    loot: Counter[tuple[str, str | None]] = Counter()
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
        if data is not None and isinstance(data.get(LOOT_TABLE), str):
            data = _map_loot_table(data, rules, loot)
        if state.name == JIGSAW and data is not None:
            data = _map_final_state(data, rules, remapped)
            face = connector_face(block.pos, state.get("orientation"), structure.size)
            if face is None and rules.jigsaws.interior == "drop":
                dropped_jigsaws[str(data.get("pool", "?"))] += 1
                state, data = _final_block(data), None
            elif face is not None and rules.jigsaws.normalize:
                connector = SHAFT if face in ("up", "down") else DOOR
                data = {**data, "name": connector, "target": connector, "pool": EMPTY_POOL}
                normalized += 1
            else:
                data = _map_jigsaw_names(data, rules, unmapped_jigsaw)
        blocks.append(StructureBlock(block.pos, state, data))

    kept = tuple(e for e in structure.entities if e.id in rules.entities.keep)
    dropped_entities = Counter(e.id or "?" for e in structure.entities if e.id not in rules.entities.keep)

    data_version = structure.data_version
    if data_version is None and rules.data_version is not None:
        data_version = rules.data_version

    notes = [f"remapped {count} x {name} to {rules.blocks[name]}" for name, count in sorted(remapped.items())]
    notes += [
        f"dropped the block entity data of {count} x {name} (map its id in block_entities to keep it)"
        for name, count in sorted(dropped_data.items())
    ]
    notes += [
        f"replaced {count} x interior jigsaw (pool {pool}) with its final_state"
        for pool, count in sorted(dropped_jigsaws.items())
    ]
    if normalized:
        notes.append(f"named {normalized} connector(s) {DOOR} or {SHAFT}")
    notes += [
        f"{'dropped the loot table' if new is None else f'mapped the loot table to {new}'} of {count} x container"
        f" ({old})"
        for (old, new), count in sorted(loot.items(), key=lambda item: item[0][0])
    ]
    notes += [f"dropped {count} x entity {id_}" for id_, count in sorted(dropped_entities.items())]
    if unmapped_jigsaw:
        notes.append(f"jigsaw names and pools left as they are: {', '.join(sorted(unmapped_jigsaw))}")
    if data_version != structure.data_version:
        notes.append(f"the file states no data version; took {data_version} from the rules")
    return replace(structure, blocks=tuple(blocks), entities=kept, data_version=data_version), notes


def _map_loot_table(
    data: dict[str, Any], rules: ImportRules, loot: Counter[tuple[str, str | None]]
) -> dict[str, Any]:
    table = data[LOOT_TABLE]
    mapped, new = rules.loot_table(table)
    if not mapped:
        return data  # validation reports a table outside the ID universe
    loot[table, new] += 1
    if new is None:
        return {k: v for k, v in data.items() if k not in (LOOT_TABLE, _LOOT_TABLE_SEED)}
    return {**data, LOOT_TABLE: new}


def _map_final_state(data: dict[str, Any], rules: ImportRules, remapped: Counter[str]) -> dict[str, Any]:
    """The block a jigsaw leaves behind (``final_state``) through ``blocks``, like any other block."""
    final_state = data.get("final_state")
    if not isinstance(final_state, str):
        return data
    name, properties, block_data = split_state_text(final_state)
    if name not in rules.blocks:
        return data
    rule = rules.blocks[name]
    remapped[name] += 1
    return {**data, "final_state": rule + block_data if "[" in rule else rule + properties + block_data}


def _final_block(data: dict[str, Any]) -> BlockState:
    """The block a dropped jigsaw becomes: its ``final_state`` without block entity data (air without one)."""
    final_state = data.get("final_state")
    if not isinstance(final_state, str):
        return BlockState(AIR)
    name, properties, _ = split_state_text(final_state)
    return BlockState.parse(name + properties)


def _map_jigsaw_names(data: dict[str, Any], rules: ImportRules, unmapped: set[str]) -> dict[str, Any]:
    """The jigsaw's names and pool through ``connectors`` and ``pools``."""
    mapped = dict(data)
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
        "# Imported by `rf rooms import`: draft tags from the connectors and the name, pools with the dungeons (W4).",
        _yaml_list("pools", room.pools),
        _yaml_list("tags", room.tags),
        f"weight: {room.weight}",
    ]
    if room.max_per_layout is not None:
        lines.append(f"max_per_layout: {room.max_per_layout}")
    if room.near_start:
        lines.append("near_start: true")
    if room.explosion_breaks:
        lines.append(_yaml_list("explosion_breaks", room.explosion_breaks))
    if room.entry is not None:
        lines.append(f"entry: {room.entry}")
    if source is not None:
        lines += [
            "source:",
            f"  file: {json.dumps(source.file, ensure_ascii=False)}",
            f"  format: {source.format}",
            f"  data_version: {source.data_version}",
        ]
        if source.ours:
            lines.append("  ours: true")
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
