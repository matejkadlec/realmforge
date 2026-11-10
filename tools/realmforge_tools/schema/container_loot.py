"""Container loot by loot points: ``content/container_loot/<id>.yaml`` (RLM-289; ADR 0041, the container loot design,
architecture 13).

A table is what a kind of container holds, a normal chest or barrel or a treasure room's chest. Each roll draws a
budget of loot points and spends it on the table's options, one by weight among those it can still afford, until none
is affordable: a valuable option leaves little for the rest, and an option with a ``limit`` comes at most that often.
The loot follows the player who opens the container: their world tier (Apotheosis's five) picks the options and
raises the budget, and each point of their luck raises it too.

Points are counted in mob kills: ``kill_points`` points are one kill, and the coins of one kill, the curve
``coin_drop`` at the tier's ``level``, cost exactly that. So a budget of 600 points at 150 a kill is a container worth
four kills, the design's medium for a normal one.

The pipeline writes each table as the loot table ``realmforge:containers/<id>`` with one entry of our own,
``realmforge:budget`` (``loot/BudgetEntry`` in the game), which Lootr rolls once per player. The path is outside
``chests/``, so Apotheosis's chest loot injection skips it and its gems and affix items come only from the budget
(Matěj left that choice to Claude, 2026-10-21). This module also simulates the roll as the game makes it (``roll``,
``simulate``), which the tests use to check each table's worth per tier.
"""

from __future__ import annotations

import random
from dataclasses import dataclass
from typing import Annotated, Literal

from pydantic import Field, model_validator

from realmforge_tools.project import MOD_ID
from realmforge_tools.schema.chapter import MAX_LEVEL
from realmforge_tools.schema.common import ContentId, ResourceId, StrictModel
from realmforge_tools.schema.curve import CurveDef
from realmforge_tools.schema.leveling import java_round

# The world tiers in order, by Apotheosis's own names for them (``WorldTier``), which the game's data uses too.
TIERS = ("haven", "frontier", "ascent", "summit", "pinnacle")
Tier = Literal["haven", "frontier", "ascent", "summit", "pinnacle"]
# A coin drop is between these shares of the curve's value (schema/coin.py); a container's coins are as a mob's.
COINS_MIN_SHARE = 0.5
COINS_MAX_SHARE = 1.5
# The game ends a roll after this many picks whatever its budget (loot/BudgetRoll.MAX_PICKS).
MAX_PICKS = 64
# The most rolls a gem may be the purest of (loot/TierLootEntry.MAX_ROLLS).
MAX_GEM_ROLLS = 10
# The slots of a chest or a barrel: a roll with more stacks would lose the rest.
CONTAINER_SLOTS = 27
# What a book or an item is enchanted from: vanilla's tag of the enchantments random loot may carry.
ENCHANTMENTS = "#minecraft:on_random_loot"
_MAX_POINTS = 1_000_000
_MAX_STACK = 99


def loot_table_path(table_id: str) -> str:
    """The path of a table's loot table: ``containers/<id>``, outside ``chests/`` (the module's docstring)."""
    return f"containers/{table_id}"


def loot_table_id(table_id: str) -> str:
    """The id of a table's loot table: ``realmforge:containers/<id>``."""
    return f"{MOD_ID}:{loot_table_path(table_id)}"


class BudgetDef(StrictModel):
    """The points a container may draw: half of all rolls lie between ``min`` and ``medium``, the other half between
    ``medium`` and ``max``, each half evenly, so a high roll is rare."""

    min: int = Field(
        ge=1, le=_MAX_POINTS, description="The least a container holds, at the first world tier."
    )
    medium: int = Field(
        ge=1, le=_MAX_POINTS, description="The roll in the middle: half of all containers hold less."
    )
    max: int = Field(ge=1, le=_MAX_POINTS, description="The most, a high roll.")

    @model_validator(mode="after")
    def _rises(self) -> BudgetDef:
        if not self.min <= self.medium <= self.max:
            raise ValueError(
                f"min, medium and max rise, but they are {self.min}, {self.medium} and {self.max}"
            )
        return self

    def at(self, roll: float) -> float:
        """The budget of the roll ``roll``, from 0 (the least) to 1 (the most); ``LootBudget#at`` in the game."""
        clamped = min(1.0, max(0.0, roll))
        if clamped <= 0.5:
            return self.min + (self.medium - self.min) * (clamped * 2)
        return self.medium + (self.max - self.medium) * (clamped * 2 - 1)


