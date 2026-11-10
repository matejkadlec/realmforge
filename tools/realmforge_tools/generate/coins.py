"""Coins (RLM-284, RLM-286): the chain of coins for Java and the QA mod's expectations (the items themselves and
their tooltips: items.py; their lang entries: lang.py).

- ``GeneratedCoins``: every coin of ``content/items/``, smallest first, with its worth in the smallest coin and the
  translation key of its plural, which ``item/Coins`` reads.
- ``GeneratedCoinExpectations``: the coins, how sample amounts split into coins, and the coin drops of sample mobs
  (level, rank, roll) from the curve ``coin_drop`` (schema/coin.py), so GameTests can check that the game agrees.
"""

from __future__ import annotations

from pathlib import Path

from realmforge_tools.content import Content
from realmforge_tools.generate.javagen import constant_name, header, java_file_path, string_literal
from realmforge_tools.project import JAVA_PACKAGE, MOD_ID, QA_JAVA_PACKAGE, RepoPaths
from realmforge_tools.schema.coin import COIN_DROP, coin_chain, coin_drop, plural_key, split
from realmforge_tools.schema.item import ItemDef

GENERATED_PACKAGE = f"{JAVA_PACKAGE}.generated"
QA_GENERATED_PACKAGE = f"{QA_JAVA_PACKAGE}.generated"
# Amounts the split samples take, in the smallest coin: nothing, one coin, the edges of a stack and of each rate,
# mixed amounts, and more of the largest coin than one stack holds.
SPLIT_SAMPLES = (0, 1, 99, 100, 101, 5_000, 9_999, 10_000, 123_456, 1_000_000, 7_654_321, 150_000_000)
# The mob levels the drop samples take: the table of RLM-286 (1, 10, 18, 20, 30, 40, 50, 60) and beyond the cap.
DROP_SAMPLE_LEVELS = (1, 10, 18, 20, 30, 40, 50, 60, 61)
# The ranks: none (the smallest slime), a swarm mob, a normal mob, strong, elite, miniboss, dungeon boss, guardian.
DROP_SAMPLE_RANKS = (0.0, 0.1, 1.0, 1.5, 2.0, 5.0, 10.0, 20.0)
# The rolls: the least, the middle and nearly the most.
DROP_SAMPLE_ROLLS = (0.0, 0.5, 0.999)


def generate(content: Content, paths: RepoPaths) -> dict[Path, bytes]:
    coins = coin_chain(entry.model for entry in content.items)
    curves = {entry.model.id: entry.model for entry in content.curves}
    base = curves.get(COIN_DROP)
    drops = (
        [
            (level, rank, roll, coin_drop(base.value_at(level), rank, roll))
            for level in DROP_SAMPLE_LEVELS
            for rank in DROP_SAMPLE_RANKS
            for roll in DROP_SAMPLE_ROLLS
        ]
        if base is not None
        else []
    )
    return {
        java_file_path(paths.mod_generated / "java", GENERATED_PACKAGE, "GeneratedCoins"): _coins(coins),
        java_file_path(paths.qa_generated / "java", QA_GENERATED_PACKAGE, "GeneratedCoinExpectations"): (
            _expectations(coins, drops)
        ),
    }


def _value(coin: ItemDef) -> int:
    assert coin.coin is not None
    return coin.coin.value


def _coins(coins: list[ItemDef]) -> bytes:
    rows = [
        f"            new Coin(GeneratedItems.{constant_name(coin.id)}, {_value(coin)}L, "
        f"{string_literal(plural_key(coin.id))})"
        for coin in coins
    ]
    source = (
        header(Path(__file__), "content/items/") + f"package {GENERATED_PACKAGE};\n"
        "\n"
        "import java.util.List;\n"
        "import net.minecraft.world.item.Item;\n"
        "import net.neoforged.neoforge.registries.DeferredItem;\n"
        "\n"
        "/** The coins of {@code content/items/} (the items with a {@code coin} block), for {@code item/Coins}. */\n"
        "public final class GeneratedCoins {\n"
        "    /**\n"
        "     * One coin.\n"
        "     *\n"
        "     * @param item its item\n"
        "     * @param value what one is worth, in the smallest coin\n"
        "     * @param pluralKey the translation key of its name for more than one\n"
        "     */\n"
        "    public record Coin(DeferredItem<Item> item, long value, String pluralKey) {\n"
        "    }\n"
        "\n"
        "    /** Every coin, smallest first. */\n"
        f"    public static final List<Coin> COINS = {_list(rows)};\n"
        "\n"
        "    private GeneratedCoins() {\n"
        "    }\n"
        "}\n"
    )
    return source.encode("utf-8")


def _expectations(coins: list[ItemDef], drops: list[tuple[int, float, float, int]]) -> bytes:
    values = [_value(coin) for coin in coins]
    coin_rows = [
        f"            new CoinExpectation({string_literal(f'{MOD_ID}:{coin.id}')}, {_value(coin)}L)"
        for coin in coins
    ]
    split_rows = [
        f"            new SplitSample({amount}L, List.of({', '.join(f'{count}L' for count in split(amount, values))}))"
        for amount in SPLIT_SAMPLES
    ]
    drop_rows = [
        f"            new DropSample({level}, {rank!r}, {roll!r}, {amount}L)"
        for level, rank, roll, amount in drops
    ]
    source = (
        header(Path(__file__), "content/items/ and content/curves/") + f"package {QA_GENERATED_PACKAGE};\n"
        "\n"
        "import java.util.List;\n"
        "\n"
        "/** What the pipeline says about the coins and mob coin drops, so GameTests can check that the game agrees. */\n"
        "public final class GeneratedCoinExpectations {\n"
        "    /** One coin: its item id and its worth in the smallest coin. */\n"
        "    public record CoinExpectation(String id, long value) {\n"
        "    }\n"
        "\n"
        "    /** How {@code amount} of the smallest coin is paid: the count of each coin, smallest first. */\n"
        "    public record SplitSample(long amount, List<Long> counts) {\n"
        "    }\n"
        "\n"
        "    /**\n"
        "     * A mob's coin drop: its level, its rank (kill XP multiplier), the roll between 0 and 1 that picks the\n"
        "     * share between 50 and 150 %, and the amount in the smallest coin.\n"
        "     */\n"
        "    public record DropSample(int level, double rank, double roll, long amount) {\n"
        "    }\n"
        "\n"
        "    /** Every coin, smallest first. */\n"
        f"    public static final List<CoinExpectation> COINS = {_list(coin_rows)};\n"
        "\n"
        f"    public static final List<SplitSample> SPLITS = {_list(split_rows)};\n"
        "\n"
        f"    public static final List<DropSample> DROPS = {_list(drop_rows)};\n"
        "\n"
        "    private GeneratedCoinExpectations() {\n"
        "    }\n"
        "}\n"
    )
    return source.encode("utf-8")


def _list(rows: list[str]) -> str:
    return "List.of(\n" + ",\n".join(rows) + ")" if rows else "List.of()"
