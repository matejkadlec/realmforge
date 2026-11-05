"""The Frost Mage's "Shatter" tree (RLM-542) in the pipeline: the cone (Cone of Cold), a bonus against a status effect
that grows per rank (Ice Lance), a proc's effect that lasts longer from a rank on (Frostbite), a passive's wider splash
(Improved Frostbolt), their placeholders, the Mage's kit by the tree's rows, D4 counting only active spells, and
an effect that damage ends at a chance (Frozen)."""

from __future__ import annotations

import json

import pytest

from realmforge_tools.project import RepoPaths
from realmforge_tools.validate import load_and_validate
from tests.test_effects import EXPECTATIONS as EFFECT_EXPECTATIONS
from tests.test_effects import REGISTRATIONS, effect
from tests.test_effects import assert_problem as effects_problem
from tests.test_effects import generate as effects_generate
from tests.test_spells import SPELL_DATA, assert_problem, generate, spell

CONE = (
    "school: realmforge:frost\n"
    "scales_with: spell_power\n"
    "targeting: {type: realmforge:self}\n"
    "cast: {type: instant}\n"
    "max_level: 3\n"
    "effects:\n"
    "  - type: realmforge:cone\n"
    "    range: 5\n"
    "    angle_degrees: 60\n"
    "    sound: minecraft:entity.player.hurt_freeze\n"
    "    particles: minecraft:packed_ice\n"
    "    effects:\n"
    "      - {type: realmforge:damage, amount: 5, per_level: 1, spell_power_percent: 60}\n"
    "      - {type: realmforge:apply_effect, effect: minecraft:slowness, duration_seconds: 3}\n"
)
LANCE = (
    "school: realmforge:frost\n"
    "scales_with: spell_power\n"
    "targeting: {type: realmforge:direction, range: 40}\n"
    "cast: {type: instant}\n"
    "max_level: 5\n"
    "effects:\n"
    "  - type: realmforge:projectile\n"
    "    speed: 45\n"
    "    size: 0.3\n"
    "    effects:\n"
    "      - type: realmforge:damage\n"
    "        amount: 3\n"
    "        against_effect: {effect: realmforge:frozen, damage_percent: 100, damage_per_level_percent: 25}\n"
)
BITE = (
    "school: realmforge:frost\n"
    "max_level: 3\n"
    "passive:\n"
    "  procs:\n"
    "    - trigger: applies_effect\n"
    "      effect: minecraft:slowness\n"
    "      chance_percent: 5\n"
    "      chance_per_level_percent: 5\n"
    "      cooldown_seconds: 5\n"
    "      apply_effect:\n"
    "        effect: realmforge:frozen\n"
    "        duration_seconds: 2\n"
    "        from_level: {level: 3, duration_seconds: 3}\n"
)
BREAKS = (
    "category: harmful\n"
    'color: "#A8E4FF"\n'
    "look: ice\n"
    "breaks_on_damage_percent: 50\n"
    "modifiers:\n"
    "  - {attribute: minecraft:movement_speed, operation: add_multiplied_total, amount_percent: -100}\n"
)
WIDER = (
    "school: realmforge:frost\n"
    "max_level: 3\n"
    "passive:\n"
    "  splash_radius:\n"
    "    - {spells: [realmforge:frostbolt], radius: 0.5, from_level: 3}\n"
)


def spell_data(repo: RepoPaths, name: str) -> dict:
    return json.loads(generate(repo)[f"{SPELL_DATA}/{name}.json"])


# The cone


def test_a_cone_is_in_game_units(repo: RepoPaths) -> None:
    spell(repo, "test_cone", CONE)
    effect = spell_data(repo, "test_cone")["effects"][0]
    assert effect["type"] == "realmforge:cone"
    assert (effect["range"], effect["angle_degrees"]) == (5, 60)
    assert effect["particles"] == "minecraft:packed_ice"
    assert effect["effects"][1]["duration_ticks"] == 60


@pytest.mark.parametrize(
    ("change", "expected"),
    [
        (
            ("targeting: {type: realmforge:self}", "targeting: {type: realmforge:enemy, range: 8}"),
            "effects.0.type",
        ),
        (("minecraft:packed_ice", "minecraft:no_such_block"), "effects.0.particles"),
        (("minecraft:entity.player.hurt_freeze", "minecraft:no_such_sound"), "effects.0.sound"),
        (
            (
                "realmforge:apply_effect, effect: minecraft:slowness, duration_seconds: 3}",
                "realmforge:heal, amount: 2}",
            ),
            "realmforge:heal cannot act on the enemies in a cone",
        ),
        (("angle_degrees: 60", "angle_degrees: 200"), "angle_degrees"),
        (("range: 5", "range: 20"), "range"),
    ],
)
def test_invalid_cones_are_rejected(repo: RepoPaths, change: tuple[str, str], expected: str) -> None:
    spell(repo, "test_cone", CONE.replace(*change))
    assert_problem(repo, expected)


