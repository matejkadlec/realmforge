"""content/container_loot/: container loot by loot points (RLM-289; ADR 0041, the container loot design).

The tests read the generated loot tables, check the validation's refusals, and simulate both tables at every world
tier as the game rolls them (``schema/container_loot.simulate``), against Matěj's kill-equivalents: a normal
container is worth about 2, 4 and 10 mob kills (the least, the roll in the middle, a high roll), a treasure room's
chest about 5, 10 and 20.
"""

from __future__ import annotations

import json
import random
import statistics
from typing import Any

import pytest

from realmforge_tools.generate import generate_all
from realmforge_tools.problems import ValidationFailed
from realmforge_tools.project import RepoPaths
from realmforge_tools.schema.coin import COIN_DROP
from realmforge_tools.schema.container_loot import (
    CONTAINER_SLOTS,
    MAX_PICKS,
    TIERS,
    Choice,
    CoinsOptionDef,
    ContainerLootDef,
    ItemOptionDef,
    choices_at,
    coins_range,
    loot_table_id,
    roll,
    simulate,
)
from realmforge_tools.validate import load_and_validate

TABLES = "mod/src/generated/resources/data/realmforge/loot_table/containers"
EXPECTATIONS = (
    "qa-mod/src/generated/java/io/github/matejkadlec/realmforge/qa/generated/"
    "GeneratedContainerLootExpectations.java"
)
ROLLS = 4000
# Matěj's kill-equivalents (ADR 0041): the least, the roll in the middle and a high roll, at the first world tier.
KILLS = {"normal": (2, 4, 10), "treasure": (5, 10, 20)}


def tables(paths: RepoPaths) -> dict[str, ContainerLootDef]:
    content, _ = load_and_validate(paths)
    return {entry.model.id: entry.model for entry in content.container_loot}


def generated(paths: RepoPaths) -> dict[str, bytes]:
    content, _ = load_and_validate(paths)
    return {paths.rel(path): data for path, data in generate_all(content, paths).items()}


def problems_for(repo: RepoPaths) -> list[str]:
    with pytest.raises(ValidationFailed) as excinfo:
        load_and_validate(repo)
    return [str(p) for p in excinfo.value.problems]


def edit(repo: RepoPaths, table: str, old: str, new: str) -> None:
    path = repo.content / "container_loot" / f"{table}.yaml"
    text = path.read_text(encoding="utf-8")
    assert old in text, old
    path.write_text(text.replace(old, new), encoding="utf-8")


# ---- The generated loot tables ----


def test_the_repository_has_the_normal_and_the_treasure_table() -> None:
    assert sorted(tables(RepoPaths())) == ["normal", "treasure"]


def test_a_table_is_a_loot_table_with_one_budget_entry() -> None:
    paths = RepoPaths()
    files = generated(paths)
    table = tables(paths)["normal"]
    data = json.loads(files[f"{TABLES}/normal.json"])
    assert data["type"] == "minecraft:chest"
    assert data["random_sequence"] == loot_table_id("normal") == "realmforge:containers/normal"
    # Outside chests/: Apotheosis injects its affix items and gems into chests/* tables only.
    assert "chests/" not in data["random_sequence"]
    assert len(data["pools"]) == 1 and len(data["pools"][0]["entries"]) == 1
    entry = data["pools"][0]["entries"][0]
    assert entry["type"] == "realmforge:budget"
    assert entry["budget"] == {"min": 300, "medium": 600, "max": 1500}
    assert entry["tier_percent"] == {tier: table.tiers[tier].budget_percent for tier in TIERS}
    assert list(entry["tier_percent"]) == list(TIERS)
    assert entry["luck_percent"] == 5
    # Every option of the game costs points and rolls one loot entry.
    assert all(
        option["cost"] >= 1 and option["weight"] >= 1 and "entry" in option for option in entry["options"]
    )


