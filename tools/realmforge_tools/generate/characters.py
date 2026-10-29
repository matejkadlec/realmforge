"""Races and classes: the QA mod's expectations (the data itself and the lang entries: datapack.py)."""

from __future__ import annotations

from pathlib import Path
from typing import Literal

from realmforge_tools.content import Content
from realmforge_tools.generate.datapack import seconds_to_ticks, translation_key
from realmforge_tools.generate.javagen import header, java_file_path, string_literal
from realmforge_tools.project import MOD_ID, QA_JAVA_PACKAGE, RepoPaths
from realmforge_tools.schema.character import (
    CharacterModifierDef,
    RaceDef,
    RageDef,
    bonuses,
    passive_bonuses,
)

QA_GENERATED_PACKAGE = f"{QA_JAVA_PACKAGE}.generated"


def generate(content: Content, paths: RepoPaths) -> dict[Path, bytes]:
    qa_java = paths.qa_generated / "java"
    return {
        java_file_path(qa_java, QA_GENERATED_PACKAGE, "GeneratedRaceExpectations"): _races(content),
        java_file_path(qa_java, QA_GENERATED_PACKAGE, "GeneratedClassExpectations"): _classes(content),
    }


def _double(value: float) -> str:
    """A Java double literal; repr() gives the shortest text that reads back as the same double."""
    return repr(float(value))


def _strings(values: list[str]) -> str:
    return f"List.of({', '.join(string_literal(value) for value in values)})"


def _bonuses(registry: Literal["race", "class"], entry_id: str, modifiers: list[CharacterModifierDef]) -> str:
    rows = ", ".join(
        f"new Bonus({string_literal(id_)}, {string_literal(attribute)}, {string_literal(operation)}, "
        f"{_double(amount)})"
        for id_, attribute, operation, amount in bonuses(registry, entry_id, modifiers)
    )
    return f"List.of({rows})"


def _rage(rage: RageDef | None) -> str:
    if rage is None:
        return "Optional.empty()"
    return (
        f"Optional.of(new Rage({_double(rage.per_hit_dealt)}, {_double(rage.per_max_health_taken)}, "
        f"{_double(rage.decay_per_second)}, {seconds_to_ticks(rage.decay_after_seconds)}))"
    )


def _list(rows: list[str]) -> str:
    return "List.of(\n" + ",\n".join(rows) + ")" if rows else "List.of()"


_BONUS_RECORDS = (
    "    /**\n"
    "     * One attribute modifier the entry gives, the same at every level; numbers are in game units (fractions for\n"
    "     * percentages).\n"
    "     */\n"
    "    public record Bonus(String id, String attribute, String operation, double amount) {\n"
    "    }\n"
    "\n"
)


def _passive(race: RaceDef) -> str:
    passive = race.racial_passive
    if passive is None:
        return "Optional.empty()"
    rows = ", ".join(
        f"new Bonus({string_literal(id_)}, {string_literal(attribute)}, {string_literal(operation)}, "
        f"{_double(amount)})"
        for id_, attribute, operation, amount in passive_bonuses(race.id, passive)
    )
    return (
        "Optional.of(new Passive(\n"
        f"                            {string_literal(translation_key('race', race.id, 'racial_passive.name'))},\n"
        f"                            {string_literal(translation_key('race', race.id, 'racial_passive.description'))},\n"
        f"                            {string_literal(passive.weapons)},\n"
        f"                            List.of({rows})))"
    )


