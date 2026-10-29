# Research notes (phase 1)

> Condensed English snapshot of the phase 1 research report of 2026-10-11 (Czech, private; the live version with charts and diagrams is the artifact https://claude.ai/code/artifact/c93f6a3c-b195-4217-9545-8286da487840). This file keeps the reasoning, comparisons, facts and sources behind the decisions. The decisions themselves live in [ADRs](decisions/README.md). Where the owner overrode the report, the text says **Superseded** and points to the ADR. Counts, versions and prices are as of 2026-10-11 unless stated otherwise.

Section numbers follow the original report.

Later topic research, starting with the reference games Craft to Exile 2, Advent of Ascension and Hytale (2026-10-12), lives in [research/](research/README.md).

## Owner overrides

| Originally (report, or Czech vision) | Now | ADR |
| --- | --- | --- |
| License: MIT for code, CC0 for assets | Mod (`mod/`, `qa-mod/`, `tools/`, everything in the jar): MPL-2.0 code, CC BY 4.0 assets. Modpack (`pack/`): All Rights Reserved | [0009](decisions/0009-licensing.md) |
| Release on GitHub and CurseForge | GitHub Releases now, CurseForge later; no Modrinth publishing (mods may still be sourced from Modrinth for the pack) | [0008](decisions/0008-distribution-github-and-curseforge.md) |
| Build ourselves: YDM's MobHealthBar, Toast Remover, No Recipe Book | Neat (over YDM's MobHealthBar) and Toast Control (over Toast Remover) are Matěj's deliberate picks (2026-10-12); No Recipe Book is in the pack since the 26.1.2 port. EMI Trades, Damage Numbers and Lightweight Inventory Sorting need no build of ours any more: JEI / REI Trades, Advanced Loot Info, Just Enough Professions and Inventory Sorting, and the pack already shows damage numbers (2026-10-18 and 19) | [0013](decisions/0013-modpack-composition.md), [0044](decisions/0044-third-party-mods-by-milestone.md) |
| Pin Iris and Sodium | The pack pins the render stack together: Sodium 0.9.2, Iris 1.11.4 and Distant Horizons 3.3.3 on 26.1.2 (on 1.21.1 it was the stable pair Iris 1.8.12 and Sodium 0.6.13) | [0013](decisions/0013-modpack-composition.md) |
| Platform: NeoForge 1.21.1, port re-evaluated in 2027 at the earliest | NeoForge 26.1.2, ported on 2026-10-12 before any M1 gameplay code; MineColonies paused, EMI and its addons, TreeChop and Quark gone | [0026](decisions/0026-platform-minecraft-26-1-2-neoforge.md) |
| ID universe includes a full-pack registry dump | Vanilla reports now; the Registry Dump step is a TODO | [0005](decisions/0005-content-pipeline-yaml-single-source-of-truth.md) |
| GameTests (location unspecified) | In the dev-only QA mod `realmforge_qa`; the release jar has no test code | [0010](decisions/0010-testing-strategy.md) |
| Communicate with the owner in Czech | English | [vision](vision.md#owner) |
| Lang files in `en_us`, possibly `cs_cz` | English only (decided 2026-10-13); a community translation would ship as a resource pack | [vision](vision.md#owner) |
| Build ourselves: gear rarity, affixes, gems, reforging and enchanting, with Apotheosis and Apothic Enchanting as inspiration | Apotheosis, Apothic Attributes and Apothic Enchanting are in the pack (Matěj, 2026-10-15 and 16) and carry them, fitted to our stats: Apothic Attributes' crit and dodge never fire, its formulas are vanilla's | [0038](decisions/0038-apotheosis-carries-gear-rarity-affixes-gems-and-enchanting.md) |
| Third-party mods only for QoL, UI, performance and libraries; all dungeons and their mobs our own | The "minimal third-party mods" vision is gone (Matěj, 2026-10-18): world-generated dungeons and their mobs may come from mods, placed only in their realm with swapped blocks, boss-like mobs only if lightweight; the picks are planned by milestone, M1 to M8 (v2), and vanilla strongholds off from M1 (RLM-264, RLM-221, [research](research/third-party-mods.md)). Quests, story, classes, spells, realms, instanced dungeons, mounts, pets and the guide stay ours; talents and glyphs are postponed | [0044](decisions/0044-third-party-mods-by-milestone.md) |
| Remove Nether and End "if you choose it" | Removed (decided 2026-10-12), and the items get new sources; Ashfire, a late fire realm, does not replace the Nether (2026-10-16) | [0025](decisions/0025-remove-the-nether-and-end.md), [0032](decisions/0032-v1-realm-layout-amends-the-nether-and-end-removal.md) |
| Villager trades for emeralds (vanilla) | Trades for our own copper, silver and gold coins at 100:1, generated from YAML as vanilla 26.1 trade data with fixed prices; JEI / REI Trades shows them (Matěj, 2026-10-15 to 2026-10-19, RLM-222, [research](research/coins-and-trades.md)) | [0040](decisions/0040-coins-and-data-driven-merchant-trades.md) |
| The owner builds key rooms in creative | Nobody builds rooms by hand (decided 2026-10-12): generated rooms plus an imported MIT, CC0 and LGPL-3.0 baseline with its notices | [0028](decisions/0028-dungeon-rooms-generated-or-imported.md) |
| No vanilla textures, not even as AI input | No vanilla texture files or recolours in our assets, and none as input for generating our textures; vanilla blocks in our structures and renders of them are fine | [0028](decisions/0028-dungeon-rooms-generated-or-imported.md) |

## Inputs

The report builds on the [vision](vision.md) and the private artifact [Design Notes from Otherworld](https://claude.ai/code/artifact/40eccf72-a604-4f4b-a1d8-92db0a1e2dad). Core lesson of both: content of this size cannot be maintained without a single source of truth and automatic validation.

**Otherworld patterns we adopt:**

- One story spine: quest chapters unlock content.
- Level cap per chapter: the story cannot be out-leveled. Otherworld keeps counting XP above the cap; we stop it
  there instead (Matěj, 2026-10-15, [architecture 10.2](architecture.md#102-leveling)).
- Mob level by location (dimension, distance, depth); fixed boss levels, shown in the quest.
- Loot by mob level: affix rarity grows with level.
- Boss locator chain: lore item, compass, structure, kill, drop, next lore item.
- Choice rewards: 1 of 4 pre-rolled pieces of gear instead of random junk.

**Otherworld pains and our safeguards** (see sections 4 and 6):

| Pain in Otherworld | Safeguard in Realmforge |
| --- | --- |
| Quests for mobs from a mod that is not in the pack | CI check of every ID |
| Boss level in the quest differs from the config | Quest texts generated from the same data as the game |
| Unfinished chapters in a release | Feature flags, release only finished content |
| "Find X" quests with no location or recipe | Quest template: where, how, with what, recipe |
| Reward repairable only with netherite locked to chapter 3 | Validation: a reward is usable and repairable in its phase |
| Hidden caps (damage cap, enchant limits) | Caps shown in tooltips and the quest book |
| Currency with manual conversion | One currency or automatic conversion |
| Kill tasks are not retroactive | Prefer advancement and stat tasks |

**Technical findings that still hold:**

- FTB Quests SNBT: IDs are 16-digit hex; a choice reward references its reward table by a decimal long; files are read on world start or via `/ftbquests reload`. (1.21.1's format: FTB Quests 26.1.2 reads JSON5 with the same 16-digit ids, [architecture 16](architecture.md#16-progression-and-quests), Q1-1.)
- The Otherworld work produced a Python SNBT parser, a region file reader and an advancement catalog. The approach is known; they get rewritten here as part of the pipeline.
- Generating quests from data and validating IDs is faster and safer than hand-editing.

**What weighs most from the vision:**

- Core 100% our own. Third parties only for QoL, UI and libraries, plus Better Combat, MineColonies, Distant Horizons and Iris unchanged. (Turned around by Matěj on 2026-10-15: Apotheosis, Apothic Attributes and Apothic Enchanting are in, with a weapons mod and more to replace planned custom work; the custom core is realms, quests, races, classes, spells, professions and semi-custom dungeons. [ADR 0038](decisions/0038-apotheosis-carries-gear-rarity-affixes-gems-and-enchanting.md) records it, amending ADR 0004; [vision](vision.md), RLM-205, RLM-220.)
- Interconnected systems are the main value: one own core, not a patchwork of third-party frameworks.
- Volume (500+ quests, 100+ structures, 5-10+ dimensions) is unsustainable without generation and validation. (100+ structures counts small simple ones and other mods' structures; fully custom ones number dozens, Matěj, 2026-10-15, RLM-220.)
- Balance without RNG grind and without one-shots, measurable by simulation (section 6).
- Performance is a requirement, not a bonus.
- Dropped from the Otherworld notes: temperature, thirst and seasons from the planned "Hearth & Harvest" section.

**Added during the research:** player character leveling with chapter level caps; procedurally generated dungeons and dimensions (one dungeon type, unlimited variants under the same rules); not every dungeon ends with a boss; procedural quests and "No Man's Sky style" (section 9); the name Realmforge, also used as the name of the in-game world.

## 1. Form

Recommendation (accepted, [ADR 0002](decisions/0002-hybrid-core-mod-and-modpack.md)): hybrid.

| Criterion | Giant mod (AoA style) | Curated modpack | Hybrid |
| --- | --- | --- | --- |
| Control over gameplay | full | low: third-party mods and configs | full in core, QoL third-party |
| QoL (map, JEI, storage) | must write ourselves | ready | ready |
| Time to playable | longest | shortest | medium |
| Incompatibility risk | low | high | medium: QoL, performance and 4 big mods |
| Fits "core 100% own" | yes | no | yes |
| Distribution | one jar | modpack | modpack + our mod separately |

- **Not a pure modpack:** exactly the content the owner wants to own (dungeons, bosses, dimensions, skills) would come from other mods. Otherworld shows the result: 400 mods without a single source of truth.
- **Not a pure giant mod:** writing our own map, JEI or storage makes no sense. Advent of Ascension is a warning: one main developer, about 8 years of work; after the rewrite starting with 1.18.2 only 2 of its 20+ dimensions came back, and those are unfinished; since December 2025 development has been on hold until Mojang slows down rendering changes. Lesson: do not chase new MC versions; keep content in data, not code.
- **Shape of the hybrid:** core mod with one mod ID and feature packages (classes, spells, talents, loot, dungeons...), split into more jars only when needed; dev-only QA mod (debug commands, test scenarios), never released; modpack defined in the repo with packwiz, configs of third-party mods generated by our pipeline; integrations with QoL mods written by us (Jade and JEI plugins, Xaero icons for our mobs as JSON, no code).

## 2. Platform

**Current platform** (accepted, [ADR 0026](decisions/0026-platform-minecraft-26-1-2-neoforge.md), superseding [ADR 0003](decisions/0003-platform-minecraft-1-21-1-neoforge.md)): NeoForge 26.1.2 (26.1.2.116), ModDevGradle 2, Java 25, no Parchment (Minecraft is unobfuscated since 26.1). Matěj chose on 2026-10-12 to port before M1 gameplay code, from the research in the RLM-65 comment of that day (Modrinth, CurseForge, the NeoForge and FTB Mavens, the mods' Git branches):

- No version newer than 1.21.1 has every mod. Of the 38 mods ADR 0013 chose, 26.1.2 has 32 (30 releases, 2 betas), 26.2 has 24, 26.3 has 23, 1.21.11 has 27.
- 26.2 and 26.3 lack the FTB Quests stack (FTB targets only 26.1.2) and a third of the pack.
- Every M1 task built on 1.21.1 would have raised the price of a port: the primers measure about 158,000 words of changes from 1.21.1 to 26.1, concentrated in GUI, rendering, GameTests and item models.

The original recommendation, kept for its reasoning: NeoForge 1.21.1 (21.1.x), ModDevGradle 2, Java 21, Parchment mappings ([ADR 0003](decisions/0003-platform-minecraft-1-21-1-neoforge.md), 2026-10-11).

**Community (Modrinth API queries, 2026-10-11):** NeoForge 1.21.1 has about 2,150 modpacks, over 30x more than NeoForge 26.1.2. In 2026 over 1,000 new modpacks were created on NeoForge 1.21.1, only 34 on 26.1.2. Fabric and Forge 1.20.1 have more packs but lack key mods from the owner's list.

**What is missing per platform** (Modrinth and CurseForge, 2026-10-11):

| Platform | Missing | Verdict |
| --- | --- | --- |
| NeoForge 26.1.2 | MineColonies (with its libraries), EMI, EMI Loot, EMI Ores, HT's TreeChop, Quark; Regions Unexplored and FTB Teams only as betas (2026-10-12) | **chosen 2026-10-12** ([ADR 0026](decisions/0026-platform-minecraft-26-1-2-neoforge.md)) |
| NeoForge 26.2 and 26.3 | as 26.1.2, plus the FTB Quests stack, Regions Unexplored, JEED, JadeAddons, Toast Control, ModernFix; Neat only a beta on 26.2, JEI only a beta on 26.3 | no: no FTB Quests |
| NeoForge 1.21.1 | EMI Trades, YDM's MobHealthBar, No Recipe Book, Toast Remover, Damage Numbers. All small UI: some replaced by existing mods (Neat, Toast Control), the rest we build | recommended on 2026-10-11, superseded |
| Forge 1.20.1 | Iris and Sodium only as unofficial forks (Oculus, Embeddium); also no Damage Numbers, No Recipe Book, Toast Remover. Older APIs: NBT on items, LazyOptional, SimpleChannel | no |
| Fabric 1.21.1 | MineColonies, Sophisticated Storage and Backpacks, Curios, Quark | no |

**Why 1.21.1 was chosen first** (2026-10-11):

- Community size (above).
- Modern APIs that fit an RPG (vs 1.20.1):
  - data components: rarity, affixes and gems directly on the item stack;
  - data attachments: player stats, class and talents;
  - data-driven enchantments (since 1.21): base for enchanting without RNG;
  - attribute tooltip API, upstreamed from Apothic Attributes into NeoForge 21.1.60 (NeoForge PR #1551).
- AI knows it well. The 26.x APIs (March to September 2026) are missing or weakly represented in model training data.

**Cost: a later port is expensive.** The NeoForge porting primers from 1.21.1 to 26.1 total roughly 160,000 words of changes (rough measurement), mostly entity rendering, item models and GUI. That cost, growing with every M1 task, is what moved the port forward to 2026-10-12 (above).

**Mitigation:** keep rendering and GUI code in a few isolated packages; keep content in data. YAML content ports cheaply (only the JSON generators change); rendering ports expensively.

**What 26.1.2 brings:** Minecraft without obfuscation (official names in the sources, no Parchment), Better Combat 3 on the maintained Player Animation Library, No Recipe Book, a release render stack, data-driven villager trades. **What it costs:** a smaller community (66 NeoForge modpacks on Modrinth for 26.1.2 against 2,152 for 1.21.1), weaker AI knowledge of its APIs, and the mods above.

## 3. Take as-is vs build

Recommendation (accepted, [ADR 0004](decisions/0004-build-rpg-systems-in-house.md)): build all RPG systems ourselves; take QoL, UI, performance, libraries and a few big mods unchanged. Third-party RPG frameworks (Spell Engine, Pufferfish's Skills, Apotheosis) are models, not foundations: each brings its own attributes and assumptions, and interconnected systems are the main value. **Superseded in part** by [ADR 0038](decisions/0038-apotheosis-carries-gear-rarity-affixes-gems-and-enchanting.md): Matěj turned this around on 2026-10-15 for Apotheosis and the Apothic mods, which are in the pack and carry gear rarity, affixes, gems, reforging and enchanting; the custom core is realms, quests, races, classes, spells, professions and semi-custom dungeons. **Superseded
further** by [ADR 0044](decisions/0044-third-party-mods-by-milestone.md) (2026-10-18 and 19): world-generated dungeons
and their mobs may come from other mods, and the pack grows by milestone ([research](research/third-party-mods.md)).

### Take as-is

All have NeoForge 26.1.2 builds. The pack only references them, nothing is forked, so ARR licenses do not matter. Current pack composition: [ADR 0013](decisions/0013-modpack-composition.md).

| Area | Mods | Note |
| --- | --- | --- |
| QoL and UI | Jade (+ JadeAddons), JEI, JEED, Enchantment Descriptions, Mouse Tweaks, AppleSkin, Overflowing Bars, TrashSlot, Just Zoom, Neat, Toast Control, No Recipe Book | EMI (+ EMI Loot, EMI Ores) has no build after 1.21.1; loot and ore information goes into our in-game guide (RLM-116) |
| Map and travel | Xaero's Minimap + World Map, Waystones | Xaero monetizes only via CurseForge and Modrinth; outside them (GitHub Release) link to his page |
| Storage | Sophisticated Storage + Backpacks, Tom's Simple Storage | |
| World | Regions Unexplored (beta on 26.1.2), Tree Harvester | Tree Harvester replaces TreeChop, which stayed on 1.21.1; Quark was dropped |
| Big mods unchanged | Better Combat, Distant Horizons, Iris; MineColonies paused | MineColonies (CurseForge-only) has no 26.1.2 build yet; RLM-125 adds it back |
| Gear and enchanting | Apotheosis, Apothic Attributes, Apothic Enchanting | since 2026-10-15 ([ADR 0038](decisions/0038-apotheosis-carries-gear-rarity-affixes-gems-and-enchanting.md)); fitted to our stats, so Apothic Attributes' crit and dodge never fire |
| Performance | Sodium, Lithium, FerriteCore, ModernFix, ImmediatelyFast, EntityCulling, spark (profiler) | Embeddium is replaced by official Sodium |

### Libraries we build on

| Library | Purpose | License |
| --- | --- | --- |
| [GeckoLib 4](https://github.com/bernie-g/geckolib) | models and animations of bosses, pets, mounts | MIT |
| [Curios](https://github.com/TheIllusiveC4/Curios) | curio slots (Accessories development has stalled; Spell Engine moved from it to Curios) | LGPL-3.0 |
| [Lithostitched](https://github.com/Apollounknowndev/lithostitched) | jigsaw depth above 20, modifying other mods' structures | MIT |
| [TerraBlender](https://github.com/Glitchfiend/TerraBlender) | custom Overworld biomes, if we add any | LGPL-3.0 |
| [FTB Quests](https://github.com/FTBTeam/FTB-Quests) (v0.1) | quest book and player progress; we generate the content | ARR, CurseForge-only |

### Build ourselves: the whole core

| System | Main inspiration | Note |
| --- | --- | --- |
| Races, classes, specializations (dual spec, no dual class: RLM-218) | WoW, Origins from Otherworld | own datapack registries |
| Character leveling, chapter level caps | Otherworld (JustLeveling), WotLK | XP stops at the cap (Otherworld banks it) |
| Mob level by location | AutoLeveling | fixed boss levels |
| Stats, milestones (10 STR = +10% dmg), crit, dodge, spell power | Apothic Attributes (MIT), Spell Power | own attributes via the NeoForge API |
| Spells, skills, spell schools | Spell Engine (best data format), Iron's Spells | spell type in Java, parameters in data |
| Talents and glyphs | WoW talents, Pufferfish's Skills, Passive Skill Tree | WoW-style GUI, not a node web. **Postponed indefinitely** (Matěj, 2026-10-16, RLM-28): specialization spell trees instead (RLM-218) |
| Status effects | Iron's Spells (Oakskin), WoW | boss buffs and debuffs |
| Gear: RNG item chests | Apotheosis (code MIT, assets ARR) | items that roll a loot table. **Superseded:** rarity, affixes, gems and reforging come from Apotheosis itself ([ADR 0038](decisions/0038-apotheosis-carries-gear-rarity-affixes-gems-and-enchanting.md)) |
| Enchanting with less RNG | Apothic Enchanting | **Superseded:** Apothic Enchanting itself is in the pack ([ADR 0038](decisions/0038-apotheosis-carries-gear-rarity-affixes-gems-and-enchanting.md)) |
| Professions | WoW, Dave's Potioneering | levels, recipes as data |
| Pets and mounts | WoW Hunter pet and mounts, familiars from Otherworld | GeckoLib models |
| Mobs and bosses with phases | Hades II, WotLK | no flying bosses |
| Dimensions and dungeons | AoA 1.12, Hytale, Trial Chambers | section 9 |
| Villages, villagers, trades | Cisco's Medieval RPG | repeatable trade quests, later contracts (section 9); trades in coins as vanilla data ([ADR 0040](decisions/0040-coins-and-data-driven-merchant-trades.md)) |
| Structure locator | Explorer's Compass | none of its code (CC BY-NC-SA); our own Structure Compass in M2 ([ADR 0044](decisions/0044-third-party-mods-by-milestone.md), RLM-319) |
| Small UI | EMI Trades, Lightweight Inventory Sorting, Damage Numbers | missing on NeoForge or Fabric-only. **Superseded:** Neat and Toast Control cover YDM's MobHealthBar and Toast Remover by Matěj's choice, No Recipe Book is in the pack since 26.1.2, and Inventory Sorting, Advanced Loot Info and Just Enough Professions take the rest ([ADR 0044](decisions/0044-third-party-mods-by-milestone.md)), see [overrides](#owner-overrides) |
| Default world rules, Night Vision toggle (RLM-295; Dark Vision before), Nether and End removed ([ADR 0025](decisions/0025-remove-the-nether-and-end.md)) | Otherworld (Dark Vision) | notes below |
| QA and debug mod | Dummmmmmy | section 6 |

### Quests

- v0.1: FTB Quests generated from data ([ADR 0006](decisions/0006-quests-ftb-quests-generated-from-data.md)).
- No custom task types via FTB's internal API: they could break with FTB updates. Instead our mod grants advancements and stats (e.g. "profession at level 5") and FTB reads them with standard tasks, which are also retroactive.
- Considered alternative: [Odyssey Quests (Heracles)](https://github.com/terrarium-earth/Heracles), MIT, also on Modrinth, but its 1.21.1 release only came out on 2026-10-04.
- Procedural contracts will run in our own system (section 9).

### Default gamerules

`mobGriefing=false` also disables villager harvesting, sheep eating grass and mobs picking up items. More targeted: block only block destruction (creeper, enderman and similar) in our code.

### Removing the Nether and End

Chosen on 2026-10-12 ([ADR 0025](decisions/0025-remove-the-nether-and-end.md), which maps the new sources). Vanilla ties materials and items to them, e.g. blaze rods, nether wart, quartz, netherite, shulker boxes, elytra. The "every item is obtainable" validation therefore also runs over vanilla recipes, and missing materials get another source, e.g. in our dimensions.

### Risks

- **Iris and Sodium:** the pack pins Sodium 0.9.2, Iris 1.11.4 and Distant Horizons 3.3.3 together (on 1.21.1 stable Iris 1.8.12 worked only with Sodium 0.6.13); the client pack boot watches them. A dedicated server loads neither Iris nor Sodium.
- **Better Combat** 3 on 26.1.2 depends on [Player Animation Library](https://github.com/ZigyTheBird/PlayerAnimationLibrary), which is maintained (on 1.21.1 it needed playerAnimator, which is not). Our spell-casting animations must coexist with it: spike 4 showed they do, on a layer above Better Combat's, and that its swings can be blocked during a cast without a compile-time dependency; the port repeated that check on PAL ([ADR 0020](decisions/0020-cast-animations-and-melee-while-casting.md)).
- **CurseForge-only:** FTB Quests (with FTB Library, FTB Teams), and MineColonies (with Structurize, BlockUI, Domum Ornamentum) once it returns. A Modrinth pack (`.mrpack`) may download only from Modrinth, GitHub and GitLab.
- **Modrinth and AI:** since 2026-08-13 Modrinth does not publicly allow projects "primarily generated by AI" ([policy](https://support.modrinth.com/en/articles/16551575)). Hence GitHub and CurseForge (section 7).
- **Spell Engine is GPL-3.0-only.** Not a problem for an open-source mod, but its code must not be copied into our files under a different license (the report said MIT; the same applies to our MPL-2.0 files). It stays an architectural inspiration only: it brings its own attributes and depends on Spell Power, playerAnimator and Curios.
- **Project license:** the report proposed MIT for code and CC0 for assets. **Superseded** by [ADR 0009](decisions/0009-licensing.md).
- **Name:** "Claude" is an Anthropic trademark; use requires prior permission and must not imply endorsement ([trademark guidelines](https://www.anthropic.com/legal/trademark-guidelines)). Hence the name Realmforge (mod ID `realmforge`, QA mod `realmforge_qa`).

## 4. Content pipeline

Recommendation (accepted, [ADR 0005](decisions/0005-content-pipeline-yaml-single-source-of-truth.md)): all content lives in `content/` as YAML; a Python pipeline validates every ID before each build and generates everything else. Python is the owner's strength and AI writes it reliably.

- Generators run only on data that passed validation. Errors are always fixed in `content/`, never in generated files.

**Split of responsibility:**

- **Java = behaviour types:** spell type (projectile, AoE, buff...), affix type, boss phase, dungeon generator.
- **Data = instances:** Fireball is a projectile spell with damage 12 and a burn effect. A new spell needs no new code.
- **Items, blocks and entities** must be registered in Java. Registrations are generated from YAML as a Java file, so code refers to them via constants and the compiler catches typos.
- **Spells, talents, affixes, gems, dungeon types and boss definitions (phases, stats)** are datapack registries with codecs. The game validates them when the world loads, but `/reload` does not reload them. Things we tune often can be loaded via a reload listener instead.

**What the pipeline generates:**

- assets: item and block models, blockstates, lang (`en_us`, possibly `cs_cz`; **Superseded:** English only, see [vision](vision.md#owner)), mob icons for Xaero;
- data: recipes, loot tables, tags, worldgen, advancements, our registries;
- FTB Quests SNBT with IDs derived from the quest key, so regeneration never breaks saved player progress;
- configs and datapack tweaks for third-party mods;
- documentation and wiki pages, e.g. a table of all bosses with levels.

**Validation, in this order, locally and in CI:**

1. **Schema:** every YAML file against a pydantic model. The models also produce JSON Schema for editor autocompletion.
2. **References:** every ID must exist in the "ID universe": vanilla reports (`--reports`), a registry dump of the whole pack (`/dump registry` from the Registry Dump mod; **TODO**, see overrides) and our own IDs.
3. **Semantic rules** (Otherworld lessons):
   - every item is obtainable (recipe, loot, trade or quest);
   - every reward is usable and repairable in its chapter;
   - the boss level in the quest title matches the data;
   - every quest says where, how and with what;
   - no orphaned textures or lang keys.
4. **Runtime:** the server starts with no errors in the log and GameTests pass (section 6).

## 5. Assets

Recommendation (accepted, [ADR 0007](decisions/0007-asset-pipeline.md); the generator choice refined by [ADR 0023](decisions/0023-texture-generator.md): Retro Diffusion Fast only): Retro Diffusion as the texture generator, models via Blockbench MCP and GeckoLib, sounds from vanilla plus ElevenLabs. ADR 0007 also named PixelLab as a cheap fallback; spike 5 dropped it (ADR 0023). Retro Diffusion has an MCP server for Claude Code, so the agent calls it directly ([setup](dev-setup.md#5-claude-code)). Budget (2026-10-11): roughly 40-140 USD per month while producing assets, 0-18 USD per month otherwise (excluding VAT).

### Image generators

| Tool | Native MC pixel art | Claude integration | Price | Output rights |
| --- | --- | --- | --- | --- |
| [Retro Diffusion](https://github.com/Retro-Diffusion/api-examples) | yes: styles `rd_plus__mc_item`, `rd_plus__mc_texture` (cheaper `rd_fast__mc_*`) | hosted MCP + Claude Code plugin | prepaid credit, small MC texture about 0.02-0.03 USD | you own outputs |
| [PixelLab](https://www.pixellab.ai/pricing) | yes: native 16x16 (Pixen model) | MCP | 12 USD/month for 2,000 generations | you own outputs, commercial use OK |
| Scenario | no, convert with its Pixelate tool | MCP | 15-75 USD/month | commercial license on paid plans |
| OpenAI GPT Image, Gemini (Nano Banana) | no, needs downscaling | no official MCP | 0.005-0.2 USD per image | you own outputs |
| FLUX via fal.ai | no, LoRA + downscale | official MCP | about 0.02-0.04 USD per megapixel | commercial use OK |

- Retro Diffusion wins: the only one with Minecraft-specific styles. It can also lock a palette, do seamless tiling (works for `rd_fast__mc_texture`, spike 5) and make cuboid 3D models exported to `.bbmodel` as a Blockbench draft.
- Spike 5 (2026-10-12) compared both on the same 10 textures. Matěj picked Retro Diffusion Fast for all 10, cleaned
  to the project palette; PixelLab looked like a generic pixel-art game rather than Minecraft and is dropped
  ([ADR 0023](decisions/0023-texture-generator.md), [results](../art/spike5/README.md)).
- Big general models (GPT Image, Gemini) are only for concept art, e.g. three views of a mob.

### Texture pipeline

1. A fixed project palette, close to vanilla: `art/palette/realmforge.gpl`.
2. A batch of candidates from Retro Diffusion Fast via MCP ([ADR 0023](decisions/0023-texture-generator.md)).
3. Grid and palette cleanup: [unfake](https://pypi.org/project/unfake/) (MIT) or Pillow `quantize`. Built in
   spike 5 without either, as `uv run rf art clean` (standard library only).
4. Review gallery: accept, reject, regenerate (`uv run rf art gallery`).
5. Manual touch-ups only where needed (LibreSprite free, Aseprite paid).
6. Variants scripted from one base: rarities, gem colours, armor tiers. Saves money and keeps the style consistent.

### Models and animations

- The agent writes geometry and keyframe animations, preferably via [Blockbench MCP](https://github.com/jasonjgardner/blockbench-mcp-plugin) (headless mode, GeckoLib validation, rendered previews).
- Weakest link: UV-mapped entity textures. No generator understands UV layouts. Process: model, UV layout, texture per face, manual touch-up.
- So "Claude can do models and texture mapping" holds for geometry and the technical side of animation. Good-looking entity textures and natural motion need iterations and the owner's eye.

### Sounds

- Cheapest and stylistically consistent: vanilla sounds with different pitch and volume via `sounds.json`. We never copy vanilla files, only reference them.
- New sounds: ElevenLabs Sound Effects (Starter 6 USD/month, commercial license on paid plans) and Freesound CC0.
- Sonniss GDC bundle: rather not. Raw files may not go into an open repo, CI would need them from private storage, and they cannot be put under the assets license.
- Format: OGG, mono. Only mono sounds attenuate with distance.

### License and legal notes

- **Purely AI-generated outputs are probably not copyrightable.** US: Copyright Office report 2025, Thaler v. Perlmutter. Czech Republic: [Municipal Court in Prague, 10 C 13/2023](https://msp.gov.cz/documents/14569/1865919/10C_13_2023_10). Fine for open source, but anyone may use such outputs freely and a CC BY license probably cannot be enforced on them; the report therefore proposed CC0. **Superseded:** assets are CC BY 4.0 ([ADR 0009](decisions/0009-licensing.md)), with this caveat accepted.
- Retro Diffusion and PixelLab terms grant the user the outputs, including commercial use, but forbid training other models on them.
- EU AI Act, Art. 50 (applies from 2026-08-02) practically does not apply to hobby textures.
- **Minecraft EULA and Usage Guidelines:** no vanilla textures or recolours of them in our jar; derivatives belong to Mojang. So we do not use them even as AI input. "Minecraft" must not be the dominant part of a name.

## 6. Testing and QA

Recommendation (accepted, [ADR 0010](decisions/0010-testing-strategy.md)): test layers from fast data checks to a nightly client test, plus balance simulation and our own QA mod. Python and GameTests catch most bugs before the game is ever launched.

| Layer | Checks | Tool | When |
| --- | --- | --- | --- |
| Data | schema, ID references, semantic rules | pytest | every commit, seconds |
| Logic | stat formulas, affix rolls, talents | JUnit, NeoForge testframework | every commit |
| World (server) | boss changes phases, dungeon instance is created and removed, loot gives valid items, quest advancements are granted | [GameTest](https://docs.neoforged.net/docs/1.21.1/misc/gametest/) (`runGameTestServer`, headless) | every PR |
| Generated files | generator output matches the repo | generators in `tools/` + `git diff --exit-code` | every PR |
| Whole pack | dedicated server with all server-side mods boots with no errors in the log | packwiz-installer, `NEOFORGE_DEDICATED_SERVER_SELFTEST`, log grep | every PR |
| Client | game loads with Iris and Sodium, screenshots, sheet of all item icons | [mc-runtime-test](https://github.com/headlesshq/mc-runtime-test) (Xvfb, software rendering) | nightly |
| Balance | TTK, DPS, survival of boss phases, time to item | Python simulation over the same YAML | every PR |
| Performance | tick time, allocations | spark profile as an artifact | nightly |

**Balance as a test, not a feeling:**

- Simulation from data computes TTK and DPS per class and tier, and survival of individual boss phases.
- The anti-RNG-grind rule as a test: expected time to a specific item must not exceed a limit (e.g. 60 minutes).
- Economy: reward value vs prices, so no useless rewards like in Otherworld.
- Results as a report with charts on every PR.

**QA mod ideas (dev only):**

- `/qa` commands: level, class and talents, gear preset, spawn a boss in phase N, teleport into a dungeon instance, god mode, time acceleration, data reload (datapack registries only via world restart).
- Training dummy with a DPS meter (our own Dummmmmmy).
- Combat log to a file the agent can read and evaluate.
- Overlay: boss phase, mob AI target, hitboxes.
- Scenarios: a script sets up the situation, the owner just plays and judges.
- Screenshot command and rendering all icons into one image.
- Later a local bridge so the agent can see screenshots and game state from the owner's PC (like the MineClient Bridge mod).

**Limits:**

- GameTest cannot do client rendering. NeoForge has no equivalent of Fabric's client gametests with screenshots.
- Software-rendered screenshots are for visual checks, not pixel-by-pixel comparison.
- No test says whether it is fun. That is the owner's job.

## 7. Repository and workflow

Recommendation (accepted, [ADR 0011](decisions/0011-repository-layout-and-workflow.md)): one monorepo on GitHub, developed in WSL, played and playtested via Prism Launcher on Windows. The GitHub repo is needed for CI and Claude Code cloud sessions.

```text
realmforge/
├─ CLAUDE.md            rules for agents, versions, commands
├─ content/             YAML: the single source of truth
├─ tools/               Python: validation, generators, simulation, asset pipeline
├─ mod/                 NeoForge core mod (ModDevGradle)
├─ qa-mod/              dev-only QA mod, never released
├─ pack/                packwiz modpack definition, third-party mod configs
├─ art/                 prompts, palettes, asset sources
├─ docs/                design, decisions (ADRs), research
└─ .github/workflows/   CI and release
```

**CLAUDE.md contains:** exactly pinned versions and a ban on upgrades without a decision; commands (build, generate, validate, test, `runClient`); rules (content only via `content/`, generated files never hand-edited, everything in the repo in English); an architecture map and known 1.21.1 pitfalls (data components instead of NBT, `ResourceLocation.fromNamespaceAndPath`, payload networking); recipes like "add an item" or "add a boss", later as skills.

**How agents work:**

1. An issue with a clear scope: one feature or one batch of content.
2. A branch and a Claude Code session: locally in WSL or in the cloud on claude.ai/code.
3. A hook runs compilation and validation after every edit; errors go straight back to the agent.
4. MCP servers with NeoForge docs and decompiled MC sources prevent API hallucinations.
5. A second agent reviews first, then the PR goes through CI.
6. The owner plays it and merges.

**CI/CD plan:**

- **PR:** build, unit tests, GameTests, generated-files check, data validation, whole-pack boot, balance report.
- **Nightly:** client test with screenshots, spark profile.
- **Tag `v*`:** version format `0.1.0+mc1.21.1`, GitHub Release (jar + modpack), CurseForge via [mc-publish](https://github.com/Kira-NT/mc-publish) or mod-publish-plugin. **Superseded in part:** CurseForge comes later ([ADR 0008](decisions/0008-distribution-github-and-curseforge.md)).
- **No Modrinth:** AI policy and CurseForge-only dependencies (section 3).

**WSL vs Windows** (the owner's question): the repo does not need to be on Windows, rather the opposite.

- Repo in WSL (ext4): fast Gradle, Linux tooling, Claude Code works best there.
- Never build via `/mnt/c`: the Windows-WSL file bridge is much slower.
- Play on Windows: a Prism Launcher instance pulls the current pack on launch via packwiz-installer (from `packwiz serve` in WSL, or from GitHub). A Gradle task copies our jar into it.
- `runClient` directly in WSL (WSLg) is enough for quick checks. Graphics tend to be slower there; Windows is better for playing and shaders.
- `.gitattributes` with LF line endings so Windows and WSL do not fight.

## 8. AI limits

Code, data and content volume: AI handles well. Fun, visual taste and final decisions: the owner. Biggest technical risk: API hallucinations, hence pinned versions, MC sources in context and compilation after every change.

| Area | How AI does | Where the owner steps in |
| --- | --- | --- |
| Java: registration, codecs, networking, commands | well, with MC sources in context | playtest, spot review |
| Data content: 500+ quests, loot, recipes, lore, story | very well, volume is no problem | direction, tone, approval |
| Balancing numbers | well, via simulations | fun only shows in play |
| Mob AI and boss phases | logic well, "feel" only after playing | play and describe what is off |
| GUI: talents, professions, quests | functional at once, pretty after screenshot iterations | taste |
| Rendering, shaders, mixins | medium: most hallucinations and most expensive ports | keep to a minimum |
| 16x16 textures | good with tools, but style varies | selection and touch-ups |
| Models, UV textures, animations | geometry yes, nice texture and motion less so | review, occasional manual edits |
| Hand-built rooms | assembling from parts yes, architectural beauty no | build key rooms in creative. **Superseded:** rooms are generated or imported, and the owner reviews them in a gallery, see [overrides](#owner-overrides) |
| Performance | profiling via spark and optimization | test on the owner's PC |
| Accounts, payments, publishing, legal choices | no | owner |

**Watch out for:**

- API hallucinations: models mix MC versions and loaders; the [Fabric wiki](https://wiki.fabricmc.net/tutorial:using_llms) describes this directly (May 2026).
- Context: a big project does not fit into one session. Small PRs and a maintained CLAUDE.md help.
- Consistency of 500 quests and hundreds of items is kept by the pipeline and validation, not by model memory.
- Pace: v0.1 as a vertical slice in weeks. The full vision is more a matter of a year than months: AI speeds up code and data, but assets, hand-built rooms and playtests depend on the owner's time.

## 9. Procedural generation

Recommendation (accepted, [ADR 0012](decisions/0012-procedural-dungeons-and-dimensions.md)): procedural dungeons and dimensions from v0.1. Procedural quests as a complement to the hand-written campaign, not a replacement. Generating the rules themselves is possible but a project the size of Realmforge.

### Dungeons: a type is a rule set, not a map

A dungeon type defines a theme palette, a room library, the graph shape (start, corridors, special rooms, end), counts, weights and difficulty. Every run has its own seed, so dungeons practically never repeat.

- **World structures:** vanilla jigsaw assembles rooms from template pools (villages, bastions, Trial Chambers). Limits in 1.21.1: assembly depth 20 (128 with Lithostitched) and reach 128 blocks from the centre (116 with terrain adaptation), i.e. about 256 x 256 blocks.
- **Instanced dungeons:** our own Java generator lays rooms into a "plot" in an instance dimension. No jigsaw limits, full control over the graph.
- **Plots instead of runtime dimensions:** Compact Machines and Mine Cells split one dimension into plots of 1,024 blocks. On 1.21.1 this is safer than creating dimensions at runtime: the Infiniverse library has an unfixed crash on unload on 1.21.x.
- **Mineshafts** work on the same principle, just with an older generator written in code.
- **Handcraft stays in the room library:** nice rooms are built in creative and the generator combines them. **Superseded:** the library holds generated and imported rooms, see [overrides](#owner-overrides).

**Not every dungeon ends with a boss.** The ending is a rule of the type. Archetypes: boss lair; treasury (traps and puzzles, vault at the end); puzzle temple; wave arena; escape or chase; mounted run you cannot dismount; ruin exploration (lore and profession materials).

### Dimensions

- Terrain is procedural by nature: worldgen in data (noise settings, density functions, biomes, features), written directly as JSON in `content/worldgen/` as the exception to YAML; the pipeline validates and copies it.
- Exotic shapes (floating islands, layered worlds) are mostly doable with density functions in data; vanilla has a `floating_islands` preset. A custom Java chunk generator only as a last resort.
- So a dimension is also a rule set, never one fixed map.

### Procedural quests (No Man's Sky style)

Feasible and not the hardest part: medium effort, less than the talent or boss systems.

- The generator takes templates: kill, collect, deliver, explore a structure, clear a dungeon instance of type X, craft with a profession at level Y.
- Parameters come from the current world and player progress.
- Rewards come from a budget in the economic model, to avoid inflation.
- Texts are assembled from templates; hundreds of variants are cheap to write.
- Needs our own quest engine: FTB Quests has one shared quest set for all players and cannot generate quests per player at runtime.

As a campaign replacement: no; as a complement: yes. Endless generic tasks get stale fast (a common complaint about Skyrim's radiant quests and No Man's Sky missions). Plan:

- a fixed hand-written campaign and side questlines with an ending (FTB Quests generated from data);
- an endless layer as **contracts**: a job board, guild ranks, profession orders, "dungeon of the day";
- contracts give currency, reputation and materials, so they matter after the campaign. Repeatable trade quests start in FTB Quests and move to contracts later.

### "Full No Man's Sky": procedurally generated rules

Technically possible: a dimension seed picks a terrain archetype, palette, biomes, mob families and dungeon types; a mob is assembled from a skeleton (GeckoLib rig), parts, a palette and an ability set; a boss is assembled from phases from a mechanics library.

Main obstacles:

- Minecraft registries freeze at startup. No new block or entity types at runtime, so the answer is "one entity type, endless variants in data".
- Assets: good procedural models, textures and animations are the hardest part.
- Balance: every combination must be fair and fun, not just different.
- "Oatmeal" risk: a thousand variants that all feel the same.
- New dimensions at runtime are shaky on 1.21.1 (Infiniverse crashes on unload). Fix: generate the set of dimensions into the world's datapack when the world is created.

Effort: very high, comparable to all of Realmforge. Makes sense as a standalone mod. The data-driven dungeon and boss generators we build now are its first step; generating the rules themselves can be added later without a rewrite.

## 10. Next steps

> Status: M0 finished step 1 and most of step 2; steps 3-6 and the vertical slice are planned in the [roadmap](roadmap.md), the architecture is in [architecture.md](architecture.md).

No more desk research needed; remaining unknowns are resolved by small technical spikes, each a small PR with an answer:

1. Skeleton: NeoForge 1.21.1 MDK, CI with a build and one GameTest.
2. Pipeline: YAML to item, recipe, lang and quest, with ID validation.
3. Instanced dungeon: a plot in an instance dimension, a generator with 3 rooms, cleanup after leaving.
4. Better Combat and our spell-casting animations without conflict.
5. Textures: the same 10 textures in Retro Diffusion and PixelLab, compared in a gallery.
6. Pack boot: all QoL mods and our jar start on server and client, Iris and Sodium pinned.

Then architecture and a roadmap whose first milestone is a small playable vertical slice, for example: 1 race, 2 classes, a few spells and talents, 1 dimension, 1-2 dungeon types, 1 boss with phases and the first quest chapter.

## Open questions

- Seasons: explicitly not for now.

## Sources

Pages opened during the research, 2026-10-10 to 2026-10-11. Mod and modpack counts come from our own Modrinth API queries.

**Platform and ecosystem**

- [Mojang: version manifest](https://piston-meta.mojang.com/mc/game/version_manifest_v2.json)
- [Mojang: removing obfuscation](https://www.minecraft.net/en-us/article/removing-obfuscation-in-java-edition)
- [NeoForge: maven versions](https://maven.neoforged.net/releases/net/neoforged/neoforge/maven-metadata.xml)
- [NeoForge: 2025 retrospective](https://neoforged.net/news/2025-retrospection/)
- [NeoForge: 26.1 snapshots and the next stable version](https://neoforged.net/news/26.1snapshots/)
- [NeoForge: porting primers](https://docs.neoforged.net/primer/docs/)
- [NeoForge: ModDevGradle 2](https://neoforged.net/news/moddevgradle2/)
- [Modrinth API](https://docs.modrinth.com/api/)
- [All the Mods 11 on CurseForge](https://www.curseforge.com/minecraft/modpacks/all-the-mods-11)
- [Advent of Ascension wiki](https://adventofascension.fandom.com/wiki/Advent_of_Ascension_Wiki)
- [Advent of Ascension wiki: player questions](https://adventofascension.fandom.com/wiki/Player_questions)
- [Fabric wiki: LLMs and modding](https://wiki.fabricmc.net/tutorial:using_llms)

**Libraries and mods**

- [Spell Engine](https://github.com/ZsoltMolnarrr/SpellEngine)
- [Pufferfish's Skills: license](https://github.com/pufmat/skillsmod/wiki/License)
- [Better Combat](https://github.com/ZsoltMolnarrr/BetterCombat)
- [Player Animation Library](https://github.com/ZigyTheBird/PlayerAnimationLibrary)
- [GeckoLib](https://github.com/bernie-g/geckolib)
- [Curios](https://github.com/TheIllusiveC4/Curios)
- [Apotheosis](https://github.com/Shadows-of-Fire/Apotheosis/tree/1.21)
- [NeoForge PR #1551: attributes from Apothic Attributes](https://github.com/neoforged/NeoForge/pull/1551)
- [FTB Quests](https://github.com/FTBTeam/FTB-Quests)
- [Heracles (Odyssey Quests)](https://github.com/terrarium-earth/Heracles)
- [Infiniverse](https://github.com/Commoble/infiniverse)
- [Compact Machines](https://github.com/CompactMods/CompactMachines)
- [Lootr](https://github.com/noobanidus/lootr)
- [Lithostitched wiki](https://github.com/Apollounknowndev/lithostitched/wiki)
- [Minecraft wiki: structure format and jigsaw limits](https://minecraft.wiki/w/Structure/JSON_format)
- [MineColonies](https://github.com/ldtteam/minecolonies)
- [Xaero's Minimap](https://modrinth.com/mod/xaeros-minimap)

**Testing, CI and distribution**

- [NeoForge: GameTest](https://docs.neoforged.net/docs/1.21.1/misc/gametest/)
- [mc-runtime-test](https://github.com/headlesshq/mc-runtime-test)
- [Fabric: automated client testing](https://docs.fabricmc.net/develop/automatic-testing)
- [AE2: example CI for 1.21.1](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1.21.1/.github/workflows/build.yml)
- [Registry Dump](https://modrinth.com/mod/registry-dump)
- [packwiz](https://github.com/packwiz/packwiz)
- [Modrinth: .mrpack format](https://support.modrinth.com/en/articles/8802351-modrinth-modpack-format-mrpack)
- [CurseForge: modpack rules](https://support.curseforge.com/support/solutions/articles/9000197908)
- [mc-publish](https://github.com/Kira-NT/mc-publish)
- [Minotaur](https://github.com/modrinth/minotaur)
- [Sodium](https://modrinth.com/mod/sodium)
- [MineClient Bridge](https://www.curseforge.com/minecraft/mc-mods/mineclient-bridge)
- [Claude Code: hooks](https://code.claude.com/docs/en/hooks)

**Assets and law**

- [Retro Diffusion: API and MC styles](https://github.com/Retro-Diffusion/api-examples)
- [Retro Diffusion: MCP server](https://github.com/Retro-Diffusion/retro-diffusion-mcp)
- [Retro Diffusion: terms](https://www.retrodiffusion.ai/terms)
- [PixelLab: pricing](https://www.pixellab.ai/pricing)
- [PixelLab: MCP](https://api.pixellab.ai/mcp/docs)
- [unfake](https://pypi.org/project/unfake/)
- [Blockbench MCP](https://github.com/jasonjgardner/blockbench-mcp-plugin)
- [ElevenLabs: API pricing](https://elevenlabs.io/pricing/api)
- [US Copyright Office: AI report, part 2](https://www.copyright.gov/ai/Copyright-and-Artificial-Intelligence-Part-2-Copyrightability-Report.pdf)
- [Municipal Court in Prague, 10 C 13/2023](https://msp.gov.cz/documents/14569/1865919/10C_13_2023_10)
- [EU AI Act, Art. 50](https://artificialintelligenceact.eu/article/50/)
- [Minecraft EULA](https://www.minecraft.net/en-us/eula)
- [Minecraft Usage Guidelines](https://www.minecraft.net/en-us/usage-guidelines)
- [Modrinth: AI policy](https://support.modrinth.com/en/articles/16551575)
- [Anthropic: trademark guidelines](https://www.anthropic.com/legal/trademark-guidelines)
