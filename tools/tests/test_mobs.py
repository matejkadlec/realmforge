"""Custom mobs (content/mobs/): validation and generation (architecture 14.2; W3, RLM-33)."""

from __future__ import annotations

import json
import struct

import pytest

from realmforge_tools.formats.png import Rgba, decode_rgba
from realmforge_tools.generate import generate_all
from realmforge_tools.generate.mobs import (
    BONES,
    MOSS_CLUMP,
    TEXTURE_SIZES,
    WHITE,
    PlaceholderModel,
    animations,
    placeholder_model,
    placeholder_texture,
)
from realmforge_tools.problems import ValidationFailed
from realmforge_tools.project import RepoPaths
from realmforge_tools.schema.mob import TRASH_LOOT, MobDef
from realmforge_tools.validate import load_and_validate
from tests.conftest import write

LANG = "mod/src/generated/resources/assets/realmforge/lang/en_us.json"
ASSETS = "mod/src/generated/resources/assets/realmforge"
LOOT = "mod/src/generated/resources/data/realmforge/loot_table/entities"
TAGS = "mod/src/generated/resources/data/realmforge/tags/entity_type"
REGISTRATIONS = "mod/src/generated/java/io/github/matejkadlec/realmforge/generated/GeneratedMobs.java"
EXPECTATIONS = (
    "qa-mod/src/generated/java/io/github/matejkadlec/realmforge/qa/generated/GeneratedMobExpectations.java"
)

MELEE = """name: {en_us: Hewn Brute}
archetype: melee
size: {width: 0.9, height: 2.2}
attributes: {max_health: 30, attack_damage: 5, movement_speed: 0.2}
model: {color: '#8C8577'}
"""

