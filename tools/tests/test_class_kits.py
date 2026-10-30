"""The class kits (C4, RLM-27) in the pipeline: what a spell needs in hand (RLM-499), the per-rank armor shred, spell
penetration, crit chance and status effects, cleanse and stuns, a player's channel every 0.5 s (RLM-518), class spells
by level, the racial spell and passive, and the melee and sword-like item tags."""

from __future__ import annotations

import json
import struct

import pytest

from realmforge_tools.generate import generate_all
from realmforge_tools.generate.effect_icons import ICONS, PIXELS, drawn_icon
from realmforge_tools.id_universe import load_vanilla
from realmforge_tools.problems import ValidationFailed
from realmforge_tools.project import REPO_ROOT, RepoPaths
from realmforge_tools.schema.character import passive_bonuses, passive_modifier_id
from realmforge_tools.schema.effect import EffectDef
from realmforge_tools.schema.item import FIXED_ITEM_TAGS, MELEE_WEAPONS_TAG, SWORD_LIKE_TAG
from realmforge_tools.schema.spell import SpellDef
from realmforge_tools.validate import load_and_validate
from tests.conftest import write

DATA = "mod/src/generated/resources/data/realmforge"
QA = "qa-mod/src/generated/java/io/github/matejkadlec/realmforge/qa/generated"

STRIKE = """id: cleave
name: {en_us: Cleave}
description: {en_us: Cleaves an enemy.}
school: realmforge:physical
scales_with: attack_damage
targeting: {type: realmforge:enemy, range: 4}
cast: {type: instant}
effects:
  - {type: realmforge:damage, amount: 2, attack_damage_percent: 100}
"""


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


def slowing(**fields: object) -> dict:
    return {
        "type": "realmforge:apply_effect",
        "effect": "minecraft:slowness",
        "duration_seconds": 3,
        **fields,
    }


# The repository's kits


def test_the_classes_teach_four_spells_by_level_10_and_one_at_level_1() -> None:
    content, _ = load_and_validate(RepoPaths())
    kits = {entry.model.id: [(t.spell, t.level) for t in entry.model.spells] for entry in content.classes}
    assert kits == {
        "warrior": [
            ("realmforge:slam", 1),
            ("realmforge:reckless_strike", 3),
            ("realmforge:shield_bash", 5),
            ("realmforge:battle_shout", 8),
        ],
        "mage": [
            ("realmforge:frostbolt", 1),
            ("realmforge:fireball", 3),
            ("realmforge:arcane_missiles", 5),
            ("realmforge:ice_barrier", 8),
        ],
    }
    human = content.races[0].model
    assert human.racial_spell == "realmforge:will_to_survive"
    assert human.racial_passive is not None and human.racial_passive.weapons == SWORD_LIKE_TAG


def test_the_specializations_first_spells_have_five_ranks() -> None:
    content, _ = load_and_validate(RepoPaths())
    ranks = {entry.model.id: entry.model.max_level for entry in content.spells}
    for spell in ("slam", "reckless_strike", "shield_bash", "frostbolt", "fireball", "arcane_missiles"):
        assert ranks[spell] == 5, spell


# Requirements in hand (RLM-499)


def test_a_players_melee_damage_needs_a_requirement(repo: RepoPaths) -> None:
    write(repo.content / "spells" / "cleave.yaml", STRIKE)
    assert_problem(repo, "needs a melee weapon in the main hand: requires: [melee_weapon]")


@pytest.mark.parametrize("extra", ["requires: [melee_weapon]\n", "requires: [shield]\n", "npc_only: true\n"])
def test_a_requirement_or_a_mob_spell_passes(repo: RepoPaths, extra: str) -> None:
    write(repo.content / "spells" / "cleave.yaml", STRIKE + extra)
    load_and_validate(repo)


def test_a_physical_projectile_needs_nothing_in_hand(repo: RepoPaths) -> None:
    write(
        repo.content / "spells" / "cleave.yaml",
        STRIKE.replace(
            "  - {type: realmforge:damage, amount: 2, attack_damage_percent: 100}\n",
            "  - type: realmforge:projectile\n    speed: 20\n    effects:\n"
            "      - {type: realmforge:damage, amount: 2, attack_damage_percent: 100}\n",
        ),
    )
    load_and_validate(repo)


