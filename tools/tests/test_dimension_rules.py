"""content/dimensions/: the datapack registry realmforge:dimension_rules and its mob level rule (architecture 14.1)."""

from __future__ import annotations

import json
import shutil

import pytest

from realmforge_tools.generate import generate_all
from realmforge_tools.generate.dimension_rules import sample_player_levels
from realmforge_tools.problems import ValidationFailed
from realmforge_tools.project import RepoPaths
from realmforge_tools.schema.dimension_rules import LevelChances, MobLevelRuleDef
from realmforge_tools.validate import load_and_validate
from tests.conftest import write

DATA = "mod/src/generated/resources/data/realmforge/realmforge/dimension_rules"
EXPECTATIONS = (
    "qa-mod/src/generated/java/io/github/matejkadlec/realmforge/qa/generated/"
    "GeneratedDimensionRuleExpectations.java"
)
RULE = "mob_levels:\n  entry_level: 6\n  max_level: 60\n  weights: [5, 10, 20, 30, 20, 10, 5]\n"
WEIGHTS = (5, 10, 20, 30, 20, 10, 5)
FLOORS = "  structure_floors:\n"


def problems_for(repo: RepoPaths) -> list[str]:
    with pytest.raises(ValidationFailed) as excinfo:
        load_and_validate(repo)
    return [str(p) for p in excinfo.value.problems]


def generate(repo: RepoPaths) -> dict[str, bytes]:
    content, _ = load_and_validate(repo)
    return {repo.rel(path): data for path, data in generate_all(content, repo).items()}


def rules(entry_id: str, dimension: str, body: str = RULE) -> str:
    return f"id: {entry_id}\ndimension: {dimension}\n{body}"


def rule(**overrides: object) -> MobLevelRuleDef:
    fields: dict[str, object] = {"entry_level": 6, "max_level": 60, "weights": list(WEIGHTS)}
    fields.update(overrides)
    return MobLevelRuleDef.model_validate(fields)


# Validation


def test_repository_dimension_rules_are_valid() -> None:
    content, universe = load_and_validate(RepoPaths())
    assert [(e.model.id, e.model.dimension) for e in content.dimensions] == [
        ("overworld", "minecraft:overworld"),
        ("stone", "realmforge:stone"),
    ]
    assert universe.contains("realmforge:dimension_rules", "realmforge:overworld")


def test_rules_may_name_our_own_dimensions(repo: RepoPaths) -> None:
    write(repo.content / "worldgen" / "dimension" / "deep.json", '{"type": "minecraft:overworld"}')
    write(repo.mod_resources / "data" / "realmforge" / "dimension" / "instances.json", "{}")
    write(repo.content / "dimensions" / "deep.yaml", rules("deep", "realmforge:deep"))
    write(repo.content / "dimensions" / "instances.yaml", rules("instances", "realmforge:instances", ""))
    content, _ = load_and_validate(repo)
    assert {e.model.dimension for e in content.dimensions} >= {"realmforge:deep", "realmforge:instances"}


def test_unknown_dimension_is_reported_with_a_suggestion(repo: RepoPaths) -> None:
    write(repo.content / "dimensions" / "nether.yaml", rules("nether", "minecraft:the_nethr"))
    assert any(
        "dimensions/nether.yaml: dimension: unknown id 'minecraft:the_nethr' in registry minecraft:dimension "
        "(did you mean: minecraft:the_nether" in p
        for p in problems_for(repo)
    )


def test_a_dimension_has_one_entry_at_most(repo: RepoPaths) -> None:
    write(repo.content / "dimensions" / "surface.yaml", rules("surface", "minecraft:overworld"))
    assert any(
        "dimensions/surface.yaml: dimension: minecraft:overworld already has rules in content/dimensions/overworld.yaml"
        in p
        for p in problems_for(repo)
    )


