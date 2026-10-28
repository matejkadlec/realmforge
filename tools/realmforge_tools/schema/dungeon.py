"""Dungeon types: ``content/dungeons/<id>.yaml``, the datapack registry ``realmforge:dungeon_type`` (architecture 5.2,
15.2, 15.3).

A dungeon type names the room pool it is built from and the rules of its layout. The generated entry carries the
pool's rooms resolved (template, size, connectors, role, weight; ``generate/dungeons.py``), so the game's planner
needs no template to plan a layout.
"""

from __future__ import annotations

from typing import Annotated, Literal

from pydantic import AfterValidator, Field

from realmforge_tools.schema.chapter import MAX_LEVEL
from realmforge_tools.schema.common import ContentId, LocalizedText, ResourceId, StrictModel
from realmforge_tools.schema.room import ContainerTables

# The role a room plays in a layout: exactly one of these is among a pooled room's tags (content/README.md).
ROOM_ROLES = ("entrance", "hallway", "combat", "stairs", "treasure", "boss")
# Rooms a player passes through: they make up the main path and the branches.
PATH_ROLES = ("hallway", "combat", "stairs")
# Dead ends that end a branch or close a connector nothing else takes: neither the entrance nor the boss's room.
DEAD_END_ROLES = ("hallway", "combat", "stairs", "treasure")
# The rooms that can end the main path: the boss's lair or a treasury (architecture 15.2).
ENDING_ROLES = ("boss", "treasure")
# The roles of the rooms a layout's pacing calls corridors; a combat room is a fight with a spawner, else a feature
# (RLM-364, the game's Pace).
CORRIDOR_ROLES = ("hallway", "stairs")
# The key of a dungeon type's container tables for every role without its own (RLM-365).
DEFAULT_CONTAINERS = "default"
ContainerRole = Literal["entrance", "hallway", "combat", "stairs", "treasure", "boss", "default"]
# The role of the plug rf gen makes from a dungeon type's walls: it closes a doorway, and is no room (RLM-364).
WALL_ROLE = "wall"
# The doorway a wall closes: three blocks wide and high, its connector at the middle of the bottom row, like every
# door of the pools so far (Stonevaults' and the room generator's, architecture 15.4).
WALL_WIDTH = 3
WALL_HEIGHT = 3


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
    list[Annotated[int, Field(ge=1, le=96)]],
    Field(
        min_length=2,
        max_length=2,
        description="[min, max] rooms between the entrance and the ending (1 to 96).",
    ),
    AfterValidator(_ordered),
]
BranchRange = Annotated[
    list[Annotated[int, Field(ge=0, le=16)]],
    Field(min_length=2, max_length=2, description="[min, max] side branches off the main path (0 to 16)."),
    AfterValidator(_ordered),
]
CorridorRunRange = Annotated[
    list[Annotated[int, Field(ge=1, le=8)]],
    Field(
        min_length=2,
        max_length=2,
        description="[min, max] corridors in a row on the main path before a feature or a fight (1 to 8), drawn "
        "binomially (RLM-364).",
    ),
    AfterValidator(_ordered),
]
FightGapRange = Annotated[
    list[Annotated[int, Field(ge=1, le=16)]],
    Field(
        min_length=2,
        max_length=2,
        description="[min, max] rooms, corridors and features, between two fights on the main path (1 to 16), "
        "drawn binomially: [2, 4] gives 2 a quarter of the time, 3 half, 4 a quarter (RLM-364).",
    ),
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


class WallsDef(StrictModel):
    """A plain wall that closes a doorway nothing else takes, one block behind it: rf gen builds it as a template of
    3 x 3 blocks with one connector and adds it to the dungeon type's rooms with the role ``wall``."""

    block: ResourceId = Field(description="The block it is built of, e.g. minecraft:stone_bricks.")
    weight: int = Field(
        ge=1,
        le=150,
        description="Its weight among the dead ends that may close a doorway, like a room's weight in its pool.",
    )


class PacingDef(StrictModel):
    """The rhythm of a layout (RLM-364, architecture 15.3): on the main path, a fight (a combat room with a spawner)
    once a gap drawn from ``fights`` lies behind the last one, else a feature (a combat room without one) after a run
    of corridors drawn from ``corridors``; the branches are corridors up to a treasure room."""

    corridors: CorridorRunRange
    fights: FightGapRange
    repeat: int = Field(
        default=0,
        ge=0,
        le=64,
        description="Rooms of distance from the entrance the planner tries to keep between two copies of a room that "
        "is no corridor, and between two locks (rooms with an entry); 0: copies may be neighbours.",
    )


class LayoutDef(StrictModel):
    """How the planner lays out a dungeon of this type (architecture 15.3): the entrance, a main path of rooms a
    player passes through, the room that ends it, and side branches off the main path, each ending in a dead end.
    The planner closes every connector left over with a dead end or a wall, so a layout has more rooms than these
    counts."""

    main_path: MainPathRange
    branches: BranchRange
    branch_rooms: BranchRoomRange
    ending: Literal["boss", "treasure"] = Field(
        description="The role of the room that ends the main path: the boss's lair or a treasury."
    )
    pacing: PacingDef | None = Field(
        default=None,
        description="If set, the rhythm of corridors, features and fights along the main path, and the treasure at "
        "the end of each branch (RLM-364). Unset: any room that fits a slot may take it.",
    )
    walls: WallsDef | None = Field(
        default=None,
        description="If set, the planner may close a leftover doorway with a wall instead of a dead end (RLM-364).",
    )


class DungeonTypeDef(StrictModel):
    """A kind of dungeon: the room pool it is built from, the rules of its layout, the key that opens a portal to it,
    how long that portal stays open (W4d) and its base level (RLM-459). Encounters, loot and its theme come with the
    tasks that use them."""

    id: ContentId
    name: LocalizedText = Field(description="The dungeon's name, a label (Title Case).")
    pool: ResourceId = Field(
        description="The room pool it is built from: the rooms whose sidecar lists this id in its pools."
    )
    layout: LayoutDef
    key: ResourceId = Field(
        description=(
            "The item that opens a dungeon portal to it, used up each time: its key. One key per dungeon type."
        )
    )
    open_minutes: int = Field(
        ge=1,
        le=240,
        description="How long a portal to it stays open, in minutes; then everyone inside is sent back (W4d).",
    )
    level: int = Field(
        ge=1,
        le=MAX_LEVEL,
        description=(
            "Its base level: the level of its boss, where the campaign places it (its chapter and its order in the "
            "chapter); a type without a boss names the level its end stands for. A run's level is this base raised "
            "to the highest level of its party when it opens, and holds for the run: bosses (#c:bosses) are at it, "
            "minibosses (#realmforge:minibosses) one below, every other mob two or three below (RLM-240, RLM-459)."
        ),
    )
    containers: dict[ContainerRole, ContainerTables] = Field(
        default_factory=dict,
        description=(
            "How many of a room's chests and barrels its dungeons place, by the room's role, and under default for "
            "every role without its own (a kind a role leaves out follows default; RLM-365). A room's sidecar may "
            "set its own. Without a table, a room keeps all of its containers."
        ),
    )
