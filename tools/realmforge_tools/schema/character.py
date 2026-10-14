"""Races and classes: ``content/races/<id>.yaml`` and ``content/classes/<id>.yaml``, the datapack registries
``realmforge:race`` and ``realmforge:class`` (architecture 5.2, 10.1)."""

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
from realmforge_tools.schema.stat_rule import MULTIPLIED, Operation

FiniteNumber = Annotated[float, Field(allow_inf_nan=False)]

# The resource types a class can use: the static registry realmforge:resource_type that spell/SpellRegistries fills
# (ids only until C3a gives them behaviour). The game refuses a class naming any other.
RESOURCE_TYPES: tuple[str, ...] = (f"{MOD_ID}:mana", f"{MOD_ID}:rage")
ResourceType = Literal["realmforge:mana", "realmforge:rage"]

# Every class's starting kit holds this much bread (Matěj, 2026-10-14: "for both classes and any future classes").
STARTING_BREAD: tuple[str, int] = ("minecraft:bread", 32)

# Levels at which the pipeline and the game must agree on the bonuses (a GameTest compares them).
SAMPLE_LEVELS: tuple[int, ...] = (1, 2, 10)


class CharacterModifierDef(StrictModel):
    """An attribute modifier a race or class gives its player. The amount applies at level 1 and grows by
    ``per_level`` for every level above it: ``amount + per_level * (level - 1)``. Units follow the stat rules:
    multiplied operations take percent (``amount_percent``, ``per_level_percent``); ``add_value`` on one of our stats
    takes its unit (percent for a share, points otherwise); a vanilla attribute takes either."""

    attribute: ResourceId = Field(description="The attribute it modifies, e.g. realmforge:strength.")
    operation: Operation = Field(
        description="How the amount applies (vanilla's attribute modifier operations)."
    )
    amount: FiniteNumber | None = Field(default=None, description="Amount at level 1, in game units.")
    amount_percent: FiniteNumber | None = Field(
        default=None, description="Amount at level 1 in percent (5 means 5 %, the fraction 0.05)."
    )
    per_level: FiniteNumber | None = Field(
        default=None, description="Added for every level above 1, in the unit of amount."
    )
    per_level_percent: FiniteNumber | None = Field(
        default=None, description="Added for every level above 1, in percent, like amount_percent."
    )

    @model_validator(mode="after")
    def _units(self) -> CharacterModifierDef:
        if (self.amount is None) == (self.amount_percent is None):
            raise ValueError("set exactly one of amount and amount_percent")
        if self.per_level is not None and self.per_level_percent is not None:
            raise ValueError("set at most one of per_level and per_level_percent")
        if self.is_percent and self.per_level is not None:
            raise ValueError("the amount is in percent, so the growth is per_level_percent")
        if not self.is_percent and self.per_level_percent is not None:
            raise ValueError("the amount is in points, so the growth is per_level")
        if self.operation in MULTIPLIED and not self.is_percent:
            raise ValueError(f"{self.operation} multiplies: write the amount as amount_percent")
        if self.game_amount == 0 and self.game_per_level == 0:
            raise ValueError("the modifier gives nothing: amount and per_level are both 0")
        return self

    @property
    def is_percent(self) -> bool:
        return self.amount_percent is not None

    @property
    def game_amount(self) -> float:
        """The amount at level 1 in game units (a fraction for a percentage)."""
        if self.amount_percent is not None:
            return self.amount_percent / 100
        assert self.amount is not None
        return self.amount

    @property
    def game_per_level(self) -> float:
        """The growth per level in game units, 0 when there is none."""
        if self.per_level_percent is not None:
            return self.per_level_percent / 100
        return self.per_level or 0.0

    def amount_at(self, level: int) -> float:
        """The amount at ``level``; the game's ``CharacterBonuses`` must agree (checked by a GameTest)."""
        return self.game_amount + self.game_per_level * (level - 1)


class KitItemDef(StrictModel):
    """One item of a class's starting kit."""

    item: ResourceId = Field(description="The item, e.g. minecraft:stone_sword.")
    count: int = Field(default=1, ge=1, le=64, description="How many (1 to 64), split into full stacks.")


class RaceDef(StrictModel):
    """A playable race: the classes it may take and the modifiers it gives (architecture 10.1). Innate spells and a
    size come with the spell system (C4) and later races."""

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


class ClassDef(StrictModel):
    """A class: its resource, the stats it gives and how they grow per level, and its starting kit (architecture
    10.1). Spells by level and talent trees come with C4 and C5."""

    id: ContentId
    name: LocalizedText = Field(
        description="The class's name, shown on character creation and the character sheet (Title Case)."
    )
    description: LocalizedText = Field(
        description="One or two sentences on character creation, in the lore style guide's voice."
    )
    resource: ResourceType = Field(
        description="The resource its spells cost: realmforge:mana or realmforge:rage."
    )
    modifiers: list[CharacterModifierDef] = Field(
        default_factory=list, description="Attribute modifiers the class gives, with their growth per level."
    )
    starting_kit: list[KitItemDef] = Field(
        default_factory=list, description="Items a new character of this class receives once."
    )


def modifier_id(registry: Literal["race", "class"], entry_id: str, n: int) -> str:
    """The id of the n-th modifier of a race or class: ``realmforge:<registry>/<namespace>/<n>/<path>``. The entry's
    path comes last, as only it may contain slashes (the game's ``CharacterBonuses`` builds the same)."""
    return f"{MOD_ID}:{registry}/{MOD_ID}/{n}/{entry_id}"


def bonuses_at(
    registry: Literal["race", "class"], entry_id: str, modifiers: list[CharacterModifierDef], level: int
) -> list[tuple[str, str, str, float]]:
    """The modifiers a race or class gives at ``level``, as (id, attribute, operation, amount), skipping those whose
    amount is 0 there. The game's ``CharacterBonuses`` must give the same."""
    found: list[tuple[str, str, str, float]] = []
    for n, modifier in enumerate(modifiers):
        amount = modifier.amount_at(level)
        if amount != 0:
            found.append((modifier_id(registry, entry_id, n), modifier.attribute, modifier.operation, amount))
    return found
