"""The Arms Warrior's "Bloodletter" tree (RLM-543) in the pipeline: the charge and its landing (Charge), a resource the
caster gains (Charge's rage), a target distance that grows per rank, a heal of a share of max health on the caster
(Victory Rush), a refund on a kill from a rank on (Execute), a proc's damage over time from the hit that fired it
(Deep Wounds), their placeholders, and the Warrior's kit by the tree's rows."""

from __future__ import annotations

import json

import pytest

from realmforge_tools.project import RepoPaths
from realmforge_tools.validate import load_and_validate
from tests.test_spells import SPELL_DATA, assert_problem, generate, spell

CHARGE = (
    "school: realmforge:physical\n"
    "scales_with: attack_damage\n"
    "targeting: {type: realmforge:enemy, range: 12}\n"
    "target_distance: {min: 3, max: 10, max_per_level: 2}\n"
    "cast: {type: instant}\n"
    "max_level: 2\n"
    "effects:\n"
    "  - type: realmforge:charge\n"
    "    speed: 24\n"
    "    landing_radius: 1.5\n"
    "    effects:\n"
    "      - {type: realmforge:apply_effect, effect: realmforge:stunned, duration_seconds: 1}\n"
    "  - {type: realmforge:gain_resource, resource: realmforge:rage, amount: 15, per_level: 5}\n"
)
RUSH = (
    "school: realmforge:physical\n"
    "scales_with: attack_damage\n"
    "targeting: {type: realmforge:enemy, melee: true}\n"
    "cast: {type: instant}\n"
    "max_level: 3\n"
    "requires: [melee_weapon]\n"
    "window: {duration_seconds: 20, opened_by: [kill]}\n"
    "effects:\n"
    "  - {type: realmforge:damage, amount: 3, per_level: 1, attack_damage_percent: 70}\n"
    "  - {type: realmforge:heal, amount: 0, max_health_percent: 10, max_health_per_level_percent: 5, "
    "on_caster: true}\n"
)
EXECUTE = (
    "school: realmforge:physical\n"
    "scales_with: attack_damage\n"
    "targeting: {type: realmforge:enemy, melee: true}\n"
    "cast: {type: instant}\n"
    "cost:\n"
    "  resource: realmforge:rage\n"
    "  amount: 15\n"
    "  extra: {max: 30, damage_per_point_percent: 3}\n"
    "  refund_on_kill: {amount: 10, from_level: 3}\n"
    "max_level: 3\n"
    "requires: [melee_weapon]\n"
    "target_health_below_percent: 20\n"
    "effects:\n"
    "  - {type: realmforge:damage, amount: 8, per_level: 2, attack_damage_percent: 150}\n"
)
WOUNDS = (
    "school: realmforge:physical\n"
    "max_level: 3\n"
    "passive:\n"
    "  procs:\n"
    "    - trigger: crit\n"
    "      chance_percent: 100\n"
    "      damage_over_time:\n"
    "        effect: realmforge:bleeding\n"
    "        damage_percent: 20\n"
    "        damage_per_level_percent: 20\n"
    "        duration_seconds: 6\n"
)


def spell_data(repo: RepoPaths, name: str) -> dict:
    return json.loads(generate(repo)[f"{SPELL_DATA}/{name}.json"])


def described(text: str, body: str) -> str:
    return f"name: {{en_us: Test}}\ndescription: {{en_us: '{text}'}}\n{body}"


# The charge and the resource it gains


def test_a_charge_is_in_game_units(repo: RepoPaths) -> None:
    spell(repo, "test_rush_in", CHARGE)
    data = spell_data(repo, "test_rush_in")
    charge, gain = data["effects"]
    assert (charge["type"], charge["speed"], charge["landing_radius"]) == ("realmforge:charge", 24, 1.5)
    assert charge["effects"][0]["duration_ticks"] == 20
    assert gain == {
        "amount": 15,
        "per_level": 5,
        "type": "realmforge:gain_resource",
        "resource": "realmforge:rage",
    }
    assert data["target_distance"] == {"min": 3, "max": 10, "max_per_level": 2}


