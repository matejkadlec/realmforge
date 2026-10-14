"""Dressing an imported room (A4c, RLM-120): block variety on its floor, walls and ceiling, and a few decorations.

Some imported sets build a whole room from one or two blocks (a cobblestone floor, stone brick walls). The import
rules' ``dress`` section gives them variety the way Matěj asked for it in the gallery review (2026-10-14): the
plainer a surface, the more variants it gets, so a corridor gets the most and a room that is already varied little.

- **Surfaces:** a block with air above it is floor, one with air below it ceiling, one with air beside it wall.
  A surface's variety is the number of block families covering at least ``FAMILY_SHARE`` of it; a family is a
  material whatever its shape (``stone_bricks``, ``stone_brick_stairs`` and ``stone_brick_slab`` are one).
- **Variants:** while a surface has fewer families than its ``target``, the next variant of its list that the
  surface lacks replaces ``VARIANT_SHARE`` of its ``replace`` blocks, in patches. Gravel goes only where a block
  below holds it.
- **Decorations**, scaled by how plain the room was (the share of its variants it needed): vines hanging from the
  top of the walls (a strand per ``VINE_SLOTS`` free spots there), cobwebs where wall meets ceiling (one per
  ``COBWEB_SLOTS``), barrels against the walls (a hallway gets one at most), and in rooms (not hallways or stairs)
  armor stands on upside-down stone brick stairs, in full iron or full gold armor. Doorways stay clear: nothing goes
  next to a connector, and no armor stand within ``DOOR_CLEARANCE`` blocks of one.

Everything is decided by hashes of the room's path and the positions, so the same import gives the same room. An
armor stand's armor is written in the format of the template's data version, which the game upgrades from.
"""

from __future__ import annotations

import hashlib
from collections import Counter
from dataclasses import dataclass, replace
from fnmatch import fnmatchcase
from typing import Any, Literal

from pydantic import BaseModel, ConfigDict, Field

from realmforge_tools.formats import nbt
from realmforge_tools.rooms.check import connector_face
from realmforge_tools.rooms.structure import (
    JIGSAW,
    BlockState,
    Pos,
    Structure,
    StructureBlock,
    StructureEntity,
)

AIRS = frozenset({"minecraft:air", "minecraft:cave_air"})
# A family counts towards a surface's variety when it covers at least this share of the surface.
FAMILY_SHARE = 0.05
# Each variant added to a surface replaces this share of the surface's replaceable blocks.
VARIANT_SHARE = 0.15
# Armor stands keep this far (horizontally, in blocks) from every connector, other decorations one block.
DOOR_CLEARANCE = 2
# Free spots at the top of the walls per vine strand and per cobweb, in a room that needed all its variants.
VINE_SLOTS = 6
COBWEB_SLOTS = 8
GRAVEL = "minecraft:gravel"
# Block names that are no full block to stand on or hang from.
_NOT_FULL = (
    "slab", "stairs", "fence", "_wall", "pane", "bars", "carpet", "button", "plate", "rail", "torch", "lantern",
    "chain", "door", "sign", "banner", "leaves", "grass", "flower", "vine", "web", "snow", "water", "lava", "chest",
    "barrel", "spawner", "jigsaw", "glass", "candle", "pot", "skull", "head", "air",
)  # fmt: skip
_SHAPES = ("_slab", "_stairs", "_wall", "_planks")
_SIDES = {"north": (0, 0, -1), "south": (0, 0, 1), "west": (-1, 0, 0), "east": (1, 0, 0)}
# An armor stand faces away from its wall: the yaw that looks south, west, north, east.
_YAW = {"north": 0.0, "east": 90.0, "south": 180.0, "west": -90.0}
_ARMOR = {
    "iron": ("iron_boots", "iron_leggings", "iron_chestplate", "iron_helmet"),
    "gold": ("golden_boots", "golden_leggings", "golden_chestplate", "golden_helmet"),
}
# Data versions where an entity's armor changed format: items became components (1.20.5), ArmorItems became
# equipment (1.21.5).
_ITEM_COMPONENTS = 3818
_EQUIPMENT = 4301

