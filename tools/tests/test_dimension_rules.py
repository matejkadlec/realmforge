"""content/dimensions/: the datapack registry realmforge:dimension_rules and its mob level rule (architecture 14.1)."""

from __future__ import annotations

import json
import shutil

import pytest

from realmforge_tools.generate import generate_all
from realmforge_tools.generate.dimension_rules import sample_positions
from realmforge_tools.problems import ValidationFailed
from realmforge_tools.project import RepoPaths
from realmforge_tools.schema.dimension_rules import MobLevelRuleDef
from realmforge_tools.validate import load_and_validate
from tests.conftest import write

DATA = "mod/src/generated/resources/data/realmforge/realmforge/dimension_rules"
EXPECTATIONS = (
    "qa-mod/src/generated/java/io/github/matejkadlec/realmforge/qa/generated/"
    "GeneratedDimensionRuleExpectations.java"
)
RULE = (
    "mob_levels:\n"
    "  origin_level: 2\n"
    "  blocks_per_level: 100\n"
    "  depth: {below_y: 0, blocks_per_level: 10}\n"
    "  jitter: 1\n"
    "  min_level: 1\n"
    "  max_level: 6\n"
)


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
    fields: dict[str, object] = {
        "origin_level": 2,
        "blocks_per_level": 100,
        "depth": {"below_y": 0, "blocks_per_level": 10},
        "jitter": 1,
        "min_level": 1,
        "max_level": 6,
    }
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
        (RULE.replace("min_level: 1", "min_level: 7"), "min_level 7 is above max_level 6"),
        (
            RULE.replace("origin_level: 2", "origin_level: 9"),
            "origin_level 9 is outside min_level 1 .. max_level 6",
        ),
        (
            RULE.replace("blocks_per_level: 100", "blocks_per_level: 0"),
            "Input should be greater than or equal to 1",
        ),
        (
            RULE.replace("jitter: 1", "jitter: -1"),
            "mob_levels.jitter: Input should be greater than or equal to 0",
        ),
        (
            RULE.replace("min_level: 1", "min_level: 0"),
            "mob_levels.min_level: Input should be greater than or equal",
        ),
        (
            RULE.replace("blocks_per_level: 10}", "blocks_per_level: 10, above_y: 3}"),
            "Extra inputs are not permitted",
        ),
        (RULE.replace("jitter: 1", "jitter: 1.5"), "mob_levels.jitter: Input should be a valid integer"),
    ],
)
def test_invalid_rules_are_rejected(repo: RepoPaths, body: str, expected: str) -> None:
    write(repo.content / "dimensions" / "overworld.yaml", rules("overworld", "minecraft:overworld", body))
    assert any(expected in p for p in problems_for(repo)), problems_for(repo)


# The level rule: the GameTest compares the game's MobLevelRule with these values.


def test_level_rises_one_step_per_blocks_per_level_of_horizontal_distance() -> None:
    model = rule()
    assert model.level_at(0, 64, 0) == 2
    assert model.level_at(99.9, 64, 0) == 2
    assert model.level_at(100, 64, 0) == 3
    assert model.level_at(0, 64, -250) == 4
    # 60, 80 is 100 blocks away: distance is horizontal and Euclidean.
    assert model.level_at(60, 64, 80) == 3
    assert model.level_at(70, 64, 70) == 2


def test_depth_adds_levels_below_its_line() -> None:
    model = rule()
    assert model.level_at(0, 0, 0) == 2
    assert model.level_at(0, -9.5, 0) == 2
    assert model.level_at(0, -10, 0) == 3
    assert model.level_at(0, -35, 0) == 5
    assert rule(depth=None).level_at(0, -35, 0) == 2


def test_levels_are_clamped_after_the_jitter() -> None:
    model = rule()
    assert model.level_at(10_000, 64, 0) == 6
    assert model.level_at(10_000, 64, 0, 1) == 6
    assert model.level_at(0, 64, 0, -1) == 1
    assert model.level_at(0, 64, 0, 1) == 3
    assert rule(origin_level=1).level_at(0, 64, 0, -1) == 1


def test_samples_cover_the_origin_every_step_the_depth_line_and_the_edge() -> None:
    model = rule(origin_level=4)
    positions = sample_positions(model)
    assert positions[0] == (0.0, 16.0, 0.0)
    # Up to the cap (6) and one step beyond: three steps, each on both sides.
    assert (299.5, 16.0, 0.0) in positions
    assert (0.0, 16.0, -300.0) in positions
    assert (399.5, 16.0, 0.0) not in positions
    assert {(0.0, 0.0, 0.0), (0.0, -0.5, 0.0), (0.0, -10.0, 0.0), (0.0, -20.5, 0.0)} <= set(positions)
    assert positions[-1] == (30_000_000.0, 16.0, 0.0)
    assert sample_positions(rule(depth=None))[0] == (0.0, 64.0, 0.0)


# Generation


def test_rules_become_data_and_expectations(repo: RepoPaths) -> None:
    files = generate(repo)
    assert json.loads(files[f"{DATA}/overworld.json"]) == {
        "dimension": "minecraft:overworld",
        "mob_levels": {
            "origin_level": 1,
            "blocks_per_level": 500,
            "depth": {"below_y": 0, "blocks_per_level": 32},
            "jitter": 1,
            "min_level": 1,
            "max_level": 5,
        },
    }
    expectations = files[EXPECTATIONS].decode()
    assert '"realmforge:overworld",\n                    "minecraft:overworld",' in expectations
    assert "Optional.of(new MobLevelRule(1, 500, Optional.of(new Depth(0, 32)), 1, 1, 5))" in expectations
    assert "new Sample(999.5, 16.0, 0.0, 2, 1, 3)" in expectations
    assert "new Sample(0.0, -64.5, 0.0, 3, 2, 4)" in expectations


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
