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
  ``COBWEB_SLOTS``) and barrels. No armor stands: Matěj tried them in the playtest (2026-10-14), and placed by rule
  they looked out of place (facing a wall, standing in front of a throne). Doorways stay clear: nothing goes next to
  a connector; nothing takes the space of the room's own entities (a painting pops off when a block fills its space).
  ``without`` leaves a decoration out of the rooms it names; barrels come last, so leaving them out changes nothing
  else in a room.
- **Barrels form a pattern** (ADR 0036, RLM-262): they stand on the room's main floor (the level most of its floor
  is on) against its outer walls, a full block with no air behind it up to the room's edge, so never on or beside
  a throne, a pillar or a platform inside the room (RLM-256). A hallway gets at most one, in a corner; stairs none;
  other rooms, by floor area (``BARREL_AREAS``), two (a pair in a corner, two corners that mirror each other or a
  pair in a wall's middle), three (a row in a wall's middle) or four (two mirrored pairs or four mirrored corners).
  Mirrors are the room's own: across the middle of its main floor, in x or z, and a group in a wall's middle is one
  a mirror maps onto itself. Barrels never stand in a passage one block wide, nor split the main floor. A room that
  fits no pattern of its size tries the smaller ones down to two, and gets no barrel rather than a single one in a
  wall's middle.

Everything is decided by hashes of the room's path and the positions, so the same import gives the same room.
"""

from __future__ import annotations

import hashlib
from collections import Counter
from collections.abc import Callable, Collection
from dataclasses import dataclass, replace
from fnmatch import fnmatchcase
from typing import Literal

from pydantic import BaseModel, ConfigDict, Field

from realmforge_tools.rooms.check import connector_face
from realmforge_tools.rooms.structure import (
    JIGSAW,
    BlockState,
    Pos,
    Structure,
    StructureBlock,
)

AIRS = frozenset({"minecraft:air", "minecraft:cave_air"})
# A family counts towards a surface's variety when it covers at least this share of the surface.
FAMILY_SHARE = 0.05
# Each variant added to a surface replaces this share of the surface's replaceable blocks.
VARIANT_SHARE = 0.15
# Free spots at the top of the walls per vine strand and per cobweb, in a room that needed all its variants.
VINE_SLOTS = 6
COBWEB_SLOTS = 8
GRAVEL = "minecraft:gravel"
BARREL = "minecraft:barrel[facing=up,open=false]"
# Floor area from which a room that is no hallway gets 4, 3 or 2 barrels.
BARREL_AREAS = ((250, 4), (100, 3), (30, 2))
# Block names that are no full block to stand on or hang from.
_NOT_FULL = (
    "slab", "stairs", "fence", "_wall", "pane", "bars", "carpet", "button", "plate", "rail", "torch", "lantern",
    "chain", "door", "sign", "banner", "leaves", "grass", "flower", "vine", "web", "snow", "water", "lava", "chest",
    "barrel", "spawner", "jigsaw", "glass", "candle", "pot", "skull", "head", "air",
)  # fmt: skip
_SHAPES = ("_slab", "_stairs", "_wall", "_planks")
_SIDES = {"north": (0, 0, -1), "south": (0, 0, 1), "west": (-1, 0, 0), "east": (1, 0, 0)}
# Entities that hang on a wall and pop off when a block fills their space; paintings span up to 4 x 4 blocks.
_HANGING = frozenset({"minecraft:painting", "minecraft:item_frame", "minecraft:glow_item_frame"})
_HANGING_REACH = 2

Surface = Literal["floor", "walls", "ceiling"]
Decoration = Literal["vines", "cobwebs", "barrels"]
Mirror = Callable[[Pos], Pos]


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
    without: dict[Decoration, list[str]] = Field(
        default_factory=dict, description="Rooms (fnmatch on their path) that do not get a decoration."
    )

    def excludes(self, name: str) -> bool:
        return any(fnmatchcase(name, pattern) for pattern in self.exclude)

    def decorations_for(self, name: str) -> list[Decoration]:
        """The decorations the room ``name`` gets: all of them but those ``without`` names it under."""
        return [
            decoration
            for decoration in self.decorations
            if not any(fnmatchcase(name, pattern) for pattern in self.without.get(decoration, []))
        ]


@dataclass
class _Room:
    """The room as a grid, with what dressing needs to know about it."""

    size: Pos
    blocks: dict[Pos, StructureBlock]
    doors: list[Pos]
    taken: set[Pos]
    """Air cells the room's own entities need; no decoration goes there."""

    def name(self, pos: Pos) -> str | None:
        block = self.blocks.get(pos)
        return block.state.name if block is not None else None

    def is_air(self, pos: Pos) -> bool:
        return self.name(pos) in AIRS

    def is_free(self, pos: Pos) -> bool:
        """Air that no entity of the room needs."""
        return self.is_air(pos) and pos not in self.taken

    def is_full(self, pos: Pos) -> bool:
        name = self.name(pos)
        return name is not None and not any(part in name for part in _NOT_FULL)

    def near_door(self, pos: Pos, clearance: int) -> bool:
        return any(max(abs(pos[0] - d[0]), abs(pos[2] - d[2])) <= clearance for d in self.doors)


