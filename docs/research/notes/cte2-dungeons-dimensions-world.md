# CTE2 (Craft to Exile 2): dungeons, dimensions, endgame loops and world content

> Appendix to [the reference-game research](../reference-games.md) (RLM-94), written on 2026-10-12 by an AI research agent. Local citations name their source (the Craft to Exile 2 1.1.3 instance and the jars it ships, the Mine and Slash and Advent of Ascension source trees, the CTE2 GitHub changelogs); those files are not in this repository. Mechanics and numbers only: never reuse names, assets or code of the reference games.


Version note: the local instance export is **CTE2 1.1.3** (Forge 47.4.10, MC 1.20.1, 401 mods; INST/manifest.json, modlist.html; changelog entry 1.1.3 dated 2025-11-06). The pack's GitHub changelog continues to **2.1.4 (2026-09-10)** with big mapping reworks (Atlas passive tree, Pinnacle bosses, Imprisoned Monsters, Strongboxes, Shrines, map "tickets", relic rework, KeyFlow replacing MineMenu). Numbers below are from 1.1.3 data unless marked "[2.x]".

Path abbreviations used in citations (all local, read-only):
- `INST/` = CTE2 1.1.3 instance: 
- `MNS/` = INST/config/openloader/data/cte_mns/data/ (CTE2's Mine and Slash / league datapack)
- `CFG/` = INST/config/openloader/data/cte_configuration/data/ (CTE2's general datapack)
- `JARS/` = CTE2 jar:  (extracted jars: data + lang only, no .class files)
- `MNSSRC/` = Mine and Slash source (1.20-Forge): src/main/java/com/robertx22/mine_and_slash/ (Mine and Slash source v6.3.7; pack ships 6.3.14)
- `DRSRC/`, `HVSRC/`, `OBSRC/` = public GitHub sources https://github.com/RobertSkalko/Dungeon_Realm , https://github.com/RobertSkalko/The_Harvest , https://github.com/RobertSkalko/Ancient_Obelisks (shallow clones, last commit 2025-07-26; read, not built)
- `CL/` = CTE2 GitHub repo: CHANGELOG.md ; `ACL/` = CTE2 GitHub repo: ARCHIVED CHANGELOG.MD
- `Q/` = INST/config/ftbquests/quests/chapters/

## 1. Dungeon Realm ("Maps"): entry, layout, objectives, bosses, loot, scaling, instancing, party play

### Takeaway
A CTE2 dungeon is a consumable **Dungeon Map** item (level, tier, rarity, random affixes) used on a crafted **Map Device**. It sends you into one shared, bedrock-filled `dungeon_realm:dungeon` dimension where an 18-24-room layout is assembled from one of ~21 themed room kits. Progress is a kill-% meter that raises an "exploration rarity" (Common to Mythic at 0/15/30/45/60/75 %), which sets the reward room. A boss teleporter appears somewhere in the map (usable from 30 %), and killing the boss opens a reward room. Uber arenas come from fragments, and side content ("leagues": Harvest, Obelisk, Prophecy) is seeded into each map. In short, it is Path of Exile's map system rebuilt in Minecraft. It is endgame-only: maps drop from level 55 (50 in [2.x]).

### Cited Findings
- **Entry items/blocks** — Dungeon Realm adds `Map Device` (block), `Dungeon Map`, `Home Pearl` ("Use to Exit the Dungeon Realm"), `Relic Key`, `Dungeon Relic`, `Uber Fragment`, `Dungeon Map Uber Upgrade`. Its tooltips read: "Right Click the [Map Device Block] with the map to start it", "You need to have at least one [Home Pearl] in your inventory to enter", "A Relic Key Item opens the Map Device" and "Relics are placed inside the Map Device" — JARS/Dungeon-Realm-1.20.1-1.1.7/assets/dungeon_realm/lang/en_us.json
- **CTE2 recipes override the mod's trivial ones**:
  - Map Device = dragon head + 2 diamonds + `mmorpg:stone/3` + obsidian (mod default: diamond over stone).
  - Home Pearl = `mmorpg:stone/3` + 6 iron, giving 8 pearls (mod default: gold + 6 iron, giving 16).
  - Relic Key = lever + `mmorpg:stone/2`.
  - Sources: MNS/dungeon_realm/recipes/{map_device,home_pearl,relic_key}.json vs JARS/Dungeon-Realm-1.20.1-1.1.7/data/dungeon_realm/recipes/
- **Map sources**: random drops from level 55 (`min_level_map_drops = 55`, `MAP_DROPRATE = 0.5`) or a crafted **Map Creator**, which gives a Common map at the player's level and only works at level ≥ 55. A new map can be started only every 120 s (`MAP_START_COOLDOWN_SECONDS = 120`). Dungeon maps never come from chests (`DUNGEON_MAP_SPAWN_CHANCE_ON_CHEST_LOOT = 0.0`) — INST/defaultconfigs/mine_and_slash-server.toml l.11-12, 136-139; MNSSRC/vanilla_mc/items/MapCreator.java; INST/defaultconfigs/dungeon_realm-server.toml. Quest text: "Maps are instanced dungeons... craft a Map Creator... only be used starting at level 55... craft a Pearl of Home, to ensure you can return safely" — Q/act_v.snbt ("Maps" lost page)
- **Rarity and tier bands** (quest text): Common T0-10, Uncommon T11-20, Rare T21-40, Epic T41-60, Legendary T61-80, Mythic T81-100. "Affixes are random modifiers that are applied either to the players (usually negative) or the mobs (usually positive)" — Q/act_v.snbt ("Maps")
- **Dimension**: one `dungeon_realm:dungeon` dimension with generator `dungeon_realm:dungeon_chunk_gen`. Every layer is bedrock (height 384). No skylight, fixed time 1000, ambient light 0.1, beds and anchors disabled — JARS/Dungeon-Realm-1.20.1-1.1.7/data/dungeon_realm/dimension/dungeon.json, dimension_type/dungeon.json
  - **Instancing**: each new map takes the next grid slot (`getOrSetStartPos` uses a counter, `getStartFromCounter`) inside that one dimension — DRSRC/.../block/MapDeviceBlock.java (`startNewMap`).
  - The config says the dimension is "not meant to be built in" and can be wiped — INST/defaultconfigs/dungeon_realm_dimension-server.toml.
  - Official servers wiped map/Harvest/Obelisk dimensions daily — ACL/ 1.0.6.
- **Party play**: a started device stays "activated", and other players right-click it (without a map) to `joinCurrentMap` into the same instance. Inside the map, a Map Device block placed at the spawn point is the exit (crouch-use = teleport home) — DRSRC/.../block/MapDeviceBlock.java (`use`, `joinCurrentMap`).
  - MnS party settings: `party_radius = 100`, `PARTY_EXP_BONUS = 1.0` (+100 % XP before splitting), `PARTY_DROP_BONUS = 0.25`, `require_team_for_dungeons = true` (enforcement not verified in code) — mine_and_slash-server.toml l.5, 30-60.
  - [2.x] maps get "tickets"; each entering player spends one, with fewer tickets at higher rarity (6 at Mythic) — CL/ 2.1.0.
- **Layout size**: `MIN_MAP_ROOMS = 18`, `MAX_MAP_ROOMS = 24`. Each "mob" data block spawns 2-3 mobs and each "pack" block 4-6 — INST/defaultconfigs/dungeon_realm-server.toml.
  - Map layout became fixed on item creation and shown in the tooltip (ACL/ 1.1.2).
  - [2.x] players can filter maps by layout type in auto-salvage ("Don't want to run Mines anymore? Filter it out!") — CL/ 2.1.0.
- **Official mod docs (primary)**: "Every Dungeon Map counts as a limited 'ticket' to the Dungeon Realm. You can only explore a single Dungeon within it with 1 map." "You can kill mobs, loot chests and solve puzzles to usually find more chests!"
  - Rules: "You are confined to a specific Dungeon, you can not exit it... Mobs do not respawn, they are spawned once per Map... You can not break/place blocks, you are affectively in simulated adventure mode... Mobs can't grief... Mobs do not take environmental damage."
  - Home Pearls "allow you to exit the Dungeon Realm without having to find the Teleportation Block" — https://moddedmc.wiki/en/project/dungeon-realm/latest/docs/intro.md
- **Authoring docs (primary)**: "create all the rooms, save them as structure blocks and list their names in the dungeon datapack json... make sure each type has at least 1 room... Structure files must be max 16x[height]x16". Content is placed with command blocks whose text is an id ("the Mod detects the text inside command blocks in the Dungeon Realm, and spawns content accordingly when a player is nearby").
  - Example: `chance_chest` "Has 25% chance to spawn the chest, but only spawns it once inside a single room. Use this to randomize chest positions". "To create a map, you pretty much only need to know about: mob, mob_horde, chest and chance_chest!" — https://moddedmc.wiki/en/project/dungeon-realm/latest/docs/datapacks.md
- **Mine and Slash wiki on the map ladder**: "To get your first map, just craft the Map Creator... completing maps will allow you to get a new map with a higher rarity... The only requirement is to kill the boss inside the map... If the map is too difficult, you could instead downgrade the rarity of the map" — https://moddedmc.wiki/en/project/mine-and-slash/latest/docs/endgame/what_are_maps.md
- **Room-kit schema**: each `dungeon_realm_dungeon/<id>.json` lists room NBT names in 6 slots: `entrances`, `straight_hallways`, `curved_hallways`, `triple_hallway`, `four_ways`, `ends`. It also has a `folder`, a `weight` and a `mob_list_tag_check` (which mob lists may populate it) — e.g. MNS/dungeon_realm/dungeon_realm_dungeon/bastion.json.
  - Room NBTs are 1-chunk pieces under `library_of_exile/structures/dun/<theme>/<slot>/` (468 files in the CTE pack) — MNS/library_of_exile/structures/dun/.
- **The 24 CTE2 dungeon definitions**:
  - 21 active themes at weight 1000: bastion, cement (Cemetery), end, garden (author Neksy21), it (Ice Temple), mine, mossy_brick, nature, nether, night_terror (author "CTE2 Team"), pirate_lair (Jarky2203/Blubberbert2209), sandstone, sewer2 (Slime Sewers), sewers, spruce_mansion, steampunk, stone_brick, tent, warped, watcher, wn (Nature's End).
  - `moo` at weight 100 (author mahjerion): a Minotaur-only map with a Minoshroom arena. It is probably the "secret map layout made by me that has a 0.43 % chance of appearing" (ACL/ 1.0.0); today's weights give 100 / (21×1000 + 100) ≈ 0.47 %.
  - `pyramid` and `template` at weight 0 (disabled).
  - Sources: MNS/dungeon_realm/dungeon_realm_dungeon/*.json. The mod jar ships 17 of these (JARS/Dungeon-Realm-1.20.1-1.1.7/data/dungeon_realm/dungeon_realm_dungeon/). Garden and Pirate Lair came from a community "Dungeon Building Contest" (ACL/ 1.0.0). Watcher and End were made by volunteer builders (ACL/ 0.9.0).
- **Room names show puzzle/secret flavour in the mod's original kits**: `trap_puzzle0`, `hidden_button0`, `parkour_puzzle_magma`, `hidden_lever_boss`, `peek_secret_ice`, `redstone_ore_hidden_room`, `drowned_puzzle`, `shooting_gallery`, `slime_puzzle`, `camo_chest`, `underwater_chest`, `safe_room`, `trader`, `fort_trader`. CTE2-made kits are mostly plain set pieces (night_terror: `barn`, `gas_station`, `library`, `dracula`, `crop_circle`) — MNS/dungeon_realm/dungeon_realm_dungeon/{sandstone,nether,spruce_mansion,stone_brick,mossy_brick,it,night_terror}.json
- **Mob population by theme tags**:
  - cement = `generic_undead`; it = `water` + `generic_undead`; nature/wn = `forest` + `spider_forest`; nether/sandstone = `fire`; mine = `illagers` + `otherworldly`; moo = `moo` (only `twilightforest:minotaur`) — same files.
  - Mob lists are weighted, e.g. generic_undead: zombie 1000, husk 500, skeleton 300, Born in Chaos undead 25-125 — MNS/library_of_exile/library_of_exile_mob_list/*.json
  - [2.x] 4 caster mobs exclusive to maps/Harvest/Obelisk (one per element + chaos, 3 skills each) make up ~25 % of map spawns — CL/ 2.1.0.
- **Data blocks** in rooms become content when the player comes near:
  - `mob`; `mob_horde` (aliases `pack`, `trader`); `elite_mob`; `elite_mob_horde`; `boss`; `map_boss`.
  - `chest` (aliases `big_chest`, `puzzle`, `trap_chest`); `chance_chest`; `map_reward` (only in the REWARD_ROOM).
  - `map_teleporter` (places `library_of_exile:teleport_back`); `uber_boss_altar`; `league_spawn_pos`.
  - Source: JARS/Dungeon-Realm-1.20.1-1.1.7/data/library_of_exile/library_of_exile_map_data_block/*.json.
  - Processing radius `CHUNK_PROCESS_RADIUS = 3`, `CHUNK_SPAWN_RADIUS = 2`. Unknown/broken data blocks spawn a small pack (`DEFAULT_DATA_BLOCK = "mob"`) — INST/defaultconfigs/dungeon_realm_dimension-server.toml
- **Completion formula**:
  - Kill % = (kills + elite kills × elite weight + miniboss kills × miniboss weight) / weighted spawns.
  - Until all data blocks have been seen (minus a leeway), the denominator assumes the maximum spawns for unseen blocks.
  - Each time kill % passes the next rarity's `perc_to_unlock`, the map's "exploration rarity" upgrades and everyone in the map gets "Your Map Exploration is now X".
  - Chests looted are counted, but only kills drive rarity — DRSRC/.../structure/DungeonMapData.java (`calculateKillCompletionPercent`, `updateMapCompletionRarity`).
  - CTE2 values: elite weight 5 (mod default 10), miniboss 20, leeway 5 data blocks — INST/defaultconfigs/dungeon_realm-server.toml.
  - Mobs despawn more than 30 blocks from players (`MOB_DESPAWN_DISTANCE_IN_MAPS = 30`) — mine_and_slash-server.toml l.122.
  - [2.x] mobs no longer despawn in maps (they are saved to the chunk), and "exploration goal is now 50 % again instead of 40 %" — CL/ 2.1.0.
- **Finish rarities (CTE2 values)**:

  | Rarity | Kill % | Reward chests | Chest table | MnS loot multi |
  |---|---|---|---|---|
  | Common | 0 | 2 | tier_1 | ×1.0 |
  | Uncommon | 15 | 2 | tier_1 | ×1.25 |
  | Rare | 30 | 3 | tier_2 | ×1.75 |
  | Epic | 45 | 3 | tier_3 | ×2.5 |
  | Legendary | 60 | 4 | tier_4 | ×3.5 |
  | Mythic | 75 | 4 | tier_5 | ×4.75 |

  - Source: MNS/library_of_exile/library_of_exile_map_finish_rar/*.json.
  - CTE2 chest tables give 1-10 Lightman's coins (copper, then iron, then gold at higher tiers) plus 2 / 2-4 / 4-6 / 6-8 / 8-10 rolls of `cte2:cte2_high_rune` — MNS/library_of_exile/loot_tables/chests/tier_*_dungeon.json
- **Boss flow**:
  - Every map places exactly one `dungeon_realm:boss_teleport` block (`always_spawn: true`). It is usable from `MAP_PERCENT_COMPLETE_NEEDED_FOR_BOSS_ARENA = 30.0` — JARS/Dungeon-Realm-1.20.1-1.1.7/data/library_of_exile/library_of_exile_map_content/boss.json; mine_and_slash-server.toml l.124.
  - Killing the final map boss places a `reward_teleport` block and generates the reward room. The boss always drops a relic and has a chance (relic stat) to drop an extra map — DRSRC/.../main/DungeonEvents.java.
  - Quest text: "You will need to find the teleporter block to warp you to the Boss Room. This block can spawn anywhere within the map. Kill the Boss to gain access to a specialized Reward Room, where the loot improves depending on your progress percent!" — Q/act_v.snbt ("Enter A Map")
- **CTE2's KubeJS "Boss Soul Stone"**:
  - On entering a map, a hidden advancement tags the player `Map`. A script then searches 128 blocks around the player for the boss-teleport block and gives a renamed amethyst shard: "Takes you and nearby allies [within 10 blocks] to the location of the Boss's Teleporter".
  - The shard is deleted when you leave the map — INST/kubejs/server_scripts/boss_teleport_locator.js; INST/config/openloader/data/cte_fix/data/harvest_fix/advancements/quests/telefix/map_port.json.
  - Added in 1.0.6 ("teleports you and your nearest allies to the Boss Teleporter block") — ACL/ 1.0.6. The 2.0.0 notes list "the teleporter block" among "major pain points" — ACL/ 2.0.0.
- **Boss arenas (CTE2)**: 4 active 2×2-chunk arenas (`arena`, `biome`, `mine`, `sandstone`; `moo` has weight 0). Each draws a random boss from 13 modded "mini-bosses": evoker, mutant skeleton/zombie/creeper, Friends&Foes wildfire, Illager Invasion inquisitor, Monster Plus abyssologer, Aquamirae maw, Risk of Rain stone golem, Born in Chaos dire hound leader / bonescaller / spirit guide / skeleton thrasher — MNS/dungeon_realm/dungeon_realm_boss_arena/*.json
- **Uber bosses**:
  - 4 uber arenas: `uber1` river ("Realm of Atrophy and Decay"), `uber2` abyss and `uber3` dragon (12 chunk-pieces each, about 4×3 chunks), `uber_nature_dungeon` (30 pieces, about 6×5 chunks). The arena JSON `size` field is the max piece index, not the chunk count — MNS/dungeon_realm/structures/uber/*/ file listing.
  - They draw from 16 big bosses taken from other mods: Wither, Stalwart awful ghast + nether keeper, Blue Skies starlit crusher + arachnarch, Aquamirae Captain Cornelia, TF lich/minoshroom/snow queen, Mowzie's frostmaw/umvuthi/sculptor, Born in Chaos lifestealer/nightmare stalker, Remnant ossukage, Infinity Golem — MNS/dungeon_realm/dungeon_realm_uber_boss/*.json.
  - Map bosses drop Uber Fragments at 10 % (`UBER_FRAG_DROP_RATE`). 4 fragments craft an Uber Upgrade that "Adds the Uber Arena" to a non-uber map — dungeon_realm-server.toml; JARS/.../recipes/uber_upgrade.json + lang.
  - Uber bosses drop 3 relics — DRSRC/.../main/DungeonEvents.java.
  - Quest: fragments come from "Bosses in Legendary and Mythic Maps... Uber Bosses... drop lots of loot. They can even drop special Unique Jewels! The pool... consist of old bosses you faced during the campaign" — Q/act_v.snbt ("More Map Content")
- **Mob rarity multipliers**:
  - Boss: HP ×4 (forced custom HP 200), dmg ×1.9, stats ×3.5, loot/exp ×10.5.
  - Uber: HP ×8 (forced 250), dmg ×2.05, stats ×4, loot/exp ×15.5, plus a CC-resist spell.
  - Elites range from Rare (weight 64; HP ×2, dmg ×1.3, loot ×1.75) to Mythic (weight 1, from level 50; HP ×3.5, dmg ×1.75, loot ×8.75).
  - Source: MNS/mmorpg/mmorpg_mob_rarity/*.json
- **Map tier scaling**:
  - Each tier adds +12 % MORE mob HP, +3 % MORE mob total damage and +2 % loot (`HP_MOB_BONUS_PER_MAP_TIER 0.12`, `DMG_MOB_BONUS_PER_MAP_TIER 0.03`; `bonusFormula = 1 + tier*0.02`).
  - Map rarity sets the tier range, a per-element resist requirement and an XP multiplier.
  - Sources: MNS/mmorpg/mmorpg_game_balance/original_balance.json; MNSSRC/maps/MapItemData.java (getTierStats, bonusFormula, getStatReq).
  - Earlier values were 20 %/6 % per tier, then 15 %/4 % (ACL/ 0.7.3). [2.x] "Map Tier mob bonuses to Health reduced... ~20 % less Health" by level 100 (CL/ 2.1.0).
  - In maps, mob level = map level (overrides dimension/entity configs) — MNSSRC/uncommon/utilityclasses/LevelUtils.java.
  - Maps skip the Anti-Mob-Farm loot penalty (an ADVENTURE_MAP multiplier is used instead) — MNSSRC/loot/LootInfo.java ~l.254.
- **Rules inside maps**:
  - Banned items: ender pearls, chorus fruit, Botania rods/flight tiara, grapples, conducting bone, cages...
  - Blocks are unbreakable except an allow-tag; dispensers cannot be opened; dimension mobs are immune to environmental damage.
  - Sources: MNS/library_of_exile/tags/items/banned_map_items.json; dungeon_realm_dimension-server.toml. "Entities in Map dims should no longer drop their default loot" (only coins + MnS loot) — ACL/ 0.9.3b.
- **Relics (map "juicing")**:
  - Relic types: `mmorpg` (max 3 equipped), `the_harvest` (1), `ancient_obelisks` (1); the Dungeon Realm general relic is disabled (weight 0).
  - Rarities run from Common (1 affix, weight 729) to Mythic (4 affixes, weight 3) — MNS/library_of_exile/library_of_exile_relic_type/*.json, library_of_exile_relic_rarity/*.json.
  - Example relic stats: +pack size, +extra map boss, +extra map from boss, +uber fragment chance, +bonus content chance (Dungeon Realm lang); +X % Harvest/Obelisk/Prophecy content weight, +1 or ×2/×3 content (Harvest/Obelisk lang; MNS/library_of_exile/library_of_exile_relic_stat/).
  - Bonus content is seeded at map start: always-spawn content (the boss teleporter), plus the map's `bonus_contents` count (+1 on a relic roll), picked by weight from non-always content, plus the uber arena if upgraded. Blocks are spread over the generated dungeon chunks — DRSRC/.../structure/MapBonusContentsData.java (`setupOnMapStart`, `calcSpawnChance`).
  - [2.x] the relic system was reworked: 1 relic per device, implicits that guarantee a league, capped at Rare, 5 uses, Relic Key removed — CL/ 2.1.0.

### Inferences
- **No dimension per run**: the "instance" is a grid plot in one infinite void dimension. Realmforge could use the same "one dungeon dimension, many plots" pattern for instanced dungeons instead of creating dimensions at runtime; party join works by re-using the activated device or portal.
- **Room-kit pipeline**: room kits (6 slot types × ~2-6 variants) plus data blocks let builders and AI agents author small NBT rooms while the generator handles layout and density. It is cheap content; the risk is sameness (CTE added a layout filter so players can skip themes they dislike).
- **Kill-% completion pushes full clears**: players must comb every room, and mobs despawning or unloading broke the meter repeatedly (fixes in ACL/ 0.9.3b, 1.0.4, 2.0.3, CL/ 2.1.0). A boss-kill completion, as the owner decided, avoids this whole class of bugs.

### Gaps
- The exact room-graph algorithm (branching, how the boss/reward rooms attach) lives in Library of Exile 1.20 (not public). Only the Dungeon Realm side was read.
- How `require_team_for_dungeons` is enforced was not found in MnS 6.3.7 source.

## 2. The Harvest and Ancient Obelisks

### Takeaway
Both are small **arena "league" encounters**. Each runs in its own void dimension (`the_harvest:harvest`, `ancient_obelisks:obelisk`) on a pre-built arena of 2×2 to 3×3 chunks. You reach one by finding the block inside a dungeon map, or by using a map item that drops from ordinary loot chests on a placed block. Mobs scale to the player's level.
- **The Harvest** is a 2-minute timed horde: about 100 mobs total, refilled 15 at a time whenever fewer than 3 are alive. It pays crafting gems.
- **Ancient Obelisks** is the closest thing to a wave-survival dungeon in the pack. It runs 3-5 waves of 25 mobs, each wave with its own affixes, plus difficulty tiers and spawn-rate orbs. At the end, reward chests scale with kills × tier × affixes.

### Cited Findings
- **Dimensions**: both are bedrock-layer void dimensions with their own chunk generators (`the_harvest:harvest_chunk_gen`; same pattern for obelisk) — JARS/The-Harvest-1.20.1-1.1.3/data/the_harvest/dimension/harvest.json; JARS/Ancient-Obelisks-1.20.1-1.2.3/data/ancient_obelisks/dimension/. Mob level scales to the nearest player and entity-config level floors are ignored there ("if the player enters side content not connected to map... force the level to scale to player") — MNSSRC/uncommon/utilityclasses/LevelUtils.java.
- **Official Obelisk docs**: "Obelisks can be crafted and placed in your base. They allow you to visit the Obelisk Realm and challenge the arena-like trials... Each map will show: mob affixes... wave count... mob difficulty, called obelisk tier... you fight waves of mobs each with more and more mob affixes. At the end, reward chests will spawn... Essences of the Ancients... can be crafted into Orbs that can further increase the difficulty and rewards" — https://moddedmc.wiki/en/project/ancient-obelisks/latest/docs/intro.md
- **Entry** — Lang: "Right Click the [Harvest Block] with the map to start it"; "The Harvest gifts you with a Map. You can use this map to Activate the Harvest"; the same pattern for Obelisks; "This Obelisk only Accepts Relic Maps" — JARS/The-Harvest-.../lang/en_us.json, JARS/Ancient-Obelisks-.../lang/en_us.json.
  - CTE2 1.1.0: "Obelisk and Harvests in maps no longer give you an item to use and instead warp you directly on use" — ACL/ 1.1.0.
  - Overworld sources:
    - Harvest maps: 4 % chance as extra loot on any loot-table chest (`MAP_SPAWN_CHANCE_ON_CHEST_LOOT = 4.0`), ×0 inside dungeon maps.
    - Obelisk maps: 2 % base (mod default 5 %), with multipliers ×0.25 in dungeon maps, ×1.25 Undergarden, ×1.5 End / Otherside / Everbright / Everdawn — INST/defaultconfigs/the_harvest-server.toml, ancient_obelisks-server.toml.
    - OBSRC/.../main/ObelisksMain.java l.216 confirms that this multiplier applies to the chest-loot chance.
  - Quest: "you may find Obelisk Maps in chests... Obelisks... wave-based instanced encounters! The mobs that spawn are affected by modifiers on the Map itself... Mobs that spawn in Obelisk encounters scale to your level" — Q/act_i.snbt ("Wave-Based Instances").
  - [2.x] Harvest/Obelisk are introduced in Act I with a free Warp Stone in the prologue (ACL/ 2.0.2).
- **Harvest arenas (CTE2)**: 6 arenas: ancient_cavern (2×2 chunks; Dungeon Building Contest 3rd place), aztec, circle, hive, theevent, watcher (3×3 chunks each). Mobs come from the `harvest` mob list — MNS/the_harvest/the_harvest_harvest_arena/*.json; ACL/ 1.0.0.
- **Harvest mechanics**:
  - Timer 2 min (`ticksLeft = 20*60*2`); total pool = `MAX_TOTAL_MOB_SPAWNS` (CTE 100).
  - Every second, if fewer than 3 harvest mobs are alive, it spawns 15 (× relic "more mob spawns") around spawner blocks.
  - It ends with "The Vines appear to shrink, for now..." and shows an action-bar countdown — HVSRC/.../structure/HarvestMapData.java; JARS/The-Harvest-.../lang.
  - Each mob has a 5 % chance to drop the harvest loot table (`LOOT_TABLE_CHANCE_PER_MOB = 5.0`) — the_harvest-server.toml.
  - The table gives 1-2 of the Chaotic/Lucid/Primal "Gems of Entanglement" + 2-4 crops — JARS/The-Harvest-.../loot_tables/harvest_loot.json.
  - Quest: "Kill mobs in these rooms to earn Chaotic Gems, which are used to craft special currencies!" — Q/act_v.snbt ("More Map Content").
- **Obelisk arenas (CTE2)**: 5 arenas: stone (mod default), amethyst_library and prismarine_soap (added 1.1.0), poison_dungeon (contest entry), trial_chamber 3×3 chunks ("inspired by the PoE trials", 1.0.7) — MNS/ancient_obelisks/ancient_obelisks_obelisk/*.json; ACL/ 1.0.7, 1.1.0.
- **Obelisk mechanics**:
  - A new obelisk map has 3 waves, each with 1 random affix. When a wave starts, players see "Wave X Starting!" plus "New Wave Affix: ...", and the last wave shows "Last Wave INCOMING!" — OBSRC/.../item/ObeliskItemMapData.java (`generateInitialWaves`); OBSRC/.../structure/ObeliskMapData.java (`tryStartNewWave`).
  - CTE config: 25 mobs per wave (× relic bonus). Spawning is "50 % chance per second to spawn 4 mobs", and the config notes "there's usually around 4 spawners in the arena". Each spawner block appears to run this until the wave's pool is used up (inferred from the per-position spawn code). Waves are 30 s apart, skipped if all mobs are dead.
  - Tiers 0-10 (each +12 % HP, +4 % dmg, +10 % loot); +20 % loot per affix; at most 20 reward chests.
  - Each kill adds 1 % chest chance × loot multi (100 % = 1 chest) — ancient_obelisks-server.toml; OBSRC/.../structure/ObeliskMapData.java (`getTotalRewardChance`); ObeliskItemMapData.java (`getLootMulti` = (1 + tier×0.1)(1 + affixes×0.2)(1 + spawn_rate/100)).
  - Crafting "orbs" modify obelisk maps:
    - Orb of Obelisk Waves: +1 wave, up to 5 waves.
    - Orb of Obelisk Difficulty: +1 tier, below the max tier.
    - Orb of Obelisk Spawns: +10 % spawn rate, cap 200 %.
    - Each orb costs 8 essences (Envy/Wrath/Greed) + iron or diamond, which yields 8 orbs.
    - Sources: JARS/Ancient-Obelisks-.../library_of_exile_currency/*.json, item_modification/*.json, item_requirement/*.json, recipes/orbs/*.json.
  - CTE reward table: 3-6 essences + 1-3 vanilla ores/ingots + 1-3 rolls `cte2:cte2_treasure` + 1-2 rolls `cte2:cte2_high_rune` (the mod default has only essences + ores). "Obelisk rewards... chance of High Runes. This is one of the only early-game sources of High Runes" — MNS/ancient_obelisks/loot_tables/obelisk_loot.json; ACL/ 0.9.5.
- **Worked example** (inference from config): a fresh 3-wave, 3-affix, tier-0 obelisk has 75 kills × 1 % × 1.6 = 120 %, so 1 chest + 20 % for a second. A 5-wave, 5-affix, tier-10, +200 % spawn-rate map has 125 kills × 1 % × (2.0 × 2.0 × 3.0) = 15 chests.
- **Tuning churn**: spawns and rewards were retuned repeatedly:
  - "Obelisk mob spawn rates have increased drastically (more than double)" (0.9.5).
  - "Reduced mob count and rate in Obelisk and Harvest arenas" (1.0.3).
  - "Ancient Obelisk spawn amount and rate has been increased by about 25 %" (1.1.0).
  - [2.x] mobs "trickle out a bit slower... over the 30s period" and a 5 s grace timer before start (2.0.2).
  - [2.x] "Harvest and Obelisk mob spawn count reduced by 20-30 %" (2.1.0).
  - Sources: ACL/, CL/.

### Inferences
- **No rooms**: these are "arena + spawner blocks + timer/wave counter" encounters, much cheaper than dungeons. Obelisk's per-wave affix list plus "orbs" that add waves, tiers or spawn rate is a clean, data-driven difficulty and reward dial for a wave-survival instance type.
- **Two-step entry**: a block in the world plus a key or map item is a "side content inside content" design. Players found it clunky, and CTE removed the intermediate item for in-map Harvest and Obelisk (1.1.0).

### Gaps
- No actual player feedback quotes specific to Harvest/Obelisk were found locally. The web pass (section 9) is limited.

## 3. Map affixes (67), prophecy modifiers (60), omens (8): what they do, and fun or grind?

### Takeaway
- **Map affixes** are rolled modifiers on map items. 30 hit mobs (+HP/armour/crit/element conversion/resists/penetration/lifesteal...); 37 hit players (−resists, −regen, −damage, less AoE/leech...). 26 of the player ones are **Prophecy curses** that players opt into inside maps for "Prophecy Coins".
- **Prophecy modifiers** are *reward* modifiers: they shape what the coins can buy (gear type, rarity, gem/jewel rarity, each with a cost multiplier).
- **Omens** are not map content at all. They are rare gear "set" items whose stats grow with how many matching-rarity gear pieces you wear.
- The overall loop is classic PoE risk/reward juice. It is deep for ARPG fans, but it is a numbers layer on top of the same rooms.

### Cited Findings
- **Affix data schema**: `affected` (Mobs/Players), `stats[]` (type MORE/FLAT, min-max roll), `map_resist`, `map_resist_bonus_needed`, `prophecy_type`, `req`, `weight` — MNS/mmorpg/mmorpg_map_affix/accurate.json.
- **Mob affixes (examples, rolls %)**:
  - accuracy MORE 15-30; armor MORE 20-40; health MORE 15-30; dodge + spell dodge MORE 15-30; crit +10-15 / crit dmg +5-10; lifesteal 15-30.
  - elemental/chaos resist +15-30 / +30-60; armor penetration 15-30; elemental penetration 10-20; move speed +10-20 with knockback resist 25-50.
  - "X_atk" affixes: physical-to-element 30-60 + 30 % extra element damage + ailment chance 15-30 (fire/water/lightning/chaos).
  - Disabled at weight 0: bonus_exp (+50 % XP), extra_mob_drops (+50 %), chaos_pene.
  - Source: MNS/mmorpg/mmorpg_map_affix/*.json.
- **Player affixes (non-prophecy)**:
  - −20 to −40 resist and −2 to −4 max resist per element; −15 to −30 % MORE element damage.
  - −10 to −20 % armour/dodge/health/mana/energy/magic-shield regen; less crit; −15 to −30 % AoE; slower projectiles; reduced leech (weight 500) — same folder.
  - "Doubled non-Prophecy elemental resistance penalties from Maps affixes" (0.9.5) after an earlier "Map Affix nerfs... Reducing volatility/variance" (0.9.1c) — ACL/.
- **26 prophecy curses** (`req: "prophecy"`; types `damage_taken`, `damage_done`, `less_crit`, `less_resource`): −30 % MORE armour/dodge/crit/crit dmg/ailment damage/ailment chance/one element's damage; −20 % health/mana/energy/magic shield (+regen); −10 max resist of one element; −25 % total damage; +20 % damage received; "always crit-vulnerable to element X" — MNS/mmorpg/mmorpg_map_affix/prophecy_*.json.
- **Prophecy as a map event** (quest text): "Prophecy Event: find altars in Maps that can apply curses to yourself. The more curses you have on yourself, the more Prophecy Coins you can earn from killing mobs. Use these Coins to purchase rewards in Maps via the Hub! Note that Legendary and Mythic rewards only appear starting in Tier 20 and 40 maps" — Q/act_v.snbt ("More Map Content").
- **Prophecy loop (code)**:
  - A **Prophecy Altar** (map content) can only be used with no enemies within `PROPHECY_SEARCH_RADIUS` (CTE: 0). It offers 3 curses, each of a different `prophecy_type`, and regenerates `PROPHECY_OFFERS_PER_REROLL` = 18 reward offers — MNSSRC/prophecy/ProphecyAltarBlock.java, PlayerProphecies.java; mine_and_slash-server.toml.
  - **Coin drops**: base chance `PROPHECY_COIN_DROPRATE` (CTE 1.5) × (1 + map tier/25) × number of curses taken, and only in maps — MNSSRC/loot/generators/ProphecyCoinLootGen.java.
  - **Rewards**: cost = 10 × amount (1-3 items) × each modifier's `cost_multi`. You can only buy inside the same map ("this is to stop people gathering points in lvl 1 maps and going into lvl 100 tier 100 maps") — MNSSRC/prophecy/ProphecyGeneration.java, PlayerProphecies.java.
  - **Guide text**: "1) Kill Monsters while Cursed to gain Prophecy Coins 2) You can Buy Rewards in this GUI 3) The More curses you accept, the more coins you will earn. Coins earned in Low-level maps won't work in High level maps" — MNSSRC/../../../../resources/assets/mmorpg/lang/en_us.json l.2008.
- **60 prophecy modifiers**:
  - GEAR_TYPE: 40 gear bases, cost ×2 (rings/necklaces ×2.5, weight 1500; trident 0).
  - GEAR_RARITY: rare ×2, epic ×3, legendary ×4 (tier ≥ 20), mythic ×5 (tier ≥ 40), unique ×10.
  - SKILL_GEM_RARITY and JEWEL_RARITY: rare ×2.5 to mythic ×7.
  - Each type has a 75 % chance to apply (`PROPHECY_*_CHANCE = 75`) — MNS/mmorpg/mmorpg_prophecy_modifier/*.json; mine_and_slash-server.toml.
  - Prophecy "starts" (the reward kind) are Gear, Jewel, Rune, Support Gem and Aura Gem prophecies — MnS lang l.2539-2543.
- **Omens (8)**: blood, echoes, fangs, flames, mirrors, shadows, storms, waves. Each gives 3 stats, e.g. flames: MORE fire dmg 2-10, fire res +3-15, max fire res +0.6-3. `lvl_req 0.5` — MNS/mmorpg/mmorpg_omen/*.json.
  - Omen strength = (sum of required rarity counts × 10 + slot requirements × 10) × rarity multiplier: "the more difficult the omen is to assemble, the more stats it provides" — MNSSRC/database/data/omen/OmenData.java.
  - Drop rate `OMEN_DROPRATE = 0.05` — mine_and_slash-server.toml.
  - Omens are a curio slot ("curios.identifier.omen") — MnS lang l.133. [2.x] Strongboxes can drop "only omens" — ACL/ 2.0.0.
- **Death penalty layer ("Favor")**: at max 2500, dying costs 500 Favor, looting a chest gains 5; Favor rarity multiplies loot — mine_and_slash-server.toml; MNSSRC/capability/player/data/DeathFavorData.java.

### Inferences
- **Real decisions, low readability**: the affix layer gives real choices (run or skip a map; how many curses to take), but every modifier is a percentage on an already huge stat sheet. Mainly ARPG veterans will read it.
- **Fit with Realmforge**: the owner disliked CTE's grind and unnecessary systems, so the full map/affix/prophecy stack is the wrong scale. A small "dungeon key modifier" (1-3 readable mutators per key) would keep the risk/reward hook.
- **Not needed for v1**: omens are character-build content; they are noted only because they drop from maps and strongboxes.

### Gaps
- The exact prophecy altar spawn rate per map and the "league" weights live in Library of Exile / MnS data not inspected in full (MNS has a `prophecy_content` relic stat; MnS lang has "library_of_exile.league.prophecy").

## 4. Gateways (Gateways to Eternity) and Act Gateways

### Takeaway
CTE2 uses Gateways to Eternity for **portable wave fights**. Crafted "gate pearls" open a portal anywhere, and since 0.8.0 "Act Gateways" also spawn directly when dimension mobs die. It spawns 2-6 scripted waves of named mobs, ends with a boss wave and a loot table, and punishes failure (blindness, explosion, extra mobs). It is a wave-survival encounter without an instance: no arena, no layout. CTE2 uses it mainly as a **boss summoner and farm** for campaign mini-bosses and Dimension Runes.

### Cited Findings
- **What the mod does** (current Modrinth page, paraphrased from a summarised fetch):
  - Craft a Gate Pearl and right-click a block to summon a Gateway. A wave is won by killing all its enemies before the wave timer runs out.
  - Each wave has wave rewards, and the final wave gives "Completion Rewards". If the timer expires, the Gateway vanishes in lightning.
  - Defaults: spawn within 8 blocks of the edge, 32-block leash.
  - Endless Gateways: "These Gateways will get progressively more difficult" — https://modrinth.com/mod/gateways-to-eternity
- **Pearls**: 22 CTE2 gateway definitions in CFG/gateways/gateways/ (17 have crafting recipes in CFG/gateways/recipes/):
  - 5 "basic" (blaze, enderman, slime, witch, zombie).
  - 3 "endless" (blaze [ghasts], labyrinth [Undergarden], darkness [Deeper Darker]).
  - 5 "act" (i-v; no recipes).
  - 9 themed: illager_invasion, overworldian_nights, ice_maze, emerald_grove, hellish_fortress, depths, the_deep_sea (no recipe; source not found), captain_cornelia, remnant.
  - 2 seasonal events in the cte_events pack (spooky, winter) — INST/config/openloader/data/cte_events/data/gateways/gateways/event/*.json.
- **Crafting**: the 17 craftable gateways (all except the act, event and deep-sea ones) use MnS stones + biome or dimension materials, e.g.:
  - Depths = froststeel + utherium + naga fang + `mmorpg:stone/4` + ender eye.
  - Remnant = sculk bones + `mmorpg:stone/5` + void worm eye.
  - Illager Invasion = iron axe + crossbows + gold coin + `mmorpg:stone/2` + ender pearl.
  - Source: CFG/gateways/recipes/**/*.json.
- **Wave structures (examples)**:
  - **Basic zombie**: 5 waves (3→7 zombies + 1→3 husks). Modifiers grow +5 %/wave HP, damage and speed (max +20 %). Wave times 800-1200 ticks, 100-tick setup. Each wave gives entity loot + 1-2 rolls `cte2:cte2_gateways`; the last wave gives 500 XP; final reward 3 rolls — CFG/gateways/gateways/basic/zombie.json.
  - **Illager Invasion**: 6 waves, from pillagers/vindicators/evoker/iceologer up to ravagers, then illusioner + inquisitor + evoker, then an Invoker boss wave (max 6000 ticks). Final: 5 rolls + Hero of the Village 1800 s. Failure: blindness + 2 pillagers — CFG/gateways/gateways/illager_invasion.json. Kept "as Illager Invasions are no longer possible" — ACL/ 0.9.3.
  - **Depths**: 5 waves (forgotten → rotwalkers/rotbeasts → umvuthana → Umvuthi boss → Sculptor boss with a 200-tick setup). Failure: explosion strength 4 + 2 naga — CFG/gateways/gateways/depths.json; "additional wave at the end that summons the Sculptor... longer set-up time so that you can recover" — ACL/ 1.0.0.
  - **Emerald Grove**: farming-themed, with seeds/saplings/crops per wave. The final reward summons 6 cows/chickens/sheep/pigs — CFG/gateways/gateways/emerald_grove.json.
  - Seasonal **spooky/winter** events: mobs with ×2 to ×50 HP and diamonds per wave; the final reward summons a Grottol — cte_events gateway files.
- **Spawning rules**: `spawn_range 4`, `leash_range 16`, `spacing 100` — every CTE gateway file. "Gateways now have a smaller radius for spawning mobs (no more mobs spawning on high ceilings) and will leash mobs back to the Gateway if they go 16 blocks out instead of 32" — ACL/ 1.0.3.
- **Act Gateways**:
  - "Mobs in dimensions up to and including Act V can now spawn Gateways on death. The Gateway they spawn depends on which dimension you're currently in! Successfully completing the Gateway gives you rewards which may include the Dimension-specific Rune!!!" (0.8.0). Later: "Act Gateways now have a 2 minute cooldown period, per player" (1.0.3); "Doubled time limit of waves in Act Gateways... Act V Gateway now includes Purpur Golems as the final wave" (1.0.0) — ACL/.
  - Each act gateway has 4 waves of that dimension's mobs (e.g. Act II: piglins/hoglins → lemurians + crimson mosquitoes → 6 wither skeletons → blazes + red wyrms). Final reward: 3 rolls `cte2_gateways` + 1 roll of the act's rune table (`cte2_owd`, `cte2_net`, `cte2_und`, `cte2_bri_daw`, `cte2_end_sid`) — CFG/gateways/gateways/act/*.json.
- **Gateways as a boss source in quests**:
  - "The Invoker... can also apparently be encountered through forced Gateway of the Raiders" — Q/act_i.snbt.
  - "you may have better luck forcing a portal to the past with a Gateway of the Labyrinth" (Forgotten Guardian) — Q/act_iii.snbt.
  - "If you're not having any luck, consider the Gateway of Darkness" (Stalker) — Q/act_v.snbt.
  - The first gateway is a quest reward ("You've found a Zombie Gateway... Try using the pearl away from a village") — Q/act_i.snbt.
- **Loot**: `cte2:cte2_gateways` is a small generic table (iron/gold 1-2, bread, wheat, bucket, redstone, coal, seeds...) — CFG/cte2/loot_tables/cte2_gateways.json. Gateway mobs have a 50 % chance to not count toward the anti-mob-farm penalty (`GATEWAYS_MOB_CHANCE_TO_NOT_PROC_PENALTY = 50.0`) — INST/config/anti_mob_farm-common.toml.

### Inferences
- **Close to the owner's planned wave instance**: Gateways is very near the "wave-survival instance" type the owner wants, minus the arena: a data file per encounter, scripted waves, per-wave attribute ramps, failure penalties, a final boss wave. Combined with Obelisk's per-wave affixes and an arena dimension, it gives a strong template.
- **Fixes a placement gap**: CTE uses gateways to make structure-bound bosses repeatable on demand. That fixes "I can't find the structure" frustration but also makes bosses farmable outside their world context.

### Gaps
- How act gateways are triggered on mob death (mod or script) was not found in the export (probably Mine and Slash Extras, whose jar is absent).

## 5. Dimensions, structure mods, progression role and mob level scaling

### Takeaway
- **Dimensions**: CTE2 has 8 "open" dimensions: Overworld, Nether, Undergarden, Everbright + Everdawn (Blue Skies), End (with The Outer End biomes), Otherside (Deeper and Darker) and Twilight Forest. On top come 3 instanced side dimensions (Dungeon Realm, Harvest, Obelisk). **No Aether** is in the pack.
- **Campaign**: each FTB Quests "Act" maps to a dimension (0/I Overworld → II Nether → III Undergarden → IV Everbright/Everdawn → V End/Otherside → Epilogue Twilight Forest at level 90+).
- **Mob levels**: they scale to the player but are capped per dimension, and **"enrage"** to a higher band once the player passes level 50+, so older dimensions stay relevant.
- **Structure mods**: they supply the open-world dungeons (When Dungeons Arise, Dungeons Enhanced, YUNG's, Stalwart, Structory, Repurposed Structures, Moog's, Explorify, Masik's Puzzle Dungeon, etc.), mostly used through an exploration checklist and boss-hunt quests.

### Cited Findings
- **Mod list** (relevant mods; INST/modlist.html, 401 entries):
  - Dimensions: The Twilight Forest, Blue Skies (+ Sky Breaker limit remover), The Undergarden, Deeper and Darker, The Outer End.
  - Structures: When Dungeons Arise + Seven Seas, Dungeons Enhanced, Stalwart Dungeons, Structory + Structory: Towers, YUNG's Better Dungeons/Mineshafts/Strongholds/Desert Temples/Jungle Temples/Witch Huts/Ocean Monuments/Nether Fortresses/End Island/Bridges/Extras, Repurposed Structures, MES (Moog's End Structures), MNS (Moog's Nether Structures), Explorify, Masik's Puzzle Dungeon, The Lost Castle, ChoiceTheorem's Overhauled Village, Sky Villages, Kobolds!, Goblin Traders, MCA Reborn, Better Archeology, Realm RPG: Pots & Mimics.
  - Worldgen: Terralith, Oh The Biomes We've Gone, TerraBlender, Lithostitched, Project: Vibrant Journeys.
  - Bosses/mobs: Mowzie's Mobs, Born in Chaos, Bosses of Mass Destruction, Remnant Bosses, Infinity Golem, Twilight Forest Bosses Resurrection, Aquamirae, Alex's Mobs, Monster Plus, Friends&Foes, Mutant Monsters, Creeper Overhaul, Illager Invasion, Risk of Rain Mobs, Realm RPG Dragon Wyrms / Sea Dwellers.
  - Encounter systems: Gateways to Eternity, Temporary Spawners, Anti Mob Farm, Respawning Structures, Lootr, Bountiful, Enhanced Celestials (blood moons).
  - Travel: Waystones, Paragliders, MineMenu, TelePass, Small Ships, ChocoCraft, Elytra Slot, Explorer's Compass, Nature's Compass, Xaero's Minimap/World Map, Traveler's Titles, Structure Credits.
  - No Aether and no Advent of Ascension.
- **Level bands** (MnS `mmorpg_dimension` configs; all `scale_to_nearest_player: true`):

  | Dimension | Levels | Enraged band (once player level ≥ its min) | Mob strength | Tier |
  |---|---|---|---|---|
  | Overworld | 1-15 | 50-70 | ×1.0 | 0 |
  | Nether | 10-30 | 55-75 | ×1.1 | 0 |
  | Undergarden | 25-40 | 60-80 | ×1.2 | 1 |
  | Everbright / Everdawn | 35-55 | 65-85 | ×1.3 | 1 |
  | End | 50-65 | 70-90 | ×1.4 | 2 |
  | Otherside | 60-75 | 75-95 | ×1.5 | 2 |
  | Twilight Forest | 90-100 | none | ×2.0 (+400 % MORE health, +25 % armour, +25 % dodge, +25 elemental/chaos resist) | 3 |

  - Source: MNS/mmorpg/mmorpg_dimension/*.json.
  - Logic: with `SCALE_MOB_LEVEL_TO_NEAREST_PLAYER = true`, mob level = the lowest party member's level nearby ± `MOB_LEVEL_VARIANCE` 1. It is then capped to the dimension range, or to the secondary range if the player's level ≥ secondary min, and then to the entity config's min/max — MNSSRC/uncommon/utilityclasses/LevelUtils.java; MNSSRC/database/data/DimensionConfig.java (`getLevelRangeFor`).
  - Quest "Travelling Backwards": "At certain breakpoint levels, past Dimensions will 'enrage', increasing their level ranges for players to potentially go back and enjoy past Dimensions! Overworld 50-70, Nether 55-75, Undergarden 60-80, Everbright/Everdawn 65-85, The End 70-90, The Otherside 75-95" — Q/act_iii.snbt. Introduced in 0.9.1 — ACL/ 0.9.1.
  - "Although mobs scale to player level, they cap out in each dimension, making it possible to 'overlevel' for a dimension" — Q/act_i.snbt ("Acts").
- **Per-mod and per-boss level floors and rarity**:
  - Mod floors: Blue Skies ≥ 35, Aquamirae ≥ 35, Undergarden ≥ 25, Deeper Darker ≥ 50, Twilight Forest ≥ 90 (mod-wide). The Twilight Forest per-mob files still say 55 — the mod-wide 90 wins via the dimension band.
  - Data quirk: CTE's `mowziesmobs.json` mod-wide file targets `"identifier":"undergarden"`, so Mowzie's floors come only from per-mob files (naga/foliaath 25, umvuthi/sculptor/wroughtnaut 35, frostmaw 50).
  - Bosses get a forced rarity and elemental kit, e.g.:
    - TF Lich: mythic, lightning 100 % conversion + electrify.
    - Snow Queen: mythic, water + 80 % freeze.
    - Hydra: mythic, fire, dmg ×1.5.
    - Wither: rare, ≥ 30.
    - Ender Dragon: rare, ≥ 50.
    - Warden: rare, ≥ 60.
    - Bosses of Mass Destruction: epic, ≥ 90 in the entity configs. The epilogue quests say "Mythic Rarity", so these sources conflict.
  - Animals are capped at level 1 with 0 XP/loot — MNS/mmorpg/mmorpg_entity/{all_mobs_in_mod,specific_mobs,mob_types}/*.json.
  - Quest: "Mobs from Twilight Forest spawn at a minimum of level 90!" — Q/epilogue.snbt.
- **Elemental affinity per dimension** (quest "Mob Affinities"): Overworld none; Nether fire; Undergarden chaos; Aquamirae mobs cold; Everbright cold + lightning; Everdawn fire + chaos; End fire + lightning + chaos; Otherside chaos; Twilight Forest none — Q/act_ii.snbt. Implemented as `phys_to_<element>` stats in entity configs (e.g. undergarden `phys_to_chaos 50`, aquamirae `phys_to_water 50`) — MNS/mmorpg/mmorpg_entity/all_mobs_in_mod/*.json.
- **Dimension entry and campaign framing**:
  - Act I ends with "rifts to other dimensions have opened in the Overworld. You'll need to go into each dimension and stabilize the inter-dimensional connection" (Nether via a vanilla portal) — Q/act_i.snbt.
  - Undergarden: "Craft a Catalyst using your Nether Star... portal with Stone Bricks" — Q/act_ii.snbt (CTE overrides `undergarden/recipes/catalyst.json`).
  - Blue Skies: "find the Gatekeeper in Gatekeeper Houses... lighter for sale" (Zeal Lighter); a "Deterministic Portals" quest lets you craft or buy the portal blocks — Q/act_iv.snbt.
  - End: stronghold + eyes (Endolocator offered as an infinite eye) — Q/act_iii.snbt, act_iv.snbt.
  - Otherside: "The Warden seemed to be protecting a gateway to a new undiscovered dimension" — Q/act_v.snbt.
  - Twilight Forest: "create a 2x2 shallow pool of water... in The Otherside... throw in a diamond" — Q/epilogue.snbt. The Explorer's Compass is blacklisted for TF structures; you must use the TF Magic Map Focus — Q/epilogue.snbt; INST/config/explorerscompass-common.toml (`structureBlacklist = ["twilightforest:*"]`).
- **Dimension structure dungeons used as act bosses**:
  - Blue Skies: Blinding Dungeons (Summoner/Alchemist) unlock the 4-floor Nature Dungeon ("massive green labyrinth... Each floor will contain 1 of 4 keys") and Poison Dungeon (Starlit Crusher, Arachnarch) — Q/act_iv.snbt.
  - Stalwart: Keeping Castle / Awful Dungeon (Nether Keeper, Awful Ghast; "summon it by placing a Nether Star in the altar... cannot be found conventionally with the Explorer's Compass") — Q/act_ii.snbt.
  - Undergarden Catacombs (Forgotten Guardian), Mowzie's Monastery (Sculptor), Aquamirae Ice Maze/Shelter (Eel, Captain Cornelia) — Q/act_iii.snbt.
  - Epilogue: Bosses of Mass Destruction "Primal" bosses: Night Lich (Lich Tower in the End's Crystal Crags, found with crafted Soul Stars), Nether Gauntlet (Gauntlet Arenas in the Otherside's Blooming Caverns), Void Blossom (Otherside Echoing Forests, found with Void Lilies), Obsidilith (moved with the Night Lich "to be in The End rather than The Otherside", ACL/ 1.0.7). The quests say each has "minimum level of 90 and... Mythic Rarity". Then comes the TF progression (Naga Courtyard → Lich Tower → Labyrinth/Minoshroom → Hydra Lair → Goblin Knight Stronghold → Dark Tower → Aurora Palace/Snow Queen), with Respawn Monuments (ironwood/gold/ancient-debris rings + wither skull "MUST face North") — Q/epilogue.snbt.
- **Exploration chapter**: about 30 "discover structure X" quests (each 50 XP × level) — Better Mineshafts, Deeper Darker ancient temple, Dungeons Arise abandoned/infested temple and coliseum, Friends&Foes citadel/illusioner shack, Illager Invasion fort/labyrinth, Repurposed mansions, Better Strongholds/Fortresses, Terralith spire/fortified villages, Undergarden catacombs, Kobold dens, etc. "The Adventurer" challenge (all of them + Masik's Puzzle Dungeon); biome-discovery quests (53 Overworld, 16 Undergarden, 4 Otherside, 5 Nether biomes); End City — Q/exploration.snbt.
- **CTE2 dimension-population edits**:
  - Born in Chaos Dark Towers moved into the End/Everdawn/Everbright via structure overrides (`biomes: #minecraft:is_end`, `#blue_skies:everdawn`, `#blue_skies:everbright`) — CFG/born_in_chaos_v1/worldgen/structure/dark_tower_*.json.
  - In Control denies some Born in Chaos bosses in the Overworld and snow golems in the Nether — INST/config/incontrol/spawn.json.
  - Changelog: "Populated Undergarden, Blue Skies dims, and Otherside with some fun structures and tons of new mobs from Ice and Fire, Mowzie's, When Dungeons Arise!" (0.8.0); "Increased Naga spawn rate in the Undergarden by 5x" (1.0.0); "Lowered spawn rate of Umvuthana and Naga in the Undergarden" (0.9.3) — ACL/.

### Inferences
- **Dimensions as progression gates**: each dimension in CTE2 is a level band + a boss list + an element theme + a rune drop. That closely mirrors the owner's "chapter = level band, realm = themed world" plan.
- **"Enrage" bands**: they are a cheap way to keep finished realms useful for gathering and replays without new content.
- **Fullness comes from stacking mods**: dimension "fullness" comes from other mods' structures and mobs pushed in by datapack. The CTE team had to repeatedly re-tune spawn rates and move bosses between dimensions, a sign of fragile, borrowed content. Realmforge builds in-house, so it should plan the density per realm deliberately.

### Gaps
- Full per-dimension structure lists (which mods generate in Undergarden/Blue Skies/Otherside) were not enumerated from the datapack tags; only the overrides were read.

## 6. Worldgen: biome overrides and structure sets

### Takeaway
CTE2's 64 vanilla biome overrides change almost nothing: smaller spawn groups (glow squid 4-6 → 1-3 in 52 Overworld biomes; Nether ghast/piglin groups 4 → 2-3) plus two Nether feature fixes. Most likely they cut entity counts and performance load (an inference; no changelog line states the reason). The real worldgen changes are **structure-set spacing**: woodland mansions about 4× denser (spacing 80 → 40), stronghold rings pushed 1.5× farther out, End cities about 0.6× as dense (spacing 20 → 18 but frequency 0.5), and many mod sets re-tuned (Dungeons Arise major structures spacing 50 / separation 45). Terralith's welcome and spawn-reset functions are neutralised.

### Cited Findings
- **Biome diff** against vanilla 1.20.1 JSON (fetched from https://github.com/misode/mcmeta/tree/1.20.1-data):
  - 52 Overworld/ocean/cave biomes differ only in `underground_water_creature` (glow squid min/max 4-6 → 1-3).
  - 7 files (deep_dark, the_end, end_*, small_end_islands, the_void) are identical to vanilla.
  - nether_wastes monsters: ghast 4 → 2-3, zombified piglin weight 100 → 80 with group 4 → 3, others 4 → 3.
  - soul_sand_valley / warped_forest: strider weight 60 → 40, groups 4-5 → 3-4.
  - crimson_forest: removes the `crimson_fungi` feature.
  - basalt_deltas: moves delta/basalt-column features from step 4 to step 0 (likely a feature-order fix).
  - Sources: CFG/minecraft/worldgen/biome/*.json vs https://raw.githubusercontent.com/misode/mcmeta/1.20.1-data/data/minecraft/worldgen/biome/<biome>.json.
- **Vanilla structure sets** (CTE vs vanilla):
  - woodland_mansions: spacing 80 → 40, separation 20 → 10.
  - strongholds: ring distance 32 → 48 (count 128, spread 3).
  - end_cities: spacing 20 → 18 + `frequency 0.5`.
  - Sources: CFG/minecraft/worldgen/structure_set/*.json vs https://raw.githubusercontent.com/misode/mcmeta/1.20.1-data/data/minecraft/worldgen/structure_set/{woodland_mansions,strongholds,end_cities}.json.
- **Mod structure sets (CTE values; mod defaults not verified)**:
  - Dungeons Arise: major (31 structures: coliseum, keep_kayra, shiraz_palace, plague_asylum, mechanical_nest, foundry...) spacing 50 / separation 45; minor (fishing hut, wishing well, tree house, bathhouse, abandoned temple, lighthouse) spacing 26 / separation 18 — CFG/dungeons_arise/worldgen/structure_set/*.json. Changelog: "Increased Dungeons Arise structure spawn rates by about 25 %" (0.8.0), "Lowered Structure spawn rate of Bathhouse and Lighthouse" (0.8.3) — ACL/.
  - Dungeons Enhanced (structure_gel grid placement): dungeon_variant spacing 16 freq 0.59; miners_house 20/0.8; druid_circle 32/0.68; jungle_monument 32/0.74; large_dungeon 40/0.56 — CFG/dungeons_enhanced/worldgen/structure_set/*.json.
  - Structory: outcast_villager (chapel/graveyard/camp/firetower/boat...) 34/12; old_manor 40/15; mid_rare_ruin 30/16 freq 0.7. Structory Towers: towers (13 types), rare towers 55/16 freq 0.625, ultra-rare warped greatsword 80/42, nether towers 33/8 freq 0.9, end towers 60/8 freq 0.4 — CFG/structory*/worldgen/structure_set/*.json.
  - Blue Skies (grid): village 18, gatekeeper house 22, frozen bunker 16, nature/poison dungeon 42, blinding dungeons 30/45 — CFG/blue_skies/worldgen/structure_set/*.json. "Blue Skies dimension structure and mob spawn rates have been increased" (0.8.0) — ACL/.
  - Mowzie's: wrought chamber 10/6, frostmaw 16/6 (10-chunk exclusion from villages), monastery 25/8, umvuthana grove 32/10. Aquamirae: ship 20/8, outpost 15/8, shelter 30/15 — CFG/mowziesmobs, CFG/aquamirae structure_set files.
- **Terralith**: CTE ships `cancel_terralith` functions that comment out Terralith's toast message and spawn-reset (`spreadplayers`/`setworldspawn`) — CFG/cancel_terralith/functions/*.mcfunction. Terralith loot tables are overridden (village/fortified/desert/spire/underground) — CFG/terralith/loot_tables/.
- **Waystones worldgen**: waystones generate in Overworld/Nether/End with `frequency = 20` and are forced into villages (`spawnInVillages`/`forceSpawnInVillages = true`) — INST/config/waystones-common.toml.
- **Respawning Structures**: structures respawn 96 h (5760 min) after the last activity if < ~200 blocks were changed and they lie > 200 blocks from a player respawn point; no difficulty increase — INST/config/respawningstructures.json; "Structures now respawn after a long period of time" (0.9.3) — ACL/.
- **Blood moons**: Blood Moon 10 %/night (≥ 4 nights apart); Super Blood Moon 5 % on full moons (≥ 20 nights apart) — CFG/enhancedcelestials/enhancedcelestials/lunar/event/*.json.

### Inferences
- **Density knobs, not new terrain**: CTE2's world density comes from ~25 structure mods plus spacing tweaks. For "full" realms, Realmforge needs its own small-structure layer (spacing ~10-30 chunks) plus rarer landmarks (~40-80 chunks). These numbers are a usable starting calibration.

### Gaps
- Mod-default spacing for Dungeons Arise, Dungeons Enhanced, Structory, etc. was not checked against their jars, so "denser or sparser than default" is unverified except for vanilla sets.

## 7. Travel and QoL: waystones, paraglider, menu wheel, teleports

### Takeaway
CTE2 removes most walking friction:
- Waystones are everywhere, cost no XP for normal use (they cost a few Lightman's coins), and come with a free Warp Stone in the prologue in [2.x].
- Paragliders use no stamina; "Spirit Orbs" from structure chests turn into Heart Containers (+2 HP each).
- Chocobos (some can fly), Small Ships and Elytra cover longer trips.
- MineMenu was a radial menu of hotkeys (replaced by KeyFlow in [2.x]).
- KubeJS scripts patch dungeon teleports: return-to-entry points and the boss "Soul Stone".

### Cited Findings
- **Waystones**:
  - `minimumBaseXpCost = 0.0`, `maximumBaseXpCost = 0.0`, so no XP cost except a 3-level dimensional warp.
  - `dimensionalWarp = "GLOBAL_ONLY"` (cross-dimension only via global waystones); Warp Stone cooldown 360 s; inventory warp button disabled; generated waystones unbreakable — INST/config/waystones-common.toml.
  - Money cost via the Waystone×Lightman's addon: 1 coin per 100 m, max 10 — INST/config/waystoneandlightman-common.toml.
  - Craftable variants: sandy waystone = warp stone + stone bricks + `mmorpg:stone/2`; normal/mossy via soap/vine conversions — CFG/waystones/recipes/*.json. "Reduced the cost of the craftable Waystone drastically" (1.0.6), "Added 3 new craftable Waystone variants" (1.0.4) — ACL/.
  - Quest: "Waystones are spread throughout the world... often found in villages. Take some Warp Scrolls so you can easily return to any discovered Waystone!" — Q/act_0.snbt. "Global Waystones are usable across dimensions. The Warp Stone allows you to teleport to any waystone you've unlocked but has a cooldown... scrolls... single use Warp Stones" — Q/act_i.snbt. [2.x] "You now receive a free Warp Stone in the prologue" — ACL/ 2.0.2.
- **Paragliders**:
  - `paraglidingConsumesStamina = false`, `runningAndSwimmingConsumesStamina = false`, durability 0 (unbreakable); ascending winds from fire/soul fire/lit campfires; spirit orbs come from loot (`spiritOrbLoots = true`, spawner drops 0); the Ender Dragon drops a vessel; 10 starting hearts, up to 100 heart containers — INST/defaultconfigs/paraglider-server.toml.
  - 43 spirit-orb loot modifiers on structure chests (10-25 % each: bastions, strongholds, Blue Skies dungeons/bunkers/gatekeeper houses, Deeper Darker ancient temple, Masik's Puzzle Dungeon, Terralith spires, Undergarden catacombs...) — CFG/paraglider/loot_modifiers/spirit_orbs/*.json.
  - About 40 boss "vessel" drops (e.g. Wither 25 %, TF Lich 75 %) — CFG/paraglider/loot_modifiers/*.json; registered in CFG/forge/loot_modifiers/global_loot_modifiers.json.
  - Quest: "Spirit Orbs... exchanged at Goddess Statues for Heart Containers. Each Heart Container increases your base Health by 2, up to a maximum of 200 Health" — Q/act_0.snbt. "Paragliders can be used to easily traverse large open distances... You can use source of fire to create ascending winds" — Q/act_i.snbt.
- **MineMenu wheel** (1.1.3): radial entries open the Inventory HUD settings, Jade settings, minimap settings, waypoints, wallet, the Mine and Slash Hub, Quests, FTB Teams, Food Book and Professions Shop — INST/minemenu/menu.json. [2.x] "Added KeyFlow which replaces MineMenu" and "Elytra usage is now toggle-able... via the quick-access wheel" — CL/ (2.1.0, line 503-509).
- **Dungeon teleport scripts (KubeJS)**:
  - `return_script.js` saves the player's coordinates and dimension when they use a map device, harvest block, obelisk, or reward/boss/uber teleporter. It teleports them back when they use `library_of_exile:teleport_back` or right-click a Home Pearl, and falls back to a hard-coded spawn (1802, 66, -423).
  - Hidden advancements tag players `Map`/`Harvest`/`Obelisk` on dimension change and clear all tags on entering a normal dimension.
  - Sources: INST/kubejs/server_scripts/return_script.js; INST/config/openloader/data/cte_fix/data/harvest_fix/{advancements,functions}/.
- **TelePass**: a crafted "teleportation compass" is bound to its crafter. Another player holding it can "teleport directly to the compass owner" — https://www.9minecraft.net/telepass-mod/ . CTE2 recipes use a Home Pearl + gold (3 uses) or diamond (15 uses) — CFG/telepass/recipes/*.json; INST/config/telepass-common.toml (`goldDurability = 3`, `diamondDurability = 15`). It is a "join your friend" item.
- **Other movement**:
  - ChocoCraft: "You can even breed special Chocobos that have the ability to fly!"; mounts and pets travel through waystones in cages or on leads — Q/act_i.snbt.
  - Small Ships: 4 ship types, ±10 % speed in cold/warm water — Q/exploration.snbt.
  - Elytra: from the End; End-island exploration quest — Q/act_v.snbt.
  - Explorer's Compass (structures, radius 10 000) and Nature's Compass (biomes) — Q/act_i.snbt; INST/config/explorerscompass-common.toml.
  - Magnets "incredibly useful for dungeons" — Q/act_ii.snbt.

### Inferences
- **Fits the owner's travel stance**: CTE2's travel stack (cheap waystones + stamina-free glider + mounts + boss teleport item) is close to the owner's "hates walking thousands of blocks" stance.
- **The scripts are a warning**: CTE needed KubeJS hacks to make dungeon exit/return and boss-finding reliable. A home-built dungeon system should own return points and boss-room access natively.

### Gaps
- KeyFlow (the [2.x] wheel) was not inspected (not in the 1.1.3 export).

## 8. Other world content: temporary spawners, bounties, anti-mob-farm

### Takeaway
CTE2 discourages stationary farming and pushes players to move:
- **Spawners self-destruct** after 1 minute of activity.
- **Chunks run dry**: after 15 free kills, each kill costs −2 % loot in that chunk; this recovers 5 %/min, and every looted chest adds +5 free kills.
- **Bounty boards** add timed fetch/kill contracts that level a "Bounty Hunting" profession.
- **Structures respawn** after 96 h.

### Cited Findings
- **Temporary Spawners**: `ENABLE_SPAWNER_DESTRUCTION = true`, `SPAWNER_LIFETIME_MINUTES = 1`, `SPAWNER_COOLDOWN_MINUTES = 3` — INST/defaultconfigs/temporary_spawners-server.toml. The logic (2021 Fabric source) counts ticks while a player is near and, after the lifetime, sets the spawner to air (or starts the cooldown if destruction is off) — https://github.com/RobertSkalko/Temporary_Spawners (TempSpawnerComponent.java). The 1.20 Forge version may differ.
- **Anti Mob Farm**:
  - `one_time_free_kills_per_chunk = 15`, `mob_kill_chunk_penalty = 0.02`, `penalty_regen_per_minute = 0.05`, `added_free_kills_on_chest_looted = 5`, `min_player_dmg_for_loot = 0.5`.
  - Excluded dimensions: `mmorpg:dungeon` (a stale id; the map dimension is `dungeon_realm:dungeon`, but MnS skips the anti-farm modifier in maps anyway, MNSSRC/loot/LootInfo.java), Everbright, Everdawn. Bosses are excluded — INST/config/anti_mob_farm-common.toml.
  - Quest: "a limit on how many mobs can be killed within a chunk before they stop yielding any drops or experience! The chunk will recover over time. This is done to encourage travel and exploration instead of the usage of automated experience methods" — Q/act_0.snbt.
- **Bountiful**:
  - 15 bounty pools (villager-profession-themed item fetches + `_all_objs` kill objectives from zombies up to shriek worms); the board updates every 45 s; bounties expire; max 3 rewards — CFG/bountiful/bounty_pools/bountiful/*.json; INST/config/bountiful/bountiful.json.
  - Rewards: MnS "Bounty Hunting" profession XP (command `prof_xp_times_lvl ... husbandry`) + Bounty Slips; Empowered slips from reputation 45-75.
  - "Kill objectives now appear more frequently and will provide double the rewards" (1.1.0) — ACL/.
  - Quest: "Bounty Hunting is a Mine and Slash Profession which levels up as you complete Bountiful bounties... Bounty Boards will generate random bounties" — Q/act_i.snbt.
- **Lootr** (per-player chests) and **Structure Credits** ("You will now see what structure you are entering", 0.9.0) — INST/modlist.html; ACL/ 0.9.0.

### Inferences
- **Push, not penalty**: per-chunk diminishing returns plus self-destructing spawners and timed structure respawn form a coherent "go explore" system. Realmforge can borrow the idea (diminishing returns per area rather than hard caps) but should surface it clearly; CTE needed a quest page to explain it.

### Gaps
- None critical. Bounty board crafting/finding details not read.

## 9. Player reception (fun vs repetitive)

### Takeaway
Public reception that could be reached splits two ways:
- **ARPG and Path of Exile fans** praise the build depth and the endgame ("created for fans of PoE").
- **Mixed or negative reviews** cite grind (gear RNG), dimensions blurring into a power-through, anti-farm and automation restrictions, and mob/gateway clutter.

The pack author's own changelog says engagement dropped when players reached maps at level 55 and that the mapping experience (teleporter block, lag) was a pain point. That is why 2.0 (Aug 2026) rebuilt mapping around a PoE-style Atlas. Reddit/CurseForge comments could not be reached (blocked), so the sample is small.

### Cited Findings
- **Pack author**, on the reason for 2.0.0: "I wanted to address some of the major pain points experienced by players: the drop off in engagement at 55 (maps), the mapping experience in general (teleporter block, lag), difficulty with skill gem scaling in the early game" — ACL/ 2.0.0. Other quality-of-life fixes on the same theme are listed in CL/ 2.1.0:
  - exit-map lag ("exiting a map would cause the entire server to wait for the chunk load");
  - mob despawning that ruined exploration %;
  - an auto-salvage filter to skip disliked map layouts.
- **moddex.gg aggregate**: 3.3/5 from 6 ratings (Gameplay 2.8, Aesthetics 4.1, Performance 4.4) — https://moddex.gg/modpack/craft-to-exile-2
  - Version labels on moddex reviews look inconsistent with their dates (e.g. a June 2025 review tagged "2.0 Atlas Update"), so they may reflect the current pack version rather than the one played.
- **aroyund, 3.5/5** ("Good modpack, not great"):
  - Praise: early "gear hunting through structures was fun".
  - Complaints: "after a while you just power straight through all the different dimensions"; gear "mostly boils down to luck".
  - Wants more side content "like the first cte with the maps" — https://moddex.gg/modpack/craft-to-exile-2
- **Alter1Al, 5/5**, 500 h: "The modpack is created for fans of PoE... If you just want to kill mobs and create builds, this modpack is for you"; "the rest of the content is pretty mediocre" — https://moddex.gg/modpack/craft-to-exile-2/reviews/1244
- **aongreyfox, 2/5**:
  - "the tedious grind 1000 gear for single useful piece".
  - "It's fun when you can keep progressing, even by a little. But it drop to dead once you stuck for hour and hour".
  - "After recent change that gate spawn every where every time, result in clutter mob / item management" (the Act Gateways that spawn on mob death).
  - The author "put a lot of effort to penalty mob grind and automate" — https://moddex.gg/modpack/craft-to-exile-2/reviews/566
- **Kafuru, 2/5**: "CTE2 is not a bad pack. It's actually balanced pretty nicely"; "if you like automating stuff don't play it, its purely just a boss killing rpg pack"; "removing all mob drops is kinda dumb" (Anti Mob Farm) — https://moddex.gg/modpack/craft-to-exile-2/reviews/521
- **LogicServers review**: positive about progression ("a strong sense of progression and satisfying build combinations"). It only mentions dungeon maps in passing and says nothing about repetitiveness — https://logicservers.com/blog/craft-to-exile-2-minecraft-modpack-review
- **PCGamesN on 2.0**: mapping "unlocks once you reach Act Five and level 50... Maps are repeatable instanced dungeons across various biomes, with special mobs and bosses". It adds an Atlas "much like the PoE 2 endgame", 5 new map types, shrines, strongboxes and imprisoned monsters, and a pinnacle encounter — https://www.pcgamesn.com/minecraft/arpg-mod-craft-to-exile-2-update (date and author not shown in the fetched text)

### Inferences
- **Fun versus grind**: the dungeon loop reads as "fun for ARPG min-maxers, grind for everyone else". The core loop of the same room kits, a kill-% meter, random affixes and chest RNG has to be propped up by meta-systems (relics, prophecy, atlas, uber/pinnacle) to stay engaging. This matches the owner's dislike of CTE's "too much grind".
- **Dimensions felt like level gates**: they were not experienced as places ("power straight through all the different dimensions"). That supports the owner's goal of realms that feel full and worth lingering in.

### Gaps
- No Reddit threads could be read (reddit.com blocked for WebFetch and the shell; the WebSearch domain filter was rejected). CurseForge comments were not reachable.
- No reception data specific to Harvest, Obelisks, Gateways, Waystones or travel was found.

## Relevance to Realmforge

### Adopt
- **One instance dimension with grid plots** (Dungeon Realm's model) for the owner's instanced dungeons: a counter allocates plots, a party joins by re-using the opened portal, and the dimension can be wiped safely (DRSRC/MapDeviceBlock.java; dungeon_realm_dimension-server.toml). This matches ADR 0012 and spike 3 in docs/roadmap.md. Copy the pattern only, never the code.
- **Room kits with 6 slot types** (entrance / straight / corner / T / cross / end, ≤ 16×16 footprint) plus **text data markers** in rooms (`mob`, `pack`, `elite`, `chest`, `chance_chest` at 25 %, `boss`, `exit`), processed when the player is within 2-3 chunks.
  - It is a proven pipeline for the planned `content/rooms/` format.
  - It suits the owner building key rooms in creative while agents assemble variants (roadmap W4/W6).
  - Example: the M1 stone realm's dwarf fortress and catacombs as two kits.
- **In-instance rules**: no block breaking or placing, mobs immune to environmental damage, banned teleport items (pearls, chorus), mobs spawned once and not respawning, and a guaranteed exit block at the entrance (Dungeon Realm docs and config).
- **Return-point bookkeeping owned by the mod**: save the entry position on portal use, return there on exit or death, with a "home" fallback. CTE needed KubeJS scripts for this (return_script.js); Realmforge should build it into InstanceManager.
- **Dimension level bands with "enrage" bands** (Overworld 1-15 → 50-70 etc., MnS mmorpg_dimension): a cheap way to keep finished realms worth revisiting after a chapter cap. It fits "chapter = 10-level band".
- **Gateways/Obelisk-style wave data** for the separate wave-survival dungeon type:
  - per-wave mob lists and counts;
  - a per-wave stat ramp (+5 % HP/damage per wave);
  - a final boss wave with a longer setup;
  - per-wave announced modifiers;
  - a wave timer and failure penalty;
  - kill-scaled reward chests (1 % per kill, ×tier ×modifiers);
  - mobs leashed to 16 blocks.

  The data shape is simple and fits a crafted key + arena.
- **Travel stack that matches the owner's wishes**: cheap, plentiful waystones forced into villages (frequency 20), a free starter Warp Stone, a stamina-free glider, and a "teleport to teammate" item. For large dungeons, add a party "recall to the boss door" item like CTE's Boss Soul Stone (party within 10 blocks).

### Adapt
- **Completion**: CTE's kill-% exploration meter caused repeated bugs and full-clear grind. The owner's rule "instance complete = final boss dead" is simpler. Keep at most a light optional bonus (e.g. "cleared 80 %+ → +1 chest") instead of the finish-rarity ladder.
- **Dungeon keys**: CTE map items carry level, tier, rarity and 1-6 random affixes. For Realmforge's crafted keys, use few, readable mutators. One example: a key from the M1 dwarf NPC trades that may roll "Stone Skin: mobs +20 % armour, +1 loot chest". Avoid PoE-scale percentage soup.
- **Side encounters inside dungeons** (Harvest and Obelisk seeded as rare blocks inside maps): use sparingly as surprise rooms (a timed horde room, a wave altar) rather than separate dimensions with their own map items, relics and currencies.
- **Biome and structure density calibration from CTE values**:
  - small structures every 10-34 chunks: Mowzie's wrought chamber 10/6, Dungeons Enhanced dungeon variant 16, Structory outcast villager 34/12;
  - medium every 20-42: Blue Skies villages 18, dungeons 42;
  - landmarks 40-80: mansions 40, Structory ultra-rare towers 80/42.

  Use these as first numbers when filling realms with "small structures, landmarks".
- **Per-realm elemental affinity** (CTE: Nether fire, Undergarden chaos, Everbright cold + lightning...): a simple identity tool for realms (desert = fire, tundra = cold), but tie it to visible mob design, not just stat conversion.
- **Anti-farm**: CTE's per-chunk diminishing returns (15 free kills, −2 %/kill, +5 %/min regen, +5 free kills per chest) and self-destructing spawners (1 min) push movement but annoyed reviewers ("removing all mob drops is kinda dumb"). If used, show it in the HUD and keep it mild.
- **Respawning structures** (96 h after last activity, < 200 blocks changed): a good way to keep open-world dungeons (the owner's non-instanced type) replayable without instancing.
- **Exploration checklists per realm**: CTE's "discover structure X / all N biomes" quests (50 XP × level each, plus a challenge for all) are a cheap incentive to roam. Use them per realm with Realmforge's own landmarks, and keep the locator honest (CTE blocks Explorer's Compass in Twilight Forest so players must explore).
- **Respawnable world bosses**: a ritual to re-summon an open-world boss at its home structure, like TF's Respawn Monuments (a block ring + item on a monument), keeps bosses tied to their place instead of pulling them into a portable gateway.

### Avoid
- **Endgame-only dungeons**: CTE gates its whole dungeon system behind level 55 (50 in 2.x) and saw "the drop off in engagement at 55 (maps)" (ACL/ 2.0.0). Realmforge's plan of 2-4 dungeons per realm from chapter 1 is the right direction.
- **Layers of meta-currency**: the map → affix → prophecy coin → relic → uber fragment → atlas → pinnacle stack, plus separate Harvest gems and Obelisk essences/orbs. Each layer added grind and UI and needed constant retuning (dozens of changelog entries). This is exactly the "unnecessary systems / too much grind" the owner dislikes.
- **Borrowed bosses with no world context**: uber pools and gateways pull bosses out of their structures ("forcing a portal to the past with a Gateway of the Labyrinth"). That makes them farmable but erases the place. Realmforge bosses should live in their realm's dungeons.
- **Empty void-arena dimensions for side content** (Harvest/Obelisk dimensions are bedrock voids with one arena). They work as instances but are exactly the "empty dimension" feel the owner dislikes if a player ever sees them as places.
- **Dimensions as pure level gates**: reviewers "power straight through all the different dimensions". Each realm needs reasons to stay (NPCs, gathering, landmarks, neutral fauna), not just a level band and a boss list.
- **Mob spawns from drops** (Act Gateways spawning on mob death caused "clutter mob / item management" complaints): keep encounters intentional (keys, altars, places).
