# Roadmap

> The order of work, by milestone. Goals: [vision.md](vision.md). Realms and their order:
> [design/realms.md](design/realms.md). How things are built: [architecture.md](architecture.md). Decisions:
> [ADRs](decisions/README.md). Baseline of 2026-10-11; M1 and M2 re-planned on 2026-10-29 (RLM-504,
> [ADR 0058](decisions/0058-player-systems-before-world-content.md)).

## How to use this roadmap

- **Milestones are minor releases from M2 on:** M1 builds the character and ships no release of its own, and M2
  ships as v0.1.0 (decision D7, amended on 2026-10-29). Later milestones keep their numbers until they are planned
  (M3 as v0.3.0, and so on). M1 and M2 are planned task by task; later milestones are outlines, detailed when the
  previous one ends.
- **The plan lives here, the status in Jira** ([ADR 0017](decisions/0017-jira-as-the-source-of-task-status.md)).
  Every task, decision and backlog item names its ticket in the Jira space Realmforge (`RLM-<n>`, at
  `https://envelopment.atlassian.net/browse/RLM-<n>`), and every milestone its epic. Whether a task waits, is in
  progress or is done is its ticket's status; this file has no checkboxes. The "after" order below is mirrored as
  "is blocked by" links. Tickets that serve the plan without changing it (follow-ups, the parts of a split task)
  live only in Jira under their milestone epic, and so does work outside this plan (developer workflow and
  tooling under RLM-1, the storyline under RLM-30).
- **One task, or one coherent batch of tasks, per PR** ([ADR 0016](decisions/0016-jira-batch-delivery.md)).
  Large tasks are split into several PRs. A task is done when its "done when" holds, the checks in CLAUDE.md pass
  and its PR has merged; `finalize-pr` then moves its ticket to Done.
