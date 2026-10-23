# 0048. Better Key Binds: our own client mod, independent of Realmforge

Date: 2026-10-22

Status: Accepted (amends [0008](0008-distribution-github-and-curseforge.md),
[0011](0011-repository-layout-and-workflow.md) and [0013](0013-modpack-composition.md))

## Context

Matěj wants secondary key binds as in World of Warcraft: every key bind has a primary and a secondary key, and either
one triggers it (RLM-372, 2026-10-22). His first use: Open Chat on Enter and T. No mod does this on NeoForge 26.1.2;
the checks for the preset feature (RLM-356) found none either, and the closest ones are Fabric-only.

It is a mod any player could use without Realmforge, so it should not depend on Realmforge's code, and Realmforge
should not depend on it. Matěj dropped a separate `minecraft-mods` repository for such mods (RLM-356, 2026-10-22):
it lives in this repository, next to the core mod. Its second feature, exporting and importing key bind presets,
follows in RLM-356.

## Decision

1. **A subproject of its own:** `better-key-binds/`, next to `mod/` and `qa-mod/`, included in the root build. Mod id
   `better_key_binds` (`betterkeybinds` is taken on Modrinth by an unrelated Fabric mod), package
   `io.github.matejkadlec.betterkeybinds`, its own `neoforge.mods.toml`, its own version (in
   `better-key-binds/gradle.properties`, from 0.1.0, written `<version>+mc<minecraft version>` like the core mod's)
   and its own jar, `better-key-binds-<version>+mc26.1.2.jar`. Client-only: `@Mod(dist = Dist.CLIENT)`, client
   mixins, and servers don't need it.
2. **Independent of Realmforge, both ways:** it imports nothing from `mod/` or `qa-mod/`, and they import nothing
   from it; its build reads none of Realmforge's folders (`content/`, `pack/`). The only links are runtime ones: the
   dev client loads it (decision 6), and the pack ships its jar (decision 5). It follows `CLAUDE.md`'s general rules
   (English, the pinned platform, APIs checked in the sources, no third-party code), but not the core mod's
   architecture ([architecture.md](../architecture.md): feature packages, the content pipeline), which is
   Realmforge's.
3. **License:** MPL-2.0, like the core mod's code, with its own `LICENSE` in `better-key-binds/` (in the jar as
   `LICENSE_better_key_binds`).
4. **Release tags are prefixed per mod:** a release of Better Key Binds is tagged `better-key-binds-v<version>`, for
   example `better-key-binds-v0.1.0`, and carries its jar. Realmforge's release (the core mod and the modpack) keeps
   0008's `v*` tags, which the roadmap's R2 builds from; a `v*` pattern never matches a `better-key-binds-v` tag.
   Distribution follows 0008: GitHub Releases, CurseForge later, never Modrinth.
5. **In the pack the way the core mod is:** our own jars are not packwiz entries (packwiz-installer downloads only
   from Modrinth and CurseForge, where neither is published), so it is installed next to the core mod: Prism gets it
   through `./gradlew :better-key-binds:installToPrism`, the client pack boot (`rf pack-boot --side client`, CI's
   `pack-boot-client`) copies its jar in and fails when it is not loaded, and the release (R2) attaches its jar
   next to the core mod's. The server boot leaves it out.
6. **The dev client loads it:** `qa-mod/build.gradle` lists it as a mod of the client run and puts its classes on that
   run's classpath only, as RLM-279 does for the pack's mods ([0042](0042-the-dev-runs-load-the-whole-pack.md)); the
   dev server and the GameTest server load only Realmforge's two mods.
7. **How it works** (its [README](../../better-key-binds/README.md) has the details):
   - every key bind in `Options.keyMappings` gets a secondary key, a hidden key mapping in vanilla's key lookup with
     its own key and NeoForge `KeyModifier` and its primary's conflict context, so key presses, mouse buttons,
     modifiers and conflict contexts reach it as they reach a primary key; mixins on `KeyMapping` and
     `ToggleKeyMapping` make the key bind held while either key is held and count the clicks of both;
   - in game, a key's primary key bind wins over another key bind's secondary key on the same key; a key bind never
     holds the same key twice, so a secondary key given its primary's key unbinds the primary (Matěj's playtest,
     2026-10-23);
   - the secondary keys live in `config/better_key_binds-secondary_keys.txt`, not `options.txt`, whose early startup
     save drops the lines of key binds it does not know yet; they are loaded once every mod has registered its key
     mappings;
   - the Key Binds screen gets a second key column right of the primary one, through mixins on the rows of vanilla's
     list and of Controlling's, which the pack uses ([0043](0043-default-keys-and-key-search-in-the-pack.md)): the
     screen is never replaced. Reset, "Reset Keys", the conflict markers, Controlling's "Show Conflicts", its search
     by key and its "Toggle Free" include the secondary keys, and three faults of Controlling's screen are fixed along
     the way (its search box focused on opening and dead after Esc, its category headings reaching into the next
     row). The Controlling mixins sit in a config that is not required, so a Controlling update that changes the code
     they hook into costs its screen the column instead of crashing the game.

## Consequences

- `CLAUDE.md` names the subproject and the independence rule; reviews refuse an import across the boundary.
- A Controlling update in the pack needs the `compileOnly` versions in `better-key-binds/build.gradle` moved and the
  column checked in game: the mixins hook into Controlling's own classes, which have no API for a second key column.
- The flow's conflict domains count `better-key-binds/` in the game domain, like the core mod: its jar ends up in the
  pack.
- RLM-356 adds the preset export and import to this mod, in the same file format and with a version bump and a
  `better-key-binds-v…` tag.