VARIANTS = """variants:
  - {id: sapphire, accent: '#2F64D6', loot: [{item: minecraft:diamond, chance_percent: 20, looting_chance_percent: 5}]}
  - {id: ruby, accent: '#C42638', weight: 2}
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
        "model": {"color": "#8C8577"},
        **fields,
    }
    return MobDef.model_validate(data)


def test_repository_mobs_are_the_hewn() -> None:
    content, universe = load_and_validate(RepoPaths())
    mobs = {entry.model.id: entry.model for entry in content.mobs}
    assert {mob_id: model.archetype for mob_id, model in mobs.items()} == {
        "hewn_chanter": "caster",
        "hewn_sentinel": "melee",
    }
    # Matěj's playtest, 2026-10-23: about 3 blocks tall, Undervault's stone creatures, Sentinels of three gems.
    assert all(model.size.height == 3 for model in mobs.values())
    assert all(model.tags == ["realmforge:stone_creatures"] for model in mobs.values())
    assert [variant.id for variant in mobs["hewn_sentinel"].variants] == ["sapphire", "ruby", "topaz"]
    assert universe.contains("minecraft:sound_event", "realmforge:entity.hewn_sentinel.step")


@pytest.mark.parametrize(
    ("fields", "expected"),
    [
        ({"archetype": "caster"}, "a caster names its spells"),
        (
            {"caster": {"spells": ["realmforge:stoneform"], "kiting": {"step_blocks": 2}}},
            "caster is only for archetype caster, not melee",
        ),
        (
            {
                "archetype": "caster",
                "caster": {"spells": ["realmforge:stoneform"]},
                "melee": {"attack_interval_seconds": 1.5},
            },
            "melee is only for archetype melee, not caster",
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
        (
            {"loot": [{"item": "minecraft:iron_ingot", "looting_chance_percent": 5}]},
            "looting_chance_percent raises a chance below 100 percent",
        ),
        ({"idle": "wander"}, "Input should be 'stroll' or 'guard'"),
        ({"melee": {"attack_interval_seconds": 0.1}}, "attack_interval_seconds is 0.25 to 5 seconds"),
        ({"melee": {"knock_up_blocks": 4}}, "less than or equal to 3"),
        ({"variants": [{"id": "ruby", "accent": "#C42638"}]}, "a mob of one kind needs no variants"),
        (
            {"variants": [{"id": "ruby", "accent": "#C42638"}, {"id": "ruby", "accent": "#C42639"}]},
            "one variant per id: ruby",
        ),
        (
            {
                "loot": [{"item": "minecraft:diamond"}],
                "variants": [
                    {"id": "ruby", "accent": "#C42638", "loot": [{"item": "minecraft:diamond"}]},
                    {"id": "topaz", "accent": "#E3A92B"},
                ],
            },
            "one drop per item: minecraft:diamond",
        ),
        ({"model": {"color": "#8C8577", "eyes": "gem"}}, "gem eyes take their pupils from the variants"),
        ({"model": {"color": "#8C8577", "build": "giant"}}, "Input should be 'humanoid', 'slim' or 'golem'"),
        ({"sounds": {"step": "quiet"}}, "sounds.step"),
    ],
)
def test_invalid_mobs_are_rejected(fields: dict, expected: str) -> None:
    with pytest.raises(ValueError, match=expected):
        mob_model(**fields)


def test_a_mob_drops_no_trash(repo: RepoPaths) -> None:
    assert "minecraft:iron_nugget" in TRASH_LOOT
    mob(repo, "hewn_brute", MELEE + "loot:\n  - {item: minecraft:iron_nugget, count: {min: 1, max: 3}}\n")
    assert_problem(repo, "loot.0.item: minecraft:iron_nugget is trash loot")


def test_a_variants_loot_is_checked_too(repo: RepoPaths) -> None:
    mob(
        repo,
        "hewn_brute",
        MELEE
        + "variants:\n  - {id: a, accent: '#000000', loot: [{item: minecraft:iron_ingots}]}\n"
        + "  - {id: b, accent: '#FFFFFF'}\n",
    )
    assert_problem(repo, "variants.0.loot.0.item: unknown id 'minecraft:iron_ingots'")


def test_loot_items_must_exist(repo: RepoPaths) -> None:
    mob(repo, "hewn_brute", MELEE + "loot:\n  - {item: minecraft:iron_ingots}\n")
    assert_problem(repo, "loot.0.item: unknown id 'minecraft:iron_ingots'")


def test_sounds_must_exist(repo: RepoPaths) -> None:
    mob(repo, "hewn_brute", MELEE + "sounds: {hurt: minecraft:block.stone.hurt}\n")
    assert_problem(repo, "sounds.hurt: unknown id 'minecraft:block.stone.hurt'")


def test_a_mob_may_walk_silently(repo: RepoPaths) -> None:
    mob(repo, "hewn_brute", MELEE + "sounds: {step: silent, hurt: realmforge:entity.hewn_sentinel.hurt}\n")
    registrations = generate(repo)[REGISTRATIONS].decode()
    assert 'CustomMobSpec.id("realmforge:entity.hewn_sentinel.hurt")' in registrations
    assert "Optional.empty(), true)," in registrations


def test_death_particles_name_a_block(repo: RepoPaths) -> None:
    mob(repo, "hewn_brute", MELEE + "death_particles: realmforge:vaultstones\n")
    assert_problem(repo, "death_particles: unknown id 'realmforge:vaultstones'")


def test_a_mob_joins_only_our_tags(repo: RepoPaths) -> None:
    mob(repo, "hewn_brute", MELEE + "tags: [minecraft:undead]\n")
    assert_problem(repo, "tags.0: minecraft:undead: a mob joins our own entity type tags (realmforge:)")


def caster(spell: str, step: float = 2) -> str:
    return MELEE.replace("archetype: melee", "archetype: caster") + (
        f"caster: {{spells: [{spell}], kiting: {{step_blocks: {step}}}}}\n"
    )


def test_a_casters_spells_must_exist(repo: RepoPaths) -> None:
    mob(repo, "hewn_brute", caster("realmforge:stone_song"))
    assert_problem(repo, "caster.spells.0: unknown id 'realmforge:stone_song'")


def test_a_caster_casts_only_at_enemies(repo: RepoPaths) -> None:
    mob(repo, "hewn_brute", caster("realmforge:test_mend"))
    assert_problem(repo, "caster.spells.0: realmforge:test_mend targets realmforge:")


def test_a_casters_spells_reach_past_one_kiting_step(repo: RepoPaths) -> None:
    write(
        repo.content / "spells" / "test_poke.yaml",
        "id: test_poke\nname: {en_us: Poke}\ndescription: {en_us: Pokes.}\nschool: realmforge:arcane\n"
        "scales_with: spell_power\ntargeting: {type: realmforge:current_target, range: 2}\ncast: {type: instant}\n"
        "npc_only: true\neffects:\n  - {type: realmforge:damage, amount: 1}\n",
    )
    mob(repo, "hewn_brute", caster("realmforge:test_poke", step=3))
    assert_problem(
        repo, "realmforge:test_poke reaches 2 blocks, no farther than one 3-block step of its kiting"
    )


def test_a_mob_cannot_take_a_hand_written_entitys_name(repo: RepoPaths) -> None:
    mob(repo, "spell_projectile", MELEE)
    assert_problem(repo, "already names an entity type spell_projectile: both would be entity.realmforge.")


def test_generation_writes_registrations_loot_and_assets(repo: RepoPaths) -> None:
    mob(
        repo,
        "hewn_brute",
        MELEE
        + "idle: guard\nmelee: {attack_interval_seconds: 1.5, knock_up_blocks: 0.5}\n"
        + "sounds: {death: minecraft:block.stone.break}\ndeath_particles: realmforge:vaultstone\n"
        + "tags: [realmforge:stone_creatures]\nloot:\n"
        + "  - {item: minecraft:iron_ingot, count: {min: 1, max: 2}, chance_percent: 40, looting_count: 1}\n"
        + "  - {item: minecraft:raw_gold}\n",
    )
    files = generate(repo)

    registrations = files[REGISTRATIONS].decode()
    assert "EntityType<MeleeMob>> HEWN_BRUTE" in registrations
    assert "new MeleeMob(type, level, HEWN_BRUTE_SPEC)" in registrations
    assert "builder.sized(0.9F, 2.2F)" in registrations
    assert "CustomMobSpec.Idle.GUARD" in registrations
    assert 'CustomMobSpec.id("minecraft:block.stone.break")' in registrations
    assert 'CustomMobSpec.id("realmforge:vaultstone")' in registrations
    assert "new CustomMobSpec.Melee(30, 0.5)" in registrations
    assert ".add(Attributes.MAX_HEALTH, 30.0)" in registrations
    assert ".add(Attributes.ARMOR, 0.0)" in registrations

    loot = json.loads(files[f"{LOOT}/hewn_brute.json"])
    assert loot["type"] == "minecraft:entity"
    assert loot["random_sequence"] == "realmforge:entities/hewn_brute"
    ingots, gold = loot["pools"]
    assert ingots["entries"][0]["functions"] == [
        {
            "function": "minecraft:set_count",
            "count": {"type": "minecraft:uniform", "min": 1.0, "max": 2.0},
            "add": False,
        },
        {
            "function": "minecraft:enchanted_count_increase",
            "enchantment": "minecraft:looting",
            "count": {"type": "minecraft:uniform", "min": 0.0, "max": 1.0},
        },
    ]
    assert ingots["conditions"] == [{"condition": "minecraft:random_chance", "chance": 0.4}]
    assert gold["entries"] == [{"type": "minecraft:item", "name": "minecraft:raw_gold"}]
    assert "conditions" not in gold
    assert "realmforge:hewn_brute" in json.loads(files[f"{TAGS}/stone_creatures.json"])["values"]

    assert json.loads(files[LANG])["entity.realmforge.hewn_brute"] == "Hewn Brute"
    expectations = files[EXPECTATIONS].decode()
    assert '"realmforge:hewn_brute"' in expectations
    assert 'new DropExpectation("minecraft:iron_ingot", 1, 2, 0.4, 1, 0.0)' in expectations

    texture = files[f"{ASSETS}/textures/entity/hewn_brute.png"]
    assert struct.unpack(">II", texture[16:24]) == (64, 64)
    animation = json.loads(files[f"{ASSETS}/geckolib/animations/entity/hewn_brute.animation.json"])
    assert set(animation["animations"]) == {"misc.idle", "move.walk", "attack.swing", "attack.cast"}
    geometry = json.loads(files[f"{ASSETS}/geckolib/models/entity/hewn_brute.geo.json"])[
        "minecraft:geometry"
    ][0]
    assert geometry["description"]["identifier"] == "geometry.realmforge.hewn_brute"
    assert [bone["name"] for bone in geometry["bones"]] == list(BONES)


def test_variants_get_their_texture_and_loot_table(repo: RepoPaths) -> None:
    mob(
        repo,
        "hewn_brute",
        MELEE.replace("model: {color: '#8C8577'}", "model: {color: '#8C8577', build: golem, eyes: gem}")
        + "loot: [{item: minecraft:iron_ingot}]\n"
        + VARIANTS,
    )
    files = generate(repo)
    assert f"{ASSETS}/textures/entity/hewn_brute.png" not in files
    assert f"{ASSETS}/textures/entity/hewn_brute_sapphire.png" in files
    assert (
        files[f"{ASSETS}/textures/entity/hewn_brute_sapphire.png"]
        != files[f"{ASSETS}/textures/entity/hewn_brute_ruby.png"]
    )
    sapphire = json.loads(files[f"{LOOT}/hewn_brute/sapphire.json"])
    assert sapphire["random_sequence"] == "realmforge:entities/hewn_brute/sapphire"
    iron, diamond = sapphire["pools"]
    assert iron["entries"] == [{"type": "minecraft:item", "name": "minecraft:iron_ingot"}]
    assert diamond["conditions"] == [
        {
            "condition": "minecraft:random_chance_with_enchanted_bonus",
            "enchantment": "minecraft:looting",
            "unenchanted_chance": 0.2,
            "enchanted_chance": {"type": "minecraft:linear", "base": 0.25, "per_level_above_first": 0.05},
        }
    ]
    ruby = json.loads(files[f"{LOOT}/hewn_brute/ruby.json"])
    assert len(ruby["pools"]) == 1
    # The type's own table keeps the drops every variant shares.
    assert len(json.loads(files[f"{LOOT}/hewn_brute.json"])["pools"]) == 1
    registrations = files[REGISTRATIONS].decode()
    assert 'new CustomMobSpec.Variant("sapphire", 1), new CustomMobSpec.Variant("ruby", 2)' in registrations
    expectations = files[EXPECTATIONS].decode()
    assert 'new VariantExpectation("ruby", 2, "realmforge:entities/hewn_brute/ruby",' in expectations


def test_a_caster_kites(repo: RepoPaths) -> None:
    mob(repo, "hewn_brute", caster("realmforge:stoneform"))
    registrations = generate(repo)[REGISTRATIONS].decode()
    assert "List.of(GeneratedSpells.STONEFORM), Optional.of(new KiteRules(2.0, 1.5))" in registrations


@pytest.mark.parametrize("build", ["humanoid", "slim", "golem"])
@pytest.mark.parametrize(
    "size", [{"width": 0.3, "height": 0.3}, {"width": 0.9, "height": 2.2}, {"width": 4, "height": 6}]
)
def test_the_placeholder_model_fills_the_hitbox_and_fits_its_texture(size: dict, build: str) -> None:
    model = placeholder_model(mob_model(size=size, model={"color": "#8C8577", "build": build}))
    top = max(cube.origin[1] + cube.size[1] for cube in model.cubes)
    assert top == round(size["height"] * 16)
    assert model.texture_size in TEXTURE_SIZES
    for cube in model.cubes:
        width, height, depth = cube.size
        assert min(cube.size) >= 1
        assert cube.uv[0] + 2 * (width + depth) <= model.texture_size
        assert cube.uv[1] + depth + height <= model.texture_size
    for eye_size in ((1, 1), (1, 2), (2, 3)):
        for x, y in model.eye_corners(*eye_size):
            assert x >= 0 and x + eye_size[0] <= model.texture_size
            assert y >= 0 and y + eye_size[1] <= model.texture_size


def test_a_golem_has_shells_and_moss_and_long_arms() -> None:
    model = placeholder_model(
        mob_model(size={"width": 1.2, "height": 3}, model={"color": "#8C8577", "build": "golem"})
    )
    materials = [cube.material for cube in model.cubes]
    assert materials.count("shell") == 2
    assert materials.count("moss") == 7
    arm = next(cube for cube in model.cubes if cube.bone == "left_arm")
    body = next(cube for cube in model.cubes if cube.bone == "body" and cube.material == "surface")
    # As thick as a player's arm, and as long as an iron golem's: well past the body.
    assert arm.size[0] == 4 and arm.size[1] > body.size[1]


def test_a_slim_mob_is_no_wider_than_its_hitbox() -> None:
    model = placeholder_model(
        mob_model(size={"width": 0.7, "height": 3}, model={"color": "#8C8577", "build": "slim"})
    )
    half_width = max(max(abs(cube.origin[0]), abs(cube.origin[0] + cube.size[0])) for cube in model.cubes)
    assert half_width <= 0.7 * 16 / 2 + 1
    legs = next(cube for cube in model.cubes if cube.bone == "left_leg")
    assert legs.size[1] > 3 * 16 / 2


def test_the_cast_animation_holds_the_hands_in_front_of_the_forehead() -> None:
    cast = animations(mob_model())["animations"]["attack.cast"]["bones"]
    for arm in ("left_arm", "right_arm"):
        pitches = [rotation[0] for rotation in cast[arm]["rotation"].values()]
        # Forward and up toward the head (-90 is straight ahead, -180 straight up), about 15 degrees higher than the
        # first version (Matěj's playtest, 2026-10-24), and still not above the head.
        assert all(-130 <= pitch <= -115 for pitch in pitches), pitches


@pytest.mark.parametrize(
    ("melee", "seconds"), [({}, 0.5), ({"melee": {"attack_interval_seconds": 1.5}}, 0.75)]
)
def test_the_attack_swing_lasts_half_the_attack_interval(melee: dict, seconds: float) -> None:
    # Matěj's playtest, 2026-10-24: a heavy stone swing, not vanilla's quick one; the hits stay as often.
    swing = animations(mob_model(**melee))["animations"]["attack.swing"]
    assert swing["animation_length"] == seconds
    assert max(float(time) for time in swing["bones"]["right_arm"]["rotation"]) == seconds


def sentinel_texture(variant: str) -> tuple[MobDef, PlaceholderModel, dict[tuple[int, int], Rgba]]:
    content, _ = load_and_validate(RepoPaths())
    sentinel = next(entry.model for entry in content.mobs if entry.model.id == "hewn_sentinel")
    model = placeholder_model(sentinel)
    chosen = next(entry for entry in sentinel.variants if entry.id == variant)
    width, _, pixels = decode_rgba(placeholder_texture(sentinel, model, chosen))
    return sentinel, model, {(n % width, n // width): pixel for n, pixel in enumerate(pixels) if pixel[3]}


def green(pixel: Rgba) -> bool:
    return pixel[1] > pixel[0] and pixel[1] > pixel[2]


@pytest.mark.parametrize("variant", ["sapphire", "ruby", "topaz"])
def test_a_sentinels_eyes_are_matejs_edit(variant: str) -> None:
    # Each eye 2 x 3: a white column outside, the gem's column inside, two lighter pixels above a darker one.
    _, model, pixels = sentinel_texture(variant)
    for n, (x, y) in enumerate(model.eye_corners(2, 3)):
        white, gem = (x, x + 1) if n == 0 else (x + 1, x)
        assert all(pixels[(white, y + dy)][:3] == WHITE for dy in range(3))
        top, middle, bottom = (pixels[(gem, y + dy)] for dy in range(3))
        assert top == middle and sum(bottom[:3]) < sum(top[:3])


@pytest.mark.parametrize("variant", ["sapphire", "ruby", "topaz"])
def test_a_sentinels_moss_grows_in_clumps(variant: str) -> None:
    # Matěj's playtest, 2026-10-24: moss as on mossy stone bricks, never a lone green pixel.
    _, _, pixels = sentinel_texture(variant)
    left = {at for at, pixel in pixels.items() if green(pixel)}
    while left:
        clump, edge = set(), [left.pop()]
        while edge:
            x, y = edge.pop()
            clump.add((x, y))
            for near in ((x + 1, y), (x - 1, y), (x, y + 1), (x, y - 1)):
                if near in left:
                    left.remove(near)
                    edge.append(near)
        assert len(clump) >= MOSS_CLUMP, sorted(clump)


@pytest.mark.parametrize("variant", ["sapphire", "ruby", "topaz"])
def test_a_sentinels_stone_has_many_shades_and_no_black(variant: str) -> None:
    # About ten shades of grey as vanilla's stone bricks, and grey mortar and dark grey cracks, never black.
    _, _, pixels = sentinel_texture(variant)
    stone = {pixel for pixel in pixels.values() if not green(pixel) and pixel[:3] != WHITE}
    assert len(stone) >= 10
    assert min(max(pixel[:3]) for pixel in stone) >= 48


def test_no_mobs_still_compile(repo: RepoPaths) -> None:
    for file in (repo.content / "mobs").glob("*.yaml"):
        file.unlink()
    # Stoneform spares the Hewn's tag, which no mob would fill.
    (repo.content / "spells" / "stoneform.yaml").unlink()
    files = generate(repo)
    registrations = files[REGISTRATIONS].decode()
    assert "ALL =\n            List.of();" in registrations
    assert "MOBS = List.of();" in files[EXPECTATIONS].decode()
    assert not any(path.startswith(LOOT) or path.startswith(TAGS) for path in files)
