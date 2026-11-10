"""Merchants: ``content/merchants/<id>.yaml`` (RLM-285; ADR 0040, architecture 14.4).

A merchant is a villager profession of ours with a workstation block and a fixed list of trades, paid in coins at
prices that never change. Minecraft 26.1 made trades data, so the pipeline writes each trade as a
``villager_trade`` file, the merchant's one ``trade_set`` (every trade, no duplicates) and its ``villager_trade`` tag;
the profession and its point of interest are registered in Java from the generated ``GeneratedMerchants``
(``mob/merchant/Merchants``), since ``villager_profession`` is a built-in registry.

A merchant never levels (ADR 0040, decision 4; ADR 0034): all its trades sit in its level 1 trade set, every trade
gives the villager no XP (``xp: 0``), and the game locks the profession at the first trade instead (Matěj's correction
on RLM-285, 2026-10-19). Prices are fixed: ``reputation_discount: 0`` switches demand and reputation off, and the game
opens our merchants' trading itself, without vanilla's special prices (Hero of the Village).

A trade's cost is a ``price`` in the smallest coin, which the pipeline pays in the largest coins (``schema/coin.py``),
or explicit ``wants`` for a trade that takes other items (a merchant that buys). A vanilla trade has two cost slots,
each of one item type and at most one stack: a price mixes at most two coin types and at most 99 of each.
"""

from __future__ import annotations

from pydantic import Field, model_validator

from realmforge_tools.project import MOD_ID
from realmforge_tools.schema.common import ContentId, LocalizedText, ResourceId, StrictModel

# A vanilla trade's cost slots: wants and additional_wants.
MAX_COST_SLOTS = 2
# The most of one item a cost slot or a result holds: our coins' stack size, vanilla's most.
MAX_STACK = 99
# The only villager level our merchants have trades at.
MERCHANT_LEVEL = 1
# The villager_profession tag of our merchants, which the game reads to trade at fixed prices (mob/merchant/Merchants).
FIXED_PRICE_TAG = f"{MOD_ID}:fixed_price"
# Vanilla's point_of_interest_type tag of the job sites a jobless villager may take.
ACQUIRABLE_JOB_SITE_TAG = "minecraft:acquirable_job_site"


def profession_key(merchant_id: str) -> str:
    """The translation key of a merchant's profession name, as vanilla builds it: ``entity.<ns>.villager.<path>``."""
    return f"entity.{MOD_ID}.villager.{merchant_id}"


def trade_set_id(merchant_id: str) -> str:
    """The id of a merchant's one trade set: ``realmforge:<merchant>/level_1``."""
    return f"{MOD_ID}:{merchant_id}/level_{MERCHANT_LEVEL}"


def trade_id(merchant_id: str, n: int, gives: str) -> str:
    """The id of a merchant's ``n``-th trade (from 1), named after what it gives:
    ``realmforge:<merchant>/01_netherrack``."""
    return f"{MOD_ID}:{merchant_id}/{n:02d}_{gives.split(':', 1)[1].replace('/', '_')}"


class TradeStackDef(StrictModel):
    """An item and how many of it: what a trade gives, or one of the costs it wants."""

    item: ResourceId = Field(
        description="The item, e.g. minecraft:netherrack. Validated against the ID universe."
    )
    count: int = Field(default=1, ge=1, le=MAX_STACK, description="How many, at most one stack of the item.")


class MerchantTradeDef(StrictModel):
    """One trade of a merchant: what it gives for a price in coins, or for the items it wants."""

    gives: TradeStackDef = Field(description="What the player gets.")
    price: int | None = Field(
        default=None,
        ge=1,
        description=(
            "What it costs, in the smallest coin (copper): 250 is 2 Silver Coins and 50 Copper Coins. Paid in the "
            "largest coins, which must be at most two coin types and at most 99 of each. Either price or wants."
        ),
    )
    wants: list[TradeStackDef] | None = Field(
        default=None,
        min_length=1,
        max_length=MAX_COST_SLOTS,
        description=(
            "Instead of a price: the one or two items it takes, for a trade that is not paid in coins (a merchant "
            "that buys). Either price or wants."
        ),
    )
    max_uses: int = Field(
        default=12,
        ge=1,
        le=9999,
        description="How often it can be bought before the merchant restocks at its workstation (vanilla's max_uses).",
    )

    @model_validator(mode="after")
    def _one_cost(self) -> MerchantTradeDef:
        if (self.price is None) == (self.wants is None):
            raise ValueError(
                "a trade has either a price (in coins) or wants (other items), not both and not neither"
            )
        if self.wants is not None:
            items = [stack.item for stack in self.wants]
            if len(set(items)) != len(items):
                raise ValueError("the two costs of a trade are different items: raise the count instead")
        return self


class MerchantDef(StrictModel):
    """A villager profession of ours with a workstation and a fixed list of trades at fixed prices."""

    id: ContentId
    name: LocalizedText = Field(description="The profession's name, shown over the trades: Ancient Builder.")
    workstation: ResourceId = Field(
        description=(
            "The block of ours (content/blocks/) that is its job site: a jobless villager near one takes the "
            "profession, as with vanilla's workstations."
        )
    )
    work_sound: ResourceId | None = Field(
        default=None,
        description="The sound of the villager working at its workstation, a sound event; none by default.",
    )
    trades: list[MerchantTradeDef] = Field(
        min_length=1,
        description="Its trades, in the order the trade screen lists them. All are offered at once: no levels.",
    )
