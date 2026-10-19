# Realmforge modpack (packwiz)

This directory is the [packwiz](https://packwiz.infra.link/) definition of the
Realmforge modpack: Minecraft 26.1.2, NeoForge 26.1.2.116
([ADR 0026](../docs/decisions/0026-platform-minecraft-26-1-2-neoforge.md)).

It holds metadata and configuration only: `pack.toml`, `index.toml`,
`mods/*.pw.toml` and the config overrides in `config/` ([Configuration](#configuration)).
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

## Configuration

`config/` holds the pack's config overrides. packwiz-installer copies them into
the instance's `config/` folder on every launch, on clients and servers alike,
replacing the file a mod wrote itself. Each one is the file exactly as the game
writes it, with only the values below changed, so NeoForge never rewrites it.
To change one, boot the pack (`uv run rf pack-boot --side server`), copy the
mod's file from `.cache/pack-boot/server/config/`, change the values, run
`packwiz refresh`, and update this list.

**Waystones** (`config/waystones-common.toml`, Waystones 26.1.2.16, RLM-132).
Travel between realms goes through waystones, so warps stay cheap (research
TR-1: CTE2 sets the XP cost to 0):

- `rules.enableXpCosts = false`: no warp costs XP. The defaults charge 0.01 XP
  points per block up to 27, and a flat 27 for a warp to another dimension
  (`warpSettings` and `warpRequirements`), which would tax every trip between
  realms.
- `rules.allowEveryoneToManageGlobalWaystones = true`: every player may make
  their own waystone global (listed for everyone) or keep it private. By
  default only a player in creative mode may. Global or private is each
  player's choice (Matěj, RLM-128, 2026-10-13), and only a waystone's owner may
  edit it. `defaultVisibility` stays `ACTIVATION`: a new waystone is private
  until its owner makes it global.

Everything else keeps the defaults. Warp stones lose durability with each warp,
and warp scrolls are used up. The only cooldown belongs to the inventory
button, which is off (`inventoryButton = ""`). No rule refuses a warp to another
dimension. Leashed mobs travel along (`transportLeashed`), pets do not
(`transportPets`). The config allows warps into every dimension: refusing a warp
into a realm the player has not unlocked is the core mod's job (realm portal
spec, W2a).

**Apothic Attributes** (`config/apotheosis/apothic_attributes.cfg`, Apothic
Attributes 3.1.0, RLM-271, [ADR 0038](../docs/decisions/0038-apotheosis-carries-gear-rarity-affixes-gems-and-enchanting.md)).
Apotheosis's attribute library is fitted to our combat, whose formulas and stats
stay the only ones:

- `combat_rules."Protection Formula" = 1 - min(protPoints, 20) / 25`: vanilla's
  protection, 4 % a point up to 80 %, instead of 2.5 % a point up to 85 %.
- `combat_rules."Armor Formula"`: vanilla's armor formula, as the config's own
  note gives it, instead of `a / (a + armor)`. Our damage pipeline computes armor
  itself, so this only keeps other callers of vanilla's `CombatRules` in step
  ([ADR 0033](../docs/decisions/0033-vanilla-armor-formula-amends-our-own.md)).
- `general."Enable Attributes GUI" = false`: no attributes panel of its own;
  our character sheet shows the stats.

Its crit and dodge are switched off in code, not here
([architecture 19.4](../docs/architecture.md#194-optional-integrations)).

**Apothic Enchanting** (`config/apotheosis/apothic_enchanting.cfg`, Apothic
Enchanting 2.1.0, RLM-271): `client."Enable Inlined Enchantment Descriptions" =
true`. Apothic Enchanting writes the enchantment lines of a tooltip itself and
cancels vanilla's, which Enchantment Descriptions hooks into, so without this
no enchantment shows its description. The descriptions are Enchantment
Descriptions' texts (the dev runs load it too), shown in dark grey under each enchantment on every item;
Enchantment Descriptions' own options (books only, hold Shift) no longer apply.

## Optional mods

The pack has none since Quark left with the 26.1.2 port. An optional mod is
marked `[option]` in its `.pw.toml`. Caveat for the next one: packwiz-installer's
command-line mode (`-g`, typical for dedicated servers) cannot choose options
and installs every optional mod.

## Mods the QA mod uses too

The dev-only QA mod gets **Player Animation Library**, **Better Combat**,
**Cloth Config**, **Yeetus Experimentus**, **Placebo**, **Apothic
Attributes**, **Apothic Enchanting**, **Apotheosis**, **Enchantment
Descriptions** and **Prickle** from Modrinth's Maven,
so the dev runs (`./gradlew runClient`, with `-PwithBetterCombat` for Better
Combat) and the GameTests test our code against the jars players get.
`qa-mod/build.gradle` names them by Modrinth version id, the `version` under
`[update.modrinth]` in their `mods/<slug>.pw.toml`. Updating one of these mods
means updating `qa-mod/build.gradle` too: `uv run pytest`
(`tools/tests/test_qa_mod_dependencies.py`) fails while they differ.

Every dev run (client, server, GameTest server) also gets this folder's
`config/`: the QA mod's `seedPackConfig` task copies it into the run's `config/`
folder before each launch, replacing the files the mods wrote, as
packwiz-installer does in a player's instance.

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
