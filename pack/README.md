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

These mods have no release build for 26.1.2 and are betas on purpose (Matěj,
RLM-65, RLM-124 and RLM-308):

- **Regions Unexplored 0.7.0-beta2**. If it crashes or logs errors in the pack
  boot or a playtest, report it to Matěj before choosing anything else; Biomes
  O' Plenty is not wanted as a replacement.
- **FTB Teams 26.1.2.4** (CurseForge), which FTB Quests needs.
- **Advanced Loot Info 26.1.2-2.5.0** and **Advanced Core Info 26.1.2-1.5.0**, its library (Matěj's M1 pick,
  RLM-308, [ADR 0044](../docs/decisions/0044-third-party-mods-by-milestone.md)): every 26.1.2 build is a beta. Both
  are pinned, so `packwiz update --all` leaves them; update them together, by hand.

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

**Default Options** (`config/defaultoptions/keybindings.txt`, Default Options
26.1.2.2, RLM-279, [ADR 0043](../docs/decisions/0043-default-keys-and-key-search-in-the-pack.md)).
The pack's default keys, one `key_<key mapping>:<key>` line each, replacing the
defaults the mods bring. Key names are positions on a US keyboard (`key.keyboard.y`),
which the game shows as the player's layout labels them (Z on a Czech QWERTZ
keyboard). A new profile gets these keys; an existing one keeps its own until the
player presses **Reset Keys** in Key Binds (or deletes `options.txt`), which
returns every key to these defaults. Matěj's picks of 2026-10-19, with the spell
bar's of RLM-25 ([ADR 0046](../docs/decisions/0046-spell-bar-default-keys-amend-the-pack-defaults.md)):

- free for spells (RLM-25): Quick Actions and Swap Offhand; the spell bar (our
  code) takes R, T, F, G, H, C, `ů` and `§` (right of L on a Czech keyboard),
  X for the racial ability and P for the spellbook, so vanilla's Chat moves
  from T to Enter, and its creative toolbar
  keys (C and X) and Social Interactions (P; the pause menu's Player Reporting
  still opens it) go unbound, and Apotheosis's Open World Tier Select moves from
  Ctrl+T to Alt+T, so holding Ctrl to sprint does not steal T;
- Open Quests on `;` (the key left of 1 on a Czech keyboard), and Inventory
  Sorting's Sort there too (RLM-308): it sorts while an inventory is open, Open
  Quests works while none is; Inventory Sorting's Open Settings is unbound (its
  default, P, is the spellbook's). Tom's Simple Storage's Open Terminal on V
  (RLM-25), Just Zoom's Zoom on Z (Czech QWERTZ);
- Xaero's Minimap (2026-10-20): Minimap Settings on `ú` (right of P on a Czech
  keyboard), Zoom In and Zoom Out Minimap on the numeric keypad's + and -, New
  Waypoint on N, Open Waypoint Screen on `,` (right of M); our Dark Vision key
  moved from N to H for it, then to `¨` (by Enter on a Czech keyboard) for the
  spell bar (its default is in our code);
- unbound: Just Zoom's Open Options (it shared B with Open Backpack), Xaero's
  Quick Waypoint, vanilla's Open Command, every Iris key and every
  Sophisticated Backpacks and Sophisticated Core key.

To find a key mapping's name, boot the pack and read the `key_` lines of the
client's `options.txt`; Controlling's search in Key Binds finds a key by name or
by its key.

**Structurify** (`config/structurify.json`, Structurify 2.0.43, RLM-310,
[ADR 0044](../docs/decisions/0044-third-party-mods-by-milestone.md)): vanilla strongholds are off. The file lists only
what differs from the defaults: a structure is switched off by an entry `{"name": "<structure id>", "is_disabled":
true}` under `structures`, and M2 places other mods' structures in their realms the same way (a structure's
`biomes`, its `structure_sets` and their spacing). Structurify writes the file only when it is missing, and its config
screen (Mods, Structurify) shows every option. With no stronghold, no eye of ender finds one and the End stays
unreachable from M1. The GameTest world generates no structures at all, so the pack boot's dedicated server checked
it on 2026-10-23 (RLM-310): `/locate structure minecraft:stronghold` answers that Structurify's config disables the
structure, and `/locate structure minecraft:village_plains` finds one.

