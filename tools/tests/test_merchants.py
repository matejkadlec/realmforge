"""Merchants (RLM-285): ``content/merchants/`` (schema/merchant.py), the vanilla trade data, tags, Java list and
placeholder textures the generator writes (generate/merchants.py), and validation's refusals."""

from __future__ import annotations

import json

import pytest

from realmforge_tools.formats.png import PNG_SIGNATURE
from realmforge_tools.generate import generate_all
from realmforge_tools.problems import ValidationFailed
from realmforge_tools.project import RepoPaths
from realmforge_tools.validate import load_and_validate

from .conftest import write

DATA = "mod/src/generated/resources/data"
ASSETS = "mod/src/generated/resources/assets/realmforge"
MERCHANTS_JAVA = "mod/src/generated/java/io/github/matejkadlec/realmforge/generated/GeneratedMerchants.java"
EXPECTATIONS_JAVA = "qa-mod/src/generated/java/io/github/matejkadlec/realmforge/qa/generated/GeneratedMerchantExpectations.java"
STALL = """\
id: test_stall
name: {en_us: Test Stall}
hardness: 2
tool: axe
"""
MERCHANT = """\
id: test_merchant
name: {en_us: Test Merchant}
workstation: realmforge:test_stall
work_sound: minecraft:entity.villager.work_mason
trades:
  - gives: {item: minecraft:netherrack, count: 64}
    price: 20
  - gives: {item: minecraft:blaze_rod, count: 8}
    price: 15000
    max_uses: 3
  - gives: {item: realmforge:silver_coin}
    wants:
      - {item: minecraft:wheat, count: 20}
      - {item: minecraft:carrot, count: 5}
"""


def merchant(trades: str, head: str = "") -> str:
    return (
        "id: test_merchant\nname: {en_us: Test Merchant}\nworkstation: realmforge:test_stall\n"
        + head
        + "trades:\n"
        + trades
    )


def setup(repo: RepoPaths, text: str = MERCHANT) -> None:
    write(repo.content / "blocks" / "test_stall.yaml", STALL)
    write(repo.content / "merchants" / "test_merchant.yaml", text)


def problems_for(repo: RepoPaths) -> list[str]:
    with pytest.raises(ValidationFailed) as excinfo:
        load_and_validate(repo)
    return [str(p) for p in excinfo.value.problems]


def generate(repo: RepoPaths) -> dict[str, bytes]:
    content, _ = load_and_validate(repo)
    return {repo.rel(path): data for path, data in generate_all(content, repo).items()}


def test_a_trade_becomes_vanilla_trade_data_at_a_fixed_price(repo: RepoPaths) -> None:
    setup(repo)
    files = generate(repo)
    trades = f"{DATA}/realmforge/villager_trade/test_merchant"
    assert json.loads(files[f"{trades}/01_netherrack.json"]) == {
        "wants": {"id": "realmforge:copper_coin", "count": 20},
        "gives": {"id": "minecraft:netherrack", "count": 64},
        "max_uses": 12,
        "reputation_discount": 0,
        "xp": 0,
    }, "20 copper, vanilla's default 12 uses, no reputation discount, no villager XP"
    assert json.loads(files[f"{trades}/02_blaze_rod.json"]) == {
        "wants": {"id": "realmforge:gold_coin"},
        "additional_wants": {"id": "realmforge:silver_coin", "count": 50},
        "gives": {"id": "minecraft:blaze_rod", "count": 8},
        "max_uses": 3,
        "reputation_discount": 0,
        "xp": 0,
    }, "15000 copper is 1 gold and 50 silver, the larger coin first; a count of 1 is left out"


def test_a_trade_may_want_items_instead_of_coins(repo: RepoPaths) -> None:
    setup(repo)
    files = generate(repo)
    assert json.loads(files[f"{DATA}/realmforge/villager_trade/test_merchant/03_silver_coin.json"]) == {
        "wants": {"id": "minecraft:wheat", "count": 20},
        "additional_wants": {"id": "minecraft:carrot", "count": 5},
        "gives": {"id": "realmforge:silver_coin"},
        "max_uses": 12,
        "reputation_discount": 0,
        "xp": 0,
    }


