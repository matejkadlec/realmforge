# 0026. Platform: Minecraft 26.1.2 on NeoForge

Date: 2026-10-12

Status: Accepted (supersedes [0003](0003-platform-minecraft-1-21-1-neoforge.md))

## Context

[0003](0003-platform-minecraft-1-21-1-neoforge.md) pinned Minecraft 1.21.1 and put a port off until 2027 at the
earliest. Matěj asked for it to be re-evaluated as early as possible instead (RLM-65), and the research of
2026-10-12 (the RLM-65 comment of that day: Modrinth, CurseForge, the NeoForge and FTB Mavens, the mods' Git
branches) found:

- **No newer version has every mod.** Of the 38 mods [0013](0013-modpack-composition.md) chose, 26.1.2 has 32 (30
  releases, 2 betas), 26.2 has 24 and 26.3 has 23. 1.21.11 has 27 and is a dead end.
- **26.2 and 26.3 lack the FTB Quests stack**, which [0006](0006-quests-ftb-quests-generated-from-data.md) builds
  on: FTB targets only 26.1.2, as it targeted only 1.21.1 before. They also lack a third of the pack.
- **The gaps on 26.1.2:** MineColonies (with Structurize, BlockUI, Domum Ornamentum, Multi-Piston, TownTalk; ldtteam
  has started porting its libraries, MineColonies itself has no 26.x branch), EMI with EMI Loot and EMI Ores (no
  build newer than 1.21.1 on any loader), HT's TreeChop and Quark. Regions Unexplored and FTB Teams exist only as
  betas.
- **26.1.2 gains:** Better Combat 3 on Player Animation Library (maintained; playerAnimator is not), No Recipe Book
  (wanted, missing on 1.21.1), a release render stack (Sodium 0.9.2, Iris 1.11.4, Distant Horizons 3.3.3), data
  driven villager trades, and Minecraft without obfuscation, with official names in the sources. NeoForge 26.1.2 is
  stable since 26.1.2.71 (2026-06-02); ATM11 and FTB Evolution 2 build on it.
- **Cost grows with every M1 task.** The NeoForge porting primers measure about 158,000 words of API changes from
  1.21.1 to 26.1, concentrated in GUI, rendering, entity renderers, GameTests and item models: what M1 adds next
  (spell bar, talents, combat text, GeckoLib mobs). In October 2026 the code base was small (core mod 2.6k Java
  lines, QA mod 2.2k, 33 GameTests, one generated item).
- **Against it:** a much smaller community (66 NeoForge modpacks on Modrinth for 26.1.2, 2,152 for 1.21.1), and AI
  models know 26.1 only partly.

Matěj decided on 2026-10-12: port to 26.1.2 now (option B of the research), before M1 gameplay code; 26.2 and 26.3
are not targets.

## Decision

Pinned exactly (RLM-123, RLM-124):

| Component | Version |
| --- | --- |
| Minecraft | 26.1.2 (data version 4790) |
| NeoForge | 26.1.2.116 |
| ModDevGradle | 2.0.148 (supports 26.1.2 unchanged) |
| Gradle (wrapper) | 9.2.1 |
| Java | 25 (Temurin), for the toolchains, the Gradle daemon, CI and the pack boot |
| Parchment | none: Minecraft ships unobfuscated since 26.1 |

- No upgrade of any of these without a new ADR ([0001](0001-record-architecture-decisions.md)). A later move to 26.3
  or newer is a decision of its own.
- **The pack follows** ([0013](0013-modpack-composition.md), Matěj's choices of 2026-10-12):
  - MineColonies is paused with its libraries, and no quests depend on it; RLM-125 adds it back once it has a 26.1.2
    build.
  - EMI, EMI Loot and EMI Ores go; JEI stays. Loot and ore information goes into our own in-game guide (RLM-116).
  - Tree Harvester (with Collective) replaces TreeChop; Quark goes.
  - Regions Unexplored 0.7.0-beta2 is taken unless it crashes; Biomes O' Plenty is not wanted as a replacement.
  - No Recipe Book joins the pack; Neat and Toast Control stay by choice.
- **Cast animations move to Player Animation Library** with Better Combat 3 ([0020](0020-cast-animations-and-melee-while-casting.md),
  amended).
- The advice of 0003 stands: rendering and GUI code in a few isolated packages, content in data
  ([0005](0005-content-pipeline-yaml-single-source-of-truth.md)), so a later port stays cheap.

## Consequences

- Agents use 26.1 idioms and verify every API in `build/mc-sources/`, which now holds the official names. NeoForge's
  own classes ship in a sources jar of their own since 26.1; `:mod:extractMinecraftSources` unpacks both. The changes
  that hit our code are in CLAUDE.md's pitfalls: `Identifier` instead of `ResourceLocation`, attachments
  serialized through a `MapCodec`, GameTests as registered test functions and instances, item model definitions in
  `assets/<ns>/items/`, dimension types with environment attributes, gamerules as server saved data,
  `SavedDataType`, `ValueInput`/`ValueOutput`, `KeyMapping.Category`, no NeoForge effect cures.
- GameTests are no longer annotation based in vanilla: the QA mod's `@QaGameTest` and `QaGameTests` register them,
  and positions are relative to the template's corner, so the platform's floor is at y = 0.
- portablemc 4.4.1, the pack boot's client launcher and its newest release, cannot run the processors of NeoForge
  26.1's installer. The pack boot installs the client with NeoForge's own installer and lets portablemc start it
  ([0018](0018-pack-boot-tests.md)).
- Every machine needs Java 25: SDKMAN in WSL, the Windows clone, CI's `setup-java`; Gradle's foojay resolver
  provisions it for the toolchains and the daemon where it is missing.
- Until MineColonies returns, the pack has no colony content; M1 does not need it. EMI's loot and ore views are gone
  until the in-game guide exists.
- Two betas in the pack (Regions Unexplored, FTB Teams), listed in the pack README; a crash from Regions
  Unexplored goes to Matěj before anything replaces it.
- A smaller community and weaker AI knowledge of 26.1 make third-party help scarcer; the unobfuscated sources
  partly make up for it.
