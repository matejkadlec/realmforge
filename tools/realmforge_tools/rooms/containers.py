"""The chests and barrels of a room that a dungeon's chance tables draw from (RLM-365), and the block each leaves
behind when the draw drops it.

A candidate is a single chest or a barrel: a trapped chest can be part of a trap's redstone, and half of a double
chest cannot go alone, so both always stay. A dropped container becomes the block around it ("the surrounding floor
or wall block", Matěj): one set into a floor or a wall (four or more full blocks among its six neighbours) or one
another container stands on becomes the full block most of its neighbours are, so neither a hole nor a floating chest
is left; any other becomes air.
"""

from __future__ import annotations

from collections import Counter
from dataclasses import dataclass
from typing import Literal

from realmforge_tools.rooms.structure import AIR, BlockState, Pos, Structure

CHEST = "minecraft:chest"
BARREL = "minecraft:barrel"
Kind = Literal["chest", "barrel"]
KINDS: tuple[Kind, ...] = ("chest", "barrel")
# A neighbour counts as full unless its name holds one of these (the dressing's own list, rooms/dress.py).
_NOT_FULL = (
    "slab", "stairs", "fence", "_wall", "pane", "bars", "carpet", "button", "plate", "rail", "torch", "lantern",
    "chain", "door", "sign", "banner", "leaves", "grass", "flower", "vine", "web", "snow", "water", "lava", "chest",
    "barrel", "spawner", "jigsaw", "glass", "candle", "pot", "skull", "head", "air", "structure",
)  # fmt: skip
# Below first: a container in a floor takes the floor's block when its neighbours tie.
_SIDES: tuple[Pos, ...] = ((0, -1, 0), (0, 0, -1), (0, 0, 1), (-1, 0, 0), (1, 0, 0), (0, 1, 0))
# Full neighbours from which a container counts as set into a floor or a wall.
_EMBEDDED = 4


@dataclass(frozen=True)
class Candidate:
    """A container the draw may drop: where it is in the template, its kind, and the block it leaves behind."""

    pos: Pos
    kind: Kind
    instead: BlockState


def candidates(structure: Structure) -> list[Candidate]:
    """The room's single chests and its barrels, in template order (by y, x, z), each with the block it leaves."""
    blocks = {block.pos: block.state for block in structure.blocks}
    found = []
    for pos, state in sorted(blocks.items(), key=lambda item: (item[0][1], item[0][0], item[0][2])):
        kind = _kind(state)
        if kind is not None:
            found.append(Candidate(pos, kind, _instead(pos, blocks)))
    return found


def count(structure: Structure, kind: Kind) -> int:
    """How many candidates of ``kind`` the room holds: the most a chance table may ask it for."""
    return sum(candidate.kind == kind for candidate in candidates(structure))


def _kind(state: BlockState) -> Kind | None:
    if state.name == BARREL:
        return "barrel"
    if state.name == CHEST and state.get("type") in (None, "single"):
        return "chest"
    return None


def _instead(pos: Pos, blocks: dict[Pos, BlockState]) -> BlockState:
    x, y, z = pos
    full = []
    for dx, dy, dz in _SIDES:
        state = blocks.get((x + dx, y + dy, z + dz))
        if state is not None and not any(part in state.name for part in _NOT_FULL):
            full.append(state)
    above = blocks.get((x, y + 1, z))
    carries = above is not None and _kind(above) is not None
    if full and (len(full) >= _EMBEDDED or carries):
        return Counter(full).most_common(1)[0][0]
    return BlockState(AIR)