**Traveler's Titles** (`config/travelerstitles-neoforge-26_1.toml`, client, Traveler's Titles 6.1.0, RLM-308):
`"Blacklisted Dimensions" = "[realmforge:stone, realmforge:instances]"`. A realm shows its own arrival title (W2b), so
Traveler's Titles shows none there, and the dungeon instances, which have no name of their own, show no "???".
Overworld keeps its title, which is the mod's own. Biome titles need open sky, which neither dimension has. A new
realm's dimension joins the list: `tools/tests/test_pack_config.py` fails until it does.

**Pick Up Notifier** (`config/pickupnotifier-client.toml`, client, Pick Up Notifier 26.1.1, RLM-308; Matěj's
playtest, 2026-10-23): the list of pickups starts at the middle of the screen's right edge and grows downwards, 150 %
of the mod's size. `display.screen_corner = "TOP_RIGHT"` and `display.display_scale = 6` (the mod divides it by 6;
its default, 4, is two thirds). The mod measures a top corner's offset from the top edge in fixed GUI pixels, so the
core mod sets `offset_y` to half the GUI height every client tick while the corner is `TOP_RIGHT`
(`client/compat/pickupnotifier/PickUpListPosition`,
[architecture 19.4](../docs/architecture.md#194-optional-integrations)), and the list starts mid-screen at every
window size and GUI scale. The mod's `display.max_height = 0.5` keeps it above the bottom edge.
`tools/tests/test_pack_config.py` checks the three values.

**Sword Blocking Mechanics** (Sword Blocking Mechanics 26.1.0, RLM-297): holding right click raises a two-handed
weapon, with the mod's poses and animations; our core mod decides what a block does (the 0.5 s full block, half of a
later hit, the 1 s cooldown, the covered arc: `config/realmforge-common.toml`, [architecture
9.3](../docs/architecture.md#93-blocking-with-two-handed-weapons)). So the mod's own rules are off:

- `config/swordblockingmechanics-server.toml`: `parrying.parry_window = 0` (no parry of its own) and
  `blocking.blocked_damage = 0.0` (no damage reduction of its own; its formula, half of the hit plus 1, rounded down,
  is not the half Matěj asked for). Its 20 % knockback reduction while blocking stays.
- `config/swordblockingmechanics-client.toml`: `render_parry_indicator = false`, since it would show its own parry
  window, which is off.

## Notes on single mods

- **Better Combat swings through grass**: with a Better Combat weapon in hand and a target in reach, a swing goes
  through blocks without collision that break at once (grass, flowers); its client options `isSwingThruGrassEnabled`
  and `isSwingThruGrassSmart` are on by default. So neither CleanCut nor Cut Through is in the pack (RLM-308).
- **Iron's RPG Tweaks is not in the pack**: its combat module drops a hit of a damage type that hurt the same entity in
  the tick before, a second player's melee hit or one of our spell schools' included. Our damage pipeline lets a
  player's hits through the hurt cooldown instead ([ADR 0049](../docs/decisions/0049-player-hits-bypass-the-hurt-cooldown-in-our-pipeline.md)).
- **Macaw's building mods need Nether and End materials only for their Nether and End variants** (RLM-309,
  [ADR 0025](../docs/decisions/0025-remove-the-nether-and-end.md)). Every piece comes in the Overworld's woods and
  stones; these variants need the matching material:
  - crimson and warped wood (every Macaw's mod), nether bricks and red nether bricks (Bridges, Fences and Walls, Roofs,
    Stairs, Windows), blackstone and polished blackstone (Bridges, Fences and Walls, Paths and Pavings, Roofs, Stairs,
    Windows), netherrack and quartz blocks (Fences and Walls, Stairs, Windows), and Lights and Lamps' soul torches and
    soul lanterns, shroomlight and glowstone. The Nether stays reachable until M4 removes it, and witches drop
    glowstone dust in the Overworld;
  - end stone and end stone bricks (Bridges, Fences and Walls, Windows) have no source from M1 on, because the End is
    unreachable without strongholds ([ADR 0044](../docs/decisions/0044-third-party-mods-by-milestone.md)), until
    [ADR 0034](../docs/decisions/0034-ancient-merchants-and-the-wither-amend-the-nether-and-end-removal.md)'s
    merchants sell them.

## Our mods

Two mods of ours ship with the pack and are not packwiz entries, because packwiz-installer downloads only from
Modrinth and CurseForge ([ADR 0008](../docs/decisions/0008-distribution-github-and-curseforge.md)):

- **Realmforge**, the core mod (`mod/`, both sides);
- **Better Key Binds** (`better-key-binds/`, client only): every key bind gets a secondary key, as in WoW
  ([its README](../better-key-binds/README.md),
  [ADR 0048](../docs/decisions/0048-better-key-binds-our-own-client-mod-independent-of-realmforge.md)). Its
  secondary keys live in `config/better_key_binds-secondary_keys.txt`, which the pack does not ship, so each
  player's own stay.

Prism gets both jars from `./gradlew :mod:installToPrism :better-key-binds:installToPrism`
([dev setup](../docs/dev-setup.md#4-playing-the-pack-on-windows-prism-launcher--packwiz-installer)), the pack boot
test copies them in ([Boot test](#boot-test)), and the dev client loads both.

## Optional mods

The pack has none since Quark left with the 26.1.2 port. An optional mod is
marked `[option]` in its `.pw.toml`. Caveat for the next one: packwiz-installer's
command-line mode (`-g`, typical for dedicated servers) cannot choose options
and installs every optional mod.

## The dev runs load the pack

The dev runs (`./gradlew runClient`, `runServer`, `runGameTestServer`) load every
mod of this folder's `mods/` for their side, at the exact build the pack ships
([ADR 0042](../docs/decisions/0042-the-dev-runs-load-the-whole-pack.md)):
`qa-mod/build.gradle` reads `mods/*.pw.toml` itself, so adding, updating or
removing a mod here changes the dev runs and the GameTests with it, with nothing
to update by hand. A `side = "client"` mod joins the client, `"server"` the dev
server and the GameTest server, `"both"` every run. Modrinth mods come from
Modrinth's Maven by `mod-id` and `version` under `[update.modrinth]`,
CurseForge ones from CurseMaven by `project-id` and `file-id` under
`[update.curseforge]`, and each jar is checked against the `hash` under
`[download]` before a run starts. `uv run pytest`
(`tools/tests/test_dev_run_mods.py`) fails on a `.pw.toml` that reader cannot
take. `./gradlew runClient -PleanClient` starts a client without the pack's
mods, except GeckoLib (which the core mod requires), Player Animation Library
and Yeetus Experimentus. The dev client also loads Better Key Binds
([Our mods](#our-mods)); the servers do not.

The core mod compiles against two kinds of pack mods, both through the pack's
`mods/<slug>.pw.toml` (`mod/build.gradle`), so it always builds against the jar
players run: GeckoLib, a required library (its custom mobs' models and
animations), and the optional integrations (Neat, Jade).

Every dev run (client, server, GameTest server) also gets this folder's
`config/`: the QA mod's `seedPackConfig` task copies it into the run's `config/`
folder before each launch, replacing the files the mods wrote, as
packwiz-installer does in a player's instance.

## Boot test

CI boots the whole pack plus the core mod on a dedicated server (job `pack-boot`) and, with Better Key Binds too, on a
client under Xvfb (job `pack-boot-client`) for every pull request (with and without the optional mods, when the pack
has any). Run the same locally from the repository root after changing anything here, with Java 25:

```sh
./gradlew :mod:jar :better-key-binds:jar && uv run rf pack-boot
```

See [tools/README.md](../tools/README.md#pack-boot) and
[ADR 0018](../docs/decisions/0018-pack-boot-tests.md) for what it checks.

## License

The modpack is All Rights Reserved. See [LICENSE](LICENSE) in this folder.
Each mod keeps its own license and is downloaded from its original source.
