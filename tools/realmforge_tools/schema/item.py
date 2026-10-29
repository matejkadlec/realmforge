"""Items: ``content/items/<id>.yaml``."""

from __future__ import annotations

from typing import Any, Literal, get_args

from pydantic import Field, model_validator

from realmforge_tools.project import MOD_ID
from realmforge_tools.schema.character import CharacterModifierDef, FiniteNumber
from realmforge_tools.schema.common import (
    ContentId,
    LocalizedText,
    ResourceId,
    StrictModel,
    UniqueResourceIds,
)
from realmforge_tools.schema.mob import DropDef
from realmforge_tools.schema.recipe import Ingredient, RecipeDef
from realmforge_tools.schema.spell_school import HexColor

# What right-clicking an item can do (architecture 13). Each use is game code in the feature it belongs to, which finds
# its items through the item tag realmforge:use/<use> that the pipeline writes; each has its tooltip line, the text
# <USE_TEXT_GROUP>.realmforge.<use> in content/texts/.
ItemUse = Literal["unlearn_stat_points"]
ITEM_USES: tuple[str, ...] = get_args(ItemUse)
USE_TEXT_GROUP = "item_use"

# What a piece of gear is (architecture 13): a wand, a caster's weapon whose stats count in the main hand, or armor
# worn in one of the four armor slots.
GearKind = Literal["wand", "head", "chest", "legs", "feet"]
ARMOR_KINDS: tuple[str, ...] = ("head", "chest", "legs", "feet")
# The item tag of every wand: the spell engine reads it, so casting a spell wears the wand in the main hand (RLM-172).
WANDS_TAG = f"{MOD_ID}:wands"
# The item tag of two-handed gear: while one is in a player's main hand, no shield can be held, and every shield stays
# locked for 3 minutes after (item/TwoHandedGear; Matěj, 2026-10-26). Not Better Combat's two-handed weapons, which a
# player raises with right click to block (combat/WeaponBlocks).
TWO_HANDED_TAG = f"{MOD_ID}:two_handed"
# A player's attack damage and attack speed without a weapon (vanilla's base values): a weapon's tooltip numbers are
# its modifiers plus these.
PLAYER_ATTACK_DAMAGE = 1.0
PLAYER_ATTACK_SPEED = 4.0
# Vanilla's leather armor equip sound, the default for cloth.
LEATHER_EQUIP_SOUND = "minecraft:item.armor.equip_leather"
# Attributes gear never modifies directly (architecture 8.2): max health comes from primary stats only, and a wand's
# attack damage and speed come from its attack_damage and attack_speed.
GEAR_FORBIDDEN_ATTRIBUTES: dict[str, str] = {
    "minecraft:max_health": "gear never adds max health directly (architecture 8.2): give vitality instead",
    "minecraft:attack_damage": "a weapon's attack damage is its attack_damage",
    "minecraft:attack_speed": "a weapon's attack speed is its attack_speed",
}


def use_tag(use: str) -> str:
    """The item tag of the items with ``use``: ``realmforge:use/<use>``."""
    return f"{MOD_ID}:use/{use}"


def entity_loot_table(entity: str) -> str:
    """A mob's own loot table, as vanilla names it: ``minecraft:witch`` -> ``minecraft:entities/witch``."""
    namespace, path = entity.split(":", 1)
    return f"{namespace}:entities/{path}"


def drop_path(item_id: str, entity: str) -> str:
    """Where the loot table and the loot modifier of ``item_id``'s drop by ``entity`` live, under ``loot_table/`` and
    ``loot_modifiers/``: ``item_drops/<item>/<entity namespace>/<entity path>``."""
    namespace, path = entity.split(":", 1)
    return f"item_drops/{item_id}/{namespace}/{path}"


class GearModifierDef(CharacterModifierDef):
    """A fixed stat the gear gives while it is in its slot: a wand in the main hand, armor where it is worn. Units
    follow the stat rules: ``add_value`` on one of our stats takes its unit (``amount_percent`` for a share, ``amount``
    for points); multiplied operations take ``amount_percent``."""