def test_a_merchant_has_one_trade_set_with_every_trade(repo: RepoPaths) -> None:
    setup(repo)
    files = generate(repo)
    assert json.loads(files[f"{DATA}/realmforge/tags/villager_trade/test_merchant/level_1.json"]) == {
        "values": [
            "realmforge:test_merchant/01_netherrack",
            "realmforge:test_merchant/02_blaze_rod",
            "realmforge:test_merchant/03_silver_coin",
        ]
    }, "in the written order"
    assert json.loads(files[f"{DATA}/realmforge/trade_set/test_merchant/level_1.json"]) == {
        "amount": 3,
        "random_sequence": "realmforge:trade_set/test_merchant/level_1",
        "trades": "#realmforge:test_merchant/level_1",
    }, "every trade is drawn, none twice (allow_duplicates is false by default)"
    assert not any("/trade_set/test_merchant/level_2" in path for path in files), "a merchant has no levels"


def test_merchants_join_the_job_site_and_fixed_price_tags(repo: RepoPaths) -> None:
    setup(repo)
    files = generate(repo)
    job_sites = json.loads(files[f"{DATA}/minecraft/tags/point_of_interest_type/acquirable_job_site.json"])
    assert job_sites == {
        "values": ["realmforge:ancient_builder", "realmforge:ancient_specialist", "realmforge:test_merchant"]
    }, "without replace: NeoForge merges it with vanilla's job sites"
    fixed = json.loads(files[f"{DATA}/realmforge/tags/villager_profession/fixed_price.json"])
    assert "realmforge:test_merchant" in fixed["values"]


def test_the_profession_reaches_java_with_its_workstation_and_trades(repo: RepoPaths) -> None:
    setup(repo)
    files = generate(repo)
    java = files[MERCHANTS_JAVA].decode()
    assert (
        'new Merchant("test_merchant", GeneratedBlocks.TEST_STALL, "minecraft:entity.villager.work_mason",'
        in java
    )
    assert (
        'List.of(new Trade("minecraft:netherrack", 64), new Trade("minecraft:blaze_rod", 8), '
        'new Trade("realmforge:silver_coin", 1))' in java
    ), "what each trade gives, in the written order: the game sorts the offers by it"
    assert "public static final int LEVEL = 1;" in java
    expectations = files[EXPECTATIONS_JAVA].decode()
    assert 'new MerchantExpectation("realmforge:test_merchant", "realmforge:test_stall",' in expectations
    assert (
        'List.of(new Stack("realmforge:gold_coin", 1), new Stack("realmforge:silver_coin", 50)), 3)'
        in expectations
    )
    lang = json.loads(files[f"{ASSETS}/lang/en_us.json"])
    assert lang["entity.realmforge.villager.test_merchant"] == "Test Merchant", (
        "vanilla's key for a profession"
    )


def test_a_profession_gets_a_placeholder_robe(repo: RepoPaths) -> None:
    setup(repo)
    files = generate(repo)
    texture = files[f"{ASSETS}/textures/entity/villager/profession/test_merchant.png"]
    assert texture.startswith(PNG_SIGNATURE)
    other = files[f"{ASSETS}/textures/entity/villager/profession/ancient_builder.png"]
    assert texture != other, "coloured by a hash of the profession's id"
    assert generate(repo)[f"{ASSETS}/textures/entity/villager/profession/test_merchant.png"] == texture


def test_the_shipped_merchants_sell_matejs_lists(repo: RepoPaths) -> None:
    content, _ = load_and_validate(repo)
    merchants = {entry.model.id: entry.model for entry in content.merchants}
    assert sorted(merchants) == ["ancient_builder", "ancient_specialist"]
    builder = [(trade.gives.item, trade.gives.count) for trade in merchants["ancient_builder"].trades]
    assert builder == [
        ("minecraft:netherrack", 64),
        ("minecraft:nether_bricks", 64),
        ("minecraft:basalt", 64),
        ("minecraft:blackstone", 64),
        ("minecraft:soul_sand", 16),
        ("minecraft:soul_soil", 16),
        ("minecraft:magma_block", 16),
        ("minecraft:glowstone", 8),
        ("minecraft:nether_quartz_ore", 8),
        ("minecraft:crying_obsidian", 4),
    ], "RLM-241's table"
    specialist = [(trade.gives.item, trade.gives.count) for trade in merchants["ancient_specialist"].trades]
    assert specialist == [
        ("minecraft:crimson_nylium", 8),
        ("minecraft:crimson_fungus", 1),
        ("minecraft:warped_nylium", 8),
        ("minecraft:warped_fungus", 1),
        ("minecraft:nether_wart", 8),
        ("minecraft:blaze_rod", 8),
        ("minecraft:ender_pearl", 1),
        ("minecraft:ghast_tear", 1),
        ("minecraft:wither_skeleton_skull", 1),
        ("minecraft:netherite_scrap", 1),
    ], "RLM-241's table"
    assert merchants["ancient_builder"].workstation == "realmforge:ancient_casting"
    assert merchants["ancient_specialist"].workstation == "realmforge:ancient_workbench"
    for merchant_def in merchants.values():
        assert all(trade.price is not None for trade in merchant_def.trades), "every trade is paid in coins"


