"""Spell schools (architecture 6.2, 9.1): the ``realmforge:spell_school`` entries, one vanilla damage type per school,
the damage type tag entries, ``ResourceKey`` constants for Java and the QA mod's expectations. The names and death
messages go into the lang files (``lang.py``)."""

from __future__ import annotations

from pathlib import Path
from typing import Any

from realmforge_tools.content import Content
from realmforge_tools.formats.jsonfmt import dump_json
from realmforge_tools.generate.datapack import data_dir, translation_key
from realmforge_tools.generate.javagen import constant_name, header, java_file_path, string_literal
from realmforge_tools.generate.textures import placeholder_school_icon
from realmforge_tools.project import JAVA_PACKAGE, MOD_ID, QA_JAVA_PACKAGE, RepoPaths
from realmforge_tools.schema.common import LocalizedText
from realmforge_tools.schema.spell_school import SpellSchoolDef, death_message_keys

GENERATED_PACKAGE = f"{JAVA_PACKAGE}.generated"
QA_GENERATED_PACKAGE = f"{QA_JAVA_PACKAGE}.generated"
REGISTRY = "spell_school"
SOURCES = "content/spell_schools/"

# Hunger a hit costs the one who takes it: vanilla's values for melee (0.1) and for magic (0.0).
_EXHAUSTION = {"physical": 0.1, "magic": 0.0}


def translations(content: Content) -> dict[str, LocalizedText]:
    """Every school's name and death message, by translation key."""
    texts: dict[str, LocalizedText] = {}
    for entry in content.spell_schools:
        school = entry.model
        texts[translation_key(REGISTRY, school.id, "name")] = school.name
        for key in death_message_keys(school):
            texts[key] = school.death_message
    return texts


def generate(content: Content, paths: RepoPaths) -> dict[Path, bytes]:
    schools = [entry.model for entry in content.spell_schools]
    data = paths.mod_generated / "resources" / "data"
    # GUI sprites: a school's icon goes inline before its numbers in spell tooltips (SpellSchools#icon).
    icons = paths.mod_generated / "resources" / "assets" / MOD_ID / "textures" / "gui" / "sprites" / "school"
    files: dict[Path, bytes] = {}
    for school in schools:
        files[data_dir(paths) / MOD_ID / REGISTRY / f"{school.id}.json"] = dump_json(_entry(school))
        files[data / MOD_ID / "damage_type" / f"{school.id}.json"] = dump_json(_damage_type(school))
        files[icons / f"{school.id}.png"] = placeholder_school_icon(int(school.color[1:], 16))
    tags = data / "minecraft" / "tags" / "damage_type"
    # Every school's hits land through the hurt cooldown and push nobody: a spell knocks back only through its
    # knockback effect (architecture 9.1, RLM-214). Magic ones skip vanilla's armor too.
    for tag, members in (
        ("bypasses_cooldown", schools),
        ("no_knockback", schools),
        ("bypasses_armor", [school for school in schools if school.bypasses_armor]),
    ):
        if members:
            files[tags / f"{tag}.json"] = dump_json({"values": sorted(s.damage_type for s in members)})
    files[java_file_path(paths.mod_generated / "java", GENERATED_PACKAGE, "GeneratedSpellSchools")] = _keys(
        content
    )
    files[
        java_file_path(paths.qa_generated / "java", QA_GENERATED_PACKAGE, "GeneratedSpellSchoolExpectations")
    ] = _expectations(content)
    return files


def _entry(school: SpellSchoolDef) -> dict[str, Any]:
    entry: dict[str, Any] = {
        "name": translation_key(REGISTRY, school.id, "name"),
        "kind": school.kind,
        "color": school.color.upper(),
        "combat_text_color": _combat_text_color(school).upper(),
        "damage_type": school.damage_type,
    }
    if school.power is not None:
        entry["power"] = school.power
    if school.resistance is not None:
        entry["resistance"] = school.resistance
    return entry


def _combat_text_color(school: SpellSchoolDef) -> str:
    return school.combat_text_color if school.combat_text_color is not None else school.color


def _damage_type(school: SpellSchoolDef) -> dict[str, Any]:
    # No difficulty scaling: school damage is balanced by levels and data, not by the world's difficulty.
    return {"exhaustion": _EXHAUSTION[school.kind], "message_id": school.message_id, "scaling": "never"}


