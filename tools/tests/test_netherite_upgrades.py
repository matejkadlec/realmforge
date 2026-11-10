"""Netherite upgrades (RLM-566): content/netherite_upgrades/ -> the registry entry, its smithing recipe and the item
tags of the pieces that take one, and validation of the upgrade's item, template and bonuses."""

from __future__ import annotations

import json

import pytest

from realmforge_tools.generate import generate_all
from realmforge_tools.problems import ValidationFailed
from realmforge_tools.project import RepoPaths
from realmforge_tools.validate import load_and_validate

DATA = "mod/src/generated/resources/data/realmforge"
UPGRADES = "content/netherite_upgrades"


def generate(repo: RepoPaths) -> dict[str, bytes]:
    content, _ = load_and_validate(repo)
    return {repo.rel(path): data for path, data in generate_all(content, repo).items()}


def problems_for(repo: RepoPaths) -> list[str]:
    with pytest.raises(ValidationFailed) as excinfo:
        load_and_validate(repo)
    return [str(p) for p in excinfo.value.problems]


def edit(repo: RepoPaths, upgrade: str, old: str, new: str) -> None:
    path = repo.root / UPGRADES / f"{upgrade}.yaml"
    text = path.read_text(encoding="utf-8")
    assert old in text
    path.write_text(text.replace(old, new), encoding="utf-8")


def test_the_three_upgrades_hold_the_values_of_2026_10_30() -> None:
    content, _ = load_and_validate(RepoPaths())
    upgrades = {entry.model.id: entry.model for entry in content.netherite_upgrades}
    assert sorted(upgrades) == ["ruby", "sapphire", "topaz"]
    values = {
        upgrade_id: {
            archetype: [(m.attribute, m.operation, m.game_amount) for m in modifiers]
            for archetype, modifiers in upgrade.archetypes()
        }
        for upgrade_id, upgrade in upgrades.items()
    }
    assert values == {
        "ruby": {
            "melee": [("minecraft:attack_damage", "add_multiplied_base", 0.05)],
            "caster": [("realmforge:spell_power", "add_multiplied_base", 0.05)],
        },
        "sapphire": {
            "melee": [("minecraft:armor", "add_value", 2), ("minecraft:armor_toughness", "add_value", 1)],
            "caster": [("realmforge:magic_shield", "add_value", 3)],
        },
        "topaz": {
            "melee": [("minecraft:movement_speed", "add_multiplied_base", 0.05)],
            "caster": [("realmforge:cooldown_reduction", "add_value", 0.05)],
        },
    }


def test_an_upgrade_becomes_its_entry_and_its_smithing_recipe() -> None:
    files = generate(RepoPaths())
    entry = json.loads(files[f"{DATA}/realmforge/netherite_upgrade/ruby.json"])
    assert entry == {
        "name": "netherite_upgrade.realmforge.ruby.name",
        "color": "#C42638",
        "item": "realmforge:ruby_upgrade",
        "template": "minecraft:netherite_upgrade_smithing_template",
        "melee": [
            {"attribute": "minecraft:attack_damage", "operation": "add_multiplied_base", "amount": 0.05}
        ],
        "caster": [
            {"attribute": "realmforge:spell_power", "operation": "add_multiplied_base", "amount": 0.05}
        ],
    }
    recipe = json.loads(files[f"{DATA}/recipe/netherite_upgrade/ruby.json"])
    assert recipe == {
        "type": "realmforge:netherite_upgrade",
        "addition": "realmforge:ruby_upgrade",
        "base": "#realmforge:netherite_upgradable",
        "template": "minecraft:netherite_upgrade_smithing_template",
        "upgrade": "realmforge:ruby",
    }
    lang = json.loads(files["mod/src/generated/resources/assets/realmforge/lang/en_us.json"])
    assert lang["netherite_upgrade.realmforge.ruby.name"] == "Ruby Upgrade"


