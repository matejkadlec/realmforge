"""Dungeon types: ``content/dungeons/<id>.yaml``, the datapack registry ``realmforge:dungeon_type`` (architecture 5.2,
15.2, 15.3).

A dungeon type names the room pool it is built from and the rules of its layout. The generated entry carries the
pool's rooms resolved (template, size, connectors, role, weight; ``generate/dungeons.py``), so the game's planner
needs no template to plan a layout.
"""

from __future__ import annotations

from typing import Annotated, Literal

from pydantic import AfterValidator, Field

from realmforge_tools.schema.common import ContentId, LocalizedText, ResourceId, StrictModel

# The role a room plays in a layout: exactly one of these is among a pooled room's tags (content/README.md).
ROOM_ROLES = ("entrance", "hallway", "combat", "stairs", "treasure", "boss")
# Rooms a player passes through: they make up the main path and the branches.
PATH_ROLES = ("hallway", "combat", "stairs")
# Dead ends that end a branch or close a connector nothing else takes: neither the entrance nor the boss's room.
DEAD_END_ROLES = ("hallway", "combat", "stairs", "treasure")
# The rooms that can end the main path: the boss's lair or a treasury (architecture 15.2).
ENDING_ROLES = ("boss", "treasure")


def room_role(tags: list[str]) -> str | None:
    """A room's role in a layout: its one tag that is a role, or None when it has none or several."""
    roles = [tag for tag in tags if tag in ROOM_ROLES]
    return roles[0] if len(roles) == 1 else None


def _ordered(bounds: list[int]) -> list[int]:
    if bounds[0] > bounds[1]:
        raise ValueError(f"the lower bound {bounds[0]} is above the upper bound {bounds[1]}")
    return bounds


# [min, max] with both ends included; the game reads it as an InclusiveRange.
MainPathRange = Annotated[
    list[Annotated[int, Field(ge=1, le=32)]],
    Field(
        min_length=2,
        max_length=2,
        description="[min, max] rooms between the entrance and the ending (1 to 32).",
    ),
    AfterValidator(_ordered),
]
BranchRange = Annotated[
    list[Annotated[int, Field(ge=0, le=16)]],
    Field(min_length=2, max_length=2, description="[min, max] side branches off the main path (0 to 16)."),
    AfterValidator(_ordered),
]
BranchRoomRange = Annotated[
    list[Annotated[int, Field(ge=1, le=8)]],
    Field(
        min_length=2,
        max_length=2,
        description="[min, max] rooms in each branch, its dead end included (1 to 8).",
    ),
    AfterValidator(_ordered),
]


class LayoutDef(StrictModel):
    """How the planner lays out a dungeon of this type (architecture 15.3): the entrance, a main path of rooms a
    player passes through, the room that ends it, and side branches off the main path, each ending in a dead end.
    The planner closes every connector left over with a dead end, so a layout has more rooms than these counts."""

    main_path: MainPathRange
    branches: BranchRange
    branch_rooms: BranchRoomRange
    ending: Literal["boss", "treasure"] = Field(
        description="The role of the room that ends the main path: the boss's lair or a treasury."
    )


class DungeonTypeDef(StrictModel):
    """A kind of dungeon: the room pool it is built from and the rules of its layout. Encounters, loot, its level
    range, its theme and its key come with the tasks that use them."""

    id: ContentId
    name: LocalizedText = Field(description="The dungeon's name, a label (Title Case).")
    pool: ResourceId = Field(
        description="The room pool it is built from: the rooms whose sidecar lists this id in its pools."
    )
    layout: LayoutDef