@pytest.mark.parametrize(
    ("change", "expected"),
    [
        (("speed: 24", "speed: 90"), "speed"),
        (("landing_radius: 1.5", "landing_radius: 20"), "landing_radius"),
        (
            (
                "realmforge:apply_effect, effect: realmforge:stunned, duration_seconds: 1}",
                "realmforge:heal, amount: 2}",
            ),
            "realmforge:heal cannot act on the enemies round a charge's landing",
        ),
        (("amount: 15, per_level: 5", "amount: 0"), "a gain of 0 gives nothing"),
        (("resource: realmforge:rage", "resource: realmforge:focus"), "resource"),
    ],
)
def test_invalid_charges_are_rejected(repo: RepoPaths, change: tuple[str, str], expected: str) -> None:
    spell(repo, "test_rush_in", CHARGE.replace(*change))
    assert_problem(repo, expected)


def test_a_charge_needs_an_enemy(repo: RepoPaths) -> None:
    body = CHARGE.replace(
        "targeting: {type: realmforge:enemy, range: 12}", "targeting: {type: realmforge:self}"
    )
    spell(repo, "test_rush_in", body.replace("target_distance: {min: 3, max: 10, max_per_level: 2}\n", ""))
    assert_problem(repo, "realmforge:charge acts on the spell's targets")


def test_a_class_spell_gains_the_class_resource(repo: RepoPaths) -> None:
    spell(repo, "test_rush_in", CHARGE.replace("resource: realmforge:rage", "resource: realmforge:mana"))
    warrior = repo.content / "classes" / "warrior.yaml"
    warrior.write_text(warrior.read_text().replace("realmforge:charge", "realmforge:test_rush_in"))
    assert_problem(
        repo, "realmforge:test_rush_in gives realmforge:mana, and the class's resource is realmforge:rage"
    )


# A target distance that grows per rank


@pytest.mark.parametrize(
    ("change", "expected"),
    [
        (
            ("range: 12", "range: 10"),
            "targeting.range is the farthest any rank reaches, target_distance's 12",
        ),
        (("{min: 3, max: 10", "{min: 10, max: 10"), "min 10 is nearer than max 10"),
        (
            ("{type: realmforge:enemy, range: 12}", "{type: realmforge:enemy, melee: true}"),
            "the targeting is an enemy with a range",
        ),
    ],
)
def test_invalid_target_distances_are_rejected(
    repo: RepoPaths, change: tuple[str, str], expected: str
) -> None:
    spell(repo, "test_rush_in", CHARGE.replace(*change))
    assert_problem(repo, expected)


def test_a_target_distance_that_grows_needs_more_than_one_rank(repo: RepoPaths) -> None:
    body = CHARGE.replace("max_level: 2", "max_level: 1").replace("amount: 15, per_level: 5", "amount: 15")
    spell(repo, "test_rush_in", body.replace("range: 12", "range: 10"))
    assert_problem(repo, "nothing grows with the level at max_level 1 (target_distance)")


def test_range_placeholders_read_the_target_distance(repo: RepoPaths) -> None:
    text = "Rushes {min_range} to {range} blocks, stunning within {radius} for {duration} sec; {resource_gain} rage."
    spell(repo, "test_rush_in", described(text, CHARGE))
    load_and_validate(repo)
    plain = CHARGE.replace("target_distance: {min: 3, max: 10, max_per_level: 2}\n", "").replace(
        "range: 12", "range: 10"
    )
    spell(repo, "test_rush_in", described(text, plain.replace("amount: 15, per_level: 5", "amount: 15")))
    assert_problem(repo, "{min_range} reads target_distance's min, and the spell has none")


# A heal of a share of max health on the caster


def test_a_heal_on_the_caster_is_in_game_units(repo: RepoPaths) -> None:
    spell(repo, "test_rush", RUSH)
    heal = spell_data(repo, "test_rush")["effects"][1]
    assert (heal["max_health"], heal["max_health_per_level"], heal["on_caster"]) == (0.1, 0.05, True)


def test_a_heal_of_an_enemy_target_is_refused_unless_it_heals_the_caster(repo: RepoPaths) -> None:
    spell(repo, "test_rush", RUSH.replace(", on_caster: true}", "}"))
    assert_problem(repo, "realmforge:heal acts on the spell's targets")


