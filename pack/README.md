# Realmforge modpack (packwiz)

This directory is the [packwiz](https://packwiz.infra.link/) definition of the
Realmforge modpack: Minecraft 1.21.1, NeoForge 21.1.259.

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

Every mod must have a NeoForge 1.21.1 build. After adding a mod, check the
`side` field in its `.pw.toml` (`client`, `server` or `both`) instead of relying
on the default packwiz picks.

## Pinned mods

These are pinned (`pin = true`), so `packwiz update --all` skips them:

- **Sodium 0.6.13** and **Iris 1.8.12**. Iris 1.8.12 is the current stable Iris,
  and it only works with Sodium 0.6.x (its Modrinth build depends on exactly
  Sodium 0.6.13). Sodium 0.8.x needs Iris 1.8.14-beta.1 or newer.
- **Distant Horizons 3.3.3**. Tested against the Iris 1.8.12 API, and pinned so
  the three rendering mods only change together.

To move to a newer pair, update Sodium and Iris together (`packwiz unpin`,
`packwiz update`, `packwiz pin`). Then check Distant Horizons again.

## Optional mods

**Quark** is optional and disabled by default (`[option]` in
`mods/quark.pw.toml`). It adds content, so if a server enables it, every
client must enable it too. Its library Zeta is always installed, which is
harmless.

Caveat: packwiz-installer's command-line mode (`-g`, typical for dedicated
servers) cannot choose options and installs every optional mod, Quark included
("Accepting option Quark as option choosing is not implemented in the CLI").
On a server without Quark, delete its jar after installing.

## Boot test

CI boots the whole pack plus the core mod on a dedicated server (job `pack-boot`) and on a client under Xvfb (job
`pack-boot-client`) for every pull request, each with and without Quark. Run the same locally from the repository
root after changing anything here:

```sh
./gradlew :mod:jar && uv run rf pack-boot
```

See [tools/README.md](../tools/README.md#pack-boot) and
[ADR 0018](../docs/decisions/0018-pack-boot-tests.md) for what it checks.

## License

The modpack is All Rights Reserved. See [LICENSE](LICENSE) in this folder.
Each mod keeps its own license and is downloaded from its original source.
