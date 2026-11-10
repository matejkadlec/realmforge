# Merchants

> Villagers that sell for coins at fixed prices, and the first two of them: the Ancient Builder and the Ancient
> Specialist (RLM-285, RLM-241). Decisions:
> [ADR 0034](../decisions/0034-ancient-merchants-and-the-wither-amend-the-nether-and-end-removal.md) (what they sell
> and why) and [ADR 0040](../decisions/0040-coins-and-data-driven-merchant-trades.md) (trades as data, fixed prices, no
> levels). The coins: [coins.md](coins.md). How it is built: [architecture 14.4](../architecture.md#144-merchants).

## How a merchant works

- **An ordinary villager profession** with a workstation of its own: any jobless villager near the workstation takes
  the job, as in vanilla (Matěj, 2026-10-16).
- **A fixed trade list:** every trade from the start, in one order, no villager levels and nothing to unlock. The
  trade screen shows the profession's name without a level and without an XP bar.
- **Prices in coins that never change:** no reputation, no demand, no Hero of the Village discount, and never scaled
  with the player's level. Coin drops grow with the story, so later the same prices feel cheap, as in WoW.
- **A price is at most two coin types** and at most 99 of each: a trade has two cost slots.
- **Stock:** a trade can be bought 12 times before the merchant restocks at its workstation, as vanilla's do.
- **A merchant keeps its job once it has traded,** even without its workstation; before its first trade it can lose
  it, as a vanilla villager can.

## The Ancient merchants

The Nether and the End are gone ([ADR 0025](../decisions/0025-remove-the-nether-and-end.md)); in the lore they were
lost in the war 700 years ago ([style guide](../lore/style.md)). These two still hold goods from the time they
existed, hence "Ancient". They are the source of the Nether's stone and of blaze rods, ender pearls and nether wart
(ADR 0034), next to what a realm supplies itself: Ashfire's blazes still drop blaze rods, Overworld's endermen ender
pearls, Stone Quartz Ore gives quartz and Undervault ancient debris.

### Ancient Builder

**Workstation: Ancient Casting.** Four obsidian in the corners, four glass on the sides and a lava bucket in the
middle; the bucket comes back.

| Item | Per trade | Matěj's band | Price |
| --- | --- | --- | --- |
| Netherrack | 64 | cheap | 20 copper |
| Nether Bricks | 64 | cheap | 40 copper |
| Basalt | 64 | cheap | 30 copper |
| Blackstone | 64 | cheap | 30 copper |
| Soul Sand | 16 | cheap | 30 copper |
| Soul Soil | 16 | cheap | 30 copper |
| Magma Block | 16 | cheap | 40 copper |
| Glowstone | 8 | medium | 2 silver |
| Nether Quartz Ore | 8 | expensive | 5 silver |
| Crying Obsidian | 4 | expensive | 5 silver |

### Ancient Specialist

**Workstation: Ancient Workbench,** an obsidian crafting table. Four obsidian on the sides around a crafting table,
the corners empty.

| Item | Per trade | Matěj's band | Price |
| --- | --- | --- | --- |
| Crimson Nylium | 8 | cheap | 40 copper |
| Crimson Fungus | 1 | cheap | 20 copper |
| Warped Nylium | 8 | cheap | 40 copper |
| Warped Fungus | 1 | cheap | 20 copper |
| Nether Wart | 8 | medium | 2 silver |
| Blaze Rod | 8 | medium | 3 silver |
| Ender Pearl | 1 | expensive | 5 silver |
| Ghast Tear | 1 | expensive | 6 silver |
| Wither Skeleton Skull | 1 | extremely expensive | 75 silver |
| Netherite Scrap | 1 | extremely expensive | 1 gold |

- **The Wither can be summoned again:** three skulls from the Specialist (2 gold 25 silver) and soul sand from the
  Builder. It is a boss of level 20 or higher (RLM-240), and its nether star feeds the Augmenting Table
  ([Apotheosis design](apotheosis.md)).
- **Early brewing** (ADR 0025's consequence): nether wart and blaze rods come from the Specialist, so brewing starts
  once a player has a villager, an Ancient Workbench and a few silver.
- **Shroomlight and the wart blocks** grow with the huge fungi from the Specialist's fungi on its nylium, as crimson
  and warped wood does.

### What the bands mean

Matěj set the bands relative to what a player earns in Overworld (2026-10-17); the numbers are the agent's, from the
[coin drops](coins.md#what-mobs-drop) at about 6 kills a minute:

| Band | Price | At level 10 (6 copper a kill) | At level 18 (31 copper a kill) |
| --- | --- | --- | --- |
| cheap | 20 to 40 copper | 3 to 7 kills, about a minute | 1 or 2 kills |
| medium | 2 to 3 silver | 31 to 47 kills, 5 to 8 minutes | 6 to 10 kills, 1 to 2 minutes |
| expensive | 5 to 6 silver | 78 to 94 kills, about a quarter of an hour | 16 to 19 kills, 3 minutes |
| extremely expensive | 75 silver to 1 gold | out of reach | 240 to 320 kills, 40 to 55 minutes |

The scrolls give the scale: the Scroll of Oblivion costs 5 silver and the Scroll of Rebirth 1 gold
([coins.md](coins.md#check-against-prices)).

## Open questions for Matěj

Each is the agent's choice from RLM-285 and RLM-241's batch, built that way and merged unplayed. That is Matěj's
decision of 2026-11-10 for the `flow1 cloud` marathon that built it, a waiver of
[ADR 0022](../decisions/0022-playtest-before-the-pull-request.md)'s playtest before the pull request for that run,
given in its session and quoted in RLM-DEFERRED, the deferred playtest he still owes it:

1. **The prices** in the tables above, and the four bands' numbers.
2. **The workstations are job sites only:** the Ancient Workbench opens no crafting grid, and neither block does
   anything when used. Hardness 5 with any pickaxe, obsidian's blast resistance.
3. **Stock:** 12 purchases of a trade before a restock, vanilla's usual number.
4. **Trading gives the player vanilla's few experience points,** as every villager trade does; the villager itself
   never levels.
5. **Placeholder looks** until the art exists (RLM-645): the blocks' placeholder tiles and a checkered robe on
   the villager. The work sounds are vanilla's mason and cleric.
6. **Guide entries** for both merchants wait for the guide itself (RLM-646, M4).
