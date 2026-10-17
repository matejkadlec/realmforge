"""The room generator (A4d, ADR 0028, architecture 15.4): a room spec, its theme and a seed -> a structure template.

The same spec, theme and seed always give the same room. The seed picks the variations (floor pattern, wall
patches, moulding, pillar layout, niche rhythm, rubble, lights, markers); the spec fixes everything else. Every
block is a role of the theme (``schema/room_spec.py``) apart from air, the connectors' jigsaws and the data markers,
so a theme swap reaches every block the room is built of. The style rules:

- **Shell:** a floor (two blocks thick under a pit), walls one block thick in three layers (a base row, the body
  with cracked and mossy patches, a cornice row under the ceiling) and a ceiling. The floor has a border along the
  walls and in the doorways and a pattern in the middle.
- **Doorways:** three blocks wide and high in the middle of each connector side, with a jigsaw at the foot of the
  middle one named ``realmforge:door`` like the imported rooms' (architecture 15.4), so generated and imported rooms
  join.
- **Paths:** the strips from each doorway to the room's middle stay clear of pillars, rubble, niches and stairs, so
  every doorway reaches every other.
- **Profiles:** stairs and slabs with their facing worked out: a moulding under the ceiling (upside-down stairs
  facing the wall, or a ledge of top slabs), corbels under pillar capitals, the steps around a pit and up to a
  balcony, the balcony's deck of top slabs. Stair shapes at corners follow vanilla's rule (``StairBlock``), which
  the game would apply on placement anyway.
- **Light:** wall lights (and lanterns from the ceiling in big rooms) until no cell where a mob could spawn is dark
  (``rooms/walk.py``), spaced so that no two wall lights are closer than ``LIGHT_SPACING``.
- **Markers:** an ``entrance`` marker in an entrance's middle, ``mob`` markers in combat rooms and arenas, ``chest``
  markers in treasure rooms (architecture 15.3).

A spec the generator cannot build (no place for a pillar, a balcony's stairs) raises ``RoomGeneratorError``.
"""

from __future__ import annotations

import random
from collections.abc import Iterable
from dataclasses import dataclass

from realmforge_tools.project import DATA_VERSION
from realmforge_tools.rooms.check import STRUCTURE_BLOCK, is_solid
from realmforge_tools.rooms.slots import shape as connector_shape
from realmforge_tools.rooms.structure import AIR, JIGSAW, BlockState, Pos, Structure, StructureBlock
from realmforge_tools.rooms.walk import LIGHT_SOURCES, Cells, light_levels, walkable
from realmforge_tools.schema.room_spec import RoomSpecDef, RoomThemeDef

# The folder below content/rooms/ (and the jar's structure/rooms/) that generated rooms take; content/rooms/ may not
# have one of its own.
GENERATED_DIR = "generated"
DOOR = "realmforge:door"
EMPTY_POOL = "minecraft:empty"
# The closest two wall lights may be (blocks apart on the larger axis).
LIGHT_SPACING = 3
_DIRECTIONS = {"north": (0, -1), "south": (0, 1), "east": (1, 0), "west": (-1, 0)}
_FACING_OF = {vector: side for side, vector in _DIRECTIONS.items()}
_CLOCKWISE = {"north": "east", "east": "south", "south": "west", "west": "north"}
_COUNTER_CLOCKWISE = {value: key for key, value in _CLOCKWISE.items()}


class RoomGeneratorError(ValueError):
    """A spec the generator cannot build."""


def room_id(spec_id: str, seed: int) -> str:
    """The id of a spec's room for ``seed``: its path below ``content/rooms/``, as the jar knows it."""
    return f"{GENERATED_DIR}/{spec_id}/{candidate_name(seed)}"


def candidate_name(seed: int) -> str:
    return f"seed_{seed}"


def spec_tags(spec: RoomSpecDef) -> list[str]:
    """The tags of a spec's rooms: the role of its kind and the shape of its connectors (``rooms/slots.py``)."""
    piece_shape = connector_shape(set(spec.connectors))
    return [spec.role] if piece_shape is None else [spec.role, piece_shape]


