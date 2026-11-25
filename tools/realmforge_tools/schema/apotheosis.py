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
- ``content/apotheosis_new_affixes/``: an affix of our own, a new entry at ``data/realmforge/apotheosis/affixes/``;
- ``content/apotheosis_gem_bonuses/``: a gem's bonuses for categories it has none for (wands, robes), Apotheosis's own
  extra gem bonuses at ``data/realmforge/apotheosis/extra_gem_bonuses/``, which add to the gem without replacing it;
- ``content/recipe_removals/``: recipes that no longer exist, Apotheosis's or vanilla's.

``realmforge_tools/apotheosis.py`` applies them; names and units are Apotheosis 9.1.0's. The constants below list what
Apotheosis 9.1.0 registers in Java; update them with an Apotheosis update.
"""

from __future__ import annotations

from typing import Annotated, ClassVar, Literal, get_args

from pydantic import Field, JsonValue, StringConstraints, model_validator

from realmforge_tools.schema.common import (
    ContentId,
    LocalizedText,
    ResourceId,
    StrictModel,
    UniqueResourceIds,
)

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
# Our own loot categories (compat/apotheosis/ApotheosisCompat): the wand (RLM-273), and a robe category per armor slot
# (RLM-546), which generate/items.py files every armor piece of ours under by its gear kind, since our armor is the
# caster's (architecture 13).
ROBE_LOOT_CATEGORIES: dict[str, str] = {
    "head": "realmforge:robe_head",
    "chest": "realmforge:robe_chest",
    "legs": "realmforge:robe_legs",
    "feet": "realmforge:robe_feet",
}
OUR_LOOT_CATEGORIES: tuple[str, ...] = ("realmforge:wand", *ROBE_LOOT_CATEGORIES.values())

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


class AffixValueRange(StrictModel):
    """What an affix adds at one rarity, by the level it rolled: Placebo's StepFunction in its bounds form. Placebo
    refuses a range that is not a whole number of steps, and min equal to max: a fixed value is a plain number."""

    min: float = Field(allow_inf_nan=False, description="The value at the lowest roll.")
    max: float = Field(allow_inf_nan=False, description="The value at the highest roll.")
    step: float = Field(gt=0, allow_inf_nan=False, description="The values between go up by this much.")

    @model_validator(mode="after")
    def _whole_steps(self) -> AffixValueRange:
        if self.min >= self.max:
            raise ValueError("min must be below max: a fixed value is a plain number")
        steps = (self.max - self.min) / self.step
        if abs(steps - round(steps)) > 1e-6:
            raise ValueError(f"{self.min} to {self.max} is not a whole number of steps of {self.step}")
        return self


class ApotheosisNewAffixDef(StrictModel):
    """An affix of our own (RLM-557), a new entry where Apotheosis looks for affixes of any namespace: the stat it adds
    per rarity, the loot categories of ours it rolls on, its name and its suffix. It is an ``apotheosis:attribute``
    affix of the ``stat`` kind, the one kind Apotheosis's own format covers without code. An item that rolls it is
    named by it: "Sorcerous Netherite Robes of Winter" (``name`` for one affix, ``suffix`` for another)."""

    id: ContentId
    name: LocalizedText = Field(description="The word before an item's name, e.g. Sorcerous.")
    suffix: LocalizedText = Field(description="The words after an item's name, e.g. of Sorcery.")
    stat: ResourceId = Field(
        description="The stat it adds, one of ours (content/stats/), e.g. realmforge:spell_power."
    )
    categories: Annotated[UniqueResourceIds, Field(min_length=1)] = Field(
        description="The loot categories it rolls on, each one of ours, e.g. realmforge:robe_chest."
    )
    values: Annotated[
        dict[ResourceId, Annotated[float, Field(allow_inf_nan=False)] | AffixValueRange], Field(min_length=1)
    ] = Field(
        description=(
            "Rarity -> what it adds there, added to the stat as a flat amount (add_value): {min, max, step} or a fixed "
            "number. A share is a fraction: 3 % is 0.03. It rolls only at the rarities listed."
        )
    )
    weight: int = Field(
        default=25, gt=0, description="How often it rolls against the others (Apotheosis's stat affixes: 25)."
    )
    quality: float = Field(
        default=0.1,
        ge=0,
        allow_inf_nan=False,
        description="How much a player's luck raises its weight (Apotheosis's stat affixes: 0.1).",
    )

    @model_validator(mode="after")
    def _known_rarities(self) -> ApotheosisNewAffixDef:
        unknown = sorted(set(self.values) - set(RARITIES))
        if unknown:
            raise ValueError(f"unknown rarities {', '.join(unknown)}; Apotheosis has {', '.join(RARITIES)}")
        return self


# Apotheosis 9.1.0's gem purities, lowest first (``Purity``): a gem drops at its ``min_purity`` and above.
PURITIES = ("cracked", "chipped", "flawed", "normal", "flawless", "perfect")
Purity = Literal["cracked", "chipped", "flawed", "normal", "flawless", "perfect"]
# Minecraft's attribute modifier operations, as Apotheosis's bonuses name them.
AttributeOperation = Literal["add_value", "add_multiplied_base", "add_multiplied_total"]
# The descriptions Apotheosis 9.1.0 has for a multi-attribute bonus (its lang, ``bonus.apotheosis:multi_attr.desc.*``),
# by how many modifiers each names: "%s and %s", "%s and %s, but %s", "%s, but %s and %s".
MULTI_ATTRIBUTE_DESCS: dict[str, int] = {
    "bonus.apotheosis:multi_attr.desc.and": 2,
    "bonus.apotheosis:multi_attr.desc.and_but": 3,
    "bonus.apotheosis:multi_attr.desc.but_and": 3,
}
# The text group that names our gem classes: a gem's tooltip reads ``gem_class.<key>``, and ours are written with the
# key ``realmforge.<key>``, so their names are ``gem_class.realmforge.<key>`` (content/texts/gem_class.yaml).
GEM_CLASS_TEXTS = "gem_class"

GemClassKey = Annotated[
    str,
    StringConstraints(pattern=r"^[a-z][a-z0-9_]*$", max_length=48),
    Field(
        description="The gem class's name in tooltips, gem_class.realmforge.<key> in content/texts/gem_class.yaml."
    ),
]
PurityValues = Annotated[
    dict[Purity, Annotated[float, Field(allow_inf_nan=False)]],
    Field(
        min_length=1,
        description=(
            "Purity -> the amount, e.g. {cracked: 1, chipped: 2, ...}: one for every purity the gem drops at, from its "
            "min_purity up. A share is a fraction: 5 % is 0.05."
        ),
    ),
]


class GemClassDef(StrictModel):
    """A gem class of ours over several loot categories, e.g. the robe chest and legs as Apotheosis's ``core_armor``
    covers the chestplate and the leggings."""

    key: GemClassKey
    types: Annotated[UniqueResourceIds, Field(min_length=1)] = Field(
        description="The loot categories it covers, e.g. [realmforge:robe_chest, realmforge:robe_legs]."
    )


class GemModifierDef(StrictModel):
    """One stat of a multi-attribute bonus."""

    attribute: ResourceId = Field(description="The stat, e.g. realmforge:spell_penetration.")
    operation: AttributeOperation = Field(
        description=(
            "add_value for flat points and for a share of ours; add_multiplied_base for a percent on points "
            "(architecture 8.2)."
        )
    )
    values: PurityValues


class ExtraGemBonusDef(StrictModel):
    """One bonus a gem gets for a gem class it has no bonus for, in one of three shapes: a stat (``attribute``,
    ``operation``, ``values``: Apotheosis's ``attribute`` bonus), several stats at once (``modifiers``: its
    ``multi_attribute`` bonus), or a copy of the gem's own bonus for another loot category (``like``), as Apotheosis
    ships it with our stat remaps applied, e.g. a robe's bonus as the armor's, so it stays in step with Apotheosis
    updates. A gem fits an item only if it has a bonus for the item's category."""

    gem_class: ResourceId | GemClassDef = Field(
        description=(
            "The loot categories it is for: one, e.g. realmforge:wand (named in tooltips by its path: wand), or a gem "
            "class of ours over several, {key, types}."
        )
    )
    like: ResourceId | None = Field(
        default=None,
        description=(
            "Copies the gem's own bonus for this loot category, e.g. apotheosis:chestplate, for this gem class. Alone, "
            "without attribute or modifiers."
        ),
    )
    attribute: ResourceId | None = Field(
        default=None, description="The stat it adds, e.g. realmforge:spell_power."
    )
    operation: AttributeOperation | None = Field(
        default=None,
        description=(
            "With attribute: add_value for flat points and for a share of ours; add_multiplied_base for a percent on "
            "points (architecture 8.2)."
        ),
    )
    values: PurityValues | None = None
    modifiers: Annotated[list[GemModifierDef], Field(min_length=2)] | None = Field(
        default=None, description="Several stats at once, each with its operation and values."
    )
    desc: str | None = Field(
        default=None,
        description=(
            "With modifiers: how the tooltip joins them, one of Apotheosis's texts (default "
            'bonus.apotheosis:multi_attr.desc.and, "%s and %s").'
        ),
    )

    @model_validator(mode="after")
    def _one_shape(self) -> ExtraGemBonusDef:
        single = (self.attribute, self.operation, self.values)
        if self.like is not None:
            if any(part is not None for part in (*single, self.modifiers, self.desc)):
                raise ValueError("like copies a whole bonus: give it alone, without attribute or modifiers")
        elif self.modifiers is not None:
            if any(part is not None for part in single):
                raise ValueError("modifiers name their own attributes: drop attribute, operation and values")
            count = MULTI_ATTRIBUTE_DESCS.get(self.multi_attribute_desc)
            if count is None:
                raise ValueError(
                    f"unknown desc {self.desc!r}; Apotheosis has {', '.join(MULTI_ATTRIBUTE_DESCS)}"
                )
            if count != len(self.modifiers):
                raise ValueError(
                    f"{self.multi_attribute_desc} joins {count} modifiers, not {len(self.modifiers)}"
                )
        elif any(part is None for part in single):
            raise ValueError("give attribute, operation and values, or modifiers, or like")
        elif self.desc is not None:
            raise ValueError("desc joins a bonus's modifiers: a single attribute has none")
        return self

    @property
    def multi_attribute_desc(self) -> str:
        return self.desc or "bonus.apotheosis:multi_attr.desc.and"

    @property
    def categories(self) -> tuple[str, ...]:
        """The loot categories it covers."""
        if isinstance(self.gem_class, GemClassDef):
            return tuple(self.gem_class.types)
        return (self.gem_class,)

    @property
    def class_key(self) -> str:
        """Its gem class's key: the one given, or the loot category's path."""
        if isinstance(self.gem_class, GemClassDef):
            return self.gem_class.key
        return self.gem_class.split(":", 1)[1]

    @property
    def modifier_list(self) -> list[GemModifierDef]:
        """Its stats: the one of an attribute bonus, the modifiers of a multi-attribute one, none for a copy."""
        if self.modifiers is not None:
            return list(self.modifiers)
        if self.attribute is not None and self.operation is not None and self.values is not None:
            return [GemModifierDef(attribute=self.attribute, operation=self.operation, values=self.values)]
        return []


