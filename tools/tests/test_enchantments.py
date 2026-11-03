"""Enchantments (content/enchantments/): validation and generation (architecture 13, RLM-537)."""

from __future__ import annotations

import json

import pytest
from pydantic import ValidationError

from realmforge_tools.generate import generate_all
from realmforge_tools.problems import ValidationFailed
from realmforge_tools.project import RepoPaths
from realmforge_tools.schema.enchantment import EnchantmentDef
from realmforge_tools.validate import load_and_validate
from tests.conftest import write

DATA = "mod/src/generated/resources/data"
LANG = "mod/src/generated/resources/assets/realmforge/lang/en_us.json"
KEYS_JAVA = "mod/src/generated/java/io/github/matejkadlec/realmforge/generated/GeneratedEnchantments.java"
ENCHANTMENT_INFO = f"{DATA}/apothic_enchanting/data_maps/enchantment/enchantment_info.json"
TEST = """\
id: test_ward
name: {en_us: Test Ward}
description: {en_us: Reduces projectile damage.}
supported_items: minecraft:enchantable/armor
slots: [armor]
weight: 2
vanilla_max_level: 4
max_level: 4
min_cost: {base: 1, per_level_above_first: 2}
max_cost: {base: 3, per_level_above_first: 2}
anvil_cost: 1
protection: {points_per_level: 1, against: minecraft:is_projectile}
"""


def generate(repo: RepoPaths) -> dict[str, bytes]:
    content, _ = load_and_validate(repo)
    return {repo.rel(path): data for path, data in generate_all(content, repo).items()}


def problems_for(repo: RepoPaths) -> list[str]:
    with pytest.raises(ValidationFailed) as excinfo:
        load_and_validate(repo)
    return [str(p) for p in excinfo.value.problems]


# The repository's content


def test_magic_protection_is_half_a_point_per_level_against_magic_hits(repo: RepoPaths) -> None:
    """Matěj's pick of 2026-11-01 (option A): 0.5 point per level in vanilla's shared pool, Projectile Protection's
    table and anvil numbers, and no exclusive set."""
    files = generate(repo)
    definition = json.loads(files[f"{DATA}/realmforge/enchantment/magic_protection.json"])
    assert definition == {
        "anvil_cost": 2,
        "description": {"translate": "enchantment.realmforge.magic_protection"},
        "effects": {
            "minecraft:damage_protection": [
                {
                    "effect": {
                        "type": "minecraft:add",
                        "value": {"type": "minecraft:linear", "base": 0.5, "per_level_above_first": 0.5},
                    },
                    "requirements": {
                        "condition": "minecraft:damage_source_properties",
                        "predicate": {
                            "tags": [
                                {"expected": True, "id": "realmforge:is_magic"},
                                {"expected": False, "id": "minecraft:bypasses_invulnerability"},
                            ]
                        },
                    },
                }
            ]
        },
        "max_cost": {"base": 9, "per_level_above_first": 6},
        "max_level": 4,
        "min_cost": {"base": 3, "per_level_above_first": 6},
        "slots": ["armor"],
        "supported_items": "#minecraft:enchantable/armor",
        "weight": 5,
    }


def test_magic_protection_reaches_viii_with_apothic_enchanting_and_joins_the_table_and_loot(
    repo: RepoPaths,
) -> None:
    files = generate(repo)
    assert json.loads(files[ENCHANTMENT_INFO]) == {
        "values": {"realmforge:magic_protection": {"max_level": 8}}
    }
    non_treasure = json.loads(files[f"{DATA}/minecraft/tags/enchantment/non_treasure.json"])
    assert non_treasure == {"values": ["realmforge:magic_protection"]}
    lang = json.loads(files[LANG])
    assert lang["enchantment.realmforge.magic_protection"] == "Magic Protection"
    assert "magic" in lang["enchantment.realmforge.magic_protection.desc"]
    assert 'MAGIC_PROTECTION = key("magic_protection")' in files[KEYS_JAVA].decode()


def test_any_two_protections_combine_and_all_five_count(repo: RepoPaths) -> None:
    """RLM-537: Fire, Blast and Projectile Protection no longer exclude each other (Apothic Enchanting already took
    Protection out), and the five protections are one group, of which a piece holds at most two."""
    files = generate(repo)
    assert json.loads(files[f"{DATA}/minecraft/tags/enchantment/exclusive_set/armor.json"]) == {
        "values": [],
        "remove": [
            "minecraft:fire_protection",
            "minecraft:blast_protection",
            "minecraft:projectile_protection",
        ],
    }
    assert json.loads(files[f"{DATA}/realmforge/tags/enchantment/protections.json"]) == {
        "values": [
            "minecraft:protection",
            "minecraft:fire_protection",
            "minecraft:blast_protection",
            "minecraft:projectile_protection",
            "realmforge:magic_protection",
        ]
    }


def test_magic_hits_are_the_magic_schools_and_vanilla_magic(repo: RepoPaths) -> None:
    files = generate(repo)
    assert json.loads(files[f"{DATA}/realmforge/tags/damage_type/is_magic.json"]) == {
        "values": [
            "realmforge:arcane",
            "realmforge:fire",
            "realmforge:frost",
            "minecraft:magic",
            "minecraft:indirect_magic",
        ]
    }


# Validation and generation of other entries


def test_an_enchantment_without_a_higher_level_writes_no_apothic_level(repo: RepoPaths) -> None:
    (repo.content / "enchantments" / "magic_protection.yaml").unlink()
    write(repo.content / "enchantments" / "test_ward.yaml", TEST)
    files = generate(repo)
    assert ENCHANTMENT_INFO not in files
    assert f"{DATA}/minecraft/tags/enchantment/non_treasure.json" not in files
    protections = json.loads(files[f"{DATA}/realmforge/tags/enchantment/protections.json"])["values"]
    assert protections[-1] == "realmforge:test_ward"


@pytest.mark.parametrize(
    ("change", "message"),
    [
        (("minecraft:is_projectile", "minecraft:is_spell"), "unknown damage type tag #minecraft:is_spell"),
        (
            ("supported_items: minecraft:enchantable/armor", "supported_items: minecraft:robes"),
            "unknown item tag #minecraft:robes",
        ),
        (
            ("anvil_cost: 1\n", "anvil_cost: 1\ntags: [minecraft:rare]\n"),
            "unknown enchantment tag #minecraft:rare",
        ),
    ],
)
def test_an_enchantment_names_tags_that_exist(repo: RepoPaths, change: tuple[str, str], message: str) -> None:
    write(repo.content / "enchantments" / "test_ward.yaml", TEST.replace(*change))
    problems = problems_for(repo)
    assert any(message in p for p in problems), problems


def test_apothic_enchanting_only_raises_a_level() -> None:
    data = {
        "id": "test",
        "name": {"en_us": "Test"},
        "description": {"en_us": "Test."},
        "supported_items": "minecraft:enchantable/armor",
        "slots": ["armor"],
        "weight": 1,
        "vanilla_max_level": 4,
        "max_level": 3,
        "min_cost": {"base": 1, "per_level_above_first": 1},
        "max_cost": {"base": 2, "per_level_above_first": 1},
        "anvil_cost": 1,
        "protection": {"points_per_level": 1, "against": "minecraft:is_fire"},
    }
    with pytest.raises(ValidationError, match="only raises a level"):
        EnchantmentDef.model_validate(data)
