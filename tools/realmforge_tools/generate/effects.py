"""Status effects: the ``MobEffect`` registrations, icons (drawn ones from effect_icons.py, placeholders otherwise) and
the QA mod's expectations (the names: the lang files, lang.py)."""

from __future__ import annotations

import math
from pathlib import Path

from realmforge_tools.content import Content
from realmforge_tools.generate.datapack import seconds_to_ticks
from realmforge_tools.generate.effect_icons import drawn_icon
from realmforge_tools.generate.javagen import constant_name, header, java_file_path, string_literal
from realmforge_tools.generate.textures import placeholder_effect_icon
from realmforge_tools.project import JAVA_PACKAGE, MOD_ID, QA_JAVA_PACKAGE, RepoPaths
from realmforge_tools.schema.common import LocalizedText
from realmforge_tools.schema.effect import EffectDef, effect_description_id, modifier_id

GENERATED_PACKAGE = f"{JAVA_PACKAGE}.generated"
QA_GENERATED_PACKAGE = f"{QA_JAVA_PACKAGE}.generated"
SOURCES = "content/effects/"

# YAML category -> vanilla's MobEffectCategory constant.
_CATEGORIES = {"beneficial": "BENEFICIAL", "harmful": "HARMFUL", "neutral": "NEUTRAL"}
# Vanilla's AttributeModifier.Operation constants, by serialized name.
_OPERATIONS = {
    "add_value": "ADD_VALUE",
    "add_multiplied_base": "ADD_MULTIPLIED_BASE",
    "add_multiplied_total": "ADD_MULTIPLIED_TOTAL",
}


def generate(content: Content, paths: RepoPaths) -> dict[Path, bytes]:
    icons = paths.mod_generated / "resources" / "assets" / MOD_ID / "textures" / "mob_effect"
    files = {
        java_file_path(paths.mod_generated / "java", GENERATED_PACKAGE, "GeneratedEffects"): _registrations(
            content
        ),
        java_file_path(paths.qa_generated / "java", QA_GENERATED_PACKAGE, "GeneratedEffectExpectations"): (
            _expectations(content)
        ),
    }
    for entry in content.effects:
        effect = entry.model
        drawn = drawn_icon(effect.id)
        files[icons / f"{effect.id}.png"] = (
            drawn if drawn is not None else placeholder_effect_icon(_rgb(effect.color), effect.category)
        )
    return files


def translations(content: Content) -> dict[str, LocalizedText]:
    """Every effect's name, by its translation key ``effect.realmforge.<id>``."""
    return {effect_description_id(entry.model.id): entry.model.name for entry in content.effects}


def _rgb(color: str) -> int:
    return int(color.removeprefix("#"), 16)


def _double(value: float) -> str:
    """A Java double literal; repr() gives the shortest text that reads back as the same double."""
    if not math.isfinite(value):
        raise ValueError(f"{value} has no Java double literal; validation should have refused the content")
    return repr(float(value))


def _hex(color: str) -> str:
    return "0x" + color.removeprefix("#").upper()


def _comment(effect: EffectDef) -> str:
    english = effect.name.en_us.replace("*/", "*&#47;").replace("@", "&#64;")
    return f'{effect.category}, "{english}"'


def _periodic(effect: EffectDef) -> str:
    periodic = effect.periodic
    if periodic is None:
        return "Optional.empty()"
    return (
        f"Optional.of(Periodic.{periodic.type}({string_literal(periodic.school)}, {_double(periodic.amount)}, "
        f"{seconds_to_ticks(periodic.interval_seconds)}))"
    )


def _registration(effect: EffectDef) -> str:
    modifiers = "".join(
        f"\n                    .modifier({string_literal(modifier.attribute)}, "
        f"{string_literal(modifier_id(effect.id, modifier.attribute))}, {_double(modifier.game_amount)}, "
        f"AttributeModifier.Operation.{_OPERATIONS[modifier.operation]}"
        + (f", {_double(modifier.game_per_amplifier)})" if modifier.game_per_amplifier is not None else ")")
        for modifier in effect.modifiers
    )
    stun = "\n                    .stuns()" if effect.stun else ""
    return (
        f"() -> new StatusEffect(MobEffectCategory.{_CATEGORIES[effect.category]}, {_hex(effect.color)},\n"
        f"                    {_periodic(effect)}){stun}{modifiers}"
    )


