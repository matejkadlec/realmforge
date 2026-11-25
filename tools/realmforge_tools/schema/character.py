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

# A spell tree's shape (Matěj, 2026-10-30, RLM-333, like WotLK's talent trees): rows of 1 to 3 spells in three columns.
# A row opens once this many points per row above it are spent in the tree, so row 2 after 5, row 3 after 10, row 4
# after 15; the first spell's free rank counts as none (Matěj, 2026-11-04, RLM-631). Java's SpellPoints.POINTS_PER_ROW
# is the same number, which a GameTest compares with the generated expectations.
TREE_COLUMNS = 3
TREE_ROW_POINTS = 5
# The most rows a tree may have: far beyond M1's 4 (17 points at level 18 open row 4).
MAX_TREE_ROWS = 12

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
        description='What it gives, one line under its name on the character sheet, e.g. "+{bonus}% Damage With '
        "Sword-like Weapons\": {bonus} is its first modifier's amount (in percent for a multiplied operation)."
    )
    tooltip_description: LocalizedText | None = Field(
        default=None,
        description="A shorter line for the weapon's own tooltip, where the item already says which weapon it is, "
        'e.g. "+{bonus}% Damage" (Matěj, 2026-11-04, RLM-563); the description when unset. Takes {bonus} too.',
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
        for field, text in self.texts():
            for name in _PLACEHOLDER.findall(text.en_us):
                if name not in PASSIVE_PLACEHOLDERS:
                    raise ValueError(
                        f"{field}: unknown placeholder {{{name}}}; known: {', '.join(PASSIVE_PLACEHOLDERS)}"
                    )
        return self

    def texts(self) -> list[tuple[str, LocalizedText]]:
        """The passive's lines that may name ``{bonus}``, by field name: the description, and the tooltip's line."""
        texts = [("description", self.description)]
        if self.tooltip_description is not None:
            texts.append(("tooltip_description", self.tooltip_description))
        return texts


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
        ge=0,
        le=100,
        allow_inf_nan=False,
        description="Rage lost every second once the holder is out of combat, 10 s after the last hit dealt or taken "
        "(the shared in-combat state, RLM-348 and RLM-594).",
    )


class TreeSpellDef(StrictModel):
    """One spell of a specialization's spell tree (C5, RLM-333): where it stands, and what it needs besides points."""

    spell: ResourceId = Field(description="The spell, e.g. realmforge:slam.")
    row: int = Field(
        ge=1,
        le=MAX_TREE_ROWS,
        description=f"Its row, from 1 at the top. Row N opens once {TREE_ROW_POINTS} x (N - 1) points are spent in the "
        "tree: row 1 from the start, row 2 after 5, row 3 after 10 (Matěj, 2026-11-04, RLM-631).",
    )
    column: int = Field(ge=1, le=TREE_COLUMNS, description="Its column in the row: 1 (left), 2 or 3 (right).")
    requires: UniqueResourceIds = Field(
        default_factory=list,
        description="The arrows: spells of this tree, in an earlier row or beside it in the same one, that the player "
        "must each know at any rank first.",
    )


