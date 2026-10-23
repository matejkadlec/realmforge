"""Set pieces (RLM-280, [the design](../../../docs/design/set-pieces.md)): the fit rule's mask, and the matcher that
finds the spots where pieces fit in a room.

**The mask** is Matěj's top view of a piece and what it needs around it, one character per cell, cells separated by
single spaces, one row per line. The first row is the wall side (north), the room lies below it (south):

- ``W`` must be wall: a full block from the floor up to the piece's height (a barrier is none);
- ``.`` is the piece: air from the floor up to its height, over a floor block (a full block, not a barrier);
- ``_`` is clearance: a cell a player stands on (``rooms/walk.py``) that stays one with every spot filled, and that
  no other spot takes;
- ``?`` is anything.

A piece's template covers its ``.`` cells (its footprint) from the floor up to its height, built as the mask shows
it: wall to the north, front to the south. A piece that brings its own walls (``builds_walls``) also covers its
``W`` cells; the matcher reads them as ``.`` and checks the room's wall behind the first row's ``W`` cells instead.

**Spots.** The matcher turns a mask four ways and mirrors it (``StructureTemplate#transform``: a ``FRONT_BACK``
mirror, x to -x, then a clockwise turn), so one fit rule serves every wall. A spot is where a piece goes: the
footprint's lowest corner in the room (``pos``, at the height pieces stand on), the way its front faces (``facing``:
south unturned, west after a clockwise turn) and whether the piece is mirrored there. The builder places a piece so
that its turned and mirrored box starts at ``pos``. Besides the mask, a spot keeps two blocks or more (seen from
above) from every connector, keeps its cells free of the room's entities and one block from every ``mob`` marker,
and leaves no doorway cut off from a doorway it reached before, nor a clearance cell from the doorways, with every
spot of the room filled. With the light check, it also leaves no cell dark that was lit, a piece counting as solid
up to its height and lighting nothing. Generated rooms (A4d) name their wall blocks by the theme's roles, so a fit
rule's ``walls`` decide which of them count; an imported room's blocks have no roles, so any full block does.

**Patterns** ([ADR 0036](../../../docs/decisions/0036-room-lights-and-decorations-form-patterns.md), point 1): a
room offers its spots in patterns, never one by one: four spots that the room's mirrors map onto each other (in x,
in z and both: a corner each, say), a pair that one mirror or a half turn maps onto each other, or one spot that a
mirror maps onto itself (centred on a wall). The mirrors are the room's own: across the middle of its main floor
(the level most of its floor is on), as for the import dressing's barrels. The builder fills a pattern with one
piece or leaves it empty. The matcher takes bigger fits first, then the larger patterns, greedily: the patterns it
offers never overlap, so any of them can be filled together. Everything is deterministic: the same rooms and fit
rules always give the same spots.

**Density** by room role (Matěj, 2026-10-19): a hallway gets at most one pattern, a combat room one or two;
``DENSITY`` holds the defaults the dungeon type entry carries, and a piece reaches only the roles it names
(``DEFAULT_ROLES`` without any: never an entrance, a treasure room or a boss's).
"""

from __future__ import annotations

import hashlib
from collections import Counter
from collections.abc import Callable, Iterable, Sequence
from dataclasses import dataclass, field, replace
from functools import cache, cached_property

from realmforge_tools.rooms.check import FLUID_BLOCKS, LOOT_TABLE, is_solid, markers
from realmforge_tools.rooms.connectors import connectors
from realmforge_tools.rooms.dress import family
from realmforge_tools.rooms.structure import AIR, BlockState, Pos, Structure, StructureBlock
from realmforge_tools.rooms.walk import LIGHT_SOURCES, Cells, light_levels, reach, walkable

WALL, PIECE, CLEAR, ANY = "W", ".", "_", "?"
MASK_CELLS = (WALL, PIECE, CLEAR, ANY)
# The tallest piece (Matěj's maximum) and the default height.
MAX_HEIGHT = 3
# A spot keeps this far from every connector, seen from above (on the larger axis).
CONNECTOR_DISTANCE = 2
AIR_BLOCKS = frozenset({AIR, "minecraft:cave_air", "minecraft:void_air"})
BARRIER = "minecraft:barrier"
# The fit rule's ``walls`` group that stands for every role of a generated room's walls.
WALL_GROUP = "wall"
WALL_ROLES = ("wall_base", "wall_body", "wall_body_cracked", "wall_body_mossy", "wall_cornice")
# The room roles a piece reaches when its fit rule names none: not the entrance, and a treasure room or a boss's
# room only for pieces made for them.
DEFAULT_ROLES = ("hallway", "combat")
# [min, max] patterns a room of each role fills, the defaults a dungeon type entry carries (Matěj, 2026-10-19).
DENSITY: dict[str, tuple[int, int]] = {
    "entrance": (0, 1),
    "hallway": (0, 1),
    "stairs": (0, 1),
    "combat": (1, 2),
    "treasure": (0, 1),
    "boss": (0, 2),
}
# The way a piece's front faces after 0 to 3 clockwise quarter turns (vanilla's Rotation); unturned, south.
FACINGS = ("south", "west", "north", "east")
# What a cell of a placed mask asks for: the piece, wall, clearance, and the wall behind a piece that builds its own.
_PIECE, _WALL, _CLEAR, _BEHIND = "P", "W", "C", "B"
# What the trial room puts into a piece's cells: solid, not passable, not a fluid, no light.
_SOLID = "realmforge:set_piece"
_HANGING = frozenset({"minecraft:painting", "minecraft:item_frame", "minecraft:glow_item_frame"})
_HANGING_REACH = 2


