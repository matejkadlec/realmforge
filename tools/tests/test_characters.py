"""Races (content/races/) and classes (content/classes/): validation, the reference bonuses and generation."""

from __future__ import annotations

import json

import pytest
from pydantic import ValidationError

from realmforge_tools.generate import generate_all
from realmforge_tools.problems import ValidationFailed
from realmforge_tools.project import RepoPaths
from realmforge_tools.schema.character import CharacterModifierDef, bonuses, modifier_id
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
RAGE = """rage:
  per_hit_dealt: 10
  per_max_health_taken: 50
  decay_per_second: 2
  decay_after_seconds: 5
"""
CLASS = (
    """id: rogue
name: {en_us: Rogue}
description: {en_us: Quick and quiet.}
role: ranged
resource: realmforge:rage
"""
    + RAGE
)
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


def test_percent_amounts_are_fractions() -> None:
    assert modifier(amount_percent=5).game_amount == pytest.approx(0.05)
    assert modifier(amount=3).game_amount == 3


@pytest.mark.parametrize(
    ("fields", "message"),
    [
        ({}, "exactly one of amount and amount_percent"),
        ({"amount": 1, "amount_percent": 1}, "exactly one of amount and amount_percent"),
        ({"amount": 0}, "gives nothing"),
        # Levels give stat points to spend instead of growing race and class stats (RLM-196).
        ({"amount": 1, "per_level": 1}, "Extra inputs are not permitted"),
        ({"amount_percent": 1, "per_level_percent": 1}, "Extra inputs are not permitted"),
    ],
)
def test_modifier_shapes_are_refused(fields: dict[str, object], message: str) -> None:
    with pytest.raises(ValidationError, match=message):
        modifier(**fields)


def test_multiplied_operations_take_percent() -> None:
    with pytest.raises(ValidationError, match="write the amount as amount_percent"):
        modifier(operation="add_multiplied_base", amount=1)
    assert modifier(operation="add_multiplied_total", amount_percent=10).game_amount == pytest.approx(0.1)


