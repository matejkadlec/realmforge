"""Stats: the attribute registrations, and the QA mod's expectations for stats and stat rules (the stat rules'
data itself: datapack.py; the stat names: the lang files, lang.py)."""

from __future__ import annotations

import math
from pathlib import Path

from realmforge_tools.content import Content
from realmforge_tools.generate.javagen import constant_name, header, java_file_path, string_literal
from realmforge_tools.project import JAVA_PACKAGE, MOD_ID, QA_JAVA_PACKAGE, RepoPaths
from realmforge_tools.schema.common import LocalizedText
from realmforge_tools.schema.stat import StatDef, stat_description_id
from realmforge_tools.schema.stat_rule import ROLES, StatRuleDef

GENERATED_PACKAGE = f"{JAVA_PACKAGE}.generated"
QA_GENERATED_PACKAGE = f"{QA_JAVA_PACKAGE}.generated"
SOURCES = "content/stats/ and content/stat_rules/"


def generate(content: Content, paths: RepoPaths) -> dict[Path, bytes]:
    return {
        java_file_path(paths.mod_generated / "java", GENERATED_PACKAGE, "GeneratedStats"): _registrations(
            content
        ),
        java_file_path(paths.qa_generated / "java", QA_GENERATED_PACKAGE, "GeneratedStatExpectations"): (
            _expectations(content)
        ),
    }


def translations(content: Content) -> dict[str, LocalizedText]:
    """Every stat's name, by its attribute's translation key ``attribute.realmforge.<id>``."""
    return {stat_description_id(entry.model.id): entry.model.name for entry in content.stats}


def sample_values(rule: StatRuleDef, stat_max: float | None = None) -> list[float]:
    """Stat values at which the GameTest compares the game's bonuses with ``StatRuleDef.bonuses_at``: none, one
    point, a fraction, both sides of every milestone, and past every cap, as far as the stat reaches: up to its
    maximum ``stat_max``, where validation keeps every total finite (a cap whose threshold overflows a double is left
    out too)."""
    values = {0.0, 1.0, 12.5}
    for milestone in rule.milestones:
        values |= {float(milestone.points - 1), float(milestone.points)}
    for entry in rule.per_point:
        if entry.game_max is not None:
            threshold = entry.game_max / entry.game_amount
            if math.isfinite(threshold):
                values.add(math.ceil(threshold) + 10.0)
    if stat_max is not None:
        values = {value for value in values if value <= stat_max}
    return sorted(values)


def _double(value: float) -> str:
    """A Java double literal; repr() gives the shortest text that reads back as the same double."""
    if not math.isfinite(value):
        raise ValueError(f"{value} has no Java double literal; validation should have refused the content")
    return repr(float(value))


def _comment(stat: StatDef) -> str:
    """The stat's kind and English name, safe inside a Javadoc comment."""
    english = stat.name.en_us.replace("*/", "*&#47;").replace("@", "&#64;")
    return f'{stat.kind}, "{english}"'


def _attribute(stat: StatDef) -> str:
    description_id = string_literal(stat_description_id(stat.id))
    numbers = f"{_double(stat.game_default)}, 0.0, {_double(stat.game_max)}"
    attribute_class = "PercentageAttribute" if stat.percent else "RangedAttribute"
    return f"new {attribute_class}({description_id}, {numbers})"


def _registrations(content: Content) -> bytes:
    fields = "".join(
        f"    /** {{@code {MOD_ID}:{entry.model.id}}} from {{@code {entry.source}}}: {_comment(entry.model)}. */\n"
        f"    public static final DeferredHolder<Attribute, Attribute> {constant_name(entry.model.id)} =\n"
        f"            ATTRIBUTES.register({string_literal(entry.model.id)},\n"
        f"                    () -> {_attribute(entry.model)}.setSyncable(true));\n"
        "\n"
        for entry in content.stats
    )
    primary = ", ".join(constant_name(e.model.id) for e in content.stats if e.model.kind == "primary")
    every = ", ".join(constant_name(e.model.id) for e in content.stats)
    exhaustion = ", ".join(
        f"new ExhaustionReduction({constant_name(e.model.id)}, "
        f"{_double(e.model.lowers_exhaustion.per_point_percent / 100)}, "
        f"{_double(e.model.lowers_exhaustion.max_percent / 100)})"
        for e in content.stats
        if e.model.lowers_exhaustion is not None
    )
    source = (
        header(Path(__file__), "content/stats/") + f"package {GENERATED_PACKAGE};\n"
        "\n"
        f"import {JAVA_PACKAGE}.Realmforge;\n"
        f"import {JAVA_PACKAGE}.stats.ExhaustionReduction;\n"
        "import java.util.List;\n"
        "import net.minecraft.core.registries.Registries;\n"
        "import net.minecraft.world.entity.ai.attributes.Attribute;\n"
        "import net.minecraft.world.entity.ai.attributes.RangedAttribute;\n"
        "import net.neoforged.bus.api.IEventBus;\n"
        "import net.neoforged.neoforge.common.PercentageAttribute;\n"
        "import net.neoforged.neoforge.registries.DeferredHolder;\n"
        "import net.neoforged.neoforge.registries.DeferredRegister;\n"
        "\n"
        "/**\n"
        " * Attributes generated from {@code content/stats/} (architecture 8.1), synced to clients. A share (a stat with\n"
        " * {@code max_percent}) is a {@code PercentageAttribute} holding a fraction.\n"
        " */\n"
        "public final class GeneratedStats {\n"
        "    public static final DeferredRegister<Attribute> ATTRIBUTES =\n"
        "            DeferredRegister.create(Registries.ATTRIBUTE, Realmforge.MOD_ID);\n"
        "\n" + fields + "    /** The primary stats, in id order: the ones stat rules read. */\n"
        f"    public static final List<DeferredHolder<Attribute, Attribute>> PRIMARY = List.of({primary});\n"
        "\n"
        "    /** Every stat above, in id order. */\n"
        f"    public static final List<DeferredHolder<Attribute, Attribute>> ALL = List.of({every});\n"
        "\n"
        "    /** The stats that lower the food exhaustion players gain ({@code lowers_exhaustion}), in id order. */\n"
        f"    public static final List<ExhaustionReduction> LOWER_EXHAUSTION = List.of({exhaustion});\n"
        "\n"
        "    private GeneratedStats() {\n"
        "    }\n"
        "\n"
        "    /** Registers the attributes. Call once from the mod constructor. */\n"
        "    public static void register(IEventBus modEventBus) {\n"
        "        ATTRIBUTES.register(modEventBus);\n"
        "    }\n"
        "}\n"
    )
    return source.encode("utf-8")