class TierDef(StrictModel):
    """What a world tier means for the table: the mob level its kills are counted at, and how much it raises the
    budget."""

    level: int = Field(
        ge=1,
        le=MAX_LEVEL,
        description="The mob level a kill is counted at for this tier: the coins of a kill are the curve coin_drop there.",
    )
    budget_percent: float = Field(
        default=100,
        ge=1,
        le=1000,
        allow_inf_nan=False,
        description="The budget at this tier, in percent of the table's own: 100 at the first tier, more at a later one.",
    )


class CountDef(StrictModel):
    """How many of an item one pick gives: a whole number from ``min`` to ``max``, each as likely."""

    min: int = Field(ge=1, le=_MAX_STACK)
    max: int = Field(ge=1, le=_MAX_STACK)

    @model_validator(mode="after")
    def _ordered(self) -> CountDef:
        if self.min > self.max:
            raise ValueError(f"min ({self.min}) is more than max ({self.max})")
        return self


class EnchantLevelsDef(StrictModel):
    """The enchanting levels an item or a book is enchanted with, as at an enchanting table: from ``min`` to ``max``."""

    min: int = Field(ge=1, le=100)
    max: int = Field(ge=1, le=100)

    @model_validator(mode="after")
    def _ordered(self) -> EnchantLevelsDef:
        if self.min > self.max:
            raise ValueError(f"min ({self.min}) is more than max ({self.max})")
        return self


class _OptionDef(StrictModel):
    weight: int = Field(
        default=1,
        ge=1,
        le=10_000,
        description="How likely it is against the other options a roll can afford.",
    )
    limit: int = Field(
        default=0,
        ge=0,
        le=MAX_PICKS,
        description="How often one container may hold it: 1 for something unique, 0 (the default) for any number.",
    )
    tiers: list[Tier] = Field(
        default_factory=list,
        description="The world tiers it is for; every tier without it.",
    )

    @model_validator(mode="after")
    def _tiers_once(self) -> _OptionDef:
        if len(set(self.tiers)) != len(self.tiers):
            raise ValueError("tiers names each world tier once")
        return self

    def is_for(self, tier: str) -> bool:
        return not self.tiers or tier in self.tiers


class _PricedOptionDef(_OptionDef):
    cost: int = Field(ge=1, le=_MAX_POINTS, description="Its loot points.")


class CoinsOptionDef(_OptionDef):
    """Coins worth a number of mob kills of the opener's tier, 50 to 150 % of it as a mob's drop is. Its cost is not
    written: ``kills`` times the table's ``kill_points``, which is what ties points to kills."""

    type: Literal["coins"]
    kills: float = Field(
        gt=0, le=100, allow_inf_nan=False, description="How many kills' coins it gives, on average."
    )


class ItemOptionDef(_PricedOptionDef):
    """Items: food, ingots, gems, ores, gear; enchanted with ``enchant``, which turns a book into an enchanted book."""

    type: Literal["item"]
    item: ResourceId = Field(description="The item, e.g. minecraft:bread. Validated against the ID universe.")
    count: CountDef = Field(
        default=CountDef(min=1, max=1), description="How many one pick gives: 1 by default."
    )
    enchant: EnchantLevelsDef | None = Field(
        default=None,
        description="Enchants it with these levels, with the enchantments random loot may carry (books included).",
    )

    @model_validator(mode="after")
    def _one_enchanted(self) -> ItemOptionDef:
        if self.enchant is not None and self.count.max > 1:
            raise ValueError("an enchanted item comes one at a time: leave count out")
        return self


class GemOptionDef(_PricedOptionDef):
    """A gem of Apotheosis's, its kind and purity rolled for the opener's world tier."""

    type: Literal["gem"]
    rolls: int = Field(
        default=1,
        ge=1,
        le=MAX_GEM_ROLLS,
        description="The gem is the purest of this many rolls: 1 by default, more for a better gem at the same tier.",
    )


class AffixItemOptionDef(_PricedOptionDef):
    """A weapon, a tool or a piece of armor with Apotheosis's affixes, its rarity rolled for the opener's world tier."""

    type: Literal["affix_item"]


OptionDef = Annotated[
    CoinsOptionDef | ItemOptionDef | GemOptionDef | AffixItemOptionDef, Field(discriminator="type")
]


