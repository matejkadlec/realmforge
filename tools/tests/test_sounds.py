"""Sounds (content/sounds/): our sound events, validation and generation (A3, ADR 0007, architecture 6.2)."""

from __future__ import annotations

import json

import pytest

from realmforge_tools.generate import generate_all
from realmforge_tools.problems import ValidationFailed
from realmforge_tools.project import RepoPaths
from realmforge_tools.schema.sound import SoundGroupDef, sound_id, subtitle_key
from realmforge_tools.validate import load_and_validate
from tests.conftest import write

SOUNDS_JSON = "mod/src/generated/resources/assets/realmforge/sounds.json"
LANG = "mod/src/generated/resources/assets/realmforge/lang/en_us.json"
REGISTRATIONS = "mod/src/generated/java/io/github/matejkadlec/realmforge/generated/GeneratedSounds.java"

ROCK = """category: entity
events:
  hurt:
    subtitle: {en_us: Rock cracks}
    sounds:
      - {event: minecraft:block.deepslate.break, pitch: 0.8}
      - {event: minecraft:entity.iron_golem.hurt, volume: 0.5}
"""


def problems_for(repo: RepoPaths) -> list[str]:
    with pytest.raises(ValidationFailed) as excinfo:
        load_and_validate(repo)
    return [str(p) for p in excinfo.value.problems]


def generate(repo: RepoPaths) -> dict[str, bytes]:
    content, _ = load_and_validate(repo)
    return {repo.rel(path): data for path, data in generate_all(content, repo).items()}


def sounds(repo: RepoPaths, name: str, body: str) -> None:
    write(repo.content / "sounds" / f"{name}.yaml", f"id: {name}\n{body}")


def test_repository_sounds_are_the_hewns_stoneforms_and_astones() -> None:
    content, universe = load_and_validate(RepoPaths())
    ids = sorted(sound_id(entry.model, name) for entry in content.sounds for name in entry.model.events)
    assert ids == [
        "realmforge:entity.astone.ambient",
        "realmforge:entity.astone.death",
        "realmforge:entity.astone.hurt",
        "realmforge:entity.astone.step",
        "realmforge:entity.hewn_chanter.ambient",
        "realmforge:entity.hewn_chanter.death",
        "realmforge:entity.hewn_chanter.hurt",
        "realmforge:entity.hewn_sentinel.death",
        "realmforge:entity.hewn_sentinel.hurt",
        "realmforge:entity.hewn_sentinel.step",
        "realmforge:spell.astone_boulder.form",
        "realmforge:spell.astone_boulder.impact",
        "realmforge:spell.astone_quake.rumble",
        "realmforge:spell.astone_rockfall.crack",
        "realmforge:spell.stoneform.form",
        "realmforge:spell.stoneform.impact",
    ]
    assert all(universe.contains("minecraft:sound_event", sound) for sound in ids)


def test_an_event_becomes_its_registration_its_sounds_json_entry_and_its_subtitle(repo: RepoPaths) -> None:
    sounds(repo, "test_rock", ROCK)
    files = generate(repo)
    entry = json.loads(files[SOUNDS_JSON])["entity.test_rock.hurt"]
    assert entry == {
        "subtitle": "subtitles.realmforge.entity.test_rock.hurt",
        "sounds": [
            {"name": "minecraft:block.deepslate.break", "type": "event", "pitch": 0.8},
            {"name": "minecraft:entity.iron_golem.hurt", "type": "event", "volume": 0.5},
        ],
    }
    assert json.loads(files[LANG])["subtitles.realmforge.entity.test_rock.hurt"] == "Rock cracks"
    registrations = files[REGISTRATIONS].decode()
    assert 'ENTITY_TEST_ROCK_HURT =\n            register("entity.test_rock.hurt");' in registrations
    assert "SoundEvent.createVariableRangeEvent(Realmforge.id(path))" in registrations


def test_a_mob_may_play_our_event(repo: RepoPaths) -> None:
    sounds(repo, "test_rock", ROCK)
    write(
        repo.content / "mobs" / "test_rock.yaml",
        "id: test_rock\nname: {en_us: Test Rock}\narchetype: melee\nsize: {width: 1, height: 1}\n"
        "attributes: {max_health: 10, attack_damage: 1, movement_speed: 0.2}\nmodel: {color: '#808080'}\n"
        "sounds: {hurt: realmforge:entity.test_rock.hurt}\n",
    )
    load_and_validate(repo)


def test_an_event_plays_only_vanilla_events_that_exist(repo: RepoPaths) -> None:
    sounds(
        repo,
        "test_rock",
        ROCK.replace("minecraft:block.deepslate.break", "realmforge:entity.hewn_sentinel.hurt").replace(
            "minecraft:entity.iron_golem.hurt", "minecraft:entity.iron_golem.hurts"
        ),
    )
    problems = problems_for(repo)
    assert any(
        "events.hurt.sounds.0.event: realmforge:entity.hewn_sentinel.hurt: an event plays vanilla sound events"
        in p
        for p in problems
    ), problems
    assert any(
        "events.hurt.sounds.1.event: unknown id 'minecraft:entity.iron_golem.hurts'" in p for p in problems
    )


@pytest.mark.parametrize(
    ("fields", "expected"),
    [
        ({"category": "music"}, "Input should be 'entity', 'block', 'item', 'spell', 'ambient' or 'ui'"),
        ({"events": {}}, "at least 1 item"),
        ({"events": {"Hurt": {"subtitle": {"en_us": "x"}, "sounds": [{"event": "minecraft:a"}]}}}, "pattern"),
        ({"events": {"hurt": {"subtitle": {"en_us": "x"}, "sounds": []}}}, "at least 1 item"),
        (
            {
                "events": {
                    "hurt": {"subtitle": {"en_us": "x"}, "sounds": [{"event": "minecraft:a", "pitch": 3}]}
                }
            },
            "less than or equal to 2",
        ),
        (
            {
                "events": {
                    "hurt": {"subtitle": {"en_us": "x"}, "sounds": [{"event": "minecraft:a", "volume": 0}]}
                }
            },
            "greater than 0",
        ),
    ],
)
def test_invalid_sound_groups_are_rejected(fields: dict, expected: str) -> None:
    data = {
        "id": "test_rock",
        "category": "entity",
        "events": {"hurt": {"subtitle": {"en_us": "Rock cracks"}, "sounds": [{"event": "minecraft:a"}]}},
        **fields,
    }
    with pytest.raises(ValueError, match=expected):
        SoundGroupDef.model_validate(data)


def test_the_subtitles_group_is_the_sounds(repo: RepoPaths) -> None:
    write(repo.content / "texts" / "subtitles.yaml", "id: subtitles\ntexts:\n  rock: {en_us: Rock}\n")
    problems = problems_for(repo)
    assert any("content/sounds/" in p for p in problems), problems


def test_keys() -> None:
    group = SoundGroupDef.model_validate(
        {
            "id": "x",
            "category": "spell",
            "events": {"form": {"subtitle": {"en_us": "y"}, "sounds": [{"event": "a:b"}]}},
        }
    )
    assert sound_id(group, "form") == "realmforge:spell.x.form"
    assert subtitle_key(group, "form") == "subtitles.realmforge.spell.x.form"