@pytest.mark.parametrize(
    ("body", "expected"),
    [
        (RULE.replace("entry_level: 6", "entry_level: 61"), "entry_level 61 is above max_level 60"),
        (
            RULE.replace("entry_level: 6", "entry_level: 0"),
            "mob_levels.entry_level: Input should be greater than",
        ),
        (
            RULE.replace("[5, 10, 20, 30, 20, 10, 5]", "[5, 10, 20, 30, 20, 10]"),
            "weights needs an odd number of entries",
        ),
        (
            RULE.replace("[5, 10, 20, 30, 20, 10, 5]", "[]"),
            "mob_levels.weights: List should have at least 1 item",
        ),
        (RULE.replace("[5, 10, 20, 30, 20, 10, 5]", "[5, 0, 5]"), "every weight must be 1 to 1000"),
        (RULE.replace("[5, 10, 20, 30, 20, 10, 5]", "[1001]"), "every weight must be 1 to 1000"),
        (RULE.replace("[5, 10, 20, 30, 20, 10, 5]", f"{[1] * 23}"), "List should have at most 21 items"),
        (RULE.replace("max_level: 60", "max_level: 60\n  jitter: 1"), "Extra inputs are not permitted"),
        (RULE.replace("[5, 10, 20, 30, 20, 10, 5]", "[5, 1.5, 5]"), "Input should be a valid integer"),
    ],
)
def test_invalid_rules_are_rejected(repo: RepoPaths, body: str, expected: str) -> None:
    write(repo.content / "dimensions" / "overworld.yaml", rules("overworld", "minecraft:overworld", body))
    assert any(expected in p for p in problems_for(repo)), problems_for(repo)


# Structure floors (RLM-342)


@pytest.mark.parametrize(
    ("floors", "expected"),
    [
        (
            "    - {structure: minecraft:ancient_cty, level: 40}\n",
            "mob_levels.structure_floors.0.structure: unknown id 'minecraft:ancient_cty' in registry "
            "minecraft:worldgen/structure (did you mean: minecraft:ancient_city",
        ),
        (
            "    - {structure: minecraft:stronghold, level: 30}\n    - {structure: '#minecraft:vilage', level: 20}\n",
            "mob_levels.structure_floors.1.structure: unknown tag '#minecraft:vilage' in registry "
            "minecraft:worldgen/structure (did you mean: #minecraft:village",
        ),
        (
            "    - {structure: minecraft:ancient_city, level: 6}\n",
            "the floor of minecraft:ancient_city, 6, must be above entry_level 6 and at most max_level 60",
        ),
        (
            "    - {structure: minecraft:ancient_city, level: 61}\n",
            "the floor of minecraft:ancient_city, 61, must be above entry_level 6",
        ),
        (
            "    - {structure: minecraft:ancient_city, level: 40}\n    - {structure: minecraft:ancient_city, level: 50}\n",
            "minecraft:ancient_city has two structure floors",
        ),
        ("    - {structure: ancient_city, level: 40}\n", "String should match pattern"),
        ("    - {structure: minecraft:ancient_city}\n", "structure_floors.0.level: Field required"),
        (
            "    - {structure: minecraft:ancient_city, level: 40, radius: 8}\n",
            "Extra inputs are not permitted",
        ),
        ("    []\n", "List should have at least 1 item"),
    ],
)
def test_invalid_structure_floors_are_rejected(repo: RepoPaths, floors: str, expected: str) -> None:
    body = RULE + FLOORS + floors
    write(repo.content / "dimensions" / "overworld.yaml", rules("overworld", "minecraft:overworld", body))
    assert any(expected in p for p in problems_for(repo)), problems_for(repo)


def test_known_structures_and_structure_tags_pass(repo: RepoPaths) -> None:
    body = (
        RULE + FLOORS + "    - {structure: minecraft:ancient_city, level: 40}\n"
        "    - {structure: '#minecraft:village', level: 20}\n"
    )
    write(repo.content / "dimensions" / "overworld.yaml", rules("overworld", "minecraft:overworld", body))
    content, _ = load_and_validate(repo)
    (overworld,) = [e.model for e in content.dimensions if e.model.id == "overworld"]
    assert overworld.mob_levels is not None
    assert [(f.structure, f.level) for f in overworld.mob_levels.structure_floors or []] == [
        ("minecraft:ancient_city", 40),
        ("#minecraft:village", 20),
    ]
    assert overworld.mob_levels.floors == [6, 40, 20]


