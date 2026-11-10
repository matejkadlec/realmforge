"""Merchants (RLM-285): vanilla trade data, the professions' Java list, placeholder textures and the QA mod's
expectations, from ``content/merchants/`` (the profession names' lang entries: lang.py).

- ``data/realmforge/villager_trade/<merchant>/<nn>_<item>.json``: one offer per trade, its price paid in the largest
  coins (at most two coin types), with ``reputation_discount: 0`` (no demand or reputation changes) and ``xp: 0`` (a
  merchant never levels; the game locks its profession at the first trade).
- ``data/realmforge/tags/villager_trade/<merchant>/level_1.json`` and ``data/realmforge/trade_set/<merchant>/
  level_1.json``: the merchant's one pool, every trade, drawn without duplicates.
- ``data/minecraft/tags/point_of_interest_type/acquirable_job_site.json``: the merchants' job sites, so a jobless
  villager takes them (NeoForge merges it with vanilla's), and ``data/realmforge/tags/villager_profession/
  fixed_price.json``: the professions the game trades at fixed prices.
- ``GeneratedMerchants``: every merchant with its workstation block, work sound and trades in order, from which
  ``mob/merchant/Merchants`` registers the point of interest and the profession (built-in registries, so Java).
- ``assets/realmforge/textures/entity/villager/profession/<merchant>.png``: a placeholder robe until A1's art.
- ``GeneratedMerchantExpectations``: every merchant's trades with their costs, so GameTests can check the game's offers.
"""

from __future__ import annotations

from pathlib import Path

from realmforge_tools.content import Content
from realmforge_tools.formats.jsonfmt import dump_json
from realmforge_tools.generate.datapack import data_dir
from realmforge_tools.generate.javagen import constant_name, header, java_file_path, string_literal
from realmforge_tools.generate.textures import placeholder_profession_texture
from realmforge_tools.project import JAVA_PACKAGE, MOD_ID, QA_JAVA_PACKAGE, RepoPaths
from realmforge_tools.schema.coin import coin_chain, split
from realmforge_tools.schema.item import ItemDef
from realmforge_tools.schema.merchant import (
    ACQUIRABLE_JOB_SITE_TAG,
    FIXED_PRICE_TAG,
    MERCHANT_LEVEL,
    MerchantDef,
    MerchantTradeDef,
    trade_id,
    trade_set_id,
)

GENERATED_PACKAGE = f"{JAVA_PACKAGE}.generated"
QA_GENERATED_PACKAGE = f"{QA_JAVA_PACKAGE}.generated"


def costs(trade: MerchantTradeDef, coins: list[ItemDef]) -> list[tuple[str, int]]:
    """What a trade costs, as (item, count) per cost slot: its ``wants`` as written, or its ``price`` paid in the
    largest coins, the larger coin first. A price that needs more coin types than a trade has cost slots still lists
    them all: validation refuses it."""
    if trade.wants is not None:
        return [(stack.item, stack.count) for stack in trade.wants]
    assert trade.price is not None
    values = [coin.coin.value for coin in coins if coin.coin is not None]
    counts = split(trade.price, values)
    return [
        (f"{MOD_ID}:{coins[index].id}", counts[index])
        for index in range(len(coins) - 1, -1, -1)
        if counts[index] > 0
    ]


def generate(content: Content, paths: RepoPaths) -> dict[Path, bytes]:
    files: dict[Path, bytes] = {}
    data = data_dir(paths)
    coins = coin_chain(entry.model for entry in content.items)
    merchants = [entry.model for entry in content.merchants]
    for merchant in merchants:
        trade_ids = []
        for n, trade in enumerate(merchant.trades, start=1):
            full_id = trade_id(merchant.id, n, trade.gives.item)
            trade_ids.append(full_id)
            files[data / "villager_trade" / f"{full_id.split(':', 1)[1]}.json"] = dump_json(
                _trade(trade, costs(trade, coins))
            )
        set_path = trade_set_id(merchant.id).split(":", 1)[1]
        files[data / "tags" / "villager_trade" / f"{set_path}.json"] = dump_json({"values": trade_ids})
        files[data / "trade_set" / f"{set_path}.json"] = dump_json(
            {
                "amount": len(trade_ids),
                "random_sequence": f"{MOD_ID}:trade_set/{set_path}",
                "trades": f"#{MOD_ID}:{set_path}",
            }
        )
        texture = paths.mod_generated / "resources" / "assets" / MOD_ID / "textures" / "entity" / "villager"
        files[texture / "profession" / f"{merchant.id}.png"] = placeholder_profession_texture(
            f"{MOD_ID}:{merchant.id}"
        )
    if merchants:
        ids = sorted(f"{MOD_ID}:{merchant.id}" for merchant in merchants)
        files[_tag_file(paths, ACQUIRABLE_JOB_SITE_TAG, "point_of_interest_type")] = dump_json(
            {"values": ids}
        )
        files[_tag_file(paths, FIXED_PRICE_TAG, "villager_profession")] = dump_json({"values": ids})
    files[java_file_path(paths.mod_generated / "java", GENERATED_PACKAGE, "GeneratedMerchants")] = _java(
        content, merchants
    )
    files[
        java_file_path(paths.qa_generated / "java", QA_GENERATED_PACKAGE, "GeneratedMerchantExpectations")
    ] = _expectations(merchants, coins)
    return files


def _tag_file(paths: RepoPaths, tag: str, registry_dir: str) -> Path:
    namespace, path = tag.split(":", 1)
    return paths.mod_generated / "resources" / "data" / namespace / "tags" / registry_dir / f"{path}.json"


