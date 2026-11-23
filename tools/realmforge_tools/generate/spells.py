"""Spells: ``ResourceKey`` constants for Java, the QA mod's expectations and the spells' icons (the data itself:
datapack.py, which turns ``cooldown_seconds`` into ``cooldown_ticks`` and every ``<name>_percent`` into a fraction
named ``<name>``).

A spell's icon is its approved art (``art/textures/spell/<id>.png``, ``art/approved.py``), written as the GUI sprite
``realmforge:spell/<id>``; ``GeneratedSpellIcons`` lists the spells that have one, so the client draws the icon for
those and their initials for the rest (test spells, mobs' spells)."""

from __future__ import annotations

from pathlib import Path

from pydantic import BaseModel

from realmforge_tools.art import approved
from realmforge_tools.content import Content
from realmforge_tools.generate.datapack import game_field, seconds_to_ticks, translation_key
from realmforge_tools.generate.javagen import constant_name, header, java_file_path, string_literal
from realmforge_tools.project import JAVA_PACKAGE, MOD_ID, QA_JAVA_PACKAGE, RepoPaths
from realmforge_tools.schema.spell import DAMAGE, DAMAGE_OVER_TIME, HEAL, SpellDef

GENERATED_PACKAGE = f"{JAVA_PACKAGE}.generated"
QA_GENERATED_PACKAGE = f"{QA_JAVA_PACKAGE}.generated"
SOURCES = "content/spells/"


def generate(content: Content, paths: RepoPaths) -> dict[Path, bytes]:
    files = {
        java_file_path(paths.mod_generated / "java", GENERATED_PACKAGE, "GeneratedSpells"): _keys(content),
        java_file_path(paths.qa_generated / "java", QA_GENERATED_PACKAGE, "GeneratedSpellExpectations"): (
            _expectations(content)
        ),
    }
    icons = spell_icons(content, paths)
    files[java_file_path(paths.mod_generated / "java", GENERATED_PACKAGE, "GeneratedSpellIcons")] = (
        _icon_list(icons)
    )
    sprites = paths.mod_generated / "resources" / "assets" / MOD_ID / "textures" / "gui" / "sprites" / "spell"
    for spell_id, icon in icons.items():
        files[sprites / f"{spell_id}.png"] = icon
    return files


def spell_icons(content: Content, paths: RepoPaths) -> dict[str, bytes]:
    """Each spell that has approved art (``art/textures/spell/``), by id, with its icon as the game gets it."""
    icons: dict[str, bytes] = {}
    for entry in content.spells:
        icon = approved.texture(paths, approved.SPELL, entry.model.id)
        if icon is not None:
            icons[entry.model.id] = icon
    return icons


def _icon_list(icons: dict[str, bytes]) -> bytes:
    ids = ", ".join(string_literal(spell_id) for spell_id in sorted(icons))
    source = (
        header(Path(__file__), "art/textures/spell/") + f"package {GENERATED_PACKAGE};\n"
        "\n"
        "import java.util.Set;\n"
        "\n"
        "/**\n"
        " * The spells that have an icon, generated from the approved art in {@code art/textures/spell/}: the GUI sprite\n"
        " * {@code realmforge:spell/<path>} of each exists, so the client draws it instead of the spell's initials\n"
        " * ({@code client/hud/SpellIcons}).\n"
        " */\n"
        "public final class GeneratedSpellIcons {\n"
        "    /** The paths of the {@code realmforge} spells with an icon. */\n"
        f"    public static final Set<String> SPELLS = Set.of({ids});\n"
        "\n"
        "    private GeneratedSpellIcons() {\n"
        "    }\n"
        "}\n"
    )
    return source.encode("utf-8")


def _double(value: float) -> str:
    """A Java double literal; repr() gives the shortest text that reads back as the same double."""
    return repr(float(value))


def _comment(spell: SpellDef) -> str:
    school = spell.school.removeprefix(f"{MOD_ID}:") if spell.school is not None else "no school"
    if spell.is_passive:
        return f"{school}, passive"
    if spell.aura is not None:
        return f"{school}, aura ({spell.aura.group})"
    effects = ", ".join(effect.type.removeprefix(f"{MOD_ID}:") for effect in spell.effects)
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


def _fraction(percent: float | None) -> float | None:
    return percent / 100 if percent is not None else None


def _bool(value: bool) -> str:
    return "true" if value else "false"


