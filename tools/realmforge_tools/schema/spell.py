"""Spells: ``content/spells/<id>.yaml``, the datapack registry ``realmforge:spell`` (architecture 5.2, 6.4, 11).

One engine casts them for players, mobs and bosses (C3a). The first part (C3a-1, RLM-209) has instant casts, the
``damage`` and ``heal`` effect types and the ``self``, ``enemy`` and ``ally`` targeting types; the second (C3a-2,
RLM-210) adds cast times and channels; the third (C3a-3, RLM-211) the spell entities (``projectile``, ``area``), the
status effect, movement and knockback effect types and the ``ground_point`` and ``direction`` targeting types; the
fourth (C3a-4, RLM-212) the NPC targeting types ``current_target``, ``random_player`` and ``farthest_player``. The spell
bar and its tooltips (C3b, RLM-25) add the name and the description, whose numbers come from data through
placeholders (architecture 6.3); our own status effects (C3c, RLM-26) add damage and healing over time. Spell
aiming (RLM-344, RLM-346) lets a projectile fly without a lifetime and a ground point clamp to its range. The class
kits (C4, RLM-27) add what a cast needs in hand (``requires``, RLM-499), the per-rank armor shred, spell penetration
and crit chance of a hit, status effects that last longer and grow stronger with the rank, ``cleanse``, spells cast
while stunned, and the rule that a player's channel ticks every 0.5 s (RLM-518).
"""

from __future__ import annotations

import re
from typing import Annotated, Literal

from pydantic import BaseModel, Field, model_validator

from realmforge_tools.project import MOD_ID
from realmforge_tools.schema.character import CharacterModifierDef, ResourceType
from realmforge_tools.schema.common import ContentId, LocalizedText, ResourceId, Seconds, StrictModel
from realmforge_tools.schema.spell_school import HexColor

FiniteNumber = Annotated[float, Field(allow_inf_nan=False)]
NonNegative = Annotated[float, Field(ge=0, allow_inf_nan=False)]
Percent = Annotated[float, Field(gt=0, le=100, allow_inf_nan=False)]

# Vanilla's line of sight check (LivingEntity#hasLineOfSight) fails beyond 128 blocks, so no range may exceed it.
MAX_RANGE = 128.0
# Half a second, as in WoW, so instant spells cannot be spammed (Matěj, 2026-10-19, RLM-25; it replaced research
# SP-11's second). Every spell is on it, physical abilities too (Matěj, 2026-10-24): a spell may change its length,
# never switch it off.
DEFAULT_GLOBAL_COOLDOWN = 0.5
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
# Every channel a player casts ticks every half second (Matěj, 2026-10-29, RLM-518: more, smaller hits beat fewer,
# bigger ones). Mob and boss spells keep their own pace for now (Astone's Quake slams every 0.75 s).
PLAYER_CHANNEL_INTERVAL = 0.5

# What a player's cast needs in hand (RLM-499); mobs and bosses cast without it. melee_weapon: an item of
# realmforge:melee_weapons in the main hand; shield: a shield (c:tools/shield) in either hand.
Requirement = Literal["melee_weapon", "shield"]

DAMAGE = f"{MOD_ID}:damage"
HEAL = f"{MOD_ID}:heal"
APPLY_EFFECT = f"{MOD_ID}:apply_effect"
REMOVE_EFFECT = f"{MOD_ID}:remove_effect"
CLEANSE = f"{MOD_ID}:cleanse"
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
CURRENT_TARGET = f"{MOD_ID}:current_target"
RANDOM_PLAYER = f"{MOD_ID}:random_player"
FARTHEST_PLAYER = f"{MOD_ID}:farthest_player"
# The targeting types a mob or boss chooses its target with: only npc_only spells use them (architecture 11.2).
NPC_TARGETING: tuple[str, ...] = (CURRENT_TARGET, RANDOM_PLAYER, FARTHEST_PLAYER)

# The placeholders a description may name, filled in the game from the spell's data at the player's spell level and
# with their current stats (spell/SpellText; architecture 6.3: numbers come from data, never from prose):
# - damage, heal: the first damage or heal effect (a projectile's or an area's included), raised by spell power and the
#   school's power as the hit or heal would be, before the target's resistance and without a crit;
# - duration: the first apply_effect's duration in seconds;
# - periodic, periodic_total, interval: one tick, every tick and the time between two ticks of that effect's damage or
#   healing over time (one of ours with periodic), raised by the caster's stats as its ticks are;
# - radius: the first area's radius in blocks;
# - armor_shred, penetration: the first damage effect's extra armor shred and spell penetration, in percent;
# - crit_chance: the spell's bonus crit chance, in percent;
# - bonus: the one attribute modifier of the first apply_effect's status effect at its strength, in percent for a
#   multiplied operation and in the attribute's points otherwise.
# Every number is the one at the player's spell level (rank).
PLACEHOLDERS: tuple[str, ...] = (
    "damage",
    "heal",
    "duration",
    "periodic",
    "periodic_total",
    "interval",
    "radius",
    "armor_shred",
    "penetration",
    "crit_chance",
    "bonus",
)
PERIODIC_PLACEHOLDERS: frozenset[str] = frozenset({"periodic", "periodic_total", "interval"})
# The placeholders a passive spell's description may name (RLM-541), each at the player's rank; the next rank's tooltip
# line reads them at that rank:
# - bonus: the passive's first modifier, in percent for a multiplied operation, in the attribute's unit otherwise;
# - spell_damage: its first spell damage bonus, in percent;
# - crit_chance: its crit chance against the condition, in percent;
# - block_cooldown, block_cooldown_rank: how much shorter a two-handed block's cooldown is, in seconds, and from which
#   rank.
PASSIVE_PLACEHOLDERS: tuple[str, ...] = (
    "bonus",
    "spell_damage",
    "crit_chance",
    "block_cooldown",
    "block_cooldown_rank",
)
_PLACEHOLDER = re.compile(r"\{([^{}]*)\}")


