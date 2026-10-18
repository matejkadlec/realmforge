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
   stable ids, so all sources stack the same way and show up in tooltips. One exception: the damage multiple a mob's
   level brings applies to each hit the mob causes, as arrows, explosions and thrown potions read no attribute
   ([14.1](#141-mob-levels)).
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
├─ combat/            spell schools, damage pipeline, crit, dodge, resistances, armor formula, pierce and shred,
│                     lifesteal, overheal, health regeneration, magic shield, combat text, damage over time
├─ effect/            behaviour classes of our status effects
├─ spell/             spells, effect types, casting, cooldowns, resources, procs, spell entities
├─ talent/            talent trees, points, spell modifiers; glyphs later
├─ item/              item behaviours, gear components; affixes and gems later
├─ loot/              loot functions and conditions, reward rolls
├─ character/         races, classes, character data, leveling, level caps, the character sheet's view
├─ world/             dimension rules and access, world defaults, Dark Vision, griefing protection
├─ mob/               mob levels and scaling, AI goal library, custom mobs
├─ boss/              boss definitions, phase machine, boss entity
├─ dungeon/           dungeon types, planner, layout, builder, instances
├─ command/           `/realmforge` operator commands (developer commands live in the QA mod)
├─ compat/            optional integrations, one subpackage per mod (client parts: client/compat/)
├─ mixin/             mixins, the last resort (19.3): the accessor that raises attribute caps
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
| Player | `PlayerEvent.PlayerLoggedInEvent`, `PlayerEvent.Clone`, `PlayerRespawnPositionEvent`, `PlayerEvent.PlayerRespawnEvent`, `PlayerEvent.PlayerChangedDimensionEvent`, `PlayerTickEvent.Post` (game) | recompute stats at the end of a new player entity's first tick and after a change; keep health across respawns ([8.2](#82-where-modifiers-come-from)); bring a character's level cap and level up to date at login ([10.2](#102-leveling)); keep or end the player's instance run, and respawn a death inside it at its entrance ([15.5](#155-instances)); reapply Dark Vision ([19.1](#191-world-defaults)) |
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
| `realmforge:class` | `content/classes/` | class: resource type, starting stats, spells by level, talent trees, starting kit | yes | M1 |
| `realmforge:spell_school` | `content/spell_schools/` | school (owned by `combat`): kind (physical or magic), colour, its damage type, and the stats that are its power and resistance (attributes from `content/stats/`, static registrations, so a new school's stats need a game restart) | yes | M1 |
| `realmforge:spell` | `content/spells/` | spell for players or NPCs: school, what it scales with, targeting, cast, cost, cooldowns, crit rules, typed effects with values per spell level (C3a-1; names and texts come with C3b) | yes | M1 |
| `realmforge:talent_tree` | `content/talents/` | WoW-style tree: nodes on a grid, ranks, prerequisites, typed effects per rank | yes | M1 |
| `realmforge:stat_rule` | `content/stat_rules/` | what a primary stat gives: per-point modifiers, milestone bonuses | yes | M1 |
| `realmforge:curve` | `content/curves/` | named table of points (level → value), linearly interpolated: XP per level, mob scaling, item budgets | yes | M1 |
| `realmforge:chapter` | `content/chapters/` | story chapter (owned by `progression`, C2): order, the level cap while it is the player's current chapter and the one once it is completed; its advancement `realmforge:chapter/<id>` marks it completed (what a chapter unlocks names the chapter through `chapter_completed`) | yes | M1 |
| `realmforge:kill_xp_multiplier` | `content/kill_xp_multipliers/` | how much kill XP the mobs of one entity type give (owned by `character`, C2), named in an `entity` field, by size for slimes and magma cubes; the XP model calls it the mob's rank. Validation allows one entry per entity type | no | M1 |
| `realmforge:dimension_rules` | `content/dimensions/` | gameplay rules of one dimension, named in a `dimension` field (for example `minecraft:overworld`): mob levels, access requirement, flags. Validation allows one entry per dimension; the server indexes the entries by dimension | no | M1 |
| `realmforge:boss` | `content/bosses/` | fixed level, attributes, phases, abilities (spells), arena, loot | no | M1 |
| `realmforge:dungeon_type` | `content/dungeons/` | rule set: the room pool it is built from, resolved into its rooms (template, size, role, weight, connectors), and its layout rules (W4a); theme, encounters, loot, level range and key join with the tasks that use them | no | M1 |
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
| `realmforge:spell_effect_type` | damage, heal (C3a-1, `spell/SpellEffectTypes`); apply_effect, remove_effect, projectile, area, teleport, dash, knockback (C3a-3); summon later | spells, so also boss abilities and procs |
| `realmforge:targeting_type` | self, enemy, ally (C3a-1, `spell/TargetingTypes`); ground point, direction (C3a-3); for NPCs current target, random player, farthest player (C3a-4), arena point (W5) | spells, boss abilities |
| `realmforge:talent_effect_type` | attribute_modifier, spell_modifier, grant_spell, proc | talents; later glyphs and set bonuses |
| `realmforge:resource_type` | mana, rage (`spell/ResourceTypes`; C1 gave them ids and names, C3a-1 their pools, C3a-4 rage's gain and decay) | classes, spell costs |
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
| `spell_schools/` (also) | `data/realmforge/damage_type/<school>.json` (death messages `death.attack.realmforge.<school>`), the tag entries in `#minecraft:bypasses_cooldown` and, for magic schools, `#minecraft:bypasses_armor` ([9.1](#91-damage-pipeline)); the school's power and resistance are stats in `content/stats/` that it names | vanilla damage types, tags |
| `dungeons/` (also) | the entry carries its pool's rooms resolved (template, size, role, weight, and the connectors read from the templates' jigsaw blocks; `generate/dungeons.py`), so the planner needs no template | the planner ([15.3](#153-generation-pure-first-then-minecraft)) |
| `loot/`, `recipes/`, tags | `data/realmforge/loot_table/`, `recipe/`, `tags/` | reloadable vanilla data |
| `chapters/`, `quests/` | `data/realmforge/advancement/`; `pack/config/ftbquests/quests/` (SNBT, stable ids per ADR 0006) | advancements; the FTB Quests book |
| `worldgen/<registry>/<id>.json` (JSON, [ADR 0012](decisions/0012-procedural-dungeons-and-dimensions.md)) | validated and copied unchanged to `data/realmforge/dimension/`, `dimension_type/`, `worldgen/<registry>/` | vanilla worldgen registries |
| `rooms/` (NBT plus a YAML sidecar; [15.4](#154-rooms)) | validated and copied, normalized, to `data/realmforge/structure/rooms/` | structure templates |
| `room_specs/`, `room_themes/` (exist, A4d; [15.4](#154-rooms)) | each kept seed of a spec built by the room generator into `data/realmforge/structure/rooms/generated/<spec>/seed_<n>.nbt`; themes name the blocks it builds with | structure templates |
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
# content/spells/firebolt.yaml (the format of C3a, schema/spell.py; projectiles, areas, status effects and movement in
# 11.3; C3b adds the name and description with their {damage} placeholders)
id: firebolt
school: realmforge:fire
scales_with: spell_power                # magic; a physical ability scales with attack_damage
targeting: {type: realmforge:enemy, range: 24}
cast: {type: cast_time, duration_seconds: 2.5}  # or {type: instant}, or a channel (11.2)
cost: {resource: realmforge:mana, amount: 20, per_level: 2}
cooldown_seconds: 0
max_level: 5
crit: {multiplier_percent: 200}         # optional: every spell crits by default, for 1 + crit damage
effects:
  - {type: realmforge:damage, amount: 12, per_level: 3}
```

1. `uv run rf validate` checks the schema, that `realmforge:fire` is a known school, that the stat it scales with
   fits the school's kind, and that its effects fit its targeting.
2. `uv run rf gen` writes `data/realmforge/realmforge/spell/firebolt.json` in game units (ticks, fractions), a
   `GeneratedSpells` key and a GameTest expectation; with C3b also the `spell.realmforge.firebolt.*` lang entries.
3. `./gradlew build` packs them into the jar, whose resources act as the mod's built-in data pack.
4. When a world loads, `Spell.CODEC` decodes the file and resolves its references; NeoForge syncs the registry to
   joining clients.
5. The player presses a spell key (C3b; until then `/qa spell cast`). The client sends `CastSpellPayload`;
   `SpellCaster` validates it and begins the cast; the caster and nearby clients get `CastStartedPayload` for the cast
   bar and animation, and when the cast completes (`spell/Casting`) the effects run and the damage goes through the
   combat pipeline.
6. A GameTest compares the decoded spell with the expectation (`ContentSpellGameTests`), and the spell GameTests
   cast the test spells at husks and compare the damage (`SpellGameTests`).

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
| `realmforge:character` | `character` | players | race, class (a one-entry list, from when dual class was planned; there is none, [10.1](#101-races-and-classes)), total character XP, cached level cap, the stat points spent on each primary stat | the owner and nearby players (small, nothing secret) |
| `realmforge:spellbook` | `spell` | players | known spells and their levels, spell bar layout | owner |
| `realmforge:combat` | `spell` | players and mobs that cast | resource pools (mana, rage), cooldowns, current cast (transient), whether a cast set Better Combat's disable tag | owner, throttled (players; clients never read a mob's copy) |
| `realmforge:talents` | `talent` | players | allocated ranks per tree, unspent points | owner |
| `realmforge:dark_vision` | `world` | players | whether Dark Vision is switched on (on by default) | not synced (the effect itself is) |
| `realmforge:instance` | `dungeon` | players | the run's plot, the return point (spike 3) and the game mode to give back when entering switched it to Adventure (W4c) | not synced |
| `realmforge:stat_tracker` | `stats` | players | when the stat modifiers were last recomputed and what a respawn owes the health ([8.2](#82-where-modifiers-come-from)); transient: not saved or copied | not synced |
| `realmforge:magic_shield` | `combat` | players (any entity with the stat) | the points left in the magic shield and the tick count from which it refills ([9.1](#91-damage-pipeline)); transient: not saved or copied, so a new player entity starts with a full shield | the owner, points only (the HUD bar) |
| `realmforge:pending_refill` | `mob` | mobs leveled as they spawn | a mark: refill the mob's health to its scaled maximum when it joins the level ([14.1](#141-mob-levels)); saved (a chunk-generation spawn waits in the chunk's data), not copied, removed at the join | not synced |

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
  syncs with `core/Attachments#toOwner`. Talent trees are named by `Identifier` until their registry exists (C5);
  races and classes are `ResourceKey`s since C1, and spells since C3a-1, which save the same string, so F3's saves
  stay readable. `CombatState` keeps one pool per resource type (`resources`, by the type's id), absent until the
  type's pool first fills ([11.5](#115-resources-cooldowns-modifiers)). Leveling (C2, [10.2](#102-leveling)) caches
  the level cap there for players with a character, and stat points (RLM-196) save the points spent on each primary
  stat by the stat's id; the unspent points are derived, never saved. Every saved field is optional with a default,
  so an empty save reads as the default value.
- `level` is copied on death, and NeoForge copies the same attachments when a mob converts (a zombie drowns,
  `LivingConversionEvent.Post`), so the converted mob keeps its level, and `mob` gives it that level's scaling
  ([14.1](#141-mob-levels)).

## 8. Stats and attributes

### 8.1 The attribute set

- **Vanilla, kept:** max health, armor, armor toughness, attack damage and speed, movement speed, knockback
  resistance, luck and the rest. Since 1.21.2 their ids have no prefixes (`minecraft:max_health`,
  `minecraft:block_interaction_range`; 1.21.1 had `minecraft:generic.max_health`). The server sends a client only
  the attributes marked syncable (`Attribute#isClientSyncable`). Vanilla marks max health, armor, toughness, attack
  speed and movement speed, but not attack damage, so the client sees only its base value. The character sheet draws
  from the player's own attributes, so `CharacterSheetView#syncShownAttributes` marks every vanilla attribute it
  shows as syncable, attack damage included; a GameTest fails if an attribute on the sheet does not sync. It runs at
  common setup (`FMLCommonSetupEvent#enqueueWork`), once every attribute is registered: at mod construction a
  `DeferredHolder` is still unbound.
- **Primary stats (ours, decision D5):** strength (STR), dexterity (DEX), vitality (VIT: health, not called
  stamina so it is never confused with a stamina resource) and intellect (INT). They are attributes, so gear, buffs
  and talents modify them like anything else. They start at 0: race, class, the stat points a player spends, talents
  and gear raise them (C1 and later; levels give stat points, not stats: RLM-196). Luck and defense stay out (D5).
- **Secondary stats (ours):** crit chance (5 % to start with), crit damage (+50 %), dodge chance (no primary stat
  gives it: D5 keeps it for future classes such as the Rogue), spell power, and each magic school's power and
  resistance (`fire_power`, `fire_resistance`, ...; F5, the resistances capped at 75 % so nothing becomes immune
  without a tell, research BM-18). Matěj's playtest of F4b and F5 (2026-10-13) set the rest of the list, shown on the
  character sheet before every one of them works. Working in the damage pipeline ([9.1](#91-damage-pipeline);
  RLM-158, RLM-160): armor pierce and armor shred (`armor_pierce` in points, `armor_shred` a share, Apotheosis's
  name for it, RLM-234; both lower the armor a hit meets, never its toughness), lifesteal (a share of a melee or ranged hit's damage healed back), overheal (a
  share of max health that healing past it fills as absorption), spell resistance (a share, added to a magic
  school's resistance, the sum at most 75 %), health regeneration (points healed every second) and magic shield (a
  pool of points that takes damage before health). Not working yet: protection pierce and protection pierce %
  (RLM-159, waiting for his decision on what they pierce); cast time reduction (C3a-2, RLM-210); and the XP
  multiplier (100 % by default, C2, RLM-23). Max mana, mana regeneration and cooldown reduction (which replaced haste)
  work since C3a-1 ([11.5](#115-resources-cooldowns-modifiers)).
  Healing power is gone: healing spells scale with spell power and their school.
- **Defined in `content/stats/`** (F4): each stat's kind (primary or secondary) and its range, in points (a
  `RangedAttribute`) or as a share (`max_percent`: NeoForge's `PercentageAttribute`, holding a fraction). `rf gen`
  writes their registrations into `generated/GeneratedStats` (owned by `stats`), with the description id
  `attribute.realmforge.<id>` and `setSyncable(true)` for all of them, as the character sheet shows them. Each
  stat's `name` is the lang entry of that description id, shown on the character sheet
  ([18](#18-client-code-rendering-gui-input)) and in tooltips.
- `StatsFeature` adds every stat to players in `EntityAttributeModificationEvent`; mobs get the ones they need
  with the features that use them (spell power for casters).
- NeoForge also has a boolean `creative_flight` attribute (`NeoForgeMod.CREATIVE_FLIGHT`): the planned basis for
  Angel Wings-style spells. It is not the basis for flying mounts: a mount is its own entity the player rides
  (RLM-115), and the mount entity itself flies, as a flying vehicle (`Entity#isFlyingVehicle()`) or with no gravity
  while airborne ([Chococraft research, notes for the mount tasks](research/chococraft.md#notes-for-the-mount-tasks)).

### 8.2 Where modifiers come from

An attribute holds at most one modifier per id. Vanilla's equipment code and the `addOrUpdate...` and
`addOrReplace...` methods replace a modifier with the same id; the plain `add...` methods throw. So every id is
unique per source, entry, equipment slot and modifier (`<n>` is the modifier's position in its entry), the way
vanilla armor uses one id per slot. Otherwise two rings with the same affix would count once, and taking one off
would remove both bonuses.

| Source | Applied by | Modifier id | Lifetime |
| --- | --- | --- | --- |
| race and class | `stats/CharacterStats`, from `character`'s `StatSource` (`character/CharacterBonuses`): each modifier's amount, the same at every level | `realmforge:race/<namespace>/<n>/<path>`, `realmforge:class/<namespace>/<n>/<path>` for the race or class `<namespace>:<path>` | saved, recomputed |
| spent stat points | `stats/CharacterStats`, from `character`'s `StatSource` (`character/StatPoints`, [10.2](#102-leveling)): one modifier per primary stat with points, adding them | `realmforge:stat_points/<namespace>/<path>` for the stat `<namespace>:<path>` | saved, recomputed |
| talents | `stats/CharacterStats`, from `talent`'s provider | `realmforge:talent/<tree>/<node>/<n>` | saved, recomputed |
| derived from primary stats | `stats/CharacterStats`, from stat rules ([8.3](#83-derived-stats-and-milestones)) | `realmforge:stat/<rule namespace>/<entry>/<rule path>`: `<entry>` is `<n>` per point, `at_<points>_<n>` per milestone; the rule's path comes last, as only it may contain slashes | saved, recomputed |
| gear with fixed stats | vanilla, from the item's attribute modifiers | `realmforge:gear/<slot>/<n>` (generated) | while equipped |
| affixes and gems (M2) | vanilla slots: `ItemAttributeModifierEvent` adds them to the stack's modifiers; curio slots (rings, trinkets): Curios' own modifier API, because Curios ignores vanilla equipment modifiers in its slots (*verify* in M2) | `realmforge:affix/<affix>/<slot>/<k>/<n>`: `<k>` counts repeats of the same affix or gem on one stack (two sockets with the same gem), and curio slots use their slot id and index as `<slot>` | while equipped |
| status effects | vanilla, `MobEffect#addAttributeModifier` | the effect's | while active |
| mob level | `mob`, when the mob gets its level ([14.1](#141-mob-levels)): health and armor (damage multiplies each hit instead) | `realmforge:mob_level/<attribute>` (`max_health`, `armor`) | saved with the mob, reapplied from its level and the current curves when it loads |

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
A recompute first applies every `stats/StatSource` that a higher feature registered (race and class since C1, spent
stat points since RLM-196), then the stat rules, so a primary stat a race, a class or a spent point raises gives its
derived bonuses in the same recompute. A login brings the player's level up to date but leaves the recompute to the
first tick too, even when the level changed (RLM-202): a recompute inside `PlayerLoggedInEvent` would read the
primary stats without the gear's and lower max health, clamping the health for good.
A GameTest player (`QaPlayers#loginOnPlatform`) is never ticked by its connection, so the tests play a client tick with
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
  (`CharacterStats#markDirty`: level, talents), plus a cheap check every 10 ticks that compares primary values and
  the level with the last applied snapshot (covers curios, other mods and commands such as `/qa level`).
- No cycles: rules read primary stats and write only secondary or vanilla attributes, and each primary stat has at
  most one rule (validation rules). `StatRule`'s codec also refuses a rule from any other data pack that reads no
  primary stat or writes one: a rule raising a primary stat would read its own bonuses back as a changed input at
  the next check and escalate them to the cap. An `add_value` amount on one of our stats is in its unit
  (`amount_percent` for a share, `amount` for points); a vanilla attribute takes either, as the pipeline does not
  catalogue vanilla units (knockback resistance is a fraction). Multiplied operations always take
  `amount_percent`.

### 8.4 Caps

Vanilla caps in 26.1.2 (as in 1.21.1) are armor 30, armor toughness 20, max health 1024, attack damage 2048 and max
absorption 2048, and the vanilla armor formula never blocks more than 80% (`CombatRules.getDamageAfterAbsorb`). The
vision wanted armor, toughness and health far beyond vanilla; for armor and toughness Matěj corrected that on
2026-10-16 (RLM-223): the best reachable armor and toughness is about netherite × 1.5 (30 armor, 18 toughness), and
better armor gets effects instead. The raised armor and toughness caps stay (Matěj, 2026-10-16): the practical
ceiling is the best armor set with the most armor affixes plus armor curios, which nobody reaches, so a hard cap adds
nothing. So:

- `stats/AttributeCaps` raises those five caps to 100,000 (F4c; max absorption with RLM-158, so overheal's
  absorption, a share of max health, follows max health past 2048), on the client and the server alike, as each clamps
  attribute values itself: a limit against broken numbers, not a balance number. It runs as deferred work of mod
  construction (`FMLConstructModEvent#enqueueWork`), which FML runs serially after every mod's constructor (the
  constructors run in parallel), so it never lowers a cap another mod raised further. A `RangedAttribute`'s maximum
  is a private final field, so the mechanism is a mixin accessor with `@Mutable` (`mixin/RangedAttributeAccessor`,
  [19.3](#193-mixins-and-access-transformers)); an access transformer would work too.
- `AttributeCaps.max` reads the cap of any attribute from the attribute itself, and `AttributeCaps.RAISED` lists the
  raised ones. Our own stats take their caps from `content/stats/` (`max`, `max_percent`), so a stat with a hard cap,
  such as tenacity (research SP-12), is a content entry, and code asks `AttributeCaps.max` for its cap.
- Every other cap stays vanilla's.
- Armor mitigation is vanilla's formula, after our armor pierce and shred ([9.1](#91-damage-pipeline), RLM-223).

## 9. Combat

### 9.1 Damage pipeline

Built in F5 (RLM-21), with armor pierce, lifesteal and overheal (RLM-158) and spell resistance, health regeneration
and the magic shield (RLM-160): `combat/DamagePipeline` on NeoForge's damage events, with every formula in the pure
`combat/DamageCalculator` (JUnit). Logical server only.

1. **Spell schools.** A school (`realmforge:spell_school`, `content/spell_schools/`, owned by `combat`) is a kind of
   damage: `physical` (armor reduces it, it can be dodged) or `magic` (bypasses armor, spell power raises it). Each
   school is a vanilla damage type `realmforge:<school>` (death messages `death.attack.realmforge.<school>` keep
   working), in `#minecraft:bypasses_cooldown` and `#minecraft:no_knockback`; magic schools are also in
   `#minecraft:bypasses_armor`. **School hits carry no knockback** (RLM-214): vanilla pushes a target away from the
   source on every hit outside `#minecraft:no_knockback`, which would let every bolt, physical ability and
   damage-over-time tick push like a melee hit. A spell pushes only through its `realmforge:knockback` effect (C3a-3,
   RLM-211); melee and projectile hits keep vanilla's knockback. A hit belongs
   to a school when its damage type is the school's (`combat/SpellSchools#of`), so `combat` needs no knowledge of
   spells: the spell engine (C3a), abilities and bosses deal school damage with `SpellSchools#damage(registries,
   school, direct, causing)`, and so does vanilla's `/damage <target> <amount> realmforge:fire by <attacker>`. M1 has
   the Warrior's `physical` and the Mage's `fire`, `frost` and `arcane` (D1); the remaining schools come in M3. A
   spell's crit rules reach the pipeline in `combat/SchoolDamageSource`, a damage source with a `combat/CritRule`
   (C3a-1, `SpellSchools#damage(registries, school, direct, causing, crit)`); its level and scaling are already in
   the amount the spell engine deals ([11.2](#112-casting-server-authoritative)).
2. **`CriticalHitEvent`**, for a player's melee attack: a full-strength attack crits on the player's crit chance and
   deals 1 + crit damage, instead of vanilla's jump crit (a jump no longer matters). Vanilla still plays its crit
   sound and particles, and a crit keeps a sword's sweep (vanilla's jump crits never happened on the ground).
3. **`LivingIncomingDamageEvent`**, before mitigation:
   - **Dodge** (the defender's dodge chance) cancels a melee hit, a projectile or a physical school hit from an
     attacker; magic, explosions and damage from the world cannot be dodged. Cancelling stops a hit cleanly since
     NeoForge 26.1 (below), and `CombatEvent.Dodge` reports it.
   - **A school hit** gets the attacker side, damage × (1 + spell power, for magic only, + the school's power stat),
     then the crit roll (crit chance and crit damage, as in melee, or the spell's crit rule: no crit at all, a bonus
     chance, its own multiplier; Matěj, 2026-10-15), then the defender's resistance: × (1 −
     resistance), through `setAmount`. The resistance is the defender's resistance to the school, plus its spell
     resistance when the school is magic, at most 75 % together (`DamageCalculator.RESISTANCE_CAP`, research BM-18);
     a physical school hit meets no spell resistance. Talents act through those attributes or are already part of
     the spell's damage ([11.5](#115-resources-cooldowns-modifiers)).
   - **The armor formula is vanilla's** (`CombatRules.getDamageAfterAbsorb`, Matěj, 2026-10-16, RLM-223), applied
     after our armor pierce through `addReductionModifier(DamageContainer.Reduction.ARMOR, ...)`. The armor a hit
     meets is the defender's armor (whole points, as vanilla's `getArmorValue`) less the attacker's flat armor
     pierce, then less its armor shred share of the rest, never below 0; armor toughness is never pierced and keeps
     its vanilla role. With D the hit's damage: effective armor = clamp(armor − D / (2 + toughness / 4), armor / 5,
     20), and the hit loses effective / 25 of its damage, then vanilla's armor-effectiveness enchantment hook
     (Breach) adjusts that share. So armor counts most against small hits and toughness against big ones, at most
     80 % blocked; the attacker's level reaches armor only through its hit size (mob damage grows with level,
     [14.1](#141-mob-levels)). Armor pierce and shred act on every hit armor meets from that attacker: melee,
     projectiles (their shooter's) and physical school hits. NeoForge runs that reduction for every hit, so the
     formula keeps vanilla's (zero) reduction for damage in `#minecraft:bypasses_armor`. Vanilla still wears the
     armor down. The formula is ours in code (`DamageCalculator#armorReduction`, JUnit-checked against vanilla's), so
     a mod that replaces `CombatRules` (Apothic Attributes, RLM-205) does not change it
     ([ADR 0033](decisions/0033-vanilla-armor-formula-amends-our-own.md)).
   - **The magic shield** joins the absorption reduction
     (`addReductionModifier(DamageContainer.Reduction.ABSORPTION, ...)`) of a defender that has the stat or points
     left (step 5).
4. **Vanilla's hurt cooldown** comes next. Within 10 ticks of the previous hit, a hit that is not bigger than the
   previous one is dropped without any further event, and a bigger one deals only the difference; a hit outside
   that window starts a new one. School hits bypass it (the tag), so spells, damage over time and abilities always
   land. A bypassing hit takes vanilla's `else` branch of `LivingEntity#hurtServer`, which grants new invulnerability
   ticks and sets the protected `lastHurt`, the amount later hits in the window are measured against. Left alone,
   a DoT tick of 1 would let a second zombie's melee hit for 6 land as 5 instead of 0, and a spell hit for 20 would
   swallow a melee hit for 8. **F5 keeps school hits out of the window entirely:** a school hit keeps the
   defender's invulnerable time (`setInvulnerabilityTicks(max(1, invulnerableTime))`: NeoForge refuses 0, and 1
   opens no window) and restores `lastHurt` after the hit, through an access transformer on `LivingEntity#lastHurt`
   ([19.3](#193-mixins-and-access-transformers)). Melee hits in an open window are therefore compared with the
   previous melee hit, as in vanilla. `CombatGameTests` measures it: melee 6, a fire tick of 1, then melee 6 again
   deals 0 (it would deal 5 without the restore); and a fire hit right after a melee hit lands in full.
5. The reductions apply: armor (our formula), enchantments, effects. **`LivingDamageEvent.Pre`** sees the damage
   before absorption hearts take their share. Then absorption hearts take what they can, the **magic shield** takes
   what they left, up to its points (`combat/MagicShields`), and health takes the rest. The shield acts in the
   absorption reduction, after `Pre`, so the hit's inflicted damage (what the combat text shows and what the hurt
   cooldown measures the next hit against, `lastHurt`) stays the whole hit. NeoForge subtracts the whole absorption
   reduction from the absorption hearts, which is right because the shield only takes something once they are
   empty.
6. **`LivingDamageEvent.Post`** reports what was dealt: `combat` posts `CombatEvent.Hit` (school, crit, the
   original damage, what armor blocked, the damage dealt and taken from health) for higher features (procs in C3a
   and C5, threat later, the QA mod's combat log), and sends the combat text (`combat/net/CombatTextPayload`) to the
   player who dealt the hit ([18](#18-client-code-rendering-gui-input)). A tick of a damaging effect has no attacker:
   `combat/DamageOverTime` remembers the player who applied each poison or wither (from `MobEffectEvent.Added`'s
   source: the player, or the owner of the splash potion, tipped arrow or lingering cloud; the latest application
   decides, and the entry goes when the effect ends or is removed), and the tick's number goes to that player in the
   effect's colour, dark green for poison and ash grey for wither (RLM-186). Our own status effects (C3c) will add
   their kinds to its list, so each effect's colour has one source. The player who took it gets no number, the
   health bar shows the damage, but the direction it came from (`combat/net/HitDirectionPayload`), as for a dodge,
   which also shows "Miss!" to both players. A melee or ranged hit (`DamagePipeline#meleeOrRanged`: a mob or player
   attack, or a projectile, from an attacker; never a school hit or an explosion) then heals its attacker by the
   attacker's **lifesteal** share of the inflicted damage, which counts what absorption hearts and the magic shield
   took (Matěj, 2026-10-14: melee and ranged hits only, never spells).
7. **`LivingHealEvent`**, the last listener: **overheal** turns the part of a heal beyond max health into absorption
   (below), and the combat text shows the health the heal restores to the players near the healed entity, from half
   a point up (Matěj, 2026-10-13).

**Overheal** (`combat/Overheal`): healing beyond max health becomes absorption, up to the overheal share of max
health. Vanilla clamps absorption to `minecraft:max_absorption` (a golden apple's Absorption effect adds a modifier
to it), so overheal makes room with a transient modifier `realmforge:overheal` of the same size. **It does not decay
gradually; it expires** 10 seconds after the last heal that overflowed, like WotLK's absorb shields (Divine Aegis,
Val'anyr's Protection of Ancient Kings), so it cannot be stocked up out of combat and carried into a fight, while
steady healing in a fight (lifesteal) keeps it up. **Overheal and other absorption (a golden apple's) stack**, each
within its own cap, and damage eats the overheal first: each server tick, what the absorption lost since the last look
comes off the overheal first, and its modifier shrinks with it. A drop that the other absorption's cap explains is not
damage: when the Absorption effect ends (or milk clears it), vanilla clamps the absorption to what is left of the
maximum, the overheal's share, and the overheal stays. When an Absorption effect starts while overhealed, vanilla
raises the absorption only to the effect's amount (`AbsorptionMobEffect#onEffectStarted`, after
`MobEffectEvent.Added`), so the next server tick puts the overheal back on top: 5 overheal and an apple's 4 make 9,
and the apple's 4 are left when the overheal expires (RLM-195). Its state is per entity and transient (maps with weak
keys, checked each server tick); a player who logs in has absorption above the maximum clamped away, since the
modifier is not saved. Passive health regeneration never overheals.

**Health regeneration** (`combat/HealthRegeneration`): a player with X health regeneration heals X health every
second (X/2 hearts; Matěj, 2026-10-14), on top of vanilla's food regeneration, never beyond max health. It runs on
the player's tick, every 20 ticks of its tick count.

**Magic shield** (`combat/MagicShields`; Matěj, 2026-10-13: "a secondary healthbar, that is hit first, and only
when empty the player begin to lose health; mainly for casters"): a pool of up to the magic shield stat's points in
the transient `realmforge:magic_shield` attachment ([7](#7-player-state)). It takes damage after absorption hearts
and before health (step 5), and refills to full, all at once, 10 seconds (`MagicShields.REFILL_DELAY_TICKS`) after
the last damage the player took, any damage, even with the pool empty (Matěj's playtest, 2026-10-14); a new player
entity starts full. The pool syncs to its owner on every change, and `client/hud/MagicShieldHud` draws it as a
translucent bar over the bottom row of hearts, a new colour every 10 points ([18](#18-client-code-rendering-gui-input)).
Like absorption hearts, it takes any damage that reaches health (falls, drowning, starvation included).

`DamageCalculatorTest` covers the formulas, the armor formula against vanilla's `CombatRules` over a grid of damage,
armor and toughness; `CombatStatGameTests` plays each stat in a world: armor pierce and shred against a training
dummy with 15 armor, spell resistance and its cap on a pillager, and a fake player for lifesteal (melee and a
thrown hit heal, a fire hit does not), overheal (its cap, its expiry, damage taking it before a golden apple's
absorption, the overheal kept when the Absorption effect ends, an apple eaten while overhealed), health regeneration
(its schedule, no overheal) and the magic shield (before health, after absorption hearts, `lastHurt` kept, the
refill).

What one hit learns in an earlier event and needs in a later one (whether a melee hit crit, a school hit's
`lastHurt`) waits in `combat/HitTracker`, per defender and matched by the damage source, because a hit the hurt
cooldown drops never reaches `Post` and one hit's events can nest inside another's.

**Cancelling `LivingIncomingDamageEvent` stops a hit cleanly since NeoForge 26.1.** `LivingEntity#hurtServer`
pushes a `DamageContainer` onto the defender's `damageContainers` stack, posts the event, and pops the container again
when the event is cancelled (checked in 26.1.2.116). NeoForge 21.1.259 returned without popping it, so on 1.21.1 every
cancel left a container on the defender; that is why this section used to forbid cancelling. A dodge cancels the
event; `CombatGameTests` shows that twenty dodged hits leave the stack empty.

The Python balance simulation (M3) will use the same formulas; one JSON file of inputs and expected outputs, read by
both pytest and JUnit, will keep the two implementations equal.

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
  removes one a crash left behind. A cast cancels a swing; an attack does not interrupt a cast. **In code (C3a-2):**
  `spell/Casting` cancels the event and sets, records (`CombatState#setBetterCombatTag`, saved) and removes the tag;
  `client/spell/ClientCasts` cancels the attack key, swing included, while the local player casts. Mobs' melee during
  their casts is C3a-4's (NPC casting).

## 10. Character: races, classes, leveling

### 10.1 Races and classes

- **Creation:** while a player's `character` attachment has no race, the client shows the character creation screen,
  which only creating a character closes (Esc opens the pause menu, and creation returns after it).
  `CreateCharacterPayload(race, class)` → the server checks the combination and that no character exists yet → sets
  the attachment, gives the starting kit, recomputes stats.
- **The server enforces creation too:** a client that skips the screen gains nothing. Until the character exists,
  the server refuses the player's gameplay payloads (casting, spell bar, talents) and awards no character XP, and
  every system that reads race or class handles a missing character instead of assuming one.
- **A race** gives base modifiers, innate spells, optionally a size (`generic.scale`), and the allowed classes.
- **Racial abilities** (Matěj, 2026-10-15, RLM-219; built in C4): every race has one racial passive and one racial
  active. The active is an innate spell on the spell bar's G key (C3b); the passive applies whenever its condition
  holds. The Human's:
  - **Will To Survive** (active) cleanses the player of every negative effect: disabling effects such as a stun
    (much rarer here than in WoW) and also damage over time and other debuffs. Cooldown 5 minutes.
  - **Sword Specialization** (passive; only its name, not a class specialization) gives +10% damage with sword-like
    weapons: the item tag `#realmforge:sword_like`, swords and katanas today, and longswords and maybe rapiers once
    the pack has them. For a Human, the tooltip of every such weapon (hover, JEI) shows two lines below the weapon's
    stats and above the Apotheosis affixes, where Apotheosis puts "Heavenly Implements": "Sword Specialization" in
    dark gray and "+10% damage" in light gray.
- **A class** gives its resource type, starting stats, spells by level, its specializations' spell trees and starting
  kit. Race and
  class stats stay the same at every level: each level gives stat points the player spends instead
  ([10.2](#102-leveling); Matěj, 2026-10-15, RLM-196: classes are for skills and abilities, not fixed stat
  growth). Whether races and classes keep their starting stats is open; they keep them until Matěj decides.
- **Specializations, no dual class** (Matěj, 2026-10-15, RLM-218): a player has one class, never two. Every class
  has three specializations (the word replaces "subclass"), each with its own spell tree of active and passive
  spells. The player picks one in game, in a spell trees window, after creating the character (race and class),
  and then spends points in that tree. From a level threshold (proposed: 20) they can pick a secondary
  specialization, a dual spec. M1 has one specialization per class; the other two, and dual spec, come in M3
  (Matěj, 2026-10-16). The `character` attachment still stores the class in a list from when dual class was
  planned; the specialization work (the spell tree model, RLM-28) decides what it stores instead.
- **Changing race or class:** a QA command in M1, an in-game way later.
- **In code (C1):** `character/Race` and `character/PlayerClass` are the entries of `realmforge:race` and
  `realmforge:class` (`CharacterRegistries`, both synced). A race names its classes by id, so a missing class fails
  the world load; a class names its resource (`realmforge:resource_type`, [5.3](#53-type-registries)). Their
  modifiers (`CharacterModifier`: an amount, the same at every level) reach the player through `CharacterBonuses`,
  the `StatSource` that `stats` applies before the stat rules ([8.2](#82-where-modifiers-come-from)).
  `CharacterCreation` holds the rules: the player has no character yet, the race and the class exist, and the race
  lists the class, checked in that order; then it sets the attachment, recomputes the stats, refills health to the
  new maximum and gives the class's starting kit once (into the inventory, or dropped for the player alone when it
  is full, as `/give` does). `Characters` is the public API (`exists`, `mainClass`). The client opens
  `client/screen/CharacterCreationScreen` once a new player entity has existed for a second without a character (so
  the synced attachment can arrive first), and closes it when the character arrives. Esc opens the pause menu
  instead, so the player can still change options or quit, and back in the game creation opens again. It pauses
  singleplayer like a menu (the server still handles payloads while paused). Its view model is
  `CharacterCreationView`. M1 has one race, Human, and decision D1's classes, Warrior (rage) and Mage (mana)
  ([content/README](../content/README.md)).

### 10.2 Leveling

- **Character XP is separate from vanilla experience** (decision D3, RLM-53). Vanilla XP stays the currency for
  enchanting and anvils until the enchanting overhaul (M5); character XP is never spent.
- **Level is derived:** `level = min(cap, level_for(total_xp))`, with the XP table from `realmforge:curve`, and
  stored in the `realmforge:level` attachment that every system reads.
- **XP stops at the cap** (Matěj, 2026-10-15, as WotLK does at max level; not the Otherworld pattern of the
  [research notes](research-notes.md#inputs), which banks it). A player holds at most the XP of their cap's level
  (`XpTable#capped`). A gain that crosses the cap counts only up to it, and a player at the cap gains nothing, so
  the next chapter's levels cannot be farmed in advance. A raised cap therefore brings no level by itself: the
  player starts at 0 XP toward the next level. Every update cuts the total (a lowered cap after a revoked chapter,
  a save from before this rule at login).
- **Level cap:** the highest cap among completed chapters (a base cap before any), cached in the `character`
  attachment and recomputed when a chapter's advancement is granted or revoked and on login
  ([16](#16-progression-and-quests)). A chapter's cap is its last level, and its main questline raises it (Matěj,
  2026-10-15, superseding D2's 10 then 20; v1 realm layout RLM-188): chapter 1 (Overworld, levels 1-10) 10 → 18,
  chapter 2 (Undervault, levels 6-18) 18 → 30, chapter 3 30 → 40, chapter 4 40 → 44, chapter 5 44 → 52,
  chapter 6 52 → 60, and chapter 7 60, the highest level. `content/chapters/` holds chapters 1 and 2 so far; the
  last chapter raises no cap, so its entry will need `completed_level_cap` to be optional.
- **The XP model** (Matěj, 2026-10-15; [design/leveling.md](design/leveling.md), RLM-192) counts in kill-equivalents
  (KE): one kill of a normal mob of the player's level, `45 + 5 × level` XP (50 at level 1, 345 at 60). A level
  takes `KE(L) × (45 + 5L)` XP, with `KE(1) = 30` (about 5 minutes of farming at 6 kills a minute) and each level
  1.07 times the KE of the one before below level 10, 1.05 times for 10-17, 1.035 times for 18-29 and 1.03 times
  from 30: 1,500 XP from level 1 to 2, 25,960 in all for level 10, 85,890 for 18, 281,690 for 30 and 1,912,740 for
  60. Quest XP (RLM-189), dungeon XP (RLM-190) and anti-farm rules (RLM-191) join later.
- **XP sources** (amounts are data): kills, where `character` listens to `LivingDeathEvent` and scales the XP by the
  victim's level relative to the player's (mobs far below give nothing) and by its kill XP multiplier (its rank); quests (`/realmforge xp`); dungeon
  completion and boss kills; later discoveries and professions. A player's XP multiplier (`xp_multiplier`, 100 % by
  default) scales every gain.
- **Level-up:** earn stat points, recompute stats, grant talent points and teach class spells through their
  features' APIs (C4, C5), toast and sound, fire the `realmforge:level_reached` trigger for advancements.
- **Stat points** (Matěj, 2026-10-15, RLM-196): every level from 2 on earns primary stat points, one a level
  (the curve `stat_points_per_level`), which the player spends on the character sheet, a point at a time, on any
  primary stat: no class locks a stat away. A spent point is final until the player unlearns them all with a Scroll of
  Oblivion, an item that is hard to get on purpose (Matěj wants it as a hard repeatable quest's reward, or an expensive
  shop item once coins exist; the quest waits for Q1). Race and class stats no longer grow with the level. Two more
  scrolls follow (Matěj, 2026-10-16): the Scroll of Respecialization returns every spell (talent) point, the Scroll
  of Rebirth resets the whole character (race, class, spell and stat points); the three share one scroll texture in
  purple-ish, yellow-ish and red-ish tones.
- **In code (C2, RLM-23):**
  - **The numbers** are four curves ([content README](../content/README.md)): `xp_to_next_level` (the XP from a
    level to the next, rounded half up, at least 1; one point per level 1-59 from the XP model), `kill_xp` (a kill's
    XP by the victim's level, `45 + 5 × level`), and `kill_xp_below_player` (90 % at 1 level below, then 15 % less a
    level, nothing from 7 below) and `kill_xp_above_player` (5 % more a level, up to +20 %), by how many levels the
    victim is below or above the player. A kill gives `kill_xp × rank × level difference × XP multiplier`, in that
    order, rounded half up. `character/XpTable` and `character/KillXp` are the pure rules, and
    `tools/realmforge_tools/schema/leveling.py` computes the same; `LevelingGameTests` compares the two at the
    generated samples.
  - **Kill XP multipliers** (`character/KillXpMultiplier`, the server-only registry `realmforge:kill_xp_multiplier`
    from `content/kill_xp_multipliers/`): the victim's entity type gives its rank, 1 when it has no entry. Vanilla's
    first numbers (Matěj, 2026-10-15): 0 for a size 1 slime and 0.25 for a larger one, 0.1 for a silverfish,
    endermite or vex, 0.5 for a cave spider, 1.5 for a witch, enderman, vindicator or breeze, 2.5 for an evoker, 4
    for a ravager, 10 for an elder guardian or warden. A slime's or magma cube's size (`Slime#getSize`) picks its
    `by_size` entry. Our own mobs and bosses take their rank the same way (RLM-190); the XP model's ranks run from 0
    (passive) and 0.25 (minion) through 1 (normal), 1.5 (strong) and 2 (elite) to 5 (miniboss), 10 (dungeon boss)
    and 20 (realm guardian).
  - **Chapters** live in `progression`: `progression/Chapter` (the `realmforge:chapter` entry: `order`, `level_cap`
    while it is the current chapter, `completed_level_cap` once completed), `LevelCaps` (the pure cap rule: the
    highest `completed_level_cap` among completed chapters, never below the first chapter's `level_cap`; with no
    chapter at all only the guard `LevelCaps.MAX_LEVEL`, 1000, holds) and `Chapters`, the public API (a chapter is
    completed once the player has `<namespace>:chapter/<path>`). The pipeline generates each chapter's advancement:
    `minecraft:impossible` as its only criterion and no display, so only the chapter's last quest or a command
    grants it, and it shows no toast or tab. NeoForge posts `AdvancementEvent.AdvancementEarnEvent` only for
    advancements with a display (`PlayerAdvancements#award`), so `character/Leveling` listens to
    `AdvancementProgressEvent`, which follows every grant and revoke of a criterion, after the progress changed.
  - **`character/Leveling`** is the system: on login it brings the cap and level up to date quietly and fires
    `level_reached` with the level, so a new criterion catches up; a chapter's advancement updates them and, when
    the cap rose, says so in chat ("Chapter 1 complete! Your level cap is now 18.") before the level-up it brings,
    whose hint may name the next chapter. A kill of a hostile mob (a
    vanilla `Enemy`; animals, villagers and golems give nothing) gives XP to the player with vanilla's kill credit:
    the one who dealt the killing blow, directly or by a projectile, else the one who hurt the victim in the last
    5 seconds or owns the pet that did (`LivingEntity#getLastHurtByPlayer`, as for experience orbs). It listens at
    the lowest priority and not to cancelled deaths, and shows "+50 XP" in the action bar. Players without a
    character gain nothing ([10.1](#101-races-and-classes)) and leveling leaves them alone; creating the character
    brings their cap and level up to date. `Characters` is the public API: `level`, `rewardXp` (times the XP
    multiplier, with level-up effects), `setTotalXp` (exact, quiet) and `xpToReach`.
  - **A level-up** recomputes the stats at once, shows the title "Level Up!" with "You are now level 5" beneath it
    (vanilla's title packets), plays the challenge fanfare to the player alone, bursts totem particles that everyone
    nearby sees, says in chat how many stat points are unspent and which key opens the sheet (a keybind component),
    and fires `realmforge:level_reached`. A level-up that reaches the cap says in chat which chapter raises it.
    Talent points and class spells join with C4 and C5. A login updates the level quietly and leaves the stat
    recompute to the player's first tick ([8.2](#82-where-modifiers-come-from), RLM-202); every other update
    (kills, rewards, commands, chapters) recomputes at once.
  - **Stat points** (`character/StatPoints`, RLM-196): the points a player has earned are derived, never saved:
    those of every level from 2 to theirs, each level's the curve `stat_points_per_level` rounded half up and at
    least 0 (`tools/realmforge_tools/schema/leveling.py` computes the same, and a GameTest compares the two). The
    `character` attachment saves only the points spent on each primary stat, so the unspent ones are the earned ones
    less the spent ones. A character from before stat points existed therefore has every point of its levels
    unspent, once, and a rebalanced curve applies at once. The sheet's `+` sends `SpendStatPointPayload(stat)`;
    the server checks that the player has a character, that the stat is one of our primary stats and that a point is
    unspent, then saves it and recomputes the stats. The spent points reach the player through their own
    `StatSource`, one modifier per stat. An item in the tag `realmforge:use/unlearn_stat_points` (the Scroll of
    Oblivion; an item's `use` in content writes the tag, [13](#13-items-gear-and-loot)) returns every spent point when
    right-clicked and is used up; with nothing spent it fails and is kept. A level that falls below what was spent
    (a command, a revoked chapter, a curve that gives fewer) returns every spent point, with a message.
  - **`/realmforge xp add|set|query`** (`command/XpCommand`, permission level 2): `add` gives XP as a reward (times
    the XP multiplier, with level-ups), `set` sets the total exactly and quietly, `query` shows XP, level and cap.
    FTB Quests' `CommandReward` runs `add` for quest rewards (Q1).
  - **The XP bar** ([18](#18-client-code-rendering-gui-input)) draws `character/XpBarView`.
  - No XP is lost on death in M1 (research PR-7).

## 11. Spells and status effects

### 11.1 Model

A `realmforge:spell` entry ([example in 6.4](#64-example-the-life-of-a-spell), `spell/Spell`) has a school, what it
scales with, targeting (with the range), cast type (instant since C3a-1; cast time and channel since C3a-2), cost,
its own cooldown and the global cooldown, crit rules, typed effects, a maximum level with values per level (the
vision prefers spell levels or power over rarities; research SP-9), and an `npc_only` flag for mob and boss spells.
Its name, description, icon, animation and sound keys come with the client side (C3b, C6).

- **What it scales with** (Matěj, 2026-10-15): spell power for magic, attack damage for physical abilities (a
  Warrior's Slam is a weapon move), later summon damage. Off-class stats do nothing for a class's abilities, so the
  pipeline refuses a spell whose `scales_with` does not fit its school's kind. A magic school's damage gets spell
  power and the school's power in the damage pipeline ([9.1](#91-damage-pipeline)); its heals get the same factor
  from the spell engine. A physical ability's damage and heals add their share of the caster's attack damage.
- **Knockback:** a spell's damage is school damage, which never knocks back ([9.1](#91-damage-pipeline), RLM-214);
  a spell pushes its target only through a `realmforge:knockback` effect (C3a-3).
- **Crits** (Matěj, 2026-10-15): every spell can crit, heals too, on the caster's crit chance for 1 + the caster's
  crit damage (150 % with the base 50 %), crit damage being shared with melee. A spell's `crit` data may switch
  crits off, add a bonus chance, or set its own multiplier (`combat/CritRule`); talents (C5) will change all three.

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
   on says so in its data. Otherwise the cost and the spell's own cooldown apply first (the global cooldown started
   with the cast), then every effect runs on the target with an `EffectContext` (caster, the entity that delivers it,
   spell, spell level, random source). Effects read the caster's stats as they run, those that a projectile or an
   area runs later included ([11.3](#113-spell-entities)).
5. A channel pays its cost and starts its cooldown when it begins, then runs its effects at the tick interval its
   data sets for as long as it lasts, checking the target before every tick as in step 4. Moving or an interrupt
   ends it early, and the ticks already run stay applied.
6. Mobs and bosses use the same `SpellCaster`, without a payload. Their cooldowns and current cast live in their
   own `combat` attachment ([7](#7-player-state)), and their cast bars, the telegraphs players react to, reach
   clients only through the cast payloads.

**In code (C3a-1):** `spell/SpellCaster` casts instant spells for any living caster; a player at the level their
spellbook knows, a mob at level 1. It refuses a cast, paying nothing and running nothing, with a `spell/CastFailure`
(research SP-3; C3b turns each into a text), checked in this order: the caster is dead or a spectator, the spell
does not exist, a player casts an `npc_only` spell or one their spellbook does not know, the spell's own cooldown
runs, the global cooldown runs and the spell is on it, the targeting finds no valid target (`spell/Targets`: in range
from the caster's eyes to the target's box, and in vanilla's line of sight), a player lacks the cost. Otherwise the
cost and the cooldowns apply, then every effect runs on the target with an `EffectContext` (caster, spell, spell
level, random source; C3a-3 adds the entity that delivers it, and procs, C3a-4, the proc origin). Allies (`Targets#allied`) are the caster, its team mates and tamed
pets (vanilla's `isAlliedTo`), and for a player every other player, as M1 has no PvP; everything else is an enemy.
Mobs pay no cost until C3a-4 decides NPC resources. `/qa spell` drives it until the spell bar exists.

**In code (C3a-2):** a spell's `cast` (`spell/SpellCast`) is `instant`, `cast_time` (a duration) or `channel` (a
duration that is a whole number of intervals), and the two timed types may set `castable_while_moving` and `lock_on`.
`SpellCaster` refuses a cast while one is in progress (`already_casting`, checked before the cooldowns). After every
other check a timed spell starts the global cooldown at once, as in WoW, so it runs during the cast; a channel also
pays and starts its own cooldown then, a cast time only when it completes. `spell/Casting` keeps the casts in
progress and ends them from the server tick (`ServerTickEvent.Post`): Cast Time Reduction (at most 75 %, its stat's
cap) shortens a cast time, and a channel's interval, so a channel keeps its tick count (`SpellScaling#castTicks`,
whole ticks, at least one). Moving more than 0.1 blocks from where the cast began, death, a dimension change, logging
out, the caster's removal and a cancel (`Spells#cancelCast`, `/qa spell cancel`) interrupt it. At its end a cast time
checks the cost and the target again (`SpellCaster#recheck`: alive, then the targeting's range, line of sight and
enemy or ally; a spell that locks on only needs its target alive and in the caster's dimension), and a channel
checks the target before every tick, run at the end of each interval. Every end is a `spell/CastEnd` (completed, an
interrupt, or the failed check), sent to the caster and its trackers as `CastEndedPayload` and posted on the game bus
as `CastEndedEvent`, which procs (C3a-4) will use and the QA mod logs for `/qa spell show` and the GameTests
(`CastGameTests`). The effects run on the first server tick at or after the cast's end time.

**In code (C3a-3, RLM-211 and RLM-243):** a cast aims at an entity, a point or nothing (`spell/SpellAim`, what the
spell bar's payload will carry), and its targeting resolves that into a `spell/SpellTarget`: an entity (`self`,
`enemy`, `ally`), a point (`ground_point`) or a direction (`direction`), each with a position and a direction from
the caster's eyes. A ground point is the point aimed at, the feet of the entity aimed at, or else the first block the
caster looks at within range, and must be in range and in sight (`Targets#canSee`); a direction points at what was
aimed at, or else where the caster looks, and its position is its range along it or the first block in the way. Only
an entity target is checked again before a cast time's effects and a channel's ticks, and seen afresh then
(`SpellTarget#now`), so a projectile flies at where the target stands at the end of the cast; a point or a direction
stays as it began. **What counts as moving** (RLM-243): only the caster's own movement, a player pressing a movement or the
jump key (`ServerPlayer#getLastClientInput`, the keys its client last sent) or a mob walking or strafing (its
movement input, which its move control sets every tick it walks), while the caster is more than 0.1 blocks from
where the cast began (`Casting#movesByItself`). Being pushed is not moving: a melee hit's knockback, crowding mobs
or a water current move the cast's start point along instead, so a Mage hit by a zombie keeps casting. A spell's
`realmforge:knockback` interrupts the target's cast explicitly (`CastEnd.KNOCKED_BACK`), unless it may be cast while
moving. `CastGameTests` covers both, and `SpellEffectGameTests` the knockback.

### 11.3 Spell entities

Registries freeze at startup, so variety lives in data: one generic projectile entity type and one generic area
entity type (persistent ground zones), configured by the spell (speed, size, visuals, effects on hit). A new entity
type only for genuinely new behaviour.

**In code (C3a-3, RLM-211):** `spell/SpellEntities` registers `realmforge:spell_projectile` and
`realmforge:spell_area`; the spell's effects create them.

- **`realmforge:projectile`** (`ProjectileEffect`, `SpellProjectile`): launched from the caster's eyes along the
  target's direction at its `speed` (blocks per second), straight, without gravity or drag, with a hitbox of its
  `size`. It hits the first enemy of the caster in its way (never the caster, an ally or a spectator) and runs its own
  effects on it, then is gone; a block or the end of its `lifetime_ticks` ends it without them.
- **`realmforge:area`** (`AreaEffect`, `SpellArea`): a zone at the target's position (the caster's feet, an enemy's,
  a ground point) with a `radius`, which at the end of every `interval_ticks` runs its own effects on every enemy (or,
  with `affects: allies`, ally) standing within the radius of its middle, from half a block below it to 3 blocks
  above, for `duration_ticks` (a whole number of intervals).
- Both deliver their effects themselves: the hit's direct entity is the projectile or the area, its causing entity
  the caster, so kill credit and the hit direction work as for an arrow. Their effects read the caster's stats when
  they land; no snapshot is taken at the cast, as nothing (talents, procs) needs one yet. A spell entity ends when its
  caster leaves its level (logs out, changes dimension, a mob that died and was removed).
- **Never saved:** they carry a live cast (the caster, the spell), so a chunk that unloads or a server that stops
  drops them (`shouldBeSaved` is false), they use no portal, and commands cannot summon them. Only their size is
  synced; the client moves them and draws particles (a trail, a ring) with no-op renderers
  (`client/render/SpellEntityRenderers`) until C6 (RLM-29) gives spells their visuals. Their entity type names have
  no translation yet (RLM-259).
- **The other effect types:** `apply_effect` (a status effect from the caster, vanilla's until C3c, at most a minute:
  research SP-8) and `remove_effect` act on an entity; `teleport` moves the caster to the target's position (a ground
  point, the end of a direction's ray with its feet where its eyes would be, an entity's feet), stepping back toward
  the caster until its box is free, or not at all; `dash` throws the caster toward the target, or along a direction,
  with a burst of speed in blocks per tick; `knockback` pushes its target along the spell's direction (away from the
  caster, along a projectile's flight, out from an area's middle) with vanilla's knockback, which Knockback Resistance
  weakens, and interrupts its cast. A player moves itself, so a dash or a knockback reaches it as a velocity its
  client applies; the server moves mobs. `SpellEffectGameTests` casts every type.

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
  decays); a class picks one and sets its numbers in data. **In code (C3a-1):** each type says how big a holder's
  pool is and what it regains every second (`spell/ResourceType`), so `spell/Resources` needs no knowledge of the
  holder's class: mana's pool is Max Mana big, fills the first time its maximum is above 0 and regains Mana
  Regeneration every second (Matěj, 2026-10-14: a Mage starts with 100 and 5, from its class modifiers); rage's is
  100 big and starts empty, its gain and decay coming with C3a-4. A pool keeps its amount while its maximum reads 0
  (a respawned player before the first stat recompute) and drops to a lowered maximum otherwise.
- **Cooldowns:** a spell's own cooldown, shortened by Cooldown Reduction (at most 75 %, its stat's cap), and the
  global cooldown, 1 s by default (research SP-11), which a spell may shorten or switch off and which Cooldown
  Reduction never touches. Both are stored as the game time they end.
- Spell modifiers from talents (later glyphs and gear) are collected into a cached per-player view, invalidated
  when talents or gear change, and applied when a cast starts: cast time, cost, cooldown, damage, extra effects.

### 11.6 Client side

The spell bar, cast bar and cooldown sweep are HUD layers; key mappings follow a default scheme decided in M1
(proposal: a separate spell bar with its own keys, leaving the item hotbar alone); tooltips are built from data
with live numbers; animations and particles start on `CastStartedPayload`. `client/spell/ClientCasts` (C3a-2) keeps
every cast in progress the client was told of, the local player's and every tracked caster's, by entity id, from
`CastStartedPayload` until `CastEndedPayload` (or until 20 ticks after its end, for a caster the player stopped
tracking), so the cast bar and the animations read one store (`CurrentCasts#progress` for the bar). Cast animations are Emotecraft JSON
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
- **Item uses** (RLM-196): an item's `use` in content says what right-clicking it does, one item used up each time.
  Each use is game code in the feature it belongs to, which finds its items through the item tag
  `realmforge:use/<use>` that the pipeline writes; the code never names the items, so `character` needs no
  dependency on `item`. A use and an item's `flavor` line are its tooltip: the vanilla lore component on the item's
  default components, the use as a green "Use:" line from `content/texts/item_use.yaml`, the flavor line in vanilla's
  lore style. The first use, `unlearn_stat_points`, is the Scroll of Oblivion's ([10.2](#102-leveling)).
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

Built in W1 (RLM-31): `world/DimensionRules`, `world/MobLevelRule` and `mob/MobLevels`, with the scaling in
`mob/MobScaling`.

- **Which mobs:** every hostile mob, vanilla's and later ours: a `Mob` that carries vanilla's `Enemy` marker
  (`MobLevels#getsLevel`). Animals, villagers and golems get none. Bosses take the fixed level from their definition
  (W5).
- **The rule:** `realmforge:dimension_rules` (owned by `world`, server only) gives a dimension an optional mob level
  rule (`content/dimensions/`, [content README](../content/README.md)): the level at the dimension's origin (x 0,
  z 0), one level more every `blocks_per_level` blocks of horizontal distance from it, one more for every
  `depth.blocks_per_level` blocks below `depth.below_y`, then a random jitter of up to `jitter` levels either way,
  all clamped to `min_level .. max_level`. `MobLevelRule` is plain Java, and the pipeline computes the same
  (`MobLevelRuleDef.level_at`); a GameTest compares the two at sample positions. A dimension without a rule (the
  Nether and End for now) gives no level, and its mobs read as level 1. The Overworld's first numbers: level 1 at
  the origin, one more every 500 blocks up to 5, one more per 32 blocks below y 0, jitter 1.
- **The scaling** (`MobScaling`) comes from three curves at the mob's level: `realmforge:mob_health` multiplies its
  maximum health, `mob_armor` adds armor points, and `mob_damage` multiplies the damage of every hit it causes. The
  first two are permanent attribute modifiers, `realmforge:mob_level/max_health` and `realmforge:mob_level/armor`
  ([8.2](#82-where-modifiers-come-from)), saved with the mob. The health modifier is `ADD_MULTIPLIED_BASE`: it
  multiplies the base plus every flat bonus, and other base multipliers add to its multiple instead of compounding
  with it (+10 % on a mob at 1.4 times makes 1.5 times, not 1.54); `ADD_MULTIPLIED_TOTAL` modifiers multiply the
  result. Whether the level's multiple should compound instead is settled with the first other source of a mob's
  maximum health. Damage is a multiplier on `LivingIncomingDamageEvent` for every hit whose causing entity
  (`DamageSource#getEntity`) is a leveled mob, so arrows, explosions and thrown potions grow with the level too; only
  melee reads the attack damage attribute. The shipped curves add nothing at level 1, and a curve a data pack removed
  adds nothing.
- **At spawn:** in `FinalizeSpawnEvent`, `mob` rolls the level from the rules of the mob's dimension at the spawn
  position, writes it into `realmforge:level` and applies the scaling. A mob spawned with a new chunk
  (`NaturalSpawner#spawnMobsForChunkGeneration`, structure pieces such as a monument's guardians) is finalized on a
  world generation thread, in a `WorldGenRegion`; setting the synced level there would read the server's entity
  tracking (`ChunkMap#getPlayersWatching`) from that thread, so the event only marks such a mob, and its join, on
  the server thread, rolls the level and refills it.
- **Health refill on join:** the constructor filled health from the unscaled maximum, and raising the maximum does
  not refill it. NeoForge posts `FinalizeSpawnEvent` before vanilla's `Mob#finalizeSpawn`, which can still raise
  the maximum (the leader zombie bonus), so the event marks the mob (`realmforge:pending_refill`,
  [7](#7-player-state)) and `mob` sets its health to the maximum when it joins the level (`EntityJoinLevelEvent`),
  removing the mark. A mob loaded from disk or moving between dimensions carries no mark and is never refilled. The
  mark is saved because a mob spawned with a new chunk (`NaturalSpawner#spawnMobsForChunkGeneration`) waits in the
  chunk's data until the chunk is added to the world, and joins only then.
- **Rescaling keeps the share of health:** whenever `mob` changes a mob's scaling outside its spawn (a new level, a
  loaded mob, a conversion), the mob keeps its share of its maximum health (`MobLevels#applyScaling`).
- **Spawns without `finalizeSpawn`** skip the event: slime splits (`Slime#remove`), silverfish from infested
  blocks, `GameTestHelper#spawn`. Split children copy the parent's level and get its scaling in `MobSplitEvent`
  (`getParent`, `getChildren`). Any other mob that joins without a level (checked with `hasData`) is leveled in
  `EntityJoinLevelEvent` the same way: the level from the rules and its scaling, keeping its share of health (a
  fresh mob's is all of it). So the default level never passes for an assigned one, and no mob carries a level
  without its scaling. A mob saved before W1 is leveled this way when it loads.
- **Conversions** (`Mob#convertTo`: a zombie drowns, a skeleton turns into a stray, a piglin or hoglin zombifies, a
  zombie villager is cured). In 26.1.2 every vanilla caller posts `LivingConversionEvent.Post` from the conversion's
  callback, which `convertTo` runs before it adds the new mob with `addFreshEntity`. NeoForge's own listener
  (`AttachmentInternals.onLivingConvert`, normal priority) copies the old mob's copy-on-death attachments onto the
  new one, `realmforge:level` included ([7](#7-player-state)); `mob` listens at `EventPriority.LOW`, after the copy:
  a new mob that gets a level keeps the level it now holds and gets that level's scaling, and is synced with
  `syncData` (which reaches nobody before the add, and matters for a caller that adds first); a new mob of a kind
  that gets none (a villager) loses the level through `removeData` and any scaling of ours. A new mob that holds no
  level, because the old one had none (a pig struck by lightning), is left to the join fallback, which levels it
  from the rules when the add follows. `Villager#thunderHit` calls `finalizeSpawn` on the witch first, and
  NeoForge's method-redirector coremod (its `finalize_spawn_targets.json` lists `Villager`) turns that call into
  `EventHooks.finalizeMobSpawn`: so `FinalizeSpawnEvent` levels and marks the witch, the villager has no level to
  copy, the conversion listener keeps the witch's level, and its join refills it. Attributes are never copied by a
  conversion (`ConversionType`), so the new mob starts with only the scaling `mob` gives it. W1's GameTests convert a
  leveled piglin (summoned outside the Nether with its time there at 300 ticks, it zombifies on its next tick) and
  check that the zombified piglin's level and scaling belong together.
- **Saved mobs follow changed curves:** when a mob loads from disk (`EntityJoinLevelEvent` with `loadedFromDisk`),
  `mob` reapplies the scaling from its stored level and the current curves, replacing its `realmforge:mob_level/`
  modifiers (removing any it no longer produces) and keeping its share of health. A release or data pack that changes
  a curve then reaches old mobs too.
- XP (`character`) and loot (`loot`) read the level from `realmforge:level`; armor reads no level, only the hit's
  size, which mob scaling raises with the level ([9.1](#91-damage-pipeline)). The level
  shows with the mob's name and health bar, not apart from them (Matěj, 2026-10-14): through Neat, the pack's
  health bar mod, if it can show it; otherwise through another health bar mod or a Neat add-on that can; otherwise
  through health bars of our own (RLM-166, *verify* the chosen mod's API). Until then the dev game shows a mob's
  level, health, armor and damage multiple with `/qa mob` ([20](#20-tests-per-layer)), the boss bar a boss's.

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

A dungeon type (`content/dungeons/<id>.yaml`, the registry `realmforge:dungeon_type`, W4a) names the room pool it is
built from and the rules of its layout ([15.3](#153-generation-pure-first-then-minecraft)):

```yaml
# content/dungeons/test_dungeon.yaml (exists)
id: test_dungeon
name: {en_us: Test Dungeon}
pool: realmforge:stone_vault          # the rooms whose sidecar lists this pool
layout:
  main_path: [3, 6]                   # [min, max] rooms between the entrance and the ending
  branches: [1, 2]                    # side branches off the main path's rooms
  branch_rooms: [1, 2]                # rooms in each branch, its dead end included
  ending: treasure                    # the role of the room that ends the main path: boss or treasure
```

`rf gen` resolves the pool into the entry: each room's template, size, role, weight and connectors, in room id order
(`generate/dungeons.py`), so the game plans a layout without loading a template, and JUnit fuzzes the planner on the
same files. Validation checks that the pool holds an entrance, rooms to pass through (two connectors, a third when
the rules always want a branch), a room for the ending and a dead end, that every room in it has exactly one role tag,
and that every connector of every room meets some connector of the pool. `realmforge:test_dungeon` is test content
like `realmforge:test_item`: nothing in the game opens it, and W6 brings the real types. What later tasks add, as a
sketch:

```yaml
# planned fields (names are examples)
placement: instance                  # world_structure later (M4)
theme: realmforge:crypt              # block palette: a processor list (see below)
ending: {type: realmforge:boss_lair, boss: realmforge:hollow_warden}   # or realmforge:treasury, ... (W5, W6)
encounters: {budget_per_room: [2, 4], mobs: realmforge:crypt}           # W6
loot: {chests: [2, 4], table: realmforge:dungeon/sunken_crypt}          # W6
level: [8, 12]
open_minutes: 40                     # how long a portal to it stays open (W4d)
key: realmforge:crypt_key            # the key that opens a portal to it (W4d)
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
  theme's processor list. The tables exist since A4d: `content/room_themes/<id>.yaml` names a block for each role
  the room generator builds with, each role its own block, so a swap from the theme a room was built with to
  another can tell the roles apart ([15.4](#154-rooms)).
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

Steps 1 and 2 exist since W4a (`dungeon/plan/`; `DungeonType#plan(seed)` → `DungeonPlan`):

- **The graph** (`RoomGraph`) is a tree in placement order: the entrance, a main path of rooms a player passes
  through (role `hallway`, `combat` or `stairs`), the ending (role `boss` or `treasure`), then the branches, each
  hanging off a different room of the main path while there are enough and ending in a dead end. Lengths and counts
  are drawn from the rules' ranges.
- **The layout** (`DungeonPlanner`) places the nodes one by one, parent first, as a depth-first search: a room that
  fits the node's slot (by role and connector count), in a random order where a heavier room tends to come first,
  turned four ways about its origin corner, with one of its connectors taking a free connector of its parent by
  vanilla's jigsaw rules (`JigsawBlock#canAttach`: opposite fronts, the source's `target` is the other's `name`, an
  aligned vertical joint keeps its top). The new room's connector block sits right in front of the parent's, its box
  must lie inside the plot's reach (`PlotGrid#limits`) and overlap no placed room, and a node with no such placement
  sends the search back to the node before it. Once every node has a room, each connector still free meets a free
  connector of a neighbour right in front of it (a loop) or gets a dead end (slot `cap`, role `hallway`, `combat`,
  `stairs` or `treasure`); a connector neither closes sends the search back too. The positions and boxes come from
  `StructureTemplate.transform`, the builder's own math.
- **Attempts:** an attempt that runs out of steps (20,000 placements) gives up, and the next draws a new graph from
  the same random source, up to 64; no layout in 64 attempts throws `PlanningException`. The entrance's origin
  corner is the plot's origin, so every room offset is relative to it.
- **Cost:** on the test dungeon (23 rooms, layouts of 8 to 17 rooms) planning took 3.5 ms at the median, 24 ms at
  the 99th percentile and 44 ms at worst over 3,000 seeds, with at most 11 attempts. It reads no world, so it can
  move off the main thread if the portal's entry path (W4d) needs it.

JUnit fuzzes steps 1 and 2 over thousands of seeds, on synthetic room sets and on the generated dungeon types
(`DungeonPlannerTest`, `PlanChecks`): every rule holds, no rooms overlap, every room is reachable from the entrance,
no connector stays open, and the same seed gives the same layout. `ContentDungeonTypeGameTests` checks in the game
that every room's connectors are the jigsaw blocks the game reads from its template in every rotation, and
`DungeonPlanGameTests` builds a layout of every type into a plot. `/qa dungeon plan <type> [<seed>]` names a layout's
rooms by slot and writes a top-down map and a legend to the server log; `/qa dungeon build <type> [<seed>]` builds it
into a plot for a fly-through, and `/qa dungeon enter <type> [<seed>]` builds it and moves you in at its entrance
([15.5](#155-instances), [20](#20-tests-per-layer)).

Step 3 exists since W4b ([15.5](#155-instances)), with the **data markers**: a room marks spots with structure blocks in
data mode whose metadata names the marker (`DataMarker.Kind`):

| Marker | Where | Used by |
| --- | --- | --- |
| `entrance` | where players arrive; one at most, in an entrance room only | W4b |
| `exit` | where the way out stands | W4d |
| `mob` | a spot the dungeon's encounters put a mob on | W6 |
| `chest` | a spot the dungeon's loot puts a chest on, facing the room's middle ([ADR 0036](decisions/0036-room-lights-and-decorations-form-patterns.md)) | W6 |

When a plot opens, the builder reads them from each room's template as vanilla's `SinglePoolElement#getDataMarkers`
does (`StructureTemplate#filterBlocks` with the room's placement settings, so they turn with the room), and the plot
keeps each one's kind, room and position relative to its origin. Placing a room leaves them out
(`BlockIgnoreProcessor.STRUCTURE_BLOCK`), so none stands in the world. No content room holds a marker yet (none of the
imported sources had any); the spike test rooms hold an entrance, a mob and an exit marker.

### 15.4 Rooms

Rooms are generated or imported; nobody has to build one by hand
([ADR 0028](decisions/0028-dungeon-rooms-generated-or-imported.md)).

- **Format:** structure NBT in `content/rooms/` with a small YAML sidecar (tags, pools, weight, and the source of
  an imported room). A room joins the room pools its sidecar lists, and a dungeon type is built from one pool
  ([15.2](#152-dungeon-types)); its role tag says which slots it can take. Connectors are vanilla jigsaw blocks,
  so the same rooms work in world structures later. Rooms are the second exception to YAML-only content, after
  worldgen JSON (ADR 0014 amends [ADR 0005](decisions/0005-content-pipeline-yaml-single-source-of-truth.md)). A
  room's id is its path below `content/rooms/` (`third_party/stonevaults/hall_a`); `rf gen` writes the template,
  normalized, to `data/realmforge/structure/rooms/<id>.nbt`, so the game knows it as the structure
  `realmforge:rooms/<id>` (`tools/realmforge_tools/generate/rooms.py`). A template keeps the data version it was
  saved with, and vanilla upgrades it from there when it loads it (`TemplateSource#readStructure`), so imported
  rooms from older versions stay as they were saved. A template without one is refused: vanilla would read it as
  version 500 and run every upgrade since on it.
- **Generated rooms** (A4d): a room spec (`content/room_specs/<id>.yaml`: kind, a footprint up to 16×16, height,
  connector sides, features, the theme, pools and weight) and a seed become a structure template through the room
  generator (`rooms/generator.py`); every kept seed ships as `realmforge:rooms/generated/<spec>/seed_<n>`, written
  by `rf gen` and never edited by hand. The kind gives the role tag (`entrance`; the hallways `corridor`, `curve`,
  `t_junction`, `crossing`, `dead_end`; `combat`; `treasure`; `arena`, role `boss`) and the connectors the shape
  tag. A theme (`content/room_themes/<id>.yaml`) names the block of each neutral role (`floor`, `floor_border`,
  `wall_base`, `wall_body` and its cracked and mossy patches, `wall_cornice`, `ceiling`, `pillar`, `stairs`,
  `slab`, `rubble`, `wall_light`, `ceiling_light`, ...), and a generated room holds nothing else besides air, its
  jigsaws and its markers. The style rules: walls one block thick in three layers (base, body with patches,
  cornice); a floor border along the walls and in the doorways and a seeded pattern in the middle; doorways three
  wide and high in the middle of each connector side, with a jigsaw `realmforge:door` at the foot like the
  imported rooms', so the two kinds join; three-wide paths from every doorway to the middle that pillars, rubble,
  niches and stairs keep clear of; profiles with their facing worked out (a moulding of upside-down stairs or top
  slabs under the ceiling, corbels under pillar capitals, the step around a pit, stairs up to a balcony's deck of
  top slabs, corner shapes by vanilla's `StairBlock` rule); lights in patterns that leave no cell dark where a mob
  could spawn (`rooms/lights.py`, [ADR 0036](decisions/0036-room-lights-and-decorations-form-patterns.md)): wall
  lights all at one height, two free blocks from a wall's ends and three apart, balanced on opposite walls, only on
  pilasters and pillars where a room has them, flanking all doorways or none, lanterns in big rooms, preferring
  combinations the room's symmetry maps onto themselves; and markers by kind: an `entrance` in the entrance's
  middle; `mob` in combat rooms and arenas, on full blocks of the floor (ground mobs, never on a step or the
  balcony), spread evenly (the middle of each spot's share of the floor, at least three blocks apart, a block clear
  of the walls and three of the doorways); five `chest` spots in a treasure room, mirrored across the doorway's line
  where the niches allow, in the niches or against the walls. Markers and lights are built last, so changing them
  leaves the rest of a kept room as it was. The features are `pillars`, `alcoves` (niches in the walls without
  a doorway), `pit` (a sunken middle one block deep; the floor is then two blocks thick), `balcony` and `rubble`.
  The seed picks the variations (floor pattern, patches, moulding, pillar layout, niche rhythm, rubble, lights,
  markers), and the same spec, theme and seed always give the same room. A change to the generator therefore
  changes the kept rooms too: `rf gen` shows it, and a change a player would see goes back to the gallery.
- **Imported rooms** (A4b, A4c): MIT, CC0 and LGPL-3.0 rooms from other mods, taken in by `rf rooms import` from
  `.nbt`, `.schem` or `.litematic` files with a rules file that remaps blocks, block entities, jigsaw names and
  pools, container loot tables and entities and picks the files that are rooms
  ([tools/README](../tools/README.md#rooms)); the rules of each source live in `art/rooms/<source>/rules.yaml`
  ([art/rooms](../art/rooms/README.md)). A4c imported 122 rooms from five sources (Stonevaults, DungeonZ and its
  Desert Dungeon addon, Dungeon Now Loading, Mo' Structures): only pieces that are rooms, hallways or stairs of a
  dungeon, with a connector, at most 32 blocks per axis, and no copy of a vanilla template, and only what Matěj's
  gallery review kept. The plainest sets are dressed on import (`rooms/dress.py`): mossy, cracked and gravel variants
  on surfaces with too few materials, vines, cobwebs and barrels, more the plainer the room. On import, jigsaws
  inside a room (props, mobs, sub-pieces the source pulls in) become the block they leave behind, every connector
  is named `realmforge:door` (sides) or `realmforge:shaft` (up, down) with the pool `minecraft:empty`, marker blocks
  the source's processors replace at generation (red wool for rock, stained glass for legs) become what those
  processors make of them, since our dungeons place templates without
  them, a source's own loot tables are dropped (the dungeon fills its chests, W4), only decorative entities stay,
  and the sidecar gets draft slot tags from its connectors and its name
  ([content/README](../content/README.md)). Each source has its own folder, `content/rooms/third_party/<source>/`,
  with its `LICENSE`, a `NOTICE` and, for LGPL-3.0, `COPYING` (the GPL-3.0 text). The folder is a license
  boundary, not a pool, so the sidecars name the pools. The jar keeps the folder (LGPL-3.0 rooms stay apart from our
  assets) and carries the notices under `META-INF/third_party/<source>/`: `rf gen` copies them for every source
  with a room there, and `mod_license` names the added licenses. Vanilla templates are referenced by id, never
  copied.
- **Hand-built rooms** stay possible: built with structure blocks and saved as NBT with a sidecar like any other,
  or exported as a schematic and taken in by `rf rooms import --ours`, which marks the sidecar's `source` as ours.
  Validation keeps every other imported room in `third_party/<source>/`, where its notices are required.
- **Review:** the room gallery (A4e, `rf rooms gallery`) renders rooms headless through nucleation, with the client
  jar's models and textures read at runtime; Matěj keeps or rejects them in batches. Every data marker shows with
  its label, drawn in its label's colour (also above a cutaway's cut) and listed on the room's card under a legend,
  because where a room puts its markers is reviewed like its blocks
  ([ADR 0035](decisions/0035-room-galleries-show-every-marker-with-its-label.md)). `rf rooms review` records the
  decisions in a `review.yaml` in the reviewed folder: rejected imports are dropped. A reject decision stays after
  its room is dropped, and `rf rooms import` skips the rooms a review rejects, so importing the source again does
  not bring them back. Generated rooms are reviewed as candidates: `rf rooms candidates` builds the next seeds of
  each spec that no review decided on into `.cache/rooms/candidates/<spec>/seed_<n>.nbt`, and `rf rooms review`
  on that folder freezes the decisions into the specs (a kept seed joins `seeds`, a rejected one
  `rejected_seeds`, so the next candidates are new). Folders inside the generated directories are not reviewed:
  `rf gen` would delete their `review.yaml`.
- **Validation:** size limits (48 blocks per axis, a structure block's maximum), connectors on the room's boundary
  facing out, known markers, only blocks the themes can swap and only ids inside the ID universe (an imported
  room's own blocks are remapped, and a container's loot table must exist), and `LICENSE` and `NOTICE` next to
  third-party rooms (`COPYING` next to LGPL-3.0 ones). `rooms/check.py` checks the size, the data version, the
  ids, the loot tables, the connectors, each jigsaw's pool, which must be a vanilla template pool or a room
  pool some sidecar lists (it becomes a template pool for world structures, M4), and the data markers: every
  structure block is one, in data mode, naming a marker of [15.3](#153-generation-pure-first-then-minecraft), with one
  entrance marker at most. A room in a dungeon type's pool also holds no fluid (water, lava, a waterlogged block,
  kelp or seagrass) that can reach its sides or floor: a fluid flows down and sideways, and from there out of the
  room's box, past what cleanup clears ([15.5](#155-instances)). `fluid_edges` follows each fluid down and sideways
  through every cell a flowing fluid can enter (`FlowingFluid#canHoldFluid`), so a fluid beside a floor hole or a
  wall gap fails too; leaving through a connector is fine, as the next room continues there (RLM-217). Python knows
  only block names, so a cell stops the fluid only when it surely does: a waterloggable block (it takes water only
  as a source), a door, a sign, a ladder, sugar cane, a portal, or a full block in `SOLID_BLOCKS` (a name or an
  ending such as `_bricks`). Anything else lets it through, an unknown block and a missing cell included; a room that
  fails on a solid block the list lacks gets the block added. A fluid on the roof of a closed room stays inside. A
  pooled room holds an entrance marker only if it is an entrance (`validate.py`). Thirteen imported rooms have fluids
  that can flow out and are in no pool. Generated rooms are also checked by `rooms/walk.py`: every connector's
  doorway has three blocks of headroom over a floor, every connector can be walked to from every other (steps of
  one block at most, three blocks of headroom all the way, no parkour), and no cell where a mob could spawn (a
  block below, two passable cells) is without block light, since the instances' dimension type lets monsters
  spawn only at block light 0. Light spreads one level less per step through passable cells; a stair or slab
  counts as blocking it, so the check errs on the dark side. A generated room also holds only its theme's blocks,
  air, jigsaws and markers. Validation checks every theme's blocks against the ID universe and against what their
  role needs (a full solid block, stairs, a slab, a wall torch, a lantern), and keeps `content/rooms/generated/`
  free for the generated rooms. Imported rooms are not held to the theme blocks yet: W4's themes decide how they
  are swapped. In the game, `ContentRoomGameTests` (QA mod) loads every room through
  the structure manager, which upgrades an imported room from its data version, and compares its size, jigsaws and
  air blocks with `GeneratedRoomExpectations`, and checks that every structure block in it is a data marker the
  builder knows.

### 15.5 Instances

Spike 3 built this and measured it; the numbers and the reasons are in
[ADR 0019](decisions/0019-dungeon-instances-in-plots.md), and W4b's changes to it in
[ADR 0031](decisions/0031-instance-builder-background-chunks-and-shared-budget.md). Code: `dungeon/DungeonFeature`,
`dungeon/DungeonAttachments` and `dungeon/instance/`.

- `InstanceManager` keeps a `SavedData` (`realmforge_instances`) in the instance dimension
  (`DimensionDataStorage#computeIfAbsent`): a grid of plots 1,024 blocks apart (`PlotGrid`; Compact Machines uses the
  same spacing), filled row by row, each plot's rooms within 256 blocks of its origin at y 64. Each plot has its
  state (building, ready, active, completed, closing, free), layout (its rooms and the block an arriving player looks
  at), its data markers, where players arrive, the boxes of its rooms and of blocks still to clear, its work
  progress, its party and the chunks it holds. The party is a list of players: solo in M1 (decision D8), parties
  later (RLM-68) without a data migration. Each player's run and return point live in the player's
  `realmforge:instance` attachment ([7](#7-player-state)), with the game mode entering took from them (W4c).
- **Entry:** an entrance in the world (W4d: a temporary portal opened with a dungeon key, D8) → allocate a plot
  (`InstanceManager#open`: the rooms' boxes and data markers from their templates, lowest free plot first) → load
  its chunks in the background, then hold them → build over ticks → find where players arrive → teleport the online
  party members there (`ServerPlayer#teleportTo(ServerLevel, x, y, z, yaw, pitch)`), looking at the layout's way on:
  the entrance's connector to the next room of the main path (`InstanceLayout#lookAt`). Players arrive at the
  entrance room's entrance marker; without one, `SpawnFinder` takes the spot nearest the middle of the entrance room
  where a player stands on a sturdy floor with two free blocks (no collision, no fluid), the lowest of equally near
  ones, and the middle of its roof when there is none. `/qa dungeon enter <type> [<seed>]` enters a planned dungeon
  until W4d's portal; `/qa instance enter` builds the spike test rooms in a row.
- **Room templates load when the server starts** and after a reload, which empties the structure manager's cache
  (`ServerStartedEvent`, `OnDatapackSyncEvent` without a player): every room of every dungeon type, so no run waits
  for a template to load and upgrade from its data version (up to 54 ms each in spike 3). A reload posts its sync
  event inside its server task before it empties the cache (`MinecraftServer#reloadResources`), so that loading is
  queued behind the task (`InstanceManager#preloadAfterReload`).
- **Building over ticks:** placing a room and clearing a box are cut into units of one chunk column each
  (`PlotWork`; placing uses `StructurePlaceSettings#setBoundingBox`, known shapes and update flag 2 like structure
  blocks, and `JigsawReplacementProcessor`, so every connector becomes its final state as in vanilla's jigsaw
  structures). The builder runs in `LevelTickEvent.Post` of the instance level, so its time shows in the server's
  tick time. Each tick, every plot being built runs one unit whatever the budget, then the rest of 5 ms goes round
  the plots with work one unit at a time, starting after the plot served last; some unit always runs. Plots opened
  together build side by side, and clearing a closed plot never holds back a run's build. The unit list is a pure
  function of the saved plot, so a restart resumes at the saved step, and a unit whose chunk is still loading waits
  for a later tick instead of loading it on the main thread. A unit that throws (a room template that no longer
  resolves after a restart) releases a plot being built and is skipped by a closing one, and is logged, instead of
  failing the tick on every start.
- **Chunk loading:** from opening until it is free, every chunk a plot touches (its rooms with the cleanup margin)
  is held by the NeoForge `TicketController` `realmforge:instance_plots` (registered in
  `RegisterTicketControllersEvent`, owner: the plot's origin, non-ticking). Its tickets survive restarts; its
  validation callback drops the tickets of plots that are free or unknown when the level loads.
  `TicketController#forceChunk` loads a chunk that is not loaded yet on the main thread (spike 3: 2-80 ms per plot,
  and 0.3-1 s the first time a plot cell is used, as vanilla generates and saves the neighbours of a forced chunk
  too, 3-4 MB per cell). So a plot first asks for its chunks with a background ticket of our own
  (`realmforge:instance_loading`: loading only, renewed every tick until the chunk has loaded and gone 20 ticks
  later, not saved), and the controller holds each chunk only once it has loaded. After a restart, a plot asks again
  for the chunks it does not hold, and a build released before any of its units ran loads none. The GameTest server
  runs its ticks back to back, so the background loading of a new plot cell (seconds) would outlast the tests' tick
  timeouts; there the QA mod loads the chunks plots wait for before each tick (`GameTestChunkLoads`).
- **Completion:** a run whose goal is done (its final boss died; W4d adds the trigger, the message and the timer) is
  `completed` (`InstanceManager#complete`): its players stay until they leave or the plot closes.
- **Rules inside: nobody edits the rooms** (W4c, `dungeon/instance/InstanceRules`). Survival players are switched to
  Adventure mode when they enter (creative operators keep theirs, and so do players in Adventure or spectator mode);
  their visit remembers survival, and leaving gives it back, unless something else changed the game mode since (an
  operator's `/gamemode`). Every way out restores it: `leave`, the respawn and teleport below, and the next login
  outside a live run, also after a crash. A login inside a live run switches a survival player to Adventure again,
  in case the server forces its default game mode.
  Vanilla then refuses every player edit, including buckets, flint and steel and tool edits such as stripping or
  tilling (`Player#mayUseItemAt`, `ItemStack#useOn`), and the client predicts the refusal, so there are no ghost
  blocks; doors, levers, chests and combat still work. Edits by anything else are stopped inside the instance
  dimension: entities placing blocks (`BlockEvent.EntityPlaceEvent`), fluids creating blocks
  (`BlockEvent.FluidPlaceBlockEvent`: its caller ignores cancelling and places the event's new state, so the guard
  sets that back to the block that was there), trampled farmland (`BlockEvent.FarmlandTrampleEvent`), mob griefing
  (`EntityMobGriefingEvent`) and explosions (the block list of `ExplosionEvent.Detonate`, which the explosion's fire
  uses too; it still hurts and pushes entities). Adventure mode still
  lets an item with the `can_break` or `can_place_on` component (`DataComponents.CAN_BREAK`, `CAN_PLACE_ON`) edit
  matching blocks, and commands, data packs or other mods can hand out such items, so block breaks
  (`BreakBlockEvent`, posted on both sides, so the client predicts it) and placements
  (`BlockEvent.EntityPlaceEvent`) by players who are not in creative mode are cancelled there too. Buckets get past
  both events: `BucketItem#use` (`MobBucketItem` included) checks
  `Level#mayInteract` (spawn protection, which covers only Overworld, and the world border) and
  `Player#mayUseItemAt`, which a matching `can_place_on` passes, then places or picks up the fluid with
  `Level#setBlock` and posts no block event, and `CommonHooks.onPlaceItemIntoWorld` captures no block snapshots for
  a `BucketItem`. So `PlayerInteractEvent.RightClickItem` is cancelled for a `BucketItem` used by a player who is
  not in creative mode inside the dimension. `BucketItem` has no `useOn`, so a bucket reaches `use` only through
  that event, and `RightClickBlock` stays alone: doors, levers and chests keep working with a bucket in hand. The
  event fires on the client too (`MultiPlayerGameMode#useItem`), so the same listener stops the client's
  prediction and no ghost fluid appears. `InstanceRulesGameTests` use a bucket (a plain one, and water, lava and
  empty buckets with `can_place_on` matching a room block, placing and picking up), flint and steel, an axe, a hoe, a
  tool with `can_break`, and place and break blocks inside an instance, and expect the room unchanged; an explosion
  with fire, a fluid's block, a mob's placement and griefing, and trampling are checked inside and outside the
  dimension.
- **Respawn at the entrance** (W4c, Matěj's decision of 2026-10-11): `PlayerRespawnPositionEvent` (posted by
  `PlayerList#respawn` before it makes the new player entity) moves a player who died in a playable run to its
  arrival spot, still inside and in the same run, looking along the way on; the visit is copied on death, and the
  respawned player keeps Adventure mode. Their bed or anchor stays their spawn point outside (vanilla still uses an
  anchor charge when the anchor was the original respawn target, as `findRespawnPositionAndUseSpawnBlock` runs before
  the event). A run released while its player lies dead does not move the body (`release` skips dead players): the
  respawn takes them to their return point instead, which ends the visit. Leaving the End is no death and is left
  alone.
- **Also inside:** no natural spawns, leave through the exit portal or a command.
- **Leaving and disconnects:** leaving by command or exit, respawning outside the dimension (at the return point
  after the run was released, above; `PlayerEvent.PlayerRespawnEvent`) or being teleported out (`PlayerEvent.PlayerChangedDimensionEvent`) leaves the
  run; a run whose party is empty is released, built or still building. A party member who has not entered yet (the
  run is still being built, or built and waiting) can leave it too (`InstanceManager#leave`, `/qa instance leave`),
  and is never moved in. A disconnect keeps the run. On login
  (`PlayerEvent.PlayerLoggedInEvent`) a player inside the dimension stays if their run is still playable and is sent
  back to their return point otherwise; a player outside it drops out of a run they never entered (they disconnected
  while it was being built). W4 adds a timeout for party members who stay offline.
- **Cleanup and plot reuse: clear, then reuse** (spike 3). When a run is released, its blocks are cleared over the
  next ticks (containers emptied first so nothing drops, entities other than players removed), its rooms and 4 blocks
  around each (`PlotWork.MARGIN`) for what spreads past a wall while players are inside: fire, a vine, a creative
  operator's block beside a wall. Room validation keeps fluids from reaching the sides and floors of the
  rooms dungeons build ([15.4](#154-rooms)), so nothing flows further. Then its chunks are released and the plot becomes free for the next
  run; nobody waits for it. Overwriting on reuse doubled the next run's build time, and never reusing plots grew the
  world by 3 MB per run ([ADR 0019](decisions/0019-dungeon-instances-in-plots.md)); W4b removed both, and
  `/qa instance bench` measures clear then reuse.
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
    it). Leveling's `AdvancementProgressEvent` listener raises the level cap (C2, [10.2](#102-leveling)). Character XP rewards use a `CommandReward` that
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
    content: `realmforge:level_reached` (exists since C2: `progression/LevelReachedTrigger`, fired with the
    character level whenever it changes and on login; a criterion names `"level": {"min": 5}`, as the cap can lift a
    player past an exact level), `boss_defeated`, `dungeon_completed`, `spell_cast`, `talent_learned`.
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
| magic shield | synced attachment, to the owner, on every change (a hit, a refill) |
| entity level | synced attachment, to the player itself and everyone tracking the entity |
| boss phase | the boss bar's title (`ServerBossEvent#setName`) |
| events: cast started or ended, combat text, dungeon status | payloads, for example `PacketDistributor.sendToPlayersTrackingEntityAndSelf` |

Cast bars and cooldowns are drawn from a start time and a duration, so they need no per-tick packets.

### 17.3 First payloads (M1)

- Client → server: `CreateCharacter` (exists: `character/net/CreateCharacterPayload`, sent by
  `client/screen/CharacterCreationScreen`), `SpendStatPoint` (exists: `character/net/SpendStatPointPayload`, sent by
  `client/screen/CharacterSheetScreen`), `CastSpell`, `CancelCast`, `SetSpellBarSlot`, `LearnTalent`,
  `ToggleDarkVision` (exists: `world/net/ToggleDarkVisionPayload`, sent by `client/input/DarkVisionKey`).
- Server → client: `CastStarted` and `CastEnded` (exist: `spell/net/CastStartedPayload` and `CastEndedPayload`, kept
  by `client/spell/ClientCasts`), `CombatText` (exists: `combat/net/CombatTextPayload`, shown by
  `client/hud/CombatTextHud`), `HitDirection` (exists: `combat/net/HitDirectionPayload`, shown by
  `client/hud/HitDirectionHud`), `DungeonStatus`.

## 18. Client code: rendering, GUI, input

- **Entry point:** `client/RealmforgeClient`, annotated `@Mod(value = Realmforge.MOD_ID, dist = Dist.CLIENT)`, which
  FML constructs only on the client. It registers HUD layers (`RegisterGuiLayersEvent`: spell bar, cast bar,
  resource bar, combat text, the magic shield's bar), key mappings (`RegisterKeyMappingsEvent`), entity renderers
  (`EntityRenderersEvent.RegisterRenderers`), particles (`RegisterParticleProvidersEvent`), menu screens
  (`RegisterMenuScreensEvent`), tooltip components (`RegisterClientTooltipComponentFactoriesEvent`) and the handlers
  of client-bound payloads (`RegisterClientPayloadHandlersEvent`, [17.1](#171-rules)).
- **Layout:** `client/hud`, `client/screen`, `client/input`, `client/render` (the spell entities' renderers, C3a-3),
  `client/tooltip`, `client/network`, `client/spell` (the casts in progress, C3a-2), `client/animation` (the PAL cast
  layer), and `client/compat/<mod>` for the client parts of integrations (JEI plugins, Jade tooltips).
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
- **What exists (F5, F4b, revised after Matěj's playtest of 2026-10-13):**
  - The combat text layer (`client/hud/CombatTextHud`) draws each `CombatTextPayload` over the entity it is about,
    popping in, rising and fading. A text keeps its entity's id and follows the entity every frame (its interpolated
    position) while the client has it, from where it was last seen once it is gone (dead, unloaded). It is projected
    onto the screen with `GameRenderer#projectPointToScreen` every frame, and its size follows its distance from the
    camera (`client/hud/CombatTextScale`: 1.75 times the font at 4 blocks, by the square root of the distance ratio,
    within 0.65 and 2.5, so it keeps shrinking up to about 29 blocks; Matěj's playtest, 2026-10-14: RLM-185). Texts
    spread at random around the entity in the world, up to 0.35 blocks sideways across the view and 0.25 upward, so
    hits close together do not cover each other. Every text is bold. A magic school's hit takes the school's colour;
    every other hit (melee, ranged, the Physical school) takes the Physical school's orange, red on a crit; a tick of
    a damaging effect takes the effect's colour, dark green for poison (`combat/CombatText` picks it). A crit's
    number ends with "!", a heal reads "+4" in green, a dodged hit reads "Miss!" in yellow, and the player's own
    dodge shows it beside the crosshair, at a fixed size. The player sees no numbers for damage taken.
  - The hit direction layer (`client/hud/HitDirectionHud`, after the MMM Indicators mod) draws a red arc around the
    crosshair toward each recent attacker for 1.5 s, turning with the camera and following the attacker.
  - The magic shield layer (`client/hud/MagicShieldHud`, RLM-160; Matěj, 2026-10-14) draws a faint bar (alpha 80 of
    255, about 31 % opaque, so the hearts stay readable) over the bottom row of hearts, inside a 1-pixel border with
    rounded corners: its straight edges stop 2 pixels short of each corner and one pixel closes the round, so the 3
    corner pixels outside it form an L. Every 10 shield points fill the bar once, in the next colour, layered over
    the tier below as Overflowing Bars layers hearts: light blue up to 10 points, then blue, purple, magenta, and red
    from 40 up (full from 50); 25 points show purple over the left half and blue over the rest. The border takes the
    top tier's colour, halfway to white (`MagicShieldBar`). Nothing shows while the shield is empty. A second layer,
    below vanilla's health layer, notes where that row goes (`Gui#leftHeight`), so the bar follows layers that raise
    the left column.
  - The character sheet (`client/screen/CharacterSheetScreen`, opened and closed by `client/input/CharacterSheetKey`,
    K by default) draws `character/CharacterSheetView`: the player's name and level in bold ("Steve · Lv. 12"),
    then four tabs of two columns (a click, the number keys, Tab or the arrow keys switch them). The heading and the
    selected tab are white, and gold marks only the section titles. Stats: the primary stats with what they give and
    their milestones, and the other attributes (luck, mining speed, reach, movement speed, swimming speed, the XP
    multiplier); Combat: offense, and defense with spell resistance last; Magic: spells (max mana, mana regeneration,
    spell power, cooldown and cast time reduction) and the spell schools' power and resistance; Origin (C1): the race
    with its description and what it gives (its racial passive and active once spells exist), and the class with its
    description, then a placeholder for the specialization until specializations exist (its text still reads
    "Subclass: WIP" until RLM-254). The names the sheet gives vanilla attributes (Mining Speed, Movement Speed, ...)
    apply on their rows and in the primary stats' bonuses. A stat of ours that no section
    names shows under Other. Values round for display only (Matěj, 2026-10-14): a percentage half up to a whole
    number (100.6% reads 101%), any other number half up to one decimal (6.45 reads 6.5). For a player with a
    character the Primary Stats title ends with "Unspent: 3", "Unspent:" white and the number yellow while any are
    left (Matěj, 2026-10-16), and while any are unspent a small green `+` beside each primary stat spends one on it
    (`SpendStatPointPayload`, RLM-196). Hovered, it says "Spend a point on Strength" and beneath it, in the section
    titles' yellow and aligned right under the stat's name (`client/tooltip/RightAlignedLines`, a tooltip component),
    what the point gives: each per-point bonus that still grows and the bonuses of a milestone it reaches, with one
    decimal (whole percents would read Dexterity's 0.1% crit chance as 0%). All of it comes from the player's synced
    attributes, level and character and the synced registries; a pet tab waits for the pet system (RLM-161).
  - The XP bar (`client/hud/XpBarHud`, C2) draws `character/XpBarView`: as wide as the hotbar, above the health and
    food rows and every other status row. NeoForge stacks those rows through `Gui#leftHeight` and `Gui#rightHeight`;
    the layer, registered above `VanillaGuiLayers.AIR_LEVEL`, reads them, so the bar rises with more heart rows, and
    raises them, so later layers (the selected item's name) stay above it. In creative, where no status row is
    drawn, it keeps clear of vanilla's experience level. It fills purple toward the next level and shows full and
    gold while the level cap holds the player, who then gains no XP; the level ("Lv. 5") sits on its middle,
    outlined like vanilla's experience level. Vanilla's experience bar keeps its place, as vanilla XP still pays for
    enchanting (D3). It shows only for a player with a character, never in spectator mode.
  - Character creation (`client/screen/CharacterCreationScreen`, C1) draws `character/CharacterCreationView`: the
    races on the left and the classes the chosen race allows on the right, as vanilla buttons with a gold outline on
    the chosen one, each with its description and what it gives ("+5 Strength"); a class also its resource and
    starting kit. It opens and closes by itself ([10.1](#101-races-and-classes)).
- **Minecraft 26.1 draws GUIs by render-state extraction:** a HUD layer (`GuiLayer`) and a screen
  (`Screen#extractRenderState`) receive a `GuiGraphicsExtractor` (`text`, `centeredText`, `fill`, `pose()` for
  scaling). A layer registered through `RegisterGuiLayersEvent` checks `options.hideGui` itself.
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
  While the switch is on, every other night vision is refused: `LivingEntity#addEffect` would merge it into Dark
  Vision through `MobEffectInstance#update`, which copies the newcomer's particles and icon onto the infinite
  effect, so `DarkVision` answers its `MobEffectEvent.Applicable` with `DO_NOT_APPLY` (a potion, a splash potion, a
  tipped arrow, suspicious stew, `/effect give`, any amplifier: night vision has no stronger level, and Dark Vision
  outlasts them all).

### 19.2 Configuration

`ModConfigSpec` only for operator and UI settings (instance limits, timeouts, debug flags, HUD options). Gameplay
numbers are content, so they are versioned, validated and tested.

### 19.3 Mixins and access transformers

Last resort, only where no event or API exists. Each one sits in `mixin/` (or the access transformer file) with a
comment saying why, and gets a test. A mod lists its mixins in `<modid>.mixins.json`, which `neoforge.mods.toml`
names under `[[mixins]]`. The core mod's one is `RangedAttributeAccessor`, which raises attribute caps
([8.4](#84-caps)). The QA mod has one too, for tests only: `GameTestServerMixin` gives the GameTest world the data
pack dimensions, which vanilla leaves out ([20](#20-tests-per-layer)).

Access transformers live in `src/main/resources/META-INF/accesstransformer.cfg`, which ModDevGradle applies to the
Minecraft jar a project compiles against (its default location), and which `neoforge.mods.toml` lists under
`[[accessTransformers]]`, so FML applies it when the game loads: in a dev run (the runs live in `qa-mod/`, whose own
jar lacks the core mod's transforms), on the GameTest server and in the pack. The core mod's one opens
`LivingEntity#lastHurt` for the hurt cooldown ([9.1](#91-damage-pipeline)); the QA mod's opens `lastHurt` and
`damageContainers` for `CombatGameTests` and `CombatStatGameTests`.

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
that agents can read. So far:

- `/qa instance enter|leave|build|free|list|bench` from spike 3, and `/qa state show|reset [<entity>]` from
  F3, which prints or clears the player state attachments ([7](#7-player-state));
- `/qa dungeon plan|build|enter <type> [<seed>]` (W4a, W4b): plan a layout of a dungeon type, with a top-down map
  and a legend in the server log, build it into a plot for a fly-through, or build it and move in at its entrance as
  the portal will (W4d); without a seed a random one is drawn and named
  ([15.3](#153-generation-pure-first-then-minecraft), [15.5](#155-instances));
- `/qa stats set <stat> <value> [<players>]`, `/qa stats reset|show` (F4b: points for a primary stat, percent for a
  share) and `/qa level <level> [<entity>]` (a player with a character gets exactly the character XP of the level,
  quietly, so the level follows up to the level cap, C2; another player or a mob gets the `realmforge:level`
  attachment, which XP and loot read; a hostile mob also gets the scaling of the level, W1, and a player's
  race and class bonuses follow it, C1). The core mod's operator command `/realmforge xp add|set|query`
  ([10.2](#102-leveling)) gives, sets or shows character XP, and vanilla's `/advancement grant @s only
  realmforge:chapter/chapter_1` completes chapter 1;
- `/qa mob [<entity>]` (W1): a mob's level, health over its maximum with the multiple its level added, armor with the
  points its level added, and the damage multiple of its hits; without an argument, the nearest hostile mob within
  32 blocks. The training dummy joins at level 1, which adds nothing;
- `/qa character show|reset [<player>]` and `/qa character create <race> <class> [<player>]` (C1): reset removes the
  character, so the creation screen opens again (the starting kit's items stay); create applies the same rules as the
  screen;
- `/qa dummy [<armor>]` (F5): a training dummy three blocks ahead, a pillager without a crossbow that stands still
  and never dies. A pillager is not undead, so poison and harming hurt it, healing heals it, and daylight does not
  burn it: it takes damage and healing the way a player does (RLM-187). Its label, a text display riding it, shows
  its armor with the share of a 10-damage hit it blocks against the nearest player's armor pierce and shred, as
  vanilla's armor formula blocks less of a bigger hit ("15 Armor (40% vs a 10-damage hit)", RLM-223), over its name with the DPS of the current session (it ends after 5 s without a
  hit); vanilla draws no name tag over an entity with a passenger, so the label carries both. The hitter sees each
  hit on the action bar, `/qa dummy reset|remove`. Pillagers carry our stats in the dev game, so
  `/attribute @n[type=pillager] realmforge:dodge_chance base set 0.5` sets a dummy's dodge chance or resistances;
- `/qa spell learn <spell> [<level>]`, `/qa spell cast <spell> [<target>]`, `/qa spell cancel`, `/qa spell resource
  <mana|rage> <amount>`, `/qa spell reset` and `/qa spell show` (C3a-1, C3a-2): teach the executing player a spell,
  cast it (as a mob with `/execute as`; a refusal names its reason; a cast-time or channelled spell begins), cancel
  the cast in progress, set a resource pool, end every cooldown, and print the known spells, the pools, the running
  cooldowns, the cast in progress and how the last cast-time or channelled spell ended
  ([11.2](#112-casting-server-authoritative));
- the combat log, `logs/realmforge-combat.log` in the game directory (`run/client/`, `run/gametest/`, ...): one line
  per hit or dodge that involves a player or a dummy, with the damage type, school, crit, attacker level, the
  original damage, what armor blocked, and the damage dealt and taken from health. A new server starts a new file.

The vanilla GameTest server builds its world from the flat preset with an empty dimension registry, so our data pack
dimensions (`realmforge:instances`, later the M1 dimension) exist in GameTests only through the QA mod's
`GameTestServerMixin`. A GameTest can log a fake player out and in again through the normal save and login path
(`PlayerList#remove`, `PlayerList#placeNewPlayer`) with `QaPlayers#login`, as `InstanceGameTests` does to test
reconnects; its connection goes through NeoForge's `NetworkRegistry#configureMockConnection`, so synced attachments
can be sent to it. `PlayerList#respawn(player, false, ...)` stands in for a respawn after death, which copies the
copy-on-death attachments (`PlayerStateGameTests`, and `WorldRulesGameTests` for Dark Vision), and
`PlayerList#respawn(player, true, ...)` for leaving the End. The connection never ticks the player, so a test that
needs player ticks plays them with `ServerPlayer#doTick`, the call the network handler makes once per tick
(`StatsGameTests`). A new player logs in at the world spawn, where the GameTest server starts its grid of tests, so a
tick there can put it inside another test's blocks and suffocate it: tests log new players in with
`QaPlayers#loginOnPlatform`, which puts them on their own test's platform, and move a player a respawn puts back at
the spawn with `QaPlayers#onPlatform` before they tick it (RLM-242). `QaPlayers#login` alone is for a returning player,
who loads where it left, and for a test that teleports the player itself right after.

## 21. Decisions and open questions

The decisions this document makes are listed in [ADR 0014](decisions/0014-core-mod-architecture.md); changing one
of them needs a new ADR. Everything else here may change with the code, in the same PR.

The owner decisions the plan depended on ([roadmap](roadmap.md#open-decisions), D1 to D11) are all made: each
decision ticket's comments record what Matěj decided, and the task that needs one follows it. Griefing protection
(D6) is built ([19.1](#191-world-defaults)), and the Nether and End (D10) are removed in M4
([ADR 0025](decisions/0025-remove-the-nether-and-end.md)). Still open: M2 decides how rarity works
([13](#13-items-gear-and-loot)).
