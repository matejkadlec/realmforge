"""Recipes: the ``recipe`` of an item or block -> ``data/realmforge/recipe/<id>.json``, a vanilla recipe whose result
is the entry itself: ``minecraft:crafting_shaped``, ``minecraft:crafting_shapeless`` or ``minecraft:smithing_transform``
(``schema/recipe.py``). A tag ingredient keeps vanilla's ``#namespace:path`` form."""

from __future__ import annotations

from pathlib import Path
from typing import Any

from realmforge_tools.content import Content
from realmforge_tools.formats.jsonfmt import dump_json
from realmforge_tools.generate.datapack import data_dir
from realmforge_tools.project import MOD_ID, RepoPaths
from realmforge_tools.schema.recipe import ShapedRecipeDef, ShapelessRecipeDef, SmithingRecipeDef


def generate(content: Content, paths: RepoPaths) -> dict[Path, bytes]:
    folder = data_dir(paths) / "recipe"
    entries = [*content.items, *content.blocks]
    return {
        folder / f"{entry.model.id}.json": dump_json(
            recipe_data(f"{MOD_ID}:{entry.model.id}", entry.model.recipe)
        )
        for entry in entries
        if entry.model.recipe is not None
    }


def recipe_data(
    result: str, recipe: ShapedRecipeDef | ShapelessRecipeDef | SmithingRecipeDef
) -> dict[str, Any]:
    """The recipe as vanilla writes its own (``lodestone.json``, ``purple_dye.json``, ``netherite_sword_smithing.json``):
    one result leaves out its count."""
    if isinstance(recipe, SmithingRecipeDef):
        return {
            "type": "minecraft:smithing_transform",
            "addition": recipe.addition,
            "base": recipe.base,
            "result": {"id": result},
            "template": recipe.template,
        }
    data: dict[str, Any]
    if isinstance(recipe, ShapelessRecipeDef):
        data = {
            "type": "minecraft:crafting_shapeless",
            "category": recipe.category,
            "ingredients": list(recipe.ingredients),
        }
    else:
        data = {
            "type": "minecraft:crafting_shaped",
            "category": recipe.category,
            "key": dict(sorted(recipe.key.items())),
            "pattern": list(recipe.pattern),
        }
    data["result"] = {"id": result} if recipe.count == 1 else {"count": recipe.count, "id": result}
    return data
