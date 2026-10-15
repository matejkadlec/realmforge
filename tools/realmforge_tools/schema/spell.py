"""Spells: ``content/spells/<id>.yaml``, the datapack registry ``realmforge:spell`` (architecture 5.2, 6.4, 11).

One engine casts them for players, mobs and bosses (C3a). The first part (C3a-1, RLM-209) has instant casts, the
``damage`` and ``heal`` effect types and the ``self``, ``enemy`` and ``ally`` targeting types; cast times, channels,
spell entities and the NPC targeting types join with the later parts. Names and descriptions come with the spell bar
and its tooltips (C3b, RLM-25).
"""

from __future__ import annotations

from typing import Annotated, Literal

from pydantic import Field, model_validator

from realmforge_tools.project import MOD_ID
from realmforge_tools.schema.character import ResourceType
from realmforge_tools.schema.common import ContentId, ResourceId, Seconds, StrictModel

FiniteNumber = Annotated[float, Field(allow_inf_nan=False)]
NonNegative = Annotated[float, Field(ge=0, allow_inf_nan=False)]

# Vanilla's line of sight check (LivingEntity#hasLineOfSight) fails beyond 128 blocks, so no range may exceed it.
MAX_RANGE = 128.0
# Research SP-11: about a second, which a spell may shorten or switch off (0).
DEFAULT_GLOBAL_COOLDOWN = 1.0
MAX_SPELL_LEVEL = 100

# What a spell's numbers grow with (Matěj, 2026-10-15): spell power for magic, attack damage for physical abilities.
# Off-class stats do nothing for a class's abilities, so a spell's school decides which one it may name.
ScalesWith = Literal["spell_power", "attack_damage"]
SCALING_OF_KIND: dict[str, str] = {"magic": "spell_power", "physical": "attack_damage"}

DAMAGE = f"{MOD_ID}:damage"
HEAL = f"{MOD_ID}:heal"
SELF = f"{MOD_ID}:self"
ENEMY = f"{MOD_ID}:enemy"
ALLY = f"{MOD_ID}:ally"


class _LeveledAmount(StrictModel):
    """An amount at spell level 1 that grows by ``per_level`` for every level above it (research SP-9)."""

    amount: NonNegative = Field(description="The amount at spell level 1.")
    per_level: NonNegative = Field(default=0, description="Added for every spell level above 1.")

    def amount_at(self, level: int) -> float:
        return self.amount + self.per_level * (level - 1)


class DamageEffectDef(_LeveledAmount):
    """Damage of the spell's school to each target. A magic school's hit gets the caster's spell power and the
    school's power in the damage pipeline (architecture 9.1); a physical ability adds a share of the caster's attack
    damage instead (``attack_damage_percent``). Either way the hit can crit as the spell's ``crit`` says."""

    type: Literal["realmforge:damage"]
    attack_damage_percent: NonNegative | None = Field(
        default=None,
        description="Share of the caster's attack damage added to the amount (100 adds all of it). Required for "
        "spells that scale with attack_damage, refused for spells that scale with spell_power.",
    )


class HealEffectDef(_LeveledAmount):
    """Healing of each target. A spell that scales with spell power heals times (1 + spell power + the school's power);
    one that scales with attack damage adds a share of the caster's attack damage instead. Heals can crit too
    (Matěj, 2026-10-15)."""

    type: Literal["realmforge:heal"]
    attack_damage_percent: NonNegative | None = Field(
        default=None,
        description="Share of the caster's attack damage added to the amount (100 adds all of it). Only for spells "
        "that scale with attack_damage.",
    )


SpellEffectDef = Annotated[DamageEffectDef | HealEffectDef, Field(discriminator="type")]


class SelfTargetingDef(StrictModel):
    """The caster itself."""

    type: Literal["realmforge:self"]


class EnemyTargetingDef(StrictModel):
    """A living entity that is not the caster's ally, in range and in line of sight."""

    type: Literal["realmforge:enemy"]
    range: float = Field(gt=0, le=MAX_RANGE, allow_inf_nan=False, description="Range in blocks, at most 128.")


