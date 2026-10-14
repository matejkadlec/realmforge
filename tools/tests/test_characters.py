"""Races (content/races/) and classes (content/classes/): validation, the reference bonuses and generation."""

from __future__ import annotations

import json

import pytest
from pydantic import ValidationError

from realmforge_tools.generate import generate_all
from realmforge_tools.problems import ValidationFailed
from realmforge_tools.project import RepoPaths
from realmforge_tools.schema.character import CharacterModifierDef, bonuses_at, modifier_id
from realmforge_tools.validate import load_and_validate
from tests.conftest import write

DATA = "mod/src/generated/resources/data/realmforge/realmforge"
QA = "qa-mod/src/generated/java/io/github/matejkadlec/realmforge/qa/generated"
LANG = "mod/src/generated/resources/assets/realmforge/lang/en_us.json"

RACE = """id: elf
name: {en_us: Elf}
description: {en_us: Light on their feet.}
classes: [realmforge:warrior]
"""
CLASS = """id: rogue
name: {en_us: Rogue}
description: {en_us: Quick and quiet.}
resource: realmforge:rage
"""
BREAD = "  - {item: minecraft:bread, count: 32}\n"


def problems_for(repo: RepoPaths) -> list[str]:
    with pytest.raises(ValidationFailed) as excinfo:
        load_and_validate(repo)
    return [str(p) for p in excinfo.value.problems]


def assert_problem(repo: RepoPaths, expected: str) -> None:
    problems = problems_for(repo)
    assert any(expected in p for p in problems), problems


def generate(repo: RepoPaths) -> dict[str, bytes]:
    content, _ = load_and_validate(repo)
    return {repo.rel(path): data for path, data in generate_all(content, repo).items()}


def modifier(**fields: object) -> CharacterModifierDef:
    return CharacterModifierDef.model_validate(
        {"attribute": "realmforge:strength", "operation": "add_value", **fields}
    )


# The repository's content


def test_repository_races_and_classes_are_valid() -> None:
    content, universe = load_and_validate(RepoPaths())
    assert [e.model.id for e in content.races] == ["human"]
    assert [e.model.id for e in content.classes] == ["mage", "warrior"]
    assert universe.contains("realmforge:race", "realmforge:human")
    assert universe.contains("realmforge:class", "realmforge:warrior")
    (human,) = (e.model for e in content.races)
    assert human.classes == ["realmforge:warrior", "realmforge:mage"]


# Modifiers


def test_amount_grows_per_level() -> None:
    growing = modifier(amount=5, per_level=1)
    assert [growing.amount_at(level) for level in (1, 2, 10)] == [5, 6, 14]
    assert modifier(amount=3).amount_at(20) == 3


def test_percent_amounts_are_fractions() -> None:
    share = modifier(amount_percent=5, per_level_percent=0.5)
    assert share.amount_at(1) == pytest.approx(0.05)
    assert share.amount_at(3) == pytest.approx(0.06)


@pytest.mark.parametrize(
    ("fields", "message"),
    [
        ({}, "exactly one of amount and amount_percent"),
        ({"amount": 1, "amount_percent": 1}, "exactly one of amount and amount_percent"),
        ({"amount": 1, "per_level": 1, "per_level_percent": 1}, "at most one of per_level"),
        ({"amount": 1, "per_level_percent": 1}, "the growth is per_level"),
        ({"amount_percent": 1, "per_level": 1}, "the growth is per_level_percent"),
        ({"amount": 0}, "gives nothing"),
        ({"amount": 0, "per_level": 0}, "gives nothing"),
    ],
)
def test_modifier_shapes_are_refused(fields: dict[str, object], message: str) -> None:
    with pytest.raises(ValidationError, match=message):
        modifier(**fields)


def test_multiplied_operations_take_percent() -> None:
    with pytest.raises(ValidationError, match="write the amount as amount_percent"):
        modifier(operation="add_multiplied_base", amount=1)
    assert modifier(operation="add_multiplied_total", amount_percent=10).game_amount == pytest.approx(0.1)


def test_growth_alone_starts_at_zero_and_is_skipped_there() -> None:
    growing = [modifier(amount=0, per_level=1)]
    assert bonuses_at("class", "rogue", growing, 1) == []
    assert bonuses_at("class", "rogue", growing, 3) == [
        ("realmforge:class/realmforge/0/rogue", "realmforge:strength", "add_value", 2.0)
    ]


def test_modifier_ids_name_the_registry_entry_and_position() -> None:
    assert modifier_id("race", "human", 2) == "realmforge:race/realmforge/2/human"
    assert modifier_id("class", "warrior", 0) == "realmforge:class/realmforge/0/warrior"


# Validation


def test_race_names_classes_that_exist(repo: RepoPaths) -> None:
    write(repo.content / "races" / "elf.yaml", RACE.replace("realmforge:warrior", "realmforge:paladin"))
    assert_problem(repo, "classes.0")


def test_race_lists_a_class_once(repo: RepoPaths) -> None:
    write(
        repo.content / "races" / "elf.yaml",
        RACE.replace("[realmforge:warrior]", "[realmforge:warrior, realmforge:warrior]"),
    )
    assert_problem(repo, "duplicate entries: realmforge:warrior")


def test_race_takes_at_least_one_class(repo: RepoPaths) -> None:
    write(repo.content / "races" / "elf.yaml", RACE.replace("[realmforge:warrior]", "[]"))
    assert_problem(repo, "classes")


