"""The spell visuals kit (RLM-639, RLM-640, ADR 0073, ADR 0075): looks, models, trajectories and particles as
content, a spell's ``visuals``, the models of cubes from ``art/models/spell/``, and what ``rf gen`` writes for
them."""

from __future__ import annotations

import itertools
import json

import pydantic
import pytest

from realmforge_tools.art import spell_models
from realmforge_tools.formats.png import decode_rgba
from realmforge_tools.generate import generate_all
from realmforge_tools.problems import ValidationFailed
from realmforge_tools.project import RepoPaths
from realmforge_tools.schema.spell import SpellDef
from realmforge_tools.schema.spell_visuals import ParticleDef, SpellLookDef, SpellModelDef, SpellTrajectoryDef
from realmforge_tools.validate import load_and_validate
from tests.conftest import write

DATA = "mod/src/generated/resources/data/realmforge/realmforge"
ASSETS = "mod/src/generated/resources/assets/realmforge"
PARTICLES_JAVA = "mod/src/generated/java/io/github/matejkadlec/realmforge/generated/GeneratedParticles.java"
EXPECTATIONS = (
    "qa-mod/src/generated/java/io/github/matejkadlec/realmforge/qa/generated/"
    "GeneratedSpellVisualsExpectations.java"
)

BOLT = (
    "name: {en_us: Bolt}\n"
    "description: {en_us: A bolt.}\n"
    "school: realmforge:frost\n"
    "scales_with: spell_power\n"
    "targeting: {type: realmforge:direction, range: 32}\n"
)
PROJECTILE = (
    "effects:\n"
    "  - type: realmforge:projectile\n"
    "    speed: 20\n"
    "    model: realmforge:orb\n"
    "    effects:\n"
    "      - {type: realmforge:damage, amount: 3}\n"
)
FRAME = ["........", "...ff...", "..ffff..", ".ffffff.", ".ffffff.", "..ffff..", "...ff...", "........"]


def problems_for(repo: RepoPaths) -> list[str]:
    with pytest.raises(ValidationFailed) as excinfo:
        load_and_validate(repo)
    return [str(p) for p in excinfo.value.problems]


def generate(repo: RepoPaths) -> dict[str, bytes]:
    content, _ = load_and_validate(repo)
    return {repo.rel(path): data for path, data in generate_all(content, repo).items()}


def spell(repo: RepoPaths, body: str, name: str = "test_shot") -> None:
    write(repo.content / "spells" / f"{name}.yaml", f"id: {name}\n{BOLT}{body}")


# The repository's kit


def test_the_repository_has_the_first_looks_two_models_and_one_trajectory() -> None:
    content, _ = load_and_validate(RepoPaths())
    assert {entry.model.id for entry in content.spell_looks} == {"frost", "fire", "arcane", "stone"}
    assert {entry.model.id: entry.model.shape for entry in content.spell_models} == {
        "orb": "orb",
        "chunk": "cubes",
    }
    assert {entry.model.id: entry.model.kind for entry in content.spell_trajectories} == {
        "straight": "straight"
    }
    assert {entry.model.id for entry in content.particles} == {"mote", "shard", "ember", "fleck"}


def test_the_frost_look_is_inside_matejs_range() -> None:
    # Matěj, 2026-10-29 (RLM-463): #B8E2F4 (lightest) through #8DCFEC to #6ABCE2 (darkest), 12 to 16 colours.
    content, _ = load_and_validate(RepoPaths())
    frost = next(entry.model for entry in content.spell_looks if entry.model.id == "frost")
    assert 12 <= len(frost.colors) <= 16
    assert frost.colors[0] == "#B8E2F4"
    assert "#8DCFEC" in frost.colors
    assert frost.colors[-1] == "#6ABCE2"
    channels = [[int(color[i : i + 2], 16) for i in (1, 3, 5)] for color in frost.colors]
    for lighter, darker in itertools.pairwise(channels):
        assert all(a >= b for a, b in zip(lighter, darker, strict=True)), "lightest first, darker and darker"


