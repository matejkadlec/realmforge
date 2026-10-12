# Hytale dungeon and endgame mods: Major Dungeons, Endgame & QoL and peers (state on 2026-10-12)

> Appendix to [the reference-game research](../reference-games.md) (RLM-94), written on 2026-10-12 by an AI research agent. Local citations name their source (the Craft to Exile 2 1.1.3 instance and the jars it ships, the Mine and Slash and Advent of Ascension source trees, the CTE2 GitHub changelogs); those files are not in this repository. Mechanics and numbers only: never reuse names, assets or code of the reference games.


Source notes: CurseForge project pages answer WebFetch but hide comments behind sign-in, and plain curl gets a 403. Project metadata, full descriptions and file lists came from the cfwidget JSON mirror of CurseForge (`api.cfwidget.com`), which is a cache and can lag by days. Both mods publish their wikis as public GitHub repos ([Dan-Mizu/MajorDungeonsWiki](https://github.com/Dan-Mizu/MajorDungeonsWiki), [lewai1/endgameqolwiki](https://github.com/lewai1/endgameqolwiki)), and I read those in full. They are the authors' own documentation, so they are primary sources. Download counts are lifetime totals as of 2026-10-12.

## 1. Major Dungeons: authors, history, popularity, dungeons, entry, layout, enemies and bosses, combat vs puzzle, loot, difficulty, multiplayer

### Takeaway
Major Dungeons has two parts. One is a data-driven dungeon framework (JSON only, no Java needed) by DanBagh. The other is MAJOR76's content pack: one handcrafted three-part series, *Dungeon of Fear I–III*. Each run is a fresh, timed instance (30/30/40 min) opened by using a consumable "Fragment" key on the vanilla Ancient Gateway. The final boss drops an exit key to a treasure room and gives every damage contributor a treasure bag. That bag holds the next dungeon's key at 100%. A merchant who takes a dungeon-only currency (the Nameless God, paid in Dark Coins) works as a safety valve against both lockout and bad luck. As of 2026-10-12 the mod has 148K downloads and its last release was on 2026-08-27.

### Cited Findings
**Identity, authors, popularity, history**
- CurseForge title "MAJOR DUNGEONS", project ID 1440458. Summary: "Custom instanced dungeons. New weapons, armor, mounts, merchants, and much more." Created 2026-01-22. 148,426 total downloads. Owner MAJOR76. Categories: Mobs/Characters, Miscellaneous, Furniture, World Gen, Gameplay. — [cfwidget mirror](https://api.cfwidget.com/1440458)
- The CurseForge page lists MAJOR76 as Owner and DanBagh as "Lead Pack Developer". It shows 574 followers, 365 comments, the All Rights Reserved license and game versions Early Access, 0.5 and 0.6. Credits: BgsMars made the Lighter Sword that became the basis for the "HellFire" and "Van Helsing" swords. Nightshark "helped create Dungeon of Fear III". — [CurseForge](https://www.curseforge.com/hytale/mods/major-dungeons)
- The framework's Java API lives in package `com.danbagh.dungeonframework.api` (`DungeonFrameworkAPIProvider.get()`), so the framework is DanBagh's code. — [MD wiki: Plugin API (GitHub source)](https://github.com/Dan-Mizu/MajorDungeonsWiki/blob/main/wiki/Modder-Tutorials/Plugin-API.md)
- DanBagh also owns "Stunning Combat" (parries, shield-bash stuns, damage-type weaknesses and resistances, crits; 17,041 downloads). — [cfwidget: stunning-combat](https://api.cfwidget.com/hytale/mods/stunning-combat)
- Release history (17 files listed): 0.2.8 (2026-02-17), 0.3.0 (02-21), 0.4.0 (03-07), 0.4.6 (03-27, 26,395 downloads), 0.4.7 (05-26, first build for game version 0.5), 0.4.12 (07-02), 0.4.13 (2026-08-27, game version 0.6, 22,864 downloads). Nothing has shipped since 2026-08-27. — [cfwidget mirror](https://api.cfwidget.com/1440458), [CurseForge files](https://www.curseforge.com/hytale/mods/major-dungeons/files/all)
- Update headlines shown on the page: "Dungeon Of Fear III, Devil Booster Packs, Boss Bars, and new Weapons + Armors!", "Treasure Bags, Mimics, Mounts, and new Artifacts + Weapons + Armor!" and "Hardcore, Lore, Relics, Deals, & Familiars" (0.4.7). — [CurseForge](https://www.curseforge.com/hytale/mods/major-dungeons)
- Wiki history: wiki created 2026-04-14, modder tutorials 2026-04-27, "update 0.4.7+ content updates" on 2026-06-01 (familiars, Dark Silver armor), "remove multiple hud dependency" on 2026-06-02, "0.4.13 update" on 2026-08-26 (Plugin API docs). — [MajorDungeonsWiki commits](https://github.com/Dan-Mizu/MajorDungeonsWiki/commits/main)
- The wiki says the mod was "Inspired by games such as Terraria, The Legend of Zelda, and Skyrim!" It describes the mod as "a combination of a framework that adds dungeon and RPG specific features that other modders can use … and an asset pack that implements the content", usable "requiring zero Java code". — [MD wiki: Getting Started](https://wiki.hytalemodding.dev/mod/major-dungeons/getting-started)
- Trailer video: [YouTube gN6I70YUzu8](https://www.youtube.com/watch?v=gN6I70YUzu8). It is linked from the wiki, but I did not watch it.

**Dungeons and theme**
- Only one series exists, *Dungeon of Fear*: "You'll gradually descend deeper and deeper into the Kingdom of Baal. You'll visit various parts of his kingdom and encounter his deadly minions." — [CurseForge](https://www.curseforge.com/hytale/mods/major-dungeons)
- Dungeon of Fear I: 30-minute limit ("Remaining within after the time limit elapses results in death"). The final boss, Azaroth, drops the Exit Key to the Treasure Room, "to be able to escape". Recommended: Iron gear, 1 player, "Plenty of Potions". — [MD wiki: DoF I](https://wiki.hytalemodding.dev/mod/major-dungeons/dungeon-of-fear-i)
- Dungeon of Fear II: 30 min. It has an extra Treasure Room opened with a Gold Dungeon Key. Boss: Katherina. Recommended: Cobalt gear, 1–2 players. — [MD wiki: DoF II](https://wiki.hytalemodding.dev/mod/major-dungeons/dungeon-of-fear-i-i)
- Dungeon of Fear III: 40 min, plus an extra Gold-key Treasure Room. "There are two Mini-Bosses required to defeat in order to progress as they drop the red Mini-Boss Keys, as well as a Green Key obtained via a Parkour challenge." Boss: Baron. Recommended: Adamantite gear, 2–4 players. — [MD wiki: DoF III](https://wiki.hytalemodding.dev/mod/major-dungeons/dungeon-of-fear-i-i-i)
- The Nameless God's trade list includes "Fragment: Dungeon of Fear IV" (4 Dark Coins, Tier 2), but the wiki has no DoF IV page. — [MD wiki: Nameless God](https://wiki.hytalemodding.dev/mod/major-dungeons/nameless-god)

**Entry mechanics (keys, portals, instances)**
- The dungeons are instanced and reached through the vanilla Ancient Portal, which unlocks after the player reaches 100 Memories. The player crafts the Ancient Gateway at the Arcanist's Workbench, crafts "Fragment: Dungeon of Fear I", "and use[s] it on the Ancient Gateway to summon a portal". — [MD wiki: Getting Started](https://wiki.hytalemodding.dev/mod/major-dungeons/getting-started)
- Fragment I costs Cobalt Ingot ×20, Gold Ingot ×20, Red Crystal Shards ×10 and Cindercloth Scraps ×5 (5 s at the Arcanist's Workbench), or 1 Dark Coin at the Nameless God. — [MD wiki: Fragment DoF I](https://wiki.hytalemodding.dev/mod/major-dungeons/fragment-dungeon-of-fear-i)
- Fragment II drops at 100% from the DoF I treasure bag or costs 2 Dark Coins. Fragment III drops at 100% from the DoF II bag or costs 3 Dark Coins (Nameless God Tier 2). — [Fragment II](https://wiki.hytalemodding.dev/mod/major-dungeons/fragment-dungeon-of-fear-i-i), [Fragment III](https://wiki.hytalemodding.dev/mod/major-dungeons/fragment-dungeon-of-fear-i-i-i)
- The fragments are "one-use crystals". "If you fail the final boss of a level, you'll have to start over from the beginning, as the one-use crystals required for entering subsequent dungeons after the first drop only from bosses. But there's a workaround: you can buy them from a merchant — a Nameless God." — [CurseForge](https://www.curseforge.com/hytale/mods/major-dungeons)
- Framework mechanics (in an unpublished draft page): a portal key item carries `PortalKey.PortalType` and `TimeLimitSeconds` (1800 = 30 min). The `Temporary` tag makes the key consumed on use. The player "is teleported into a fresh copy of the dungeon instance", and on exit or timeout returns "to where they came from". — [MD wiki source: Basic Dungeon Setup (draft)](https://github.com/Dan-Mizu/MajorDungeonsWiki/blob/main/wiki/Modder-Tutorials/Basic-Dungeon-Setup.md)
- The portal UI shows one tab per difficulty mode next to a Summon button, so the player picks the mode before entry. An instance can float a preview image of its destination in front of the portal block while the instance is alive. The instance HUD shows a title, a subtitle, a timer bar and a mode tag. — [MD wiki: Instance Config](https://wiki.hytalemodding.dev/mod/major-dungeons/instance-config)

**Layout generation**
- Dungeons are built by hand in-game with Hytale's prefab and instance tools (`/editprefab`, `/prefab save`) and saved as an instance template (`Server/Instances/<id>/instance.bson`, "a binary world snapshot"). Each entry loads a fresh copy of that template. — [MD wiki source: Basic Dungeon Setup (draft)](https://github.com/Dan-Mizu/MajorDungeonsWiki/blob/main/wiki/Modder-Tutorials/Basic-Dungeon-Setup.md)
- Variation between runs comes from block spawners. A chest-spawner entry can roll as a normal chest or a mimic (the example weights are 75 chest / 25 mimic), and "the chosen block bakes into the chunk when the chunk first loads". — [MD wiki: Instance Config](https://wiki.hytalemodding.dev/mod/major-dungeons/instance-config), [Mimic Blocks](https://wiki.hytalemodding.dev/mod/major-dungeons/mimic-blocks)

**Enemies and bosses**
- The mod has "completely New Enemies and Bosses". — [CurseForge](https://www.curseforge.com/hytale/mods/major-dungeons)
- Azaroth has 3,000 HP. Katherina has 3,800 HP and "Will be surrounded by several Ancient Dark Titan Constructs". Baron has 4,000 HP. Each boss "Drops a single Exit Key for the dungeon's treasure room on death, and provides a Treasure Bag … to each player that dealt damage." — [Azaroth](https://wiki.hytalemodding.dev/mod/major-dungeons/azaroth-dungeon-of-fear-i), [Katherina](https://wiki.hytalemodding.dev/mod/major-dungeons/katherina-dungeon-of-fear-i-i), [Baron](https://wiki.hytalemodding.dev/mod/major-dungeons/baron-dungeon-of-fear-i-i-i)
- Boss framework: an animated boss bar appears for nearby players, every damage contributor is tracked, and "Every player who dealt damage to the boss receives one roll of this drop list". — [MD wiki: Bosses](https://wiki.hytalemodding.dev/mod/major-dungeons/bosses)
- "The dungeon and boss difficulty are based around the parrying and stuns implemented by" Stunning Combat, which is recommended but not required. — [CurseForge](https://www.curseforge.com/hytale/mods/major-dungeons), [MD wiki: Getting Started](https://wiki.hytalemodding.dev/mod/major-dungeons/getting-started)

**Combat vs puzzle**
- Gating uses locked doors and keys: the key is consumed, the block turns into its unlocked variant, and a player without the key gets a notice naming the required key. Other gating elements are the miniboss keys and the parkour key in DoF III, plus mimic chests. No puzzle mechanic is documented anywhere. — [Locked Doors and Keys](https://wiki.hytalemodding.dev/mod/major-dungeons/locked-doors-and-keys), [DoF III](https://wiki.hytalemodding.dev/mod/major-dungeons/dungeon-of-fear-i-i-i)

**Loot and rewards**
- Unique weapons and armor come as boss and completion rewards, "which drops at a certain rate, just like in Terraria". — [CurseForge](https://www.curseforge.com/hytale/mods/major-dungeons)
- Treasure Bag: Dungeon of Fear I: Fragment II 100%, Dark Coin 70%, Greater Health Potion 70%, Ruby 50%, Mithril Ore 50%, Storm Leather 1–3 (30%), Adamantite Ore 5–8 (30%), Azaroth Vampire Longsword 10%, and a Devil Card pool at 20% (Common 82% / Legendary 15% / Ultra 3%). — [Treasure Bag DoF I](https://wiki.hytalemodding.dev/mod/major-dungeons/treasure-bag-dungeon-of-fear-i)
- The Hardcore version of the same bag has better odds: Dark Coin 1–2 at 80%, Longsword 20%, Dying Star Gloves 5%, a familiar pool at 30% (six familiars at about 3.5% each, Red Dragon Frog about 9%) and Devil Cards at 40% (Ultra 5%). Its source file is flagged `draft: true`. — [Treasure Bag DoF I (Hardcore)](https://wiki.hytalemodding.dev/mod/major-dungeons/treasure-bag-dungeon-of-fear-i-hardcore)
- The Nameless God is "Found in every treasure room in every dungeon" and accepts only Dark Coins, which "Can be found in Dungeons of Fear". His shop sells:
  - Weapons: Hellfire (4 DC), Van Helsing (15 DC), and Mogruith (90 Thorium Ingot + 30 Concentrated Life Essence + 20 DC), with upgrade chains Mogruith → Silver → Superior Silver and Silver Grave → Superior.
  - Armor: the Silver Cult set and the Dark Silver and Charged Dark Silver helmets.
  - All Fragment keys.
  - Two benches: the Nameless God itself (1 DC) and the Mjolnir Furnace (3 DC).
  - Devil Card Booster Packs (20 DC) and the Gold Dungeon Key (10 DC).
  - Decorative build blocks.
  - His Tier 2 upgrade costs 30 DC + 30 Dark Silver Ingot + 60 Bolt of Dark Silver Cloth + a Silver Skull.

  — [Nameless God](https://wiki.hytalemodding.dev/mod/major-dungeons/nameless-god), [Dark Coin](https://wiki.hytalemodding.dev/mod/major-dungeons/dark-coin)
- Mjolnir Furnace (has a Tier 2 upgrade): smelts Dark Silver Ingot (Moon Salt + Silver Ingot) and crafts mounts, for example Armored Horse = Horse + 50 Cobalt + 50 Iron, and Hell Wolf = 3 Black Wolves + 150 Adamantite Ingot + Devil Card "Wolf" + Void Grimoire. It also crafts artifacts and the Mimic Familiar. — [Mjolnir Furnace](https://wiki.hytalemodding.dev/mod/major-dungeons/mjolnir-furnace), [Hell Wolf](https://wiki.hytalemodding.dev/mod/major-dungeons/hell-wolf)
- Devil Cards are a collectible meta-economy. The Devil Merchant wanders the surface plains of Zone 1, "Their trades restock daily", and they trade card scraps for booster packs. The merchant follows players holding the Devil Stone artifact, and the Golden Devil Stone summons one. — [Devil Merchant](https://wiki.hytalemodding.dev/mod/major-dungeons/devil-merchant), [Booster Pack](https://wiki.hytalemodding.dev/mod/major-dungeons/devil-card-booster-pack)
- "Treasure Bag: Silver Moon" (bought for 5 DC + Mimic's Tongue) has a 3% rare pool and a 2% ultra pool. The Silver Moon Wolf Spirit mount sits in that ultra pool at about 1%. — [Silver Moon bag](https://wiki.hytalemodding.dev/mod/major-dungeons/treasure-bag-silver-moon), [Silver Moon Wolf Spirit](https://wiki.hytalemodding.dev/mod/major-dungeons/silver-moon-wolf-spirit)
- Catalogue (wiki navigation): 11 weapons, 15+ armor pieces, 7 mounts and 12 cosmetic familiars. Familiars are "invulnerable" and "intangible", one at a time. Mounts are "disabled by default inside instance worlds whose names match `*Dungeon_Of_Fear*`" and dismount when the mount or rider takes damage. — [MD wiki](https://wiki.hytalemodding.dev/mod/major-dungeons/getting-started), [Familiars](https://wiki.hytalemodding.dev/mod/major-dungeons/familiars-1), [Summonable Mounts](https://wiki.hytalemodding.dev/mod/major-dungeons/summonable-mounts)
- "Instance objectives" (also called "Devil's Deals"): when a player enters carrying an item tagged `InstanceObjectiveItem`, a weighted random objective is assigned. Task types are `KillNPC` and `UseBlockOnce` (for example, open 5 chests). The final task is always `LeaveInstance`. The reward is paid only on a voluntary exit; "If the instance timer forces them out, the objective is cancelled with no reward." — [Instance Objectives](https://wiki.hytalemodding.dev/mod/major-dungeons/instance-objectives)
- Other framework features: loot packs (consumable "open me" items rolling drop lists, with an extra sound for `_Ultra` items); mutating barter shops (trade lists re-rolled from pools "every time the server starts"); tabbed barter shops; armor set bonuses that can depend on time of day or moon phase. — [Modder Tutorials](https://wiki.hytalemodding.dev/mod/major-dungeons/modder-tutorials), [Loot Packs](https://wiki.hytalemodding.dev/mod/major-dungeons/loot-packs), [Mutating Barter Shops](https://wiki.hytalemodding.dev/mod/major-dungeons/mutating-barter-shops), [Armor Set Bonuses](https://wiki.hytalemodding.dev/mod/major-dungeons/armor-set-bonuses)

**Difficulty scaling**
- Difficulty "Modes" are set per instance. The documented Hardcore example uses `TimeLimitSeconds` 1500, PvP on, mob HP ×3, mob damage ×2, boss HP ×4, boss damage ×2, plus drop-list and chest-spawner swaps to `_Hardcore` variants. It also sets `DeathOverrides` to `RespawnController: ExitInstance` and `ItemsLossMode: CONSUME`, which wipes the inventory and shows "You've Lost Everything...". Server owners can retune the modes in `mods/MAJOR76_MajorDungeons/InstanceModifiers/`. Leaving voluntarily "always preserve[s] inventory". — [Instance Config](https://wiki.hytalemodding.dev/mod/major-dungeons/instance-config)
- Option `RemoveSpawnReturnPortal` removes the return portal at the instance spawn "when the only exit should be through a boss door". `DisableNpcFriendlyFire` defaults to true. — [Instance Config](https://wiki.hytalemodding.dev/mod/major-dungeons/instance-config)

**Multiplayer**
- Recommended party sizes are 1, then 1–2, then 2–4. Each damage contributor gets a bag. Boss rewards drop "as per-player owned item entities" (`BossKillRewardsAsDrops`). "Each player who enters spawns their own world (or shares depending on your `InstanceWorldConfig`)." Plugin API events: `DungeonEnteredEvent`, `DungeonExitedEvent`, `DungeonCompletedEvent`, and `BossKilledEvent` (which carries `attackerUuids`). — [DoF pages](https://wiki.hytalemodding.dev/mod/major-dungeons/dungeon-of-fear-i-i-i), [Instance Config](https://wiki.hytalemodding.dev/mod/major-dungeons/instance-config), [Plugin API source](https://github.com/Dan-Mizu/MajorDungeonsWiki/blob/main/wiki/Modder-Tutorials/Plugin-API.md)

### Inferences
- The progression loop is fully linear and key-chained: craft key I, clear I, get bag (key II), clear II, get bag (key III). The coin vendor turns a fragile chain (one failed boss means starting over) into a soft currency loop. That is the same shape as Realmforge's "boss drops the item that opens the next dungeon", with a safety valve the owner's loop also needs.
- Layouts are handcrafted and fixed. Replay variety comes only from rolled chests and mimics, loot, and the Hardcore mode. That suits a three-dungeon "campaign" but goes stale with repetition.
- Puzzles are close to 0%. The non-combat gating is keys, locked doors and one parkour key. Every progression step is combat or loot.
- The mod has a lot of collectible side systems (cards, scraps, familiars, wolves, mimic tongues, artifacts) on top of only three dungeons. For Realmforge's vision this is the "gazillion systems" risk.

### Gaps
- No boss attack lists or phase descriptions exist on the wiki. The wiki pages give only HP and drops, so boss mechanics (phases, telegraphs) could not be verified, and I did not watch the trailer.
- Enemy roster per dungeon: not documented. Only the "Ancient Dark Titan Constructs" are named.
- Party mechanics: how a second player joins a run, and whether the portal stays open for others, is not documented.
- Comment text (365 comments) is hidden behind CurseForge sign-in.
- Why the earliest listed file is 0.2.8 (2026-02-17) when the project was created 2026-01-22: older files may have been removed. Unverified.
- Whether DoF IV is planned: it appears only as a trade entry.

## 2. Endgame & QoL: endgame systems (tiers, modifiers, waves, bosses, gear, repeatable loops), QoL features, configuration

### Takeaway
Endgame & QoL by Lewaii (about 481K downloads, created 2026-01-16) is the most-downloaded endgame-content mod I found. Only leveling and RPG-system mods have more downloads. It is a single "second act" after vanilla's Adamantite cap:
- One craftable gateway block with three consumable keys leads to three handcrafted instances in order: Frozen, Swamp, then the Void Realm dimension.
- A four-step material ladder runs Adamantite → Mithril → Onyxium → Prisma.
- Three phased bosses and six elites.
- A reusable wave engine powers Warden's Trials I–IV and the Void Gauntlet.
- Random "temporal" challenge portals spawn in three difficulty tiers.
- Boss pets go from tier D to SS and become mounts, including a flying mount.
- A daily and weekly meta layer: bounties with reputation, achievements, a bestiary and a combo meter.

Everything can be tuned live in an eight-tab config UI. Its "QoL" is mostly UI and convenience; it adds no inventory, map or travel tools.

### Cited Findings
**Identity, popularity, history, licensing**
- Project ID 1431338, created 2026-01-16, 481,172 downloads, owner Lewaii, categories Gameplay and Quality of Life. The current title is "Endgame & QoL - Big update (12th October)". Summary: "Adds a real endgame to Hytale: 3 multi-phase bosses, 6 elites, 6 themed dungeons + the Void Realm dimension, rideable pet companions, temporal portals, Warden's Trials and 150+ items." — [cfwidget mirror](https://api.cfwidget.com/hytale/mods/endgame-qol)
- At fetch time the CurseForge page showed 478.6K downloads, about 1.5k comments, main file 5.4.2 (0.6), "No required dependencies" and All Rights Reserved. The description header still said v5.4.1. — [CurseForge](https://www.curseforge.com/hytale/mods/endgame-qol)
- Release history: 39 files listed, from 4.0.0 (2026-03-01) through 5.0.3 (04-19), 5.2.0 (05-27, first for 0.5), 5.3.0 (06-28) and 5.4.0 (08-27, first for 0.6) to 5.4.2 (2026-09-22). — [cfwidget mirror](https://api.cfwidget.com/hytale/mods/endgame-qol)
- Older versions existed in January and February, for example "Boss Update 1 - EndgameAndQoL2.1.0.zip" and EndgameAndQoL3.5.6.jar ("3.5.0 OUT !"). — [CF file 7504888](https://www.curseforge.com/hytale/mods/endgame-qol/files/7504888), [CF file 7644347](https://www.curseforge.com/hytale/mods/endgame-qol/files/7644347)
- License: "The compiled JAR is free to install and run … The source code, assets and any compiled artifact may not be copied, modified, redistributed, mirrored, decompiled or used in derivative works." Credits: ReyZ41 made "Models, textures, and all dungeon builds", georgegroeg made models and textures, and narwhals fixed attack hit timing. Thumbnail: "100% hand-made (no AI)". — [cfwidget mirror](https://api.cfwidget.com/hytale/mods/endgame-qol)
- Monetization changed over time. The June 2026 wiki describes Patreon tiers that unlocked server-owner perks through Ed25519-signed license tokens: overworld bosses, an "ad-free Journal" that hides Featured Servers, rebranding, and a Featured Listing. — [E&QoL wiki: Premium & Patreon](https://wiki.hytalemodding.dev/mod/endgame-qol/premium-patreon)
- The current description says instead: "completely free — every feature, for everyone. No license keys, no premium ranks, no locked content", with Ko-fi supporter credits. It keeps a "Partner Server" tab in the Journal, "hideable in the config". — [cfwidget mirror](https://api.cfwidget.com/hytale/mods/endgame-qol)
- Intent: "Once you've reached Adamantite tier in vanilla and the world starts feeling empty, this is what's next." Roadmap: "✅ Void Realm ➡️ ✅ Temporal Portals ➡️ ✅ Wave Arenas ➡️ 🏰 Intermediate Dungeons ➡️ 🌀 More Temporal Challenges". — [E&QoL wiki: Introduction](https://wiki.hytalemodding.dev/mod/endgame-qol/introduction), [cfwidget mirror](https://api.cfwidget.com/hytale/mods/endgame-qol)

**Gateway, keys, instances**
- The Endgame Gateway is a craftable portal block (Epic, Endgame Bench Tier 1, recipe 30 Adamantite Bar + 10 Cobalt Bar + 30 Fire Essence). Right-clicking it with a key gives it that key's theme (cyan, green or purple). "A return portal inside the dungeon brings you back to the exact gateway you came from." When the last player leaves, the gateway resets to neutral and can be reused "as many times as you want". A vanilla Portal Device rejects Endgame keys and shows a chat redirect. — [E&QoL wiki: Portals & Gateways](https://wiki.hytalemodding.dev/mod/endgame-qol/portals-gateways)
- "The key is consumed on use." — [E&QoL wiki: Frozen Dungeon](https://wiki.hytalemodding.dev/mod/endgame-qol/frozen-dungeon)
- Recommended order: Frozen → Swamp → Void Realm. — [E&QoL wiki: Introduction](https://wiki.hytalemodding.dev/mod/endgame-qol/introduction)

**Dungeons: layouts, enemies, bosses, loot**
- All dungeon builds and the three temporal interiors are handcrafted by ReyZ41. — [cfwidget mirror](https://api.cfwidget.com/hytale/mods/endgame-qol)
- **Frozen Dungeon** ("Multi-level ice cavern"):
  - Enemies: Ice Rat 35 HP, Frost Toad 124, Frost Feran 150, Frost Spirit 200, Frost Crystal Golem 300, Frost Yeti 400.
  - Currency: every mob drops "Flocons". Hidden trader Korvyn sells the Frostbite Blade for 45 Flocons, Mithril armor pieces for 12–18, and potions. He "restocks daily", with limited stock on gear.
  - Chest zones rise in rarity with risk and secrecy: Rat Zone (Common) → Feran Zone (Uncommon) → Tower (Rare) → Hidden Side / Hidden Underwater (Epic) → Dragon Zone (Legendary).
  - Boss Dragon Frost (1,400 HP) has three HP-gated phases with "alternating immunity": Sky Sentinel (>70%, flying, melee-immune), Ground Fury (70–40%, projectile-immune), and Hybrid Frenzy (<40%, cycling every 8–10 s). Hitting it with an immune damage type "fires a one-time chat hint".
  - The Dragon Heart drops at 100% and is "Required to upgrade the Endgame Bench", which in turn unlocks the Swamp key.

  — [Frozen Dungeon](https://wiki.hytalemodding.dev/mod/endgame-qol/frozen-dungeon), [Bosses & Elites](https://wiki.hytalemodding.dev/mod/endgame-qol/bosses-elites)
- **Swamp Dungeon**:
  - The Hedera Key is crafted at the Hedera Autel, "a special bench found in the swamp biome". Ingredients: Hedera's Bramble (Bramble Elite drop), Swamp Gem (mining), Swamp Ingot (smelted Swamp Ore), Crocodile Scale (Swamp Crocodile drop) and Infused Rope.
  - Enemies: Grooble 250 HP, Fen Stalker 200, Root Spirit 200, Swamp Crocodile 900 (elite), Bramble Elite 550 (mini-boss).
  - Locked doors. Chest tiers are Common, Rare, Epic and Hidden ("Behind breakable walls and underwater passages"). Trader Morghul.
  - Boss Hedera (1,800 HP): Vine Growth phase, then Toxic Bloom (Vine Grab, Ground Slam with a 7-block AOE and Stagger, poison clouds every 18–25 s), then Corrupted Fury (adds Charge).

  — [Swamp Dungeon](https://wiki.hytalemodding.dev/mod/endgame-qol/swamp-dungeon), [Bosses & Elites](https://wiki.hytalemodding.dev/mod/endgame-qol/bosses-elites)
- **Void Realm** (a new dimension since v5.0.0, a floating-island arena):
  - The Void Gauntlet auto-starts on entry: 3 waves, 360 s, "Leash: None". "On clear, the Golem Void spawns at the arena center."
  - Golem Void: 3,500 HP, knockback-immune, phases Void Awakening → Rift Surge → Void Collapse, signature Jump Slam (telegraphs, teleports above the target, crashes), and an 8 m Danger Zone DoT aura.
  - "Falling off the floating island no longer kills you… 'The Void pulls you back.' — zero death penalty."
  - A dimension-wide combat music track plays.
  - The Void Architect's Tome drops from chests and teaches 10 decorative block recipes.

  — [Void Realm](https://wiki.hytalemodding.dev/mod/endgame-qol/void-realm), [Bosses & Elites](https://wiki.hytalemodding.dev/mod/endgame-qol/bosses-elites)
- **Shared boss rules**:
  - 3 s of invulnerability on phase transitions.
  - Boss HP +50% per extra player: `multiplier = 1.0 + (playerCount − 1) × (scalingPercent/100)`.
  - Enrage when the boss takes more than 200 damage in 5 s: ×1.5 damage for 8 s, 15 s cooldown.
  - Weighted target switching every 8 s.
  - `Endgame_Stagger` (a 1 s stun) on heavy AOE.
  - The proximity boss bar shows within 50 blocks, hides at 55, and only one bar is visible at a time.
  - "Instance-locked bosses" cannot spawn outside their instances.
  - "Shared boss kill credit" for everyone in the instance.

  — [Bosses & Elites](https://wiki.hytalemodding.dev/mod/endgame-qol/bosses-elites), [cfwidget](https://api.cfwidget.com/hytale/mods/endgame-qol)
- "Shared Loot — every player who participated in a co-op mob kill inside a dungeon gets their own independent loot roll." — [E&QoL wiki: Introduction](https://wiki.hytalemodding.dev/mod/endgame-qol/introduction)

**Tiers, gear upgrades, modifiers**
- Material ladder:
  - Adamantite: the "Vanilla survival cap".
  - Mithril: from Frozen, the Oakwood Warlord and the Eldergrove Despot; Mithril armor has mana regen.
  - Onyxium: from Swamp and Golem Void; Onyxium armor has HP regen.
  - Prisma: Golem Void only; every Prisma weapon has a "Unique signature attack". Golem Void drops Onyxium OR Prisma at 60/40.

  — [Introduction](https://wiki.hytalemodding.dev/mod/endgame-qol/introduction), [Bosses & Elites](https://wiki.hytalemodding.dev/mod/endgame-qol/bosses-elites)
- Gear: "130+ items" (the wiki says 145+): 40+ weapons, armor sets Mithril / Onyxium / Prisma (Prisma "70% Phys / 45% Fire / Fall resist"), Prisma tools with 3×3 area break, combat potions in Common→Epic grades, and Prisma signature abilities per weapon class (for example a Daggers "Vanish" with i-frames, and a Staff "Void Beam" gated by a Signature Energy bar). A "Full weapon tier rebalance" gives a "clean +30% damage step at every tier". — [cfwidget](https://api.cfwidget.com/hytale/mods/endgame-qol), [Introduction](https://wiki.hytalemodding.dev/mod/endgame-qol/introduction)
- Difficulty presets (HP × / damage ×): Easy 0.6/0.5, Medium 1/1, Hard 1.5/1.5, Extreme 2.5/2.0, Custom 0.1–10. No per-run modifiers or affixes are documented for E&QoL dungeons. — [Bosses & Elites](https://wiki.hytalemodding.dev/mod/endgame-qol/bosses-elites)

**Wave modes**
- Warden's Trial: a crafted Warden Challenge item (Tier I–IV) starts 5 waves:
  - States: COUNTDOWN 3 s → SPAWNING → ACTIVE → WAVE_CLEAR → INTERVAL 8 s, ending in COMPLETED or FAILED.
  - Timers: 4:30 / 6:00 / 7:30 / 9:00.
  - Final-wave bosses: Tier I has none (the CurseForge page says every trial ends on a boss wave; the wiki says Tier I has none). Tier II: Goblin Duke; Tier III: Necromancer Void; Tier IV: Shadow Knight "+ full roster".
  - A 50-block leash only warns; it does not fail the trial.
  - The trial fails on death, disconnect or timer expiry.
  - XP = Tier × 150. Each tier's drops match its material tier. The rare tier reward is a 25% chance of a Prisma or Onyxium weapon.
  - Trials are blocked inside instances by default.

  — [Warden Trials](https://wiki.hytalemodding.dev/mod/endgame-qol/warden-trials), [cfwidget](https://api.cfwidget.com/hytale/mods/endgame-qol)
- Both wave modes run on `endgame.wavearena.*`, a "generic data-driven wave engine" with callbacks `onCountdown / onWaveStart / onWaveClear / onArenaCompleted`. — [cfwidget](https://api.cfwidget.com/hytale/mods/endgame-qol)

**Temporal portals (random challenge instances)**
- Particle-only portals spawn every ~1 h 30 (50% roll per attempt), 80–300 blocks from a player, with up to 3 at once server-wide. They respect land claims.
- Portals decay through STABLE → DESTABILIZING → CRITICAL → COLLAPSING, with chat warnings at 5 min and 1 min and a 30 s grace period.
- Default portal lifetime is 180 s and the instance limit is 900 s. `AllowRespawnInside` defaults to false, so death sends the player to the overworld.
- Spawn weights favour the chill instance: Canopy Shrine 50%, Oakwood 30%, Eldergrove 10%.
  - Canopy Shrine: "No boss, no elite — exploration only", with 3 chests.
  - Oakwood Refuge: the Oakwood Warlord (1,000 HP; Triple Strike, Scepter Shot, Ground Slam, and a death-summoned 400 HP husk; drops 2–4 Mithril).
  - Eldergrove Hollow: the Eldergrove Despot (1,800 HP pure caster with Ice Bolt, Fire Blast and Lightning, plus a spiked-shield block).

— [Temporal Portals](https://wiki.hytalemodding.dev/mod/endgame-qol/temporal-portals)

**Pets and mounts**
- There are 4 boss pets (Dragon Frost, Dragon Fire, Golem Void, Hedera). Drop rates conflict: 10–15% per main boss on CurseForge, but the wiki config shows `…UnlockChance` defaulting to 0.30.
- Pets are fed to climb tiers D → SS. Mounting unlocks at Tier C. The Frost Dragon "ride-and-fly hybrid" unlocks at Tier B+: sprint to climb, and empty stamina auto-consumes 5 Flocons.
- Tier S grants a perk (for example 100% element immunity), and Tier SS adds an aura that hits mobs within 5 blocks every 2 s. Pet damage multipliers run from 1.4× (D) to 3.5× (SS). An "Equip/Ghost" mode at S+ gives the perk without spawning the pet.

— [cfwidget](https://api.cfwidget.com/hytale/mods/endgame-qol), [E&QoL wiki: Configuration](https://wiki.hytalemodding.dev/mod/endgame-qol/configuration)

**Repeatable loops and meta systems**
- Bounty Board:
  - Each player gets "3 daily bounties (1 easy, 1 medium, 1 hard) plus 1 weekly", "deterministic per player per day".
  - 13 task types, including KILL_NPC, SPEED_KILL_BOSS, COMPLETE_TRIAL, DUNGEON_CLEAR, CRAFT_ITEM, MINE_ORE and EXPLORE_DUNGEON.
  - Bonus objectives: `combo_x3`, `combo_frenzy`, `at_full_hp`.
  - Reputation ranks: Novice 0 / Veteran 500 / Elite 1,500 / Legend 3,000. CurseForge counts "59 bounty templates across 4 tiers".

  — [Bounty Board](https://wiki.hytalemodding.dev/mod/endgame-qol/bounty-board), [cfwidget](https://api.cfwidget.com/hytale/mods/endgame-qol)
- Combo Meter: ×2 = 110% damage; ×3 = 125% + speed; ×4 = 150% + heal on kill + crit; FRENZY = 200% + lifesteal. It decays after 5 s without a kill. — [Combo Meter](https://wiki.hytalemodding.dev/mod/endgame-qol/combo-meter)
- Achievements: "63 built-in across 8 categories … + unlimited custom via JSON" (the wiki introduction says 51). Bestiary: 31 entries with kill milestones and discovery rewards. "Endgame Memories" adds a custom Memories category for endgame mobs. Leaderboard ranks players by 6 stats. — [cfwidget](https://api.cfwidget.com/hytale/mods/endgame-qol), [Introduction](https://wiki.hytalemodding.dev/mod/endgame-qol/introduction)

**QoL features**
- UI: a native 7-tab Journal (Bounty Board, Bestiary, Achievements, Partner/Featured Servers, Leaderboard, Wiki, Statistics) that also opens from a craftable Endgame Journal Book. Also the redesigned boss bar, boss combat music, and `/eg status` diagnostics. — [cfwidget](https://api.cfwidget.com/hytale/mods/endgame-qol)
- Items and convenience: a Trinket Pouch (5 slots holding 6 accessories such as Frostwalkers and Ocean Striders), gliders, Backpack Upgrades, a Mana Totem, Spear Pickup ("100% return"), Prisma tools with 3×3 area break, and shears that silk-touch every plant block. — [cfwidget](https://api.cfwidget.com/hytale/mods/endgame-qol), [Introduction](https://wiki.hytalemodding.dev/mod/endgame-qol/introduction)
- World safety: void-fall rescue, and `EnableDungeonBlockProtection` (no building inside dungeons, "preventing sequence-breaking"). — [Void Realm](https://wiki.hytalemodding.dev/mod/endgame-qol/void-realm), [Swamp Dungeon](https://wiki.hytalemodding.dev/mod/endgame-qol/swamp-dungeon)
- The mod adds no inventory sorting, map, waypoint/teleport or auto-loot feature; none appears in its description or wiki. — [cfwidget](https://api.cfwidget.com/hytale/mods/endgame-qol), [E&QoL wiki](https://wiki.hytalemodding.dev/mod/endgame-qol)

**Configuration**
- `/eg config` is an in-game UI with 8 tabs (Difficulty, Scaling, Weapons, Armor, Crafting, Misc, Integration, Addons), "global search, live (no restart)". `RecipeOverrides.json` gives "full control over all ~96 mod recipes". There are 5 presets plus custom multipliers. — [cfwidget](https://api.cfwidget.com/hytale/mods/endgame-qol)
- Example keys: `PvpEnabled` (false), `EnableDungeonBlockProtection`, `BossTargetSwitchIntervalMs` 8000, `SharedBossKillCredit`, `TeleportDistance` 35 for pets, `EnableGlider`, `EnableMithrilOre` (false, "bypass dungeon"), bounty `RefreshHours` 24, combo `TimerSeconds` 5, Warden timers. Temporal-portal timings are configurable too. — [Configuration](https://wiki.hytalemodding.dev/mod/endgame-qol/configuration), [Temporal Portals](https://wiki.hytalemodding.dev/mod/endgame-qol/temporal-portals)
- Optional integrations:
  - RPG Leveling: "Boss kill XP, instance mob levels, zone overrides".
  - MMO Skill Tree: bosses scale HP and damage to the player's MMO level so "over-leveled players can't trivialize them".
  - Endless Leveling: E&QoL "defers party + level scaling to EL entirely".
  - Optional database: SQLite, MySQL, MariaDB or PostgreSQL. 7 in-game languages.

  — [cfwidget](https://api.cfwidget.com/hytale/mods/endgame-qol), [Introduction](https://wiki.hytalemodding.dev/mod/endgame-qol/introduction)

### Inferences
- E&QoL is effectively a mini expansion with three "raids". The loop is: gather → craft key → instance → guaranteed progression item (Dragon Heart) → bench upgrade → next key. The repeatable side (bounties, trials, temporal portals, pet feeding, chests) keeps players busy between key runs.
- The Swamp key recipe (five materials from elites, mining, smelting and crafting) is the owner's preferred "several different activities" style of grind: it feels like playing rather than farming one drop.
- A large share of the design is about multiplayer servers (leaderboards, partner servers, shared credit, DB sync, player scaling, claims). For a mostly solo Realmforge, much of this layer is overhead.
- Several numbers conflict between the CurseForge text and the June wiki: 12 vs 6 Frozen enemy types, 130+/145+/150+ items, 63 vs 51 achievements, 10–15% vs 30% pet drop, and whether Warden Tier I has a boss. The wiki also disagrees with itself: the Frozen page says the upgraded Endgame Bench crafts the next key, while the Swamp page says the Hedera Key is crafted only at the Hedera Autel. The mod is iterating fast, so treat the counts as approximate.

### Gaps
- Comment text (about 1.5k comments) needs sign-in, so concrete player complaints could not be verified.
- Content of the "Big update (12th October)": the latest listed file is 5.4.2 from 2026-09-22, so whether the update has shipped, and what it contains, is unverified.
- `lewai1/endgameqol` is named as the source repository by a mirror ([hytalewiki.pro](https://www.hytalewiki.pro/mods/endgame-qol)), but cloning it asks for credentials, so it is private or gone. The code was not inspected; the ARR license forbids reuse anyway.
- Why the Patreon licensing was dropped: not found.

## 3. Other notable Hytale dungeon/endgame/RPG mods (as of October 2026): what they add and why they are popular

### Takeaway
The dungeon niche is small next to the progression niche. The most-downloaded RPG mods add levels, zones, skills and classes: RPG Leveling (536K) and MMO Skill Tree (471K). Dungeon and endgame content mods integrate with them for mob levels. Among content mods:
- Endless Leveling (206K) is the "everything" RPG package, with 6+ dungeons and augments.
- YUNG's HyDungeons (178K) proved demand for procedural instances but stalled at one dungeon in January.
- Fullmetal Labyrinth (71K) is a frequently updated maze dimension.
- Void Asylum (6.9K) is a six-boss raid-style boss rush.
- Trail of Orbis (1.5K) is a Path-of-Exile-like "Realm map" system that is very close to Realmforge's instanced-realm idea but barely adopted.

### Cited Findings
- **Endless Leveling** (Airijko, with Zbeve as "ZbevePack Developer"):
  - Title "Endless Leveling V13 | 6+ Dungeons Released", 205,701 downloads, last updated 2026-09-29.
  - "A variety of dungeons will be free and included in the core mod", but "Additional premium dungeons may be released as separate DLC". There is an "Endless Dungeons: Outlander Bridge" addon (2026-04-15).
  - Augments at key progression tiers (bonus stats, on-hit triggers, shields). Dungeon mobs and other mobs can also spawn with augment effects (paraphrase of the page).
  - XP comes only from mobs within ±10 levels. Mob scaling uses modes MIXED, DISTANCE, PLAYER, TIERED or FIXED.
  - Guilds are a premium addon. `/party` needs PartyPro.
  - The page does not say how its dungeons are entered.

  — [CurseForge: Endless Leveling](https://www.curseforge.com/hytale/mods/endlessleveling)
- The CurseForge blog says it "reaches far past a simple leveling mod with augments, dungeons, guilds, and more". — [CurseForge blog, 2026-07-29](https://blog.curseforge.com/top-10-hytale-rpg-mods-you-need-to-try-on-curseforge/)
- **YUNG's HyDungeons** (YUNGNICKYOUNG): "Adds new procedurally generated dungeon instances!", 177,595 downloads, created 2026-01-11. "These dungeons are instanced and can only be accessed via the Ancient Portal, unlockable at 100 Memories." "Currently there is only one dungeon … as the game's tooling for procedurally generated structures improves", and "the loot may not be properly balanced". The last file is v0.1.1 from 2026-01-26. — [cfwidget: yungs-hydungeons](https://api.cfwidget.com/hytale/mods/yungs-hydungeons), [CurseForge](https://www.curseforge.com/hytale/mods/yungs-hydungeons)
- **Void Asylum – Boss Dungeon/Raid** (Adathan): 6,851 downloads, created 2026-04-05.
  - "a difficult boss rush dungeon with SIX unique and custom boss encounters", each boss with 5+ custom abilities and a custom weapon drop at 10%.
  - Access: craft a fragment and use it on the Ancient Gateway.
  - "designed for 1/2 people"; "full Adamantite armor is recommended".
  - Needs AdathansBossLibrary and uses Major Dungeons' boss bars if installed. Last file 1.1.4 (2026-05-26, Early Access only).

  — [cfwidget: void-asylum](https://api.cfwidget.com/hytale/mods/void-asylum), [CurseForge](https://www.curseforge.com/hytale/mods/void-asylum)
- **Trail of Orbis** (Larsonix): 1,509 downloads, created 2026-04-28. An ARPG overhaul.
  - "You pick up a Realm Map from a mob drop. It has a biome, a size, maybe some modifiers… Activate it with the map, a portal opens… Timer starts… Kill everything before the timer runs out… Die, and you loose the map."
  - Realm parameters: 14 biomes, 4 sizes (Small: 15 mobs / 5 min, up to Massive: 70 mobs / 20 min), 31 map modifiers.
  - Portal Devices have 5 tiers, from Copper (max level 10) to Adamantite (unlimited), "upgrade[d]" with overworld ores.
  - Build depth: a 489-node skill tree walked in a 3D "Skill Sanctum", 118 gear modifiers and 28 crafting "Stones".
  - "AI has been and is my main and sole tool for creating this project." The last file is from 2026-05-16.

  — [cfwidget: trail-of-orbis](https://api.cfwidget.com/hytale/mods/trail-of-orbis), [CurseForge](https://www.curseforge.com/hytale/mods/trail-of-orbis)
- **Fullmetal Labyrinth** (Lunaronin777): 70,594 downloads, created 2026-02-12, 252 files, latest 1.10.11 on 2026-10-11. "Adds a brand-new Labyrinth Dimension featuring a vast desert surface and an endless maze stretching deep below." It also has secondary dimensions, "Coldest Reaches of Oblivion" and "Hircine's Hunting Grounds". — [cfwidget: fullmetal-labyrinth](https://api.cfwidget.com/hytale/mods/fullmetal-labyrinth)
- E&QoL recommends it as "A cool and original labyrinth dimension". — [cfwidget: endgame-qol](https://api.cfwidget.com/hytale/mods/endgame-qol)
- **RPG Leveling And Stats/Skills** (Zuxaw_dev): 535,512 downloads.
  - Monster levels with level-gap XP penalties, stat points, 6 classes, 18 passives.
  - "6 zones - Emerald Grove, Howling Sands, Borea, Devastated Lands, Skylands, Poisonlands with different level ranges. HUD shows zone name and color (green = good match, orange/red = too hard)."
  - "Instance Level Config for dungeons (e.g. Yung's HyDungeons)".
  - A 0.4.0 beta was uploaded on 2026-10-11. Its file name says "pre-0.7.0", but it is listed under game version 0.6.

  — [cfwidget: rpg-leveling-and-stats](https://api.cfwidget.com/hytale/mods/rpg-leveling-and-stats)
- **MMO Skill Tree & Abilities** (ziggfreed): 470,605 downloads, "inspired by mcMMO and RuneScape - hotbar abilities, triggered passives, quests, achievements, and a deep skill tree". Updated 2026-10-09. — [cfwidget: mmo-skill-tree](https://api.cfwidget.com/hytale/mods/mmo-skill-tree)
- CurseForge's July 2026 list of the top 10 RPG mods: RPG Leveling, Endless Leveling, MMO Skill Tree, Endgame & QoL, Major Dungeons, Perfect Parries, Perfect Dodges, Hexcode, Socket Reforge and Simple Enchantments. — [CurseForge blog](https://blog.curseforge.com/top-10-hytale-rpg-mods-you-need-to-try-on-curseforge/)
- Arcane Power (Tayko_Dev), "evolutive spellbooks", 66,811 downloads. — [cfwidget: arcane-power](https://api.cfwidget.com/hytale/mods/arcane-power)

### Inferences
- Popularity follows two things: progression systems that make the whole world matter (levels and zones), and polished, guaranteed content (E&QoL's tiers and bosses). Procedural-dungeon promises without follow-up (YUNG's single layout, Trail of Orbis's thin adoption) did not hold attention.
- The Ancient Gateway, fragment keys and timed instances are vanilla building blocks (they show up in YUNG, MD and Void Asylum alike). Hytale modders compete on content and frameworks (boss bars, rewards, modes), not on the portal plumbing. Realmforge must build that plumbing itself on Minecraft (roadmap spike 3 / W4).
- Trail of Orbis's "Realm map → tiered gateway → timed procedural instance → loot" is the closest existing analogue to Realmforge's instanced dungeons. Its map loss on death and modifier rolling are exactly the Craft-to-Exile-style grind the owner rejected.

### Gaps
- I could not read Endless Leveling's dungeon entry and layout rules (they appear only in screenshots), its augment list or its prestige system.
- Fullmetal Labyrinth's mechanics (maze generation, bosses, progression) are shown only as images and videos on the page and were not verified.
- I could not get per-mod "why popular" evidence beyond download counts and editorial blurbs.

## 4. Player reception (praise and criticism) and what the mods reveal about base Hytale's endgame gaps

### Takeaway
I could not reach first-hand player comments. CurseForge comments need sign-in, and searches found no relevant reddit threads; one YouTube page refused fetching. The secondary coverage I found is uniformly positive:
- E&QoL is framed as "the fix" for a late game that goes "dull" after Adamantite.
- Major Dungeons is "the dungeon mod of Hytale" with "amazing awards/collectibles".

The clearest negative signals are indirect, read from the authors' own fixes: boss damage nerfs, death-safety nets, removed dependencies, dropped licensing, and the Nameless God's key sales, which work around restart-on-failure. The mods exist because vanilla Hytale in 2026 caps at Adamantite with no multi-phase bosses or structured endgame. Hypixel itself acknowledged this in July 2026.

### Cited Findings
- Major Dungeons is "the dungeon mod of Hytale with semi-custom bosses and amazing awards/collectibles". The blog also notes that repeated playthroughs unlock familiars and mounts. E&QoL "introduces 3 main bosses, each with their own instance". — [CurseForge blog, 2026-07-29](https://blog.curseforge.com/top-10-hytale-rpg-mods-you-need-to-try-on-curseforge/)
- 4netplayers (2026-05-04) presents E&QoL as the answer to a late game that can get "dull" after Adamantite, with bosses of "increasingly difficult phases" and "around 130 additional items". It recommends Major Dungeons for "straightforward dungeon crawling", noting three dungeons and that failing means starting over, though a mysterious merchant can help (paraphrase). It lists no criticism. — [4netplayers](https://www.4netplayers.com/en/blog/hytale/hytale-7-best-mods/)
- HostHavoc (2026-01-15) ranked YUNG's HyDungeons #4 ("12,000+ downloads"). Criticism: "Right now there's one dungeon type". Verdict: "a solid start". — [HostHavoc](https://hosthavoc.com/blog/hytales-most-popular-mods)
- Engagement: Major Dungeons has 365 comments and 574 followers; E&QoL has about 1.5k comments. Comment text is behind sign-in. — [MD CurseForge](https://www.curseforge.com/hytale/mods/major-dungeons), [E&QoL CurseForge](https://www.curseforge.com/hytale/mods/endgame-qol)
- Indirect feedback signals in E&QoL:
  - "Frost Dragon damage pulled back to survivable values" and "Full weapon tier rebalance".
  - "EndlessLeveling double-scaling fix".
  - Void-fall rescue: falling "no longer kills you".
  - The `/eg license` chat command was removed because chat truncated tokens.
  - The MultipleHUD requirement was dropped with Update 5.
  - The Patreon licensing seen in June is replaced by "No license keys, no premium ranks" in October.

  — [E&QoL wiki: Introduction](https://wiki.hytalemodding.dev/mod/endgame-qol/introduction), [Void Realm](https://wiki.hytalemodding.dev/mod/endgame-qol/void-realm), [Premium & Patreon](https://wiki.hytalemodding.dev/mod/endgame-qol/premium-patreon), [cfwidget](https://api.cfwidget.com/hytale/mods/endgame-qol)
- Indirect feedback signals in Major Dungeons: the merchant workaround for restart-on-failure, "remove multiple hud dependency" (2026-06-02), and "No parry or combat mod is a requirement". — [CurseForge](https://www.curseforge.com/hytale/mods/major-dungeons), [MajorDungeonsWiki commits](https://github.com/Dan-Mizu/MajorDungeonsWiki/commits/main), [Getting Started](https://wiki.hytalemodding.dev/mod/major-dungeons/getting-started)
- Base-game gap, stated by Hypixel: on 2026-07-17 game director Simon Collins-Laflamme acknowledged there "aren't many reasons to come back and play Hytale right now" (as quoted by FullCleared). The rest of this item is paraphrased from the article. Chapter 1, targeted two to three months out, adds:
  - the goblin faction;
  - Goblin Breach portals to a regenerating loot area that the Void eventually forces you out of;
  - the first handcrafted dungeon, ending in the game's first multi-phase boss fight, built on an Encounter Manager;
  - runes (active abilities).

  Update 6 was due within two to three weeks, bringing Hardcore worlds and an in-game Mod Browser. Hypixel confirmed there will be no paid mods. — [FullCleared](https://fullcleared.com/news/hytale-admits-there-arent-many-reasons-to-play-it-right-now/)
- E&QoL fills specific vanilla holes. It treats Adamantite as the "vanilla survival cap". It turns the Prisma Mace into a real item because "vanilla shipped the model + texture but no item". It adds boss bars, which Void Asylum also borrows from Major Dungeons. — [E&QoL Introduction](https://wiki.hytalemodding.dev/mod/endgame-qol/introduction), [cfwidget: void-asylum](https://api.cfwidget.com/hytale/mods/void-asylum)
- YUNG writes that loot balance "will be addressed as the vanilla game's progression is more clearly defined over time". — [cfwidget: yungs-hydungeons](https://api.cfwidget.com/hytale/mods/yungs-hydungeons)

### Inferences
- The gaps the mods fill:
  - Post-cap gear tiers (Mithril, Onyxium, Prisma).
  - Phased bosses with readable UI (boss bars, phase markers, music).
  - Guaranteed progression items and structured unlock chains.
  - Repeatable goals (bounties, trials, temporal events).
  - RPG progression (levels and zones).
  - Party and loot-sharing rules.
- Likely friction points, inferred from what the authors softened: tuning damage spikes, death penalties (void falls, run restarts), and dependence on extra mods (HUD, combat). These match the owner's own dislikes (RLCraft-like one-shots, harsh penalties).
- Both big mods ship their content as rigid fixed instances. The players' enthusiasm is for bosses and rewards, not for layout variety. Variety requests are visible only in YUNG's "one dungeon type" critique.

### Gaps
- No verified first-hand player praise or criticism: CurseForge comments are gated, no reddit or YouTube threads about these mods surfaced in several search variants, and the one YouTube-related page found (TroubleChute's hub page) blocked fetching. Curl to reddit returns 403 from this environment.
- Base Hytale's own endgame belongs to another researcher, so I did not verify it here.
- Game-version dates are inferred from file dates (first 0.5 builds 2026-05-26/27, first 0.6 builds 2026-08-27).

## Relevance to Realmforge

Context: owner decisions of 2026-10-11/12, and the roadmap's spike 3 (instanced dungeon in a plot), W4 (dungeon framework) and D8 (solo first, party later) — [roadmap](https://github.com/matejkadlec/realmforge/blob/master/docs/roadmap.md). Realmforge cannot reuse code, assets or names from these mods: both are All Rights Reserved, and E&QoL explicitly forbids decompiling and derivative works. That rules out names like "Nameless God", "Dark Coin", "Dungeon of Fear", "Flocon", "Warden's Trial" and "Temporal Portal". Only mechanics transfer.

### Adopt
- **Key chain with a guaranteed next key.** Treasure bag I contains Fragment II at 100% ([MD](https://wiki.hytalemodding.dev/mod/major-dungeons/treasure-bag-dungeon-of-fear-i)). E&QoL's Dragon Heart is guaranteed and unlocks the next key via a bench upgrade ([E&QoL](https://wiki.hytalemodding.dev/mod/endgame-qol/frozen-dungeon)). Realmforge's "boss drops the item that opens the next dungeon" should use 100% drops for chain items, never a percentage roll. This fits the owner's no-RNG-grind rule.
- **Deterministic fallback path for every key.** The Nameless God sells every fragment for dungeon coins ([MD](https://wiki.hytalemodding.dev/mod/major-dungeons/nameless-god)). Realmforge's version: the friendly realm NPC who trades realm ores for the first key also trades later keys for ores and dungeon tokens. A failed or lost run then never strands a solo player.
- **One persistent portal block, many keys, themed activation, return to the same spot.** E&QoL's Endgame Gateway takes the key's theme, its return portal lands at the exact gateway, and it resets when empty ([E&QoL](https://wiki.hytalemodding.dev/mod/endgame-qol/portals-gateways)). This maps directly onto the owner's portal structure inside each realm: the key is consumed, the structure persists, and the portal stays open until the last player leaves (useful for D8 "players standing at the entrance join"). MD's floating destination preview over the portal ([Instance Config](https://wiki.hytalemodding.dev/mod/major-dungeons/instance-config)) is a cheap, strong UX touch.
- **Entry splash and HUD as data.** Show the dungeon name, flavour text, tips, the recommended level sub-range (for example 13–17) and the boss count on the portal screen. In the instance, show a title, a timer bar and a mode tag ([MD Basic Setup](https://github.com/Dan-Mizu/MajorDungeonsWiki/blob/main/wiki/Modder-Tutorials/Basic-Dungeon-Setup.md), [Instance Config](https://wiki.hytalemodding.dev/mod/major-dungeons/instance-config)). MD shows recommended gear and player count per dungeon; Realmforge shows level bands.
- **Boss-room → treasure-room ending.** The boss drops an exit key that opens a treasure room with the exit and an in-dungeon vendor ([MD DoF I](https://wiki.hytalemodding.dev/mod/major-dungeons/dungeon-of-fear-i)). This gives a clean "completion" moment for the owner's "instance is completed by killing its final boss", and it makes the roadmap's "treasury" ending a natural capstone.
- **Miniboss keys gate the boss door.** DoF III's two minibosses drop the red keys that open the way on ([MD DoF III](https://wiki.hytalemodding.dev/mod/major-dungeons/dungeon-of-fear-i-i-i)). This is exactly the owner's miniboss → miniboss → boss structure, implemented with locked-door blocks that consume the key and name the missing key on failed use ([Locked Doors](https://wiki.hytalemodding.dev/mod/major-dungeons/locked-doors-and-keys)).
- **Wave-survival as a data-driven engine with an explicit state machine.** COUNTDOWN → SPAWNING → ACTIVE → WAVE_CLEAR → INTERVAL → COMPLETED/FAILED, with tier timers, a leash that warns instead of failing, and a final-wave boss ([E&QoL Warden Trials](https://wiki.hytalemodding.dev/mod/endgame-qol/warden-trials)). Also use waves as a pre-boss gate: clear the waves and the boss spawns ([Void Realm](https://wiki.hytalemodding.dev/mod/endgame-qol/void-realm)). This fits the owner's "wave-survival instance is its own dungeon type", and the same engine can run boss add-waves.
- **Phase-boss conventions.** HP-threshold phases, a short invulnerability on transition (E&QoL uses 3 s), telegraphed heavy AOE with a 1 s stagger, a one-time chat hint when the player uses an immune damage type, and a proximity boss bar with phase markers and INVULNERABLE/ENRAGED badges, one bar at a time ([E&QoL Bosses](https://wiki.hytalemodding.dev/mod/endgame-qol/bosses-elites)). These serve the vision's "boss phases!" and Hades-like readability.
- **Loot placed by risk and secrecy.** Common chests sit at the entrance, Epic ones in hidden or underwater side areas, Legendary ones in the boss zone ([Frozen Dungeon](https://wiki.hytalemodding.dev/mod/endgame-qol/frozen-dungeon)). This rewards exploration without puzzles and matches "every piece of loot should be good for something".
- **Per-contributor rewards and owned drops.** Rewards roll per damage contributor and drop as player-owned items, and co-op mob loot rolls independently per player ([MD Bosses](https://wiki.hytalemodding.dev/mod/major-dungeons/bosses), [E&QoL Introduction](https://wiki.hytalemodding.dev/mod/endgame-qol/introduction)). Build this into the reward pipeline now so the later command-based party needs no redesign.
- **Safety nets and anti-cheese rules inside instances.** Rescue players from falling off arenas with zero penalty, block building and breaking in instances, cancel NPC friendly fire by default, and always preserve inventory on voluntary exit ([Void Realm](https://wiki.hytalemodding.dev/mod/endgame-qol/void-realm), [Swamp](https://wiki.hytalemodding.dev/mod/endgame-qol/swamp-dungeon), [Instance Config](https://wiki.hytalemodding.dev/mod/major-dungeons/instance-config)). This supports "challenging but not 1 mistake = death" and keeps the keepInventory default consistent.
- **Everything as content data.** MD defines key, portal type, time limit, modes, drop-list swaps, locked doors, loot packs and mimic chests entirely in JSON ([Modder Tutorials](https://wiki.hytalemodding.dev/mod/major-dungeons/modder-tutorials)). This mirrors Realmforge's YAML pipeline: a dungeon YAML should declare key item, level sub-range, boss list (0–3), time limit, chest tiers and modes.

### Adapt
- **Difficulty modes, chosen at the portal before entry.** MD's Hardcore mode multiplies mob and boss HP and damage, swaps drop lists and chest spawners, and wipes the inventory on death ([Instance Config](https://wiki.hytalemodding.dev/mod/major-dungeons/instance-config)). Keep the mode picker and the drop-list swap (better loot for a harder mode), plus the rule that chest rolls are fixed at generation. Drop item loss entirely (keepInventory is ON, and the owner wants no soulslike). The death penalty can be "run ends, key not refunded".
- **Random challenge rifts inside persistent realms.** E&QoL's temporal portals spawn every ~1.5 h, 80–300 blocks away, with tiered weights (chill 50% / mid 30% / hard 10%) and decay warnings ([Temporal Portals](https://wiki.hytalemodding.dev/mod/endgame-qol/temporal-portals)). This could make Realmforge's realms feel inhabited, which is the owner's cure for AoA-style empty dimensions. Adapt it so rifts spawn near the player or a waystone and appear on the minimap, last much longer than 180 s, and are optional bonus content, never required. That respects "hates walking" and avoids FOMO.
- **"Bring a contract" side objectives.** MD's Instance Objectives give a random task (kill N, open N chests) when the player carries a token into the instance, paid on voluntary exit ([Instance Objectives](https://wiki.hytalemodding.dev/mod/major-dungeons/instance-objectives)). This is a ready model for the vision's procedural "contracts". Make the token come from a realm NPC, and pay on completion even if the run timer expires.
- **Realm-specific currency and an in-realm trader.** Flocons with Korvyn and Swamp Currency with Morghul, restocking daily ([Frozen](https://wiki.hytalemodding.dev/mod/endgame-qol/frozen-dungeon), [Swamp](https://wiki.hytalemodding.dev/mod/endgame-qol/swamp-dungeon)). This matches "mine realm ores → trade to friendly NPCs". Use at most one realm currency, ideally the realm's ore itself, and have traders restock per chapter or on demand rather than on real-time days, since Realmforge is mostly solo.
- **Key recipes that span activities.** The Hedera Key needs an elite drop, a mined gem, a smelted ingot, a second elite drop and a crafted rope ([Swamp](https://wiki.hytalemodding.dev/mod/endgame-qol/swamp-dungeon)). Use this shape for realm keys: three to five ingredients from open-world realm activities, for the vision's "grind that doesn't feel like grind". Show sources in-game through EMI/Jade, and keep elite ingredient drops at 100%.
- **Bench and station tiers unlocked by guaranteed boss items.** The Dragon Heart upgrades the Endgame Bench, and the Nameless God's Tier 2 needs a Silver Skull ([Frozen](https://wiki.hytalemodding.dev/mod/endgame-qol/frozen-dungeon), [Nameless God](https://wiki.hytalemodding.dev/mod/major-dungeons/nameless-god)). This is a good chapter gate for Realmforge's profession stations and per-chapter material tiers. A tier ladder with a fixed damage step per tier (E&QoL's +30%) is an easy balance rule.
- **Alternating-vulnerability phases, without flight.** Dragon Frost forces weapon switching by flipping melee and projectile immunity ([Bosses](https://wiki.hytalemodding.dev/mod/endgame-qol/bosses-elites)). Keep the idea with grounded bosses, for example a shielded phase that only spells, or only a specific spell school, can break. This ties into the 8-slot spell bar.
- **Player-count scaling formula for the later party system.** HP × (1 + (n−1) × 0.5) ([Bosses](https://wiki.hytalemodding.dev/mod/endgame-qol/bosses-elites)). Keep fixed per-band stats for solo, and do not scale bosses to player level: the chapter caps already bound power. MMO-style "scale to player" removes the reward of levelling.
- **Pet tiers that unlock mount, flight and aura.** E&QoL pets climb D→SS by feeding: mount at C, flight at B, a perk at S, an aura at SS ([cfwidget](https://api.cfwidget.com/hytale/mods/endgame-qol)). This is a model for Realmforge's single useful pet (vision) and an early flight path. Make the pet's acquisition deterministic (a quest or crafted item, not a 10–30% boss drop). Flight should come early per the owner's QoL decision, not at endgame tier B.
- **Handcrafted vs procedural split.** MD and E&QoL ship fixed handcrafted instances that players love for their bosses. YUNG's single procedural dungeon drew the "one dungeon type" critique ([HostHavoc](https://hosthavoc.com/blog/hytales-most-popular-mods)). Use handcrafted layouts for story and boss dungeons (one-time or few-time), and procedural ones with several room sets per realm for repeatables. One template per realm is not enough.
- **Visible level-match colour.** RPG Leveling shows zone colour from green to orange to red against the player's level ([cfwidget](https://api.cfwidget.com/hytale/mods/rpg-leveling-and-stats)). Apply it to Realmforge's dungeon sub-ranges on the key tooltip and portal splash.

### Avoid
- **Restart-from-scratch on failure with keys only from the previous boss.** MD had to add a coin vendor to make this bearable ([CurseForge](https://www.curseforge.com/hytale/mods/major-dungeons)). Every key in Realmforge needs a non-boss re-acquisition path.
- **Item loss on death and death on timer expiry.** MD uses `CONSUME` ("You've Lost Everything..."), and in DoF staying past the 30–40 min limit "results in death" ([Instance Config](https://wiki.hytalemodding.dev/mod/major-dungeons/instance-config), [DoF I](https://wiki.hytalemodding.dev/mod/major-dungeons/dungeon-of-fear-i)). This conflicts with keepInventory and the anti-soulslike stance. If a timer exists, it should be generous, or soft (a bonus chest for a fast clear).
- **Low-percentage drops for meaningful progression or mounts.** E&QoL pets at 10–15%, Frostbite Blade at about 3%; MD's Silver Moon Wolf Spirit at about 1% and Ultra cards at 3–5% ([E&QoL](https://wiki.hytalemodding.dev/mod/endgame-qol/bosses-elites), [MD](https://wiki.hytalemodding.dev/mod/major-dungeons/treasure-bag-silver-moon)). This is exactly the "WoW raid, months for one item" pattern the owner hates. If a chase item exists, pair it with a token vendor or bad-luck protection.
- **Flying, melee-immune boss phases.** Dragon Frost's Sky Sentinel phase ([Bosses](https://wiki.hytalemodding.dev/mod/endgame-qol/bosses-elites)) breaks the owner's "exactly 0 flying bosses" rule.
- **Required parkour.** DoF III's Green Key comes from "a Parkour challenge" ([DoF III](https://wiki.hytalemodding.dev/mod/major-dungeons/dungeon-of-fear-i-i-i)). The owner wants no or minimal parkour, and never on the required path.
- **Side economies stacked on few dungeons.** MD has Devil Cards with scraps, booster packs, a wandering daily-restock merchant and artifacts that summon it. E&QoL has multiple currencies, a combo meter (up to 200% damage with lifesteal, which rewards farming trash hordes), daily and weekly bounties with reputation, leaderboards and partner-server tabs ([Devil Merchant](https://wiki.hytalemodding.dev/mod/major-dungeons/devil-merchant), [Combo Meter](https://wiki.hytalemodding.dev/mod/endgame-qol/combo-meter), [Bounty Board](https://wiki.hytalemodding.dev/mod/endgame-qol/bounty-board)). This is the "over-modded, gazillion systems" trap from the vision, and real-time daily resets add chore pressure to a solo game.
- **ARPG-scale stat sprawl and map consumption.** Trail of Orbis has 489 nodes, 216–246 stats, 118 modifiers and an 11-stage damage pipeline, and loses the map on death ([cfwidget](https://api.cfwidget.com/hytale/mods/trail-of-orbis)). That is the Craft-to-Exile feel the owner already rejected.
- **Balancing around a third-party combat mod.** MD tunes difficulty to Stunning Combat's parries ([CurseForge](https://www.curseforge.com/hytale/mods/major-dungeons)). Realmforge should tune bosses explicitly for its own pack (Better Combat plus its own spells) and test them with GameTests and the QA tools.
- **Monetized or ad surfaces.** Premium DLC dungeons (Endless Leveling), license tokens and a partner-server tab (E&QoL) ([Endless Leveling](https://www.curseforge.com/hytale/mods/endlessleveling), [Premium](https://wiki.hytalemodding.dev/mod/endgame-qol/premium-patreon)) do not fit an open-source passion project.