@pytest.mark.parametrize(
    ("fields", "expected"),
    [
        ({"requires": ["melee_weapon"], "npc_only": True}, "requires is checked for players only"),
        ({"requires": ["shield", "shield"]}, "requires names each requirement once"),
        ({"requires": ["wand"]}, "'melee_weapon' or 'shield'"),
    ],
)
def test_bad_requirements_are_refused(fields: dict, expected: str) -> None:
    with pytest.raises(ValueError, match=expected):
        spell_model(**fields)


def test_the_requirements_reach_the_data(repo: RepoPaths) -> None:
    write(
        repo.content / "spells" / "cleave.yaml",
        STRIKE + "requires: [melee_weapon]\ncastable_while_stunned: true\n",
    )
    data = json.loads(generate(repo)[f"{DATA}/realmforge/spell/cleave.json"])
    assert data["requires"] == ["melee_weapon"]
    assert data["castable_while_stunned"] is True


# Per rank


def test_per_rank_numbers_grow_and_stay_bounded() -> None:
    model = spell_model(
        max_level=5,
        effects=[
            {
                "type": "realmforge:damage",
                "amount": 4,
                "penetration_percent": 5,
                "penetration_per_level_percent": 5,
            }
        ],
    )
    assert [model.effects[0].penetration_at(level) for level in (1, 5)] == [5, 25]
    with pytest.raises(ValueError, match="armor shred and penetration stay at most 100"):
        spell_model(
            max_level=5,
            effects=[
                {
                    "type": "realmforge:damage",
                    "amount": 4,
                    "armor_shred_percent": 60,
                    "armor_shred_per_level_percent": 20,
                }
            ],
        )
    with pytest.raises(ValueError, match="armor_shred_per_level_percent grows armor_shred_percent"):
        spell_model(
            max_level=5,
            effects=[{"type": "realmforge:damage", "amount": 4, "armor_shred_per_level_percent": 5}],
        )


def test_status_effects_last_longer_and_grow_stronger_by_rank() -> None:
    model = spell_model(
        max_level=5, effects=[slowing(duration_per_level_seconds=0.5, stronger_at_levels=[5])]
    )
    effect = model.effects[0]
    assert [effect.duration_at(level) for level in (1, 5)] == [3, 5]
    assert [effect.amplifier_at(level) for level in (4, 5)] == [0, 1]


@pytest.mark.parametrize(
    ("fields", "expected"),
    [
        ({"effects": [slowing(stronger_at_levels=[5, 3])]}, "ascending order"),
        ({"effects": [slowing(stronger_at_levels=[1])]}, "from 2 up"),
        ({"effects": [slowing(stronger_at_levels=[7])]}, "a level above max_level 5 is never reached"),
        ({"effects": [slowing(duration_seconds=50, duration_per_level_seconds=5)]}, "more than 60"),
        ({"effects": [slowing(duration_per_level_seconds=0)]}, "duration_per_level_seconds is more than 0"),
        (
            {"crit": {"bonus_chance_percent": 50, "bonus_chance_per_level_percent": 20}},
            "the bonus chance stays",
        ),
    ],
)
def test_bad_rank_growth_is_refused(fields: dict, expected: str) -> None:
    with pytest.raises(ValueError, match=expected):
        spell_model(max_level=5, **fields)


@pytest.mark.parametrize(
    "fields",
    [
        {
            "effects": [
                {
                    "type": "realmforge:damage",
                    "amount": 4,
                    "armor_shred_percent": 5,
                    "armor_shred_per_level_percent": 5,
                }
            ]
        },
        {"effects": [slowing(duration_per_level_seconds=0.5)]},
        {"effects": [slowing(stronger_at_levels=[2])]},
        {"crit": {"bonus_chance_percent": 5, "bonus_chance_per_level_percent": 5}},
    ],
)
def test_growth_needs_more_than_one_rank(fields: dict) -> None:
    with pytest.raises(ValueError, match="nothing grows with the level at max_level 1"):
        spell_model(**fields)


