# 0014. Core mod architecture

Date: 2026-10-11

Status: Accepted; decision 9 amended by [0027](0027-client-bound-payload-handlers-through-the-client-event.md) (client-bound
payload handlers through NeoForge's client event)

## Context

ADRs 0002-0013 settled the form, platform, content pipeline, testing and modpack, but not how the core mod's code
is organised or how its systems connect. Milestone M1 ([roadmap](../roadmap.md)) builds every core system at once
(character, stats, combat, spells, talents, mobs, bosses, dungeons, quests), so the structure must be fixed first,
in a form that agents can follow from session to session. The full description is
[architecture.md](../architecture.md); this ADR records its decisions. All Minecraft, NeoForge and FML APIs it
relies on were checked against the pinned 1.21.1 sources, and the FTB Quests and Better Combat facts against their
1.21.1 jars.

## Decision

1. **Feature packages** (`character/`, `spell/`, `boss/`, `dungeon/`, ...), each with one `<Name>Feature` entry
   class that registers everything the feature needs. Dependencies and data references point one way
   ([architecture 4.4](../architecture.md#44-dependencies-between-features)): higher features call lower ones'
   APIs, lower ones reach higher ones through provider interfaces, and facts that many features read (the level of
   every living entity) live low, in `stats`.
2. **Client isolation:** all client-only code lives under `client/` with its own entry point
   (`@Mod(value = MOD_ID, dist = Dist.CLIENT)`), including the client parts of integrations (`client/compat/`). No
   other package imports Minecraft's, Blaze3D's or NeoForge's client classes, checked by an automated test.
3. **Registries:** static registries only for what Minecraft needs Java objects for (generated from YAML when it is
   content); datapack registries for our game definitions (races, classes, spell schools, spells, talent trees,
   stat rules, curves, chapters, dimension rules, bosses, dungeon types; affixes and gems from M2); polymorphic data
   dispatches through our own static type registries.
4. **One spell and effect engine** for players, mobs, bosses, items and talent procs. Boss abilities are spells.
5. **Player state in NeoForge data attachments** (immutable records, synced by NeoForge; one attachment per
   audience of its updates). NeoForge 21.1.259 sends every synced attachment to each player who starts tracking
   the holder, whatever the sync predicate says, so a synced attachment holds nothing other players' clients must
   not see, and client code reads owner-only attachments only from the local player
   ([architecture 7](../architecture.md#7-player-state)). Attribute modifiers from race, class, level, talents
   and stat rules are saved but always recomputed from data, on change and on the first tick after login or
   respawn; modifier ids are unique per source, entry, slot and modifier.
6. **One attribute model:** primary stats are attributes, derived stats come from data rules, attribute caps are
   raised in one place, and armor mitigation is our own formula applied through NeoForge's damage container.
7. **Quests and the mod meet only through standard FTB Quests features:** a chapter's last quest grants a chapter
   advancement, which raises the level cap; our criteria triggers and custom statistics feed FTB Quests'
   advancement and stat tasks. This refines [0006](0006-quests-ftb-quests-generated-from-data.md).
8. **Dungeons:** a pure, seed-driven planner and layout, then a Minecraft builder that places hand-made NBT rooms
   into plots of the instance dimension ([0012](0012-procedural-dungeons-and-dimensions.md)). Rooms live in
   `content/rooms/` as NBT with a YAML sidecar: the second exception to YAML-only content after worldgen JSON. This
   amends [0005](0005-content-pipeline-yaml-single-source-of-truth.md).
9. **Networking:** one registration point whose protocol version is the mod version, so a client and server with
   different Realmforge versions never connect (our codecs also travel inside NeoForge's and vanilla's packets,
   which the payload version check does not cover); client-to-server payloads are intents the server validates;
   client-bound payloads are handled through an interface declared in `network/` and implemented in `client/`.
10. **Content conventions:** human units in YAML (seconds, percent), game units in generated data (ticks,
    fractions); numbers shown to players always come from data.

## Consequences

- Agents know where new code goes, and reviews check it against one document.
- Most new content is YAML; new Java is mostly a new behaviour type or archetype.
- Every layer has its test type: pure logic in JUnit, content in pytest, codecs and behaviour in GameTests.
- Some details wait for spikes or M1 tasks: dungeon cleanup and plot reuse (spike 3), the casting animation
  approach (spike 4), the attribute cap mechanism (task F4). Outcomes that change a decision above get their own
  ADR.
- Datapack registries need the world to be reopened before changes apply; a table that needs hot reload while
  tuning may move to a reload listener later.
- Owner decisions still open (listed in the roadmap) do not change this structure.
- [architecture.md](../architecture.md) is kept current with the code; changing a decision above needs a new ADR.