def _registrations(content: Content) -> bytes:
    fields = "".join(
        f"    /** {{@code {MOD_ID}:{entry.model.id}}} from {{@code {entry.source}}}: {_comment(entry.model)}. */\n"
        f"    public static final DeferredHolder<MobEffect, MobEffect> {constant_name(entry.model.id)} =\n"
        f"            EFFECTS.register({string_literal(entry.model.id)}, {_registration(entry.model)});\n"
        "\n"
        for entry in content.effects
    )
    every = ", ".join(constant_name(entry.model.id) for entry in content.effects)
    source = (
        header(Path(__file__), SOURCES) + f"package {GENERATED_PACKAGE};\n"
        "\n"
        f"import {JAVA_PACKAGE}.Realmforge;\n"
        f"import {JAVA_PACKAGE}.effect.Periodic;\n"
        f"import {JAVA_PACKAGE}.effect.StatusEffect;\n"
        "import java.util.List;\n"
        "import java.util.Optional;\n"
        "import net.minecraft.core.registries.Registries;\n"
        "import net.minecraft.world.effect.MobEffect;\n"
        "import net.minecraft.world.effect.MobEffectCategory;\n"
        "import net.minecraft.world.entity.ai.attributes.AttributeModifier;\n"
        "import net.neoforged.bus.api.IEventBus;\n"
        "import net.neoforged.neoforge.registries.DeferredHolder;\n"
        "import net.neoforged.neoforge.registries.DeferredRegister;\n"
        "\n"
        "/**\n"
        " * Status effects generated from {@code content/effects/} (architecture 11.4): attribute modifiers, damage or\n"
        " * healing over time ({@link Periodic}) and stuns. NeoForge registers attributes before status effects, so a\n"
        " * modifier finds its attribute, ours included, when its effect is built.\n"
        " */\n"
        "public final class GeneratedEffects {\n"
        "    public static final DeferredRegister<MobEffect> EFFECTS =\n"
        "            DeferredRegister.create(Registries.MOB_EFFECT, Realmforge.MOD_ID);\n"
        "\n" + fields + "    /** Every effect above, in id order. */\n"
        f"    public static final List<DeferredHolder<MobEffect, MobEffect>> ALL = List.of({every});\n"
        "\n"
        "    private GeneratedEffects() {\n"
        "    }\n"
        "\n"
        "    /** Registers the effects. Call once from the mod constructor. */\n"
        "    public static void register(IEventBus modEventBus) {\n"
        "        EFFECTS.register(modEventBus);\n"
        "    }\n"
        "}\n"
    )
    return source.encode("utf-8")


def _expectation(effect: EffectDef) -> str:
    modifiers = ", ".join(
        f"new ModifierExpectation({string_literal(modifier.attribute)}, "
        f"{string_literal(modifier_id(effect.id, modifier.attribute))}, {_double(modifier.game_amount)}, "
        f"{string_literal(modifier.operation)}, {_double(modifier.game_amount_at(1))})"
        for modifier in effect.modifiers
    )
    periodic = effect.periodic
    periodic_expectation = (
        f"Optional.of(new PeriodicExpectation({string_literal(periodic.type)}, {string_literal(periodic.school)}, "
        f"{_double(periodic.amount)}, {seconds_to_ticks(periodic.interval_seconds)}))"
        if periodic is not None
        else "Optional.empty()"
    )
    return (
        "            new EffectExpectation(\n"
        f"                    {string_literal(f'{MOD_ID}:{effect.id}')},\n"
        f"                    {string_literal(effect_description_id(effect.id))},\n"
        f"                    {string_literal(effect.category)},\n"
        f"                    {_hex(effect.color)},\n"
        f"                    List.of({modifiers}),\n"
        f"                    {periodic_expectation},\n"
        f"                    {'true' if effect.stun else 'false'})"
    )


def _expectations(content: Content) -> bytes:
    rows = [_expectation(entry.model) for entry in content.effects]
    effects = "List.of(\n" + ",\n".join(rows) + ")" if rows else "List.of()"
    source = (
        header(Path(__file__), SOURCES) + f"package {QA_GENERATED_PACKAGE};\n"
        "\n"
        "import java.util.List;\n"
        "import java.util.Optional;\n"
        "\n"
        "/**\n"
        " * What {@code content/effects/} says about every status effect, so GameTests can check that the game agrees.\n"
        " * Ids are full resource locations, e.g. {@code realmforge:test_burning}; numbers are in game units (ticks,\n"
        " * fractions).\n"
        " */\n"
        "public final class GeneratedEffectExpectations {\n"
        "    /**\n"
        "     * An attribute modifier at the first level (amplifier 0), and its amount at the second (amplifier 1): vanilla\n"
        "     * multiplies it by the amplifier + 1, a per-amplifier growth adds its step instead.\n"
        "     */\n"
        "    public record ModifierExpectation(String attribute, String id, double amount, String operation,\n"
        "            double secondLevelAmount) {\n"
        "    }\n"
        "\n"
        "    /** Damage or healing over time: one tick's amount at the first level, every {@code intervalTicks}. */\n"
        "    public record PeriodicExpectation(String type, String school, double amount, int intervalTicks) {\n"
        "    }\n"
        "\n"
        "    /**\n"
        "     * Expected state of one effect: its category, particle colour (RGB), modifiers, periodic ticks and whether\n"
        "     * it stuns.\n"
        "     */\n"
        "    public record EffectExpectation(String id, String descriptionId, String category, int color,\n"
        "            List<ModifierExpectation> modifiers, Optional<PeriodicExpectation> periodic, boolean stun) {\n"
        "    }\n"
        "\n"
        f"    public static final List<EffectExpectation> EFFECTS = {effects};\n"
        "\n"
        "    private GeneratedEffectExpectations() {\n"
        "    }\n"
        "}\n"
    )
    return source.encode("utf-8")
