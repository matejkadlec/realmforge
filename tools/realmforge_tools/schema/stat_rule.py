"""Stat rules: ``content/stat_rules/<id>.yaml``, the datapack registry ``realmforge:stat_rule`` (architecture 8.3)."""

from __future__ import annotations

from itertools import pairwise
from typing import Annotated, Literal

from pydantic import Field, model_validator

from realmforge_tools.project import MOD_ID
from realmforge_tools.schema.common import ContentId, ResourceId, StrictModel

FiniteNumber = Annotated[float, Field(allow_inf_nan=False)]
Positive = Annotated[float, Field(gt=0, allow_inf_nan=False)]
# The game reads milestone points as a Java int (stats/StatRule.Milestone).
JAVA_INT_MAX = 2**31 - 1

# Vanilla's AttributeModifier.Operation, by its serialized name.
Operation = Literal["add_value", "add_multiplied_base", "add_multiplied_total"]
MULTIPLIED: frozenset[str] = frozenset({"add_multiplied_base", "add_multiplied_total"})

# The role a class plays (ClassDef.role), which decides the role-bound bonuses of stat rules its players get
# (architecture 8.3); the game's stats/ClassRole has the same names. Assassins count as ranged.
Role = Literal["melee", "caster", "ranged"]
ROLES: tuple[str, ...] = ("melee", "caster", "ranged")


class StatModifierDef(StrictModel):
    """An attribute modifier a stat rule writes. ``add_value`` adds to the base; ``add_multiplied_base`` adds a share
    of the base (weapons included); ``add_multiplied_total`` multiplies the result, which validation refuses in stat
    rules: every value is ``(default + flat) * (1 + sum of percents)`` (architecture 8.2). Multiplied operations take
    ``amount_percent``. ``add_value`` on one of our stats takes its unit (``amount_percent`` for a share,
    ``amount`` for points); on a vanilla attribute, whichever fits it (``amount_percent: 5`` adds 0.05 to the
    fraction ``minecraft:knockback_resistance``)."""

    attribute: ResourceId = Field(description="The attribute it modifies, e.g. minecraft:attack_damage.")
    operation: Operation = Field(
        description="How the amount applies (vanilla's attribute modifier operations)."
    )
    amount: FiniteNumber | None = Field(
        default=None, description="Amount in game units, for add_value on points."
    )
    amount_percent: FiniteNumber | None = Field(
        default=None,
        description="Amount in percent (5 means 5 %, the fraction 0.05), for multiplied operations and shares.",
    )
    roles: list[Role] | None = Field(
        default=None,
        min_length=1,
        description="The class roles it applies to (melee, caster, ranged); left out, it applies to every player.",
    )

    @model_validator(mode="after")
    def _one_amount(self) -> StatModifierDef:
        if (self.amount is None) == (self.amount_percent is None):
            raise ValueError("set exactly one of amount and amount_percent")
        if self.game_amount == 0:
            raise ValueError("the amount must not be 0")
        if self.operation in MULTIPLIED and self.amount_percent is None:
            raise ValueError(f"{self.operation} multiplies: write the amount as amount_percent")
        if self.roles is not None and len(set(self.roles)) != len(self.roles):
            raise ValueError(f"a role is listed twice: {self.roles}")
        return self

    def applies_to(self, role: str | None) -> bool:
        """Whether a player of ``role`` (None: no character, or a class without a role) gets this modifier: one
        without roles applies to everyone, one with roles only to those roles (the game's ``StatRule.Modifier``
        agrees)."""
        return self.roles is None or role in self.roles

    @property
    def is_percent(self) -> bool:
        return self.amount_percent is not None

    @property
    def game_amount(self) -> float:
        """The amount in game units (a fraction for a percentage)."""
        if self.amount_percent is not None:
            return self.amount_percent / 100
        assert self.amount is not None
        return self.amount