def test_no_spell_names_the_old_visual_fields() -> None:
    # RLM-639: appearance, impact and forms_projectile gave way to visuals, model and trajectory.
    for file in sorted((RepoPaths().content / "spells").glob("*.yaml")):
        data = "\n".join(
            line
            for line in file.read_text(encoding="utf-8").splitlines()
            if not line.lstrip().startswith("#")
        )
        for old in ("appearance:", "impact:", "forms_projectile"):
            assert old not in data, f"{file.name} still names {old}"


def test_the_twins_share_a_model_and_a_trajectory_and_differ_only_in_their_look(repo: RepoPaths) -> None:
    # RLM-639's "Done when": purely in data.
    files = generate(repo)
    frost = json.loads(files[f"{DATA}/spell/test_twin_frost.json"])
    fire = json.loads(files[f"{DATA}/spell/test_twin_fire.json"])
    assert frost["effects"][0]["model"] == fire["effects"][0]["model"] == "realmforge:orb"
    assert frost["effects"][0]["trajectory"] == fire["effects"][0]["trajectory"] == "realmforge:straight"
    assert frost.pop("visuals") == {"look": "realmforge:frost"}
    assert fire.pop("visuals") == {"look": "realmforge:fire"}
    for data in (frost, fire):
        # Their texts are a spell's own: translation keys by id.
        del data["name"], data["description"]
    assert frost == fire
    # And the two looks differ in what a look holds: the material, the particles, the sound.
    frost_look = json.loads(files[f"{DATA}/spell_look/frost.json"])
    fire_look = json.loads(files[f"{DATA}/spell_look/fire.json"])
    for part in ("colors", "trail", "burst", "impact_sound"):
        assert frost_look[part] != fire_look[part], part
    assert (
        files[f"{ASSETS}/textures/spell/material/frost.png"]
        != files[f"{ASSETS}/textures/spell/material/fire.png"]
    )


# What rf gen writes


def test_a_look_becomes_a_datapack_entry_and_a_placeholder_material(repo: RepoPaths) -> None:
    write(
        repo.content / "spell_looks" / "shadow.yaml",
        "id: shadow\n"
        "colors: ['#2a1a3a', '#120818']\n"
        "trail: {particle: realmforge:mote, per_block: 1.5}\n"
        "burst: {particle: realmforge:shard, count: 8}\n"
        "impact_sound: minecraft:block.glass.break\n",
    )
    files = generate(repo)
    assert json.loads(files[f"{DATA}/spell_look/shadow.json"]) == {
        "colors": ["#2A1A3A", "#120818"],
        "trail": {"particle": "realmforge:mote", "per_block": 1.5},
        "burst": {"particle": "realmforge:shard", "count": 8},
        "light": "world",
        "impact_sound": "minecraft:block.glass.break",
    }
    # Frost swirls while it forms and glows a little; fire is drawn at full brightness (RLM-640).
    frost = json.loads(files[f"{DATA}/spell_look/frost.json"])
    assert frost["swirl"] == {"particle": "realmforge:fleck", "per_tick": 6.0}
    assert frost["light"] == "glow"
    assert json.loads(files[f"{DATA}/spell_look/fire.json"])["light"] == "emit"
    width, height, pixels = decode_rgba(files[f"{ASSETS}/textures/spell/material/shadow.png"])
    assert (width, height) == (16, 16)
    # Noise in the look's colours, opaque, so it tiles and reads as the element.
    assert set(pixels) == {(0x2A, 0x1A, 0x3A, 255), (0x12, 0x08, 0x18, 255)}
    assert (
        files[f"{ASSETS}/textures/spell/material/shadow.png"]
        == generate(repo)[f"{ASSETS}/textures/spell/material/shadow.png"]
    ), "the same on every run"


def test_a_model_and_a_trajectory_become_datapack_entries(repo: RepoPaths) -> None:
    write(
        repo.content / "spell_models" / "dart.yaml",
        "id: dart\nshape: orb\nscale: 0.6\norientation: forward\nspin_degrees_per_second: 360\n",
    )
    files = generate(repo)
    assert json.loads(files[f"{DATA}/spell_model/dart.json"]) == {
        "shape": "orb",
        "scale": 0.6,
        "orientation": "forward",
        "spin_degrees_per_second": 360,
    }
    assert json.loads(files[f"{DATA}/spell_model/orb.json"]) == {
        "shape": "orb",
        "scale": 1.0,
        "orientation": "tumble",
        "spin_degrees_per_second": 0.0,
    }
    assert json.loads(files[f"{DATA}/spell_trajectory/straight.json"]) == {"kind": "straight"}


