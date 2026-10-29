"""Bosses (content/bosses/ and the boss archetype of content/mobs/): validation and generation (architecture 14.3;
W5a, RLM-490)."""

from __future__ import annotations

import json
import re

import pytest
from pydantic import ValidationError

from realmforge_tools.generate import generate_all
from realmforge_tools.problems import ValidationFailed
from realmforge_tools.project import RepoPaths
from realmforge_tools.schema.boss import BossDef
from realmforge_tools.schema.mob import MobDef
from realmforge_tools.validate import load_and_validate
from tests.conftest import write

BOSS_DATA = "mod/src/generated/resources/data/realmforge/realmforge/boss"
BOSSES_TAG = "mod/src/generated/resources/data/c/tags/entity_type/bosses.json"
LANG = "mod/src/generated/resources/assets/realmforge/lang/en_us.json"
REGISTRATIONS = "mod/src/generated/java/io/github/matejkadlec/realmforge/generated/GeneratedMobs.java"
MOB_EXPECTATIONS = (
    "qa-mod/src/generated/java/io/github/matejkadlec/realmforge/qa/generated/GeneratedMobExpectations.java"
)
BOSS_EXPECTATIONS = (
    "qa-mod/src/generated/java/io/github/matejkadlec/realmforge/qa/generated/GeneratedBossExpectations.java"
)

BODY = """name: {en_us: Stone Colossus}
archetype: boss
size: {width: 1.6, height: 4.0}
attributes: {max_health: 300, attack_damage: 8, movement_speed: 0.22}
melee: {attack_interval_seconds: 2}
model: {build: golem, color: '#8C8577'}
"""

