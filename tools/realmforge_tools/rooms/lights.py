"""Lights of a generated room as patterns, not scattered (A4d, Matěj's gallery review of 2026-10-17,
[ADR 0036](../../../docs/decisions/0036-room-lights-and-decorations-form-patterns.md)).

A light placed wherever darkness happens to be reads as random; the same lights in a pattern read as built. So the
planner puts wall lights only in a few kinds of groups, each a pattern on its own, combines them, and keeps a
combination only when it leaves no cell dark where a mob could spawn (``rooms/walk.py``):

- **Wall runs:** along a wall, at least ``END_GAP`` free blocks between a light and the wall's end (a corner, a
  doorway, the room's entry edge) and at least ``BETWEEN_GAP`` between two lights on the wall, spread evenly. On a
  wall with alcoves only on the pilasters, never in a niche. Opposite walls carry N and N, or N and N + 1 lights; a
  wall alone carries one when its opposite can carry none, and a dead end may carry a single light.
- **Pillars:** every pillar's faces toward the room's middle; with four pillars in the four quarters also one light
  per pillar, each facing another world side (a pinwheel), or the inward faces of two opposite pillars.
- **Doorways:** a light on each side of a doorway, on the doorway's wall, where no pillar stands within two blocks:
  all doorways, or none (with four, also an opposite pair).
- **Lanterns** from the ceiling in big rooms that are not hallways: none, one in the middle or a pair.

Every wall light hangs at the same height, one block above the floor players walk on, over a cell of that floor,
and never above a marker. Only a dead end gets a single wall light by choice; elsewhere one is the last resort,
when rubble leaves no other pattern that lights the room. Among the combinations that light the room, the planner
prefers those the room's own
symmetry (a mirror or turn of the room that maps its doorways onto themselves) maps onto themselves, then the
fewer lights, and picks one of those by the seed.
"""

from __future__ import annotations

import itertools
from collections.abc import Callable, Iterable
from dataclasses import dataclass
from typing import Any

from realmforge_tools.rooms.check import is_solid
from realmforge_tools.rooms.structure import AIR, Pos
from realmforge_tools.rooms.walk import LIGHT_SOURCES, light_levels, walkable

# Free blocks between a wall light and its wall's end, and between two wall lights on one wall.
END_GAP = 2
BETWEEN_GAP = 3
# How many lights more than the fewest a chosen combination may have.
EXTRA_LIGHTS = 4

_DIRECTIONS = {"north": (0, -1), "south": (0, 1), "east": (1, 0), "west": (-1, 0)}
_OPPOSITE = {"north": "south", "south": "north", "east": "west", "west": "east"}
_PAIRS = (("north", "south"), ("west", "east"))


class NoLightPattern(ValueError):
    """No pattern of lights leaves the room without dark cells."""


@dataclass(frozen=True)
class Light:
    """A wall light: its cell, its facing (away from the block it hangs on), and the wall it belongs to with its
    position along that wall (pillar lights belong to their pillar)."""

    pos: Pos
    facing: str
    wall: str
    along: int


# A group's option: the wall lights and the lantern cells it adds.
Option = tuple[tuple[Light, ...], tuple[Pos, ...]]


def plan_lights(builder: Any) -> None:
    """Choose and place the lights of a built room (``generator._Builder``, with its markers placed)."""
    planner = _Planner(builder)
    lights, lanterns = planner.choose()
    for pos in lanterns:
        builder.put(pos, builder.state("ceiling_light", hanging="true", waterlogged="false"))
    for light in lights:
        builder.put(light.pos, builder.state("wall_light", facing=light.facing))


