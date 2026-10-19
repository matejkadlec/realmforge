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