class MaskError(ValueError):
    """A mask that is not Matěj's notation."""


@dataclass(frozen=True)
class Mask:
    """A mask's rows, the first one the wall side (north), each a tuple of its cells (``W``, ``.``, ``_``, ``?``)."""

    rows: tuple[tuple[str, ...], ...]

    @classmethod
    def parse(cls, text: str) -> Mask:
        lines = text.strip("\n").splitlines()
        if not lines or not any(line.strip() for line in lines):
            raise MaskError("the mask is empty")
        rows: list[tuple[str, ...]] = []
        for number, line in enumerate(lines, 1):
            cells = line.strip().split(" ")
            if any(len(cell) != 1 for cell in cells):
                raise MaskError(
                    f"row {number} ({line.strip()!r}): cells are one character each, separated by single spaces"
                )
            unknown = sorted({cell for cell in cells if cell not in MASK_CELLS})
            if unknown:
                raise MaskError(
                    f"row {number}: unknown cell {', '.join(unknown)} (W wall, . piece, _ clearance, ? anything)"
                )
            rows.append(tuple(cells))
        if len({len(row) for row in rows}) != 1:
            raise MaskError("every row needs the same number of cells")
        if not any(PIECE in row for row in rows):
            raise MaskError("the mask has no piece cell (.)")
        return cls(tuple(rows))

    @property
    def text(self) -> str:
        return "\n".join(" ".join(row) for row in self.rows)

    @property
    def width(self) -> int:
        return len(self.rows[0])

    @property
    def depth(self) -> int:
        return len(self.rows)

    def cells(self) -> dict[tuple[int, int], str]:
        """Every cell by (column, row): x grows east, z south."""
        return {(x, z): cell for z, row in enumerate(self.rows) for x, cell in enumerate(row)}

    @property
    def mirror_symmetric(self) -> bool:
        """Whether mirroring it (west to east) gives the same mask: a piece placed mirrored then takes the same
        cells, so a piece that may not be mirrored can take a mirrored spot as it is."""
        return all(row == row[::-1] for row in self.rows)


@dataclass(frozen=True)
class NamedMask:
    """A mask the workshop jig builds and the mask report counts, by name (``wall_3x1``)."""

    name: str
    label: str
    mask: Mask
    builds_walls: bool = False


def _named_masks() -> dict[str, NamedMask]:
    def rows(*lines: str) -> Mask:
        return Mask.parse("\n".join(lines))

    def line(*cells: str) -> str:
        return " ".join(cells)

    found: list[NamedMask] = []
    for width, depth in ((1, 1), (2, 1), (3, 1), (4, 1), (5, 1), (3, 2), (5, 2)):
        found.append(
            NamedMask(
                f"wall_{width}x{depth}",
                f"{width}x{depth} against a wall",
                rows(line(*[WALL] * width), *[line(*[PIECE] * width)] * depth, line(*[CLEAR] * width)),
            )
        )
    for size in (1, 2):
        found.append(
            NamedMask(
                f"corner_{size}x{size}",
                f"{size}x{size} corner",
                rows(
                    line(ANY, *[WALL] * size),
                    *[line(WALL, *[PIECE] * size)] * size,
                    line(ANY, *[CLEAR] * size),
                ),
            )
        )
    for width in (1, 2, 3):
        found.append(
            NamedMask(
                f"niche_{width}x1",
                f"{width}x1 niche",
                rows(
                    line(ANY, *[WALL] * width, ANY),
                    line(WALL, *[PIECE] * width, WALL),
                    line(ANY, *[CLEAR] * width, ANY),
                ),
            )
        )
    for size in (1, 2, 3):
        ring = line(*[CLEAR] * (size + 2))
        found.append(
            NamedMask(
                f"free_{size}x{size}",
                f"{size}x{size} free-standing",
                rows(ring, *[line(CLEAR, *[PIECE] * size, CLEAR)] * size, ring),
            )
        )
    found.append(
        NamedMask(
            "built_niche_3x1",
            "3x1 niche with its own walls (5x2 against a flat wall)",
            rows(line(*[WALL] * 5), line(WALL, *[PIECE] * 3, WALL), line(*[CLEAR] * 5)),
            builds_walls=True,
        )
    )
    return {named.name: named for named in found}


