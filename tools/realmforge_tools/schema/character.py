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
    Seconds,
    StrictModel,
    UniqueResourceIds,
)
from realmforge_tools.schema.stat_rule import MULTIPLIED, Operation

FiniteNumber = Annotated[float, Field(allow_inf_nan=False)]

# The resource types a class can use: the static registry realmforge:resource_type that spell/SpellRegistries fills
# (ids only until C3a gives them behaviour). The game refuses a class naming any other.
RESOURCE_TYPES: tuple[str, ...] = (f"{MOD_ID}:mana", f"{MOD_ID}:rage")
ResourceType = Literal["realmforge:mana", "realmforge:rage"]
RAGE = f"{MOD_ID}:rage"

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


class ClassDef(StrictModel):
    """A class: its resource, the stats it gives and its starting kit (architecture 10.1). Spells by level and talent
    trees come with C4 and C5."""

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
        default_factory=list, description="Attribute modifiers the class gives, the same at every level."
    )
    rage: RageDef | None = Field(
        default=None,
        description="How the class gains and loses rage; a rage class sets it, any other leaves it out.",
    )
    starting_kit: list[KitItemDef] = Field(
        default_factory=list, description="Items a new character of this class receives once."
    )

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