def test_armor_shred_needs_a_school_armor_stops(repo: RepoPaths) -> None:
    write(
        repo.content / "spells" / "frost_lance.yaml",
        """id: frost_lance
name: {en_us: Frost Lance}
description: {en_us: A lance of ice.}
school: realmforge:frost
scales_with: spell_power
targeting: {type: realmforge:enemy, range: 20}
cast: {type: instant}
effects:
  - {type: realmforge:damage, amount: 4, armor_shred_percent: 10}
""",
    )
    assert_problem(repo, "realmforge:frost hits skip armor, so armor shred does nothing for them")


def test_penetration_needs_a_school_with_a_resistance(repo: RepoPaths) -> None:
    write(
        repo.content / "spells" / "cleave.yaml",
        STRIKE.replace("attack_damage_percent: 100}", "attack_damage_percent: 100, penetration_percent: 10}")
        + "requires: [melee_weapon]\n",
    )
    assert_problem(repo, "realmforge:physical has no resistance for spell penetration to ignore")


# Placeholders


@pytest.mark.parametrize(
    ("fields", "expected"),
    [
        ({"description": {"en_us": "Ignores {armor_shred}% armor."}}, "lacks"),
        ({"description": {"en_us": "Ignores {penetration}% resistance."}}, "lacks"),
        ({"description": {"en_us": "{crit_chance}% more crit."}}, "the spell has none"),
        ({"description": {"en_us": "Raises it by {bonus}."}}, "needs a realmforge:apply_effect effect"),
    ],
)
def test_new_placeholders_need_their_numbers(fields: dict, expected: str) -> None:
    with pytest.raises(ValueError, match=expected):
        spell_model(**fields)


def test_bonus_reads_an_effect_of_one_modifier(repo: RepoPaths) -> None:
    write(
        repo.content / "effects" / "warcry.yaml",
        """id: warcry
name: {en_us: Warcry}
category: beneficial
color: "#FF0000"
modifiers:
  - {attribute: minecraft:attack_damage, operation: add_multiplied_total, amount_percent: 10}
  - {attribute: minecraft:attack_speed, operation: add_multiplied_total, amount_percent: 10}
""",
    )
    write(
        repo.content / "spells" / "warcry.yaml",
        """id: warcry
name: {en_us: Warcry}
description: {en_us: "Raises attack damage by {bonus}%."}
school: realmforge:physical
scales_with: attack_damage
targeting: {type: realmforge:self}
cast: {type: instant}
effects:
  - {type: realmforge:apply_effect, effect: realmforge:warcry, duration_seconds: 10}
""",
    )
    assert_problem(repo, "{bonus} reads realmforge:warcry's one modifier, and it has 2")


# Channels (RLM-518)


def test_a_players_channel_ticks_every_half_second() -> None:
    with pytest.raises(ValueError, match=r"a player's channel ticks every 0\.5 s .*RLM-518.*not every 1 s"):
        spell_model(cast={"type": "channel", "duration_seconds": 3, "interval_seconds": 1})
    spell_model(cast={"type": "channel", "duration_seconds": 3, "interval_seconds": 0.5})
    spell_model(npc_only=True, cast={"type": "channel", "duration_seconds": 3, "interval_seconds": 0.75})


def test_a_dot_grows_by_whole_intervals(repo: RepoPaths) -> None:
    write(
        repo.content / "spells" / "scorch.yaml",
        """id: scorch
name: {en_us: Scorch}
description: {en_us: Burns an enemy.}
school: realmforge:fire
scales_with: spell_power
targeting: {type: realmforge:enemy, range: 20}
cast: {type: instant}
max_level: 3
effects:
  - {type: realmforge:apply_effect, effect: realmforge:burning, duration_seconds: 4, duration_per_level_seconds: 0.25}
""",
    )
    assert_problem(repo, "duration_per_level_seconds 0.25 s is not one")


# Cleanse and stuns


def test_cleanse_helps_the_caster_or_an_ally(repo: RepoPaths) -> None:
    write(
        repo.content / "spells" / "purge.yaml",
        """id: purge
name: {en_us: Purge}
description: {en_us: Purges an enemy.}
school: realmforge:arcane
scales_with: spell_power
targeting: {type: realmforge:enemy, range: 20}
cast: {type: instant}
effects:
  - {type: realmforge:cleanse}
""",
    )
    assert_problem(repo, "realmforge:cleanse acts on the spell's targets")


