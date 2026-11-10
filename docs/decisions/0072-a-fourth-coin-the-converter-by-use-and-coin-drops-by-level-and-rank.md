# 0072. A fourth coin, the converter by use, and coin drops by level and rank

Date: 2026-11-10

Status: Accepted (amends [0040](0040-coins-and-data-driven-merchant-trades.md): decisions 1, 2 and 5)

## Context

[0040](0040-coins-and-data-driven-merchant-trades.md) decided our own copper, silver and gold coins at 100:1, a
converter of our own and coin drops "from loot data per realm". Before the coins were built, Matěj changed two of
those points on their tickets:

- **RLM-284, 2026-10-21:** a fourth coin, platinum, at 100 gold. Scroll prices double with every purchase and stop at
  99 platinum (RLM-354), and late mobs drop several gold.
- **RLM-286, 2026-10-21/22:** coin drops follow the mob's level and rank, not its realm, on one smooth curve, and
  neither Luck nor Looting changes them ("otherwise the whole system falls apart"). So the amount comes from our own
  code, not from a loot table per realm.

0040 left the converter's form open ("for example a coin pouch, or using a stack of coins"). The batch that built
the coins (RLM-284, RLM-286) chose it; that choice is the agent's and waits for Matěj's playtest (below).

That playtest comes after the merge. Matěj deferred it on 2026-11-10, in the session of the `flow1 cloud` marathon
that built the batch: "Carry a playtest batch through its handoff, with every automated check. Then don't wait for
Matěj: continue as if he answered `qa1`, and carry the pull request through `finalize-pr` to its merge." It is his
waiver of [0022](0022-playtest-before-the-pull-request.md)'s playtest before the pull request for that run, not a
change of 0022, and RLM-638 records the playtest he still owes this batch, with his words.

## Decision

1. **Four coins:** Copper, Silver, Gold and Platinum Coin, each worth 100 of the coin below it, stacking to 99
   (Matěj). Copper stays the name of the smallest coin.
2. **Coin drops by mob level and rank** (Matěj), replacing 0040's drops per realm:
   - the base is `e^(-0.2109 + 0.2113 L - 0.000458 L²)` copper at mob level `L`: about 1 copper at level 1, 1 gold at
     level 50 and 5 gold at level 60;
   - the drop is uniformly 50 to 150 % of the base, at least 1 copper, paid in the largest coins;
   - times the mob's rank, the same number as its kill XP multiplier: normal 1, tough normal 1.25, strong 1.5, elite
     2, miniboss 5, dungeon boss 10, realm guardian 20;
   - **neither Luck nor Looting affects it:** no looting or `enchanted_count_increase` functions, no bonus rolls, no
     entry quality, and the luck attributes of vanilla, Apotheosis and Apothic Attributes are ignored. The amount is
     computed by our code in a drop handler.
3. **The converter is using a coin** (the agent's choice, open until Matěj's playtest, RLM-638): a use merges every
   full hundred of that coin in the inventory into the next larger coin; sneaking while using it breaks one coin into
   100 of the next smaller. No block, no screen, no pouch item. What does not fit the inventory drops at the
   player's feet.
4. **Who gets coins** (the agent's choice, open until the playtest): a hostile mob drops them only with a player's
   kill credit and while the gamerule `mob_drops` is on, as vanilla's experience orbs; a mob of rank 0 (the smallest
   slime) drops none, and the swarm ranks below 1 scale the drop down as they scale kill XP.

## Consequences

- Prices can be written in up to four coin types, though a vanilla trade still has two cost slots (0040).
- A mob's rank is read below `character`, which owns the kill XP multipliers: the rank's provider moved into
  `stats` (`MobRanks`), where aim assistance and coin drops both read it
  ([architecture 4.4](../architecture.md#44-dependencies-between-features)).
- Coin drops are not loot table data, so a loot viewer (JEI's loot plugins) does not list them, and a data pack
  cannot change them except through the curve `realmforge:coin_drop` and the kill XP multipliers.
- A mob farm that kills without a player's hit yields no coins; one that leaves the last hit to a player does, at
  the pace of the kills. The price checks of RLM-286 assume about 6 kills a minute.
- The numbers and the price checks live in the [coins design](../design/coins.md) and are tuned after a playtest.