# The masks of the design's mask report and the workshop jig, by name.
NAMED_MASKS: dict[str, NamedMask] = _named_masks()


@dataclass(frozen=True)
class Fit:
    """What the matcher needs from a fit rule: the mask, the height, whether the piece builds its own walls, and the
    theme roles (or block ids) its walls may be."""

    mask: Mask
    height: int = MAX_HEIGHT
    builds_walls: bool = False
    walls: tuple[str, ...] = (WALL_GROUP,)

    @property
    def key(self) -> str:
        """A stable name of the fit, for cache keys and to group pieces that fit the same spots."""
        walls = ",".join(sorted(self.walls))
        return f"{self.mask.text.replace(chr(10), '/')}|h{self.height}|{'own' if self.builds_walls else 'room'}|{walls}"

    @property
    def digest(self) -> str:
        """A short digest of ``key``, for cache folder names."""
        return hashlib.sha256(self.key.encode()).hexdigest()[:16]

    def footprint(self) -> set[tuple[int, int]]:
        """The template's cells seen from above, in mask coordinates: the piece, and its walls if it builds them."""
        kinds = (PIECE, WALL) if self.builds_walls else (PIECE,)
        return {pos for pos, cell in self.mask.cells().items() if cell in kinds}

    def template_size(self) -> Pos:
        """The size the piece's template has: its footprint's box, as high as the piece."""
        cells = self.footprint()
        xs, zs = [x for x, _ in cells], [z for _, z in cells]
        return (max(xs) - min(xs) + 1, self.height, max(zs) - min(zs) + 1)

    @cached_property
    def area(self) -> int:
        return len(self.footprint())


@dataclass(frozen=True)
class Placement:
    """A fit's mask turned and mirrored: what each cell asks for, relative to the footprint's lowest corner."""

    turns: int
    """Clockwise quarter turns (vanilla's Rotation)."""
    mirror: bool
    """Mirrored (FRONT_BACK, x to -x) before the turn."""
    cells: tuple[tuple[int, int, str], ...]
    size: tuple[int, int]
    """The footprint's box seen from above (x, z)."""

    @property
    def facing(self) -> str:
        return FACINGS[self.turns]


def transform(x: int, z: int, turns: int, mirror: bool) -> tuple[int, int]:
    """``StructureTemplate#transform`` about the origin: a FRONT_BACK mirror, then ``turns`` clockwise turns."""
    if mirror:
        x = -x
    for _ in range(turns % 4):
        x, z = -z, x
    return x, z


@cache
def placement(fit: Fit, turns: int, mirror: bool) -> Placement:
    cells = fit.mask.cells()
    footprint = fit.footprint()
    wanted: dict[tuple[int, int], str] = {}
    for (x, z), cell in cells.items():
        if (x, z) in footprint:
            wanted[x, z] = _PIECE
        elif cell == WALL:
            wanted[x, z] = _WALL
        elif cell == CLEAR:
            wanted[x, z] = _CLEAR
    if fit.builds_walls:
        for (x, z), cell in cells.items():
            if z == 0 and cell == WALL:
                wanted[x, -1] = _BEHIND
    moved = {transform(x, z, turns, mirror): kind for (x, z), kind in wanted.items()}
    pieces = [pos for pos, kind in moved.items() if kind == _PIECE]
    min_x, min_z = min(x for x, _ in pieces), min(z for _, z in pieces)
    size = (max(x for x, _ in pieces) - min_x + 1, max(z for _, z in pieces) - min_z + 1)
    return Placement(
        turns % 4,
        mirror,
        tuple(sorted((x - min_x, z - min_z, kind) for (x, z), kind in moved.items())),
        size,
    )


def placements(fit: Fit, mirrorable: bool = True) -> list[Placement]:
    """Every different way to place the mask: the four turns, then mirrored (when ``mirrorable``); a turn or mirror
    that asks for the same cells as an earlier one is left out."""
    found: dict[tuple[tuple[int, int, str], ...], Placement] = {}
    for mirror in (False, True) if mirrorable else (False,):
        for turns in range(4):
            found.setdefault(placement(fit, turns, mirror).cells, placement(fit, turns, mirror))
    return list(found.values())


@dataclass(frozen=True, order=True)
class Spot:
    """Where a piece goes: its footprint's lowest corner in the room, at the height it stands on, and how it is
    turned and mirrored."""

    pos: Pos
    turns: int
    mirror: bool

    @property
    def facing(self) -> str:
        return FACINGS[self.turns]


