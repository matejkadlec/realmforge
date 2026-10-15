"""Kill XP multipliers: ``content/kill_xp_multipliers/<id>.yaml``, the datapack registry
``realmforge:kill_xp_multiplier`` (architecture 5.2, 10.2).

An entry names an entity type in ``entity`` and multiplies the kill XP its mobs give (the XP model of 2026-10-15,
RLM-192, calls it the mob's rank): 0 for a mob that gives nothing, 0.25 for a minion, 1.5 for a strong mob, 10 for
a boss. A hostile mob without an entry gives 1.0. Slimes and magma cubes may also give by size (``by_size``): the
first entry whose ``max_size`` the mob does not exceed. At most one entry per entity type.
"""

from __future__ import annotations

from itertools import pairwise
from typing import Annotated

from pydantic import Field, model_validator

from realmforge_tools.schema.common import ContentId, ResourceId, StrictModel

# The vanilla entity types with a size (net.minecraft.world.entity.monster.Slime and its subclass MagmaCube).
SIZED_ENTITY_TYPES = ("minecraft:slime", "minecraft:magma_cube")
# Slime#setSize clamps a size to 1 .. 127.
MAX_SIZE = 127
# A guard against typos, not a balance number: a realm guardian's rank is 20 (RLM-192).
MAX_MULTIPLIER = 1000

Multiplier = Annotated[float, Field(ge=0, le=MAX_MULTIPLIER, allow_inf_nan=False)]


class SizeMultiplierDef(StrictModel):
    """The multiplier of a sized mob (a slime) up to a size."""

    max_size: int = Field(
        ge=1, le=MAX_SIZE, description="The largest size this multiplier applies to (1 or more)."
    )
    multiplier: Multiplier = Field(description="Kill XP multiplier for these sizes (0 gives nothing).")


class KillXpMultiplierDef(StrictModel):
    """How much kill XP the mobs of one entity type give, as a multiple of a normal mob's."""

    id: ContentId
    entity: ResourceId = Field(
        description="The entity type, e.g. minecraft:witch. At most one entry per type."
    )
    multiplier: Multiplier = Field(description="Kill XP multiplier (1 is a normal mob, 0 gives nothing).")
    by_size: list[SizeMultiplierDef] = Field(
        default_factory=list,
        description=(
            "Slimes and magma cubes only: multipliers by size, in increasing max_size order; the first entry whose "
            "max_size the mob does not exceed applies, larger mobs take multiplier."
        ),
    )

    @model_validator(mode="after")
    def _sizes_increase(self) -> KillXpMultiplierDef:
        for before, after in pairwise(self.by_size):
            if after.max_size <= before.max_size:
                raise ValueError(
                    f"by_size max_size must strictly increase, but {after.max_size} follows {before.max_size}"
                )
        return self

    def multiplier_at(self, size: int | None) -> float:
        """The multiplier of a mob of this type with ``size`` (None: the type has no size). The game's
        ``character/KillXpMultiplier#at`` must agree (checked by a GameTest)."""
        if size is not None:
            for entry in self.by_size:
                if size <= entry.max_size:
                    return entry.multiplier
        return self.multiplier
