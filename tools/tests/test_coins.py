"""Coins (RLM-284, RLM-286): the ``coin`` block on items (schema/item.py), the chain and its math (schema/coin.py),
what the generators write (generate/items.py, generate/coins.py, generate/lang.py) and validation."""

from __future__ import annotations

import json
import math

import pytest

from realmforge_tools.generate import generate_all
from realmforge_tools.problems import ValidationFailed
from realmforge_tools.project import RepoPaths
from realmforge_tools.schema.coin import COIN_DROP, coin_chain, coin_drop, plural_key, split
from realmforge_tools.validate import load_and_validate

from .conftest import write

GENERATED = "mod/src/generated/java/io/github/matejkadlec/realmforge/generated"
ITEMS_JAVA = f"{GENERATED}/GeneratedItems.java"
COINS_JAVA = f"{GENERATED}/GeneratedCoins.java"
EXPECTATIONS_JAVA = (
    "qa-mod/src/generated/java/io/github/matejkadlec/realmforge/qa/generated/GeneratedCoinExpectations.java"
)
ASSETS = "mod/src/generated/resources/assets/realmforge"
DATA = "mod/src/generated/resources/data"
CHAIN = [1, 100, 10_000, 1_000_000]


def coin_yaml(item_id: str, value: int, extra: str = "") -> str:
    name = item_id.replace("_", " ").title()
    return (
        f"id: {item_id}\nname: {{en_us: {name}}}\nmax_stack_size: 99\n"
        f"coin:\n  value: {value}\n  plural: {{en_us: {name}s}}\n{extra}"
    )


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


def test_the_four_coins_form_a_chain_of_hundreds(repo: RepoPaths) -> None:
    content, _ = load_and_validate(repo)
    coins = coin_chain(entry.model for entry in content.items)
    assert [coin.id for coin in coins] == ["copper_coin", "silver_coin", "gold_coin", "platinum_coin"]
    assert [coin.coin.value for coin in coins if coin.coin] == CHAIN
    assert all(coin.max_stack_size == 99 for coin in coins), "coins stack to 99 (ADR 0040)"


def test_an_amount_is_paid_in_the_largest_coins() -> None:
    assert split(123_456, CHAIN) == [56, 34, 12, 0]
    assert split(7_654_321, CHAIN) == [21, 43, 65, 7]
    assert split(150_000_000, CHAIN) == [0, 0, 0, 150], "the largest coin takes whatever is left above"
    assert split(0, CHAIN) == [0, 0, 0, 0]
    assert split(-5, CHAIN) == [0, 0, 0, 0]


def test_a_drop_is_half_to_one_and_a_half_of_the_base_times_the_rank() -> None:
    assert coin_drop(10_000, 1, 0) == 5_000
    assert coin_drop(10_000, 1, 0.5) == 10_000
    assert coin_drop(10_000, 1, 0.999) == 14_990
    assert coin_drop(10_000, 20, 0.5) == 200_000, "a realm guardian's rank"
    assert coin_drop(1.0, 1, 0) == coin_drop(1.0, 1, 0.999) == 1, "a level 1 mob always drops 1 copper"
    assert coin_drop(1.0, 0.1, 0) == 1, "at least 1 copper"
    assert coin_drop(10_000, 0, 0.5) == 0, "a mob of rank 0 drops nothing"
    assert coin_drop(0, 1, 0.5) == 0
    assert coin_drop(5, 1, 0) == 3, "halves round up, as Java's Math.round"


