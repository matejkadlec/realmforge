"""Custom mobs: ``content/mobs/<id>.yaml``, our own entity types (architecture 14.2, W3, RLM-33).

Entity types are a static registry, so each mob becomes a generated registration of the archetype it picks, a Java
class with its AI (``mob/custom/``): ``melee`` fights in melee, ``caster`` casts its spells through the spell engine
and keeps its distance. Everything else is the mob's data: its name, size, attributes, how it idles, its sounds, its
loot (a vanilla loot table, ``data/realmforge/loot_table/entities/<id>.json``) and the colour of its placeholder
texture. Its model and animations are placeholders until A2 (RLM-48) brings the art. A custom mob is hostile, so it
gets a level and its scaling like every hostile mob (architecture 14.1).
"""

from __future__ import annotations

from typing import Annotated, Literal

from pydantic import Field, model_validator

from realmforge_tools.project import MOD_ID
from realmforge_tools.schema.common import (
    ContentId,
    LocalizedText,
    ResourceId,
    StrictModel,
    UniqueResourceIds,
)
from realmforge_tools.schema.spell_school import HexColor

PositiveSize = Annotated[float, Field(ge=0.3, le=4.0, allow_inf_nan=False)]

# The archetype classes in mob/custom/ (generate/mobs.py names them).
ARCHETYPES = ("melee", "caster")
MAX_KEEP_DISTANCE = 16.0
MAX_DROP_COUNT = 64

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


class CasterDef(StrictModel):
    """What a caster casts and how it fights: it casts each spell at its target whenever the spell is ready and the
    target is in range and in sight, in the listed order, and backs away from a target nearer than
    ``keep_distance``."""

    spells: UniqueResourceIds = Field(
        min_length=1,
        description="Its spells, by id (realmforge:<id>), most important first. Each targets an enemy.",
    )
    keep_distance: float = Field(
        ge=0,
        le=MAX_KEEP_DISTANCE,
        allow_inf_nan=False,
        description="It backs away from a target nearer than this many blocks; 0 never backs away.",
    )


class DropCountDef(StrictModel):
    """How many of the item one drop gives: a whole number from ``min`` to ``max``, each as likely."""

    min: int = Field(ge=1, le=MAX_DROP_COUNT)
    max: int = Field(ge=1, le=MAX_DROP_COUNT)

    @model_validator(mode="after")
    def _ordered(self) -> DropCountDef:
        if self.min > self.max:
            raise ValueError(f"min ({self.min}) is more than max ({self.max})")
        return self


class MobDropDef(StrictModel):
    """One item the mob may drop when it dies: rolled on its own, so drops never crowd each other out. Every drop is
    worth having (the vision: no trash loot)."""

    item: ResourceId = Field(description="The item, e.g. minecraft:iron_ingot.")
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


class MobSoundsDef(StrictModel):
    """The sounds it makes, by sound event id; a sound left out is vanilla's for hostile mobs (none for ambient
    and the step sound of the block it walks on)."""

    ambient: ResourceId | None = Field(default=None, description="Now and then while it lives.")
    hurt: ResourceId | None = Field(default=None, description="When it is hurt.")
    death: ResourceId | None = Field(default=None, description="When it dies.")
    step: ResourceId | None = Field(default=None, description="Each step it takes.")


class MobDef(StrictModel):
    """A custom mob, ``realmforge:<id>``: an entity type of one archetype with its data. Its name is the entity
    type's translation, ``entity.realmforge.<id>``, shown in Neat's card, Jade's tooltip and death messages."""

    id: ContentId
    name: LocalizedText = Field(description="The mob's name, Title Case (lore style guide).")
    archetype: Literal["melee", "caster"] = Field(
        description="melee: walks up to its target and hits it; caster: casts its spells and keeps its distance."
    )
    size: MobSizeDef
    attributes: MobAttributesDef
    idle: Literal["stroll", "guard"] = Field(
        default="stroll",
        description="stroll: wanders while it has no target; guard: stands at the spot where it spawned and walks "
        "back to it once it loses its target.",
    )
    caster: CasterDef | None = Field(
        default=None, description="A caster's spells; only for archetype caster."
    )
    sounds: MobSoundsDef = Field(default=MobSoundsDef(), description="Its sounds.")
    loot: list[MobDropDef] = Field(default_factory=list, description="What it may drop when it dies.")
    color: HexColor = Field(description='Main colour of its placeholder texture, "#RRGGBB".')

    @model_validator(mode="after")
    def _archetype_fields(self) -> MobDef:
        if self.archetype == "caster" and self.caster is None:
            raise ValueError("a caster names its spells: caster: {spells: [...], keep_distance: ...}")
        if self.archetype != "caster" and self.caster is not None:
            raise ValueError(f"caster is only for archetype caster, not {self.archetype}")
        items = [drop.item for drop in self.loot]
        repeated = sorted({item for item in items if items.count(item) > 1})
        if repeated:
            raise ValueError(f"one drop per item: {', '.join(repeated)}")
        return self


def mob_id(mob: str) -> str:
    """The full id of a mob in ``content/mobs/``: ``realmforge:<id>``."""
    return f"{MOD_ID}:{mob}"


def mob_description_id(mob: str) -> str:
    """The entity type's translation key, ``entity.realmforge.<id>`` (``EntityType#getDescriptionId``)."""
    return f"entity.{MOD_ID}.{mob}"


def mob_loot_table(mob: str) -> str:
    """The loot table of the mob's entity type, vanilla's default: ``realmforge:entities/<id>``."""
    return f"{MOD_ID}:entities/{mob}"
