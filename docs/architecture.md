# Architecture

> How the Realmforge core mod is built: code layout, registries, data flow and the main systems. Goals live in
> [vision.md](vision.md), settled decisions in the [ADRs](decisions/README.md), the order of work in
> [roadmap.md](roadmap.md). This document describes mechanisms and boundaries, not game numbers: coefficients,
> class kits and balance are data in `content/`.
>
> **Status:** baseline of 2026-10-11, recorded in [ADR 0014](decisions/0014-core-mod-architecture.md). Almost
> everything below is planned; today the item pipeline and the empty feature skeleton (M1 task F1: the packages of
> [4.2](#42-packages-of-the-core-mod), `network/`, `client/` and the import check) exist. M1 builds most of it; parts
> that come later are marked with their milestone (M2, M3, ...).
>
> **Living document.** When code and this document disagree, fix one of them in the same PR. Changing one of the
> decisions recorded in [ADR 0014](decisions/0014-core-mod-architecture.md) needs a new ADR
> ([section 21](#21-decisions-and-open-questions)).
>
> **APIs.** Every Minecraft, NeoForge and FML API named here was checked against the pinned sources: Minecraft
> 1.21.1 and NeoForge 21.1.259 on 2026-10-11, and again for Minecraft 26.1.2 and NeoForge 26.1.2.116 with the port of
> 2026-10-12 ([ADR 0026](decisions/0026-platform-minecraft-26-1-2-neoforge.md)). The FTB Quests and Better Combat
> facts were checked against their 1.21.1 jars (FTB Quests 2101.1.36, Better Combat 2.4.0), Better Combat's disable
> tag and Player Animation Library's API against their 26.1.2 builds (Better Combat 3.2.2, PAL 1.2.8). Libraries we
> have not added yet (GeckoLib, Curios, Jade, Xaero) are marked *verify*: check their API before first use
> (CLAUDE.md rule 6).

## At a glance

- One core mod split into **feature packages** (`character/`, `spell/`, `boss/`, `dungeon/`, ...), each with one
  `<Name>Feature` entry class. All client-only code sits under `client/`, enforced by an automated check.
- **Three kinds of registries:** static (what Minecraft needs Java objects for: items, entities, status effects,
  attributes), datapack (our game definitions: races, classes, spells, talents, bosses, dungeon types, affixes),
  reloadable vanilla data (loot tables, recipes, tags, advancements). Polymorphic data dispatches on Java
  "type registries".
- **YAML → `rf gen` → generated Java and JSON → jar → game.** Python validates first; codecs validate again at
  world load; GameTests compare the running game with expectations generated from the same YAML.
- **One spell and effect engine** for players, mobs, bosses, items and talent procs. Boss abilities are spells.
- **One attribute model:** primary stats (STR, DEX, INT, ...) are attributes; race, class, level, talents, gear and
  effects all act through attribute modifiers. Our own damage pipeline sits on NeoForge's damage events.
- **Player state** lives in NeoForge data attachments that sync to the client automatically.
- **Quests stay in FTB Quests**, bridged by advancements and statistics: a chapter's last quest grants an
  advancement, and that raises the level cap.
- **Dungeons** are planned and laid out by pure, seed-driven Java, then built from generated or imported NBT rooms
  into plots of one instance dimension.
- **Networking:** one registration point, versioned by the mod version; client → server messages are intents that
  the server validates.

## Contents

1. [Terms](#1-terms)
2. [Principles](#2-principles)
3. [Overview](#3-overview)
4. [Code layout](#4-code-layout)
5. [Registries](#5-registries)
6. [Data flow: from YAML into the game](#6-data-flow-from-yaml-into-the-game)
7. [Player state](#7-player-state)
8. [Stats and attributes](#8-stats-and-attributes)
9. [Combat](#9-combat)
10. [Character: races, classes, leveling](#10-character-races-classes-leveling)
11. [Spells and status effects](#11-spells-and-status-effects)
12. [Talents](#12-talents)
13. [Items, gear and loot](#13-items-gear-and-loot)
14. [Mobs and bosses](#14-mobs-and-bosses)
15. [Dimensions and dungeons](#15-dimensions-and-dungeons)
16. [Progression and quests](#16-progression-and-quests)
17. [Networking](#17-networking)
18. [Client code: rendering, GUI, input](#18-client-code-rendering-gui-input)
19. [Cross-cutting rules](#19-cross-cutting-rules)
20. [Tests per layer](#20-tests-per-layer)
21. [Decisions and open questions](#21-decisions-and-open-questions)

## 1. Terms

| Term | Meaning |
| --- | --- |
| Logical server / client | The server runs the rules and owns all state; the client renders and sends input. In singleplayer both run in one process, but the code still keeps them apart. |
| Mod bus / game bus | Two event buses. The mod bus carries startup and registration events; the game bus (`NeoForge.EVENT_BUS`) carries gameplay events (damage, ticks, logins). |
| Static registry | A Minecraft registry filled from Java code at game start and frozen afterwards: items, blocks, entity types, status effects, attributes. |
| Datapack registry | A registry filled from JSON files when a world loads, like vanilla biomes or enchantments. Each file is decoded and validated by a codec; the registry can be synced to clients. |
| Codec, `StreamCodec` | Mojang's declarative serializers. A `Codec` reads and validates JSON or NBT (the Java counterpart of a pydantic model); a `StreamCodec` encodes network messages. |
| `MapCodec`, dispatch | A codec for the fields of an object. Dispatch picks the `MapCodec` from a `type` field, like a pydantic discriminated union. |
| `ResourceKey`, `Holder` | A `ResourceKey` names a registry entry (`realmforge:firebolt` in the spell registry); a `Holder` is a resolved reference to the entry itself. |
| Tag | A named set of registry entries defined in data, such as `#minecraft:swords` or `#minecraft:bypasses_armor`. |
| Data attachment | NeoForge's way to store our own data on a player, entity, chunk or level (character level, talents), saved with its holder and optionally synced. |
| Data component | Typed data on an item stack (replaces stack NBT since 1.20.5): rarity, affixes, sockets. |
| Attribute, modifier | Vanilla's stat system (`generic.max_health`, `generic.attack_damage`, ...). Modifiers with an id add to or multiply the base value. |
| `SavedData` | Vanilla's way to store data for a whole level or world (our dungeon plots, world flags). |
| Payload | One network message: a Java `record` plus its `StreamCodec`. |
| Jigsaw | Vanilla's structure assembler (villages, Trial Chambers): rooms from template pools joined at jigsaw blocks. |
| GameTest | An automated test that runs inside a headless game server ([ADR 0010](decisions/0010-testing-strategy.md)). |
| Property test | A test that checks rules against many random inputs, like Python's Hypothesis. |
| Mixin, access transformer | Ways to patch Minecraft classes or open up private members. Last resort here ([19.3](#193-mixins-and-access-transformers)). |

## 2. Principles

1. **Server-authoritative.** All rules and state live on the logical server. The client renders, plays effects and
   sends intents ("cast spell X at target Y"); the server validates every intent.
2. **Java holds behaviour types, data holds instances**
   ([ADR 0005](decisions/0005-content-pipeline-yaml-single-source-of-truth.md)). A new spell, talent, boss or dungeon
   type is YAML. Java changes only for a new kind of behaviour, such as a new spell effect type.
3. **One effect engine.** Players, mobs, bosses, items and talent procs act through the same spell system. Boss
   abilities are spells, so interrupts, dispels and crowd control work on bosses by design ("boss mechanics that
   tie in with the skills and talents" in the [vision](vision.md)).
4. **One attribute model.** Every combat number is a vanilla or Realmforge attribute changed by modifiers with
   stable ids, so all sources stack the same way and show up in tooltips.
5. **Pure core, thin glue.** Formulas, generators, state machines and rule checks are plain Java with as few
   Minecraft types as possible, tested by JUnit in milliseconds. Event handlers only translate between Minecraft
   and that core.
6. **Client code is isolated** ([ADR 0003](decisions/0003-platform-minecraft-1-21-1-neoforge.md)). Rendering,
   screens, input and `Minecraft.getInstance()` live only under `client/`; a future port rewrites mostly that.
7. **Fail at load, not in play.** Python validates content before the build. When a world loads, an error in one of
   our registries stops it; errors in vanilla data (loot tables, recipes, advancements, tags) are only logged, so
   CI fails on those log lines. GameTests compare the running game with generated expectations.
8. **Integrate through standard surfaces:** advancements, statistics, data files and public APIs, never another
   mod's internals ([ADR 0006](decisions/0006-quests-ftb-quests-generated-from-data.md)).
9. **Performance is a requirement:** event-driven work with early exits, no per-tick scans of all entities,
   world generation spread over ticks, throttled network sync.
10. **Hard no's are validation rules.** Where a hard no from the vision can be checked mechanically (no flying
    bosses, few bosses with blindness or darkness, no trash loot), `tools/` enforces it.

## 3. Overview

```text
content/   YAML, plus worldgen JSON and room NBT
   │  uv run rf gen    tools/: schema → ids and references → semantic rules → generators
   ▼
generated and committed, never edited by hand
   mod/src/generated/java ........ registrations and id constants (items, blocks, entities, effects, ...)
   mod/src/generated/resources ... assets: models, textures, lang, sounds
                                   data: our registries, loot tables, recipes, tags, advancements,
                                         damage types, worldgen, structures
   qa-mod/src/generated .......... expectations and structures for GameTests
   pack/config/ftbquests ......... the quest book (FTB Quests SNBT)
   │  ./gradlew build → realmforge jar                packwiz → modpack
   ▼
game start .. static registries (generated and hand-written Java), then frozen
world load .. datapack registries decoded by codecs and synced to clients
/reload ..... loot tables, recipes, tags, advancements
   ▼
server: feature systems (character, stats, combat, spells, talents, mobs, bosses, dungeons, ...)
   ⇅  payloads and synced attachments
client: HUD, screens, input, renderers (client/ only)
```

## 4. Code layout

### 4.1 Gradle projects

| Project | Mod ID | Contents | Released |
| --- | --- | --- | --- |
| `mod/` | `realmforge` | everything players get | yes |
| `qa-mod/` | `realmforge_qa` | GameTests, `/qa` commands, training dummy, combat log, test scenarios | never |

The QA mod may call the core mod's public APIs; the core mod never depends on the QA mod (CLAUDE.md rule 7).

### 4.2 Packages of the core mod

```text
io.github.matejkadlec.realmforge
├─ Realmforge         entry point: registers every feature in a fixed order
├─ generated/         output of `rf gen` (never edit)
├─ core/              shared infrastructure: registry keys, codec helpers, requirements, curves
├─ network/           payload registration (one place), versioned by the mod version
├─ progression/       chapters, criteria triggers, statistics: the bridge to quests
├─ stats/             attributes, entity levels, stat sources, derived stats, caps
├─ combat/            damage pipeline, crit, dodge, resistances
├─ effect/            behaviour classes of our status effects
├─ spell/             spells, schools, effect types, casting, cooldowns, resources, procs, spell entities
├─ talent/            talent trees, points, spell modifiers; glyphs later
├─ item/              item behaviours, gear components; affixes and gems later
├─ loot/              loot functions and conditions, reward rolls
├─ character/         races, classes, character data, leveling, level caps
├─ world/             dimension rules and access, world defaults, Dark Vision, griefing protection
├─ mob/               mob levels and scaling, AI goal library, custom mobs
├─ boss/              boss definitions, phase machine, boss entity
├─ dungeon/           dungeon types, planner, layout, builder, instances
├─ command/           `/realmforge` operator commands (developer commands live in the QA mod)
├─ compat/            optional integrations, one subpackage per mod (client parts: client/compat/)
└─ client/            everything client-only, mirroring the features
   ├─ RealmforgeClient   client entry point
   └─ hud/  screen/  input/  render/  tooltip/  network/  compat/
```

Later milestones add `profession/`, `pet/`, `mount/`, `enchanting/` and `contract/` (procedural quests).

Every feature package exists from the start with its `<Name>Feature` class, and `Realmforge` calls them in the
order of the dependency table ([4.4](#44-dependencies-between-features)), lowest first. The client subpackages
appear with their first class.

### 4.3 Shape of a feature package

Every feature has the same parts, so it is clear where code goes:

| Part | Example in `spell/` | Role |
| --- | --- | --- |
| `<Name>Feature` | `SpellFeature` | `static void register(IEventBus modBus)`: the feature's `DeferredRegister`s, registry declarations and event listeners. Called once from `Realmforge`. |
| Records with `CODEC` (and `STREAM_CODEC` when sent) | `Spell`, `SpellEffect` | Shapes of datapack entries, attachment values and payloads. |
| Pure logic | `CastRules`, `SpellScaling` | Formulas and decisions without world access; JUnit. |
| Systems | `SpellCaster` | Glue: read the world, call the pure logic, apply the result. |
| `net/` | `CastSpellPayload` | The feature's payload records (registered in `network/`). |
| Public API | `Spells` | The only part other features use. |

Event listeners are registered explicitly in `<Name>Feature.register` (`modBus.addListener(...)`,
`NeoForge.EVENT_BUS.addListener(...)`) rather than through `@EventBusSubscriber`, so every hook of a feature is
visible in one file.

### 4.4 Dependencies between features

Dependencies point one way, and data references follow the same direction (a class names its spells and talent
trees, so `character` sits above `spell` and `talent`). "May use" includes whatever the used package may use.

| Package | May use |
| --- | --- |
| `core` | - |
| `progression`, `stats` | `core` |
| `combat` | `stats` |
| `effect` | `combat` |
| `spell` | `effect`, `progression` |
| `talent`, `item` | `spell` |
| `loot` | `item` |
| `character` | `talent`, `progression` |
| `world` | `progression`, `stats` |
| `mob` | `world`, `loot` |
| `boss` | `mob` |
| `dungeon` | `boss`, `character` |
| `Realmforge` (entry point), `command`, `compat`, `network` | public APIs of any feature |
| `client` | anything; nothing outside `client/` uses `client/` |

Generated classes belong to the feature that owns their content type (`GeneratedItems` to `item`, generated
status effects to `effect`) and follow that feature's row.

How features cooperate without breaking the direction:

- **Higher calls lower** through its public API: on level-up, `character` grants talent points through `talent`'s
  API and teaches class spells through `spell`'s; after a change, `talent` asks `stats` to recompute and `spell` to
  drop its cached spell modifiers.
- **Lower asks higher through provider interfaces** it defines and the higher feature implements: casting needs
  talent modifiers, the spellbook needs spells granted by equipped items, `stats` collects modifiers from race,
  class and talents, and `talent` needs to know which trees the player's class allows.
- **Higher reacts to lower through our own events** posted on the game bus: for example, `combat` reports a
  critical hit and the proc engine in `spell` reacts to it.
- **Each feature declares its own registries and their keys** (`spell/SpellRegistries.SPELL`). Shared type
  registries are filled by their users: `core` declares `realmforge:requirement_type`, `character` registers
  `level_at_least` in it and `progression` registers `chapter_completed`.
- **Facts that many features read live low:** every living entity's level is one attachment in `stats`
  ([7](#7-player-state)), written by `character` for players and by `mob` for mobs.

### 4.5 Startup and lifecycle

| When | Event (bus) | What we do |
| --- | --- | --- |
| Mod construction | constructor `Realmforge(IEventBus modBus, ModContainer container)` | call every `<Name>Feature.register(modBus)`; register configs with `container.registerConfig(...)` |
| Registration | `DeferredRegister`s (mod) | static entries: items, blocks, entity types, mob effects, attributes, attachment types, data components, sounds, particles, menus, criteria triggers, custom statistics, loot function types |
| | `NewRegistryEvent` (mod) | our type registries ([5.3](#53-type-registries)) |
| | `DataPackRegistryEvent.NewRegistry` (mod) | our datapack registries ([5.2](#52-our-datapack-registries)) |
| | `EntityAttributeModificationEvent` (mod) | add our attributes to players and other living entities |
| | `EntityAttributeCreationEvent` (mod) | attributes of our own entity types |
| | `RegisterPayloadHandlersEvent` (mod) | all payloads ([17](#17-networking)) |
| | `RegisterTicketControllersEvent` (mod) | chunk ticket controllers: instance plots ([15.5](#155-instances)) |
| World creation | `LevelEvent.CreateSpawnPosition` (game; listened to, never cancelled) | the gamerules of a new world, once ([19.1](#191-world-defaults)) |
| Server start | `RegisterCommandsEvent`, `ServerStartedEvent` (game) | commands |
| Player | `PlayerEvent.PlayerLoggedInEvent`, `PlayerEvent.Clone`, `PlayerEvent.PlayerRespawnEvent`, `PlayerEvent.PlayerChangedDimensionEvent`, `PlayerTickEvent.Post` (game) | recompute stats at the end of a new player entity's first tick and after a change; keep health across respawns ([8.2](#82-where-modifiers-come-from)); keep or end the player's instance run ([15.5](#155-instances)); reapply Dark Vision ([19.1](#191-world-defaults)) |
| Play | damage, death, spawn, tick and advancement events (game) | sections 8-16 |
| Client start | `RealmforgeClient` constructor; client registration events (mod) | [section 18](#18-client-code-rendering-gui-input) |

## 5. Registries

### 5.1 Which kind for what

| Kind | Filled | `/reload` | Client | We use it for |
| --- | --- | --- | --- | --- |
| Static | at game start from Java, then frozen | no | ids synced by NeoForge | everything Minecraft needs a Java object for: items, blocks, entity types, status effects (`MobEffect`), attributes, sounds, particles, attachment types, data components, menus, criteria triggers, custom statistics |
| Datapack | at world load from `data/`, decoded by codecs | no, the world must be reopened | synced if registered with a network codec | our game definitions ([5.2](#52-our-datapack-registries)) |
| Reloadable vanilla data | at world load and on `/reload` | yes | partly (recipes, tags, advancements) | loot tables, recipes, tags, advancements |

- Static entries that are content (items, blocks, mobs, status effects) are still written in YAML. `rf gen` turns
  them into Java registration code, so Java refers to them by constants and a typo fails compilation.
- A datapack registry only changes when the world is reopened (in singleplayer: save, quit, reopen; seconds). If
  some table needs hot reload while tuning, it can move to a reload listener (`AddReloadListenerEvent`); decide
  per case.
- File location: `data/<pack namespace>/<registry namespace>/<registry path>/<entry>.json`. NeoForge prefixes
  non-vanilla registries with their namespace (`CommonHooks.prefixNamespace`), so the spell `realmforge:firebolt`
  is `data/realmforge/realmforge/spell/firebolt.json`.
- Registration: `DataPackRegistryEvent.NewRegistry#dataPackRegistry(key, codec, networkCodec)`. With a network
  codec the registry is synced to clients and every client must have it; without one it stays on the server.
  Use the same codec for both unless an entry is large: a client with the same mod version can load the entries
  from its own jar with the network codec, so that codec must still read the full JSON.

### 5.2 Our datapack registries

| Registry | Content | One entry | Synced | Milestone |
| --- | --- | --- | --- | --- |
| `realmforge:race` | `content/races/` | playable race: base modifiers, innate spells, allowed classes | yes | M1 |
| `realmforge:class` | `content/classes/` | class: resource type, stat growth, spells by level, talent trees, starting kit | yes | M1 |
| `realmforge:spell_school` | `content/spell_schools/` | school: damage type, colour, power and resistance attributes (the attributes themselves are generated static registrations, so a new school needs a game restart) | yes | M1 |
| `realmforge:spell` | `content/spells/` | spell for players or NPCs: school, targeting, cost, cast, cooldown, typed effects, scaling | yes | M1 |
| `realmforge:talent_tree` | `content/talents/` | WoW-style tree: nodes on a grid, ranks, prerequisites, typed effects per rank | yes | M1 |
| `realmforge:stat_rule` | `content/stat_rules/` | what a primary stat gives: per-point modifiers, milestone bonuses | yes | M1 |
| `realmforge:curve` | `content/curves/` | named table of points (level → value), linearly interpolated: XP per level, mob scaling, item budgets | yes | M1 |
| `realmforge:chapter` | `content/chapters/` | story chapter: order and the level cap after completion (what a chapter unlocks names the chapter through `chapter_completed`) | yes | M1 |
| `realmforge:dimension_rules` | `content/dimensions/` | gameplay rules of one dimension, named in a `dimension` field (for example `minecraft:overworld`): mob levels, access requirement, flags. Validation allows one entry per dimension; the server indexes the entries by dimension | no | M1 |
| `realmforge:boss` | `content/bosses/` | fixed level, attributes, phases, abilities (spells), arena, loot | no | M1 |
| `realmforge:dungeon_type` | `content/dungeons/` | rule set: theme, room pools, layout rules, ending, budgets, level range | no | M1 |
| `realmforge:affix` | `content/affixes/` | rollable modifier: item categories (tags), tiers by rarity and level | yes | M2 |
| `realmforge:gem` | `content/gems/` | socketable gem and its bonuses | yes | M2 |

Professions, pets, mounts and contract templates follow in later milestones. A milestone task may still rename a
registry before its first use. Server-only registries reach players only through what the server sends: the boss
bar's title carries the boss's phase name, for example.

### 5.3 Type registries

Polymorphic data uses Mojang's dispatch pattern: each JSON object has a `type` that selects a `MapCodec` and a
Java class, the way vanilla handles loot functions. The types are entries of static registries we create in
`NewRegistryEvent` (`RegistryBuilder`), so a new behaviour means "write a class and register its type".

| Registry | Types (first set) | Used by |
| --- | --- | --- |
| `realmforge:spell_effect_type` | damage, heal, apply_effect, remove_effect, projectile, area, teleport, dash, knockback; summon later | spells, so also boss abilities and procs |
| `realmforge:targeting_type` | self, enemy, ally, ground point, direction; for NPCs current target, random player, farthest player, arena point | spells, boss abilities |
| `realmforge:talent_effect_type` | attribute_modifier, spell_modifier, grant_spell, proc | talents; later glyphs and set bonuses |
| `realmforge:resource_type` | mana, rage | classes |
| `realmforge:phase_trigger_type` | health_below, time_in_phase, adds_defeated | bosses |
| `realmforge:dungeon_ending_type` | boss_lair, treasury; later puzzle, wave_arena, escape, mounted_run, exploration | dungeon types |
| `realmforge:requirement_type` | level_at_least, chapter_completed, class_is, has_advancement | unlocks: dimensions, dungeons, items, talents |
| `realmforge:affix_type` (M2) | attribute, on_hit, on_kill | affixes |

### 5.4 Using registry entries in Java

- Each feature declares the keys of the registries it owns (`spell/SpellRegistries.SPELL`); `core` declares only
  its own (`curve`, `requirement_type`). Lookups go through the level's registries:
  `level.registryAccess().lookupOrThrow(SpellRegistries.SPELL)`.
- Saved data (attachments, item components) stores `ResourceKey`s; runtime code resolves them to `Holder`s. A key
  whose entry no longer exists (content removed) is dropped with a warning in the log, never a crash.
- Entries reference each other with `RegistryFixedCodec` (by id) or `RegistryFileCodec` (by id or inline), and
  lists with `RegistryCodecs.homogeneousList` (which also accepts `#tag`). A dangling reference fails the world
  load; Python reports it earlier and with a better message.
- Java rarely names a specific content entry. Where it must (well-known curves, QA presets), `rf gen` emits
  `ResourceKey` constants, so a renamed entry breaks compilation instead of play.

## 6. Data flow: from YAML into the game

### 6.1 Stages

1. **Schema:** one pydantic model per content type (`tools/realmforge_tools/schema/`).
2. **Ids and references** against the ID universe: the vanilla snapshot plus our content; the full-pack registry
   dump is still a TODO ([ADR 0005](decisions/0005-content-pipeline-yaml-single-source-of-truth.md)).
3. **Semantic rules:** the ADR 0005 rules (every item obtainable, rewards usable and repairable in their chapter,
   quest texts match the data, no orphaned lang keys or textures), structural rules per type (talent trees have
   no cycles and no unreachable nodes, boss phase thresholds decrease, dungeon rules are satisfiable) and the
   vision guardrails (principle 10).
4. **Generators** write every derived file; CI fails when `rf gen` changes anything.
5. **Runtime:** when a world loads, a decoding error in one of our datapack registries stops the GameTest server
   and fails CI. Vanilla data (loot tables, recipes, advancements, tags) is different: a broken file is logged
   (`Couldn't parse element`, `Parsing error loading recipe`, ...) and skipped. So CI also fails on those log lines
   (`uv run rf gametest-log` after the GameTests: every ERROR or FATAL line outside a reasoned allowlist, and loot
   table validation warnings; `tools/realmforge_tools/gamelog.py`), and GameTests check that every generated id
   resolves and matches the generated expectations.

### 6.2 What each content type becomes

| `content/` | Generated | Loaded as |
| --- | --- | --- |
| `items/` (exists), `stats/` (exists: attributes, [8.1](#81-the-attribute-set)), `blocks/`, `entities/`, `effects/`, `sounds/` | `Generated*.java` registrations; models, blockstates, textures (placeholders until art exists), lang, `sounds.json`; QA expectations | static registries |
| `races/`, `classes/`, `spells/`, `spell_schools/`, `talents/`, `stat_rules/`, `curves/`, `chapters/`, `dimensions/`, `bosses/`, `dungeons/` | `data/realmforge/realmforge/<registry>/<id>.json`, lang, `ResourceKey` constants where needed, QA expectations | datapack registries |
| `spell_schools/` (also) | `data/realmforge/damage_type/<school>.json`, the tag entries in `#minecraft:bypasses_cooldown` ([9.1](#91-damage-pipeline)), generated registrations of the school's power and resistance attributes | vanilla damage types, tags; static attributes |
| `loot/`, `recipes/`, tags | `data/realmforge/loot_table/`, `recipe/`, `tags/` | reloadable vanilla data |
| `chapters/`, `quests/` | `data/realmforge/advancement/`; `pack/config/ftbquests/quests/` (SNBT, stable ids per ADR 0006) | advancements; the FTB Quests book |
| `worldgen/<registry>/<id>.json` (JSON, [ADR 0012](decisions/0012-procedural-dungeons-and-dimensions.md)) | validated and copied unchanged to `data/realmforge/dimension/`, `dimension_type/`, `worldgen/<registry>/` | vanilla worldgen registries |
| `rooms/` (NBT plus a YAML sidecar, or a room spec in YAML; [15.4](#154-rooms)) | NBT validated and copied, specs generated into NBT, both to `data/realmforge/structure/` | structure templates |
| item YAML with a weapon preset | `data/realmforge/weapon_attributes/<item>.json` (Better Combat format) | read by Better Combat |
| mob YAML | Xaero's Minimap icon files (*verify* format) | read by Xaero's Minimap |
| `texts/` (exists) | lang keys `<group>.realmforge.<name>`, `GeneratedTexts` constants | translations |
| every type | `tools/schemas/<type>.schema.json` | editor completion |

The quest book is part of the modpack (All Rights Reserved, [ADR 0009](decisions/0009-licensing.md)), so its
files are generated into `pack/`, not into the jar. FTB Quests reads them from `config/ftbquests/quests/` in the
instance. After `rf gen` changes files in `pack/`, run `packwiz refresh` (CI checks the pack index).

### 6.3 Conventions

- One YAML file per entry; the file name equals the id (existing rule).
- Every entry we generate is `realmforge:<id>`: a datapack entry takes its namespace from the folder under `data/`,
  and we write everything under `data/realmforge/`. An entry about something in another namespace (the rules of
  `minecraft:overworld`) names it in a field, never in its id.
- snake_case keys in YAML and JSON, as in vanilla data.
- **Human units in YAML, game units in data.** YAML says `cooldown_seconds: 8` or `chance_percent: 5`; generated
  JSON and Java use ticks and fractions. Field names carry the unit; generators convert: `<name>_seconds` becomes
  `<name>_ticks` (the `Seconds` type in `schema/common.py` allows only whole ticks) and `<name>_percent` becomes the
  fraction `<name>`.
- Player-facing text is `LocalizedText` in YAML; generated data holds only translation keys, following the scheme
  `<registry>.realmforge.<id>.<field path>`, for example `spell.realmforge.firebolt.name` (a nested field adds its
  path: `spell.realmforge.firebolt.effects.0.label`). Texts that belong to no content type (key mapping names,
  messages) live in `content/texts/<group>.yaml` and get the key `<group>.realmforge.<name>`
  (`key.realmforge.dark_vision`); Java names them through the generated `GeneratedTexts` constants
  (`GeneratedTexts.KEY_DARK_VISION`), which belong to `core`, so every feature and `client` may use them.
- One generator writes every datapack type (`tools/realmforge_tools/generate/datapack.py`): the `id` becomes the
  file name, texts become keys, units are converted, unset optional fields are left out, and everything else keeps
  its YAML name and value. A type that needs a different JSON shape converts in its own generator instead.
- Numbers shown to players come from data, never typed into prose. Spell tooltips fill placeholders such as
  `{damage}` at runtime from the current values, talents included; quest texts are generated from the same YAML as
  the game (boss level, location, recipe).

### 6.4 Example: the life of a spell

```yaml
# content/spells/firebolt.yaml (sketch; names are examples, the pydantic model is the authority once written)
id: firebolt
name: {en_us: Firebolt}
description: {en_us: "Hurls a bolt of fire that deals {damage} fire damage."}
school: realmforge:fire
targeting: {type: enemy, range: 24}
cast: {type: cast_time, seconds: 1.5}
cooldown_seconds: 0
cost: {resource: realmforge:mana, amount: 20}
max_level: 5
effects:
  - type: realmforge:projectile
    speed: 1.2
    on_hit:
      - {type: realmforge:damage, base: 12, per_level: 3, spell_power: 0.8}
      - {type: realmforge:apply_effect, effect: realmforge:burning, duration_seconds: 4}
```

1. `uv run rf validate` checks the schema, that `realmforge:fire` is a known school, that the effect and every
   referenced status effect exist, and the semantic rules.
2. `uv run rf gen` writes `data/realmforge/realmforge/spell/firebolt.json`, the `spell.realmforge.firebolt.*` lang
   entries and a GameTest expectation.
3. `./gradlew build` packs them into the jar, whose resources act as the mod's built-in data pack.
4. When a world loads, `Spell.CODEC` decodes the file and resolves its references; NeoForge syncs the registry to
   joining clients.
5. The player presses a spell key. The client sends `CastSpellPayload`; `SpellCaster` validates it and runs the
   effects; the damage goes through the combat pipeline; nearby clients get `CastStartedPayload` for the cast bar
   and animation.
6. A GameTest casts `firebolt` at a dummy and compares the damage with the expectation.

### 6.5 Adding a content type

CLAUDE.md rule 1 lists the Python side; a datapack-registry type is also a `DatapackType` entry in
`schema/__init__.py`, which the generic generator turns into data and lang entries. On the Java side it needs a
record with a `CODEC` in its feature package, the registry key in that feature's registries class, the
`dataPackRegistry(...)` call, and a GameTest that checks the generated expectations. Curves are the worked
example: `CurveDef`, `generate/curves.py` (`GeneratedCurves` key constants, `GeneratedCurveExpectations`),
`core/Curve`, `core/CoreRegistries` and `ContentCurveGameTests`. The pydantic model and the codec describe the
same shape; CI keeps them in step because it starts a game that decodes every generated file and compares it with
the expectations.

## 7. Player state

Player and entity data lives in NeoForge data attachments, one per rate of change and audience:

| Attachment | Owner package | On | Holds | Synced to |
| --- | --- | --- | --- | --- |
| `realmforge:level` | `stats` | every living entity | the level: derived from XP and the cap for players, set at spawn for mobs, fixed for bosses | the player itself and everyone tracking the entity |
| `realmforge:character` | `character` | players | race, classes (a list: one in M1, room for dual class), total character XP, cached level cap | the owner and nearby players (small, nothing secret) |
| `realmforge:spellbook` | `spell` | players | known spells and their levels, spell bar layout | owner |
| `realmforge:combat` | `spell` | players and mobs that cast | resource (mana, rage), cooldowns, current cast | owner, throttled (players; clients never read a mob's copy) |
| `realmforge:talents` | `talent` | players | allocated ranks per tree, unspent points | owner |
| `realmforge:dark_vision` | `world` | players | whether Dark Vision is switched on (on by default) | not synced (the effect itself is) |
| `realmforge:instance` | `dungeon` | players | the run's plot and the return point (spike 3); W4 adds the game mode to restore | not synced |
| `realmforge:stat_tracker` | `stats` | players | when the stat modifiers were last recomputed and what a respawn owes the health ([8.2](#82-where-modifiers-come-from)); transient: not saved or copied | not synced |

- Values are immutable records with a `Codec` for saving and a `StreamCodec` for syncing, declared with
  `AttachmentType.builder(...).serialize(codec.fieldOf("value")).copyOnDeath().sync(predicate, streamCodec)`:
  NeoForge 26.1 saves an attachment through a `MapCodec`. A change is `player.setData(type, newValue)`; NeoForge
  then sends the update to the players the predicate allows (for example `(holder, to) -> holder == to` for
  owner-only data).
- A synced attachment sends the same data to every player its predicate accepts (NeoForge encodes each update
  once), so data meant for different audiences needs separate attachments.
- Combat state changes too often for that. It is a mutable object synced explicitly with `player.syncData(type)`,
  at most a few times per second or on important changes (cast started, cooldown started). Cooldowns are stored as
  the server game time when they end, so they survive relogging and need no per-tick updates. The current cast is
  transient: the saved `Codec` leaves it out, so neither relogging nor the copy on death brings it back (NeoForge's
  default copy handler round-trips the saved codec), and death cancels it.
- **The predicate filters updates only.** When a player starts tracking an entity, NeoForge (21.1.259 and 26.1.2.116
  alike) sends that
  player every synced attachment on it without asking the predicate (`ServerEntity#sendPairingData` →
  `AttachmentSync.syncInitialEntityAttachments`). The sync handler's `write` is not told the recipient, so no
  handler can filter that first sync either. Other players' clients therefore hold a snapshot of the owner-only
  attachments (`spellbook`, `combat`, `talents`) that later updates never refresh. Two rules follow:
  - a synced attachment holds nothing that other players' clients must not see; such data stays unsynced and
    reaches its owner through a payload;
  - client code reads owner-only attachments only from the local player, never from another player's entity.
    What clients show about other players (levels, cast bars) comes from attachments synced to every tracker or
    from payloads ([17.2](#172-what-syncs-how)).
- **Saved shapes stay readable.** NeoForge logs an attachment that fails to decode and skips it
  (`AttachmentHolder#deserializeAttachments`), so the next save drops that player's data. From the first release
  (v0.1.0) on, a saved `Codec` therefore changes only compatibly: a new field is `optionalFieldOf` with a default,
  and a change that cannot be expressed that way adds a version field and a migration. JUnit keeps a saved sample
  of every released shape and decodes it.
- Computed values (attribute modifiers, spell modifiers) are derived from these attachments and the registries,
  never treated as a source of truth ([8.2](#82-where-modifiers-come-from)).
- **In code (F3):** each owner package declares its attachments in a `<Feature>Attachments` class
  (`stats/StatsAttachments`, `character/CharacterAttachments`, `spell/SpellAttachments`,
  `talent/TalentAttachments`), registered from its `<Name>Feature`. The values are `stats/EntityLevel`,
  `character/CharacterData`, `spell/Spellbook`, `spell/CombatState` and `talent/TalentAllocation`; owner-only data
  syncs with `core/Attachments#toOwner`. Races, classes, spells and talent trees are named by `Identifier`
  until their registries exist (C1, C3a, C5); a `ResourceKey` saves the same string, so saves stay readable. The
  cached level cap stays empty until C2 computes it. Every saved field is optional with a default, so an empty
  save reads as the default value.
- `level` is copied on death, and NeoForge copies the same attachments when a mob converts (a zombie drowns,
  `LivingConversionEvent.Post`), so the converted mob keeps its level; W1 makes its scaling follow
  ([14.1](#141-mob-levels)).

## 8. Stats and attributes

### 8.1 The attribute set

- **Vanilla, kept:** max health, armor, armor toughness, attack damage and speed, movement speed, knockback
  resistance, luck and the rest. Since 1.21.2 their ids have no prefixes (`minecraft:max_health`,
  `minecraft:block_interaction_range`; 1.21.1 had `minecraft:generic.max_health`).
- **Primary stats (ours, decision D5):** strength (STR), dexterity (DEX), vitality (VIT: health, not called
  stamina so it is never confused with a stamina resource) and intellect (INT). They are attributes, so gear, buffs
  and talents modify them like anything else. They start at 0: race, class, level, talents and gear raise them (C1
  and later). Luck and defense stay out (D5).
- **Secondary stats (ours):** crit chance (5 % to start with), crit damage (+50 %), dodge chance (no primary stat
  gives it: D5 keeps it for future classes such as the Rogue), spell power, healing power and haste (cast and
  cooldown speed); resource regeneration comes with resources (C3a), per-school power and resistance with the
  schools (F5).
- **Defined in `content/stats/`** (F4): each stat's kind (primary or secondary) and its range, in points (a
  `RangedAttribute`) or as a share (`max_percent`: NeoForge's `PercentageAttribute`, holding a fraction). `rf gen`
  writes their registrations into `generated/GeneratedStats` (owned by `stats`), with the description id
  `attribute.realmforge.<id>` and `setSyncable(true)` for all of them, as the character sheet shows them. Their
  names, and with them their lang entries, come with the character sheet (RLM-136).
- `StatsFeature` adds every stat to players in `EntityAttributeModificationEvent`; mobs get the ones they need
  with the features that use them (spell power for casters).
- NeoForge also has a boolean `creative_flight` attribute (`NeoForgeMod.CREATIVE_FLIGHT`): the planned basis for
  Angel Wings-style spells and flying mounts.

### 8.2 Where modifiers come from

An attribute holds at most one modifier per id. Vanilla's equipment code and the `addOrUpdate...` and
`addOrReplace...` methods replace a modifier with the same id; the plain `add...` methods throw. So every id is
unique per source, entry, equipment slot and modifier (`<n>` is the modifier's position in its entry), the way
vanilla armor uses one id per slot. Otherwise two rings with the same affix would count once, and taking one off
would remove both bonuses.

| Source | Applied by | Modifier id | Lifetime |
| --- | --- | --- | --- |
| race, class and level | `stats/CharacterStats`, from `character`'s provider | `realmforge:race/<race>/<n>`, `realmforge:class/<class>/<n>` | saved, recomputed |
| talents | `stats/CharacterStats`, from `talent`'s provider | `realmforge:talent/<tree>/<node>/<n>` | saved, recomputed |
| derived from primary stats | `stats/CharacterStats`, from stat rules ([8.3](#83-derived-stats-and-milestones)) | `realmforge:stat/<rule>/<n>` per point, `realmforge:stat/<rule>/at_<points>/<n>` per milestone | saved, recomputed |
| gear with fixed stats | vanilla, from the item's attribute modifiers | `realmforge:gear/<slot>/<n>` (generated) | while equipped |
| affixes and gems (M2) | vanilla slots: `ItemAttributeModifierEvent` adds them to the stack's modifiers; curio slots (rings, trinkets): Curios' own modifier API, because Curios ignores vanilla equipment modifiers in its slots (*verify* in M2) | `realmforge:affix/<affix>/<slot>/<k>/<n>`: `<k>` counts repeats of the same affix or gem on one stack (two sockets with the same gem), and curio slots use their slot id and index as `<slot>` | while equipped |
| status effects | vanilla, `MobEffect#addAttributeModifier` | the effect's | while active |
| mob level | `mob`, at `FinalizeSpawnEvent` ([14.1](#141-mob-levels)) | `realmforge:mob_level/<n>` | saved with the mob, reapplied from its level and the current curves when it loads |

"Saved, recomputed" means: `CharacterStats` writes these modifiers as permanent ones and rewrites the whole set
whenever a source changes, and on the player's first tick after login or respawn. `stats/RecomputedModifiers` does
the rewrite for one source: it owns the ids its predicate accepts (stat rules: our `stat/` prefix), removes those it
no longer produces, and replaces only the modifiers whose amount or operation changed. The data stays the source of
truth, so a rebalanced talent applies at the next login without migrations. The modifiers are saved rather than
transient because of how vanilla handles health:

- **Loading a player** applies saved modifiers first, then health, and `setHealth` clamps to the max health at that
  moment (`LivingEntity#readAdditionalSaveData`). Transient bonuses would not exist yet, so health would be cut.
- **Gear applies one tick later.** Equipment modifiers are transient and are first applied on the player's first
  tick (`LivingEntity#detectEquipmentUpdates`), and lowering max health clamps health at once
  (`LivingEntity#onAttributeUpdated`). Hence two rules: gear never adds `generic.max_health` directly (a validation
  rule; max health comes from primary stats through the saved derived modifiers), and the recompute after login
  waits for that first tick, so it sees the same gear the saved values were computed with.
- **Respawning:** after a death, vanilla copies only base values and sets health before our modifiers exist
  (`ServerPlayer#restoreFrom`), so refill health to the new maximum after the first-tick recompute. Leaving the End
  (`PlayerEvent.Clone#isWasDeath` is false) is not a death: vanilla copies the permanent modifiers (ours, and
  effects', which `MobEffect#addAttributeModifiers` adds as permanent ones) and the health, but clamps the health
  while transient equipment modifiers are still missing (another mod's gear with max health). So restore the health
  captured in `PlayerEvent.Clone` after the first-tick recompute instead.

**In code (F4):** `CharacterStats` recomputes at the end of a player tick (`PlayerTickEvent.Post`), after vanilla
applied the tick's equipment changes. Every new player entity (a login, a respawn) starts with a fresh
`realmforge:stat_tracker`, a transient attachment that is neither saved, synced nor copied, so its first tick
recomputes and then applies the health rule above. Other recomputes are in [8.3](#83-derived-stats-and-milestones).
A GameTest player (`QaPlayers#login`) is never ticked by its connection, so the tests play a client tick with
`ServerPlayer#doTick` ([20](#20-tests-per-layer)).

### 8.3 Derived stats and milestones

- A `realmforge:stat_rule` entry (`content/stat_rules/`, `stats/StatRule`) says what one primary stat gives:
  per-point modifiers (the vision's "10 STR = +10% attack damage" is 1% per point), each with an optional cap on its
  total (`max_percent: 20` keeps DEX's movement speed small), and milestone bonuses once the stat reaches a number
  of points. Every point counts, fractions included; a per-point total of 0 gives no modifier.
- What the D5 stats give, with F4's first numbers in `content/stat_rules/` (RLM-136 plays them): STR attack damage,
  DEX crit chance (spells too), attack speed and movement speed, VIT max health, INT spell power; each has one
  milestone, at 25 points.
- `stats/StatRules` is the pure part: the modifiers a stat value gives. The pipeline computes the same
  (`StatRuleDef.bonuses_at`), and a GameTest compares the two at sample values.
- Recompute when a primary stat may have changed: marked dirty by `LivingEquipmentChangeEvent`,
  `MobEffectEvent.Added`, `MobEffectEvent.Remove`, `MobEffectEvent.Expired` and our own changes
  (`CharacterStats#markDirty`: level, talents), plus a cheap check every 10 ticks that compares primary values with
  the last applied snapshot (covers curios, other mods and commands).
- No cycles: rules read primary stats and write only secondary or vanilla attributes, and each primary stat has at
  most one rule (validation rules). An `add_value` amount is in the unit of its attribute: `amount_percent` for a
  share, `amount` for points; multiplied operations always take `amount_percent`.

### 8.4 Caps

Vanilla caps in 26.1.2 (as in 1.21.1) are armor 30, armor toughness 20, max health 1024 and attack damage 2048, and the vanilla
armor formula never blocks more than 80% (`CombatRules.getDamageAfterAbsorb`). The vision wants armor, toughness
and health far beyond vanilla, so:

- caps are raised in one place (`stats/AttributeCaps`), by an access transformer on `RangedAttribute#maxValue` or
  a small mixin, whichever task F4c (RLM-137) proves to work; NeoForge reads both from `neoforge.mods.toml`;
- armor mitigation uses our own level-aware formula ([9.1](#91-damage-pipeline)).

## 9. Combat

### 9.1 Damage pipeline

1. **Sources.** Melee goes through vanilla `Player#attack` (Better Combat only changes swings, combos and reach).
   Spells call `hurt` with a `DamageSource` subclass defined in `combat/`, which carries the school's id, the
   spell level and the attributes for the school's power and resistance, so `combat` needs no knowledge of spells.
   Each school is a vanilla damage type (`data/realmforge/damage_type/<school>.json`), so death messages and damage
   type tags keep working.
2. **`LivingIncomingDamageEvent`**, before mitigation: attacker side (spell power and school bonus attributes, crit
   roll) through `setAmount`; talents act through those attributes or are already part of the spell's damage
   ([11.5](#115-resources-cooldowns-modifiers)). Defender side: dodge (never by cancelling the event, see below),
   school resistance, and our armor formula in place of vanilla's through
   `addReductionModifier(DamageContainer.Reduction.ARMOR, ...)`.
   NeoForge runs that reduction for every hit, so our formula must itself skip damage in
   `#minecraft:bypasses_armor`, as vanilla's does.
3. **Vanilla's hurt cooldown** comes next. Within 10 ticks of the previous hit, a hit that is not bigger than the
   previous one is dropped without any further event, and a bigger one deals only the difference; a hit outside
   that window starts a new one. Spell, area and damage-over-time hits would be swallowed, and each would shield the
   target from the next sword swing. So the school damage types are in the `#minecraft:bypasses_cooldown` tag
   (generated), and spell hits keep the target's current window
   (`DamageContainer#setPostAttackInvulnerabilityTicks(target.invulnerableTime)`), so they neither grant nor clear
   invulnerability. They still set vanilla's protected `lastHurt`, the amount every later hit in the window is
   measured against (`LivingEntity#hurtServer` takes the `else` branch for a hit that bypasses
   the cooldown and stores `lastHurt = amount`). Inside an open window a melee hit is therefore compared with the
   last spell or damage-over-time hit, not with the hit that opened the window:
   - **after a small one it lands almost in full:** a zombie hits a player for 6 (the window opens, `lastHurt` 6), a
     DoT ticks for 1 three ticks later (`lastHurt` 1, window kept), and a second zombie hitting for 6 at tick 6
     deals 5 instead of vanilla's 0. Any DoT on a target largely removes its in-window protection against melee
     from mobs, pets and party members;
   - **after a bigger one it is dropped:** a spell hit for 20 inside an open window sets `lastHurt` 20, and a melee
     hit for 8 in the same window deals 0, where vanilla would deal the difference from the previous melee hit.

   Task F5 measures this with a GameTest (a melee hit after a DoT tick inside the window) and decides: keep it,
   restore `lastHurt` after each spell hit (an access transformer, [19.3](#193-mixins-and-access-transformers)), or
   another approach.
4. The reductions apply: armor (our formula), enchantments, effects. **`LivingDamageEvent.Pre`** sees the damage
   before absorption hearts take their share.
5. **`LivingDamageEvent.Post`** reports what was actually taken from health: on-hit procs, combat text payloads,
   the QA combat log; threat later (with pets).

**Cancelling `LivingIncomingDamageEvent` stops a hit cleanly since NeoForge 26.1.** `LivingEntity#hurtServer`
pushes a `DamageContainer` onto the defender's `damageContainers` stack, posts the event, and pops the container again
when the event is cancelled (checked in 26.1.2.116). NeoForge 21.1.259 returned without popping it, so on 1.21.1 every
cancel left a container on the defender; that is why this section used to forbid cancelling. Dodge can therefore
cancel the event; task F5 still keeps a GameTest that shows the stack does not grow.

All formulas live in a pure `DamageCalculator` (inputs: attacker and defender stat snapshots, spell and school, a
random source), tested by JUnit. The Python balance simulation (M3) will use the same formulas; one JSON file of
inputs and expected outputs, read by both pytest and JUnit, keeps the two implementations equal.

### 9.2 Better Combat

Taken unchanged ([ADR 0004](decisions/0004-build-rpg-systems-in-house.md)), with no compile-time dependency. Our
weapons get Better Combat weapon attribute files generated from item YAML:
`data/realmforge/weapon_attributes/<item>.json` with `"parent": "bettercombat:<preset>"`.

Casting and Better Combat ([ADR 0020](decisions/0020-cast-animations-and-melee-while-casting.md), spike 4):

- **Animations:** both use Player Animation Library (PAL), a required client-side dependency of the core mod. Our
  cast layer sits
  at priority 3000, above Better Combat's attack layer (2000) and idle poses (1 to 4), and animates only the body
  parts a cast needs ([11.6](#116-client-side)).
- **No melee while casting:** during a cast-time or channelled spell the server cancels `AttackEntityEvent` (every
  melee hit, Better Combat's included, goes through `Player#attack`) and sets Better Combat's entity tag
  `bettercombat_disabled`, which stops its swings and cancels the one in progress; the client cancels the attack
  key. The `combat` attachment records that a cast set the tag, so an operator's own tag survives and a login
  removes one a crash left behind. A cast cancels a swing; an attack does not interrupt a cast.

## 10. Character: races, classes, leveling

### 10.1 Races and classes

- **Creation:** while a player's `character` attachment has no race, the client shows the character creation screen,
  which cannot be closed. `CreateCharacterPayload(race, class)` → the server checks the combination and that no
  character exists yet → sets the attachment, gives the starting kit, recomputes stats.
- **The server enforces creation too:** a client that skips the screen gains nothing. Until the character exists,
  the server refuses the player's gameplay payloads (casting, spell bar, talents) and awards no character XP, and
  every system that reads race or class handles a missing character instead of assuming one.
- **A race** gives base modifiers, innate spells, optionally a size (`generic.scale`), and the allowed classes.
- **A class** gives its resource type, stat growth per level, spells by level, talent trees and starting kit.
- **Dual class:** the data model keeps a list of classes; the rules come in M3.
- **Changing race or class:** a QA command in M1, an in-game way later.

### 10.2 Leveling

- **Character XP is separate from vanilla experience** (proposal, see the roadmap's open decisions). Vanilla XP
  stays the currency for enchanting and anvils until the enchanting overhaul; character XP is never spent.
- **Level is derived:** `level = min(cap, level_for(total_xp))`, with the XP table from `realmforge:curve`, and
  stored in the `realmforge:level` attachment that every system reads. XP above the cap keeps accumulating (the
  Otherworld pattern in the [research notes](research-notes.md#inputs)), so raising the cap levels the player up
  at once.
- **Level cap:** the highest cap among completed chapters (a base cap before any), cached in the `character`
  attachment and recomputed on `AdvancementEvent.AdvancementEarnEvent` and on login ([16](#16-progression-and-quests)).
- **XP sources** (amounts are data): kills, where `character` listens to `LivingDeathEvent` and scales the XP by the
  victim's level relative to the player's (mobs far below give nothing); quests; dungeon completion and boss kills;
  later discoveries and professions.
- **Level-up:** recompute stats, grant talent points and teach class spells through their features' APIs, toast and
  sound, fire the `realmforge:level_reached` trigger for advancements.

## 11. Spells and status effects

### 11.1 Model

A `realmforge:spell` entry ([sketch in 6.4](#64-example-the-life-of-a-spell)) has a school, targeting, cast type
(instant, cast time, channel), cost, cooldown, range, typed effects, a maximum level with per-level scaling (the
vision prefers spell levels or power over rarities), icon, animation and sound keys, and an `npc_only` flag for
mob and boss spells.

A player knows a spell through their class (by level), race, talents (`grant_spell`) or items (an item component
lists spells granted while equipped or on use): the vision's "some from the race or class, some from items".

### 11.2 Casting (server-authoritative)

1. The client sends `CastSpellPayload(spell, target entity or position)`.
2. `SpellCaster` checks: the spell is known, off cooldown and global cooldown, the resource suffices, the target is
   in range and line of sight, and the caster is not silenced, stunned or already casting.
3. Instant spells run now. A cast-time or channelled spell stores the current cast in the `combat` attachment and
   sends `CastStartedPayload` to the caster and tracking players (cast bar, animation). A player who starts
   tracking the caster later (walking up to a boss, reconnecting mid-cast) gets the current cast from a
   `PlayerEvent.StartTracking` listener, as a `CastStartedPayload` with the original start time, so the telegraph
   still shows. Moving or an interrupt cancels the cast, as the spell's rules say.
4. When a cast-time spell completes, the server checks the resource and the target again, because both can change
   during the cast: rage decays, effects drain mana, and the target can die, leave the caster's dimension, move out
   of range or go behind cover. If either check fails, the cast fails like an interrupt; a spell that should lock
   on says so in its data. Otherwise the cost and cooldowns apply first, then every effect runs with an
   `EffectContext` (caster, targets, position, spell level, stat snapshot, random source).
5. A channel pays its cost and starts its cooldown when it begins, then runs its effects at the tick interval its
   data sets for as long as it lasts, checking the target before every tick as in step 4. Moving or an interrupt
   ends it early, and the ticks already run stay applied.
6. Mobs and bosses use the same `SpellCaster`, without a payload. Their cooldowns and current cast live in their
   own `combat` attachment ([7](#7-player-state)), and their cast bars, the telegraphs players react to, reach
   clients only through the cast payloads.

### 11.3 Spell entities

Registries freeze at startup, so variety lives in data: one generic projectile entity type and one generic area
entity type (persistent ground zones), configured by the spell (speed, size, visuals, effects on hit). A new entity
type only for genuinely new behaviour.

### 11.4 Status effects

- `MobEffect` is a static registry, so our status effects are YAML in `content/effects/` turned into generated
  registrations: attribute modifiers through `MobEffect#addAttributeModifier`, periodic damage or healing through
  a few behaviour classes in `effect/` (`applyEffectTick`, `shouldApplyEffectTickThisTick`). Vanilla effects can be
  referenced directly.
- Effects applied by spells remember their source as plain ids (the caster's UUID, the spell's id), because
  `effect` sits below `spell`; dispels and talent interactions use them. They also remember whether a proc
  applied them, and the damage or healing of their ticks carries that proc origin ([12](#12-talents)).
- Guardrail: only a few bosses may apply blindness or darkness (validation counts them).

### 11.5 Resources, cooldowns, modifiers

- Resource kinds are Java types (`realmforge:resource_type`: mana regenerates, rage is gained in combat and
  decays); a class picks one and sets its numbers in data.
- Spell modifiers from talents (later glyphs and gear) are collected into a cached per-player view, invalidated
  when talents or gear change, and applied when a cast starts: cast time, cost, cooldown, damage, extra effects.

### 11.6 Client side

The spell bar, cast bar and cooldown sweep are HUD layers; key mappings follow a default scheme decided in M1
(proposal: a separate spell bar with its own keys, leaving the item hotbar alone); tooltips are built from data
with live numbers; animations and particles start on `CastStartedPayload`. Cast animations are Emotecraft JSON
files in `assets/realmforge/player_animations/`, played by `client/animation` on the caster's PAL layer
for the caster and every tracking player, with the third-person model's arms in first person
([ADR 0020](decisions/0020-cast-animations-and-melee-while-casting.md)). The arms aim where the player looks, since
the model's arms hang on a body that never pitches and trails the head by up to 50 degrees: in first person they
turn rigidly with the camera about the eye (rotation and shoulder pivots), so the hands keep their place on screen;
seen from outside only their rotation follows the head, like vanilla's bow pose. The QA mod's test cast does this in
`QaCastAimAnimation` (RLM-108), which C6 moves into `client/animation`.

## 12. Talents

- **Model:** one `realmforge:talent_tree` entry per tree with its grid size and nodes: id, column, row, icon, max
  rank, prerequisites (another node at some rank; points spent in the tree for each tier) and typed effects per
  rank.
- **Effect types:** attribute modifier; spell modifier (for one spell or a whole school: cast time, cost, cooldown,
  damage, added effects); grant spell; proc (on hit, crit, kill or being hit: cast a spell or apply an effect).
  Big-impact talents are combinations of these.
- **Procs never trigger procs:** a proc's spell or effect runs with a proc origin in its `EffectContext`, our
  damage source carries it, and the proc engine ignores hits that have one. Otherwise a guaranteed on-hit proc
  that deals damage would trigger itself until the server's stack overflows. A status effect applied by a proc
  keeps the origin for all its later ticks ([11.4](#114-status-effects)), so a proc's damage over time cannot
  start a chain either.
- **Learning:** on level-up, `character` grants points through `talent`'s API, as its class data says (for example
  one per level from some level on). `LearnTalentPayload(tree, node)` → pure `TalentRules` check that the tree is
  one of the player's class trees (a provider that `character` implements), points, prerequisites, tier and rank →
  the `talents` attachment → stats and spell modifiers are recomputed. Respec is a QA command first, an in-game way
  later.
- **Validation:** Python checks every tree (no cycles, every node reachable, the tier math works out); JUnit
  property tests check that no sequence of payloads reaches an invalid allocation.
- **Glyphs** (M3) reuse the same effect types, with glyph items as the source.
- **Screen:** a WoW-style grid, drawn from a pure `TalentTreeView`.

## 13. Items, gear and loot

- **M1:** fixed-stat gear from YAML: generated items with vanilla attribute modifiers (`Item.Properties#attributes`,
  per-slot ids from [8.2](#82-where-modifiers-come-from)), Better Combat presets for weapons, and vanilla's rarity
  (`Item.Properties#rarity`).
- **Rarity:** the vision wants five tiers, common to legendary; vanilla's `Rarity` has four (common to epic). M2
  picks one of two ways:
  - extend vanilla `Rarity` with a fifth constant through NeoForge's enum extension (in NeoForge 26.1 `Rarity` is
    an `IExtensibleEnum`): name colours then work wherever vanilla styles item names (tooltips, chat) and likely in
    mods that reuse that styling (*verify* Jade and JEI), but vanilla raises the shown rarity of enchanted common,
    uncommon and rare items (`ItemStack#getRarity`);
  - our own data component: full control and WoW-style colours, but we style item names ourselves and other mods
    do not see the tier.
- **Affixes, gems, reforging (M2):** rolled affixes are a data component on the stack (`realmforge:affixes`: affix
  key plus rolled values). `ItemAttributeModifierEvent` turns them into attribute modifiers, so vanilla applies them
  on equip and lists them in tooltips; special affixes are procs. Sockets and gems are another component;
  reforging is a station with a menu.
- **Loot:** loot tables generated from YAML, with our own loot functions (`Registries.LOOT_FUNCTION_TYPE`, for
  example "roll affixes for the mob's level"), so other mods and data packs see ordinary loot tables. "No trash loot"
  is a validation rule: a denylist plus a minimum value per tier.
- **Rewards:** quest choice rewards are pre-rolled stacks written into the quest files (ADR 0006). RNG item chests
  (M2) are items that roll a loot table when opened.
- **Curios (M2)** supplies curio slots ([ADR 0004](decisions/0004-build-rpg-systems-in-house.md)); *verify* its
  26.1.2 API when adding it.

## 14. Mobs and bosses

### 14.1 Mob levels

- `realmforge:dimension_rules` (owned by `world`) gives every dimension a level rule: by distance from the
  dimension's origin and by depth, with some jitter, clamped to a range.
- In `FinalizeSpawnEvent`, `mob` computes the level from the rules for the mob's dimension, writes it into
  `realmforge:level` and applies scaling modifiers (health, damage, armor, from curves). Vanilla hostile mobs get
  levels the same way as ours. Bosses take the fixed level from their definition.
- **Health refill on join:** the constructor filled health from the unscaled maximum, and raising the maximum does
  not refill it. NeoForge posts `FinalizeSpawnEvent` before vanilla's `Mob#finalizeSpawn`, which can still raise
  the maximum (the leader zombie bonus), so `mob` sets health to the maximum only when the mob joins the level
  (`EntityJoinLevelEvent`), and only for a mob leveled in this spawn: never for one loaded from disk
  (`loadedFromDisk`) or moving between dimensions.
- Some spawns skip `finalizeSpawn` and with it this event: slime splits (`Slime#remove`), silverfish from infested
  blocks, `GameTestHelper#spawn`. Split children copy the parent's level and scaling in `MobSplitEvent`
  (`getParent`, `getChildren`). Any other mob that joins without a level (checked with `hasData`) goes through an
  `EntityJoinLevelEvent` fallback that runs the same leveling as `FinalizeSpawnEvent`: the level from the rules,
  the scaling modifiers, then the health refill. So the default level never passes for an assigned one, and no mob
  carries a level without its scaling.
- **Conversions** (`Mob#convertTo`: a zombie drowns, a skeleton turns into a stray, a piglin or hoglin zombifies)
  add the new mob with `addFreshEntity`, so the fallback above levels and scales it from the rules, and players
  nearby already receive that level when they start tracking it. Only then does the caller post
  `LivingConversionEvent.Post`, where NeoForge (`AttachmentInternals.onLivingConvert`) copies the old mob's
  copy-on-death attachments onto the new one, `realmforge:level` included ([7](#7-player-state)), without syncing
  them (`IEntityExtension#copyAttachmentsFrom`). Left alone, XP, loot and armor would use the old mob's level while
  health and damage stay scaled for the rules' level, and clients would keep showing the rules' level. So `mob`
  listens to `LivingConversionEvent.Post` at `EventPriority.LOW`, after NeoForge's copy: a new mob of a kind that
  gets levels keeps the level it holds after the copy (the old mob's, or the fallback's when the old mob had none),
  gets its scaling reapplied from it (as for a mob loaded from disk, keeping its health fraction), and is synced
  with `syncData`; one of a kind that does not (a zombie villager cured into a villager) loses the level through
  `removeData`, which syncs. The two lightning conversions differ. `Pig#thunderHit` posts the event before it adds
  the zombified piglin, so the listener finds no level there and leaves the mob to the join fallback.
  `Villager#thunderHit` calls `finalizeSpawn` on the witch first, and NeoForge's method-redirector coremod (its
  `finalize_spawn_targets.json` lists `Villager`) turns that call into `EventHooks.finalizeMobSpawn`: so
  `FinalizeSpawnEvent` levels and scales the witch before the conversion event, the listener keeps that level
  through its normal path, and the join fallback never runs for it. W1's GameTests convert a leveled mob (a piglin
  summoned outside the Nether with `TimeInOverworld` 300 zombifies on its next tick) and check that its level and
  scaled maximum health belong together.
- **Saved mobs follow changed curves:** when a mob loads from disk (`EntityJoinLevelEvent` with `loadedFromDisk`),
  `mob` reapplies the scaling from its stored level and the current curves, replacing its
  `realmforge:mob_level/<n>` modifiers and keeping its health fraction. A release or data pack that changes a curve
  then reaches old mobs too.
- XP (`character`), loot (`loot`) and the armor formula (`combat`) read the level from `realmforge:level`. Jade
  shows it through a compat plugin (*verify* Jade's API), the boss bar for bosses.

### 14.2 Custom mobs

- Entity types are static. A small Java library of archetypes (base classes with AI goal sets, such as melee brute
  or ranged caster) plus YAML mobs that pick an archetype, attributes, spells, model and loot. A new archetype is
  Java; a new mob of an existing archetype is YAML.
- Models and animations: GeckoLib ([ADR 0004](decisions/0004-build-rpg-systems-in-house.md); *verify* its 26.1.2
  build's API and asset paths when adding it), renderers in `client/render/`.
- Minimap icons for Xaero's maps are generated files, no code ([ADR 0002](decisions/0002-hybrid-core-mod-and-modpack.md)).

### 14.3 Bosses

```yaml
# content/bosses/hollow_warden.yaml (sketch; names are examples)
id: hollow_warden
entity: realmforge:hollow_warden     # the boss's entity type
level: 10
attributes: {max_health: 400, attack_damage: 9, armor: 8}
arena: {radius: 18}
phases:
  - id: awakening
    abilities:
      - {spell: realmforge:grave_cleave, cooldown_seconds: 6, target: current_target}
      - {spell: realmforge:bone_spikes, cooldown_seconds: 12, target: random_player}
  - id: summoning
    enter_when: {type: health_below, percent: 70}
    on_enter: [{type: realmforge:apply_effect, effect: realmforge:bone_armor, target: self}]
    abilities: [...]
  - id: fury
    enter_when: {type: health_below, percent: 30}
    abilities: [...]
loot: realmforge:boss/hollow_warden
```

- **Phase machine:** pure Java (`BossPhaseMachine`: health fraction, time in phase and living adds go in; the current
  phase and transitions come out), JUnit-tested against every boss definition.
- **Boss entity:** one base class runs the abilities through the spell engine, so every boss attack is a readable,
  telegraphed cast that player tools can answer. Ground movement only: no flying bosses (a validation rule). Pulled
  out of its arena, the boss resets.
- **Boss bar:** a `ServerBossEvent` whose title carries the phase name; `ServerBossEvent#setName` sends title changes
  to the players, so M1 needs no payload and no custom drawing for it.
- **Level and lore** come from the same data as the quest texts.
- **GameTests:** spawn the boss, set its health, assert the phase changes and their effects.

## 15. Dimensions and dungeons

### 15.1 Dimensions

- Worldgen JSON in `content/worldgen/<registry>/<id>.json`
  ([ADR 0012](decisions/0012-procedural-dungeons-and-dimensions.md)), validated and copied unchanged to
  `data/realmforge/dimension/`, `dimension_type/` and `worldgen/<registry>/`. Python checks the location, the id,
  that the file is a JSON object, that a dimension's `type` names a known dimension type, and that no file clashes
  with the core mod's own resources; the game decodes the rest when a world loads.
- Gameplay rules in `realmforge:dimension_rules`: mob levels, the access requirement (for example a chapter), flags.
- Access by portal or item, locked behind the requirement (the vision's "locked dimensions").
- A technical dimension `realmforge:instances` (void, no natural spawning, fixed time) hosts dungeon instances. The
  code depends on it, so it lives in the core mod's resources (`data/realmforge/dimension/instances.json` and
  `dimension_type/instances.json`), not in `content/worldgen/`
  ([ADR 0019](decisions/0019-dungeon-instances-in-plots.md)).
- **No experimental-settings warning.** Vanilla calls every level stem other than the overworld, nether and end
  experimental (`WorldDimensions#checkStability` and `bake`), so each of our dimensions would make
  `WorldOpenFlows.confirmWorldCreation` show "These settings are using experimental features" for every new world,
  and a world created before Realmforge was added would ask for a backup once. The client-only pack mod Yeetus
  Experimentus ([ADR 0013](decisions/0013-modpack-composition.md)) skips both screens and the warning for
  experimental data packs in the create screen; nothing changes on servers. The dev client loads the same build
  from `qa-mod/build.gradle`. We add no mixin of our own for it ([19.3](#193-mixins-and-access-transformers)).
- The Nether and End are removed in M4 ([ADR 0025](decisions/0025-remove-the-nether-and-end.md)); M1 does not touch
  them.

### 15.2 Dungeon types

```yaml
# content/dungeons/sunken_crypt.yaml (sketch; names are examples)
id: sunken_crypt
placement: instance                  # world_structure later (M4)
theme: realmforge:crypt              # block palette: a processor list (see below)
rooms: realmforge:crypt              # room pool
layout:
  main_path_rooms: [6, 9]
  branches: {count: [1, 3], rooms: [1, 2]}
  required_rooms: [treasure, trap]
ending: {type: realmforge:boss_lair, boss: realmforge:hollow_warden}   # or realmforge:treasury, ...
encounters: {budget_per_room: [2, 4], mobs: realmforge:crypt}
loot: {chests: [2, 4], table: realmforge:dungeon/sunken_crypt}
level: [8, 12]
```

The ending is a rule of the type, so "not every dungeon ends with a boss" is just another ending type.

A theme is a vanilla processor list (`worldgen/processor_list`), so it needs no registry of our own. Its rules swap
the blocks rooms are built from for the dungeon's look; the builder applies it when placing rooms, and room
validation reads the themes' rules to check that a room uses only blocks they can swap. A theme that swaps only
blocks without states can be hand-written worldgen JSON (`content/worldgen/processor_list/crypt.json`, validated and
copied like the other worldgen JSON, [6.2](#62-what-each-content-type-becomes)); stairs, slabs and other blocks with
states need the generated rules below.

**Pitfall: block states.** A vanilla `rule` processor writes its rule's fixed `output_state` (`RuleProcessor` places
`ProcessorRule#getOutputState`), so a rule that swaps one kind of stairs for the theme's stairs loses their facing,
half and shape, and a slab loses its type. Two ways keep them:

- **One rule per block state** (a `blockstate_match` input and the same properties on the output), written by
  `rf gen` from a theme's table of roles and blocks, so nobody writes hundreds of rules by hand. Vanilla only, and
  every id stays inside the ID universe. This is the default: W4 adds the generator, whose output is then the
  theme's processor list.
- **Lithostitched's `lithostitched:block_swap`** (in the pack), which copies the properties
  (`Block#withPropertiesOf`, checked in its 1.8.0 build for 26.1). It ties the themes to Lithostitched and names an
  id outside the ID universe until the full-pack registry dump exists, so it is only the fallback.

### 15.3 Generation: pure first, then Minecraft

1. **Plan** (pure Java): seed + rules → room graph (start, main path, branches, special rooms, ending).
2. **Layout** (pure Java): graph → room boxes placed so their connectors meet; backtracking, no overlaps.
3. **Build** (Minecraft): place the NBT templates (`StructureTemplate#placeInWorld`) with the theme's processors;
   read data markers (structure blocks in data mode, as vanilla does) for spawns, chests and traps; spread the work
   over ticks within a time budget.
4. **Populate:** encounters and loot from the budgets.

Steps 1 and 2 are deterministic; JUnit fuzzes them over thousands of seeds (constraints hold, no overlaps, every
room reachable). A QA command renders a layout preview for review.

### 15.4 Rooms

Rooms are generated or imported; nobody has to build one by hand
([ADR 0028](decisions/0028-dungeon-rooms-generated-or-imported.md)).

- **Format:** structure NBT in `content/rooms/` with a small YAML sidecar (tags, pools, weight). Connectors are
  vanilla jigsaw blocks, so the same rooms work in world structures later. Rooms are the second exception to
  YAML-only content, after worldgen JSON (ADR 0014 amends
  [ADR 0005](decisions/0005-content-pipeline-yaml-single-source-of-truth.md)).
- **Generated rooms** (A4d): room specs in YAML (kind, a footprint up to 16×16, height, connector sides, features,
  a seed, blocks named by neutral roles that themes swap) become structure NBT through the room generator in
  `rf gen`, with style rules for walls, floors, stairs and light, and jigsaws and markers from the connectors. The
  NBT is generated output, never edited by hand.
- **Imported rooms** (A4b, A4c): MIT, CC0 and LGPL-3.0 rooms from other mods, taken in by `rf rooms import`,
  remapped to vanilla blocks or neutral roles and retagged into our slot types. Each source has its own folder,
  `content/rooms/third_party/<source>/`, with its `LICENSE` and a `NOTICE`. The folder is a license boundary, not a
  pool, so the sidecars name the pools. The jar keeps the folder (LGPL-3.0 rooms stay apart from our assets) and
  carries the notices under `META-INF/third_party/<source>/`. Vanilla templates are referenced by id, never copied.
- **Hand-built rooms** stay possible: built with structure blocks and saved as NBT with a sidecar like any other.
- **Review:** the room gallery (A4e) renders rooms headless; Matěj keeps or rejects them in batches, and a kept
  generated candidate's seed is frozen.
- **Validation:** size limits, connectors on the room's boundary, known markers, only blocks the themes can swap
  and only ids inside the ID universe (an imported room's own blocks are remapped). Generated rooms are also
  checked for reachability between their connectors and for headroom.

### 15.5 Instances

Spike 3 built this and measured it; the numbers and the reasons are in
[ADR 0019](decisions/0019-dungeon-instances-in-plots.md). Code: `dungeon/DungeonFeature`, `dungeon/DungeonAttachments`
and `dungeon/instance/`.

- `InstanceManager` keeps a `SavedData` (`realmforge_instances`) in the instance dimension
  (`DimensionDataStorage#computeIfAbsent`): a grid of plots 1,024 blocks apart (`PlotGrid`; Compact Machines uses the
  same spacing), filled row by row, each plot's rooms within 256 blocks of its origin at y 64. Each plot has its
  state (building, ready, active, closing, free; W4 adds completed), layout (W4: dungeon type and seed), the boxes of
  its rooms and of blocks still to clear, its work progress, its party and the chunks it holds. The party is a list
  of players: solo in M1 (decision D8), parties later (RLM-68) without a data migration. Each player's run and
  return point live in the player's `realmforge:instance` attachment ([7](#7-player-state)); W4 adds the previous
  game mode.
- **Entry:** an entrance in the world (W4: a temporary portal opened with a dungeon key, D8) → allocate a plot
  (`InstanceManager#open`, lowest free plot first) → build over ticks → teleport the online party members
  (`ServerPlayer#teleportTo(ServerLevel, x, y, z, yaw, pitch)`). Until W4 the QA mod's `/qa instance enter` opens a
  plot with three test rooms.
- **Building over ticks:** placing a room and clearing a box are cut into units of one chunk column each
  (`PlotWork`; placing uses `StructurePlaceSettings#setBoundingBox`, known shapes and update flag 2 like structure
  blocks). The builder runs units for up to 5 ms per tick, at least one, in `LevelTickEvent.Post` of the instance
  level, so its time shows in the server's tick time. The unit list is a pure function of the saved plot, so a
  restart resumes at the saved step, and a unit whose chunk is still loading waits for a later tick instead of
  loading it on the main thread.
- **Chunk loading:** from opening until it is free, every chunk a plot touches is held by the NeoForge
  `TicketController` `realmforge:instance_plots` (registered in `RegisterTicketControllersEvent`, owner: the plot's
  origin, non-ticking). Its tickets survive restarts; its validation callback drops the tickets of plots that are
  free or unknown when the level loads. `TicketController#forceChunk` loads the chunk synchronously, which makes
  opening a plot block the main thread for 2-80 ms, and for 0.3-1 s the first time a plot cell is used (vanilla
  generates and saves the neighbours of a forced chunk too, 3-4 MB per cell); W4 keeps that off the entry path.
- **Rules inside: nobody edits the rooms.** Survival players are switched to Adventure mode while inside (creative
  operators keep theirs); the previous game mode is saved in an attachment and restored when they leave (also on
  the next login after a crash).
  Vanilla then refuses every player edit, including buckets, flint and steel and tool edits such as stripping or
  tilling (`Player#mayUseItemAt`, `ItemStack#useOn`), and the client predicts the refusal, so there are no ghost
  blocks; doors, levers, chests and combat still work. Edits by anything else are cancelled inside the instance
  dimension: entities placing blocks (`BlockEvent.EntityPlaceEvent`), fluids creating blocks
  (`BlockEvent.FluidPlaceBlockEvent`), trampled farmland (`BlockEvent.FarmlandTrampleEvent`), mob griefing
  (`EntityMobGriefingEvent`) and explosions (the block list of `ExplosionEvent.Detonate`). Adventure mode still
  lets an item with the `can_break` or `can_place_on` component (`DataComponents.CAN_BREAK`, `CAN_PLACE_ON`) edit
  matching blocks, and commands, data packs or other mods can hand out such items, so block breaks
  (`BlockEvent.BreakEvent`) and placements (`BlockEvent.EntityPlaceEvent`) by players who are not in creative mode
  are cancelled there too. Buckets get past both events: `BucketItem#use` (`MobBucketItem` included) checks
  `Level#mayInteract` (spawn protection, which covers only the Overworld, and the world border) and
  `Player#mayUseItemAt`, which a matching `can_place_on` passes, then places or picks up the fluid with
  `Level#setBlock` and posts no block event, and `CommonHooks.onPlaceItemIntoWorld` captures no block snapshots for
  a `BucketItem`. So `PlayerInteractEvent.RightClickItem` is cancelled for a `BucketItem` used by a player who is
  not in creative mode inside the dimension. `BucketItem` has no `useOn`, so a bucket reaches `use` only through
  that event, and `RightClickBlock` stays alone: doors, levers and chests keep working with a bucket in hand. The
  event fires on the client too (`MultiPlayerGameMode#useItem`), so the same listener stops the client's
  prediction and no ghost fluid appears. W4's GameTests use a bucket (a plain one, and water, lava and empty
  buckets with `can_place_on` matching a room block, placing and picking up), flint and steel, an axe, a hoe and a
  tool with `can_break`, and place and break blocks inside an instance, and expect the room unchanged.
- **Also inside:** respawn at the entrance, no natural spawns, leave through the exit portal or a command.
- **Leaving and disconnects:** leaving by command or exit, respawning outside the dimension
  (`PlayerEvent.PlayerRespawnEvent`) or being teleported out (`PlayerEvent.PlayerChangedDimensionEvent`) leaves the
  run; a run whose party is empty is released, built or still building. A disconnect keeps the run. On login
  (`PlayerEvent.PlayerLoggedInEvent`) a player inside the dimension stays if their run is still playable and is sent
  back to their return point otherwise; a player outside it drops out of a run they never entered (they disconnected
  while it was being built). W4 adds a timeout for party members who stay offline.
- **Cleanup and plot reuse: clear, then reuse** (spike 3). When a run is released, its blocks are cleared over the
  next ticks (containers emptied first so nothing drops, entities other than players removed), its chunks are
  released and the plot becomes free for the next run; nobody waits for it. Overwriting on reuse doubled the next
  run's build time, and never reusing plots grew the world by 3 MB per run; `/qa instance cleanup` still switches to
  them for measurements.
- No dimensions are created at runtime (ADR 0012).

### 15.6 World structures (M4)

The same dungeon type format with `placement: world_structure`: the pipeline emits jigsaw `worldgen/structure`,
`structure_set`, `template_pool` and `processor_list` files. Vanilla 26.1.2 limits jigsaw to depth 20
(`JigsawStructure.MAX_DEPTH`) and 128 blocks from the start (`MAX_TOTAL_STRUCTURE_RANGE`); Lithostitched lifts the
depth limit.

## 16. Progression and quests

- `realmforge:chapter`: order and the level cap after completion. Content that a chapter unlocks (a dimension, a
  dungeon) names the chapter through the `chapter_completed` requirement.
- Quests stay in FTB Quests ([ADR 0006](decisions/0006-quests-ftb-quests-generated-from-data.md)) and connect only
  through its standard reward and task types:
  - **Quests → our mod:** the last quest of a chapter has an `AdvancementReward` that grants
    `realmforge:chapter/<id>` (an advancement whose only criterion is `minecraft:impossible`, so nothing else grants
    it). Our `AdvancementEarnEvent` listener raises the level cap. Character XP rewards use a `CommandReward` that
    runs our operator command `/realmforge xp`, so the generator writes `permission_level: 2` (FTB Quests' default
    is 0).
  - **Every team member gets the chapter advancement** (checked in the FTB Quests 2101.1.36 jar, RLM-73). Quest
    progress belongs to the FTB Teams team, while advancements and level caps are per player. FTB Quests records a
    claimed reward under the claiming player, or under `Util.NIL_UUID` for a team reward (`QuestKey#forReward`),
    so a team reward is claimed once, by the player who completes the quest, and the other members never get the
    advancement. The chapter reward is therefore a per-player reward that claims itself: the generator writes
    `team_reward: false` and `auto: "enabled"` (any value but `disabled`, and never `default`, which defers to the
    quest file) on the `AdvancementReward`, and the quest file never sets `suppress_all_autoclaiming`. FTB Quests
    then claims it for every online member when the quest completes (`TeamData#checkAutoCompletion`) and for each
    other member at their next login (`ServerQuestFile#playerLoggedIn` runs the same check over every quest), a
    player who joins the team later included; `TeamData#claimReward` grants a reward once per player
    (`markRewardAsClaimed`). No FTB Quests API is needed, so decision 7 of
    [ADR 0014](decisions/0014-core-mod-architecture.md) stands. Q1's pytest checks that every generated chapter
    reward is per player and auto-claimed.
  - **Our mod → quests:** our criteria triggers (`Registries.TRIGGER_TYPE`) complete advancements generated from
    content: `realmforge:level_reached`, `boss_defeated`, `dungeon_completed`, `spell_cast`, `talent_learned`.
    Custom statistics (`Registries.CUSTOM_STAT`) count repeatable things such as dungeons cleared. FTB Quests'
    `AdvancementTask` and `StatTask` read them and are retroactive.
- **Pipeline:** `content/chapters/` and `content/quests/` → advancements in the jar, FTB Quests SNBT in
  `pack/config/ftbquests/quests/` (ids derived from quest keys), lang. Quest texts come from the same data (boss
  level; where, how and with what).
- Procedural contracts come later (M7), in our own `contract/` package.

## 17. Networking

### 17.1 Rules

- **One registration point, versioned by the mod version:** `network/RealmforgeNetwork` handles
  `RegisterPayloadHandlersEvent` with `event.registrar(version)`, where `version` is the mod's own version
  (`ModContainer#getModInfo().getVersion()`). The registrar's version check only covers our own payloads, but our
  codecs also travel inside NeoForge's and vanilla's packets (synced attachments, synced datapack registries).
  Tying the check to the mod version refuses any client whose Realmforge version differs from the server's when it
  connects, whatever changed on the wire, instead of letting it desync. Nobody has to remember to bump a number.
- **A payload** is a `record` implementing `CustomPacketPayload` with a `TYPE` and a `STREAM_CODEC`
  (`StreamCodec.composite(...)`), kept in its feature's `net/` package.
- **Client → server payloads are intents.** The server validates every field (the sender may act on it, range,
  cooldown, resource, rate limit) and never trusts numbers from the client.
- **Handlers run on the main thread** (the registrar's default, `HandlerThread.MAIN`) and hand over to the feature's
  system at once.
- **Client-bound handlers live in `client/network/`**
  ([ADR 0027](decisions/0027-client-bound-payload-handlers-through-the-client-event.md)). `RealmforgeNetwork`
  registers a client-bound payload without a handler, `registrar.playToClient(TYPE, STREAM_CODEC)`, and
  `client/network/ClientPayloadHandlers` registers its handler in NeoForge's client-only
  `RegisterClientPayloadHandlersEvent` (mod bus; `RealmforgeClient` adds the listener). A dedicated server loads only
  the payload's type and codec, and no common class refers to a client handler. NeoForge stops the client at startup
  when a client-bound payload has no client handler ("Some clientbound payloads are missing client-side handlers"),
  so a forgotten handler shows on the first launch, not on the first packet. A payload both sides handle registers
  its server handler in common code (`playBidirectional(type, codec, serverHandler)`) and its client handler through
  the event. The QA mod's test cast takes the same route (`castanim/QaCastAnimation`,
  `client/castanim/QaCastAnimationClient`).

### 17.2 What syncs how

| Data | Mechanism |
| --- | --- |
| datapack registries | NeoForge, at login, through each registry's network codec |
| character, spellbook, talents | synced attachments, sent on every `setData` |
| combat state | attachment synced explicitly and throttled |
| entity level | synced attachment, to the player itself and everyone tracking the entity |
| boss phase | the boss bar's title (`ServerBossEvent#setName`) |
| events: cast started or ended, combat text, dungeon status | payloads, for example `PacketDistributor.sendToPlayersTrackingEntityAndSelf` |

Cast bars and cooldowns are drawn from a start time and a duration, so they need no per-tick packets.

### 17.3 First payloads (M1)

- Client → server: `CreateCharacter`, `CastSpell`, `CancelCast`, `SetSpellBarSlot`, `LearnTalent`,
  `ToggleDarkVision` (exists: `world/net/ToggleDarkVisionPayload`, sent by `client/input/DarkVisionKey`).
- Server → client: `CastStarted`, `CastEnded`, `CombatText`, `DungeonStatus`.

## 18. Client code: rendering, GUI, input

- **Entry point:** `client/RealmforgeClient`, annotated `@Mod(value = Realmforge.MOD_ID, dist = Dist.CLIENT)`, which
  FML constructs only on the client. It registers HUD layers (`RegisterGuiLayersEvent`: spell bar, cast bar,
  resource bar, combat text), key mappings (`RegisterKeyMappingsEvent`), entity renderers
  (`EntityRenderersEvent.RegisterRenderers`), particles (`RegisterParticleProvidersEvent`), menu screens
  (`RegisterMenuScreensEvent`), tooltip components (`RegisterClientTooltipComponentFactoriesEvent`) and the handlers
  of client-bound payloads (`RegisterClientPayloadHandlersEvent`, [17.1](#171-rules)).
- **Layout:** `client/hud`, `client/screen`, `client/input`, `client/render`, `client/tooltip`, `client/network`,
  `client/animation` (the PAL cast layer), and `client/compat/<mod>` for the client parts of
  integrations (JEI plugins, Jade tooltips).
- **The rule:** no class outside `client/` imports `net.minecraft.client.*`, `com.mojang.blaze3d.*`,
  `net.neoforged.neoforge.client.*` or a library's rendering classes. An automated import check enforces it
  (`tools/tests/test_client_isolation.py`, run by `uv run pytest`): it scans the main and generated sources of the
  core mod and of the QA mod (whose own `qa/client/` package is exempt) for those packages in imports and fully
  qualified names. A library's rendering packages join its list with the library (so far Player Animation
  Library's `com.zigythebird.playeranim` and `com.zigythebird.playeranimcore`,
  [ADR 0020](decisions/0020-cast-animations-and-melee-while-casting.md)). The GameTest
  server in CI runs as a dedicated server and is a second line of defence, but only for code the tests run, and
  NeoForge's own client classes are mostly not marked client-only, so only the import check catches those.
- **Thin screens:** screens and HUD layers draw from view models computed in common code (`CharacterSheetView`,
  `TalentTreeView`), so their logic is testable without rendering.
- **Little rendering code:** GeckoLib for animated entities, vanilla widgets for UI, no rendering mixins unless
  unavoidable, no shaders of our own (Iris and Complementary). Porting costs the most here
  ([ADR 0003](decisions/0003-platform-minecraft-1-21-1-neoforge.md)), so it stays small.
- **Client config** (`ModConfigSpec`, client type) holds UI preferences only.

## 19. Cross-cutting rules

### 19.1 World defaults

Implemented in the `world` feature (R1): `WorldDefaults`, `GriefingProtection`, `DarkVision`.

- When a world is created, set the vision's gamerules once: `keepInventory` and `doImmediateRespawn` on;
  `blockExplosionDropDecay`, `mobExplosionDropDecay` and `tntExplosionDropDecay` off. The signal is
  `LevelEvent.CreateSpawnPosition` (listened to, never cancelled, since cancelling skips vanilla's spawn search; the
  listener also receives it when another mod cancelled it): the server posts it only while it sets up a world that
  is not yet initialized. A flag in our own `SavedData` could not tell a new world from an existing one that
  Realmforge is added to. Players may change the gamerules afterwards, and worlds that existed before Realmforge
  keep theirs. `mobGriefing` stays on: the next point replaces it.
- Destructive mobs (decision D6, RLM-56): targeted protection instead of `mobGriefing=false`, which the
  [research notes](research-notes.md#default-gamerules) advise against because it also stops villagers farming,
  piglins bartering and mobs picking up items. Mobs never damage blocks; players keep their own destruction (TNT,
  beds, respawn anchors). Four hooks:
  - **Explosions:** `ExplosionEvent.Detonate` clears the block list of every explosion a mob caused, that is whose
    `Explosion#getIndirectSourceEntity` is a living entity other than a player: a creeper, the ghast or wither
    that owns a projectile, the mob that lit a TNT. A ghast fireball or wither skull whose shooter died or unloaded
    in flight has no owner left (`Projectile#getOwner`) and still counts: only those mobs fire them, and a player
    who deflects one becomes its owner. Fire lands only where blocks were destroyed, so such an
    explosion lights none either. It also takes the objects a player placed off the entity list (item frames,
    paintings and leash knots, armor stands, boats and minecarts: `GriefingProtection#isPlacedObject`), so they
    survive with their contents; players and mobs are still hurt and pushed. TNT lit by redstone or a player, beds
    and respawn anchors keep breaking blocks.
  - **Per mob type:** `EntityMobGriefingEvent` names the entity but not the action, and every griefing check posts
    it, so denying it for a mob denies all of that mob's checks. It is denied only for mobs whose every check
    changes blocks (`GriefingProtection#TERRAIN_GRIEFERS`): endermen, ravagers, silverfish, the wither (also its
    wither roses), blazes (their fireballs' fire) and rabbits (carrot crops).
  - **Breaking blocks with their bodies:** `LivingDestroyBlockEvent` (posted by `CommonHooks#canEntityDestroy`) is
    cancelled for every entity but a player: zombies and vindicators breaking doors, the ender dragon flying
    through blocks, the wither breaking out. This is the action-specific hook for the zombie, whose griefing check
    also gates picking up items.
  - **Farmland:** `BlockEvent.FarmlandTrampleEvent` is cancelled for every entity but a player.

  Still allowed, as mixed or harmless cases: zombies breaking turtle eggs, a skeleton's arrows breaking decorated
  pots, chorus flowers or pointed dripstone, foxes picking sweet berries, sheep eating grass, snow golems leaving
  snow. Inside instances every mob is denied, which is intended there ([15.5](#155-instances)).
- Dark Vision: a key (`client/input/DarkVisionKey`, N by default, "Toggle Dark Vision" under Realmforge in the
  controls) sends `ToggleDarkVisionPayload`; the server flips the sender's switch and confirms it on the action
  bar. The effect is vanilla night vision as an infinite, hidden effect (`MobEffectInstance.INFINITE_DURATION`, no
  particles, no icon); every player has it, switched on by default. The on/off state lives in the
  `realmforge:dark_vision` attachment (copied on death) because effects do not survive death: the effect is
  reapplied on respawn and login. Milk and a totem of undying clear every effect (`LivingEntity#removeAllEffects`;
  NeoForge 26.1 has no effect cures any more), and each removal posts the cancellable `MobEffectEvent.Remove`:
  `DarkVision` cancels it for Dark Vision while the switch is on, so only the switch removes it, `/effect clear`
  included. Switching on replaces a potion's night vision, which
  Dark Vision outlasts; switching off removes only infinite night vision, so a potion drunk while it is off stays.

### 19.2 Configuration

`ModConfigSpec` only for operator and UI settings (instance limits, timeouts, debug flags, HUD options). Gameplay
numbers are content, so they are versioned, validated and tested.

### 19.3 Mixins and access transformers

Last resort, only where no event or API exists. Each one sits in `mixin/` (or the access transformer file) with a
comment saying why, and gets a test. The QA mod has one too, for tests only: `GameTestServerMixin` gives the GameTest
world the data pack dimensions, which vanilla leaves out ([20](#20-tests-per-layer)).

### 19.4 Optional integrations

`compat/<modid>/` (client parts in `client/compat/<modid>/`) runs only when `ModList.get().isLoaded(modid)`, with
an optional dependency in `neoforge.mods.toml`. Data-only integrations (Better Combat weapon files and its disable
tag, Xaero icons, FTB Quests files) need no code.

### 19.5 Performance

- Event handlers exit early (side, entity type, dimension) before doing work.
- No per-tick loops over all entities; periodic checks are throttled.
- Derived values are cached and invalidated by events.
- Dungeon building runs under a per-tick time budget.
- The network carries changes, not full state every tick.
- A spark profile before every release; nightly profiles later ([ADR 0010](decisions/0010-testing-strategy.md)).

## 20. Tests per layer

| What | Where | M1 examples |
| --- | --- | --- |
| pure logic | JUnit in `mod/src/test` | damage formula, talent rules, dungeon planner fuzzing, boss phase machine, XP curve |
| content | pytest in `tools/tests` | schemas, references, semantic rules, guardrails, talent tree validity |
| data against codecs | GameTest server start, data errors in its log (`uv run rf gametest-log`), generated expectations | every spell decodes; every generated loot table, recipe and advancement loads; ids and key fields match |
| behaviour in a world | GameTests in `qa-mod` | firebolt damages a dummy by the expected amount; the level stops at the cap; the chapter advancement raises it; the boss enters phase 2 at 70%; an instance is built, entered and cleaned up |
| client isolation | pytest import check (`tools/tests/test_client_isolation.py`); dedicated GameTest server | - |
| whole pack | `uv run rf pack-boot`: the CI jobs `pack-boot` and `pack-boot-client` boot the pack plus our jar with NeoForge's self tests ([ADR 0018](decisions/0018-pack-boot-tests.md)) | server and client load (with and without the optional mods, when the pack has any); no unexplained ERROR line |
| balance | Python simulation (M3) | time to kill per class and level, boss phase survival, time to item |

The QA mod grows with the features: `/qa` commands (level, class, talents, gear presets, boss at phase N, enter an
instance with a given seed, reset cooldowns, god mode), a training dummy with a DPS meter, and a combat log file
that agents can read. The first are `/qa instance enter|leave|build|free|list|cleanup|bench` from spike 3 and
`/qa state show|reset [<entity>]` from F3, which prints or clears the player state attachments ([7](#7-player-state)).

The vanilla GameTest server builds its world from the flat preset with an empty dimension registry, so our data pack
dimensions (`realmforge:instances`, later the M1 dimension) exist in GameTests only through the QA mod's
`GameTestServerMixin`. A GameTest can log a fake player out and in again through the normal save and login path
(`PlayerList#remove`, `PlayerList#placeNewPlayer`) with `QaPlayers#login`, as `InstanceGameTests` does to test
reconnects; its connection goes through NeoForge's `NetworkRegistry#configureMockConnection`, so synced attachments
can be sent to it. `PlayerList#respawn(player, false, ...)` stands in for a respawn after death, which copies the
copy-on-death attachments (`PlayerStateGameTests`, and `WorldRulesGameTests` for Dark Vision), and
`PlayerList#respawn(player, true, ...)` for leaving the End. The connection never ticks the player, so a test that
needs player ticks plays them with `ServerPlayer#doTick`, the call the network handler makes once per tick
(`StatsGameTests`).

## 21. Decisions and open questions

The decisions this document makes are listed in [ADR 0014](decisions/0014-core-mod-architecture.md); changing one
of them needs a new ADR. Everything else here may change with the code, in the same PR.

The owner decisions the plan depended on ([roadmap](roadmap.md#open-decisions), D1 to D11) are all made: each
decision ticket's comments record what Matěj decided, and the task that needs one follows it. Griefing protection
(D6) is built ([19.1](#191-world-defaults)), and the Nether and End (D10) are removed in M4
([ADR 0025](decisions/0025-remove-the-nether-and-end.md)). Still open: M2 decides how rarity works
([13](#13-items-gear-and-loot)).