def _effect(effect: BaseModel, indent: str) -> str:
    """One effect as an ``EffectExpectation``: its numbers and names by their game field names (``duration_ticks``,
    ``attack_damage`` as a fraction), and the effects it runs itself (a projectile's, an area's)."""
    numbers: dict[str, float] = {}
    names: dict[str, str] = {}
    inner: list[BaseModel] = []
    for name in type(effect).model_fields:
        value = getattr(effect, name)
        if name == "type":
            continue
        if name == "effects":
            inner = value
        elif isinstance(value, list):
            # stronger_at_levels: named only when it has levels, as the game reads an empty list by default.
            if value:
                names[name] = ",".join(str(item) for item in value)
        elif isinstance(value, str):
            names[name] = value
        elif isinstance(value, int | float) and not isinstance(value, bool):
            field, converted = game_field(name, value)
            numbers[field] = converted
    if effect.type in (DAMAGE, HEAL, DAMAGE_OVER_TIME):
        # The codec reads a missing share as 0.
        for share in ("attack_damage", "attack_damage_per_level", "spell_power", "spell_power_per_level"):
            numbers.setdefault(share, 0.0)
    number_map = ", ".join(f"{string_literal(key)}, {_double(numbers[key])}" for key in sorted(numbers))
    name_map = ", ".join(f"{string_literal(key)}, {string_literal(names[key])}" for key in sorted(names))
    nested = (
        "List.of(\n" + ",\n".join(_effect(e, indent + "        ") for e in inner) + ")"
        if inner
        else "List.of()"
    )
    return (
        f"{indent}new EffectExpectation({string_literal(effect.type)}, Map.of({number_map}), Map.of({name_map}), "
        f"{nested})"
    )


def _expectation(spell: SpellDef) -> str:
    effects = ",\n".join(_effect(effect, "                            ") for effect in spell.effects)
    effect_list = f"List.of(\n{effects})" if effects else "List.of()"
    cost = spell.cost
    up_front = cost.up_front if cost is not None else None
    crit = spell.crit
    multiplier = crit.multiplier_percent / 100 if crit.multiplier_percent is not None else None
    cast = spell.cast
    visuals = spell.visuals
    duration = seconds_to_ticks(getattr(cast, "duration_seconds", 0))
    interval = seconds_to_ticks(getattr(cast, "interval_seconds", 0))
    return (
        "            new SpellExpectation(\n"
        f"                    {string_literal(f'{MOD_ID}:{spell.id}')},\n"
        f"                    {string_literal(translation_key('spell', spell.id, 'name'))},\n"
        f"                    {string_literal(translation_key('spell', spell.id, 'description'))},\n"
        f"                    {_optional_string(spell.school)},\n"
        f"                    {string_literal(spell.scales_with)},\n"
        f"                    {string_literal(spell.targeting.type)},\n"
        f"                    {_double(getattr(spell.targeting, 'range', None) or 0.0)},\n"
        f"                    {string_literal(cast.type)},\n"
        f"                    {duration},\n"
        f"                    {interval},\n"
        f"                    {_bool(getattr(cast, 'castable_while_moving', False))},\n"
        f"                    {_bool(getattr(cast, 'lock_on', False))},\n"
        f"                    {_bool(getattr(cast, 'keep_sight', False))},\n"
        f"                    {_optional_double(_fraction(getattr(cast, 'keep_range_percent', None)))},\n"
        f"                    {_optional_string(getattr(cast, 'sound', None))},\n"
        f"                    {_optional_string(visuals.look if visuals is not None else None)},\n"
        f"                    {_optional_string(visuals.forming if visuals is not None else None)},\n"
        f"                    {_optional_string(visuals.impact_sound if visuals is not None else None)},\n"
        f"                    {string_literal(getattr(cast, 'animation', None) or 'raise')},\n"
        f"                    {string_literal(getattr(cast, 'cast_time_reduction', None) or 'shortens')},\n"
        f"                    {_optional_string(getattr(cast, 'player_animation', None))},\n"
        f"                    {_optional_string(cost.resource if cost is not None else None)},\n"
        f"                    {_double(cost.amount if cost is not None else 0.0)},\n"
        f"                    {_double(cost.per_level if cost is not None else 0.0)},\n"
        f"                    {_double(up_front.amount if up_front is not None else 0.0)},\n"
        f"                    {_double(up_front.per_level if up_front is not None else 0.0)},\n"
        f"                    {seconds_to_ticks(spell.cooldown_seconds)},\n"
        f"                    {seconds_to_ticks(spell.cooldown_per_level_seconds or 0)},\n"
        f"                    {seconds_to_ticks(spell.global_cooldown_seconds)},\n"
        f"                    {spell.max_level},\n"
        f"                    {_bool(crit.can_crit)},\n"
        f"                    {_optional_double(multiplier)},\n"
        f"                    {_double(crit.bonus_chance_percent / 100)},\n"
        f"                    {_double((crit.bonus_chance_per_level_percent or 0) / 100)},\n"
        f"                    {_bool(spell.npc_only)},\n"
        f"                    List.of({', '.join(string_literal(requirement) for requirement in spell.requires)}),\n"
        f"                    {_bool(spell.castable_while_stunned)},\n"
        f"                    {_bool(spell.is_passive)},\n"
        f"                    {_optional_string(spell.aura.group if spell.aura is not None else None)},\n"
        f"                    {effect_list})"
    )


