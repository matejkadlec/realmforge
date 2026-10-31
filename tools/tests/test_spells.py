"""Spells (content/spells/): validation and generation (architecture 6.4, 11; C3a-1, RLM-209; C3a-2, RLM-210)."""

from __future__ import annotations

import json
import re

import pydantic
import pytest

from realmforge_tools.generate import generate_all
from realmforge_tools.problems import ValidationFailed
from realmforge_tools.project import RepoPaths
from realmforge_tools.schema.spell import DamageEffectDef, SpellDef
from realmforge_tools.validate import load_and_validate
from tests.conftest import write

SPELL_DATA = "mod/src/generated/resources/data/realmforge/realmforge/spell"
LANG = "mod/src/generated/resources/assets/realmforge/lang/en_us.json"
KEYS_JAVA = "mod/src/generated/java/io/github/matejkadlec/realmforge/generated/GeneratedSpells.java"
EXPECTATIONS = (
    "qa-mod/src/generated/java/io/github/matejkadlec/realmforge/qa/generated/GeneratedSpellExpectations.java"
)

FIREBALL = (
    "school: realmforge:fire\n"
    "scales_with: spell_power\n"
    "targeting: {type: realmforge:enemy, range: 24}\n"
    "cast: {type: instant}\n"
    "effects:\n"
    "  - {type: realmforge:damage, amount: 12}\n"
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


# Every spell names itself and says what it does (C3b, RLM-25); the tests that are not about the texts take these.
TEXTS = "name: {en_us: Fireball}\ndescription: {en_us: A ball of fire.}\n"


def spell(repo: RepoPaths, name: str, body: str) -> None:
    texts = "" if body.startswith(("name:", "description:")) or "\nname:" in body else TEXTS
    write(repo.content / "spells" / f"{name}.yaml", f"id: {name}\n{texts}{body}")


def spell_model(**fields: object) -> SpellDef:
    data = {
        "id": "fireball",
        "name": {"en_us": "Fireball"},
        "description": {"en_us": "A ball of fire."},
        "school": "realmforge:fire",
        "scales_with": "spell_power",
        "targeting": {"type": "realmforge:enemy", "range": 24},
        "cast": {"type": "instant"},
        "effects": [{"type": "realmforge:damage", "amount": 12}],
        **fields,
    }
    return SpellDef.model_validate(data)


# The repository's content


def test_repository_spells_are_the_class_kits_engine_tests_stoneform_and_astones() -> None:
    content, universe = load_and_validate(RepoPaths())
    ids = {entry.model.id for entry in content.spells}
    assert ids == {
        # The Warrior's and the Mage's kits and the Human's racial (C4, RLM-27).
        "slam",
        "battle_shout",
        "shield_bash",
        "reckless_strike",
        "frostbolt",
        "ice_barrier",
        "fireball",
        "arcane_missiles",
        "will_to_survive",
        # The first passive spells (RLM-541), of the M1 trees (RLM-542, RLM-543).
        "improved_frostbolt",
        "shatter",
        "two_handed_weapon_specialization",
        # Astone's (W5b, RLM-491).
        "astone_boulder",
        "astone_cracked_stone",
        "astone_quake",
        "astone_rockfall",
        "astone_shockwave",
        # The Hewn Chanter's (W3, RLM-33); the rest are the engine's tests.
        "stoneform",
        "test_blink",
        "test_bolt",
        "test_charge",
        "test_chill",
        "test_cleanse",
        "test_drain",
        "test_forward_missile",
        "test_homing_bolt",
        "test_ignite",
        "test_mend",
        "test_mending_ring",
        "test_missile",
        "test_npc_bolt",
        "test_npc_far_bolt",
        "test_npc_random_bolt",
        "test_npc_target_bolt",
        "test_proc_spark",
        "test_regrow",
        "test_sap",
        "test_scorch",
        "test_shove",
        "test_slow_bolt",
        "test_slow_missile",
        "test_strike",
        "test_sure_crit",
        # The boss framework's test boss hardens in its second phase (W5a, RLM-490).
        "test_titan_harden",
        # Bleeding's (RLM-539), the windows', procs' and conditions' (RLM-540) and the melee shapes' (RLM-399).
        "test_cleave",
        "test_deep_wound",
        "test_deep_wounds",
        "test_execute",
        "test_frostbite",
        "test_ice_lance",
        "test_lacerate",
        "test_overpower",
        "test_rend",
        "test_taste_for_blood",
        "test_victory_rush",
    }
    assert universe.contains("realmforge:spell", "realmforge:test_bolt")


# Schema


@pytest.mark.parametrize(
    ("fields", "expected"),
    [
        ({"effects": []}, "an active spell has at least one effect"),
        ({"effects": [{"type": "realmforge:fly", "amount": 1}]}, "does not match any of the expected tags"),
        ({"effects": [{"type": "realmforge:damage", "amount": -1}]}, "greater than or equal to 0"),
        (
            {"effects": [{"type": "realmforge:damage", "amount": 1, "per_level": 1}]},
            "nothing grows with the level at max_level 1 \\(effects.0\\)",
        ),
        (
            {"cost": {"resource": "realmforge:mana", "amount": 5, "per_level": 1}},
            "nothing grows with the level at max_level 1 \\(cost\\)",
        ),
        ({"cost": {"resource": "realmforge:mana", "amount": 0}}, "a cost of 0 is no cost"),
        ({"cost": {"resource": "realmforge:energy", "amount": 5}}, "realmforge:mana"),
        ({"targeting": {"type": "realmforge:enemy", "range": 200}}, "less than or equal to 128"),
        ({"targeting": {"type": "realmforge:enemy", "range": 0}}, "greater than 0"),
        ({"targeting": {"type": "realmforge:self", "range": 5}}, "Extra inputs are not permitted"),
        ({"cast": {"type": "charge"}}, "does not match any of the expected tags"),
        ({"cast": {"type": "instant", "lock_on": True}}, "Extra inputs are not permitted"),
        ({"cast": {"type": "cast_time"}}, "duration_seconds\n  Field required"),
        ({"cast": {"type": "cast_time", "duration_seconds": 0}}, "a cast time of 0 is an instant cast"),
        ({"cast": {"type": "cast_time", "duration_seconds": 1.01}}, "not a whole number of game ticks"),
        ({"cast": {"type": "cast_time", "duration_seconds": 2, "interval_seconds": 1}}, "Extra inputs"),
        ({"cast": {"type": "channel", "duration_seconds": 3}}, "interval_seconds\n  Field required"),
        (
            {"cast": {"type": "channel", "duration_seconds": 3, "interval_seconds": 0}},
            "ticks after an interval of more than 0",
        ),
        (
            {"cast": {"type": "channel", "duration_seconds": 3, "interval_seconds": 2}},
            "duration_seconds 3.0 is not a whole number of intervals of 2.0 s",
        ),
        (
            {"cast": {"type": "channel", "duration_seconds": 0, "interval_seconds": 1}},
            "duration_seconds 0.0 is not a whole number of intervals",
        ),
        ({"cooldown_seconds": 0.01}, "not a whole number of game ticks"),
        ({"global_cooldown_seconds": 0}, "greater than 0"),
        ({"max_level": 0}, "greater than or equal to 1"),
        ({"crit": {"can_crit": False, "multiplier_percent": 200}}, "a spell that cannot crit takes no"),
        ({"crit": {"can_crit": False, "bonus_chance_percent": 5}}, "a spell that cannot crit takes no"),
        ({"crit": {"multiplier_percent": 90}}, "greater than or equal to 100"),
        ({"crit": {"bonus_chance_percent": 150}}, "less than or equal to 100"),
        ({"scales_with": "intellect"}, "'spell_power' or 'attack_damage'"),
    ],
)
def test_invalid_spells_are_rejected(fields: dict, expected: str) -> None:
    with pytest.raises(ValueError, match=expected):
        spell_model(**fields)


def test_per_level_values_grow_from_level_one() -> None:
    model = spell_model(
        max_level=3,
        cost={"resource": "realmforge:mana", "amount": 10, "per_level": 5},
        effects=[{"type": "realmforge:damage", "amount": 6, "per_level": 2}],
    )
    assert [model.effects[0].amount_at(level) for level in (1, 2, 3)] == [6, 8, 10]
    assert model.cost is not None
    assert model.cost.amount_at(3) == 20


def test_timed_casts() -> None:
    cast_time = spell_model(cast={"type": "cast_time", "duration_seconds": 2.5}).cast
    assert (cast_time.type, cast_time.castable_while_moving, cast_time.lock_on) == ("cast_time", False, False)
    channel = spell_model(
        cast={"type": "channel", "duration_seconds": 3, "interval_seconds": 0.5, "lock_on": True}
    ).cast
    assert (channel.type, channel.interval_seconds, channel.lock_on) == ("channel", 0.5, True)


def test_defaults() -> None:
    model = spell_model()
    assert model.cost is None
    assert (model.cooldown_seconds, model.global_cooldown_seconds, model.max_level) == (0, 0.5, 1)
    assert (
        model.crit.can_crit and model.crit.multiplier_percent is None and model.crit.bonus_chance_percent == 0
    )
    assert not model.npc_only


# Validation against other content


def test_the_school_must_exist(repo: RepoPaths) -> None:
    spell(repo, "fireball", FIREBALL.replace("realmforge:fire\n", "realmforge:fyre\n"))
    assert_problem(repo, "school: unknown id 'realmforge:fyre' in registry realmforge:spell_school")


def test_magic_scales_with_spell_power(repo: RepoPaths) -> None:
    spell(
        repo,
        "fireball",
        FIREBALL.replace("spell_power", "attack_damage").replace(
            "amount: 12", "amount: 12, attack_damage_percent: 50"
        ),
    )
    assert_problem(
        repo,
        "scales_with: realmforge:fire is a magic school, so the spell scales with spell_power, not attack_damage",
    )


def test_physical_scales_with_attack_damage(repo: RepoPaths) -> None:
    spell(repo, "slam", FIREBALL.replace("realmforge:fire", "realmforge:physical"))
    assert_problem(
        repo,
        "realmforge:physical is a physical school, so the spell scales with attack_damage, not spell_power",
    )


def test_attack_damage_adds_nothing_to_spell_power_spells(repo: RepoPaths) -> None:
    spell(repo, "fireball", FIREBALL.replace("amount: 12", "amount: 12, attack_damage_percent: 50"))
    assert_problem(
        repo,
        "effects.0.attack_damage_percent: the spell scales with spell_power, so attack damage adds nothing to it",
    )


def test_spell_power_adds_nothing_to_attack_damage_spells(repo: RepoPaths) -> None:
    body = FIREBALL.replace("realmforge:fire", "realmforge:physical").replace("spell_power", "attack_damage")
    spell(
        repo,
        "slam",
        body.replace("amount: 12", "amount: 12, attack_damage_percent: 100, spell_power_percent: 50"),
    )
    assert_problem(
        repo,
        "effects.0.spell_power_percent: the spell scales with attack_damage, so spell power adds nothing to it",
    )


def test_a_coefficient_grows_per_rank_from_its_base(repo: RepoPaths) -> None:
    """Scaling system C (RLM-534): Frostbolt's 100 % + 25 % a rank, 200 % at rank 5; growth without a base is
    refused."""
    damage = DamageEffectDef(
        type="realmforge:damage",
        amount=8,
        per_level=1,
        spell_power_percent=100,
        spell_power_per_level_percent=25,
    )
    assert damage.spell_power_at(5) == 200
    assert damage.attack_damage_at(5) == 0
    with pytest.raises(
        pydantic.ValidationError, match="spell_power_per_level_percent grows spell_power_percent"
    ):
        DamageEffectDef(type="realmforge:damage", amount=8, spell_power_per_level_percent=25)
    spell(
        repo,
        "fireball",
        FIREBALL.replace(
            "amount: 12", "amount: 12, spell_power_percent: 100, spell_power_per_level_percent: 25"
        ),
    )
    assert_problem(repo, "nothing grows with the level at max_level 1 (effects.0)")


def test_attack_damage_damage_names_its_share(repo: RepoPaths) -> None:
    body = FIREBALL.replace("realmforge:fire", "realmforge:physical").replace("spell_power", "attack_damage")
    spell(repo, "slam", body)
    assert_problem(
        repo, "effects.0.attack_damage_percent: the spell scales with attack_damage: name the share"
    )


def test_an_attack_damage_heal_may_leave_out_its_share(repo: RepoPaths) -> None:
    spell(
        repo,
        "second_wind",
        "school: realmforge:physical\n"
        "scales_with: attack_damage\n"
        "targeting: {type: realmforge:self}\n"
        "cast: {type: instant}\n"
        "effects:\n"
        "  - {type: realmforge:heal, amount: 6}\n",
    )
    content, _ = load_and_validate(repo)
    assert any(entry.model.id == "second_wind" for entry in content.spells)


# Damage hits an enemy, which the NPC targeting types choose too (C3a-4).
ENEMIES = (
    "realmforge:enemy or realmforge:current_target or realmforge:random_player or realmforge:farthest_player"
)


@pytest.mark.parametrize(
    ("targeting", "effect", "allowed"),
    [
        ("{type: realmforge:self}", "{type: realmforge:damage, amount: 3}", ENEMIES),
        ("{type: realmforge:ally, range: 8}", "{type: realmforge:damage, amount: 3}", ENEMIES),
        (
            "{type: realmforge:enemy, range: 8}",
            "{type: realmforge:heal, amount: 3}",
            "realmforge:self or realmforge:ally",
        ),
    ],
)
def test_effects_fit_the_targeting(repo: RepoPaths, targeting: str, effect: str, allowed: str) -> None:
    spell(
        repo,
        "odd",
        "school: realmforge:arcane\n"
        "scales_with: spell_power\n"
        f"targeting: {targeting}\n"
        "cast: {type: instant}\n"
        f"effects:\n  - {effect}\n",
    )
    assert_problem(repo, "effects.0.type: ")
    assert_problem(repo, f"so the targeting is {allowed}, not")


def test_the_file_name_is_the_id(repo: RepoPaths) -> None:
    write(repo.content / "spells" / "fireball.yaml", "id: fire_ball\n" + TEXTS + FIREBALL)
    assert_problem(repo, "'fire_ball' must equal the file name 'fireball'")


# Generation


def test_spell_data_is_in_game_units(repo: RepoPaths) -> None:
    spell(
        repo,
        "fireball",
        FIREBALL.replace(
            "cast: {type: instant}\n", "cast: {type: instant}\ncooldown_seconds: 8\nmax_level: 2\n"
        ).replace("amount: 12}", "amount: 12, per_level: 3}")
        + "cost: {resource: realmforge:mana, amount: 20}\n"
        + "crit: {multiplier_percent: 200, bonus_chance_percent: 10}\n",
    )
    assert json.loads(generate(repo)[f"{SPELL_DATA}/fireball.json"]) == {
        "name": "spell.realmforge.fireball.name",
        "description": "spell.realmforge.fireball.description",
        "school": "realmforge:fire",
        "scales_with": "spell_power",
        "targeting": {"type": "realmforge:enemy", "range": 24.0},
        "cast": {"type": "instant"},
        "cost": {"amount": 20.0, "per_level": 0, "resource": "realmforge:mana"},
        "cooldown_ticks": 160,
        "global_cooldown_ticks": 10,
        "max_level": 2,
        "crit": {"can_crit": True, "multiplier": 2.0, "bonus_chance": 0.1},
        "npc_only": False,
        "requires": [],
        "castable_while_stunned": False,
        "effects": [{"amount": 12.0, "per_level": 3.0, "type": "realmforge:damage"}],
    }


def test_timed_casts_are_in_ticks(repo: RepoPaths) -> None:
    files = generate(repo)
    assert json.loads(files[f"{SPELL_DATA}/test_slow_bolt.json"])["cast"] == {
        "castable_while_moving": False,
        "lock_on": False,
        "keep_sight": False,
        "type": "cast_time",
        "duration_ticks": 40,
        "forms_projectile": False,
    }
    assert json.loads(files[f"{SPELL_DATA}/test_drain.json"])["cast"] == {
        "castable_while_moving": False,
        "lock_on": False,
        "keep_sight": False,
        "type": "channel",
        "duration_ticks": 60,
        "interval_ticks": 10,
    }
    assert json.loads(files[f"{SPELL_DATA}/test_homing_bolt.json"])["cast"] == {
        "castable_while_moving": True,
        "lock_on": True,
        "keep_sight": False,
        "type": "cast_time",
        "duration_ticks": 20,
        "forms_projectile": False,
    }


def test_attack_damage_is_a_fraction(repo: RepoPaths) -> None:
    effect = json.loads(generate(repo)[f"{SPELL_DATA}/test_strike.json"])["effects"][0]
    assert effect == {"amount": 2.0, "per_level": 0, "type": "realmforge:damage", "attack_damage": 1.0}


def test_names_and_descriptions_go_into_the_lang_file(repo: RepoPaths) -> None:
    lang = json.loads(generate(repo)[LANG])
    assert lang["spell.realmforge.test_bolt.name"] == "Test Bolt"
    assert lang["spell.realmforge.test_bolt.description"] == (
        "Hurls a bolt of fire at an enemy for {damage} damage."
    )
    spell_keys = {key for key in lang if key.startswith("spell.realmforge.")}
    assert len(spell_keys) == 2 * len(load_and_validate(repo)[0].spells)


def test_keys_and_expectations(repo: RepoPaths) -> None:
    files = generate(repo)
    keys = files[KEYS_JAVA].decode()
    assert 'public static final ResourceKey<Spell> TEST_BOLT = key("test_bolt");' in keys
    assert (
        "ALL = List.of(ARCANE_MISSILES, ASTONE_BOULDER, ASTONE_CRACKED_STONE, ASTONE_QUAKE, ASTONE_ROCKFALL, "
        "ASTONE_SHOCKWAVE, BATTLE_SHOUT, FIREBALL, FROSTBOLT, ICE_BARRIER, IMPROVED_FROSTBOLT, RECKLESS_STRIKE, "
        "SHATTER, SHIELD_BASH, SLAM, STONEFORM, TEST_BLINK, TEST_BOLT, TEST_CHARGE, TEST_CHILL, TEST_CLEANSE, "
        "TEST_CLEAVE, TEST_DEEP_WOUND, TEST_DEEP_WOUNDS, TEST_DRAIN, TEST_EXECUTE, TEST_FORWARD_MISSILE, "
        "TEST_FROSTBITE, TEST_HOMING_BOLT, TEST_ICE_LANCE, TEST_IGNITE, TEST_LACERATE, TEST_MEND, TEST_MENDING_RING, "
        "TEST_MISSILE, TEST_NPC_BOLT, TEST_NPC_FAR_BOLT, TEST_NPC_RANDOM_BOLT, TEST_NPC_TARGET_BOLT, TEST_OVERPOWER, "
        "TEST_PROC_SPARK, TEST_REGROW, TEST_REND, TEST_SAP, TEST_SCORCH, TEST_SHOVE, TEST_SLOW_BOLT, TEST_SLOW_MISSILE, "
        "TEST_STRIKE, TEST_SURE_CRIT, TEST_TASTE_FOR_BLOOD, TEST_TITAN_HARDEN, TEST_VICTORY_RUSH, "
        "TWO_HANDED_WEAPON_SPECIALIZATION, WILL_TO_SURVIVE);"
    ) in keys
    expectations = files[EXPECTATIONS].decode()
    assert (
        "            new SpellExpectation(\n"
        '                    "realmforge:test_bolt",\n'
        '                    "spell.realmforge.test_bolt.name",\n'
        '                    "spell.realmforge.test_bolt.description",\n'
        '                    Optional.of("realmforge:fire"),\n'
        '                    "spell_power",\n'
        '                    "realmforge:enemy",\n'
        "                    16.0,\n"
        '                    "instant",\n'
        "                    0,\n"
        "                    0,\n"
        "                    false,\n"
        "                    false,\n"
        "                    false,\n"
        "                    Optional.empty(),\n"
        "                    Optional.empty(),\n"
        "                    false,\n"
        '                    "raise",\n'
        '                    Optional.of("realmforge:mana"),\n'
        "                    10.0,\n"
        "                    5.0,\n"
        "                    60,\n"
        "                    10,\n"
        "                    3,\n"
        "                    true,\n"
        "                    Optional.empty(),\n"
        "                    0.0,\n"
        "                    0.0,\n"
        "                    false,\n"
        "                    List.of(),\n"
        "                    false,\n"
        "                    false,\n"
        "                    List.of(\n"
        '                            new EffectExpectation("realmforge:damage", '
        'Map.of("amount", 6.0, "attack_damage", 0.0, "attack_damage_per_level", 0.0, "per_level", 2.0, '
        '"spell_power", 1.0, "spell_power_per_level", 0.0), Map.of(), List.of())))'
    ) in expectations
    assert (
        'new EffectExpectation("realmforge:damage", Map.of("amount", 2.0, "attack_damage", 1.0, '
        '"attack_damage_per_level", 0.0, "per_level", 0.0, "spell_power", 0.0, "spell_power_per_level", 0.0)'
    ) in expectations
    assert "Optional.of(2.0),\n                    1.0," in expectations
    channel = expectations[expectations.index('"realmforge:test_drain"') :]
    assert (
        '"channel",\n                    60,\n                    10,\n                    false,\n'
        in channel
    )
    homing = expectations[expectations.index('"realmforge:test_homing_bolt"') :]
    assert (
        '"cast_time",\n                    20,\n                    0,\n                    true,\n                    true,\n'
        in (homing)
    )


# C3a-3: spell entities, status effects, movement, knockback, ground points and directions

PROJECTILE = "{type: realmforge:projectile, speed: 20, effects: [{type: realmforge:damage, amount: 4}]}"
AREA = (
    "{type: realmforge:area, radius: 3, duration_seconds: 4, interval_seconds: 1, "
    "effects: [{type: realmforge:damage, amount: 2}]}"
)


@pytest.mark.parametrize(
    ("effect", "expected"),
    [
        (
            {"type": "realmforge:apply_effect", "effect": "minecraft:slowness", "duration_seconds": 0},
            "duration_seconds is more than 0 and at most 60",
        ),
        (
            {"type": "realmforge:apply_effect", "effect": "minecraft:slowness", "duration_seconds": 90},
            "research SP-8",
        ),
        (
            {
                "type": "realmforge:apply_effect",
                "effect": "minecraft:slowness",
                "duration_seconds": 3,
                "amplifier": 10,
            },
            "less than or equal to 9",
        ),
        ({"type": "realmforge:knockback", "strength": 0}, "greater than 0"),
        ({"type": "realmforge:dash", "strength": 6}, "less than or equal to 5"),
        ({"type": "realmforge:teleport", "distance": 5}, "Extra inputs are not permitted"),
        ({"type": "realmforge:projectile", "speed": 20, "effects": []}, "at least 1 item"),
        (
            {
                "type": "realmforge:projectile",
                "speed": 20,
                "lifetime_seconds": 20,
                "effects": [{"type": "realmforge:damage", "amount": 1}],
            },
            "lifetime_seconds is more than 0 and at most 10",
        ),
        (
            {
                "type": "realmforge:projectile",
                "speed": 20,
                "effects": [{"type": "realmforge:projectile", "speed": 5, "effects": []}],
            },
            "does not match any of the expected tags",
        ),
        (
            {
                "type": "realmforge:area",
                "radius": 3,
                "duration_seconds": 3,
                "interval_seconds": 2,
                "effects": [{"type": "realmforge:damage", "amount": 1}],
            },
            "duration_seconds 3.0 is not a whole number of intervals of 2.0 s",
        ),
        (
            {
                "type": "realmforge:area",
                "radius": 20,
                "duration_seconds": 3,
                "interval_seconds": 1,
                "effects": [{"type": "realmforge:damage", "amount": 1}],
            },
            "less than or equal to 16",
        ),
        (
            {
                "type": "realmforge:area",
                "radius": 3,
                "duration_seconds": 3,
                "interval_seconds": 1,
                "affects": "everyone",
                "effects": [{"type": "realmforge:damage", "amount": 1}],
            },
            "'enemies' or 'allies'",
        ),
    ],
)
def test_invalid_c3a3_effects_are_rejected(effect: dict, expected: str) -> None:
    with pytest.raises(ValueError, match=expected):
        spell_model(effects=[effect])


def test_inner_effects_count_for_per_level() -> None:
    with pytest.raises(
        ValueError, match=r"nothing grows with the level at max_level 1 \(effects.0.effects.0\)"
    ):
        spell_model(
            effects=[
                {
                    "type": "realmforge:projectile",
                    "speed": 20,
                    "effects": [{"type": "realmforge:damage", "amount": 1, "per_level": 1}],
                }
            ]
        )


@pytest.mark.parametrize(
    ("targeting", "effect", "allowed"),
    [
        (
            "{type: realmforge:self}",
            PROJECTILE,
            "realmforge:enemy or realmforge:ground_point or realmforge:direction",
        ),
        (
            "{type: realmforge:direction, range: 8}",
            AREA,
            "realmforge:self or realmforge:enemy or realmforge:ground_point",
        ),
        ("{type: realmforge:enemy, range: 8}", "{type: realmforge:teleport}", "realmforge:ground_point or"),
        ("{type: realmforge:self}", "{type: realmforge:dash, strength: 1}", "realmforge:enemy or"),
        (
            "{type: realmforge:ally, range: 8}",
            "{type: realmforge:knockback, strength: 1}",
            "realmforge:enemy",
        ),
        (
            "{type: realmforge:ground_point, range: 8}",
            "{type: realmforge:damage, amount: 3}",
            "realmforge:enemy",
        ),
        (
            "{type: realmforge:direction, range: 8}",
            "{type: realmforge:apply_effect, effect: minecraft:slowness, duration_seconds: 2}",
            "realmforge:self or realmforge:enemy or realmforge:ally",
        ),
    ],
)
def test_c3a3_effects_fit_the_targeting(repo: RepoPaths, targeting: str, effect: str, allowed: str) -> None:
    spell(
        repo,
        "odd",
        "school: realmforge:arcane\n"
        "scales_with: spell_power\n"
        f"targeting: {targeting}\n"
        "cast: {type: instant}\n"
        f"effects:\n  - {effect}\n",
    )
    assert_problem(repo, f"so the targeting is {allowed}")


@pytest.mark.parametrize(
    ("effect", "path", "expected"),
    [
        (
            "{type: realmforge:projectile, speed: 20, effects: [{type: realmforge:heal, amount: 3}]}",
            "effects.0.effects.0.type",
            "realmforge:heal cannot act on the enemy a projectile hits",
        ),
        (
            "{type: realmforge:area, radius: 3, duration_seconds: 2, interval_seconds: 1, affects: allies, "
            "effects: [{type: realmforge:damage, amount: 3}]}",
            "effects.0.effects.0.type",
            "realmforge:damage cannot act on an area's allies",
        ),
        (
            "{type: realmforge:area, radius: 3, duration_seconds: 2, interval_seconds: 1, "
            "effects: [{type: realmforge:knockback, strength: 1}, {type: realmforge:heal, amount: 1}]}",
            "effects.0.effects.1.type",
            "realmforge:heal cannot act on an area's enemies",
        ),
        (
            "{type: realmforge:projectile, speed: 20, effects: [{type: realmforge:remove_effect, effect: "
            "minecraft:not_an_effect}]}",
            "effects.0.effects.0.effect",
            "minecraft:not_an_effect",
        ),
        (
            "{type: realmforge:projectile, speed: 20, effects: [{type: realmforge:damage, amount: 3, "
            "attack_damage_percent: 50}]}",
            "effects.0.effects.0.attack_damage_percent",
            "the spell scales with spell_power",
        ),
    ],
)
def test_inner_effects_fit_what_they_reach(repo: RepoPaths, effect: str, path: str, expected: str) -> None:
    spell(
        repo,
        "odd",
        "school: realmforge:arcane\n"
        "scales_with: spell_power\n"
        "targeting: {type: realmforge:ground_point, range: 16}\n"
        "cast: {type: instant}\n"
        f"effects:\n  - {effect}\n",
    )
    assert_problem(repo, f"{path}: ")
    assert_problem(repo, expected)


def test_an_area_for_allies_may_heal(repo: RepoPaths) -> None:
    spell(
        repo,
        "sanctuary",
        "school: realmforge:arcane\n"
        "scales_with: spell_power\n"
        "targeting: {type: realmforge:self}\n"
        "cast: {type: instant}\n"
        "effects:\n"
        "  - {type: realmforge:area, radius: 3, duration_seconds: 2, interval_seconds: 1, affects: allies, "
        "effects: [{type: realmforge:heal, amount: 2}, {type: realmforge:remove_effect, effect: minecraft:poison}]}\n",
    )
    content, _ = load_and_validate(repo)
    assert any(entry.model.id == "sanctuary" for entry in content.spells)


def test_a_quake_slams_and_its_area_has_a_grey_ring(repo: RepoPaths) -> None:
    # Astone's Quake (Matěj's playtest, 2026-10-29): a channel her fists beat through, its rings grey.
    spell(
        repo,
        "quake",
        "school: realmforge:physical\n"
        "scales_with: attack_damage\n"
        "targeting: {type: realmforge:self}\n"
        "cast: {type: channel, duration_seconds: 3, interval_seconds: 0.75, animation: slam}\n"
        "npc_only: true\n"
        "effects:\n"
        "  - {type: realmforge:area, radius: 5, duration_seconds: 0.5, interval_seconds: 0.5, ring_color: '#8C8C8C', "
        "effects: [{type: realmforge:damage, amount: 1, attack_damage_percent: 30}]}\n",
    )
    files = generate(repo)
    data = json.loads(files[f"{SPELL_DATA}/quake.json"])
    assert data["cast"]["animation"] == "slam"
    assert data["effects"][0]["ring_color"] == "#8C8C8C"
    expectations = files[EXPECTATIONS].decode()
    quake = expectations[expectations.index('"realmforge:quake"') :]
    assert '                    "slam",\n' in quake[: quake.index("List.of(")]
    assert 'Map.of("affects", "enemies", "ring_color", "#8C8C8C")' in quake


@pytest.mark.parametrize(
    ("cast", "expected"),
    [
        ({"type": "instant", "animation": "slam"}, "Extra inputs are not permitted"),
        (
            {"type": "cast_time", "duration_seconds": 1, "animation": "dance"},
            "Input should be 'raise' or 'slam'",
        ),
    ],
)
def test_only_a_timed_cast_has_an_animation(cast: dict, expected: str) -> None:
    with pytest.raises(ValueError, match=re.escape(expected)):
        spell_model(cast=cast)


def test_status_effects_must_exist(repo: RepoPaths) -> None:
    spell(
        repo,
        "hex",
        "school: realmforge:arcane\n"
        "scales_with: spell_power\n"
        "targeting: {type: realmforge:enemy, range: 16}\n"
        "cast: {type: instant}\n"
        "effects:\n"
        "  - {type: realmforge:apply_effect, effect: minecraft:slowness_x, duration_seconds: 2}\n",
    )
    assert_problem(repo, "effects.0.effect: ")
    assert_problem(repo, "minecraft:slowness_x")


@pytest.mark.parametrize(
    ("status", "expected"),
    [
        ("minecraft:instant_health", "use realmforge:heal instead"),
        ("minecraft:instant_damage", "use realmforge:damage instead"),
        ("minecraft:saturation", "a spell does not feed"),
    ],
)
def test_apply_effect_refuses_instant_status_effects(repo: RepoPaths, status: str, expected: str) -> None:
    spell(
        repo,
        "hex",
        "school: realmforge:arcane\n"
        "scales_with: spell_power\n"
        "targeting: {type: realmforge:enemy, range: 16}\n"
        "cast: {type: instant}\n"
        "effects:\n"
        "  - {type: realmforge:projectile, speed: 20, size: 0.5, lifetime_seconds: 2, effects: "
        f"[{{type: realmforge:apply_effect, effect: {status}, duration_seconds: 2}}]}}\n",
    )
    assert_problem(repo, f"effects.0.effects.0.effect: {status} is instant")
    assert_problem(repo, expected)


def test_remove_effect_may_name_an_instant_status_effect(repo: RepoPaths) -> None:
    spell(
        repo,
        "cleanse",
        "school: realmforge:arcane\n"
        "scales_with: spell_power\n"
        "targeting: {type: realmforge:self}\n"
        "cast: {type: instant}\n"
        "effects:\n"
        "  - {type: realmforge:remove_effect, effect: minecraft:instant_damage}\n",
    )
    content, _ = load_and_validate(repo)
    assert any(entry.model.id == "cleanse" for entry in content.spells)


def test_spell_entities_are_in_game_units(repo: RepoPaths) -> None:
    files = generate(repo)
    missile = json.loads(files[f"{SPELL_DATA}/test_missile.json"])
    assert missile["effects"] == [
        {
            "type": "realmforge:projectile",
            "speed": 20.0,
            "size": 0.5,
            "lifetime_ticks": 40,
            "effects": [
                {"amount": 5.0, "per_level": 0, "type": "realmforge:damage"},
                {
                    "type": "realmforge:apply_effect",
                    "effect": "minecraft:slowness",
                    "duration_ticks": 60,
                    "amplifier": 0,
                    "stronger_at_levels": [],
                },
            ],
        }
    ]
    scorch = json.loads(files[f"{SPELL_DATA}/test_scorch.json"])
    assert scorch["targeting"] == {"type": "realmforge:ground_point", "range": 16.0}
    assert scorch["effects"][0] | {"effects": None} == {
        "type": "realmforge:area",
        "radius": 2.0,
        "duration_ticks": 60,
        "interval_ticks": 20,
        "affects": "enemies",
        "effects": None,
    }
    blink = json.loads(files[f"{SPELL_DATA}/test_blink.json"])
    assert blink["targeting"] == {"type": "realmforge:direction", "range": 4.0}
    assert blink["effects"] == [{"type": "realmforge:teleport"}]


def test_a_projectile_without_a_lifetime_has_none_in_its_data(repo: RepoPaths) -> None:
    # Spell aiming (RLM-344): without lifetime_seconds a projectile flies until it hits something or leaves the world.
    files = generate(repo)
    forward = json.loads(files[f"{SPELL_DATA}/test_forward_missile.json"])["effects"][0]
    assert forward["type"] == "realmforge:projectile"
    assert "lifetime_ticks" not in forward
    expectations = files[EXPECTATIONS].decode()
    assert (
        'new EffectExpectation("realmforge:projectile", Map.of("size", 0.5, "speed", 20.0), ' in expectations
    )


def test_expectations_of_spell_entities_hold_their_effects(repo: RepoPaths) -> None:
    expectations = generate(repo)[EXPECTATIONS].decode()
    assert (
        'new EffectExpectation("realmforge:projectile", Map.of("lifetime_ticks", 40.0, "size", 0.5, "speed", 20.0), '
        "Map.of(), List.of(\n"
    ) in expectations
    assert (
        'new EffectExpectation("realmforge:apply_effect", Map.of("amplifier", 0.0, "duration_ticks", 60.0), '
        'Map.of("effect", "minecraft:slowness"), List.of())'
    ) in expectations
    assert 'Map.of("affects", "allies")' in expectations
    assert 'new EffectExpectation("realmforge:teleport", Map.of(), Map.of(), List.of())' in expectations


# C3a-4 (RLM-212): the NPC targeting types

NPC_TARGETING = ["realmforge:current_target", "realmforge:random_player", "realmforge:farthest_player"]


@pytest.mark.parametrize("targeting", NPC_TARGETING)
def test_npc_targeting_is_for_npc_spells_only(targeting: str) -> None:
    with pytest.raises(ValueError, match="so the spell is npc_only: true"):
        spell_model(targeting={"type": targeting, "range": 16})
    npc_spell = spell_model(targeting={"type": targeting, "range": 16}, npc_only=True)
    assert npc_spell.targeting.type == targeting


@pytest.mark.parametrize("targeting", NPC_TARGETING)
def test_npc_targeting_needs_a_range_of_at_most_128(targeting: str) -> None:
    with pytest.raises(ValueError, match="range"):
        spell_model(targeting={"type": targeting}, npc_only=True)
    with pytest.raises(ValueError, match="range"):
        spell_model(targeting={"type": targeting, "range": 129}, npc_only=True)


@pytest.mark.parametrize("targeting", NPC_TARGETING)
@pytest.mark.parametrize(
    "effect",
    [
        "{type: realmforge:damage, amount: 3}",
        "{type: realmforge:knockback, strength: 1}",
        "{type: realmforge:apply_effect, effect: minecraft:slowness, duration_seconds: 2}",
        "{type: realmforge:projectile, speed: 20, size: 0.5, lifetime_seconds: 2, effects: "
        "[{type: realmforge:damage, amount: 3}]}",
        "{type: realmforge:dash, strength: 1}",
    ],
)
def test_npc_targeting_goes_where_enemy_does(repo: RepoPaths, targeting: str, effect: str) -> None:
    spell(
        repo,
        "boss_bolt",
        "school: realmforge:arcane\n"
        "scales_with: spell_power\n"
        f"targeting: {{type: {targeting}, range: 16}}\n"
        "cast: {type: instant}\n"
        "npc_only: true\n"
        f"effects:\n  - {effect}\n",
    )
    content, _ = load_and_validate(repo)
    assert any(entry.model.id == "boss_bolt" for entry in content.spells)


@pytest.mark.parametrize("targeting", NPC_TARGETING)
def test_npc_targeting_heals_nobody(repo: RepoPaths, targeting: str) -> None:
    spell(
        repo,
        "boss_mend",
        "school: realmforge:arcane\n"
        "scales_with: spell_power\n"
        f"targeting: {{type: {targeting}, range: 16}}\n"
        "cast: {type: instant}\n"
        "npc_only: true\n"
        "effects:\n  - {type: realmforge:heal, amount: 3}\n",
    )
    assert_problem(repo, f"so the targeting is realmforge:self or realmforge:ally, not {targeting}")


def test_npc_targeting_data(repo: RepoPaths) -> None:
    data = json.loads(generate(repo)[f"{SPELL_DATA}/test_npc_far_bolt.json"])
    assert data["targeting"] == {"type": "realmforge:farthest_player", "range": 16.0}
    assert data["npc_only"] is True


# Names, descriptions and their placeholders (C3b, RLM-25)


def test_a_spell_names_itself_and_says_what_it_does() -> None:
    for missing in ("name", "description"):
        data = {
            "id": "fireball",
            "name": {"en_us": "Fireball"},
            "description": {"en_us": "A ball of fire."},
            "school": "realmforge:fire",
            "scales_with": "spell_power",
            "targeting": {"type": "realmforge:enemy", "range": 24},
            "cast": {"type": "instant"},
            "effects": [{"type": "realmforge:damage", "amount": 12}],
        }
        del data[missing]
        with pytest.raises(ValueError, match="Field required"):
            SpellDef.model_validate(data)


@pytest.mark.parametrize(
    ("description", "expected"),
    [
        ("Deals {dmg} damage.", "unknown placeholder {dmg}"),
        ("Deals {Damage} damage.", "unknown placeholder {Damage}"),
        ("Deals {damage damage.", "a brace that opens or closes no {placeholder}"),
        ("Deals damage} damage.", "a brace that opens or closes no {placeholder}"),
        ("Heals for {heal}.", "{heal} needs a realmforge:heal effect"),
        ("Slows for {duration} sec.", "{duration} needs a realmforge:apply_effect effect"),
        ("Burns for {periodic_total}.", "{periodic_total} needs a realmforge:apply_effect effect"),
        ("A ring of {radius} blocks.", "{radius} needs a realmforge:area effect"),
    ],
)
def test_placeholders_must_be_known_and_have_their_effect(description: str, expected: str) -> None:
    with pytest.raises(ValueError, match=re.escape(expected)):
        spell_model(description={"en_us": description})


def test_placeholders_read_the_first_effect_of_their_type() -> None:
    model = spell_model(
        description={
            "en_us": "Deals {damage}, then {damage} again, in a ring of {radius}, for {duration} sec."
        },
        targeting={"type": "realmforge:self"},
        effects=[
            {
                "type": "realmforge:area",
                "radius": 3,
                "duration_seconds": 2,
                "interval_seconds": 1,
                "effects": [
                    {"type": "realmforge:damage", "amount": 4},
                    {
                        "type": "realmforge:apply_effect",
                        "effect": "minecraft:slowness",
                        "duration_seconds": 2,
                    },
                ],
            }
        ],
    )
    assert model.placeholders() == ["damage", "damage", "radius", "duration"]
    assert model.first_effect("realmforge:damage").amount == 4


def test_periodic_placeholders_need_an_effect_with_ticks(repo: RepoPaths) -> None:
    spell(
        repo,
        "chilling_touch",
        "name: {en_us: Chilling Touch}\n"
        'description: {en_us: "Chills for {periodic} a tick."}\n'
        "school: realmforge:frost\n"
        "scales_with: spell_power\n"
        "targeting: {type: realmforge:enemy, range: 8}\n"
        "cast: {type: instant}\n"
        "effects:\n"
        "  - {type: realmforge:apply_effect, effect: minecraft:slowness, duration_seconds: 3}\n",
    )
    assert_problem(repo, "{periodic} reads the first apply_effect's damage or healing over time")


def test_an_effect_with_ticks_lasts_whole_intervals(repo: RepoPaths) -> None:
    spell(
        repo,
        "flicker",
        "school: realmforge:fire\n"
        "scales_with: spell_power\n"
        "targeting: {type: realmforge:enemy, range: 8}\n"
        "cast: {type: instant}\n"
        "effects:\n"
        "  - {type: realmforge:apply_effect, effect: realmforge:test_burning, duration_seconds: 2.5}\n",
    )
    assert_problem(
        repo, "realmforge:test_burning ticks every 1 s, so it lasts a whole number of its intervals"
    )


BURNING_4S = "{type: realmforge:apply_effect, effect: realmforge:test_burning, duration_seconds: 4}"


def reapplying_spell(repo: RepoPaths, cast: str, effect: str) -> None:
    # A player's channel ticks every 0.5 s (RLM-518), so a channel of another interval is a mob's.
    mob = "type: channel" in cast and "interval_seconds: 0.5}" not in cast
    spell(
        repo,
        "smoulder",
        "school: realmforge:fire\n"
        "scales_with: spell_power\n"
        "targeting: {type: realmforge:enemy, range: 16}\n"
        f"cast: {cast}\n" + ("npc_only: true\n" if mob else "") + f"effects:\n  - {effect}\n",
    )


@pytest.mark.parametrize(
    ("cast", "effect", "path", "what", "interval"),
    [
        (
            "{type: instant}",
            "{type: realmforge:area, radius: 3, duration_seconds: 4, interval_seconds: 0.5, "
            f"effects: [{BURNING_4S}]}}",
            "effects.0.effects.0.effect",
            "the area",
            "0.5",
        ),
        (
            "{type: channel, duration_seconds: 3, interval_seconds: 0.5}",
            BURNING_4S,
            "effects.0.effect",
            "the channel",
            "0.5",
        ),
        (
            "{type: channel, duration_seconds: 3, interval_seconds: 0.5}",
            f"{{type: realmforge:projectile, speed: 20, effects: [{BURNING_4S}]}}",
            "effects.0.effects.0.effect",
            "the channel",
            "0.5",
        ),
        (
            "{type: channel, duration_seconds: 2, interval_seconds: 0.25}",
            "{type: realmforge:area, radius: 3, duration_seconds: 4, interval_seconds: 1, "
            f"effects: [{BURNING_4S}]}}",
            "effects.0.effects.0.effect",
            "the channel",
            "0.25",
        ),
    ],
)
def test_an_area_or_channel_does_not_reapply_an_effect_faster_than_it_ticks(
    repo: RepoPaths, cast: str, effect: str, path: str, what: str, interval: str
) -> None:
    reapplying_spell(repo, cast, effect)
    assert_problem(
        repo,
        f"{path}: realmforge:test_burning ticks every 1 s and {what} applies it again every {interval} s: each "
        f"application resets its duration before it ticks, so it never ticks while {what} keeps applying it; give "
        f"{what} an interval of at least 1 s",
    )


@pytest.mark.parametrize(
    ("cast", "effect"),
    [
        (
            "{type: instant}",
            f"{{type: realmforge:area, radius: 3, duration_seconds: 4, interval_seconds: 1, effects: [{BURNING_4S}]}}",
        ),
        (
            "{type: instant}",
            f"{{type: realmforge:area, radius: 3, duration_seconds: 4, interval_seconds: 2, effects: [{BURNING_4S}]}}",
        ),
        ("{type: channel, duration_seconds: 3, interval_seconds: 1}", BURNING_4S),
        ("{type: channel, duration_seconds: 4, interval_seconds: 2}", BURNING_4S),
        (
            "{type: channel, duration_seconds: 3, interval_seconds: 1}",
            f"{{type: realmforge:projectile, speed: 20, effects: [{BURNING_4S}]}}",
        ),
        # A projectile of a spell that is not a channel applies it once, and a vanilla effect has no ticks of ours.
        ("{type: instant}", f"{{type: realmforge:projectile, speed: 20, effects: [{BURNING_4S}]}}"),
        (
            "{type: channel, duration_seconds: 3, interval_seconds: 0.5}",
            "{type: realmforge:apply_effect, effect: minecraft:slowness, duration_seconds: 4}",
        ),
    ],
)
def test_an_area_or_channel_may_reapply_an_effect_as_often_as_it_ticks(
    repo: RepoPaths, cast: str, effect: str
) -> None:
    reapplying_spell(repo, cast, effect)
    content, _ = load_and_validate(repo)
    assert any(entry.model.id == "smoulder" for entry in content.spells)


def test_our_effects_may_be_applied(repo: RepoPaths) -> None:
    content, _ = load_and_validate(repo)
    ignite = next(entry.model for entry in content.spells if entry.model.id == "test_ignite")
    assert ignite.first_effect("realmforge:apply_effect").effect == "realmforge:test_burning"


STONE_CAST = (
    "school: realmforge:arcane\n"
    "scales_with: spell_power\n"
    "targeting: {type: realmforge:current_target, range: 8}\n"
    "npc_only: true\n"
)
STONE_PROJECTILE = (
    "effects:\n"
    "  - type: realmforge:projectile\n"
    "    speed: 16\n"
    "    spares: realmforge:stone_creatures\n"
    "    impact: {sound: realmforge:spell.stoneform.impact, particles: realmforge:vaultstone}\n"
    "    appearance: {model: orb, color: '#808080'}\n"
    "    effects:\n"
    "      - {type: realmforge:damage, amount: 4}\n"
)


def test_a_cast_keeps_its_target_in_sight_and_in_range(repo: RepoPaths) -> None:
    spell(
        repo,
        "test_stone",
        STONE_CAST
        + "cast: {type: cast_time, duration_seconds: 1.5, keep_sight: true, keep_range_percent: 50, "
        + "sound: realmforge:spell.stoneform.form, forms_projectile: true}\n"
        + STONE_PROJECTILE,
    )
    files = generate(repo)
    data = json.loads(files[f"{SPELL_DATA}/test_stone.json"])
    assert data["cast"] == {
        "castable_while_moving": False,
        "lock_on": False,
        "keep_sight": True,
        "keep_range": 0.5,
        "sound": "realmforge:spell.stoneform.form",
        "type": "cast_time",
        "duration_ticks": 30,
        "forms_projectile": True,
    }
    projectile = data["effects"][0]
    assert projectile["spares"] == "realmforge:stone_creatures"
    assert projectile["impact"] == {
        "sound": "realmforge:spell.stoneform.impact",
        "particles": "realmforge:vaultstone",
    }
    # The orb's placeholder texture, where spell/SpellVisuals looks for it.
    texture = files["mod/src/generated/resources/assets/realmforge/textures/entity/spell/test_stone.png"]
    assert texture.startswith(b"\x89PNG")


@pytest.mark.parametrize(
    ("cast", "expected"),
    [
        (
            "{type: cast_time, duration_seconds: 1, lock_on: true, keep_sight: true}",
            "a spell that locks on checks neither sight nor range",
        ),
        ("{type: cast_time, duration_seconds: 1, keep_range_percent: 0}", "greater than 0"),
        (
            "{type: channel, duration_seconds: 2, interval_seconds: 1, forms_projectile: true}",
            "forms_projectile",
        ),
    ],
)
def test_invalid_kept_casts_are_rejected(repo: RepoPaths, cast: str, expected: str) -> None:
    spell(repo, "test_stone", STONE_CAST + f"cast: {cast}\n" + STONE_PROJECTILE)
    assert_problem(repo, expected)


def test_a_formed_projectile_is_the_first_effect_with_an_appearance(repo: RepoPaths) -> None:
    spell(
        repo,
        "test_stone",
        STONE_CAST
        + "cast: {type: cast_time, duration_seconds: 1, forms_projectile: true}\n"
        + STONE_PROJECTILE.replace("    appearance: {model: orb, color: '#808080'}\n", ""),
    )
    assert_problem(repo, "forms_projectile: the spell's first effect is a projectile with an appearance")


def test_a_projectile_spares_a_tag_our_mobs_fill(repo: RepoPaths) -> None:
    spell(
        repo,
        "test_stone",
        STONE_CAST
        + "cast: {type: instant}\n"
        + STONE_PROJECTILE.replace("realmforge:stone_creatures", "realmforge:glass_creatures")
        .replace("realmforge:spell.stoneform.impact", "realmforge:spell.stoneform.crash")
        .replace("particles: realmforge:vaultstone", "particles: realmforge:vaultstones"),
    )
    problems = problems_for(repo)
    assert any(
        "effects.0.spares: no mob in content/mobs/ is tagged realmforge:glass_creatures" in p
        for p in problems
    )
    assert any("effects.0.impact.sound: unknown id 'realmforge:spell.stoneform.crash'" in p for p in problems)
    assert any("effects.0.impact.particles: unknown id 'realmforge:vaultstones'" in p for p in problems), (
        problems
    )


def test_a_cast_sound_must_exist(repo: RepoPaths) -> None:
    spell(
        repo,
        "test_stone",
        STONE_CAST
        + "cast: {type: cast_time, duration_seconds: 1, sound: realmforge:spell.nothing}\n"
        + STONE_PROJECTILE,
    )
    assert_problem(repo, "cast.sound: unknown id 'realmforge:spell.nothing'")


# RLM-541: passive spells, learned with ranks and never cast.

PASSIVE = (
    "max_level: 3\n"
    "passive:\n"
    "  spell_damage:\n"
    "    - {spells: [realmforge:frostbolt], damage_percent: 5, damage_per_level_percent: 5}\n"
)


def test_a_passive_spell_names_no_cast_and_reads_its_rank(repo: RepoPaths) -> None:
    spell(
        repo, "keen_frost", "name: {en_us: Keen Frost}\ndescription: {en_us: '+{spell_damage}%'}\n" + PASSIVE
    )
    content, _ = load_and_validate(repo)
    passive = next(entry.model for entry in content.spells if entry.model.id == "keen_frost")
    assert passive.is_passive
    assert passive.passive is not None and passive.passive.spell_damage[0].damage_at(3) == 15
    data = json.loads(generate(repo)[f"{SPELL_DATA}/keen_frost.json"])
    assert data["effects"] == [] and data["targeting"] == {"type": "realmforge:self"}
    assert data["passive"]["spell_damage"][0] == {
        "spells": ["realmforge:frostbolt"],
        "damage": 0.05,
        "damage_per_level": 0.05,
    }


@pytest.mark.parametrize(
    ("body", "message"),
    [
        (PASSIVE + "cooldown_seconds: 3\n", "a passive spell is never cast, so it names no cooldown_seconds"),
        (
            PASSIVE.replace("realmforge:frostbolt", "realmforge:frost_bolt"),
            "passive.spell_damage.0.spells.0: unknown id 'realmforge:frost_bolt'",
        ),
        (
            PASSIVE.replace("realmforge:frostbolt", "realmforge:ice_barrier"),
            "realmforge:ice_barrier deals no damage, so more damage does nothing",
        ),
        (
            "max_level: 3\npassive:\n  crit_chance: {chance_percent: 10, against: realmforge:chilled}\n",
            "passive.crit_chance.against: unknown id 'realmforge:chilled'",
        ),
        (
            "passive:\n  while_wielding: realmforge:greatswords\n"
            "  modifiers: [{attribute: minecraft:attack_damage, operation: add_multiplied_base, amount_percent: 4}]\n",
            "unknown item tag 'realmforge:greatswords'",
        ),
        (
            "passive:\n  modifiers: [{attribute: minecraft:attack_damage, operation: add_multiplied_total, "
            "amount_percent: 4}]\n",
            "use add_multiplied_base, not add_multiplied_total",
        ),
        (
            "name: {en_us: Keen Frost}\ndescription: {en_us: '{damage}'}\n" + PASSIVE,
            "unknown placeholder {damage} for a passive spell",
        ),
        (
            "name: {en_us: Keen Frost}\ndescription: {en_us: '{crit_chance}'}\n" + PASSIVE,
            "{crit_chance} reads a part of the passive it does not have",
        ),
    ],
)
def test_invalid_passive_spells_are_rejected(repo: RepoPaths, body: str, message: str) -> None:
    spell(repo, "keen_frost", body)
    assert_problem(repo, message)


def test_an_active_spell_still_names_its_cast(repo: RepoPaths) -> None:
    spell(repo, "fireball", FIREBALL.replace("cast: {type: instant}\n", ""))
    assert_problem(repo, "an active spell names cast; a passive one names passive instead")


# RLM-539, RLM-540, RLM-399: damage over time, windows, procs, conditions, melee shapes, swings and splash

BLEED = (
    "school: realmforge:physical\n"
    "scales_with: attack_damage\n"
    "targeting: {type: realmforge:enemy, range: 5}\n"
    "cast: {type: instant}\n"
    "effects:\n"
    "  - {type: realmforge:damage_over_time, effect: realmforge:bleeding, amount: 12, duration_seconds: 6}\n"
)

CLEAVE = (
    "school: realmforge:physical\n"
    "scales_with: attack_damage\n"
    "targeting: {type: realmforge:self}\n"
    "cast: {type: instant, animation_seconds: 1, hit_at_percent: 25}\n"
    "requires: [melee_weapon]\n"
    "effects:\n"
    "  - type: realmforge:melee\n"
    "    shape: box\n"
    "    reach: 1\n"
    "    width: 2\n"
    "    height: 2\n"
    "    effects:\n"
    "      - {type: realmforge:damage, amount: 3, attack_damage_percent: 80}\n"
)


def test_a_damage_over_time_keeps_its_damage_in_stacks(repo: RepoPaths) -> None:
    spell(repo, "test_bleed", BLEED)
    data = json.loads(generate(repo)[f"{SPELL_DATA}/test_bleed.json"])
    assert data["effects"][0] == {
        "type": "realmforge:damage_over_time",
        "amount": 12,
        "per_level": 0,
        "effect": "realmforge:bleeding",
        "duration_ticks": 120,
    }


def test_a_damage_over_time_needs_an_effect_with_stacks(repo: RepoPaths) -> None:
    spell(repo, "test_bleed", BLEED.replace("realmforge:bleeding", "realmforge:burning"))
    assert_problem(repo, "realmforge:burning is no effect of content/effects/ with stacks")


def test_a_damage_over_time_lasts_whole_intervals(repo: RepoPaths) -> None:
    spell(repo, "test_bleed", BLEED.replace("duration_seconds: 6", "duration_seconds: 6.25"))
    assert_problem(repo, "realmforge:bleeding ticks every 0.5 s")


def test_a_damage_over_time_scales_with_its_ticks_school(repo: RepoPaths) -> None:
    spell(
        repo,
        "test_bleed",
        BLEED.replace("school: realmforge:physical", "school: realmforge:frost").replace(
            "scales_with: attack_damage", "scales_with: spell_power"
        ),
    )
    assert_problem(
        repo, "deals realmforge:physical damage, a physical school, so the spell scales with attack_damage"
    )


def test_a_swing_and_its_melee_shape_are_in_game_units(repo: RepoPaths) -> None:
    spell(repo, "test_swing", CLEAVE)
    data = json.loads(generate(repo)[f"{SPELL_DATA}/test_swing.json"])
    assert data["cast"] == {"type": "instant", "animation_ticks": 20, "hit_at": 0.25}
    assert data["effects"][0]["type"] == "realmforge:melee"
    assert data["effects"][0]["shape"] == "box"
    assert data["effects"][0]["width"] == 2


@pytest.mark.parametrize(
    ("change", "expected"),
    [
        (("width: 2", "angle_degrees: 120"), "a box names width and no angle_degrees"),
        (("shape: box", "shape: sector"), "a sector names angle_degrees and no width"),
        (("reach: 1", "reach: 7"), "less than or equal to 6"),
        (
            ("animation_seconds: 1, ", ""),
            "hit_at_percent is a moment of the animation: name animation_seconds too",
        ),
    ],
)
def test_invalid_swings_are_rejected(repo: RepoPaths, change: tuple[str, str], expected: str) -> None:
    spell(repo, "test_swing", CLEAVE.replace(*change))
    assert_problem(repo, expected)


def test_a_melee_shape_lies_in_front_of_the_caster_itself(repo: RepoPaths) -> None:
    spell(repo, "test_swing", CLEAVE.replace("{type: realmforge:self}", "{type: realmforge:enemy, range: 4}"))
    assert_problem(repo, "realmforge:melee acts on the spell's targets, so the targeting is realmforge:self")


def test_a_melee_shapes_damage_needs_a_melee_weapon(repo: RepoPaths) -> None:
    spell(repo, "test_swing", CLEAVE.replace("requires: [melee_weapon]\n", ""))
    assert_problem(repo, "needs a melee weapon in the main hand")


def test_a_splash_is_in_game_units(repo: RepoPaths) -> None:
    spell(
        repo,
        "test_splash",
        "school: realmforge:frost\nscales_with: spell_power\ntargeting: {type: realmforge:direction, range: 40}\n"
        "cast: {type: instant}\neffects:\n  - type: realmforge:projectile\n    speed: 30\n"
        "    splash: {radius: 1, damage_percent: 50}\n    effects:\n      - {type: realmforge:damage, amount: 8}\n",
    )
    data = json.loads(generate(repo)[f"{SPELL_DATA}/test_splash.json"])
    assert data["effects"][0]["splash"] == {"radius": 1, "damage": 0.5}


@pytest.mark.parametrize(
    ("fields", "expected"),
    [
        ({"window": {"duration_seconds": 0}}, "duration_seconds is more than 0 and at most 60"),
        (
            {"window": {"duration_seconds": 5, "opened_by": ["block", "block"]}},
            "opened_by names each trigger once",
        ),
        ({"target_health_below_percent": 120}, "less than or equal to 100"),
        (
            {
                "cost": {
                    "resource": "realmforge:mana",
                    "amount": 5,
                    "extra": {"max": 0, "damage_per_point_percent": 3},
                }
            },
            "greater than 0",
        ),
    ],
)
def test_invalid_conditions_are_rejected(fields: dict, expected: str) -> None:
    with pytest.raises(pydantic.ValidationError) as excinfo:
        spell_model(**fields)
    assert expected in str(excinfo.value)


def test_windows_and_conditions_are_in_game_units(repo: RepoPaths) -> None:
    spell(
        repo,
        "test_finisher",
        FIREBALL.replace(
            "cast: {type: instant}\n",
            "cast: {type: instant}\n"
            "window: {duration_seconds: 5, opened_by: [block]}\ntarget_health_below_percent: 20\n"
            "cost: {resource: realmforge:mana, amount: 5, extra: {max: 30, damage_per_point_percent: 3}}\n",
        ),
    )
    data = json.loads(generate(repo)[f"{SPELL_DATA}/test_finisher.json"])
    assert data["window"] == {"duration_ticks": 100, "opened_by": ["block"]}
    assert data["target_health_below"] == 0.2
    assert data["cost"]["extra"] == {"max": 30, "damage_per_point": 0.03}


def test_a_health_condition_needs_an_enemy_target(repo: RepoPaths) -> None:
    spell(
        repo,
        "test_finisher",
        "school: realmforge:arcane\nscales_with: spell_power\ntargeting: {type: realmforge:self}\n"
        "cast: {type: instant}\ntarget_health_below_percent: 20\neffects:\n"
        "  - {type: realmforge:apply_effect, effect: minecraft:speed, duration_seconds: 5}\n",
    )
    assert_problem(repo, "a target's health is read from an enemy target")


def test_extra_resource_raises_damage_so_the_spell_deals_some(repo: RepoPaths) -> None:
    spell(
        repo,
        "test_finisher",
        "school: realmforge:arcane\nscales_with: spell_power\ntargeting: {type: realmforge:self}\n"
        "cast: {type: instant}\ncost: {resource: realmforge:mana, amount: 5, extra: {max: 30, "
        "damage_per_point_percent: 3}}\neffects:\n"
        "  - {type: realmforge:apply_effect, effect: minecraft:speed, duration_seconds: 5}\n",
    )
    assert_problem(repo, "extra resource spent raises the spell's damage, and it deals none")


PASSIVE_PROC = (
    "school: realmforge:frost\n"
    "passive:\n"
    "  procs:\n"
    "    - trigger: applies_effect\n"
    "      effect: minecraft:slowness\n"
    "      chance_percent: 10\n"
    "      chance_per_level_percent: 5\n"
    "      cooldown_seconds: 3\n"
    "      apply_effect: {effect: realmforge:frozen, duration_seconds: 2}\n"
    "max_level: 3\n"
)


def test_a_passives_proc_is_in_game_units(repo: RepoPaths) -> None:
    spell(repo, "test_bite", PASSIVE_PROC)
    data = json.loads(generate(repo)[f"{SPELL_DATA}/test_bite.json"])
    assert data["passive"]["procs"] == [
        {
            "trigger": "applies_effect",
            "effect": "minecraft:slowness",
            "chance": 0.1,
            "chance_per_level": 0.05,
            "cooldown_ticks": 60,
            "apply_effect": {"effect": "realmforge:frozen", "duration_ticks": 40},
        }
    ]


@pytest.mark.parametrize(
    ("change", "expected"),
    [
        (
            ("      apply_effect: {effect: realmforge:frozen, duration_seconds: 2}\n", ""),
            "a proc does one thing: apply_effect, cast or open_window",
        ),
        (("      effect: minecraft:slowness\n", ""), "a applies_effect proc names the effect it watches"),
        (("trigger: applies_effect", "trigger: crit"), "only an applies_effect or tick proc names an effect"),
        (("realmforge:frozen", "realmforge:no_such_effect"), "passive.procs.0.apply_effect.effect"),
    ],
)
def test_invalid_procs_are_rejected(repo: RepoPaths, change: tuple[str, str], expected: str) -> None:
    spell(repo, "test_bite", PASSIVE_PROC.replace(*change))
    assert_problem(repo, expected)


def test_a_proc_opens_only_a_spells_window(repo: RepoPaths) -> None:
    spell(
        repo,
        "test_bite",
        PASSIVE_PROC.replace(
            "      apply_effect: {effect: realmforge:frozen, duration_seconds: 2}\n",
            "      open_window: realmforge:test_bolt\n",
        ),
    )
    assert_problem(repo, "realmforge:test_bolt has no window to open")
