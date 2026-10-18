"""Where a player can walk in a room, and where a mob could spawn in the dark (A4d, architecture 15.4).

The room generator lights its rooms with these rules, and validation checks every generated room with them:

- **Standing:** a player stands at a cell when the block below holds them (anything not passable and not a fluid)
  and the cell and the two above it are passable: three blocks of headroom, one more than a player needs, so
  nobody scrapes the ceiling when jumping.
- **Reachable:** from a standing cell a player walks to a standing cell beside it at most one block up or down
  (stairs, slabs and single steps; no parkour, DU-23). Every connector of a room must be reachable from every other.
  A connector's own cell is its doorway's foot, so it stands only when its doorway is clear.
- **Dark:** a mob could spawn at a cell with a block below it and two passable cells, so each such cell needs block
  light: the instances' dimension type lets monsters spawn only at block light 0
  (``monster_spawn_block_light_limit``). Light spreads from its sources one level less per step through passable
  cells and lanterns; any other block stops it (a stair or slab lets some light through in the game, so this
  errs on the dark side).

Python knows block names, not shapes, so passable is a short list of blocks without collision.
"""

from __future__ import annotations

from collections import deque
from collections.abc import Iterable

from realmforge_tools.rooms.check import FLUID_BLOCKS, STRUCTURE_BLOCK
from realmforge_tools.rooms.connectors import connectors
from realmforge_tools.rooms.structure import AIR, JIGSAW, STRUCTURE_VOID, Pos, Structure

HEADROOM = 3
# Blocks a player walks through. A jigsaw becomes its final state (air in our rooms) and a data marker is never
# placed; structure void keeps whatever the plot holds, which is air in a dungeon's plot.
PASSABLE = frozenset(
    {
        AIR,
        "minecraft:cave_air",
        "minecraft:void_air",
        STRUCTURE_VOID,
        JIGSAW,
        STRUCTURE_BLOCK,
        "minecraft:torch",
        "minecraft:wall_torch",
        "minecraft:soul_torch",
        "minecraft:soul_wall_torch",
        "minecraft:copper_torch",
        "minecraft:copper_wall_torch",
        "minecraft:vine",
        "minecraft:cobweb",
        "minecraft:light",
    }
)
# Block light sources and their level.
LIGHT_SOURCES = {
    "minecraft:torch": 14,
    "minecraft:wall_torch": 14,
    "minecraft:copper_torch": 14,
    "minecraft:copper_wall_torch": 14,
    "minecraft:soul_torch": 10,
    "minecraft:soul_wall_torch": 10,
    "minecraft:lantern": 15,
    "minecraft:soul_lantern": 10,
    "minecraft:copper_lantern": 15,
    "minecraft:glowstone": 15,
    "minecraft:sea_lantern": 15,
    "minecraft:shroomlight": 15,
    "minecraft:jack_o_lantern": 15,
}
# Blocks light passes through besides the passable ones.
_CLEAR = frozenset({"minecraft:lantern", "minecraft:soul_lantern", "minecraft:copper_lantern"})
_SIDES = ((1, 0), (-1, 0), (0, 1), (0, -1))


class Cells:
    """A room's blocks by position, with the box's size. A position outside the box is outside the room."""

    def __init__(self, size: Pos, names: dict[Pos, str]):
        self.size = size
        self.names = names

    @classmethod
    def of(cls, structure: Structure) -> Cells:
        return cls(structure.size, {block.pos: block.state.name for block in structure.blocks})

    def inside(self, pos: Pos) -> bool:
        return all(0 <= c < s for c, s in zip(pos, self.size, strict=True))

    def name(self, pos: Pos) -> str:
        """The block at ``pos``; a cell the template leaves out is structure void."""
        return self.names.get(pos, STRUCTURE_VOID)

    def passable(self, pos: Pos) -> bool:
        return self.inside(pos) and self.name(pos) in PASSABLE

    def holds(self, pos: Pos) -> bool:
        """Whether the block at ``pos`` holds a player or a mob standing on it."""
        if not self.inside(pos):
            return False
        name = self.name(pos)
        return name not in PASSABLE and name not in FLUID_BLOCKS

    def standing(self, pos: Pos) -> bool:
        x, y, z = pos
        return self.holds((x, y - 1, z)) and all(self.passable((x, y + dy, z)) for dy in range(HEADROOM))

    def spawnable(self, pos: Pos) -> bool:
        x, y, z = pos
        return self.holds((x, y - 1, z)) and self.passable(pos) and self.passable((x, y + 1, z))

    def positions(self) -> Iterable[Pos]:
        sx, sy, sz = self.size
        return ((x, y, z) for y in range(sy) for x in range(sx) for z in range(sz))