class _Planner:
    def __init__(self, builder: Any):
        self.b = builder
        self.width, self.depth = builder.width, builder.depth
        self.y = builder.floor + 1
        self.cells = builder._cells()
        self.stands = walkable(self.cells)
        self.marked = {(x, z) for _, (x, _, z) in builder.markers}
        self.rubble = {builder.theme.block("rubble"), builder.theme.block("rubble_slab")}
        self.door_sides = {door.side: door for door in builder.doors}
        self.level = LIGHT_SOURCES.get(builder.theme.block("wall_light"), 14)
        self.lantern_level = LIGHT_SOURCES.get(builder.theme.block("ceiling_light"), 15)
        self._reach: dict[tuple[Pos, int], frozenset[Pos]] = {}

    # --- where a wall light may hang -------------------------------------------------------------------------

    def usable(self, x: int, z: int) -> bool:
        """Whether a wall light may take the cell above floor column (x, z): inside the room, free, over a cell of
        the floor players walk on or a pile of rubble on it, and not above a marker."""
        b = self.b
        return (
            b.interior(x, z)
            and (x, z) not in b.wall_columns
            and (x, z) not in self.marked
            and self.cells.name((x, self.y, z)) == AIR
            and ((x, b.floor, z) in self.stands or self.cells.name((x, b.floor, z)) in self.rubble)
        )

    def hangs(self, x: int, z: int, facing: str) -> bool:
        """Whether the block behind a light at (x, z) facing ``facing`` holds it."""
        dx, dz = _DIRECTIONS[facing]
        return is_solid(self.cells.name((x - dx, self.y, z - dz)))

    def wall_cell(self, side: str, along: int) -> tuple[int, int] | None:
        """The first cell in from the wall on ``side`` at ``along``, past any wall grown into the room."""
        dx, dz = _DIRECTIONS[_OPPOSITE[side]]
        x, z = self._edge(side, along)
        x, z = x + dx, z + dz
        while self.b.interior(x, z) and (x, z) in self.b.wall_columns:
            x, z = x + dx, z + dz
        return (x, z) if self.b.interior(x, z) else None

    def _edge(self, side: str, along: int) -> tuple[int, int]:
        return {
            "north": (along, 0),
            "south": (along, self.depth - 1),
            "west": (0, along),
            "east": (self.width - 1, along),
        }[side]

    def _length(self, side: str) -> int:
        return self.width if side in ("north", "south") else self.depth

    def wall_runs(self, side: str) -> list[tuple[int, int]]:
        """The stretches of the wall on ``side`` between its ends (corners, a doorway), as (first, last) positions
        along it of the cells in front of it."""
        door = self.door_sides.get(side)
        blocked = set()
        if door is not None:
            middle = door.pos[0] if side in ("north", "south") else door.pos[2]
            blocked = {middle - 1, middle, middle + 1}
        runs, start = [], None
        for along in range(self._length(side)):
            cell = self.wall_cell(side, along)
            inside = cell is not None and along not in blocked and self._row_cell(side, cell)
            if inside and start is None:
                start = along
            if not inside and start is not None:
                runs.append((start, along - 1))
                start = None
        if start is not None:
            runs.append((start, self._length(side) - 1))
        return runs

    def _row_cell(self, side: str, cell: tuple[int, int]) -> bool:
        """Whether a cell in front of the wall lies in the wall's own row, not past a corner of a wall that grew."""
        depth = self._depth_of(side, cell)
        return depth == self._depth_of(side, self.wall_cell(side, self._middle(side)) or cell)

    def _middle(self, side: str) -> int:
        return (self._length(side) - 1) // 2

    def _depth_of(self, side: str, cell: tuple[int, int]) -> int:
        x, z = cell
        return {"north": z, "south": self.depth - 1 - z, "west": x, "east": self.width - 1 - x}[side]

    def wall_lights(self, side: str) -> list[Light]:
        """Where a light may hang on the wall on ``side``: ``END_GAP`` free blocks from each end of its stretch,
        on a block that holds it (a pilaster on a wall with alcoves, never a niche)."""
        facing = _OPPOSITE[side]
        lights = []
        for first, last in self.wall_runs(side):
            for along in range(first + END_GAP, last - END_GAP + 1):
                cell = self.wall_cell(side, along)
                if cell is not None and self.usable(*cell) and self.hangs(*cell, facing):
                    lights.append(Light((cell[0], self.y, cell[1]), facing, side, along))
        return lights

    # --- the groups ------------------------------------------------------------------------------------------

    def wall_pair_options(self, pair: tuple[str, str]) -> list[Option]:
        """Lights on two opposite walls: N and N, or N and N + 1; one wall alone when the other carries none (or
        in a dead end)."""
        first, second = pair
        spots = {side: self.wall_lights(side) for side in pair}
        best = {side: _most(spots[side]) for side in pair}
        counts = {(0, 0)}
        for n in range(1, max(best.values()) + 1):
            for a, c in ((n, n), (n, n + 1), (n + 1, n)):
                if a <= best[first] and c <= best[second]:
                    counts.add((a, c))
        if best[first] == 0 or self.b.spec.kind == "dead_end":
            counts |= {(0, c) for c in (1,) if c <= best[second]}
        if best[second] == 0 or self.b.spec.kind == "dead_end":
            counts |= {(a, 0) for a in (1,) if a <= best[first]}
        options = []
        for a, c in sorted(counts):
            chosen_a = _even(spots[first], a, self._centre(first))
            chosen_c = _even(spots[second], c, self._centre(second))
            if chosen_a is not None and chosen_c is not None:
                options.append((tuple(chosen_a + chosen_c), ()))
        return options

    def _centre(self, side: str) -> float:
        runs = self.wall_runs(side)
        if not runs:
            return self._middle(side)
        return (runs[0][0] + runs[-1][1]) / 2

    def doorway_options(self) -> list[Option]:
        """A light on each side of every doorway, or none; with four doorways also an opposite pair."""
        flanks: dict[str, tuple[Light, ...]] = {}
        for side, door in self.door_sides.items():
            middle = door.pos[0] if side in ("north", "south") else door.pos[2]
            facing = _OPPOSITE[side]
            pair = []
            for along in (middle - 2, middle + 2):
                cell = self.wall_cell(side, along)
                if cell is None or not self.usable(*cell) or not self.hangs(*cell, facing):
                    break
                if any(max(abs(cell[0] - px), abs(cell[1] - pz)) <= 2 for px, pz in self.b.pillars):
                    break
                pair.append(Light((cell[0], self.y, cell[1]), facing, side, along))
            else:
                flanks[side] = tuple(pair)
        options: list[Option] = [((), ())]
        if len(flanks) == len(self.door_sides) and flanks:
            options.append((tuple(light for side in sorted(flanks) for light in flanks[side]), ()))
        if len(self.door_sides) == 4:
            for pair in _PAIRS:
                if all(side in flanks for side in pair):
                    options.append((flanks[pair[0]] + flanks[pair[1]], ()))
        return options

    def pillar_options(self) -> list[Option]:
        """Lights on the pillars: every pillar's faces toward the middle; with four pillars in the four quarters
        also a pinwheel (one light each, each facing another side) both ways, and the inward faces of either pair of
        opposite pillars."""
        pillars = sorted(self.b.pillars)
        if not pillars:
            return []
        cx, cz = (self.width - 1) / 2, (self.depth - 1) / 2

        def inward(px: int, pz: int) -> list[str]:
            faces = []
            if px < cx:
                faces.append("east")
            if px > cx:
                faces.append("west")
            if pz < cz:
                faces.append("south")
            if pz > cz:
                faces.append("north")
            return faces

        def lights(choice: Iterable[tuple[tuple[int, int], str]]) -> tuple[Light, ...] | None:
            found = []
            for (px, pz), facing in choice:
                dx, dz = _DIRECTIONS[facing]
                x, z = px + dx, pz + dz
                if not self.usable(x, z):
                    return None
                found.append(Light((x, self.y, z), facing, f"pillar {px} {pz}", 0))
            return tuple(found)

        choices = [[(p, face) for p in pillars for face in inward(*p)]]
        quarters = {(p[0] < cx, p[1] < cz): p for p in pillars if p[0] != cx and p[1] != cz}
        if len(pillars) == 4 and len(quarters) == 4:
            nw, ne, se, sw = (
                quarters[(True, True)],
                quarters[(False, True)],
                quarters[(False, False)],
                quarters[(True, False)],
            )
            choices.append([(nw, "north"), (ne, "east"), (se, "south"), (sw, "west")])
            choices.append([(nw, "west"), (ne, "north"), (se, "east"), (sw, "south")])
            choices.append([(p, face) for p in (nw, se) for face in inward(*p)])
            choices.append([(p, face) for p in (ne, sw) for face in inward(*p)])
        options: list[Option] = [((), ())]
        for choice in choices:
            found = lights(choice)
            if found:
                options.append((found, ()))
        return options

    def lantern_options(self) -> list[Option]:
        """Lanterns from the ceiling in a big room that is no hallway: none, one in the middle or a pair."""
        b = self.b
        inner_w, inner_d = self.width - 2, self.depth - 2
        if b.spec.role == "hallway" or min(inner_w, inner_d) < 9 or b.top < b.floor + 3:
            return [((), ())]
        cx, cz = (self.width - 1) // 2, (self.depth - 1) // 2
        layouts = [[(cx, cz)]]
        if inner_w >= inner_d:
            layouts.append([(cx - inner_w // 4, cz), (cx + inner_w // 4, cz)])
        if inner_d >= inner_w:
            layouts.append([(cx, cz - inner_d // 4), (cx, cz + inner_d // 4)])
        options: list[Option] = [((), ())]
        for layout in layouts:
            cells = tuple((x, b.top, z) for x, z in layout)
            if all(self.cells.name(pos) == AIR and (pos[0], pos[2]) not in b.deck for pos in cells):
                options.append(((), cells))
        return options

    # --- choosing ---------------------------------------------------------------------------------------------

    def choose(self) -> tuple[tuple[Light, ...], tuple[Pos, ...]]:
        lantern = self.lantern_options()
        doors = self.doorway_options()
        walls = [self.wall_pair_options(pair) for pair in _PAIRS]
        attempts = [[*walls, doors, lantern]]
        if self.b.pillars:
            attempts = [
                [self.pillar_options(), doors, lantern],
                [self.pillar_options(), *walls, doors, lantern],
            ]
        for groups in attempts:
            found = self._lit_combinations(groups)
            if found:
                if (
                    self.b.spec.kind != "dead_end"
                ):  # a lone wall light only where nothing else lights the room
                    found = [c for c in found if len(c[0]) != 1] or found
                symmetric = [combo for combo in found if self._symmetric(*combo)]
                pool = symmetric or found
                fewest = min(len(lights) + len(lanterns) for lights, lanterns in pool)
                pool = [c for c in pool if len(c[0]) + len(c[1]) <= fewest + EXTRA_LIGHTS]
                return self.b.rng.choice(pool)
        raise NoLightPattern(
            "no pattern of lights leaves the room without dark cells where a mob could spawn"
        )

    def _lit_combinations(
        self, groups: list[list[Option]]
    ) -> list[tuple[tuple[Light, ...], tuple[Pos, ...]]]:
        dark = frozenset(p for p in self.cells.positions() if self.cells.spawnable(p))
        found = []
        for combo in itertools.product(*groups):
            lights = tuple(light for option in combo for light in option[0])
            lanterns = tuple(pos for option in combo for pos in option[1])
            if not self._spaced(lights):
                continue
            lit: set[Pos] = set()
            for light in lights:
                lit |= self._lit(light.pos, self.level)
            for pos in lanterns:
                lit |= self._lit(pos, self.lantern_level)
            if dark <= lit:
                found.append((tuple(sorted(lights, key=lambda li: li.pos)), tuple(sorted(lanterns))))
        return sorted(set(found), key=lambda c: ([li.pos for li in c[0]], c[1]))

    @staticmethod
    def _spaced(lights: tuple[Light, ...]) -> bool:
        """Whether no two lights share a cell, and lights on one wall keep ``BETWEEN_GAP`` free blocks apart."""
        if len({light.pos for light in lights}) < len(lights):
            return False
        for a, b in itertools.combinations(lights, 2):
            if a.wall == b.wall and not a.wall.startswith("pillar") and abs(a.along - b.along) <= BETWEEN_GAP:
                return False
        return True

    def _lit(self, pos: Pos, level: int) -> frozenset[Pos]:
        key = (pos, level)
        if key not in self._reach:
            self._reach[key] = frozenset(light_levels(self.cells, [(pos, level)]))
        return self._reach[key]

    def _symmetric(self, lights: tuple[Light, ...], lanterns: tuple[Pos, ...]) -> bool:
        """Whether a mirror or turn of the room that maps its doorways onto themselves (other than leaving it as it
        is) maps the lights onto themselves."""
        if not lights and not lanterns:
            return True
        here = {(li.pos[0], li.pos[2], li.facing) for li in lights}
        hung = {(x, z) for x, _, z in lanterns}
        for move, turn in self._symmetries():
            moved = {(*move(x, z), turn[facing]) for x, z, facing in here}
            if moved == here and {move(x, z) for x, z in hung} == hung:
                return True
        return False

    def _symmetries(self) -> list[tuple[Callable[[int, int], tuple[int, int]], dict[str, str]]]:
        w, d = self.width - 1, self.depth - 1
        same = {side: side for side in _DIRECTIONS}
        flip_x = {**same, "east": "west", "west": "east"}
        flip_z = {**same, "north": "south", "south": "north"}
        moves: list[tuple[Callable[[int, int], tuple[int, int]], dict[str, str]]] = [
            (lambda x, z: (w - x, z), flip_x),
            (lambda x, z: (x, d - z), flip_z),
            (lambda x, z: (w - x, d - z), {side: _OPPOSITE[side] for side in _DIRECTIONS}),
        ]
        if w == d:
            moves += [
                (lambda x, z: (z, x), {"north": "west", "west": "north", "south": "east", "east": "south"}),
                (
                    lambda x, z: (w - z, w - x),
                    {"north": "east", "east": "north", "south": "west", "west": "south"},
                ),
                (
                    lambda x, z: (w - z, x),
                    {"north": "east", "east": "south", "south": "west", "west": "north"},
                ),
                (
                    lambda x, z: (z, w - x),
                    {"north": "west", "west": "south", "south": "east", "east": "north"},
                ),
            ]
        doors = set(self.door_sides)
        return [(move, turn) for move, turn in moves if {turn[side] for side in doors} == doors]


def _most(lights: list[Light]) -> int:
    """How many of ``lights`` (all on one wall) fit with ``BETWEEN_GAP`` free blocks between them."""
    count, last = 0, None
    for light in sorted(lights, key=lambda li: li.along):
        if last is None or light.along - last > BETWEEN_GAP:
            count, last = count + 1, light.along
    return count


def _even(lights: list[Light], count: int, centre: float) -> list[Light] | None:
    """``count`` of ``lights`` (on one wall) spread evenly with ``BETWEEN_GAP`` free blocks between them: one in
    the middle of the wall, more spaced evenly across the places there are. None when they do not fit."""
    if count == 0:
        return []
    ordered = sorted(lights, key=lambda li: li.along)
    if len(ordered) < count:
        return None
    if count == 1:
        targets = [centre]
    else:
        low, high = ordered[0].along, ordered[-1].along
        targets = [low + (high - low) * k / (count - 1) for k in range(count)]
    best, best_cost = None, None
    for subset in itertools.combinations(ordered, count):
        if any(b.along - a.along <= BETWEEN_GAP for a, b in itertools.pairwise(subset)):
            continue
        cost = sum((li.along - t) ** 2 for li, t in zip(subset, targets, strict=True))
        if best_cost is None or cost < best_cost:
            best, best_cost = list(subset), cost
    return best