def test_the_drop_curve_follows_matejs_formula(repo: RepoPaths) -> None:
    content, _ = load_and_validate(repo)
    curve = next(entry.model for entry in content.curves if entry.model.id == COIN_DROP)
    assert [point.level for point in curve.points] == list(range(1, 61)), "a point per level: the curve bends"
    for point in curve.points:
        level = point.level
        formula = math.exp(-0.2109 + 0.2113 * level - 0.000458 * level * level)
        assert point.value == pytest.approx(formula, rel=0.005), f"level {level}"
    assert curve.value_at(1) == pytest.approx(1, abs=0.01), "about 1 copper at level 1"
    assert curve.value_at(50) == pytest.approx(10_000, rel=0.01), "about 1 gold at level 50"
    assert curve.value_at(60) == pytest.approx(50_000, rel=0.01), "about 5 gold at level 60"
    assert curve.value_at(75) == curve.value_at(60), "constant beyond the cap"


def test_coins_join_their_tag_and_get_their_tooltip_lines(repo: RepoPaths) -> None:
    files = generate(repo)
    assert json.loads(files[f"{DATA}/realmforge/tags/item/coins.json"])["values"] == [
        "realmforge:copper_coin",
        "realmforge:gold_coin",
        "realmforge:platinum_coin",
        "realmforge:silver_coin",
    ]
    java = files[ITEMS_JAVA].decode()
    silver = registration(java, "silver_coin")
    assert ".stacksTo(99)" in silver
    assert 'Component.translatable("tooltip.realmforge.coin_merge", 100,' in silver
    assert 'Component.translatable("item.realmforge.silver_coin.plural")' in silver
    assert 'Component.translatable("item.realmforge.gold_coin"))' in silver, "it merges into gold"
    assert (
        'Component.translatable("tooltip.realmforge.coin_break", Component.keybind("key.sneak"),' in silver
    ), "the sneak key is the player's own binding"
    assert '100, Component.translatable("item.realmforge.copper_coin.plural"))' in silver, (
        "it breaks into copper"
    )
    assert silver.index("coin_merge") < silver.index("coin_break") < silver.index("silver_coin.flavor")
    copper = registration(java, "copper_coin")
    assert "coin_merge" in copper and "coin_break" not in copper, "the smallest coin does not break"
    platinum = registration(java, "platinum_coin")
    assert "coin_break" in platinum and "coin_merge" not in platinum, "the largest coin does not merge"
    lang = json.loads(files[f"{ASSETS}/lang/en_us.json"])
    assert lang[plural_key("copper_coin")] == "Copper Coins"
    assert lang["item.realmforge.platinum_coin"] == "Platinum Coin"
    assert "%1$s" in lang["tooltip.realmforge.coin_merge"] and "%3$s" in lang["tooltip.realmforge.coin_merge"]


def test_the_chain_reaches_java_smallest_first_with_the_qa_samples(repo: RepoPaths) -> None:
    files = generate(repo)
    coins = files[COINS_JAVA].decode()
    rows = [line.strip() for line in coins.splitlines() if line.strip().startswith("new Coin(")]
    assert rows == [
        'new Coin(GeneratedItems.COPPER_COIN, 1L, "item.realmforge.copper_coin.plural"),',
        'new Coin(GeneratedItems.SILVER_COIN, 100L, "item.realmforge.silver_coin.plural"),',
        'new Coin(GeneratedItems.GOLD_COIN, 10000L, "item.realmforge.gold_coin.plural"),',
        'new Coin(GeneratedItems.PLATINUM_COIN, 1000000L, "item.realmforge.platinum_coin.plural"));',
    ]
    expectations = files[EXPECTATIONS_JAVA].decode()
    assert 'new CoinExpectation("realmforge:gold_coin", 10000L)' in expectations
    assert "new SplitSample(123456L, List.of(56L, 34L, 12L, 0L))" in expectations
    assert "new DropSample(1, 1.0, 0.0, 1L)" in expectations, "a level 1 mob drops 1 copper"
    assert "new DropSample(50, 1.0, 0.5, 9988L)" in expectations, "about 1 gold at level 50"
    assert "new DropSample(60, 20.0, 0.5, 998520L)" in expectations, "a level 60 realm guardian"
    assert "new DropSample(61, 1.0, 0.5, 49926L)" in expectations, "level 61 reads the curve's last point"
    assert "new DropSample(30, 0.0, 0.5, 0L)" in expectations, "rank 0 drops nothing"