@dataclass(frozen=True)
class Pattern:
    """Spots a piece fills together (or none of them): ``four``, ``pair``, ``centred``, or a ``single`` spot (the
    mask report only)."""

    fit: int
    """The index of its fit among those the room was matched with."""
    kind: str
    spots: tuple[Spot, ...]


def pattern_cells(fit: Fit, spot: Spot) -> tuple[set[Pos], set[Pos]]:
    """The cells a spot's piece fills (its footprint up to its height) and its clearance cells."""
    place = placement(fit, spot.turns, spot.mirror)
    x0, y0, z0 = spot.pos
    filled = {
        (x0 + dx, y0 + dy, z0 + dz)
        for dx, dz, kind in place.cells
        if kind == _PIECE
        for dy in range(fit.height)
    }
    clear = {(x0 + dx, y0, z0 + dz) for dx, dz, kind in place.cells if kind == _CLEAR}
    return filled, clear


class RoomView:
    """What the matcher reads from a room: its cells, where players stand, its connectors, markers and entities, its
    main floor's mirrors and its light. ``theme_blocks`` maps a generated room's roles to its theme's blocks; an
    imported room has none, and any full block of it is wall."""

    def __init__(self, structure: Structure, theme_blocks: dict[str, str] | None = None) -> None:
        self.structure = structure
        self.cells = Cells.of(structure)
        self.theme_blocks = theme_blocks
        self.stands = walkable(self.cells)
        found = connectors(structure)
        self.connectors = [(c.pos[0], c.pos[2]) for c in found]
        self.doors = [c.pos for c in found if not c.vertical and c.pos in self.stands]
        self.markers = {pos for _, pos in markers(structure)}
        self.mobs = [pos for name, pos in markers(structure) if name == "mob"]
        self.taken = _entity_cells(structure)
        self.floor = sorted(
            pos
            for pos, name in self.cells.names.items()
            if name in AIR_BLOCKS and self._floor_block((pos[0], pos[1] - 1, pos[2]))
        )

    def _floor_block(self, pos: Pos) -> bool:
        if not self.cells.inside(pos):
            return False
        name = self.cells.name(pos)
        return name != BARRIER and is_solid(name)

    @cached_property
    def door_groups(self) -> list[tuple[Pos, frozenset[Pos]]]:
        """The doorways in groups that reach each other in the room as it is, each with one doorway to start from."""
        groups: list[tuple[Pos, frozenset[Pos]]] = []
        left = list(self.doors)
        while left:
            start = left[0]
            reached = reach(self.cells, start, self.stands)
            members = frozenset(door for door in left if door in reached)
            groups.append((start, members))
            left = [door for door in left if door not in members]
        return groups

    @cached_property
    def mirrors(self) -> tuple[int, int] | None:
        """Twice the middle of the main floor in x and in z: a mirror maps x to the first minus x."""
        levels = Counter(pos[1] for pos in self.stands)
        if not levels:
            return None
        level = max(sorted(levels), key=lambda y: levels[y])
        cells = [pos for pos in self.stands if pos[1] == level]
        return (
            min(p[0] for p in cells) + max(p[0] for p in cells),
            min(p[2] for p in cells) + max(p[2] for p in cells),
        )

    @cached_property
    def sources(self) -> list[tuple[Pos, int]]:
        return [(pos, LIGHT_SOURCES[name]) for pos, name in self.cells.names.items() if name in LIGHT_SOURCES]

    @cached_property
    def levels(self) -> dict[Pos, int]:
        return light_levels(self.cells, self.sources)

    def walls(self, fit: Fit) -> Callable[[str], bool]:
        """Whether a block is wall for ``fit``: a full block that is no barrier and, in a generated room, a block of
        one of the roles (or one of the blocks) its ``walls`` name. An imported room takes any full block for a
        role, as its blocks have no roles."""
        blocks = {name for name in fit.walls if ":" in name}
        roles = [name for name in fit.walls if ":" not in name]
        if self.theme_blocks is not None:
            for role in roles:
                for name in WALL_ROLES if role == WALL_GROUP else (role,):
                    if name in self.theme_blocks:
                        blocks.add(self.theme_blocks[name])
            any_block = False
        else:
            any_block = bool(roles)

        def is_wall(name: str) -> bool:
            return name != BARRIER and is_solid(name) and (any_block or name in blocks)

        return is_wall

    def fits(self, fit: Fit, place: Placement, origin: Pos, is_wall: Callable[[str], bool]) -> bool:
        """Whether the placed mask fits at ``origin`` (its footprint's lowest corner) on its own."""
        x0, y0, z0 = origin
        cells, height = self.cells, fit.height
        for dx, dz, kind in place.cells:
            x, z = x0 + dx, z0 + dz
            if kind == _PIECE:
                if not self._floor_block((x, y0 - 1, z)):
                    return False
                for dy in range(height):
                    pos = (x, y0 + dy, z)
                    if not cells.inside(pos) or cells.name(pos) not in AIR_BLOCKS or pos in self.taken:
                        return False
                if any(max(abs(x - cx), abs(z - cz)) < CONNECTOR_DISTANCE for cx, cz in self.connectors):
                    return False
                if any(
                    max(abs(x - mx), abs(z - mz)) <= 1 and y0 - 1 <= my <= y0 + height
                    for mx, my, mz in self.mobs
                ):
                    return False
            elif kind in (_WALL, _BEHIND):
                for dy in range(height):
                    pos = (x, y0 + dy, z)
                    if not cells.inside(pos) or not is_wall(cells.name(pos)):
                        return False
            elif kind == _CLEAR:
                if (x, y0, z) not in self.stands or (x, y0, z) in self.markers:
                    return False
        return True

    def spots(self, fit: Fit, mirrorable: bool = True) -> list[Spot]:
        """Every spot where the fit fits on its own, in position order."""
        is_wall = self.walls(fit)
        found: set[Spot] = set()
        for place in placements(fit, mirrorable):
            ax, az = next((dx, dz) for dx, dz, kind in place.cells if kind == _PIECE)
            for x, y, z in self.floor:
                origin = (x - ax, y, z - az)
                if self.fits(fit, place, origin, is_wall):
                    found.add(Spot(origin, place.turns, place.mirror))
        return sorted(found)

    def image(self, fit: Fit, spot: Spot, in_x: bool, in_z: bool) -> Spot | None:
        """The spot a mirror of the room (in x, in z, or both: a half turn) maps ``spot`` onto, or None without a
        main floor. A mirror flips the piece too: in x it takes ``turns`` to ``-turns``, in z to ``2 - turns``."""
        if self.mirrors is None:
            return None
        x2, z2 = self.mirrors
        sx, sz = placement(fit, spot.turns, spot.mirror).size
        x, y, z = spot.pos
        turns, mirror = spot.turns, spot.mirror
        if in_x:
            x, turns, mirror = x2 - (x + sx - 1), -turns, not mirror
        if in_z:
            z, turns, mirror = z2 - (z + sz - 1), 2 - turns, not mirror
        return Spot((x, y, z), turns % 4, mirror)