def _trade(trade: MerchantTradeDef, cost: list[tuple[str, int]]) -> dict[str, object]:
    """One ``villager_trade`` file. ``reputation_discount: 0`` is the offer's price multiplier, which scales demand and
    reputation: nothing changes the price. ``xp: 0``: the villager never levels."""
    data: dict[str, object] = {"wants": _cost(*cost[0])}
    if len(cost) > 1:
        data["additional_wants"] = _cost(*cost[1])
    gives: dict[str, object] = {"id": trade.gives.item}
    if trade.gives.count != 1:
        gives["count"] = trade.gives.count
    data["gives"] = gives
    data["max_uses"] = trade.max_uses
    data["reputation_discount"] = 0
    data["xp"] = 0
    return data


def _cost(item: str, count: int) -> dict[str, object]:
    return {"id": item, "count": count} if count != 1 else {"id": item}


def _java(content: Content, merchants: list[MerchantDef]) -> bytes:
    blocks = {f"{MOD_ID}:{entry.model.id}": entry.model.id for entry in content.blocks}
    rows = []
    for merchant in merchants:
        trades = ", ".join(
            f"new Trade({string_literal(trade.gives.item)}, {trade.gives.count})" for trade in merchant.trades
        )
        # Validation makes the workstation one of our blocks; an unknown one would not compile, which is the point.
        block = constant_name(blocks.get(merchant.workstation, merchant.workstation.split(":", 1)[1]))
        rows.append(
            f"            new Merchant({string_literal(merchant.id)}, GeneratedBlocks.{block}, "
            f"{string_literal(merchant.work_sound or '')},\n"
            f"                    List.of({trades}))"
        )
    source = (
        header(Path(__file__), "content/merchants/") + f"package {GENERATED_PACKAGE};\n"
        "\n"
        "import java.util.List;\n"
        "import net.minecraft.world.level.block.Block;\n"
        "import net.neoforged.neoforge.registries.DeferredBlock;\n"
        "\n"
        "/** The merchants of {@code content/merchants/}, for {@code mob/merchant/Merchants}. */\n"
        "public final class GeneratedMerchants {\n"
        "    /**\n"
        "     * One merchant, a villager profession of ours.\n"
        "     *\n"
        "     * @param id its id: the profession's, its point of interest's and its trade set's path\n"
        "     * @param workstation the block that is its job site\n"
        "     * @param workSound the sound event of its work, empty for none\n"
        "     * @param trades what each trade gives, in the order the trade screen lists them\n"
        "     */\n"
        "    public record Merchant(String id, DeferredBlock<Block> workstation, String workSound, List<Trade> trades) {\n"
        "    }\n"
        "\n"
        "    /** What a trade gives: the item's id and how many. One trade per such stack and merchant. */\n"
        "    public record Trade(String gives, int count) {\n"
        "    }\n"
        "\n"
        f"    /** The villager level the merchants' trades sit at: they have no other. */\n"
        f"    public static final int LEVEL = {MERCHANT_LEVEL};\n"
        "\n"
        "    /** Every merchant, in id order. */\n"
        f"    public static final List<Merchant> MERCHANTS = {_list(rows)};\n"
        "\n"
        "    private GeneratedMerchants() {\n"
        "    }\n"
        "}\n"
    )
    return source.encode("utf-8")


def _expectations(merchants: list[MerchantDef], coins: list[ItemDef]) -> bytes:
    rows = []
    for merchant in merchants:
        trades = []
        for trade in merchant.trades:
            cost = ", ".join(
                f"new Stack({string_literal(item)}, {count})" for item, count in costs(trade, coins)
            )
            trades.append(
                f"                            new TradeExpectation(new Stack({string_literal(trade.gives.item)}, "
                f"{trade.gives.count}),\n"
                f"                                    List.of({cost}), {trade.max_uses})"
            )
        rows.append(
            f"            new MerchantExpectation({string_literal(f'{MOD_ID}:{merchant.id}')}, "
            f"{string_literal(merchant.workstation)},\n"
            f"                    {string_literal(trade_set_id(merchant.id))},\n"
            "                    List.of(\n" + ",\n".join(trades) + "))"
        )
    source = (
        header(Path(__file__), "content/merchants/ and content/items/") + f"package {QA_GENERATED_PACKAGE};\n"
        "\n"
        "import java.util.List;\n"
        "\n"
        "/** What {@code content/merchants/} says about every merchant, so GameTests can check the game's offers. */\n"
        "public final class GeneratedMerchantExpectations {\n"
        "    /** An item id and a count. */\n"
        "    public record Stack(String item, int count) {\n"
        "    }\n"
        "\n"
        "    /** One trade: what it gives, its one or two costs in the order of the cost slots, and its uses. */\n"
        "    public record TradeExpectation(Stack gives, List<Stack> costs, int maxUses) {\n"
        "    }\n"
        "\n"
        "    /** One merchant: its profession's id, its workstation block, its trade set and its trades in order. */\n"
        "    public record MerchantExpectation(String id, String workstation, String tradeSet,\n"
        "            List<TradeExpectation> trades) {\n"
        "    }\n"
        "\n"
        f"    public static final List<MerchantExpectation> MERCHANTS = {_list(rows)};\n"
        "\n"
        "    private GeneratedMerchantExpectations() {\n"
        "    }\n"
        "}\n"
    )
    return source.encode("utf-8")


def _list(rows: list[str]) -> str:
    return "List.of(\n" + ",\n".join(rows) + ")" if rows else "List.of()"
