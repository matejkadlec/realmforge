"""Apotheosis affixes: ``content/apotheosis_affixes/<id>.yaml`` -> ``data/apotheosis/apotheosis/affixes/<path>.json``
(ADR 0038, architecture 13 and 19.4).

Apotheosis defines its affixes as data, and a file in our data pack at the same path replaces one of them: the core
mod loads after Apotheosis, so its data pack sits above Apotheosis's. An entry names the affix it replaces in
``affix`` and holds the affix's whole definition, because Apotheosis reads our file instead of its own and merges
nothing. Only attribute affixes (``apotheosis:attribute``) so far; RLM-272 adds the other kinds with the stat
remapping.

Names and units are Apotheosis 9.1.0's (``AttributeAffix``, ``AffixDefinition``): ``values`` per rarity as a step
function, ``weight`` and ``quality`` from its ``definition.weights``. The constants below list what Apotheosis 9.1.0
registers; update them with an Apotheosis update.
"""

from __future__ import annotations

from typing import Annotated, Literal

from pydantic import AfterValidator, Field, StringConstraints, model_validator

from realmforge_tools.schema.common import ContentId, ResourceId, StrictModel, UniqueResourceIds

ATTRIBUTE_AFFIX = "apotheosis:attribute"

# The loot categories Apotheosis 9.1.0 registers (Apoth.LootCategories), without apotheosis:none.
LOOT_CATEGORIES = (
    "apotheosis:bow",
    "apotheosis:breaker",
    "apotheosis:helmet",
    "apotheosis:chestplate",
    "apotheosis:leggings",
    "apotheosis:boots",
    "apotheosis:shield",
    "apotheosis:trident",
    "apotheosis:melee_weapon",
    "apotheosis:shears",
)
# Registered only when Curios is installed (compat/curios/CuriosCompat), which the pack is not yet: Apotheosis skips
# a whole affix that lists one, with an error (RLM-271, RLM-273).
CURIOS_LOOT_CATEGORIES = ("apotheosis:charm",)

# Apotheosis 9.1.0's rarities (data/apotheosis/apotheosis/rarities/), lowest first; mythic shows as Legendary
# (RLM-272).
RARITIES = (
    "apotheosis:common",
    "apotheosis:uncommon",
    "apotheosis:rare",
    "apotheosis:epic",
    "apotheosis:mythic",
)

AffixId = Annotated[
    str,
    StringConstraints(pattern=r"^apotheosis:[a-z0-9_]+(/[a-z0-9_]+)*$"),
    Field(description="An Apotheosis affix, e.g. apotheosis:generic/attribute/lucky."),
]


def _check_categories(categories: list[str]) -> list[str]:
    for category in categories:
        if category in CURIOS_LOOT_CATEGORIES:
            raise ValueError(
                f"{category} exists only with Curios, which the pack lacks: Apotheosis would skip the whole affix"
            )
        if category not in LOOT_CATEGORIES:
            raise ValueError(
                f"unknown loot category {category!r}; Apotheosis has {', '.join(LOOT_CATEGORIES)}"
            )
    return categories


class StepValuesDef(StrictModel):
    """The values an affix rolls at one rarity: from ``min`` to ``max`` in steps of ``step``."""

    min: float = Field(allow_inf_nan=False, description="The lowest value.")
    max: float = Field(allow_inf_nan=False, description="The highest value, at least min.")
    step: float = Field(
        gt=0, allow_inf_nan=False, description="The step between values (Apotheosis's default 0.01)."
    )

    @model_validator(mode="after")
    def _ordered(self) -> StepValuesDef:
        if self.max < self.min:
            raise ValueError(f"max {self.max} is below min {self.min}")
        return self


class ApotheosisAffixDef(StrictModel):
    """One of Apotheosis's attribute affixes, replaced by this definition."""

    id: ContentId
    affix: AffixId = Field(
        description="The Apotheosis affix this entry replaces, e.g. apotheosis:generic/attribute/lucky."
    )
    type: Literal["apotheosis:attribute"] = Field(
        description="The affix kind; only apotheosis:attribute (an attribute modifier) so far."
    )
    attribute: ResourceId = Field(description="The attribute the affix modifies, e.g. minecraft:luck.")
    operation: Literal["add_value", "add_multiplied_base", "add_multiplied_total"] = Field(
        description="How the modifier applies, as a vanilla attribute modifier's operation."
    )
    affix_type: Literal["stat", "basic_effect", "ability"] = Field(
        description="Apotheosis's kind of affix, which decides the rarities it can roll on: stat from common up."
    )
    exclusive_set: list[AffixId] = Field(
        default_factory=list, description="Affixes that never roll together with this one."
    )
    weight: int = Field(ge=0, description="How often it rolls against other affixes (Apotheosis's weight).")
    quality: float = Field(
        ge=0, allow_inf_nan=False, description="Apotheosis's quality: luck's effect on the weight."
    )
    categories: Annotated[UniqueResourceIds, AfterValidator(_check_categories), Field(min_length=1)] = Field(
        description="The loot categories it rolls on, e.g. apotheosis:melee_weapon."
    )
    values: dict[str, StepValuesDef] = Field(
        min_length=1,
        description="Its values per rarity, keyed by rarity (apotheosis:common to apotheosis:mythic).",
    )

    @model_validator(mode="after")
    def _known_rarities(self) -> ApotheosisAffixDef:
        unknown = sorted(set(self.values) - set(RARITIES))
        if unknown:
            raise ValueError(f"unknown rarities {', '.join(unknown)}; Apotheosis has {', '.join(RARITIES)}")
        return self

    @property
    def data_path(self) -> str:
        """The affix file's path below ``data/``: ``apotheosis/apotheosis/affixes/<path>.json``."""
        namespace, path = self.affix.split(":", 1)
        return f"{namespace}/apotheosis/affixes/{path}.json"