def test_the_overworlds_ancient_cities_are_level_40_and_more() -> None:
    content, _ = load_and_validate(RepoPaths())
    (overworld,) = [e.model for e in content.dimensions if e.model.id == "overworld"]
    assert overworld.mob_levels is not None
    assert [(f.structure, f.level) for f in overworld.mob_levels.structure_floors or []] == [
        ("minecraft:ancient_city", 40)
    ]


# The level rule: the GameTest compares the game's MobLevelRule with these values.


def test_a_player_between_the_floor_and_the_cap_meets_every_offset_by_its_weight() -> None:
    chances = rule().chances(20)
    assert chances == LevelChances(17, WEIGHTS)
    assert (chances.lowest, chances.highest) == (17, 23)
    assert rule().spread == 3


def test_the_floor_drops_the_offsets_below_it() -> None:
    model = rule()
    assert model.chances(6) == LevelChances(6, (30, 20, 10, 5))
    assert model.chances(7) == LevelChances(6, (20, 30, 20, 10, 5))
    assert model.chances(9) == LevelChances(6, WEIGHTS)


def test_a_player_below_the_floor_meets_every_mob_at_it() -> None:
    assert rule().chances(5) == LevelChances(6, (1,))
    assert rule().chances(1) == LevelChances(6, (1,))


def test_the_cap_drops_the_offsets_above_it() -> None:
    model = rule()
    assert model.chances(60) == LevelChances(57, (5, 10, 20, 30))
    assert model.chances(61) == LevelChances(58, (5, 10, 20))
    assert model.chances(63) == LevelChances(60, (5,))
    assert model.chances(64) == LevelChances(60, (1,))
    assert model.chances(1000) == LevelChances(60, (1,))


def test_a_structure_floor_raises_the_floor_and_cuts_the_offsets_below_it() -> None:
    model = rule(entry_level=1, structure_floors=[{"structure": "minecraft:ancient_city", "level": 40}])
    assert model.chances(5, 40) == LevelChances(40, (1,))
    assert model.chances(39, 40) == LevelChances(40, (1,))
    assert model.chances(40, 40) == LevelChances(40, (30, 20, 10, 5))
    assert model.chances(45, 40) == LevelChances(42, WEIGHTS)
    assert model.chances(64, 40) == LevelChances(60, (1,))
    assert model.chances(5) == model.chances(5, 1) == LevelChances(2, WEIGHTS)
    assert model.floors == [1, 40]


def test_a_floor_outside_the_rules_levels_is_refused() -> None:
    with pytest.raises(ValueError, match="floor 5 is outside 6 to 60"):
        rule().chances(20, 5)
    with pytest.raises(ValueError, match="floor 61 is outside 6 to 60"):
        rule().chances(20, 61)


def test_one_weight_means_the_players_own_level() -> None:
    model = rule(entry_level=1, weights=[1])
    assert model.chances(1) == LevelChances(1, (1,))
    assert model.chances(42) == LevelChances(42, (1,))


def test_samples_cover_the_floor_the_middle_and_the_cap() -> None:
    assert sample_player_levels(rule(), 6) == [1, 5, 6, 7, 9, 33, 57, 60, 61, 64]
    assert sample_player_levels(rule(), 40) == [1, 39, 40, 41, 43, 50, 57, 60, 61, 64]
    # Duplicates and levels below 1 drop out.
    assert sample_player_levels(rule(entry_level=1, max_level=4, weights=[1]), 1) == [1, 2, 4, 5]


# Generation


