"""Netherite upgrades (RLM-566; gear design, "Netherite upgrades"): an upgrade item that a smithing table puts on one
netherite piece, at most one per piece and no set bonuses (Matěj, 2026-10-15). Its bonus follows the piece's
archetype (Matěj, 2026-10-30, RLM-511): a melee piece (vanilla's netherite armor) gets ``melee``, a caster piece (our
netherite robes) ``caster``; the ranged column waits for the first ranged set (M3).

``content/netherite_upgrades/<id>.yaml`` becomes an entry of the datapack registry ``realmforge:netherite_upgrade``
(``generate/datapack.py``) and a smithing recipe of ours, ``data/realmforge/recipe/netherite_upgrade/<id>.json``
(``generate/netherite_upgrades.py``): the template, a netherite piece of ``#realmforge:netherite_upgradable`` and the
upgrade's ``item`` make the same piece with the data component ``realmforge:netherite_upgrade`` naming this entry. The
bonus is read from the entry while the piece is worn, so a rebalance reaches pieces that already carry it
(architecture 13).
"""

from __future__ import annotations

from pydantic import Field, model_validator

from realmforge_tools.project import MOD_ID
from realmforge_tools.schema.common import ContentId, LocalizedText, ResourceId, StrictModel
from realmforge_tools.schema.item import (
    NETHERITE_UPGRADABLE_CASTER_TAG,
    NETHERITE_UPGRADABLE_MELEE_TAG,
    NETHERITE_UPGRADABLE_TAG,
    GearModifierDef,
)
from realmforge_tools.schema.recipe import Ingredient
from realmforge_tools.schema.spell_school import HexColor

__all__ = [
    "NETHERITE_UPGRADABLE_CASTER_TAG",
    "NETHERITE_UPGRADABLE_MELEE_TAG",
    "NETHERITE_UPGRADABLE_TAG",
    "NETHERITE_UPGRADE_RECIPE_TYPE",
    "NetheriteUpgradeDef",
]

# The recipe type of ours that puts an upgrade on a piece (item/NetheriteUpgradeRecipe).
NETHERITE_UPGRADE_RECIPE_TYPE = f"{MOD_ID}:netherite_upgrade"


class NetheriteUpgradeDef(StrictModel):
    """One netherite upgrade: the item that carries it to the smithing table, the line the piece's tooltip shows, and
    its bonus per archetype of the piece. The bonus counts while the piece is worn, in the piece's armor slot."""

    id: ContentId
    name: LocalizedText = Field(
        description='The line the upgraded piece\'s tooltip shows, e.g. "Ruby Upgrade".'
    )
    color: HexColor = Field(description='The colour of that line, "#RRGGBB": its gem\'s.')
    item: ResourceId = Field(
        description="The upgrade item a smithing table takes as the addition, e.g. realmforge:ruby_upgrade: one of our "
        "items that is no gear and carries no other upgrade."
    )
    template: Ingredient = Field(
        description="The smithing template the recipe takes, e.g. minecraft:netherite_upgrade_smithing_template."
    )
    melee: list[GearModifierDef] = Field(
        min_length=1,
        description="The bonus on a melee piece (vanilla's netherite armor), e.g. {attribute: "
        "minecraft:attack_damage, operation: add_multiplied_base, amount_percent: 5}.",
    )
    caster: list[GearModifierDef] = Field(
        min_length=1,
        description="The bonus on a caster piece (our netherite robes), e.g. {attribute: realmforge:spell_power, "
        "operation: add_multiplied_base, amount_percent: 5}.",
    )

    @model_validator(mode="after")
    def _one_modifier_per_attribute(self) -> NetheriteUpgradeDef:
        for archetype, modifiers in (("melee", self.melee), ("caster", self.caster)):
            attributes = [modifier.attribute for modifier in modifiers]
            repeated = sorted({attribute for attribute in attributes if attributes.count(attribute) > 1})
            if repeated:
                raise ValueError(f"{archetype}: one modifier per attribute: {', '.join(repeated)} repeats")
        return self

    def archetypes(self) -> list[tuple[str, list[GearModifierDef]]]:
        """Its bonus per archetype, by field name."""
        return [("melee", self.melee), ("caster", self.caster)]
