"""Requirements: what a player needs to enter a dimension, and later a dungeon, an item or a talent (architecture 5.3).

Each requirement is an entry of the type registry ``realmforge:requirement_type``, selected by its ``type``. The
game knows the same types: ``core/HasAdvancement``, ``character/LevelAtLeast`` and ``progression/ChapterCompleted``.
"""

from __future__ import annotations

from typing import Annotated, Literal

from pydantic import Field

from realmforge_tools.schema.common import ResourceId, StrictModel

# The game reads the level as a Java int; progression/LevelCaps.MAX_LEVEL bounds every level.
MAX_LEVEL = 1000


class HasAdvancementDef(StrictModel):
    """The player has earned an advancement: a realm's unlock, ``realmforge:realm/<realm>``, for example."""

    type: Literal["realmforge:has_advancement"]
    advancement: ResourceId = Field(description="The advancement, e.g. realmforge:realm/stone.")


class LevelAtLeastDef(StrictModel):
    """The player's character level is at least ``level``."""

    type: Literal["realmforge:level_at_least"]
    level: int = Field(ge=1, le=MAX_LEVEL, description="The lowest character level that passes.")


class ChapterCompletedDef(StrictModel):
    """The player has completed a chapter: they have its advancement ``realmforge:chapter/<id>``."""

    type: Literal["realmforge:chapter_completed"]
    chapter: ResourceId = Field(description="The chapter, e.g. realmforge:chapter_1 (content/chapters/).")


RequirementDef = Annotated[
    HasAdvancementDef | LevelAtLeastDef | ChapterCompletedDef, Field(discriminator="type")
]
