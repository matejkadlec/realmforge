# Craft to Exile 2: progression arc, quest design, onboarding, QoL and reception (plus CTE1)

> Appendix to [the reference-game research](../reference-games.md) (RLM-94), written on 2026-10-12 by an AI research agent. Local citations name their source (the Craft to Exile 2 1.1.3 instance and the jars it ships, the Mine and Slash and Advent of Ascension source trees, the CTE2 GitHub changelogs); those files are not in this repository. Mechanics and numbers only: never reuse names, assets or code of the reference games.


Local source abbreviations used in citations (all read-only):
- `Q/` = `CTE2 1.1.3 instance: config/ftbquests/quests/` (FTB Quests SNBT)
- `PB/` = `CTE2 1.1.3 instance: patchouli_books/CTE2/en_us/` (Patchouli "Adventurer Handbook")
- `CFG/` = `CTE2 1.1.3 instance: config/`, `DCFG/` = `CTE2 1.1.3 instance: defaultconfigs/`
- `MNSDP/` = `CTE2 1.1.3 instance: config/openloader/data/cte_mns/data/mmorpg/` (CTE2's own Mine and Slash datapack, which overrides the jar defaults)
- `GH/` = `CTE2 GitHub repo: ` (`CHANGELOG.md` = 2.1.x; `ARCHIVED CHANGELOG.MD` = 0.1.0 (2023-10-05) to 2.0.4 (2026-08-10))

**Version caveat.** The local instance is CTE2 **1.1.3**: `manifest.json` gives name "Craft to Exile 2", version "1.1.3", Forge 47.4.10, MC 1.20.1 and 401 mods (`CTE2 1.1.3 instance: manifest.json`). The changelog dates 1.1.3 to 2025-11-06. The current line is 2.1.4 (2026-09-10). Between them, 2.0.0 reworked mapping (the Atlas), added 2 classes and 2.1.0 added a Mercenary companion and loot filtering (`CTE2 GitHub repo: CHANGELOG.md`, [GH/ARCHIVED CHANGELOG.MD](https://github.com/mahjerion/Craft-to-Exile-2/blob/main/ARCHIVED%20CHANGELOG.MD)). Quest numbers below are for 1.1.3 unless marked otherwise. I computed the counts with a throw-away SNBT parser; its output is in `local analysis script (not published)`. A third-party wiki made for 2.1.4 lists almost the same per-chapter counts (Prologue 27, Act I 51, Act IV 33, Act V 29, Campaign Shop 13; all others identical), so the structure has been stable ([minecraft-guides.com CTE2 wiki](https://www.minecraft-guides.com/modpacks/craft-to-exile-2/)).

## 1. Quest book structure: chapters, counts, task and reward types, gating, points

### Takeaway
CTE2's FTB Quests book has about 620 quests in 21 chapters and 4 groups: Campaign, Development, Logbook and Multiplayer. The spine is a 7-chapter campaign (Prologue, Acts I to V, Epilogue). Each act is one or two dimensions plus a level band, with a short "Main Quest" chain of 6 to 9 quests. The last main quests open the next dimension, usually by killing a boss whose drop is the next portal key. Everything else is optional side content that pays XP and loot. A separate "Rewards" chapter turns campaign milestones into talent, skill, passive and ascendancy points shared across the account.

### Cited Findings
- **Chapter groups:** Campaign, Development, Logbook, Multiplayer (`CTE2 1.1.3 instance: config/ftbquests/quests/chapter_groups.snbt`).
- **Global settings:** `progression_mode: "flexible"`, `default_autoclaim_rewards: "disabled"`, `drop_book_on_death: false`. The emergency item is `waystones:return_scroll` with `emergency_items_cooldown: 30` (`CTE2 1.1.3 instance: config/ftbquests/quests/data.snbt`).
- **Quests per chapter (parsed, total 620)** (`CTE2 1.1.3 instance: config/ftbquests/quests/chapters/`):
  - Campaign: Prologue 25, Act I 52, Act II 30, Act III 31, Act IV 31, Act V 27, Epilogue 43, Rewards 30, Ascendancy 4, Campaign Shop 11, Repeatables 10.
  - Development: M&S Professions 24, Storage Solutions 17, Magical Flora (Botania) 62, Homestead 31, Culinary Delights 33.
  - Logbook: Exploration 42, Fishing 66, Other Hobbies 29, Completionist 1.
  - Multiplayer: Jolly Cooperation 21.
  - The CurseForge text says "OVER 250 Quests with a complete Campaign, Quest Shops, and Epic Epilogue!" ([ModDex mirror of the CurseForge description](https://moddex.gg/modpack/craft-to-exile-2)). Prologue + Acts + Epilogue alone come to 239 quests, so "250" probably counts only the campaign (my inference).
- **Level bands** are printed as chapter subtitles: Act I "Lv. 1-15", Act II "Lv. 10-30", Act III "Lv. 25-40", Act IV "Lv. 35-55", Act V "Lv. 50-65" + "Lv. 60-75", Epilogue "Lv. 90" (`chapter.subtitle` in `CTE2 1.1.3 instance: config/ftbquests/quests/chapters/act_i.snbt` through `CTE2 1.1.3 instance: config/ftbquests/quests/chapters/epilogue.snbt`). Bands overlap by 5 levels; the Epilogue then jumps 15 levels past Act V.
- **Task-type mix** (parsed):

  | Chapter | Task types |
  | --- | --- |
  | Prologue | checkmark 23, advancement 1, kill 1 |
  | Act I | kill 31, item 23, checkmark 10, dimension 1 |
  | Act II | kill 13, item 12, checkmark 6, advancement 3, dimension 1 |
  | Act III | kill 13, item 10, checkmark 6, advancement 3, structure 2 |
  | Act IV | item 19, kill 15, structure 6, advancement 3, checkmark 3, dimension 2 |
  | Act V | kill 15, advancement 5, item 5, dimension 2, checkmark 2 |
  | Epilogue | kill 12, item 11 (mostly boss trophies), structure 10, advancement 5, checkmark 3, biome 2, dimension 1 |

  Side chapters are mostly `item` tasks (Magical Flora 111, Culinary 89, Jolly Cooperation 68). Exploration is 33 `structure` + 5 `advancement` tasks. Other Hobbies uses `stat` tasks (deaths, mob kills, gateways defeated).
- **Reward types:** flat M&S XP via `/mine_and_slash give exp @p N`, loot chests ("Rare Gear Loot Chest (Lv. N)"), pick-one choice tables, Lightman's coins, Gateway pearls, cosmetic hats for achievements, and "custom" icons that display point grants.
- **Flat XP per act** (sum of `give exp`): Prologue 3,150; Act I 44,975; Act II 57,250; Act III 222,250; Act IV 486,250; Act V 547,300; Epilogue 0 (items only). Single quests range from 50 XP (Prologue "Anti Mob Farm") to 35,000 XP (Act V "Untied Loose Ends").
- **Side chapters pay level-scaled XP** (`give xp_times_lvl @p N`): N = 50 for exploration and fishing finds, 100 typical, 500 for "Challenge" quests, 750 for a chapter "Achievement" (`CTE2 1.1.3 instance: config/ftbquests/quests/chapters/exploration.snbt`, `CTE2 1.1.3 instance: config/ftbquests/quests/chapters/fishing.snbt`). The 0.4.0 changelog gives the rationale: "Quests not found in the main Acts now provide experience scaled to your level. It's not a percentage, as it would be too strong at higher levels (people could just horde % rewards till 99)" ([GH/ARCHIVED CHANGELOG.MD](https://github.com/mahjerion/Craft-to-Exile-2/blob/main/ARCHIVED%20CHANGELOG.MD)).
- **Main-quest chains:** Act I 6, Act II 6, Act III 7, Act IV 9, Act V 8. The Act I lost page "Acts" says: "once you've completed the line of main quests (typically 5-7 quests in length), you will receive the quest to move on to the next act/dimension … each act is associated with a different dimension" (`CTE2 1.1.3 instance: config/ftbquests/quests/chapters/act_i.snbt`, quest "Acts").
- **Gating** (cross-chapter dependencies, parsed):

  | Unlocks | Requires |
  | --- | --- |
  | Act I "Training Arc" | Prologue "Loot. Level. Explore." |
  | Act II | Act I "Realm Unexplored" (visit the Nether) |
  | Act III "Foreign Enemies" | Act II "Travel Catalyst" (craft `undergarden:catalyst` from the Wither's Nether Star, then visit the Undergarden) |
  | Act IV "Wanderlust Ignition" | Act III "The Gatekeepers" (find a Blue Skies Gatekeeper house) |
  | Act V "Dragon Quest" | Act IV "The End" (enter the End; "Requires ALL dependencies") |
  | Epilogue "Primal Evils" | Act V "Untied Loose Ends" (find the Otherside Ancient Temple) |

  One late back-link: Act I "Bestiary VII" (Frostmaw, Lv 50) needs Act IV "The End" (`CTE2 1.1.3 instance: config/ftbquests/quests/chapters/`).
- **Flexible progression:** only the Prologue is `progression_mode: "linear"`. Elsewhere: "In CTE2, you can complete tasks in later quests even if you have yet to complete the dependencies. You won't be able to claim the rewards until you actually reach the quest" (`CTE2 1.1.3 instance: config/ftbquests/quests/chapters/act_0.snbt`, "Loot. Level. Explore.").
- **Quest-shape legend** taught in Act I ("Quest Shapes" in `CTE2 1.1.3 instance: config/ftbquests/quests/chapters/act_i.snbt`):
  - rounded square = Main Quest
  - circle = Side Quest
  - gear = Repeatable
  - diamond = Mini-Boss
  - octagon = Shop
  - hexagon = Achievement
  - paper icon = "Lost Pages" (click-to-read info)
- **Points from quests:** a "Rewards" chapter auto-completes when the referenced act quest is done. The trick is a `stat minecraft:walk_one_cm ≥ 1` task plus a dependency.
  - 25 talent points, 5 per act. Act V includes 2 for the Void Worm; Act I includes 2 for the Invoker.
  - 10 skill ("SPELLS") points: 2 per Twilight Forest boss trophy, 5 bosses.
  - 4 passive points for killing all Twilight Forest bosses.
  - 9 ascendancy points in the Ascendancy chapter: 3 "Novice" (Nether Keeper), 2 "Cruel" (Eel), 2 "Merciless" (Farseers) and 2 "Ascendant" (all four Epilogue elemental bosses).
  - Quest text: "These Talent Points are shared amongst your characters … If quests are reset via 'Main Campaign Quest Reset' … these quests will remain completed" (`CTE2 1.1.3 instance: config/ftbquests/quests/chapters/campaign_talent_rewards.snbt`, `CTE2 1.1.3 instance: config/ftbquests/quests/chapters/ascendancy.snbt`).
  - The changelog confirms the totals: 0.2.0 "It is possible to get 25 Talent Points this way"; 0.7.1 "You no longer receive Ascendancy Points from leveling."
  - The Act II lost page "Ascendancy" still says points "are capped at 7", which is stale.
- **Per-level point rates in CTE2's own balance override** (`CTE2 1.1.3 instance: config/openloader/data/cte_mns/data/mmorpg/mmorpg_game_balance/original_balance.json`, `player_points`):

  | Point type | Per level | Notes |
  | --- | --- | --- |
  | STATS | 1 | max 300, +50 bonus |
  | TALENTS | 1 (+1 base) | max 200, +25 bonus |
  | SPELLS | 1 | max 150, +10 bonus |
  | PASSIVES | 0.5 | max 75, +10 bonus |
  | ASCENDANCY | 0 | 9 bonus from quests |

  MAX_LEVEL is 100. **Conflict:** a third-party guide written for 2.1.4 says "Spell Points 1 per level … Passive Points 1 per level … Stat Points 2 per level" ([minecraft-guides class guide](https://www.minecraft-guides.com/guide/craft-to-exile-2-classes-guide/)). That matches the unmodified M&S jar default, so either 2.x changed the rates or the guide read the jar. Version-specific.
- **"Reset Main Campaign Quests":** a repeatable quest (3 confirmation checkmarks, a name tag and 5 diamond coins) that resets all campaign chapters so a new character can replay them on the same world. It needs Act III "The Gatekeepers"; its text says "unlocked in Act IV" (`CTE2 1.1.3 instance: config/ftbquests/quests/chapters/campaign_talent_rewards.snbt`).
- **Campaign Shop** (octagon "quests" that consume a coin; `CTE2 1.1.3 instance: config/ftbquests/quests/chapters/gem_shop.snbt`):
  - Augment and Support Gem Shops I to IV, each unlocked by an act milestone (Act I lost page "Acts"; the Wither; Captain Cornelia; both Act IV dungeon bosses).
  - "Random Unique Shop": 1 netherite coin buys a random Lv 60/80/100 unique, after the Twilight Lich.
  - A repeatable "Adventurer Handbook".
- **Repeatables chapter** (10 quests, unlocked by specific act quests) (`CTE2 1.1.3 instance: config/ftbquests/quests/chapters/repeatables.snbt`):
  - 20 Glow Skeletons → 150 XP
  - 24 Blazes + 12 Red Wyrms → 1,500
  - 30 Tortured Souls → 7,500
  - 30 Frost Spirits → 12,500
  - 30 Ender Wyrms → 15,500
  - 20 Farseers → 17,000
  - Frozen Key → frozen-chest loot
  - The 0.9.1 changelog says repeatables "have been moved out of the main lines and into a new Chapter to prevent players from getting 'stuck'".
- **Act achievements:** each act has a hexagon "Achievement" that pays a cosmetic hat and coins (Act I "Read All Act I Lost Pages!", Act II "Defeat All Act II Mini-Bosses!", Acts III to V "Complete All Act N Quests!"). "The Completionist" needs every chapter achievement and broadcasts a server-wide message (`CTE2 1.1.3 instance: config/ftbquests/quests/chapters/completionist.snbt`).
- **XP curve** (CTE2 keeps the M&S formula; `NORMAL_STAT_SCALING {base 1.0, per_level 0.2}` in the override above):
  - XP to go from level L to L+1 = (10·(1+0.2·L))^2.4 (Mine and Slash evaluates its level helper at L+1); base mob XP = 50 + (4·(1+0.2·(L−1)))^1.1 (`Mine and Slash source (1.20-Forge): src/main/java/com/robertx22/mine_and_slash/uncommon/utilityclasses/LevelUtils.java`, lines 54–64).
  - XP for one level: from Lv 1 389; from Lv 15 6,998; from Lv 50 79,313; from Lv 99 365,891.
  - Cumulative XP to reach: Lv 15 ≈ 37k, Lv 30 ≈ 262k, Lv 50 ≈ 1.24M, Lv 55 ≈ 1.68M, Lv 75 ≈ 4.49M, Lv 100 ≈ 11.38M (computed).
  - Real kill XP is multiplied by mob rarity, map tier and CTE2 multipliers, so kill counts from the base formula are only indicative.

### Inferences
- The critical path is short (about 36 main quests across Acts I to V, plus Prologue pages) while total volume is large (about 620). Hobbies are opt-in.
- Levels 1–55 (up to the map unlock at 55) are only about 15% of the XP to 100. About 85% of the grind (55→100) comes after Maps unlock; Act V (Warden at level 60) and the Epilogue still run past 55, so this is the share after the map unlock, not strictly after the campaign. That matches the dev's own "drop off in engagement at 55" diagnosis (section 6).
- The flat campaign quest XP (about 1.36M) roughly equals the base XP to reach level 52. Quests were deliberately made a main XP source during the campaign (0.6.0 and 0.9.0 fixes, section 6).

### Gaps
- Real average kill XP after multipliers cannot be derived from the files; hours-to-max must come from player reports (section 7).

## 2. The journey: what each act asks, where it sends you, gates and pacing walls

### Takeaway
CTE2 is a "dimension ladder":

1. Overworld (Lv 1–15)
2. Nether (10–30)
3. Undergarden plus the Overworld frozen-ocean Ice Maze (25–40)
4. Blue Skies' Everbright and Everdawn (35–55)
5. End (50–65), then Otherside (60–75)
6. After the campaign: Maps (55+), then the Epilogue's Primal Titans and the Twilight Forest (90)

Each act follows the same template: kill a few local mob packs, find the act's signature structure, kill 1–3 bosses (several are summoned via altars or Gateways), then use a boss drop to open the next portal. The walls are the boss gates (Wither, Blue Skies dungeon bosses, Warden) and the post-55 map grind.

### Cited Findings
- **Per-dimension level config** (`CTE2 1.1.3 instance: config/openloader/data/cte_mns/data/mmorpg/mmorpg_dimension/`). Every dimension has `scale_to_nearest_player: true`. The "enraged" column is the `secondary_lvl_range` used for revisits after Lv 50.

  | Dimension | Levels | Mob strength | Enraged |
  | --- | --- | --- | --- |
  | Overworld | 1–15 | 1.0 | 50–70 |
  | Nether | 10–30 | 1.1 | 55–75 |
  | Undergarden | 25–40 | 1.2 | 60–80 |
  | Everbright / Everdawn | 35–55 | 1.3 | 65–85 |
  | End | 50–65 | 1.4 | 70–90 |
  | Otherside | 60–75 | 1.5 | 75–95 |
  | Twilight Forest | 90–100 | 2.0 | none |

  Twilight Forest mobs also get "+400% more health", +25% armor and dodge, and +25 elemental and chaos resist.
- **Guide book and quest copies of the same table:** the Patchouli "Dimensions" entries list level range, enraged range, quests covered, gateways, bosses and portal recipe for each dimension (`CTE2 1.1.3 instance: patchouli_books/CTE2/en_us/entries/dimensions/`). The Act III lost page "Travelling Backwards" explains enraging ("At certain breakpoint levels, past Dimensions will 'enrage'") (`CTE2 1.1.3 instance: config/ftbquests/quests/chapters/act_iii.snbt`). Enraging was added in 0.9.1 as "Something players have been asking for" ([GH/ARCHIVED CHANGELOG.MD](https://github.com/mahjerion/Craft-to-Exile-2/blob/main/ARCHIVED%20CHANGELOG.MD)).
- **Scaling rules:**
  - "Although mobs scale to player level, they cap out in each dimension, making it possible to 'overlevel' for a dimension if you're struggling with it" (Act I lost page "Acts").
  - "the higher level a mob is to you it takes less damage and deals more … This bonus starts at a 3 level differential" (Act II "Dark Ritual").
  - Config: `LEVEL_DISTANCE_PENALTY_LEEWAY = 3`, `lvl_distance_loot_penalty_per_level = 0.33`, a skull icon on mobs 10+ levels above you, `SCALE_MOB_LEVEL_TO_NEAREST_PLAYER = true` (`CTE2 1.1.3 instance: defaultconfigs/mine_and_slash-server.toml`).
- **Prologue (Lv 1, 25 quests, linear):** 23 click-to-read "pages" plus 2 kills of mobs spawned from reward spawn eggs ("Consider summoning in a cave"). Details in section 3.
- **Act I: Overworld, Lv 1–15, 52 quests** (`CTE2 1.1.3 instance: config/ftbquests/quests/chapters/act_i.snbt`). The story is an Illager/Witch "Chemical X" mutation plot.
  - Main chain:
    1. "Training Arc": 5 zombies + 5 skeletons → Zombie Gateway pearl
    2. "A Brewsome Problem": 3 witches → Chemical X splash potions
    3. "An Experiment Gone Wrong": a Mutant Skeleton, e.g. made by throwing Chemical X on a skeleton
    4. "Investigation": 12 Pillagers + 6 Vindicators
    5. "The Mastermind": the Illager Invoker, min Lv 15, in a Woodland Mansion or a "forced Gateway of the Raiders"; 2,500 XP + 2 talent points
    6. "Realm Unexplored": enter the Nether; 1,000 XP
  - Side arcs:
    - Illager Arc (fortress, tower and hut minibosses).
    - The Underground (Glow Skeletons; Hermit Abysswalker via "Gateway of Overworldian Nights"; goblin and kobold traders).
    - A Born-in-Chaos "Bestiary I–VII". VII is two Frostmaws at Lv 50 and VIb is Lord Pumpkinhead at Lv 60, as late back-links.
    - Utility "Extras": Ultimine, sleeping bag, compasses, waystone, transmog, paraglider, Chocopedia, auto-repair kit, Obelisk wave instances, bounty board.
- **Act II: Nether, Lv 10–30, 30 quests** (`CTE2 1.1.3 instance: config/ftbquests/quests/chapters/act_ii.snbt`).
  - Main chain:
    1. "Otherworldly Beings": 12 piglins + 4 hoglins
    2. "Nether Fortress": find a fortress
    3. "Skull Collectors": 12 wither skeletons
    4. "Dark Ritual": the Wither, min Lv 30, "requires you to have a projectile weapon or spell … first phase"; 7,500 XP + 1 talent
    5. "Preparation": a Netherite template from a Bastion
    6. "Travel Catalyst": Nether Star → Undergarden Catalyst
  - Side arcs: lava squids → Mother Lava Squid; Citadel → Wildfire; Crimson-forest mosquitoes → Warped Mosco.
  - "Big Baddies": the Awful Ghast and the Nether Keeper, each summoned with a Nether Star on a structure altar. The quest warns these structures "cannot be found conventionally with the Explorer's Compass".
- **Act III: Undergarden + Overworld Deep Frozen Ocean (Aquamirae), Lv 25–40, 31 quests** (`CTE2 1.1.3 instance: config/ftbquests/quests/chapters/act_iii.snbt`).
  - Main chain:
    1. "Foreign Enemies": 6 Rotlings
    2. "Rotting Creatures": 12 Rotbeasts + 12 Rotwalkers
    3. "Civilization from the Past": 12 Forgotten in the Catacombs
    4. "Hidden Secrets": the Forgotten Guardian, or "Gateway of the Labyrinth"
    5. "The Hidden Depths": find an Ice Maze ("Mobs from Aquamirae spawn at a minimum of level 35!")
    6. "Captain Cornelia's Locker": a horn-summoned ghost, min Lv 40
    7. "The Gatekeepers": find a Gatekeeper house
  - Side arcs: an Undersea arc (Eel = Cruel Ascendancy; Mother of the Ice Maze, which "has a chance of spawning with Anglerfish (5.0% chance … when an Anglerfish dies)"); Mowzie's bosses via "Gateway of the Depths"; new ores; locator items.
- **Act IV: Everbright + Everdawn (Blue Skies), Lv 35–55, 31 quests** (`CTE2 1.1.3 instance: config/ftbquests/quests/chapters/act_iv.snbt`).
  - Main chain:
    1. Buy the Zeal Lighter from the Gatekeeper and get the Blue Journal.
    2. Visit both dimensions (Lv 40 loot chests).
    3. In each Blinding Dungeon kill one boss: the Summoner and the Alchemist, both min Lv 45.
    4. Clear two 4-floor dungeons, each with "one of 4 keys" per floor: the Nature Dungeon (Starlit Crusher) and the Poison Dungeon (Arachnarch), both min Lv 50. Quest text: "Should you find a … Dungeon that's already been cleared, you can go back to the Gatekeeper and purchase the required keys."
    5. "The End" needs ALL of these, then entering the End.
  - Side arc: an 11-quest "Ever Exploration" of kill sets, crafting and minibosses.
- **Act V: End (Lv 50–65) + Otherside (Lv 60–75), 27 quests** (`CTE2 1.1.3 instance: config/ftbquests/quests/chapters/act_v.snbt`).
  - Main chain:
    1. Ender Dragon, min Lv 50
    2. Respawn the dragon
    3. Mysterious Worm, then the Void Worm, min Lv 55, 2 talents
    4. Ancient City, then the Warden, min Lv 60
    5. Otherside
    6. "Untied Loose Ends": "Congratulations on completing the main Campaign! … try to overcome the greatest challenges in Maps!"
  - "Maps": "These can only be used starting at level 55". Rarity maps to tier (Common T0–10 … Mythic T81–100). Config: `min_level_map_drops = 55`, `MAP_PERCENT_COMPLETE_NEEDED_FOR_BOSS_ARENA = 30.0` (`CTE2 1.1.3 instance: defaultconfigs/mine_and_slash-server.toml`).
- **Epilogue: Lv 90, 43 quests, item rewards only** (`CTE2 1.1.3 instance: config/ftbquests/quests/chapters/epilogue.snbt`).
  - Four "Primal Titans" from Bosses of Mass Destruction, all at "minimum level of 90 and at Mythic Rarity".
  - Then the Twilight Forest. Its portal must be built in the Otherside, and "Mobs from Twilight Forest spawn at a minimum of level 90!".
  - The chapter follows TF's own boss order: Naga → Lich → Minoshroom → Hydra → Knight Phantoms → Ur-Ghast → Alpha Yeti → Snow Queen. Bosses have respawn monuments, and "Explorer's Compass … doesn't work".
  - The Epilogue was added in 0.7.0; the TF section in 0.8.0 "Note that this is a level 90 zone" ([GH/ARCHIVED CHANGELOG.MD](https://github.com/mahjerion/Craft-to-Exile-2/blob/main/ARCHIVED%20CHANGELOG.MD)).
- **Elemental identity per dimension** ("Mob Affinities" lost page in `CTE2 1.1.3 instance: config/ftbquests/quests/chapters/act_ii.snbt`): Overworld none, Nether Fire, Undergarden Chaos, Aquamirae Cold, Everbright Cold+Lightning, Everdawn Fire+Chaos, End Fire+Lightning+Chaos, Otherside Chaos.
- **Resist penalty by level** ("Resistances" lost page in `CTE2 1.1.3 instance: config/ftbquests/quests/chapters/act_iii.snbt`): Lv 1–24 +25%, 25–49 0%, 50–74 −25%, 75+ −50% elemental and chaos resistance.
- **Boss-finding aids:**
  - Gateways to Eternity pearls let players summon quest bosses and mobs instead of hunting rare spawns.
  - Since 0.8.0: "Mobs in dimensions up to and including Act V can now spawn Gateways on death."
  - Changelog fixes for hard-to-complete quests: "Split Defenders of the Nether into 2 quests … Added images to show what the structure looks like" (1.0.6); "Changed objectives for Awful Ghast and Nether Keeper quest so it's more reliable to complete" (1.0.0); "You no longer need to kill the Maze Mother to continue the quest line" (0.2.1) ([GH/ARCHIVED CHANGELOG.MD](https://github.com/mahjerion/Craft-to-Exile-2/blob/main/ARCHIVED%20CHANGELOG.MD)).

### Inferences
- The pacing walls visible in the data:
  1. The Wither: min Lv 30 and needs ranged damage.
  2. Act IV's double requirement: two 4-floor dungeons plus both Blinding Dungeon bosses.
  3. The Warden at Lv 60.
  4. The biggest one: a Lv 75→90 gap before the Epilogue, scaffolded only by Maps.
- Quest steps that depend on unfindable structures or spawn-chance bosses were the recurring friction point. The fixes (gateway summons, key shops, structure images) all work by making targets **summonable or purchasable**.

### Gaps
- No playtime-per-act data in the files; see section 7 for hour estimates.

## 3. Onboarding and teaching: Prologue, lost pages, guide book, menu wheel, tooltips

### Takeaway
CTE2 teaches almost entirely through the quest book:
- a 25-page linear Prologue (23 click-to-read pages that pay XP and items),
- 22 "Lost Pages" spread through the acts, each introducing one advanced concept when it becomes relevant,
- an optional Patchouli "Adventurer Handbook" that repeats the same text.

About 20 systems are introduced before the first real fight. That front-loading is the root of the "overwhelming" or "confusing" perception, and the dev has kept patching it.

### Cited Findings
- **Prologue opening:** "a highly customized modpack that turns Minecraft into an action RPG … 'Prologue' is meant to give you an overview to certain systems … that may be different from what you're used to" (`CTE2 1.1.3 instance: config/ftbquests/quests/chapters/act_0.snbt`).
- **Prologue page topics** (same file):
  - Money and the Wallet.
  - Key Binds: the hub is on "H". If "R" opens the wheel, the custom binds failed and the player must "Reset Keys".
  - Character Stats (400 XP reward).
  - Party Play: three separate team systems (M&S teams for XP and heal sharing, FTB Teams for quest sharing, Lightman's Teams for money).
  - Land Claiming (FLAN).
  - Talent Tree: "Each level grants you 1 talent point".
  - "The Rest of the GUI": up to 3 saved characters that share quest-earned points, Salvaging, Classes ("you can specialize in two different classes"), Jewels.
  - Your First Weapon: "3 'main' fighting styles".
  - Gear Stat Souls: "Gear without a soul will have no effect".
  - Combat: "press 'Alt' to dodge roll".
  - Skill, Support and Augment Gems: first support socket at skill level 4, max 5 sockets at skill level 16, plus 1 slot per 10 player levels.
  - Spell Bar.
  - Suit Up: "you don't lose equipment on death, but starting at level 50, you will lose a good chunk of your experience".
  - Blocking.
  - Gems, Runes and Runewords.
  - Anti Mob Farm.
  - Waystones (8 Warp Scrolls).
  - Heart Containers: +2 HP each, up to 200 HP.
  - Managing Your Inventory: "You will very soon realize that Craft to Exile 2 has a LOT of loot … One of the most frustrating things is keeping your inventory organized".
  - VR, the Patchouli book, and the appendix page.
- **Lost Pages** (deferred teaching). The Act I achievement requires reading all of them. 21 of the 22 are mirrored in the book (`CTE2 1.1.3 instance: patchouli_books/CTE2/en_us/entries/lost_pages/`).
  - Act I (9): Acts, Quest Shapes, Gear Improvement (7 droppable rarities), Favor, Dimension Runes, Other Weapon Types (11 types, two-handers, dual-wield at 25% off-hand stats), Summoning Classes, Spice of Life, Monster Stats.
  - Act II (5): Gear Soul Extractors, More On Currencies (gear "Potential"), Mob Affinities, A Moment's Respite (rested XP), Ascendancy.
  - Act III (4): Resistances, Stat/Enchant Compatibility, Infusion, Travelling Backwards.
  - Act IV (2): Mounts, Codex.
  - Act V (1): More Map Content (Obelisk, Prophecy, Harvest, Relics, Uber bosses).
  - Epilogue (1).
- **Patchouli "Adventurer Handbook"** (v1.0.7), 5 categories and 51 entries: Lost Pages 21, Dimensions 9 (each with an in-world "visualize portal" button), Disabled Crafts 7, Professions 10, Storage 4 (`CTE2 1.1.3 instance: patchouli_books/CTE2/book.json`).
  - Landing text: "More vital information is contained within the questbook. Please read."
  - Added in 1.1.0 (2025-10-22): "A new Patchoulie book has been added which consolidates the information found in the quests!" ([GH/ARCHIVED CHANGELOG.MD](https://github.com/mahjerion/Craft-to-Exile-2/blob/main/ARCHIVED%20CHANGELOG.MD)).
- **MineMenu radial wheel ("H")** (`CTE2 1.1.3 instance: minemenu/menu.json`): Mine and Slash Hub, Quests, FTB Teams, Wallet, Food Book, Professions Shop, Minimap Settings, Waypoints, Jade Settings, Inventory HUD Settings.
  - Other hotkeys the quests teach: Alt (dodge), Alt+B (Master Bag), Y (quick potion), \` (Ultimine), = (monster stats), Delete (unsummon), Shift+] (DPS overlay).
- **First join:** the message "You wake up in an unfamiliar place." (`CTE2 1.1.3 instance: config/firstjoinmessage.json5`), and the spawn is forced into a village (`CTE2 1.1.3 instance: config/villagespawnpoint.json5`).
- **Author's own onboarding advice** in the pack description ([ModpackIndex mirror](https://www.modpackindex.com/modpack/72640/craft-to-exile-2-20-atlas-update)): "sticking to the Overworld until level 15 to familiarize yourself with Mine and Slash concepts", "Don't worry too much about messing up your skills and talents", "If you're worried that you'll feel lost, don't fret".
- **The M&S author's newbie docs:** "Mine and Slash is a big and complex mod, but if you know the basics, you can't screw up too badly. Progression is simple, but optimizing your build and knowing how to efficiently grind gear is the complex part" ([Starting Out](https://moddedmc.wiki/en/project/mine-and-slash/latest/docs/newbie_guide/starting_out)).
  - "Mistakes to Avoid" lists: killing mobs ±10 levels away, no base, not salvaging, unspent points ("Reset Potions are cheap"), not replacing gear, ignoring professions ([Mistakes to Avoid](https://moddedmc.wiki/en/project/mine-and-slash/latest/docs/newbie_guide/mistakes_to_avoid)).
- **The CTE2 GitHub wiki also admits the friction:** "The talent tree may appear intimidating at first". Parts of it are stale; for example, Favor is described with a 250 cap and −125 on death, versus 2,500 and −500 in the 1.1.3 config ([wiki Mine-&-Slash page](https://github.com/mahjerion/Craft-to-Exile-2/wiki/Mine-&-Slash)).
- **Teaching fixes in the changelog** ([GH/ARCHIVED CHANGELOG.MD](https://github.com/mahjerion/Craft-to-Exile-2/blob/main/ARCHIVED%20CHANGELOG.MD), `CTE2 GitHub repo: CHANGELOG.md`):
  - "Weapon type requirement tooltips have been streamlined a bit to make more sense to the newer player" (0.7.0).
  - "Added a few more Lost Page quests to help with newer players" (0.6.0).
  - "Improved introduction to Runeword in Prologue" (1.0.3).
  - "Removed ALL ALLIES option … it causes a ton of confusion" (1.0.6).
  - Tooltip Scaler added, "tooltips should no longer be cut off!" (1.1.2).
  - "Repair kit quest now appears earlier and is forced as part of the main campaign" (2.0.0).
  - "You now receive a free Warp Stone in the prologue, and are introduced to Waystones and Harvest/Obelisks a bit earlier" (2.0.2).
  - "hold ALT on some stats in tooltips to see more details" (2.1.0).
  - The 2.0.0 dev note lists "confusion from certain skills and stats" among the major pain points.
- **A 2.0.4 player guide** opens: "Since these aspects are confusing, this article will explain the actual operational sequence". It also notes "813 total profession recipes in CTE2" ([R's Journal, note.com](https://note.com/rhe39/n/n9457ea0683f9?hl=en)).

### Inferences
- The structure is sound: a linear primer, then lost pages, then a reference book. The flaw is load. The player reads about 22 pages and receives reset potions before killing a real mob. The Prologue itself has to apologise for loot overload and broken key binds.
- XP bribes (200–700 XP per page) and the read-all-pages hat are what make players actually read.

### Gaps
- No contextual in-world tutorials (NPC dialogue, triggers) were found, beyond the Tips mod, whose `tips.json` I did not inspect.

## 4. Quality of life and travel

### Takeaway
CTE2 ships a dense QoL layer from minute one: iron backpack, a loot "Master Bag", auto-salvage, waystones plus warp scrolls, a return-scroll emergency item, a paraglider with no stamina, sleeping bags, Ultimine, quick-stack, trash slot, minimap and keep-inventory. Real flight is rationed: a flying Chocobo breed (Nether), the elytra after the Ender Dragon (Lv 50+), and a Botania tiara at the end of that chapter. Travel is waystone-centric: normal use costs no XP (both XP cost settings are 0), only a few coins (1 per 100 m, at most 10); cross-dimension travel works only via global waystones and costs 3 levels.

### Cited Findings
- **Free starting gear from Prologue quests:** Iron Backpack, 12 bread, Gold Wallet, 8 Warp Scrolls, a Heart Container, and the Master Bag ("wear the bag in a Curio slot and access it via 'Alt + B'") (`CTE2 1.1.3 instance: config/ftbquests/quests/chapters/act_0.snbt`).
- **Waystones** (`CTE2 1.1.3 instance: config/waystones-common.toml`): `blocksPerXpLevel = 1000` (no effect, because `minimumBaseXpCost` and `maximumBaseXpCost` are 0), `dimensionalWarp = "GLOBAL_ONLY"` (cost 3), `warpStoneCooldown = 360` s, `generatedWaystonesUnbreakable = true`, `transportLeashed = true`, worldgen `frequency = 20`.
  - A coin cost on top: `moneyCostPerHundredMeter = 1`, `maximumCost = 10` (`CTE2 1.1.3 instance: config/waystoneandlightman-common.toml`).
  - 2.0.2: "Warp Stone cooldown is now 2 minutes instead of 6" ([GH/ARCHIVED CHANGELOG.MD](https://github.com/mahjerion/Craft-to-Exile-2/blob/main/ARCHIVED%20CHANGELOG.MD)).
- **Paraglider:** `paraglidingConsumesStamina = false`, `runningAndSwimmingConsumesStamina = false`, `maxHeartContainers = 100` (`CTE2 1.1.3 instance: defaultconfigs/paraglider-server.toml`). 0.5.2: "Completely removed Stamina from the game".
- **Mounts and flight:**
  - Act I Chocobo quest (+1 talent): "All this running around has to be tiring... Maybe it's time for a mount … breed special Chocobos that have the ability to fly!"
  - Act II "Aerial Exploration" covers the Nether flying breed; Act IV adds Blue Skies mounts; Act V gives the elytra (quest files).
  - 2.0.0 made elytras craftable through the Campaign Shop; 2.1.0 made them toggleable.
  - Botania's Flight Tiara is listed under "Disabled Crafts" and must be bought at the end of the Botania chapter (1.0.0) (`CTE2 1.1.3 instance: patchouli_books/CTE2/en_us/entries/disabled/botania.json`).
  - Small Ships for sea travel (1.1.0); Telepass teleports to a teammate (diamond telepass = 15 uses) (`CTE2 1.1.3 instance: config/telepass-common.toml`).
- **Mining and inventory:**
  - FTB Ultimine: `max_blocks: 32` (`CTE2 1.1.3 instance: defaultconfigs/ftbultimine-server.snbt`).
  - Quickstack radius 4 (`CTE2 1.1.3 instance: defaultconfigs/quickstack-server.toml`).
  - TrashSlot, Inventory Sorter, Mouse Tweaks, Simple Magnets (the backpack magnet upgrades are disabled per `CTE2 1.1.3 instance: patchouli_books/CTE2/en_us/entries/disabled/various.json`).
  - Refined Storage and Sophisticated Storage; Lootr per-player chest loot (`CTE2 1.1.3 instance: modlist.html`).
- **Auto Repair Kits:** repair equipped gear every 10 s and are recharged with M&S stones; the leather kit has 200 charges, the netherite kit 125,000 (`CTE2 1.1.3 instance: defaultconfigs/auto_repair_kit-server.toml`).
- **Sleeping:** Comforts sleeping bags at night with no break chance (`CTE2 1.1.3 instance: config/comforts-server.toml`), and `sleep_percent = 25` (`CTE2 1.1.3 instance: defaultconfigs/easier_sleeping-server.toml`).
- **Loot handling timeline** (`CTE2 GitHub repo: `):
  - "Added a Quick Loot button in chests that allows you to move items to the Master Bag" (0.9.3b).
  - "Greatly improved the auto-salvaging feature … can salvage maps!" (1.0.0).
  - "Increased master bag size and stack counts" (1.1.2).
  - "long-requested feature: improved auto-salvaging (loot filtering)! … filter by gear base type" (2.1.0).
  - A unique "Sticker Book" salvage "to reduce item-hoarders from maxing out their storage's NBT data which caused a hard chunk lock" (2.1.0).
- **Map return:** a KubeJS script remembers where you entered a Map, Harvest or Obelisk and returns you there (`CTE2 1.1.3 instance: kubejs/server_scripts/return_script.js`).

### Inferences
- Convenience is front-loaded (scrolls, no stamina, sleeping bag); true flight comes late (Lv 50+). Long-distance travel is waystone hops that get pricier with distance.
- Loot overload was a recurring, long-requested fix area across 0.7 to 2.1.

### Gaps
- The only waystone XP cost is the 3-level dimensional warp; it is presumably vanilla XP, since Mine and Slash XP is a separate bar (not verified).

## 5. Difficulty, mob scaling and death handling

### Takeaway
Death is soft. Keep-inventory is on and gear is never lost. Only from level 50 does death cost 5% XP (10% in older versions) plus 500 Favor, the loot and XP bonus meter. Anti-farm rules and level-gap penalties push players to travel instead of camping.

### Cited Findings
- **Gamerules:** `keep_inventory = true`, `natural_regeneration = false`, `disable_raids = true`, `mob_griefing = true` (`CTE2 1.1.3 instance: config/globalgamerules-common.toml`).
- **Death costs:** `DEATH_PENALTY_START_LEVEL = 50`, `death_exp_penalty = 0.05`, `favor_death_loss = 500.0`, `MAX_POSSIBLE_FAVOR = 2500` (`CTE2 1.1.3 instance: defaultconfigs/mine_and_slash-server.toml`).
  - History: 5% → 10% (0.6.0) → "Death EXP penalty now only starts at level 50" (0.9.0) → "reduced from 10% to 5%" (1.1.0) ([GH/ARCHIVED CHANGELOG.MD](https://github.com/mahjerion/Craft-to-Exile-2/blob/main/ARCHIVED%20CHANGELOG.MD)).
- **Favor** "rewards players for living". At the 2,500 cap it gives "+25% increase to loot and experience"; dying costs 500. Rarity gates: Epic from Lv 10, Legendary 25, Mythic 50 (Act I lost page "Favor").
- **Anti Mob Farm:** 15 free kills per chunk, then −2% per kill, recovering 5% per minute, +5 free kills per looted chest. Exempt: the M&S dungeon dimension, Everbright, Everdawn and bosses (`CTE2 1.1.3 instance: config/anti_mob_farm-common.toml`). The Prologue's reason: "to encourage travel and exploration instead of the usage of automated experience methods". The CTE2 wiki explains how to delete the anti-farm mods ([wiki Modpack-Tweaks](https://github.com/mahjerion/Craft-to-Exile-2/wiki/Modpack-Tweaks)).
- **Death-count quests** with a joke tone: "Welcome to Craft to Exile 2" (1 death), "This is Craft to Exile 2" (25), "…Prepare to Mine Edition" (50), "Hollowed" (100) (`CTE2 1.1.3 instance: config/ftbquests/quests/chapters/other_hobbies.snbt`).
- **Difficulty tuning history:**
  - "Mob damage and health scaling per dimension has increased … from 10% to 100%" (0.7.0).
  - "fewer 'gotcha' moments" (0.8.0).
  - "cap the amount of damage your gear can take per hit, which should alleviate early game armors being blown up in one hit" (1.0.4).
  - A baseline map resist requirement "to help with players novice to ARPGs who are consistently getting one-shot" (0.6.0).
  - Early-caster buffs in 0.1.0 and 0.2.0.
  - InControl blocks the hardest Born-in-Chaos mobs in the Overworld (`CTE2 1.1.3 instance: config/incontrol/spawn.json`).
- **Party and alts:**
  - 1.1.0: XP is no longer split up to party size 4, and drops get +25% per member.
  - Alts: 3 characters (7 from 2.1.0) and "10% experience boost for each character you have that's above your current character" (0.4.0).

### Inferences
- Death is cheap before Lv 50. Its cost lands exactly where the grind is longest (maps).

### Gaps
- No hardcore option was found in the configs.

## 6. Iteration history: what the developer kept fixing (changelog evidence)

### Takeaway
Over about 3 years (0.1.0 in Oct 2023 to 2.1.4 in Sep 2026) the dev:
- repeatedly re-tuned XP,
- raised late-act quest XP because players arrived under-levelled,
- moved repeatables out of the main line,
- cut kill-quest counts by 25–30%,
- softened death,
- removed heavy mods for lag.

In 2.0.0 he named the four biggest pain points himself: the engagement drop at Lv 55 (maps), mapping UX and lag, weak early skill-gem scaling, and confusing skills and stats.

### Cited Findings
All from [GH/ARCHIVED CHANGELOG.MD](https://github.com/mahjerion/Craft-to-Exile-2/blob/main/ARCHIVED%20CHANGELOG.MD) and `CTE2 GitHub repo: CHANGELOG.md`.
- **2.0.0 dev note (2026-08-02):** "I wanted to address some of the major pain points experienced by players: the drop off in engagement at 55 (maps), the mapping experience in general (teleporter block, lag), difficulty with skill gem scaling in the early game, and confusion from certain skills and stats."
  - Same patch: "The progression through Acts IV and V has been refined to optimize player onboarding for the mapping phase"; "Maps start dropping at 50 now"; an Atlas passive tree that starts with 4 map types.
- **XP see-saw:**
  - 0.4.0: kill XP +150%.
  - 0.5.1: kill XP +100%, but "Lowered Campaign Quest experience rewards by 50%".
  - 0.6.0: "Increased EXP gain in Acts III, IV, and V. Players were reaching Act V at too low of a level" and "Doubled EXP rewards from Campaign Bosses".
  - 0.7.3: mob XP reduced.
  - 0.8.0: Act IV quest XP raised.
  - 0.9.0: "Experience gain from quests starting in Act IV onwards has increased drastically (about 30-50%)", plus new profession quests to "help a bit with the experience gain from level 45-55".
  - 1.0.0: "Kill EXP has been doubled". 1.0.1: −25%. 1.0.4: +33%, "back to 1.0.0 rates".
  - 1.1.0: "You can now toggle your experience gain".
- **Quest friction fixes:**
  - "Reduced the number of required kills for kill quests across the board by about 25-30%" (0.9.5).
  - Repeatables moved out "to prevent players from getting 'stuck'" (0.9.1).
  - "Quests now only show when startable" (0.2.1).
  - Epilogue boss quests became trophy "Consume Item" tasks "to make the quests a little more consistent" (0.9.3).
- **Level-gap XP:** 0.2.1b raised the penalty to "50% less" at a 5-level difference. 0.7.3 settled on 3 levels of leeway and a 6-level penalty range.
- **Lag-driven removals:** Create (replaced by Botania, 0.8.0), Incendium ("laggy and didn't offer much"), Stellarity ("a bunch of bloat and … a large source of lag"), Sound Physics, Structure Credits, Custom NPCs. Repeated "Talent Tree lag" and "map exit lag" fixes, up to 2.1.0's "Greatly improved the exit-map lag".
- **Class and skill growth:** the class system arrived in 0.1.0; Rogue in 0.5.0; Brawler, Crusader and Chronomancer in one patch (0.9.0); Cryolancer and Sanguimancer in 2.0.0. That makes 12 classes in 2.1.0. 2.1.0 also says: "Warlock is going through a bit of an identity shift due to the overlap between itself and the new Sanguimancer".
- **Skill counts in 1.1.3** (parsed from `CTE2 1.1.3 instance: config/openloader/data/cte_mns/data/mmorpg/mmorpg_spell_school/`):
  - 10 class trees, with rows unlocking at levels 1, 5, 10, 15, 20, 25 and 30.
  - 12–21 active skills per class, 170 in total, plus 8 passive nodes per class.
  - A player can invest in 2 classes, so roughly 25–40 actives are on offer.
  - Also defined: 235 spell entries (including weapon, unique and proc skills), 83 support gems, 28 auras, 579 talent-tree perks, 220 uniques, 57 runewords, 810 profession recipes (`CTE2 1.1.3 instance: config/openloader/data/cte_mns/data/mmorpg/`).
  - 2.1.4 guide: "You can only have points in 2 Classes at the same time … 14 [Ascendancies] in total" ([minecraft-guides class guide](https://www.minecraft-guides.com/guide/craft-to-exile-2-classes-guide/)).

### Inferences
- The XP see-saw shows feedback-driven tuning without a fixed pacing target. The fix that stuck: quest XP drives the campaign and maps drive 55+.
- Skill counts are large and growing; no change has reduced them.

### Gaps
- The changelog rarely quotes players. GitHub issues were not reachable from this session.

## 7. Reception: what players and reviewers love and hate

### Takeaway
CTE2 is popular (about 2.8M CurseForge downloads, a 2.0 update covered by PCGamesN) and is praised for its build depth, satisfying ARPG progression and structured Act questing. The recurring complaints are:
- luck-based gear grind ("1000 gear for single useful piece"),
- a dead feeling when stuck,
- the post-campaign level gap ("why is the epilogue 30 levels further"),
- complexity and confusion,
- anti-farm and lack-of-automation friction for builders,
- clutter from spawn mechanics,
- lag.

The owner's own verdict matches: "didn't finish: too much grind … isn't balanced and is too hard for me". Direct evidence for "spell bloat" is mostly indirect: large skill counts, support-gem uselessness, class overlap.

### Cited Findings
- **Popularity:** "Total downloads: 2,800,899", "Last updated: Sep 10, 2026" ([CurseForge page](https://www.curseforge.com/minecraft/modpacks/craft-to-exile-2)). ModpackIndex: 2,787,556 downloads, 407 mods, first released November 14, 2023 ([ModpackIndex](https://www.modpackindex.com/modpack/72640/craft-to-exile-2-20-atlas-update)). CurseForge showed no comment section when fetched.
- **ModDex rating:** 3.3 average from 4 reviews and 6 ratings. The ratings are 2.0★ ×2, 2.5★, 3.5★, 4.5★ and 5.0★ ([ModDex](https://moddex.gg/modpack/craft-to-exile-2)).
- **ModDex "aroyund", 3.5★, "Good modpack, not great"** (20 hours, completed, version 1.1.2; posted Sep 18, 2026) ([ModDex](https://moddex.gg/modpack/craft-to-exile-2)).
  - Praise: "the main content of getting gear (early game) was fun going through structures and the different build types you could do was interesting … the talent tree was super sick".
  - Complaints: "after a while you just power straight through all the different dimensions and getting better gear felt like a chore at times since it mostly boils down to luck"; "why is the epilogue 30 levels further then the previous chapter? it doesn't really make sense to me"; "i also wish there was more side content like the first cte with the maps".
- **ModDex "aongreyfox", 2.0★, "Previously quite fun, what happen now?"** (20 hours; June 6, 2025) ([review 566](https://moddex.gg/modpack/craft-to-exile-2/reviews/566)).
  - Praise: "Crafting your skill tree in PoE style is something I really like."
  - Complaints: "Many support gem are not even useful"; "The downside is the tedious grind 1000 gear for single useful piece … It's fun when you can keep progressing, even by a little. But it drop to dead once you stuck for hour and hour. Author also put a lot of effort to penalty mob grind and automate".
  - On gateways spawning on mob death: "clutter mob / item management … killing a zombie or two result in chaos. While I'm being penalty for killing them around my base".
  - Also disliked the Create → Botania swap and forced MCA villagers. "Just like PoE, you'll love it or hate it."
- **ModDex "Kafuru", "Used to like it, but now it sucks"** (version 0.8.4b; Jan 19, 2025) ([review 521](https://moddex.gg/modpack/craft-to-exile-2/reviews/521)).
  - "it's actually balanced pretty nicely".
  - Built a vanilla iron farm "only to find out that there's an anti mob farm mod … removing all mob drops is kinda dumb … if you like automating stuff don't play it, its purely just a boss killing rpg pack".
  - Also disliked the Create removal, Botania item bans and MCA villagers.
- **ModDex "Alter1Al", 5.0★** (500 hours; May 27, 2026) ([review 1244](https://moddex.gg/modpack/craft-to-exile-2/reviews/1244)): "I'm a huge fan of PoE and Minecraft, so this modpack was created specifically for people like me. If you just want to kill mobs and create builds, this modpack is for you. The rest of the content is pretty mediocre".
- **LogicServers blog** (Nov 19, 2025, updated Aug 5, 2026) ([review](https://logicservers.com/blog/craft-to-exile-2-minecraft-modpack-review)). This is a server-hosting company, so promotional bias is likely.
  - "strong sense of progression and satisfying build combinations … the primary [skill tree] is ridiculously large!"
  - "The classes system, while difficult to learn, is very satisfying to master".
  - "Overwhelmed by what to do in modpacks? Not to worry, the quest book has a very straightforward Act system … We recommend clearing out an act entirely before moving to the next one".
- **PCGamesN, Ken Allsop, Aug 9, 2026** (news coverage of 2.0) ([article](https://www.pcgamesn.com/minecraft/arpg-mod-craft-to-exile-2-update)): "one of the most deep and immersive options … an extremely deep and rewarding progression system … once you reach Act Five and level 50, Craft to Exile 2 introduces its mapping system … It really does have the substantial feel of a PoE patch."
- **Third-party guides** take the complexity for granted. The 2.0.4 crafting guide says "Since these aspects are confusing…" ([note.com](https://note.com/rhe39/n/n9457ea0683f9?hl=en)). The 2.1.4 class guide warns players to decide their 2 classes early "before you've spent points that need Reset Potions to get back" and to check weapon requirements ("That weapon type Can't cast this spell") ([minecraft-guides](https://www.minecraft-guides.com/guide/craft-to-exile-2-classes-guide/)).
- **Performance:** the description advertises "High Performance! 30/30 Players, 20 TPS!" ([ModDex mirror](https://moddex.gg/modpack/craft-to-exile-2)). The wiki recommends "4-8 GB of RAM" ([Installation](https://github.com/mahjerion/Craft-to-Exile-2/wiki/Installation)) and "6-8gb" when troubleshooting ([Troubleshooting](https://github.com/mahjerion/Craft-to-Exile-2/wiki/Troubleshooting-&-Performance)). The changelog lag fixes are in section 6.
- **YouTube:** a video titled "I LOVE the Craft to Exile 2 modpack!" by Cosmonautic exists ([YouTube](https://www.youtube.com/watch?v=bfR9n6eo4wc)); only the title was retrievable. The CurseForge text lists let's-plays by CaptainSparklez, direwolf20 and itsRyanHiga ([ModDex mirror](https://moddex.gg/modpack/craft-to-exile-2)).
- **Owner's own experience** (Realmforge vision): "Craft to Exile 2 (didn't finish: too much grind; the core mod is fairly good, but it isn't balanced and is too hard for me)" (`repo: docs/vision.md`, line 107).

### Inferences
- Praise centres on build depth (talents, classes, uniques) and the act structure as a guide. Hate centres on RNG gear grind, the post-55 and Epilogue level gap, and friction systems (anti-farm, gateway spam, no automation).
- Two of the four reviewers log about 20 hours, which suggests a mid-campaign drop-off. That matches the dev's "drop off in engagement at 55" and the owner's unfinished run.
- "Spell bloat" is not a common phrase in the reachable sources. The evidence is structural instead: 170 class actives across 10 classes, 83 support gems, and reviewer remarks on useless support gems and class overlap.

### Gaps
- Reddit (r/feedthebeast and CTE subreddits) and the CurseForge comments were not reachable: reddit is blocked, and CurseForge showed no comments. GitHub issues were blocked. No reliable "hours to level 100" figure was found. Discord-based feedback is inaccessible. Treat reception as a small sample: 4 ModDex reviews, 1 hosting blog, 1 news article, 2 guides and the owner.

## 8. Craft to Exile 1 (the predecessors) and what changed in CTE2

### Takeaway
"Craft to Exile" was two sister packs by Mahjerion:
- **[Dissonance]**: Forge 1.15.2 on the old Mine and Slash. Linear, deep, MineColonies and tech, 220+ FTB quests, maps from Lv 30.
- **[Harmony]**: Fabric 1.16.5 on Age of Exile. Vanilla-like freedom, mobs scaled to the player everywhere, max level 50, base-defence invasions, about 127 HQM quests.

CTE2 (Forge 1.20.1, Mine and Slash) brings back Dissonance's linear dimension ladder and Harmony 3.0's planned systems: skill gems, professions, instanced team dungeons, loadouts, an Overworld 1–15 band and static boss levels. It replaces "scale everywhere" with per-dimension level caps. The reason for the switch is not stated anywhere I could find.

### Cited Findings
- **The two packs, from Harmony's README:** "[Harmony] takes advantage of Age of Exile's intrinsic compatibility with vanilla … [Harmony] focuses on smaller excursions and a main base. Every so often, mobs will attack you and try to invade your base. To accompany this, mobs scale with your level in every dimension (including the Overworld). Rather than limit dimensions to a narrow level range, in [Harmony], all dimensions have the potential of reaching max level. On the other hand, [Dissonance] was the first of the two packs and features more linear progression. One of [Dissonance]'s main hallmarks is its depth: MineColonies, technology mods, and its quest log" ([GitHub Craft-to-Exile-Harmony](https://github.com/mahjerion/Craft-to-Exile-Harmony), README).
- **Harmony's quest book** says "Instead of linearized progression as in [Dissonance], [Harmony] focuses on compatibility and freedom. Mobs will always scale to the nearest player's level (the max level is 50)" (repo `config/hqm/default/description.txt`). Parsed HQM sets: Overworld (1-50) 50 quests, Nether (15-50) 33, End (30-50) 18, Bumblezone (5-50) 4, The Exchange 22, about 127 in total, with daily and weekly repeatables.
- **Harmony details:** Fabric 1.16.5 with Age-Of-Exile 2.1.5, World of Exile, HQM and Origins (ascendancies). 327,511 downloads; last update Dec 10, 2021 (2.3.2) ([CurseForge Harmony](https://www.curseforge.com/minecraft/modpacks/crafttoexileharmony)). Age of Exile itself: Fabric 1.16.x, last update Jul 29, 2021, 472,693 downloads ([CurseForge Age of Exile](https://www.curseforge.com/minecraft/mc-mods/age-of-exile)).
- **Harmony's unreleased "3.0" changelog section** ([Harmony CHANGELOG.md](https://github.com/mahjerion/Craft-to-Exile-Harmony/blob/master/CHANGELOG.md), [Unreleased]):
  - "A completely new skill system … skill GEMS and support gems. Just like in POE!"
  - "Instanced Dungeons... AND TEAM DUNGEONS!"
  - "A profession system"
  - "loadouts! Save multiple characters per account"
  - "Mob level scales with distance now instead of to player in the Overworld"
  - "New dimension progression: Overworld (1-15), Bumble (5-20), Nether (15-25), End (25-30), Hell (30-40)"
  - "Boss mobs now have a static level"
- **Harmony 2.x tuning echoes CTE2's:**
  - "Reduced mob scaling by 20%" and "Mobs are a bit easier in dimensions after the Overworld".
  - "Reduced the cooldown of the 'ball' spells … Spells felt too clunky to use early on".
  - "New level up rewards at 5, 10, 15, 20, 30, 40, and 50 … to help with getting your feet off the ground".
  - "Increased minimum level of the Wither, Blackstone Golem, and Ender Dragon to be 10 levels above the minimum level of the dimension".
  - A death-chest mod ("Vanilla Death Chest"; "Death chest is now a white shulker box"; protection timer raised "from 5 in-game days to 20"). This implies items drop into a chest on death rather than keep-inventory (my inference, same changelog).
- **Dissonance details:** Forge 1.15.2 with "Mine+and+Slash-1.15.2-3.15.25", ftb-quests and minecolonies (145 jars in the server repo; [GitHub Dissonance server](https://github.com/mahjerion/Craft-to-Exile-Dissonance-Server)). 907,807 downloads; last update Sep 9, 2023 (3.1.9) ([CurseForge Dissonance](https://www.curseforge.com/minecraft/modpacks/crafttoexiledissonance)).
- **Dissonance's description:** "Progression is the main theme"; "there are over 220 quests … The quest log will provide you … 'checkpoints' that guide you from one dimension to the other" ([ModDex Dissonance](https://moddex.gg/modpack/crafttoexiledissonance)).
  - Its ladder: Overworld 1–30, Bumblezone 14–18, Nether 18–26, End 26–30, Atum 30–40, Good Night's Sleep 40–50, Tropicraft or Midnight 50–58, Gaia 58–74, and "Maps" 30–99.
  - Crafting tied to levels: "Craft a stone sword to help you from levels 4-8, then swap over to an iron sword at level 9".
  - "Global waystones intrinsically cost more vanilla XP … and impose an increased cool down" (same description).
- **Timeline:** CTE2's first changelog entry is 0.1.0 on 2023-10-05 with "New class-based system. Ascensions and Skill Gems have been combined" ([GH/ARCHIVED CHANGELOG.MD](https://github.com/mahjerion/Craft-to-Exile-2/blob/main/ARCHIVED%20CHANGELOG.MD)). CurseForge first release: Nov 14, 2023 ([ModpackIndex](https://www.modpackindex.com/modpack/72640/craft-to-exile-2-20-atlas-update)).
- **A CTE2 player wants CTE1's maps back:** "i also wish there was more side content like the first cte with the maps" ([ModDex aroyund](https://moddex.gg/modpack/craft-to-exile-2)).

### Inferences
- What changed, CTE1 → CTE2:
  1. Platform: Fabric Age of Exile (Harmony) and 1.15 Mine and Slash (Dissonance) → one Forge 1.20.1 pack on the reworked Mine and Slash, which carries the AoE-era systems (skill/support gems, professions, maps, characters).
  2. Progression: "scale everywhere to 50" (Harmony) → per-dimension capped bands with revisit "enrage", to 100.
  3. Maps: unlocked from Lv 30 (Dissonance) → Lv 55 (CTE2; Lv 50 from 2.0). This created the mid-game hole the dev later had to patch.
  4. Questing: HQM (Harmony) → FTB Quests acts with lost pages, shops and repeatables.
  5. Base defence and tech: Harmony's base invasions and Dissonance's MineColonies/tech are gone; CTE2 is pure "explore + kill + loot".
- Why: likely because Age of Exile stopped updating after Jul 2021 and its author's work continued in Mine and Slash. This is an inference; no source found states it.

### Gaps
- No primary statement by Mahjerion explaining the move to CTE2; Discord and Reddit were not reachable. CTE1 reception data was not found (ModDex lists no reviews for either pack).

## 9. Relevance to Realmforge

### Adopt
- **Act = level band + realm(s) + a short main chain ending in a boss whose drop or crafted key opens the next realm.**
  - CTE2 examples: Nether Star → Catalyst; Zeal Lighter; Heart of the Deep.
  - Maps directly onto the owner's plan: chapters of 10 levels, sequential realm unlock via one portal structure with non-consumable realm items. Make the realm item a boss or quest reward of the previous chapter.
  - Keep 6–9 main quests per chapter and everything else optional, as CTE2 does (about 36 main quests vs. about 620 total).
- **Per-realm level ranges with mobs scaling to the nearest player but capped per realm, plus an "enraged" secondary range for revisits** (CTE2's `secondary_lvl_range`).
  - This keeps persistent realms worth visiting after the chapter is done, which serves the owner's "realms must feel full and alive" goal.
  - Give each realm an elemental affinity (CTE2's Mob Affinities) to theme the desert and tundra realms.
- **Quest taxonomy and teaching tools:**
  - Shape legend: main, side, repeatable, miniboss, shop, achievement.
  - Click-to-read "lost pages" that teach a concept exactly where it becomes relevant.
  - A guide book generated from the same text.
  - Realmforge already generates FTB Quests from data (research-notes "Quests", ADR 0006), so emit the quest and the guide entry from one source.
- **Account-wide point rewards through an auto-completing "Rewards" chapter** (CTE2 uses a `stat` task plus a dependency).
  - Exactly the approach in Realmforge research-notes: "our mod grants advancements and stats … FTB reads them with standard tasks". No custom task types needed.
  - Keep the CTE2 idea that quest-earned talent and ascendancy points are shared across a player's characters.
- **Repeatables in their own chapter**, each unlocked by the act quest that introduced the mob. This avoids "stuck" players. It also pairs well with the owner's mine NPCs trading dungeon keys.
- **Summonable and buyable boss access:**
  - CTE2 sells dungeon keys at the Gatekeeper "should you find a Dungeon that's already been cleared".
  - CTE2 lets players force bosses with Gateway pearls instead of hunting 5%-chance spawns.
  - Realmforge's crafted dungeon keys and instanced portals already fit. Add an NPC fallback for keys.
- **Soft death:** keep-inventory and no gear loss. Any XP penalty only late (CTE2: from Lv 50 at 5%). Cheap early mistakes suit the owner's "too hard for me" experience.
- **Side content pays level-scaled XP** (CTE2 `xp_times_lvl`) so hobbies (mining, fishing, cooking) stay worthwhile in every chapter without %-of-level rewards.
- **QoL from minute one:** backpack, warp scrolls, a return-scroll emergency item on a 30 s cooldown, a sleeping bag, no stamina, quick-stack, trash slot, auto-salvage. This matches the owner's must-have list and his hatred of walking.

### Adapt
- **Onboarding.** CTE2's Prologue front-loads about 20 systems in 25 click-to-read pages before the first real fight.
  - Realmforge M1 (about 15 quests): teach one system per quest through play, e.g. "cast your class spell on 5 animated statues", "open the spellbook and bind Frostbolt to R".
  - Reach the first fight within minutes.
  - Defer the rest to chapter-local lost pages.
- **Level bands.** CTE2's bands overlap by 5 levels and allow over-levelling.
  - Realmforge's hard caps (10, 20, 30…) stop the "power straight through the dimensions" complaint.
  - Use soft entry, as already decided: the first realm is open from Lv 1 but recommended from 5.
  - Never leave a CTE2-style gap (Epilogue at Lv 90 after Lv 60–75 content, scaffolded only by maps).
- **XP curve.** In CTE2 about 85% of the XP to 100 sits after the map unlock at level 55 (55→100), driven by an exponent of 2.4 on the level multiplier.
  - With chapter caps, Realmforge can tune XP per chapter so each 10-level band takes a target time, with quests supplying a fixed share. CTE2's changelog shows more than 10 global XP re-tunes between 0.1.0 and 1.1.0 (section 6), consistent with having no such targets.
- **Travel.** CTE2 charges only coins for normal waystone travel (1 per 100 m, at most 10; XP cost 0) and allows cross-dimension hops only via global waystones, for 3 levels; flight comes at Lv 50+.
  - For an owner who hates walking: free or cheap waystones within a realm, the realm portal hub for cross-realm travel, and an early teleport or blink spell or mount (already decided).
- **Anti-farm.** CTE2's per-chunk diminishing returns (15 free kills, −2% per kill, +5% per minute regen, +5 per chest) push exploration but alienated builders and automation players.
  - If Realmforge adopts it, exempt instances, wave dungeons and bosses (as CTE2 does).
  - Never pair it with mechanics that spawn mobs at the player's base (CTE2's on-death Gateways caused the "clutter" complaint).
- **Loot volume.** CTE2 needed a master bag, auto-salvage, loot filters and a "sticker book" (NBT chunk-lock fix) to handle loot overload.
  - Plan M2 (gear and loot) with low drop counts, meaningful drops, and auto-salvage plus filters from day one.

### Avoid
- **Skill bloat.** CTE2 1.1.3 offers 10 class trees with 12–21 actives each (170 total), 2 classes per character, 83 support gems and 28 auras. Reviewers note useless support gems and class overlap (Warlock vs. Sanguimancer).
  - The owner's 8-slot bar, the "4·N spells per chapter" cap and spell ranks are the right counter.
  - Keep each class tree small (M1: 4–5 spells per class) and add depth via ranks, not count.
- **RNG-only gear progression** ("1000 gear for single useful piece", "mostly boils down to luck").
  - Guarantee chapter-gated deterministic upgrades: quest choice rewards, crafted keys, boss tables. This echoes the owner's Otherworld [D&D] praise for "choice rewards with real gear".
- **Quest targets that are unfindable or rare.** CTE2 examples: structures the Explorer's Compass can't locate, a 5% spawn on Anglerfish death, Twilight Forest without a compass. Every Realmforge quest target needs a locator, a summon item or a vendor fallback.
- **Three parallel party systems** (M&S teams, FTB Teams, Lightman's teams, all taught in the Prologue). Use one party concept, the later command-based party system, with quest sharing built in.
- **Leaning on third-party dimension mods and heavy content mods.** CTE2's acts depend on the Undergarden, Blue Skies, Deeper Darker and TF, and it later removed Create, Incendium and Stellarity for lag and bloat. Realmforge's in-house realms (and removing the vanilla Nether and End) sidestep this; keep the modpack lean.
- **An endgame that starts at the cap and is the only content for the remaining levels.** That is CTE2's own top pain point, the "drop off in engagement at 55 (maps)". Realmforge's realms should carry 2–4 dungeons per level band up to each cap. Treat any wave or instance endgame as additive, not the sole path.
