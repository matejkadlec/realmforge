"""Room source files: vanilla structure templates (``.nbt``), Sponge schematics (``.schem``, versions 1 to 3, as
WorldEdit saves them) and Litematica schematics (``.litematic``).

All three are NBT, read here with ``formats/nbt.py`` (the byte, int and long array tags carry Sponge's block data,
Litematica's packed block states and entity UUIDs). A room keeps the data version its file states, and the game
upgrades it from there when it loads it.

nucleation (the room gallery's renderer) reads both schematic formats too, but version 0.10.24 writes only the
tight box around the non-air blocks: air layers at a room's edge vanish and the blocks shift, which would move a
room's connectors (RLM-119). So the importer reads the files itself, every block of the saved box included.
"""

from __future__ import annotations

import math
from dataclasses import dataclass
from pathlib import Path
from typing import Any

from realmforge_tools.formats import nbt
from realmforge_tools.rooms.structure import (
    STRUCTURE_VOID,
    BlockState,
    Pos,
    Structure,
    StructureBlock,
    StructureEntity,
    StructureError,
    decode_root,
    structure_from_nbt,
)

# File suffix -> the format name a sidecar records.
FORMATS = {".nbt": "nbt", ".schem": "schem", ".litematic": "litematic"}

# Keys Sponge and Litematica keep in block entity data for the block's position and type. A template stores the
# position in the block entry and the type as ``id``, so they are dropped.
_BLOCK_ENTITY_POSITION_KEYS = frozenset({"Id", "Pos", "x", "y", "z"})
_MASK_64 = (1 << 64) - 1


@dataclass(frozen=True)
class SourceRoom:
    structure: Structure
    format: str
    notes: tuple[str, ...] = ()
    """What reading the file changed or dropped, for the import report."""


def read_source(path: Path) -> SourceRoom:
    fmt = FORMATS.get(path.suffix.lower())
    if fmt is None:
        known = ", ".join(sorted(FORMATS))
        raise StructureError(f"unknown room format {path.suffix!r} (known: {known})")
    root = decode_root(path.read_bytes())
    if fmt == "schem":
        return SourceRoom(structure_from_sponge(root), fmt)
    if fmt == "litematic":
        return structure_from_litematic(root)
    notes: tuple[str, ...] = ()
    palettes = root.get("palettes")
    if "palette" not in root and isinstance(palettes, nbt.NbtList) and len(palettes.items) > 1:
        notes = (f"kept the first of {len(palettes.items)} palettes",)
    return SourceRoom(structure_from_nbt(root, first_palette=True), fmt, notes)


# --- Sponge ------------------------------------------------------------------------------------------------------