@dataclass
class _Filled:
    """The room with the patterns accepted so far: what their pieces fill, their clearance, where players can still
    stand and the light."""

    view: RoomView
    light: bool
    filled: set[Pos] = field(default_factory=set)
    clear: set[Pos] = field(default_factory=set)
    stands: set[Pos] = field(default_factory=set)
    levels: dict[Pos, int] = field(default_factory=dict)

    def __post_init__(self) -> None:
        self.stands = set(self.view.stands)
        if self.light:
            self.levels = self.view.levels

    def add(self, fit: Fit, spots: Sequence[Spot]) -> bool:
        """Accept the spots if, with every accepted one, the room still works; otherwise change nothing."""
        filled: set[Pos] = set()
        clear: set[Pos] = set()
        for spot in spots:
            cells, near = pattern_cells(fit, spot)
            if cells & filled:
                return False  # the pattern's own spots overlap
            if near & clear:
                return False
            filled |= cells
            clear |= near
        all_filled = self.filled | filled
        if filled & self.filled or clear & self.clear or (clear | self.clear) & all_filled:
            return False
        columns = {(x, z) for x, _, z in filled}
        low = min(y for _, y, _ in filled) - 2
        high = max(y for _, y, _ in filled) + 1
        stands = {pos for pos in self.stands if not ((pos[0], pos[2]) in columns and low <= pos[1] <= high)}
        all_clear = self.clear | clear
        if not all_clear <= stands:
            return False
        reached: set[Pos] = set()
        for start, members in self.view.door_groups:
            if start not in stands:
                return False
            found = reach(self.view.cells, start, stands)
            if not members <= found:
                return False
            reached |= found
        if self.view.doors and not all_clear <= reached:
            return False
        levels = self.levels
        if self.light:
            levels = self._light(filled, all_filled)
            if levels is None:
                return False
        self.filled, self.clear, self.stands, self.levels = all_filled, all_clear, stands, levels
        return True

    def _light(self, filled: set[Pos], all_filled: set[Pos]) -> dict[Pos, int] | None:
        """The light with the new pieces in place, or None when they leave a cell dark that was lit, or let a mob
        spawn on a piece in the dark where it could not on the floor below."""
        overlay = _Overlay(self.view.cells, all_filled)
        levels = self.levels
        if any(levels.get(pos, 0) > 0 for pos in filled):
            levels = light_levels(overlay, self.view.sources)
            for pos, level in self.levels.items():
                if level > 0 and levels.get(pos, 0) == 0 and overlay.spawnable(pos):
                    return None
        columns: dict[tuple[int, int], list[int]] = {}
        for x, y, z in filled:
            columns.setdefault((x, z), []).append(y)
        for (x, z), ys in columns.items():
            top, covered = (x, max(ys) + 1, z), (x, min(ys), z)
            # A mob that could spawn on the piece in the dark is a new dark cell unless the floor it covers was one.
            if overlay.spawnable(top) and levels.get(top, 0) == 0 and covered not in self._dark:
                return None
        return levels

    @cached_property
    def _dark(self) -> set[Pos]:
        """The cells of the room as it is where a mob could already spawn in the dark."""
        cells, levels = self.view.cells, self.view.levels
        return {pos for pos in cells.positions() if cells.spawnable(pos) and levels.get(pos, 0) == 0}