def test_radius_reads_a_cones_range(repo: RepoPaths) -> None:
    spell(
        repo,
        "test_cone",
        "name: {en_us: Cone}\ndescription: {en_us: 'Blasts {radius} blocks for {damage}.'}\n" + CONE,
    )
    load_and_validate(repo)


# A bonus against a status effect, per rank


def test_a_bonus_against_an_effect_grows_per_rank(repo: RepoPaths) -> None:
    spell(repo, "test_lance", LANCE)
    damage = spell_data(repo, "test_lance")["effects"][0]["effects"][0]
    assert damage["against_effect"] == {
        "effect": "realmforge:frozen",
        "damage": 1.0,
        "damage_per_level": 0.25,
    }


def test_a_bonus_against_an_effect_stays_at_most_1000_percent(repo: RepoPaths) -> None:
    spell(repo, "test_lance", LANCE.replace("damage_per_level_percent: 25", "damage_per_level_percent: 300"))
    assert_problem(repo, "stays at most 1000 % more damage at max_level 5")


def test_against_damage_needs_an_against_effect(repo: RepoPaths) -> None:
    text = "name: {en_us: Lance}\ndescription: {en_us: 'Strikes for {damage}, {against_damage} if Frozen.'}\n"
    spell(repo, "test_lance", text + LANCE)
    load_and_validate(repo)
    plain = LANCE.replace(
        "        against_effect: {effect: realmforge:frozen, damage_percent: 100, damage_per_level_percent: 25}\n",
        "",
    )
    spell(repo, "test_lance", text + plain)
    assert_problem(repo, "{against_damage} reads the first damage effect's against_effect, which it lacks")


def test_a_bonus_that_grows_needs_more_than_one_rank(repo: RepoPaths) -> None:
    spell(repo, "test_lance", LANCE.replace("max_level: 5", "max_level: 1"))
    assert_problem(repo, "nothing grows with the level at max_level 1")


# A proc's effect that lasts longer from a rank on


def test_a_procs_longer_effect_is_in_game_units(repo: RepoPaths) -> None:
    spell(repo, "test_bite", BITE)
    proc = spell_data(repo, "test_bite")["passive"]["procs"][0]
    assert proc["apply_effect"] == {
        "effect": "realmforge:frozen",
        "duration_ticks": 40,
        "from_level": {"level": 3, "duration_ticks": 60},
    }


@pytest.mark.parametrize(
    ("change", "expected"),
    [
        (("{level: 3, duration_seconds: 3}", "{level: 4, duration_seconds: 3}"), "a rank above max_level 3"),
        (("{level: 3, duration_seconds: 3}", "{level: 1, duration_seconds: 3}"), "from_level.level"),
        (("{level: 3, duration_seconds: 3}", "{level: 3, duration_seconds: 90}"), "at most 60"),
    ],
)
def test_invalid_longer_proc_effects_are_rejected(
    repo: RepoPaths, change: tuple[str, str], expected: str
) -> None:
    spell(repo, "test_bite", BITE.replace(*change))
    assert_problem(repo, expected)


def test_proc_placeholders_read_the_first_proc(repo: RepoPaths) -> None:
    text = (
        "name: {en_us: Bite}\n"
        "description: {en_us: '{proc_chance}% to freeze for {proc_duration} sec, once every {proc_cooldown} sec.'}\n"
    )
    spell(repo, "test_bite", text + BITE)
    load_and_validate(repo)
    spell(repo, "test_bite", text + BITE.replace("      cooldown_seconds: 5\n", ""))
    assert_problem(repo, "{proc_cooldown} reads a part of the passive it does not have")


# A wider splash


def test_a_wider_splash_is_in_game_units(repo: RepoPaths) -> None:
    spell(repo, "test_wider", WIDER)
    assert spell_data(repo, "test_wider")["passive"]["splash_radius"] == [
        {"spells": ["realmforge:frostbolt"], "radius": 0.5, "from_level": 3}
    ]


@pytest.mark.parametrize(
    ("change", "expected"),
    [
        (("from_level: 3", "from_level: 4"), "passive.splash_radius.0.from_level: a rank above max_level 3"),
        (
            ("realmforge:frostbolt", "realmforge:ice_barrier"),
            "realmforge:ice_barrier launches no projectile with a splash, so a wider splash does nothing",
        ),
        (("realmforge:frostbolt", "realmforge:no_such_spell"), "passive.splash_radius.0.spells.0"),
        (("radius: 0.5", "radius: 5"), "radius"),
    ],
)
def test_invalid_wider_splashes_are_rejected(repo: RepoPaths, change: tuple[str, str], expected: str) -> None:
    spell(repo, "test_wider", WIDER.replace(*change))
    assert_problem(repo, expected)


