"""Vanilla structure templates (``.nbt``): read them, and write them back normalized.

A template is gzipped NBT: ``size``, a ``palette`` of block states, ``blocks`` (position, palette index and, for a
block entity, its data) and ``entities`` (position, block position, data), plus the ``DataVersion`` it was saved
with. Vanilla upgrades a template from that version when it loads it and sorts its blocks itself
(``StructureTemplate#load``), so the order of the lists in the file does not matter.

Normalized output is deterministic: blocks by (y, x, z), the palette in order of first use, compound keys sorted,
stored deflate blocks (``formats/deflate.py``). The same template always gives the same bytes.
"""

from __future__ import annotations

import gzip
import re
import zlib
from dataclasses import dataclass
from typing import Any

from realmforge_tools.formats import nbt
from realmforge_tools.formats.deflate import gzip_stored

AIR = "minecraft:air"
STRUCTURE_VOID = "minecraft:structure_void"
JIGSAW = "minecraft:jigsaw"

Pos = tuple[int, int, int]

_STATE = re.compile(r"^(?P<name>[^\[\]]+)(?:\[(?P<props>[^\[\]]*)\])?$")


class StructureError(ValueError):
    """A file that is not a readable structure template."""


@dataclass(frozen=True, order=True)
class BlockState:
    """A block and its properties, sorted by name, as a structure palette stores them."""

    name: str
    properties: tuple[tuple[str, str], ...] = ()

    @classmethod
    def parse(cls, text: str) -> BlockState:
        """Read ``minecraft:oak_stairs[facing=east,half=bottom]`` (the form commands and ``.schem`` use)."""
        match = _STATE.match(text.strip())
        if match is None:
            raise StructureError(f"invalid block state {text!r}")
        props: list[tuple[str, str]] = []
        for part in filter(None, (match["props"] or "").split(",")):
            key, sep, value = part.partition("=")
            if not sep or not key.strip() or not value.strip():
                raise StructureError(f"invalid block state {text!r}")
            props.append((key.strip(), value.strip()))
        return cls(match["name"].strip(), tuple(sorted(props)))

    def __str__(self) -> str:
        if not self.properties:
            return self.name
        return f"{self.name}[{','.join(f'{k}={v}' for k, v in self.properties)}]"

    def get(self, key: str) -> str | None:
        return dict(self.properties).get(key)


def split_state_text(text: str) -> tuple[str, str, str]:
    """``minecraft:chest[facing=north]{LootTable:"x"}`` -> (``minecraft:chest``, ``[facing=north]``,
    ``{LootTable:"x"}``): the form a jigsaw's ``final_state`` takes (vanilla parses it with block entity data
    allowed). Missing parts are empty strings."""
    name_end = min((i for i in (text.find("["), text.find("{")) if i >= 0), default=len(text))
    name, rest = text[:name_end], text[name_end:]
    properties = ""
    if rest.startswith("["):
        close = rest.find("]")
        properties, rest = (rest, "") if close < 0 else (rest[: close + 1], rest[close + 1 :])
    return name.strip(), properties, rest


@dataclass(frozen=True)
class StructureBlock:
    pos: Pos
    state: BlockState
    nbt: dict[str, Any] | None = None
    """Block entity data (with its ``id``), or None."""

    @property
    def block_entity_id(self) -> str | None:
        value = (self.nbt or {}).get("id")
        return value if isinstance(value, str) else None


@dataclass(frozen=True)
class StructureEntity:
    pos: tuple[float, float, float]
    block_pos: Pos
    nbt: dict[str, Any]
    """Entity data, with its ``id``. Vanilla replaces ``Pos`` and drops ``UUID`` when it places the entity."""

    @property
    def id(self) -> str | None:
        value = self.nbt.get("id")
        return value if isinstance(value, str) else None


@dataclass(frozen=True)
class Structure:
    size: Pos
    data_version: int | None
    """The version the template was saved with; None when the file has none. Vanilla would then assume 500 and run
    every upgrade since on the room, so ``check_structure`` refuses it."""
    blocks: tuple[StructureBlock, ...]
    entities: tuple[StructureEntity, ...] = ()


def read_structure(data: bytes, *, first_palette: bool = False) -> Structure:
    """Read a template, gzipped or not. A template with several palettes (``palettes``, like shipwrecks) is
    refused unless ``first_palette`` is set; then the first palette is read."""
    return structure_from_nbt(decode_root(data), first_palette=first_palette)


def decode_root(data: bytes) -> dict[str, Any]:
    """The root compound of an NBT file, gzipped or not."""
    if data[:2] == b"\x1f\x8b":
        try:
            data = gzip.decompress(data)
        except (OSError, EOFError, zlib.error) as e:
            raise StructureError(f"broken gzip data: {e}") from e
    try:
        return nbt.decode(data)[1]
    except (ValueError, UnicodeDecodeError) as e:
        raise StructureError(f"not NBT: {e}") from e


