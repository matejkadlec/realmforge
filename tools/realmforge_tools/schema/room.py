"""Rooms: ``content/rooms/<path>.nbt`` (a structure template) with its sidecar ``<path>.yaml`` (architecture 15.4).

Rooms are the second exception to YAML-only content, after worldgen JSON: the template is the room, and the
sidecar says how dungeons use it. A room's id is its path below ``content/rooms/`` without the suffix
(``third_party/stonevaults/hall_a``); the jar holds it as the structure ``realmforge:rooms/<id>``. Imported rooms
live in ``third_party/<source>/`` with the source's ``LICENSE`` and ``NOTICE``
([ADR 0028](../../../docs/decisions/0028-dungeon-rooms-generated-or-imported.md)).
"""

from __future__ import annotations

from typing import Annotated, Literal

from pydantic import Field, RootModel, StringConstraints

from realmforge_tools.schema.common import StrictModel, UniqueResourceIds

ROOMS_DIR = "rooms"
# A file in any folder of content/rooms/ that records the gallery's keep and reject decisions (rooms/gallery.py).
REVIEW_FILE = "review.yaml"
# Files a third-party source's folder carries besides its rooms (ADR 0028).
LICENSE_FILES = ("LICENSE", "NOTICE")

# One path segment of a room id: lowercase snake_case, like our other ids.
ROOM_SEGMENT = r"[a-z0-9][a-z0-9_]*"

Tag = Annotated[str, StringConstraints(pattern=r"^[a-z][a-z0-9_]*$")]


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
        description="The template pools the room joins (e.g. realmforge:rooms/hallway). Empty for a room that is "
        "not used yet, such as a fresh import.",
    )
    tags: list[Tag] = Field(
        default_factory=list,
        description="Slot types and other labels (entrance, hallway, crossing, dead_end, combat, treasure, ...).",
    )
    weight: int = Field(default=1, ge=1, le=150, description="Weight in its pools (vanilla allows 1 to 150).")
    source: RoomSource | None = Field(default=None, description="Set for an imported room.")


class ReviewFile(RootModel[dict[str, Literal["keep", "reject"]]]):
    """``review.yaml``: the gallery decision per room, keyed by the room's path below the folder that holds the
    file, without the suffix (``hall_a``, ``stone/corridor_3``)."""