def test_splash_placeholders_need_a_splash_bonus(repo: RepoPaths) -> None:
    text = "name: {en_us: Wider}\ndescription: {en_us: 'From rank {splash_radius_rank}, {splash_radius} blocks more.'}\n"
    spell(repo, "test_wider", text + WIDER)
    load_and_validate(repo)
    spell(repo, "test_bite", text.replace("Wider", "Bite") + BITE)
    assert_problem(repo, "{splash_radius_rank} reads a part of the passive it does not have")


# The Mage's kit (RLM-542)


def test_the_frost_tree_stands_in_its_rows() -> None:
    content, _ = load_and_validate(RepoPaths())
    mage = next(entry.model for entry in content.classes if entry.model.id == "mage")
    frost = next(entry for entry in mage.specializations if entry.id == "frost")
    assert frost.first_spell == "realmforge:frostbolt"
    assert [
        (taught.spell.removeprefix("realmforge:"), taught.level, taught.column) for taught in frost.spells
    ] == [
        ("improved_frostbolt", 1, 1),
        ("frostbolt", 1, 2),
        ("cone_of_cold", 1, 3),
        ("ice_lance", 6, 1),
        ("frost_nova", 6, 2),
        ("frostbite", 6, 3),
        ("shatter", 12, 1),
        ("ice_barrier", 12, 2),
        ("blizzard", 18, 1),
    ]
    assert [entry.id for entry in mage.specializations if not entry.spells] == ["fire", "arcane"]
    ranks = {entry.model.id: entry.model.max_level for entry in content.spells}
    assert {
        spell: ranks[spell] for spell, _ in [(t.spell.removeprefix("realmforge:"), t) for t in frost.spells]
    } == {
        "frostbolt": 5,
        "improved_frostbolt": 3,
        "cone_of_cold": 3,
        "ice_lance": 5,
        "frost_nova": 3,
        "frostbite": 3,
        "shatter": 3,
        "ice_barrier": 3,
        "blizzard": 3,
    }


def test_passive_spells_do_not_count_against_d4(repo: RepoPaths) -> None:
    mage = repo.content / "classes" / "mage.yaml"
    text = mage.read_text()
    frostbite = "{spell: realmforge:frostbite, level: 6, column: 3}"
    # A passive in row 2's third place keeps chapter 1 at 4 actives, whichever passive it is.
    mage.write_text(
        text.replace(frostbite, "{spell: realmforge:shatter, level: 6, column: 3}").replace(
            "{spell: realmforge:shatter, level: 12, column: 1}",
            "{spell: realmforge:frostbite, level: 12, column: 1}",
        ),
        encoding="utf-8",
    )
    load_and_validate(repo)
    # An active spell in that place makes 5.
    mage.write_text(
        text.replace(frostbite, "{spell: realmforge:ice_barrier, level: 6, column: 3}").replace(
            "{spell: realmforge:ice_barrier, level: 12, column: 2}",
            "{spell: realmforge:frostbite, level: 12, column: 2}",
        ),
        encoding="utf-8",
    )
    assert_problem(repo, "5 active spells by level 10, the end of chapter 1, which allows 4")


# Frozen breaks on damage (Matěj's playtest of 2026-11-01)


def test_frozen_breaks_on_half_the_damage() -> None:
    content, _ = load_and_validate(RepoPaths())
    frozen = next(entry.model for entry in content.effects if entry.model.id == "frozen")
    assert frozen.breaks_on_damage_percent == 50


def test_a_break_chance_is_generated(repo: RepoPaths) -> None:
    effect(repo, "test_chill_lock", BREAKS)
    files = effects_generate(repo)
    assert (
        ".looks(StatusEffect.Look.ICE)\n                    .breaksOnDamage(0.5)"
        in files[REGISTRATIONS].decode()
    )
    expectation = files[EFFECT_EXPECTATIONS].decode()
    assert "Optional.empty(),\n                    false,\n                    0.5,\n" in expectation


@pytest.mark.parametrize("chance", ["0", "120"])
def test_a_break_chance_is_a_share(repo: RepoPaths, chance: str) -> None:
    effect(
        repo,
        "test_chill_lock",
        BREAKS.replace("breaks_on_damage_percent: 50", f"breaks_on_damage_percent: {chance}"),
    )
    effects_problem(repo, "breaks_on_damage_percent")
