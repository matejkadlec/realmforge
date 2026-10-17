"""Spells (content/spells/): validation and generation (architecture 6.4, 11; C3a-1, RLM-209; C3a-2, RLM-210)."""

from __future__ import annotations

import json

import pytest

from realmforge_tools.generate import generate_all
from realmforge_tools.problems import ValidationFailed
from realmforge_tools.project import RepoPaths
from realmforge_tools.schema.spell import SpellDef
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


def spell(repo: RepoPaths, name: str, body: str) -> None:
    write(repo.content / "spells" / f"{name}.yaml", f"id: {name}\n{body}")


def spell_model(**fields: object) -> SpellDef:
    data = {
        "id": "fireball",
        "school": "realmforge:fire",
        "scales_with": "spell_power",
        "targeting": {"type": "realmforge:enemy", "range": 24},
        "cast": {"type": "instant"},
        "effects": [{"type": "realmforge:damage", "amount": 12}],
        **fields,
    }
    return SpellDef.model_validate(data)


# The repository's content


def test_repository_spells_are_the_engine_tests() -> None:
    content, universe = load_and_validate(RepoPaths())
    ids = {entry.model.id for entry in content.spells}
    assert ids == {
        "test_blink",
        "test_bolt",
        "test_charge",
        "test_chill",
        "test_cleanse",
        "test_drain",
        "test_forward_missile",
        "test_homing_bolt",
        "test_mend",
        "test_mending_ring",
        "test_missile",
        "test_npc_bolt",
        "test_scorch",
        "test_shove",
        "test_slow_bolt",
        "test_strike",
        "test_sure_crit",
    }
    assert universe.contains("realmforge:spell", "realmforge:test_bolt")


# Schema


@pytest.mark.parametrize(
    ("fields", "expected"),
    [
        ({"effects": []}, "at least 1 item"),
        ({"effects": [{"type": "realmforge:fly", "amount": 1}]}, "does not match any of the expected tags"),
        ({"effects": [{"type": "realmforge:damage", "amount": -1}]}, "greater than or equal to 0"),
        (
            {"effects": [{"type": "realmforge:damage", "amount": 1, "per_level": 1}]},
            "per_level never applies with max_level 1 \\(effects.0\\)",
        ),
        (
            {"cost": {"resource": "realmforge:mana", "amount": 5, "per_level": 1}},
            "per_level never applies with max_level 1 \\(cost\\)",
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
    assert (model.cooldown_seconds, model.global_cooldown_seconds, model.max_level) == (0, 1.0, 1)
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


@pytest.mark.parametrize(
    ("targeting", "effect", "allowed"),
    [
        ("{type: realmforge:self}", "{type: realmforge:damage, amount: 3}", "realmforge:enemy"),
        ("{type: realmforge:ally, range: 8}", "{type: realmforge:damage, amount: 3}", "realmforge:enemy"),
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
    write(repo.content / "spells" / "fireball.yaml", "id: fire_ball\n" + FIREBALL)
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
        "school": "realmforge:fire",
        "scales_with": "spell_power",
        "targeting": {"type": "realmforge:enemy", "range": 24.0},
        "cast": {"type": "instant"},
        "cost": {"amount": 20.0, "per_level": 0, "resource": "realmforge:mana"},
        "cooldown_ticks": 160,
        "global_cooldown_ticks": 20,
        "max_level": 2,
        "crit": {"can_crit": True, "multiplier": 2.0, "bonus_chance": 0.1},
        "npc_only": False,
        "effects": [{"amount": 12.0, "per_level": 3.0, "type": "realmforge:damage"}],
    }


def test_timed_casts_are_in_ticks(repo: RepoPaths) -> None:
    files = generate(repo)
    assert json.loads(files[f"{SPELL_DATA}/test_slow_bolt.json"])["cast"] == {
        "castable_while_moving": False,
        "lock_on": False,
        "type": "cast_time",
        "duration_ticks": 40,
    }
    assert json.loads(files[f"{SPELL_DATA}/test_drain.json"])["cast"] == {
        "castable_while_moving": False,
        "lock_on": False,
        "type": "channel",
        "duration_ticks": 60,
        "interval_ticks": 20,
    }
    assert json.loads(files[f"{SPELL_DATA}/test_homing_bolt.json"])["cast"] == {
        "castable_while_moving": True,
        "lock_on": True,
        "type": "cast_time",
        "duration_ticks": 20,
    }


def test_attack_damage_is_a_fraction(repo: RepoPaths) -> None:
    effect = json.loads(generate(repo)[f"{SPELL_DATA}/test_strike.json"])["effects"][0]
    assert effect == {"amount": 2.0, "per_level": 0, "type": "realmforge:damage", "attack_damage": 1.0}


def test_spells_add_no_texts(repo: RepoPaths) -> None:
    lang = json.loads(generate(repo)[LANG])
    assert not [key for key in lang if key.startswith("spell.")]


def test_keys_and_expectations(repo: RepoPaths) -> None:
    files = generate(repo)
    keys = files[KEYS_JAVA].decode()
    assert 'public static final ResourceKey<Spell> TEST_BOLT = key("test_bolt");' in keys
    assert (
        "ALL = List.of(TEST_BLINK, TEST_BOLT, TEST_CHARGE, TEST_CHILL, TEST_CLEANSE, TEST_DRAIN, TEST_FORWARD_MISSILE, "
        "TEST_HOMING_BOLT, TEST_MEND, TEST_MENDING_RING, TEST_MISSILE, TEST_NPC_BOLT, TEST_SCORCH, TEST_SHOVE, "
        "TEST_SLOW_BOLT, TEST_STRIKE, TEST_SURE_CRIT);"
    ) in keys
    expectations = files[EXPECTATIONS].decode()
    assert (
        "            new SpellExpectation(\n"
        '                    "realmforge:test_bolt",\n'
        '                    "realmforge:fire",\n'
        '                    "spell_power",\n'
        '                    "realmforge:enemy",\n'
        "                    16.0,\n"
        '                    "instant",\n'
        "                    0,\n"
        "                    0,\n"
        "                    false,\n"
        "                    false,\n"
        '                    Optional.of("realmforge:mana"),\n'
        "                    10.0,\n"
        "                    5.0,\n"
        "                    60,\n"
        "                    20,\n"
        "                    3,\n"
        "                    true,\n"
        "                    Optional.empty(),\n"
        "                    0.0,\n"
        "                    false,\n"
        "                    List.of(\n"
        '                            new EffectExpectation("realmforge:damage", '
        'Map.of("amount", 6.0, "attack_damage", 0.0, "per_level", 2.0), Map.of(), List.of())))'
    ) in expectations
    assert (
        'new EffectExpectation("realmforge:damage", Map.of("amount", 2.0, "attack_damage", 1.0, "per_level", 0.0)'
    ) in expectations
    assert "Optional.of(2.0),\n                    1.0," in expectations
    channel = expectations[expectations.index('"realmforge:test_drain"') :]
    assert (
        '"channel",\n                    60,\n                    20,\n                    false,\n'
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
    with pytest.raises(ValueError, match=r"per_level never applies with max_level 1 \(effects.0.effects.0\)"):
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
