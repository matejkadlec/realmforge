"""Chapters: ``content/chapters/<id>.yaml``, the datapack registry ``realmforge:chapter`` (architecture 5.2, 10.2, 16).

A chapter of the story sets the level cap. A player's cap is the ``completed_level_cap`` of the chapters they have
completed, the highest one, or, before they complete any, the first chapter's ``level_cap``. A chapter counts as
completed once the player has its advancement ``realmforge:chapter/<id>``, which the chapter's last quest grants
(Q1). A chapter's cap is its last level (Matěj, 2026-10-15, superseding D2's 10 then 20): 10 during chapter 1, 18
after it, 30 after chapter 2 (architecture 10.2).
"""

from __future__ import annotations

from pydantic import Field, model_validator

from realmforge_tools.schema.common import ContentId, LocalizedText, StrictModel

# The game reads these as Java ints (progression/Chapter); progression/LevelCaps.MAX_LEVEL bounds every level.
MAX_LEVEL = 1000


def advancement_id(chapter_id: str) -> str:
    """The advancement that marks a chapter completed: ``realmforge:chapter/<id>`` (architecture 16)."""
    return f"chapter/{chapter_id}"


class ChapterDef(StrictModel):
    """A chapter of the story: its place in the chain and the level caps it sets."""

    id: ContentId
    name: LocalizedText = Field(description="The chapter's name, shown when a player completes it.")
    order: int = Field(
        ge=1, le=MAX_LEVEL, description="Position in the story: 1 for the first chapter, then 2, 3."
    )
    level_cap: int = Field(
        ge=1,
        le=MAX_LEVEL,
        description=(
            "The level cap while this is the player's current chapter: every chapter before it completed, this "
            "one not. The first chapter's is the cap of a new player."
        ),
    )
    completed_level_cap: int = Field(
        ge=1,
        le=MAX_LEVEL,
        description="The level cap once the player completes the chapter; above level_cap.",
    )

    @model_validator(mode="after")
    def _cap_rises(self) -> ChapterDef:
        if self.completed_level_cap <= self.level_cap:
            raise ValueError(
                f"completed_level_cap {self.completed_level_cap} must be above level_cap {self.level_cap}: "
                "completing a chapter raises the cap"
            )
        return self


def level_cap(chapters: list[ChapterDef], completed: set[str]) -> int:
    """The level cap of a player who completed the chapters with the ids in ``completed``: the highest
    ``completed_level_cap`` among them, never below the first chapter's ``level_cap``. The game's
    ``progression/LevelCaps#cap`` must agree (checked by a GameTest)."""
    ordered = sorted(chapters, key=lambda chapter: chapter.order)
    if not ordered:
        return MAX_LEVEL
    cap = ordered[0].level_cap
    for chapter in ordered:
        if chapter.id in completed:
            cap = max(cap, chapter.completed_level_cap)
    return cap
