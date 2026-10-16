"""Chapters, kill XP multipliers and leveling: each chapter's advancement, and the QA mod's expectations for chapters,
kill XP multipliers and character XP (the chapter and multiplier data themselves and the chapters' lang entries:
datapack.py).

- ``data/realmforge/advancement/chapter/<id>.json``: the advancement that marks the chapter completed, with
  ``minecraft:impossible`` as its only criterion, so only a quest reward or a command grants it (architecture 16).
  It has no display, so it shows no toast and no tab; leveling announces the raised cap.
- ``GeneratedChapterExpectations``: every chapter, and the level cap the pipeline computes for sample sets of
  completed chapters.
- ``GeneratedKillXpMultiplierExpectations``: every kill XP multiplier, with its value at sample sizes.
- ``GeneratedLevelingExpectations``: the XP and the stat points per level, the levels of sample XP totals under sample
  caps, and the XP of sample kills, from the curves in ``content/curves/`` (schema/leveling.py).
"""

from __future__ import annotations

from pathlib import Path

from realmforge_tools.content import Content
from realmforge_tools.formats.jsonfmt import dump_json
from realmforge_tools.generate.datapack import data_dir, translation_key
from realmforge_tools.generate.javagen import header, java_file_path, string_literal
from realmforge_tools.project import MOD_ID, QA_JAVA_PACKAGE, RepoPaths
from realmforge_tools.schema.chapter import ChapterDef, advancement_id, level_cap
from realmforge_tools.schema.curve import CurveDef
from realmforge_tools.schema.kill_xp_multiplier import KillXpMultiplierDef
from realmforge_tools.schema.leveling import (
    KILL_XP,
    KILL_XP_ABOVE_PLAYER,
    KILL_XP_BELOW_PLAYER,
    STAT_POINTS_PER_LEVEL,
    XP_TO_NEXT_LEVEL,
    capped_xp,
    kill_xp,
    level_for,
    stat_points_to,
    xp_to_next,
    xp_to_reach,
)

QA_GENERATED_PACKAGE = f"{QA_JAVA_PACKAGE}.generated"
REGISTRY = "chapter"
# The levels the kill samples combine (victim against player), and the (rank, XP multiplier) pairs they take.
KILL_SAMPLE_LEVELS = (1, 3, 5, 10, 14, 20)
KILL_SAMPLE_FACTORS = ((1.0, 1.0), (1.0, 1.5), (0.25, 1.0), (1.5, 1.0), (10.0, 1.0))
# The sizes the kill XP multiplier samples take (none: a mob without a size).
SIZE_SAMPLES = (None, 1, 2, 4, 127)


def generate(content: Content, paths: RepoPaths) -> dict[Path, bytes]:
    files: dict[Path, bytes] = {}
    advancement_dir = data_dir(paths) / "advancement"
    for entry in content.chapters:
        files[advancement_dir / f"{advancement_id(entry.model.id)}.json"] = dump_json(_advancement())
    qa_java = paths.qa_generated / "java"
    files[java_file_path(qa_java, QA_GENERATED_PACKAGE, "GeneratedChapterExpectations")] = _chapters(content)
    files[java_file_path(qa_java, QA_GENERATED_PACKAGE, "GeneratedKillXpMultiplierExpectations")] = (
        _kill_xp_multipliers([entry.model for entry in content.kill_xp_multipliers])
    )
    curves = {entry.model.id: entry.model for entry in content.curves}
    files[java_file_path(qa_java, QA_GENERATED_PACKAGE, "GeneratedLevelingExpectations")] = _leveling(
        [entry.model for entry in content.chapters], curves
    )
    return files


def _advancement() -> dict[str, object]:
    return {
        "criteria": {"completed": {"trigger": "minecraft:impossible"}},
        "requirements": [["completed"]],
    }


def _double(value: float) -> str:
    """A Java double literal; repr() gives the shortest text that reads back as the same double."""
    return repr(float(value))


def _list(rows: list[str]) -> str:
    return "List.of(\n" + ",\n".join(rows) + ")" if rows else "List.of()"


def _strings(values: list[str]) -> str:
    return f"List.of({', '.join(string_literal(value) for value in values)})"