def dress(
    structure: Structure, rules: DressRules, *, name: str, tags: list[str] | None = None
) -> tuple[Structure, list[str]]:
    """The room with variety and decorations added, and what was added. ``name`` (the room's path below its
    source) seeds every choice; ``tags`` (its slot tags) keep barrels out of stairs and down to one in a hallway."""
    room = _Room(
        structure.size,
        {block.pos: block for block in structure.blocks},
        [
            block.pos
            for block in structure.blocks
            if block.state.name == JIGSAW
            and connector_face(block.pos, block.state.get("orientation"), structure.size) is not None
        ],
        _taken(structure),
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

    placed = _decorate(room, surfaces, rules.decorations_for(name), plainness, name, set(tags or []))
    if placed:
        notes.append(f"added {', '.join(placed)}")
    return replace(structure, blocks=tuple(room.blocks.values())), notes


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
) -> list[str]:
    placed: list[str] = []
    floor_area = len(surfaces["floor"])
    top = _top_wall_slots(room)
    if "vines" in decorations:
        strands = round(len(top) * plainness / VINE_SLOTS)
        hung = 0
        for pos, side in sorted(top, key=lambda slot: _value(name, "vines", slot[0]))[:strands]:
            for step in range(1 + int(_value(name, "vine-length", pos) * 3)):
                below = (pos[0], pos[1] - step, pos[2])
                if not room.is_free(below) or not room.is_full(_beside(below, side)):
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
    if "barrels" in decorations and "stairs" not in tags:
        if "hallway" in tags:
            count = int(plainness >= 0.5 and _value(name, "barrel-chance", (0, 0, 0)) < 0.5)
        else:
            count = next((n for area, n in BARREL_AREAS if floor_area >= area), 0)
        barrels = barrel_pattern(room, count, name)
        for pos in barrels:
            room.blocks[pos] = StructureBlock(pos, BlockState.parse(BARREL))
        if barrels:
            placed.append(f"{len(barrels)} x barrel")
    return placed


def barrel_pattern(room: _Room, count: int, name: str) -> list[Pos]:
    """Where ``count`` barrels go as one of the patterns of the module's docstring, or fewer, down to two (a hallway's
    one stays one), when no pattern of that size fits the room; ``name`` picks among the patterns that fit."""
    floor = _main_floor(room)
    walls = _outer_wall_slots(room, floor)
    if not walls:
        return []
    corners = {pos for pos, sides in walls.items() if _perpendicular(sides)}
    x2 = min(pos[0] for pos in floor) + max(pos[0] for pos in floor)
    z2 = min(pos[2] for pos in floor) + max(pos[2] for pos in floor)
    mirrors: tuple[Mirror, ...] = (lambda p: (x2 - p[0], p[1], p[2]), lambda p: (p[0], p[1], z2 - p[2]))
    parts = _parts(floor, set())
    for size in range(count, 1 if count > 1 else 0, -1):
        patterns = {p for p in _patterns(walls, corners, mirrors, size) if _parts(floor, set(p)) <= parts}
        if patterns:
            return list(
                min(patterns, key=lambda pattern: (_value(name, f"barrels:{pattern}", (0, 0, 0)), pattern))
            )
    return []


def _patterns(
    walls: dict[Pos, set[str]], corners: set[Pos], mirrors: tuple[Mirror, ...], count: int
) -> set[tuple[Pos, ...]]:
    """Every pattern of ``count`` barrels that fits, each as its sorted positions: one in a corner; a pair in a
    corner, two mirrored corners or a pair in a wall's middle; a row of three in a wall's middle; two mirrored pairs
    or four mirrored corners. A wall's middle is where a mirror maps the group onto itself."""
    singles = [(corner,) for corner in corners]
    pairs = _runs(walls, 2)
    found: set[tuple[Pos, ...]] = set()
    if count == 1:
        found = set(singles)
    elif count == 2:
        found = {pair for pair in pairs if pair[0] in corners or pair[1] in corners}
        found |= set(_mirrored(singles, mirrors, 1, corners))
        found |= {pair for pair in pairs if _centred(pair, mirrors)}
    elif count == 3:
        found = {row for row in _runs(walls, 3) if _centred(row, mirrors)}
    elif count == 4:
        found = set(_mirrored(pairs, mirrors, 1, walls.keys())) | set(_mirrored(singles, mirrors, 2, corners))
    return {pattern for pattern in found if len(pattern) == count}


def _parts(floor: list[Pos], blocked: set[Pos]) -> int:
    """How many pieces the main floor falls into, side by side, without ``blocked``: barrels must not add one."""
    left = set(floor) - blocked
    count = 0
    while left:
        count += 1
        todo = [left.pop()]
        while todo:
            x, y, z = todo.pop()
            for dx, _, dz in _SIDES.values():
                cell = (x + dx, y, z + dz)
                if cell in left:
                    left.remove(cell)
                    todo.append(cell)
    return count