def test_a_particle_becomes_a_registration_a_sprite_set_and_its_frames(repo: RepoPaths) -> None:
    rows = "".join(f'    - "{row}"\n' for row in FRAME)
    write(
        repo.content / "particles" / "spark.yaml",
        "id: spark\n"
        f"frames:\n  -\n{rows}  -\n{rows.replace('f', '8')}"
        "lifetime_ticks: [4, 9]\nsize: 0.05\ngravity: 0.25\nfriction: 0.9\nglows: false\n",
    )
    files = generate(repo)
    assert json.loads(files[f"{ASSETS}/particles/spark.json"]) == {
        "textures": ["realmforge:spark_0", "realmforge:spark_1"]
    }
    width, height, pixels = decode_rgba(files[f"{ASSETS}/textures/particle/spark_0.png"])
    assert (width, height) == (8, 8)
    assert pixels[0] == (0, 0, 0, 0), "a dot is transparent"
    assert pixels[1 * 8 + 3] == (255, 255, 255, 255), "f is white, which a spawn's colour tints"
    assert decode_rgba(files[f"{ASSETS}/textures/particle/spark_1.png"])[2][1 * 8 + 3] == (136, 136, 136, 255)
    java = files[PARTICLES_JAVA].decode()
    assert (
        "    public static final TintedParticles.Entry SPARK =\n"
        '            register("spark",\n'
        "                    new TintedParticles.Motion(4, 9, 0.05F, 0.25F, 0.9F, false));\n"
    ) in java
    assert "ALL = List.of(EMBER, FLECK, MOTE, SHARD, SPARK);" in java
    assert 'new ParticleExpectation("realmforge:spark", 2, 4, 9)' in files[EXPECTATIONS].decode()


def test_the_expectations_hold_every_part(repo: RepoPaths) -> None:
    expectations = generate(repo)[EXPECTATIONS].decode()
    assert (
        "            new LookExpectation(\n"
        '                    "realmforge:stone",\n'
        '                    List.of("#B4B0A8", "#A09C94", "#8D9094", "#8B8579", "#74706A", "#5E5B57"),\n'
        "                    Optional.empty(),\n"
        "                    0.0,\n"
        '                    Optional.of("realmforge:shard"),\n'
        "                    14,\n"
        '                    Optional.of("realmforge:shard"),\n'
        "                    3.0,\n"
        '                    "world",\n'
        '                    Optional.of("realmforge:spell.stoneform.impact"))'
    ) in expectations
    assert 'new ModelExpectation("realmforge:orb", "orb", 1.0, "tumble", 0.0)' in expectations
    assert 'new ModelExpectation("realmforge:chunk", "cubes", 0.6, "forward", 0.0)' in expectations
    assert 'new TrajectoryExpectation("realmforge:straight", "straight")' in expectations


def test_a_spells_visuals_reach_its_data_and_its_expectations(repo: RepoPaths) -> None:
    files = generate(repo)
    boulder = json.loads(files[f"{DATA}/spell/astone_boulder.json"])
    assert boulder["visuals"] == {
        "look": "realmforge:stone",
        "forming": "grow",
        "impact_sound": "realmforge:spell.astone_boulder.impact",
    }
    assert "forms_projectile" not in boulder["cast"]
    assert boulder["effects"][0]["model"] == "realmforge:orb"
    missiles = json.loads(files[f"{DATA}/spell/arcane_missiles.json"])
    assert missiles["visuals"] == {"look": "realmforge:arcane", "forming": "hands"}
    # A spell without visuals has none in its data, and its projectile still names how it flies.
    missile = json.loads(files[f"{DATA}/spell/test_missile.json"])
    assert "visuals" not in missile
    assert "model" not in missile["effects"][0]
    assert missile["effects"][0]["trajectory"] == "realmforge:straight"
    assert not any(path.startswith(f"{ASSETS}/textures/entity/spell/") for path in files), (
        "no texture per spell any more: a look's material serves every spell of it"
    )


