"""The sides a room may open (RLM-412): per side of the template (as saved, before any turn), the blocks a run turns
to air there, such as the block in front of a spawner the room hides. The game opens one of them at random among the
sides that are not the room's way in (``RoomOpenings`` and ``DungeonPlan`` in the core mod), so a player meets the
spawner from the left, the right or across the room, never straight from the doorway (Matěj, 2026-10-28).

An opening clears blocks that are there, and only plain ones: never a connector, which the planner joins rooms by,
nor a spawner, a chest or a barrel, which it would remove instead of revealing, or which the chance tables draw.
"""

from __future__ import annotations

import hashlib
import json
from collections.abc import Mapping, Sequence

from realmforge_tools.rooms.structure import AIR, JIGSAW, SPAWNER, Structure

# The sides an opening may face, in the order the game draws from.
SIDES = ("north", "east", "south", "west")
# Blocks an opening must leave: a connector, the spawner it reveals, and the containers the chance tables draw.
_KEPT = (JIGSAW, SPAWNER, "minecraft:chest", "minecraft:trapped_chest", "minecraft:barrel")


def digest(openings: Mapping[str, Sequence[Sequence[int]]]) -> str:
    """A short digest of the openings, for the cache kind of their problems."""
    text = json.dumps({side: [list(pos) for pos in openings[side]] for side in sorted(openings)})
    return hashlib.sha256(text.encode()).hexdigest()[:16]


def problems(structure: Structure, openings: Mapping[str, Sequence[Sequence[int]]]) -> list[tuple[str, str]]:
    """Each position of an opening that lies outside the room, holds nothing to clear (air, or no block), or holds a
    block an opening must leave; as (where, what) pairs."""
    blocks = {block.pos: block.state.name for block in structure.blocks}
    found = []
    for side in SIDES:
        for index, written in enumerate(openings.get(side, ())):
            pos = (written[0], written[1], written[2])
            where = f"openings.{side}[{index}]"
            name = blocks.get(pos)
            if not all(0 <= c < s for c, s in zip(pos, structure.size, strict=True)):
                found.append(
                    (where, f"{list(pos)} lies outside the room, whose size is {list(structure.size)}")
                )
            elif name is None or name == AIR:
                found.append((where, f"{list(pos)} holds nothing to clear"))
            elif name in _KEPT:
                found.append((where, f"{list(pos)} holds {name}, which an opening must leave"))
    return found
