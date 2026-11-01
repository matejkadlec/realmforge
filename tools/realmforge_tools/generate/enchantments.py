"""Enchantments (architecture 13): each entry of ``content/enchantments/`` as vanilla's data-driven enchantment
(``data/realmforge/enchantment/<id>.json``), the vanilla enchantment tags it joins, its highest level for Apothic
Enchanting (its data map ``apothic_enchanting:enchantment_info``, which NeoForge merges with Apothic Enchanting's own),
its name and tooltip line, and ``ResourceKey`` constants for Java.

The two-protection rule (RLM-537): ``#realmforge:protections`` holds vanilla's four protections and ours, and
``#minecraft:exclusive_set/armor`` loses Fire, Blast and Projectile Protection (NeoForge's ``remove``; Apothic
Enchanting already takes Protection out), so any two protections combine and ``item/ProtectionLimit`` refuses a
third. Both are written whatever the content holds: the rule is the vanilla protections' too."""

from __future__ import annotations

from collections import defaultdict
from pathlib import Path
from typing import Any

from realmforge_tools.content import Content
from realmforge_tools.formats.jsonfmt import dump_json
from realmforge_tools.generate.javagen import constant_name, header, java_file_path, string_literal
from realmforge_tools.project import JAVA_PACKAGE, MOD_ID, RepoPaths
from realmforge_tools.schema.common import LocalizedText
from realmforge_tools.schema.enchantment import (
    ARMOR_EXCLUSIVE_SET,
    PROTECTIONS_TAG,
    VANILLA_PROTECTIONS,
    EnchantmentCostDef,
    EnchantmentDef,
)

GENERATED_PACKAGE = f"{JAVA_PACKAGE}.generated"
SOURCES = "content/enchantments/"
APOTHIC_ENCHANTMENT_INFO = "apothic_enchanting/data_maps/enchantment/enchantment_info.json"


def translation_key(enchantment_id: str) -> str:
    """Vanilla's key of an enchantment's name, ``enchantment.realmforge.<id>``."""
    return f"enchantment.{MOD_ID}.{enchantment_id}"


def description_key(enchantment_id: str) -> str:
    """Enchantment Descriptions' key of its tooltip line, which Apothic Enchanting shows inline."""
    return f"{translation_key(enchantment_id)}.desc"


def translations(content: Content) -> dict[str, LocalizedText]:
    """Every enchantment's name and tooltip line, by translation key."""
    texts: dict[str, LocalizedText] = {}
    for entry in content.enchantments:
        texts[translation_key(entry.model.id)] = entry.model.name
        texts[description_key(entry.model.id)] = entry.model.description
    return texts


def generate(content: Content, paths: RepoPaths) -> dict[Path, bytes]:
    data = paths.mod_generated / "resources" / "data"
    files: dict[Path, bytes] = {}
    members: dict[str, list[str]] = defaultdict(list)
    max_levels: dict[str, dict[str, int]] = {}
    for entry in content.enchantments:
        enchantment = entry.model
        enchantment_id = f"{MOD_ID}:{enchantment.id}"
        files[data / MOD_ID / "enchantment" / f"{enchantment.id}.json"] = dump_json(_definition(enchantment))
        for tag in enchantment.tags:
            members[tag].append(enchantment_id)
        if enchantment.max_level > enchantment.vanilla_max_level:
            max_levels[enchantment_id] = {"max_level": enchantment.max_level}
    for tag, ids in sorted(members.items()):
        namespace, path = tag.split(":", 1)
        files[data / namespace / "tags" / "enchantment" / f"{path}.json"] = dump_json({"values": sorted(ids)})
    namespace, path = PROTECTIONS_TAG.split(":", 1)
    ours = sorted(f"{MOD_ID}:{entry.model.id}" for entry in content.enchantments)
    files[data / namespace / "tags" / "enchantment" / f"{path}.json"] = dump_json(
        {"values": [*VANILLA_PROTECTIONS, *ours]}
    )
    namespace, path = ARMOR_EXCLUSIVE_SET.split(":", 1)
    files[data / namespace / "tags" / "enchantment" / f"{path}.json"] = dump_json(
        {"values": [], "remove": [p for p in VANILLA_PROTECTIONS if p != "minecraft:protection"]}
    )
    if max_levels:
        files[data / APOTHIC_ENCHANTMENT_INFO] = dump_json({"values": dict(sorted(max_levels.items()))})
    files[java_file_path(paths.mod_generated / "java", GENERATED_PACKAGE, "GeneratedEnchantments")] = _keys(
        content
    )
    return files


def _definition(enchantment: EnchantmentDef) -> dict[str, Any]:
    """Vanilla's enchantment JSON, field for field as ``projectile_protection.json``: a protection effect against
    the tag's hits, never one that bypasses invulnerability (/kill, the void). No exclusive set."""
    protection = enchantment.protection
    return {
        "anvil_cost": enchantment.anvil_cost,
        "description": {"translate": translation_key(enchantment.id)},
        "effects": {
            "minecraft:damage_protection": [
                {
                    "effect": {
                        "type": "minecraft:add",
                        "value": {
                            "type": "minecraft:linear",
                            "base": protection.points_per_level,
                            "per_level_above_first": protection.points_per_level,
                        },
                    },
                    "requirements": {
                        "condition": "minecraft:damage_source_properties",
                        "predicate": {
                            "tags": [
                                {"expected": True, "id": protection.against},
                                {"expected": False, "id": "minecraft:bypasses_invulnerability"},
                            ]
                        },
                    },
                }
            ]
        },
        "max_cost": _cost(enchantment.max_cost),
        "max_level": enchantment.vanilla_max_level,
        "min_cost": _cost(enchantment.min_cost),
        "slots": list(enchantment.slots),
        "supported_items": f"#{enchantment.supported_items}",
        "weight": enchantment.weight,
    }


def _cost(cost: EnchantmentCostDef) -> dict[str, int]:
    return {"base": cost.base, "per_level_above_first": cost.per_level_above_first}


def _keys(content: Content) -> bytes:
    constants = [constant_name(entry.model.id) for entry in content.enchantments]
    fields = "".join(
        f"    /** {{@code {MOD_ID}:{entry.model.id}}} from {{@code {entry.source}}}. */\n"
        f"    public static final ResourceKey<Enchantment> {constant} = key({string_literal(entry.model.id)});\n"
        "\n"
        for entry, constant in zip(content.enchantments, constants, strict=True)
    )
    source = (
        header(Path(__file__), SOURCES) + f"package {GENERATED_PACKAGE};\n"
        "\n"
        f"import {JAVA_PACKAGE}.Realmforge;\n"
        "import java.util.List;\n"
        "import net.minecraft.core.registries.Registries;\n"
        "import net.minecraft.resources.ResourceKey;\n"
        "import net.minecraft.world.item.enchantment.Enchantment;\n"
        "\n"
        "/** Keys of the enchantments in {@code content/enchantments/}, so Java names one without typing its id. */\n"
        "public final class GeneratedEnchantments {\n" + fields + "    /** Every key above, in id order. */\n"
        f"    public static final List<ResourceKey<Enchantment>> ALL = List.of({', '.join(constants)});\n"
        "\n"
        "    private GeneratedEnchantments() {\n"
        "    }\n"
        "\n"
        "    private static ResourceKey<Enchantment> key(String id) {\n"
        "        return ResourceKey.create(Registries.ENCHANTMENT, Realmforge.id(id));\n"
        "    }\n"
        "}\n"
    )
    return source.encode("utf-8")
