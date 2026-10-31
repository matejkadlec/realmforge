"""Bosses: ``content/bosses/<id>.yaml``, the fight of a boss (architecture 14.3, W5, RLM-490).

A boss's body is a custom mob of archetype ``boss`` in ``content/mobs/``: its name, size, attributes at level 1,
sounds, loot and placeholder model. Its entry here is its fight, the server-only datapack registry ``realmforge:boss``
(architecture 5.2): the entity it runs on, its fixed level, its arena, its boss bar's colour and its phases. A phase
has a name the boss bar shows, a trigger that enters it (from the type registry ``realmforge:phase_trigger_type``),
spells it casts once when it begins and the abilities it casts while it lasts, all through the spell engine, so every
attack is a readable cast.
"""

from __future__ import annotations

from typing import Annotated, Literal

from pydantic import Field, model_validator

from realmforge_tools.project import MOD_ID
from realmforge_tools.schema.common import (
    ContentId,
    LocalizedText,
    ResourceId,
    Seconds,
    StrictModel,
    UniqueResourceIds,
)
from realmforge_tools.schema.requirement import MAX_LEVEL

# The trigger types of realmforge:phase_trigger_type (boss/PhaseTriggerTypes).
HEALTH_BELOW = f"{MOD_ID}:health_below"
TIME_IN_PHASE = f"{MOD_ID}:time_in_phase"

# Vanilla's boss bar colours (BossEvent.BossBarColor).
BarColor = Literal["pink", "blue", "red", "green", "yellow", "purple", "white"]


class HealthBelowDef(StrictModel):
    """The boss's health has fallen below a share of its maximum."""

    type: Literal["realmforge:health_below"]
    health_percent: float = Field(
        gt=0,
        lt=100,
        allow_inf_nan=False,
        description="The share of its maximum health, in percent: 0 to 100.",
    )


class TimeInPhaseDef(StrictModel):
    """The boss has spent a while in the phase before this one: an enrage timer, or a phase that ends by itself."""

    type: Literal["realmforge:time_in_phase"]
    after_seconds: Seconds = Field(description="How long the phase before lasts, in seconds; more than 0.")

    @model_validator(mode="after")
    def _positive(self) -> TimeInPhaseDef:
        if self.after_seconds <= 0:
            raise ValueError("after_seconds is more than 0")
        return self


PhaseTriggerDef = Annotated[HealthBelowDef | TimeInPhaseDef, Field(discriminator="type")]


class BossAbilityDef(StrictModel):
    """A spell the boss casts while its phase lasts, whenever the spell is ready and its target in reach. The boss
    aims it at its current target; a spell that picks its own target (``random_player``, ``farthest_player``) or acts
    on the boss itself (``self``) chooses for itself."""

    spell: ResourceId = Field(description="The spell, realmforge:<id> (content/spells/).")
    cooldown_seconds: Seconds = Field(
        default=0,
        description="The ability's own pause between two casts, on top of the spell's cooldown; 0 (the default): the "
        "spell's cooldown alone.",
    )


class BossPhaseDef(StrictModel):
    """One phase of the fight. The first is where the fight starts; every later one has a trigger that enters it from
    the phase before, so the phases come in order."""

    id: ContentId = Field(description="The phase's id, unique within the boss, e.g. awakening.")
    name: LocalizedText = Field(
        description="The phase's name in the boss bar, after the boss's name: Title Case, 1 to 3 words (lore style "
        "guide), e.g. Stone Fury."
    )
    enter_when: PhaseTriggerDef | None = Field(
        default=None, description="What enters this phase from the one before; none for the first phase."
    )
    on_enter: UniqueResourceIds = Field(
        default_factory=list,
        description="Spells the boss casts at once when the phase begins, without their cooldowns: a buff on "
        "itself, a blast round it. Not for the first phase.",
    )
    abilities: list[BossAbilityDef] = Field(
        default_factory=list, description="What the boss casts while the phase lasts, most important first."
    )

    @model_validator(mode="after")
    def _unique_spells(self) -> BossPhaseDef:
        spells = [ability.spell for ability in self.abilities]
        repeated = sorted({spell for spell in spells if spells.count(spell) > 1})
        if repeated:
            raise ValueError(f"one ability per spell: {', '.join(repeated)}")
        return self


class ArenaDef(StrictModel):
    """Where the boss fights: a sphere round its home, the spot where it first stood on the ground. It targets only
    players inside, and pulled out of it, it resets: back home, at full health, in its first phase."""

    radius: float = Field(
        ge=4, le=64, allow_inf_nan=False, description="The arena's radius in blocks, 4 to 64."
    )


class BossDef(StrictModel):
    """A boss's fight, ``realmforge:<id>``: the entity it runs on and its phases."""

    id: ContentId
    entity: ResourceId = Field(
        description="Its body: a mob of archetype boss in content/mobs/, realmforge:<mob>. One boss per such mob."
    )
    level: int = Field(
        ge=1,
        le=MAX_LEVEL,
        description="Its fixed level, which scales its attributes like every mob's (architecture 14.1); in a dungeon "
        "run it takes the run's level instead.",
    )
    arena: ArenaDef
    bar_color: BarColor = Field(
        default="red",
        description="The colour of its boss bar: pink, blue, red, green, yellow, purple or white.",
    )
    phase_change_seconds: Seconds = Field(
        default=3,
        description="How long it stands, unhurt and casting nothing, when it enters a new phase, in seconds (research "
        "BM-2: 3 s); 0 for no pause. Particles round it show it.",
    )
    immune_to: list[ResourceId] = Field(
        default_factory=list,
        description="Status effects it never takes, e.g. [realmforge:frozen, realmforge:stunned] (RLM-539): per boss, "
        "as ordinary mobs have no immunity.",
    )
    phases: list[BossPhaseDef] = Field(
        min_length=1, description="Its phases, in the order the fight goes through."
    )

    @model_validator(mode="after")
    def _phases(self) -> BossDef:
        ids = [phase.id for phase in self.phases]
        repeated = sorted({phase for phase in ids if ids.count(phase) > 1})
        if repeated:
            raise ValueError(f"one phase per id: {', '.join(repeated)}")
        first = self.phases[0]
        if first.enter_when is not None:
            raise ValueError(f"the first phase ({first.id}) is where the fight starts: it has no enter_when")
        if first.on_enter:
            raise ValueError(f"the first phase ({first.id}) begins at spawn: it casts no on_enter spells")
        for phase in self.phases[1:]:
            if phase.enter_when is None:
                raise ValueError(
                    f"phase {phase.id} needs an enter_when: what enters it from the phase before"
                )
        threshold = 100.0
        for phase in self.phases:
            trigger = phase.enter_when
            if isinstance(trigger, HealthBelowDef):
                if trigger.health_percent >= threshold:
                    raise ValueError(
                        f"phase {phase.id} enters below {trigger.health_percent:g} % health, not below an earlier "
                        f"phase's {threshold:g} %: health thresholds decrease phase by phase"
                    )
                threshold = trigger.health_percent
        return self


def boss_id(boss: str) -> str:
    """The full id of a boss in ``content/bosses/``: ``realmforge:<id>``."""
    return f"{MOD_ID}:{boss}"
