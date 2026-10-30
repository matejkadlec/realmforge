"""Spells: ``ResourceKey`` constants for Java and the QA mod's expectations (the data itself: datapack.py, which turns
``cooldown_seconds`` into ``cooldown_ticks`` and every ``<name>_percent`` into a fraction named ``<name>``)."""

from __future__ import annotations

from pathlib import Path

from pydantic import BaseModel

from realmforge_tools.content import Content
from realmforge_tools.generate.datapack import game_field, seconds_to_ticks, translation_key
from realmforge_tools.generate.javagen import constant_name, header, java_file_path, string_literal
from realmforge_tools.generate.textures import placeholder_orb_texture
from realmforge_tools.project import JAVA_PACKAGE, MOD_ID, QA_JAVA_PACKAGE, RepoPaths
from realmforge_tools.schema.spell import DAMAGE, HEAL, SpellDef

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
    textures = paths.mod_generated / "resources" / "assets" / MOD_ID / "textures" / "entity" / "spell"
    for entry in content.spells:
        for _, effect in entry.model.all_effects():
            appearance = getattr(effect, "appearance", None)
            if appearance is not None:
                # spell/SpellVisuals#projectileTexture: textures/entity/spell/<spell>.png.
                files[textures / f"{entry.model.id}.png"] = placeholder_orb_texture(
                    f"{MOD_ID}:{entry.model.id}", int(appearance.color.removeprefix("#"), 16)
                )
    return files


def _double(value: float) -> str:
    """A Java double literal; repr() gives the shortest text that reads back as the same double."""
    return repr(float(value))


def _comment(spell: SpellDef) -> str:
    effects = ", ".join(effect.type.removeprefix(f"{MOD_ID}:") for effect in spell.effects)
    school = spell.school.removeprefix(f"{MOD_ID}:") if spell.school is not None else "no school"
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
    if effect.type in (DAMAGE, HEAL):
        # The codec reads a missing share as 0.
        numbers.setdefault("attack_damage", 0.0)
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
    cost = spell.cost
    crit = spell.crit
    multiplier = crit.multiplier_percent / 100 if crit.multiplier_percent is not None else None
    cast = spell.cast
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
        f"                    {_double(getattr(spell.targeting, 'range', 0.0))},\n"
        f"                    {string_literal(cast.type)},\n"
        f"                    {duration},\n"
        f"                    {interval},\n"
        f"                    {_bool(getattr(cast, 'castable_while_moving', False))},\n"
        f"                    {_bool(getattr(cast, 'lock_on', False))},\n"
        f"                    {_bool(getattr(cast, 'keep_sight', False))},\n"
        f"                    {_optional_double(_fraction(getattr(cast, 'keep_range_percent', None)))},\n"
        f"                    {_optional_string(getattr(cast, 'sound', None))},\n"
        f"                    {_bool(getattr(cast, 'forms_projectile', False))},\n"
        f"                    {string_literal(getattr(cast, 'animation', None) or 'raise')},\n"
        f"                    {_optional_string(cost.resource if cost is not None else None)},\n"
        f"                    {_double(cost.amount if cost is not None else 0.0)},\n"
        f"                    {_double(cost.per_level if cost is not None else 0.0)},\n"
        f"                    {seconds_to_ticks(spell.cooldown_seconds)},\n"
        f"                    {seconds_to_ticks(spell.global_cooldown_seconds)},\n"
        f"                    {spell.max_level},\n"
        f"                    {_bool(crit.can_crit)},\n"
        f"                    {_optional_double(multiplier)},\n"
        f"                    {_double(crit.bonus_chance_percent / 100)},\n"
        f"                    {_double((crit.bonus_chance_per_level_percent or 0) / 100)},\n"
        f"                    {_bool(spell.npc_only)},\n"
        f"                    List.of({', '.join(string_literal(requirement) for requirement in spell.requires)}),\n"
        f"                    {_bool(spell.castable_while_stunned)},\n"
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
        "     * spell on the caster itself, the cost's amounts are 0 for a free spell, and the cast's duration and interval\n"
        "     * are 0 where its type has none; {@code keepRange} is the share of the cast that keeps the target in range;\n"
        "     * {@code castAnimation} is how a mob moves while it casts, {@code raise} unless the data says otherwise;\n"
        "     * {@code requires} names what a player's cast needs in hand ({@code melee_weapon}, {@code shield}).\n"
        "     */\n"
        "    public record SpellExpectation(String id, String name, String description, Optional<String> school,\n"
        "            String scalesWith,\n"
        "            String targeting, double range,\n"
        "            String cast, int castDurationTicks, int castIntervalTicks, boolean castableWhileMoving, boolean lockOn,\n"
        "            boolean keepSight, Optional<Double> keepRange, Optional<String> castSound, boolean formsProjectile,\n"
        "            String castAnimation, Optional<String> costResource, double costAmount, double costPerLevel,\n"
        "            int cooldownTicks, int globalCooldownTicks, int maxLevel, boolean canCrit, Optional<Double> critMultiplier,\n"
        "            double critBonusChance, double critBonusChancePerLevel, boolean npcOnly, List<String> requires,\n"
        "            boolean castableWhileStunned, List<EffectExpectation> effects) {\n"
        "    }\n"
        "\n"
        f"    public static final List<SpellExpectation> SPELLS = {spells};\n"
        "\n"
        "    private GeneratedSpellExpectations() {\n"
        "    }\n"
        "}\n"
    )
    return source.encode("utf-8")