def test_a_heal_of_the_caster_is_one_of_the_spells_own_effects(repo: RepoPaths) -> None:
    body = RUSH.replace(
        "  - {type: realmforge:damage, amount: 3, per_level: 1, attack_damage_percent: 70}\n  - {type: realmforge:heal",
        "  - type: realmforge:melee\n    shape: swing\n    effects:\n"
        "      - {type: realmforge:damage, amount: 3, per_level: 1, attack_damage_percent: 70}\n"
        "      - {type: realmforge:heal",
    ).replace("targeting: {type: realmforge:enemy, melee: true}", "targeting: {type: realmforge:self}")
    spell(repo, "test_rush", body)
    assert_problem(
        repo, "effects.0.effects.1.on_caster: a heal of the caster is one of the spell's own effects"
    )


def test_max_health_growth_needs_its_base(repo: RepoPaths) -> None:
    spell(repo, "test_rush", RUSH.replace("max_health_percent: 10, ", ""))
    assert_problem(repo, "max_health_per_level_percent grows max_health_percent")


def test_window_and_heal_placeholders(repo: RepoPaths) -> None:
    text = "For {window} sec after a kill: {damage} damage, healing {max_health_heal}% of max health."
    spell(repo, "test_rush", described(text, RUSH))
    load_and_validate(repo)
    spell(
        repo,
        "test_rush",
        described(text, RUSH.replace("window: {duration_seconds: 20, opened_by: [kill]}\n", "")),
    )
    assert_problem(repo, "{window} reads the spell's window, and the spell has none")


# A refund on a kill


def test_a_kill_refund_is_in_game_units(repo: RepoPaths) -> None:
    spell(repo, "test_finish", EXECUTE)
    assert spell_data(repo, "test_finish")["cost"]["refund_on_kill"] == {"amount": 10, "from_level": 3}


@pytest.mark.parametrize(
    ("change", "expected"),
    [
        (("from_level: 3}", "from_level: 4}"), "cost.refund_on_kill.from_level: a rank above max_level 3"),
        (("{amount: 10, from_level: 3}", "{amount: 0, from_level: 3}"), "amount"),
    ],
)
def test_invalid_refunds_are_rejected(repo: RepoPaths, change: tuple[str, str], expected: str) -> None:
    spell(repo, "test_finish", EXECUTE.replace(*change))
    assert_problem(repo, expected)


def test_a_refund_needs_damage_to_kill(repo: RepoPaths) -> None:
    body = EXECUTE.replace("  extra: {max: 30, damage_per_point_percent: 3}\n", "").replace(
        "  - {type: realmforge:damage, amount: 8, per_level: 2, attack_damage_percent: 150}\n",
        "  - {type: realmforge:apply_effect, effect: realmforge:stunned, duration_seconds: 1}\n",
    )
    spell(repo, "test_finish", body.replace("requires: [melee_weapon]\n", ""))
    assert_problem(repo, "a kill refunds the cost, and the spell deals no damage to kill")


def test_execute_placeholders(repo: RepoPaths) -> None:
    text = (
        "Below {health_below}%: {damage} damage, up to {extra_cost} more rage for {extra_damage}% each; "
        "from rank {refund_rank} a kill refunds {refund}."
    )
    spell(repo, "test_finish", described(text, EXECUTE))
    load_and_validate(repo)
    spell(
        repo,
        "test_finish",
        described(text, EXECUTE.replace("  refund_on_kill: {amount: 10, from_level: 3}\n", "")),
    )
    assert_problem(repo, "{refund_rank} reads cost.refund_on_kill, and the spell has none")


def test_a_refund_may_grow_by_rank(repo: RepoPaths) -> None:
    # Matěj, 2026-11-03: Execute refunds 5/10/15 rage.
    body = EXECUTE.replace("{amount: 10, from_level: 3}", "{amount: 5, per_level: 5}")
    spell(repo, "test_finish", described("A kill refunds {refund} rage.", body))
    data = spell_data(repo, "test_finish")
    assert data["cost"]["refund_on_kill"] == {"amount": 5, "per_level": 5, "from_level": 1}
    spell(repo, "test_finish", body.replace("max_level: 3\n", "max_level: 1\n"))
    assert_problem(repo, "cost.refund_on_kill")


