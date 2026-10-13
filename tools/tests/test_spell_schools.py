"""Spell schools (content/spell_schools/): validation and generation (architecture 6.2, 9.1)."""

from __future__ import annotations

import json

import pytest

from realmforge_tools.generate import generate_all
from realmforge_tools.problems import ValidationFailed
from realmforge_tools.project import RepoPaths
from realmforge_tools.schema.spell_school import SpellSchoolDef
from realmforge_tools.validate import load_and_validate
from tests.conftest import write

DATA = "mod/src/generated/resources/data"
SCHOOL_DATA = f"{DATA}/realmforge/realmforge/spell_school"
DAMAGE_TYPES = f"{DATA}/realmforge/damage_type"
TAGS = f"{DATA}/minecraft/tags/damage_type"
LANG = "mod/src/generated/resources/assets/realmforge/lang"
KEYS_JAVA = "mod/src/generated/java/io/github/matejkadlec/realmforge/generated/GeneratedSpellSchools.java"
EXPECTATIONS = (
    "qa-mod/src/generated/java/io/github/matejkadlec/realmforge/qa/generated/"
    "GeneratedSpellSchoolExpectations.java"
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


def school(repo: RepoPaths, name: str, body: str) -> None:
    write(repo.content / "spell_schools" / f"{name}.yaml", f"id: {name}\n{body}")


def school_model(**fields: object) -> SpellSchoolDef:
    data = {
        "id": "shadow",
        "name": {"en_us": "Shadow"},
        "kind": "magic",
        "color": "#663399",
        "death_message": {"en_us": "%1$s faded away"},
        **fields,
    }
    return SpellSchoolDef.model_validate(data)


SHADOW = 'name: {en_us: Shadow}\nkind: magic\ncolor: "#663399"\ndeath_message: {en_us: "%1$s faded away"}\n'


# The repository's content


def test_repository_schools_are_the_m1_set() -> None:
    content, universe = load_and_validate(RepoPaths())
    kinds = {e.model.id: e.model.kind for e in content.spell_schools}
    assert kinds == {"arcane": "magic", "fire": "magic", "frost": "magic", "physical": "physical"}
    assert universe.contains("realmforge:spell_school", "realmforge:fire")
    assert universe.contains("minecraft:damage_type", "realmforge:physical")


# Schema


@pytest.mark.parametrize(
    ("fields", "expected"),
    [
        (
            {"death_message": {"en_us": "Someone burned"}},
            "death_message.en_us must name the victim with %1\\$s",
        ),
        (
            {"death_message": {"en_us": "%1$s was burned by %2$s"}},
            "death_message.en_us may use only %1\\$s \\(and %%\\), not %2\\$s",
        ),
        ({"death_message": {"en_us": "%1$s and %s"}}, "may use only %1\\$s \\(and %%\\), not %s"),
        (
            {"power": "realmforge:shadow_power", "resistance": "realmforge:shadow_power"},
            "power and resistance must be different stats",
        ),
        ({"color": "663399"}, "String should match pattern"),
        ({"color": "#66339"}, "String should match pattern"),
        ({"kind": "holy"}, "Input should be 'physical' or 'magic'"),
    ],
)
def test_invalid_schools_are_rejected(fields: dict, expected: str) -> None:
    with pytest.raises(ValueError, match=expected):
        school_model(**fields)


def test_a_percent_sign_and_the_victim_are_fine() -> None:
    assert school_model(death_message={"en_us": "%1$s took 100%% damage"}).death_message.en_us


def test_a_school_needs_a_name_and_a_death_message(repo: RepoPaths) -> None:
    school(repo, "shadow", 'kind: magic\ncolor: "#663399"\n')
    problems = problems_for(repo)
    assert any("content/spell_schools/shadow.yaml: name: Field required" in p for p in problems), problems
    assert any("death_message: Field required" in p for p in problems), problems


# References


def test_power_must_be_a_known_stat(repo: RepoPaths) -> None:
    school(repo, "shadow", SHADOW + "power: realmforge:shadow_powr\n")
    assert_problem(repo, "unknown id 'realmforge:shadow_powr' in registry minecraft:attribute")


def test_power_must_be_one_of_our_stats(repo: RepoPaths) -> None:
    school(repo, "shadow", SHADOW + "power: minecraft:luck\n")
    assert_problem(repo, "power: minecraft:luck is not one of our stats (content/stats/)")


@pytest.mark.parametrize(
    "stat",
    [
        pytest.param("realmforge:strength", id="a primary stat"),
        pytest.param("realmforge:shadow_points", id="a stat that counts points"),
    ],
)
def test_resistance_must_be_a_secondary_share(repo: RepoPaths, stat: str) -> None:
    write(
        repo.content / "stats" / "shadow_points.yaml",
        "id: shadow_points\nname: {en_us: Points}\nkind: secondary\nmax: 10\n",
    )
    school(repo, "shadow", SHADOW + f"resistance: {stat}\n")
    assert_problem(repo, f"resistance: {stat} must be a secondary stat that is a share")


def test_a_stat_serves_one_school(repo: RepoPaths) -> None:
    school(repo, "shadow", SHADOW + "power: realmforge:fire_power\n")
    assert_problem(
        repo, "power: realmforge:fire_power already serves content/spell_schools/fire.yaml (power)"
    )


def test_a_stat_serves_one_role(repo: RepoPaths) -> None:
    school(repo, "shadow", SHADOW + "resistance: realmforge:frost_power\n")
    assert_problem(repo, "realmforge:frost_power already serves content/spell_schools/frost.yaml (power)")


@pytest.mark.parametrize("group", ["death.attack", "spell_school"])
def test_text_groups_must_not_take_a_school_key_prefix(repo: RepoPaths, group: str) -> None:
    write(repo.content / "texts" / f"{group}.yaml", f"id: {group}\ntexts:\n  fire:\n    en_us: Fire\n")
    assert_problem(repo, f"{group!r} is the translation key prefix of content/spell_schools/")


# Generation


def test_school_entries_name_their_damage_type_and_stats(repo: RepoPaths) -> None:
    files = generate(repo)
    assert json.loads(files[f"{SCHOOL_DATA}/fire.json"]) == {
        "name": "spell_school.realmforge.fire.name",
        "kind": "magic",
        "color": "#FF7F1F",
        "damage_type": "realmforge:fire",
        "power": "realmforge:fire_power",
        "resistance": "realmforge:fire_resistance",
    }
    assert json.loads(files[f"{SCHOOL_DATA}/physical.json"]) == {
        "name": "spell_school.realmforge.physical.name",
        "kind": "physical",
        "color": "#FFAA00",
        "damage_type": "realmforge:physical",
    }


def test_the_colour_is_written_in_capitals(repo: RepoPaths) -> None:
    school(repo, "shadow", SHADOW.replace("#663399", "#66aaff"))
    assert json.loads(generate(repo)[f"{SCHOOL_DATA}/shadow.json"])["color"] == "#66AAFF"


def test_damage_types_follow_the_kind(repo: RepoPaths) -> None:
    files = generate(repo)
    assert json.loads(files[f"{DAMAGE_TYPES}/fire.json"]) == {
        "exhaustion": 0.0,
        "message_id": "realmforge.fire",
        "scaling": "never",
    }
    assert json.loads(files[f"{DAMAGE_TYPES}/physical.json"])["exhaustion"] == 0.1


def test_every_school_bypasses_the_cooldown_and_magic_bypasses_armor(repo: RepoPaths) -> None:
    files = generate(repo)
    assert json.loads(files[f"{TAGS}/bypasses_cooldown.json"]) == {
        "values": ["realmforge:arcane", "realmforge:fire", "realmforge:frost", "realmforge:physical"]
    }
    assert json.loads(files[f"{TAGS}/bypasses_armor.json"]) == {
        "values": ["realmforge:arcane", "realmforge:fire", "realmforge:frost"]
    }


def test_no_magic_school_writes_no_armor_tag(repo: RepoPaths) -> None:
    for name in ("arcane", "fire", "frost"):
        (repo.content / "spell_schools" / f"{name}.yaml").unlink()
    files = generate(repo)
    assert f"{TAGS}/bypasses_cooldown.json" in files
    assert f"{TAGS}/bypasses_armor.json" not in files


def test_names_and_death_messages_go_into_the_lang_file(repo: RepoPaths) -> None:
    english = json.loads(generate(repo)[f"{LANG}/en_us.json"])
    assert english["spell_school.realmforge.frost.name"] == "Frost"
    for key in ("death.attack.realmforge.fire", "death.attack.realmforge.fire.player"):
        assert english[key] == "%1$s burned to ashes"
    assert english["death.attack.realmforge.fire.item"] == "%1$s burned to ashes"


def test_keys_and_expectations_cover_every_school(repo: RepoPaths) -> None:
    files = generate(repo)
    keys = files[KEYS_JAVA].decode()
    assert 'public static final ResourceKey<SpellSchool> FIRE = key("fire");' in keys
    assert "ALL = List.of(ARCANE, FIRE, FROST, PHYSICAL);" in keys
    expectations = files[EXPECTATIONS].decode()
    assert (
        "new SpellSchoolExpectation(\n"
        '                    "realmforge:fire",\n'
        '                    "spell_school.realmforge.fire.name",\n'
        "                    true,\n"
        "                    0xFFFF7F1F,\n"
        '                    "realmforge:fire",\n'
        '                    "realmforge.fire",\n'
        '                    Optional.of("realmforge:fire_power"),\n'
        '                    Optional.of("realmforge:fire_resistance"))' in expectations
    )
    assert "0xFFFFAA00,\n" in expectations
    assert "Optional.empty(),\n                    Optional.empty())" in expectations


def test_no_schools_still_compile(repo: RepoPaths) -> None:
    for file in (repo.content / "spell_schools").glob("*.yaml"):
        file.unlink()
    files = generate(repo)
    assert "ALL = List.of();" in files[KEYS_JAVA].decode()
    assert "SPELL_SCHOOLS = List.of();" in files[EXPECTATIONS].decode()
    assert not any(path.startswith((SCHOOL_DATA, DAMAGE_TYPES, TAGS)) for path in files)
