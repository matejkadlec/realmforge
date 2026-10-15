"""Chapters (content/chapters/), kill XP multipliers (content/kill_xp_multipliers/) and leveling (architecture 10.2):
validation, the reference XP, kill XP and level cap rules, and generation."""

from __future__ import annotations

import json
import math

import pytest
from pydantic import ValidationError

from realmforge_tools.generate import generate_all
from realmforge_tools.generate.leveling import cap_samples, level_samples
from realmforge_tools.problems import ValidationFailed
from realmforge_tools.project import RepoPaths
from realmforge_tools.schema.chapter import ChapterDef, advancement_id, level_cap
from realmforge_tools.schema.curve import CurveDef
from realmforge_tools.schema.kill_xp_multiplier import KillXpMultiplierDef
from realmforge_tools.schema.leveling import (
    capped_xp,
    java_round,
    kill_xp,
    level_for,
    stat_points_at,
    stat_points_to,
    xp_to_next,
    xp_to_reach,
)
from realmforge_tools.validate import load_and_validate
from tests.conftest import write

DATA = "mod/src/generated/resources/data/realmforge"
QA = "qa-mod/src/generated/java/io/github/matejkadlec/realmforge/qa/generated"
LANG = "mod/src/generated/resources/assets/realmforge/lang/en_us.json"

CHAPTER_3 = """id: chapter_3
name: {en_us: Chapter 3}
order: 3
level_cap: 30
completed_level_cap: 40
"""
MULTIPLIER = """id: zombie
entity: minecraft:zombie
multiplier: 2
"""


def ke_model_xp(level: int) -> int:
    """The XP model of 2026-10-15 (RLM-192): XP(L) = KE(L) x (45 + 5L), KE(1) = 30, KE(L + 1) = KE(L) x g, rounded
    half up to 10 below 10,000 and to 50 from 10,000."""
    ke = 30.0
    for below in range(1, level):
        ke *= 1.07 if below < 10 else 1.05 if below < 18 else 1.035 if below < 30 else 1.03
    raw = ke * (45 + 5 * level)
    step = 10 if raw < 10_000 else 50
    return math.floor(raw / step + 0.5) * step


def curve(*points: tuple[int, float]) -> CurveDef:
    return CurveDef.model_validate(
        {"id": "test", "points": [{"level": level, "value": value} for level, value in points]}
    )