FIGHT = """entity: realmforge:stone_colossus
level: 12
arena: {radius: 20}
phases:
  - id: waking
    name: {en_us: Waking Stone}
    abilities: [{spell: realmforge:test_npc_target_bolt, cooldown_seconds: 4}]
  - id: fury
    name: {en_us: Stone Fury}
    enter_when: {type: realmforge:health_below, health_percent: 60}
    on_enter: [realmforge:test_titan_harden]
    abilities: [{spell: realmforge:test_npc_target_bolt}]
  - id: last_stand
    name: {en_us: Last Stand}
    enter_when: {type: realmforge:time_in_phase, after_seconds: 30}
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


def body(repo: RepoPaths, name: str = "stone_colossus", text: str = BODY) -> None:
    write(repo.content / "mobs" / f"{name}.yaml", f"id: {name}\n{text}")


def fight(repo: RepoPaths, name: str = "stone_colossus", text: str = FIGHT) -> None:
    write(repo.content / "bosses" / f"{name}.yaml", f"id: {name}\n{text}")


def boss_model(**fields: object) -> BossDef:
    data = {
        "id": "stone_colossus",
        "entity": "realmforge:stone_colossus",
        "level": 12,
        "arena": {"radius": 20},
        "phases": [
            {"id": "waking", "name": {"en_us": "Waking Stone"}},
            {
                "id": "fury",
                "name": {"en_us": "Stone Fury"},
                "enter_when": {"type": "realmforge:health_below", "health_percent": 60},
            },
        ],
        **fields,
    }
    return BossDef.model_validate(data)


def test_the_repository_has_the_test_titan() -> None:
    content, _ = load_and_validate(RepoPaths())
    bosses = {entry.model.id: entry.model for entry in content.bosses}
    assert set(bosses) == {"test_titan"}
    titan = bosses["test_titan"]
    assert titan.entity == "realmforge:test_titan"
    assert [phase.id for phase in titan.phases] == ["awakening", "fury", "frenzy"]
    assert {entry.model.id: entry.model.archetype for entry in content.mobs}["test_titan"] == "boss"


@pytest.mark.parametrize(
    ("phases", "expected"),
    [
        (
            [
                {
                    "id": "a",
                    "name": {"en_us": "A"},
                    "enter_when": {"type": "realmforge:time_in_phase", "after_seconds": 1},
                }
            ],
            "the first phase (a) is where the fight starts",
        ),
        (
            [{"id": "a", "name": {"en_us": "A"}, "on_enter": ["realmforge:test_titan_harden"]}],
            "the first phase (a) begins at spawn",
        ),
        (
            [{"id": "a", "name": {"en_us": "A"}}, {"id": "b", "name": {"en_us": "B"}}],
            "phase b needs an enter_when",
        ),
        (
            [
                {"id": "a", "name": {"en_us": "A"}},
                {
                    "id": "a",
                    "name": {"en_us": "B"},
                    "enter_when": {"type": "realmforge:health_below", "health_percent": 50},
                },
            ],
            "one phase per id: a",
        ),
        (
            [
                {"id": "a", "name": {"en_us": "A"}},
                {
                    "id": "b",
                    "name": {"en_us": "B"},
                    "enter_when": {"type": "realmforge:health_below", "health_percent": 30},
                },
                {
                    "id": "c",
                    "name": {"en_us": "C"},
                    "enter_when": {"type": "realmforge:health_below", "health_percent": 70},
                },
            ],
            "health thresholds decrease phase by phase",
        ),
        (
            [
                {
                    "id": "a",
                    "name": {"en_us": "A"},
                    "abilities": [
                        {"spell": "realmforge:x"},
                        {"spell": "realmforge:x", "cooldown_seconds": 2},
                    ],
                }
            ],
            "one ability per spell: realmforge:x",
        ),
        (
            [
                {"id": "a", "name": {"en_us": "A"}},
                {
                    "id": "b",
                    "name": {"en_us": "B"},
                    "enter_when": {"type": "realmforge:time_in_phase", "after_seconds": 0},
                },
            ],
            "after_seconds is more than 0",
        ),
        ([], "at least 1 item"),
    ],
)
def test_invalid_phases_are_rejected(phases: list, expected: str) -> None:
    with pytest.raises(ValidationError, match=re.escape(expected)):
        boss_model(phases=phases)


@pytest.mark.parametrize(
    ("fields", "expected"),
    [
        ({"arena": {"radius": 2}}, "greater than or equal to 4"),
        ({"level": 0}, "greater than or equal to 1"),
        ({"bar_color": "orange"}, "pink"),
        ({"phase_change_seconds": 0.01}, "whole number of game ticks"),
    ],
)
def test_invalid_bosses_are_rejected(fields: dict, expected: str) -> None:
    with pytest.raises(ValidationError, match=re.escape(expected)):
        boss_model(**fields)


def test_a_health_threshold_is_a_share_below_100() -> None:
    phases = [
        {"id": "a", "name": {"en_us": "A"}},
        {
            "id": "b",
            "name": {"en_us": "B"},
            "enter_when": {"type": "realmforge:health_below", "health_percent": 100},
        },
    ]
    with pytest.raises(ValidationError, match="less than 100"):
        boss_model(phases=phases)


def test_a_boss_body_guards_its_arena() -> None:
    data = {
        "id": "stone_colossus",
        "name": {"en_us": "Stone Colossus"},
        "archetype": "boss",
        "size": {"width": 1.6, "height": 4.0},
        "attributes": {"max_health": 300, "attack_damage": 8, "movement_speed": 0.22},
        "model": {"color": "#8C8577"},
    }
    assert MobDef.model_validate(data).archetype == "boss"
    with pytest.raises(ValidationError, match="a boss guards its arena"):
        MobDef.model_validate({**data, "idle": "stroll"})
    with pytest.raises(ValidationError, match="a boss guards its arena"):
        MobDef.model_validate({**data, "idle": "guard"})


def test_a_boss_runs_on_a_mob_of_archetype_boss(repo: RepoPaths) -> None:
    fight(repo, text=FIGHT.replace("realmforge:stone_colossus", "realmforge:hewn_sentinel"))
    assert_problem(repo, "realmforge:hewn_sentinel is a melee mob: a boss runs on a mob of archetype boss")


def test_a_boss_runs_on_one_of_our_mobs(repo: RepoPaths) -> None:
    fight(repo, text=FIGHT.replace("realmforge:stone_colossus", "minecraft:zombie"))
    assert_problem(repo, "minecraft:zombie is no mob of content/mobs/")


def test_a_boss_body_needs_its_fight(repo: RepoPaths) -> None:
    body(repo)
    assert_problem(repo, "no boss in content/bosses/ runs on realmforge:stone_colossus")


def test_one_boss_per_body(repo: RepoPaths) -> None:
    body(repo)
    fight(repo)
    fight(repo, "stone_colossus_again")
    assert_problem(repo, "realmforge:stone_colossus is already the body of content/bosses/")


def test_a_bosses_spells_must_exist(repo: RepoPaths) -> None:
    body(repo)
    fight(repo, text=FIGHT.replace("realmforge:test_titan_harden", "realmforge:no_such_spell"))
    assert_problem(repo, "phases.1.on_enter.0")


def test_a_boss_casts_no_ally_spell(repo: RepoPaths) -> None:
    body(repo)
    fight(
        repo,
        text=FIGHT.replace("realmforge:test_npc_target_bolt, cooldown", "realmforge:test_regrow, cooldown"),
    )
    assert_problem(repo, "realmforge:test_regrow targets realmforge:ally; a boss casts with")


def test_generation_writes_the_fight_the_body_and_the_expectations(repo: RepoPaths) -> None:
    body(repo)
    fight(repo)
    files = generate(repo)

    data = json.loads(files[f"{BOSS_DATA}/stone_colossus.json"])
    assert data["entity"] == "realmforge:stone_colossus"
    assert data["level"] == 12
    assert data["arena"] == {"radius": 20.0}
    assert data["bar_color"] == "red"
    assert data["phase_change_ticks"] == 60
    assert [phase["id"] for phase in data["phases"]] == ["waking", "fury", "last_stand"]
    assert "enter_when" not in data["phases"][0]
    assert data["phases"][0]["abilities"] == [
        {"spell": "realmforge:test_npc_target_bolt", "cooldown_ticks": 80}
    ]
    assert data["phases"][1]["enter_when"] == {"type": "realmforge:health_below", "health": 0.6}
    assert data["phases"][1]["on_enter"] == ["realmforge:test_titan_harden"]
    assert data["phases"][2]["enter_when"] == {"type": "realmforge:time_in_phase", "after_ticks": 600}
    assert data["phases"][1]["name"] == "boss.realmforge.stone_colossus.phases.1.name"

    lang = json.loads(files[LANG])
    assert lang["boss.realmforge.stone_colossus.phases.1.name"] == "Stone Fury"
    assert lang["entity.realmforge.stone_colossus"] == "Stone Colossus"
    assert lang["gui.realmforge.boss_bar_title"] == "%1$s — %2$s"

    assert "realmforge:stone_colossus" in json.loads(files[BOSSES_TAG])["values"]
    registrations = files[REGISTRATIONS].decode()
    assert 'Archetypes.create("boss", type, level, STONE_COLOSSUS_SPEC)' in registrations
    assert "EntityType<CustomMob>> STONE_COLOSSUS" in registrations
    assert "STONE_COLOSSUS_SPEC = new CustomMobSpec(CustomMobSpec.Idle.GUARD," in registrations
    assert ".clientTrackingRange(10)" in registrations

    mob_expectations = files[MOB_EXPECTATIONS].decode()
    assert '"c:bosses"' in mob_expectations
    expectations = files[BOSS_EXPECTATIONS].decode()
    assert '"realmforge:stone_colossus",\n                    "realmforge:stone_colossus",' in expectations
    assert '"realmforge:health_below", 0.6,' in expectations
    assert '"realmforge:time_in_phase", 600.0,' in expectations
    assert 'new AbilityExpectation("realmforge:test_npc_target_bolt", 80)' in expectations


def test_no_bosses_still_compile(repo: RepoPaths) -> None:
    for name in ("test_titan",):
        (repo.content / "bosses" / f"{name}.yaml").unlink()
        (repo.content / "mobs" / f"{name}.yaml").unlink()
    files = generate(repo)
    assert "BOSSES = List.of();" in files[BOSS_EXPECTATIONS].decode()
    assert not any(path.startswith(BOSS_DATA) for path in files)
    assert BOSSES_TAG not in files
