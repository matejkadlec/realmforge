"""Recipes: the ``recipe`` of an item or block -> ``data/realmforge/recipe/<id>.json``, a vanilla
``minecraft:crafting_shaped`` recipe whose result is the entry itself (``schema/recipe.py``)."""

from __future__ import annotations

from pathlib import Path
from typing import Any

from realmforge_tools.content import Content
from realmforge_tools.formats.jsonfmt import dump_json
from realmforge_tools.generate.datapack import data_dir
from realmforge_tools.project import MOD_ID, RepoPaths
from realmforge_tools.schema.recipe import ShapedRecipeDef


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


def recipe_data(result: str, recipe: ShapedRecipeDef) -> dict[str, Any]:
    """The recipe as vanilla writes its own (``data/minecraft/recipe/lodestone.json``): one result leaves out its
    count."""
    data: dict[str, Any] = {
        "type": "minecraft:crafting_shaped",
        "category": recipe.category,
        "key": dict(sorted(recipe.key.items())),
        "pattern": list(recipe.pattern),
        "result": {"id": result},
    }
    if recipe.count != 1:
        data["result"] = {"count": recipe.count, "id": result}
    return data