def test_the_upgrade_items_are_eight_gems_around_a_diamond() -> None:
    files = generate(RepoPaths())
    for gem in ("ruby", "sapphire", "topaz"):
        recipe = json.loads(files[f"{DATA}/recipe/{gem}_upgrade.json"])
        key = {char: item for char, item in recipe["key"].items()}
        gem_char = next(char for char, item in key.items() if item == f"realmforge:{gem}")
        assert key[recipe["pattern"][1][1]] == "minecraft:diamond"
        assert "".join(recipe["pattern"]).count(gem_char) == 8
        assert recipe["result"] == {"id": f"realmforge:{gem}_upgrade"}


def test_the_pieces_that_take_an_upgrade_are_tagged_by_archetype() -> None:
    files = generate(RepoPaths())
    melee = json.loads(files[f"{DATA}/tags/item/netherite_upgradable/melee.json"])
    assert melee == {
        "values": [
            "minecraft:netherite_boots",
            "minecraft:netherite_chestplate",
            "minecraft:netherite_helmet",
            "minecraft:netherite_leggings",
        ]
    }
    caster = json.loads(files[f"{DATA}/tags/item/netherite_upgradable/caster.json"])
    assert caster == {
        "values": [
            "realmforge:netherite_cap",
            "realmforge:netherite_pants",
            "realmforge:netherite_robes",
            "realmforge:netherite_sandals",
        ]
    }
    every = json.loads(files[f"{DATA}/tags/item/netherite_upgradable.json"])
    assert every == {
        "values": ["#realmforge:netherite_upgradable/caster", "#realmforge:netherite_upgradable/melee"]
    }


def test_an_upgrade_names_one_of_our_items(repo: RepoPaths) -> None:
    edit(repo, "ruby", "item: realmforge:ruby_upgrade", "item: minecraft:diamond")
    assert any("minecraft:diamond is not one of our items" in p for p in problems_for(repo))


def test_an_upgrade_item_is_no_gear(repo: RepoPaths) -> None:
    edit(repo, "ruby", "item: realmforge:ruby_upgrade", "item: realmforge:netherite_wand")
    assert any("realmforge:netherite_wand is gear" in p for p in problems_for(repo))


def test_an_item_carries_one_upgrade(repo: RepoPaths) -> None:
    edit(repo, "topaz", "item: realmforge:topaz_upgrade", "item: realmforge:ruby_upgrade")
    assert any("realmforge:ruby_upgrade already carries" in p for p in problems_for(repo))


def test_the_template_exists(repo: RepoPaths) -> None:
    edit(
        repo,
        "ruby",
        "template: minecraft:netherite_upgrade_smithing_template",
        "template: minecraft:gem_upgrade_smithing_template",
    )
    assert any("template" in p and "gem_upgrade_smithing_template" in p for p in problems_for(repo))


def test_a_bonus_adds_its_percents_to_the_base(repo: RepoPaths) -> None:
    edit(
        repo,
        "topaz",
        "{attribute: minecraft:movement_speed, operation: add_multiplied_base",
        "{attribute: minecraft:movement_speed, operation: add_multiplied_total",
    )
    assert any("melee.0.operation" in p and "add_multiplied_base" in p for p in problems_for(repo))


def test_a_bonus_never_gives_max_health(repo: RepoPaths) -> None:
    edit(repo, "sapphire", "{attribute: minecraft:armor_toughness,", "{attribute: minecraft:max_health,")
    assert any("melee.1.attribute" in p and "max health" in p for p in problems_for(repo))


def test_a_bonus_on_a_share_takes_its_unit(repo: RepoPaths) -> None:
    edit(repo, "topaz", "operation: add_value, amount_percent: 5", "operation: add_value, amount: 0.05")
    assert any("caster.0.amount" in p and "share" in p for p in problems_for(repo))


def test_only_armor_is_netherite_upgradable(repo: RepoPaths) -> None:
    wand = repo.content / "items" / "netherite_wand.yaml"
    text = wand.read_text(encoding="utf-8")
    wand.write_text(
        text.replace("  modifiers:", "  netherite_upgradable: true\n  modifiers:", 1), encoding="utf-8"
    )
    assert any("only armor takes a netherite upgrade" in p for p in problems_for(repo))
