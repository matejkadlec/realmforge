"""Dimension rules: the QA mod's expectations (the data itself: datapack.py)."""

from __future__ import annotations

from pathlib import Path

from realmforge_tools.content import Content
from realmforge_tools.formats.jsonfmt import dump_json
from realmforge_tools.generate.javagen import header, java_file_path, string_literal
from realmforge_tools.project import MOD_ID, QA_JAVA_PACKAGE, RepoPaths
from realmforge_tools.schema.dimension_rules import DimensionRulesDef, MobLevelRuleDef

QA_GENERATED_PACKAGE = f"{QA_JAVA_PACKAGE}.generated"
SOURCES = "content/dimensions/"


def generate(content: Content, paths: RepoPaths) -> dict[Path, bytes]:
    return {
        java_file_path(
            paths.qa_generated / "java", QA_GENERATED_PACKAGE, "GeneratedDimensionRuleExpectations"
        ): (_expectations(content))
    }


def sample_player_levels(rule: MobLevelRuleDef) -> list[int]:
    """The player levels at which the GameTest compares the game's level rule with ``MobLevelRuleDef.chances``:
    level 1; around the floor (below it, at it, one above, and where the floor stops cutting offsets); the middle;
    around the cap (where it starts cutting, at it, above it, and so far above that it leaves no offset)."""
    entry, top, spread = rule.entry_level, rule.max_level, rule.spread
    levels = {
        1,
        entry - 1,
        entry,
        entry + 1,
        entry + spread,
        (entry + top) // 2,
        top - spread,
        top,
        top + 1,
        top + spread + 1,
    }
    return sorted(level for level in levels if level >= 1)


def _ints(values: list[int] | tuple[int, ...]) -> str:
    return f"List.of({', '.join(str(value) for value in values)})"


def _rule(rule: MobLevelRuleDef | None) -> str:
    if rule is None:
        return "Optional.empty()"
    return f"Optional.of(new MobLevelRule({rule.entry_level}, {rule.max_level}, {_ints(rule.weights)}))"


def _samples(rule: MobLevelRuleDef | None) -> str:
    if rule is None:
        return "List.of()"
    samples = []
    for player_level in sample_player_levels(rule):
        chances = rule.chances(player_level)
        samples.append(f"new Sample({player_level}, {chances.lowest}, {_ints(chances.weights)})")
    return "List.of(\n                            " + ",\n                            ".join(samples) + ")"


def _access(rules: DimensionRulesDef) -> str:
    """The access requirements as the JSON the data holds, one string each, so the GameTest decodes them."""
    requirements = [
        string_literal(dump_json(requirement.model_dump(), sort_keys=True).decode().strip())
        for requirement in rules.access or []
    ]
    return f"List.of({', '.join(requirements)})"


def _expectations(content: Content) -> bytes:
    rows = []
    for entry in content.dimensions:
        rules = entry.model
        rows.append(
            "            new DimensionRulesExpectation(\n"
            f"                    {string_literal(f'{MOD_ID}:{rules.id}')},\n"
            f"                    {string_literal(rules.dimension)},\n"
            f"                    {_rule(rules.mob_levels)},\n"
            f"                    {_samples(rules.mob_levels)},\n"
            f"                    {_access(rules)})"
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
        "    /** The mob level rule, as written in the YAML. */\n"
        "    public record MobLevelRule(int entryLevel, int maxLevel, List<Integer> weights) {\n"
        "    }\n"
        "\n"
        "    /**\n"
        "     * The levels the pipeline's rule gives a mob spawning near a player of {@code playerLevel}: from\n"
        "     * {@code lowest} up, one weight per level, not normalised. The game must agree.\n"
        "     */\n"
        "    public record Sample(int playerLevel, int lowest, List<Integer> weights) {\n"
        "    }\n"
        "\n"
        "    /**\n"
        "     * Expected state of one entry. Ids are full resource locations: the entry's, e.g. {@code realmforge:overworld},\n"
        "     * and the dimension's, e.g. {@code minecraft:overworld}. Each access requirement is the JSON the data holds.\n"
        "     */\n"
        "    public record DimensionRulesExpectation(String id, String dimension, Optional<MobLevelRule> mobLevels,\n"
        "            List<Sample> samples, List<String> access) {\n"
        "    }\n"
        "\n"
        f"    public static final List<DimensionRulesExpectation> DIMENSION_RULES = {entries};\n"
        "\n"
        "    private GeneratedDimensionRuleExpectations() {\n"
        "    }\n"
        "}\n"
    )
    return source.encode("utf-8")
