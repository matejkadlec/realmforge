# 0013. Modpack composition

Date: 2026-10-11

Status: Accepted; composition updated 2026-10-12 for the port to Minecraft 26.1.2
([0026](0026-platform-minecraft-26-1-2-neoforge.md), RLM-124); config overrides added 2026-10-13 (RLM-132); amended
by [0038](0038-apotheosis-carries-gear-rarity-affixes-gems-and-enchanting.md) (Apotheosis, Apothic Attributes and Apothic Enchanting added, RLM-271); amended by [0040](0040-coins-and-data-driven-merchant-trades.md) (JEI / REI Trades) and
[0041](0041-dungeon-containers-hold-loot-through-lootr.md) (Lootr, M1), 2026-10-19; amended by
[0044](0044-third-party-mods-by-milestone.md) (the mods by milestone; nothing left to build of the small UI mods),
2026-10-20; amended by [0048](0048-better-key-binds-our-own-client-mod-independent-of-realmforge.md) (Better Key
Binds, our own client mod, ships next to the core mod), 2026-10-22; composition updated 2026-10-23 for the M1 pack
tasks PK1 to PK3 and two-handed blocking (RLM-308, RLM-309, RLM-310, RLM-297); amended by
[0053](0053-target-dummies-in-the-pack-worldedit-for-building-and-no-ftb-teams-parties.md) (MmmMmmMmmMmm with
Moonlight Lib, a beta exception; FTB Teams parties off), 2026-10-25

## Context

The modpack wraps our core mod with third-party QoL, UI, performance and library mods ([0002](0002-hybrid-core-mod-and-modpack.md), [0004](0004-build-rpg-systems-in-house.md)). It must stay reproducible, contain no jars ([0005](0005-content-pipeline-yaml-single-source-of-truth.md)) and work for both a client and a dedicated server. Some mods from the owner's list had no NeoForge 1.21.1 build ([research notes, section 2](../research-notes.md#2-platform)); with the port to 26.1.2 ([0026](0026-platform-minecraft-26-1-2-neoforge.md)) others have none there.

## Decision

