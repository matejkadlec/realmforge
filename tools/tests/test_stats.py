"""Stats (content/stats/) and stat rules (content/stat_rules/): validation, the reference bonuses and generation."""

from __future__ import annotations

import json

import pytest

from realmforge_tools.generate import generate_all
from realmforge_tools.generate.stats import sample_values
from realmforge_tools.problems import ValidationFailed
from realmforge_tools.project import RepoPaths
from realmforge_tools.schema.stat_rule import StatRuleDef
from realmforge_tools.validate import load_and_validate
from tests.conftest import write

GENERATED = "mod/src/generated/java/io/github/matejkadlec/realmforge/generated"
STATS_JAVA = f"{GENERATED}/GeneratedStats.java"
EXPECTATIONS = (
    "qa-mod/src/generated/java/io/github/matejkadlec/realmforge/qa/generated/GeneratedStatExpectations.java"
)
RULE_DATA = "mod/src/generated/resources/data/realmforge/realmforge/stat_rule"
LANG = "mod/src/generated/resources/assets/realmforge/lang"

PRIMARY = ["dexterity", "intellect", "strength", "vitality"]


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


def stat(repo: RepoPaths, name: str, body: str) -> None:
    """Writes content/stats/<name>.yaml, named after its id unless ``body`` names it."""
    named = body if body.startswith("name:") or "\nname:" in body else f"name: {{en_us: {name}}}\n{body}"
    write(repo.content / "stats" / f"{name}.yaml", f"id: {name}\n{named}")


def rule(repo: RepoPaths, name: str, body: str) -> None:
    write(repo.content / "stat_rules" / f"{name}.yaml", f"id: {name}\n{body}")


def per_point(
    attribute: str, amount: str, operation: str = "add_value", stat_id: str = "realmforge:strength"
) -> str:
    return f"stat: {stat_id}\nper_point:\n  - {{attribute: {attribute}, operation: {operation}, {amount}}}\n"


def rule_model(data: dict) -> StatRuleDef:
    return StatRuleDef.model_validate({"id": "r", "stat": "realmforge:strength", **data})


# The repository's content


def test_repository_stats_and_rules_are_valid() -> None:
    content, universe = load_and_validate(RepoPaths())
    assert sorted(e.model.id for e in content.stats if e.model.kind == "primary") == PRIMARY
    assert sorted(e.model.stat for e in content.stat_rules) == [f"realmforge:{name}" for name in PRIMARY]
    assert universe.contains("minecraft:attribute", "realmforge:strength")
    assert universe.contains("realmforge:stat_rule", "realmforge:strength")


def test_strength_gives_the_visions_ten_percent_for_ten_points() -> None:
    content, _ = load_and_validate(RepoPaths())
    (strength,) = (e.model for e in content.stat_rules if e.model.id == "strength")
    assert (
        "realmforge:stat/realmforge/0/strength",
        "minecraft:attack_damage",
        "add_multiplied_base",
        0.1,
    ) in (strength.bonuses_at(10))


# Stats: schema


@pytest.mark.parametrize(
    ("body", "expected"),
    [
        ("kind: primary\n", "set exactly one of max (points) and max_percent (a share)"),
        ("kind: secondary\nmax: 5\nmax_percent: 5\n", "set exactly one of max (points) and max_percent"),
        ("kind: primary\nmax_percent: 100\n", "a primary stat counts points: use max, not max_percent"),
        ("kind: secondary\ndefault: 1\nmax_percent: 100\n", "writes its default as default_percent"),
        (
            "kind: secondary\ndefault_percent: 1\nmax: 10\n",
            "writes its default as default, not default_percent",
        ),
        (
            "kind: secondary\ndefault_percent: 150\nmax_percent: 100\n",
            "the default must not be above the maximum",
        ),
        ("kind: primary\ndefault: -1\nmax: 10\n", "default: Input should be greater than or equal to 0"),
        ("kind: primary\nmax: 0\n", "max: Input should be greater than 0"),
        ("kind: secondary\nmax_percent: 1.0e-323\n", "the maximum is too small: it is 0 in game units"),
        ("kind: tertiary\nmax: 10\n", "kind: Input should be 'primary' or 'secondary'"),
    ],
)
def test_invalid_stats_are_rejected(repo: RepoPaths, body: str, expected: str) -> None:
    stat(repo, "luck_stat", body)
    assert_problem(repo, expected)