def _expectations(content: Content) -> bytes:
    rows = [_expectation(entry.model) for entry in content.spells]
    spells = "List.of(\n" + ",\n".join(rows) + ")" if rows else "List.of()"
    source = (
        header(Path(__file__), SOURCES) + f"package {QA_GENERATED_PACKAGE};\n"
        "\n"
        "import java.util.List;\n"
        "import java.util.Map;\n"
        "import java.util.Optional;\n"
        "\n"
        "/** What {@code content/spells/} says about every spell, so GameTests can check that the game agrees. */\n"
        "public final class GeneratedSpellExpectations {\n"
        "    /**\n"
        "     * One effect as written in the YAML, in game units: its numbers and names by their data field names (e.g.\n"
        "     * {@code amount}, {@code duration_ticks}, {@code attack_damage} as a fraction, 0 when it adds none;\n"
        '     * {@code effect}, {@code affects}; a list of levels as {@code "2,5"}), and the effects a projectile or an\n'
        "     * area runs.\n"
        "     */\n"
        "    public record EffectExpectation(String type, Map<String, Double> numbers, Map<String, String> names,\n"
        "            List<EffectExpectation> effects) {\n"
        "    }\n"
        "\n"
        "    /**\n"
        "     * Expected state of one spell, in game units (ticks, fractions). Ids are full resource locations, e.g.\n"
        "     * {@code realmforge:test_bolt}; the name and description are translation keys; {@code range} is 0 for a\n"
        "     * spell on the caster itself and for a melee spell's enemy (in the caster's melee swing, RLM-399), the\n"
        "     * cost's amounts are 0 for a free spell, and the cast's duration and interval are 0 where its type has none;\n"
        "     * {@code keepRange} is the share of the cast that keeps the target in range;\n"
        "     * {@code look}, {@code forming} and {@code impactSound} are its visuals (RLM-639): its look, what shows in\n"
        "     * the caster's hands during the cast ({@code grow}, {@code storm}, {@code hands}) and an impact sound of\n"
        "     * its own, each empty when the data names none;\n"
        "     * {@code castAnimation} is how a mob moves while it casts, {@code raise} unless the data says otherwise;\n"
        "     * {@code castTimeReduction} is what Cast Time Reduction does to a channel ({@code shortens} or\n"
        "     * {@code adds_ticks}, RLM-572), {@code shortens} for every other cast; {@code playerAnimation} is the\n"
        "     * player animation its cast names instead of its cast type's own (RLM-641); the up-front cost is a channel's\n"
        "     * cost when it begins, 0 without one; {@code cooldownPerLevelTicks} is added for every rank above 1;\n"
        "     * {@code requires} names what a player's cast needs in hand ({@code melee_weapon}, {@code shield});\n"
        "     * {@code passive} marks a passive spell (RLM-541), which has no effects and the defaults of its cast;\n"
        "     * {@code auraGroup} an aura spell's group (RLM-595), empty for any other spell.\n"
        "     */\n"
        "    public record SpellExpectation(String id, String name, String description, Optional<String> school,\n"
        "            String scalesWith,\n"
        "            String targeting, double range,\n"
        "            String cast, int castDurationTicks, int castIntervalTicks, boolean castableWhileMoving, boolean lockOn,\n"
        "            boolean keepSight, Optional<Double> keepRange, Optional<String> castSound, Optional<String> look,\n"
        "            Optional<String> forming, Optional<String> impactSound,\n"
        "            String castAnimation, String castTimeReduction, Optional<String> playerAnimation,\n"
        "            Optional<String> costResource, double costAmount,\n"
        "            double costPerLevel, double costUpFront, double costUpFrontPerLevel, int cooldownTicks,\n"
        "            int cooldownPerLevelTicks, int globalCooldownTicks, int maxLevel, boolean canCrit,\n"
        "            Optional<Double> critMultiplier,\n"
        "            double critBonusChance, double critBonusChancePerLevel, boolean npcOnly, List<String> requires,\n"
        "            boolean castableWhileStunned, boolean passive, Optional<String> auraGroup,\n"
        "            List<EffectExpectation> effects) {\n"
        "    }\n"
        "\n"
        f"    public static final List<SpellExpectation> SPELLS = {spells};\n"
        "\n"
        "    private GeneratedSpellExpectations() {\n"
        "    }\n"
        "}\n"
    )
    return source.encode("utf-8")
