# Coins, merchant trades and trade display on 26.1.2

> Research for RLM-222 (2026-10-19): which third-party mods could carry Matěj's decision that villagers and merchants
> trade for coins, not emeralds (2026-10-15), with coin drops that grow with the story and fixed prices (2026-10-17).
> Checked on **2026-10-19** for Minecraft 26.1.2 and NeoForge 26.1.2.116 unless a line says otherwise. Matěj's
> decision on it is the next section; [ADR 0040](../decisions/0040-coins-and-data-driven-merchant-trades.md)
> records it.

## Decision (Matěj, 2026-10-19)

- **Our own coins with a converter of our own:** copper, silver and gold coins at 100:1 (WoW's ratio), and a small
  converter that breaks a coin into smaller ones and merges them back, since crafting converts at most 9:1. No
  currency mod: Lightman's Currency was the alternative (section 1a).
- **Vanilla 26.1 trades as data:** our merchants' trades are `villager_trade` and `trade_set` files generated from
  our YAML (section 4); prices stay fixed (`reputation_discount: 0`, `xp: 0`, and Hero of the Village's discount
  stopped in code).
- **JEI / REI Trades joins the pack** to show trades in JEI, since EMI and EMI Trades have no 26.1 builds.

## How the facts were checked

- **Modrinth API** (`api.modrinth.com/v2`): searches with `facets=[["versions:26.1.2"],["categories:neoforge"]]`, then
  `/v2/project/<slug>` (license, source, client/server side) and
  `/v2/project/<slug>/version?game_versions=["26.1.2"]&loaders=["neoforge"]` for every candidate. The project-level
  search facet does not prove that a NeoForge build exists for 26.1.2, so every "26.1.2 NeoForge" claim below comes
  from the per-version query.
- **Source code**: shallow clones of the public repositories (commit hashes given), and the **decompiled 26.1.2
  game and NeoForge 26.1.2.116 sources** in the repo checkout (`build/mc-sources/`, from
  `./gradlew :mod:extractMinecraftSources`). The vanilla section rests on those sources, which are authoritative for
  our exact version.
- **Blocked in this session:** minecraft.wiki, minecraft.net, docs.neoforged.net, curseforge.com and the WebFetch tool
  (DNS failures / proxy 403). Wiki and changelog URLs below come from search results only and are marked so.
  CurseForge-only mods could not be checked (section 1c).

---

## 1. Currency and coin mods

### 1a. Candidates with a NeoForge build for 26.1.2

| Mod (slug) | NeoForge 26.1.2 build | License | Source | What it adds | Trades / prices defined | Fit for fixed prices, tiered coins, mob drops, NPC merchants |
| --- | --- | --- | --- | --- | --- | --- |
| **Lightman's Currency** (`lightmans-currency`) | `26.1-1.0.0.2`, **beta**, 2026-10-18 (first 26.1 build `26.1-1.0.0.0` beta 2026-10-02, "Initial 26.1 port/rework"). 1.21.1 line is `1.21-2.3.0.6` release. Client **required**, server required. 3.4 M downloads | Apache-2.0 | github.com/Lightman314/LightmansCurrency (main @ `8036298`, 2026-10-18; `gradle.properties`: `minecraft_version=26.1.2`, `neo_version=26.1.2.93`) | Item coins (copper, iron, gold, emerald, diamond, netherite by default), 6+ wallets (Curios slot optional), ATM, Coin Mint, player trading machines, bank accounts, teams, money enchantments, tickets, Banker and Cashier professions, structures | **Coin chain** in `config/lightmanscurrency/MasterCoinList.json`: any item id can be a coin, each with an `exchangeRate` to the previous one (default 10), display as coin initials or a number format. **Villager trades** in 26.1 use vanilla `villager_trade`/`trade_set` data (its own `data/lightmanscurrency/villager_trade/...`). A mixin on `VillagerTrade#getOffer` can swap emeralds for coins per profession (config `changeVanillaTrades`/`changeModdedTrades`/`changeWanderingTrades`, all **false** by default; `professionEmeraldReplacementOverrides`). **Mob drops**: common config lists entity ids in tiers T1-T6 (+ boss tiers), each tier rolls loot table `lightmanscurrency:loot_addons/entity/tierN` (overridable by datapack); `enableEntityDrops` default **true**. 1.0.0.2 re-added "auto-moves coins to/from your wallet" in the vanilla merchant menu | **Strongest coin mod on 26.1.2.** Tiered coins with any rate (100:1 possible) and our own coin items accepted in the chain; wallet pays vanilla villager trades; mob-drop tiers. Costs: big feature surface to switch off (trading machines, banks, mint, its own coins, chest and mob drops, structures, professions can't be removed per its own description); the 26.1 line is a **3-week-old beta rework** with missing features (coupons). Sources: [Modrinth project](https://api.modrinth.com/v2/project/lightmans-currency), [26.1.2 versions](https://api.modrinth.com/v2/project/lightmans-currency/version?loaders=%5B%22neoforge%22%5D), [Newer-Changelogs wiki](https://github.com/Lightman314/LightmansCurrency/wiki/Newer-Changelogs), [Master Coin List Config wiki](https://github.com/Lightman314/LightmansCurrency/wiki/Master-Coin-List-Config), source files `LCConfig.java`, `features/villagers/VillagerTradeHelper.java`, `mixin/VillagerTradeMixin.java`, `api/loot/LCLootTables.java` |
| **Coins JE** (`coins-je`) | `26.1.2.7` release, 2026-07-01 (26.1-26.1.2); needs Bookshelf | Modrinth says `LicenseRef-All-Rights-Reserved` (LICENSE file not readable at the repo root) | github.com/Darkhax-Minecraft/Coins | 5 coin tiers (copper, iron, gold, ...), coin piles, banner pattern | Coins by smelting ingots; 9:1 conversion by crafting | Plain items only, 9:1 rate, no drops, no wallet. Little gain over our own items; ARR. [Modrinth](https://api.modrinth.com/v2/project/coins-je) |
| **RPG Coins** (`rpgcoins`) | `3.0.1` release, 2026-06-23 (26.1-26.3) | MIT | github.com/GMalvestiti/Coins | Gold, iron and copper coin items, tags `coins:*` | No recipes since 3.0.0 (pack supplies them) | Three plain items; we would still write conversion, drops and trades. Only saves three textures. [Modrinth](https://api.modrinth.com/v2/project/rpgcoins) |
| **Money Talks** (`money-talks`) | `1.4.0` release, 2026-05-15 | GPL-3.0-or-later | github.com/DingleTheRat/MoneyTalks | One coin ("dollar"), wallet, Investor villager, Merchant's Carpet shops | Investor trades in code; emeralds → dollars | Single currency, keep-inventory design, coins can't leave the inventory. Poor fit. [Modrinth](https://api.modrinth.com/v2/project/money-talks) |
| **New Horizons Coins** (`new-horizons-coins`) | `neoforge-26.1.2-0.1.1`, 2026-05-20; 171 downloads | CC0-1.0 | github.com/SRINJAYDASGUPTA-Git/nhcoins-fabric | Bronze Sickles, Silver Ducats, Gold Mohurs, Coin Smith exchange block, Voidcore ore | Recipe-driven conversion at the Coin Smith | Right three-tier idea, but tiny SMP mod with its own ore; risky dependency. [Modrinth](https://api.modrinth.com/v2/project/new-horizons-coins) |
| **Never Enough Currency 2** (`never-enough-currency-2-forge`) | `6.0.0-beta.4-26.1.2` beta, 2026-07-15 | ARR | none listed | USD-style money, wallets, ATMs; "complete rewrite in progress" | Mobs drop money = max health / 8 (hard-coded, per description) | Rewrite in progress, ARR, USD theme. No. [Modrinth](https://api.modrinth.com/v2/project/never-enough-currency-2-forge) |
| **CTD Mint** (`ctd-mint`) | `26.1.2-1.6.1` beta, 2026-04-09; needs CTD Core | MIT | github.com/MasterGeneral156/CTD-Mint | USD-style currency from villager trades | Code | No. [Modrinth](https://api.modrinth.com/v2/project/ctd-mint) |
| **Fancy Coins** (`fancy-coins`) | `0.3.2.4`, 2026-09-02; 70 downloads | ARR | none | 11 craftable, placeable coins | Crafting | No. |
| **BUBUSTEIN's Money** / **Saro's Money** / **New Currencies** / **AVECOINS** / **Economy 経済** (`ogata-mizuki-economy`) | all have NeoForge 26.1.2 builds (13.1.0 2026-06-17; 3.6.6 2026-10-02; 1.1.0 2026-10-05; 2.5 2026-10-13; 1.0.1 2026-08-30) | all ARR | none / one GitHub folder | Real-world euro or "realistic" economies, banknotes, ATMs, banks, shops | Config / in-game | Real-money themes, ARR. Not for a WoW-style fantasy RPG. |
| **EconomyCraft** (`economycraft`) | `1.11.0`, 2026-10-16; server-only | GPL-3.0-only | github.com/PhilipB06/EconomyCraft | Balance-based server economy (`/pay`, shops) | Commands/config | No physical coins; villagers can't take a balance. No. |
| **Economy** (`ly-economy`) | `1.0.0+mod`, 2026-06-26 (datapack as mod) | LGPL-3.0-or-later | github.com/lullaby6/data-packs | Scoreboard economy | Datapack | No physical coins. No. |
| **CraftWallet** (`craftwallet`) | `26.1.2-1.1.0`, 2026-10-10 | ARR | none | Wallet for purchases (Bukkit/Paper-style) | - | No. |

### 1b. Well-known coin mods without a 26.1.2 NeoForge build

| Mod | Latest version | License | Note |
| --- | --- | --- | --- |
| Numismatic Overhaul (`numismatic-overhaul`) | Fabric/Quilt only, 1.21.1 | MIT | Terraria-style bronze/silver/gold at 100:1 with a purse; the closest concept to WoW copper/silver/gold, but not on our loader or version. [Modrinth](https://api.modrinth.com/v2/project/numismatic-overhaul) |
| Numismatic Overhaul: Neoforged (`numismatic-overhaul-neoforged`) | `4.0.0`, 2026-05-21, 1.21.1 only | MIT | Port, 1.5 k downloads. |
| Create: Numismatics (`numismatics`) | 1.20.1 / 1.21.1 | LGPL-3.0-only | Needs Create; not on 26.1.x. |
| Wallet (`wallet`) | `3.0.0+26.3` (NeoForge 26.3 only), 2026-09-18 | MIT | 7 denominations; skipped 26.1.2. |
| Magic Coins (`magic-coins`) | Modrinth: `1.1.3` NeoForge 1.21.1 only | custom | A web search result says a CurseForge file "26.1.0" lists 26.1.2 (**not verified**: curseforge.com blocked). |
| Simple Coins (`simple-coins`), Spare Change (`spare-change`) | 1.21.10 / 1.21.1 | ARR / MIT | Not on 26.1.2. |

### 1c. CurseForge-only candidates (not verified)

curseforge.com was unreachable from this session. A web search named **The Coinage** (copper, silver, gold coins; NeoForge
1.21.1 and 26.2 listed, apparently not 26.1.2), **Nomisma** (iron, copper, gold coins; random mob drops) and **Pocket
Money** (copper, silver, gold; NeoForge port without drops). None was confirmed for NeoForge 26.1.2. Check them on
CurseForge before deciding; packwiz takes CurseForge mods, but some authors block third-party distribution, which
packwiz's installer then cannot download.

---

## 2. Trade-editing and NPC mods

| Mod (slug) | NeoForge 26.1.2 build | License | Source | How trades are defined | Fit |
| --- | --- | --- | --- | --- | --- |
| **VillagerConfig** (`villagerconfig`) | `neoforge-4.6.0+26.1.2` release, 2026-09-23 (8 builds for 26.1.2); server-only | LGPL-3.0-only | github.com/DrexHD/VillagerConfig (main @ `5e1e016`) | Its own datapack trade format with an online generator (predates vanilla's data-driven trades; 4.6.0 removed its own number providers), plus config: `max_discount` (100 = vanilla, 0 = none), infinite trades, trade cycling, zombie conversion chance | With vanilla 26.1 trades data-driven, its format adds little. Its **`max_discount = 0`** config is the one useful bit for "prices never change" (it clamps `specialPriceDiff`, which covers reputation and Hero of the Village). [Modrinth](https://api.modrinth.com/v2/project/villagerconfig), [wiki](https://github.com/DrexHD/VillagerConfig/wiki/Creating-your-own-datapack) |
| **Villager API** (`villagerapi`) | `1.26.7.1` release, 2026-07-05 (one jar for 26.1-26.4 snapshots) | LGPL-3.0-only | github.com/frikinjay/villagerapi | JSON "villager packs" in a `villagerpacks/` folder (not a datapack): professions, POI/workstations, villager types, trades per level, hero gifts | Lets a pack add **new professions without Java**. We have a core mod, so registering two professions in code is simpler than a second content format outside `data/`. [Modrinth](https://api.modrinth.com/v2/project/villagerapi), [docs](https://github.com/frikinjay/villagerapi/blob/main/DOCUMENTATION_26_1.md) |
| **VillagerTradesEdit** (`villagertradesedit`) | `26.1.2-1.0.8-neoforge`, 2026-06-13; server-only | MIT | github.com/Flemmli97/VillagerTrades | In-game GUI (`/villagertrades edit`) edits one villager's offers, stored on that entity; infinite stock flag | Hand-edits single entities; not data-driven. Useful at most as an admin tool. [Modrinth](https://api.modrinth.com/v2/project/villagertradesedit) |
| **Villager Shop Edit** (`villager-shop-edit`) | `1.5+mod`, 2026-06-26 | MIT | GitHub (yusufoznam) | Player-run villager shops edited in survival | No. |
| **Better Villager Trades** (`villager-coin`) | `1.3.0`, 2026-07-01; server-only | Apache-2.0 | github.com/Rvhoyos/VillagerCoin | Config `config/villager_coin.json` `currencyItem` / `/villagercoin set <item>`: one item replaces emeralds in all trades | One currency only, no tiers. A vanilla datapack does the same with more control. [Modrinth](https://api.modrinth.com/v2/project/villager-coin) |
| **Easy NPC** (`easy-npc`, needs `easy-npc-core` + `easy-npc-config-ui`) | `7.16.0` release, 2026-10-16 (35 builds for 26.1.2) | MIT (code; assets excluded) | github.com/MarkusBordihn/BOs-Easy-NPC (branch `26.1.2` @ `c76cc6b`) | NPCs with dialogs and trading types NONE/BASIC/ADVANCED/CUSTOM, configured **in game** and stored in the entity; presets are gzipped NBT (`data/<ns>/preset/.../*.npc.nbt`). Does **not** use vanilla `villager_trade`/`trade_set` | Good for dialog NPCs; trades are not our JSON and JEI Trades cannot see them. Generating `.npc.nbt` presets from YAML is possible but awkward. [Modrinth](https://api.modrinth.com/v2/project/easy-npc) |
| EasyNPC x FTB Quests Compat (`easynpc-x-ftb-quests-compat`) | `26.1.2+1.0.2`, 2026-10-13 | MIT | none listed | NPC dialog lines gated by FTB quest completion; FTB task "talk to NPC" | Only relevant if we adopt Easy NPC for quest-givers. |
| Trading Post, Trade Cycling, Infinite Trading, Unlimited Villager Trades, Trade Picker, Reroll-Trades, Visible Traders, Retraining | all on 26.1.2 | various | - | QoL around vanilla trading, no currency | Not needed; Infinite/Unlimited Trading could replace restock logic for shops if wanted. |

### Trade display (JEI; EMI is not available)

| Mod | NeoForge 26.1.2 build | License | What it shows |
| --- | --- | --- | --- |
| **JEI** (`jei`, already in `pack/mods/jei.pw.toml`) | `29.47.0.111` beta, 2026-10-16 | MIT | No villager trade category of its own (JEI main @ `aa9927d`: no trade code outside a debug plugin). |
| **JEI / REI Trades** (`jei-trades`) | `1.2.2+neoforge-26.1`, 2026-08-20; client and server optional | GPL-3.0-only | github.com/Larsens-Mods/JEITrades (branch `mc26.1` @ `21591bb`). On server start it reads every `VillagerProfession#tradeSetsByLevel()` and the `trade_set` registry, sends them to the client and shows them in JEI/REI; the wandering trader through vanilla's three `wandering_trader/*` trade sets. **Must be installed on the server too** (single player: same jar). Any profession we register with trade sets shows up with no extra work; a custom non-villager entity with its own trade-set keys does **not**. [Modrinth](https://api.modrinth.com/v2/project/jei-trades) |
| Advanced Loot Info (`advanced-loot-info`) | `26.1.2-2.5.0` beta, 2026-10-16; needs Advanced Core Info | MIT | Loot tables (and villager trades per its summary) in JEI/EMI/REI: would also show coin drops of our loot tables. Trade support on 26.1.2 not checked in source. |
| Just Enough Resources (`just-enough-resources-jer`) | `1.10.1.40` **alpha**, 2026-07-27 | "Don't Be a Jerk" | Mob drops, villagers. |
| **EMI** (`emi`) | **none**: newest NeoForge build `1.1.24+1.21.1` (2026-05-13) | MIT | Not on 26.1.x. |
| **EMI Trades** (`emitrades`) | **none**: newest `1.3.0+mc1.20.4` (2024-01-13) | MIT | Abandoned since 1.20.4. |

---

## 3. Villager overhauls (brief)

| Mod | NeoForge 26.1.2 build | License | Currency or trades? |
| --- | --- | --- | --- |
| MCA Reborn (`minecraft-comes-alive-reborn`) | `8.1.11+26.1.2`, 2026-08-29 | GPL-3.0-only | Replaces villagers with humanlike NPCs (relationships, villages, marriage). No currency per its description; whether its entities keep vanilla professions and trade sets was **not verified**. A large overhaul that would restyle our merchants too. |
| Guard Villagers (`guard-villagers`) | `3.0.4`, 2026-06-11 | custom license | Adds guards, cleric healing, smith repairs; no currency or trade changes. |
| Villager Names (`villager-names-serilum`) | `26.1.2-8.5`, 2026-06-07; needs Collective (already in the pack) | ARR | Names only. |
| Easy Villagers (`easy-villagers`) | `neoforge-1.1.47+26.1.2` beta, 2026-10-11 | ARR | Villager items, trader and **auto-trader** blocks with configurable restock: no currency change, but an auto-trader turns a fixed-price shop into automation. |
| Humanoid Villagers (`humanoid-villagers`) | `1.0.3.2`, 2026-06-28 | Apache-2.0 | Player-skinned villagers; cosmetic. |
| Villager Guards (`villager-guards`), Homesteads, More Villagers: Re-employed (26.4 snapshots only) | - | - | Not checked in depth; none advertises a currency. |

---

## 4. Vanilla 26.1.x data-driven trades (verified in the 26.1.2 sources)

Source: decompiled Minecraft 26.1.2 in `build/mc-sources/` (`version.json`: world version 4790, data pack format
**101.1**): `net/minecraft/world/item/trading/{VillagerTrade,TradeSet,TradeSets,TradeCost,MerchantOffer}.java`,
`world/entity/npc/villager/{VillagerProfession,Villager,AbstractVillager}.java`,
`world/entity/npc/wanderingtrader/WanderingTrader.java`, `resources/RegistryDataLoader.java`, and the vanilla data in
`data/minecraft/`. Introduced in **26.1 Snapshot 1** (data pack version 95) per search results for
[minecraft.wiki: Java Edition 26.1 Snapshot 1](https://minecraft.wiki/w/Java_Edition_26.1-snapshot-1),
[minecraft.wiki: Villager trade definition](https://minecraft.wiki/w/Villager_trade_definition) and
[minecraft.net: Minecraft 26.1 Snapshot 1](https://www.minecraft.net/en-us/article/minecraft-26-1-snapshot-1)
(pages not fetched: blocked; the snapshot number comes from search summaries). The wiki summary also notes a
context-dependent integer form "upcoming in JE 26.3", so the format may change after 26.1.

### Two datapack registries

| Registry | Folder | One file is |
| --- | --- | --- |
| `minecraft:villager_trade` | `data/<ns>/villager_trade/<path>.json` | one offer |
| `minecraft:trade_set` | `data/<ns>/trade_set/<path>.json` | a pool: how many offers to draw from a set of trades |

Trades are grouped with **tags**: `data/<ns>/tags/villager_trade/<path>.json` (vanilla:
`#minecraft:armorer/level_1` holds `#minecraft:common_smith/level_1` plus four armorer trades). Both registries are
in `RegistryDataLoader.WORLDGEN_REGISTRIES`: loaded when the world or server starts, **not** by `/reload`, and not
synced to clients (which is why JEI Trades needs a server-side install).

### `villager_trade` fields (`VillagerTrade.CODEC`)

| Field | Type | Default | Meaning |
| --- | --- | --- | --- |
| `wants` | `{id, count?, components?}` (`TradeCost`) | required | First cost. `count` is a number provider, default 1; `components` an exact component predicate |
| `additional_wants` | same | none | Second cost slot |
| `gives` | item stack template `{id, count?, components?}` | required | Result |
| `max_uses` | number provider | **4** | Uses before restock (values < 1 become 1) |
| `reputation_discount` | number provider | **0** | Stored as the offer's `priceMultiplier`: scales demand and gossip-reputation price changes |
| `xp` | number provider | **1** | Villager XP per trade |
| `merchant_predicate` | loot item condition | none | Offer is skipped when false. Context `villager_trade`: `this` = the merchant entity, `origin` = its position; **no player** |
| `given_item_modifiers` | list of loot item functions | `[]` | Modify the result (enchant, set name, exploration map, `filtered` + `discard` to drop the offer) |
| `double_trade_price_enchantments` | enchantment holder set | none | Doubles the extra cost from `include_additional_cost_component` when the book has one of them |

Vanilla example (`data/minecraft/villager_trade/armorer/1/emerald_iron_boots.json`):

```json
{ "gives": { "id": "minecraft:iron_boots" }, "max_uses": 12.0, "reputation_discount": 0.2,
  "wants": { "count": 4.0, "id": "minecraft:emerald" } }
```

There is no "sells/buys" switch: a "villager buys" trade is `wants: 20 wheat`, `gives: 1 emerald`.

### `trade_set` fields (`TradeSet.CODEC`)

| Field | Type | Default | Meaning |
| --- | --- | --- | --- |
| `trades` | villager trade id, list or `#tag` | required | The pool |
| `amount` | number provider | required | How many offers to draw |
| `allow_duplicates` | bool | false | Draw with replacement |
| `random_sequence` | identifier | none | Named random sequence for deterministic draws |

Vanilla has 68 trade sets: `<profession>/level_1` ... `level_5` (amount 2 each; one has 3) and
`wandering_trader/buying` (2), `wandering_trader/common` (5), `wandering_trader/uncommon` (2). All 387 vanilla
`villager_trade` files use `minecraft:emerald`.

### How a merchant picks trades

- **Villagers:** `VillagerProfession` (a **built-in** registry, not datapack) carries
  `Int2ObjectMap<ResourceKey<TradeSet>> tradeSetsByLevel`. On each level-up `Villager#updateTrades` looks up the set
  for the new level and `AbstractVillager#addOffersFromTradeSet` draws `amount` offers (without duplicates unless
  allowed), skipping any offer whose `merchant_predicate` fails. A **new profession needs code** (or Villager API);
  a datapack can only change what the existing trade-set keys contain.
- **Wandering trader:** `WanderingTrader#updateTrades` draws from the hard-coded keys
  `minecraft:wandering_trader/buying`, `/uncommon`, `/common`. A datapack can rewrite all three; a second
  traveller with its own list needs a small entity class whose `updateTrades` calls `addOffersFromTradeSet` with our
  keys (the method is `protected` on `AbstractVillager`).
- **Mods:** NeoForge 26.1.2.116 has **no** `VillagerTradesEvent`/`WandererTradesEvent` any more (only
  `TradeWithVillagerEvent`); Lightman's Currency 26.1 also moved its trades into these registries and removed its
  config switches for them.

### Can a datapack replace emeralds everywhere?

Yes for every vanilla villager and the wandering trader: override the 387 `villager_trade` files (or override the
tags to point at our own trades). Limits that matter for coins:

- **Two cost slots**, each one item type: `wants` + `additional_wants`. A price can mix at most two denominations
  (e.g. 2 gold + 35 silver, never gold + silver + copper).
- **Each slot is clamped to the coin item's max stack size** (`TradeCost#toItemCost` and
  `MerchantOffer#getModifiedCostCount`): 64 by default, up to 99 with the `max_stack_size` component.
- **Price changes:** with `reputation_discount: 0` demand and gossip reputation change nothing, but **Hero of the
  Village always discounts** a `Villager`'s trades (`0.3 + 0.0625 x amplifier` of cost A, at least 1)
  (`Villager#updateSpecialPrices`). Fixed prices therefore need either our own merchant entity, cancelling or
  clamping that discount in code, or VillagerConfig's `max_discount: 0`.
- **No player in the predicate context**: offers are rolled per merchant, not per player, so chapter-gated stock
  needs separate merchants (or our own code); `location_check` on `origin` can vary stock by dimension/realm.
- **Levels:** `xp` defaults to 1, so a "no levels" merchant needs `xp: 0` on every trade (or only a level-1 set).
- Villagers restock at their workstation; the wandering trader never restocks.

---

## 5. Recommendation (the basis of the decision)

**Vanilla 26.1 data-driven trades + our own coin items, generated from YAML, with JEI Trades for display.** No
currency mod is needed for coin trades; Lightman's Currency is the one optional add-on worth a decision.

What it takes, by RLM-222's requirements:

1. **Coins** (copper, silver, gold): three items in `content/items/` through the existing item pipeline, stack size
   99. A third-party coin mod only saves three textures (RPG Coins, MIT) and none of the 26.1.2 coin mods supports a
   100:1 chain except Lightman's Currency.
2. **Fixed-price trades:** a new content type (say `content/merchants/`) that the generator turns into
   `data/realmforge/villager_trade/<merchant>/*.json`, a `trade_set` per merchant (`amount` = number of trades,
   `allow_duplicates: false`, `random_sequence` set) and its `tags/villager_trade/` file, with `xp: 0`,
   `reputation_discount: 0` and a large `max_uses`. Validation: at most two denominations per price, each ≤ 99.
3. **Ancient Builder and Ancient Specialist** (ADR 0034: ordinary professions with workstations, no levels): Java
   in the core mod registers two `PoiType`s (Ancient Casting, Ancient Workbench) and two `VillagerProfession`s
   whose `tradeSetsByLevel` holds only level 1, plus profession textures. Hero of the Village needs a fix in code
   (cancel or clamp the discount for our professions) to keep prices fixed. JEI Trades then lists them for free.
4. **Ancient Traveler:** a small `WanderingTrader` subclass whose `updateTrades` uses our trade sets, if it must be
   separate from vanilla's trader; otherwise rewrite vanilla's three wandering-trader sets. JEI Trades shows only
   the vanilla wandering trader's sets.
5. **Mob coin drops that grow with the story:** NeoForge global loot modifiers (`neoforge:add_table`, present in
   26.1.2.116) or entity loot tables generated from YAML, with `minecraft:location_check` per realm dimension (and,
   if wanted, our own condition on mob level from W1): copper in Overworld, silver in later realms, gold late.
6. **Quest shop (Random Perfect Gem):** a repeatable FTB Quests quest with an item task on coins that consumes
   them (`consume_items` on `ItemTask`, FTB-Quests main @ `3fc875b`, 26.1.2) and the gem as the reward, generated
   like the other quests. No shop mod needed.
7. **Display:** add **JEI / REI Trades** (`jei-trades`, GPL-3.0-only, both sides) next to JEI. EMI and EMI Trades
   have no 26.1.x builds, so the vision's "EMI Trades" wish cannot be met on 26.1.2.
8. **Change and conversion** is the real gap: crafting can convert at most 9:1, so a WoW-style 100:1 needs either
   (a) our own exchange (a recipe-free "coin pouch" or right-click conversion, small Java), (b) a 9:1 or 10:1 ratio
   in crafting (a design change for Matěj), or (c) **Lightman's Currency** with our three coins as its chain
   (`MasterCoinList.json`, exchange rate 100), which brings the wallet, the ATM and wallet-paid villager trades,
   at the cost of a beta 26.1 rework and a large feature surface to switch off in config (trading machines, banks,
   mint, its own coins and drops, structures). A decision for Matěj, not something to adopt by default.

What we build ourselves in every case: the coin items, the merchant YAML type and its generator (trades, trade
sets, tags), two professions and their workstations, the traveller entity if separate, the coin-drop loot data,
the quest-shop quests, and a fix for Hero of the Village. Nothing in this list needs a third-party mod except the
trade display.

## Not verified

- CurseForge-only mods (The Coinage, Nomisma, Pocket Money, Magic Coins' 26.1 file): curseforge.com blocked.
- minecraft.wiki / minecraft.net pages for the 26.1 snapshot: blocked; the field list above comes from the 26.1.2
  sources instead.
- Whether Advanced Loot Info shows villager trades on 26.1.2, and whether MCA Reborn keeps vanilla professions.
- Lightman's Currency's behaviour with our own coin items in practice (only the config format and source were read).
