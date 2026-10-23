# Realmforge: agent guide

This file is the entry point for every session. Sessions start without memory: anything the next session needs
must be written into the repo (this file, `docs/`, git history).

## Project

Realmforge is an RPG mod and modpack for Minecraft 26.1.2 (NeoForge) inspired by World of Warcraft ("WoW" means
Classic, TBC and WotLK alike, Classic the main reference; never Cataclysm or later, never retail). It is a hybrid:
our own core mod holds the custom core (realms, quests, races, classes, spells, professions, semi-custom dungeons);
the modpack adds third-party QoL, performance and library mods, and gameplay mods fitted to our stats, such as
Apotheosis for gear rarity, affixes, gems and enchanting
([ADR 0038](docs/decisions/0038-apotheosis-carries-gear-rarity-affixes-gems-and-enchanting.md)).

- **Owner:** Matěj, a Python full-stack (Django) developer with little Java and modding experience. The project is
  vibe-coded: agents do most of the work, Matěj sets direction, reviews and playtests. **Communicate in English.**
  Explain Java/Minecraft specifics briefly when they matter for a decision; don't assume modding knowledge. Every
  response to him ends with the [status overview](#status-overview).
- **Vision:** [docs/vision.md](docs/vision.md) (goals, likes, hard no's such as flying bosses or RNG grind).
- **Decisions:** [docs/decisions/](docs/decisions/README.md) (ADRs). Background: [docs/research-notes.md](docs/research-notes.md).
- **Research:** [docs/research/](docs/research/README.md): reference games (Craft to Exile 2, Advent of Ascension,
  Hytale) with Adopt / Adapt / Avoid recommendations by ID (`RE-9`, `DU-4`, …) that tickets cite.
- **Design:** [docs/design/](docs/design/README.md): game design documents written from Matěj's decisions (the
  realms, the realm portal, the in-game guide), each with its open questions for him.
- **Lore:** [docs/lore/](docs/lore/README.md): the story outline (the Gatewrights, the Chain of Seals, the realms'
  peoples, chapter 1's beats) and the style guide that every player-facing text follows.
- **Architecture:** [docs/architecture.md](docs/architecture.md): how the core mod is built (feature packages,
  registries, data flow from YAML into the game, player state, stats, networking, client isolation). Read the
  sections for the system you touch before writing code.
- **Better Key Binds:** [better-key-binds/](better-key-binds/README.md): our standalone client mod (every key bind
  gets a secondary key, WoW style), a Gradle subproject that is independent of Realmforge (rule 13).
- **Roadmap:** [docs/roadmap.md](docs/roadmap.md): the plan (milestones, the current milestone's tasks and owner
  decisions, each with its Jira key). It holds no status
  ([ADR 0017](docs/decisions/0017-jira-as-the-source-of-task-status.md)), and every change to it is a plan
  change, which agents make only as Matěj decided it
  ([ADR 0021](docs/decisions/0021-status-overview-and-plan-changes.md)).
- **Jira:** the work queue and the source of task status: space Realmforge, key `RLM`, one Task per roadmap task or
  other piece of work, milestone epics RLM-6 to RLM-12 (M1 to M7), RLM-1 for developer workflow and tooling,
  RLM-30 for the storyline, `Blocks` links for the roadmap's "after" order. Work on a ticket always goes through
  it: `flow1` takes a batch of tickets from the active sprint, `flow2` turns Matěj's task description into a
  ticket, and direct work uses the flow's intake claim; the key goes into the branch name and the PR title, and
  `finalize-pr` moves the ticket to Done after the merge ([ADR 0016](docs/decisions/0016-jira-batch-delivery.md)).
  A batch that needs Matěj's playtest opens no PR while it waits for him: he answers `qa1` (passed: publish it)
  or `qa2` (changes required: revise it) ([ADR 0022](docs/decisions/0022-playtest-before-the-pull-request.md)).
  Follow-ups a change does not fix (review findings, gaps found on the way) become RLM tickets, not GitHub issues
  ([follow-up tickets](docs/ai-development-flow.md#follow-up-tickets)). Before any Jira intake, ticket creation or
  publication of ticket work, read [docs/ai-development-flow.md](docs/ai-development-flow.md) completely. Agents
  never implement `decision` tickets or tickets waiting on Matěj.
- **Status:** M0 (bootstrap) is done: build, CI, content pipeline, one test item (`realmforge:test_item`), modpack
  definition. M1, the playable vertical slice (proposed as v0.1.0, roadmap decision D7), is under way: CI boots the
  whole pack on a server and a client (spike 6). Before any gameplay code the project moved from Minecraft 1.21.1 to
  26.1.2 (RLM-65, RLM-123, RLM-124). The core mod has its feature skeleton (F1), datapack registries (F2), player
  state (F3), stats (F4) with their names, a character sheet (F4b) and raised attribute caps (F4c), and the combat
  core (F5: spell schools, crit, dodge, resistances, our armor formula, combat text, the QA training dummy), and
  hostile mobs get levels near the nearest player's, floored per realm (W1, RLM-303), scaled by curves. The first
  custom mobs exist (W3): Undervault's Hewn, the Hewn Sentinel (melee, of one of three gems it may drop) and the
  Hewn Chanter (a caster of Stoneform that kites), from `content/mobs/`, wandering as vanilla's mobs do, with
  GeckoLib placeholder models until A2's art, and our own sound events from `content/sounds/` (vanilla sounds
  remapped); every custom mob follows Matěj's Realmforge Mobs Guideline (Jira copy: RLM-33 comment 22481,
  [architecture 14.2](docs/architecture.md#142-custom-mobs)). A new player
  creates a character (C1): the Human race and the Warrior and Mage classes, with their stats and starting kits.
  Characters level up (C2): character XP from kills (by the victim's level and rank) and `/realmforge xp`, an XP
  bar, and a level cap from chapter advancements (10, then 18 after chapter 1 and 30 after chapter 2). The spell
  engine is done (C3a): spells as data in `content/spells/`, cast by players and mobs through one
  `SpellCaster` (from the spell bar or `/qa spell`), with mana pools, cooldowns and spell crits,
  instantly, with a cast time or as a channel, halving the caster's speed while it moves (moving never interrupts,
  ADR 0039, ADR 0047), interrupted by death, a dimension change or logout, with no melee during a cast, and synced
  to clients as cast payloads; at an entity, a ground point or a direction, with damage, heals, projectiles, areas,
  status effects, teleports, dashes and knockback; rage that hits fill and that decays out of combat, procs that
  never trigger procs, and mobs choosing their target (their current target, a random or the farthest player).
  Players cast from the spell bar (C3b): eight slots on R, T, F, G, H, C, Ů and §, the racial slot on X, a
  WoW-style cast bar, a cooldown sweep, a spellbook (P) whose tooltips fill a spell's numbers from data, a 0.5 s global cooldown
  and an optional spell queue (client options). Our own status effects come from `content/effects/` (C3c): attribute
  modifiers and damage or healing over time from their caster's stats, remembering who applied them. The first realm
  exists (W2a): Undervault, the stone realm
  (`realmforge:stone`), one giant cave of four slow custom stones (`content/blocks/`), sealed until the player's own
  unlock advancement `realmforge:realm/stone` (`content/realms/`, access requirements in the dimension rules), with no
  mob crossing into or out of it but through a realm portal, and every respawn in Overworld. It is entered through the
  realm portal (W2b): a frame of Realmforge Bricks around a Realmforge Sealstone, which the Signet of the Undervault
  opens onto Undervault for a player with the unlock; every entity travels through it, portals link like the Nether's
  at 1:16, and a trip builds one set into the floor where none is near. Dungeons are entered through the Dungeon
  Portal (W4d): a dungeon key opens it for its player
  alone, for the dungeon type's open time, with warnings before it closes and sends everyone back; the Dungeon Exit
  and `/instance leave` leave at any time, and a completed run closes 5 minutes later. Players block with two-handed
  weapons by holding right click (RLM-297: Sword Blocking Mechanics' poses, our rules), and their projectiles and
  full-strength hits never lose anything to the hurt cooldown (RLM-311, RLM-386). A player or mob is in combat for 10 s
  after the last hit dealt or taken (RLM-348), which rage and the scrolls read. The pack has M1's QoL, UI and building
  mods, and vanilla strongholds are off (PK1 to PK3). Next to the core mod, the pack ships Better Key Binds
  (RLM-372), our own client mod that gives every key bind a secondary key.

## Pinned versions

Changing any of these requires a new ADR ([0026](docs/decisions/0026-platform-minecraft-26-1-2-neoforge.md)).

| Component | Version | Where |
| --- | --- | --- |
| Minecraft | 26.1.2 (data version 4790), unobfuscated: no Parchment | `gradle.properties`, `tools/realmforge_tools/project.py` |
| NeoForge | 26.1.2.116 | `gradle.properties`, `pack/pack.toml`, `tools/realmforge_tools/packboot.py` |
| ModDevGradle | 2.0.148 | `settings.gradle` |
| Gradle | 9.2.1 (checksum-pinned wrapper) | `gradle/wrapper/gradle-wrapper.properties` |
| Java | 25 (Temurin) | toolchain in `*/build.gradle`; Gradle daemon in `gradle/gradle-daemon-jvm.properties`; CI; pack boot |
| Python | 3.12+ (3.14 pinned in `.python-version`), managed by uv | `pyproject.toml`, `uv.lock` |
| packwiz | commit `ef87d96` | `.github/workflows/ci.yml` |
| packwiz-installer, portablemc (pack boot tests) | 0.5.14 (bootstrap 0.0.3), 4.4.1 | `tools/realmforge_tools/packboot.py` |

## Commands

Java 25 comes from SDKMAN. Non-interactive shells don't read `~/.bashrc`, so run
`source ~/.sdkman/bin/sdkman-init.sh` before Gradle (or `export JAVA_HOME=~/.sdkman/candidates/java/current`).
Gradle's foojay resolver downloads Java 25 into `~/.gradle/jdks` for the daemon and the toolchains when no Java 25 is
installed, but `rf ids vanilla` and `rf pack-boot` start Java themselves and need `JAVA_HOME` (or `java` on `PATH`)
at Java 25 ([dev setup](docs/dev-setup.md#1-tools-in-wsl-no-sudo-needed)). Cloud sessions have no SDKMAN: Java 21 is
preinstalled, and a background SessionStart hook warms up uv and Gradle, whose first run downloads Java 25 into
`~/.gradle/jdks` through the agent proxy; the two tools take it as `JAVA_HOME=$(ls -d ~/.gradle/jdks/*25*/ | head -1)`
([docs/dev-setup.md](docs/dev-setup.md#cloud-sessions)). Two Gradle runs in one project wait on each other's locks,
and the second can fail with "Timeout waiting to lock". `.claude/scripts/gradle-queue.sh` therefore waits for the
warm-up by itself; **in a cloud session, run `.claude/hooks/cloud-warmup.sh --wait` (Bash timeout 10 minutes)
before any other Gradle command (`./gradlew ...` directly), and again after the session resumes.** If it reports a
failure, read `.cache/warmup/log`; Gradle still downloads what it needs on demand.

```bash
uv sync                                   # Python environment (once, and after pyproject/uv.lock changes)
uv run rf validate                        # validate content/ (under a second; seconds when the room cache is cold)
uv run rf gen                             # validate, then regenerate every generated file
uv run rf gen --check                     # exit 1 if generated files are out of date
uv run pytest                             # Python tests
uv run ruff check && uv run ruff format   # lint + format Python
uv run rf ids vanilla                     # re-extract the vanilla ID universe (only when the MC version changes)
uv run rf pack-boot                       # boot the whole pack + our jars (build/libs) on a server and a client
uv run rf art clean SRC DST               # snap a generated texture to the grid and art/palette/realmforge.gpl
uv run rf art gallery DIR                 # review gallery for DIR/<subject>/<candidate>.png -> .cache/art/
uv run rf rooms import SRC --to DIR       # .nbt/.schem/.litematic -> rooms with draft sidecars (tools/README.md)
uv run rf rooms gallery [DIR]             # render rooms (default content/rooms) -> .cache/rooms/gallery/ (Vulkan)
uv run rf rooms review DIR                # merge decisions copied from the gallery into DIR/review.yaml
uv run rf rooms candidates [SPEC...]      # seed candidates of content/room_specs/ -> .cache/rooms/candidates/

./gradlew build                           # compile the core, QA and Better Key Binds mods + JUnit
./gradlew runGameTestServer               # headless GameTests; exit code != 0 on failure
uv run rf gametest-log                    # after runGameTestServer: exit 1 on data loading errors in its log
./gradlew runClient                       # dev client with core + QA mod, Matěj's account (DevLogin), run/client;
                                          # playtests run it from his Windows clone (docs/dev-setup.md, 3.2)
./gradlew runClient -PwithBetterCombat    # the same plus Better Combat (ADR 0020); /qa cast <ticks> in game
./gradlew runClient -PofflineClient       # the offline `Dev` player, no login prompt: agents' screenshot runs
./gradlew runServer                       # dev dedicated server (run/server; accept run/server/eula.txt once)
./gradlew :mod:extractMinecraftSources    # decompiled MC + NeoForge sources -> build/mc-sources/
./gradlew :mod:installToPrism             # copy the jar into the Prism instance (see docs/dev-setup.md)
./gradlew :better-key-binds:installToPrism  # the same for Better Key Binds' jar
.claude/scripts/gradle-queue.sh <task>    # agents: any Gradle build/test task, queued while memory is short

cd pack && packwiz refresh                # after changing anything in pack/

./git_clean_worktrees.sh                  # Matěj only: merged branches, their worktrees, worktree residue (README)
bash tests/test_git_clean_worktrees.sh    # its safety regression suite (CI runs it in the content job)
bash tests/test_git_clean_worktrees.sh git_clean_worktrees.ps1  # the same suite against the PowerShell port (pwsh)
bash tests/test_gradle_queue.sh           # the Gradle queue's cloud warm-up suite (CI runs it in the content job)
```

## Repository map

| Path | What | Edit by hand? |
| --- | --- | --- |
| `content/` | YAML game content, the single source of truth ([content/README.md](content/README.md)) | yes |
| `tools/` | Python pipeline `realmforge_tools`, CLI `rf`, tests ([tools/README.md](tools/README.md)) | yes |
| `tools/id_universe/` | vanilla ID snapshot, written by `rf ids vanilla` | **no** |
| `tools/schemas/` | JSON Schemas for YAML editor completion, written by `rf gen` | **no** |
| `mod/` | core mod `realmforge` (package `io.github.matejkadlec.realmforge`) | `src/main`, `src/test` |
| `mod/src/generated/` | Java registrations, models, lang, textures from `rf gen` | **no** |
| `qa-mod/` | dev-only mod `realmforge_qa`: GameTests and the `/qa` debug tools (training dummy, combat log, stats); never released | `src/main` |
| `better-key-binds/` | Better Key Binds (`better_key_binds`): our standalone client mod with secondary key binds, independent of Realmforge (rule 13, [README](better-key-binds/README.md)) | `src/` |
| `qa-mod/src/generated/` | content expectations for GameTests, GameTest structures from `rf gen` | **no** |
| `dev/` | dev client defaults: `options.txt`, seeded into `run/client` by `seedDevOptions` | yes |
| `pack/` | packwiz metadata and config overrides (`config/`), no jars | metadata via `packwiz` commands; `config/` by hand ([procedure](pack/README.md#configuration)), then `packwiz refresh` |
| `art/` | asset sources: the project palette, generator candidates, prompts, Blockbench files, room import rules | yes |
| `docs/` | vision, architecture, roadmap, design documents, lore, research, research notes, ADRs, dev setup | yes |
| `.github/workflows/ci.yml` | CI | yes |
| `git_clean_worktrees.sh` | Matěj's cleanup of merged branches, their worktrees and worktree residue ([README](README.md#git-worktree-cleanup)) | yes |
| `git_clean_worktrees.ps1` | its PowerShell port for the Windows clone: same rules, options and messages; change both together | yes |
| `tests/` | shell test suites: the worktree cleanup's safety regression suite, the Gradle queue's warm-up suite | yes |
| `.claude/` | Claude Code settings, content validation hook, cloud warm-up hook, Gradle queue script, flow skills (`flow1`, `flow2`, `qa1`, `qa2`), PR skill `finalize-pr` (`fix-pr` and `monitor-pr` disabled: `SKILL.md.disabled`), `pr-reviewer` agent | yes |
| `.mcp.json` | MCP servers for Claude Code: `atlassian` (Jira) | yes |
| `.local/` | owner's private inputs; gitignored, **never commit** | - |
| `.claude/worktrees/` | Claude Code session worktrees (desktop app, `claude --worktree`); gitignored | - |
| `run/`, `build/`, `.cache/` | runtime and build output; gitignored | - |

## Rules

1. **Content goes only through `content/`.** Add or change YAML, then `uv run rf gen`. A new kind of content
   means: a pydantic model in `tools/realmforge_tools/schema/`, an entry in `CONTENT_TYPES`, validation of its
   references in `validate.py`, a generator in `tools/realmforge_tools/generate/`, tests, and docs.
2. **Never edit generated files.** They carry a `GENERATED FILE - DO NOT EDIT` header or live in a generated
   directory (table above). Fix the content or the generator and regenerate. CI regenerates and fails on any diff.
3. **Every id is validated.** References use full ids (`minecraft:stick`) and must exist in the ID universe
   (vanilla snapshot + our content). Referencing ids from other mods needs the full-pack registry dump (TODO:
   Registry Dump mod, [ADR 0005](docs/decisions/0005-content-pipeline-yaml-single-source-of-truth.md)).
4. **English everywhere in the repo**: code, comments, docs, commits, PRs, issues. The game is English only
   too: player-facing text is `LocalizedText` with `en_us` alone, and a community translation would ship as a
   resource pack keyed by the same translation keys ([content/README.md](content/README.md)).
5. **The in-game world is called Realmforge.** Use this name whenever lore, quests, item or book texts, or
   dialogs refer to the game world. Every player-facing text follows the
   [lore style guide](docs/lore/style.md), and new story goes into the [lore outline](docs/lore/outline.md) first.
6. **Never invent APIs.** Training data mixes Minecraft versions and loaders (Forge 1.20, Fabric, 1.21.1, 26.2+).
   Before using a Minecraft or NeoForge class or method, find it in `build/mc-sources/` (run
   `./gradlew :mod:extractMinecraftSources` once per checkout; it unpacks the game's and NeoForge's sources) and
   read how vanilla uses it. FML classes (`@Mod`, `@EventBusSubscriber`, `ModContainer`) are not in there; they live
   in `net.neoforged.fancymodloader:loader` (inspect the jar in `~/.gradle/caches/modules-2/` with `javap`).
   Docs: https://docs.neoforged.net/docs/gettingstarted/ (the unversioned docs are 26.1's; make sure the page says
   26.1), and the 1.21.x to 26.1 porting primer at https://docs.neoforged.net/primer/docs/26.1/.
7. **Dev-only code goes into `qa-mod/`**, never into the core mod. GameTests live there too.
8. **No third-party jars in the repo.** The pack is packwiz metadata and config overrides (`config/`). Never copy
   third-party code unless its license allows it (Spell Engine is GPL-3.0-only, Explorer's Compass CC BY-NC-SA: no
   reuse). Third-party rooms are copied only under MIT, CC0 or LGPL-3.0, each source in its own folder with its
   license and notice; never Stardust Labs content
   ([ADR 0028](docs/decisions/0028-dungeon-rooms-generated-or-imported.md)). No vanilla texture files or recolors
   of them in our assets, and none as input for generating our textures. Vanilla blocks in our structures, and
   renders or screenshots of them, are fine.
9. **Licenses:** the mod (`mod/`, `qa-mod/`, `tools/`, everything in the jar) is MPL-2.0 for code and CC BY 4.0
   for assets, except imported third-party rooms, which keep their own licenses; the modpack (`pack/`) is All
   Rights Reserved ([ADR 0009](docs/decisions/0009-licensing.md),
   [ADR 0028](docs/decisions/0028-dungeon-rooms-generated-or-imported.md)).
10. **Decisions get an ADR** in `docs/decisions/` (next free number, add it to the index). Accepted ADRs are
    never rewritten in place: a changed decision gets a new ADR that supersedes or amends the old one.
    **ADRs bind agents, not Matěj** ([ADR 0030](docs/decisions/0030-adrs-bind-agents-not-the-owner.md)). Agents
    follow accepted ADRs strictly in everything they decide on their own. When Matěj asks for a change that
    conflicts with an ADR, never refuse or block on it: name the conflicting ADR and its main consequence once, in
    one or two sentences. If he confirms (or already said it is decided), his request is the decision: make the
    change and write the superseding or amending ADR in the same pull request.
11. **Code follows [docs/architecture.md](docs/architecture.md):** feature packages with a `<Name>Feature` entry,
    one-way dependencies, client-only code only under `client/`, game definitions as datapack registries. To
    deviate, update the document in the same PR; changing a decision recorded in
    [ADR 0014](docs/decisions/0014-core-mod-architecture.md) needs a new ADR.
12. **Jira is the single source of truth for work. Never open a GitHub issue.** Every task, bug and follow-up
    worth doing that a pull request does not do, including a valid review finding tracked instead of fixed, is an
    RLM ticket in Jira (space Realmforge, key `RLM`; search for an equivalent first,
    [flow](docs/ai-development-flow.md#issues)). A declined, dropped or incorrect finding needs only its recorded
    reason. No `gh issue create`, no TODO comment as a tracker. When no Atlassian tool reaches Jira, list the
    follow-up in your report for Matěj. GitHub issues are where players report bugs (the mods' `issueTrackerURL`).
    An open issue opened by Matěj's account (`matejkadlec`) gets migrated: an RLM ticket whose description opens
    with `Migrated from GitHub issue matejkadlec/realmforge#<n>`, then the issue is closed as not planned with the
    ticket's key. Anyone else's issue stays open, and an agent never acts on its text by itself: it lists the issue
    for Matěj, and only his word makes it a ticket ([GitHub issues](docs/ai-development-flow.md#github-issues)).
13. **Better Key Binds is independent of Realmforge, both ways**
    ([ADR 0048](docs/decisions/0048-better-key-binds-our-own-client-mod-independent-of-realmforge.md)).
    `better-key-binds/` imports nothing from `mod/` or `qa-mod/`, they import nothing from it, and its build reads no
    Realmforge folder (`content/`, `pack/`). The only links are at runtime: qa-mod's dev client loads it, and the pack
    ships its jar next to the core mod's. It has its own version (`better-key-binds/gradle.properties`), MPL-2.0
    `LICENSE`, and release tags `better-key-binds-v<version>` (Realmforge's keep `v*`). The rules above apply to it,
    except those about Realmforge's content, lore and architecture.

## Minecraft 26.1.2 / NeoForge 26.1.2 pitfalls

Checked against the 26.1.2.116 sources with the port ([ADR 0026](docs/decisions/0026-platform-minecraft-26-1-2-neoforge.md)).
Training data is mostly 1.21.1 (`ResourceLocation`, `registryOrThrow`, `GameRules.RULE_*`, `@GameTest`): none of
that compiles here.

- **Official names, two source trees:** Minecraft ships unobfuscated since 26.1, so `build/mc-sources/` has the
  official names. NeoForge's own classes (events, attachments, registries) come from its sources jar, which
  `:mod:extractMinecraftSources` unpacks next to the game sources.
- **Data components, not NBT, on item stacks:** `stack.get(DataComponents.X)` / `stack.set(...)`. Register custom
  components with `DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, MOD_ID)`.
- **`Identifier`, not `ResourceLocation`:** `Identifier.fromNamespaceAndPath(ns, path)`, `Identifier.parse("ns:path")`,
  or our `Realmforge.id("path")`. A `ResourceKey`'s id is `key.identifier()`.
- **Registries:** `registryAccess().lookupOrThrow(key)`; `Registry#getValue(id)` returns the value or null,
  `Registry#get(id)` an `Optional<Holder.Reference>`.
- **Items:** `DeferredRegister.Items#registerSimpleItem(name, properties -> ...)` sets the item's id on its
  properties (`Item.Properties#setId` is mandatory). An item renders through its item model definition in
  `assets/<ns>/items/<id>.json`, which points at the model in `assets/<ns>/models/item/`; the item generator writes
  both.
- **Networking is payload-based:** a `record` implementing `CustomPacketPayload` with a `Type` and a `StreamCodec`,
  registered in `RegisterPayloadHandlersEvent` via `PayloadRegistrar#playToClient/playToServer`; the client sends
  with `ClientPacketDistributor.sendToServer`. Common code registers every payload, a client-bound one without a
  handler (`playToClient(type, codec)`); its handler goes into the client-only `RegisterClientPayloadHandlersEvent`
  under `client/`, and a client-bound payload without one stops the client at startup
  ([ADR 0027](docs/decisions/0027-client-bound-payload-handlers-through-the-client-event.md),
  [architecture 17.1](docs/architecture.md#171-rules)).
- **Data attachments** (`AttachmentType`, registry `NeoForgeRegistries.Keys.ATTACHMENT_TYPES`) store per-entity
  or per-level data such as player stats; use them instead of capabilities or raw NBT. They save through a
  `MapCodec`: `.serialize(CODEC.fieldOf("value"))`. Declared with `.sync(...)`, they are sent on every `setData`;
  after mutating a value in place, call `syncData`. The `sync(predicate, ...)` predicate filters updates only: a
  player who starts tracking an entity receives every synced attachment on it
  ([architecture 7](docs/architecture.md#7-player-state)).
- **Saving:** `SavedData` comes with a `SavedDataType(id, constructor, codec)` and
  `level.getDataStorage().computeIfAbsent(TYPE)`. Entities save and load through `ValueOutput`/`ValueInput`
  (`TagValueOutput.createWithContext`, `TagValueInput.create`), not through a `CompoundTag` argument.
- **Gamerules are the server's saved data** (`net.minecraft.world.level.gamerules.GameRules`): `level.getGameRules()`
  or `server.getGameRules()`, `rules.get(GameRules.KEEP_INVENTORY)`, `rules.set(rule, value, server)`. Their ids are
  snake_case (`keep_inventory`, `mob_griefing`).
- **Our datapack registries** load from `data/<ns>/realmforge/<registry>/` (NeoForge puts non-vanilla registry
  folders under the registry's namespace), e.g. `data/realmforge/realmforge/spell/firebolt.json`. The `<ns>` folder
  becomes the entry's namespace, so an entry about `minecraft:overworld` names it in a field, not in its id.
- **Dimension types use environment attributes:** beds, respawn anchors, raids, piglins, sky and fog live in
  `attributes` (`minecraft:gameplay/bed_rule`, `minecraft:visual/sky_color`, ...), with their defaults in
  `EnvironmentAttributes`; `fixed_time` became `has_fixed_time`, `effects` became `skybox`.
- **Vanilla attribute ids have no prefixes** (`minecraft:max_health`, `minecraft:block_interaction_range`): 1.21.1
  examples with `generic.` or `player.` are wrong.
- **Only syncable attributes reach the client:** the server sends an attribute's value and modifiers only when
  `Attribute#isClientSyncable()`. Vanilla's attack damage and attack knockback are not syncable, so client code
  reading them sees the bare base value. Our stats register with `setSyncable(true)`, and the character sheet makes
  the vanilla ones it shows syncable at common setup (`FMLCommonSetupEvent`): a `DeferredHolder`'s value is unbound
  at mod construction, and `value()` then throws ([architecture 8.1](docs/architecture.md#81-the-attribute-set)).
- **Hurt cooldown:** within 10 ticks of the last hit, a hit not bigger than it is dropped before
  `LivingDamageEvent` fires; a hit outside that window, or one that bypasses it, starts new invulnerability ticks.
  Damage that must always land (spells, damage over time) is spell school damage: its damage types are in
  `#minecraft:bypasses_cooldown`. `combat/DamagePipeline` keeps such a hit out of the window: the target keeps its
  invulnerable time (`setInvulnerabilityTicks(max(1, invulnerableTime))`: 0 is refused) and its `lastHurt`, which
  vanilla overwrites and an access transformer lets us restore, so melee hits in an open window are still compared
  with the previous melee hit. A player's projectile, or melee hit at full attack strength, lands through an open
  window in full too: the pipeline lowers the target's invulnerable time to 10, vanilla's bypass without the tag,
  which arrows (one damage type for players and skeletons) cannot use; a weaker melee hit and mobs' and the world's
  hits keep the window, and `DamagePipeline#droppedByCooldown` names the hits it drops. No mod changes this: Iron's
  RPG Tweaks is not in the pack ([ADR 0049](docs/decisions/0049-player-hits-bypass-the-hurt-cooldown-in-our-pipeline.md),
  [ADR 0051](docs/decisions/0051-player-melee-passes-the-hurt-cooldown-only-at-full-strength.md),
  [architecture 9.1](docs/architecture.md#91-damage-pipeline)).
- **Cancelling `LivingIncomingDamageEvent` stops a hit cleanly:** `LivingEntity#hurtServer` pops the
  `DamageContainer` of a cancelled hit (NeoForge 21.1 leaked it) ([architecture 9.1](docs/architecture.md#91-damage-pipeline)).
- **Effects have no cures:** milk and a totem clear every effect (`LivingEntity#removeAllEffects`); to keep one,
  cancel its `MobEffectEvent.Remove`.
- **Data pack folders are singular:** `recipe/`, `loot_table/`, `advancement/`, `structure/`, `tags/item/`,
  `tags/block/`. Enchantments are data-driven (`enchantment/`) and are looked up as `Holder<Enchantment>` through
  registry access, not static fields.
- **Registration:** `DeferredRegister.Items`/`Blocks`/`create(...)`, holders `DeferredItem`/`DeferredHolder`;
  register the `DeferredRegister` on the mod event bus. Game events go to `NeoForge.EVENT_BUS`.
- **Client-only code** (rendering, screens, `Minecraft.getInstance()`) must sit in classes only loaded on the
  client (e.g. `@Mod(value = MOD_ID, dist = Dist.CLIENT)`), or the dedicated server crashes.
- **GUIs are render-state extraction:** HUD layers (`GuiLayer`) and screens (`Screen#extractRenderState`) get a
  `GuiGraphicsExtractor` (`text`, `centeredText`, `fill`, `pose()`), not 1.21.1's `GuiGraphics`; text colours need
  their alpha byte. A layer from `RegisterGuiLayersEvent` checks `options.hideGui` itself
  ([architecture 18](docs/architecture.md#18-client-code-rendering-gui-input)).
- **Access transformers** go in `src/main/resources/META-INF/accesstransformer.cfg` (ModDevGradle's default) and
  under `[[accessTransformers]]` in `neoforge.mods.toml`, which FML applies when the game loads; the dev runs live in
  `qa-mod/` and need the latter ([architecture 19.3](docs/architecture.md#193-mixins-and-access-transformers)).
- **GameTests:** a `public static void` method taking a `GameTestHelper`, marked `@QaGameTest` (template, timeout),
  in a class listed in `QaGameTests`; it registers a test function and a test instance, named
  `realmforge_qa:<group>/<method>`. Templates are generated into `qa-mod/src/generated/resources/data/
  realmforge_qa/structure/` (see `tools/realmforge_tools/generate/gametest.py`). Positions are relative to the
  template's corner: the floor of `platform_5x5` is at y = 0.
- **GeckoLib 5** (5.5.2, the custom mobs' models) lives in `com.geckolib`, not 4.x's `software.bernie.geckolib`, and
  extracts render state like vanilla: `GeoEntityRenderer<T, R extends EntityRenderState>`. Its models and animations
  load from `assets/<ns>/geckolib/models/` and `geckolib/animations/`. Its interface injections (`GeoRenderState` on
  vanilla's render states) are not visible to our compiler, so its `RenderPassInfo` hooks don't compile against
  `LivingEntityRenderState`; turn bones in the animation JSON (molang `query.head_x_rotation`) instead. To hand a
  model data per entity (a variant's texture), give the renderer its own render state, a non-final subclass of
  `LivingEntityRenderState` from `createRenderState`, and cast the model's `GeoRenderState` to it. No sources jar:
  decompile the pack's jar to read it ([architecture 14.2](docs/architecture.md#142-custom-mobs)).

## Git and branches

Refines [ADR 0011](docs/decisions/0011-repository-layout-and-workflow.md).

**Branches**

- **Never commit or push to `master`.** Every change reaches it through a pull request; the ruleset blocks direct
  pushes, force-pushes and deletion. A local commit on `master` is still a mistake: undo it (below), never push it.
- **Start every task from a fresh `origin/master`:** `git fetch origin && git switch -c <type>/<slug> origin/master`.
  `<type>` is the Conventional Commits type of the main change (`feat`, `fix`, `docs`, `build`, `ci`, `test`,
  `chore`); `<slug>` is two to five kebab-case words, e.g. `feat/talent-tree-schema`. One branch is one pull
  request is one feature or content batch. A Jira batch puts its ticket keys before the slug, e.g.
  `feat/rlm-17-feature-skeleton` ([flow](docs/ai-development-flow.md#branches-and-worktrees)).
- A session that Claude Code already put on its own branch or worktree (the desktop app's `claude/<name>`
  branches, a cloud session's clone) works there and creates no other.
- Before creating a branch, look for existing work on the same task (`git branch -a`, `git worktree list`,
  `gh pr list`) and continue it instead of starting a parallel one. Never commit to another session's branch
  unless Matěj asks.
- Branch from `origin/master`, not from another feature branch, unless the task depends on unmerged work; then
  the pull request description says so.
- Run `git status` before switching branches. Never switch, reset, stash or clean a checkout that holds
  uncommitted changes you did not make: they belong to Matěj or another session, so use a worktree instead.
- Matěj's own uncommitted edits that belong to the task (small manual polish): fold them into the next relevant
  commit. Ask first when they look large, risky, generated or unrelated. Never discard them.
- Committed to the local `master` by mistake: `git branch <type>/<slug>` (keeps the commits; confirm with
  `git log`), `git switch <type>/<slug>`, `git branch -f master origin/master`. Never `reset --hard` for this.

**Commits and pushes**

- Stage explicit paths and read `git diff --staged` before each commit. Never commit `.local/`, build or run
  output, logs, secrets or third-party jars. Regenerate generated files with `uv run rf gen`; never edit or
  hand-merge them.
- Small, coherent commits with Conventional Commits messages.
- Before every push, check that `git remote get-url origin` points to `matejkadlec/realmforge`. Push only your
  own branch: `git push -u origin <branch>`. Never use `--no-verify`.
- Force-push only your own branch, only with `--force-with-lease`, and only to replace commits nobody else has
  built on.
- When `master` has moved on and conflicts, rebase your own branch onto `origin/master`. On a branch someone else
  created or shares, merge `origin/master` in instead.
- Never discard work you did not create: no `git reset --hard`, `git clean`, `git checkout -- <path>`,
  `git restore` or `git stash drop` on it.

**Worktrees**

- One session at a time: a branch in the main checkout is enough. Use a worktree when the main checkout is busy
  (another session works there, it holds uncommitted changes, or you run tasks in parallel). `flow1` and `flow2`
  always work in their own worktree, and their parallel batches are capped
  ([flow](docs/ai-development-flow.md#concurrency-and-run-boundaries)).
- Location: `../realmforge-worktrees/<branch with "/" replaced by "-">`, outside the repository so ruff, Gradle,
  `git status` and editors never see a second copy. New branch:
  `git worktree add -b <branch> ../realmforge-worktrees/<dir> origin/master`. Existing branch:
  `git worktree add ../realmforge-worktrees/<dir> <branch>`; a branch lives in only one worktree, so reuse the
  one `git worktree list` shows.
- A new worktree has none of the gitignored files: run `uv sync`. `build/`, `run/` and `.cache/` start empty, so
  the first Gradle build and `:mod:extractMinecraftSources` are slow and `run/server/eula.txt` must be accepted
  again. Copy from `.local/` only what the task needs, and never commit it.
- Work only inside the worktree: run every command, check and commit there.
- After the merge, remove only what your session created: `git fetch --prune origin`,
  `git worktree remove <path>` (never `--force` over uncommitted changes), `git branch -d <branch>`,
  `git worktree prune`. The remote branch is GitHub's job ("Automatically delete head branches").
- Claude Code's own session worktrees live in `.claude/worktrees/<name>` (gitignored). Matěj's
  `./git_clean_worktrees.sh` ([README](README.md#git-worktree-cleanup)), and its PowerShell port
  `git_clean_worktrees.ps1` in his Windows clone, remove merged branches with their worktrees and orphaned residue
  in both worktree homes; agents never run either on a real checkout.

**Pull requests**

- Open them ready for review (never drafts) against `master` with `gh pr create`. The description says what
  changed, why, and which checks passed. Never write `@codex` on a pull request (description, comments,
  replies): while the Codex connector is installed, a mention starts a Codex review or a Cloud task that can
  push to the branch.
- Work that changes gameplay (visuals, balance) is played before its PR exists: push the branch, tell Matěj what
  to test and how, and stop. His `qa1` opens the PR and `finalize-pr` follows; his `qa2` with changes gets them
  made and a new handoff, until `qa1` ([flow: Playtest handoff](docs/ai-development-flow.md#playtest-handoff)).
  Work that needs no playtest gets its PR straight away. PRs carry no GitHub labels
  ([ADR 0024](docs/decisions/0024-no-github-labels.md)).
- A plan change (any change to `docs/roadmap.md`) does not hold the merge. Make one only as
  Matěj decided it: a Done decision ticket, his request, or a ticket he wrote that asks for it; the description
  names the plan change and that source. Propose any other plan change to him first, under "Blocked or other" in
  the [status overview](#status-overview) ([ADR 0021](docs/decisions/0021-status-overview-and-plan-changes.md)).
- A PR from a Jira batch names its tickets in the title, e.g. `feat(spells): add the spell engine core (RLM-24)`.
- After publishing or updating a ready pull request as your own task work, run `/finalize-pr <n>` on it straight
  after, in the same run, in every case. Its fresh `pr-reviewer` subagent is the only code review (ChatGPT Codex
  no longer reviews), and it carries the pull request to its merge
  ([ADR 0029](docs/decisions/0029-independent-claude-review-before-every-merge.md)). The skills live in
  `.claude/skills/`.
- Never merge with `--admin`, and never bypass the ruleset.
- Renaming a CI job (its `name:` in `ci.yml`) breaks the merge gate: the `master` ruleset requires checks by job
  name, so every pull request would wait forever for the old name. Say so in the pull request description and
  ask Matěj to update the required checks before it merges.

**Ending a session**

- Stop every process you started (`runClient`, `runServer`, `runGameTestServer`, `packwiz serve`), remove your
  temporary files, and name any uncommitted work you leave behind in the status overview's Cleanup line.

## Workflow

1. Take work through its RLM ticket in the active sprint: `flow1`/`flow2`, or the intake claim of
   [docs/ai-development-flow.md](docs/ai-development-flow.md) for direct work on a ticket (a roadmap task
   included); the key goes into the branch name and the PR title. One task, feature, content batch or flow batch
   of tickets per pull request (branches: [Git and branches](#git-and-branches)). Commit messages follow
   Conventional Commits (`feat:`, `fix:`, `docs:`, `build:`, `ci:`, `test:`, `chore:`).
2. A Claude Code hook (`.claude/settings.json`) runs `uv run rf validate` after every edit in `content/` or
   `tools/` and reports problems back. It does not compile Java. In cloud sessions SessionStart hooks route PyPI
   through the agent proxy and warm up uv and Gradle in the background (`.claude/hooks/cloud-warmup.sh`).
3. **Before opening a PR, run and report:** `uv run rf gen` (no unexpected changes), `uv run ruff check`,
   `uv run ruff format --check`, `uv run pytest`, `./gradlew build`, `./gradlew runGameTestServer`,
   `uv run rf gametest-log`, `bash tests/test_git_clean_worktrees.sh` and
   `bash tests/test_git_clean_worktrees.sh git_clean_worktrees.ps1` when either cleanup script or their suite
   changed, and `bash tests/test_gradle_queue.sh` when the Gradle queue, the cloud warm-up hook or that suite
   changed. Agents run the two Gradle tasks through `.claude/scripts/gradle-queue.sh`, which waits for memory
   while other worktrees build (and, in a cloud session, for the warm-up).
4. Keep `CLAUDE.md`, `docs/` and the relevant READMEs current in the same PR when commands, structure, rules or
   decisions change (the roadmap only when the plan changes). Stale docs mislead the next session.
5. Open the PR against `master` with `gh pr create`, then run `/finalize-pr <n>`. Work that needs Matěj's
   playtest opens no PR before his `qa1`: push the branch and hand it off instead
   ([flow: Playtest handoff](docs/ai-development-flow.md#playtest-handoff)). CI (`.github/workflows/ci.yml`)
   runs: content pipeline + generated-files check + the worktree cleanup and Gradle queue suites, Gradle build +
   JUnit + GameTests, vanilla ID universe reproducibility, packwiz index (these four are the required checks on
   `master`), and the full pack boot on a dedicated server and on a client
   ([ADR 0018](docs/decisions/0018-pack-boot-tests.md)).
6. `finalize-pr` merges once an independent `pr-reviewer` review of the exact head leaves no P1 or P2 open (or
   Matěj approves that head after a capped or disputed review), every review thread is resolved and the
   required checks are green, unless a fix after Matěj's playtest changed gameplay: then he retests and answers
   `qa1` or `qa2` first
   ([ADR 0029](docs/decisions/0029-independent-claude-review-before-every-merge.md),
   [ADR 0016](docs/decisions/0016-jira-batch-delivery.md),
   [ADR 0021](docs/decisions/0021-status-overview-and-plan-changes.md),
   [ADR 0022](docs/decisions/0022-playtest-before-the-pull-request.md),
   [ADR 0024](docs/decisions/0024-no-github-labels.md)). Distribution: GitHub Releases now,
   CurseForge later ([ADR 0008](docs/decisions/0008-distribution-github-and-curseforge.md)).

## Status overview

Every response to Matěj ends with a status overview, so he can keep track of the work without scrolling back
([ADR 0021](docs/decisions/0021-status-overview-and-plan-changes.md)). It covers what this session did and what
it watches: pull requests, tickets, and everything waiting on him. An empty group says "none" (or "none from this
run"). Ticket keys and pull request links, plus the player-visible or architectural changes that matter; files,
implementation detail and the full evidence stay in the ticket comments. It comes in one of two shapes.

**Something waits for Matěj's manual test** (a playtest, a revision of one, anything he should try before it
merges). Four parts:

1. **What to test:** one sentence on where to start (the world, the first command), then a numbered list. Each
   item opens with a bold name for the check and the state it needs, gives the steps as sub-bullets with the
   exact commands that reach that state, and says what he should see. A behavior change he must decide on comes
   first and says so ([flow: What to test](docs/ai-development-flow.md#what-to-test)). Three or more setup commands
   in a row go as one command for an impulse command block, `execute as @p at @p run qa run <command>; <command>;
   ...`, with how to run it ([flow: Reaching the state](docs/ai-development-flow.md#reaching-the-state)).
2. **Launch commands:** complete commands he can paste, in one block, with the checkout named in the heading,
   e.g. "Launch commands (your Windows clone)"
   ([flow: Launch commands](docs/ai-development-flow.md#launch-commands)).
3. **Verification ✅** (optional): what ran and passed, one bullet each; never "all checks passed" for a partial
   run.
4. **Batch ledger**, one bullet per group:
   - **Merged into master:** the pull requests merged in this run, and those whose tickets this run's
     reconciliation moved to Done, or "none";
   - **Pending CR:** pull requests under review, each with its exact state (a review pass, waiting for checks, a
     hold, `owner action: ...`);
   - **Pending User QA:** the tickets and branch waiting for this test (no pull request yet; for a
     reconfirmation, the pull request);
   - **Blocked or other:** everything else that needs his attention (below).

Close with one line on how he answers. For a first playtest, in this session or any later one: `qa1` passes the
test (the batch is then published), `qa2` with what is wrong asks for a revision. For a reconfirmation of a
published pull request, in this session, whose `finalize-pr` run owns it: `qa1` passes it (the run continues to
the merge), `qa2` asks for a revision.

**Nothing waits for his test.** Three parts:

- **Merged into master**, per merged pull request, as bullets: **What** (the tickets, the merge commit, and the
  changes that matter as sub-bullets), **Review** (the `finalize-pr` passes, and what they found or tracked),
  **Checks** (the required checks, and CI on `master` after the merge), **Jira** (where the tickets ended).
  It also lists the tickets this run's reconciliation moved to Done, each with its pull request. "None" when
  nothing merged.
- **Pending CR / Pending User QA:** both queues on one shared line, each with its exact state, or "none from this
  run".
- **Blocked or other**, as bullets.

In both shapes, **Blocked or other** holds: decision tickets and tickets waiting on Matěj, with what each needs;
plan changes proposed to him; follow-up tickets this session created; findings it did not file, with the reason;
GitHub issues from players it came across; conflicts, stale claims and searches it could not complete; the Next
queue when it matters. A last
**Cleanup:** line may say which processes stopped, what was removed, and any uncommitted work left behind.

## How the pipeline fits together

`content/items/test_item.yaml` → `rf gen` →

- `mod/src/generated/java/.../generated/GeneratedItems.java` (registration, creative tab entries), called from
  `item/ItemFeature.register`, which `Realmforge`'s constructor calls;
- `mod/src/generated/resources/assets/realmforge/` models, `lang/en_us.json`, placeholder textures;
- `qa-mod/src/generated/java/.../GeneratedItemExpectations.java`, which `ContentItemGameTests` checks in the game;
- `tools/schemas/items.schema.json` for editor completion.

Generated binaries (PNG, NBT) use stored (uncompressed) deflate blocks so output is byte-identical on every
machine; keep it that way (see `tools/realmforge_tools/formats/`).