class AllyTargetingDef(StrictModel):
    """The caster or one of its allies (for players: other players), in range and in line of sight. Without a
    target the spell takes the caster."""

    type: Literal["realmforge:ally"]
    range: float = Field(gt=0, le=MAX_RANGE, allow_inf_nan=False, description="Range in blocks, at most 128.")


TargetingDef = Annotated[SelfTargetingDef | EnemyTargetingDef | AllyTargetingDef, Field(discriminator="type")]


class CastDef(StrictModel):
    """How the spell is cast. Only ``instant`` so far; cast times and channels come with C3a-2 (RLM-210)."""

    type: Literal["instant"] = Field(description="instant: the spell's effects run when it is cast.")


class CostDef(_LeveledAmount):
    """What a cast costs: an amount of a resource (mana or rage), growing per spell level like the effects."""

    resource: ResourceType = Field(description="The resource the cast costs.")

    @model_validator(mode="after")
    def _positive(self) -> CostDef:
        if self.amount == 0 and self.per_level == 0:
            raise ValueError("a cost of 0 is no cost: leave cost out instead")
        return self


class CritDef(StrictModel):
    """How the spell crits (Matěj, 2026-10-15). By default every spell can crit, on the caster's crit chance, and a
    crit deals or heals 1 + the caster's crit damage (150 % with the base 50 %). Talents (C5) can change all three."""

    can_crit: bool = Field(default=True, description="Whether the spell can crit at all.")
    multiplier_percent: float | None = Field(
        default=None,
        ge=100,
        allow_inf_nan=False,
        description="A crit's own multiplier in percent (200 doubles), instead of 1 + the caster's crit damage.",
    )
    bonus_chance_percent: float = Field(
        default=0,
        ge=0,
        le=100,
        allow_inf_nan=False,
        description="Crit chance in percent added to the caster's own for this spell.",
    )

    @model_validator(mode="after")
    def _consistent(self) -> CritDef:
        if not self.can_crit and (self.multiplier_percent is not None or self.bonus_chance_percent != 0):
            raise ValueError("a spell that cannot crit takes no multiplier_percent or bonus_chance_percent")
        return self


class SpellDef(StrictModel):
    """A spell for players or NPCs: its school, what it scales with, its targeting, cast, cost, cooldowns, crit
    rules and typed effects, with numbers per spell level up to ``max_level`` (architecture 11.1)."""

    id: ContentId
    school: ResourceId = Field(description="The spell school whose damage it deals, e.g. realmforge:fire.")
    scales_with: ScalesWith = Field(
        description="spell_power for magic schools, attack_damage for physical abilities (Matěj, 2026-10-15)."
    )
    targeting: TargetingDef = Field(description="Who the spell can target.")
    cast: CastDef = Field(description="How it is cast: {type: instant}.")
    cost: CostDef | None = Field(default=None, description="What a cast costs; leave out for a free spell.")
    cooldown_seconds: Seconds = Field(
        default=0, description="The spell's own cooldown; Cooldown Reduction shortens it."
    )
    global_cooldown_seconds: Seconds = Field(
        default=DEFAULT_GLOBAL_COOLDOWN,
        description="How long a cast blocks every other spell (research SP-11: 1 s); 0 for spells off the global "
        "cooldown.",
    )
    max_level: int = Field(default=1, ge=1, le=MAX_SPELL_LEVEL, description="Highest spell level (rank).")
    crit: CritDef = Field(
        default_factory=CritDef, description="How the spell crits; every spell can by default."
    )
    npc_only: bool = Field(default=False, description="For mob and boss spells that no player may cast.")
    effects: list[SpellEffectDef] = Field(min_length=1, description="What the spell does, in order.")

    @model_validator(mode="after")
    def _levels(self) -> SpellDef:
        if self.max_level == 1:
            leveled = [f"effects.{n}" for n, effect in enumerate(self.effects) if effect.per_level != 0]
            if self.cost is not None and self.cost.per_level != 0:
                leveled.append("cost")
            if leveled:
                raise ValueError(
                    f"per_level never applies with max_level 1 ({', '.join(leveled)}): raise max_level or drop it"
                )
        return self
