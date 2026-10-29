"""Bosses: the QA mod's expectations (the boss entries and their lang entries: datapack.py; the bodies: mobs.py).
Architecture 14.3.

- ``GeneratedBossExpectations``: every boss, its body, level, arena, bar colour, phase change and phases, so GameTests
  can check that the game decodes the same.
"""

from __future__ import annotations

from pathlib import Path

from realmforge_tools.content import Content
from realmforge_tools.generate.datapack import percent_to_fraction, seconds_to_ticks, translation_key
from realmforge_tools.generate.javagen import header, java_file_path, string_literal
from realmforge_tools.project import QA_JAVA_PACKAGE, RepoPaths
from realmforge_tools.schema.boss import (
    HEALTH_BELOW,
    TIME_IN_PHASE,
    BossDef,
    BossPhaseDef,
    HealthBelowDef,
    boss_id,
)

QA_GENERATED_PACKAGE = f"{QA_JAVA_PACKAGE}.generated"
REGISTRY = "boss"
SOURCES = "content/bosses/"


def generate(content: Content, paths: RepoPaths) -> dict[Path, bytes]:
    qa_java = paths.qa_generated / "java"
    return {
        java_file_path(qa_java, QA_GENERATED_PACKAGE, "GeneratedBossExpectations"): _expectations(content)
    }


def _double(value: float) -> str:
    return repr(float(value))


def _strings(values: list[str]) -> str:
    return "List.of(" + ", ".join(string_literal(value) for value in values) + ")"


def _trigger(phase: BossPhaseDef) -> tuple[str, float]:
    """The phase's trigger type and its value in game units (a fraction of health, or ticks); none for the first."""
    trigger = phase.enter_when
    if trigger is None:
        return "", 0.0
    if isinstance(trigger, HealthBelowDef):
        return HEALTH_BELOW, percent_to_fraction(trigger.health_percent)
    return TIME_IN_PHASE, float(seconds_to_ticks(trigger.after_seconds))


def _phase(boss: BossDef, index: int, phase: BossPhaseDef) -> str:
    trigger, value = _trigger(phase)
    abilities = ", ".join(
        f"new AbilityExpectation({string_literal(ability.spell)}, {seconds_to_ticks(ability.cooldown_seconds)})"
        for ability in phase.abilities
    )
    return (
        "                            new PhaseExpectation(\n"
        f"                                    {string_literal(phase.id)},\n"
        f"                                    {string_literal(translation_key(REGISTRY, boss.id, f'phases.{index}.name'))},\n"
        f"                                    {string_literal(trigger)}, {_double(value)},\n"
        f"                                    {_strings(phase.on_enter)},\n"
        f"                                    List.of({abilities}))"
    )


def _boss(boss: BossDef) -> str:
    phases = ",\n".join(_phase(boss, index, phase) for index, phase in enumerate(boss.phases))
    return (
        "            new BossExpectation(\n"
        f"                    {string_literal(boss_id(boss.id))},\n"
        f"                    {string_literal(boss.entity)},\n"
        f"                    {boss.level}, {_double(boss.arena.radius)}, {string_literal(boss.bar_color)}, "
        f"{seconds_to_ticks(boss.phase_change_seconds)},\n"
        f"                    List.of(\n{phases}))"
    )


def _expectations(content: Content) -> bytes:
    rows = [_boss(entry.model) for entry in content.bosses]
    bosses = "List.of(\n" + ",\n".join(rows) + ")" if rows else "List.of()"
    source = (
        header(Path(__file__), SOURCES) + f"package {QA_GENERATED_PACKAGE};\n"
        "\n"
        "import java.util.List;\n"
        "\n"
        "/** What {@code content/bosses/} says about every boss, so GameTests can check that the game agrees. */\n"
        "public final class GeneratedBossExpectations {\n"
        "    /** One ability: its spell's full id and its own cooldown in ticks (0: the spell's alone). */\n"
        "    public record AbilityExpectation(String spell, int cooldownTicks) {\n"
        "    }\n"
        "\n"
        "    /**\n"
        "     * One phase: its id, its name's translation key, its trigger's type (empty for the first phase) and value\n"
        "     * (a fraction of health for {@code realmforge:health_below}, ticks for {@code realmforge:time_in_phase}),\n"
        "     * the spells it casts when it begins and its abilities.\n"
        "     */\n"
        "    public record PhaseExpectation(String id, String nameKey, String trigger, double triggerValue,\n"
        "            List<String> onEnter, List<AbilityExpectation> abilities) {\n"
        "    }\n"
        "\n"
        "    /**\n"
        "     * One boss: its full id, its body's entity type, its fixed level, its arena's radius in blocks, its boss\n"
        "     * bar's colour, how many ticks a phase change lasts, and its phases in order.\n"
        "     */\n"
        "    public record BossExpectation(String id, String entity, int level, double arenaRadius, String barColor,\n"
        "            int phaseChangeTicks, List<PhaseExpectation> phases) {\n"
        "    }\n"
        "\n"
        f"    public static final List<BossExpectation> BOSSES = {bosses};\n"
        "\n"
        "    private GeneratedBossExpectations() {\n"
        "    }\n"
        "}\n"
    )
    return source.encode("utf-8")