# Stat rules: schema


@pytest.mark.parametrize(
    ("data", "expected"),
    [
        ({}, "a stat rule gives something: add per_point or milestones"),
        (
            {"per_point": [{"attribute": "minecraft:luck", "operation": "add_value"}]},
            "set exactly one of amount and amount_percent",
        ),
        (
            {
                "per_point": [
                    {
                        "attribute": "minecraft:luck",
                        "operation": "add_value",
                        "amount": 1,
                        "amount_percent": 1,
                    }
                ]
            },
            "set exactly one of amount and amount_percent",
        ),
        (
            {"per_point": [{"attribute": "minecraft:luck", "operation": "add_value", "amount": 0}]},
            "the amount must not be 0",
        ),
        (
            {
                "per_point": [
                    {"attribute": "minecraft:luck", "operation": "add_multiplied_base", "amount": 0.01}
                ]
            },
            "add_multiplied_base multiplies: write the amount as amount_percent",
        ),
        (
            {
                "per_point": [
                    {"attribute": "minecraft:luck", "operation": "add_value", "amount": 1, "max_percent": 5}
                ]
            },
            "the amount is in points, so the cap is max",
        ),
        (
            {
                "per_point": [
                    {
                        "attribute": "minecraft:luck",
                        "operation": "add_multiplied_base",
                        "amount_percent": 1,
                        "max": 5,
                    }
                ]
            },
            "the amount is in percent, so the cap is max_percent",
        ),
        (
            {
                "per_point": [
                    {"attribute": "minecraft:luck", "operation": "add_value", "amount": -1, "max": 5}
                ]
            },
            "a cap needs a positive amount",
        ),
        (
            {
                "per_point": [
                    {
                        "attribute": "minecraft:luck",
                        "operation": "add_value",
                        "amount_percent": 1,
                        "max_percent": 1e-323,
                    }
                ]
            },
            "the cap is too small: it is 0 in game units",
        ),
        (
            {"milestones": [{"points": 10, "modifiers": []}]},
            "List should have at least 1 item",
        ),
        (
            {
                "milestones": [
                    {
                        "points": 10,
                        "modifiers": [{"attribute": "minecraft:luck", "operation": "add_value", "amount": 1}],
                    },
                    {
                        "points": 10,
                        "modifiers": [{"attribute": "minecraft:luck", "operation": "add_value", "amount": 1}],
                    },
                ]
            },
            "milestone points must strictly increase, but 10 follows 10",
        ),
        (
            {
                "milestones": [
                    {
                        "points": 0,
                        "modifiers": [{"attribute": "minecraft:luck", "operation": "add_value", "amount": 1}],
                    }
                ]
            },
            "greater than or equal to 1",
        ),
        (
            {"per_point": [{"attribute": "minecraft:luck", "operation": "multiply", "amount": 1}]},
            "operation",
        ),
    ],
)
def test_invalid_rule_shapes_are_rejected(data: dict, expected: str) -> None:
    with pytest.raises(ValueError, match=expected):
        rule_model(data)


# Stat rules: references


def test_rule_must_read_a_known_stat(repo: RepoPaths) -> None:
    rule(repo, "strength", per_point("minecraft:luck", "amount: 1", stat_id="realmforge:strenght"))
    assert_problem(
        repo,
        "stat_rules/strength.yaml: stat: unknown id 'realmforge:strenght' in registry minecraft:attribute",
    )


@pytest.mark.parametrize("read", ["realmforge:crit_chance", "minecraft:luck"])
def test_rule_must_read_a_primary_stat(repo: RepoPaths, read: str) -> None:
    rule(repo, "strength", per_point("minecraft:armor", "amount: 1", stat_id=read))
    assert_problem(repo, f"stat: {read} is not a primary stat (content/stats/, kind: primary)")


def test_one_rule_per_stat(repo: RepoPaths) -> None:
    rule(repo, "strength_bonus", per_point("minecraft:armor", "amount: 1"))
    assert_problem(repo, "stat_rules/strength_bonus.yaml: stat: realmforge:strength already has a stat rule")