def test_coins_are_one_option_per_tier_worth_their_kills() -> None:
    paths = RepoPaths()
    content, _ = load_and_validate(paths)
    coin_drop = next(entry.model for entry in content.curves if entry.model.id == COIN_DROP)
    table = tables(paths)["normal"]
    entry = json.loads(generated(paths)[f"{TABLES}/normal.json"])["pools"][0]["entries"][0]
    money = [option for option in entry["options"] if option["entry"]["type"] == "realmforge:money"]
    coins = [option for option in table.options if isinstance(option, CoinsOptionDef)]
    assert len(money) == len(coins) * len(TIERS)
    first = coins[0]
    assert first.kills == 1 and table.cost(first) == table.kill_points == 150
    for tier, option in zip(TIERS, money[: len(TIERS)], strict=True):
        assert option["tiers"] == [tier] and option["cost"] == 150 and option["weight"] == first.weight
        least, most = coins_range(coin_drop, table, first, tier)
        assert (option["entry"]["min"], option["entry"]["max"]) == (least, most)
        base = coin_drop.value_at(table.tiers[tier].level)
        # 50 to 150 % of a kill's coins, as a mob's drop is; at least 1 copper.
        assert least == max(1, int(base * 0.5 + 0.5)) and most == max(least, int(base * 1.5 + 0.5))
    # A kill's coins grow with the tier's level: a later tier's container pays more for the same points.
    mosts = [option["entry"]["max"] for option in money[: len(TIERS)]]
    assert mosts == sorted(mosts) and mosts[-1] > 1000 * mosts[0]
    # Three kills' coins cost three kills.
    assert table.cost(coins[1]) == 450


def test_items_gems_and_affix_items_become_loot_entries() -> None:
    entry = json.loads(generated(RepoPaths())[f"{TABLES}/treasure.json"])["pools"][0]["entries"][0]
    by_name: dict[str, dict[str, Any]] = {}
    for option in entry["options"]:
        inner = option["entry"]
        by_name.setdefault(inner.get("name", inner["type"]), option)
    carrot = by_name["minecraft:golden_carrot"]
    assert carrot["entry"] == {
        "type": "minecraft:item",
        "name": "minecraft:golden_carrot",
        "functions": [
            {
                "function": "minecraft:set_count",
                "count": {"type": "minecraft:uniform", "min": 3.0, "max": 5.0},
                "add": False,
            }
        ],
    }
    assert "tiers" not in carrot and "limit" not in carrot
    # One item at a time needs no count; a book enchanted with levels becomes an enchanted book in the game.
    assert by_name["minecraft:book"]["entry"] == {
        "type": "minecraft:item",
        "name": "minecraft:book",
        "functions": [
            {
                "function": "minecraft:enchant_with_levels",
                "levels": {"type": "minecraft:uniform", "min": 20.0, "max": 30.0},
                "options": "#minecraft:on_random_loot",
            }
        ],
    }
    apple = by_name["minecraft:enchanted_golden_apple"]
    assert apple["limit"] == 1 and apple["cost"] == 1500 and "functions" not in apple["entry"]
    assert by_name["minecraft:diamond_ore"]["tiers"] == ["haven"]
    # A treasure room's gem is the purest of three rolls; Apotheosis's own entries are never named, so the table
    # loads without Apotheosis too.
    assert by_name["realmforge:tier_gem"]["entry"] == {"type": "realmforge:tier_gem", "rolls": 3}
    assert by_name["realmforge:tier_affix_item"]["entry"] == {"type": "realmforge:tier_affix_item"}
    assert "apotheosis:" not in json.dumps(entry)


def test_the_qa_mod_gets_each_tables_budget_per_tier() -> None:
    expectations = generated(RepoPaths())[EXPECTATIONS].decode()
    assert '"realmforge:normal", "realmforge:containers/normal", 150, 5.0,' in expectations
    # The least and the most points without luck, the cheapest option and the number of options, per world tier.
    assert 'new Tier("haven", 300, 1500, 60, 16)' in expectations
    assert 'new Tier("pinnacle", 420, 2100, 60, 17)' in expectations
    assert 'new Tier("haven", 750, 3000, 150, 14)' in expectations


# ---- The roll ----


def test_a_roll_never_overspends_and_stops_only_when_nothing_is_affordable() -> None:
    choices = [Choice(10, 60, 0), Choice(5, 150, 0), Choice(1, 1200, 1)]
    rng = random.Random(1)
    for budget in range(0, 3000, 7):
        picked = roll(budget, choices, rng)
        spent = sum(choices[index].cost for index in picked)
        assert spent <= budget
        assert budget - spent < 60 or (budget < 60 and not picked)
        # A unique thing never comes twice from one container.
        assert picked.count(2) <= 1


