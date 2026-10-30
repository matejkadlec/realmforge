"""Status effects (content/effects/): validation and generation (architecture 11.4; C3c, RLM-26)."""

from __future__ import annotations

import json
import struct

import pytest

from realmforge_tools.generate import generate_all
from realmforge_tools.problems import ValidationFailed
from realmforge_tools.project import RepoPaths
from realmforge_tools.schema.effect import EffectDef, modifier_id
from realmforge_tools.validate import load_and_validate
from tests.conftest import drop_class_kits, write

LANG = "mod/src/generated/resources/assets/realmforge/lang/en_us.json"
ICONS = "mod/src/generated/resources/assets/realmforge/textures/mob_effect"
REGISTRATIONS = "mod/src/generated/java/io/github/matejkadlec/realmforge/generated/GeneratedEffects.java"
EXPECTATIONS = (
    "qa-mod/src/generated/java/io/github/matejkadlec/realmforge/qa/generated/GeneratedEffectExpectations.java"
)


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


def effect(repo: RepoPaths, name: str, body: str) -> None:
    write(repo.content / "effects" / f"{name}.yaml", f"id: {name}\nname: {{en_us: Frostbite}}\n{body}")


def effect_model(**fields: object) -> EffectDef:
    data = {
        "id": "frostbite",
        "name": {"en_us": "Frostbite"},
        "category": "harmful",
        "color": "#7FBFFF",
        "periodic": {"type": "damage", "school": "realmforge:frost", "amount": 1, "interval_seconds": 1},
        **fields,
    }
    return EffectDef.model_validate(data)


def test_repository_effects_are_the_engine_tests() -> None:
    content, _ = load_and_validate(RepoPaths())
    assert {entry.model.id for entry in content.effects} == {
        "battle_shout",
        "burning",
        "cracked_stone",
        "ice_barrier",
        "stunned",
        "test_burning",
        "test_regrowth",
        "test_sapped",
    }


@pytest.mark.parametrize(
    ("fields", "expected"),
    [
        ({"periodic": None}, "an effect gives modifiers, periodic damage or healing, or both"),
        ({"category": "cursed"}, "Input should be 'beneficial', 'harmful' or 'neutral'"),
        ({"color": "blue"}, "String should match pattern"),
        (
            {
                "periodic": {
                    "type": "damage",
                    "school": "realmforge:frost",
                    "amount": 0,
                    "interval_seconds": 1,
                }
            },
            "Input should be greater than 0",
        ),
        (
            {
                "periodic": {
                    "type": "damage",
                    "school": "realmforge:frost",
                    "amount": 1,
                    "interval_seconds": 0.1,
                }
            },
            "interval_seconds is from 0.25 to 10 seconds",
        ),
        (
            {"periodic": {"type": "drain", "school": "realmforge:frost", "amount": 1, "interval_seconds": 1}},
            "Input should be 'damage' or 'heal'",
        ),
        (
            {
                "modifiers": [
                    {"attribute": "minecraft:movement_speed", "operation": "add_value", "amount": -0.01},
                    {
                        "attribute": "minecraft:movement_speed",
                        "operation": "add_multiplied_total",
                        "amount_percent": -10,
                    },
                ]
            },
            "one modifier per attribute: minecraft:movement_speed",
        ),
        (
            {
                "modifiers": [
                    {
                        "attribute": "minecraft:movement_speed",
                        "operation": "add_multiplied_total",
                        "amount": 1,
                    }
                ]
            },
            "add_multiplied_total multiplies: write the amount as amount_percent",
        ),
    ],
)
def test_invalid_effects_are_rejected(fields: dict, expected: str) -> None:
    with pytest.raises(ValueError, match=expected):
        effect_model(**fields)


def test_the_school_must_exist(repo: RepoPaths) -> None:
    effect(
        repo,
        "frostbite",
        "category: harmful\ncolor: '#7FBFFF'\n"
        "periodic: {type: damage, school: realmforge:ice, amount: 1, interval_seconds: 1}\n",
    )
    assert_problem(repo, "periodic.school: unknown id 'realmforge:ice'")