def test_rule_must_write_a_known_attribute(repo: RepoPaths) -> None:
    rule(repo, "strength", per_point("minecraft:attack_damag", "amount: 1"))
    assert_problem(repo, "per_point.0.attribute: unknown id 'minecraft:attack_damag'")


def test_rule_must_not_write_a_primary_stat(repo: RepoPaths) -> None:
    rule(repo, "strength", per_point("realmforge:vitality", "amount: 1"))
    assert_problem(
        repo,
        "per_point.0.attribute: stat rules write secondary stats and vanilla attributes, not the primary",
    )


def test_milestone_must_not_write_a_primary_stat(repo: RepoPaths) -> None:
    rule(
        repo,
        "strength",
        "stat: realmforge:strength\nmilestones:\n  - points: 5\n    modifiers:\n"
        "      - {attribute: realmforge:intellect, operation: add_value, amount: 1}\n",
    )
    assert_problem(repo, "milestones.0.modifiers.0.attribute: stat rules write secondary stats")


def test_add_value_on_a_share_is_in_percent(repo: RepoPaths) -> None:
    rule(repo, "strength", per_point("realmforge:crit_chance", "amount: 0.01"))
    assert_problem(
        repo, "per_point.0.amount: realmforge:crit_chance is a share: write the amount as amount_percent"
    )


def test_add_value_on_our_points_is_not_in_percent(repo: RepoPaths) -> None:
    stat(repo, "toughness", "kind: secondary\nmax: 100\n")
    rule(repo, "strength", per_point("realmforge:toughness", "amount_percent: 1"))
    assert_problem(
        repo, "per_point.0.amount_percent: realmforge:toughness counts points: add_value takes amount"
    )


@pytest.mark.parametrize("amount", ["amount_percent: 5", "amount: 0.05"])
def test_add_value_on_a_vanilla_attribute_takes_either_unit(repo: RepoPaths, amount: str) -> None:
    # The pipeline does not know vanilla units: knockback resistance is a fraction, max health counts points.
    rule(repo, "strength", per_point("minecraft:knockback_resistance", amount))
    files = generate(repo)
    assert json.loads(files[f"{RULE_DATA}/strength.json"])["per_point"][0]["amount"] == 0.05


# The reference bonuses (the GameTest compares the game's StatRules with these)


def test_bonuses_scale_per_point_and_respect_the_cap() -> None:
    model = rule_model(
        {
            "per_point": [
                {"attribute": "minecraft:max_health", "operation": "add_value", "amount": 2},
                {
                    "attribute": "minecraft:movement_speed",
                    "operation": "add_multiplied_base",
                    "amount_percent": 1,
                    "max_percent": 5,
                },
            ]
        }
    )
    assert model.bonuses_at(3) == [
        ("realmforge:stat/realmforge/0/r", "minecraft:max_health", "add_value", 6.0),
        ("realmforge:stat/realmforge/1/r", "minecraft:movement_speed", "add_multiplied_base", 0.03),
    ]
    assert model.bonuses_at(50)[1] == (
        "realmforge:stat/realmforge/1/r",
        "minecraft:movement_speed",
        "add_multiplied_base",
        0.05,
    )


def test_no_points_give_no_bonus() -> None:
    model = rule_model(
        {"per_point": [{"attribute": "minecraft:armor", "operation": "add_value", "amount": 1}]}
    )
    assert model.bonuses_at(0) == []


def test_milestones_apply_from_their_points_on() -> None:
    model = rule_model(
        {
            "milestones": [
                {
                    "points": 10,
                    "modifiers": [{"attribute": "minecraft:armor", "operation": "add_value", "amount": 2}],
                },
                {
                    "points": 20,
                    "modifiers": [
                        {"attribute": "minecraft:armor", "operation": "add_value", "amount": 3},
                        {
                            "attribute": "realmforge:crit_chance",
                            "operation": "add_value",
                            "amount_percent": 1,
                        },
                    ],
                },
            ]
        }
    )
    assert model.bonuses_at(9.99) == []
    assert model.bonuses_at(10) == [
        ("realmforge:stat/realmforge/at_10_0/r", "minecraft:armor", "add_value", 2.0)
    ]
    assert model.bonuses_at(25) == [
        ("realmforge:stat/realmforge/at_10_0/r", "minecraft:armor", "add_value", 2.0),
        ("realmforge:stat/realmforge/at_20_0/r", "minecraft:armor", "add_value", 3.0),
        ("realmforge:stat/realmforge/at_20_1/r", "realmforge:crit_chance", "add_value", 0.01),
    ]


