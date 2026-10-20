# 0042. The dev runs load the whole pack

Date: 2026-10-19

Status: Accepted (amends [0020](0020-cast-animations-and-melee-while-casting.md))

## Context

The dev client (`./gradlew runClient`) loaded only our two mods and the few pack mods the QA mod named as Gradle
dependencies, one by one, by Modrinth version id: Player Animation Library, Yeetus Experimentus, Apotheosis with its
modules, Enchantment Descriptions and Prickle. Better Combat and Cloth Config joined only with `-PwithBetterCombat`,
because [0020](0020-cast-animations-and-melee-while-casting.md) decision 4 kept the default runs and CI without
them. Prism has the whole pack but not the QA mod.

So Matěj could not check JEI or Jade during the RLM-271 playtest, and a collision between a pack mod and our code
showed only in Prism, if at all. He asked to playtest everything together (RLM-279, 2026-10-19), so collisions show
up early.

## Decision

1. **Every dev run loads the pack's mods for its side**, read from `pack/mods/*.pw.toml` by `qa-mod/build.gradle`
   at the builds the pack ships, so the two never drift:
   - a `side = "client"` mod joins the client, a `"server"` one the dev server and the GameTest server, a `"both"`
     one every run, as packwiz-installer sorts them for a player and a server;
   - a Modrinth mod comes from Modrinth's Maven by project and version id, a CurseForge-only one (FTB Quests, FTB
     Library, FTB Teams) from CurseMaven by project and file id;
   - before a run starts, each jar is checked against the hash its `.pw.toml` records, so a run never loads another
     file than packwiz-installer gives a player.
2. **The GameTest server loads the pack's server-side mods too**, so CI's GameTests catch a collision between them
   and our code. `rf gametest-log` allows the two harmless errors they log (Jade's shearable blocks, NeoForge's
   dev-only `@OnlyIn` notice about Jade Addons); a pack mod that makes the GameTests flaky is taken out of the
   GameTest run, with its reason, instead of the GameTests being weakened.
3. **Better Combat is in the default runs and in CI.** This amends 0020 decision 4: `-PwithBetterCombat` no longer
   separates "with" from "without", because the default runs now have it.
4. **A lean client stays one flag away:** `./gradlew runClient -PleanClient` loads only our mods, Player Animation
   Library (the QA mod compiles against it) and Yeetus Experimentus, for quick checks and agents' screenshot runs;
   `-PwithBetterCombat` adds Better Combat and Cloth Config to it.
5. **The dev runs hide NeoForge's `@OnlyIn` loading warning** (`neoforge.warnings.onlyin.hide`), which a pack mod
   (Jade Addons) would otherwise show as a screen to click through on every dev launch; the log keeps it.

## Consequences

- A playtest in the dev client plays with the whole pack, render mods (Sodium, Iris, Distant Horizons) included:
  a first launch downloads about 50 jars, and the client takes longer to start. `-PleanClient` is the quick way.
- Adding, updating or removing a pack mod changes the dev runs with it; `tools/tests/test_dev_run_mods.py` fails on
  a `.pw.toml` the reader in `qa-mod/build.gradle` cannot take, and on a mod coordinate written there by hand.
- Our code is tested with Better Combat on every pull request. The check 0020 asks for after a Better Combat update
  (`/qa cast` still blocks swings) needs no flag any more; a check without Better Combat runs with `-PleanClient`.
- The QA mod's `implementation` dependency on Player Animation Library takes its coordinate from the pack too.
- 0020's decision 4 keeps its accepted text; its `-PwithBetterCombat` sentence is read with this ADR.
