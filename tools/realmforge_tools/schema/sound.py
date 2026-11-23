"""Sounds: ``content/sounds/<subject>.yaml``, our own sound events (A3, ADR 0007, ADR 0079, architecture 6.2).

Each event is a stable id, ``realmforge:<category>.<subject>.<name>`` (``realmforge:entity.hewn_sentinel.hurt``), that
mobs, spells and blocks play. It plays vanilla sounds with their own pitch and volume, by reference in
``assets/realmforge/sounds.json`` (never copies of vanilla's files), or our own files from ``art/sounds/``
(``file: <subject>/<name>``, RLM-126): a new sound replaces what an event plays without changing its id. Every event
has a subtitle.
"""

from __future__ import annotations

from typing import Annotated, Literal

from pydantic import Field, StringConstraints, model_validator

from realmforge_tools.project import MOD_ID
from realmforge_tools.schema.common import ContentId, LocalizedText, ResourceId, StrictModel

SoundName = Annotated[str, StringConstraints(pattern=r"^[a-z][a-z0-9_]*$", max_length=64)]
# One of our sound files: art/sounds/<subject>/<name>.ogg, shipped as assets/realmforge/sounds/<subject>/<name>.ogg.
SoundFile = Annotated[str, StringConstraints(pattern=r"^[a-z][a-z0-9_]*/[a-z][a-z0-9_]*$", max_length=129)]
# How far a sound carries by default, in blocks (vanilla's attenuation_distance).
DEFAULT_ATTENUATION = 16
# The first word of an event id, after vanilla's own (entity.zombie.hurt, block.stone.break, item.trident.throw).
SOUND_CATEGORIES = ("entity", "block", "item", "spell", "ambient", "ui")
# The game clamps what it plays to these (SoundEngine): a pitch outside them sounds like the bound.
MIN_PITCH = 0.5
MAX_PITCH = 2.0


class SoundRefDef(StrictModel):
    """One sound the event may play: a vanilla sound event, with its own volume and pitch that the game multiplies
    with the vanilla event's, or one of our files (``file``). An event with several picks one at random each time:
    a file as likely as its weight, a vanilla event as likely as it has sounds."""

    event: ResourceId | None = Field(
        default=None,
        description="A vanilla sound event, e.g. minecraft:entity.iron_golem.hurt. Or give file.",
    )
    file: SoundFile | None = Field(
        default=None,
        description="One of our sound files, <subject>/<name>: art/sounds/<subject>/<name>.ogg. Or give event.",
    )
    weight: int = Field(
        default=1, ge=1, le=100, description="How often it plays against the others, at least 1."
    )
    stream: bool = Field(
        default=False, description="Streamed from disk instead of loaded whole: for long sounds and music."
    )
    attenuation_distance: int = Field(
        default=DEFAULT_ATTENUATION,
        ge=1,
        le=256,
        description="A file's: how far it carries, in blocks (vanilla's 16).",
    )
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

    @model_validator(mode="after")
    def _one_source(self) -> SoundRefDef:
        if (self.event is None) == (self.file is None):
            raise ValueError("a sound plays either a vanilla event or one of our files: give event or file")
        if self.event is not None and (self.weight != 1 or self.attenuation_distance != DEFAULT_ATTENUATION):
            # SoundManager plays a referenced event with that event's own weight and reach (26.1.2).
            raise ValueError(
                "a vanilla event keeps its own weight and attenuation_distance: set them on a file"
            )
        return self


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