class ContainerLootDef(StrictModel):
    """What a kind of container holds, drawn by loot points for the player who opens it."""

    id: ContentId
    budget: BudgetDef
    kill_points: int = Field(
        default=150,
        ge=1,
        le=_MAX_POINTS,
        description="The points one mob kill is worth: the coins of a kill cost this much.",
    )
    luck_percent: float = Field(
        default=5,
        ge=0,
        le=1000,
        allow_inf_nan=False,
        description="How much each point of the opener's luck (minecraft:luck) adds to the budget, in percent.",
    )
    tiers: dict[Tier, TierDef] = Field(
        description="Every world tier: haven, frontier, ascent, summit, pinnacle."
    )
    options: list[OptionDef] = Field(min_length=1, description="What the budget may buy.")

    @model_validator(mode="after")
    def _every_tier(self) -> ContainerLootDef:
        missing = [tier for tier in TIERS if tier not in self.tiers]
        if missing:
            raise ValueError(f"tiers needs every world tier: {', '.join(missing)} missing")
        levels = [self.tiers[tier].level for tier in TIERS]
        percents = [self.tiers[tier].budget_percent for tier in TIERS]
        if levels != sorted(levels) or percents != sorted(percents):
            raise ValueError("a later world tier has no lower level or budget than the one before it")
        return self

    def cost(self, option: OptionDef) -> int:
        """An option's loot points: its ``cost``, or for coins their kills times ``kill_points``, at least 1."""
        if isinstance(option, CoinsOptionDef):
            return max(1, java_round(option.kills * self.kill_points))
        return option.cost

    def options_at(self, tier: str) -> list[OptionDef]:
        """The options a container opened at ``tier`` may hold, in the written order."""
        return [option for option in self.options if option.is_for(tier)]

    def points(self, roll: float, tier: str, luck: float = 0.0) -> int:
        """The points of the roll ``roll`` (0 to 1) for an opener at ``tier`` with ``luck``: the budget times the
        tier's percent, times 1 + luck x ``luck_percent`` of a hundred, never below 0, rounded half up
        (``LootBudget#points``)."""
        lucky = max(0.0, 1.0 + luck * self.luck_percent / 100.0)
        return java_round(self.budget.at(roll) * self.tiers[tier].budget_percent / 100.0 * lucky)


def coins_range(
    coin_drop: CurveDef, table: ContainerLootDef, option: CoinsOptionDef, tier: str
) -> tuple[int, int]:
    """The least and the most a coins option gives at ``tier``, in the smallest coin: 50 and 150 % of its kills times
    the curve at the tier's level, rounded half up, at least 1."""
    base = option.kills * coin_drop.value_at(table.tiers[tier].level)
    least = max(1, java_round(base * COINS_MIN_SHARE))
    return least, max(least, java_round(base * COINS_MAX_SHARE))


@dataclass(frozen=True)
class Choice:
    """One thing a roll may buy, as ``BudgetRoll.Choice`` in the game."""

    weight: int
    cost: int
    limit: int


def choices_at(table: ContainerLootDef, tier: str) -> list[Choice]:
    """The choices of a roll at ``tier``: the tier's options with their weights, costs and limits."""
    return [Choice(option.weight, table.cost(option), option.limit) for option in table.options_at(tier)]


def roll(budget: int, choices: list[Choice], rng: random.Random) -> list[int]:
    """The choices a budget buys, as indexes in the order picked: ``BudgetRoll#roll`` in the game. One of the
    affordable choices by weight, its cost paid, until none is affordable or ``MAX_PICKS`` are picked."""
    picked: list[int] = []
    times = [0] * len(choices)
    remaining = budget
    while len(picked) < MAX_PICKS:
        affordable = [
            index
            for index, choice in enumerate(choices)
            if choice.cost <= remaining and (choice.limit == 0 or times[index] < choice.limit)
        ]
        total = sum(choices[index].weight for index in affordable)
        if total <= 0:
            break
        draw = rng.randrange(total)
        for index in affordable:
            draw -= choices[index].weight
            if draw < 0:
                picked.append(index)
                times[index] += 1
                remaining -= choices[index].cost
                break
    return picked


@dataclass(frozen=True)
class Simulation:
    """What many rolls of a table gave at one tier: every roll's budget, the points it spent, and its picks."""

    budgets: list[int]
    spent: list[int]
    picks: list[list[int]]

    def kills(self, kill_points: int) -> list[float]:
        """Each roll's worth in mob kills."""
        return [points / kill_points for points in self.spent]


def simulate(table: ContainerLootDef, tier: str, rolls: int, seed: int, luck: float = 0.0) -> Simulation:
    """Rolls ``table`` ``rolls`` times for an opener at ``tier`` with ``luck``, from ``seed``, as the game does."""
    rng = random.Random(seed)
    choices = choices_at(table, tier)
    budgets: list[int] = []
    spent: list[int] = []
    picks: list[list[int]] = []
    for _ in range(rolls):
        budget = table.points(rng.random(), tier, luck)
        picked = roll(budget, choices, rng)
        budgets.append(budget)
        spent.append(sum(choices[index].cost for index in picked))
        picks.append(picked)
    return Simulation(budgets, spent, picks)