def test_bonuses_are_every_modifier_in_order_in_game_units() -> None:
    modifiers = [modifier(amount=5), modifier(attribute="realmforge:crit_chance", amount_percent=2)]
    assert bonuses("class", "rogue", modifiers) == [
        ("realmforge:class/realmforge/0/rogue", "realmforge:strength", "add_value", 5.0),
        ("realmforge:class/realmforge/1/rogue", "realmforge:crit_chance", "add_value", 0.02),
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


def test_a_class_names_its_role(repo: RepoPaths) -> None:
    write(repo.content / "classes" / "rogue.yaml", CLASS.replace("role: ranged\n", ""))
    assert_problem(repo, "role: Field required")


def test_a_class_role_is_a_known_one(repo: RepoPaths) -> None:
    write(repo.content / "classes" / "rogue.yaml", CLASS.replace("role: ranged", "role: healer"))
    assert_problem(repo, "role")


def test_race_and_class_bonuses_add_their_percents(repo: RepoPaths) -> None:
    write(repo.content / "races" / "elf.yaml", RACE.replace("[realmforge:warrior]", "[realmforge:rogue]"))
    write(
        repo.content / "classes" / "rogue.yaml",
        CLASS
        + "modifiers:\n"
        + "  - {attribute: minecraft:attack_damage, operation: add_multiplied_total, amount_percent: 5}\n"
        + "starting_kit:\n  - {item: minecraft:bread, count: 32}\n",
    )
    assert_problem(repo, "modifiers.0.operation: stat bonuses add their percents")


def test_class_resource_is_a_known_type(repo: RepoPaths) -> None:
    write(repo.content / "classes" / "rogue.yaml", CLASS.replace("realmforge:rage", "realmforge:energy"))
    assert_problem(repo, "resource")


def test_a_rage_class_sets_its_rage_numbers(repo: RepoPaths) -> None:
    write(repo.content / "races" / "elf.yaml", RACE.replace("[realmforge:warrior]", "[realmforge:rogue]"))
    write(repo.content / "classes" / "rogue.yaml", CLASS.replace(RAGE, ""))
    assert_problem(repo, "a rage class sets its rage numbers")


def test_only_a_rage_class_sets_rage_numbers(repo: RepoPaths) -> None:
    write(repo.content / "races" / "elf.yaml", RACE.replace("[realmforge:warrior]", "[realmforge:rogue]"))
    write(repo.content / "classes" / "rogue.yaml", CLASS.replace("realmforge:rage", "realmforge:mana"))
    assert_problem(repo, "rage numbers are for a class whose resource is realmforge:rage")


@pytest.mark.parametrize(
    ("field", "value"),
    [
        ("per_hit_dealt", "-1"),
        ("per_hit_dealt", "101"),
        ("per_max_health_taken", "-1"),
        ("decay_per_second", "-1"),
        ("decay_after_seconds", "0"),
        ("decay_after_seconds", "61"),
        ("decay_after_seconds", "0.01"),
    ],
)
def test_rage_numbers_are_bounded(repo: RepoPaths, field: str, value: str) -> None:
    write(repo.content / "races" / "elf.yaml", RACE.replace("[realmforge:warrior]", "[realmforge:rogue]"))
    numbers = {line.split(":")[0].strip(): line for line in RAGE.splitlines()[1:]}
    write(
        repo.content / "classes" / "rogue.yaml",
        CLASS.replace(numbers[field], f"  {field}: {value}") + "starting_kit:\n" + BREAD,
    )
    assert_problem(repo, f"rage.{field}")


def test_rage_numbers_are_in_game_units(repo: RepoPaths) -> None:
    out = generate(repo)
    data = json.loads(out[f"{DATA}/class/warrior.json"])
    assert data["rage"] == {
        "per_hit_dealt": 10.0,
        "per_max_health_taken": 50.0,
        "decay_per_second": 2.0,
        "decay_after_ticks": 100,
    }
    assert "rage" not in json.loads(out[f"{DATA}/class/mage.json"])
    expectations = out[f"{QA}/GeneratedClassExpectations.java"].decode()
    assert "Optional.of(new Rage(10.0, 50.0, 2.0, 100))" in expectations


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
        + "  - {attribute: realmforge:crit_chance, operation: add_value, amount_percent: 2}\n"
        + "starting_kit:\n  - {item: minecraft:bread, count: 32}\n  - {item: minecraft:stick}\n",
    )
    files = generate(repo)
    rogue = json.loads(files[f"{DATA}/class/rogue.json"])
    assert rogue == {
        "name": "class.realmforge.rogue.name",
        "description": "class.realmforge.rogue.description",
        "role": "ranged",
        "resource": "realmforge:rage",
        "rage": {
            "per_hit_dealt": 10.0,
            "per_max_health_taken": 50.0,
            "decay_per_second": 2.0,
            "decay_after_ticks": 100,
        },
        "modifiers": [
            {
                "attribute": "realmforge:crit_chance",
                "operation": "add_value",
                "amount": 0.02,
            }
        ],
        "starting_kit": [{"item": "minecraft:bread", "count": 32}, {"item": "minecraft:stick", "count": 1}],
        "spells": [],
    }
    elf = json.loads(files[f"{DATA}/race/elf.json"])
    assert elf["classes"] == ["realmforge:rogue"]
    assert "modifiers" in elf and elf["modifiers"] == []
    lang = json.loads(files[LANG])
    assert lang["race.realmforge.elf.name"] == "Elf"
    assert lang["class.realmforge.rogue.description"] == "Quick and quiet."


def test_expectations_list_the_bonuses() -> None:
    files = generate(RepoPaths())
    classes = files[f"{QA}/GeneratedClassExpectations.java"].decode()
    assert '"realmforge:warrior"' in classes and '"realmforge:rage"' in classes
    assert 'new KitItem("minecraft:bread", 32)' in classes
    # Warrior strength: 5 at every level (RLM-196: no growth per level).
    assert (
        'new Bonus("realmforge:class/realmforge/0/warrior", "realmforge:strength", "add_value", 5.0)'
        in classes
    )
    assert "Sample" not in classes
    races = files[f"{QA}/GeneratedRaceExpectations.java"].decode()
    assert 'List.of("realmforge:warrior", "realmforge:mage")' in races