# Validation: a spell that names a missing part is refused


@pytest.mark.parametrize(
    ("body", "expected"),
    [
        (
            "cast: {type: instant}\nvisuals: {look: realmforge:frosty}\n" + PROJECTILE,
            "visuals.look: unknown id 'realmforge:frosty' in registry realmforge:spell_look "
            "(did you mean: realmforge:frost",
        ),
        (
            "cast: {type: instant}\nvisuals: {look: realmforge:frost}\n"
            + PROJECTILE.replace("realmforge:orb", "realmforge:ice_chunk"),
            "effects.0.model: unknown id 'realmforge:ice_chunk' in registry realmforge:spell_model",
        ),
        (
            "cast: {type: instant}\nvisuals: {look: realmforge:frost}\n"
            + PROJECTILE.replace("    model: realmforge:orb\n", "    trajectory: realmforge:curve\n"),
            "effects.0.trajectory: unknown id 'realmforge:curve' in registry realmforge:spell_trajectory",
        ),
        (
            "cast: {type: instant}\nvisuals: {look: realmforge:frost, impact_sound: realmforge:spell.nothing}\n"
            + PROJECTILE,
            "visuals.impact_sound: unknown id 'realmforge:spell.nothing' in registry minecraft:sound_event",
        ),
        (
            "cast: {type: instant}\n" + PROJECTILE,
            "a projectile's model is drawn with the material of the spell's look: name visuals.look, or drop the "
            "model of effects.0",
        ),
        (
            "cast: {type: instant}\nvisuals: {look: realmforge:frost, forming: grow}\n" + PROJECTILE,
            "visuals.forming: grow forms the spell's projectile during a cast time",
        ),
        (
            "cast: {type: channel, duration_seconds: 1.5, interval_seconds: 0.5}\n"
            "visuals: {look: realmforge:frost, forming: storm}\n" + PROJECTILE,
            "visuals.forming: storm forms the spell's projectile during a cast time",
        ),
        (
            "cast: {type: cast_time, duration_seconds: 1}\nvisuals: {look: realmforge:frost, forming: grow}\n"
            + PROJECTILE.replace("    model: realmforge:orb\n", ""),
            "the cast is a cast_time and the first effect a projectile with a model",
        ),
        (
            "cast: {type: instant}\nvisuals: {look: realmforge:frost, forming: hands}\n" + PROJECTILE,
            "visuals.forming: hands shows during a cast time or a channel",
        ),
        (
            "cast: {type: cast_time, duration_seconds: 1, forms_projectile: true}\n"
            "visuals: {look: realmforge:frost, forming: grow}\n" + PROJECTILE,
            "forms_projectile",
        ),
        (
            "cast: {type: instant}\nvisuals: {look: realmforge:frost}\n"
            + PROJECTILE.replace(
                "    model: realmforge:orb\n", "    appearance: {model: orb, color: '#808080'}\n"
            ),
            "appearance",
        ),
        # RLM-640: the stone look has a burst but no trail, so nothing would draw this bolt until it hits.
        (
            "cast: {type: instant}\nvisuals: {look: realmforge:stone}\n"
            + PROJECTILE.replace("    model: realmforge:orb\n", ""),
            "effects.0: a projectile without a model is drawn by its look's trail, and realmforge:stone has none: "
            "give the projectile a model or the look a trail",
        ),
    ],
)
def test_a_spell_that_names_a_missing_or_misplaced_part_is_refused(
    repo: RepoPaths, body: str, expected: str
) -> None:
    spell(repo, body)
    problems = problems_for(repo)
    assert any(expected in problem for problem in problems), problems


def test_a_projectile_is_drawn_by_a_model_or_by_its_looks_trail(repo: RepoPaths) -> None:
    # RLM-640: each alone is enough.
    spell(
        repo,
        "cast: {type: instant}\nvisuals: {look: realmforge:frost}\n"
        + PROJECTILE.replace("    model: realmforge:orb\n", ""),
        "test_trail_only",
    )
    spell(repo, "cast: {type: instant}\nvisuals: {look: realmforge:stone}\n" + PROJECTILE, "test_model_only")
    spell(
        repo, "cast: {type: instant}\n" + PROJECTILE.replace("    model: realmforge:orb\n", ""), "test_sparks"
    )
    load_and_validate(repo)


