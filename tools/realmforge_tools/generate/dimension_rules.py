"""Dimension rules: the QA mod's expectations (the data itself: datapack.py)."""

from __future__ import annotations

from pathlib import Path

from realmforge_tools.content import Content
from realmforge_tools.generate.javagen import header, java_file_path, string_literal
from realmforge_tools.project import MOD_ID, QA_JAVA_PACKAGE, RepoPaths
from realmforge_tools.schema.dimension_rules import MobLevelRuleDef

QA_GENERATED_PACKAGE = f"{QA_JAVA_PACKAGE}.generated"
SOURCES = "content/dimensions/"
# How far beyond the cap the samples reach, in levels, and the farthest point from the origin: the world border.
_EXTRA_LEVELS = 1
_MAX_SAMPLED_LEVELS = 6
_WORLD_EDGE = 30_000_000.0


def generate(content: Content, paths: RepoPaths) -> dict[Path, bytes]:
    return {
        java_file_path(
            paths.qa_generated / "java", QA_GENERATED_PACKAGE, "GeneratedDimensionRuleExpectations"
        ): (_expectations(content))
    }


def sample_positions(rule: MobLevelRuleDef) -> list[tuple[float, float, float]]:
    """Where the GameTest compares the game's level rule with ``MobLevelRuleDef.level_at``: the origin; on both
    sides of each distance step up to the cap and one beyond; a diagonal; on both sides of the depth line and its
    first steps; and the world's edge."""
    top = float(rule.depth.below_y + 16) if rule.depth is not None else 64.0
    step = float(rule.blocks_per_level)
    positions = [(0.0, top, 0.0)]
    steps = min(rule.max_level - rule.origin_level + _EXTRA_LEVELS, _MAX_SAMPLED_LEVELS)
    for k in range(1, steps + 1):
        positions += [(k * step - 0.5, top, 0.0), (0.0, top, -k * step)]
    positions.append((step, top, step))
    if rule.depth is not None:
        line = float(rule.depth.below_y)
        depth_step = float(rule.depth.blocks_per_level)
        positions += [(0.0, line, 0.0), (0.0, line - 0.5, 0.0), (0.0, line - depth_step, 0.0)]
        positions.append((0.0, line - 2 * depth_step - 0.5, 0.0))
    positions.append((_WORLD_EDGE, top, 0.0))
    return positions


def _double(value: float) -> str:
    """A Java double literal; repr() gives the shortest text that reads back as the same double."""
    return repr(float(value))


def _rule(rule: MobLevelRuleDef | None) -> str:
    if rule is None:
        return "Optional.empty()"
    depth = (
        f"Optional.of(new Depth({rule.depth.below_y}, {rule.depth.blocks_per_level}))"
        if rule.depth is not None
        else "Optional.empty()"
    )
    return (
        f"Optional.of(new MobLevelRule({rule.origin_level}, {rule.blocks_per_level}, {depth}, {rule.jitter}, "
        f"{rule.min_level}, {rule.max_level}))"
    )


def _samples(rule: MobLevelRuleDef | None) -> str:
    if rule is None:
        return "List.of()"
    samples = [
        f"new Sample({_double(x)}, {_double(y)}, {_double(z)}, {rule.level_at(x, y, z)}, "
        f"{rule.level_at(x, y, z, -rule.jitter)}, {rule.level_at(x, y, z, rule.jitter)})"
        for x, y, z in sample_positions(rule)
    ]
    return "List.of(\n                            " + ",\n                            ".join(samples) + ")"


def _expectations(content: Content) -> bytes:
    rows = []
    for entry in content.dimensions:
        rules = entry.model
        rows.append(
            "            new DimensionRulesExpectation(\n"
            f"                    {string_literal(f'{MOD_ID}:{rules.id}')},\n"
            f"                    {string_literal(rules.dimension)},\n"
            f"                    {_rule(rules.mob_levels)},\n"
            f"                    {_samples(rules.mob_levels)})"
        )
    entries = "List.of(\n" + ",\n".join(rows) + ")" if rows else "List.of()"
    source = (
        header(Path(__file__), SOURCES) + f"package {QA_GENERATED_PACKAGE};\n"
        "\n"
        "import java.util.List;\n"
        "import java.util.Optional;\n"
        "\n"
        "/** What {@code content/dimensions/} says about every dimension, so GameTests can check that the game agrees. */\n"
        "public final class GeneratedDimensionRuleExpectations {\n"
        "    /** Levels added with depth, as written in the YAML. */\n"
        "    public record Depth(int belowY, int blocksPerLevel) {\n"
        "    }\n"
        "\n"
        "    /** The mob level rule, as written in the YAML. */\n"
        "    public record MobLevelRule(int originLevel, int blocksPerLevel, Optional<Depth> depth, int jitter,\n"
        "            int minLevel, int maxLevel) {\n"
        "    }\n"
        "\n"
        "    /**\n"
        "     * The level the pipeline's rule gives a mob spawning at a position: without jitter, and with the jitter's\n"
        "     * lowest and highest roll. The game must agree.\n"
        "     */\n"
        "    public record Sample(double x, double y, double z, int level, int lowest, int highest) {\n"
        "    }\n"
        "\n"
        "    /**\n"
        "     * Expected state of one entry. Ids are full resource locations: the entry's, e.g. {@code realmforge:overworld},\n"
        "     * and the dimension's, e.g. {@code minecraft:overworld}.\n"
        "     */\n"
        "    public record DimensionRulesExpectation(String id, String dimension, Optional<MobLevelRule> mobLevels,\n"
        "            List<Sample> samples) {\n"
        "    }\n"
        "\n"
        f"    public static final List<DimensionRulesExpectation> DIMENSION_RULES = {entries};\n"
        "\n"
        "    private GeneratedDimensionRuleExpectations() {\n"
        "    }\n"
        "}\n"
    )
    return source.encode("utf-8")