Surface = Literal["floor", "walls", "ceiling"]
Decoration = Literal["armor_stands", "vines", "cobwebs", "barrels"]


class _Strict(BaseModel):
    model_config = ConfigDict(extra="forbid", frozen=True)


class SurfaceRules(_Strict):
    target: int = Field(ge=1, description="Block families the surface should show.")
    replace: list[str] = Field(description="Blocks the variants may replace.")
    variants: list[str] = Field(description="Blocks to add, in this order, while the surface lacks variety.")


class DressRules(_Strict):
    exclude: list[str] = Field(
        default_factory=list, description="Rooms (fnmatch on their path) left as they are."
    )
    floor: SurfaceRules | None = None
    walls: SurfaceRules | None = None
    ceiling: SurfaceRules | None = None
    decorations: list[Decoration] = Field(default_factory=list)

    def excludes(self, name: str) -> bool:
        return any(fnmatchcase(name, pattern) for pattern in self.exclude)


@dataclass
class _Room:
    """The room as a grid, with what dressing needs to know about it."""

    size: Pos
    blocks: dict[Pos, StructureBlock]
    doors: list[Pos]

    def name(self, pos: Pos) -> str | None:
        block = self.blocks.get(pos)
        return block.state.name if block is not None else None

    def is_air(self, pos: Pos) -> bool:
        return self.name(pos) in AIRS

    def is_full(self, pos: Pos) -> bool:
        name = self.name(pos)
        return name is not None and not any(part in name for part in _NOT_FULL)

    def near_door(self, pos: Pos, clearance: int) -> bool:
        return any(max(abs(pos[0] - d[0]), abs(pos[2] - d[2])) <= clearance for d in self.doors)


def dress(
    structure: Structure, rules: DressRules, *, name: str, tags: list[str] | None = None
) -> tuple[Structure, list[str]]:
    """The room with variety and decorations added, and what was added. ``name`` (the room's path below its
    source) seeds every choice; ``tags`` (its slot tags) keep armor stands out of hallways and stairs."""
    room = _Room(
        structure.size,
        {block.pos: block for block in structure.blocks},
        [
            block.pos
            for block in structure.blocks
            if block.state.name == JIGSAW
            and connector_face(block.pos, block.state.get("orientation"), structure.size) is not None
        ],
    )
    surfaces = _surfaces(room)
    notes: list[str] = []
    needed = possible = 0
    for kind in ("floor", "walls", "ceiling"):
        surface_rules: SurfaceRules | None = getattr(rules, kind)
        if surface_rules is None:
            continue
        families = _families(room, surfaces[kind])
        missing = max(0, surface_rules.target - len(families))
        needed += missing
        possible += surface_rules.target - 1
        added = _add_variants(room, surfaces[kind], surface_rules, families, missing, name, kind)
        if added:
            notes.append(f"dressed the {kind} with {', '.join(added)}")
    plainness = needed / possible if possible else 0.0

    entities = list(structure.entities)
    placed = _decorate(
        room, surfaces, rules.decorations, plainness, name, set(tags or []), structure, entities
    )
    if placed:
        notes.append(f"added {', '.join(placed)}")
    return replace(structure, blocks=tuple(room.blocks.values()), entities=tuple(entities)), notes


def family(name: str) -> str:
    """A block's material whatever its shape: ``minecraft:stone_brick_stairs`` -> ``stone_brick``."""
    base = name.split(":", 1)[-1]
    for suffix in _SHAPES:
        base = base.removesuffix(suffix)
    return base.removesuffix("s") if base.endswith(("bricks", "tiles")) else base


def _surfaces(room: _Room) -> dict[Surface, list[Pos]]:
    surfaces: dict[Surface, list[Pos]] = {"floor": [], "walls": [], "ceiling": []}
    for pos, block in sorted(room.blocks.items()):
        if block.state.name in AIRS or block.state.name == JIGSAW:
            continue
        x, y, z = pos
        if room.is_air((x, y + 1, z)):
            surfaces["floor"].append(pos)
        elif room.is_air((x, y - 1, z)):
            surfaces["ceiling"].append(pos)
        elif any(room.is_air((x + dx, y, z + dz)) for dx, _, dz in _SIDES.values()):
            surfaces["walls"].append(pos)
    return surfaces