def chapter(chapter_id: str, order: int, cap: int, completed: int) -> ChapterDef:
    return ChapterDef.model_validate(
        {
            "id": chapter_id,
            "name": {"en_us": chapter_id},
            "order": order,
            "level_cap": cap,
            "completed_level_cap": completed,
        }
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


# The repository's content


def test_repository_chapters_cap_at_their_last_levels() -> None:
    # Matěj, 2026-10-15 (RLM-52's note, v1 layout RLM-188): 10 in chapter 1, 18 after it, 30 after chapter 2.
    content, _ = load_and_validate(RepoPaths())
    chapters = [entry.model for entry in content.chapters]
    assert level_cap(chapters, set()) == 10
    assert level_cap(chapters, {"chapter_1"}) == 18
    assert level_cap(chapters, {"chapter_1", "chapter_2"}) == 30


def test_repository_xp_curve_follows_the_ke_model() -> None:
    content, _ = load_and_validate(RepoPaths())
    xp = next(entry.model for entry in content.curves if entry.model.id == "xp_to_next_level")
    assert [(point.level, point.value) for point in xp.points] == [
        (level, ke_model_xp(level)) for level in range(1, 60)
    ]
    checks = {1: 1500, 2: 1770, 10: 5240, 18: 11000, 30: 24000, 44: 49350, 59: 98650}
    assert {level: xp_to_next(xp, level) for level in checks} == checks
    totals = {10: 25_960, 18: 85_890, 30: 281_690, 60: 1_912_740}
    assert {level: xp_to_reach(xp, level) for level in totals} == totals


def test_repository_kill_xp_is_a_ke_and_its_level_difference() -> None:
    content, _ = load_and_validate(RepoPaths())
    curves = {entry.model.id: entry.model for entry in content.curves}
    assert [curves["kill_xp"].value_at(level) for level in (1, 10, 60)] == [50, 95, 345]
    below = curves["kill_xp_below_player"]
    assert [round(below.value_at(n), 9) for n in range(9)] == [1.0, 0.9, 0.75, 0.6, 0.45, 0.3, 0.15, 0.0, 0.0]


def test_repository_kill_xp_multipliers_follow_the_model() -> None:
    content, _ = load_and_validate(RepoPaths())
    given = {entry.model.entity: entry.model for entry in content.kill_xp_multipliers}
    expected = {
        "minecraft:silverfish": 0.1,
        "minecraft:endermite": 0.1,
        "minecraft:vex": 0.1,
        "minecraft:slime": 0.25,
        "minecraft:cave_spider": 0.5,
        "minecraft:witch": 1.5,
        "minecraft:enderman": 1.5,
        "minecraft:vindicator": 1.5,
        "minecraft:breeze": 1.5,
        "minecraft:evoker": 2.5,
        "minecraft:ravager": 4,
        "minecraft:elder_guardian": 10,
        "minecraft:warden": 10,
    }
    assert {entity: entry.multiplier for entity, entry in given.items()} == expected
    slime = given["minecraft:slime"]
    assert [slime.multiplier_at(size) for size in (1, 2, 4)] == [0, 0.25, 0.25]


# XP rules (schema/leveling.py, the reference for character/XpTable and character/KillXp)


def test_java_round_rounds_halves_up() -> None:
    assert [java_round(value) for value in (0.5, 1.5, 2.5, -0.5, 2.49)] == [1, 2, 3, 0, 2]


def test_xp_to_next_rounds_and_is_at_least_one() -> None:
    xp = curve((1, 100), (3, 300))
    assert [xp_to_next(xp, level) for level in (1, 2, 3, 9)] == [100, 200, 300, 300]
    assert xp_to_next(curve((1, 0)), 1) == 1
    assert xp_to_next(curve((1, 0.5), (2, 2.5)), 2) == 3


def test_level_follows_xp_up_to_the_cap() -> None:
    xp = curve((1, 100), (3, 300))
    assert [xp_to_reach(xp, level) for level in (1, 2, 3, 4)] == [0, 100, 300, 600]
    assert [level_for(xp, total, 10) for total in (0, 99, 100, 299, 300, 600)] == [1, 1, 2, 2, 3, 4]
    assert level_for(xp, 1_000_000, 3) == 3
    assert level_for(xp, 1_000_000, 1) == 1


def test_xp_stops_at_the_cap() -> None:
    xp = curve((1, 100), (3, 300))
    # The cap of level 3 lets a player hold 300 XP: the XP of level 3, none toward level 4.
    assert [capped_xp(xp, total, 3) for total in (0, 299, 300, 301, 1_000_000)] == [0, 299, 300, 300, 300]
    assert capped_xp(xp, 1_000_000, 1) == 0
    # A raised cap lets the held XP count again, from 0 toward the next level: no level comes with it.
    held = capped_xp(xp, 1_000_000, 3)
    assert level_for(xp, held, 10) == 3


def test_kill_xp_scales_with_the_level_difference_and_multiplier() -> None:
    base = curve((1, 10), (10, 100))
    below = curve((0, 1), (2, 1), (6, 0))
    above = curve((0, 1), (4, 1.4))
    assert kill_xp(base, below, above, 10, 10, 1.0, 1.0) == 100
    assert kill_xp(base, below, above, 10, 14, 1.0, 1.0) == 50
    assert kill_xp(base, below, above, 10, 16, 1.0, 1.0) == 0
    assert kill_xp(base, below, above, 10, 8, 1.0, 1.0) == 120
    assert kill_xp(base, below, above, 10, 1, 1.0, 1.0) == 140
    assert kill_xp(base, below, above, 1, 1, 1.0, 1.5) == 15
    assert kill_xp(curve((1, -50)), below, above, 1, 1, 1.0, 1.0) == 0


def test_kill_xp_takes_the_victims_rank() -> None:
    base = curve((1, 10), (10, 100))
    below = curve((0, 1), (2, 1), (6, 0))
    above = curve((0, 1), (4, 1.4))
    assert kill_xp(base, below, above, 1, 1, 1.5, 1.0) == 15
    assert kill_xp(base, below, above, 10, 10, 10, 1.0) == 1000
    assert kill_xp(base, below, above, 10, 14, 0.5, 1.0) == 25
    assert kill_xp(base, below, above, 1, 1, 2, 1.5) == 30
    assert kill_xp(base, below, above, 10, 10, 0, 1.0) == 0


# Chapters and their level caps


def test_cap_is_the_highest_completed_cap_never_below_the_first_chapters() -> None:
    chapters = [chapter("three", 3, 30, 40), chapter("one", 1, 10, 20), chapter("two", 2, 20, 30)]
    assert level_cap(chapters, set()) == 10
    assert level_cap(chapters, {"one"}) == 20
    assert level_cap(chapters, {"two"}) == 30
    assert level_cap(chapters, {"one", "two", "three"}) == 40
    assert advancement_id("chapter_1") == "chapter/chapter_1"


def test_cap_samples_cover_none_each_and_all() -> None:
    chapters = [chapter("one", 1, 10, 20), chapter("two", 2, 20, 30)]
    assert cap_samples(chapters) == [([], 10), (["one"], 20), (["two"], 30), (["one", "two"], 30)]


def test_completing_a_chapter_must_raise_the_cap() -> None:
    with pytest.raises(ValidationError, match="must be above level_cap"):
        chapter("one", 1, 10, 10)


@pytest.mark.parametrize("field", ["order", "level_cap", "completed_level_cap"])
def test_chapter_numbers_are_positive(field: str) -> None:
    fields = {"order": 1, "level_cap": 10, "completed_level_cap": 20, field: 0}
    with pytest.raises(ValidationError):
        ChapterDef.model_validate({"id": "one", "name": {"en_us": "One"}, **fields})


def test_chapters_are_numbered_without_gaps(repo: RepoPaths) -> None:
    write(repo.content / "chapters/chapter_3.yaml", CHAPTER_3.replace("order: 3", "order: 4"))
    assert_problem(repo, "expected 3, found 4")


def test_a_chapter_starts_at_the_cap_the_one_before_ends_with(repo: RepoPaths) -> None:
    write(repo.content / "chapters/chapter_3.yaml", CHAPTER_3.replace("level_cap: 30", "level_cap: 35"))
    assert_problem(repo, "must equal the completed_level_cap of the chapter before it")


def test_leveling_needs_a_chapter(repo: RepoPaths) -> None:
    for file in (repo.content / "chapters").glob("*.yaml"):
        file.unlink()
    assert_problem(repo, "leveling needs a chapter")


def test_leveling_curves_are_required_and_bounded(repo: RepoPaths) -> None:
    (repo.content / "curves/kill_xp.yaml").unlink()
    path = repo.content / "curves/xp_to_next_level.yaml"
    path.write_text(path.read_text(encoding="utf-8").replace("value: 1500}", "value: 0}"), encoding="utf-8")
    points = repo.content / "curves/stat_points_per_level.yaml"
    points.write_text(points.read_text(encoding="utf-8").replace("value: 1}", "value: -1}"), encoding="utf-8")
    problems = problems_for(repo)
    assert any("kill_xp.yaml" in p and "leveling needs this curve" in p for p in problems), problems
    assert any("xp_to_next_level.yaml" in p and "below 1" in p for p in problems), problems
    assert any("stat_points_per_level.yaml" in p and "below 0" in p for p in problems), problems


# Stat points (RLM-196)


def test_repository_gives_one_stat_point_a_level_from_level_2() -> None:
    content, _ = load_and_validate(RepoPaths())
    points = next(entry.model for entry in content.curves if entry.model.id == "stat_points_per_level")
    assert [stat_points_at(points, level) for level in (1, 2, 3, 60)] == [0, 1, 1, 1]
    assert [stat_points_to(points, level) for level in (1, 2, 10, 18, 60)] == [0, 1, 9, 17, 59]


def test_stat_points_round_half_up_and_are_never_negative() -> None:
    curve = CurveDef.model_validate(
        {
            "id": "points",
            "points": [{"level": 2, "value": 0.5}, {"level": 4, "value": 2.5}, {"level": 5, "value": -3}],
        }
    )
    assert [stat_points_at(curve, level) for level in (1, 2, 3, 4, 5)] == [0, 1, 2, 3, 0]
    assert stat_points_to(curve, 5) == 6


# Generation


def test_generated_chapter_data_advancement_and_lang(repo: RepoPaths) -> None:
    write(repo.content / "chapters/chapter_3.yaml", CHAPTER_3)
    files = generate(repo)
    assert json.loads(files[f"{DATA}/realmforge/chapter/chapter_3.json"]) == {
        "name": "chapter.realmforge.chapter_3.name",
        "order": 3,
        "level_cap": 30,
        "completed_level_cap": 40,
    }
    assert json.loads(files[f"{DATA}/advancement/chapter/chapter_3.json"]) == {
        "criteria": {"completed": {"trigger": "minecraft:impossible"}},
        "requirements": [["completed"]],
    }
    assert json.loads(files[LANG])["chapter.realmforge.chapter_3.name"] == "Chapter 3"
    expectations = files[f"{QA}/GeneratedChapterExpectations.java"].decode()
    assert '"realmforge:chapter/chapter_3"' in expectations
    assert (
        'new CapSample(List.of("realmforge:chapter_1", "realmforge:chapter_2", "realmforge:chapter_3"), 40)'
        in expectations
    )


def test_leveling_expectations_match_the_reference_rules() -> None:
    content, _ = load_and_validate(RepoPaths())
    files = {RepoPaths().rel(path): data for path, data in generate_all(content, RepoPaths()).items()}
    source = files[f"{QA}/GeneratedLevelingExpectations.java"].decode()
    xp = next(entry.model for entry in content.curves if entry.model.id == "xp_to_next_level")
    assert f"new LevelXp(1, {xp_to_next(xp, 1)}L, 0L, 0)" in source
    # One stat point a level from level 2 on (Matěj, 2026-10-15, RLM-196).
    assert f"new LevelXp(10, {xp_to_next(xp, 10)}L, {xp_to_reach(xp, 10)}L, 9)" in source
    assert f"new LevelXp(31, {xp_to_next(xp, 31)}L, {xp_to_reach(xp, 31)}L, 30)" in source
    for total, cap, level, held in level_samples(xp, [10, 18, 30]):
        assert f"new LevelSample({total}L, {cap}, {level}, {held}L)" in source
    assert "new KillSample(1, 1, 1.0, 1.0, 50L)" in source
    beyond = xp_to_reach(xp, 35)
    assert [level_for(xp, beyond, cap) for cap in (10, 18, 30)] == [10, 18, 30]
    # Matěj's report (2026-10-15): 60,000 XP at cap 10 is held as level 10's 25,960, and chapter 1 keeps level 10.
    assert [capped_xp(xp, 60_000, 10), capped_xp(xp, beyond, 18)] == [25_960, 85_890]
    assert level_for(xp, capped_xp(xp, 60_000, 10), 18) == 10


# Kill XP multipliers


def test_multiplier_by_size_takes_the_first_size_the_mob_fits() -> None:
    slime = KillXpMultiplierDef.model_validate(
        {
            "id": "slime",
            "entity": "minecraft:slime",
            "multiplier": 0.25,
            "by_size": [{"max_size": 1, "multiplier": 0}, {"max_size": 3, "multiplier": 0.1}],
        }
    )
    assert [slime.multiplier_at(size) for size in (None, 1, 2, 3, 4, 127)] == [0.25, 0, 0.1, 0.1, 0.25, 0.25]


@pytest.mark.parametrize(
    ("fields", "message"),
    [
        ({"multiplier": -1}, "greater than or equal to 0"),
        ({"multiplier": 2000}, "less than or equal to 1000"),
        ({"by_size": [{"max_size": 0, "multiplier": 0}]}, "greater than or equal to 1"),
        (
            {"by_size": [{"max_size": 2, "multiplier": 0}, {"max_size": 2, "multiplier": 1}]},
            "strictly increase",
        ),
    ],
)
def test_multiplier_shapes_are_refused(fields: dict[str, object], message: str) -> None:
    with pytest.raises(ValidationError, match=message):
        KillXpMultiplierDef.model_validate(
            {"id": "slime", "entity": "minecraft:slime", "multiplier": 1, **fields}
        )


def test_multiplier_entity_must_exist(repo: RepoPaths) -> None:
    write(
        repo.content / "kill_xp_multipliers/zombie.yaml",
        MULTIPLIER.replace("minecraft:zombie", "minecraft:zombi"),
    )
    assert_problem(repo, "unknown id 'minecraft:zombi' in registry minecraft:entity_type")


def test_one_multiplier_per_entity_type(repo: RepoPaths) -> None:
    write(repo.content / "kill_xp_multipliers/zombie.yaml", MULTIPLIER)
    write(
        repo.content / "kill_xp_multipliers/zombie_again.yaml",
        MULTIPLIER.replace("id: zombie", "id: zombie_again"),
    )
    assert_problem(repo, "minecraft:zombie already has a kill XP multiplier")


def test_only_slimes_give_by_size(repo: RepoPaths) -> None:
    write(
        repo.content / "kill_xp_multipliers/zombie.yaml",
        MULTIPLIER + "by_size:\n  - {max_size: 1, multiplier: 0}\n",
    )
    assert_problem(repo, "minecraft:zombie gives by multiplier alone")


def test_generated_multiplier_data_and_expectations(repo: RepoPaths) -> None:
    write(repo.content / "kill_xp_multipliers/zombie.yaml", MULTIPLIER)
    files = generate(repo)
    data = f"{DATA}/realmforge/kill_xp_multiplier"
    assert json.loads(files[f"{data}/zombie.json"]) == {
        "entity": "minecraft:zombie",
        "multiplier": 2.0,
        "by_size": [],
    }
    assert json.loads(files[f"{data}/slime.json"]) == {
        "entity": "minecraft:slime",
        "multiplier": 0.25,
        "by_size": [{"max_size": 1, "multiplier": 0.0}],
    }
    expectations = files[f"{QA}/GeneratedKillXpMultiplierExpectations.java"].decode()
    assert '"realmforge:zombie", "minecraft:zombie", 2.0,' in expectations
    assert "new Sample(1, 0.0), new Sample(2, 0.25)" in expectations