def test_modifier_ids_end_with_the_rule_path() -> None:
    model = rule_model(
        {"per_point": [{"attribute": "minecraft:armor", "operation": "add_value", "amount": 1}]}
    )
    assert model.modifier_id("0") == "realmforge:stat/realmforge/0/r"
    assert model.modifier_id("at_25_1") == "realmforge:stat/realmforge/at_25_1/r"


def test_a_cap_too_far_to_reach_adds_no_sample() -> None:
    model = rule_model(
        {
            "per_point": [
                {"attribute": "minecraft:armor", "operation": "add_value", "amount": 1e-200, "max": 1e200},
                {"attribute": "minecraft:luck", "operation": "add_value", "amount": 1e-300, "max": 1e300},
            ]
        }
    )
    # Both thresholds (1e400, 1e600) overflow a double, which used to crash rf gen in math.ceil.
    assert sample_values(model) == [0.0, 1.0, 12.5]


def test_samples_stay_within_the_stats_maximum() -> None:
    model = rule_model(
        {
            "per_point": [{"attribute": "minecraft:armor", "operation": "add_value", "amount": 1}],
            "milestones": [
                {
                    "points": 50,
                    "modifiers": [{"attribute": "minecraft:armor", "operation": "add_value", "amount": 1}],
                }
            ],
        }
    )
    assert sample_values(model) == [0.0, 1.0, 12.5, 49.0, 50.0]
    assert sample_values(model, 20.0) == [0.0, 1.0, 12.5]


def test_an_uncapped_amount_must_stay_finite_at_the_stats_maximum(repo: RepoPaths) -> None:
    rule(repo, "strength", per_point("minecraft:armor", "amount: 1.0e+308"))
    assert_problem(repo, "per_point.0: the amount overflows a double at realmforge:strength's maximum")


def test_a_capped_huge_amount_is_fine(repo: RepoPaths) -> None:
    # The cap bounds the total, in the game (Math.min) and in the pipeline.
    rule(repo, "strength", per_point("minecraft:armor", "amount: 1.0e+308, max: 5"))
    assert "new StatRuleExpectation(" in generate(repo)[EXPECTATIONS].decode()


def test_far_caps_still_generate(repo: RepoPaths) -> None:
    rule(
        repo,
        "strength",
        "stat: realmforge:strength\nper_point:\n"
        "  - {attribute: minecraft:armor, operation: add_value, amount: 1.0e-200, max: 1.0e+200}\n",
    )
    assert "new StatRuleExpectation(" in generate(repo)[EXPECTATIONS].decode()


def test_samples_cover_zero_both_sides_of_milestones_and_past_caps() -> None:
    model = rule_model(
        {
            "per_point": [
                {
                    "attribute": "minecraft:movement_speed",
                    "operation": "add_multiplied_base",
                    "amount_percent": 0.2,
                    "max_percent": 20,
                }
            ],
            "milestones": [
                {
                    "points": 25,
                    "modifiers": [{"attribute": "minecraft:armor", "operation": "add_value", "amount": 1}],
                }
            ],
        }
    )
    assert sample_values(model) == [0.0, 1.0, 12.5, 24.0, 25.0, 110.0]


# Generation


def test_rule_data_is_in_game_units(repo: RepoPaths) -> None:
    files = generate(repo)
    assert json.loads(files[f"{RULE_DATA}/strength.json"]) == {
        "stat": "realmforge:strength",
        "per_point": [
            {"attribute": "minecraft:attack_damage", "operation": "add_multiplied_base", "amount": 0.01}
        ],
        "milestones": [
            {
                "points": 25,
                "modifiers": [
                    {"attribute": "realmforge:crit_damage", "operation": "add_value", "amount": 0.1}
                ],
            }
        ],
    }
    dexterity = json.loads(files[f"{RULE_DATA}/dexterity.json"])
    assert dexterity["per_point"][2] == {
        "attribute": "minecraft:movement_speed",
        "operation": "add_multiplied_base",
        "amount": 0.002,
        "max": 0.2,
    }


