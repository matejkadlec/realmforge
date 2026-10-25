"""Custom mobs: ``content/mobs/<id>.yaml``, our own entity types (architecture 14.2, W3, RLM-33).

Entity types are a static registry, so each mob becomes a generated registration of the archetype it picks, a Java
class with its AI (``mob/custom/``): ``melee`` fights in melee, ``caster`` casts its spells through the spell engine
and steps between its casts. Everything else is the mob's data: its name, size, attributes, how it idles and hits, its
sounds, how it breaks apart, its loot (a vanilla loot table, ``data/realmforge/loot_table/entities/<id>.json``, one per
variant), its variants and its placeholder model's look. Its model and animations are placeholders until A2 (RLM-48)
brings the art. A custom mob is hostile, so it gets a level and its scaling like every hostile mob (architecture
14.1).
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
from realmforge_tools.schema.spell_school import HexColor

PositiveSize = Annotated[float, Field(ge=0.3, le=4.0, allow_inf_nan=False)]

# The archetype classes in mob/custom/ (generate/mobs.py names them).
ARCHETYPES = ("melee", "caster")
MAX_DROP_COUNT = 64
MAX_LOOTING_COUNT = 8
MAX_KNOCK_UP = 3.0
MIN_ATTACK_INTERVAL = 0.25
MAX_ATTACK_INTERVAL = 5.0

# Loot that is there only so a drop does not look empty (the vision's "trash loot like iron nuggets"): a mob never
# drops it (architecture principle 10). Validation refuses these items in a mob's loot.
TRASH_LOOT = frozenset(
    {
        "minecraft:stick",
        "minecraft:iron_nugget",
        "minecraft:gold_nugget",
        "minecraft:copper_nugget",
        "minecraft:rotten_flesh",
        "minecraft:poisonous_potato",
        "minecraft:dead_bush",
        "minecraft:dirt",
        "minecraft:gravel",
        "minecraft:cobblestone",
        "minecraft:cobbled_deepslate",
        "minecraft:wheat_seeds",
    }
)


class MobSizeDef(StrictModel):
    """The mob's hitbox in blocks; the placeholder model fills it."""

    width: PositiveSize = Field(description="Width (and depth) in blocks, 0.3 to 4.")
    height: float = Field(ge=0.3, le=6.0, allow_inf_nan=False, description="Height in blocks, 0.3 to 6.")


class MobAttributesDef(StrictModel):
    """The mob's vanilla attributes at level 1; its level scales health, armor and damage (architecture 14.1)."""

    max_health: float = Field(gt=0, le=1024, allow_inf_nan=False, description="Health points (2 per heart).")
    attack_damage: float = Field(
        ge=0, le=100, allow_inf_nan=False, description="Damage of a melee hit, in health points."
    )
    armor: float = Field(default=0, ge=0, le=30, allow_inf_nan=False, description="Armor points, 0 to 30.")
    movement_speed: float = Field(
        ge=0.05, le=0.5, allow_inf_nan=False, description="Vanilla's movement speed: a zombie's is 0.23."
    )
    follow_range: float = Field(
        default=16,
        ge=4,
        le=64,
        allow_inf_nan=False,
        description="How far in blocks it notices a player and follows its target.",
    )
    knockback_resistance_percent: float = Field(
        default=0,
        ge=0,
        le=100,
        allow_inf_nan=False,
        description="The share of knockback it shrugs off, 0 to 100.",
    )


class KitingDef(StrictModel):
    """How a caster moves between two casts (research BM-22): a quick step of ``step_blocks``, away from its target
    when it stays in range there, otherwise around the target or a little toward it, so the next cast is in range and
    in sight again. The mob AI's reusable kiting (``mob/ai/Kiting``)."""

    step_blocks: float = Field(
        default=2, ge=1, le=6, allow_inf_nan=False, description="How far one step goes, in blocks: 1 to 6."
    )
    speed_percent: float = Field(
        default=150,
        ge=50,
        le=300,
        allow_inf_nan=False,
        description="How fast it steps, in percent of its walking speed: 50 to 300.",
    )


class CasterDef(StrictModel):
    """What a caster casts and how it fights: it casts each spell at its target whenever the spell is ready and the
    target is in range and in sight, in the listed order. While it casts it stands still and faces its target; a
    target out of range or out of sight sends it after the target until it can cast again."""

    spells: UniqueResourceIds = Field(
        min_length=1,
        description="Its spells, by id (realmforge:<id>), most important first. Each targets an enemy.",
    )
    kiting: KitingDef | None = Field(
        default=None, description="Its step between two casts; without it, it stands where it cast."
    )