def _stat_rows(content: Content) -> str:
    rows = [
        "            new StatExpectation(\n"
        f"                    {string_literal(f'{MOD_ID}:{stat.id}')},\n"
        f"                    {string_literal(stat_description_id(stat.id))},\n"
        f"                    {'true' if stat.kind == 'primary' else 'false'},\n"
        f"                    {'true' if stat.percent else 'false'},\n"
        f"                    {_double(stat.game_default)},\n"
        f"                    {_double(stat.game_max)},\n"
        f"                    {_double(_exhaustion_per_point(stat))},\n"
        f"                    {_double(_exhaustion_max(stat))})"
        for stat in (entry.model for entry in content.stats)
    ]
    return "List.of(\n" + ",\n".join(rows) + ")" if rows else "List.of()"


def _exhaustion_per_point(stat: StatDef) -> float:
    return 0.0 if stat.lowers_exhaustion is None else stat.lowers_exhaustion.per_point_percent / 100


def _exhaustion_max(stat: StatDef) -> float:
    return 0.0 if stat.lowers_exhaustion is None else stat.lowers_exhaustion.max_percent / 100


def sample_roles(rule: StatRuleDef) -> list[str | None]:
    """The class roles at which the GameTest compares a rule's bonuses: no role (a player without a character), and
    every role when some of its modifiers apply to some roles only."""
    return [None, *ROLES] if rule.role_bound else [None]


def _rule_rows(content: Content) -> str:
    rows = []
    stat_max = {f"{MOD_ID}:{entry.model.id}": entry.model.game_max for entry in content.stats}
    for rule in (entry.model for entry in content.stat_rules):
        samples = []
        for role in sample_roles(rule):
            for value in sample_values(rule, stat_max.get(rule.stat)):
                bonuses = ", ".join(
                    f"new Bonus({string_literal(id_)}, {string_literal(attribute)}, {string_literal(operation)}, "
                    f"{_double(amount)})"
                    for id_, attribute, operation, amount in rule.bonuses_at(value, role)
                )
                shown_role = "null" if role is None else string_literal(role)
                samples.append(
                    f"                            new Sample({_double(value)}, {shown_role}, List.of({bonuses}))"
                )
        rows.append(
            "            new StatRuleExpectation(\n"
            f"                    {string_literal(f'{MOD_ID}:{rule.id}')},\n"
            f"                    {string_literal(rule.stat)},\n"
            "                    List.of(\n" + ",\n".join(samples) + "))"
        )
    return "List.of(\n" + ",\n".join(rows) + ")" if rows else "List.of()"


def _expectations(content: Content) -> bytes:
    source = (
        header(Path(__file__), SOURCES) + f"package {QA_GENERATED_PACKAGE};\n"
        "\n"
        "import java.util.List;\n"
        "\n"
        "/**\n"
        " * What {@code content/stats/} and {@code content/stat_rules/} say, so GameTests can check that the game agrees.\n"
        " * Ids are full resource locations, e.g. {@code realmforge:strength}; numbers are in game units (fractions).\n"
        " */\n"
        "public final class GeneratedStatExpectations {\n"
        "    /**\n"
        "     * Expected state of one stat's attribute; {@code exhaustionPerPoint} and {@code exhaustionMax} are how it\n"
        "     * lowers food exhaustion, both 0 for a stat that does not.\n"
        "     */\n"
        "    public record StatExpectation(String id, String descriptionId, boolean primary, boolean percent, "
        "double defaultValue,\n"
        "            double maxValue, double exhaustionPerPoint, double exhaustionMax) {\n"
        "    }\n"
        "\n"
        "    /** One attribute modifier a stat rule gives. */\n"
        "    public record Bonus(String id, String attribute, String operation, double amount) {\n"
        "    }\n"
        "\n"
        "    /**\n"
        "     * The bonuses the pipeline computes for a stat value and a class role (null: a player without a character),\n"
        "     * which the game must match, in this order.\n"
        "     */\n"
        "    public record Sample(double value, String role, List<Bonus> bonuses) {\n"
        "    }\n"
        "\n"
        "    /** Expected behaviour of one stat rule. */\n"
        "    public record StatRuleExpectation(String id, String stat, List<Sample> samples) {\n"
        "    }\n"
        "\n"
        f"    public static final List<StatExpectation> STATS = {_stat_rows(content)};\n"
        "\n"
        f"    public static final List<StatRuleExpectation> STAT_RULES = {_rule_rows(content)};\n"
        "\n"
        "    private GeneratedStatExpectations() {\n"
        "    }\n"
        "}\n"
    )
    return source.encode("utf-8")
