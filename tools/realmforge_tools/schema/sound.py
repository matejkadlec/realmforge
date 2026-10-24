"""Sounds: ``content/sounds/<subject>.yaml``, our own sound events (A3, ADR 0007, architecture 6.2).

Each event is a stable id, ``realmforge:<category>.<subject>.<name>`` (``realmforge:entity.hewn_sentinel.hurt``), that
mobs, spells and blocks play. For now it plays vanilla sounds with their own pitch and volume, by reference in
``assets/realmforge/sounds.json`` (never copies of vanilla's files); new sounds (ElevenLabs, Freesound CC0, RLM-126)
later replace what an event plays without changing its id. Every event has a subtitle.
"""

from __future__ import annotations

from typing import Annotated, Literal

from pydantic import Field, StringConstraints

from realmforge_tools.project import MOD_ID
from realmforge_tools.schema.common import ContentId, LocalizedText, ResourceId, StrictModel

SoundName = Annotated[str, StringConstraints(pattern=r"^[a-z][a-z0-9_]*$", max_length=64)]
# The first word of an event id, after vanilla's own (entity.zombie.hurt, block.stone.break, item.trident.throw).
SOUND_CATEGORIES = ("entity", "block", "item", "spell", "ambient", "ui")
# The game clamps what it plays to these (SoundEngine): a pitch outside them sounds like the bound.
MIN_PITCH = 0.5
MAX_PITCH = 2.0


class SoundRefDef(StrictModel):
    """One vanilla sound event the event may play, with its own volume and pitch: the game multiplies them with the
    vanilla event's. An event with several picks one at random each time, as likely as the vanilla event has
    sounds."""

    event: ResourceId = Field(description="A vanilla sound event, e.g. minecraft:entity.iron_golem.hurt.")
    volume: float = Field(
        default=1.0, gt=0, le=1, allow_inf_nan=False, description="Volume, more than 0 and at most 1."
    )
    pitch: float = Field(
        default=1.0,
        ge=MIN_PITCH,
        le=MAX_PITCH,
        allow_inf_nan=False,
        description="Pitch, 0.5 to 2: lower is deeper.",
    )


class SoundEventDef(StrictModel):
    """A sound event: what it plays and the subtitle that names it."""

    subtitle: LocalizedText = Field(
        description='What the subtitles show while it plays, vanilla\'s style: "Hewn Sentinel cracks".'
    )
    sounds: list[SoundRefDef] = Field(
        min_length=1, description="The sounds it may play; one at random each time."
    )


class SoundGroupDef(StrictModel):
    """The sound events of one subject (a mob, a spell, a block), ``realmforge:<category>.<id>.<name>``."""

    id: ContentId = Field(
        description="The subject: a mob's, spell's or block's id. Must equal the file name."
    )
    category: Literal["entity", "block", "item", "spell", "ambient", "ui"] = Field(
        description="The first word of the event ids: entity for a mob, spell for a spell, block for a block."
    )
    events: dict[SoundName, SoundEventDef] = Field(
        min_length=1, description="Event name (lowercase snake_case) -> the event."
    )


def sound_path(group: SoundGroupDef, name: str) -> str:
    """An event's path: ``<category>.<subject>.<name>``, its key in ``sounds.json``."""
    return f"{group.category}.{group.id}.{name}"


def sound_id(group: SoundGroupDef, name: str) -> str:
    """An event's full id: ``realmforge:<category>.<subject>.<name>``."""
    return f"{MOD_ID}:{sound_path(group, name)}"


def subtitle_key(group: SoundGroupDef, name: str) -> str:
    """An event's subtitle translation key: ``subtitles.realmforge.<category>.<subject>.<name>``."""
    return f"subtitles.{MOD_ID}.{sound_path(group, name)}"
