"""Recipes: the ``recipe`` of an item or block (``content/items/``, ``content/blocks/``), how a player makes it, and
the vanilla recipes ``content/recipes/`` replaces.

A recipe whose result is the entry itself, in one of three kinds, told apart by their keys: a shaped crafting recipe
(``pattern`` and ``key``), a shapeless one (``ingredients``) or a smithing table upgrade (``template``, ``base`` and
``addition``). It becomes ``data/realmforge/recipe/<id>.json`` in vanilla's format (``minecraft:crafting_shaped``,
``minecraft:crafting_shapeless``, ``minecraft:smithing_transform``; ``generate/recipes.py``).

A ``content/recipes/<id>.yaml`` entry replaces a vanilla recipe (``RecipeOverrideDef``): it becomes
``data/minecraft/recipe/<path>.json``, the vanilla recipe's own path. The core mod's data pack sits above vanilla's, so
the game loads that file instead of vanilla's and merges nothing: the entry holds the whole recipe, its result included.
"""

from __future__ import annotations

from typing import Annotated, Any, Literal

from pydantic import Discriminator, Field, StringConstraints, Tag, model_validator

from realmforge_tools.schema.common import ContentId, ResourceId, StrictModel

# A row of the crafting grid: one to three characters, a space for an empty slot.
PatternRow = Annotated[str, StringConstraints(min_length=1, max_length=3)]
# The character a pattern uses for an ingredient: anything but a space.
KeyChar = Annotated[str, StringConstraints(pattern=r"^\S$")]
# Vanilla's crafting book categories (net.minecraft.world.item.crafting.CraftingBookCategory).
RecipeCategory = Literal["building", "redstone", "equipment", "misc"]
# An ingredient: an item, or an item tag as #namespace:path (vanilla's ingredient form: any item of the tag fits).
Ingredient = Annotated[
    str,
    StringConstraints(pattern=r"^#?[a-z0-9_.-]+:[a-z0-9_./-]+$"),
    Field(
        description="An item (minecraft:stick) or an item tag (#minecraft:wool). Validated against the ID universe."
    ),
]
# The most ingredients a crafting grid holds.
GRID_SLOTS = 9


def is_tag(ingredient: str) -> bool:
    """Whether ``ingredient`` names an item tag (``#minecraft:wool``) rather than an item."""
    return ingredient.startswith("#")


class ShapedRecipeDef(StrictModel):
    """A shaped crafting recipe that makes the entry: its rows of the crafting grid and the ingredient each character
    stands for."""

    pattern: list[PatternRow] = Field(
        min_length=1,
        max_length=3,
        description='Rows of the crafting grid, top to bottom, all the same width; a space is an empty slot: ["SSS", "S#S"].',
    )
    key: dict[KeyChar, Ingredient] = Field(
        min_length=1,
        description=(
            "Character -> the ingredient it stands for, e.g. S: minecraft:stone_bricks or W: '#minecraft:wool'. "
            "Every character is used."
        ),
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

    def ingredients_by_path(self) -> list[tuple[str, str]]:
        """Each ingredient with its path in the YAML, for validation."""
        return [(f"key.{char}", ingredient) for char, ingredient in sorted(self.key.items())]


class ShapelessRecipeDef(StrictModel):
    """A shapeless crafting recipe that makes the entry: its ingredients go anywhere in the grid."""

    ingredients: list[Ingredient] = Field(
        min_length=1,
        max_length=GRID_SLOTS,
        description=(
            "One entry per grid slot it fills (1 to 9), repeated for two of the same: "
            "['#minecraft:wool', '#minecraft:wool', minecraft:iron_ingot]."
        ),
    )
    count: int = Field(default=1, ge=1, le=64, description="How many the recipe makes.")
    category: RecipeCategory = Field(default="misc", description="Its tab in the recipe book.")

    def ingredients_by_path(self) -> list[tuple[str, str]]:
        return [(f"ingredients.{n}", ingredient) for n, ingredient in enumerate(self.ingredients)]


class SmithingRecipeDef(StrictModel):
    """A smithing table upgrade that makes the entry from ``base``, as vanilla's diamond-to-netherite upgrade: the
    result keeps the base's enchantments, damage and name."""

    template: Ingredient = Field(
        description="The smithing template, e.g. minecraft:netherite_upgrade_smithing_template."
    )
    base: Ingredient = Field(description="The item it upgrades, e.g. realmforge:diamond_wand.")
    addition: Ingredient = Field(
        description="The material it adds, e.g. '#minecraft:netherite_tool_materials'."
    )

    def ingredients_by_path(self) -> list[tuple[str, str]]:
        return [("template", self.template), ("base", self.base), ("addition", self.addition)]


def _recipe_kind(value: Any) -> str | None:
    """Which kind of recipe ``value`` is, by its keys: shaped (``pattern``), shapeless (``ingredients``) or smithing
    (``template``)."""
    if isinstance(value, dict):
        for key, kind in (("pattern", "shaped"), ("ingredients", "shapeless"), ("template", "smithing")):
            if key in value:
                return kind
        return None
    return {ShapedRecipeDef: "shaped", ShapelessRecipeDef: "shapeless", SmithingRecipeDef: "smithing"}.get(
        type(value)
    )


RecipeDef = Annotated[
    Annotated[ShapedRecipeDef, Tag("shaped")]
    | Annotated[ShapelessRecipeDef, Tag("shapeless")]
    | Annotated[SmithingRecipeDef, Tag("smithing")],
    Discriminator(
        _recipe_kind,
        custom_error_type="recipe_kind",
        custom_error_message="a recipe has a pattern (shaped), ingredients (shapeless) or a template (smithing)",
    ),
]


# A vanilla recipe a content/recipes/ entry replaces: minecraft:<path>.
VanillaRecipeId = Annotated[
    str,
    StringConstraints(pattern=r"^minecraft:[a-z0-9_]+(/[a-z0-9_]+)*$"),
    Field(
        description=(
            "The vanilla recipe it replaces, e.g. minecraft:netherite_upgrade_smithing_template. "
            "Validated against the ID universe."
        )
    ),
]


class RecipeOverrideDef(StrictModel):
    """A vanilla recipe, replaced by this one in the same place: ``data/minecraft/recipe/<path>.json``."""

    id: ContentId
    replaces: VanillaRecipeId
    result: ResourceId = Field(
        description="The item it makes, e.g. minecraft:netherite_upgrade_smithing_template. Validated as an item."
    )
    recipe: RecipeDef = Field(
        description="The whole recipe, as an item's: shaped, shapeless or a smithing upgrade (count and category too)."
    )

    @property
    def data_path(self) -> str:
        """The recipe file's path below ``data/``: vanilla's own, ``minecraft/recipe/<path>.json``."""
        return f"minecraft/recipe/{self.replaces.removeprefix('minecraft:')}.json"