def test_rules_become_data_and_expectations(repo: RepoPaths) -> None:
    files = generate(repo)
    assert json.loads(files[f"{DATA}/overworld.json"]) == {
        "dimension": "minecraft:overworld",
        "mob_levels": {
            "entry_level": 1,
            "max_level": 60,
            "weights": [5, 10, 20, 30, 20, 10, 5],
            "structure_floors": [{"structure": "minecraft:ancient_city", "level": 40}],
        },
    }
    stone = json.loads(files[f"{DATA}/stone.json"])["mob_levels"]
    assert stone["entry_level"] == 6
    assert "structure_floors" not in stone
    expectations = files[EXPECTATIONS].decode()
    assert '"realmforge:overworld",\n                    "minecraft:overworld",' in expectations
    assert (
        "Optional.of(new MobLevelRule(1, 60, List.of(5, 10, 20, 30, 20, 10, 5),\n"
        '                            List.of(new StructureFloor("minecraft:ancient_city", 40))))'
    ) in expectations
    assert (
        "Optional.of(new MobLevelRule(6, 60, List.of(5, 10, 20, 30, 20, 10, 5),\n                            List.of()))"
        in expectations
    )
    assert "new Sample(1, 1, 1, List.of(30, 20, 10, 5))" in expectations
    assert "new Sample(5, 6, 6, List.of(1))" in expectations
    assert "new Sample(61, 6, 58, List.of(5, 10, 20))" in expectations
    # The Ancient City's floor gets its own samples.
    assert "new Sample(39, 40, 40, List.of(1))" in expectations
    assert "new Sample(41, 40, 40, List.of(20, 30, 20, 10, 5))" in expectations


def test_rules_without_mob_levels(repo: RepoPaths) -> None:
    write(repo.content / "dimensions" / "the_end.yaml", rules("the_end", "minecraft:the_end", ""))
    files = generate(repo)
    assert json.loads(files[f"{DATA}/the_end.json"]) == {"dimension": "minecraft:the_end"}
    assert (
        '"minecraft:the_end",\n                    Optional.empty(),\n                    List.of(),\n                    List.of())'
        in files[EXPECTATIONS].decode()
    )


def test_no_rules_still_compile(repo: RepoPaths) -> None:
    for path in (repo.content / "dimensions").glob("*.yaml"):
        path.unlink()
    # A realm needs its dimension's rules.
    shutil.rmtree(repo.content / "realms")
    files = generate(repo)
    assert "DIMENSION_RULES = List.of();" in files[EXPECTATIONS].decode()
    assert not any(path.startswith(DATA) for path in files)


# Access


def test_the_stone_realms_rules_require_its_unlock(repo: RepoPaths) -> None:
    files = generate(repo)
    data = json.loads(files[f"{DATA}/stone.json"])
    assert data["access"] == [{"type": "realmforge:has_advancement", "advancement": "realmforge:realm/stone"}]
    assert "access" not in json.loads(files[f"{DATA}/overworld.json"])
    assert (
        '"{\\n  \\"advancement\\": \\"realmforge:realm/stone\\",\\n  '
        '\\"type\\": \\"realmforge:has_advancement\\"\\n}"'
    ) in files[EXPECTATIONS].decode()


@pytest.mark.parametrize(
    ("requirement", "expected"),
    [
        (
            "{type: realmforge:has_advancement, advancement: realmforge:realm/nowhere}",
            "access.0.advancement: unknown id 'realmforge:realm/nowhere' in registry minecraft:advancement",
        ),
        (
            "{type: realmforge:chapter_completed, chapter: realmforge:chapter_9}",
            "access.0.chapter: unknown id 'realmforge:chapter_9' in registry realmforge:chapter",
        ),
        ("{type: realmforge:level_at_least, level: 0}", "Input should be greater than or equal to 1"),
        ("{type: realmforge:has_level, level: 3}", "does not match any of the expected tags"),
    ],
)
def test_bad_access_requirements_are_reported(repo: RepoPaths, requirement: str, expected: str) -> None:
    body = f"access:\n  - {requirement}\n"
    write(repo.content / "dimensions" / "the_end.yaml", rules("the_end", "minecraft:the_end", body))
    assert any(expected in p for p in problems_for(repo)), problems_for(repo)


def test_known_advancements_and_chapters_pass(repo: RepoPaths) -> None:
    body = (
        "access:\n"
        "  - {type: realmforge:has_advancement, advancement: minecraft:story/enter_the_nether}\n"
        "  - {type: realmforge:chapter_completed, chapter: realmforge:chapter_1}\n"
        "  - {type: realmforge:level_at_least, level: 10}\n"
    )
    write(repo.content / "dimensions" / "the_end.yaml", rules("the_end", "minecraft:the_end", body))
    content, _ = load_and_validate(repo)
    (end,) = [e.model for e in content.dimensions if e.model.id == "the_end"]
    assert end.access is not None and len(end.access) == 3