class _LeveledAmount(StrictModel):
    """An amount at spell level 1 that grows by ``per_level`` for every level above it (research SP-9)."""

    amount: NonNegative = Field(description="The amount at spell level 1.")
    per_level: NonNegative = Field(default=0, description="Added for every spell level above 1.")

    def amount_at(self, level: int) -> float:
        return self.amount + self.per_level * (level - 1)


class _ScaledAmount(_LeveledAmount):
    """An amount that adds a share of the caster's power, the coefficient, growing per rank like the amount (scaling
    system C, Matěj, 2026-10-30, RLM-534): (amount + coefficient times power) times (1 + the school's power +
    passives). The power is Spell Power for a spell that scales with spell_power and Attack Damage for one that scales
    with attack_damage, so an effect names the coefficient of its spell's power only."""

    attack_damage_percent: NonNegative | None = Field(
        default=None,
        description="Share of the caster's attack damage added to the amount at spell level 1 (100 adds all of it). "
        "Required for the damage of spells that scale with attack_damage, refused for spells that scale with "
        "spell_power.",
    )
    attack_damage_per_level_percent: NonNegative | None = Field(
        default=None, description="Added to attack_damage_percent for every spell level above 1."
    )
    spell_power_percent: NonNegative | None = Field(
        default=None,
        description="Share of the caster's Spell Power (points) added to the amount at spell level 1 (100 adds all "
        "of it). Only for spells that scale with spell_power; without it, Spell Power adds nothing.",
    )
    spell_power_per_level_percent: NonNegative | None = Field(
        default=None, description="Added to spell_power_percent for every spell level above 1."
    )

    @model_validator(mode="after")
    def _coefficient_growth_needs_base(self) -> _ScaledAmount:
        for name in ("attack_damage", "spell_power"):
            if (
                getattr(self, f"{name}_per_level_percent") is not None
                and getattr(self, f"{name}_percent") is None
            ):
                raise ValueError(f"{name}_per_level_percent grows {name}_percent: set it too")
        return self

    def attack_damage_at(self, level: int) -> float:
        """The share of attack damage added at ``level``, in percent; 0 without any."""
        return _leveled_percent(self.attack_damage_percent, self.attack_damage_per_level_percent, level)

    def spell_power_at(self, level: int) -> float:
        """The share of Spell Power added at ``level``, in percent; 0 without any."""
        return _leveled_percent(self.spell_power_percent, self.spell_power_per_level_percent, level)


class DamageEffectDef(_ScaledAmount):
    """Damage of the spell's school to each target: its amount plus its coefficient of the caster's power (Spell
    Power for magic, Attack Damage for a physical ability), then times (1 + the school's power + passives) in the damage
    pipeline (architecture 9.1). The hit can crit as the spell's ``crit`` says."""

    type: Literal["realmforge:damage"]
    armor_shred_percent: Percent | None = Field(
        default=None,
        description="Armor shred this hit adds to the caster's own, in percent at spell level 1: the share of the "
        "target's armor (after armor pierce) the hit ignores. Only a school that armor stops (physical).",
    )
    armor_shred_per_level_percent: Percent | None = Field(
        default=None, description="Added to armor_shred_percent for every spell level above 1."
    )
    penetration_percent: Percent | None = Field(
        default=None,
        description="Spell penetration, in percent at spell level 1: the share of the target's resistance to the "
        "school the hit ignores. Only a magic school.",
    )
    penetration_per_level_percent: Percent | None = Field(
        default=None, description="Added to penetration_percent for every spell level above 1."
    )

    @model_validator(mode="after")
    def _per_level_needs_base(self) -> DamageEffectDef:
        for name in ("armor_shred", "penetration"):
            if (
                getattr(self, f"{name}_per_level_percent") is not None
                and getattr(self, f"{name}_percent") is None
            ):
                raise ValueError(f"{name}_per_level_percent grows {name}_percent: set it too")
        return self

    def armor_shred_at(self, level: int) -> float:
        """The extra armor shred at ``level``, in percent; 0 without any."""
        return _leveled_percent(self.armor_shred_percent, self.armor_shred_per_level_percent, level)

    def penetration_at(self, level: int) -> float:
        """The spell penetration at ``level``, in percent; 0 without any."""
        return _leveled_percent(self.penetration_percent, self.penetration_per_level_percent, level)


def _leveled_percent(base: float | None, per_level: float | None, level: int) -> float:
    return (base or 0) + (per_level or 0) * (level - 1)