def cap_samples(chapters: list[ChapterDef]) -> list[tuple[list[str], int]]:
    """Sets of completed chapters (none, each one alone, all of them) with the cap each gives."""
    ids = [chapter.id for chapter in sorted(chapters, key=lambda chapter: chapter.order)]
    sets = [[]] + [[chapter_id] for chapter_id in ids]
    if len(ids) > 1:
        sets.append(ids)
    return [(completed, level_cap(chapters, set(completed))) for completed in sets]


def _chapters(content: Content) -> bytes:
    chapters = [entry.model for entry in content.chapters]
    rows = [
        "            new ChapterExpectation(\n"
        f"                    {string_literal(f'{MOD_ID}:{chapter.id}')},\n"
        f"                    {string_literal(translation_key(REGISTRY, chapter.id, 'name'))},\n"
        f"                    {chapter.order}, {chapter.level_cap}, {chapter.completed_level_cap},\n"
        f"                    {string_literal(f'{MOD_ID}:{advancement_id(chapter.id)}')})"
        for chapter in chapters
    ]
    caps = [
        f"            new CapSample({_strings([f'{MOD_ID}:{chapter_id}' for chapter_id in completed])}, {cap})"
        for completed, cap in cap_samples(chapters)
    ]
    source = (
        header(Path(__file__), "content/chapters/") + f"package {QA_GENERATED_PACKAGE};\n"
        "\n"
        "import java.util.List;\n"
        "\n"
        "/** What {@code content/chapters/} says about every chapter, so GameTests can check that the game agrees. */\n"
        "public final class GeneratedChapterExpectations {\n"
        "    /**\n"
        "     * Expected state of one chapter. Ids are full resource locations, e.g. {@code realmforge:chapter_1}; the\n"
        "     * advancement is the one that marks it completed.\n"
        "     */\n"
        "    public record ChapterExpectation(String id, String nameKey, int order, int levelCap, int completedLevelCap,\n"
        "            String advancement) {\n"
        "    }\n"
        "\n"
        "    /** The level cap the pipeline computes for a player who completed exactly these chapters. */\n"
        "    public record CapSample(List<String> completed, int cap) {\n"
        "    }\n"
        "\n"
        f"    public static final List<ChapterExpectation> CHAPTERS = {_list(rows)};\n"
        "\n"
        f"    public static final List<CapSample> CAPS = {_list(caps)};\n"
        "\n"
        "    private GeneratedChapterExpectations() {\n"
        "    }\n"
        "}\n"
    )
    return source.encode("utf-8")


def level_samples(xp_curve: CurveDef, caps: list[int]) -> list[tuple[int, int, int, int]]:
    """XP totals around each level's threshold up to the highest cap, and far beyond it, under each cap: (total XP,
    cap, level, the XP the cap lets the player hold)."""
    top = max(caps)
    totals = {0}
    for level in (2, 5, 10, top):
        reach = xp_to_reach(xp_curve, level)
        totals.update({reach - 1, reach})
    totals.add(xp_to_reach(xp_curve, top + 5))
    return [
        (total, cap, level_for(xp_curve, total, cap), capped_xp(xp_curve, total, cap))
        for cap in sorted(set(caps))
        for total in sorted(totals)
    ]


