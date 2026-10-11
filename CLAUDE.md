# Realmforge: agent guide

This file is the entry point for every session. Sessions start without memory: anything the next session needs
must be written into the repo (this file, `docs/`, git history).

## Project

Realmforge is an RPG mod and modpack for Minecraft 1.21.1 (NeoForge) inspired by World of Warcraft: Wrath of the
Lich King ("WoW" always means WotLK, never retail). It is a hybrid: our own core mod holds all RPG systems; the
modpack adds third-party QoL, performance and library mods.

- **Owner:** Matěj, a Python full-stack (Django) developer with little Java and modding experience. The project is
  vibe-coded: agents do most of the work, Matěj sets direction, reviews and playtests. **Communicate in English.**
  Explain Java/Minecraft specifics briefly when they matter for a decision; don't assume modding knowledge.
- **Vision:** [docs/vision.md](docs/vision.md) (goals, likes, hard no's such as flying bosses or RNG grind).
- **Decisions:** [docs/decisions/](docs/decisions/README.md) (ADRs). Background: [docs/research-notes.md](docs/research-notes.md).
- **Architecture:** [docs/architecture.md](docs/architecture.md): how the core mod is built (feature packages,
  registries, data flow from YAML into the game, player state, stats, networking, client isolation). Read the
  sections for the system you touch before writing code.
- **Roadmap:** [docs/roadmap.md](docs/roadmap.md): milestones, the current milestone's tasks and open owner
  decisions. Work on tasks of the current milestone and tick them off in the same PR.
- **Jira:** the work queue that mirrors the roadmap: space Realmforge, key `RLM`, one Task per roadmap task or
  other piece of work, `Blocks` links for the roadmap's "after" order. `flow1` takes a batch of tickets from the
  active sprint and `flow2` turns Matěj's task description into a ticket; both deliver one PR per batch
  ([ADR 0016](docs/decisions/0016-jira-batch-delivery.md)). Before any Jira intake, ticket creation or
  publication of ticket work, read [docs/ai-development-flow.md](docs/ai-development-flow.md) completely. Agents
  never implement `decision` tickets or tickets waiting on Matěj.
- **Status:** M0 (bootstrap) is done: build, CI, content pipeline, one test item (`realmforge:test_item`), modpack
  definition. Next is M1, the playable vertical slice (proposed as v0.1.0, roadmap decision D7), starting with its
  spikes. No gameplay yet.

## Pinned versions

Changing any of these requires a new ADR ([0003](docs/decisions/0003-platform-minecraft-1-21-1-neoforge.md)).

| Component | Version | Where |
| --- | --- | --- |
| Minecraft | 1.21.1 (data version 3955) | `gradle.properties`, `tools/realmforge_tools/project.py` |
| NeoForge | 21.1.259 | `gradle.properties`, `pack/pack.toml` |
| ModDevGradle | 2.0.148 | `settings.gradle` |
| Gradle | 9.2.1 (checksum-pinned wrapper) | `gradle/wrapper/gradle-wrapper.properties` |
| Java | 21 (Temurin) | toolchain in `*/build.gradle` |
| Parchment | 1.21.1 / 2024.11.17 | `gradle.properties` |
| Python | 3.12+ (3.14 pinned in `.python-version`), managed by uv | `pyproject.toml`, `uv.lock` |
| packwiz | commit `ef87d96` | `.github/workflows/ci.yml` |

## Commands

Java comes from SDKMAN. Non-interactive shells don't read `~/.bashrc`, so run
`source ~/.sdkman/bin/sdkman-init.sh` before Gradle (or `export JAVA_HOME=~/.sdkman/candidates/java/current`).
Cloud sessions have no SDKMAN: Java 21 is preinstalled, and a background SessionStart hook warms up uv and Gradle
([docs/dev-setup.md](docs/dev-setup.md#cloud-sessions)). **In a cloud session, run
`.claude/hooks/cloud-warmup.sh --wait` (Bash timeout 10 minutes) before the first Gradle command, and again after
the session resumes:** two Gradle runs in one project wait on each other's locks and the second can fail with
"Timeout waiting to lock". If it
reports a failure, read `.cache/warmup/log`; Gradle still downloads what it needs on demand.

```bash
uv sync                                   # Python environment (once, and after pyproject/uv.lock changes)
uv run rf validate                        # validate content/ (~1 s)
uv run rf gen                             # validate, then regenerate every generated file
uv run rf gen --check                     # exit 1 if generated files are out of date
uv run pytest                             # Python tests
uv run ruff check && uv run ruff format   # lint + format Python
uv run rf ids vanilla                     # re-extract the vanilla ID universe (only when the MC version changes)

./gradlew build                           # compile both mods + JUnit
./gradlew runGameTestServer               # headless GameTests; exit code != 0 on failure
./gradlew runClient                       # dev client with core + QA mod (game dir: run/client)
./gradlew runServer                       # dev dedicated server (run/server; accept run/server/eula.txt once)
./gradlew :mod:extractMinecraftSources    # decompiled MC + NeoForge sources -> build/mc-sources/
./gradlew :mod:installToPrism             # copy the jar into the Prism instance (see docs/dev-setup.md)
.claude/scripts/gradle-queue.sh <task>    # agents: any Gradle build/test task, queued while memory is short

cd pack && packwiz refresh                # after changing anything in pack/
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
| `qa-mod/` | dev-only mod `realmforge_qa` (GameTests, later debug tools); never released | `src/main` |
| `qa-mod/src/generated/` | content expectations for GameTests, GameTest structures from `rf gen` | **no** |
| `pack/` | packwiz modpack definition (metadata only, no jars) | via `packwiz` commands |
| `art/` | asset sources: prompts, palettes, Blockbench files | yes |
| `docs/` | vision, architecture, roadmap, research notes, ADRs, dev setup | yes |
| `.github/workflows/ci.yml` | CI | yes |
| `.claude/` | Claude Code settings, content validation hook, cloud warm-up hook, Gradle queue script, flow skills (`flow1`, `flow2`), PR skills (`fix-pr`, `finalize-pr`, `monitor-pr`), `pr-reviewer` agent | yes |
| `.mcp.json` | MCP servers for Claude Code: `atlassian` (Jira) | yes |
| `.local/` | owner's private inputs; gitignored, **never commit** | - |
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
4. **English everywhere in the repo**: code, comments, docs, commits, PRs, issues. Player-facing text is
   `LocalizedText`: `en_us` required, `cs_cz` optional.
5. **The in-game world is called Realmforge.** Use this name whenever lore, quests, item or book texts, or
   dialogs refer to the game world.
6. **Never invent APIs.** Training data mixes Minecraft versions and loaders (Forge 1.20, Fabric, 1.21.2+).
   Before using a Minecraft or NeoForge class or method, find it in `build/mc-sources/` (run
   `./gradlew :mod:extractMinecraftSources` once per checkout) and read how vanilla uses it. FML classes
   (`@Mod`, `@EventBusSubscriber`, `ModContainer`) are not in there; they live in
   `net.neoforged.fancymodloader:loader` (inspect the jar in `~/.gradle/caches/modules-2/` with `javap`).
   Docs: https://docs.neoforged.net/docs/1.21.1/ (make sure the URL says 1.21.1).
7. **Dev-only code goes into `qa-mod/`**, never into the core mod. GameTests live there too.
8. **No third-party jars in the repo.** The pack is packwiz metadata only. Never copy third-party code unless its
   license allows it (Spell Engine is GPL-3.0-only, Explorer's Compass CC BY-NC-SA: no reuse). No vanilla
   textures or recolors, not even as AI input.
9. **Licenses:** the mod (`mod/`, `qa-mod/`, `tools/`, everything in the jar) is MPL-2.0 for code and CC BY 4.0
   for assets; the modpack (`pack/`) is All Rights Reserved ([ADR 0009](docs/decisions/0009-licensing.md)).
10. **Decisions get an ADR** in `docs/decisions/` (next free number, add it to the index).
11. **Code follows [docs/architecture.md](docs/architecture.md):** feature packages with a `<Name>Feature` entry,
    one-way dependencies, client-only code only under `client/`, game definitions as datapack registries. To
    deviate, update the document in the same PR; changing a decision recorded in
    [ADR 0014](docs/decisions/0014-core-mod-architecture.md) needs a new ADR.

## Minecraft 1.21.1 / NeoForge 21.1 pitfalls

- **Data components, not NBT, on item stacks:** `stack.get(DataComponents.X)` / `stack.set(...)`.
  `ItemStack#getTag()` no longer exists. Register custom components with
  `DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, MOD_ID)`.
- **`ResourceLocation`:** constructors are private. Use `ResourceLocation.fromNamespaceAndPath(ns, path)`,
  `ResourceLocation.parse("ns:path")`, or our `Realmforge.id("path")`.
- **Networking is payload-based:** a `record` implementing `CustomPacketPayload` with a `Type` and a
  `StreamCodec`, registered in `RegisterPayloadHandlersEvent` via `PayloadRegistrar#playToClient/playToServer`.
  There is no `SimpleChannel`. 21.1 has no client-only payload registration event: common code registers every
  payload, and client-bound handlers go through an interface implemented in `client/`
  ([architecture 17.1](docs/architecture.md#171-rules)).
- **Data attachments** (`AttachmentType`, registry `NeoForgeRegistries.Keys.ATTACHMENT_TYPES`) store per-entity
  or per-level data such as player stats; use them instead of capabilities or raw NBT. NeoForge 21.1.259 can sync
  them: declared with `.sync(...)`, they are sent on every `setData`; after mutating a value in place, call
  `syncData`. The `sync(predicate, ...)` predicate filters updates only: a player who starts tracking an entity
  receives every synced attachment on it ([architecture 7](docs/architecture.md#7-player-state)).
- **Our datapack registries** load from `data/<ns>/realmforge/<registry>/` (NeoForge puts non-vanilla registry
  folders under the registry's namespace), e.g. `data/realmforge/realmforge/spell/firebolt.json`. The `<ns>` folder
  becomes the entry's namespace, so an entry about `minecraft:overworld` names it in a field, not in its id.
- **Vanilla attribute ids keep their prefixes** in 1.21.1 (`minecraft:generic.max_health`,
  `minecraft:player.block_interaction_range`); newer versions dropped them, so don't copy their examples.
- **Hurt cooldown:** within 10 ticks of the last hit, a hit not bigger than it is dropped before
  `LivingDamageEvent` fires; a hit outside that window, or one that bypasses it, starts new invulnerability ticks.
  Damage that must always land (spells, damage over time) uses damage types in `#minecraft:bypasses_cooldown` and
  keeps the target's current window (`setInvulnerabilityTicks(target.invulnerableTime)`), so it neither grants nor
  clears invulnerability ([architecture 9.1](docs/architecture.md#91-damage-pipeline)).
- **Never cancel `LivingIncomingDamageEvent`:** in 21.1.259 `LivingEntity#hurt` then returns without popping the
  `DamageContainer` it pushed, so every cancel leaks one on the defender. Dodge and other "stop this hit" code use
  another mechanism ([architecture 9.1](docs/architecture.md#91-damage-pipeline)).
- **Data pack folders are singular since 1.21:** `recipe/`, `loot_table/`, `advancement/`, `structure/`,
  `tags/item/`, `tags/block/`. Enchantments are data-driven (`enchantment/`) and are looked up as
  `Holder<Enchantment>` through registry access, not static fields.
- **Not in 1.21.1** (newer versions only, don't use): `Item.Properties#setId`, item model definitions in
  `assets/<ns>/items/`, `Registry#getValue`. 1.21.1 item models live in `assets/<ns>/models/item/`.
- **Registration:** `DeferredRegister.Items`/`Blocks`/`create(...)`, holders `DeferredItem`/`DeferredHolder`;
  register the `DeferredRegister` on the mod event bus. Game events go to `NeoForge.EVENT_BUS`.
- **Client-only code** (rendering, screens, `Minecraft.getInstance()`) must sit in classes only loaded on the
  client (e.g. `@Mod(value = MOD_ID, dist = Dist.CLIENT)`), or the dedicated server crashes.
- **GameTests:** `@GameTestHolder(RealmforgeQa.MOD_ID)` + `@PrefixGameTestTemplate(false)` +
  `@GameTest(template = "platform_5x5")`. Templates are generated into `qa-mod/src/generated/resources/data/
  realmforge_qa/structure/` (see `tools/realmforge_tools/generate/gametest.py`). Positions are relative to the
  structure block: the template's floor is at y = 1.

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

**Pull requests**

- Open them ready for review (never drafts) against `master` with `gh pr create`. The description says what
  changed, why, and which checks passed. Never write `@codex` on a pull request (description, comments,
  replies): any mention starts a Codex review or a Cloud task that can push to the branch.
- Add the `needs-playtest` label when Matěj should play the change before it merges (gameplay, visuals,
  balance), and the `needs-approval` label when the PR changes the plan (`docs/roadmap.md` beyond ticking off
  finished tasks). Only Matěj removes either label.
- A PR from a Jira batch names its tickets in the title, e.g. `feat(spells): add the spell engine core (RLM-24)`.
- After publishing or updating a ready pull request as your own task work, run `/monitor-pr <n>` on it straight
  after, in the same run. Codex findings alone: `/fix-pr <n>`. Extra independent review: `/finalize-pr <n>`.
  The skills live in `.claude/skills/`.
- Never merge with `--admin`, and never bypass the ruleset.
- Renaming a CI job (its `name:` in `ci.yml`) breaks the merge gate: the `master` ruleset requires checks by job
  name, so every pull request would wait forever for the old name. Say so in the pull request description and
  ask Matěj to update the required checks before it merges.

**Ending a session**

- Stop every process you started (`runClient`, `runServer`, `runGameTestServer`, `packwiz serve`), remove your
  temporary files, and name any uncommitted work you leave behind in your final message.

## Workflow

1. Take a task from the current milestone in [docs/roadmap.md](docs/roadmap.md), or run `flow1`/`flow2` for
   Jira tickets ([docs/ai-development-flow.md](docs/ai-development-flow.md)); one task, feature, content batch or
   flow batch of tickets per pull request (branches: [Git and branches](#git-and-branches)). Commit messages
   follow Conventional Commits (`feat:`, `fix:`, `docs:`, `build:`, `ci:`, `test:`, `chore:`).
2. A Claude Code hook (`.claude/settings.json`) runs `uv run rf validate` after every edit in `content/` or
   `tools/` and reports problems back. It does not compile Java. In cloud sessions SessionStart hooks route PyPI
   through the agent proxy and warm up uv and Gradle in the background (`.claude/hooks/cloud-warmup.sh`).
3. **Before opening a PR, run and report:** `uv run rf gen` (no unexpected changes), `uv run ruff check`,
   `uv run ruff format --check`, `uv run pytest`, `./gradlew build`, `./gradlew runGameTestServer`. Agents run
   the two Gradle tasks through `.claude/scripts/gradle-queue.sh`, which waits for memory while other worktrees
   build.
4. Keep `CLAUDE.md`, `docs/` (including the roadmap's task list) and the relevant READMEs current in the same PR
   when commands, structure, rules or decisions change. Stale docs mislead the next session.
5. Open the PR against `master` with `gh pr create`, then run `/monitor-pr <n>`. CI (`.github/workflows/ci.yml`)
   runs: content pipeline + generated-files check, Gradle build + JUnit + GameTests, vanilla ID universe
   reproducibility, packwiz index. These four jobs are the required checks on `master`.
6. `monitor-pr` merges once Codex is done (its approval, a completed review whose findings are all closed, or
   10 silent minutes), every review thread is resolved and the required checks are green, unless the PR has the
   `needs-playtest` label (Matěj playtests first and removes the label) or the `needs-approval` label (Matěj
   approves the plan change) ([ADR 0015](docs/decisions/0015-merging-through-monitor-pr.md),
   [ADR 0016](docs/decisions/0016-jira-batch-delivery.md)). Distribution: GitHub Releases now, CurseForge later
   ([ADR 0008](docs/decisions/0008-distribution-github-and-curseforge.md)).

## How the pipeline fits together

`content/items/test_item.yaml` → `rf gen` →

- `mod/src/generated/java/.../generated/GeneratedItems.java` (registration, creative tab entries), called from
  `Realmforge`'s constructor;
- `mod/src/generated/resources/assets/realmforge/` models, `lang/en_us.json` + `cs_cz.json`, placeholder textures;
- `qa-mod/src/generated/java/.../GeneratedItemExpectations.java`, which `ContentItemGameTests` checks in the game;
- `tools/schemas/items.schema.json` for editor completion.

Generated binaries (PNG, NBT) use stored (uncompressed) deflate blocks so output is byte-identical on every
machine; keep it that way (see `tools/realmforge_tools/formats/`).