- **packwiz** defines the pack in `pack/` (metadata and config overrides only). Players and Prism install it with packwiz-installer. Mods come from Modrinth where possible and from CurseForge when they are CurseForge-only.
- **Mods** (all with NeoForge 26.1.2 builds, checked on 2026-10-12; dependencies are pulled by packwiz):
  - Libraries our code builds on ([0004](0004-build-rpg-systems-in-house.md)): GeckoLib (added 2026-10-23 by RLM-33:
    the custom mobs' models and animations, [architecture 14.2](../architecture.md#142-custom-mobs)).
  - QoL and UI: Jade, Jade Addons, JEI, JEED, Enchantment Descriptions, Mouse Tweaks, AppleSkin, Overflowing Bars, TrashSlot, Just Zoom, Neat, Toast Control, No Recipe Book, Yeetus Experimentus (added 2026-10-12 by RLM-84: skips the experimental-settings warning our data pack dimensions cause on every new world, [architecture 15.1](../architecture.md#151-dimensions)).
  - Map and travel: Xaero's Minimap, Xaero's World Map, Waystones.
  - Storage: Sophisticated Storage, Sophisticated Backpacks, Tom's Simple Storage.
  - World: Regions Unexplored, Tree Harvester.
  - As-is: Better Combat, Distant Horizons, Iris.
  - Performance: Sodium, Lithium, FerriteCore, ModernFix, ImmediatelyFast, EntityCulling, spark.
  - Quests: FTB Quests, FTB Library, FTB Teams (CurseForge).
  - QoL and UI for M1 (RLM-308, [0044](0044-third-party-mods-by-milestone.md)): Inventory Sorting, Advanced Loot Info
    with Advanced Core Info (betas, pinned), Just Enough Professions, Clumps, Crafting Tweaks, Item Highlighter (needs
    Iceberg), Pick Up Notifier, RightClickHarvest (needs JamLib), Comforts, Traveler's Titles (needs YUNG's API),
    Better ModList, BetterF3. Neither CleanCut nor Cut Through: Better Combat already swings through grass.
  - Building for M1 (RLM-309): Macaw's Furniture, Doors, Windows, Roofs, Bridges, Lights and Lamps, Fences and Walls,
    Paths and Pavings, Trapdoors, Stairs.
  - World for M1 (RLM-310): Structurify (needs YetAnotherConfigLib), which switches vanilla strongholds off.
  - Combat (RLM-297): Sword Blocking Mechanics, which raises a two-handed weapon to block; the core mod's rules decide
    what a block does. Iron's RPG Tweaks stays out ([0049](0049-player-hits-bypass-the-hurt-cooldown-in-our-pipeline.md)).
  - Our own, next to the core mod: Better Key Binds (added 2026-10-22 by RLM-372,
    [0048](0048-better-key-binds-our-own-client-mod-independent-of-realmforge.md)), secondary key binds. Like the core
    mod it is no packwiz entry: Prism gets its jar through `installToPrism`, the client pack boot copies it in.
- **Matěj's choices** (2026-10-12): Neat (over YDM's MobHealthBar) and Toast Control (over Toast Remover) are deliberate picks, no longer stand-ins. No Recipe Book, which 1.21.1 lacked, is in the pack.
- **Paused or removed with the 26.1.2 port** (Matěj, RLM-65):
  - MineColonies, with Structurize, BlockUI, Domum Ornamentum, Multi-Piston and TownTalk: no 26.1.2 build yet, no quests depend on it; RLM-125 adds it back.
  - EMI, EMI Loot and EMI Ores: no build newer than 1.21.1 on any loader. JEI stays; loot and ore information goes into our in-game guide (RLM-116).
  - HT's TreeChop: replaced by Tree Harvester (needs Collective). Tree Harvester fells the whole tree at once; TreeChop's gradual chopping is gone.
  - Quark, with Zeta.
- **Missing on NeoForge 26.1.2, to be built by us later:** EMI Trades, Damage Numbers, Lightweight Inventory Sorting.
- **Pinned render stack:** Sodium 0.9.2 + Iris 1.11.4 + Distant Horizons 3.3.3, all releases on 26.1.2 (1.21.1 had Sodium 0.6.13 + Iris 1.8.12, the stable pair of that version). These three change only together and only after a test with a DH-capable shaderpack.
- **Release channels:** releases only, except Regions Unexplored 0.7.0-beta2 and FTB Teams 26.1.2.4, which have no 26.1.2 release (pack README). Later exceptions, each pinned for the same reason: Advanced Loot Info 26.1.2-2.5.0 and Advanced Core Info 26.1.2-1.5.0 (every 26.1.2 build is a beta; Matěj's pick, RLM-308, [0044](0044-third-party-mods-by-milestone.md)) and Moonlight Lib 26.1.2-4.1.1 (MmmMmmMmmMmm's library, [0053](0053-target-dummies-in-the-pack-worldedit-for-building-and-no-ftb-teams-parties.md)).
- **Sides are set per mod** in each `.pw.toml`: client for rendering and UI-only mods (Sodium, Iris, Distant Horizons, ImmediatelyFast, EntityCulling, Xaero's maps, Neat, Toast Control, No Recipe Book, Just Zoom, Mouse Tweaks, Overflowing Bars, JEED, Enchantment Descriptions, Yeetus Experimentus, Item Highlighter, Traveler's Titles, Better ModList, BetterF3, Better Key Binds); both for everything that adds content, syncs data or is a library. Tree Harvester and RightClickHarvest only run on the server but are installed on both sides, because singleplayer runs an integrated server.
- **Config overrides** go into `pack/config/`, each the mod's own file as the game writes it with only the pack's values changed, listed with their reasons in the [pack README](../../pack/README.md#configuration). The first one (2026-10-13, RLM-132) keeps waystone warps cheap: no warp costs XP (Waystones 26.1.2.16 charges up to 27 XP points by distance, and a flat 27 for a warp to another dimension), and every player may make their own waystone global.
- `packwiz refresh` must leave the committed index unchanged (CI check).

## Consequences

- Updating mods is `packwiz update --all` plus a playtest; the pinned render stack is updated deliberately.
- `packwiz update` picks the newest file regardless of its release channel; a mod that publishes betas between releases gets pinned to its newest release build ([pack README](../../pack/README.md#release-builds)). On 26.1.2 that is JEI and Jade; Yeetus Experimentus is pinned because Modrinth tags it 26.1 only, so `packwiz update` finds no 26.1.2 build of it.
- After every mod change, check the dependencies the jars declare (`neoforge.mods.toml`, embedded jars included): packwiz misses some, and a library can stay behind after its last user left. The 26.1.2 port dropped Fzzy Config and Kotlin for Forge (EMI Loot's), Bookshelf (Enchantment Descriptions now needs Prickle) and Architectury API (FTB no longer needs it), and added Player Animation Library (Better Combat 3), Shogi (Waystones) and Collective (Tree Harvester).
- Better Combat 3 animates through Player Animation Library, which is maintained, instead of playerAnimator; our cast animations use the same library ([0020](0020-cast-animations-and-melee-while-casting.md)).
- The pack has no optional mod since Quark left, so each side boots once in the pack boot test ([0018](0018-pack-boot-tests.md)).
- A manual dedicated-server boot on 2026-10-11 (NeoForge 21.1.259, the 44 non-client pack mods including Quark, plus `realmforge` 0.0.1) started in about 19 s with no errors. Since [0018](0018-pack-boot-tests.md) CI boots the pack on a server and a client for every pull request; on 26.1.2 both boots were clean on 2026-10-12 (server 30 s, client 34 s).
