"""Stats: ``content/stats/<id>.yaml``, our attributes (architecture 8.1), registered on ``minecraft:attribute``."""

from __future__ import annotations

from typing import Annotated, Literal

from pydantic import Field, model_validator

from realmforge_tools.project import MOD_ID
from realmforge_tools.schema.common import ContentId, LocalizedText, StrictModel

NonNegative = Annotated[float, Field(ge=0, allow_inf_nan=False)]
Positive = Annotated[float, Field(gt=0, allow_inf_nan=False)]


class ExhaustionDef(StrictModel):
    """How a stat lowers the food exhaustion its player gains (architecture 8.1, RLM-171): from sprinting, jumping,
    swimming, attacking, taking damage, mining and the Hunger effect, everything vanilla adds through
    ``Player#causeFoodExhaustion``. Natural regeneration's food cost stays vanilla's. The shares of several stats add
    up, at most 100 %."""

    per_point_percent: Positive = Field(description="How much each point lowers exhaustion, in percent.")
    max_percent: Annotated[float, Field(gt=0, le=100, allow_inf_nan=False)] = Field(
        description="The most it lowers exhaustion, in percent (at most 100)."
    )


class StatDef(StrictModel):
    """One of our attributes: a primary stat (strength, dexterity, vitality, intellect) that stat rules read, or a
    secondary stat that stat rules and other sources write (crit chance, spell power, ...). Generates its
    registration, added to every player. Its minimum is 0.

    A stat counts either points (``default``, ``max``) or a share (``default_percent``, ``max_percent``, written
    in percent and shown as a percentage in the game). Primary stats count points. Its name is the attribute's
    translation, ``attribute.realmforge.<id>``: the character sheet and item tooltips show it.
    """

    id: ContentId
    name: LocalizedText = Field(description="The stat's name, shown on the character sheet and in tooltips.")
    kind: Literal["primary", "secondary"] = Field(
        description="primary: read by stat rules (content/stat_rules/); secondary: written by them and other sources."
    )
    default: NonNegative | None = Field(default=None, description="Starting value in points (default 0).")
    max: Positive | None = Field(default=None, description="Highest value in points.")
    default_percent: NonNegative | None = Field(
        default=None, description="Starting value in percent (default 0)."
    )
    max_percent: Positive | None = Field(default=None, description="Highest value in percent.")
    lowers_exhaustion: ExhaustionDef | None = Field(
        default=None,
        description="Each point lowers the food exhaustion the player gains (Stamina); a stat in points only.",
    )

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
        if self.game_max == 0:
            # max_percent below about 5e-322 underflows to 0 as a fraction: a stat that could never leave 0.
            raise ValueError("the maximum is too small: it is 0 in game units")
        if self.game_default > self.game_max:
            raise ValueError("the default must not be above the maximum")
        if self.lowers_exhaustion is not None and self.percent:
            raise ValueError("lowers_exhaustion counts per point: the stat needs max, not max_percent")
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

    def exhaustion_reduction_at(self, value: float) -> float:
        """The share of exhaustion ``value`` points take away, as a fraction: 0 for a stat without
        ``lowers_exhaustion``. The game's ``stats/ExhaustionReduction`` must agree (checked by a GameTest)."""
        if self.lowers_exhaustion is None:
            return 0.0
        per_point = self.lowers_exhaustion.per_point_percent / 100
        return min(max(value, 0.0) * per_point, self.lowers_exhaustion.max_percent / 100)


def stat_description_id(stat_id: str) -> str:
    """The attribute's description id, its translation key: ``attribute.realmforge.<id>``."""
    return f"attribute.{MOD_ID}.{stat_id}"
