"""Recipes: the ``recipe`` of an item or block (``content/items/``, ``content/blocks/``), how a player crafts it.

A shaped crafting recipe whose result is the entry itself. It becomes ``data/realmforge/recipe/<id>.json``, vanilla's
``minecraft:crafting_shaped`` format (``generate/recipes.py``).
"""

from __future__ import annotations

from typing import Annotated, Literal

from pydantic import Field, StringConstraints, model_validator

from realmforge_tools.schema.common import ResourceId, StrictModel

# A row of the crafting grid: one to three characters, a space for an empty slot.
PatternRow = Annotated[str, StringConstraints(min_length=1, max_length=3)]
# The character a pattern uses for an ingredient: anything but a space.
KeyChar = Annotated[str, StringConstraints(pattern=r"^\S$")]
# Vanilla's crafting book categories (net.minecraft.world.item.crafting.CraftingBookCategory).
RecipeCategory = Literal["building", "redstone", "equipment", "misc"]


class ShapedRecipeDef(StrictModel):
    """A shaped crafting recipe that makes the entry: its rows of the crafting grid and the item each character
    stands for."""

    pattern: list[PatternRow] = Field(
        min_length=1,
        max_length=3,
        description='Rows of the crafting grid, top to bottom, all the same width; a space is an empty slot: ["SSS", "S#S"].',
    )
    key: dict[KeyChar, ResourceId] = Field(
        min_length=1,
        description="Character -> the item it stands for, e.g. S: minecraft:stone_bricks. Every character is used.",
    )
    count: int = Field(default=1, ge=1, le=64, description="How many the recipe makes.")
    category: RecipeCategory = Field(default="misc", description="Its tab in the recipe book.")

    @model_validator(mode="after")
    def _grid(self) -> ShapedRecipeDef:
        if len({len(row) for row in self.pattern}) > 1:
            raise ValueError("every row of the pattern has the same width")
        used = {char for row in self.pattern for char in row if char != " "}
        if not used:
            raise ValueError("the pattern names no ingredient")
        missing = sorted(used - self.key.keys())
        if missing:
            raise ValueError(f"the key does not say what {', '.join(missing)} stands for")
        unused = sorted(self.key.keys() - used)
        if unused:
            raise ValueError(f"the pattern does not use {', '.join(unused)}")
        return self