def theme_blocks(theme: RoomThemeDef) -> frozenset[str]:
    """Every block a room of ``theme`` may hold: the theme's and the fixed ones."""
    return frozenset({*theme.blocks.model_dump().values(), AIR, JIGSAW, STRUCTURE_BLOCK})


def generate_room(spec: RoomSpecDef, theme: RoomThemeDef, seed: int) -> Structure:
    return _Builder(spec, theme, seed).build()


@dataclass(frozen=True)
class _Door:
    side: str
    pos: Pos  # the jigsaw, at the foot of the middle of the doorway
    inward: tuple[int, int]


class _Builder:
    def __init__(self, spec: RoomSpecDef, theme: RoomThemeDef, seed: int):
        self.spec = spec
        self.theme = theme
        self.rng = random.Random(f"{spec.id}:{seed}")
        self.width, self.depth = spec.footprint
        self.height = spec.height
        self.floor = spec.floor  # the y players walk on
        self.top = self.height - 2  # the cornice row, just under the ceiling
        self.blocks: dict[Pos, BlockState] = {}
        self.data: dict[Pos, dict] = {}
        self.doors = [self._door(side) for side in spec.connectors]
        self.paths = self._paths()
        self.taken: set[tuple[int, int]] = set()  # columns a feature occupies at floor level
        self.pit: set[tuple[int, int]] = set()
        self.deck: set[tuple[int, int]] = set()
        self.niches: list[Pos] = []
        self.wall_columns: set[tuple[int, int]] = set()  # interior columns a wall with alcoves has grown into
        self.balcony_side: str | None = None
        self.markers: list[tuple[str, Pos]] = []

    # --- blocks -----------------------------------------------------------------------------------------------

    def state(self, role: str, **properties: str) -> BlockState:
        return BlockState(self.theme.block(role), tuple(sorted(properties.items())))

    def stairs(self, facing: str, half: str) -> BlockState:
        return self.state("stairs", facing=facing, half=half, shape="straight", waterlogged="false")

    def put(self, pos: Pos, state: BlockState) -> None:
        self.blocks[pos] = state
        self.data.pop(pos, None)

    def air(self, pos: Pos) -> bool:
        return self.blocks.get(pos, BlockState(AIR)).name == AIR

    def interior(self, x: int, z: int) -> bool:
        return 0 < x < self.width - 1 and 0 < z < self.depth - 1

    def columns(self) -> list[tuple[int, int]]:
        return [(x, z) for x in range(1, self.width - 1) for z in range(1, self.depth - 1)]

    def ring(self, x: int, z: int) -> bool:
        """Whether an interior column lies along a wall."""
        return self.interior(x, z) and (x in (1, self.width - 2) or z in (1, self.depth - 2))

    # --- layout -----------------------------------------------------------------------------------------------

    def _door(self, side: str) -> _Door:
        cx, cz = (self.width - 1) // 2, (self.depth - 1) // 2
        x, z = {
            "north": (cx, 0),
            "south": (cx, self.depth - 1),
            "west": (0, cz),
            "east": (self.width - 1, cz),
        }[side]
        dx, dz = _DIRECTIONS[side]
        return _Door(side, (x, self.floor, z), (-dx, -dz))

    def _paths(self) -> set[tuple[int, int]]:
        """The columns that stay clear: a three-wide strip from each doorway to the middle, and the middle."""
        cx, cz = (self.width - 1) // 2, (self.depth - 1) // 2
        paths = {(x, z) for x in range(cx - 1, cx + 2) for z in range(cz - 1, cz + 2)}
        for door in self.doors:
            x, _, z = door.pos
            if door.side in ("north", "south"):
                paths |= {
                    (lx, lz) for lx in range(cx - 1, cx + 2) for lz in range(min(z, cz), max(z, cz) + 1)
                }
            else:
                paths |= {
                    (lx, lz) for lz in range(cz - 1, cz + 2) for lx in range(min(x, cx), max(x, cx) + 1)
                }
        return {(x, z) for x, z in paths if self.interior(x, z)}

    # --- build ------------------------------------------------------------------------------------------------

    def build(self) -> Structure:
        self._shell()
        self._doorways()
        features = self.spec.features
        if "pit" in features:
            self._pit()
        if "balcony" in features:
            self._balcony()
        if "alcoves" in features:
            self._alcoves()
        if "pillars" in features:
            self._pillars()
        self._moulding()
        if "rubble" in features:
            self._rubble()
        self._markers()
        self._lights()
        self._shape_stairs()
        return self._structure()

    def _shell(self) -> None:
        rng = self.rng
        pattern = rng.choice(("scatter", "checker", "cross"))
        cx, cz = (self.width - 1) // 2, (self.depth - 1) // 2
        for x in range(self.width):
            for z in range(self.depth):
                for y in range(self.height):
                    self.blocks[(x, y, z)] = BlockState(AIR)
                for y in range(self.floor - 1):
                    self.put((x, y, z), self.state("floor"))
                self.put((x, self.floor - 1, z), self._floor_block(x, z, pattern, cx, cz))
                self.put((x, self.height - 1, z), self.state("ceiling"))
                if not self.interior(x, z):
                    self._wall_column(x, z)

    def _floor_block(self, x: int, z: int, pattern: str, cx: int, cz: int) -> BlockState:
        if not self.interior(x, z) or self.ring(x, z):
            return self.state("floor_border")
        variant = {
            "scatter": self.rng.random() < 0.12,
            "checker": (x + z) % 2 == 0,
            "cross": x == cx or z == cz,
        }[pattern]
        return self.state("floor_variant" if variant else "floor")

    def _wall_column(self, x: int, z: int) -> None:
        """A column of wall in three layers: base, body with patches, cornice."""
        self.put((x, self.floor, z), self.state("wall_base"))
        for y in range(self.floor + 1, self.top):
            roll = self.rng.random()
            low = y == self.floor + 1
            role = "wall_body_mossy" if roll < (0.16 if low else 0.07) else "wall_body"
            if role == "wall_body" and roll > 0.9:
                role = "wall_body_cracked"
            self.put((x, y, z), self.state(role))
        self.put((x, self.top, z), self.state("wall_cornice"))

    def _doorways(self) -> None:
        for door in self.doors:
            x, y, z = door.pos
            dx, dz = door.inward
            for side in (-1, 0, 1):
                lx, lz = x + side * abs(dz), z + side * abs(dx)
                for dy in range(3):
                    self.put((lx, y + dy, lz), BlockState(AIR))
                self.put((lx, y - 1, lz), self.state("floor_border"))
            self.put((x, y, z), BlockState(JIGSAW, (("orientation", f"{door.side}_up"),)))
            self.data[(x, y, z)] = {
                "id": JIGSAW,
                "final_state": AIR,
                "joint": "rollable",
                "name": DOOR,
                "pool": EMPTY_POOL,
                "target": DOOR,
            }

    def _pit(self) -> None:
        """A sunken middle one block deep: its outer ring is a step of stairs facing out, its middle the pit's
        floor. The room's floor is two blocks thick here (``RoomSpecDef.floor``)."""
        inset = max(3, round(min(self.width, self.depth) / 4))
        x0, x1, z0, z1 = inset, self.width - 1 - inset, inset, self.depth - 1 - inset
        floor_y = self.floor - 1
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                self.pit.add((x, z))
                self.taken.add((x, z))
                edges = [
                    side
                    for side, hit in (
                        ("west", x == x0),
                        ("east", x == x1),
                        ("north", z == z0),
                        ("south", z == z1),
                    )
                    if hit
                ]
                if edges:
                    self.put((x, floor_y, z), self.stairs(edges[0], "bottom"))
                else:
                    self.put((x, floor_y, z), BlockState(AIR))
                    self.put((x, floor_y - 1, z), self.state("floor_variant" if (x + z) % 2 else "floor"))

    def _balcony(self) -> None:
        """A deck of top slabs two blocks deep along a side without a doorway, three blocks above the floor, and
        three stairs up to it, standing on a wall-body footing."""
        rng = self.rng
        side = rng.choice(self.spec.free_sides())
        self.balcony_side = side
        deck_y = self.floor + 3
        rows = self._rows_along(side, 2)
        for x, z in rows:
            self.deck.add((x, z))
            self.put((x, deck_y, z), self.state("slab", type="top", waterlogged="false"))
        # Three stairs climb to the deck's front row from the room, perpendicular to the wall, as near a corner as
        # the paths allow.
        dx, dz = _DIRECTIONS[side]
        inward = (-dx, -dz)
        starts = sorted(
            self._rows_along(side, 1),
            key=lambda c: min(c[0] - 1, self.width - 2 - c[0]) + min(c[1] - 1, self.depth - 2 - c[1]),
        )
        for x0, z0 in starts:
            run = [(x0 + inward[0] * (2 + i), z0 + inward[1] * (2 + i)) for i in range(3)]
            if all(self.interior(*c) and c not in self.paths and c not in self.taken for c in run):
                break
        else:
            raise RoomGeneratorError(f"no place for the stairs up to the balcony on the {side} side")
        for i, (x, z) in enumerate(run):
            step_y = deck_y - 1 - i
            for y in range(self.floor, step_y):
                self.put((x, y, z), self.state("wall_body"))
            self.put((x, step_y, z), self.stairs(side, "bottom"))
            self.taken.add((x, z))

    def _rows_along(self, side: str, depth: int) -> list[tuple[int, int]]:
        """The interior columns within ``depth`` of the wall on ``side``."""
        cols = []
        for x, z in self.columns():
            distance = {
                "north": z - 1,
                "south": self.depth - 2 - z,
                "west": x - 1,
                "east": self.width - 2 - x,
            }
            if distance[side] < depth:
                cols.append((x, z))
        return cols

    def _alcoves(self) -> None:
        """Niches one block deep and three high in the walls without a doorway (besides a balcony's): the row
        along such a wall becomes wall, with gaps at a seeded rhythm."""
        rng = self.rng
        sides = [side for side in self.spec.free_sides() if side != self.balcony_side]
        if not sides:
            raise RoomGeneratorError("alcoves need a side without a doorway that the balcony does not take")
        period = rng.choice((2, 3))
        phase = rng.randrange(period)
        for side in sides:
            row = sorted(self._rows_along(side, 1))
            lateral = [x if side in ("north", "south") else z for x, z in row]
            for (x, z), along in zip(row, lateral, strict=True):
                if (x, z) in self.paths or (x, z) in self.taken:
                    continue
                ends = along in (1, (self.width if side in ("north", "south") else self.depth) - 2)
                niche = not ends and (along + phase) % period != 0
                self.taken.add((x, z))
                self.wall_columns.add((x, z))
                if niche:
                    self.niches.append((x, self.floor, z))
                    for y in range(self.floor + 3, self.top):
                        self.put((x, y, z), self.state("wall_body"))
                    self.put((x, self.top, z), self.state("wall_cornice"))
                else:
                    self._wall_column(x, z)

    def _pillars(self) -> None:
        rng = self.rng
        margin = 3 if "alcoves" in self.spec.features else 2
        xs = self._pillar_line(self.width, margin)
        zs = self._pillar_line(self.depth, margin)
        candidates = [(x, z) for x in xs for z in zs]
        if rng.random() < 0.5 and len(xs) > 2:  # only the outer pillars of a long hall
            candidates = [(x, z) for x, z in candidates if x in (xs[0], xs[-1]) or z in (zs[0], zs[-1])]
        placed = []
        for x, z in candidates:
            near = [(x + dx, z + dz) for dx in (-1, 0, 1) for dz in (-1, 0, 1)]
            if (x, z) in self.paths or (x, z) in self.pit or any(c in self.taken - self.pit for c in near):
                continue
            if (x, z) in self.deck:
                continue
            placed.append((x, z))
        if not placed:
            raise RoomGeneratorError(
                "no place for a pillar outside the paths between the doorways and the features"
            )
        corbels = self.top >= self.floor + 3 and rng.random() < 0.7
        for x, z in placed:
            self.taken.add((x, z))
            self.put((x, self.floor, z), self.state("wall_base"))
            for y in range(self.floor + 1, self.top):
                self.put((x, y, z), self.state("pillar"))
            self.put((x, self.top, z), self.state("wall_cornice"))
            if corbels:
                for side, (dx, dz) in _DIRECTIONS.items():
                    pos = (x + dx, self.top, z + dz)
                    if self.interior(pos[0], pos[2]) and self.air(pos):
                        self.put(pos, self.stairs(_opposite(side), "top"))

    @staticmethod
    def _pillar_line(length: int, margin: int) -> list[int]:
        """Pillar positions along an axis: ``margin`` blocks in from each wall, and every four to six blocks in
        between."""
        first, last = margin, length - 1 - margin
        if last - first < 2:
            return [first] if first == last else []
        count = max(1, round((last - first) / 5))
        return sorted({first + round(i * (last - first) / count) for i in range(count + 1)})

    def _moulding(self) -> None:
        """Under the ceiling, along the walls: upside-down stairs facing the wall, or a ledge of top slabs. Only
        where it leaves the headroom below it (``rooms/walk.py``)."""
        if self.top < self.floor + 3:
            return
        style = self.rng.choice(("stairs", "slab"))
        for x, z in self.columns():
            pos = (x, self.top, z)
            if not self.air(pos):
                continue
            if (x, z) in self.deck and self.top < self.floor + 7:
                continue
            for side, (dx, dz) in _DIRECTIONS.items():
                neighbour = (x + dx, self.top, z + dz)
                if self._is_wall(neighbour):
                    if style == "stairs":
                        self.put(pos, self.stairs(side, "top"))
                    else:
                        self.put(pos, self.state("slab", type="top", waterlogged="false"))
                    break

    def _is_wall(self, pos: Pos) -> bool:
        """Whether ``pos`` is part of a wall: the room's shell or the row a wall with alcoves has grown into."""
        x, _, z = pos
        return not self.interior(x, z) or (x, z) in self.wall_columns

    def _rubble(self) -> None:
        rng = self.rng
        spots = [
            (x, z)
            for x, z in self.columns()
            if self.ring(x, z)
            and (x, z) not in self.paths
            and (x, z) not in self.taken
            and not self._near_door(x, z)
        ]
        rng.shuffle(spots)
        count = min(len(spots), 2 + (self.width * self.depth) // 60 + rng.randrange(2))
        for x, z in spots[:count]:
            y = self.floor
            corner = sum(1 for dx, dz in _DIRECTIONS.values() if not self.interior(x + dx, z + dz)) > 1
            self.put((x, y, z), self.state("rubble"))
            if corner and rng.random() < 0.6 and self.top > y + 1:
                self.put((x, y + 1, z), self.state("rubble_slab", type="bottom", waterlogged="false"))
            self.taken.add((x, z))
            for dx, dz in _DIRECTIONS.values():
                nx, nz = x + dx, z + dz
                if (
                    self.interior(nx, nz)
                    and (nx, nz) not in self.paths
                    and (nx, nz) not in self.taken
                    and not self._near_door(nx, nz)
                    and rng.random() < 0.45
                ):
                    self.put((nx, y, nz), self.state("rubble_slab", type="bottom", waterlogged="false"))
                    self.taken.add((nx, nz))
                elif not self.interior(nx, nz) and self.blocks[(nx, y + 1, nz)] == self.state("wall_body"):
                    self.put((nx, y + 1, nz), self.state("wall_body_cracked"))

    def _near_door(self, x: int, z: int) -> bool:
        return any(abs(x - door.pos[0]) + abs(z - door.pos[2]) <= 2 for door in self.doors)

    def _markers(self) -> None:
        kind = self.spec.kind
        stands = walkable(self._cells())
        floor_cells = sorted(
            (x, self.floor, z)
            for x, z in self.columns()
            if (x, self.floor, z) in stands and not self._near_door(x, z)
        )
        if kind == "entrance":
            cx, cz = (self.width - 1) // 2, (self.depth - 1) // 2
            middle = min(
                floor_cells or [(cx, self.floor, cz)], key=lambda p: (abs(p[0] - cx) + abs(p[2] - cz), p)
            )
            self._marker("entrance", middle)
        elif kind in ("combat", "arena"):
            count = 4 if kind == "arena" else (3 if self.width * self.depth >= 120 else 2)
            if kind == "arena" and self.deck:
                deck = sorted(p for p in stands if (p[0], p[2]) in self.deck)
                if deck:
                    self._marker("mob", self.rng.choice(deck))
                    count -= 1
            for pos in self._spread(floor_cells, count):
                self._marker("mob", pos)
        elif kind == "treasure":
            spots = [p for p in self.niches if p in stands] or [
                p for p in floor_cells if self.ring(p[0], p[2]) and (p[0], p[2]) not in self.paths
            ]
            for pos in self._spread(sorted(spots), 2 if self.width * self.depth >= 100 else 1):
                self._marker("chest", pos)

    def _spread(self, cells: list[Pos], count: int) -> list[Pos]:
        """``count`` cells far from the doorways and from each other (farthest-point sampling)."""
        if not cells:
            return []
        chosen: list[Pos] = []
        anchors = [door.pos for door in self.doors]
        for _ in range(min(count, len(cells))):

            def distance(p: Pos) -> tuple[int, float]:
                return (min(abs(p[0] - a[0]) + abs(p[2] - a[2]) for a in anchors), self.rng.random())

            best = max((p for p in cells if p not in chosen), key=distance)
            chosen.append(best)
            anchors.append(best)
        return chosen

    def _marker(self, name: str, pos: Pos) -> None:
        self.put(pos, BlockState(STRUCTURE_BLOCK, (("mode", "data"),)))
        self.data[pos] = {"id": STRUCTURE_BLOCK, "metadata": name, "mode": "DATA"}
        self.markers.append((name, pos))

    def _lights(self) -> None:
        """Wall lights at a cell above a floor next to a wall, and in a big room lanterns from the ceiling, until no
        cell where a mob could spawn is dark."""
        rng = self.rng
        torch = self.theme.block("wall_light")
        inner_w, inner_d = self.width - 2, self.depth - 2
        if self.spec.role != "hallway" and min(inner_w, inner_d) >= 9 and self.top >= self.floor + 3:
            cx, cz = (self.width - 1) // 2, (self.depth - 1) // 2
            spots = (
                [(cx, cz)]
                if rng.random() < 0.5 or max(inner_w, inner_d) < 12
                else (
                    [(cx - inner_w // 4, cz), (cx + inner_w // 4, cz)]
                    if inner_w >= inner_d
                    else [(cx, cz - inner_d // 4), (cx, cz + inner_d // 4)]
                )
            )
            for x, z in spots:
                pos = (x, self.top, z)
                if self.air(pos) and (x, z) not in self.deck:
                    self.put(pos, self.state("ceiling_light", hanging="true", waterlogged="false"))
        cells = self._cells()
        levels = light_levels(cells)
        candidates = self._torch_spots(cells)
        rng.shuffle(candidates)
        placed: list[Pos] = []
        level_of = LIGHT_SOURCES.get(torch, 14)
        dark = {p for p in cells.positions() if cells.spawnable(p) and levels.get(p, 0) == 0}
        reach_of: dict[Pos, set[Pos]] = {}
        while dark:
            best, best_count = None, 0
            for pos, _ in candidates:
                if any(
                    max(abs(pos[0] - p[0]), abs(pos[1] - p[1]), abs(pos[2] - p[2])) < LIGHT_SPACING
                    for p in placed
                ):
                    continue
                if pos not in reach_of:
                    reach_of[pos] = set(light_levels(cells, [(pos, level_of)]))
                count = len(reach_of[pos] & dark)
                if count > best_count:
                    best, best_count = pos, count
            if best is None:
                break  # validation reports what stays dark
            facing = dict(candidates)[best]
            self.put(best, self.state("wall_light", facing=facing))
            cells.names[best] = torch
            placed.append(best)
            dark -= reach_of[best]

    def _torch_spots(self, cells: Cells) -> list[tuple[Pos, str]]:
        """Cells one above a standing cell, beside a full block to hang a wall light on, with the light's facing
        (away from the block). Never in a doorway."""
        spots = []
        for x, y, z in sorted(walkable(cells)):
            if not self.interior(x, z):
                continue
            pos = (x, y + 1, z)
            if cells.name(pos) != AIR:
                continue
            for side, (dx, dz) in sorted(_DIRECTIONS.items()):
                wall = (x + dx, y + 1, z + dz)
                if cells.inside(wall) and is_solid(cells.name(wall)):
                    spots.append((pos, _opposite(side)))
                    break
        return spots

    def _shape_stairs(self) -> None:
        """Each stair's shape from its neighbours, as ``StairBlock#getStairsShape`` works it out."""
        stairs_block = self.theme.block("stairs")
        shapes = {}
        for pos, state in self.blocks.items():
            if state.name == stairs_block:
                shapes[pos] = self._stair_shape(pos, state)
        for pos, shape in shapes.items():
            props = dict(self.blocks[pos].properties)
            props["shape"] = shape
            self.blocks[pos] = BlockState(self.blocks[pos].name, tuple(sorted(props.items())))

    def _stair_shape(self, pos: Pos, state: BlockState) -> str:
        facing, half = state.get("facing"), state.get("half")
        assert facing is not None and half is not None

        def stair_at(direction: str) -> BlockState | None:
            dx, dz = _DIRECTIONS[direction]
            other = self.blocks.get((pos[0] + dx, pos[1], pos[2] + dz))
            if other is not None and other.name.endswith("_stairs") and other.get("half") == half:
                return other
            return None

        def can_take(direction: str) -> bool:
            other = stair_at(direction)
            return other is None or other.get("facing") != facing

        behind = stair_at(facing)
        if behind is not None:
            behind_facing = behind.get("facing")
            assert behind_facing is not None
            if _axis(behind_facing) != _axis(facing) and can_take(_opposite(behind_facing)):
                return "outer_left" if behind_facing == _COUNTER_CLOCKWISE[facing] else "outer_right"
        front = stair_at(_opposite(facing))
        if front is not None:
            front_facing = front.get("facing")
            assert front_facing is not None
            if _axis(front_facing) != _axis(facing) and can_take(front_facing):
                return "inner_left" if front_facing == _COUNTER_CLOCKWISE[facing] else "inner_right"
        return "straight"

    def _cells(self) -> Cells:
        return Cells((self.width, self.height, self.depth), {pos: s.name for pos, s in self.blocks.items()})

    def _structure(self) -> Structure:
        blocks = tuple(
            StructureBlock(pos, state, self.data.get(pos)) for pos, state in sorted(self.blocks.items())
        )
        return Structure((self.width, self.height, self.depth), DATA_VERSION, blocks)


def _opposite(side: str) -> str:
    return _CLOCKWISE[_CLOCKWISE[side]]


def _axis(side: str) -> str:
    return "x" if side in ("east", "west") else "z"


def candidate_seeds(spec: RoomSpecDef, count: int) -> list[int]:
    """The next ``count`` seeds no review decided on yet, from 1 up."""
    decided = set(spec.seeds) | set(spec.rejected_seeds)
    seeds: list[int] = []
    seed = 1
    while len(seeds) < count:
        if seed not in decided:
            seeds.append(seed)
        seed += 1
    return seeds


def unknown_blocks(structure: Structure, allowed: Iterable[str]) -> list[str]:
    """Blocks of ``structure`` outside ``allowed``, sorted."""
    allowed = set(allowed)
    return sorted({block.state.name for block in structure.blocks} - allowed)