class MeleeDef(StrictModel):
    """How a melee mob hits."""

    attack_interval_seconds: Seconds = Field(
        default=1.0,
        description="The time between two of its hits, in seconds: vanilla's monsters hit every 1 s.",
    )
    knock_up_blocks: float = Field(
        default=0,
        ge=0,
        le=MAX_KNOCK_UP,
        allow_inf_nan=False,
        description="How high each hit throws its target, in blocks, on top of vanilla's knockback; 0 for none.",
    )

    @model_validator(mode="after")
    def _interval(self) -> MeleeDef:
        if not MIN_ATTACK_INTERVAL <= self.attack_interval_seconds <= MAX_ATTACK_INTERVAL:
            raise ValueError(
                f"attack_interval_seconds is {MIN_ATTACK_INTERVAL:g} to {MAX_ATTACK_INTERVAL:g} seconds"
            )
        return self


class DropCountDef(StrictModel):
    """How many of the item one drop gives: a whole number from ``min`` to ``max``, each as likely. A ``min`` of 0
    sometimes gives none, as vanilla's witch drops 0 to 2 glowstone dust; Looting's extra still counts then."""

    min: int = Field(ge=0, le=MAX_DROP_COUNT)
    max: int = Field(ge=1, le=MAX_DROP_COUNT)

    @model_validator(mode="after")
    def _ordered(self) -> DropCountDef:
        if self.min > self.max:
            raise ValueError(f"min ({self.min}) is more than max ({self.max})")
        return self


class DropDef(StrictModel):
    """How one drop rolls when a mob dies: on its own, so drops never crowd each other out. Looting raises it as
    vanilla's drops: more of the item, a better chance, or both."""

    count: DropCountDef = Field(
        default=DropCountDef(min=1, max=1), description="How many: from min to max, 1 by default."
    )
    chance_percent: float = Field(
        default=100,
        gt=0,
        le=100,
        allow_inf_nan=False,
        description="The chance it drops at all, in percent: 100 by default.",
    )
    looting_count: int = Field(
        default=0,
        ge=0,
        le=MAX_LOOTING_COUNT,
        description="Each level of Looting adds 0 to this many more of the item (vanilla's 0 to 1 for most drops).",
    )
    looting_chance_percent: float = Field(
        default=0,
        ge=0,
        le=100,
        allow_inf_nan=False,
        description="Each level of Looting adds this much to the chance, in percent points.",
    )

    @model_validator(mode="after")
    def _looting(self) -> DropDef:
        if self.looting_chance_percent > 0 and self.chance_percent >= 100:
            raise ValueError("looting_chance_percent raises a chance below 100 percent")
        return self


class MobDropDef(DropDef):
    """One item the mob may drop when it dies. Every drop is worth having (the vision: no trash loot)."""

    item: ResourceId = Field(description="The item, e.g. minecraft:iron_ingot.")


class MobSoundsDef(StrictModel):
    """The sounds it makes, by sound event id (ours from content/sounds/, or vanilla's); a sound left out is
    vanilla's for hostile mobs (none for ambient, and the step sound of the block it walks on)."""

    ambient: ResourceId | None = Field(default=None, description="Now and then while it lives.")
    hurt: ResourceId | None = Field(default=None, description="When it is hurt.")
    death: ResourceId | None = Field(default=None, description="When it dies.")
    step: ResourceId | Literal["silent"] | None = Field(
        default=None, description="Each step it takes; silent: it walks without a sound."
    )


class MobVariantDef(StrictModel):
    """One kind of the mob, rolled when it spawns and kept for its life: its own accent colour in its placeholder
    texture (eyes, gems), and drops on top of the mob's own."""

    id: ContentId = Field(description="The variant's id, e.g. sapphire.")
    weight: int = Field(default=1, ge=1, le=1000, description="How likely it is against the other variants.")
    accent: HexColor = Field(description='Its colour in the placeholder texture: pupils and gems, "#RRGGBB".')
    loot: list[MobDropDef] = Field(
        default_factory=list, description="What this variant drops besides the mob's own."
    )


class MobModelDef(StrictModel):
    """The placeholder model and texture, until A2 (RLM-48) brings the art: a build that fills the hitbox, a
    surface, eyes and colours."""

    build: Literal["humanoid", "slim", "golem"] = Field(
        default="humanoid",
        description="humanoid: legs, body and head in thirds; slim: an enderman's long legs and arms, a tall head; "
        "golem: a broad body with shells on its front and back, long thick arms and a big head.",
    )
    surface: Literal["speckled", "smooth", "mossy_bricks"] = Field(
        default="speckled",
        description="speckled stone; smooth stone; mossy stone bricks with moss clumps and strands hanging off, and "
        "gems (the variant's accent) in long cracks of the shells.",
    )
    eyes: Literal["dot", "tall", "gem"] = Field(
        default="dot",
        description="dot: one bright pixel each; tall: two bright pixels each; gem: 2x3 white with a two-pixel pupil "
        "in the variant's accent.",
    )
    color: HexColor = Field(description='Main colour, "#RRGGBB".')
    moss: HexColor = Field(default="#56702F", description='The moss of mossy_bricks, "#RRGGBB".')


