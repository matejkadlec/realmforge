# 0013. Modpack composition

Date: 2026-10-11

Status: Accepted; composition updated 2026-10-12 for the port to Minecraft 26.1.2
([0026](0026-platform-minecraft-26-1-2-neoforge.md), RLM-124)

## Context

The modpack wraps our core mod with third-party QoL, UI, performance and library mods ([0002](0002-hybrid-core-mod-and-modpack.md), [0004](0004-build-rpg-systems-in-house.md)). It must stay reproducible, contain no jars ([0005](0005-content-pipeline-yaml-single-source-of-truth.md)) and work for both a client and a dedicated server. Some mods from the owner's list had no NeoForge 1.21.1 build ([research notes, section 2](../research-notes.md#2-platform)); with the port to 26.1.2 ([0026](0026-platform-minecraft-26-1-2-neoforge.md)) others have none there.

## Decision

- **packwiz** defines the pack in `pack/` (metadata only). Players and Prism install it with packwiz-installer. Mods come from Modrinth where possible and from CurseForge when they are CurseForge-only.
- **Mods** (all with NeoForge 26.1.2 builds, checked on 2026-10-12; dependencies are pulled by packwiz):
  - QoL and UI: Jade, Jade Addons, JEI, JEED, Enchantment Descriptions, Mouse Tweaks, AppleSkin, Overflowing Bars, TrashSlot, Just Zoom, Neat, Toast Control, No Recipe Book, Yeetus Experimentus (added 2026-10-12 by RLM-84: skips the experimental-settings warning our data pack dimensions cause on every new world, [architecture 15.1](../architecture.md#151-dimensions)).
  - Map and travel: Xaero's Minimap, Xaero's World Map, Waystones.
  - Storage: Sophisticated Storage, Sophisticated Backpacks, Tom's Simple Storage.
  - World: Regions Unexplored, Tree Harvester.
  - As-is: Better Combat, Distant Horizons, Iris.
  - Performance: Sodium, Lithium, FerriteCore, ModernFix, ImmediatelyFast, EntityCulling, spark.
  - Quests: FTB Quests, FTB Library, FTB Teams (CurseForge).
- **Matěj's choices** (2026-10-12): Neat (over YDM's MobHealthBar) and Toast Control (over Toast Remover) are deliberate picks, no longer stand-ins. No Recipe Book, which 1.21.1 lacked, is in the pack.
- **Paused or removed with the 26.1.2 port** (Matěj, RLM-65):
  - MineColonies, with Structurize, BlockUI, Domum Ornamentum, Multi-Piston and TownTalk: no 26.1.2 build yet, no quests depend on it; RLM-125 adds it back.
  - EMI, EMI Loot and EMI Ores: no build newer than 1.21.1 on any loader. JEI stays; loot and ore information goes into our in-game guide (RLM-116).
  - HT's TreeChop: replaced by Tree Harvester (needs Collective). Tree Harvester fells the whole tree at once; TreeChop's gradual chopping is gone.
  - Quark, with Zeta.
- **Missing on NeoForge 26.1.2, to be built by us later:** EMI Trades, Damage Numbers, Lightweight Inventory Sorting.
- **Pinned render stack:** Sodium 0.9.2 + Iris 1.11.4 + Distant Horizons 3.3.3, all releases on 26.1.2 (1.21.1 had Sodium 0.6.13 + Iris 1.8.12, the stable pair of that version). These three change only together and only after a test with a DH-capable shaderpack.
- **Release channels:** releases only, except Regions Unexplored 0.7.0-beta2 and FTB Teams 26.1.2.4, which have no 26.1.2 release (pack README).
- **Sides are set per mod** in each `.pw.toml`: client for rendering and UI-only mods (Sodium, Iris, Distant Horizons, ImmediatelyFast, EntityCulling, Xaero's maps, Neat, Toast Control, No Recipe Book, Just Zoom, Mouse Tweaks, Overflowing Bars, JEED, Enchantment Descriptions, Yeetus Experimentus); both for everything that adds content, syncs data or is a library. Tree Harvester only runs on the server but is installed on both sides, because singleplayer runs an integrated server.
- `packwiz refresh` must leave the committed index unchanged (CI check).

## Consequences

- Updating mods is `packwiz update --all` plus a playtest; the pinned render stack is updated deliberately.
- `packwiz update` picks the newest file regardless of its release channel; a mod that publishes betas between releases gets pinned to its newest release build ([pack README](../../pack/README.md#release-builds-of-libraries)). On 26.1.2 that is JEI and Jade; Yeetus Experimentus is pinned because Modrinth tags it 26.1 only, so `packwiz update` finds no 26.1.2 build of it.
- After every mod change, check the dependencies the jars declare (`neoforge.mods.toml`, embedded jars included): packwiz misses some, and a library can stay behind after its last user left. The 26.1.2 port dropped Fzzy Config and Kotlin for Forge (EMI Loot's), Bookshelf (Enchantment Descriptions now needs Prickle) and Architectury API (FTB no longer needs it), and added Player Animation Library (Better Combat 3), Shogi (Waystones) and Collective (Tree Harvester).
- Better Combat 3 animates through Player Animation Library, which is maintained, instead of playerAnimator; our cast animations use the same library ([0020](0020-cast-animations-and-melee-while-casting.md)).
- The pack has no optional mod since Quark left, so each side boots once in the pack boot test ([0018](0018-pack-boot-tests.md)).
- A manual dedicated-server boot on 2026-10-11 (NeoForge 21.1.259, the 44 non-client pack mods including Quark, plus `realmforge` 0.0.1) started in about 19 s with no errors. Since [0018](0018-pack-boot-tests.md) CI boots the pack on a server and a client for every pull request; on 26.1.2 both boots were clean on 2026-10-12 (server 30 s, client 34 s).
