"""Gear on items (wands, armor: schema/item.py GearDef, generate/items.py) and the drops of vanilla mobs an item names
(``dropped_by``): RLM-172, RLM-197."""

from __future__ import annotations

import json

import pytest

from realmforge_tools.formats.png import PNG_SIGNATURE
from realmforge_tools.generate import generate_all
from realmforge_tools.problems import ValidationFailed
from realmforge_tools.project import RepoPaths
from realmforge_tools.validate import load_and_validate

from .conftest import write

GENERATED_JAVA = "mod/src/generated/java/io/github/matejkadlec/realmforge/generated/GeneratedItems.java"
EXPECTATIONS_JAVA = (
    "qa-mod/src/generated/java/io/github/matejkadlec/realmforge/qa/generated/GeneratedItemExpectations.java"
)
ASSETS = "mod/src/generated/resources/assets/realmforge"
DATA = "mod/src/generated/resources/data"
WAND = """\
id: test_wand
name: {en_us: Test Wand}
gear:
  kind: wand
  durability: 131
  enchantability: 5
  repair_with: '#minecraft:stone_tool_materials'
  attack_damage: 2
  attack_speed: 1.0
  two_handed: true
  modifiers:
    - {attribute: realmforge:spell_power, operation: add_value, amount_percent: 15}
    - {attribute: realmforge:magic_shield, operation: add_value, amount: 3}
"""
CAP = """\
id: test_cap
name: {en_us: Test Cap}
color: "#2F4FA8"
gear:
  kind: head
  durability: 165
  enchantability: 9
  repair_with: minecraft:iron_ingot
  look: test_cloth
  modifiers:
    - {attribute: realmforge:magic_shield, operation: add_value, amount: 2}
"""
DUST = """\
id: test_dust
name: {en_us: Test Dust}
dropped_by:
  - {entity: minecraft:witch, count: {min: 0, max: 2}, looting_count: 1}
"""


def problems_for(repo: RepoPaths) -> list[str]:
    with pytest.raises(ValidationFailed) as excinfo:
        load_and_validate(repo)
    return [str(p) for p in excinfo.value.problems]


def generate(repo: RepoPaths) -> dict[str, bytes]:
    content, _ = load_and_validate(repo)
    return {repo.rel(path): data for path, data in generate_all(content, repo).items()}


def registration(java: str, item_id: str) -> str:
    start = java.index(f'registerSimpleItem("{item_id}"')
    return java[start : java.index(";\n", start)]


def test_a_wand_gets_durability_its_attack_and_its_stats_in_the_main_hand(repo: RepoPaths) -> None:
    write(repo.content / "items" / "test_wand.yaml", WAND)
    files = generate(repo)
    wand = registration(files[GENERATED_JAVA].decode(), "test_wand")
    assert ".durability(131)" in wand
    assert ".enchantable(5)" in wand
    assert ".stacksTo(" not in wand, "durability stacks it to 1"
    assert 'TagKey.create(Registries.ITEM, Identifier.parse("minecraft:stone_tool_materials"))' in wand
    assert ".component(DataComponents.WEAPON, new Weapon(1))" in wand
    assert (
        "new AttributeModifier(Item.BASE_ATTACK_DAMAGE_ID, 1.0, AttributeModifier.Operation.ADD_VALUE), "
        "EquipmentSlotGroup.MAINHAND" in wand
    ), "the tooltip's 2 attack damage less a bare hand's 1"
    assert "new AttributeModifier(Item.BASE_ATTACK_SPEED_ID, -3.0" in wand, (
        "1.0 attacks a second less a hand's 4"
    )
    assert (
        'attribute(context, "realmforge:spell_power"), modifier("realmforge:gear/mainhand/0", 0.15, '
        "AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND" in wand
    )
    assert 'modifier("realmforge:gear/mainhand/1", 3.0' in wand
    assert "EQUIPPABLE" not in wand
    assert json.loads(files[f"{DATA}/realmforge/tags/item/wands.json"])["values"] == [
        "realmforge:copper_wand",
        "realmforge:diamond_wand",
        "realmforge:golden_wand",
        "realmforge:iron_wand",
        "realmforge:netherite_wand",
        "realmforge:stone_wand",
        "realmforge:test_wand",
        "realmforge:wooden_wand",
    ]
    assert (
        "realmforge:test_wand"
        in json.loads(files[f"{DATA}/minecraft/tags/item/enchantable/durability.json"])["values"]
    )
    assert (
        "realmforge:test_wand" in json.loads(files[f"{DATA}/realmforge/tags/item/two_handed.json"])["values"]
    )
    expectations = files[EXPECTATIONS_JAVA].decode()
    assert '"wand", "mainhand", 131, 5, "#minecraft:stone_tool_materials",' in expectations
    assert '2.0, 1.0, "", true,' in expectations, "the wand is two-handed"
    assert (
        'new GearModifier("realmforge:gear/mainhand/0", "realmforge:spell_power", "add_value", 0.15)'
        in expectations
    )