class PerPointDef(StatModifierDef):
    """What every point of the stat gives. ``max`` (or ``max_percent``, in the unit of the amount) caps the total
    from all points; it needs a positive amount."""

    max: Positive | None = Field(default=None, description="Cap on the total, in points.")
    max_percent: Positive | None = Field(default=None, description="Cap on the total, in percent.")

    @model_validator(mode="after")
    def _cap_in_the_amount_unit(self) -> PerPointDef:
        if self.max is not None and self.max_percent is not None:
            raise ValueError("set at most one of max and max_percent")
        if self.max is not None and self.is_percent:
            raise ValueError("the amount is in percent, so the cap is max_percent")
        if self.max_percent is not None and not self.is_percent:
            raise ValueError("the amount is in points, so the cap is max")
        if self.game_max is not None and self.game_amount < 0:
            raise ValueError("a cap needs a positive amount")
        if self.game_max == 0:
            # max_percent below about 5e-322 underflows to 0 as a fraction, which the game refuses.
            raise ValueError("the cap is too small: it is 0 in game units")
        return self

    @property
    def game_max(self) -> float | None:
        if self.max_percent is not None:
            return self.max_percent / 100
        return self.max

    def total_at(self, value: float) -> float:
        """The amount for ``value`` points of the stat; the game's ``StatRules`` must agree (checked by a
        GameTest)."""
        total = value * self.game_amount
        return total if self.game_max is None else min(total, self.game_max)


class MilestoneDef(StrictModel):
    """A bonus the stat gives once it reaches ``points``, on top of the per-point bonuses."""

    points: int = Field(
        ge=1, le=JAVA_INT_MAX, description="Stat value that reaches the milestone (1 or more)."
    )
    modifiers: list[StatModifierDef] = Field(min_length=1, description="What the milestone gives.")


class StatRuleDef(StrictModel):
    """What one primary stat gives: per-point modifiers and milestone bonuses. Rules read a primary stat and write
    only secondary stats and vanilla attributes, so they never feed each other."""

    id: ContentId
    stat: ResourceId = Field(description="The primary stat it reads, e.g. realmforge:strength.")
    per_point: list[PerPointDef] = Field(default_factory=list, description="Modifiers every point gives.")
    milestones: list[MilestoneDef] = Field(
        default_factory=list, description="Bonuses at thresholds, in strictly increasing points order."
    )

    @model_validator(mode="after")
    def _shape(self) -> StatRuleDef:
        if not self.per_point and not self.milestones:
            raise ValueError("a stat rule gives something: add per_point or milestones")
        for before, after in pairwise(m.points for m in self.milestones):
            if after <= before:
                raise ValueError(f"milestone points must strictly increase, but {after} follows {before}")
        return self

    def modifiers(self) -> list[tuple[str, StatModifierDef]]:
        """Every modifier with its field path (``per_point.0``, ``milestones.1.modifiers.0``), for messages."""
        found: list[tuple[str, StatModifierDef]] = [
            (f"per_point.{n}", modifier) for n, modifier in enumerate(self.per_point)
        ]
        for m, milestone in enumerate(self.milestones):
            found += [
                (f"milestones.{m}.modifiers.{n}", modifier) for n, modifier in enumerate(milestone.modifiers)
            ]
        return found

    def modifier_id(self, entry: str) -> str:
        """The id of one modifier the rule gives: ``realmforge:stat/<namespace>/<entry>/<path>``, the entry being
        ``<n>`` for the n-th per-point entry and ``at_<points>_<n>`` for the n-th modifier of a milestone. The rule's
        path comes last, as only it may contain slashes (the game's ``StatRules`` builds the same)."""
        return f"{MOD_ID}:stat/{MOD_ID}/{entry}/{self.id}"

    @property
    def role_bound(self) -> bool:
        """Whether any of its modifiers applies to some class roles only."""
        return any(modifier.roles is not None for _, modifier in self.modifiers())

    def bonuses_at(self, value: float, role: str | None = None) -> list[tuple[str, str, str, float]]:
        """The modifiers ``value`` points give a player of ``role`` (None: no character), as (id, attribute,
        operation, amount): every per-point entry for that role with a non-zero total, then every modifier for that
        role of each milestone reached. The game's ``StatRules`` must give the same.

        Ids follow architecture 8.2 (``modifier_id``): an entry keeps its position whatever the role, so a role's
        bonuses never take another's id."""
        found: list[tuple[str, str, str, float]] = []
        for n, entry in enumerate(self.per_point):
            total = entry.total_at(value)
            if total != 0 and entry.applies_to(role):
                found.append((self.modifier_id(str(n)), entry.attribute, entry.operation, total))
        for milestone in self.milestones:
            if value >= milestone.points:
                found += [
                    (
                        self.modifier_id(f"at_{milestone.points}_{n}"),
                        modifier.attribute,
                        modifier.operation,
                        modifier.game_amount,
                    )
                    for n, modifier in enumerate(milestone.modifiers)
                    if modifier.applies_to(role)
                ]
        return found
