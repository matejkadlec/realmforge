"""Curves: ``content/curves/<id>.yaml``, the datapack registry ``realmforge:curve`` (architecture 5.2)."""

from __future__ import annotations

from bisect import bisect_right
from itertools import pairwise
from typing import Annotated

from pydantic import Field, model_validator

from realmforge_tools.schema.common import ContentId, LocalizedText, StrictModel

FiniteNumber = Annotated[float, Field(allow_inf_nan=False)]


class CurvePoint(StrictModel):
    """One point of a curve: the value at a level."""

    level: int = Field(ge=0, description="Level (0 or more).")
    value: FiniteNumber = Field(description="Value at that level.")


class CurveDef(StrictModel):
    """A named table of points (level -> value), linearly interpolated between the points and constant beyond the
    first and the last one: XP per level, mob scaling, item budgets."""

    id: ContentId
    name: LocalizedText | None = Field(
        default=None, description="Optional display name, for tools and debugging."
    )
    points: list[CurvePoint] = Field(min_length=1, description="Points in strictly increasing level order.")

    @model_validator(mode="after")
    def _levels_increase(self) -> CurveDef:
        levels = [point.level for point in self.points]
        for before, after in pairwise(levels):
            if after <= before:
                raise ValueError(f"point levels must strictly increase, but {after} follows {before}")
        return self

    def value_at(self, level: float) -> float:
        """The curve's value at ``level``; the game's ``Curve#valueAt`` must agree (checked by a GameTest)."""
        levels = [point.level for point in self.points]
        index = bisect_right(levels, level)
        if index == 0:
            return self.points[0].value
        if index == len(self.points):
            return self.points[-1].value
        low, high = self.points[index - 1], self.points[index]
        t = (level - low.level) / (high.level - low.level)
        return low.value + t * (high.value - low.value)