class _Overlay(Cells):
    """A room's cells with pieces in some of them."""

    def __init__(self, base: Cells, filled: set[Pos]) -> None:
        super().__init__(base.size, base.names)
        self.filled = filled

    def name(self, pos: Pos) -> str:
        return _SOLID if pos in self.filled else super().name(pos)


def match(
    view: RoomView,
    fits: Sequence[Fit],
    *,
    mirrorable: Sequence[bool] | None = None,
    patterns: bool = True,
    light: bool = True,
) -> list[Pattern]:
    """The room's spots for ``fits``: as patterns (``patterns``), or as single spots for the mask report. Bigger
    fits come first; ``mirrorable[i]`` says whether some piece of fit ``i`` may be mirrored (a fit whose mask
    mirroring changes then gets no mirrored spot). ``light`` adds the light check."""
    mirrorable = list(mirrorable) if mirrorable is not None else [True] * len(fits)
    state = _Filled(view, light)
    found: list[Pattern] = []
    order = sorted(range(len(fits)), key=lambda i: (-fits[i].area, fits[i].key))
    for index in order:
        fit = fits[index]
        may_mirror = mirrorable[index] or fit.mask.mirror_symmetric
        for spot in view.spots(fit, may_mirror):
            if not patterns:
                if state.add(fit, [spot]):
                    found.append(Pattern(index, "single", (spot,)))
                continue
            for kind, group in _patterns(view, fit, spot, may_mirror):
                if state.add(fit, group):
                    found.append(Pattern(index, kind, group))
                    break
    return found


def _patterns(
    view: RoomView, fit: Fit, spot: Spot, may_mirror: bool
) -> Iterable[tuple[str, tuple[Spot, ...]]]:
    """The patterns ``spot`` can be part of, larger first, each with its spots fitting on their own."""
    is_wall = view.walls(fit)

    def usable(other: Spot | None) -> bool:
        if other is None or (other.mirror and not may_mirror):
            return False
        return view.fits(fit, placement(fit, other.turns, other.mirror), other.pos, is_wall)

    in_x, in_z, both = (view.image(fit, spot, *axes) for axes in ((True, False), (False, True), (True, True)))
    same_x = in_x is not None and _same_cells(fit, spot, in_x)
    same_z = in_z is not None and _same_cells(fit, spot, in_z)
    if not same_x and not same_z and all(usable(other) for other in (in_x, in_z, both)):
        assert in_x is not None and in_z is not None and both is not None
        yield "four", (spot, in_x, in_z, both)
    for other, same in ((in_x, same_x), (in_z, same_z), (both, False)):
        if not same and usable(other):
            assert other is not None
            yield "pair", (spot, other)
    if same_x or same_z:
        yield "centred", (spot,)


def _same_cells(fit: Fit, spot: Spot, other: Spot) -> bool:
    return pattern_cells(fit, spot)[0] == pattern_cells(fit, other)[0]


def _entity_cells(structure: Structure) -> set[Pos]:
    """The cells the room's entities need: around a hanging entity as far as the largest painting reaches, and any
    other entity's own two cells (as the import dressing keeps them free, ``rooms/dress.py``)."""
    taken: set[Pos] = set()
    reach_range = range(-_HANGING_REACH, _HANGING_REACH + 1)
    for entity in structure.entities:
        x, y, z = entity.block_pos
        if entity.id in _HANGING:
            taken |= {
                (x + dx, y + dy, z + dz) for dx in reach_range for dy in reach_range for dz in reach_range
            }
        else:
            taken |= {(x, y, z), (x, y + 1, z)}
    return taken


# --- checking a piece's template --------------------------------------------------------------------------------

