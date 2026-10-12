# Realmforge modpack (packwiz)

This directory is the [packwiz](https://packwiz.infra.link/) definition of the
Realmforge modpack: Minecraft 26.1.2, NeoForge 26.1.2.116
([ADR 0026](../docs/decisions/0026-platform-minecraft-26-1-2-neoforge.md)).

It holds metadata only: `pack.toml`, `index.toml` and `mods/*.pw.toml`.
No mod jars are stored here, and none should ever be committed. Players get the
jars from Modrinth/CurseForge through packwiz-installer.

Files listed in `.packwizignore` (this README, `LICENSE`, ...) are left out of
the index, so they never end up in a player's instance.

## Common tasks

Run these from inside this directory:

```sh
packwiz refresh                  # rebuild index.toml after editing any file by hand
packwiz mr add <slug>            # add a mod from Modrinth (preferred)
packwiz cf add <slug>            # add a mod from CurseForge
packwiz update --all             # update everything that is not pinned
packwiz list                     # list the mods in the pack
```

Every mod must have a NeoForge 26.1.2 build. After adding a mod, check the
`side` field in its `.pw.toml` (`client`, `server` or `both`) instead of relying
on the default packwiz picks, and check the dependencies its jar declares
(`META-INF/neoforge.mods.toml`, embedded jars included): packwiz misses some
([ADR 0013](../docs/decisions/0013-modpack-composition.md)).

## Pinned mods

These are pinned (`pin = true`), so `packwiz update --all` skips them:

- **Sodium 0.9.2**, **Iris 1.11.4** and **Distant Horizons 3.3.3**, the render
  stack. All three are releases on 26.1.2; they change only together and only
  after a test with a Distant Horizons-capable shaderpack.

To move the render stack, update all three (`packwiz unpin`, `packwiz update`,
`packwiz pin`) and run that shaderpack test.

### Release builds

packwiz picks a project's newest file whatever its release channel, and some
mods publish beta builds after their latest release. These are pinned to their
newest release build, so `packwiz update --all` cannot move them to a beta:

- **JEI 29.34.0.90**: the later 26.1.2 builds are betas.
- **Jade 26.1.10**: 26.1.11 is a beta.

**Yeetus Experimentus 99.0.0** is pinned too: Modrinth tags it 26.1 only (its jar
accepts Minecraft 26.1 to 26.1.x), so `packwiz update` finds no 26.1.2 build.

To update one, find its newer release build (Modrinth: version type "release";
CurseForge: file type "Release"), then `packwiz unpin <mod>`, add exactly that
file (`packwiz mr add --project-id <id> --version-id <id>` or
`packwiz cf add --addon-id <id> --file-id <id>`), `packwiz pin <mod>`, and
boot the pack ([Boot test](#boot-test)).

### Release-channel exceptions

Two mods have no release build for 26.1.2 and are betas on purpose (Matěj,
RLM-65 and RLM-124):

- **Regions Unexplored 0.7.0-beta2**. If it crashes or logs errors in the pack
  boot or a playtest, report it to Matěj before choosing anything else; Biomes
  O' Plenty is not wanted as a replacement.
- **FTB Teams 26.1.2.4** (CurseForge), which FTB Quests needs.

## Optional mods

The pack has none since Quark left with the 26.1.2 port. An optional mod is
marked `[option]` in its `.pw.toml`. Caveat for the next one: packwiz-installer's
command-line mode (`-g`, typical for dedicated servers) cannot choose options
and installs every optional mod.

## Mods the QA mod uses too

The dev-only QA mod gets **Player Animation Library**, **Better Combat**,
**Cloth Config** and **Yeetus Experimentus** from Modrinth's Maven, so the dev
runs (`./gradlew runClient`, with `-PwithBetterCombat` for Better Combat) test
our code against the jars players get. `qa-mod/build.gradle` names them by
Modrinth version id, the `version` under `[update.modrinth]` in
`mods/player-animation-library.pw.toml`, `better-combat.pw.toml`,
`cloth-config.pw.toml` and `yeetus-experimentus.pw.toml`. Updating one of these
mods means updating `qa-mod/build.gradle` too: `uv run pytest`
(`tools/tests/test_qa_mod_dependencies.py`) fails while they differ.

## Boot test

CI boots the whole pack plus the core mod on a dedicated server (job `pack-boot`) and on a client under Xvfb (job
`pack-boot-client`) for every pull request (with and without the optional mods, when the pack has any). Run the
same locally from the repository root after changing anything here, with Java 25:

```sh
./gradlew :mod:jar && uv run rf pack-boot
```

See [tools/README.md](../tools/README.md#pack-boot) and
[ADR 0018](../docs/decisions/0018-pack-boot-tests.md) for what it checks.

## License

The modpack is All Rights Reserved. See [LICENSE](LICENSE) in this folder.
Each mod keeps its own license and is downloaded from its original source.