class GearDef(StrictModel):
    """What makes the item a piece of gear (architecture 13): its kind and slot, its durability, how it is enchanted
    and repaired, and its fixed stats, vanilla attribute modifiers with one id per slot and modifier
    (``realmforge:gear/<slot>/<n>``, architecture 8.2). Gear stacks to 1."""

    kind: GearKind = Field(
        description=(
            "wand: a caster's weapon, its stats in the main hand only, worn down by every spell its holder casts "
            "and every hit, as a sword by hits; head, chest, legs, feet: armor worn in that slot, worn down by hits "
            "taken."
        )
    )
    durability: int = Field(ge=1, le=100000, description="Uses before it breaks (vanilla's max damage).")
    enchantability: int | None = Field(
        default=None,
        ge=1,
        le=100,
        description=(
            "Vanilla's enchantment value: higher gets better enchantments at the table (iron armor 9, gold tools 22). "
            "Unset: the enchanting table refuses it. A wand takes the durability enchantments (Unbreaking, Mending), "
            "armor those of its slot's vanilla armor."
        ),
    )
    repair_with: Ingredient | None = Field(
        default=None,
        description=(
            "The material that repairs it in an anvil: an item (realmforge:spellweave) or an item tag "
            "('#minecraft:stone_tool_materials'). Unset: only two of it combine."
        ),
    )
    attack_damage: FiniteNumber | None = Field(
        default=None,
        gt=0,
        description="A wand's attack damage, as its tooltip shows it (a bare hand is 1). Wands only, and required.",
    )
    attack_speed: FiniteNumber | None = Field(
        default=None,
        gt=0,
        description="A wand's attacks per second, as its tooltip shows it (a bare hand is 4). Wands only, and required.",
    )
    look: ContentId | None = Field(
        default=None,
        description=(
            "Armor only, and required: how it looks worn, assets/realmforge/equipment/<look>.json, shared by a set "
            "(spellweave). Its texture is a placeholder in the item's colour until the set's art exists."
        ),
    )
    equip_sound: ResourceId = Field(
        default=LEATHER_EQUIP_SOUND,
        description="Armor only: the sound of putting it on, leather's by default.",
    )
    two_handed: bool = Field(
        default=False,
        description=(
            "Wands only: it takes both hands, so no shield can be held with it. While it is in the main hand every "
            "shield is locked and stays locked for 3 minutes after; a locked shield taken in hand goes into the bag, "
            "or drops at the player's feet when the bag is full."
        ),
    )
    modifiers: list[GearModifierDef] = Field(
        default_factory=list,
        description="Its fixed stats while in its slot, e.g. {attribute: realmforge:spell_power, operation: add_value, amount_percent: 15}.",
    )

    @model_validator(mode="after")
    def _kind_fields(self) -> GearDef:
        if self.kind == "wand":
            if self.attack_damage is None or self.attack_speed is None:
                raise ValueError("a wand needs attack_damage and attack_speed")
            if self.look is not None or "equip_sound" in self.model_fields_set:
                raise ValueError("a wand is held, not worn: no look and no equip_sound")
        else:
            if self.look is None:
                raise ValueError(f"{self.kind} armor needs a look: how it looks worn")
            if self.attack_damage is not None or self.attack_speed is not None:
                raise ValueError("attack_damage and attack_speed are a wand's")
            if self.two_handed:
                raise ValueError("only a wand is two-handed: armor is worn, not held")
        attributes = [modifier.attribute for modifier in self.modifiers]
        repeated = sorted({attribute for attribute in attributes if attributes.count(attribute) > 1})
        if repeated:
            raise ValueError(f"one modifier per attribute: {', '.join(repeated)} repeats")
        return self

    @property
    def slot(self) -> str:
        """The equipment slot its stats count in: ``mainhand`` for a wand, else its armor slot."""
        return "mainhand" if self.kind == "wand" else self.kind

    def modifier_id(self, n: int) -> str:
        """The id of its ``n``-th modifier, unique per slot and position (architecture 8.2)."""
        return f"{MOD_ID}:gear/{self.slot}/{n}"


class ItemDropDef(DropDef):
    """The item dropped by a mob that is not ours (a vanilla witch): added to the mob's own loot by a NeoForge loot
    modifier, which leaves its loot table as it is. Our own mobs list their drops in their ``loot``
    (``content/mobs/``)."""

    entity: ResourceId = Field(description="The mob, e.g. minecraft:witch.")


class ItemDef(StrictModel):
    """A simple item. Generates its registration, model, lang entries and (for now) a placeholder texture; a use and a
    flavor line become its tooltip, a recipe its recipe, and gear its durability, slot and fixed stats."""

    id: ContentId
    name: LocalizedText = Field(description="Display name.")
    max_stack_size: int = Field(
        default=64, ge=1, le=99, description="Maximum stack size (1-99); gear always stacks to 1."
    )
    fire_resistant: bool = Field(
        default=False, description="Fire and lava do not destroy the dropped item, as netherite's."
    )
    creative_tabs: UniqueResourceIds = Field(
        default_factory=list,
        description="Creative mode tabs to list the item in, e.g. minecraft:ingredients. No duplicates.",
    )
    use: ItemUse | None = Field(
        default=None,
        description=(
            "What right-clicking the item does; one item is used up each time. unlearn_stat_points: every spent "
            "stat point is unspent again (RLM-196)."
        ),
    )
    flavor: LocalizedText | None = Field(
        default=None,
        description="A flavor line in the tooltip: one sentence, no mechanics, at most 12 words (lore style guide).",
    )
    recipe: RecipeDef | None = Field(
        default=None,
        description="Optional: the recipe that makes the item: shaped, shapeless or a smithing upgrade.",
    )
    gear: GearDef | None = Field(
        default=None, description="Optional: makes the item a wand or a piece of armor, with its fixed stats."
    )
    dropped_by: list[ItemDropDef] = Field(
        default_factory=list,
        description="Mobs that are not ours (vanilla's) that drop the item, one entry per mob.",
    )
    color: HexColor | None = Field(
        default=None,
        description='Main colour of its placeholder texture, "#RRGGBB"; by default a colour from a hash of its id.',
    )

    @model_validator(mode="before")
    @classmethod
    def _gear_stacks_to_one(cls, data: Any) -> Any:
        if isinstance(data, dict) and data.get("gear") is not None and "max_stack_size" not in data:
            return {**data, "max_stack_size": 1}
        return data

    @model_validator(mode="after")
    def _gear(self) -> ItemDef:
        if self.gear is not None:
            if self.max_stack_size != 1:
                raise ValueError("gear stacks to 1: leave max_stack_size out")
            if self.use is not None:
                raise ValueError("gear has no use: right-clicking armor puts it on")
        entities = [drop.entity for drop in self.dropped_by]
        repeated = sorted({entity for entity in entities if entities.count(entity) > 1})
        if repeated:
            raise ValueError(f"one dropped_by entry per mob: {', '.join(repeated)} repeats")
        return self