def test_forming_and_hands_validate_where_they_fit(repo: RepoPaths) -> None:
    spell(
        repo,
        "cast: {type: cast_time, duration_seconds: 1}\nvisuals: {look: realmforge:frost, forming: grow}\n"
        + PROJECTILE,
    )
    spell(
        repo,
        "cast: {type: channel, duration_seconds: 1.5, interval_seconds: 0.5}\n"
        "visuals: {look: realmforge:frost, forming: hands}\n" + PROJECTILE,
        name="test_stream",
    )
    # A look without a model: only the look's trail and burst, as Test Forward Missile's.
    spell(
        repo,
        "cast: {type: instant}\nvisuals: {look: realmforge:fire}\n"
        + PROJECTILE.replace("    model: realmforge:orb\n", ""),
        name="test_sparks",
    )
    content, _ = load_and_validate(repo)
    by_id = {entry.model.id: entry.model for entry in content.spells}
    assert by_id["test_shot"].forms_projectile
    assert not by_id["test_stream"].forms_projectile
    assert not by_id["test_sparks"].forms_projectile
    assert by_id["test_sparks"].effects[0].trajectory == "realmforge:straight"


def test_a_passive_spell_names_no_visuals() -> None:
    with pytest.raises(
        pydantic.ValidationError, match="a passive spell is never cast, so it names no visuals"
    ):
        SpellDef.model_validate(
            {
                "id": "chill",
                "name": {"en_us": "Chill"},
                "description": {"en_us": "Colder."},
                "school": "realmforge:frost",
                "visuals": {"look": "realmforge:frost"},
                "passive": {
                    "modifiers": [{"attribute": "minecraft:armor", "amount": 1, "operation": "add_value"}]
                },
            }
        )


# Validation: looks


@pytest.mark.parametrize(
    ("change", "expected"),
    [
        (
            ("realmforge:mote", "minecraft:flame"),
            "trail.particle: minecraft:flame is not one of our particles (content/particles/)",
        ),
        (
            ("realmforge:shard", "realmforge:shards"),
            "burst.particle: realmforge:shards is not one of our particles (content/particles/) "
            "(did you mean: realmforge:shard",
        ),
        (
            ("minecraft:block.glass.break", "minecraft:block.glass.shatter"),
            "impact_sound: unknown id 'minecraft:block.glass.shatter' in registry minecraft:sound_event",
        ),
        (
            ("swirl: {particle: realmforge:fleck", "swirl: {particle: minecraft:snowflake"),
            "swirl.particle: minecraft:snowflake is not one of our particles (content/particles/)",
        ),
        # The particle budget: a mote lives up to 12 ticks, so 7 a tick would keep 84 alive per caster.
        (
            (
                "swirl: {particle: realmforge:fleck, per_tick: 6}",
                "swirl: {particle: realmforge:mote, per_tick: 7}",
            ),
            "swirl.per_tick: 7 a tick of realmforge:mote, which lives up to 12 ticks, keeps more than 80 alive",
        ),
    ],
)
def test_a_look_names_particles_of_ours_and_a_sound_that_exists(
    repo: RepoPaths, change: tuple[str, str], expected: str
) -> None:
    file = repo.content / "spell_looks" / "frost.yaml"
    file.write_text(file.read_text(encoding="utf-8").replace(*change), encoding="utf-8")
    problems = problems_for(repo)
    assert any(expected in problem for problem in problems), problems


def look(**fields: object) -> SpellLookDef:
    return SpellLookDef.model_validate({"id": "frost", "colors": ["#B8E2F4"], **fields})