def test_a_valuable_pick_leaves_little_for_the_rest() -> None:
    """The design's example: a curio of 1,200 points may be all a container of 1,200 points holds."""

    class First(random.Random):
        def randrange(self, *args: Any, **kwargs: Any) -> int:
            return 0

    curio_first = [Choice(1, 1200, 1), Choice(10, 60, 0)]
    assert roll(1200, curio_first, First()) == [0]
    assert roll(1500, curio_first, First()) == [0, 1, 1, 1, 1, 1]
    assert roll(59, curio_first, First()) == []
    assert len(roll(10**9, [Choice(1, 1, 0)], First())) == MAX_PICKS


def test_the_budget_is_split_at_its_medium_and_scaled_by_tier_and_luck() -> None:
    table = tables(RepoPaths())["normal"]
    assert [table.budget.at(roll_) for roll_ in (0, 0.25, 0.5, 0.75, 1)] == [300, 450, 600, 1050, 1500]
    assert (table.points(0.5, "haven"), table.points(0.5, "frontier"), table.points(0.5, "pinnacle")) == (
        600,
        660,
        840,
    )
    # Each point of luck adds 5 %; bad luck takes away, never below nothing.
    assert table.points(0.5, "haven", luck=2) == 660
    assert table.points(0.5, "frontier", luck=2) == 726
    assert table.points(0.5, "haven", luck=-1) == 570
    assert table.points(0.5, "haven", luck=-40) == 0


# ---- The simulation against the kill-equivalents ----


@pytest.mark.parametrize("table_id", ["normal", "treasure"])
@pytest.mark.parametrize("tier", TIERS)
def test_a_container_is_worth_its_kills_at_every_tier(table_id: str, tier: str) -> None:
    table = tables(RepoPaths())[table_id]
    least, medium, most = KILLS[table_id]
    scale = table.tiers[tier].budget_percent / 100
    cheapest = min(choice.cost for choice in choices_at(table, tier))
    simulation = simulate(table, tier, ROLLS, seed=289)
    kills = simulation.kills(table.kill_points)

    # No container is ever empty (ADR 0041, decision 1), and what a roll leaves over buys nothing.
    assert all(simulation.picks)
    assert all(
        budget - spent < cheapest for budget, spent in zip(simulation.budgets, simulation.spent, strict=True)
    )
    # The least, the roll in the middle and a high roll, in kills, raised by the tier's percent; a roll may fall
    # short of its budget by less than its cheapest option.
    slack = cheapest / table.kill_points
    assert least * scale - slack <= min(kills) <= least * scale + 0.25
    assert medium * scale - slack - 0.3 <= statistics.median(kills) <= medium * scale + 0.3
    assert most * scale - slack - 0.25 <= max(kills) <= most * scale
    # The average lies between the medium and the middle of the range: a high roll is rare.
    assert medium * scale - slack <= statistics.mean(kills) <= (least + most) / 2 * scale
    # A container holds its loot: fewer stacks than slots even if nothing joined.
    assert max(len(picks) for picks in simulation.picks) < CONTAINER_SLOTS


@pytest.mark.parametrize("table_id", ["normal", "treasure"])
def test_luck_and_a_later_tier_give_more(table_id: str) -> None:
    table = tables(RepoPaths())[table_id]
    plain = statistics.mean(simulate(table, "haven", ROLLS, seed=7).spent)
    lucky = statistics.mean(simulate(table, "haven", ROLLS, seed=7, luck=2).spent)
    later = statistics.mean(simulate(table, "frontier", ROLLS, seed=7).spent)
    # Luck II adds a tenth, and so does the next world tier.
    assert 1.07 < lucky / plain < 1.13
    assert 1.07 < later / plain < 1.13