def test_modifiers_name_attributes_in_their_unit(repo: RepoPaths) -> None:
    effect(
        repo,
        "frostbite",
        "category: harmful\ncolor: '#7FBFFF'\nmodifiers:\n"
        "  - {attribute: minecraft:speediness, operation: add_value, amount: 1}\n"
        "  - {attribute: realmforge:spell_power, operation: add_value, amount: 0.1}\n"
        "  - {attribute: realmforge:strength, operation: add_value, amount_percent: 10}\n",
    )
    problems = problems_for(repo)
    assert any("modifiers.0.attribute: unknown id 'minecraft:speediness'" in p for p in problems), problems
    assert any("realmforge:spell_power is a share: write the amount as amount_percent" in p for p in problems)
    assert any("realmforge:strength counts points: add_value takes amount" in p for p in problems)


def test_effects_join_the_mob_effect_registry(repo: RepoPaths) -> None:
    _, universe = load_and_validate(repo)
    assert universe.contains("minecraft:mob_effect", "realmforge:test_burning")


def test_a_text_group_may_not_be_named_effect(repo: RepoPaths) -> None:
    write(repo.content / "texts" / "effect.yaml", "id: effect\ntexts:\n  test_burning: {en_us: Clash}\n")
    assert_problem(repo, "'effect' is the translation key prefix of content/effects/")


def test_registrations(repo: RepoPaths) -> None:
    java = generate(repo)[REGISTRATIONS].decode()
    assert (
        "    public static final DeferredHolder<MobEffect, MobEffect> TEST_BURNING =\n"
        '            EFFECTS.register("test_burning", () -> new StatusEffect(MobEffectCategory.HARMFUL, 0xFF7F1F,\n'
        '                    Optional.of(Periodic.damage("realmforge:fire", 2.0, 20))));\n'
    ) in java
    assert (
        '                    .modifier("minecraft:movement_speed", '
        '"realmforge:effect.test_sapped.minecraft.movement_speed", -0.2, '
        "AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)\n"
    ) in java
    assert (
        "ALL = List.of(BATTLE_SHOUT, BURNING, CRACKED_STONE, ICE_BARRIER, STUNNED, TEST_BURNING, TEST_REGROWTH, "
        "TEST_SAPPED);"
    ) in java


def test_modifier_ids_name_the_effect_and_the_attribute() -> None:
    assert (
        modifier_id("test_sapped", "realmforge:spell_power")
        == "realmforge:effect.test_sapped.realmforge.spell_power"
    )


def test_names_and_icons(repo: RepoPaths) -> None:
    files = generate(repo)
    assert json.loads(files[LANG])["effect.realmforge.test_regrowth"] == "Test Regrowth"
    icon = files[f"{ICONS}/test_regrowth.png"]
    # Vanilla's effect icons are 18 by 18 (assets/minecraft/textures/mob_effect/).
    assert icon.startswith(b"\x89PNG") and struct.unpack(">II", icon[16:24]) == (18, 18)
    assert files[f"{ICONS}/test_burning.png"] != files[f"{ICONS}/test_regrowth.png"]


def test_expectations(repo: RepoPaths) -> None:
    expectations = generate(repo)[EXPECTATIONS].decode()
    assert (
        "            new EffectExpectation(\n"
        '                    "realmforge:test_regrowth",\n'
        '                    "effect.realmforge.test_regrowth",\n'
        '                    "beneficial",\n'
        "                    0x4FCF6F,\n"
        "                    List.of(),\n"
        '                    Optional.of(new PeriodicExpectation("heal", "realmforge:arcane", 2.0, 20)),\n'
        "                    false)"
    ) in expectations
    assert (
        'new ModifierExpectation("realmforge:spell_power", "realmforge:effect.test_sapped.realmforge.spell_power", '
        '-0.1, "add_value", -0.2)'
    ) in expectations


def test_no_effects_still_compile(repo: RepoPaths) -> None:
    # The spells that apply them go too, with the class kits that teach some of them, and Astone, whose last phase
    # casts one of them.
    for file in (repo.content / "effects").glob("*.yaml"):
        file.unlink()
    for name in (
        "test_ignite",
        "test_regrow",
        "test_sap",
        "astone_cracked_stone",
        "battle_shout",
        "fireball",
        "ice_barrier",
        "shield_bash",
    ):
        (repo.content / "spells" / f"{name}.yaml").unlink()
    drop_class_kits(repo.content)
    for folder in ("bosses", "mobs", "kill_xp_multipliers"):
        (repo.content / folder / "astone.yaml").unlink()
    files = generate(repo)
    assert "ALL = List.of();" in files[REGISTRATIONS].decode()
    assert "EFFECTS = List.of();" in files[EXPECTATIONS].decode()
    assert not any(path.startswith(ICONS) for path in files)
