"""Container loot by loot points (RLM-289): the loot tables and the QA mod's expectations, from
``content/container_loot/``.

- ``data/realmforge/loot_table/containers/<id>.json``: a chest loot table with one pool and one entry of our own,
  ``realmforge:budget`` (``loot/BudgetEntry``): the table's budget, each world tier's percent of it, the luck's
  percent, and the options, each with its weight, cost, limit, tiers and the loot entry a pick rolls. A coins option
  becomes one option per world tier, since a kill's coins grow with the tier's level: ``realmforge:money`` with the
  least and the most of that tier. An item is vanilla's item entry (with ``set_count`` and ``enchant_with_levels``), a
  gem and an affix item our ``realmforge:tier_gem`` and ``realmforge:tier_affix_item``, which ask Apotheosis.
- ``GeneratedContainerLootExpectations``: every table with its loot table id, and per world tier the least and the
  most points a roll may have without luck and its cheapest option, so GameTests can check what the game rolls.
"""

from __future__ import annotations

from pathlib import Path
from typing import Any

from realmforge_tools.content import Content
from realmforge_tools.formats.jsonfmt import dump_json
from realmforge_tools.generate.datapack import data_dir
from realmforge_tools.generate.javagen import header, java_file_path, string_literal
from realmforge_tools.project import MOD_ID, QA_JAVA_PACKAGE, RepoPaths
from realmforge_tools.schema.coin import COIN_DROP
from realmforge_tools.schema.container_loot import (
    ENCHANTMENTS,
    TIERS,
    AffixItemOptionDef,
    CoinsOptionDef,
    ContainerLootDef,
    GemOptionDef,
    ItemOptionDef,
    OptionDef,
    coins_range,
    loot_table_id,
    loot_table_path,
)
from realmforge_tools.schema.curve import CurveDef

QA_GENERATED_PACKAGE = f"{QA_JAVA_PACKAGE}.generated"
BUDGET = f"{MOD_ID}:budget"
MONEY = f"{MOD_ID}:money"
TIER_GEM = f"{MOD_ID}:tier_gem"
TIER_AFFIX_ITEM = f"{MOD_ID}:tier_affix_item"
SOURCES = "content/container_loot/ and content/curves/coin_drop.yaml"


def generate(content: Content, paths: RepoPaths) -> dict[Path, bytes]:
    files: dict[Path, bytes] = {}
    coin_drop = next((entry.model for entry in content.curves if entry.model.id == COIN_DROP), None)
    tables = [entry.model for entry in content.container_loot]
    for table in tables:
        files[data_dir(paths) / "loot_table" / f"{loot_table_path(table.id)}.json"] = dump_json(
            loot_table(table, coin_drop)
        )
    files[
        java_file_path(
            paths.qa_generated / "java", QA_GENERATED_PACKAGE, "GeneratedContainerLootExpectations"
        )
    ] = _expectations(tables)
    return files


def loot_table(table: ContainerLootDef, coin_drop: CurveDef | None) -> dict[str, Any]:
    """The loot table of ``table``: one pool, one ``realmforge:budget`` entry."""
    return {
        "type": "minecraft:chest",
        "pools": [
            {
                "rolls": 1.0,
                "bonus_rolls": 0.0,
                "entries": [
                    {
                        "type": BUDGET,
                        "budget": {
                            "min": table.budget.min,
                            "medium": table.budget.medium,
                            "max": table.budget.max,
                        },
                        "tier_percent": {tier: table.tiers[tier].budget_percent for tier in TIERS},
                        "luck_percent": table.luck_percent,
                        "options": [
                            entry for option in table.options for entry in _options(table, option, coin_drop)
                        ],
                    }
                ],
            }
        ],
        "random_sequence": loot_table_id(table.id),
    }


def _options(table: ContainerLootDef, option: OptionDef, coin_drop: CurveDef | None) -> list[dict[str, Any]]:
    """The game's options of one written option: one, or for coins one per world tier it is for."""
    if isinstance(option, CoinsOptionDef):
        if coin_drop is None:  # validation refuses coins without the curve
            return []
        return [
            _option(table, option, [tier], _money(*coins_range(coin_drop, table, option, tier)))
            for tier in TIERS
            if option.is_for(tier)
        ]
    return [_option(table, option, list(option.tiers), _entry(option))]