@pytest.mark.parametrize(
    ("fields", "expected"),
    [
        ({"category": "beneficial"}, "a stun is harmful"),
        (
            {
                "modifiers": [
                    {
                        "attribute": "minecraft:movement_speed",
                        "operation": "add_multiplied_total",
                        "amount_percent": -100,
                    }
                ]
            },
            "minecraft:jump_strength",
        ),
    ],
)
def test_a_stun_is_harmful_and_stops_movement(fields: dict, expected: str) -> None:
    data = {
        "id": "dazed",
        "name": {"en_us": "Dazed"},
        "category": "harmful",
        "color": "#FFFFFF",
        "stun": True,
        "modifiers": [
            {
                "attribute": "minecraft:movement_speed",
                "operation": "add_multiplied_total",
                "amount_percent": -100,
            },
            {
                "attribute": "minecraft:jump_strength",
                "operation": "add_multiplied_total",
                "amount_percent": -100,
            },
        ],
        **fields,
    }
    with pytest.raises(ValueError, match=expected):
        EffectDef.model_validate(data)


def test_a_stun_reaches_the_registration() -> None:
    files = generate(RepoPaths())
    effects = files[
        "mod/src/generated/java/io/github/matejkadlec/realmforge/generated/GeneratedEffects.java"
    ].decode()
    stunned = effects[effects.index("STUNNED =") :]
    assert stunned.index(".stuns()") < stunned.index(";")


# Classes and races


CLASS_SPELL = "spells:\n  - {spell: realmforge:slam, level: 1}\n"


def test_class_spells_cost_the_class_resource(repo: RepoPaths) -> None:
    mage = repo.content / "classes" / "mage.yaml"
    mage.write_text(
        mage.read_text().replace(
            "  - {spell: realmforge:frostbolt, level: 1}", "  - {spell: realmforge:slam, level: 1}"
        )
    )
    assert_problem(repo, "realmforge:slam costs realmforge:rage, and the class's resource is realmforge:mana")


def test_class_spells_are_players_spells_that_exist(repo: RepoPaths) -> None:
    warrior = repo.content / "classes" / "warrior.yaml"
    warrior.write_text(warrior.read_text().replace("realmforge:battle_shout", "realmforge:test_npc_bolt"))
    assert_problem(repo, "realmforge:test_npc_bolt is a mob's spell (npc_only)")
    warrior.write_text(warrior.read_text().replace("realmforge:test_npc_bolt", "realmforge:whirlwind"))
    assert_problem(repo, "realmforge:whirlwind is not a spell of content/spells/")


def test_a_chapter_allows_four_spells_a_chapter(repo: RepoPaths) -> None:
    warrior = repo.content / "classes" / "warrior.yaml"
    warrior.write_text(warrior.read_text() + "  - {spell: realmforge:will_to_survive, level: 9}\n")
    assert_problem(repo, "5 spells by level 10, the end of chapter 1, which allows 4")


@pytest.mark.parametrize(
    ("spells", "expected"),
    [
        ("  - {spell: realmforge:slam, level: 3}\n", "every class starts with one spell"),
        (
            "  - {spell: realmforge:slam, level: 1}\n  - {spell: realmforge:slam, level: 2}\n",
            "each spell once",
        ),
    ],
)
def test_class_spell_lists_start_at_level_1_and_name_each_spell_once(
    repo: RepoPaths, spells: str, expected: str
) -> None:
    warrior = repo.content / "classes" / "warrior.yaml"
    text = warrior.read_text()
    warrior.write_text(text[: text.index("spells:\n")] + "spells:\n" + spells)
    assert_problem(repo, expected)


def test_a_racial_spell_is_a_free_players_spell(repo: RepoPaths) -> None:
    human = repo.content / "races" / "human.yaml"
    human.write_text(human.read_text().replace("realmforge:will_to_survive", "realmforge:slam"))
    assert_problem(repo, "realmforge:slam costs realmforge:rage: a racial spell is free")