def _families(room: _Room, positions: list[Pos]) -> set[str]:
    counts = Counter(family(room.blocks[pos].state.name) for pos in positions)
    return {name for name, count in counts.items() if count >= FAMILY_SHARE * len(positions)}


def _add_variants(
    room: _Room,
    positions: list[Pos],
    rules: SurfaceRules,
    families: set[str],
    missing: int,
    name: str,
    kind: str,
) -> list[str]:
    replaceable = [pos for pos in positions if room.blocks[pos].state.name in rules.replace]
    count = round(VARIANT_SHARE * len(replaceable))
    variants = [v for v in rules.variants if family(v) not in families][:missing]
    added = []
    for index, variant in enumerate(variants):
        candidates = replaceable
        if variant == GRAVEL:  # it falls without a block below it
            candidates = [p for p in replaceable if p[1] > 0 and room.is_full((p[0], p[1] - 1, p[2]))]
        chosen = sorted(candidates, key=lambda p: _patch(name, f"{kind}:{index}", p))[:count]
        if not chosen:
            continue
        for pos in chosen:
            room.blocks[pos] = StructureBlock(pos, BlockState(variant))
        taken = set(chosen)
        replaceable = [p for p in replaceable if p not in taken]
        added.append(f"{len(chosen)} x {variant.split(':', 1)[-1]}")
    return added


def _decorate(
    room: _Room,
    surfaces: dict[Surface, list[Pos]],
    decorations: list[Decoration],
    plainness: float,
    name: str,
    tags: set[str],
    structure: Structure,
    entities: list[StructureEntity],
) -> list[str]:
    placed: list[str] = []
    floor_area = len(surfaces["floor"])
    top = _wall_slots(room, below="ceiling")
    if "vines" in decorations:
        strands = round(len(top) * plainness / VINE_SLOTS)
        hung = 0
        for pos, side in sorted(top, key=lambda slot: _value(name, "vines", slot[0]))[:strands]:
            for step in range(1 + int(_value(name, "vine-length", pos) * 3)):
                below = (pos[0], pos[1] - step, pos[2])
                if not room.is_air(below) or not room.is_full(_beside(below, side)):
                    break
                room.blocks[below] = StructureBlock(below, BlockState("minecraft:vine", _vine_faces(side)))
                hung += 1
        if hung:
            placed.append(f"{hung} x vine")
    if "cobwebs" in decorations:
        free = [slot for slot in top if room.is_air(slot[0])]
        webs = sorted(free, key=lambda slot: _value(name, "cobwebs", slot[0]))[
            : round(len(free) * plainness / COBWEB_SLOTS)
        ]
        for pos, _ in webs:
            room.blocks[pos] = StructureBlock(pos, BlockState("minecraft:cobweb"))
        if webs:
            placed.append(f"{len(webs)} x cobweb")
    is_room = not tags & {"hallway", "stairs"}
    if "armor_stands" in decorations and is_room and floor_area >= 40:
        stands = 1 + (floor_area >= 150)
        slots = [
            s
            for s in _wall_slots(room, below="floor", clearance=DOOR_CLEARANCE)
            if all(room.is_air(_up(s[0], i)) for i in (1, 2))
        ]
        for pos, side in sorted(slots, key=lambda slot: _value(name, "stands", slot[0]))[:stands]:
            if not room.is_air(pos):
                continue
            armor = "iron" if _value(name, "armor", pos) < 0.5 else "gold"
            pedestal = BlockState.parse(
                f"minecraft:stone_brick_stairs[facing={side},half=top,shape=straight,waterlogged=false]"
            )
            room.blocks[pos] = StructureBlock(pos, pedestal)
            entities.append(_armor_stand(_up(pos, 1), side, armor, structure.data_version))
            placed.append(f"armor stand ({armor})")
    if "barrels" in decorations and "stairs" not in tags:
        if "hallway" in tags:
            count = int(plainness >= 0.5 and _value(name, "barrel-chance", (0, 0, 0)) < 0.5)
        else:
            count = (floor_area >= 30) + (floor_area >= 100) + (floor_area >= 250)
        slots = [s for s in _wall_slots(room, below="floor") if room.is_air(s[0])]
        barrels = sorted(slots, key=lambda slot: _value(name, "barrels", slot[0]))[:count]
        for pos, _ in barrels:
            room.blocks[pos] = StructureBlock(pos, BlockState.parse("minecraft:barrel[facing=up,open=false]"))
        if barrels:
            placed.append(f"{len(barrels)} x barrel")
    return placed