class ApotheosisGemBonusesDef(StrictModel):
    """A gem's extra bonuses (RLM-547): bonuses for gem classes it has none for, through Apotheosis's own
    ``extra_gem_bonuses`` (``ExtraGemBonusRegistry``), which adds them to the gem when it loads instead of replacing
    its file (decision G3, Matěj, 2026-10-30). One file per gem. The wand bonuses (``realmforge:wand``), the robes'
    (RLM-548) and Slipstream's helmet are the first. Only attribute bonuses of our own: Apotheosis's special ones (on
    hit, on being hurt) fire on melee hits, arrows and being hurt, never on a spell, but a ``like`` copy brings
    whatever the gem's own bonus is, as on armor."""

    id: ContentId
    gem: ApotheosisEntryId = Field(description="The gem, e.g. apotheosis:core/ballast.")
    bonuses: Annotated[list[ExtraGemBonusDef], Field(min_length=1)] = Field(
        description="Its new bonuses, each for loot categories the gem and the other bonuses here do not cover."
    )


# The kinds of affix slot a rarity's rules hold (Apotheosis's AffixType).
AffixKind = Literal["stat", "basic_effect", "ability"]
AFFIX_KINDS: tuple[str, ...] = get_args(AffixKind)


class ApotheosisRarityOverrideDef(StrictModel):
    """One of Apotheosis's loot categories with fewer affix slots at some rarities (RLM-232): Apotheosis rolls a
    rarity's rules for every category unless a rarity override names other rules for it, and a slot that finds no
    affix of its kind left for the item logs "no affixes available" and rolls nothing.

    The category's rules at each rarity listed are the rarity's own (as ``content/apotheosis_rarities/`` fits them),
    or those of Apotheosis's override for the category where it has one, keeping at most the number of slots given
    for each kind: chanced slots go first, the last first, then plain ones from the end. The other rarities keep what
    they had.

    A category of ours with no affix of its own yet (the wand until its affixes, RLM-562) takes ``no_affixes`` instead: every rarity keeps its other rules (sockets, durability) and drops every affix slot, so
    reforging rolls a rarity without an error for each empty slot (RLM-546)."""

    id: ContentId
    category: ResourceId = Field(
        description="The loot category, e.g. apotheosis:helmet, or one of ours, e.g. realmforge:robe_chest."
    )
    max_slots: dict[
        ResourceId, Annotated[dict[AffixKind, Annotated[int, Field(ge=0)]], Field(min_length=1)]
    ] = Field(
        default_factory=dict,
        description=(
            "Rarity -> affix kind -> the most slots of that kind the category keeps there, e.g. "
            "apotheosis:rare: {ability: 0, basic_effect: 1}."
        ),
    )
    no_affixes: bool = Field(
        default=False,
        description=(
            "Every affix slot dropped at every rarity: a category of ours with no affix to roll yet. Instead of "
            "max_slots."
        ),
    )

    @model_validator(mode="after")
    def _one_way(self) -> ApotheosisRarityOverrideDef:
        if self.no_affixes == bool(self.max_slots):
            raise ValueError(
                "say how the slots change: max_slots, or no_affixes: true for a category with no affix"
            )
        return self


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
