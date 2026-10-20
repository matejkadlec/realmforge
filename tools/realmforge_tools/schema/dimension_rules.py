"""Dimension rules: ``content/dimensions/<id>.yaml``, the datapack registry ``realmforge:dimension_rules``
(architecture 5.2, 14.1, 15.1)."""

from __future__ import annotations

from dataclasses import dataclass

from pydantic import Field, model_validator

from realmforge_tools.schema.common import ContentId, ResourceId, StrictModel
from realmforge_tools.schema.requirement import RequirementDef

# The game reads these numbers as Java ints (world/MobLevelRule).
JAVA_INT_MAX = 2**31 - 1
# The most offsets either side of the player's level (world/MobLevelRule.MAX_SPREAD), and the largest weight
# (world/MobLevelRule.MAX_WEIGHT): far beyond any sensible rule, so a weight sum always fits an int.
MAX_SPREAD = 10
MAX_WEIGHT = 1000


@dataclass(frozen=True)
class LevelChances:
    """The levels a mob may spawn at near a player, with their weights (not normalised): ``weights[i]`` is the
    weight of level ``lowest + i``."""

    lowest: int
    weights: tuple[int, ...]

    @property
    def highest(self) -> int:
        return self.lowest + len(self.weights) - 1


class MobLevelRuleDef(StrictModel):
    """How the level of a mob spawning in the dimension follows the nearest player (RLM-240, architecture 14.1):
    near the player's level, by ``weights`` for the offsets around it, never below ``entry_level`` (the realm's floor)
    nor above ``max_level``."""

    entry_level: int = Field(
        ge=1,
        le=JAVA_INT_MAX,
        description=(
            "The realm's floor: the level needed to enter it. No mob there is lower, and a player below it meets "
            "every mob at it."
        ),
    )
    max_level: int = Field(ge=1, le=JAVA_INT_MAX, description="The highest level a mob there gets.")
    weights: list[int] = Field(
        min_length=1,
        max_length=2 * MAX_SPREAD + 1,
        description=(
            "The weight of each offset from the nearest player's level, lowest first, the middle one for the "
            "player's own level: [5, 10, 20, 30, 20, 10, 5] is -3 to +3. An odd number of weights, each 1 to "
            f"{MAX_WEIGHT}; the floor and the cap drop offsets, and the rest share the chance by their weights."
        ),
    )

    @model_validator(mode="after")
    def _shape(self) -> MobLevelRuleDef:
        if self.entry_level > self.max_level:
            raise ValueError(f"entry_level {self.entry_level} is above max_level {self.max_level}")
        if len(self.weights) % 2 == 0:
            raise ValueError(
                f"weights needs an odd number of entries, the middle one for offset 0: {self.weights}"
            )
        if any(not 1 <= weight <= MAX_WEIGHT for weight in self.weights):
            raise ValueError(f"every weight must be 1 to {MAX_WEIGHT}: {self.weights}")
        return self

    @property
    def spread(self) -> int:
        """How many levels a mob may be above or below the player."""
        return len(self.weights) // 2

    def chances(self, player_level: int) -> LevelChances:
        """The levels a mob spawning near a player of ``player_level`` may get: the game's ``MobLevelRule``
        computes the same. A player below the floor meets every mob at it; when the cap leaves no offset (a player
        far above it), every mob is at the cap."""
        if player_level < self.entry_level:
            return LevelChances(self.entry_level, (1,))
        lowest = max(self.entry_level, player_level - self.spread)
        highest = min(self.max_level, player_level + self.spread)
        if lowest > highest:
            return LevelChances(self.max_level, (1,))
        start = lowest - player_level + self.spread
        return LevelChances(lowest, tuple(self.weights[start : start + highest - lowest + 1]))


class DimensionRulesDef(StrictModel):
    """The gameplay rules of one dimension, which the ``dimension`` field names: the rule for mob levels and what a
    player needs to enter it. At most one entry per dimension."""

    id: ContentId
    dimension: ResourceId = Field(description="The dimension these rules are for, e.g. minecraft:overworld.")
    mob_levels: MobLevelRuleDef | None = Field(
        default=None,
        description="Optional: the level of mobs spawning there. Without it, mobs have no level.",
    )
    access: list[RequirementDef] | None = Field(
        default=None,
        min_length=1,
        description=(
            "Optional: what a player needs to enter the dimension, every requirement at once; refused on every way "
            "in (a portal, a command, a warp). A realm's rules name its unlock advancement (realm portal spec)."
        ),
    )
