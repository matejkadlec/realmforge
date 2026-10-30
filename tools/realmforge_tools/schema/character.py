"""Races and classes: ``content/races/<id>.yaml`` and ``content/classes/<id>.yaml``, the datapack registries
``realmforge:race`` and ``realmforge:class`` (architecture 5.2, 10.1)."""

from __future__ import annotations

import re
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
from realmforge_tools.schema.stat_rule import MULTIPLIED, Operation, Role

FiniteNumber = Annotated[float, Field(allow_inf_nan=False)]

# The resource types a class can use: the static registry realmforge:resource_type that spell/SpellRegistries fills
# (ids only until C3a gives them behaviour). The game refuses a class naming any other.
RESOURCE_TYPES: tuple[str, ...] = (f"{MOD_ID}:mana", f"{MOD_ID}:rage")
ResourceType = Literal["realmforge:mana", "realmforge:rage"]
RAGE = f"{MOD_ID}:rage"

# The highest level a class may teach a spell at: far beyond today's caps (30), within the chapters' levels.
MAX_CLASS_SPELL_LEVEL = 1000

# Every class's starting kit holds this much bread (Matěj, 2026-10-14: "for both classes and any future classes").
STARTING_BREAD: tuple[str, int] = ("minecraft:bread", 32)


class CharacterModifierDef(StrictModel):
    """An attribute modifier a race or class gives its player, the same at every level: levels give stat points to
    spend instead (Matěj, 2026-10-15, RLM-196). Units follow the stat rules: multiplied operations take percent
    (``amount_percent``); ``add_value`` on one of our stats takes its unit (percent for a share, points otherwise); a
    vanilla attribute takes either."""

    attribute: ResourceId = Field(description="The attribute it modifies, e.g. realmforge:strength.")
    operation: Operation = Field(
        description="How the amount applies (vanilla's attribute modifier operations)."
    )
    amount: FiniteNumber | None = Field(default=None, description="The amount, in game units.")
    amount_percent: FiniteNumber | None = Field(
        default=None, description="The amount in percent (5 means 5 %, the fraction 0.05)."
    )

    @model_validator(mode="after")
    def _units(self) -> CharacterModifierDef:
        if (self.amount is None) == (self.amount_percent is None):
            raise ValueError("set exactly one of amount and amount_percent")
        if self.operation in MULTIPLIED and not self.is_percent:
            raise ValueError(f"{self.operation} multiplies: write the amount as amount_percent")
        if self.game_amount == 0:
            raise ValueError("the modifier gives nothing: its amount is 0")
        return self

    @property
    def is_percent(self) -> bool:
        return self.amount_percent is not None

    @property
    def game_amount(self) -> float:
        """The amount in game units (a fraction for a percentage); the game's ``CharacterBonuses`` must agree."""
        if self.amount_percent is not None:
            return self.amount_percent / 100
        assert self.amount is not None
        return self.amount


class KitItemDef(StrictModel):
    """One item of a class's starting kit."""

    item: ResourceId = Field(description="The item, e.g. minecraft:stone_sword.")
    count: int = Field(default=1, ge=1, le=64, description="How many (1 to 64), split into full stacks.")


# The placeholder a racial passive's description may name: its first modifier, in percent for a multiplied operation.
PASSIVE_PLACEHOLDERS: tuple[str, ...] = ("bonus",)
_PLACEHOLDER = re.compile(r"\{([^{}]*)\}")


class RacialPassiveDef(StrictModel):
    """A race's racial passive (Matěj, 2026-10-15, RLM-219): modifiers its players have while the main hand holds an
    item of ``weapons``, such as the Human's Sword Specialization. For a player of the race, every such item's tooltip
    shows the passive's name and description below the item's stats (architecture 10.1)."""

    name: LocalizedText = Field(
        description="The passive's name, e.g. Sword Specialization (gold in tooltips)."
    )
    description: LocalizedText = Field(
        description='What it gives, one short line in tooltips, e.g. "+{bonus}% damage": {bonus} is its first '
        "modifier's amount (in percent for a multiplied operation)."
    )
    weapons: ResourceId = Field(
        description="An item tag, e.g. realmforge:sword_like: the passive applies while an item of it is in the main "
        "hand."
    )
    modifiers: list[CharacterModifierDef] = Field(
        min_length=1, description="Attribute modifiers it gives while it applies."
    )

    @model_validator(mode="after")
    def _placeholders(self) -> RacialPassiveDef:
        for name in _PLACEHOLDER.findall(self.description.en_us):
            if name not in PASSIVE_PLACEHOLDERS:
                raise ValueError(
                    f"description: unknown placeholder {{{name}}}; known: {', '.join(PASSIVE_PLACEHOLDERS)}"
                )
        return self


class RaceDef(StrictModel):
    """A playable race: the classes it may take, the modifiers it gives, and its racial active and passive
    (architecture 10.1; C4). A size comes with later races."""

    id: ContentId
    name: LocalizedText = Field(description="The race's name, shown on character creation (Title Case).")
    description: LocalizedText = Field(
        description="One or two sentences on character creation, in the lore style guide's voice."
    )
    classes: UniqueResourceIds = Field(
        min_length=1, description="The classes this race may take, e.g. realmforge:warrior."
    )
    modifiers: list[CharacterModifierDef] = Field(
        default_factory=list, description="Attribute modifiers the race gives, usually primary stats."
    )
    racial_spell: ResourceId | None = Field(
        default=None,
        description="Its racial active (RLM-219): a free spell every player of the race knows from creation, in the "
        "racial slot beside the spell bar (X), e.g. realmforge:will_to_survive.",
    )
    racial_passive: RacialPassiveDef | None = Field(
        default=None, description="Its racial passive (RLM-219), e.g. the Human's Sword Specialization."
    )


