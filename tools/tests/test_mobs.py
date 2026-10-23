"""Custom mobs (content/mobs/): validation and generation (architecture 14.2; W3, RLM-33)."""

from __future__ import annotations

import json
import struct

import pytest

from realmforge_tools.generate import generate_all
from realmforge_tools.generate.mobs import BONES, TEXTURE_SIZES, placeholder_model
from realmforge_tools.problems import ValidationFailed
from realmforge_tools.project import RepoPaths
from realmforge_tools.schema.mob import TRASH_LOOT, MobDef
from realmforge_tools.validate import load_and_validate
from tests.conftest import write

LANG = "mod/src/generated/resources/assets/realmforge/lang/en_us.json"
ASSETS = "mod/src/generated/resources/assets/realmforge"
LOOT = "mod/src/generated/resources/data/realmforge/loot_table/entities"
REGISTRATIONS = "mod/src/generated/java/io/github/matejkadlec/realmforge/generated/GeneratedMobs.java"
EXPECTATIONS = (
    "qa-mod/src/generated/java/io/github/matejkadlec/realmforge/qa/generated/GeneratedMobExpectations.java"
)

MELEE = """name: {en_us: Hewn Brute}
archetype: melee
size: {width: 0.9, height: 2.2}
attributes: {max_health: 30, attack_damage: 5, movement_speed: 0.2}
color: '#8C8577'
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


def mob(repo: RepoPaths, name: str, body: str) -> None:
    write(repo.content / "mobs" / f"{name}.yaml", f"id: {name}\n{body}")


def mob_model(**fields: object) -> MobDef:
    data = {
        "id": "hewn_brute",
        "name": {"en_us": "Hewn Brute"},
        "archetype": "melee",
        "size": {"width": 0.9, "height": 2.2},
        "attributes": {"max_health": 30, "attack_damage": 5, "movement_speed": 0.2},
        "color": "#8C8577",
        **fields,
    }
    return MobDef.model_validate(data)


def test_repository_mobs_are_the_hewn() -> None:
    content, _ = load_and_validate(RepoPaths())
    assert {entry.model.id: entry.model.archetype for entry in content.mobs} == {
        "hewn_chanter": "caster",
        "hewn_sentinel": "melee",
    }


@pytest.mark.parametrize(
    ("fields", "expected"),
    [
        ({"archetype": "caster"}, "a caster names its spells"),
        (
            {"caster": {"spells": ["realmforge:shard_song"], "keep_distance": 6}},
            "caster is only for archetype caster, not melee",
        ),
        ({"archetype": "summoner"}, "Input should be 'melee' or 'caster'"),
        ({"size": {"width": 0.1, "height": 2}}, "Input should be greater than or equal to 0.3"),
        ({"attributes": {"max_health": 0, "attack_damage": 1, "movement_speed": 0.2}}, "greater than 0"),
        (
            {
                "loot": [
                    {"item": "minecraft:iron_ingot"},
                    {"item": "minecraft:iron_ingot", "chance_percent": 5},
                ]
            },
            "one drop per item: minecraft:iron_ingot",
        ),
        (
            {"loot": [{"item": "minecraft:iron_ingot", "count": {"min": 3, "max": 2}}]},
            "min \\(3\\) is more than max",
        ),
        ({"loot": [{"item": "minecraft:iron_ingot", "chance_percent": 0}]}, "greater than 0"),
        ({"idle": "wander"}, "Input should be 'stroll' or 'guard'"),
    ],
)
def test_invalid_mobs_are_rejected(fields: dict, expected: str) -> None:
    with pytest.raises(ValueError, match=expected):
        mob_model(**fields)


def test_a_mob_drops_no_trash(repo: RepoPaths) -> None:
    assert "minecraft:iron_nugget" in TRASH_LOOT
    mob(repo, "hewn_brute", MELEE + "loot:\n  - {item: minecraft:iron_nugget, count: {min: 1, max: 3}}\n")
    assert_problem(repo, "loot.0.item: minecraft:iron_nugget is trash loot")


def test_loot_items_must_exist(repo: RepoPaths) -> None:
    mob(repo, "hewn_brute", MELEE + "loot:\n  - {item: minecraft:iron_ingots}\n")
    assert_problem(repo, "loot.0.item: unknown id 'minecraft:iron_ingots'")


def test_sounds_must_exist(repo: RepoPaths) -> None:
    mob(repo, "hewn_brute", MELEE + "sounds: {hurt: minecraft:block.stone.hurt}\n")
    assert_problem(repo, "sounds.hurt: unknown id 'minecraft:block.stone.hurt'")


def caster(spell: str, keep_distance: float = 6) -> str:
    return MELEE.replace("archetype: melee", "archetype: caster") + (
        f"caster: {{spells: [{spell}], keep_distance: {keep_distance}}}\n"
    )


def test_a_casters_spells_must_exist(repo: RepoPaths) -> None:
    mob(repo, "hewn_brute", caster("realmforge:stone_song"))
    assert_problem(repo, "caster.spells.0: unknown id 'realmforge:stone_song'")


def test_a_caster_casts_only_at_enemies(repo: RepoPaths) -> None:
    mob(repo, "hewn_brute", caster("realmforge:test_mend"))
    assert_problem(repo, "caster.spells.0: realmforge:test_mend targets realmforge:")


def test_a_casters_spells_reach_past_the_distance_it_keeps(repo: RepoPaths) -> None:
    mob(repo, "hewn_brute", caster("realmforge:shard_song", keep_distance=16))
    assert_problem(repo, "realmforge:shard_song reaches 16 blocks, not past the 16 blocks the mob keeps")


def test_a_mob_cannot_take_a_hand_written_entitys_name(repo: RepoPaths) -> None:
    mob(repo, "spell_projectile", MELEE)
    assert_problem(repo, "already names an entity type spell_projectile: both would be entity.realmforge.")


def test_generation_writes_registrations_loot_and_assets(repo: RepoPaths) -> None:
    mob(
        repo,
        "hewn_brute",
        MELEE
        + "idle: guard\nsounds: {death: minecraft:block.stone.break}\nloot:\n"
        + "  - {item: minecraft:iron_ingot, count: {min: 1, max: 2}, chance_percent: 40}\n"
        + "  - {item: minecraft:raw_gold}\n",
    )
    files = generate(repo)

    registrations = files[REGISTRATIONS].decode()
    assert "EntityType<MeleeMob>> HEWN_BRUTE" in registrations
    assert "new MeleeMob(type, level, HEWN_BRUTE_SPEC)" in registrations
    assert "builder.sized(0.9F, 2.2F)" in registrations
    assert "CustomMobSpec.Idle.GUARD" in registrations
    assert 'CustomMobSpec.sound("minecraft:block.stone.break")' in registrations
    assert ".add(Attributes.MAX_HEALTH, 30.0)" in registrations
    assert ".add(Attributes.ARMOR, 0.0)" in registrations

    loot = json.loads(files[f"{LOOT}/hewn_brute.json"])
    assert loot["type"] == "minecraft:entity"
    assert loot["random_sequence"] == "realmforge:entities/hewn_brute"
    ingots, gold = loot["pools"]
    assert ingots["entries"][0]["functions"][0]["count"] == {
        "type": "minecraft:uniform",
        "min": 1.0,
        "max": 2.0,
    }
    assert ingots["conditions"] == [{"condition": "minecraft:random_chance", "chance": 0.4}]
    assert gold["entries"] == [{"type": "minecraft:item", "name": "minecraft:raw_gold"}]
    assert "conditions" not in gold

    assert json.loads(files[LANG])["entity.realmforge.hewn_brute"] == "Hewn Brute"
    expectations = files[EXPECTATIONS].decode()
    assert '"realmforge:hewn_brute"' in expectations
    assert 'new DropExpectation("minecraft:iron_ingot", 1, 2, 0.4)' in expectations

    texture = files[f"{ASSETS}/textures/entity/hewn_brute.png"]
    assert struct.unpack(">II", texture[16:24]) == (64, 64)
    animations = json.loads(files[f"{ASSETS}/geckolib/animations/entity/hewn_brute.animation.json"])
    assert set(animations["animations"]) == {"misc.idle", "move.walk", "attack.swing", "attack.cast"}
    geometry = json.loads(files[f"{ASSETS}/geckolib/models/entity/hewn_brute.geo.json"])[
        "minecraft:geometry"
    ][0]
    assert geometry["description"]["identifier"] == "geometry.realmforge.hewn_brute"
    assert [bone["name"] for bone in geometry["bones"]] == list(BONES)


@pytest.mark.parametrize(
    "size", [{"width": 0.3, "height": 0.3}, {"width": 0.9, "height": 2.2}, {"width": 4, "height": 6}]
)
def test_the_placeholder_model_fills_the_hitbox_and_fits_its_texture(size: dict) -> None:
    model = placeholder_model(mob_model(size=size))
    top = max(cube.origin[1] + cube.size[1] for cube in model.cubes)
    assert top == round(size["height"] * 16)
    assert model.texture_size in TEXTURE_SIZES
    for cube in model.cubes:
        width, height, depth = cube.size
        assert cube.uv[0] + 2 * (width + depth) <= model.texture_size
        assert cube.uv[1] + depth + height <= model.texture_size
    assert all(0 <= x < model.texture_size and 0 <= y < model.texture_size for x, y in model.eyes())


def test_no_mobs_still_compile(repo: RepoPaths) -> None:
    for file in (repo.content / "mobs").glob("*.yaml"):
        file.unlink()
    files = generate(repo)
    registrations = files[REGISTRATIONS].decode()
    assert "ALL =\n            List.of();" in registrations
    assert "MOBS = List.of();" in files[EXPECTATIONS].decode()
    assert not any(path.startswith(LOOT) for path in files)