def _comment(school: SpellSchoolDef) -> str:
    english = school.name.en_us.replace("*/", "*&#47;").replace("@", "&#64;")
    return f'{school.kind}, "{english}"'


def _keys(content: Content) -> bytes:
    constants = [constant_name(entry.model.id) for entry in content.spell_schools]
    fields = "".join(
        f"    /** {{@code {MOD_ID}:{entry.model.id}}} from {{@code {entry.source}}}: {_comment(entry.model)}. */\n"
        f"    public static final ResourceKey<SpellSchool> {constant} = key({string_literal(entry.model.id)});\n"
        "\n"
        for entry, constant in zip(content.spell_schools, constants, strict=True)
    )
    source = (
        header(Path(__file__), SOURCES) + f"package {GENERATED_PACKAGE};\n"
        "\n"
        f"import {JAVA_PACKAGE}.Realmforge;\n"
        f"import {JAVA_PACKAGE}.combat.CombatRegistries;\n"
        f"import {JAVA_PACKAGE}.combat.SpellSchool;\n"
        "import java.util.List;\n"
        "import net.minecraft.resources.ResourceKey;\n"
        "\n"
        "/**\n"
        " * Keys of the spell schools in {@code content/spell_schools/}, so Java names a school without typing its id. Each\n"
        " * school's damage type has the same id ({@link SpellSchool#damageType()}).\n"
        " */\n"
        "public final class GeneratedSpellSchools {\n" + fields + "    /** Every key above, in id order. */\n"
        f"    public static final List<ResourceKey<SpellSchool>> ALL = List.of({', '.join(constants)});\n"
        "\n"
        "    private GeneratedSpellSchools() {\n"
        "    }\n"
        "\n"
        "    private static ResourceKey<SpellSchool> key(String id) {\n"
        "        return ResourceKey.create(CombatRegistries.SPELL_SCHOOL, Realmforge.id(id));\n"
        "    }\n"
        "}\n"
    )
    return source.encode("utf-8")


def _optional(value: str | None) -> str:
    return f"Optional.of({string_literal(value)})" if value is not None else "Optional.empty()"


def _expectations(content: Content) -> bytes:
    rows = []
    for entry in content.spell_schools:
        school = entry.model
        rows.append(
            "            new SpellSchoolExpectation(\n"
            f"                    {string_literal(f'{MOD_ID}:{school.id}')},\n"
            f"                    {string_literal(translation_key(REGISTRY, school.id, 'name'))},\n"
            f"                    {'true' if school.kind == 'magic' else 'false'},\n"
            f"                    0xFF{school.color[1:].upper()},\n"
            f"                    0xFF{_combat_text_color(school)[1:].upper()},\n"
            f"                    {string_literal(school.damage_type)},\n"
            f"                    {string_literal(school.message_id)},\n"
            f"                    {_optional(school.power)},\n"
            f"                    {_optional(school.resistance)})"
        )
    schools = "List.of(\n" + ",\n".join(rows) + ")" if rows else "List.of()"
    source = (
        header(Path(__file__), SOURCES) + f"package {QA_GENERATED_PACKAGE};\n"
        "\n"
        "import java.util.List;\n"
        "import java.util.Optional;\n"
        "\n"
        "/** What {@code content/spell_schools/} says about every school, so GameTests can check that the game agrees. */\n"
        "public final class GeneratedSpellSchoolExpectations {\n"
        "    /**\n"
        "     * Expected state of one school. Ids are full resource locations, e.g. {@code realmforge:fire}; the colours are\n"
        '     * opaque ARGB, as the school\'s codec reads {@code "#RRGGBB"}.\n'
        "     */\n"
        "    public record SpellSchoolExpectation(String id, String nameKey, boolean magic, int color,\n"
        "            int combatTextColor, String damageType, String messageId, Optional<String> power,\n"
        "            Optional<String> resistance) {\n"
        "    }\n"
        "\n"
        f"    public static final List<SpellSchoolExpectation> SPELL_SCHOOLS = {schools};\n"
        "\n"
        "    private GeneratedSpellSchoolExpectations() {\n"
        "    }\n"
        "}\n"
    )
    return source.encode("utf-8")