- **Agents** take the next unblocked task of the current milestone through its ticket in the active sprint:
  `flow1`, `flow2`, or the intake claim of the [AI development flow](ai-development-flow.md) for direct work, with
  the key in the branch name and the PR title. A task waiting on an open decision ([below](#open-decisions)) is not
  started: it waits for Matěj.
- **Player systems first:** no M2 task enters a sprint before M1's [definition of done](#definition-of-done) holds,
  unless Matěj says so. Technical "after" links alone never make world work ready (Matěj, 2026-10-29,
  [ADR 0058](decisions/0058-player-systems-before-world-content.md)).
- **Changing the plan** is a PR to this file, and every change to it is one
  ([ADR 0017](decisions/0017-jira-as-the-source-of-task-status.md)). Agents make it only as Matěj decided it (a
  Done decision ticket, his request, or a ticket he wrote that asks for it) and propose any other change to him
  first; the PR names the change and its source, carries no label and merges like any other
  ([ADR 0021](decisions/0021-status-overview-and-plan-changes.md)). A new task gets its key here in the same PR;
  its ticket waits in the backlog, with a comment naming the PR, until the PR merges, so no agent starts work the
  plan does not hold yet. A change of direction also needs an ADR.
- **Sizes** instead of dates: S fits one agent session, M takes one to three, L takes more and is split. Playtests,
  room and art review depend on Matěj's time, so calendar estimates would be guesses.

## Milestones

| Milestone | Epic | Theme | Release | Status |
| --- | --- | --- | --- | --- |
| [M0](#m0-bootstrap) | - | Bootstrap: build, CI, content pipeline, modpack | - | done |
| [M1](#m1-the-character-to-level-18-stats-spells-gear) | RLM-6 | The character to level 18 (stats, spells, gear) | - | next |
| [M2](#m2-the-world-of-chapters-1-and-2) | RLM-7 | The world of chapters 1 and 2 | v0.1.0 | after M1 |
| [M3](#m3-classes-and-combat-depth) | RLM-8 | Classes and combat depth | v0.3.0 | outline |
| [M4](#m4-the-world-grows) | RLM-9 | The world grows | v0.4.0 | outline |
| [M5](#m5-professions-and-enchanting) | RLM-10 | Professions and enchanting | v0.5.0 | outline |
| [M6](#m6-companions-and-mounts) | RLM-11 | Companions and mounts | v0.6.0 | outline |
| [M7](#m7-contracts) | RLM-12 | Contracts | v0.7.0 | outline |
| [M8](#m8-after-v1) | RLM-307 | After v1: optional features and the post-v1 mods | v2 | outline |

The order follows Matěj's rule of 2026-10-29, **player systems first**: the character works from level 1 to 18
(M1) before the custom mobs, dungeons and bosses of its world are built and balanced against it (M2), because a
boss built before the stats, spells and gear it is balanced against cannot be judged
([ADR 0058](decisions/0058-player-systems-before-world-content.md)). After that, new classes need a combat system
with depth (M3), new dimensions need mobs, bosses and dungeon archetypes to fill them (M4), and professions need
gear and materials to work on (M5). M8 holds what comes after v1 (Matěj, 2026-10-19, RLM-264). Which third-party
mods join in which milestone is [ADR 0044](decisions/0044-third-party-mods-by-milestone.md), amended for M1 and M2
by [ADR 0058](decisions/0058-player-systems-before-world-content.md), with every pick in the
[research note](research/third-party-mods.md).

## M0: Bootstrap

Done in [PR #1](https://github.com/matejkadlec/realmforge/pull/1) (merged 2026-10-11): Gradle build with the core
and QA mods, CI, the YAML content pipeline with ID validation, one test item, the packwiz modpack, docs and ADRs
0001-0013.

Of the six technical spikes in the research report ([research notes, section 10](research-notes.md#10-next-steps)),
M0 finished spike 1 (skeleton) and most of spike 2 (pipeline: items and lang with ID validation). Quests remain
and come with task Q1; recipes come with the first content that needs one. Spikes 3-6 open M1.

## M1: The character to level 18 (stats, spells, gear)

**Goal:** a Warrior and a Mage that work completely from level 1 to 18: their stats, their spells with real
visuals, and their gear up to netherite with curios and Apotheosis affixes (Matěj, 2026-10-29, RLM-6). **Player
systems come first:** every new system must be testable when it lands, and a boss built before the stats, spells and
gear it is balanced against cannot be (Astone, RLM-491). So M1 builds and balances no custom mob, dungeon or boss;
M2 does, against the finished character ([ADR 0058](decisions/0058-player-systems-before-world-content.md)). M1
replaces the first plan's vertical slice: its world part moved to [M2](#m2-the-world-of-chapters-1-and-2), and the
gear of the old M2 moved here.

### Scope

| Area | M1 content |
| --- | --- |
| Race | 1, the Human |
| Classes | 2: a melee class and a caster (decision D1: Warrior with rage, Mage with mana) |
| Stats | primary stats with derived bonuses, milestones and class-dependent bonuses; damage-type resistances and Healing Received as our own stats; the character sheet, split into tabs and reworked with our own background, font and layout |
| Spells | 4-5 per class, covering a self-buff, a debuff, single target, AoE and one special spell (such as a blink), with hit shapes; the spell bar, casting and combat QoL |
| Spell trees | one specialization spell tree per class, full to level 18 (C5, RLM-218); talents are postponed indefinitely (Matěj, 2026-10-16, RLM-28, [ADR 0044](decisions/0044-third-party-mods-by-milestone.md)) |
| Spell visuals | icons, and every spell's model, trajectory, particles, animation and sounds |
| Leveling | levels 1-18 with the level cap of D2 (C2); the chapter advancements that raise the cap are granted by command until M2's quests grant them |
| Gear | Apotheosis's rarities, affixes, gems and reforging fitted to our stats ([ADR 0038](decisions/0038-apotheosis-carries-gear-rarity-affixes-gems-and-enchanting.md)); weapons, wands and caster robes up to netherite with textures; our own shield tiers; curios with textures, with Artifacts and Immersive Armors as Matěj picks them |
| Items | the Scrolls of Respec and Rebirth |
| World | Overworld as the test world: vanilla hostile mobs with levels (W1), vanilla strongholds off |
| Pack | the M1 mods of [ADR 0044](decisions/0044-third-party-mods-by-milestone.md) ([Pack](#pack-parallel)) and the gear mods, which were M2's until 2026-10-29 |

**Testing:** in an Overworld test world, on the training dummy and vanilla mobs with levels (W1). No custom mob,
dungeon, boss or quest is needed (RLM-6).

**Not in M1** (M2's since 2026-10-29): Undervault's art and worldgen, the realm portal's art, custom mobs and their
models, instanced dungeons, Astone's finish and balance, quests, coins and merchants, and the release. **Later
milestones:** a second race, more classes, each class's other two specializations and dual spec (M1 has one
specialization per class; RLM-218), talents and glyphs (postponed indefinitely, RLM-28), professions, pets,
mounts, villages, the enchanting overhaul, contracts, the gated ores (RLM-194, M4), custom NPCs, and changes to the
Nether and End (apart from strongholds off, which leaves the End unreachable,
[ADR 0044](decisions/0044-third-party-mods-by-milestone.md)).

**Order:** the foundations first, then the phases in order: stats, classes and spells, spell visuals, gear and
curios (Matěj, 2026-10-29). "After" in a task names what must be merged first.

### Foundations

Phases 0 to 2 of the first M1 plan (2026-10-11): the spikes, the foundations and the character's core systems. The
rest of that plan's phase 2 (C4 to C6) is in the phases below, and its phases 3 and 4 are M2's.

#### Phase 0: spikes

The four spikes left from the research report. Each is time-boxed and ends in one small PR with a written answer,
plus an ADR when it decides something. Prototype code stays only if it fits the architecture.

- **Spike 6: full pack boot test** (RLM-13, S, first, because it protects every later PR). *Question:* does the whole
  pack (every server-side mod plus our jar) boot cleanly on a dedicated server, and does the full client (Iris,
  Sodium, Distant Horizons) load? *How:* a CI job builds our jar, installs the pack's server side with
  packwiz-installer, starts NeoForge 21.1.259 with its dedicated-server self test
  (`NEOFORGE_DEDICATED_SERVER_SELFTEST`) and fails on errors in the log, with a short allowlist of known harmless
  lines. packwiz-installer's CLI also installs the optional Quark ([ADR 0013](decisions/0013-modpack-composition.md)),
  so the job decides whether to test with it. The client is checked once by hand in Prism; NeoForge's client self
  test (`NEOFORGE_CLIENT_SELFTEST`) under Xvfb is worth a try to automate that, otherwise the automated client test
  (mc-runtime-test) follows in M3. *Done when:* the `pack-boot` CI job is green on `master` and fails on a
  deliberately broken pack.
- **Spike 3: instanced dungeon in a plot** (RLM-14, M). *Question:* can we build dungeons into plots of one instance
  dimension on 1.21.1 reliably and fast enough, and clean up afterwards? *How:* a void dimension
  `realmforge:instances`, a plot allocator in `SavedData`, the plot's chunks kept loaded by a NeoForge
  `TicketController`, three hand-made rooms placed by a minimal planner (start, corridor, end), teleport in and
  back; measure build time per room and the tick cost with spark; compare cleanup strategies (clear the plot over
  ticks; never reuse plots and measure disk growth; overwrite on reuse); try a server restart and a disconnect in
  the middle of a run. *Done when:* a GameTest builds, enters and frees an
  instance, and the PR records the numbers and the chosen strategy (an ADR if it changes
  [ADR 0012](decisions/0012-procedural-dungeons-and-dimensions.md)). Settles D8 with Matěj. Blocks W4.
- **Spike 4: Better Combat and spell-casting animations** (RLM-15, M). *Question:* can our casting animations play on
  the player while Better Combat (which animates attacks through playerAnimator) is installed, and do the inputs
  coexist? *How:* a dev run with Better Combat and playerAnimator; one test animation through playerAnimator's API
  (*verify* it) layered with Better Combat's; first and third person, dual wielding, a cast interrupted by an
  attack; and the core mod without Better Combat. *Done when:* an ADR picks the animation approach
  (playerAnimator as an optional or required dependency, or something else) and says whether swings are blocked
  while casting; input for D4. Blocks C3b and C6.
- **Spike 5: textures, Retro Diffusion vs PixelLab** (RLM-16, M, needs Matěj). *Question:* which generator gives
  better vanilla-style 16x16 textures for us, at what cost and effort? *How:* the same 10 textures in both (for
  example a sword, a staff, a potion, two spell icons, two tileable blocks, a gem, an armor icon and a mob
  texture) through their MCP servers, then palette cleanup (Pillow `quantize` or unfake) and a review gallery
  ([ADR 0007](decisions/0007-asset-pipeline.md)). *Needs from Matěj:* accounts and credits, approval to install
  the MCP servers, the final pick. *Done when:* Matěj has picked; ADR 0007 is confirmed or superseded; the project
  palette and the first texture pipeline step exist. Blocks final art (A1, A2); placeholders until then.

#### Phase 1: foundations

- **F1 Feature skeleton** (RLM-17, S, after spike 6). Packages from
  [architecture 4.2](architecture.md#42-packages-of-the-core-mod) with empty `<Name>Feature` classes wired in
  `Realmforge`; `client/RealmforgeClient`; `network/RealmforgeNetwork`, versioned by the mod version, with the
  interface for client-bound handlers; the import check that bans `net.minecraft.client`, `com.mojang.blaze3d` and
  `net.neoforged.neoforge.client` outside `client/`. *Done when:* everything builds and passes, and the import
  check fails on a deliberate violation.
- **F2 Pipeline for datapack registries** (RLM-18, M, after F1). A generic generator from a content type to
  `data/realmforge/realmforge/<registry>/`; translation keys for data types; unit conversion (seconds to ticks,
  percent to fractions); generated `ResourceKey` constants; generated expectations with a GameTest pattern;
  `content/curves/` as the first type; validate-and-copy for `content/worldgen/`; CI fails on data loading errors
  in the GameTest log ([architecture 6.1](architecture.md#61-stages)). *Done when:* a curve written in YAML is
  decoded in the game and checked by a GameTest.
- **F3 Player state** (RLM-19, M, after F2). The attachments `level` (every living entity), `character`, `spellbook`,
  `talents` and `combat` with codecs, sync and copy on death; the `/qa` command root (show and reset state). *Done
  when:* JUnit round-trips every attachment's `Codec` and `StreamCodec`, and a GameTest checks copy on death
  (resource and cooldowns kept, the current cast dropped) and save and load on a mock server player
  (`GameTestHelper#makeMockServerPlayerInLevel`).
- **F4 Stats** (RLM-20, M, after F3; D5). Primary and secondary attributes on players; `CharacterStats` with the
  saved-but-recomputed modifiers, the first-tick recompute and the health rules
  ([architecture 8.2](architecture.md#82-where-modifiers-come-from)); `realmforge:stat_rule` with derived bonuses
  and milestones; the cap mechanism; a basic character sheet. *Done when:* JUnit covers the rules; GameTests show
  STR raising attack damage as configured and bonus max health surviving a relog and a respawn.
- **F5 Combat core** (RLM-21, M, after F4). Spell schools: `content/spell_schools/`, the `realmforge:spell_school`
  registry, their damage types (in `#minecraft:bypasses_cooldown`) and generated attributes. `DamageCalculator` on
  the NeoForge damage events with the hurt-cooldown handling ([architecture 9.1](architecture.md#91-damage-pipeline));
  crit, dodge, resistances; our armor formula; minimal combat text; a QA training dummy with a DPS meter and a
  combat log file. *Done when:* JUnit covers the formulas, and GameTests hit the dummy, check the numbers, show
  that school damage right after a melee hit is not swallowed, measure a melee hit after a DoT tick inside the
  hurt-cooldown window, and show that a dodged hit leaves no damage container behind
  ([architecture 9.1](architecture.md#91-damage-pipeline)).

#### Phase 2: character core

- **C1 Races and classes** (RLM-22, M, after F5; D1). The registries, including the `realmforge:resource_type` type
  registry with `mana` and `rage` (ids only; C3a gives them behaviour), so class entries can name their resource;
  the character creation screen and payload, the starting kit; the race and both classes as content (stats only;
  spells come in C4). *Done when:* a new player must create a character, and a GameTest checks the creation rules.
- **C2 Leveling and the level cap** (RLM-23, M, after C1; D2, D3). The XP curve; XP from kills by the victim's level
  (`realmforge:level`, 1 for every mob until W1 assigns levels) and from `/realmforge xp`; the
  `realmforge:chapter` registry and the cap from chapter advancements; level-up effects; the `level_reached`
  trigger; an XP bar. *Done when:* GameTests show XP stopping at the cap and granting the chapter advancement
  raising the cap.
- **C3a Spell engine** (RLM-24, L, after C2). `realmforge:spell` (schools exist since F5), effect and targeting types,
  `SpellCaster`, cooldowns and the global cooldown, the behaviour of mana and rage, procs, the projectile and area
  entities. *Done when:* GameTests cast every effect type, a cast whose resource runs out or whose target leaves
  range before completion fails without its effects, and a channel ticks at its interval and stops when
  interrupted.
- **C3b Casting controls and HUD** (RLM-25, M, after C3a and spike 4; D4). Spell bar, key mappings, cast bar,
  cooldown sweep, tooltips with live numbers, the payloads.
- **C3c Status effects** (RLM-26, M, after C3a). `content/effects/` to generated `MobEffect` registrations; damage and
  healing over time; source tracking for dispels.
- **Spell aiming** (RLM-344, RLM-345, RLM-346, after C3b; moved from M3 before C4, Matěj, 2026-10-22). Spells cast
  without a target and aim with the crosshair (RLM-344); smart auto-aim for targeted spells, with the target
  highlighted (RLM-345); ground-targeted areas land at the crosshair, with clamp cast (RLM-346).
- **W1 Mob levels** (RLM-31, M, after F5). `realmforge:dimension_rules` with an entry for `minecraft:overworld`
  (named in its `dimension` field); levels at spawn for vanilla hostile mobs (and later ours); scaling from curves,
  with health refilled to the scaled maximum; Jade shows the level (optional). *Done when:* GameTests check the
  level by distance, the scaled maximum health, that a new mob's current health equals it, that a split slime's
  children keep the parent's level, that a mob added without `finalizeSpawn` (`GameTestHelper#spawn`) gets a
  level and its scaling, and that a converted mob (a piglin zombifying outside the Nether) keeps a level and
  scaling that belong together. M1 tests the character on these mobs.

### Phase 3: stats

- **Primary stats** (RLM-170, after F4). Rebalance the primary stats, add milestones and class-dependent bonuses.
- **Stamina** (RLM-171, after RLM-170). The Stamina stat that RLM-170 adds slows the hunger drain.
- **Damage-type resistances** (RLM-233). Physical, Projectile, Explosion and Fall resistance and Healing Received
  as our own stats, on the character sheet.
- **Character sheet tabs** (RLM-235). The stats split into Melee, Ranged, Magic and Defense tabs, with ranged stats.
- **Character sheet rework** (RLM-225, RLM-254). Our own background, font and layout; the specialization's
  placeholder.

### Phase 4: classes and spells

- **C4 Class kits** (RLM-27, M, after C3b, C3c and the spell aiming). 4-5 spells per class; starting gear with fixed
  stats, generated with per-slot modifier ids and the rule that gear never adds max health directly
  ([architecture 8.2](architecture.md#82-where-modifiers-come-from)); the Human's racial passive and active (Sword
  Specialization with the `#realmforge:sword_like` item tag and its tooltip lines, Will To Survive in the racial slot
  beside the spell bar (X; [ADR 0046](decisions/0046-spell-bar-default-keys-amend-the-pack-defaults.md));
  [architecture 10.1](architecture.md#101-races-and-classes), RLM-219); a first balance pass on the training dummy.
  The Mage never heals: it survives through utility (Ice Block, Mirror Image) and shields (Mana Shield, Ice Barrier,
  perhaps spells that raise the Magic Shield for a while) (Matěj, 2026-10-22).
- **C5 Specialization spell trees** (RLM-333, M, after C4). Talents are postponed indefinitely (RLM-28), but the M1
  tree stays as a specialization spell tree, one per class (Matěj, 2026-10-16, RLM-218): most spells take several
  points and grow stronger with each, some gain an extra effect at the top rank, big-impact spells have one rank;
  the tree holds active and passive spells. The spell trees window, where the player picks the specialization and
  spends points ([architecture 10.1](architecture.md#101-races-and-classes)). A full tree to level 18 for both the
  Warrior and the Mage (RLM-6). *Done when:* JUnit covers the point rules, and a GameTest shows a spent point
  changing a spell's cast.
- **Hit shapes** (RLM-399, after C4). AoE hit shapes for the Warrior's melee spells and a splash explosion for the
  Mage's bolts.
- **The spell bar, casting and combat QoL:** the options and fixes that playing the two classes turns up, each its
  own ticket under RLM-6.

### Phase 5: spell visuals

- **A1 Icons** (RLM-46, after spike 5) for spells, items and gear. Placeholders are generated until real art exists
  ([ADR 0007](decisions/0007-asset-pipeline.md)).
- **Spell visuals** (epic RLM-434, [spell visuals design](design/spell-visuals.md)): the visuals engine, then every
  spell's model, trajectory, particles and animation, with **C6 Casting animations and effects** (RLM-29, M, after
  spike 4 and C4): animations, particles and sounds for both classes.
- **Sounds** (after the visuals): the sound-effect generator and audio pipeline (RLM-126), the first spells' sounds
  (RLM-445), and **A3 Sounds** (RLM-49): vanilla sounds remapped in `sounds.json`, a few new ones.

### Phase 6: gear and curios

- **Apotheosis** ([ADR 0038](decisions/0038-apotheosis-carries-gear-rarity-affixes-gems-and-enchanting.md),
  [Apotheosis design](design/apotheosis.md)): Apotheosis, Apothic Attributes and Apothic Enchanting in the pack,
  fitted to our combat and stats (RLM-205); its data from our YAML (RLM-272); loot categories for wands and our
  curio slots (RLM-273); the affix set from Matěj's review (RLM-232); rarity tiers by realm, affixes per tier and
  item type, and loot-only gear (RLM-198). Its five rarities (mythic shown as Legendary), affixes, gems and sockets
  and its reforging, with loot by the player's world tier, not an item level. Probably also the full-pack registry
  dump for the ID universe ([ADR 0005](decisions/0005-content-pipeline-yaml-single-source-of-truth.md)), once
  content references other mods' items.
- **Weapons and armor:** every weapon type in the seven vanilla materials (Matěj, 2026-10-15), the wands (RLM-172)
  and caster robes (RLM-197) up to netherite, all with textures, and more weapon and armor sets.
- **Shields** (RLM-378). Our own shield tiers with a block cooldown per tier, from wood to netherite, then our realm
  materials.
- **Curios:** Curios with the first curios of our own, with textures; Artifacts and Immersive Armors after their
  catalogues, from which Matěj picks what stays (RLM-312, RLM-313; Artifacts' curio items wait for his word on
  [ADR 0025](decisions/0025-remove-the-nether-and-end.md)'s curio rule), and the gear and tooltip mods (RLM-314:
  Legendary Tooltips, Item Borders, YDM's Weapon Master).
- **Scrolls** of Respec (RLM-226), a full spell point reset, and of Rebirth (RLM-228), a full character reset.

### Pack (parallel)

The M1 mods of [ADR 0044](decisions/0044-third-party-mods-by-milestone.md) (Matěj, 2026-10-18 and 2026-10-19,
RLM-264); the gear mods come with [phase 6](#phase-6-gear-and-curios). Each goes in with packwiz, its side, the
[pack README](../pack/README.md) and the ADR 0013 list, and a pack boot. Controlling with Searchables is in since
RLM-279; Lootr comes with W6 in M2 (RLM-288).

- **PK1 QoL and UI mods** (RLM-308, M). Inventory Sorting (instead of building Lightweight Inventory Sorting, RLM-61),
  Advanced Loot Info with Advanced Core Info, Just Enough Professions, Clumps, Crafting Tweaks, Item Highlighter, Pick
  Up Notifier, RightClickHarvest, Comforts, Traveler's Titles (never a second title next to W2b's arrival title),
  Better ModList, BetterF3, and CleanCut or Cut Through only if Better Combat does not already hit through grass.
  *Done when:* the pack boots with them on a server and a client, and Matěj played them.
- **PK2 Macaw's building mods** (RLM-309, S). Furniture, Doors, Windows, Roofs, Bridges, Lights and Lamps, Fences and
  Walls, Paths and Pavings, Trapdoors, Stairs. *Done when:* the pack boots with them, and Matěj played them.
- **PK3 Structurify, strongholds off** (RLM-310, S). Vanilla strongholds are disabled completely; Structurify also
  places other mods' structures in M2. *Done when:* the pack boots with it, and a check shows that no stronghold
  generates.
- **PK4 Iron's RPG Tweaks, combat module only** (RLM-311, M, after F5). Player damage never loses anything to the
  10-tick hurt cooldown, melee included; its other modules stay off, and if it misbehaves our data pack adds player
  attacks, arrows and our spell damage types to `#minecraft:bypasses_cooldown` instead
  ([architecture 9.1](architecture.md#91-damage-pipeline)). *Done when:* GameTests show two melee hits 6 ticks apart
  both landing in full and lava, fire and cactus hitting no more often than vanilla, and Matěj played it.

### Definition of done

1. The full pack boots in CI (spike 6 job) and plays in Prism on Matěj's PC with acceptable performance (spark
   profile).
2. A Warrior and a Mage can each be levelled from 1 to 18 with a full specialization tree, every spell with its real
   visuals, gear up to netherite (the Mage's caster robes included), working curios and Apotheosis affixes, and the
   reworked character sheet (RLM-6).
3. JUnit covers the formulas and the spell tree rules; GameTests cover spells, leveling and the cap, and the gear;
   every CI job is green.
4. `docs/architecture.md` and CLAUDE.md match the code; spike outcomes and changed decisions have ADRs.

## M2: The world of chapters 1 and 2

**Goal:** the world the finished character plays in: the first two chapters of the v1 realm layout (Matěj, RLM-188,
[realm design](design/realms.md)), chapter 1 in Overworld (levels 1-10) and chapter 2 in Undervault (levels 6-18),
about 12 hours for an average player by the [XP model](design/leveling.md). It is built and balanced only once M1's
character works to level 18, and v0.1.0 is released at its end (Matěj, 2026-10-29, RLM-7).

**No M2 task enters a sprint before M1's [definition of done](#definition-of-done) holds**, unless Matěj says so.
Technical "after" links alone never make world work ready
([ADR 0058](decisions/0058-player-systems-before-world-content.md)). Documents written before 2026-10-29 call the
first stage of this milestone M1 (the chapters' short versions, with the quest book as the only voice) and the rest
M2; both stages are M2's now.

### Scope

| Area | M2 content |
| --- | --- |
| Chapters | 2: Overworld (chapter 1, levels 1-10) and Undervault (chapter 2, levels 6-18); each chapter's last quest grants the advancement that raises the level cap (D2) |
| Dimension | 1 custom dimension, the stone realm: one giant cave with a single walkable floor (Undervault, [realm design](design/realms.md#the-stone-realm)), with its textures; data-driven worldgen, locked until chapter 1's main questline unlocks it, entered through the realm portal |
| Mobs | 1-2 custom mob types for the dungeons (proposal D11) with real models, textures and animations, Undervault's own mobs, and mob levels for dungeons and structures |
| Dungeons | 2 procedural, instanced dungeon types: one ends in the boss's lair, one in a treasury with no boss; the world-structure dungeons of Overworld and Undervault |
| Bosses | Astone, the Unbroken: 3 phases, on the ground, no blindness or darkness, balanced against the finished character; Overworld's final boss, an elite Illusioner |
| Quests | chapters 1 and 2, about 25-30 quests, generated into FTB Quests, with XP rewards, quest choice rewards with pre-rolled gear, and RNG item chests |
| World | default gamerules, Night Vision toggle, griefing protection (R1); illagers and villages; simple foods and potions; coins and merchants ([ADR 0040](decisions/0040-coins-and-data-driven-merchant-trades.md)) |
| Pack | Lootr ([ADR 0041](decisions/0041-dungeon-containers-hold-loot-through-lootr.md)), the structure, dungeon, illager and village mods of [ADR 0044](decisions/0044-third-party-mods-by-milestone.md) |
| Release | v0.1.0, built from a tag (D7) |

**Not in M2:** dungeon parties: instances are solo (decision D8); parties come later (RLM-68). The gated ores
(RLM-194, M4), and changes to the Nether and End apart from strongholds off
([ADR 0044](decisions/0044-third-party-mods-by-milestone.md)).

**Order:** the realm, its mobs, the dungeons and the boss; the rest of the world; then the quests and the release.
"After" in a task names what must be merged first.

### Phase 1: realm, mobs, dungeons and the boss

- **W2a Stone realm** (RLM-32, M, after W1 and C2). Worldgen JSON for the stone realm
  ([realm design](design/realms.md#the-stone-realm): one giant cave with a single walkable floor, common stone of
  about hardness 25, ancient debris below the floor, no weather; RLM-188), its dimension type (the coordinate scale
  the realm portal links by, beds that set no spawn) and its dimension rules; the `realmforge:requirement_type`
  registry with its first types; `content/realms/` with the realm's unlock advancement, which chapter 1's last main
  quest grants; access refused on every way in without the unlock, mobs kept out, respawn always in Overworld
  ([realm portal design](design/realm-portal.md)). *Done when:* GameTests show a player teleported into the realm
  refused before the unlock and allowed after it, a mob teleported in staying where it is, and a death in the realm
  respawning in Overworld.
- **W2b Realm portal** (RLM-154, M, after W2a). Access through the realm portal
  ([realm portal design](design/realm-portal.md)): the frame, the sealstone and the stone realm's signet, a
  chapter 1 quest reward; Nether-like linking in both directions at the realm's coordinate scale, to a portal near
  the scaled position or one built there. *Done when:* GameTests show the portal refusing a player before the unlock
  and passing them after it, and a trip into the realm and back that links to the portals at the scaled positions
  or builds them.
- **Undervault's textures and worldgen** (RLM-266 to RLM-270) and **the realm portal's art and rules** (RLM-300,
  RLM-301, RLM-306, RLM-337, RLM-358, RLM-394).
- **W3 Custom mobs** (RLM-33, M, after W1 and C3a; D11). 1-2 custom mob types for the dungeons (archetypes: melee and
  caster), GeckoLib models, loot tables without trash. *Done when:* GameTests check that they spawn with levels and
  drop valid loot. Their real models, textures and animations (A2, RLM-350, RLM-401, RLM-453, RLM-385), and mob
  levels for dungeons (RLM-240, RLM-304, RLM-305).
- **W4 Dungeon framework** (RLM-34, L, after spike 3 and W1). The instance dimension from spike 3;
  `realmforge:dungeon_type`; the room format and its validation (`content/rooms/`); the pure planner and layout
  with fuzz tests; the builder with data markers and chunk tickets; `InstanceManager`; entry through a temporary
  dungeon portal that a dungeon key opens for its player alone (D8), open for the dungeon type's time with warnings
  before it closes and sends everyone back, and exit at any time by the dungeon exit or `/instance leave`; a completed
  run closes 5 minutes later; the rules inside (Adventure mode plus guards for non-player edits and for items with
  `can_break` or `can_place_on`, [architecture 15.5](architecture.md#155-instances)); QA commands to preview a layout
  and enter a dungeon. *Done when:* GameTests build and enter an instance, open a portal with a key, close it by its
  timer and by the last player's `/instance leave`, and check the completion's 5 minutes, and a bucket (a plain one,
  and water, lava and empty buckets with `can_place_on`), flint and steel, an axe, a hoe, a tool with `can_break`,
  placing and breaking all leave the room unchanged.
- **W5 Boss** (RLM-35, L, after C3a and W3). `realmforge:boss`, `BossPhaseMachine`, abilities through the spell engine,
  the boss entity and model, the phase name in the boss bar, arena rules, loot. The boss is tested on its own,
  before any dungeon references it. *Done when:* GameTests cover every phase change. Astone is finished with custom
  particles, models, textures and animations once Undervault's textures exist, then balanced against the finished
  character (RLM-7); the boss bar (RLM-174).
- **W6 Two dungeon types** (RLM-36, L, after W4 and W5). Type A ends in the boss's lair; type B ends in a treasury with
  traps and a vault, without a boss. Its rooms come from A4 (generated and imported,
  [ADR 0028](decisions/0028-dungeon-rooms-generated-or-imported.md)); agents assemble variants and set encounter
  and loot budgets. Both types put the netherite upgrade smithing template into their loot, fairly often (Matěj,
  RLM-188). No container in a dungeon is empty: every chest and barrel holds loot worth a few mob kills, per player
  through Lootr, which joins the pack with it (Matěj, 2026-10-19,
  [ADR 0041](decisions/0041-dungeon-containers-hold-loot-through-lootr.md),
  [container loot](design/container-loot.md)).

### Phase 2: the rest of the world

The world content of the old M2 outline (Matěj, 2026-10-19, RLM-264,
[ADR 0044](decisions/0044-third-party-mods-by-milestone.md)), whose gear moved to M1:

- **World-structure dungeons, moved from M4,** for Overworld and Undervault, each structure only in its realm
  ([realm design](design/realms.md#structures-from-other-mods)): Overworld's structure and dungeon mods (RLM-315:
  YUNG's suite, Dungeons and Taverns, MVS, Battle Towers, Dungeons Enhanced, Underground Worlds; Structory waits for
  Matěj's reading of its Stardust Labs License), Undervault's from other mods in its stones (RLM-316), and our own
  dungeon types as world structures, starting with Undervault's dwarf-like fortress (RLM-317).
- **Undervault's own structures and mobs:** the Hewn's Mine (RLM-429), Halcyar's Chamber and the gem ores
  (RLM-433), from [chapter 2](lore/chapters/02-the-weight-of-stone.md).
- **Illagers and villages** (RLM-318): Illager Invasion, It Takes a Pillage, Illager Warship, ChoiceTheorem's
  Overhauled Village with Village Taverns, Villager Names, Ender Zoology.
- **The Overworld chapter's content:** its main questline (2–3 zombie, skeleton or spider dungeons, then pillagers,
  illagers and evokers to the end of the chapter), the raid quests with our raid-wave and Bad Omen triggers, and the
  Realmforge Trials quest group for every realm's non-story dungeons (RLM-320); the Illusioner tower with Overworld's
  final boss, an elite Illusioner at level 8, whose kill opens Undervault from then on (RLM-321).
- **Our own Structure Compass, moved from M4** (RLM-319): every structure of the dimension, with filtering and search
  by group, an early quest reward.
- **Painting variants of our own** (RLM-322), one set per dungeon theme.
- **Simple craftable foods and potions** with weak, long-lasting buffs (a few percent, more against one mob type, for
  example +5% damage for an hour or +8% against undead), brewable without the Nether
  ([ADR 0025](decisions/0025-remove-the-nether-and-end.md)).
- **RNG item chests** (as items) and quest choice rewards with pre-rolled gear.

### Phase 3: story, polish, release

- **Q1 Quest pipeline** (RLM-37, L, after C2). `content/quests/` (chapters exist since C2) to FTB Quests SNBT with
  stable ids, advancements, our criteria triggers and statistics, lang; `CommandReward`s with `permission_level: 2`
  for our commands; `packwiz refresh` after generation; quest validation (where, how, with what; boss levels from
  data). Finishes the quest half of research spike 2. *Done when:* pytest checks the generated SNBT, including that
  every chapter reward is a per-player, auto-claimed `AdvancementReward`
  ([architecture 16](architecture.md#16-progression-and-quests)).
- **Q2 Chapters 1 and 2** (RLM-38, M, after Q1 and W6). About 25-30 quests with lore set in Realmforge: the two main
  questlines of the [lore outline's beats](lore/outline.md#chapters-1-and-2) plus side quests, rewards from
  the gear set, XP rewards; each chapter's last quest grants its chapter advancement (RLM-188).
- **Coins and merchants** ([ADR 0040](decisions/0040-coins-and-data-driven-merchant-trades.md),
  [coins and trades](research/coins-and-trades.md)).
- **R1 World rules** (RLM-42, S, after F1; D6). Default gamerules for new worlds, griefing protection, Night Vision.
- **R2 Release pipeline** (RLM-44, M, after Q2; D7, D9). A workflow that builds the jar and the pack from a `v*` tag
  ([ADR 0008](decisions/0008-distribution-github-and-curseforge.md)), version 0.1.0, changelog, README.
- **R3 Playtest and balance** (RLM-45, M, repeated, after Q2). Matěj plays the slice in Prism; agents fix what he
  finds; a spark profile on his PC.

### Assets (parallel)

Placeholders are generated until real art exists ([ADR 0007](decisions/0007-asset-pipeline.md)).

- **A2 Models and animations** (RLM-48) for the custom mobs and the boss (GeckoLib; Blockbench MCP, installed only with
  Matěj's approval). UV-mapped entity textures need his eye ([ADR 0007](decisions/0007-asset-pipeline.md)).
- **A4 Rooms** (RLM-50): rooms generated from YAML room specs by our room generator, plus an imported baseline of
  MIT, CC0 and LGPL-3.0 rooms with their notices; nobody builds rooms by hand, and Matěj reviews them in a gallery
  ([ADR 0028](decisions/0028-dungeon-rooms-generated-or-imported.md)).

### Definition of done

1. The full pack boots in CI and plays in Prism on Matěj's PC with acceptable performance (spark profile).
2. In a new world a player can create a character (1 race, 2 classes); reach level 10 by killing mobs and
   questing and stay there until chapter 1 is done; finish chapter 1 and see the level cap rise to 18; enter
   Undervault once chapter 1's quest unlocks it; reach level 18 and stay there until chapter 2 is done; clear both
   dungeon types, one ending with the boss and one without; beat the boss through all three phases; finish
   chapter 2 and see the level cap rise to 30.
3. JUnit covers the dungeon planner and layout, and the phase machine; GameTests cover the chapter bridge,
   instances and boss phases; every CI job is green.
4. `docs/architecture.md` and CLAUDE.md match the code; changed decisions have ADRs.
5. GitHub Release v0.1.0 (jar and pack), built from a tag.

## M3: Classes and combat depth

Outline: more classes toward the vision's play styles (melee, ranged, mage, battle mage, summoner); a second race;
the remaining spell schools: Holy, the healing school, with the Paladin, a warrior with holy magic who leans toward
spells and tanking (RLM-162; Holy and the Paladin come together, in M3 or later, Matěj, 2026-10-22); the other two
specialization spell trees of each class, toward three per class in v1 (Matěj, 2026-10-16), and dual spec, a
secondary specialization from a level threshold (proposed: 20; RLM-218); interrupts, dispels and crowd control on
bosses; threat; the balance simulation in CI and a nightly client test ([ADR 0010](decisions/0010-testing-strategy.md)). With the
ranged play style: Ranged Weapon API and More Bows and Arrows (RLM-324), after a catalogue from which Matěj picks what
stays (RLM-323; [ADR 0044](decisions/0044-third-party-mods-by-milestone.md)). Glyphs are postponed indefinitely
(Matěj, 2026-10-16, RLM-28).

## M4: The world grows

Outline: dimensions 2 and 3, Sunwastes and Everwinter, the twin realms of chapter 3 (levels 16-30,
[realm design](design/realms.md)); the gated ores (RLM-194, [realm design](design/realms.md#tool-tiers-and-gated-ores));
the Nether and End removed and their items given new sources (D10,
[ADR 0025](decisions/0025-remove-the-nether-and-end.md)); the twin realms' world structures, each only in its realm
(RLM-325: Dungeons Enhanced, YUNG's Cave Biomes, The Lost Castle, Alex's Mobs Continued; Overworld's and Undervault's
come in M2, [ADR 0044](decisions/0044-third-party-mods-by-milestone.md)); more dungeon endings (puzzle temple, wave
arena, escape, mounted run, ruin exploration) and open-air instanced dungeons under the sky, in a second instance
dimension (RLM-327); visual and info mods (RLM-326: LambDynamicLights, Effectual, Falling Leaves, Fresh Animations,
Fusion, Just Enough Resources); more mobs (friendly ones too) and bosses with lore and locator items; chapter 3; the
start of the village, villager and trade overhaul, with trades paid in our own coins, not emeralds
([ADR 0040](decisions/0040-coins-and-data-driven-merchant-trades.md)). Also:

- **Elytra from chapter 3's final boss** (RLM-138): its drop, or an item frame in its room (like the End ship),
  so elytra arrive at the step into chapter 4 and ship with the removal of the End. The "every item is
  obtainable" check lists elytra as a known exception only until that boss exists
  ([realm design, open question 8](design/realms.md#open-questions)).
- **Mounts, first part** (RLM-115, [Chococraft research](research/chococraft.md#decision-rlm-115)): our own
  mounts, not Chococraft. The `realmforge:mount` registry and its content type (`content/mounts/`), the player's
  mount collection, summon and dismiss (a mount is summoned when used and cannot be lost), ground riding with
  attacks from the mount, flight, and mount tiers 1 to 3. Each tier ships with the chapter that unlocks it, so tier
  1 becomes the reward of a chapter 1 quest at the same time.
- **The in-game guide** (RLM-116, [guide design](design/guide.md)): our own screen on `J` with nested tabs
  (Realms, Dungeons, Bosses, Character, Basics), search and chapter locks; each realm lists its ores, each dungeon
  its notable loot, each boss its full loot table with drop chances. It replaces EMI Loot and EMI Ores, which the
  26.1.2 port removed, and its entries cover the content of chapters 1 and 2 too (RLM-188).

## M5: Professions and enchanting

Outline: professions that can be combined and leveled (for example mining, alchemy, smithing, jewelcrafting for
curios, tailoring, cooking); cooking and alchemy bring the stronger foods and potions, with medium to very strong
effects (M2 has the simple ones); enchanting with less RNG (Apothic Enchanting,
[ADR 0038](decisions/0038-apotheosis-carries-gear-rarity-affixes-gems-and-enchanting.md)); the final role of
vanilla XP (follows D3). Aquaculture 2 and Better Archeology seed the fishing and archaeology professions
(RLM-328, [ADR 0044](decisions/0044-third-party-mods-by-milestone.md)).

## M6: Companions and mounts

Outline: the pet system (one pet, class exceptions, spells, skills that level up, talents, dialogues, a bond
progression, resurrection); mount tiers 4 to 6 and their extras (tier 5's rider ignores fire and lava damage, tier
6's resists its chapter's debuff once the debuff and tenacity system exists), and an optional mount journal
(RLM-115; the mount system and tiers 1 to 3 come in M4, and tier 4 moves with chapter 4 if that is planned
earlier). Ground mounts beat a Speed II potion; elytra fly faster than any mount, while mounts win on overall
speed, utility and combat (RLM-115).

## M7: Contracts

Outline: our own contract system for procedural quests: a job board, guild ranks, profession orders, "dungeon of
the day"; repeatable trade quests move over from FTB Quests
([ADR 0006](decisions/0006-quests-ftb-quests-generated-from-data.md)).

## M8: After v1

Outline (v2, Matěj, 2026-10-19, RLM-264): the nice-to-haves after v1
([ADR 0044](decisions/0044-third-party-mods-by-milestone.md)). An optional-features screen that switches on Shoulder
Surfing Reloaded and Camera Overhaul, both off by default (RLM-329); Apothic Spawners, whose spawners give vanilla XP
and mob loot but never class XP, Trims Overhaul, Gateways to Eternity and Yggdrasil (RLM-330).

## Backlog

No milestone yet:

- Small UI mods missing on NeoForge 1.21.1 (RLM-61, [ADR 0013](decisions/0013-modpack-composition.md)): nothing is
  left to build. Inventory Sorting replaces Lightweight Inventory Sorting (PK1), Advanced Loot Info and Just Enough
  Professions replace EMI Trades next to JEI / REI Trades
  ([ADR 0040](decisions/0040-coins-and-data-driven-merchant-trades.md)), the pack already shows damage numbers, No
  Recipe Book is in, and Toast Control stays ([ADR 0044](decisions/0044-third-party-mods-by-milestone.md)).
- Talents (RLM-28) and glyphs: postponed indefinitely, maybe not in v1 (Matěj, 2026-10-16). Each class's spells
  form specialization spell trees instead (C5, RLM-218).
- The Nature (RLM-369) and Shadow (RLM-370) spell schools, late and undecided: Nature with the Druid and the Shaman,
  Shadow with the Warlock (relatively late) and a shadow Priest. No school comes without a class that uses it
  (Matěj, 2026-10-22).
- CurseForge publishing (RLM-62, [ADR 0008](decisions/0008-distribution-github-and-curseforge.md)).
- Nightly spark profiles; our own performance mod (RLM-63).
- A local bridge so agents can see screenshots and game state from Matěj's PC (RLM-64,
  [research notes, section 6](research-notes.md#6-testing-and-qa)).
- Minecraft version: decided 2026-10-12 to port to 26.1.2 before M1 gameplay code (RLM-65,
  [ADR 0026](decisions/0026-platform-minecraft-26-1-2-neoforge.md)); a later move beyond 26.1.2 is a decision of
  its own.
- Explicitly not for now: seasons, decorative pets, "full No Man's Sky" (possibly a separate mod,
  [ADR 0012](decisions/0012-procedural-dungeons-and-dimensions.md)).

## Every milestone

- **Pipeline:** each new content type comes with its schema, validation, generator, tests and docs.
- **QA mod:** commands, the training dummy, the combat log and scenarios grow with the features.
- **CI:** the "later" layers of [ADR 0010](decisions/0010-testing-strategy.md) arrive one by one: pack boot in M1,
  balance simulation and the nightly client test in M3, nightly performance profiles afterwards.
- **Docs:** architecture, ADRs and CLAUDE.md stay current in the same PR as the change; this roadmap changes only
  when the plan does ([ADR 0017](decisions/0017-jira-as-the-source-of-task-status.md),
  [ADR 0021](decisions/0021-status-overview-and-plan-changes.md)).
- **Performance:** a spark profile before every release.

## Open decisions

Owner decisions the plan depends on. Each has a proposal so work is not blocked; the task that needs it asks Matěj
before starting. Whether a decision is made is its ticket's status: a Done decision's comments record what Matěj
decided, which wins over the proposal here.

| # | Ticket | Decision | Needed by | Proposal |
| --- | --- | --- | --- | --- |
| D1 | RLM-51 | The two M1 classes | C1 | **Decided 2026-10-11 (RLM-51): Warrior (melee, rage) and Mage (caster, mana)**, as proposed: the most different pair, which tests both Better Combat melee and casting. Hunter was the alternative to Mage; it would leave the spell system untested and needs a pet (M6), so it is a candidate for a later class |
| D2 | RLM-52 | Level cap numbers | C2 | each chapter's cap is its last level, raised by its main questline (Matěj, 2026-10-15, v1 layout RLM-188): 10, 18, 30, 40, 44, 52, 60, so chapter 1 raises 10 to 18 |
| D3 | RLM-53 | Character XP and vanilla XP | C2 | separate: character XP is never spent; vanilla XP stays the currency for enchanting and anvils until M5 decides its role |
| D4 | RLM-54 | Spell key scheme | C3b | a separate spell bar with its own keys, leaving the item hotbar alone; spike 4 informs it |
| D5 | RLM-55 | Primary stats | F4 | **Decided 2026-10-11 (RLM-55): strength, dexterity, vitality and intellect.** Vitality is the health stat, not called stamina so it is never confused with a stamina resource. Dexterity gives bonuses every class uses: critical strike chance (spells too), attack speed and movement speed (small, capped). Dodge is no Dexterity bonus: it is kept for future classes such as the Rogue and the Hunter. Luck and defense stay out. F4 (RLM-20) set the first numbers in `content/stat_rules/` |
| D6 | RLM-56 | Destructive mobs | R1 | targeted protection (no terrain damage) instead of `mobGriefing=false`, as the research notes recommend |
| D7 | RLM-57 | Release numbering | R2 | **Amended 2026-10-29 (RLM-504): M2 is v0.1.0**, the world of chapters 1 and 2; M1, the character, ships no release of its own ([ADR 0058](decisions/0058-player-systems-before-world-content.md)). Before, M1 was v0.1.0: the vision describes v0.1 as "everything, cut down"; the research report called v0.1 a vertical slice, and the first plan followed the report and the M1 brief |
| D8 | RLM-58 | Who enters an instance | spike 3 | **Decided 2026-10-11 (RLM-58): solo only in M1** (the dungeons are M2's since 2026-10-29, so solo only in M2). Every player who enters gets their own instance; entry is a temporary portal opened with a dungeon key (W4); parties come later through our own command-based party system (RLM-68), not FTB Teams |
| D9 | RLM-59 | License of `content/`, `art/` and `docs/` | R2 | an ADR before the first release ([ADR 0009](decisions/0009-licensing.md) left it open); the quest texts feed the All Rights Reserved pack |
| D10 | RLM-47 | Nether and End: overhaul or remove | M4 planning | **Decided 2026-10-12 (RLM-47): remove both** ([ADR 0025](decisions/0025-remove-the-nether-and-end.md)). Their items get new sources (quests, trades, realm drops); Overworld is the starting area and the hub ([realm design](design/realms.md)). [ADR 0032](decisions/0032-v1-realm-layout-amends-the-nether-and-end-removal.md) amends it for the v1 layout (RLM-188): Overworld covers levels 1-10, Ashfire (the late fire realm) does not replace the Nether, and netherite comes from Undervault. Carried out in M4 |
| D11 | RLM-60 | Custom mobs in M2 (M1 until 2026-10-29) | W3 | 1-2 custom types (a melee and a caster archetype) plus leveled vanilla hostile mobs; every custom mob needs a model, animations and a UV texture that Matěj reviews, so more wait for M4 |