def test_a_racial_passive_names_a_tag_that_exists(repo: RepoPaths) -> None:
    human = repo.content / "races" / "human.yaml"
    human.write_text(human.read_text().replace("realmforge:sword_like", "realmforge:katana_like"))
    assert_problem(repo, "unknown item tag 'realmforge:katana_like'")


def test_a_racial_passive_description_names_only_bonus(repo: RepoPaths) -> None:
    human = repo.content / "races" / "human.yaml"
    human.write_text(human.read_text().replace("+{bonus}% damage", "+{damage}% damage"))
    assert_problem(repo, "unknown placeholder {damage}")


def test_class_spells_and_racials_reach_the_data_and_expectations() -> None:
    files = generate(RepoPaths())
    warrior = json.loads(files[f"{DATA}/realmforge/class/warrior.json"])
    assert warrior["spells"][0] == {"spell": "realmforge:slam", "level": 1}
    human = json.loads(files[f"{DATA}/realmforge/race/human.json"])
    assert human["racial_spell"] == "realmforge:will_to_survive"
    assert human["racial_passive"] == {
        "name": "race.realmforge.human.racial_passive.name",
        "description": "race.realmforge.human.racial_passive.description",
        "weapons": "realmforge:sword_like",
        "modifiers": [
            {"attribute": "minecraft:attack_damage", "operation": "add_multiplied_base", "amount": 0.1}
        ],
    }
    races = files[f"{QA}/GeneratedRaceExpectations.java"].decode()
    assert 'Optional.of("realmforge:will_to_survive")' in races
    assert 'new Bonus("realmforge:race/realmforge/passive/0/human", "minecraft:attack_damage"' in races
    classes = files[f"{QA}/GeneratedClassExpectations.java"].decode()
    assert 'new ClassSpell("realmforge:frostbolt", 1)' in classes


def test_passive_modifier_ids_name_the_race() -> None:
    content, _ = load_and_validate(RepoPaths())
    human = content.races[0].model
    assert passive_modifier_id("human", 0) == "realmforge:race/realmforge/passive/0/human"
    assert passive_bonuses("human", human.racial_passive) == [
        ("realmforge:race/realmforge/passive/0/human", "minecraft:attack_damage", "add_multiplied_base", 0.1)
    ]


# Item tags


def test_the_melee_and_sword_like_tags_are_generated() -> None:
    files = generate(RepoPaths())
    melee = json.loads(files[f"{DATA}/tags/item/melee_weapons.json"])
    assert melee == {"values": sorted(FIXED_ITEM_TAGS[MELEE_WEAPONS_TAG])}
    assert "#minecraft:swords" in melee["values"] and "minecraft:mace" in melee["values"]
    sword_like = json.loads(files[f"{DATA}/tags/item/sword_like.json"])
    assert sword_like == {"values": ["#minecraft:swords"]}


def test_the_fixed_tags_name_vanilla_ids_that_exist() -> None:
    vanilla = load_vanilla(RepoPaths())
    for members in FIXED_ITEM_TAGS.values():
        for member in members:
            if member.startswith("#"):
                assert vanilla.contains_tag("minecraft:item", member[1:]), member
            else:
                assert vanilla.contains("minecraft:item", member), member


# The playtest revision (Matěj, 2026-10-30)


def test_the_self_buffs_have_three_ranks_and_a_minute_cooldown() -> None:
    content, _ = load_and_validate(RepoPaths())
    spells = {entry.model.id: entry.model for entry in content.spells}
    effects = {entry.model.id: entry.model for entry in content.effects}
    shout, barrier = spells["battle_shout"], spells["ice_barrier"]
    assert (shout.max_level, shout.cooldown_seconds, barrier.max_level, barrier.cooldown_seconds) == (
        3,
        60,
        3,
        60,
    )
    shout_effect = shout.effects[0].effects[0]
    assert [shout_effect.duration_at(rank) for rank in (1, 2, 3)] == [10, 15, 20]
    assert [shout_effect.amplifier_at(rank) for rank in (1, 2, 3)] == [0, 1, 2]
    bonus = effects["battle_shout"].modifiers[0]
    assert [round(bonus.game_amount_at(level), 6) for level in (0, 1, 2)] == [0.1, 0.15, 0.2]
    barrier_effect = barrier.effects[0]
    assert [barrier_effect.duration_at(rank) for rank in (1, 2, 3)] == [10, 20, 30]
    shield = effects["ice_barrier"].modifiers[0]
    assert [shield.game_amount_at(barrier_effect.amplifier_at(rank)) for rank in (1, 2, 3)] == [10, 20, 30]
    assert spells["will_to_survive"].cooldown_seconds == 60


