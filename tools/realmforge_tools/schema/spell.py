"""Spells: ``content/spells/<id>.yaml``, the datapack registry ``realmforge:spell`` (architecture 5.2, 6.4, 11).

One engine casts them for players, mobs and bosses (C3a). The first part (C3a-1, RLM-209) has instant casts, the
``damage`` and ``heal`` effect types and the ``self``, ``enemy`` and ``ally`` targeting types; the second (C3a-2,
RLM-210) adds cast times and channels; the third (C3a-3, RLM-211) the spell entities (``projectile``, ``area``), the
status effect, movement and knockback effect types and the ``ground_point`` and ``direction`` targeting types. The NPC
targeting types join with C3a-4. Names and descriptions come with the spell bar and its tooltips (C3b, RLM-25).
"""

from __future__ import annotations

from typing import Annotated, Literal

from pydantic import BaseModel, Field, model_validator

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

# Combat effects keep their own short time scale (research SP-8: most last 4-10 s, none beyond 30 s in CTE2).
MAX_EFFECT_SECONDS = 60.0
MAX_AMPLIFIER = 9
MAX_PROJECTILE_SPEED = 100.0
MAX_PROJECTILE_SIZE = 4.0
MAX_PROJECTILE_LIFETIME = 10.0
MAX_AREA_RADIUS = 16.0
MAX_AREA_DURATION = 60.0
MAX_STRENGTH = 5.0

DAMAGE = f"{MOD_ID}:damage"
HEAL = f"{MOD_ID}:heal"
APPLY_EFFECT = f"{MOD_ID}:apply_effect"
REMOVE_EFFECT = f"{MOD_ID}:remove_effect"
KNOCKBACK = f"{MOD_ID}:knockback"
PROJECTILE = f"{MOD_ID}:projectile"
AREA = f"{MOD_ID}:area"
TELEPORT = f"{MOD_ID}:teleport"
DASH = f"{MOD_ID}:dash"
SELF = f"{MOD_ID}:self"
ENEMY = f"{MOD_ID}:enemy"
ALLY = f"{MOD_ID}:ally"
GROUND_POINT = f"{MOD_ID}:ground_point"
DIRECTION = f"{MOD_ID}:direction"


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


class ApplyEffectEffectDef(StrictModel):
    """Gives each target a status effect: a vanilla one (``minecraft:slowness``) until our own come with C3c
    (RLM-26). Combat effects keep their own short time scale (research SP-8), so at most 60 seconds."""

    type: Literal["realmforge:apply_effect"]
    effect: ResourceId = Field(description="The status effect, e.g. minecraft:slowness.")
    duration_seconds: Seconds = Field(description="How long it lasts; more than 0, at most 60.")
    amplifier: int = Field(
        default=0,
        ge=0,
        le=MAX_AMPLIFIER,
        description="Its strength above the first: 0 is Slowness I, 1 is Slowness II.",
    )

    @model_validator(mode="after")
    def _duration(self) -> ApplyEffectEffectDef:
        if not 0 < self.duration_seconds <= MAX_EFFECT_SECONDS:
            raise ValueError(
                f"duration_seconds is more than 0 and at most {MAX_EFFECT_SECONDS:g} (research SP-8)"
            )
        return self


class RemoveEffectEffectDef(StrictModel):
    """Removes a status effect from each target, if it has it."""

    type: Literal["realmforge:remove_effect"]
    effect: ResourceId = Field(description="The status effect, e.g. minecraft:slowness.")


class KnockbackEffectDef(StrictModel):
    """Pushes each target away along the spell's direction (from the caster, the projectile's flight or the area's
    middle) and interrupts its cast, unless that may be cast while moving. Knockback Resistance weakens it. The only
    way a spell pushes: its damage never knocks back (architecture 9.1)."""

    type: Literal["realmforge:knockback"]
    strength: float = Field(
        gt=0,
        le=MAX_STRENGTH,
        allow_inf_nan=False,
        description="As vanilla's knockback: a melee hit pushes with 0.4, at most 5.",
    )


# Effects that act on an entity: the spell's target, or what a projectile hits or an area holds.
EntityEffectDef = Annotated[
    DamageEffectDef | HealEffectDef | ApplyEffectEffectDef | RemoveEffectEffectDef | KnockbackEffectDef,
    Field(discriminator="type"),
]


