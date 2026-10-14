"""Chapters (content/chapters/) and leveling (architecture 10.2): validation, the reference XP and level cap rules,
and generation."""

from __future__ import annotations

import json

import pytest
from pydantic import ValidationError

from realmforge_tools.generate import generate_all
from realmforge_tools.generate.chapters import cap_samples, level_samples
from realmforge_tools.problems import ValidationFailed
from realmforge_tools.project import RepoPaths
from realmforge_tools.schema.chapter import ChapterDef, advancement_id, level_cap
from realmforge_tools.schema.curve import CurveDef
from realmforge_tools.schema.leveling import java_round, kill_xp, level_for, xp_to_next, xp_to_reach
from realmforge_tools.validate import load_and_validate
from tests.conftest import write

DATA = "mod/src/generated/resources/data/realmforge"
QA = "qa-mod/src/generated/java/io/github/matejkadlec/realmforge/qa/generated"
LANG = "mod/src/generated/resources/assets/realmforge/lang/en_us.json"

CHAPTER_2 = """id: chapter_2
name: {en_us: Chapter 2}
order: 2
level_cap: 20
completed_level_cap: 30
"""


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


def test_repository_chapters_follow_decision_d2() -> None:
    content, _ = load_and_validate(RepoPaths())
    chapters = [entry.model for entry in content.chapters]
    assert level_cap(chapters, set()) == 10
    assert level_cap(chapters, {"chapter_1"}) == 20


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


def test_kill_xp_scales_with_the_level_difference_and_multiplier() -> None:
    base = curve((1, 10), (10, 100))
    below = curve((0, 1), (2, 1), (6, 0))
    above = curve((0, 1), (4, 1.4))
    assert kill_xp(base, below, above, 10, 10, 1.0) == 100
    assert kill_xp(base, below, above, 10, 14, 1.0) == 50
    assert kill_xp(base, below, above, 10, 16, 1.0) == 0
    assert kill_xp(base, below, above, 10, 8, 1.0) == 120
    assert kill_xp(base, below, above, 10, 1, 1.0) == 140
    assert kill_xp(base, below, above, 1, 1, 1.5) == 15
    assert kill_xp(curve((1, -50)), below, above, 1, 1, 1.0) == 0


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
    write(
        repo.content / "chapters/chapter_3.yaml",
        CHAPTER_2.replace("chapter_2", "chapter_3").replace("2\n", "3\n"),
    )
    assert_problem(repo, "expected 2, found 3")


def test_a_chapter_starts_at_the_cap_the_one_before_ends_with(repo: RepoPaths) -> None:
    write(repo.content / "chapters/chapter_2.yaml", CHAPTER_2.replace("level_cap: 20", "level_cap: 15"))
    assert_problem(repo, "must equal the completed_level_cap of the chapter before it")


def test_leveling_needs_a_chapter(repo: RepoPaths) -> None:
    (repo.content / "chapters/chapter_1.yaml").unlink()
    assert_problem(repo, "leveling needs a chapter")


def test_leveling_curves_are_required_and_bounded(repo: RepoPaths) -> None:
    (repo.content / "curves/kill_xp.yaml").unlink()
    path = repo.content / "curves/xp_to_next_level.yaml"
    path.write_text(path.read_text(encoding="utf-8").replace("value: 100}", "value: 0}"), encoding="utf-8")
    problems = problems_for(repo)
    assert any("kill_xp.yaml" in p and "leveling needs this curve" in p for p in problems), problems
    assert any("xp_to_next_level.yaml" in p and "below 1" in p for p in problems), problems


# Generation


def test_generated_chapter_data_advancement_and_lang(repo: RepoPaths) -> None:
    write(repo.content / "chapters/chapter_2.yaml", CHAPTER_2)
    files = generate(repo)
    assert json.loads(files[f"{DATA}/realmforge/chapter/chapter_2.json"]) == {
        "name": "chapter.realmforge.chapter_2.name",
        "order": 2,
        "level_cap": 20,
        "completed_level_cap": 30,
    }
    assert json.loads(files[f"{DATA}/advancement/chapter/chapter_2.json"]) == {
        "criteria": {"completed": {"trigger": "minecraft:impossible"}},
        "requirements": [["completed"]],
    }
    assert json.loads(files[LANG])["chapter.realmforge.chapter_2.name"] == "Chapter 2"
    expectations = files[f"{QA}/GeneratedChapterExpectations.java"].decode()
    assert '"realmforge:chapter/chapter_2"' in expectations
    assert 'new CapSample(List.of("realmforge:chapter_1", "realmforge:chapter_2"), 30)' in expectations


def test_leveling_expectations_match_the_reference_rules() -> None:
    content, _ = load_and_validate(RepoPaths())
    files = {RepoPaths().rel(path): data for path, data in generate_all(content, RepoPaths()).items()}
    source = files[f"{QA}/GeneratedLevelingExpectations.java"].decode()
    xp = next(entry.model for entry in content.curves if entry.model.id == "xp_to_next_level")
    assert f"new LevelXp(10, {xp_to_next(xp, 10)}L, {xp_to_reach(xp, 10)}L)" in source
    assert f"new LevelXp(21, {xp_to_next(xp, 21)}L, {xp_to_reach(xp, 21)}L)" in source
    for total, cap, level in level_samples(xp, [10, 20]):
        assert f"new LevelSample({total}L, {cap}, {level})" in source
    beyond = xp_to_reach(xp, 25)
    assert level_for(xp, beyond, 10) == 10
    assert level_for(xp, beyond, 20) == 20