def test_class_no_race_lists_is_refused(repo: RepoPaths) -> None:
    write(repo.content / "classes" / "rogue.yaml", CLASS)
    assert_problem(repo, "no race lists realmforge:rogue in its classes")


def test_class_resource_is_a_known_type(repo: RepoPaths) -> None:
    write(repo.content / "classes" / "rogue.yaml", CLASS.replace("realmforge:rage", "realmforge:energy"))
    assert_problem(repo, "resource")


def test_kit_items_exist(repo: RepoPaths) -> None:
    write(repo.content / "races" / "elf.yaml", RACE.replace("[realmforge:warrior]", "[realmforge:rogue]"))
    write(
        repo.content / "classes" / "rogue.yaml",
        CLASS + "starting_kit:\n  - {item: minecraft:dagger}\n",
    )
    assert_problem(repo, "starting_kit.0.item")


def test_every_kit_holds_the_starting_bread(repo: RepoPaths) -> None:
    write(repo.content / "races" / "elf.yaml", RACE.replace("[realmforge:warrior]", "[realmforge:rogue]"))
    write(
        repo.content / "classes" / "rogue.yaml",
        CLASS + "starting_kit:\n  - {item: minecraft:bread, count: 8}\n",
    )
    assert_problem(repo, "every class starts with minecraft:bread x32")


def test_starting_bread_may_come_in_several_entries(repo: RepoPaths) -> None:
    write(repo.content / "races" / "elf.yaml", RACE.replace("[realmforge:warrior]", "[realmforge:rogue]"))
    write(repo.content / "classes" / "rogue.yaml", CLASS + "starting_kit:\n" + BREAD.replace("32", "16") * 2)
    load_and_validate(repo)


def test_kit_counts_fit_a_stack(repo: RepoPaths) -> None:
    write(repo.content / "races" / "elf.yaml", RACE.replace("[realmforge:warrior]", "[realmforge:rogue]"))
    write(
        repo.content / "classes" / "rogue.yaml",
        CLASS + "starting_kit:\n  - {item: minecraft:bread, count: 65}\n",
    )
    assert_problem(repo, "starting_kit.0.count")


def test_modifier_attribute_exists_and_takes_its_unit(repo: RepoPaths) -> None:
    write(
        repo.content / "races" / "elf.yaml",
        RACE
        + "modifiers:\n"
        + "  - {attribute: realmforge:luckiness, operation: add_value, amount: 1}\n"
        + "  - {attribute: realmforge:crit_chance, operation: add_value, amount: 1}\n"
        + "  - {attribute: realmforge:strength, operation: add_value, amount_percent: 1}\n"
        + "  - {attribute: minecraft:max_health, operation: add_value, amount: 2}\n",
    )
    problems = problems_for(repo)
    assert any("modifiers.0.attribute" in p for p in problems), problems
    assert any("modifiers.1.amount" in p and "is a share" in p for p in problems), problems
    assert any("modifiers.2.amount_percent" in p and "counts points" in p for p in problems), problems
    assert not any("modifiers.3" in p for p in problems), problems


# Generation


def test_generated_data_and_lang(repo: RepoPaths) -> None:
    write(repo.content / "races" / "elf.yaml", RACE.replace("[realmforge:warrior]", "[realmforge:rogue]"))
    write(
        repo.content / "classes" / "rogue.yaml",
        CLASS
        + "modifiers:\n"
        + "  - {attribute: realmforge:crit_chance, operation: add_value, amount_percent: 2, per_level_percent: 0.5}\n"
        + "starting_kit:\n  - {item: minecraft:bread, count: 32}\n  - {item: minecraft:stick}\n",
    )
    files = generate(repo)
    rogue = json.loads(files[f"{DATA}/class/rogue.json"])
    assert rogue == {
        "name": "class.realmforge.rogue.name",
        "description": "class.realmforge.rogue.description",
        "resource": "realmforge:rage",
        "modifiers": [
            {
                "attribute": "realmforge:crit_chance",
                "operation": "add_value",
                "amount": 0.02,
                "per_level": 0.005,
            }
        ],
        "starting_kit": [{"item": "minecraft:bread", "count": 32}, {"item": "minecraft:stick", "count": 1}],
    }
    elf = json.loads(files[f"{DATA}/race/elf.json"])
    assert elf["classes"] == ["realmforge:rogue"]
    assert "modifiers" in elf and elf["modifiers"] == []
    lang = json.loads(files[LANG])
    assert lang["race.realmforge.elf.name"] == "Elf"
    assert lang["class.realmforge.rogue.description"] == "Quick and quiet."


def test_expectations_list_the_bonuses_at_sample_levels() -> None:
    files = generate(RepoPaths())
    classes = files[f"{QA}/GeneratedClassExpectations.java"].decode()
    assert '"realmforge:warrior"' in classes and '"realmforge:rage"' in classes
    assert 'new KitItem("minecraft:bread", 32)' in classes
    # Warrior strength: 5 at level 1, +1 per level.
    assert (
        'new Bonus("realmforge:class/realmforge/0/warrior", "realmforge:strength", "add_value", 14.0)'
        in classes
    )
    races = files[f"{QA}/GeneratedRaceExpectations.java"].decode()
    assert 'List.of("realmforge:warrior", "realmforge:mage")' in races