class ProjectileEffectDef(StrictModel):
    """Launches a projectile from the caster's eyes toward the target (an enemy, a ground point or along a
    direction). It flies straight, hits the first enemy in its way and runs its own ``effects`` on it; a block or the
    end of its lifetime ends it without them (architecture 11.3)."""

    type: Literal["realmforge:projectile"]
    speed: float = Field(
        gt=0, le=MAX_PROJECTILE_SPEED, allow_inf_nan=False, description="Blocks per second, at most 100."
    )
    size: float = Field(
        default=0.5,
        gt=0,
        le=MAX_PROJECTILE_SIZE,
        allow_inf_nan=False,
        description="Width and height of its hitbox in blocks, at most 4.",
    )
    lifetime_seconds: Seconds = Field(
        default=5, description="How long it flies before it fizzles; more than 0, at most 10."
    )
    effects: list[EntityEffectDef] = Field(
        min_length=1, description="What it does to the enemy it hits, in order."
    )

    @model_validator(mode="after")
    def _lifetime(self) -> ProjectileEffectDef:
        if not 0 < self.lifetime_seconds <= MAX_PROJECTILE_LIFETIME:
            raise ValueError(f"lifetime_seconds is more than 0 and at most {MAX_PROJECTILE_LIFETIME:g}")
        return self


class AreaEffectDef(StrictModel):
    """Places a ground zone at the target (the caster's feet, an enemy's or a ground point) that runs its own
    ``effects`` on every enemy (or ally) inside it at the end of every interval, for ``duration_seconds``
    (architecture 11.3)."""

    type: Literal["realmforge:area"]
    radius: float = Field(gt=0, le=MAX_AREA_RADIUS, allow_inf_nan=False, description="In blocks, at most 16.")
    duration_seconds: Seconds = Field(
        description="How long it lasts: a whole number of intervals, at most 60 s."
    )
    interval_seconds: Seconds = Field(description="The time between two runs of its effects; more than 0.")
    affects: Literal["enemies", "allies"] = Field(
        default="enemies", description="Whom its effects act on: the caster's enemies or its allies."
    )
    effects: list[EntityEffectDef] = Field(
        min_length=1, description="What it does to each entity inside at every interval, in order."
    )

    @model_validator(mode="after")
    def _ticks(self) -> AreaEffectDef:
        if self.interval_seconds <= 0:
            raise ValueError("an area runs its effects after an interval of more than 0 seconds")
        ticks = round(self.duration_seconds / self.interval_seconds)
        if ticks < 1 or abs(ticks * self.interval_seconds - self.duration_seconds) > 1e-9:
            raise ValueError(
                f"duration_seconds {self.duration_seconds} is not a whole number of intervals of "
                f"{self.interval_seconds} s"
            )
        if self.duration_seconds > MAX_AREA_DURATION:
            raise ValueError(f"duration_seconds is at most {MAX_AREA_DURATION:g}")
        return self


class TeleportEffectDef(StrictModel):
    """Moves the caster at once to the target: a ground point, or as far along a direction as its range allows. It
    stops short of blocks, and does not move when no free spot is found on the way."""

    type: Literal["realmforge:teleport"]


class DashEffectDef(StrictModel):
    """Throws the caster toward the target (an enemy, a ground point or along a direction) with a burst of speed,
    which friction then slows as for any push."""

    type: Literal["realmforge:dash"]
    strength: float = Field(
        gt=0,
        le=MAX_STRENGTH,
        allow_inf_nan=False,
        description="The burst's speed in blocks per tick (1 carries about 2 blocks on the ground), at most 5.",
    )


SpellEffectDef = Annotated[
    DamageEffectDef
    | HealEffectDef
    | ApplyEffectEffectDef
    | RemoveEffectEffectDef
    | KnockbackEffectDef
    | ProjectileEffectDef
    | AreaEffectDef
    | TeleportEffectDef
    | DashEffectDef,
    Field(discriminator="type"),
]


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