def test_stats_register_points_and_shares(repo: RepoPaths) -> None:
    java = generate(repo)[STATS_JAVA].decode()
    assert (
        'ATTRIBUTES.register("strength",\n'
        '                    () -> new RangedAttribute("attribute.realmforge.strength", 0.0, 0.0, 100000.0)'
        ".setSyncable(true));" in java
    )
    assert (
        '() -> new PercentageAttribute("attribute.realmforge.crit_chance", 0.05, 0.0, 1.0).setSyncable(true));'
        in java
    )
    assert "PRIMARY = List.of(DEXTERITY, INTELLECT, STRENGTH, VITALITY);" in java
    assert (
        "ALL = List.of(ARCANE_POWER, ARCANE_RESISTANCE, ARMOR_PIERCE, ARMOR_SHRED, CAST_TIME_REDUCTION,"
        in java
    )


def test_stat_names_go_into_the_lang_file(repo: RepoPaths) -> None:
    files = generate(repo)
    english = json.loads(files[f"{LANG}/en_us.json"])
    assert english["attribute.realmforge.strength"] == "Strength"
    assert english["attribute.realmforge.crit_chance"] == "Crit Chance"


def test_a_stat_without_a_name_is_rejected(repo: RepoPaths) -> None:
    write(repo.content / "stats" / "luck_stat.yaml", "id: luck_stat\nkind: secondary\nmax: 10\n")
    assert_problem(repo, "content/stats/luck_stat.yaml: name: Field required")


def test_the_stat_name_is_in_its_registration_comment(repo: RepoPaths) -> None:
    stat(repo, "luck_stat", 'name: {en_us: "Luck */ @Odd"}\nkind: secondary\nmax: 10\n')
    java = generate(repo)[STATS_JAVA].decode()
    assert '{@code content/stats/luck_stat.yaml}: secondary, "Luck *&#47; &#64;Odd". */' in java


def test_expectations_hold_stats_and_rule_samples(repo: RepoPaths) -> None:
    java = generate(repo)[EXPECTATIONS].decode()
    assert (
        'new StatExpectation(\n                    "realmforge:crit_chance",\n'
        '                    "attribute.realmforge.crit_chance",\n                    false,\n'
        "                    true,\n                    0.05,\n                    1.0)" in java
    )
    assert (
        'new Sample(12.5, List.of(new Bonus("realmforge:stat/realmforge/0/vitality", "minecraft:max_health", "add_value", '
        "12.5)))" in java
    )
    assert (
        'new Bonus("realmforge:stat/realmforge/at_25_0/strength", "realmforge:crit_damage", "add_value", 0.1)'
        in java
    )
    assert "new Sample(0.0, List.of())" in java


def test_no_stats_or_rules_still_compile(repo: RepoPaths) -> None:
    # Spell schools name their power and resistance stats, and races, classes, effects and gear their modifiers' stats,
    # so they go too, and with the schools the spells and effects that name them, and the mobs and bosses that cast them.
    # Apotheosis's stat remaps name the stats that replace Apothic Attributes'.
    for folder in (
        "stats",
        "apotheosis_stat_remaps",
        "stat_rules",
        "spell_schools",
        "spells",
        "effects",
        "races",
        "classes",
        "mobs",
        "bosses",
    ):
        for file in (repo.content / folder).glob("*.yaml"):
            file.unlink()
    for file in (repo.content / "items").glob("*.yaml"):
        if "\ngear:" in file.read_text(encoding="utf-8"):
            file.unlink()
    # Astone's kill XP rank names her body.
    (repo.content / "kill_xp_multipliers" / "astone.yaml").unlink()
    files = generate(repo)
    assert "ALL = List.of();" in files[STATS_JAVA].decode()
    expectations = files[EXPECTATIONS].decode()
    assert "STATS = List.of();" in expectations
    assert "STAT_RULES = List.of();" in expectations
    assert not any(path.startswith(RULE_DATA) for path in files)