def test_a_price_mixes_at_most_two_coin_types(repo: RepoPaths) -> None:
    setup(repo, merchant("  - gives: {item: minecraft:netherrack}\n    price: 10203\n"))
    problems = problems_for(repo)
    assert any(
        "trades.0.price" in p and "1 gold_coin + 2 silver_coin + 3 copper_coin" in p and "2 cost slots" in p
        for p in problems
    ), problems


def test_a_price_is_at_most_a_stack_of_the_largest_coin(repo: RepoPaths) -> None:
    setup(repo, merchant("  - gives: {item: minecraft:netherrack}\n    price: 100000000\n"))
    problems = problems_for(repo)
    assert any("needs 100 platinum_coin, more than a stack (99)" in p for p in problems), problems


def test_a_trade_has_either_a_price_or_wants(repo: RepoPaths) -> None:
    setup(repo, merchant("  - gives: {item: minecraft:netherrack}\n"))
    assert any("either a price (in coins) or wants" in p for p in problems_for(repo))
    setup(
        repo,
        merchant(
            "  - gives: {item: minecraft:netherrack}\n    price: 5\n    wants: [{item: minecraft:wheat}]\n"
        ),
    )
    assert any("either a price (in coins) or wants" in p for p in problems_for(repo))


def test_trade_items_exist_and_fit_a_stack(repo: RepoPaths) -> None:
    setup(
        repo,
        merchant(
            "  - gives: {item: minecraft:nether_rack}\n    price: 5\n"
            "  - gives: {item: minecraft:netherrack, count: 65}\n    price: 5\n"
            "  - gives: {item: realmforge:copper_coin, count: 99}\n    wants: [{item: minecraft:whet}]\n"
        ),
    )
    problems = problems_for(repo)
    assert any("trades.0.gives.item" in p and "minecraft:nether_rack" in p for p in problems), problems
    assert any(
        "trades.1.gives.count" in p and "more than a stack of minecraft:netherrack (64)" in p
        for p in problems
    )
    assert any("trades.2.wants.0.item" in p for p in problems)
    assert not any("trades.2.gives" in p for p in problems), "a coin stacks to 99"


def test_one_trade_per_stack_it_gives(repo: RepoPaths) -> None:
    setup(
        repo,
        merchant(
            "  - gives: {item: minecraft:netherrack, count: 8}\n    price: 5\n"
            "  - gives: {item: minecraft:netherrack, count: 8}\n    price: 9\n"
            "  - gives: {item: minecraft:netherrack, count: 64}\n    price: 20\n"
        ),
    )
    problems = problems_for(repo)
    assert any("trades.1.gives" in p and "one trade per stack" in p for p in problems), problems
    assert not any("trades.2.gives" in p for p in problems), "another count is another trade"


def test_a_workstation_is_a_block_of_ours_and_one_merchants(repo: RepoPaths) -> None:
    setup(repo, MERCHANT.replace("realmforge:test_stall", "minecraft:smithing_table"))
    assert any(
        "workstation" in p and "minecraft:smithing_table is not a block of ours" in p
        for p in problems_for(repo)
    )
    setup(repo, MERCHANT.replace("realmforge:test_stall", "realmforge:ancient_casting"))
    assert any(
        "workstation" in p and "realmforge:ancient_casting is already the job site of" in p
        for p in problems_for(repo)
    )
    setup(repo, MERCHANT.replace("realmforge:test_stall", "realmforge:realm_portal"))
    assert any("workstation" in p and "has no item" in p for p in problems_for(repo))


def test_a_work_sound_exists(repo: RepoPaths) -> None:
    setup(repo, MERCHANT.replace("work_mason", "work_wizard"))
    assert any("work_sound" in p and "minecraft:entity.villager.work_wizard" in p for p in problems_for(repo))


def test_a_merchant_sells_something(repo: RepoPaths) -> None:
    write(repo.content / "blocks" / "test_stall.yaml", STALL)
    write(
        repo.content / "merchants" / "test_merchant.yaml",
        "id: test_merchant\nname: {en_us: Test Merchant}\nworkstation: realmforge:test_stall\ntrades: []\n",
    )
    assert any("trades" in p for p in problems_for(repo))
