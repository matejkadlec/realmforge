"""Stats: ``content/stats/<id>.yaml``, our attributes (architecture 8.1), registered on ``minecraft:attribute``."""

from __future__ import annotations

from typing import Annotated, Literal

from pydantic import Field, model_validator

from realmforge_tools.project import MOD_ID
from realmforge_tools.schema.common import ContentId, StrictModel

NonNegative = Annotated[float, Field(ge=0, allow_inf_nan=False)]
Positive = Annotated[float, Field(gt=0, allow_inf_nan=False)]


class StatDef(StrictModel):
    """One of our attributes: a primary stat (strength, dexterity, vitality, intellect) that stat rules read, or a
    secondary stat that stat rules and other sources write (crit chance, spell power, ...). Generates its
    registration, added to every player. Its minimum is 0.

    A stat counts either points (``default``, ``max``) or a share (``default_percent``, ``max_percent``, written
    in percent and shown as a percentage in the game). Primary stats count points.
    """

    id: ContentId
    kind: Literal["primary", "secondary"] = Field(
        description="primary: read by stat rules (content/stat_rules/); secondary: written by them and other sources."
    )
    default: NonNegative | None = Field(default=None, description="Starting value in points (default 0).")
    max: Positive | None = Field(default=None, description="Highest value in points.")
    default_percent: NonNegative | None = Field(
        default=None, description="Starting value in percent (default 0)."
    )
    max_percent: Positive | None = Field(default=None, description="Highest value in percent.")

    @model_validator(mode="after")
    def _one_unit(self) -> StatDef:
        if (self.max is None) == (self.max_percent is None):
            raise ValueError("set exactly one of max (points) and max_percent (a share)")
        if self.percent and self.default is not None:
            raise ValueError("a stat with max_percent writes its default as default_percent")
        if not self.percent and self.default_percent is not None:
            raise ValueError("a stat with max writes its default as default, not default_percent")
        if self.percent and self.kind == "primary":
            raise ValueError("a primary stat counts points: use max, not max_percent")
        if self.game_default > self.game_max:
            raise ValueError("the default must not be above the maximum")
        return self

    @property
    def percent(self) -> bool:
        """Whether the stat is a share, shown as a percentage (NeoForge's ``PercentageAttribute``)."""
        return self.max_percent is not None

    @property
    def game_default(self) -> float:
        """The default in game units (a fraction for a share)."""
        if self.percent:
            return (self.default_percent or 0.0) / 100
        return self.default or 0.0

    @property
    def game_max(self) -> float:
        """The maximum in game units (a fraction for a share)."""
        if self.max_percent is not None:
            return self.max_percent / 100
        assert self.max is not None
        return self.max


def stat_description_id(stat_id: str) -> str:
    """The attribute's description id, its translation key: ``attribute.realmforge.<id>``."""
    return f"attribute.{MOD_ID}.{stat_id}"