class RageDef(StrictModel):
    """How a rage class gains and loses rage (architecture 11.5): from melee hits it deals and damage it takes, decaying
    out of combat. The pool holds 100."""

    per_hit_dealt: float = Field(
        ge=0,
        le=100,
        allow_inf_nan=False,
        description="Rage for a full-strength melee hit dealt; a weaker swing gives its share.",
    )
    per_max_health_taken: float = Field(
        ge=0,
        le=1000,
        allow_inf_nan=False,
        description="Rage for taking damage equal to the whole max health from an attacker; a hit for a tenth of it "
        "gives a tenth.",
    )
    decay_per_second: float = Field(
        ge=0, le=100, allow_inf_nan=False, description="Rage lost every second out of combat."
    )
    decay_after_seconds: Seconds = Field(
        gt=0,
        le=60,
        description="How long after the last hit dealt or taken the holder is out of combat and its rage decays.",
    )


class ClassSpellDef(StrictModel):
    """A spell a class teaches its players at a level (C4): they learn it at rank 1 once they reach the level, and
    keep it."""

    spell: ResourceId = Field(description="The spell, e.g. realmforge:slam.")
    level: int = Field(ge=1, le=MAX_CLASS_SPELL_LEVEL, description="The character level that teaches it.")


class ClassDef(StrictModel):
    """A class: its role, its resource, the stats it gives, its spells by level and its starting kit (architecture
    10.1). The specialization spell trees come with C5."""

    id: ContentId
    name: LocalizedText = Field(
        description="The class's name, shown on character creation and the character sheet (Title Case)."
    )
    description: LocalizedText = Field(
        description="One or two sentences on character creation, in the lore style guide's voice."
    )
    role: Role = Field(
        description="What the class plays: melee, caster or ranged (assassins count as ranged). It decides which "
        "role-bound stat rule bonuses its players get (architecture 8.3)."
    )
    resource: ResourceType = Field(
        description="The resource its spells cost: realmforge:mana or realmforge:rage."
    )
    modifiers: list[CharacterModifierDef] = Field(
        default_factory=list, description="Attribute modifiers the class gives, the same at every level."
    )
    rage: RageDef | None = Field(
        default=None,
        description="How the class gains and loses rage; a rage class sets it, any other leaves it out.",
    )
    starting_kit: list[KitItemDef] = Field(
        default_factory=list, description="Items a new character of this class receives once."
    )
    spells: list[ClassSpellDef] = Field(
        default_factory=list,
        description="The spells the class teaches by level (C4): one at level 1, and at most 4 per chapter in all "
        "(decision D4: chapter N allows 4 x N spells).",
    )

    @model_validator(mode="after")
    def _spells(self) -> ClassDef:
        names = [spell.spell for spell in self.spells]
        repeated = sorted({name for name in names if names.count(name) > 1})
        if repeated:
            raise ValueError(f"a class teaches each spell once: {', '.join(repeated)}")
        if names and not any(spell.level == 1 for spell in self.spells):
            raise ValueError("every class starts with one spell (decision D4): teach one at level 1")
        return self

    @model_validator(mode="after")
    def _rage(self) -> ClassDef:
        if self.resource == RAGE and self.rage is None:
            raise ValueError("a rage class sets its rage numbers (rage)")
        if self.resource != RAGE and self.rage is not None:
            raise ValueError(f"rage numbers are for a class whose resource is {RAGE}")
        return self


def modifier_id(registry: Literal["race", "class"], entry_id: str, n: int) -> str:
    """The id of the n-th modifier of a race or class: ``realmforge:<registry>/<namespace>/<n>/<path>``. The entry's
    path comes last, as only it may contain slashes (the game's ``CharacterBonuses`` builds the same)."""
    return f"{MOD_ID}:{registry}/{MOD_ID}/{n}/{entry_id}"


def bonuses(
    registry: Literal["race", "class"], entry_id: str, modifiers: list[CharacterModifierDef]
) -> list[tuple[str, str, str, float]]:
    """The modifiers a race or class gives, as (id, attribute, operation, amount in game units). The game's
    ``CharacterBonuses`` must give the same."""
    return [
        (modifier_id(registry, entry_id, n), modifier.attribute, modifier.operation, modifier.game_amount)
        for n, modifier in enumerate(modifiers)
    ]


def passive_modifier_id(race_id: str, n: int) -> str:
    """The id of the n-th modifier of a race's racial passive: ``realmforge:race/<namespace>/passive/<n>/<path>``
    (the game's ``CharacterBonuses`` builds the same)."""
    return f"{MOD_ID}:race/{MOD_ID}/passive/{n}/{race_id}"


def passive_bonuses(race_id: str, passive: RacialPassiveDef) -> list[tuple[str, str, str, float]]:
    """The modifiers a race's racial passive gives while it applies, as ``bonuses`` lists them."""
    return [
        (passive_modifier_id(race_id, n), modifier.attribute, modifier.operation, modifier.game_amount)
        for n, modifier in enumerate(passive.modifiers)
    ]