class MobDef(StrictModel):
    """A custom mob, ``realmforge:<id>``: an entity type of one archetype with its data. Its name is the entity
    type's translation, ``entity.realmforge.<id>``, shown in Neat's card, Jade's tooltip and death messages."""

    id: ContentId
    name: LocalizedText = Field(description="The mob's name, Title Case (lore style guide).")
    archetype: Literal["melee", "caster"] = Field(
        description="melee: walks up to its target and hits it; caster: casts its spells from a distance."
    )
    size: MobSizeDef
    attributes: MobAttributesDef
    idle: Literal["stroll", "guard"] = Field(
        default="stroll",
        description="stroll (the default): wanders while it has no target, as vanilla's mobs do, at the Zombified "
        "Piglin's speed; guard: stands at the spot where it spawned and walks back to it once it loses its target.",
    )
    melee: MeleeDef | None = Field(
        default=None, description="How a melee mob hits; only for archetype melee."
    )
    caster: CasterDef | None = Field(
        default=None, description="A caster's spells and steps; only for archetype caster."
    )
    sounds: MobSoundsDef = Field(default=MobSoundsDef(), description="Its sounds.")
    death_particles: ResourceId | None = Field(
        default=None,
        description="A block, e.g. realmforge:vaultstone: the mob breaks apart into its particles when it dies, as a "
        "mined block does, and its body is gone at once.",
    )
    tags: UniqueResourceIds = Field(
        default_factory=list,
        description="Our entity type tags it belongs to, e.g. realmforge:stone_creatures, which a spell's projectile "
        "may spare.",
    )
    loot: list[MobDropDef] = Field(default_factory=list, description="What it may drop when it dies.")
    variants: list[MobVariantDef] = Field(
        default_factory=list, description="Its kinds, one rolled at spawn; none: a single kind."
    )
    model: MobModelDef = Field(description="Its placeholder model and texture.")

    @model_validator(mode="after")
    def _archetype_fields(self) -> MobDef:
        if self.archetype == "caster" and self.caster is None:
            raise ValueError("a caster names its spells: caster: {spells: [...]}")
        if self.archetype != "caster" and self.caster is not None:
            raise ValueError(f"caster is only for archetype caster, not {self.archetype}")
        if self.archetype != "melee" and self.melee is not None:
            raise ValueError(f"melee is only for archetype melee, not {self.archetype}")
        ids = [variant.id for variant in self.variants]
        repeated = sorted({variant for variant in ids if ids.count(variant) > 1})
        if repeated:
            raise ValueError(f"one variant per id: {', '.join(repeated)}")
        if len(self.variants) == 1:
            raise ValueError("a mob of one kind needs no variants: name two or more, or none")
        if self.model.eyes == "gem" and not self.variants:
            raise ValueError("gem eyes take their pupils from the variants' accents: name the variants")
        for drops in [self.loot] + [self.loot + variant.loot for variant in self.variants]:
            items = [drop.item for drop in drops]
            repeated = sorted({item for item in items if items.count(item) > 1})
            if repeated:
                raise ValueError(f"one drop per item: {', '.join(repeated)}")
        return self


def melee_of(mob: MobDef) -> MeleeDef:
    """How ``mob`` hits: its ``melee``, or vanilla's defaults."""
    return mob.melee if mob.melee is not None else MeleeDef()


def mob_id(mob: str) -> str:
    """The full id of a mob in ``content/mobs/``: ``realmforge:<id>``."""
    return f"{MOD_ID}:{mob}"


def mob_description_id(mob: str) -> str:
    """The entity type's translation key, ``entity.realmforge.<id>`` (``EntityType#getDescriptionId``)."""
    return f"entity.{MOD_ID}.{mob}"


def mob_loot_table(mob: str, variant: str | None = None) -> str:
    """The loot table of the mob's entity type, vanilla's default: ``realmforge:entities/<id>``; a variant's,
    with the mob's own drops and its own: ``realmforge:entities/<id>/<variant>``."""
    return f"{MOD_ID}:entities/{mob}" if variant is None else f"{MOD_ID}:entities/{mob}/{variant}"
