"""Dimension rules: ``content/dimensions/<id>.yaml``, the datapack registry ``realmforge:dimension_rules``
(architecture 5.2, 14.1, 15.1)."""

from __future__ import annotations

import math

from pydantic import Field, model_validator

from realmforge_tools.schema.common import ContentId, ResourceId, StrictModel
from realmforge_tools.schema.requirement import RequirementDef

# The game reads every number here as a Java int (world/MobLevelRule); the world is far smaller than that.
JAVA_INT_MAX = 2**31 - 1
JAVA_INT_MIN = -(2**31)
# The largest jitter (world/MobLevelRule.MAX_JITTER), far beyond any band.
MAX_JITTER = 1000


class DepthDef(StrictModel):
    """Deeper is stronger: below ``below_y``, a mob is one level higher for every ``blocks_per_level`` blocks."""

    below_y: int = Field(
        ge=JAVA_INT_MIN, le=JAVA_INT_MAX, description="The height below which depth adds levels."
    )
    blocks_per_level: int = Field(
        ge=1, le=JAVA_INT_MAX, description="Blocks below below_y for each level more (1 or more)."
    )


class MobLevelRuleDef(StrictModel):
    """How the level of a mob spawning in the dimension follows from where it spawns: the level at the
    dimension's origin, one level more every ``blocks_per_level`` blocks of horizontal distance from it, more with
    depth, a random spread of ``jitter`` levels either way, all clamped to ``min_level`` .. ``max_level``."""

    origin_level: int = Field(
        ge=1, le=JAVA_INT_MAX, description="Level at the origin (x 0, z 0), above the depth line."
    )
    blocks_per_level: int = Field(
        ge=1,
        le=JAVA_INT_MAX,
        description="Horizontal distance from the origin, in blocks, for each level more (1 or more).",
    )
    depth: DepthDef | None = Field(default=None, description="Optional: levels added with depth.")
    jitter: int = Field(
        default=0,
        ge=0,
        le=MAX_JITTER,
        description="Each mob's level is up to this many levels higher or lower, at random (0: none).",
    )
    min_level: int = Field(ge=1, le=JAVA_INT_MAX, description="The lowest level in the dimension.")
    max_level: int = Field(ge=1, le=JAVA_INT_MAX, description="The highest level in the dimension.")

    @model_validator(mode="after")
    def _range(self) -> MobLevelRuleDef:
        if self.min_level > self.max_level:
            raise ValueError(f"min_level {self.min_level} is above max_level {self.max_level}")
        if not self.min_level <= self.origin_level <= self.max_level:
            raise ValueError(
                f"origin_level {self.origin_level} is outside min_level {self.min_level} .. "
                f"max_level {self.max_level}"
            )
        return self

    def unclamped_level_at(self, x: float, y: float, z: float) -> float:
        """The level before the jitter and the clamp: the game's ``MobLevelRule`` computes the same."""
        distance = math.sqrt(x * x + z * z)
        level = self.origin_level + math.floor(distance / self.blocks_per_level)
        if self.depth is not None and y < self.depth.below_y:
            level += math.floor((self.depth.below_y - y) / self.depth.blocks_per_level)
        return float(level)

    def level_at(self, x: float, y: float, z: float, offset: int = 0) -> int:
        """The level of a mob spawning at ``x, y, z`` whose jitter came out as ``offset`` (-jitter .. jitter)."""
        level = self.unclamped_level_at(x, y, z) + offset
        return int(min(max(level, self.min_level), self.max_level))


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
