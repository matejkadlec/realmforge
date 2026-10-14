"""Races and classes: the QA mod's expectations (the data itself and the lang entries: datapack.py)."""

from __future__ import annotations

from pathlib import Path
from typing import Literal

from realmforge_tools.content import Content
from realmforge_tools.generate.datapack import translation_key
from realmforge_tools.generate.javagen import header, java_file_path, string_literal
from realmforge_tools.project import MOD_ID, QA_JAVA_PACKAGE, RepoPaths
from realmforge_tools.schema.character import SAMPLE_LEVELS, CharacterModifierDef, bonuses_at

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


def _samples(registry: Literal["race", "class"], entry_id: str, modifiers: list[CharacterModifierDef]) -> str:
    samples = []
    for level in SAMPLE_LEVELS:
        bonuses = ", ".join(
            f"new Bonus({string_literal(id_)}, {string_literal(attribute)}, {string_literal(operation)}, "
            f"{_double(amount)})"
            for id_, attribute, operation, amount in bonuses_at(registry, entry_id, modifiers, level)
        )
        samples.append(f"                            new Sample({level}, List.of({bonuses}))")
    return "List.of(\n" + ",\n".join(samples) + ")"


def _list(rows: list[str]) -> str:
    return "List.of(\n" + ",\n".join(rows) + ")" if rows else "List.of()"


_BONUS_RECORDS = (
    "    /** One attribute modifier the entry gives; numbers are in game units (fractions for percentages). */\n"
    "    public record Bonus(String id, String attribute, String operation, double amount) {\n"
    "    }\n"
    "\n"
    "    /** The bonuses the pipeline computes at a level, which the game must match, in this order. */\n"
    "    public record Sample(int level, List<Bonus> bonuses) {\n"
    "    }\n"
    "\n"
)


def _races(content: Content) -> bytes:
    rows = []
    for race in (entry.model for entry in content.races):
        rows.append(
            "            new RaceExpectation(\n"
            f"                    {string_literal(f'{MOD_ID}:{race.id}')},\n"
            f"                    {string_literal(translation_key('race', race.id, 'name'))},\n"
            f"                    {string_literal(translation_key('race', race.id, 'description'))},\n"
            f"                    {_strings(list(race.classes))},\n"
            f"                    {_samples('race', race.id, race.modifiers)})"
        )
    source = (
        header(Path(__file__), "content/races/") + f"package {QA_GENERATED_PACKAGE};\n"
        "\n"
        "import java.util.List;\n"
        "\n"
        "/** What {@code content/races/} says about every race, so GameTests can check that the game agrees. */\n"
        "public final class GeneratedRaceExpectations {\n" + _BONUS_RECORDS + "    /**\n"
        "     * Expected state of one race. Ids are full resource locations, e.g. {@code realmforge:human}; the classes\n"
        "     * are the ones it may take, in the YAML's order.\n"
        "     */\n"
        "    public record RaceExpectation(String id, String nameKey, String descriptionKey, List<String> classes,\n"
        "            List<Sample> samples) {\n"
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
        rows.append(
            "            new ClassExpectation(\n"
            f"                    {string_literal(f'{MOD_ID}:{player_class.id}')},\n"
            f"                    {string_literal(translation_key('class', player_class.id, 'name'))},\n"
            f"                    {string_literal(translation_key('class', player_class.id, 'description'))},\n"
            f"                    {string_literal(player_class.resource)},\n"
            f"                    List.of({kit}),\n"
            f"                    {_samples('class', player_class.id, player_class.modifiers)})"
        )
    source = (
        header(Path(__file__), "content/classes/") + f"package {QA_GENERATED_PACKAGE};\n"
        "\n"
        "import java.util.List;\n"
        "\n"
        "/** What {@code content/classes/} says about every class, so GameTests can check that the game agrees. */\n"
        "public final class GeneratedClassExpectations {\n"
        + _BONUS_RECORDS
        + "    /** One item of the starting kit. */\n"
        "    public record KitItem(String item, int count) {\n"
        "    }\n"
        "\n"
        "    /** Expected state of one class. Ids are full resource locations, e.g. {@code realmforge:warrior}. */\n"
        "    public record ClassExpectation(String id, String nameKey, String descriptionKey, String resource,\n"
        "            List<KitItem> startingKit, List<Sample> samples) {\n"
        "    }\n"
        "\n"
        f"    public static final List<ClassExpectation> CLASSES = {_list(rows)};\n"
        "\n"
        "    private GeneratedClassExpectations() {\n"
        "    }\n"
        "}\n"
    )
    return source.encode("utf-8")