class HealEffectDef(_ScaledAmount):
    """Healing of each target: its amount plus its coefficient of the caster's power, times (1 + the school's power) for
    a spell that scales with spell power. Heals can crit too (Matěj, 2026-10-15)."""

    type: Literal["realmforge:heal"]


class ApplyEffectEffectDef(StrictModel):
    """Gives each target a status effect: a vanilla one (``minecraft:slowness``) until our own come with C3c
    (RLM-26). Combat effects keep their own short time scale (research SP-8), so at most 60 seconds."""

    type: Literal["realmforge:apply_effect"]
    effect: ResourceId = Field(description="The status effect, e.g. minecraft:slowness.")
    duration_seconds: Seconds = Field(
        description="How long it lasts at spell level 1; more than 0, at most 60."
    )
    duration_per_level_seconds: Seconds | None = Field(
        default=None,
        description="Added to the duration for every spell level above 1 (C4: a rank lasts longer).",
    )
    amplifier: int = Field(
        default=0,
        ge=0,
        le=MAX_AMPLIFIER,
        description="Its strength above the first at spell level 1: 0 is Slowness I, 1 is Slowness II.",
    )
    stronger_at_levels: list[int] = Field(
        default_factory=list,
        description="Spell levels (ranks) from which the amplifier is one higher, in ascending order: [5] makes "
        "Slowness I Slowness II from rank 5.",
    )

    @model_validator(mode="after")
    def _duration(self) -> ApplyEffectEffectDef:
        if not 0 < self.duration_seconds <= MAX_EFFECT_SECONDS:
            raise ValueError(
                f"duration_seconds is more than 0 and at most {MAX_EFFECT_SECONDS:g} (research SP-8)"
            )
        if self.stronger_at_levels != sorted(set(self.stronger_at_levels)) or any(
            level < 2 for level in self.stronger_at_levels
        ):
            raise ValueError("stronger_at_levels lists spell levels from 2 up, each once, in ascending order")
        if self.amplifier + len(self.stronger_at_levels) > MAX_AMPLIFIER:
            raise ValueError(f"the amplifier never rises above {MAX_AMPLIFIER}")
        return self

    def duration_at(self, level: int) -> float:
        """How long it lasts at spell ``level``, in seconds."""
        return self.duration_seconds + (self.duration_per_level_seconds or 0) * (level - 1)

    @model_validator(mode="after")
    def _growth(self) -> ApplyEffectEffectDef:
        if self.duration_per_level_seconds is not None and self.duration_per_level_seconds <= 0:
            raise ValueError("duration_per_level_seconds is more than 0: leave it out for no growth")
        return self

    def amplifier_at(self, level: int) -> int:
        """Its amplifier at spell ``level``."""
        return self.amplifier + sum(1 for stronger in self.stronger_at_levels if level >= stronger)


class RemoveEffectEffectDef(StrictModel):
    """Removes a status effect from each target, if it has it."""

    type: Literal["realmforge:remove_effect"]
    effect: ResourceId = Field(description="The status effect, e.g. minecraft:slowness.")


class CleanseEffectDef(StrictModel):
    """Removes every harmful status effect from each target: debuffs, damage over time and loss of control such as a
    stun (the Human's Will To Survive, C4)."""

    type: Literal["realmforge:cleanse"]


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
    DamageEffectDef
    | HealEffectDef
    | ApplyEffectEffectDef
    | RemoveEffectEffectDef
    | CleanseEffectDef
    | KnockbackEffectDef,
    Field(discriminator="type"),
]


class ProjectileImpactDef(StrictModel):
    """What a projectile does where it hits a block or an entity: a sound and a block's break particles, as a mined
    block shatters."""

    sound: ResourceId | None = Field(
        default=None, description="A sound event, e.g. realmforge:spell.stoneform.impact."
    )
    particles: ResourceId | None = Field(
        default=None,
        description="A block whose break particles burst where it hits, e.g. realmforge:vaultstone.",
    )


class ProjectileAppearanceDef(StrictModel):
    """How a projectile looks; without it, a trail of sparks (until C6, RLM-29). Its placeholder texture is
    generated at ``textures/entity/spell/<spell>.png`` until real art exists."""

    model: Literal["orb"] = Field(
        description="orb: a small spinning orb of three crossed slabs, the shape of a shulker's bullet."
    )
    color: HexColor = Field(description='Main colour of its placeholder texture, "#RRGGBB".')


