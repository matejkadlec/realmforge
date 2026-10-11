# Roadmap

> The order of work, by milestone. Goals: [vision.md](vision.md). How things are built:
> [architecture.md](architecture.md). Decisions: [ADRs](decisions/README.md). Baseline of 2026-10-11.

## How to use this roadmap

- **Milestones are minor releases:** M1 ships as v0.1.0, M2 as v0.2.0, and so on (open decision D7). Only M1 is
  planned task by task; later milestones are outlines, detailed when the previous one ends.
- **One task, one PR.** Large tasks are split into several PRs. A task is done when its "done when" holds and the
  checks in CLAUDE.md pass; tick it off (`[x]` plus the PR link) in the same PR.
- **Agents** take the next unblocked task of the current milestone, spikes first. A task waiting on an open
  decision ([below](#open-decisions)) asks Matěj before starting.
- **Changing the plan** is a PR to this file that waits for Matěj's approval: a hold under
  [ADR 0015](decisions/0015-merging-through-monitor-pr.md), so `monitor-pr` does not merge it on its own. A change
  of direction also needs an ADR. Ticking off a finished task is not a plan change.
- **Sizes** instead of dates: S fits one agent session, M takes one to three, L takes more and is split. Playtests,
  hand-built rooms and art review depend on Matěj's time, so calendar estimates would be guesses.

## Milestones

| Milestone | Theme | Release | Status |
| --- | --- | --- | --- |
| [M0](#m0-bootstrap) | Bootstrap: build, CI, content pipeline, modpack | - | done |
| [M1](#m1-vertical-slice-the-first-chapter) | Vertical slice: the first chapter | v0.1.0 | next |
| [M2](#m2-gear-and-loot) | Gear and loot | v0.2.0 | outline |
| [M3](#m3-classes-and-combat-depth) | Classes and combat depth | v0.3.0 | outline |
| [M4](#m4-the-world-grows) | The world grows | v0.4.0 | outline |
| [M5](#m5-professions-and-enchanting) | Professions and enchanting | v0.5.0 | outline |
| [M6](#m6-companions-and-mounts) | Companions and mounts | v0.6.0 | outline |
| [M7](#m7-contracts) | Contracts | v0.7.0 | outline |

The order follows dependencies and what makes the slice worth playing: loot gives dungeons a purpose (M2), new
classes need a combat system with depth (M3), new dimensions need mobs, bosses and dungeon archetypes to fill them
(M4), professions need gear and materials to work on (M5).

## M0: Bootstrap

Done in [PR #1](https://github.com/matejkadlec/realmforge/pull/1) (merged 2026-10-11): Gradle build with the core
and QA mods, CI, the YAML content pipeline with ID validation, one test item, the packwiz modpack, docs and ADRs
0001-0013.

Of the six technical spikes in the research report ([research notes, section 10](research-notes.md#10-next-steps)),
M0 finished spike 1 (skeleton) and most of spike 2 (pipeline: items and lang with ID validation). Quests remain
and come with task Q1; recipes come with the first content that needs one. Spikes 3-6 open M1.

## M1: Vertical slice, the first chapter

**Goal:** a small, complete, playable piece of Realmforge that uses every core system once, so later milestones
add content to systems that already work instead of building them.

### Scope

| Area | M1 content |
| --- | --- |
| Race | 1 |
| Classes | 2: a melee class and a caster (proposal D1: warrior-like with rage, mage-like with mana) |
| Spells | 4-5 per class, covering a self-buff, a debuff, single target, AoE and one special spell (such as a blink) |
| Talents | one tree per class, about 8-10 nodes in 3 tiers, including spell-modifying talents and one big-impact talent |
| Leveling | levels 1-10 with a level cap; finishing chapter 1 raises the cap (proposal D2: 10, then 20) |
| Stats | primary stats with derived bonuses and at least one milestone bonus; a character sheet |
| Dimension | 1 custom dimension (data-driven worldgen), locked until a chapter 1 quest unlocks it |
| Mobs | levels by location for vanilla hostile mobs, plus 1-2 custom mob types for the dungeons (proposal D11) |
| Dungeons | 2 procedural, instanced dungeon types: one ends in the boss's lair, one in a treasury with no boss |
| Boss | 1 boss with 3 phases, on the ground, no blindness or darkness |
| Quests | chapter 1, about 15 quests, generated into FTB Quests |
| Gear | a small set of fixed-stat weapons and armor as rewards; no affixes yet |
| World | default gamerules, Dark Vision toggle, griefing protection |

**Not in M1:** a second race, more classes, dual class, glyphs, affixes, gems, reforging, professions, pets, mounts,
villages, the enchanting overhaul, contracts, world-structure dungeons, changes to the Nether and End, and
multiplayer parties beyond "players who enter together" (proposal D8).

**Order:** the spikes first, then foundations; character and world work can run in parallel after that; quests
and the release come last. "After" in a task names what must be merged first.

### Phase 0: spikes

The four spikes left from the research report. Each is time-boxed and ends in one small PR with a written answer,
plus an ADR when it decides something. Prototype code stays only if it fits the architecture.

- [ ] **Spike 6: full pack boot test** (S, first, because it protects every later PR). *Question:* does the whole
  pack (every server-side mod plus our jar) boot cleanly on a dedicated server, and does the full client (Iris,
  Sodium, Distant Horizons) load? *How:* a CI job builds our jar, installs the pack's server side with
  packwiz-installer, starts NeoForge 21.1.259 with its dedicated-server self test
  (`NEOFORGE_DEDICATED_SERVER_SELFTEST`) and fails on errors in the log, with a short allowlist of known harmless
  lines. packwiz-installer's CLI also installs the optional Quark ([ADR 0013](decisions/0013-modpack-composition.md)),
  so the job decides whether to test with it. The client is checked once by hand in Prism; NeoForge's client self
  test (`NEOFORGE_CLIENT_SELFTEST`) under Xvfb is worth a try to automate that, otherwise the automated client test
  (mc-runtime-test) follows in M3. *Done when:* the `pack-boot` CI job is green on `master` and fails on a
  deliberately broken pack.
- [ ] **Spike 3: instanced dungeon in a plot** (M). *Question:* can we build dungeons into plots of one instance
  dimension on 1.21.1 reliably and fast enough, and clean up afterwards? *How:* a void dimension
  `realmforge:instances`, a plot allocator in `SavedData`, the plot's chunks kept loaded by a NeoForge
  `TicketController`, three hand-made rooms placed by a minimal planner (start, corridor, end), teleport in and
  back; measure build time per room and the tick cost with spark; compare cleanup strategies (clear the plot over
  ticks; never reuse plots and measure disk growth; overwrite on reuse); try a server restart and a disconnect in
  the middle of a run. *Done when:* a GameTest builds, enters and frees an
  instance, and the PR records the numbers and the chosen strategy (an ADR if it changes
  [ADR 0012](decisions/0012-procedural-dungeons-and-dimensions.md)). Settles D8 with Matěj. Blocks W4.
- [ ] **Spike 4: Better Combat and spell-casting animations** (M). *Question:* can our casting animations play on
  the player while Better Combat (which animates attacks through playerAnimator) is installed, and do the inputs
  coexist? *How:* a dev run with Better Combat and playerAnimator; one test animation through playerAnimator's API
  (*verify* it) layered with Better Combat's; first and third person, dual wielding, a cast interrupted by an
  attack; and the core mod without Better Combat. *Done when:* an ADR picks the animation approach
  (playerAnimator as an optional or required dependency, or something else) and says whether swings are blocked
  while casting; input for D4. Blocks C3b and C6.
- [ ] **Spike 5: textures, Retro Diffusion vs PixelLab** (M, needs Matěj). *Question:* which generator gives
  better vanilla-style 16x16 textures for us, at what cost and effort? *How:* the same 10 textures in both (for
  example a sword, a staff, a potion, two spell icons, two tileable blocks, a gem, an armor icon and a mob
  texture) through their MCP servers, then palette cleanup (Pillow `quantize` or unfake) and a review gallery
  ([ADR 0007](decisions/0007-asset-pipeline.md)). *Needs from Matěj:* accounts and credits, approval to install
  the MCP servers, the final pick. *Done when:* Matěj has picked; ADR 0007 is confirmed or superseded; the project
  palette and the first texture pipeline step exist. Blocks final art (A1, A2); placeholders until then.

### Phase 1: foundations

- [ ] **F1 Feature skeleton** (S, after spike 6). Packages from [architecture 4.2](architecture.md#42-packages-of-the-core-mod)
  with empty `<Name>Feature` classes wired in `Realmforge`; `client/RealmforgeClient`;
  `network/RealmforgeNetwork`, versioned by the mod version, with the interface for client-bound handlers; the import
  check that bans `net.minecraft.client`, `com.mojang.blaze3d` and `net.neoforged.neoforge.client` outside
  `client/`. *Done when:* everything builds and passes, and the import check fails on a deliberate violation.
- [ ] **F2 Pipeline for datapack registries** (M, after F1). A generic generator from a content type to
  `data/realmforge/realmforge/<registry>/`; translation keys for data types; unit conversion (seconds to ticks,
  percent to fractions); generated `ResourceKey` constants; generated expectations with a GameTest pattern;
  `content/curves/` as the first type; validate-and-copy for `content/worldgen/`; CI fails on data loading errors
  in the GameTest log ([architecture 6.1](architecture.md#61-stages)). *Done when:* a curve written in YAML is
  decoded in the game and checked by a GameTest.
- [ ] **F3 Player state** (M, after F2). The attachments `level` (every living entity), `character`, `spellbook`,
  `talents` and `combat` with codecs, sync and copy on death; the `/qa` command root (show and reset state). *Done
  when:* JUnit round-trips every attachment's `Codec` and `StreamCodec`, and a GameTest checks copy on death
  (resource and cooldowns kept, the current cast dropped) and save and load on a mock server player
  (`GameTestHelper#makeMockServerPlayerInLevel`).
- [ ] **F4 Stats** (M, after F3; D5). Primary and secondary attributes on players; `CharacterStats` with the
  saved-but-recomputed modifiers, the first-tick recompute and the health rules
  ([architecture 8.2](architecture.md#82-where-modifiers-come-from)); `realmforge:stat_rule` with derived bonuses
  and milestones; the cap mechanism; a basic character sheet. *Done when:* JUnit covers the rules; GameTests show
  STR raising attack damage as configured and bonus max health surviving a relog and a respawn.
- [ ] **F5 Combat core** (M, after F4). Spell schools: `content/spell_schools/`, the `realmforge:spell_school`
  registry, their damage types (in `#minecraft:bypasses_cooldown`) and generated attributes. `DamageCalculator` on
  the NeoForge damage events with the hurt-cooldown handling ([architecture 9.1](architecture.md#91-damage-pipeline));
  crit, dodge, resistances; our armor formula; minimal combat text; a QA training dummy with a DPS meter and a
  combat log file. *Done when:* JUnit covers the formulas, and GameTests hit the dummy, check the numbers, show
  that school damage right after a melee hit is not swallowed, and show that a dodged hit leaves no damage container
  behind ([architecture 9.1](architecture.md#91-damage-pipeline)).

### Phase 2: character and abilities

- [ ] **C1 Races and classes** (M, after F5; D1). The registries, the character creation screen and payload, the
  starting kit; the race and both classes as content (stats only; spells come in C4). *Done when:* a new player must
  create a character, and a GameTest checks the creation rules.
- [ ] **C2 Leveling and the level cap** (M, after C1; D2, D3). The XP curve; XP from kills by the victim's level
  (`realmforge:level`, 1 for every mob until W1 assigns levels) and from `/realmforge xp`; the
  `realmforge:chapter` registry and the cap from chapter advancements; level-up effects; the `level_reached`
  trigger; an XP bar. *Done when:* GameTests show XP accumulating past the cap and granting the chapter advancement
  raising the level.
- [ ] **C3a Spell engine** (L, after C2). `realmforge:spell` (schools exist since F5), effect and targeting types,
  `SpellCaster`, cooldowns and the global cooldown, mana and rage, procs, the projectile and area entities. *Done
  when:* GameTests cast every effect type, and a cast whose resource runs out before completion fails without its
  effects.
- [ ] **C3b Casting controls and HUD** (M, after C3a and spike 4; D4). Spell bar, key mappings, cast bar,
  cooldown sweep, tooltips with live numbers, the payloads.
- [ ] **C3c Status effects** (M, after C3a). `content/effects/` to generated `MobEffect` registrations; damage and
  healing over time; source tracking for dispels.
- [ ] **C4 Class kits** (M, after C3b and C3c). 4-5 spells per class; starting gear with fixed stats, generated
  with per-slot modifier ids and the rule that gear never adds max health directly
  ([architecture 8.2](architecture.md#82-where-modifiers-come-from)); a first balance pass on the training dummy.
- [ ] **C5 Talents** (M, after C4). The registry, effect types, `TalentRules`, the talent screen, a respec command,
  one tree per class. *Done when:* JUnit property tests pass and a GameTest shows a talent changing a cast.
- [ ] **C6 Casting animations and effects** (M, after spike 4 and C4). Animations, particles and sounds for both
  classes.

### Phase 3: world, dungeons and the boss

- [ ] **W1 Mob levels** (M, after F5). `realmforge:dimension_rules` with an entry for `minecraft:overworld`
  (named in its `dimension` field); levels at spawn for vanilla hostile mobs (and later ours); scaling from curves,
  with health refilled to the scaled maximum; Jade shows the level (optional). *Done when:* GameTests check the
  level by distance, the scaled maximum health, that a new mob's current health equals it, and that a split
  slime's children keep the parent's level.
- [ ] **W2 Dimension** (M, after W1 and C2). Worldgen JSON for the first dimension and its dimension rules; the
  `realmforge:requirement_type` registry with its first types; access by portal or item, locked by a chapter 1
  step. *Done when:* a GameTest shows access refused before the requirement and allowed after it.
- [ ] **W3 Custom mobs** (M, after W1 and C3a; D11). 1-2 custom mob types for the dungeons (archetypes: melee and
  caster), GeckoLib models, loot tables without trash. *Done when:* GameTests check that they spawn with levels and
  drop valid loot.
- [ ] **W4 Dungeon framework** (L, after spike 3 and W1). The instance dimension from spike 3;
  `realmforge:dungeon_type`; the room format and its validation (`content/rooms/`); the pure planner and layout
  with fuzz tests; the builder with data markers and chunk tickets; `InstanceManager`; entry and exit; the rules
  inside (Adventure mode plus guards for non-player edits and for items with `can_break` or `can_place_on`,
  [architecture 15.5](architecture.md#155-instances)); QA commands to preview a layout and enter a dungeon. *Done
  when:* GameTests build and enter an instance, and a bucket, flint and steel, an axe, a hoe, a tool with
  `can_break`, placing and breaking all leave the room unchanged.
- [ ] **W5 Boss** (L, after C3a and W3). `realmforge:boss`, `BossPhaseMachine`, abilities through the spell engine,
  the boss entity and model, the phase name in the boss bar, arena rules, loot. The boss is tested on its own,
  before any dungeon references it. *Done when:* GameTests cover every phase change.
- [ ] **W6 Two dungeon types** (L, after W4 and W5). Type A ends in the boss's lair; type B ends in a treasury with
  traps and a vault, without a boss. Matěj builds the key rooms in creative; agents assemble variants and set
  encounter and loot budgets.

### Phase 4: story, polish, release

- [ ] **Q1 Quest pipeline** (L, after C2). `content/quests/` (chapters exist since C2) to FTB Quests SNBT with
  stable ids, advancements, our criteria triggers and statistics, lang; `CommandReward`s with `permission_level: 2`
  for our commands; `packwiz refresh` after generation; quest validation (where, how, with what; boss levels from
  data). Finishes the quest half of research spike 2.
- [ ] **Q2 Chapter 1** (M, after Q1 and W6). About 15 quests with lore set in Realmforge, rewards from the gear
  set; the last quest grants the chapter advancement.
- [ ] **R1 World rules** (S, after F1; D6). Default gamerules for new worlds, griefing protection, Dark Vision.
- [ ] **R2 Release pipeline** (M, after Q2; D7, D9). A workflow that builds the jar and the pack from a `v*` tag
  ([ADR 0008](decisions/0008-distribution-github-and-curseforge.md)), version 0.1.0, changelog, README.
- [ ] **R3 Playtest and balance** (M, repeated, after Q2). Matěj plays the slice in Prism; agents fix what he
  finds; a spark profile on his PC.

### Assets (parallel)

Placeholders are generated until real art exists ([ADR 0007](decisions/0007-asset-pipeline.md)).

- [ ] **A1 Icons** for spells, items and gear (after spike 5).
- [ ] **A2 Models and animations** for the custom mobs and the boss (GeckoLib; Blockbench MCP, installed only with
  Matěj's approval). UV-mapped entity textures need his eye ([ADR 0007](decisions/0007-asset-pipeline.md)).
- [ ] **A3 Sounds:** vanilla sounds remapped in `sounds.json`, a few new ones.
- [ ] **A4 Rooms:** key rooms built by Matěj, variants by agents.

### Definition of done

1. The full pack boots in CI (spike 6 job) and plays in Prism on Matěj's PC with acceptable performance (spark
   profile).
2. In a new world a player can create a character (1 race, 2 classes); reach level 10 by killing mobs and
   questing and stay there until chapter 1 is done; use every class spell and learn talents; enter the dimension
   once a quest unlocks it; clear both dungeon types, one ending with the boss and one without; beat the boss
   through all three phases; finish chapter 1 and see the level cap rise.
3. JUnit covers formulas, talent rules, the dungeon planner and layout, and the phase machine; GameTests cover
   spells, leveling and the cap, the chapter bridge, instances and boss phases; every CI job is green.
4. `docs/architecture.md` and CLAUDE.md match the code; spike outcomes and changed decisions have ADRs.
5. GitHub Release v0.1.0 (jar and pack), built from a tag.

## M2: Gear and loot

Outline: five rarity tiers everywhere (first decide how: extend vanilla `Rarity` or use our own component,
[architecture 13](architecture.md#13-items-gear-and-loot)); affixes with item levels and loot by mob level; gems
and sockets; a
reforging station; RNG item chests (as items); quest choice rewards with pre-rolled gear; Curios with the first
curios; more weapon and armor sets. Probably also the full-pack registry dump for the ID universe
([ADR 0005](decisions/0005-content-pipeline-yaml-single-source-of-truth.md)), once content references other mods'
items.

## M3: Classes and combat depth

Outline: more classes toward the vision's play styles (melee, ranged, mage, battle mage, summoner); a second race;
the remaining spell schools; glyphs; dual class; interrupts, dispels and crowd control on bosses; threat; the
balance simulation in CI and a nightly client test ([ADR 0010](decisions/0010-testing-strategy.md)).

## M4: The world grows

Outline: dimensions 2 and 3; the Nether and End decision (D10) carried out; world-structure dungeons (jigsaw and
Lithostitched); more dungeon endings (puzzle temple, wave arena, escape, mounted run, ruin exploration); our own
structure locator; more mobs (friendly ones too) and bosses with lore and locator items; chapters 2 and 3; the
start of the village, villager and trade overhaul.

## M5: Professions and enchanting

Outline: professions that can be combined and leveled (for example mining, alchemy with long-lasting potions,
smithing, jewelcrafting for curios, tailoring, cooking); new foods; enchanting without RNG on data-driven
enchantments; the final role of vanilla XP (follows D3).

## M6: Companions and mounts

Outline: the pet system (one pet, class exceptions, spells, skills that level up, talents, dialogues, a bond
progression, resurrection); mounts summoned when used, ground mounts faster than Speed II, flying mounts better
than elytra (through NeoForge's `creative_flight` attribute).

## M7: Contracts

Outline: our own contract system for procedural quests: a job board, guild ranks, profession orders, "dungeon of
the day"; repeatable trade quests move over from FTB Quests
([ADR 0006](decisions/0006-quests-ftb-quests-generated-from-data.md)).

## Backlog

No milestone yet:

- Small UI mods missing on NeoForge 1.21.1 ([ADR 0013](decisions/0013-modpack-composition.md)): EMI Trades, Damage
  Numbers (M1's combat text may cover it), No Recipe Book, Lightweight Inventory Sorting, and a replacement for the
  temporary Toast Control.
- CurseForge publishing ([ADR 0008](decisions/0008-distribution-github-and-curseforge.md)).
- Nightly spark profiles; our own performance mod.
- A local bridge so agents can see screenshots and game state from Matěj's PC
  ([research notes, section 6](research-notes.md#6-testing-and-qa)).
- Port to a newer Minecraft: re-evaluate in 2027 at the earliest
  ([ADR 0003](decisions/0003-platform-minecraft-1-21-1-neoforge.md)).
- Explicitly not for now: seasons, decorative pets, "full No Man's Sky" (possibly a separate mod,
  [ADR 0012](decisions/0012-procedural-dungeons-and-dimensions.md)).

## Every milestone

- **Pipeline:** each new content type comes with its schema, validation, generator, tests and docs.
- **QA mod:** commands, the training dummy, the combat log and scenarios grow with the features.
- **CI:** the "later" layers of [ADR 0010](decisions/0010-testing-strategy.md) arrive one by one: pack boot in M1,
  balance simulation and the nightly client test in M3, nightly performance profiles afterwards.
- **Docs:** architecture, ADRs, this roadmap and CLAUDE.md stay current in the same PR as the change.
- **Performance:** a spark profile before every release.

## Open decisions

Owner decisions the plan depends on. Each has a proposal so work is not blocked; the task that needs it asks Matěj
before starting.

| # | Decision | Needed by | Proposal |
| --- | --- | --- | --- |
| D1 | The two M1 classes | C1 | a warrior-like melee class (rage) and a mage-like caster (mana): the most different pair, which tests both Better Combat melee and casting |
| D2 | Level cap numbers | C2 | cap 10 during chapter 1, raised to 20 when it is finished |
| D3 | Character XP and vanilla XP | C2 | separate: character XP is never spent; vanilla XP stays the currency for enchanting and anvils until M5 decides its role |
| D4 | Spell key scheme | C3b | a separate spell bar with its own keys, leaving the item hotbar alone; spike 4 informs it |
| D5 | Primary stats | F4 | STR, DEX, INT and a stamina stat (health); one stat per play style plus survivability |
| D6 | Destructive mobs | R1 | targeted protection (no terrain damage) instead of `mobGriefing=false`, as the research notes recommend |
| D7 | Release numbering | R2 | M1 is v0.1.0. The vision describes v0.1 as "everything, cut down"; the research report called v0.1 a vertical slice; this roadmap follows the report and the M1 brief |
| D8 | Who enters an instance | spike 3 | solo first; players standing at the entrance join the run; FTB Teams parties later |
| D9 | License of `content/`, `art/` and `docs/` | R2 | an ADR before the first release ([ADR 0009](decisions/0009-licensing.md) left it open); the quest texts feed the All Rights Reserved pack |
| D10 | Nether and End: overhaul or remove | M4 planning | open, no proposal yet ([vision](vision.md#open-questions)) |
| D11 | Custom mobs in M1 | W3 | 1-2 custom types (a melee and a caster archetype) plus leveled vanilla hostile mobs; every custom mob needs a model, animations and a UV texture that Matěj reviews, so more wait for M4 |