def structure_from_nbt(root: dict[str, Any], *, first_palette: bool = False) -> Structure:
    size = _int_triple(root.get("size"), "size")
    palette_tag = root.get("palette")
    palettes = root.get("palettes")
    if palette_tag is None and isinstance(palettes, nbt.NbtList) and palettes.items:
        if len(palettes.items) > 1 and not first_palette:
            raise StructureError(f"the template has {len(palettes.items)} palettes; a room uses one")
        palette_tag = palettes.items[0]
    if not isinstance(palette_tag, nbt.NbtList):
        raise StructureError("the template has no palette")
    palette = tuple(_palette_state(entry) for entry in palette_tag.items)

    blocks: list[StructureBlock] = []
    for entry in _compounds(root.get("blocks"), "blocks"):
        pos = _int_triple(entry.get("pos"), "blocks.pos")
        index = entry.get("state")
        if not isinstance(index, nbt.Int) or not 0 <= index.value < len(palette):
            raise StructureError(f"block at {pos} names no palette entry")
        data = entry.get("nbt")
        blocks.append(StructureBlock(pos, palette[index.value], data if isinstance(data, dict) else None))

    entities: list[StructureEntity] = []
    for entry in _compounds(root.get("entities"), "entities"):
        data = entry.get("nbt")
        if not isinstance(data, dict):
            continue  # vanilla skips an entity without data, too
        entities.append(
            StructureEntity(
                _double_triple(entry.get("pos"), "entities.pos"),
                _int_triple(entry.get("blockPos"), "entities.blockPos"),
                data,
            )
        )

    version = root.get("DataVersion")
    return Structure(
        size=size,
        data_version=version.value if isinstance(version, nbt.Int) else None,
        blocks=tuple(blocks),
        entities=tuple(entities),
    )


def write_structure(structure: Structure) -> bytes:
    """The normalized, gzipped template."""
    blocks = sorted(structure.blocks, key=lambda b: (b.pos[1], b.pos[0], b.pos[2]))
    palette: dict[BlockState, int] = {}
    for block in blocks:
        palette.setdefault(block.state, len(palette))

    def block_entry(block: StructureBlock) -> dict[str, Any]:
        entry: dict[str, Any] = {"pos": nbt.int_list(*block.pos), "state": nbt.Int(palette[block.state])}
        if block.nbt is not None:
            entry["nbt"] = _sorted(block.nbt)
        return entry

    def entity_entry(entity: StructureEntity) -> dict[str, Any]:
        return {
            "blockPos": nbt.int_list(*entity.block_pos),
            "nbt": _sorted(entity.nbt),
            "pos": nbt.NbtList(nbt.TAG_DOUBLE, tuple(nbt.Double(v) for v in entity.pos)),
        }

    root: dict[str, Any] = {}
    if structure.data_version is not None:
        root["DataVersion"] = nbt.Int(structure.data_version)
    root["size"] = nbt.int_list(*structure.size)
    root["palette"] = nbt.NbtList(nbt.TAG_COMPOUND, tuple(_palette_entry(state) for state in palette))
    root["blocks"] = nbt.NbtList(nbt.TAG_COMPOUND, tuple(block_entry(b) for b in blocks))
    root["entities"] = nbt.NbtList(nbt.TAG_COMPOUND, tuple(entity_entry(e) for e in structure.entities))
    return gzip_stored(nbt.encode(root))


def _palette_state(entry: Any) -> BlockState:
    if not isinstance(entry, dict) or not isinstance(entry.get("Name"), str):
        raise StructureError("a palette entry has no Name")
    props = entry.get("Properties") or {}
    if not isinstance(props, dict) or not all(isinstance(v, str) for v in props.values()):
        raise StructureError(f"palette entry {entry['Name']} has invalid Properties")
    return BlockState(entry["Name"], tuple(sorted(props.items())))


def _palette_entry(state: BlockState) -> dict[str, Any]:
    if not state.properties:
        return {"Name": state.name}
    return {"Name": state.name, "Properties": dict(state.properties)}


def _compounds(tag: Any, what: str) -> tuple[dict[str, Any], ...]:
    if tag is None:
        return ()
    if not isinstance(tag, nbt.NbtList) or not all(isinstance(item, dict) for item in tag.items):
        raise StructureError(f"{what} is not a list of compounds")
    return tag.items


def _int_triple(tag: Any, what: str) -> Pos:
    if not isinstance(tag, nbt.NbtList) or len(tag.items) != 3 or tag.element_type != nbt.TAG_INT:
        raise StructureError(f"{what} is not a list of three ints")
    x, y, z = (item.value for item in tag.items)
    return x, y, z


def _double_triple(tag: Any, what: str) -> tuple[float, float, float]:
    if not isinstance(tag, nbt.NbtList) or len(tag.items) != 3 or tag.element_type != nbt.TAG_DOUBLE:
        raise StructureError(f"{what} is not a list of three doubles")
    x, y, z = (item.value for item in tag.items)
    return x, y, z


def _sorted(value: Any) -> Any:
    """``value`` with every compound's keys sorted, so equal data always encodes to equal bytes."""
    if isinstance(value, dict):
        return {key: _sorted(value[key]) for key in sorted(value)}
    if isinstance(value, nbt.NbtList):
        return nbt.NbtList(value.element_type, tuple(_sorted(item) for item in value.items))
    return value