class ProjectileEffectDef(StrictModel):
    """Launches a projectile from the caster's eyes toward the target (an enemy, a ground point or along a
    direction, where the crosshair points). It flies straight and stops at the first living entity in its way that is
    not the caster or its ally: one that can be damaged, hostile or passive, takes its own ``effects``, and one that
    cannot (invulnerable) or of ``spares`` stops it untouched. A block or the end of its lifetime ends it without them,
    and so does leaving the world: above the build height, beyond where entities move, or in the void (architecture
    11.3, RLM-344)."""

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
    lifetime_seconds: Seconds | None = Field(
        default=None,
        description="How long it flies before it fizzles; more than 0, at most 10. Without it, it flies until it hits "
        "something or leaves the world.",
    )
    effects: list[EntityEffectDef] = Field(
        min_length=1, description="What it does to the enemy it hits, in order."
    )
    spares: ResourceId | None = Field(
        default=None,
        description="An entity type tag, e.g. realmforge:stone_creatures: an entity in it stops the projectile but "
        "takes none of its effects.",
    )
    impact: ProjectileImpactDef | None = Field(
        default=None, description="Its sound and particles where it hits a block or an entity."
    )
    appearance: ProjectileAppearanceDef | None = Field(
        default=None, description="How it looks; without it, a trail of sparks."
    )

    @model_validator(mode="after")
    def _lifetime(self) -> ProjectileEffectDef:
        if self.lifetime_seconds is not None and not 0 < self.lifetime_seconds <= MAX_PROJECTILE_LIFETIME:
            raise ValueError(f"lifetime_seconds is more than 0 and at most {MAX_PROJECTILE_LIFETIME:g}")
        return self


