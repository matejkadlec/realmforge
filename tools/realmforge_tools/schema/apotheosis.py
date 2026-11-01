"""Apotheosis data, fitted to Realmforge (ADR 0038, ADR 0063, architecture 19.4).

Apotheosis defines its affixes, gems, tier augments and rarities as data, and a file in our data pack at the same path
replaces one of them: the core mod loads after Apotheosis, so its data pack sits above Apotheosis's, and Apotheosis
reads our file instead of its own and merges nothing. So every file we write holds a whole entry. The entries start
from the snapshot of Apotheosis's own data (``tools/id_universe/apotheosis-<version>.json``, written by
``rf ids apotheosis``), and ``content/`` says what changes:

- ``content/apotheosis_stat_remaps/``: an Apothic Attributes stat that our stat replaces in every affix, gem bonus
  and tier augment that grants it, or that is switched off there;
- ``content/apotheosis_affixes/``, ``apotheosis_gems/``, ``apotheosis_tier_augments/``, ``apotheosis_rarities/``: one
  entry switched off (``disabled``) or with some of its top-level fields replaced (``replace``), in Apotheosis's own
  format;
- ``content/recipe_removals/``: recipes that no longer exist, Apotheosis's or vanilla's.

``realmforge_tools/apotheosis.py`` applies them; names and units are Apotheosis 9.1.0's. The constants below list what
Apotheosis 9.1.0 registers in Java; update them with an Apotheosis update.
"""

from __future__ import annotations

from typing import Annotated, ClassVar, Literal

from pydantic import Field, JsonValue, StringConstraints, model_validator

from realmforge_tools.schema.common import ContentId, ResourceId, StrictModel, UniqueResourceIds

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
# (RLM-272, the core mod's assets/apotheosis/lang/en_us.json).
RARITIES = (
    "apotheosis:common",
    "apotheosis:uncommon",
    "apotheosis:rare",
    "apotheosis:epic",
    "apotheosis:mythic",
)

# An Apotheosis data entry, by its id: apotheosis:melee/attribute/intricate, apotheosis:core/breach.
ApotheosisEntryId = Annotated[
    str,
    StringConstraints(pattern=r"^[a-z0-9_.-]+:[a-z0-9_]+(/[a-z0-9_]+)*$"),
    Field(
        description="An Apotheosis entry, e.g. apotheosis:melee/attribute/intricate. Validated against the snapshot."
    ),
]

# An attribute of Apothic Attributes, Apotheosis's attribute library.
ApothicAttributeId = Annotated[
    str,
    StringConstraints(pattern=r"^apothic_attributes:[a-z0-9_]+$"),
    Field(description="An Apothic Attributes stat, e.g. apothic_attributes:crit_chance."),
]


class ApotheosisEntryDef(StrictModel):
    """One Apotheosis entry, switched off or with some of its top-level fields replaced. Subclasses name the entry's
    kind (the folder below ``data/<namespace>/apotheosis/``) and the field that names the entry."""

    KIND: ClassVar[str]
    TARGET_FIELD: ClassVar[str]
    CAN_DISABLE: ClassVar[bool] = True

    id: ContentId
    disabled: bool = Field(
        default=False,
        description="true switches the entry off: Apotheosis skips it, as if it did not exist (neoforge:never).",
    )
    replace: dict[str, JsonValue] = Field(
        default_factory=dict,
        description=(
            "Top-level fields of the entry, each replaced as a whole, in Apotheosis's own format, e.g. categories: "
            "[apotheosis:bow]. Every field must exist in the entry and keep its JSON type."
        ),
    )

    @model_validator(mode="after")
    def _one_change(self) -> ApotheosisEntryDef:
        if self.disabled and self.replace:
            raise ValueError("an entry is either disabled or has fields replaced, not both")
        if self.disabled and self.converted:
            raise ValueError("a switched-off affix becomes no attribute affix: drop disabled or to_attribute")
        if not self.disabled and not self.replace and not self.converted:
            raise ValueError("say what changes: disabled: true, or the fields to replace")
        if self.disabled and not self.CAN_DISABLE:
            raise ValueError(f"Apotheosis needs every one of its {self.KIND}: replace fields instead")
        return self

    @property
    def converted(self) -> bool:
        """Whether the entry becomes another kind of entry (an affix's ``to_attribute``)."""
        return False

    @property
    def target(self) -> str:
        """The Apotheosis entry this one changes."""
        return getattr(self, self.TARGET_FIELD)


class ApotheosisAffixDef(ApotheosisEntryDef):
    """One of Apotheosis's affixes, switched off or with fields replaced."""

    KIND = "affixes"
    TARGET_FIELD = "affix"

    affix: ApotheosisEntryId = Field(
        description="The affix it changes, e.g. apotheosis:generic/attribute/lucky."
    )
    to_attribute: ResourceId | None = Field(
        default=None,
        description=(
            "Turns a damage reduction affix (apotheosis:damage_reduction) into an attribute affix of this stat of ours, "
            "a share (content/stats/), e.g. realmforge:fall_resistance: add_value with the affix's own values, its "
            "categories and definition kept (RLM-233). replace applies first."
        ),
    )

    @property
    def converted(self) -> bool:
        return self.to_attribute is not None