def _option(
    table: ContainerLootDef, option: OptionDef, tiers: list[str], entry: dict[str, Any]
) -> dict[str, Any]:
    data: dict[str, Any] = {"weight": option.weight, "cost": table.cost(option)}
    if option.limit:
        data["limit"] = option.limit
    if tiers:
        data["tiers"] = tiers
    data["entry"] = entry
    return data


def _money(least: int, most: int) -> dict[str, Any]:
    return {"type": MONEY, "min": least, "max": most}


def _entry(option: OptionDef) -> dict[str, Any]:
    match option:
        case ItemOptionDef():
            functions: list[dict[str, Any]] = []
            if (option.count.min, option.count.max) != (1, 1):
                count: Any = (
                    float(option.count.min)
                    if option.count.min == option.count.max
                    else {
                        "type": "minecraft:uniform",
                        "min": float(option.count.min),
                        "max": float(option.count.max),
                    }
                )
                functions.append({"function": "minecraft:set_count", "count": count, "add": False})
            if option.enchant is not None:
                functions.append(
                    {
                        "function": "minecraft:enchant_with_levels",
                        "levels": {
                            "type": "minecraft:uniform",
                            "min": float(option.enchant.min),
                            "max": float(option.enchant.max),
                        },
                        "options": ENCHANTMENTS,
                    }
                )
            entry: dict[str, Any] = {"type": "minecraft:item", "name": option.item}
            if functions:
                entry["functions"] = functions
            return entry
        case GemOptionDef():
            return {"type": TIER_GEM} | ({"rolls": option.rolls} if option.rolls > 1 else {})
        case AffixItemOptionDef():
            return {"type": TIER_AFFIX_ITEM}
        case _:
            raise AssertionError(f"no loot entry for {option!r}")


def _double(value: float) -> str:
    return repr(float(value))


def _expectations(tables: list[ContainerLootDef]) -> bytes:
    rows = []
    for table in tables:
        tiers = ",\n".join(
            f"                            new Tier({string_literal(tier)}, {table.points(0.0, tier)}, "
            f"{table.points(1.0, tier)}, {min(table.cost(option) for option in table.options_at(tier))}, "
            f"{len(table.options_at(tier))})"
            for tier in TIERS
        )
        rows.append(
            "            new ContainerLootExpectation(\n"
            f"                    {string_literal(f'{MOD_ID}:{table.id}')}, "
            f"{string_literal(loot_table_id(table.id))}, {table.kill_points}, {_double(table.luck_percent)},\n"
            f"                    List.of(\n{tiers}))"
        )
    tables_list = "List.of(\n" + ",\n".join(rows) + ")" if rows else "List.of()"
    source = (
        header(Path(__file__), SOURCES) + f"package {QA_GENERATED_PACKAGE};\n"
        "\n"
        "import java.util.List;\n"
        "\n"
        "/**\n"
        " * What {@code content/container_loot/} says about every container loot table, so GameTests can check what the\n"
        " * game rolls (architecture 13).\n"
        " */\n"
        "public final class GeneratedContainerLootExpectations {\n"
        "    /**\n"
        "     * A table at one world tier, by the tier's name: the least and the most points a roll has there without\n"
        "     * luck, the cost of its cheapest option, and how many options it has.\n"
        "     */\n"
        "    public record Tier(String name, int minPoints, int maxPoints, int cheapest, int options) {\n"
        "    }\n"
        "\n"
        "    /**\n"
        "     * Expected state of one table. Ids are full resource locations: the table's, e.g.\n"
        "     * {@code realmforge:normal}, and its loot table's, {@code realmforge:containers/normal}.\n"
        "     */\n"
        "    public record ContainerLootExpectation(String id, String lootTable, int killPoints, double luckPercent,\n"
        "            List<Tier> tiers) {\n"
        "    }\n"
        "\n"
        f"    public static final List<ContainerLootExpectation> TABLES = {tables_list};\n"
        "\n"
        "    private GeneratedContainerLootExpectations() {\n"
        "    }\n"
        "}\n"
    )
    return source.encode("utf-8")
