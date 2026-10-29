"""Status effects: ``content/effects/<id>.yaml``, our own ``MobEffect`` registrations (architecture 11.4, C3c, RLM-26).

``MobEffect`` is a static registry, so each effect becomes a generated registration of ``effect/StatusEffect``: its
attribute modifiers, and damage or healing at every interval while it lasts. Spells apply it through
``realmforge:apply_effect``, which remembers who applied it and with which spell (``effect/EffectSources``), for
dispels and talents.
"""

from __future__ import annotations

from typing import Annotated, Literal

from pydantic import Field, model_validator

from realmforge_tools.project import MOD_ID
from realmforge_tools.schema.character import CharacterModifierDef
from realmforge_tools.schema.common import ContentId, LocalizedText, ResourceId, Seconds, StrictModel
from realmforge_tools.schema.spell_school import HexColor

Positive = Annotated[float, Field(gt=0, allow_inf_nan=False)]

# What a stun's modifiers bring to a halt (C4): walking and jumping.
STUN_STOPS: tuple[str, ...] = ("minecraft:movement_speed", "minecraft:jump_strength")

# A tick of damage or healing is a hit or a heal of its own, so a few per second at most.
MIN_INTERVAL_SECONDS = 0.25
MAX_INTERVAL_SECONDS = 10.0


class EffectModifierDef(CharacterModifierDef):
    """An attribute modifier the effect gives while it lasts. The amount is the first level's: vanilla multiplies it
    by the amplifier + 1, so Slowness II (amplifier 1) slows twice as much as Slowness I. Units follow the stat rules:
    multiplied operations take ``amount_percent``; ``add_value`` on one of our stats takes its unit."""


class PeriodicDef(StrictModel):
    """Damage or healing of a spell school at the end of every interval while the effect lasts (damage or healing
    over time). Each amplifier level adds the amount again, and the caster's stats raise it as they would the
    school's hit or heal: spell power for a magic school, and the school's power. Ticks never crit."""

    type: Literal["damage", "heal"] = Field(description="damage: a hit of the school; heal: healing.")
    school: ResourceId = Field(description="The spell school, e.g. realmforge:fire.")
    amount: Positive = Field(description="Damage or healing of one tick at the first level (amplifier 0).")
    interval_seconds: Seconds = Field(description="The time between two ticks, from 0.25 to 10 s.")

    @model_validator(mode="after")
    def _interval(self) -> PeriodicDef:
        if not MIN_INTERVAL_SECONDS <= self.interval_seconds <= MAX_INTERVAL_SECONDS:
            raise ValueError(
                f"interval_seconds is from {MIN_INTERVAL_SECONDS:g} to {MAX_INTERVAL_SECONDS:g} seconds"
            )
        return self


class EffectDef(StrictModel):
    """A status effect of ours, ``realmforge:<id>``: harmful, beneficial or neutral, with attribute modifiers, damage
    or healing over time, or both. Its name is the effect's translation, ``effect.realmforge.<id>``, shown in the
    inventory and on the HUD with its icon (a placeholder until the art exists)."""

    id: ContentId
    name: LocalizedText = Field(description="The effect's name, shown with its icon.")
    category: Literal["beneficial", "harmful", "neutral"] = Field(
        description="beneficial: a buff; harmful: a debuff (it can be dispelled as one); neutral: neither."
    )
    color: HexColor = Field(description='Colour of its particles, "#RRGGBB".')
    modifiers: list[EffectModifierDef] = Field(
        default_factory=list, description="Attribute modifiers it gives while it lasts."
    )
    periodic: PeriodicDef | None = Field(
        default=None, description="Damage or healing at the end of every interval while it lasts."
    )
    stun: bool = Field(
        default=False,
        description="A stun (C4): its holder neither attacks nor casts, a mob's AI stands still, and a cast in "
        "progress is interrupted. Harmful only; its modifiers stop movement (movement_speed and jump_strength at "
        "-100 % add_multiplied_total).",
    )

    @model_validator(mode="after")
    def _does_something(self) -> EffectDef:
        if not self.modifiers and self.periodic is None:
            raise ValueError("an effect gives modifiers, periodic damage or healing, or both")
        if self.stun:
            if self.category != "harmful":
                raise ValueError("a stun is harmful")
            stopped = {
                modifier.attribute
                for modifier in self.modifiers
                if modifier.operation == "add_multiplied_total" and modifier.amount_percent == -100
            }
            missing = sorted(set(STUN_STOPS) - stopped)
            if missing:
                raise ValueError(
                    f"a stun stops movement: give it {', '.join(missing)} at amount_percent -100, "
                    "add_multiplied_total"
                )
        attributes = [modifier.attribute for modifier in self.modifiers]
        repeated = sorted({attribute for attribute in attributes if attributes.count(attribute) > 1})
        if repeated:
            # MobEffect keeps one modifier per attribute: a second one would replace the first.
            raise ValueError(f"one modifier per attribute: {', '.join(repeated)}")
        return self


def effect_id(effect: str) -> str:
    """The full id of an effect in ``content/effects/``: ``realmforge:<id>``."""
    return f"{MOD_ID}:{effect}"


def effect_description_id(effect: str) -> str:
    """The effect's translation key, ``effect.realmforge.<id>`` (``MobEffect#getDescriptionId``)."""
    return f"effect.{MOD_ID}.{effect}"


def modifier_id(effect: str, attribute: str) -> str:
    """The id of an effect's modifier on ``attribute``: ``realmforge:effect.<effect>.<namespace>.<path>``."""
    namespace, path = attribute.split(":", 1)
    return f"{MOD_ID}:effect.{effect}.{namespace}.{path.replace('/', '.')}"