@pytest.mark.parametrize(
    ("fields", "expected"),
    [
        ({"colors": []}, "at least 1 item"),
        ({"colors": [f"#0000{n:02X}" for n in range(17)]}, "at most 16 items"),
        ({"colors": ["#B8E2F4", "#b8e2f4"]}, "colors: each colour once"),
        ({"colors": ["blue"]}, "String should match pattern"),
        # The particle budget (docs/design/spell-visuals.md).
        ({"trail": {"particle": "realmforge:mote", "per_block": 3.5}}, "less than or equal to 3"),
        ({"trail": {"particle": "realmforge:mote", "per_block": 0}}, "greater than 0"),
        ({"burst": {"particle": "realmforge:shard", "count": 25}}, "less than or equal to 24"),
        ({"burst": {"particle": "realmforge:shard", "count": 0}}, "greater than or equal to 1"),
        ({"swirl": {"particle": "realmforge:mote"}}, "Field required"),
        ({"swirl": {"particle": "realmforge:mote", "per_tick": 8.5}}, "less than or equal to 8"),
        ({"swirl": {"particle": "realmforge:mote", "per_tick": 0}}, "greater than 0"),
        ({"light": "blinding"}, "Input should be 'world', 'glow' or 'emit'"),
        ({"aura": {"particle": "realmforge:mote"}}, "Extra inputs are not permitted"),
    ],
)
def test_invalid_looks_are_rejected(fields: dict[str, object], expected: str) -> None:
    with pytest.raises(pydantic.ValidationError, match=expected):
        look(**fields)


def test_a_look_keeps_to_the_caps_at_their_edges() -> None:
    full = look(
        colors=[f"#0000{n:02X}" for n in range(16)],
        trail={"particle": "realmforge:mote", "per_block": 3},
        burst={"particle": "realmforge:shard", "count": 24},
        swirl={"particle": "realmforge:mote", "per_tick": 8},
    )
    assert len(full.colors) == 16
    assert full.trail.per_block == 3
    assert full.burst.count == 24
    assert full.swirl.per_tick == 8
    assert full.light == "world", "lit by the world unless it says otherwise"
    assert look().swirl is None


# Validation: models, trajectories, particles


@pytest.mark.parametrize(
    ("fields", "expected"),
    [
        ({"shape": "cube"}, "Input should be 'orb' or 'cubes'"),
        ({"scale": 0}, "greater than 0"),
        ({"scale": 4.5}, "less than or equal to 4"),
        ({"orientation": "sideways"}, "Input should be 'tumble' or 'forward'"),
        ({"spin_degrees_per_second": 90}, "only a forward model spins about its flight"),
        ({"orientation": "forward", "spin_degrees_per_second": 2000}, "less than or equal to 1440"),
    ],
)
def test_invalid_models_are_rejected(fields: dict[str, object], expected: str) -> None:
    with pytest.raises(pydantic.ValidationError, match=expected):
        SpellModelDef.model_validate({"id": "dart", "shape": "orb", **fields})


def test_the_only_trajectory_kind_is_straight() -> None:
    # Matěj, 2026-10-29 (RLM-463, answer 11): no curve; ADR 0052 stays.
    assert SpellTrajectoryDef.model_validate({"id": "straight", "kind": "straight"}).kind == "straight"
    with pytest.raises(pydantic.ValidationError, match="Input should be 'straight'"):
        SpellTrajectoryDef.model_validate({"id": "curve", "kind": "curve"})


def particle(**fields: object) -> ParticleDef:
    return ParticleDef.model_validate(
        {"id": "spark", "frames": [FRAME], "lifetime_ticks": [4, 8], "size": 0.05, **fields}
    )


@pytest.mark.parametrize(
    ("fields", "expected"),
    [
        ({"frames": []}, "at least 1 item"),
        ({"frames": [FRAME] * 9}, "at most 8 items"),
        ({"frames": [FRAME[:7]]}, "frames.0: a frame has 8 rows, not 7"),
        ({"frames": [["........"] * 8]}, "frames.0: an empty frame draws nothing"),
        ({"frames": [[*FRAME[:7], ".......g"]]}, "String should match pattern"),
        ({"frames": [[*FRAME[:7], "...ff...."]]}, "String should match pattern"),
        ({"frames": [[*FRAME[:7], "...00..."]]}, "String should match pattern"),
        ({"lifetime_ticks": [8, 4]}, "lifetime_ticks"),
        ({"lifetime_ticks": [0, 4]}, "lifetime_ticks"),
        ({"lifetime_ticks": [4, 41]}, "lifetime_ticks"),
        ({"lifetime_ticks": [4]}, "at least 2 items"),
        ({"size": 0}, "greater than 0"),
        ({"size": 0.6}, "less than or equal to 0.5"),
        ({"friction": 0}, "greater than 0"),
        ({"friction": 1.1}, "less than or equal to 1"),
        ({"gravity": 3}, "less than or equal to 2"),
    ],
)
def test_invalid_particles_are_rejected(fields: dict[str, object], expected: str) -> None:
    with pytest.raises(pydantic.ValidationError, match=expected):
        particle(**fields)