class AreaEffectDef(StrictModel):
    """Places a ground zone at the target (the caster's feet, an enemy's or a ground point) that runs its own
    ``effects`` on every enemy (or ally) inside it as it appears and then every interval, once per interval of
    ``duration_seconds`` (architecture 11.3)."""

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
        min_length=1, description="What it does to each entity inside each time it acts, in order."
    )
    ring_color: HexColor | None = Field(
        default=None,
        description='The colour of the ring of dust that shows its edge, "#RRGGBB"; without it, a ring of purple '
        "sparks (until C6, RLM-29).",
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
    | CleanseEffectDef
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
    """Where a ground spell lands, never refused for range (RLM-346): the block the crosshair hits within range, or
    the farthest point in range along the crosshair's line (clamp cast), on the ground below it; aimed at an entity or
    a point in range, its feet or the point (in sight). No entity: effects that act on one run through a projectile or
    an area."""

    type: Literal["realmforge:ground_point"]
    range: float = Field(gt=0, le=MAX_RANGE, allow_inf_nan=False, description="Range in blocks, at most 128.")


class DirectionTargetingDef(StrictModel):
    """A direction from the caster's eyes: toward what the cast aims at, or else where the caster looks, which a
    player's crosshair shows and a cast time or a channel follows until its effects run (RLM-344). Its point is
    ``range`` blocks along it, or the first block in the way (a teleport's destination). It never refuses a cast."""

    type: Literal["realmforge:direction"]
    range: float = Field(gt=0, le=MAX_RANGE, allow_inf_nan=False, description="Range in blocks, at most 128.")


class CurrentTargetTargetingDef(StrictModel):
    """NPC only: the caster's current target (the mob's attack target), an enemy in range and in line of sight."""

    type: Literal["realmforge:current_target"]
    range: float = Field(gt=0, le=MAX_RANGE, allow_inf_nan=False, description="Range in blocks, at most 128.")


class RandomPlayerTargetingDef(StrictModel):
    """NPC only: a random player in range and in line of sight that the caster may attack (not in creative or
    spectator mode, not its ally)."""

    type: Literal["realmforge:random_player"]
    range: float = Field(gt=0, le=MAX_RANGE, allow_inf_nan=False, description="Range in blocks, at most 128.")


class FarthestPlayerTargetingDef(StrictModel):
    """NPC only: the farthest player in range and in line of sight that the caster may attack, as random_player
    chooses from."""

    type: Literal["realmforge:farthest_player"]
    range: float = Field(gt=0, le=MAX_RANGE, allow_inf_nan=False, description="Range in blocks, at most 128.")


TargetingDef = Annotated[
    SelfTargetingDef
    | EnemyTargetingDef
    | AllyTargetingDef
    | GroundPointTargetingDef
    | DirectionTargetingDef
    | CurrentTargetTargetingDef
    | RandomPlayerTargetingDef
    | FarthestPlayerTargetingDef,
    Field(discriminator="type"),
]


class InstantCastDef(StrictModel):
    """The spell's effects run when it is cast."""

    type: Literal["instant"]


class _TimedCastDef(StrictModel):
    """What cast-time spells and channels share: moving never cancels them (ADR 0039), the cast halves its caster's
    speed unless the spell allows full speed (ADR 0047), and the target is checked again before the effects run
    (architecture 11.2)."""

    castable_while_moving: bool = Field(
        default=False,
        description="Whether the caster moves at full speed during the cast; by default the cast halves its speed "
        "(moving never cancels it).",
    )
    lock_on: bool = Field(
        default=False,
        description="Whether the spell keeps its target once cast: the target must still be alive and in the caster's "
        "dimension, but range and line of sight are not checked again.",
    )
    keep_sight: bool = Field(
        default=False,
        description="Whether the caster must keep its target in sight for the whole cast: losing sight of it ends the "
        "cast at once.",
    )
    keep_range_percent: float | None = Field(
        default=None,
        gt=0,
        le=100,
        allow_inf_nan=False,
        description="For this share of the cast, in percent, the target must stay in range, or the cast ends at once; "
        "after it, range no longer matters. Left out: range is checked only before the effects.",
    )
    sound: ResourceId | None = Field(
        default=None, description="A sound event the caster makes when the cast begins, e.g. its chant."
    )
    animation: Literal["raise", "slam"] | None = Field(
        default=None,
        description="How a custom mob or boss moves its arms while it casts (its model's animation): raise (the "
        "default) lifts them forward and up, the hands before its forehead; slam beats its fists up and down, one slam "
        "every 0.75 s, the time a channel of the same interval ticks at (a quake). Players' casts ignore it.",
    )

    @model_validator(mode="after")
    def _keeps(self) -> _TimedCastDef:
        if self.lock_on and (self.keep_sight or self.keep_range_percent is not None):
            raise ValueError(
                "a spell that locks on checks neither sight nor range: drop keep_sight and "
                "keep_range_percent, or lock_on"
            )
        return self


class CastTimeCastDef(_TimedCastDef):
    """The spell takes ``duration_seconds`` to cast, which Cast Time Reduction shortens. At the end the cost and the
    target are checked again; the cost, its own cooldown and the effects follow. The global cooldown starts with the
    cast."""

    type: Literal["cast_time"]
    duration_seconds: Seconds = Field(description="The cast time, before Cast Time Reduction; more than 0.")
    forms_projectile: bool = Field(
        default=False,
        description="Whether the spell's projectile forms in front of the caster during the cast, growing to its "
        "size, and flies from there; the spell's first effect is a projectile with an appearance.",
    )

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
    crit deals or heals 1 + the caster's crit damage (150 % with the base 50 %). Talents (postponed, RLM-28) will
    change all three."""

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
        description="Crit chance in percent added to the caster's own for this spell, at spell level 1. A chance "
        "above 100 % in all turns into crit damage, 1 % for 1 % (C4).",
    )
    bonus_chance_per_level_percent: Percent | None = Field(
        default=None, description="Added to bonus_chance_percent for every spell level above 1."
    )

    @model_validator(mode="after")
    def _consistent(self) -> CritDef:
        if not self.can_crit and (
            self.multiplier_percent is not None
            or self.bonus_chance_percent != 0
            or self.bonus_chance_per_level_percent is not None
        ):
            raise ValueError(
                "a spell that cannot crit takes no multiplier_percent, bonus_chance_percent or "
                "bonus_chance_per_level_percent"
            )
        return self

    def bonus_chance_at(self, level: int) -> float:
        """The bonus crit chance at spell ``level``, in percent."""
        return _leveled_percent(self.bonus_chance_percent, self.bonus_chance_per_level_percent, level)


class PassiveModifierDef(CharacterModifierDef):
    """An attribute modifier a passive spell gives (RLM-541), growing per rank: its amount at rank 1 plus ``per_level``
    (or ``per_level_percent``, in the amount's unit) for every rank above it."""

    per_level: FiniteNumber | None = Field(
        default=None, description="Added to amount for every rank above 1."
    )
    per_level_percent: FiniteNumber | None = Field(
        default=None, description="Added to amount_percent for every rank above 1."
    )

    @model_validator(mode="after")
    def _growth_unit(self) -> PassiveModifierDef:
        if self.per_level is not None and self.amount is None:
            raise ValueError("per_level grows amount: write it as per_level_percent with amount_percent")
        if self.per_level_percent is not None and self.amount_percent is None:
            raise ValueError("per_level_percent grows amount_percent: write it as per_level with amount")
        return self

    def amount_at(self, level: int) -> float:
        """The amount at rank ``level`` in its own unit (percent for amount_percent)."""
        if self.amount_percent is not None:
            return _leveled_percent(self.amount_percent, self.per_level_percent, level)
        assert self.amount is not None
        return self.amount + (self.per_level or 0) * (level - 1)

    def grows(self) -> bool:
        return self.per_level is not None or self.per_level_percent is not None


class SpellDamageDef(StrictModel):
    """More damage for named spells (RLM-541: Improved Frostbolt), added to their hits' (1 + School Power + passives)
    layer."""

    spells: Annotated[list[ResourceId], Field(min_length=1)] = Field(
        description="The spells whose hits deal more, e.g. [realmforge:frostbolt]."
    )
    damage_percent: Annotated[float, Field(gt=0, le=1000, allow_inf_nan=False)] = Field(
        description="How much more damage at rank 1, in percent."
    )
    damage_per_level_percent: NonNegative | None = Field(
        default=None, description="Added to damage_percent for every rank above 1."
    )

    @model_validator(mode="after")
    def _unique(self) -> SpellDamageDef:
        if len(set(self.spells)) != len(self.spells):
            raise ValueError("spells names each spell once")
        return self

    def damage_at(self, level: int) -> float:
        """The bonus at rank ``level``, in percent."""
        return _leveled_percent(self.damage_percent, self.damage_per_level_percent, level)


class CritChanceDef(StrictModel):
    """More crit chance for the player's spell hits on a target with a status effect (RLM-541: Shatter, against Frozen
    targets)."""

    chance_percent: Percent = Field(description="The crit chance added at rank 1, in percent.")
    chance_per_level_percent: Percent | None = Field(
        default=None, description="Added to chance_percent for every rank above 1."
    )
    against: ResourceId = Field(description="The status effect the target has, e.g. realmforge:frozen.")

    def chance_at(self, level: int) -> float:
        """The crit chance added at rank ``level``, in percent."""
        return _leveled_percent(self.chance_percent, self.chance_per_level_percent, level)


class BlockCooldownDef(StrictModel):
    """A raised two-handed weapon's cooldown after a block or after lowering it (architecture 9.3), shorter from a rank
    on (RLM-541: Two-Handed Weapon Specialization's 0.25 s at rank 3)."""

    reduction_seconds: Seconds = Field(description="How much shorter, in seconds; more than 0, at most 10.")
    from_level: int = Field(
        default=1, ge=1, le=MAX_SPELL_LEVEL, description="The rank from which it applies."
    )

    @model_validator(mode="after")
    def _seconds(self) -> BlockCooldownDef:
        if not 0 < self.reduction_seconds <= 10:
            raise ValueError("reduction_seconds is more than 0 and at most 10")
        return self


class PassiveDef(StrictModel):
    """What a passive spell does (RLM-541): it is never cast and never on the spell bar; while the player knows it, at
    its rank, it gives modifiers, raises named spells' damage, adds crit chance against a condition or shortens a
    two-handed block's cooldown. Procs (Frostbite, Taste for Blood, Deep Wounds) come with the windows-and-procs
    ticket and live in a passive spell too."""

    while_wielding: ResourceId | None = Field(
        default=None,
        description="An item tag, e.g. realmforge:two_handed_weapons: the whole passive applies only while the main "
        "hand holds an item of it.",
    )
    modifiers: list[PassiveModifierDef] = Field(
        default_factory=list, description="Attribute modifiers it gives, growing per rank."
    )
    spell_damage: list[SpellDamageDef] = Field(
        default_factory=list, description="More damage for named spells, growing per rank."
    )
    crit_chance: CritChanceDef | None = Field(
        default=None,
        description="More crit chance for the player's spell hits on a target with a status effect.",
    )
    block_cooldown: BlockCooldownDef | None = Field(
        default=None, description="A two-handed block's cooldown, shorter from a rank on."
    )

    @model_validator(mode="after")
    def _does_something(self) -> PassiveDef:
        if not (self.modifiers or self.spell_damage or self.crit_chance or self.block_cooldown):
            raise ValueError("a passive gives modifiers, spell_damage, crit_chance or block_cooldown")
        return self

    def grows(self) -> bool:
        """Whether anything grows with the rank."""
        return (
            any(modifier.grows() for modifier in self.modifiers)
            or any(bonus.damage_per_level_percent is not None for bonus in self.spell_damage)
            or (self.crit_chance is not None and self.crit_chance.chance_per_level_percent is not None)
            or (self.block_cooldown is not None and self.block_cooldown.from_level > 1)
        )


# The fields an active spell names and a passive one leaves out (RLM-541).
ACTIVE_FIELDS: tuple[str, ...] = (
    "scales_with",
    "targeting",
    "cast",
    "cost",
    "cooldown_seconds",
    "global_cooldown_seconds",
    "crit",
    "npc_only",
    "requires",
    "castable_while_stunned",
    "effects",
)
REQUIRED_ACTIVE_FIELDS: tuple[str, ...] = ("scales_with", "targeting", "cast", "effects")


# The effect type each placeholder reads; crit_chance reads the spell's crit data instead.
_PLACEHOLDER_EFFECT: dict[str, str] = {
    "damage": DAMAGE,
    "heal": HEAL,
    "duration": APPLY_EFFECT,
    "periodic": APPLY_EFFECT,
    "periodic_total": APPLY_EFFECT,
    "interval": APPLY_EFFECT,
    "radius": AREA,
    "armor_shred": DAMAGE,
    "penetration": DAMAGE,
    "bonus": APPLY_EFFECT,
}
# Placeholders that read a field of the first damage effect, which it must set.
_DAMAGE_FIELD_PLACEHOLDERS: dict[str, str] = {
    "armor_shred": "armor_shred_percent",
    "penetration": "penetration_percent",
}


class SpellDef(StrictModel):
    """A spell for players or NPCs: its school, what it scales with, its targeting, cast, cost, cooldowns, crit
    rules and typed effects, with numbers per spell level up to ``max_level`` (architecture 11.1)."""

    id: ContentId
    name: LocalizedText = Field(
        description="The spell's name, on the spell bar, in the spellbook and on the cast bar."
    )
    description: LocalizedText = Field(
        description="What the spell does, in its tooltip: one or two sentences. Numbers come from data through "
        "placeholders: {damage}, {heal}, {duration}, {periodic}, {periodic_total}, {interval} and {radius}."
    )
    school: ResourceId | None = Field(
        default=None,
        description="The spell school whose damage it deals, e.g. realmforge:fire. A spell that neither deals damage "
        "nor heals may leave it out: it belongs to no school (Will To Survive, Matěj, 2026-10-30), and the spell bar "
        "frames it in black.",
    )
    scales_with: ScalesWith = Field(
        default="spell_power",
        description="spell_power for magic schools, attack_damage for physical abilities (Matěj, 2026-10-15). "
        "Required for an active spell; a passive one leaves it out.",
    )
    targeting: TargetingDef = Field(
        default_factory=lambda: SelfTargetingDef(type=SELF),
        description="Who the spell can target. Required for an active spell; a passive one leaves it out.",
    )
    cast: CastDef = Field(
        default_factory=lambda: InstantCastDef(type="instant"),
        description="How it is cast: {type: instant}, {type: cast_time, duration_seconds: 2} or "
        "{type: channel, duration_seconds: 3, interval_seconds: 1}. Required for an active spell; a passive one "
        "leaves it out.",
    )
    cost: CostDef | None = Field(
        default=None,
        description="What a cast costs, paid when it begins; a channel pays it again as each later second begins. "
        "Leave out for a free spell.",
    )
    cooldown_seconds: Seconds = Field(
        default=0, description="The spell's own cooldown; Cooldown Reduction shortens it."
    )
    global_cooldown_seconds: Seconds = Field(
        default=DEFAULT_GLOBAL_COOLDOWN,
        gt=0,
        description="How long a cast blocks every other spell (0.5 s by default, as in WoW). Every spell is on the "
        "global cooldown.",
    )
    max_level: int = Field(default=1, ge=1, le=MAX_SPELL_LEVEL, description="Highest spell level (rank).")
    crit: CritDef = Field(
        default_factory=CritDef, description="How the spell crits; every spell can by default."
    )
    npc_only: bool = Field(default=False, description="For mob and boss spells that no player may cast.")
    requires: list[Requirement] = Field(
        default_factory=list,
        description="What a player needs in hand to cast it (RLM-499): melee_weapon (an item of "
        "realmforge:melee_weapons in the main hand) or shield (a shield in either hand). A player's physical spell "
        "that deals damage in melee names one. Mobs and bosses cast without it.",
    )
    castable_while_stunned: bool = Field(
        default=False,
        description="Whether a stunned caster may cast it, as the Human's Will To Survive breaks a stun; every other "
        "spell waits until the stun ends.",
    )
    effects: list[SpellEffectDef] = Field(
        default_factory=list,
        description="What the spell does, in order. Required for an active spell; a passive one leaves it out.",
    )
    passive: PassiveDef | None = Field(
        default=None,
        description="Makes the spell passive (RLM-541): learned with ranks, never cast and never on the spell bar, it "
        "does this while the player knows it. A passive spell names no targeting, cast, cost, cooldowns, crit, "
        "effects or scales_with.",
    )

    @model_validator(mode="after")
    def _active_or_passive(self) -> SpellDef:
        if self.passive is None:
            missing = [name for name in REQUIRED_ACTIVE_FIELDS if name not in self.model_fields_set]
            if missing:
                raise ValueError(
                    f"an active spell names {', '.join(missing)}; a passive one names passive instead"
                )
            if not self.effects:
                raise ValueError("an active spell has at least one effect")
            return self
        named = [name for name in ACTIVE_FIELDS if name in self.model_fields_set]
        if named:
            raise ValueError(f"a passive spell is never cast, so it names no {', '.join(named)}")
        return self

    @property
    def is_passive(self) -> bool:
        return self.passive is not None

    @model_validator(mode="after")
    def _levels(self) -> SpellDef:
        if self.max_level == 1:
            leveled = [path for path, effect in self.all_effects() if _grows_with_level(effect)]
            if self.passive is not None and self.passive.grows():
                leveled.append("passive")
            if self.cost is not None and self.cost.per_level != 0:
                leveled.append("cost")
            if self.crit.bonus_chance_per_level_percent is not None:
                leveled.append("crit")
            if leveled:
                raise ValueError(
                    f"nothing grows with the level at max_level 1 ({', '.join(leveled)}): raise max_level or drop "
                    "the per-level growth"
                )
        return self

    @model_validator(mode="after")
    def _school(self) -> SpellDef:
        if self.school is None:
            hits = [path for path, effect in self.all_effects() if effect.type in (DAMAGE, HEAL)]
            if hits:
                raise ValueError(
                    f"{hits[0]}: a spell that deals damage or heals names its school; only a spell that does "
                    "neither belongs to none"
                )
        return self

    @model_validator(mode="after")
    def _top_rank(self) -> SpellDef:
        top = self.max_level
        for path, effect in self.all_effects():
            if effect.type == DAMAGE and max(effect.armor_shred_at(top), effect.penetration_at(top)) > 100:
                raise ValueError(f"{path}: armor shred and penetration stay at most 100 % at max_level {top}")
            if effect.type == APPLY_EFFECT:
                if effect.duration_at(top) > MAX_EFFECT_SECONDS:
                    raise ValueError(
                        f"{path}: lasts {effect.duration_at(top):g} s at max_level {top}, more than "
                        f"{MAX_EFFECT_SECONDS:g} (research SP-8)"
                    )
                if any(level > top for level in effect.stronger_at_levels):
                    raise ValueError(
                        f"{path}.stronger_at_levels: a level above max_level {top} is never reached"
                    )
        if self.crit.bonus_chance_at(top) > 100:
            raise ValueError(f"crit: the bonus chance stays at most 100 % at max_level {top}")
        if self.passive is not None:
            if self.passive.crit_chance is not None and self.passive.crit_chance.chance_at(top) > 100:
                raise ValueError(f"passive.crit_chance stays at most 100 % at max_level {top}")
            if self.passive.block_cooldown is not None and self.passive.block_cooldown.from_level > top:
                raise ValueError(
                    f"passive.block_cooldown.from_level: a rank above max_level {top} is never reached"
                )
        return self

    @model_validator(mode="after")
    def _requirements(self) -> SpellDef:
        if len(set(self.requires)) != len(self.requires):
            raise ValueError("requires names each requirement once")
        if self.requires and self.npc_only:
            raise ValueError("requires is checked for players only; a mob or boss casts without it (RLM-499)")
        return self

    @model_validator(mode="after")
    def _player_channel(self) -> SpellDef:
        interval = getattr(self.cast, "interval_seconds", None)
        if (
            self.cast.type == "channel"
            and not self.npc_only
            and abs(interval - PLAYER_CHANNEL_INTERVAL) > 1e-9
        ):
            raise ValueError(
                f"a player's channel ticks every {PLAYER_CHANNEL_INTERVAL:g} s (Matěj, 2026-10-29, RLM-518), not "
                f"every {interval:g} s"
            )
        return self

    @model_validator(mode="after")
    def _formed_projectile(self) -> SpellDef:
        if getattr(self.cast, "forms_projectile", False):
            first = self.effects[0]
            if first.type != PROJECTILE or first.appearance is None:
                raise ValueError(
                    "forms_projectile: the spell's first effect is a projectile with an appearance"
                )
        appearances = [
            path for path, effect in self.all_effects() if getattr(effect, "appearance", None) is not None
        ]
        if len(appearances) > 1:
            raise ValueError(
                f"one projectile with an appearance per spell, its texture textures/entity/spell/<spell>.png: "
                f"{', '.join(appearances)}"
            )
        return self

    @model_validator(mode="after")
    def _npc_targeting(self) -> SpellDef:
        if self.targeting.type in NPC_TARGETING and not self.npc_only:
            raise ValueError(f"{self.targeting.type} chooses a mob's target, so the spell is npc_only: true")
        return self

    @model_validator(mode="after")
    def _placeholders(self) -> SpellDef:
        text = self.description.en_us
        if _PLACEHOLDER.sub("", text).count("{") or _PLACEHOLDER.sub("", text).count("}"):
            raise ValueError("description: a brace that opens or closes no {placeholder}")
        if self.passive is not None:
            self._passive_placeholders()
            return self
        for name in self.placeholders():
            if name not in PLACEHOLDERS:
                raise ValueError(
                    f"description: unknown placeholder {{{name}}}; known: {', '.join(PLACEHOLDERS)}"
                )
            if name == "crit_chance":
                if self.crit.bonus_chance_at(self.max_level) == 0:
                    raise ValueError(
                        "description: {crit_chance} reads the crit's bonus chance, and the spell has none"
                    )
                continue
            needed = _PLACEHOLDER_EFFECT[name]
            first = self.first_effect(needed)
            if first is None:
                raise ValueError(f"description: {{{name}}} needs a {needed} effect, and the spell has none")
            field = _DAMAGE_FIELD_PLACEHOLDERS.get(name)
            if field is not None and getattr(first, field) is None:
                raise ValueError(
                    f"description: {{{name}}} reads the first damage effect's {field}, which it lacks"
                )
        return self

    def _passive_placeholders(self) -> None:
        passive = self.passive
        assert passive is not None
        present = {
            "bonus": bool(passive.modifiers),
            "spell_damage": bool(passive.spell_damage),
            "crit_chance": passive.crit_chance is not None,
            "block_cooldown": passive.block_cooldown is not None,
            "block_cooldown_rank": passive.block_cooldown is not None,
        }
        for name in self.placeholders():
            if name not in PASSIVE_PLACEHOLDERS:
                raise ValueError(
                    f"description: unknown placeholder {{{name}}} for a passive spell; known: "
                    f"{', '.join(PASSIVE_PLACEHOLDERS)}"
                )
            if not present[name]:
                raise ValueError(f"description: {{{name}}} reads a part of the passive it does not have")

    def placeholders(self) -> list[str]:
        """The names of the placeholders in the description, in order."""
        return _PLACEHOLDER.findall(self.description.en_us)

    def first_effect(self, effect_type: str) -> BaseModel | None:
        """The first effect of ``effect_type`` in ``all_effects`` order, what a placeholder reads."""
        return next((effect for _, effect in self.all_effects() if effect.type == effect_type), None)

    def all_effects(self) -> list[tuple[str, BaseModel]]:
        """Every effect with its path, those that a projectile or an area runs included."""
        found: list[tuple[str, BaseModel]] = []
        for n, effect in enumerate(self.effects):
            found.append((f"effects.{n}", effect))
            for m, inner in enumerate(getattr(effect, "effects", ())):
                found.append((f"effects.{n}.effects.{m}", inner))
        return found


def _grows_with_level(effect: BaseModel) -> bool:
    """Whether an effect has anything that grows with the spell level."""
    return (
        getattr(effect, "per_level", 0) != 0
        or getattr(effect, "attack_damage_per_level_percent", None) is not None
        or getattr(effect, "spell_power_per_level_percent", None) is not None
        or getattr(effect, "armor_shred_per_level_percent", None) is not None
        or getattr(effect, "penetration_per_level_percent", None) is not None
        or getattr(effect, "duration_per_level_seconds", None) is not None
        or bool(getattr(effect, "stronger_at_levels", ()))
    )