# Blocks a piece never holds (the design's gameplay rules): what the builder or a room needs to own (connectors,
# markers, spawners), redstone and puzzle-like parts, and doors that look locked. Fluids and waterlogged blocks too.
FORBIDDEN_BLOCKS: dict[str, str] = {
    **dict.fromkeys(
        ("minecraft:jigsaw", "minecraft:structure_block"), "a connector or marker belongs to a room"
    ),
    **dict.fromkeys(("minecraft:spawner", "minecraft:trial_spawner"), "spawners belong to the dungeon"),
    **dict.fromkeys(
        (
            "minecraft:redstone_wire",
            "minecraft:repeater",
            "minecraft:comparator",
            "minecraft:redstone_torch",
            "minecraft:redstone_wall_torch",
            "minecraft:redstone_block",
            "minecraft:lever",
            "minecraft:observer",
            "minecraft:piston",
            "minecraft:sticky_piston",
            "minecraft:dispenser",
            "minecraft:dropper",
            "minecraft:hopper",
            "minecraft:tripwire",
            "minecraft:tripwire_hook",
            "minecraft:daylight_detector",
            "minecraft:target",
            "minecraft:tnt",
            "minecraft:sculk_sensor",
            "minecraft:calibrated_sculk_sensor",
        ),
        "no redstone or puzzle-like parts in a piece",
    ),
    **dict.fromkeys(
        ("minecraft:iron_door", "minecraft:iron_trapdoor"), "a door that looks locked looks like progression"
    ),
}
_FORBIDDEN_SUFFIXES = {
    "_button": "no puzzle-like buttons",
    "_pressure_plate": "no redstone or puzzle-like parts",
}


def piece_problems(fit: Fit, structure: Structure) -> list[tuple[str, str]]:
    """What keeps a template from being the piece its fit rule describes, as (location, message) pairs: its size
    is the footprint's box up to the piece's height, it holds blocks only in the footprint (no air elsewhere either,
    which would carve into the room), and none that a piece may not hold. ``rooms/check.py`` checks its ids."""
    problems: list[tuple[str, str]] = []
    size = fit.template_size()
    if structure.size != size:
        problems.append(
            (
                "size",
                f"{_fmt(structure.size)}, but the mask's piece cells and the height make {_fmt(size)} (x y z, the wall "
                "side to the north)",
            )
        )
    footprint = fit.footprint()
    min_x, min_z = min(x for x, _ in footprint), min(z for _, z in footprint)
    inside = {(x - min_x, z - min_z) for x, z in footprint}
    outside: list[Pos] = []
    for block in structure.blocks:
        x, _, z = block.pos
        where = f"block {_fmt(block.pos)}"
        if (x, z) not in inside:
            outside.append(block.pos)
            continue
        name = block.state.name
        reason = FORBIDDEN_BLOCKS.get(name) or next(
            (why for suffix, why in _FORBIDDEN_SUFFIXES.items() if name.endswith(suffix)), None
        )
        if reason is not None:
            problems.append((where, f"{name}: {reason}"))
        elif name in FLUID_BLOCKS or block.state.get("waterlogged") == "true":
            problems.append((where, f"{block.state}: no fluids in a piece (a water cauldron is fine)"))
        if isinstance((block.nbt or {}).get(LOOT_TABLE), str):
            problems.append(
                (
                    where,
                    "a container in a piece holds no loot table: the builder gives it the container loot (ADR 0041)",
                )
            )
    if outside:
        count = f" ({len(outside)} blocks)" if len(outside) > 1 else ""
        problems.append(
            (
                f"block {_fmt(outside[0])}",
                f"lies outside the piece's cells of the mask{count}: the template holds the piece cells only, not even "
                "air elsewhere",
            )
        )
    return problems


def _fmt(pos: Pos) -> str:
    return " ".join(str(c) for c in pos)


# --- placing pieces (the gallery's sample dressing) -------------------------------------------------------------------

_CLOCKWISE = {"north": "east", "east": "south", "south": "west", "west": "north"}
_MIRRORED = {"east": "west", "west": "east"}
_STAIR_MIRROR = {"outer_left": "outer_right", "outer_right": "outer_left"}


def transform_state(state: BlockState, turns: int, mirror: bool) -> BlockState:
    """A block state as vanilla places it with a FRONT_BACK mirror and ``turns`` clockwise turns
    (``BlockState#mirror``, then ``#rotate``): facings and side connections turn and flip, an axis turns, a skull's
    or banner's ``rotation`` turns and flips, a door's hinge and a stair's shape follow the mirror."""
    props = dict(state.properties)
    if mirror:
        facing = props.get("facing")
        if state.name.endswith("_stairs") and facing in ("east", "west"):
            props["facing"] = _MIRRORED[facing]
            props["shape"] = _STAIR_MIRROR.get(props.get("shape", ""), props.get("shape", "straight"))
        elif facing in _MIRRORED and not state.name.endswith("_stairs"):
            props["facing"] = _MIRRORED[facing]
            if "hinge" in props:
                props["hinge"] = "right" if props["hinge"] == "left" else "left"
        if "east" in props and "west" in props:
            props["east"], props["west"] = props["west"], props["east"]
        if props.get("rotation", "").isdigit():
            rotation = int(props["rotation"])
            relative = rotation - 16 if rotation > 8 else rotation
            props["rotation"] = str((16 - relative) % 16)
    for _ in range(turns % 4):
        if props.get("facing") in _CLOCKWISE:
            props["facing"] = _CLOCKWISE[props["facing"]]
        if props.get("axis") in ("x", "z"):
            props["axis"] = "z" if props["axis"] == "x" else "x"
        if all(side in props for side in _CLOCKWISE):
            props["north"], props["east"], props["south"], props["west"] = (
                props["west"],
                props["north"],
                props["east"],
                props["south"],
            )
        if props.get("rotation", "").isdigit():
            props["rotation"] = str((int(props["rotation"]) + 4) % 16)
    return BlockState(state.name, tuple(sorted(props.items())))


