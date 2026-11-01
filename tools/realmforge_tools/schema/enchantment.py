"""Enchantments: ``content/enchantments/<id>.yaml``, vanilla's data-driven enchantments at
``data/realmforge/enchantment/<id>.json`` (architecture 13).

An enchantment of ours is a protection for now (RLM-537, Magic Protection): protection points per level in vanilla's
shared protection pool against the hits of one damage type tag, as Projectile and Blast Protection give against
theirs. The pool is capped at 20 points, 80 % (the pack's Apothic Attributes formula, vanilla's). Its table, loot and
anvil numbers are vanilla's fields (``Enchantment.EnchantmentDefinition``); ``max_level`` is its highest level with
Apothic Enchanting, which raises the protections past vanilla's IV (its data map ``apothic_enchanting:enchantment_info``),
and ``vanilla_max_level`` vanilla's own, which counts where Apothic Enchanting does not reach.

Every protection, vanilla's four and ours, is in ``#realmforge:protections``: a piece of gear holds at most two
different ones (Matěj, 2026-10-30), which the anvil, the enchanting table and loot follow (``item/ProtectionLimit``).
"""

from __future__ import annotations

from typing import Literal

from pydantic import Field, model_validator

from realmforge_tools.project import MOD_ID
from realmforge_tools.schema.common import (
    ContentId,
    LocalizedText,
    ResourceId,
    StrictModel,
    UniqueResourceIds,
)

# The tag of every protection enchantment: at most two different ones on one piece (RLM-537).
PROTECTIONS_TAG = f"{MOD_ID}:protections"
VANILLA_PROTECTIONS: tuple[str, ...] = (
    "minecraft:protection",
    "minecraft:fire_protection",
    "minecraft:blast_protection",
    "minecraft:projectile_protection",
)
# Vanilla's tag that makes Fire, Blast and Projectile Protection exclude each other; Apothic Enchanting already takes
# Protection out of it. Ours takes the other three out, so any two protections combine (the limit counts them instead).
ARMOR_EXCLUSIVE_SET = "minecraft:exclusive_set/armor"
# Vanilla's EquipmentSlotGroup names, which an enchantment's slots take.
SlotGroup = Literal[
    "any", "mainhand", "offhand", "hand", "head", "chest", "legs", "feet", "armor", "body", "saddle"
]


class EnchantmentCostDef(StrictModel):
    """Vanilla's level-based cost: ``base`` at level I, ``per_level_above_first`` more for each level after it."""

    base: int = Field(ge=0, le=1000, description="The cost at level I.")
    per_level_above_first: int = Field(ge=0, le=1000, description="What each level after I adds.")


class EnchantmentProtectionDef(StrictModel):
    """Protection points in vanilla's shared pool against the hits of one damage type tag (its
    ``minecraft:damage_protection`` effect, as Projectile Protection's)."""

    points_per_level: float = Field(
        gt=0,
        le=20,
        allow_inf_nan=False,
        description=(
            "Protection points each level gives, e.g. 0.5; a point lowers a hit by 4 %, and all protection points on "
            "the armor together stop at 20 (80 %)."
        ),
    )
    against: ResourceId = Field(
        description=(
            "The damage type tag of the hits it lowers, without '#', e.g. realmforge:is_magic (every magic school "
            "and vanilla's magic) or minecraft:is_projectile."
        )
    )


class EnchantmentDef(StrictModel):
    """An enchantment: its name and tooltip line, where and how it is enchanted, and what it does."""

    id: ContentId
    name: LocalizedText = Field(description="Its name, as the tooltip and the enchanting table show it.")
    description: LocalizedText = Field(
        description=(
            "Its tooltip line under the name (Apothic Enchanting's inline descriptions, Enchantment Descriptions' "
            "key): which hits it lowers."
        )
    )
    supported_items: ResourceId = Field(
        description="The item tag it can go on, without '#', e.g. minecraft:enchantable/armor."
    )
    slots: list[SlotGroup] = Field(
        min_length=1, description="Where the item must be for it to work, e.g. [armor]."
    )
    weight: int = Field(
        ge=1, le=1024, description="How often the table and loot pick it (Projectile Protection 5)."
    )
    vanilla_max_level: int = Field(
        ge=1,
        le=255,
        description="Vanilla's highest level, which counts where Apothic Enchanting does not (IV).",
    )
    max_level: int = Field(
        ge=1,
        le=255,
        description="The highest level with Apothic Enchanting, e.g. 8 (its enchantment_info data map).",
    )
    min_cost: EnchantmentCostDef = Field(description="The lowest enchanting power that gives each level.")
    max_cost: EnchantmentCostDef = Field(description="The highest enchanting power that gives each level.")
    anvil_cost: int = Field(ge=0, le=100, description="Its anvil price per level (half from a book).")
    protection: EnchantmentProtectionDef = Field(description="The protection it gives.")
    tags: UniqueResourceIds = Field(
        default_factory=list,
        description=(
            "Vanilla's enchantment tags it joins, e.g. minecraft:non_treasure: the enchanting table, random loot, "
            "mobs' and traders' gear, as the other protections."
        ),
    )

    @model_validator(mode="after")
    def _levels(self) -> EnchantmentDef:
        if self.max_level < self.vanilla_max_level:
            raise ValueError(
                f"max_level {self.max_level} is below vanilla_max_level {self.vanilla_max_level}: Apothic Enchanting "
                "only raises a level"
            )
        return self