def test_what_the_tables_hold_is_what_the_design_asks() -> None:
    """Coins in most containers, Apotheosis's gems and affix items in some, more of them in a treasure room; and
    nothing that is only for a later tier at the first one."""
    both = tables(RepoPaths())

    def share(table: ContainerLootDef, tier: str, wanted: str) -> float:
        options = table.options_at(tier)
        simulation = simulate(table, tier, ROLLS, seed=3)
        return (
            sum(any(options[index].type == wanted for index in picks) for picks in simulation.picks) / ROLLS
        )

    normal, treasure = both["normal"], both["treasure"]
    assert share(normal, "haven", "coins") > 0.6
    assert 0.1 < share(normal, "haven", "gem") < 0.3
    assert 0.05 < share(normal, "haven", "affix_item") < 0.25
    assert share(treasure, "haven", "gem") > share(normal, "haven", "gem") + 0.1
    assert share(treasure, "haven", "affix_item") > share(normal, "haven", "affix_item") + 0.1
    haven = {option.item for option in normal.options_at("haven") if isinstance(option, ItemOptionDef)}
    assert "minecraft:diamond" not in haven and "realmforge:ruby" not in haven
    # Diamonds rather than iron at Haven, in a treasure room.
    rich = {option.item for option in treasure.options_at("haven") if isinstance(option, ItemOptionDef)}
    assert "minecraft:diamond" in rich and "minecraft:iron_ingot" not in rich


# ---- Validation ----


@pytest.mark.parametrize(
    ("old", "new", "expected"),
    [
        ("minecraft:bread", "minecraft:breadd", "unknown id 'minecraft:breadd'"),
        # A progression item is never left to a roll (research LE-1).
        (
            "minecraft:bread",
            "realmforge:undervault_signet",
            "a progression item never comes from a container's roll",
        ),
        ("budget: {min: 300, medium: 600, max: 1500}", "budget: {min: 600, medium: 300, max: 1500}", "rise"),
        # The cheapest option must fit the least budget, or a container would be empty.
        (
            "budget: {min: 300, medium: 600, max: 1500}",
            "budget: {min: 30, medium: 600, max: 1500}",
            "would be empty",
        ),
        # Nobody could ever roll an option that costs more than the most a roll may have.
        (
            "item: minecraft:golden_apple, cost: 300",
            "item: minecraft:golden_apple, cost: 9000",
            "would never come",
        ),
        (
            "  pinnacle: {level: 56, budget_percent: 140}",
            "",
            "tiers needs every world tier: pinnacle missing",
        ),
        (
            "  haven: {level: 5, budget_percent: 100}",
            "  haven: {level: 50, budget_percent: 100}",
            "no lower level",
        ),
        (
            "enchant: {min: 5, max: 15}, cost: 300, weight: 4",
            "enchant: {min: 5, max: 15}, count: {min: 2, max: 2}, cost: 300",
            "one at a time",
        ),
        (
            "{type: gem, cost: 300, weight: 7}",
            "{type: gem, cost: 0, weight: 7}",
            "greater than or equal to 1",
        ),
        (
            "{type: gem, cost: 300, weight: 7}",
            "{type: gem, rolls: 11, cost: 300, weight: 7}",
            "less than or equal to 10",
        ),
        ("{type: coins, kills: 1, weight: 30}", "{type: coins, kills: 1, cost: 5, weight: 30}", "cost"),
    ],
)
def test_validation_refuses_a_broken_table(repo: RepoPaths, old: str, new: str, expected: str) -> None:
    edit(repo, "normal", old, new)
    problems = problems_for(repo)
    assert any(expected in p for p in problems), problems


def test_every_tier_needs_an_option(repo: RepoPaths) -> None:
    path = repo.content / "container_loot" / "normal.yaml"
    head = path.read_text(encoding="utf-8").split("options:")[0]
    path.write_text(
        head + "options:\n  - {type: item, item: minecraft:bread, cost: 60, tiers: [haven, frontier]}\n",
        encoding="utf-8",
    )
    problems = problems_for(repo)
    for tier in ("ascent", "summit", "pinnacle"):
        assert any(f"no option is for the world tier {tier}" in p for p in problems), problems
    assert not any("the world tier haven" in p for p in problems)


def test_coins_need_our_coins_and_the_coin_drop_curve(repo: RepoPaths) -> None:
    (repo.content / "curves" / "coin_drop.yaml").unlink()
    assert any("coins are counted in kills" in p for p in problems_for(repo))


def test_a_dungeons_key_is_no_container_loot(repo: RepoPaths) -> None:
    """A realm's signet and a dungeon type's key are progression items; the test repository has no dungeon type, so
    the signet stands for both, and a table without it loads."""
    load_and_validate(repo)
    edit(repo, "treasure", "minecraft:golden_apple, cost: 300", "realmforge:undervault_signet, cost: 300")
    assert any("the signet of the realm 'stone'" in p for p in problems_for(repo))