def place_piece(blocks: dict[Pos, StructureBlock], piece: Structure, spot: Spot, mirror: bool) -> None:
    """Put ``piece`` into a room's ``blocks`` at ``spot``, mirrored when the spot is and ``mirror`` allows it: each
    block turned and flipped as vanilla places a template, so the template's box starts at the spot's ``pos``. A
    piece that may not be mirrored takes a mirrored spot unmirrored, which the matcher allows only where mirroring
    leaves the mask as it is."""
    mirror = mirror and spot.mirror
    sx, _, sz = piece.size
    corners = [transform(x, z, spot.turns, mirror) for x in (0, sx - 1) for z in (0, sz - 1)]
    min_x, min_z = min(x for x, _ in corners), min(z for _, z in corners)
    x0, y0, z0 = spot.pos
    for block in piece.blocks:
        x, y, z = block.pos
        tx, tz = transform(x, z, spot.turns, mirror)
        pos = (x0 + tx - min_x, y0 + y, z0 + tz - min_z)
        blocks[pos] = StructureBlock(pos, transform_state(block.state, spot.turns, mirror), block.nbt)


def dressed(
    structure: Structure,
    found: Sequence[Pattern],
    pick: Callable[[Pattern], tuple[Structure, bool] | None],
) -> Structure:
    """The room with a piece in every pattern: ``pick`` names the piece's template and whether it may be mirrored
    (None leaves the pattern empty)."""
    blocks = {block.pos: block for block in structure.blocks}
    for pattern in found:
        chosen = pick(pattern)
        if chosen is None:
            continue
        piece, mirror = chosen
        for spot in pattern.spots:
            place_piece(blocks, piece, spot, mirror)
    return replace(structure, blocks=tuple(blocks[pos] for pos in sorted(blocks)))


# --- the mask report -------------------------------------------------------------------------------------------------


@dataclass(frozen=True)
class ReportRoom:
    """A room the mask report counts: its id, the group it is counted in, its template and its theme's blocks (for
    a generated room)."""

    id: str
    group: str
    structure: Structure
    theme_blocks: dict[str, str] | None = None


@dataclass
class ReportRow:
    """One mask's counts: rooms with a spot and spots, per group and in all, and the spots by wall material."""

    name: str
    label: str
    groups: dict[str, tuple[int, int]] = field(default_factory=dict)
    total: tuple[int, int] = (0, 0)
    materials: Counter[str] = field(default_factory=Counter)


def mask_report(
    rooms: Sequence[ReportRoom], masks: Sequence[NamedMask], *, light: bool = False
) -> list[ReportRow]:
    """How many spots each mask gets in the rooms, placed greedily one by one so they can all be filled at once (the
    design's mask report). ``light`` adds the light check; patterns are not formed."""
    rows = []
    views = [(room, RoomView(room.structure, room.theme_blocks)) for room in rooms]
    for named in masks:
        fit = Fit(named.mask, builds_walls=named.builds_walls)
        row = ReportRow(named.name, named.label)
        groups: dict[str, list[int]] = {}
        total = [0, 0]
        for room, view in views:
            found = match(view, [fit], patterns=False, light=light)
            counts = groups.setdefault(room.group, [0, 0])
            if found:
                counts[0] += 1
                counts[1] += len(found)
                total[0] += 1
                total[1] += len(found)
            for pattern in found:
                material = _wall_material(view, fit, pattern.spots[0])
                if material is not None:
                    row.materials[material] += 1
        row.groups = {group: (counts[0], counts[1]) for group, counts in groups.items()}
        row.total = (total[0], total[1])
        rows.append(row)
    return rows


def _wall_material(view: RoomView, fit: Fit, spot: Spot) -> str | None:
    """The block family most of a spot's wall cells are made of (``stone_brick``), or None for a free-standing one."""
    place = placement(fit, spot.turns, spot.mirror)
    x0, y0, z0 = spot.pos
    names = Counter(
        family(view.cells.name((x0 + dx, y0 + dy, z0 + dz)))
        for dx, dz, kind in place.cells
        if kind in (_WALL, _BEHIND)
        for dy in range(fit.height)
    )
    return min(names, key=lambda name: (-names[name], name)) if names else None