def test_armor_gets_its_slot_look_and_placeholder_textures(repo: RepoPaths) -> None:
    write(repo.content / "items" / "test_cap.yaml", CAP)
    files = generate(repo)
    cap = registration(files[GENERATED_JAVA].decode(), "test_cap")
    assert "Equippable.builder(EquipmentSlot.HEAD)" in cap
    assert 'Identifier.parse("minecraft:item.armor.equip_leather")' in cap, "leather's sound by default"
    assert 'ResourceKey.create(EquipmentAssets.ROOT_ID, Identifier.parse("realmforge:test_cloth"))' in cap
    assert (
        'HolderSet.direct(context.getOrThrow(ResourceKey.create(Registries.ITEM, Identifier.parse("minecraft:iron_ingot"))))'
        in cap
    )
    assert 'modifier("realmforge:gear/head/0", 2.0' in cap
    assert "WEAPON" not in cap and "ATTACK_DAMAGE" not in cap
    assert json.loads(files[f"{ASSETS}/equipment/test_cloth.json"]) == {
        "layers": {
            "humanoid": [{"texture": "realmforge:test_cloth"}],
            "humanoid_leggings": [{"texture": "realmforge:test_cloth"}],
        }
    }
    for layer in ("humanoid", "humanoid_leggings"):
        texture = files[f"{ASSETS}/textures/entity/equipment/{layer}/test_cloth.png"]
        assert texture.startswith(PNG_SIGNATURE)
        assert texture[16:24] == (64).to_bytes(4, "big") + (32).to_bytes(4, "big"), "vanilla's armor layout"
    for tag in ("enchantable/head_armor", "enchantable/durability", "enchantable/equippable"):
        assert (
            "realmforge:test_cap" in json.loads(files[f"{DATA}/minecraft/tags/item/{tag}.json"])["values"]
        ), tag
    assert "realmforge:test_cap" not in json.loads(files[f"{DATA}/realmforge/tags/item/wands.json"])["values"]
    assert (
        "realmforge:test_cap"
        not in json.loads(files[f"{DATA}/realmforge/tags/item/two_handed.json"])["values"]
    )


@pytest.mark.parametrize(
    ("gear", "message"),
    [
        (
            "  kind: wand\n  durability: 10\n  attack_damage: 2\n",
            "a wand needs attack_damage and attack_speed",
        ),
        (
            "  kind: wand\n  durability: 10\n  attack_damage: 2\n  attack_speed: 1\n  look: x\n",
            "held, not worn",
        ),
        (
            "  kind: wand\n  durability: 10\n  attack_damage: 2\n  attack_speed: 1\n"
            "  equip_sound: minecraft:item.armor.equip_leather\n",
            "held, not worn",
        ),
        ("  kind: chest\n  durability: 10\n", "chest armor needs a look"),
        ("  kind: head\n  durability: 10\n  look: x\n  two_handed: true\n", "only a wand is two-handed"),
        ("  kind: feet\n  durability: 10\n  look: x\n  attack_speed: 1\n", "are a wand's"),
        ("  kind: head\n  durability: 0\n  look: x\n", "durability"),
        (
            "  kind: head\n  durability: 10\n  look: x\n  modifiers:\n"
            "    - {attribute: realmforge:max_mana, operation: add_value, amount: 1}\n"
            "    - {attribute: realmforge:max_mana, operation: add_value, amount: 2}\n",
            "realmforge:max_mana repeats",
        ),
    ],
)
def test_gear_fields_fit_its_kind(repo: RepoPaths, gear: str, message: str) -> None:
    write(repo.content / "items" / "test_gear.yaml", f"id: test_gear\nname: {{en_us: Gear}}\ngear:\n{gear}")
    problems = problems_for(repo)
    assert any("test_gear.yaml" in p and message in p for p in problems), problems


def test_gear_stacks_to_one(repo: RepoPaths) -> None:
    write(repo.content / "items" / "test_wand.yaml", WAND + "max_stack_size: 16\n")
    assert any("gear stacks to 1" in p for p in problems_for(repo))