def _wall_slots(
    room: _Room, *, below: Literal["floor", "ceiling"], clearance: int = 1
) -> list[tuple[Pos, str]]:
    """Air next to a full wall block, at least ``clearance`` + 1 blocks from a connector: right under the ceiling
    (``below="ceiling"``; any block above, an arch's stairs too) or right on a full floor block (``below="floor"``).
    Each slot comes with the side its wall is on."""
    slots = []
    for pos in sorted(room.blocks):
        if not room.is_air(pos) or room.near_door(pos, clearance):
            continue
        if below == "ceiling" and (_up(pos, 1) not in room.blocks or room.is_air(_up(pos, 1))):
            continue
        if below == "floor" and not room.is_full(_up(pos, -1)):
            continue
        for side in _SIDES:
            if room.is_full(_beside(pos, side)):
                slots.append((pos, side))
                break
    return slots


def _armor_stand(pos: Pos, wall: str, armor: str, data_version: int | None) -> StructureEntity:
    """An armor stand at ``pos`` with its back to ``wall``, in the entity format of ``data_version``."""
    items = [f"minecraft:{item}" for item in _ARMOR[armor]]  # feet, legs, chest, head
    data: dict[str, Any] = {
        "id": "minecraft:armor_stand",
        "Rotation": nbt.NbtList(nbt.TAG_FLOAT, (nbt.Float(_YAW[wall]), nbt.Float(0.0))),
    }
    version = data_version or 0
    if version >= _EQUIPMENT:
        slots = ("feet", "legs", "chest", "head")
        data["equipment"] = {
            slot: {"id": item, "count": nbt.Int(1)} for slot, item in zip(slots, items, strict=True)
        }
    else:

        def stack(item: str) -> dict[str, Any]:
            if version >= _ITEM_COMPONENTS:
                return {"id": item, "count": nbt.Int(1)}
            return {"id": item, "Count": nbt.Byte(1)}

        data["ArmorItems"] = nbt.NbtList(nbt.TAG_COMPOUND, tuple(stack(item) for item in items))
        data["HandItems"] = nbt.NbtList(nbt.TAG_COMPOUND, ({}, {}))
    x, y, z = pos
    return StructureEntity((x + 0.5, float(y), z + 0.5), pos, data)


def _vine_faces(side: str) -> tuple[tuple[str, str], ...]:
    faces = {
        "east": "false",
        "north": "false",
        "south": "false",
        "up": "false",
        "west": "false",
        side: "true",
    }
    return tuple(sorted(faces.items()))


def _up(pos: Pos, dy: int) -> Pos:
    return (pos[0], pos[1] + dy, pos[2])


def _beside(pos: Pos, side: str) -> Pos:
    dx, _, dz = _SIDES[side]
    return (pos[0] + dx, pos[1], pos[2] + dz)


def _value(seed: str, salt: str, pos: Pos) -> float:
    """A stable number in [0, 1) for a room, a purpose and a position."""
    digest = hashlib.blake2b(f"{seed}|{salt}|{pos}".encode(), digest_size=8).digest()
    return int.from_bytes(digest) / 2**64


def _patch(seed: str, salt: str, pos: Pos) -> float:
    """Like ``_value``, but neighbouring positions share most of it, so variants come in patches."""
    cell = (pos[0] // 3, pos[1] // 3, pos[2] // 3)
    return 0.7 * _value(seed, salt + ":cell", cell) + 0.3 * _value(seed, salt, pos)