def walkable(cells: Cells) -> set[Pos]:
    """Every standing cell."""
    return {pos for pos in cells.positions() if cells.standing(pos)}


def reach(cells: Cells, start: Pos, stands: set[Pos]) -> set[Pos]:
    """The standing cells a player at ``start`` can walk to."""
    seen = {start}
    todo = deque([start])
    while todo:
        x, y, z = todo.popleft()
        for dx, dz in _SIDES:
            for dy in (0, 1, -1):
                step = (x + dx, y + dy, z + dz)
                if step in stands and step not in seen:
                    seen.add(step)
                    todo.append(step)
    return seen


def light_levels(cells: Cells, sources: Iterable[tuple[Pos, int]] | None = None) -> dict[Pos, int]:
    """Block light per cell: the brightest source's level less one per step through passable cells and lanterns.
    ``sources`` defaults to the room's own light blocks."""
    if sources is None:
        sources = [(pos, LIGHT_SOURCES[name]) for pos, name in cells.names.items() if name in LIGHT_SOURCES]
    levels: dict[Pos, int] = {}
    for start, level in sources:
        _spread(cells, start, level, levels)
    return levels


def _spread(cells: Cells, start: Pos, level: int, levels: dict[Pos, int]) -> None:
    if levels.get(start, 0) >= level:
        return
    levels[start] = level
    todo = deque([start])
    while todo:
        pos = todo.popleft()
        next_level = levels[pos] - 1
        if next_level <= 0:
            continue
        x, y, z = pos
        for step in (
            (x + 1, y, z),
            (x - 1, y, z),
            (x, y + 1, z),
            (x, y - 1, z),
            (x, y, z + 1),
            (x, y, z - 1),
        ):
            if not cells.inside(step) or levels.get(step, 0) >= next_level:
                continue
            name = cells.name(step)
            if name not in PASSABLE and name not in _CLEAR:
                continue
            levels[step] = next_level
            todo.append(step)


def dark_cells(cells: Cells, levels: dict[Pos, int] | None = None) -> list[Pos]:
    """The cells where a mob could spawn without block light, in position order."""
    levels = light_levels(cells) if levels is None else levels
    return sorted(pos for pos in cells.positions() if cells.spawnable(pos) and levels.get(pos, 0) == 0)


def walk_problems(structure: Structure) -> list[tuple[str, str]]:
    """What keeps a generated room from being played: a connector whose doorway lacks headroom, connectors a
    player cannot walk between, and dark cells where mobs could spawn. As (location, message) pairs."""
    cells = Cells.of(structure)
    stands = walkable(cells)
    problems: list[tuple[str, str]] = []
    doors = []
    for connector in connectors(structure):
        where = f"connector {_fmt(connector.pos)}"
        if connector.vertical:
            continue  # a shaft: the room above or below continues it
        if connector.pos not in stands:
            problems.append((where, f"its doorway has no {HEADROOM} blocks of headroom over a floor"))
        else:
            doors.append(connector.pos)
    if doors:
        reached = reach(cells, doors[0], stands)
        for door in doors[1:]:
            if door not in reached:
                problems.append(
                    (
                        f"connector {_fmt(door)}",
                        f"cannot be walked to from the connector at {_fmt(doors[0])} (steps of one block at "
                        f"most, {HEADROOM} blocks of headroom)",
                    )
                )
    dark = dark_cells(cells)
    if dark:
        count = f" ({len(dark)} cells)" if len(dark) > 1 else ""
        problems.append((f"at {_fmt(dark[0])}", f"a mob could spawn here in the dark{count}: add light"))
    return problems


def _fmt(pos: Pos) -> str:
    return " ".join(str(c) for c in pos)