@pytest.mark.parametrize(
    ("modifier", "message"),
    [
        ("{attribute: minecraft:max_health, operation: add_value, amount: 2}", "never adds max health"),
        (
            "{attribute: minecraft:attack_damage, operation: add_value, amount: 2}",
            "attack damage is its attack_damage",
        ),
        ("{attribute: realmforge:spell_power, operation: add_value, amount: 2}", "is a share"),
        ("{attribute: realmforge:magic_shield, operation: add_value, amount_percent: 2}", "counts points"),
        (
            "{attribute: realmforge:spel_power, operation: add_value, amount: 2}",
            "unknown id 'realmforge:spel_power'",
        ),
        (
            "{attribute: minecraft:armor, operation: add_multiplied_total, amount_percent: 2}",
            "stat bonuses add their percents",
        ),
    ],
)
def test_gear_modifiers_name_attributes_in_their_unit(repo: RepoPaths, modifier: str, message: str) -> None:
    original = "{attribute: realmforge:magic_shield, operation: add_value, amount: 2}"
    write(repo.content / "items" / "test_cap.yaml", CAP.replace(original, modifier))
    problems = problems_for(repo)
    assert any("test_cap.yaml" in p and "gear.modifiers.0" in p and message in p for p in problems), problems


def test_repair_material_and_equip_sound_exist(repo: RepoPaths) -> None:
    write(
        repo.content / "items" / "test_cap.yaml",
        CAP.replace("minecraft:iron_ingot", "'#minecraft:iron_tool_material'").replace(
            "  look: test_cloth\n", "  look: test_cloth\n  equip_sound: minecraft:item.armor.equip_lether\n"
        ),
    )
    problems = problems_for(repo)
    assert any("gear.repair_with" in p and "#minecraft:iron_tool_materials" in p for p in problems), problems
    assert any("gear.equip_sound" in p and "equip_lether" in p for p in problems), problems


def test_the_items_of_a_look_share_its_colour(repo: RepoPaths) -> None:
    write(repo.content / "items" / "test_cap.yaml", CAP)
    write(
        repo.content / "items" / "test_robes.yaml",
        CAP.replace("test_cap", "test_robes")
        .replace("kind: head", "kind: chest")
        .replace("#2F4FA8", "#8A4FD0"),
    )
    problems = problems_for(repo)
    assert any("test_robes.yaml" in p and "share one colour" in p for p in problems), problems


def test_a_vanilla_mob_drops_an_item_through_a_loot_modifier(repo: RepoPaths) -> None:
    write(repo.content / "items" / "test_dust.yaml", DUST)
    files = generate(repo)
    path = "item_drops/test_dust/minecraft/witch"
    assert json.loads(files[f"{DATA}/realmforge/loot_modifiers/{path}.json"]) == {
        "type": "neoforge:add_table",
        "conditions": [{"condition": "neoforge:loot_table_id", "loot_table_id": "minecraft:entities/witch"}],
        "table": f"realmforge:{path}",
    }
    table = json.loads(files[f"{DATA}/realmforge/loot_table/{path}.json"])
    assert table["type"] == "minecraft:entity"
    functions = table["pools"][0]["entries"][0]["functions"]
    assert functions[0] == {
        "function": "minecraft:set_count",
        "count": {"type": "minecraft:uniform", "min": 0.0, "max": 2.0},
        "add": False,
    }, "0 to 2, as vanilla's witch drops glowstone dust"
    assert functions[1]["function"] == "minecraft:enchanted_count_increase"
    expectations = files[EXPECTATIONS_JAVA].decode()
    assert 'new ItemDrop("minecraft:witch", "minecraft:entities/witch",' in expectations
    assert 'new DropExpectation("realmforge:test_dust", 0, 2, 1.0, 1, 0.0)' in expectations


@pytest.mark.parametrize(
    ("entity", "message"),
    [
        ("realmforge:hewn_chanter", "is ours: list the item in its loot"),
        ("minecraft:wich", "unknown id 'minecraft:wich'"),
        ("minecraft:arrow", "has no loot table"),
    ],
)
def test_dropped_by_names_a_mob_that_is_not_ours(repo: RepoPaths, entity: str, message: str) -> None:
    write(repo.content / "items" / "test_dust.yaml", DUST.replace("minecraft:witch", entity))
    problems = problems_for(repo)
    assert any("test_dust.yaml" in p and "dropped_by.0.entity" in p and message in p for p in problems), (
        problems
    )


def test_dropped_by_names_each_mob_once(repo: RepoPaths) -> None:
    write(
        repo.content / "items" / "test_dust.yaml",
        DUST + "  - {entity: minecraft:witch, count: {min: 1, max: 1}}\n",
    )
    assert any("minecraft:witch repeats" in p for p in problems_for(repo))