def test_a_particles_defaults_float_and_glow() -> None:
    spark = particle()
    assert (spark.gravity, spark.friction, spark.glows) == (0.0, 0.98, True)


def test_a_particle_and_a_text_group_cannot_share_a_translation_prefix(repo: RepoPaths) -> None:
    # The datapack types reserve their registry names as text group ids, the kit's three among them.
    write(repo.content / "texts" / "spell_look.yaml", "id: spell_look\ntexts:\n  frost: {en_us: Frost}\n")
    problems = problems_for(repo)
    assert any("spell_look" in problem and "content/spell_looks/" in problem for problem in problems), (
        problems
    )


# Models of cubes (RLM-640, art/spell_models.py)


def geo(cubes: list[dict[str, object]], **bone: object) -> str:
    return json.dumps(
        {
            "format_version": "1.12.0",
            "minecraft:geometry": [
                {
                    "description": {"identifier": "geometry.test", "texture_width": 64, "texture_height": 64},
                    "bones": [{"name": "body", "pivot": [0, 0, 0], **bone, "cubes": cubes}],
                }
            ],
        }
    )


CUBE: dict[str, object] = {"origin": [-1, -1, -3], "size": [2, 2, 6], "uv": [0, 0]}
TURNED: dict[str, object] = {
    "origin": [1, 0, 0],
    "size": [1.5, 1, 3],
    "pivot": [1.5, 0.5, 1],
    "rotation": [0, 30, 45],
}


def cube_model(repo: RepoPaths, source: str | None, name: str = "dart") -> None:
    write(repo.content / "spell_models" / f"{name}.yaml", f"id: {name}\nshape: cubes\norientation: forward\n")
    if source is not None:
        write(repo.root / "art" / "models" / "spell" / f"{name}.geo.json", source)


def test_the_repositorys_chunk_is_a_model_of_cubes_within_the_cap() -> None:
    paths = RepoPaths()
    assert spell_models.ids(paths) == ("chunk",)
    source = spell_models.read(spell_models.source_path(paths, "chunk"))
    assert 12 <= len(source.cubes) <= spell_models.MAX_CUBES, "dozens of small cubes, at most 64"


def test_a_model_of_cubes_becomes_a_geckolib_model_mapped_onto_the_material(repo: RepoPaths) -> None:
    cube_model(repo, geo([CUBE, TURNED]))
    files = generate(repo)
    assert json.loads(files[f"{DATA}/spell_model/dart.json"])["shape"] == "cubes"
    model = json.loads(files[f"{ASSETS}/geckolib/models/spell/dart.geo.json"])
    (geometry,) = model["minecraft:geometry"]
    # One 16 x 16 material for every model: the source's own texture size and UVs are gone.
    assert geometry["description"] == {
        "identifier": "geometry.realmforge.spell.dart",
        "texture_width": 16,
        "texture_height": 16,
    }
    (bone,) = geometry["bones"]
    assert "rotation" not in bone
    first, second = bone["cubes"]
    assert (first["origin"], first["size"]) == ([-1, -1, -3], [2, 2, 6])
    assert "rotation" not in first
    assert (second["pivot"], second["rotation"]) == ([1.5, 0.5, 1], [0, 30, 45]), "a cube keeps its own turn"
    for cube in (first, second):
        x, y, z = cube["size"]
        sizes = {
            "north": [x, y],
            "south": [x, y],
            "east": [z, y],
            "west": [z, y],
            "up": [x, z],
            "down": [x, z],
        }
        assert set(cube["uv"]) == set(sizes)
        for face, mapping in cube["uv"].items():
            # Pixel for pixel, as a block's face takes a block texture, and inside the tile.
            assert mapping["uv_size"] == sizes[face], face
            (u, v), (width, height) = mapping["uv"], mapping["uv_size"]
            assert u == int(u) and v == int(v), "whole-pixel corners"
            assert u >= 0 and u + width <= 16 and v >= 0 and v + height <= 16, face
    assert first["uv"] != second["uv"], "each cube takes another patch of the tile"
    assert files == generate(repo), "the same on every run"
    # The chunk the repository ships is written too.
    assert f"{ASSETS}/geckolib/models/spell/chunk.geo.json" in files