class SpecializationDef(StrictModel):
    """A specialization and its spell tree (C5, RLM-333; Matěj, 2026-10-15, RLM-218): the player picks one in the spell
    trees window and spends points there. One without spells comes later: the window names it, and nobody can pick it
    yet (M1 has one tree per class, the other two come in M3)."""

    id: str = Field(
        pattern=r"^[a-z][a-z0-9_]*$",
        max_length=64,
        description="Lowercase snake_case id, unique in the class, e.g. frost. Characters save it.",
    )
    name: LocalizedText = Field(description="Its name above the tree (Title Case), e.g. Frost.")
    description: LocalizedText = Field(description="One short sentence on how it plays, in its tooltip.")
    first_spell: ResourceId | None = Field(
        default=None,
        description="The tree's first spell, in its first row: free at rank 1 when the player picks the "
        "specialization (decision D4: every class starts with one spell; Matěj, 2026-11-04). Set exactly when the "
        "tree has spells.",
    )
    spells: list[TreeSpellDef] = Field(
        default_factory=list,
        description="The tree's spells. Every rank costs a point, but first_spell's first.",
    )

    @model_validator(mode="after")
    def _tree(self) -> SpecializationDef:
        rows = {taught.spell: taught.row for taught in self.spells}
        if len(rows) != len(self.spells):
            names = [taught.spell for taught in self.spells]
            repeated = sorted({name for name in names if names.count(name) > 1})
            raise ValueError(f"a tree holds each spell once: {', '.join(repeated)}")
        if not self.spells:
            if self.first_spell is not None:
                raise ValueError("a specialization without spells has no first_spell")
            return self
        if self.first_spell is None:
            raise ValueError("every class starts with one spell (decision D4): set first_spell")
        if rows.get(self.first_spell) != 1:
            raise ValueError(f"first_spell {self.first_spell} must be in the tree's first row")
        places = [(taught.row, taught.column) for taught in self.spells]
        if len(set(places)) != len(places):
            repeated_places = sorted({place for place in places if places.count(place) > 1})
            raise ValueError(f"two spells share a place (row, column): {repeated_places}")
        for taught in self.spells:
            for required in taught.requires:
                if required == taught.spell:
                    raise ValueError(f"{taught.spell} requires itself")
                needed = rows.get(required)
                if needed is None:
                    raise ValueError(f"{taught.spell} requires {required}, which is not in this tree")
                if needed > taught.row:
                    raise ValueError(
                        f"{taught.spell} requires {required}, which is in a later row: an arrow points down or "
                        "across its row"
                    )
        cycle = _requirement_cycle(self.spells)
        if cycle:
            raise ValueError(
                f"the arrows run in a circle, so none of them can be learned: {' -> '.join(cycle)}"
            )
        return self

    def row_ranks(self, max_ranks: dict[str, int]) -> dict[int, int]:
        """The ranks each row holds, by its spells' highest ranks in ``max_ranks``; a spell missing there counts none."""
        ranks: dict[int, int] = {}
        for taught in self.spells:
            ranks[taught.row] = ranks.get(taught.row, 0) + max_ranks.get(taught.spell, 0)
        return ranks


def row_points(row: int) -> int:
    """The points that must be spent in a tree before ``row`` opens (RLM-631)."""
    return TREE_ROW_POINTS * (row - 1)


def open_rows(points: int) -> int:
    """How many rows ``points`` spent in a tree open: 1 with none, 2 from 5, 3 from 10 (RLM-631)."""
    return points // TREE_ROW_POINTS + 1


def _requirement_cycle(spells: list[TreeSpellDef]) -> list[str]:
    """A circle of arrows, as the spells along it, or an empty list. Arrows only point down or across a row, so a
    circle can only run within one row."""
    requires = {taught.spell: taught.requires for taught in spells}
    done: set[str] = set()

    def walk(spell: str, path: list[str]) -> list[str]:
        if spell in path:
            return [*path[path.index(spell) :], spell]
        if spell in done:
            return []
        for required in requires.get(spell, []):
            found = walk(required, [*path, spell])
            if found:
                return found
        done.add(spell)
        return []

    for taught in spells:
        found = walk(taught.spell, [])
        if found:
            return found
    return []


class ClassDef(StrictModel):
    """A class: its role, its resource, the stats it gives, its specializations' spell trees and its starting kit
    (architecture 10.1)."""

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
    specializations: list[SpecializationDef] = Field(
        default_factory=list,
        description="Its specializations with their spell trees (C5). A tree holds at most 4 active spells per chapter "
        "(decision D4: chapter N allows 4 x N spells).",
    )

    @model_validator(mode="after")
    def _specializations(self) -> ClassDef:
        ids = [specialization.id for specialization in self.specializations]
        repeated = sorted({name for name in ids if ids.count(name) > 1})
        if repeated:
            raise ValueError(f"a class names each specialization once: {', '.join(repeated)}")
        if ids and not any(specialization.spells for specialization in self.specializations):
            raise ValueError("every class starts with one spell (decision D4): give a specialization spells")
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