def _races(content: Content) -> bytes:
    rows = []
    for race in (entry.model for entry in content.races):
        racial_spell = (
            f"Optional.of({string_literal(race.racial_spell)})"
            if race.racial_spell is not None
            else "Optional.empty()"
        )
        rows.append(
            "            new RaceExpectation(\n"
            f"                    {string_literal(f'{MOD_ID}:{race.id}')},\n"
            f"                    {string_literal(translation_key('race', race.id, 'name'))},\n"
            f"                    {string_literal(translation_key('race', race.id, 'description'))},\n"
            f"                    {_strings(list(race.classes))},\n"
            f"                    {_bonuses('race', race.id, race.modifiers)},\n"
            f"                    {racial_spell},\n"
            f"                    {_passive(race)})"
        )
    source = (
        header(Path(__file__), "content/races/") + f"package {QA_GENERATED_PACKAGE};\n"
        "\n"
        "import java.util.List;\n"
        "import java.util.Optional;\n"
        "\n"
        "/** What {@code content/races/} says about every race, so GameTests can check that the game agrees. */\n"
        "public final class GeneratedRaceExpectations {\n" + _BONUS_RECORDS + "    /**\n"
        "     * A racial passive: its name and description keys, the item tag the main hand needs, and the bonuses it\n"
        "     * gives meanwhile.\n"
        "     */\n"
        "    public record Passive(String nameKey, String descriptionKey, String weapons, List<Bonus> bonuses) {\n"
        "    }\n"
        "\n"
        "    /**\n"
        "     * Expected state of one race. Ids are full resource locations, e.g. {@code realmforge:human}; the classes\n"
        "     * are the ones it may take, in the YAML's order; the bonuses are the ones the game must give, in this order;\n"
        "     * the racial spell fills the slot beside the spell bar.\n"
        "     */\n"
        "    public record RaceExpectation(String id, String nameKey, String descriptionKey, List<String> classes,\n"
        "            List<Bonus> bonuses, Optional<String> racialSpell, Optional<Passive> racialPassive) {\n"
        "    }\n"
        "\n"
        f"    public static final List<RaceExpectation> RACES = {_list(rows)};\n"
        "\n"
        "    private GeneratedRaceExpectations() {\n"
        "    }\n"
        "}\n"
    )
    return source.encode("utf-8")


def _classes(content: Content) -> bytes:
    rows = []
    for player_class in (entry.model for entry in content.classes):
        kit = ", ".join(
            f"new KitItem({string_literal(item.item)}, {item.count})" for item in player_class.starting_kit
        )
        spells = ", ".join(
            f"new ClassSpell({string_literal(taught.spell)}, {taught.level})"
            for taught in player_class.spells
        )
        rows.append(
            "            new ClassExpectation(\n"
            f"                    {string_literal(f'{MOD_ID}:{player_class.id}')},\n"
            f"                    {string_literal(translation_key('class', player_class.id, 'name'))},\n"
            f"                    {string_literal(translation_key('class', player_class.id, 'description'))},\n"
            f"                    {string_literal(player_class.resource)},\n"
            f"                    {_rage(player_class.rage)},\n"
            f"                    List.of({kit}),\n"
            f"                    {_bonuses('class', player_class.id, player_class.modifiers)},\n"
            f"                    List.of({spells}))"
        )
    source = (
        header(Path(__file__), "content/classes/") + f"package {QA_GENERATED_PACKAGE};\n"
        "\n"
        "import java.util.List;\n"
        "import java.util.Optional;\n"
        "\n"
        "/** What {@code content/classes/} says about every class, so GameTests can check that the game agrees. */\n"
        "public final class GeneratedClassExpectations {\n"
        + _BONUS_RECORDS
        + "    /** How a rage class gains and loses rage, in game units (the decay's delay in ticks). */\n"
        "    public record Rage(double perHitDealt, double perMaxHealthTaken, double decayPerSecond,\n"
        "            int decayAfterTicks) {\n"
        "    }\n"
        "\n"
        "    /** One item of the starting kit. */\n"
        "    public record KitItem(String item, int count) {\n"
        "    }\n"
        "\n"
        "    /** A spell the class teaches at a level. */\n"
        "    public record ClassSpell(String spell, int level) {\n"
        "    }\n"
        "\n"
        "    /**\n"
        "     * Expected state of one class. Ids are full resource locations, e.g. {@code realmforge:warrior}; the bonuses\n"
        "     * are the ones the game must give, in this order.\n"
        "     */\n"
        "    public record ClassExpectation(String id, String nameKey, String descriptionKey, String resource,\n"
        "            Optional<Rage> rage, List<KitItem> startingKit, List<Bonus> bonuses, List<ClassSpell> spells) {\n"
        "    }\n"
        "\n"
        f"    public static final List<ClassExpectation> CLASSES = {_list(rows)};\n"
        "\n"
        "    private GeneratedClassExpectations() {\n"
        "    }\n"
        "}\n"
    )
    return source.encode("utf-8")