@pytest.mark.parametrize(
    ("modifier", "expected"),
    [
        (
            {
                "attribute": "realmforge:magic_shield",
                "operation": "add_value",
                "amount": 2,
                "per_amplifier_percent": 5,
            },
            "write its growth as per_amplifier",
        ),
        ({"amount_percent": 10, "per_amplifier": 0.05}, "write its growth as per_amplifier_percent"),
        (
            {"amount_percent": 10, "per_amplifier": 0.05, "per_amplifier_percent": 5},
            "set at most one of per_amplifier and per_amplifier_percent",
        ),
    ],
)
def test_per_amplifier_growth_takes_the_amounts_units(modifier: dict, expected: str) -> None:
    data = {
        "id": "rally",
        "name": {"en_us": "Rally"},
        "category": "beneficial",
        "color": "#F5241B",
        "modifiers": [
            {"attribute": "minecraft:attack_damage", "operation": "add_multiplied_total"} | modifier
        ],
    }
    with pytest.raises(ValueError, match=expected):
        EffectDef.model_validate(data)


def test_per_amplifier_growth_reaches_the_registration_and_expectations() -> None:
    files = generate(RepoPaths())
    effects = files[
        "mod/src/generated/java/io/github/matejkadlec/realmforge/generated/GeneratedEffects.java"
    ].decode()
    shout = effects[effects.index("BATTLE_SHOUT =") :]
    assert "AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, 0.05)" in shout[: shout.index(";")]
    expectations = files[f"{QA}/GeneratedEffectExpectations.java"].decode()
    # 0.1 + 0.05 in doubles, as the game's curve computes it.
    assert f'"add_multiplied_total", {0.1 + 0.05!r})' in expectations
    assert '"realmforge:effect.ice_barrier.realmforge.magic_shield", 10.0, "add_value", 20.0)' in expectations


def test_a_spell_of_no_school_neither_deals_damage_nor_heals() -> None:
    assert spell_model(
        school=None, effects=[{"type": "realmforge:cleanse"}], targeting={"type": "realmforge:self"}
    )
    with pytest.raises(ValueError, match="a spell that deals damage or heals names its school"):
        spell_model(school=None)


def test_will_to_survive_has_no_school_in_the_data() -> None:
    files = generate(RepoPaths())
    data = json.loads(files[f"{DATA}/realmforge/spell/will_to_survive.json"])
    assert "school" not in data
    assert data["cooldown_ticks"] == 1200
    expectations = files[f"{QA}/GeneratedSpellExpectations.java"].decode()
    will = expectations[expectations.index('"realmforge:will_to_survive"') :]
    assert will.index("Optional.empty()") < will.index('"attack_damage"')


def test_battle_shout_and_ice_barrier_have_drawn_icons() -> None:
    files = generate(RepoPaths())
    icons = "mod/src/generated/resources/assets/realmforge/textures/mob_effect"
    for effect in ("battle_shout", "ice_barrier"):
        icon = files[f"{icons}/{effect}.png"]
        assert icon == drawn_icon(effect)
        assert icon.startswith(b"\x89PNG") and struct.unpack(">II", icon[16:24]) == (18, 18)
    assert drawn_icon("stunned") is None


def test_drawn_icons_use_only_palette_colours() -> None:
    palette = {
        tuple(int(part) for part in line.split()[:3])
        for line in (REPO_ROOT / "art" / "palette" / "realmforge.gpl")
        .read_text(encoding="utf-8")
        .splitlines()
        if line[:1].isspace() or line[:1].isdigit()
    }
    assert all(colour[3] == 0 or colour[:3] in palette for colour in PIXELS.values())
    for rows in ICONS.values():
        assert all(char in PIXELS for row in rows for char in row)