def _centred(group: tuple[Pos, ...], mirrors: tuple[Mirror, ...]) -> bool:
    return any({mirror(pos) for pos in group} == set(group) for mirror in mirrors)


def _mirrored(
    groups: list[tuple[Pos, ...]], mirrors: tuple[Mirror, ...], times: int, allowed: Collection[Pos]
) -> list[tuple[Pos, ...]]:
    """The groups with their mirror images added, across one mirror (``times`` 1) or both (2), as sorted
    positions; a mirror image that leaves ``allowed`` or meets the group itself drops the pattern."""
    results = []
    for chosen in [(mirror,) for mirror in mirrors] if times == 1 else [mirrors]:
        for group in groups:
            cells = set(group)
            for mirror in chosen:
                image = {mirror(pos) for pos in cells}
                if image & cells or not all(pos in allowed for pos in image):
                    break
                cells |= image
            else:
                results.append(tuple(sorted(cells)))
    return results


def _runs(walls: dict[Pos, set[str]], length: int) -> list[tuple[Pos, ...]]:
    """``length`` slots side by side along one outer wall."""
    runs = []
    for start, sides in walls.items():
        for side in sides:
            dx, _, dz = _SIDES[side]
            step = (dz, 0, dx)  # along the wall
            run = [start]
            for _ in range(length - 1):
                nxt = (run[-1][0] + step[0], run[-1][1], run[-1][2] + step[2])
                if side not in walls.get(nxt, set()):
                    break
                run.append(nxt)
            if len(run) == length:
                runs.append(tuple(sorted(run)))
    return runs


def _main_floor(room: _Room) -> list[Pos]:
    """The air cells on full blocks at the level most of them are on: where players walk through the room."""
    cells = [pos for pos in sorted(room.blocks) if room.is_air(pos) and room.is_full(_up(pos, -1))]
    levels = Counter(pos[1] for pos in cells)
    level = max(sorted(levels), key=lambda y: levels[y], default=None)
    return [pos for pos in cells if pos[1] == level]


def _outer_wall_slots(room: _Room, floor: list[Pos]) -> dict[Pos, set[str]]:
    """The main floor's free cells two blocks or more from a connector and not in a passage one block wide, with
    the sides on which an outer wall stands: a full block with nothing but blocks (or the room's edge) behind it."""
    slots: dict[Pos, set[str]] = {}
    for pos in floor:
        if not room.is_free(pos) or room.near_door(pos, 1) or _narrow(room, pos):
            continue
        sides = {side for side in _SIDES if _outer_wall(room, pos, side)}
        if sides:
            slots[pos] = sides
    return slots


def _outer_wall(room: _Room, pos: Pos, side: str) -> bool:
    behind = _beside(pos, side)
    if not room.is_full(behind):
        return False
    while all(0 <= behind[axis] < room.size[axis] for axis in range(3)):
        if room.is_air(behind):
            return False
        behind = _beside(behind, side)
    return True


def _narrow(room: _Room, pos: Pos) -> bool:
    """A passage one block wide: full blocks on two opposite sides and none on the other two, so a barrel there would
    close it (a notch, walled on three sides, takes one)."""
    pairs = (("north", "south"), ("west", "east"))
    return any(
        all(room.is_full(_beside(pos, side)) for side in walled)
        and not any(room.is_full(_beside(pos, side)) for side in open_)
        for walled, open_ in (pairs, pairs[::-1])
    )


def _perpendicular(sides: set[str]) -> bool:
    return bool(sides & {"north", "south"}) and bool(sides & {"west", "east"})


def _taken(structure: Structure) -> set[Pos]:
    """The cells the room's entities need: around a hanging entity as far as the largest painting reaches, and an
    armor stand's or mob's own two cells."""
    taken: set[Pos] = set()
    reach = range(-_HANGING_REACH, _HANGING_REACH + 1)
    for entity in structure.entities:
        x, y, z = entity.block_pos
        if entity.id in _HANGING:
            taken |= {(x + dx, y + dy, z + dz) for dx in reach for dy in reach for dz in reach}
        else:
            taken |= {(x, y, z), (x, y + 1, z)}
    return taken


def _top_wall_slots(room: _Room, clearance: int = 1) -> list[tuple[Pos, str]]:
    """Air right under the ceiling (any block above, an arch's stairs too) next to a full wall block, at least
    ``clearance`` + 1 blocks from a connector. Each slot comes with the side its wall is on."""
    slots = []
    for pos in sorted(room.blocks):
        if not room.is_free(pos) or room.near_door(pos, clearance):
            continue
        if _up(pos, 1) not in room.blocks or room.is_air(_up(pos, 1)):
            continue
        for side in _SIDES:
            if room.is_full(_beside(pos, side)):
                slots.append((pos, side))
                break
    return slots


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
