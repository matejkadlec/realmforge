# 0040. Coins and data-driven merchant trades

Date: 2026-10-19

Status: Accepted (amends [0013](0013-modpack-composition.md): JEI / REI Trades joins the pack)

## Context

Matěj decided on 2026-10-15 that villagers and merchants trade for coins or money, not emeralds, and that taking
from third-party mods is welcome where it saves work (Jira RLM-222). On 2026-10-17 he added that mob coin drops grow
with the story, like WoW's copper, then silver, then gold, and that shop and merchant prices are fixed: cheap,
medium and expensive relative to Overworld's earning rate, never scaled with the player's level. The first users
are the Ancient Builder and Ancient Specialist ([0034](0034-ancient-merchants-and-the-wither-amend-the-nether-and-end-removal.md),
RLM-241) and the quest shop's Random Perfect Gem ([Apotheosis design](../design/apotheosis.md)).

The research ([coins and trades](../research/coins-and-trades.md)) found that Minecraft 26.1 made villager trades
data: the datapack registries `villager_trade` (one offer) and `trade_set` (a pool, which a villager profession
names per level in code). NeoForge's trade events are gone. Lightman's Currency is the only serious coin mod on
26.1.2, as a three-week-old beta rework with a large feature surface to switch off. EMI and EMI Trades have no 26.1
builds.

## Decision

Matěj's choices of 2026-10-19:

1. **Our own coins:** copper, silver and gold, items of our content pipeline, at 100:1 (100 copper make a silver,
   100 silver a gold), stacking to 99.
2. **A converter of our own** breaks a coin into smaller ones and merges them back, since crafting converts at most
   9:1. No currency mod.
3. **Trades are vanilla data generated from our YAML:** a merchant content type becomes `villager_trade` files, a
   `trade_set` per merchant and level and their tags. Our merchants are villager professions registered in Java
   (their trade sets per level) or small `AbstractVillager` subclasses that read our trade sets.
4. **Prices stay fixed and merchants never level up into new trades:** our trades set `reputation_discount: 0` (no
   demand or reputation changes), our code stops Hero of the Village's discount on our merchants, and a merchant's
   trades all sit in its level-1 trade set, so a higher level offers nothing new. A merchant still keeps its
   profession once it has traded (below), so its trades do not all give `xp: 0`.
5. **Coin drops** come from loot data per realm: copper in the first realms, silver later, gold late.
6. **JEI / REI Trades** (GPL-3.0-only, unmodified, installed on client and server) joins the pack and shows every
   villager profession's trades in JEI, ours included. It replaces the EMI Trades wish of 0013's list of mods to
   build ourselves.

## Consequences

- A price mixes at most two coin types (a trade has two cost slots) and at most 99 of each.
- Vanilla's own villagers keep their emerald trades until a ticket decides what they sell; our datapack can rewrite
  every vanilla trade the same way when it does.
- **`xp: 0` would let a merchant lose its profession:** a villager at level 1 with 0 XP that loses its workstation
  (broken, moved or claimed) goes back to no profession and drops its offers (`ResetProfession`,
  `Villager#setVillagerData` in 26.1.2), and any jobless villager can then take that workstation. Vanilla villagers
  lock their profession with their first trade's XP. Our merchants keep an `xp` of 1 or more on their trades, or
  lock the profession in code; RLM-285 and RLM-241 choose.
- Trades and trade sets load with the world, not on `/reload`, and are not sent to clients: JEI / REI Trades reads
  them on the server.
- Follow-up tickets carry it: the coins and the converter (RLM-284), the merchant content type (RLM-285), coin drops
  by realm (RLM-286) and JEI / REI Trades in the pack (RLM-287); RLM-241 builds the two professions on them.