def test_a_face_longer_than_the_material_takes_the_whole_tile(repo: RepoPaths) -> None:
    cube_model(repo, geo([{"origin": [0, 0, 0], "size": [20, 1, 1]}]))
    model = json.loads(generate(repo)[f"{ASSETS}/geckolib/models/spell/dart.geo.json"])
    north = model["minecraft:geometry"][0]["bones"][0]["cubes"][0]["uv"]["north"]
    assert north == {"uv": [0, 0], "uv_size": [16, 1]}


def test_a_model_of_at_most_64_cubes_is_fine_and_one_more_is_refused(repo: RepoPaths) -> None:
    def row(count: int) -> list[dict[str, object]]:
        return [{"origin": [n, 0, 0], "size": [1, 1, 1]} for n in range(count)]

    cube_model(repo, geo(row(64)))
    load_and_validate(repo)
    cube_model(repo, geo(row(65)))
    assert problems_for(repo) == [
        "art/models/spell/dart.geo.json: the model has 65 cubes; a spell model has at most 64"
    ]


@pytest.mark.parametrize(
    ("source", "expected"),
    [
        (
            None,
            "content/spell_models/dart.yaml: shape: a cubes model has its source at "
            "art/models/spell/dart.geo.json (art/README.md): the file is missing",
        ),
        ("{not json", "art/models/spell/dart.geo.json: cannot be read as JSON"),
        ('{"bones": []}', 'a model source is Bedrock geometry with one entry in "minecraft:geometry"'),
        (geo([]), "art/models/spell/dart.geo.json: the model has no cube"),
        (
            geo([CUBE], rotation=[0, 45, 0]),
            "bone body is rotated: the game draws a spell model's cubes, not its bones",
        ),
        (
            geo([{"origin": [0, 0, 0], "size": [1, 0, 1]}]),
            "bone body, cube 0: every side of its size is above 0, not [1.0, 0.0, 1.0]",
        ),
        (
            geo([{"origin": [0, 0], "size": [1, 1, 1]}]),
            "bone body, cube 0: origin is three numbers, not [0, 0]",
        ),
        (geo([{**CUBE, "rotation": [0, "a", 0]}]), "bone body, cube 0: rotation is three numbers"),
        (
            geo([{**CUBE, "inflate": 0.5}]),
            "bone body, cube 0 is inflated by 0.5: the game draws a spell model's cubes at their own size",
        ),
        (geo([{**CUBE, "inflate": "a"}]), "bone body, cube 0: inflate is a number, not 'a'"),
        (geo([CUBE], inflate=0.25), "bone body is inflated by 0.25"),
    ],
)
def test_a_cubes_model_needs_a_source_the_game_can_draw(
    repo: RepoPaths, source: str | None, expected: str
) -> None:
    cube_model(repo, source)
    problems = problems_for(repo)
    assert any(expected in problem for problem in problems), problems


def test_a_bone_turned_by_nothing_is_fine(repo: RepoPaths) -> None:
    # Blockbench writes a zero rotation for a group that was turned and turned back.
    cube_model(repo, geo([CUBE], rotation=[0, 0, 0]))
    load_and_validate(repo)


def test_a_source_that_no_cubes_model_names_is_refused(repo: RepoPaths) -> None:
    write(repo.root / "art" / "models" / "spell" / "orb.geo.json", geo([CUBE]))
    write(repo.root / "art" / "models" / "spell" / "notes.txt", "stray")
    assert sorted(problems_for(repo)) == [
        "art/models/spell/notes.txt: a spell model's source is art/models/spell/<id>.geo.json",
        "art/models/spell/orb.geo.json: no spell model of shape cubes has the id 'orb' (content/spell_models/)",
    ]
