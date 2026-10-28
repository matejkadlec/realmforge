"""Rooms: ``content/rooms/<path>.nbt`` (a structure template) with its sidecar ``<path>.yaml`` (architecture 15.4).

Rooms are the second exception to YAML-only content, after worldgen JSON: the template is the room, and the
sidecar says how dungeons use it. A room's id is its path below ``content/rooms/`` without the suffix
(``third_party/stonevaults/hall_a``); the jar holds it as the structure ``realmforge:rooms/<id>``. Imported rooms
live in ``third_party/<source>/`` with the source's ``LICENSE`` and ``NOTICE``, and an LGPL-3.0 source also with
``COPYING``, the GPL-3.0 text ([ADR 0028](../../../docs/decisions/0028-dungeon-rooms-generated-or-imported.md)).
"""

from __future__ import annotations

from typing import Annotated, Literal

from pydantic import AfterValidator, Field, NonNegativeInt, RootModel, StringConstraints

from realmforge_tools.schema.common import StrictModel, UniqueResourceIds

ROOMS_DIR = "rooms"
# A file in any folder of content/rooms/ that records the gallery's keep and reject decisions (rooms/gallery.py).
REVIEW_FILE = "review.yaml"
# Files a third-party source's folder carries besides its rooms (ADR 0028).
LICENSE_FILES = ("LICENSE", "NOTICE")
# The GPL-3.0 text an LGPL-3.0 source carries next to its LICENSE, as LGPL-3.0 requires (ADR 0028, point 4).
GPL_FILE = "COPYING"
# A LICENSE that starts the LGPL-3.0 text, and a COPYING that starts the GPL-3.0 text.
LGPL_TITLE = "GNU LESSER GENERAL PUBLIC LICENSE"
GPL_TITLE = "GNU GENERAL PUBLIC LICENSE"

# One path segment of a room id: lowercase snake_case, like our other ids.
ROOM_SEGMENT = r"[a-z0-9][a-z0-9_]*"

Tag = Annotated[str, StringConstraints(pattern=r"^[a-z][a-z0-9_]*$")]

# The most chests or barrels of one kind a chance table may ask a room for (RLM-365).
MAX_CONTAINERS = 64


def _whole(table: dict[int, int]) -> dict[int, int]:
    if sum(table.values()) != 100:
        raise ValueError(f"the chances add up to {sum(table.values())} %, not 100 %")
    return dict(sorted(table.items()))


ChanceTable = Annotated[
    dict[Annotated[int, Field(ge=0, le=MAX_CONTAINERS)], Annotated[int, Field(ge=1, le=100)]],
    Field(
        min_length=1,
        description="The chance in percent of each count (0 to 64), adding up to 100, e.g. {1: 75, 2: 20, 3: 5}.",
    ),
    AfterValidator(_whole),
]


TemplatePos = Annotated[list[NonNegativeInt], Field(min_length=3, max_length=3)]
Side = Literal["north", "east", "south", "west"]


def _sides(openings: dict[Side, list[TemplatePos]]) -> dict[Side, list[TemplatePos]]:
    if len(openings) == 1:
        raise ValueError(
            "a run opens a side that is not the room's way in, so a room lists two sides or more"
        )
    return openings


Openings = Annotated[
    dict[Side, Annotated[list[TemplatePos], Field(min_length=1)]],
    AfterValidator(_sides),
]


class ContainerTables(StrictModel):
    """How many of a room's chests and barrels a dungeon places (RLM-365): for each kind, the chance of each count.
    A room's candidates are its template's single chests and its barrels (trapped chests and double chests always
    stay); the builder keeps that many of them, at most as many as the room holds, and the rest become the block
    around them. A kind without a table keeps all of its candidates."""

    chests: ChanceTable | None = Field(default=None, description="How many of its single chests stay.")
    barrels: ChanceTable | None = Field(default=None, description="How many of its barrels stay.")


class RoomSource(StrictModel):
    """Where an imported room came from, written by ``rf rooms import``."""

    file: str = Field(description="The source file's name.")
    format: Literal["nbt", "schem", "litematic"] = Field(description="The source file's format.")
    data_version: int = Field(
        description="The data version the source was saved with (for a file that states none, the import rules' "
        "data_version); the template keeps it, and the game upgrades the room from it when it loads."
    )
    ours: bool = Field(
        default=False,
        description="True for a room we built ourselves (exported from a dev world, say). Every other imported "
        "room is third-party: it lives in third_party/<source>/ with the source's LICENSE and NOTICE.",
    )


class RoomDef(StrictModel):
    """How dungeons use a room. The template next to it holds the blocks."""

    pools: UniqueResourceIds = Field(
        default_factory=list,
        description="The room pools the room joins (e.g. realmforge:stone_vault): a dungeon type is built from the "
        "rooms of one pool. Empty for a room that is not used yet, such as a fresh import.",
    )
    tags: list[Tag] = Field(
        default_factory=list,
        description="Slot types and other labels: one role (entrance, hallway, combat, stairs, treasure, boss), which a "
        "room in a pool must have, the shape (dead_end, straight, curved, t_junction, crossing), vertical, ...",
    )
    weight: int = Field(default=1, ge=1, le=150, description="Weight in its pools (vanilla allows 1 to 150).")
    max_per_layout: int | None = Field(
        default=None,
        ge=1,
        le=16,
        description="The most times one dungeon layout holds this room; no limit when unset (RLM-256).",
    )
    near_start: bool = Field(
        default=False,
        description="True: the planner places this room only next to the entrance or the first room of the main "
        "path, at the dungeon's start (RLM-256).",
    )
    explosion_breaks: UniqueResourceIds = Field(
        default_factory=list,
        description="Blocks an explosion inside this room may break in a dungeon instance, where explosions "
        "otherwise break nothing: what a trap's TNT is meant to blow open (RLM-257). Every other block of the room "
        "stays.",
    )
    entry: Literal["north", "east", "south", "west", "up", "down"] | None = Field(
        default=None,
        description="The side of the template (as saved, before any turn) whose one connector is the room's way in: "
        "a lock's mechanism side. The planner attaches the room to the room before it only through that connector, "
        "and no loop leads into the room from another side (RLM-362). Unset: any connector.",
    )
    containers: ContainerTables | None = Field(
        default=None,
        description="How many of its chests and barrels a dungeon places, where the dungeon type's table for its "
        "role does not fit it (RLM-365). A kind it leaves out follows the dungeon type.",
    )
    openings: Openings = Field(
        default_factory=dict,
        description="Sides of the template (as saved, before any turn) that a run may open, each with the blocks "
        "it turns to air there, e.g. {north: [[6, 1, 5]]}: the block in front of a spawner the room hides. A run "
        "opens one side at random among those that are not the side it enters the room from (RLM-412), so list "
        "two sides or more. Positions hold plain blocks: no connector, spawner, chest or barrel.",
    )
    source: RoomSource | None = Field(default=None, description="Set for an imported room.")


class ReviewFile(RootModel[dict[str, Literal["keep", "reject"]]]):
    """``review.yaml``: the gallery decision per room, keyed by the room's path below the folder that holds the
    file, without the suffix (``hall_a``, ``stone/corridor_3``)."""