def _leveling(chapters: list[ChapterDef], curves: dict[str, CurveDef]) -> bytes:
    xp_curve, points_curve = curves[XP_TO_NEXT_LEVEL], curves[STAT_POINTS_PER_LEVEL]
    caps = sorted({cap for _, cap in cap_samples(chapters)})
    top = max(caps)
    levels = [
        f"            new LevelXp({level}, {xp_to_next(xp_curve, level)}L, {xp_to_reach(xp_curve, level)}L, "
        f"{stat_points_to(points_curve, level)})"
        for level in range(1, top + 2)
    ]
    samples = [
        f"            new LevelSample({total}L, {cap}, {level}, {held}L)"
        for total, cap, level, held in level_samples(xp_curve, caps)
    ]
    base, below, above = curves[KILL_XP], curves[KILL_XP_BELOW_PLAYER], curves[KILL_XP_ABOVE_PLAYER]
    kills = [
        f"            new KillSample({victim}, {player}, {_double(rank)}, {_double(multiplier)}, "
        f"{kill_xp(base, below, above, victim, player, rank, multiplier)}L)"
        for victim in KILL_SAMPLE_LEVELS
        for player in KILL_SAMPLE_LEVELS
        for rank, multiplier in KILL_SAMPLE_FACTORS
    ]
    source = (
        header(Path(__file__), "content/curves/ and content/chapters/") + f"package {QA_GENERATED_PACKAGE};\n"
        "\n"
        "import java.util.List;\n"
        "\n"
        "/**\n"
        " * Character XP as the pipeline computes it from {@code content/curves/} (architecture 10.2), so GameTests can\n"
        " * check that the game's leveling agrees.\n"
        " */\n"
        "public final class GeneratedLevelingExpectations {\n"
        "    /**\n"
        "     * The XP from a level to the next, the total XP a player needs for the level, and the primary stat points\n"
        "     * a player of the level has earned.\n"
        "     */\n"
        "    public record LevelXp(int level, long toNext, long toReach, int statPoints) {\n"
        "    }\n"
        "\n"
        "    /** The level of a player with this much XP under this level cap, and the XP the cap lets them hold. */\n"
        "    public record LevelSample(long totalXp, int cap, int level, long cappedXp) {\n"
        "    }\n"
        "\n"
        "    /**\n"
        "     * The XP a player of a level gets for a kill of a victim's level, with the victim's kill XP multiplier (its\n"
        "     * rank) and the player's XP multiplier (1.0 is 100 %).\n"
        "     */\n"
        "    public record KillSample(int victimLevel, int playerLevel, double rank, double multiplier, long xp) {\n"
        "    }\n"
        "\n"
        f"    public static final List<LevelXp> LEVELS = {_list(levels)};\n"
        "\n"
        f"    public static final List<LevelSample> LEVEL_SAMPLES = {_list(samples)};\n"
        "\n"
        f"    public static final List<KillSample> KILLS = {_list(kills)};\n"
        "\n"
        "    private GeneratedLevelingExpectations() {\n"
        "    }\n"
        "}\n"
    )
    return source.encode("utf-8")


def _kill_xp_multipliers(entries: list[KillXpMultiplierDef]) -> bytes:
    rows = []
    for entry in entries:
        sizes = ", ".join(
            f"new SizeMultiplier({size.max_size}, {_double(size.multiplier)})" for size in entry.by_size
        )
        samples = ", ".join(
            f"new Sample({-1 if size is None else size}, {_double(entry.multiplier_at(size))})"
            for size in SIZE_SAMPLES
        )
        rows.append(
            "            new KillXpMultiplierExpectation(\n"
            f"                    {string_literal(f'{MOD_ID}:{entry.id}')}, {string_literal(entry.entity)}, "
            f"{_double(entry.multiplier)},\n"
            f"                    List.of({sizes}),\n"
            f"                    List.of({samples}))"
        )
    source = (
        header(Path(__file__), "content/kill_xp_multipliers/") + f"package {QA_GENERATED_PACKAGE};\n"
        "\n"
        "import java.util.List;\n"
        "\n"
        "/**\n"
        " * What {@code content/kill_xp_multipliers/} says about every kill XP multiplier, so GameTests can check that the\n"
        " * game agrees.\n"
        " */\n"
        "public final class GeneratedKillXpMultiplierExpectations {\n"
        "    /** The multiplier for mobs of a size up to {@code maxSize}. */\n"
        "    public record SizeMultiplier(int maxSize, double multiplier) {\n"
        "    }\n"
        "\n"
        "    /** The multiplier the pipeline gives a mob of a size, -1 for a mob without one. */\n"
        "    public record Sample(int size, double multiplier) {\n"
        "    }\n"
        "\n"
        "    /** Expected state of one entry. Ids are full resource locations, e.g. {@code realmforge:witch}. */\n"
        "    public record KillXpMultiplierExpectation(String id, String entity, double multiplier,\n"
        "            List<SizeMultiplier> bySize, List<Sample> samples) {\n"
        "    }\n"
        "\n"
        f"    public static final List<KillXpMultiplierExpectation> MULTIPLIERS = {_list(rows)};\n"
        "\n"
        "    private GeneratedKillXpMultiplierExpectations() {\n"
        "    }\n"
        "}\n"
    )
    return source.encode("utf-8")