class ApotheosisGemDef(ApotheosisEntryDef):
    """One of Apotheosis's gems, switched off or with fields replaced (``bonuses`` replaces all its bonuses)."""

    KIND = "gems"
    TARGET_FIELD = "gem"

    gem: ApotheosisEntryId = Field(description="The gem it changes, e.g. apotheosis:core/breach.")


class ApotheosisTierAugmentDef(ApotheosisEntryDef):
    """One of Apotheosis's world tier augments, switched off or with fields replaced."""

    KIND = "tier_augments"
    TARGET_FIELD = "augment"

    augment: ApotheosisEntryId = Field(
        description="The tier augment it changes, e.g. apotheosis:haven/max_eterna."
    )


class ApotheosisRarityDef(ApotheosisEntryDef):
    """One of Apotheosis's rarities with fields replaced. A rarity cannot be switched off: Apotheosis needs all five."""

    KIND = "rarities"
    TARGET_FIELD = "rarity"
    CAN_DISABLE = False

    rarity: ApotheosisEntryId = Field(description="The rarity it changes, e.g. apotheosis:rare.")


# The entry kinds, in the order the snapshot lists them.
ENTRY_MODELS: tuple[type[ApotheosisEntryDef], ...] = (
    ApotheosisAffixDef,
    ApotheosisGemDef,
    ApotheosisTierAugmentDef,
    ApotheosisRarityDef,
)


# The kinds of affix slot a rarity's rules hold (Apotheosis's AffixType).
AffixKind = Literal["stat", "basic_effect", "ability"]


class ApotheosisRarityOverrideDef(StrictModel):
    """One of Apotheosis's loot categories with fewer affix slots at some rarities (RLM-232): Apotheosis rolls a
    rarity's rules for every category unless a rarity override names other rules for it, and a slot that finds no
    affix of its kind left for the item logs "no affixes available" and rolls nothing.

    The category's rules at each rarity listed are the rarity's own (as ``content/apotheosis_rarities/`` fits them),
    or those of Apotheosis's override for the category where it has one, keeping at most the number of slots given
    for each kind: chanced slots go first, the last first, then plain ones from the end. The other rarities keep what
    they had."""

    id: ContentId
    category: ResourceId = Field(description="The loot category, e.g. apotheosis:helmet.")
    max_slots: Annotated[
        dict[ResourceId, Annotated[dict[AffixKind, Annotated[int, Field(ge=0)]], Field(min_length=1)]],
        Field(min_length=1),
    ] = Field(
        description=(
            "Rarity -> affix kind -> the most slots of that kind the category keeps there, e.g. "
            "apotheosis:rare: {ability: 0, basic_effect: 1}."
        )
    )


class ApotheosisStatRemapDef(StrictModel):
    """An Apothic Attributes stat that ours replaces, or that is switched off, wherever an Apotheosis affix, gem bonus
    or tier augment grants it (RLM-272).

    With ``target``, each of them grants our stat instead, with the same operation and values. With ``off``, an
    attribute affix or a tier augment is switched off, a gem bonus is dropped (a multi-attribute bonus as a whole, as
    its description joins all its modifiers), and the stat leaves an all-stats bonus's list."""

    id: ContentId
    source: ApothicAttributeId = Field(description="The Apothic Attributes stat it replaces.")
    target: ResourceId | None = Field(
        default=None,
        description="The stat that replaces it, e.g. realmforge:crit_chance. Leave out with off.",
    )
    off: bool = Field(
        default=False, description="true switches whatever grants it off instead of remapping it."
    )
    source_base: float | None = Field(
        default=None,
        gt=0,
        allow_inf_nan=False,
        description=(
            "The source's base value where its scale differs from the target's (Apothic Attributes' crit damage is "
            "1.5, ours +0.5). An add_multiplied_base modifier then becomes an add_value of its values times this, "
            "so it adds what it added to the source."
        ),
    )

    @model_validator(mode="after")
    def _target_or_off(self) -> ApotheosisStatRemapDef:
        if (self.target is None) == (not self.off):
            raise ValueError("give a target stat, or off: true, but not both")
        if self.off and self.source_base is not None:
            raise ValueError("source_base converts a remapped value; a stat switched off has none")
        return self


class RecipeRemovalDef(StrictModel):
    """Recipes that no longer exist: each is replaced by a file at its own path that NeoForge skips
    (``neoforge:never``), so the recipe never loads (RLM-272: Apotheosis's potion charms)."""

    id: ContentId
    recipes: Annotated[UniqueResourceIds, Field(min_length=1)] = Field(
        description=(
            "The recipes to remove, e.g. apotheosis:potion_charm: vanilla's or Apotheosis's (its snapshot), never "
            "our own."
        )
    )