class GroundPointTargetingDef(StrictModel):
    """A point within range and in line of sight: the one the cast aims at, or else the first block the caster looks
    at. No entity: effects that act on one run through a projectile or an area."""

    type: Literal["realmforge:ground_point"]
    range: float = Field(gt=0, le=MAX_RANGE, allow_inf_nan=False, description="Range in blocks, at most 128.")


class DirectionTargetingDef(StrictModel):
    """A direction from the caster's eyes: toward what the cast aims at, or else where the caster looks. Its point is
    ``range`` blocks along it, or the first block in the way (a teleport's destination)."""

    type: Literal["realmforge:direction"]
    range: float = Field(gt=0, le=MAX_RANGE, allow_inf_nan=False, description="Range in blocks, at most 128.")


TargetingDef = Annotated[
    SelfTargetingDef | EnemyTargetingDef | AllyTargetingDef | GroundPointTargetingDef | DirectionTargetingDef,
    Field(discriminator="type"),
]


class InstantCastDef(StrictModel):
    """The spell's effects run when it is cast."""

    type: Literal["instant"]


class _TimedCastDef(StrictModel):
    """What cast-time spells and channels share: moving cancels them unless the spell allows it, and the target is
    checked again before the effects run (architecture 11.2)."""

    castable_while_moving: bool = Field(
        default=False,
        description="Whether the caster may move during the cast; by default moving cancels it.",
    )
    lock_on: bool = Field(
        default=False,
        description="Whether the spell keeps its target once cast: the target must still be alive and in the caster's "
        "dimension, but range and line of sight are not checked again.",
    )


class CastTimeCastDef(_TimedCastDef):
    """The spell takes ``duration_seconds`` to cast, which Cast Time Reduction shortens. At the end the cost and the
    target are checked again; the cost, its own cooldown and the effects follow. The global cooldown starts with the
    cast."""

    type: Literal["cast_time"]
    duration_seconds: Seconds = Field(description="The cast time, before Cast Time Reduction; more than 0.")

    @model_validator(mode="after")
    def _positive(self) -> CastTimeCastDef:
        if self.duration_seconds <= 0:
            raise ValueError("a cast time of 0 is an instant cast: use {type: instant}")
        return self


class ChannelCastDef(_TimedCastDef):
    """The spell pays its cost and starts its cooldowns when the channel begins, then runs its effects every
    ``interval_seconds`` for ``duration_seconds``, checking the target before every tick. Cast Time Reduction shortens
    the interval, so the channel keeps its number of ticks."""

    type: Literal["channel"]
    duration_seconds: Seconds = Field(
        description="How long the channel lasts, before Cast Time Reduction: a whole number of intervals."
    )
    interval_seconds: Seconds = Field(description="The time between two ticks of the effects; more than 0.")

    @model_validator(mode="after")
    def _ticks(self) -> ChannelCastDef:
        if self.interval_seconds <= 0:
            raise ValueError("a channel ticks after an interval of more than 0 seconds")
        ticks = round(self.duration_seconds / self.interval_seconds)
        if ticks < 1 or abs(ticks * self.interval_seconds - self.duration_seconds) > 1e-9:
            raise ValueError(
                f"duration_seconds {self.duration_seconds} is not a whole number of intervals of "
                f"{self.interval_seconds} s"
            )
        return self


CastDef = Annotated[InstantCastDef | CastTimeCastDef | ChannelCastDef, Field(discriminator="type")]


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
    cast: CastDef = Field(
        description="How it is cast: {type: instant}, {type: cast_time, duration_seconds: 2} or "
        "{type: channel, duration_seconds: 3, interval_seconds: 1}."
    )
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
            leveled = [path for path, effect in self.all_effects() if getattr(effect, "per_level", 0) != 0]
            if self.cost is not None and self.cost.per_level != 0:
                leveled.append("cost")
            if leveled:
                raise ValueError(
                    f"per_level never applies with max_level 1 ({', '.join(leveled)}): raise max_level or drop it"
                )
        return self

    def all_effects(self) -> list[tuple[str, BaseModel]]:
        """Every effect with its path, those that a projectile or an area runs included."""
        found: list[tuple[str, BaseModel]] = []
        for n, effect in enumerate(self.effects):
            found.append((f"effects.{n}", effect))
            for m, inner in enumerate(getattr(effect, "effects", ())):
                found.append((f"effects.{n}.effects.{m}", inner))
        return found