def test_a_new_coin_joins_the_chain_by_its_value(repo: RepoPaths) -> None:
    write(repo.content / "items" / "test_coin.yaml", coin_yaml("test_coin", 100_000_000))
    files = generate(repo)
    java = files[ITEMS_JAVA].decode()
    assert 'Component.translatable("item.realmforge.test_coin"))' in registration(java, "platinum_coin")
    test_coin = registration(java, "test_coin")
    assert (
        'Component.translatable("tooltip.realmforge.coin_break", Component.keybind("key.sneak"),' in test_coin
    )
    assert '100, Component.translatable("item.realmforge.platinum_coin.plural"))' in test_coin
    assert "new Coin(GeneratedItems.TEST_COIN, 100000000L" in files[COINS_JAVA].decode()


def test_the_smallest_coin_is_worth_one(repo: RepoPaths) -> None:
    write(repo.content / "items" / "copper_coin.yaml", coin_yaml("copper_coin", 5))
    assert any("the smallest coin is worth 1, not 5" in problem for problem in problems_for(repo))


def test_two_coins_never_share_a_value(repo: RepoPaths) -> None:
    write(repo.content / "items" / "test_coin.yaml", coin_yaml("test_coin", 100))
    assert any("is worth 100 too: one coin per value" in problem for problem in problems_for(repo))


def test_a_coin_is_a_whole_multiple_of_the_coin_below(repo: RepoPaths) -> None:
    write(repo.content / "items" / "test_coin.yaml", coin_yaml("test_coin", 150))
    problems = problems_for(repo)
    assert any("150 is no whole multiple of 100" in problem for problem in problems)
    assert any("10000 is no whole multiple of 150" in problem for problem in problems), (
        "gold sits above it now"
    )


def test_a_coin_has_no_use_and_is_no_gear(repo: RepoPaths) -> None:
    write(
        repo.content / "items" / "test_coin.yaml",
        coin_yaml("test_coin", 100_000_000, "use: unlearn_stat_points\n"),
    )
    assert any("a coin is neither gear nor an item with a use" in problem for problem in problems_for(repo))


def test_a_coin_needs_its_value_and_plural(repo: RepoPaths) -> None:
    write(
        repo.content / "items" / "test_coin.yaml",
        "id: test_coin\nname: {en_us: Test Coin}\ncoin:\n  value: 0\n",
    )
    problems = problems_for(repo)
    assert any("coin.value" in problem for problem in problems), "a coin is worth at least 1"
    assert any("coin.plural" in problem for problem in problems)


def test_mob_coin_drops_need_their_curve(repo: RepoPaths) -> None:
    (repo.content / "curves" / "coin_drop.yaml").unlink()
    assert any(
        "content/curves/coin_drop.yaml" in problem and "mob coin drops need this curve" in problem
        for problem in problems_for(repo)
    )


def test_the_drop_curve_is_never_negative(repo: RepoPaths) -> None:
    write(
        repo.content / "curves" / "coin_drop.yaml",
        "id: coin_drop\npoints:\n  - {level: 1, value: 1}\n  - {level: 2, value: -3}\n",
    )
    assert any("points.1.value" in problem and "is negative" in problem for problem in problems_for(repo))


def test_the_coins_tooltips_need_their_texts(repo: RepoPaths) -> None:
    tooltip = repo.content / "texts" / "tooltip.yaml"
    text = tooltip.read_text(encoding="utf-8")
    start = text.index("  coin_break:")
    tooltip.write_text(text[:start].rstrip() + "\n", encoding="utf-8")
    assert any(
        "texts.coin_break" in problem and "the coins' tooltips need this line" in problem
        for problem in problems_for(repo)
    )
