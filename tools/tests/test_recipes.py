"""The recipe of an item or block: a shaped crafting recipe that makes it (schema/recipe.py, generate/recipes.py)."""

from __future__ import annotations

import json

import pytest

from realmforge_tools.generate import generate_all
from realmforge_tools.problems import ValidationFailed
from realmforge_tools.project import RepoPaths
from realmforge_tools.validate import load_and_validate

from .conftest import write

RECIPES = "mod/src/generated/resources/data/realmforge/recipe"
ITEM = "id: test_key\nname: {en_us: Test Key}\n"
BLOCK = "id: test_rock\nname: {en_us: Test Rock}\nhardness: 1.5\n"
RECIPE = "recipe:\n  pattern: [SSS, 'S#S', SSS]\n  key:\n    S: minecraft:stone_bricks\n    '#': minecraft:iron_ingot\n"


def problems_for(repo: RepoPaths) -> list[str]:
    with pytest.raises(ValidationFailed) as excinfo:
        load_and_validate(repo)
    return [str(p) for p in excinfo.value.problems]


def generate(repo: RepoPaths) -> dict[str, bytes]:
    content, _ = load_and_validate(repo)
    return {repo.rel(path): data for path, data in generate_all(content, repo).items()}


def test_items_and_blocks_get_vanillas_shaped_recipe(repo: RepoPaths) -> None:
    write(repo.content / "items" / "test_key.yaml", ITEM + RECIPE + "  count: 2\n  category: equipment\n")
    write(repo.content / "blocks" / "test_rock.yaml", BLOCK + RECIPE)
    files = generate(repo)
    assert json.loads(files[f"{RECIPES}/test_rock.json"]) == {
        "type": "minecraft:crafting_shaped",
        "category": "misc",
        "key": {"#": "minecraft:iron_ingot", "S": "minecraft:stone_bricks"},
        "pattern": ["SSS", "S#S", "SSS"],
        "result": {"id": "realmforge:test_rock"},
    }, "as vanilla's lodestone.json: one result leaves out its count"
    key = json.loads(files[f"{RECIPES}/test_key.json"])
    assert (key["category"], key["result"]) == ("equipment", {"count": 2, "id": "realmforge:test_key"})


def test_an_entry_without_a_recipe_gets_none(repo: RepoPaths) -> None:
    write(repo.content / "items" / "test_key.yaml", ITEM)
    assert f"{RECIPES}/test_key.json" not in generate(repo)


def test_every_ingredient_is_an_item(repo: RepoPaths) -> None:
    write(repo.content / "items" / "test_key.yaml", ITEM + RECIPE.replace("iron_ingot", "iron_ingto"))
    problems = problems_for(repo)
    assert any("test_key.yaml" in p and "recipe.key.#" in p and "iron_ingto" in p for p in problems), problems


@pytest.mark.parametrize(
    ("pattern", "key", "message"),
    [
        ("[SS, S]", "{S: minecraft:stone}", "same width"),
        ("[SX]", "{S: minecraft:stone}", "does not say what X stands for"),
        ("[S]", "{S: minecraft:stone, X: minecraft:stick}", "does not use X"),
        ("['   ']", "{S: minecraft:stone}", "names no ingredient"),
        ("[SSSS]", "{S: minecraft:stone}", "pattern.0"),
        ("[S, S, S, S]", "{S: minecraft:stone}", "pattern"),
    ],
)
def test_the_pattern_and_key_must_fit_a_crafting_grid(
    repo: RepoPaths, pattern: str, key: str, message: str
) -> None:
    write(repo.content / "items" / "test_key.yaml", ITEM + f"recipe:\n  pattern: {pattern}\n  key: {key}\n")
    problems = problems_for(repo)
    assert any("test_key.yaml" in p and message in p for p in problems), problems


def test_a_tag_stands_for_any_of_its_items(repo: RepoPaths) -> None:
    write(
        repo.content / "items" / "test_key.yaml",
        ITEM + "recipe:\n  pattern: ['WWW', 'W W']\n  key: {W: '#minecraft:wool'}\n",
    )
    assert json.loads(generate(repo)[f"{RECIPES}/test_key.json"])["key"] == {"W": "#minecraft:wool"}


def test_an_unknown_tag_is_refused_with_close_matches(repo: RepoPaths) -> None:
    write(
        repo.content / "items" / "test_key.yaml",
        ITEM + "recipe:\n  pattern: [W]\n  key: {W: '#minecraft:wol'}\n",
    )
    problems = problems_for(repo)
    assert any(
        "recipe.key.W" in p and "unknown tag '#minecraft:wol'" in p and "#minecraft:wool" in p
        for p in problems
    )


def test_a_shapeless_recipe_lists_its_ingredients(repo: RepoPaths) -> None:
    write(
        repo.content / "items" / "test_key.yaml",
        ITEM
        + "recipe:\n  ingredients: ['#minecraft:wool', minecraft:string, minecraft:string]\n  count: 2\n",
    )
    assert json.loads(generate(repo)[f"{RECIPES}/test_key.json"]) == {
        "type": "minecraft:crafting_shapeless",
        "category": "misc",
        "ingredients": ["#minecraft:wool", "minecraft:string", "minecraft:string"],
        "result": {"count": 2, "id": "realmforge:test_key"},
    }, "as vanilla's purple_dye.json"


def test_a_smithing_recipe_upgrades_another_item(repo: RepoPaths) -> None:
    write(
        repo.content / "items" / "test_key.yaml",
        ITEM
        + "recipe:\n  template: minecraft:netherite_upgrade_smithing_template\n  base: minecraft:diamond_sword\n"
        "  addition: '#minecraft:netherite_tool_materials'\n",
    )
    assert json.loads(generate(repo)[f"{RECIPES}/test_key.json"]) == {
        "type": "minecraft:smithing_transform",
        "addition": "#minecraft:netherite_tool_materials",
        "base": "minecraft:diamond_sword",
        "result": {"id": "realmforge:test_key"},
        "template": "minecraft:netherite_upgrade_smithing_template",
    }, "as vanilla's netherite_sword_smithing.json"


@pytest.mark.parametrize(
    ("recipe", "message"),
    [
        (
            "  template: minecraft:netherite_upgrade_smithing_template\n  base: realmforge:test_key\n"
            "  addition: minecraft:netherite_ingot\n",
            "makes the item from another one",
        ),
        ("  ingredients: [minecraft:sting]\n", "recipe.ingredients.0"),
        ("  ingredients: []\n", "ingredients"),
        ("  count: 2\n", "a pattern (shaped), ingredients (shapeless) or a template (smithing)"),
    ],
)
def test_shapeless_and_smithing_recipes_are_checked(repo: RepoPaths, recipe: str, message: str) -> None:
    write(repo.content / "items" / "test_key.yaml", ITEM + "recipe:\n" + recipe)
    problems = problems_for(repo)
    assert any("test_key.yaml" in p and message in p for p in problems), problems