# Long buffs and their bonuses


def test_a_status_effect_may_last_ten_minutes(repo: RepoPaths) -> None:
    shout = repo.content / "spells" / "battle_shout.yaml"
    text = shout.read_text(encoding="utf-8")
    assert "duration_seconds: 600" in text
    shout.write_text(text.replace("duration_seconds: 600", "duration_seconds: 601"), encoding="utf-8")
    assert_problem(repo, "duration_seconds is more than 0 and at most 600")


def test_bonus_placeholders_read_one_modifier_each(repo: RepoPaths) -> None:
    shout = repo.content / "spells" / "battle_shout.yaml"
    text = shout.read_text(encoding="utf-8")
    shout.write_text(text.replace("{bonus_attack_speed}", "{bonus_armor}"), encoding="utf-8")
    assert_problem(repo, "{bonus_armor} reads realmforge:battle_shout's one modifier on armor, and it has 0")
    shout.write_text(text.replace("{bonus_attack_damage}", "{bonus}"), encoding="utf-8")
    assert_problem(repo, "{bonus} reads realmforge:battle_shout's one modifier, and it has 2")


# A hit that drains more of a magic shield (RLM-580)


def test_a_hit_may_drain_more_of_a_magic_shield(repo: RepoPaths) -> None:
    more = "attack_damage_percent: 150, magic_shield_damage_percent: 30}"
    spell(repo, "test_finish", EXECUTE.replace("attack_damage_percent: 150}", more))
    assert spell_data(repo, "test_finish")["effects"][0]["magic_shield_damage"] == 0.3
    spell(repo, "test_finish", EXECUTE.replace("attack_damage_percent: 150}", more.replace("30}", "0}")))
    assert_problem(repo, "magic_shield_damage_percent")


# A proc's damage over time


def test_a_procs_damage_over_time_is_in_game_units(repo: RepoPaths) -> None:
    spell(repo, "test_wounds", WOUNDS)
    proc = spell_data(repo, "test_wounds")["passive"]["procs"][0]
    assert proc["damage_over_time"] == {
        "effect": "realmforge:bleeding",
        "damage": 0.2,
        "damage_per_level": 0.2,
        "duration_ticks": 120,
    }


@pytest.mark.parametrize(
    ("change", "expected"),
    [
        (("trigger: crit", "trigger: kill"), "a damage_over_time proc takes a share of a hit"),
        (
            ("effect: realmforge:bleeding", "effect: realmforge:stunned"),
            "is no effect of content/effects/ with stacks",
        ),
        (("duration_seconds: 6", "duration_seconds: 6.25"), "a whole number of its intervals"),
        (
            (
                "      chance_percent: 100\n",
                "      chance_percent: 100\n      open_window: realmforge:overpower\n",
            ),
            "a proc does one thing",
        ),
    ],
)
def test_invalid_proc_damage_over_time_is_rejected(
    repo: RepoPaths, change: tuple[str, str], expected: str
) -> None:
    spell(repo, "test_wounds", WOUNDS.replace(*change))
    assert_problem(repo, expected)


def test_proc_damage_placeholders(repo: RepoPaths) -> None:
    text = "Crits bleed for {proc_damage}% over {proc_duration} sec."
    spell(repo, "test_wounds", described(text, WOUNDS))
    load_and_validate(repo)


# The Warrior's kit (RLM-543)


