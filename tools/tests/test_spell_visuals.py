"""The spell visuals kit (RLM-639, ADR 0073): looks, models, trajectories and particles as content, a spell's
``visuals``, and what ``rf gen`` writes for them."""

from __future__ import annotations

import itertools
import json

import pydantic
import pytest

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


def test_the_repository_has_the_first_looks_one_model_and_one_trajectory() -> None:
    content, _ = load_and_validate(RepoPaths())
    assert {entry.model.id for entry in content.spell_looks} == {"frost", "fire", "arcane", "stone"}
    assert {entry.model.id for entry in content.spell_models} == {"orb"}
    assert {entry.model.id: entry.model.kind for entry in content.spell_trajectories} == {
        "straight": "straight"
    }
    assert {entry.model.id for entry in content.particles} == {"mote", "shard", "ember"}


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
        "impact_sound": "minecraft:block.glass.break",
    }
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
    assert "ALL = List.of(EMBER, MOTE, SHARD, SPARK);" in java
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
        '                    Optional.of("realmforge:spell.stoneform.impact"))'
    ) in expectations
    assert 'new ModelExpectation("realmforge:orb", "orb", 1.0, "tumble", 0.0)' in expectations
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
    ],
)
def test_a_spell_that_names_a_missing_or_misplaced_part_is_refused(
    repo: RepoPaths, body: str, expected: str
) -> None:
    spell(repo, body)
    problems = problems_for(repo)
    assert any(expected in problem for problem in problems), problems


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
        ({"swirl": {"particle": "realmforge:mote"}}, "Extra inputs are not permitted"),
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
    )
    assert len(full.colors) == 16
    assert full.trail.per_block == 3
    assert full.burst.count == 24


# Validation: models, trajectories, particles


@pytest.mark.parametrize(
    ("fields", "expected"),
    [
        ({"shape": "cube"}, "Input should be 'orb'"),
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
