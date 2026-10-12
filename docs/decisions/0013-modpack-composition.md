# 0013. Modpack composition

Date: 2026-10-11

Status: Accepted

## Context

The modpack wraps our core mod with third-party QoL, UI, performance and library mods ([0002](0002-hybrid-core-mod-and-modpack.md), [0004](0004-build-rpg-systems-in-house.md)). It must stay reproducible, contain no jars ([0005](0005-content-pipeline-yaml-single-source-of-truth.md)) and work for both a client and a dedicated server. Some mods from the owner's list have no NeoForge 1.21.1 build ([research notes, section 2](../research-notes.md#2-platform)).

## Decision

- **packwiz** defines the pack in `pack/` (metadata only). Players and Prism install it with packwiz-installer. Mods come from Modrinth where possible and from CurseForge when they are CurseForge-only.
- **Mods** (all verified to have NeoForge 1.21.1 builds on 2026-10-11; dependencies are pulled by packwiz):
  - QoL and UI: Jade, Jade Addons, JEI, EMI, EMI Loot, EMI Ores, JEED, Enchantment Descriptions, Mouse Tweaks, AppleSkin, Overflowing Bars, TrashSlot, Just Zoom, Neat, Toast Control.
  - Map and travel: Xaero's Minimap, Xaero's World Map, Waystones.
  - Storage: Sophisticated Storage, Sophisticated Backpacks, Tom's Simple Storage.
  - World: Regions Unexplored, HT's TreeChop, Quark (**optional, disabled by default**).
  - As-is: Better Combat, MineColonies (CurseForge), Distant Horizons, Iris.
  - Performance: Sodium, Lithium, FerriteCore, ModernFix, ImmediatelyFast, EntityCulling, spark.
  - Quests: FTB Quests, FTB Library, FTB Teams (CurseForge).
- **Replacements:** Neat replaces YDM's MobHealthBar. Toast Control temporarily replaces Toast Remover.
- **Missing on NeoForge 1.21.1, to be built by us later:** EMI Trades, Damage Numbers, No Recipe Book, Lightweight Inventory Sorting.
- **Pinned render stack:** Sodium 0.6.13 + Iris 1.8.12 (the stable pair; stable Iris 1.8.12 only works with Sodium 0.6.13, and Sodium 0.8.x needs Iris 1.8.14-beta.1) + Distant Horizons 3.3.3. These three change only together and only after a test with a DH-capable shaderpack.
- **Sides are set per mod** in each `.pw.toml`: client for rendering and UI-only mods (Sodium, Iris, Distant Horizons, ImmediatelyFast, EntityCulling, Xaero's maps, Neat, Toast Control, Just Zoom, Mouse Tweaks, Overflowing Bars, JEED, Enchantment Descriptions); both for everything that adds content, syncs data or is a library.
- `packwiz refresh` must leave the committed index unchanged (CI check).

## Consequences

- Updating mods is `packwiz update --all` plus a playtest; the pinned render stack is updated deliberately.
- `packwiz update` picks the newest file regardless of its release channel. Libraries that publish betas between releases are therefore pinned to their newest release build: Structurize, BlockUI and Domum Ornamentum for MineColonies, and JEI, whose release build needs no MezzConfig. playerAnimator has no release build and stays a beta ([pack README](../../pack/README.md#release-builds-of-libraries)).
- Kotlin for Forge was added by hand, because packwiz didn't pull it as a dependency of Fzzy Config (EMI Loot). After every mod addition, check the dependencies the jars declare.
- Better Combat depends on playerAnimator, which is no longer developed; our spell-casting animations must coexist with it (spike).
- Quark adds content: if a server enables it, every client must too. packwiz-installer's CLI mode (used for servers) cannot choose options and installs Quark anyway; remove its jar on servers that should run without it.
- A manual dedicated-server boot on 2026-10-11 (NeoForge 21.1.259, the 44 non-client pack mods including Quark, plus `realmforge` 0.0.1) started in about 19 s with no errors. Client-only mods were not covered. The boot is not yet automated in CI (planned, [0010](0010-testing-strategy.md)).