def test_the_arms_tree_stands_in_its_rows() -> None:
    content, _ = load_and_validate(RepoPaths())
    warrior = next(entry.model for entry in content.classes if entry.model.id == "warrior")
    arms = next(entry for entry in warrior.specializations if entry.id == "arms")
    assert arms.first_spell == "realmforge:slam"
    assert [
        (taught.spell.removeprefix("realmforge:"), taught.level, taught.column) for taught in arms.spells
    ] == [
        ("charge", 1, 1),
        ("slam", 1, 2),
        ("deep_wounds", 1, 3),
        ("rend", 6, 1),
        ("battle_shout", 6, 2),
        ("two_handed_weapon_specialization", 6, 3),
        ("bloodthirst", 12, 1),
        ("overpower", 12, 2),
        ("victory_rush", 12, 3),
        ("execute", 18, 1),
    ]
    assert [taught.requires for taught in arms.spells if taught.requires] == ["realmforge:rend"]
    assert [entry.id for entry in warrior.specializations if not entry.spells] == ["fury", "protection"]
    ranks = {entry.model.id: entry.model.max_level for entry in content.spells}
    assert {
        t.spell.removeprefix("realmforge:"): ranks[t.spell.removeprefix("realmforge:")] for t in arms.spells
    } == {
        "slam": 5,
        "charge": 2,
        "deep_wounds": 3,
        "rend": 3,
        "battle_shout": 2,
        "two_handed_weapon_specialization": 3,
        "bloodthirst": 2,
        "overpower": 5,
        "victory_rush": 3,
        "execute": 3,
    }


def test_the_trees_numbers_follow_the_table() -> None:
    content, _ = load_and_validate(RepoPaths())
    spells = {entry.model.id: entry.model for entry in content.spells}
    slam = spells["slam"].effects[0].effects[0]
    assert [slam.amount_at(rank) for rank in (1, 5)] == [5, 9]
    assert [slam.attack_damage_at(rank) for rank in (1, 5)] == [150, 230]
    rend_hit, rend_bleed = spells["rend"].effects
    assert [(rend_hit.amount_at(r), rend_hit.attack_damage_at(r)) for r in (1, 3)] == [(5, 50), (7, 70)]
    assert [(rend_bleed.amount_at(r), rend_bleed.attack_damage_at(r)) for r in (1, 3)] == [(8, 90), (12, 130)]
    overpower = spells["overpower"]
    assert [
        (overpower.effects[0].amount_at(r), overpower.effects[0].attack_damage_at(r)) for r in (1, 5)
    ] == [
        (6, 120),
        (10, 180),
    ]
    assert [overpower.crit.bonus_chance_at(r) for r in (1, 5)] == [10, 30]
    rush_hit, rush_heal = spells["victory_rush"].effects
    assert [(rush_hit.amount_at(r), rush_hit.attack_damage_at(r)) for r in (1, 3)] == [(5, 80), (7, 100)]
    assert [rush_heal.max_health_at(r) for r in (1, 3)] == [10, 20]
    execute = spells["execute"]
    assert [(execute.effects[0].amount_at(r), execute.effects[0].attack_damage_at(r)) for r in (1, 3)] == [
        (10, 190),
        (14, 230),
    ]
    charge = spells["charge"]
    assert charge.target_distance is not None
    assert [charge.target_distance.max_at(r) for r in (1, 2)] == [10, 12]
    assert [charge.effects[1].amount_at(r) for r in (1, 2)] == [10, 20]
    assert [charge.effects[0].effects[0].duration_at(r) for r in (1, 2)] == [3, 3]
    # Costs and cooldowns (Matěj, 2026-11-03, RLM-543's Confluence comments, and his rebalance, RLM-604).
    assert [spells["slam"].cooldown_seconds, charge.cooldown_seconds, overpower.cooldown_seconds] == [
        4,
        10,
        4,
    ]
    assert [spells["rend"].cost.amount_at(r) for r in (1, 2, 3)] == [20, 30, 40]
    assert [overpower.cost.amount_at(r) for r in (1, 5)] == [20, 20]
    assert [spells["victory_rush"].cost.amount_at(r) for r in (1, 3)] == [10, 10]
    assert spells["battle_shout"].cost is None
    assert [execute.cost.amount_at(r) for r in (1, 2, 3)] == [10, 20, 30]
    assert execute.cost.extra.max == 30
    assert [execute.cooldown_seconds, spells["victory_rush"].cooldown_seconds] == [0, 0]
    assert [execute.cost.refund_on_kill.amount_at(r) for r in (1, 2, 3)] == [5, 10, 15]
    wounds = spells["deep_wounds"].passive.procs[0].damage_over_time
    assert [wounds.damage_at(r) for r in (1, 3)] == [20, 60]
    taste = spells["bloodthirst"].passive.procs[0]
    assert [taste.chance_at(r) for r in (1, 2)] == [10, 20]
