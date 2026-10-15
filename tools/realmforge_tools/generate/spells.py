"""Spells: ``ResourceKey`` constants for Java and the QA mod's expectations (the data itself: datapack.py, which turns
``cooldown_seconds`` into ``cooldown_ticks`` and every ``<name>_percent`` into a fraction named ``<name>``)."""

from __future__ import annotations

from pathlib import Path

from realmforge_tools.content import Content
from realmforge_tools.generate.datapack import seconds_to_ticks
from realmforge_tools.generate.javagen import constant_name, header, java_file_path, string_literal
from realmforge_tools.project import JAVA_PACKAGE, MOD_ID, QA_JAVA_PACKAGE, RepoPaths
from realmforge_tools.schema.spell import SpellDef

GENERATED_PACKAGE = f"{JAVA_PACKAGE}.generated"
QA_GENERATED_PACKAGE = f"{QA_JAVA_PACKAGE}.generated"
SOURCES = "content/spells/"


def generate(content: Content, paths: RepoPaths) -> dict[Path, bytes]:
    return {
        java_file_path(paths.mod_generated / "java", GENERATED_PACKAGE, "GeneratedSpells"): _keys(content),
        java_file_path(paths.qa_generated / "java", QA_GENERATED_PACKAGE, "GeneratedSpellExpectations"): (
            _expectations(content)
        ),
    }


def _double(value: float) -> str:
    """A Java double literal; repr() gives the shortest text that reads back as the same double."""
    return repr(float(value))


def _comment(spell: SpellDef) -> str:
    effects = ", ".join(effect.type.removeprefix(f"{MOD_ID}:") for effect in spell.effects)
    school = spell.school.removeprefix(f"{MOD_ID}:")
    return f"{school}, {spell.targeting.type.removeprefix(f'{MOD_ID}:')}, {effects}"


def _keys(content: Content) -> bytes:
    constants = [constant_name(entry.model.id) for entry in content.spells]
    fields = "".join(
        f"    /** {{@code {MOD_ID}:{entry.model.id}}} from {{@code {entry.source}}}: {_comment(entry.model)}. */\n"
        f"    public static final ResourceKey<Spell> {constant} = key({string_literal(entry.model.id)});\n"
        "\n"
        for entry, constant in zip(content.spells, constants, strict=True)
    )
    source = (
        header(Path(__file__), SOURCES) + f"package {GENERATED_PACKAGE};\n"
        "\n"
        f"import {JAVA_PACKAGE}.Realmforge;\n"
        f"import {JAVA_PACKAGE}.spell.Spell;\n"
        f"import {JAVA_PACKAGE}.spell.SpellRegistries;\n"
        "import java.util.List;\n"
        "import net.minecraft.resources.ResourceKey;\n"
        "\n"
        "/** Keys of the spells in {@code content/spells/}, so Java names a spell without typing its id. */\n"
        "public final class GeneratedSpells {\n" + fields + "    /** Every key above, in id order. */\n"
        f"    public static final List<ResourceKey<Spell>> ALL = List.of({', '.join(constants)});\n"
        "\n"
        "    private GeneratedSpells() {\n"
        "    }\n"
        "\n"
        "    private static ResourceKey<Spell> key(String id) {\n"
        "        return ResourceKey.create(SpellRegistries.SPELL, Realmforge.id(id));\n"
        "    }\n"
        "}\n"
    )
    return source.encode("utf-8")


def _optional_string(value: str | None) -> str:
    return f"Optional.of({string_literal(value)})" if value is not None else "Optional.empty()"


def _optional_double(value: float | None) -> str:
    return f"Optional.of({_double(value)})" if value is not None else "Optional.empty()"


def _expectation(spell: SpellDef) -> str:
    effects = ",\n".join(
        "                            new EffectExpectation("
        f"{string_literal(effect.type)}, {_double(effect.amount)}, {_double(effect.per_level)}, "
        f"{_double((effect.attack_damage_percent or 0) / 100)})"
        for effect in spell.effects
    )
    cost = spell.cost
    crit = spell.crit
    multiplier = crit.multiplier_percent / 100 if crit.multiplier_percent is not None else None
    return (
        "            new SpellExpectation(\n"
        f"                    {string_literal(f'{MOD_ID}:{spell.id}')},\n"
        f"                    {string_literal(spell.school)},\n"
        f"                    {string_literal(spell.scales_with)},\n"
        f"                    {string_literal(spell.targeting.type)},\n"
        f"                    {_double(getattr(spell.targeting, 'range', 0.0))},\n"
        f"                    {string_literal(spell.cast.type)},\n"
        f"                    {_optional_string(cost.resource if cost is not None else None)},\n"
        f"                    {_double(cost.amount if cost is not None else 0.0)},\n"
        f"                    {_double(cost.per_level if cost is not None else 0.0)},\n"
        f"                    {seconds_to_ticks(spell.cooldown_seconds)},\n"
        f"                    {seconds_to_ticks(spell.global_cooldown_seconds)},\n"
        f"                    {spell.max_level},\n"
        f"                    {'true' if crit.can_crit else 'false'},\n"
        f"                    {_optional_double(multiplier)},\n"
        f"                    {_double(crit.bonus_chance_percent / 100)},\n"
        f"                    {'true' if spell.npc_only else 'false'},\n"
        "                    List.of(\n"
        f"{effects}))"
    )


def _expectations(content: Content) -> bytes:
    rows = [_expectation(entry.model) for entry in content.spells]
    spells = "List.of(\n" + ",\n".join(rows) + ")" if rows else "List.of()"
    source = (
        header(Path(__file__), SOURCES) + f"package {QA_GENERATED_PACKAGE};\n"
        "\n"
        "import java.util.List;\n"
        "import java.util.Optional;\n"
        "\n"
        "/** What {@code content/spells/} says about every spell, so GameTests can check that the game agrees. */\n"
        "public final class GeneratedSpellExpectations {\n"
        "    /**\n"
        "     * One effect as written in the YAML, in game units: {@code attackDamage} is the share of the caster's attack\n"
        "     * damage it adds (0 when it adds none).\n"
        "     */\n"
        "    public record EffectExpectation(String type, double amount, double perLevel, double attackDamage) {\n"
        "    }\n"
        "\n"
        "    /**\n"
        "     * Expected state of one spell, in game units (ticks, fractions). Ids are full resource locations, e.g.\n"
        "     * {@code realmforge:test_bolt}; {@code range} is 0 for a spell on the caster itself, and the cost's amounts\n"
        "     * are 0 for a free spell.\n"
        "     */\n"
        "    public record SpellExpectation(String id, String school, String scalesWith, String targeting, double range,\n"
        "            String cast, Optional<String> costResource, double costAmount, double costPerLevel, int cooldownTicks,\n"
        "            int globalCooldownTicks, int maxLevel, boolean canCrit, Optional<Double> critMultiplier,\n"
        "            double critBonusChance, boolean npcOnly, List<EffectExpectation> effects) {\n"
        "    }\n"
        "\n"
        f"    public static final List<SpellExpectation> SPELLS = {spells};\n"
        "\n"
        "    private GeneratedSpellExpectations() {\n"
        "    }\n"
        "}\n"
    )
    return source.encode("utf-8")