def structure_from_sponge(root: dict[str, Any]) -> Structure:
    """A template from a Sponge schematic. Version 3 nests the blocks in ``Blocks`` and the entity data in ``Data``;
    versions 1 and 2 keep both flat. Air stays (a template clears with it); structure voids are left out, as vanilla
    does when it saves a template."""
    schematic = root.get("Schematic", root)
    version = _int(schematic, "Version")
    if version not in (1, 2, 3):
        raise StructureError(f"unknown Sponge schematic version {version}")
    width, height, length = (_int(schematic, key) & 0xFFFF for key in ("Width", "Height", "Length"))
    if version == 3:
        blocks_tag = schematic.get("Blocks")
        if not isinstance(blocks_tag, dict):
            raise StructureError("the schematic has no Blocks")
        palette_tag, data, entity_list = (
            blocks_tag.get("Palette"),
            blocks_tag.get("Data"),
            blocks_tag.get("BlockEntities"),
        )
    else:
        palette_tag, data = schematic.get("Palette"), schematic.get("BlockData")
        entity_list = schematic.get("BlockEntities", schematic.get("TileEntities"))
    if not isinstance(palette_tag, dict) or not all(isinstance(i, nbt.Int) for i in palette_tag.values()):
        raise StructureError("the schematic has no block palette")
    palette = {index.value: BlockState.parse(name) for name, index in palette_tag.items()}
    indices = _varints(data.values if isinstance(data, nbt.ByteArray) else ())
    if len(indices) != width * height * length:
        raise StructureError(f"expected {width * height * length} blocks, found {len(indices)}")

    block_data: dict[Pos, dict[str, Any]] = {}
    for entry in _compounds(entity_list):
        pos = entry.get("Pos")
        if not isinstance(pos, nbt.IntArray) or len(pos.values) != 3:
            raise StructureError("a block entity has no Pos")
        values = entry.get("Data", {}) if version == 3 else entry
        x, y, z = pos.values
        block_data[x, y, z] = _block_entity(values, entry.get("Id"))

    blocks: list[StructureBlock] = []
    for i, index in enumerate(indices):
        state = palette.get(index)
        if state is None:
            raise StructureError(f"block data names palette index {index}, which the palette lacks")
        if state.name == STRUCTURE_VOID:
            continue
        pos = (i % width, i // (width * length), (i // width) % length)
        blocks.append(StructureBlock(pos, state, block_data.get(pos)))

    entities: list[StructureEntity] = []
    for entry in _compounds(schematic.get("Entities")):
        values = dict(entry.get("Data", {}) if version == 3 else entry)
        if isinstance(entry.get("Id"), str):
            values.setdefault("id", entry["Id"])
        values.pop("Id", None)
        entities.append(_entity(entry.get("Pos"), values, (0, 0, 0)))

    declared = schematic.get("DataVersion")
    return Structure(
        size=(width, height, length),
        data_version=declared.value if isinstance(declared, nbt.Int) else None,
        blocks=tuple(blocks),
        entities=tuple(entities),
    )


def _varints(signed: tuple[int, ...]) -> list[int]:
    out: list[int] = []
    value = shift = 0
    for byte in signed:
        byte &= 0xFF
        value |= (byte & 0x7F) << shift
        if byte & 0x80:
            shift += 7
            if shift > 28:
                raise StructureError("a block index varint is too long")
            continue
        out.append(value)
        value = shift = 0
    if shift:
        raise StructureError("block data ends inside a varint")
    return out


# --- Litematica --------------------------------------------------------------------------------------------------


def structure_from_litematic(root: dict[str, Any]) -> SourceRoom:
    """A template from a Litematica schematic: every region, placed in one box around them all. A region's size can
    be negative (it then extends from its position towards smaller coordinates); positions no region covers stay
    empty, so the template leaves the world there as it is. Litematica stores block entities relative to a region's
    lowest corner and entities relative to its position."""
    regions = root.get("Regions")
    if not isinstance(regions, dict) or not regions:
        raise StructureError("the schematic has no regions")
    boxes = {name: _region_box(region) for name, region in regions.items()}
    low_x, low_y, low_z = (min(box[0][axis] for box in boxes.values()) for axis in range(3))
    high_x, high_y, high_z = (max(box[0][axis] + box[1][axis] for box in boxes.values()) for axis in range(3))
    low = (low_x, low_y, low_z)
    size = (high_x - low_x, high_y - low_y, high_z - low_z)

    blocks: dict[Pos, StructureBlock] = {}
    entities: list[StructureEntity] = []
    for name, region in regions.items():
        corner, (sx, sy, sz), position = boxes[name]
        offset = (corner[0] - low[0], corner[1] - low[1], corner[2] - low[2])
        entity_offset = (position[0] - low[0], position[1] - low[1], position[2] - low[2])
        palette = tuple(_litematic_state(entry) for entry in _compounds(region.get("BlockStatePalette")))
        if not palette:
            raise StructureError(f"region {name} has no block palette")
        states = region.get("BlockStates")
        values = _unpack(
            states.values if isinstance(states, nbt.LongArray) else (), len(palette), sx * sy * sz
        )
        block_data: dict[Pos, dict[str, Any]] = {}
        for entry in _compounds(region.get("TileEntities")):
            local = tuple(_int(entry, axis) for axis in ("x", "y", "z"))
            block_data[local] = _block_entity(entry, entry.get("id", entry.get("Id")))
        for index, value in enumerate(values):
            if value >= len(palette):
                raise StructureError(f"region {name} names palette index {value}, which the palette lacks")
            state = palette[value]
            if state.name == STRUCTURE_VOID:
                continue
            local = (index % sx, index // (sx * sz), (index // sx) % sz)
            pos = (local[0] + offset[0], local[1] + offset[1], local[2] + offset[2])
            blocks[pos] = StructureBlock(pos, state, block_data.get(local))
        for entry in _compounds(region.get("Entities")):
            entities.append(_entity(entry.get("Pos"), dict(entry), entity_offset))

    declared = root.get("MinecraftDataVersion")
    notes = (f"merged {len(regions)} regions into one room",) if len(regions) > 1 else ()
    structure = Structure(
        size=size,
        data_version=declared.value if isinstance(declared, nbt.Int) else None,
        blocks=tuple(blocks.values()),
        entities=tuple(entities),
    )
    return SourceRoom(structure, "litematic", notes)


def _region_box(region: Any) -> tuple[Pos, Pos, Pos]:
    """A region's lowest corner, its (positive) size and its position."""
    if not isinstance(region, dict):
        raise StructureError("a region is not a compound")
    position, size = region.get("Position"), region.get("Size")
    if not isinstance(position, dict) or not isinstance(size, dict):
        raise StructureError("a region has no Position or Size")
    corner, extent, start = [], [], []
    for axis in ("x", "y", "z"):
        origin, length = _int(position, axis), _int(size, axis)
        if length == 0:
            raise StructureError("a region is empty")
        corner.append(origin if length > 0 else origin + length + 1)
        extent.append(abs(length))
        start.append(origin)
    return (
        (corner[0], corner[1], corner[2]),
        (extent[0], extent[1], extent[2]),
        (start[0], start[1], start[2]),
    )


def _litematic_state(entry: dict[str, Any]) -> BlockState:
    name = entry.get("Name")
    props = entry.get("Properties") or {}
    if not isinstance(name, str) or not isinstance(props, dict):
        raise StructureError("a palette entry has no Name")
    return BlockState(name, tuple(sorted(props.items())))


def _unpack(longs: tuple[int, ...], palette_size: int, count: int) -> list[int]:
    """Litematica's packed block states: ``bits`` per value, values may span two longs."""
    bits = max(2, (palette_size - 1).bit_length())
    if len(longs) * 64 < count * bits:
        raise StructureError("the block states are shorter than the region")
    words = [value & _MASK_64 for value in longs]
    mask = (1 << bits) - 1
    out: list[int] = []
    for index in range(count):
        start = index * bits
        word, offset = divmod(start, 64)
        value = words[word] >> offset
        if offset + bits > 64:
            value |= words[word + 1] << (64 - offset)
        out.append(value & mask)
    return out


# --- shared ------------------------------------------------------------------------------------------------------


def _block_entity(values: Any, entity_id: Any) -> dict[str, Any]:
    if not isinstance(values, dict):
        raise StructureError("block entity data is not a compound")
    data = {key: value for key, value in values.items() if key not in _BLOCK_ENTITY_POSITION_KEYS}
    if isinstance(entity_id, str):
        data["id"] = entity_id
    return data


def _entity(pos_tag: Any, values: dict[str, Any], offset: Pos) -> StructureEntity:
    if not isinstance(pos_tag, nbt.NbtList) or len(pos_tag.items) != 3:
        raise StructureError("an entity has no Pos")
    x, y, z = (item.value + shift for item, shift in zip(pos_tag.items, offset, strict=True))
    return StructureEntity((x, y, z), (math.floor(x), math.floor(y), math.floor(z)), values)


def _int(compound: dict[str, Any], key: str) -> int:
    value = compound.get(key)
    if not isinstance(value, nbt.Byte | nbt.Short | nbt.Int):
        raise StructureError(f"{key} is missing or not a number")
    return value.value


def _compounds(tag: Any) -> tuple[dict[str, Any], ...]:
    if tag is None:
        return ()
    if not isinstance(tag, nbt.NbtList) or not all(isinstance(item, dict) for item in tag.items):
        raise StructureError("expected a list of compounds")
    return tag.items
