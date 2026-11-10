"""Coins (RLM-284, RLM-286; ADR 0040, ADR 0072): the chain of coins from ``content/items/`` and what a mob drops.

The coins are the items with a ``coin`` block, a chain by value: copper 1, silver 100, gold 10000, platinum 1000000.
An amount of money is a number of the smallest coin and is paid in the largest coins (``split``). A hostile mob drops
the curve ``coin_drop`` at its level, times its rank (its kill XP multiplier), times a uniform 50 to 150 %, at least
one copper (``coin_drop``). The game computes both the same (``item/CoinMath``, ``loot/CoinDropMath``), and a GameTest
compares the two.
"""

from __future__ import annotations

from collections.abc import Iterable

from realmforge_tools.project import MOD_ID
from realmforge_tools.schema.item import ItemDef
from realmforge_tools.schema.leveling import java_round

# The curve of a normal mob's coin drop by its level, in the smallest coin (content/curves/coin_drop.yaml).
COIN_DROP = "coin_drop"
# A drop is between these shares of the curve's value, uniformly (Matěj, 2026-10-21, RLM-286).
COIN_DROP_MIN_SHARE = 0.5
COIN_DROP_MAX_SHARE = 1.5


def plural_key(item_id: str) -> str:
    """The translation key of a coin's name for more than one."""
    return f"item.{MOD_ID}.{item_id}.plural"


def coin_chain(items: Iterable[ItemDef]) -> list[ItemDef]:
    """The coins among ``items``, smallest first."""
    return sorted((item for item in items if item.coin is not None), key=lambda item: _value(item))


def _value(item: ItemDef) -> int:
    assert item.coin is not None
    return item.coin.value


def split(amount: int, values: list[int]) -> list[int]:
    """How many coins of each of ``values`` (the chain's values, smallest first) pay ``amount``, largest coins first
    in worth: as few coins as the chain allows. The counts come back in ``values``' order; an amount below 1 is no
    coins."""
    counts = [0] * len(values)
    rest = max(0, amount)
    for index in range(len(values) - 1, -1, -1):
        counts[index], rest = divmod(rest, values[index])
    return counts


def coin_drop(base: float, rank: float, roll: float) -> int:
    """A mob's coin drop in the smallest coin: ``base`` (the curve at its level) times ``rank`` times a share between
    50 and 150 %, which ``roll`` (0 up to 1) picks; rounded half up and at least 1. A mob of rank 0 (the smallest
    slime) or a level whose base is 0 drops nothing."""
    if rank <= 0 or base <= 0:
        return 0
    share = COIN_DROP_MIN_SHARE + roll * (COIN_DROP_MAX_SHARE - COIN_DROP_MIN_SHARE)
    return max(1, java_round(base * rank * share))
