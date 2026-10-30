# tools/

Python content pipeline (`realmforge_tools`, CLI `rf`). Requires Python 3.12+ and [uv](https://docs.astral.sh/uv/);
the project itself is defined in the root `pyproject.toml`.

```bash
uv run rf validate        # validate content/ (schema, ids, references)
uv run rf gen             # validate, then regenerate every generated file
uv run rf gen --check     # exit 1 if any generated file is out of date
uv run rf ids vanilla     # re-extract the vanilla ID universe (downloads the server jar, needs Java 25)
uv run rf ids apotheosis  # re-snapshot Apotheosis's data from the jar pack/mods/apotheosis.pw.toml pins
uv run rf pack-boot       # boot the whole pack plus mod/build/libs' jar on a server and a client (needs Java 25)
uv run rf gametest-log    # after ./gradlew runGameTestServer: exit 1 on data loading errors in its log
uv run rf art clean SRC DST   # snap a generated texture to the 16x16 grid and the project palette
uv run rf art gallery DIR     # review gallery for DIR/<subject>/<candidate>.png (.cache/art/)
uv run rf rooms import SRC... --to DIR [--rules FILE]  # .nbt/.schem/.litematic -> rooms with draft sidecars
uv run rf rooms gallery [DIR]  # render the rooms below DIR (default content/rooms) -> .cache/rooms/gallery/
uv run rf rooms review DIR     # merge decisions pasted from the gallery (stdin or --from) into DIR/review.yaml
uv run rf set-pieces report [MASK...] [--game]  # spots of the jig's masks in every room (the design's mask report)
uv run rf set-pieces import FILE --id ID        # a workshop jig save -> content/set_pieces/ID (or --from-world WORLD)
uv run pytest             # tests
uv run ruff check && uv run ruff format --check
```

| Path | What |
| --- | --- |
| `realmforge_tools/schema/` | pydantic models, one per content type (`CONTENT_TYPES` maps `content/<dir>` to a model) |
| `realmforge_tools/content.py` | loads `content/` (step 1: schema) |
| `realmforge_tools/validate.py` | own ids and references against the ID universe (steps 2-3) |
| `realmforge_tools/id_universe.py`, `vanilla.py` | the ID universe (vanilla's ids, NeoForge's attributes, ours) and the vanilla extractor |
| `realmforge_tools/apotheosis.py` | Apotheosis's data ([ADR 0063](../docs/decisions/0063-apotheosis-data-is-fitted-from-a-pinned-snapshot.md)): `rf ids apotheosis` writes its snapshot, and `fit` applies `content/`'s entry changes and stat remaps to it, for `validate.py` and `generate/apotheosis.py` |
| `realmforge_tools/generate/` | generators; `generate_all` + `sync` own the generated directories; `datapack.py` writes every type in `DATAPACK_TYPES` as datapack registry entries (translation keys, unit conversion); `spell_schools.py` writes the spell schools with their damage types, damage type tag entries and placeholder icons; `spells.py` writes the spells' `GeneratedSpells` keys, the placeholder textures of their projectiles' appearances and QA expectations (their data comes from `datapack.py`); `effects.py` writes the status effects' `GeneratedEffects` registrations, placeholder icons and QA expectations; `mobs.py` writes the custom mobs' `GeneratedMobs` registrations, loot tables (one per variant too), entity type tags, placeholder GeckoLib models, animations and textures (one per variant), where each mob's raised hands are (a forming projectile grows there), and QA expectations (a boss's body, archetype `boss`, made through `mob/custom/Archetypes` and in `c:bosses`); `bosses.py` writes the bosses' QA expectations (their data and phase names come from `datapack.py`); `sounds.py` writes our sound events' `sounds.json`, `GeneratedSounds` registrations and subtitles; `texts.py` writes the `GeneratedTexts` key constants and `lang.py` every locale's lang file; `apotheosis.py` writes every Apotheosis affix, gem, tier augment and rarity that `content/` changes to Apotheosis's own paths, and every removed recipe as a file NeoForge skips; `fonts.py` writes our symbols font (`realmforge:symbols`, the mob level's skull) and its texture from the pixel rows it holds; `gui_icons.py` writes our GUI icons (`realmforge:icon/<name>` sprites, the spellbook's gear) from the pixel rows it holds |
| `realmforge_tools/formats/` | deterministic PNG, NBT, gzip/zlib and JSON writers; a PNG reader for generator output; an NBT reader (every tag type, the arrays included) |
| `realmforge_tools/art/` | texture pipeline ([ADR 0023](../docs/decisions/0023-texture-generator.md)): palette, `rf art clean`, `rf art gallery` |
| `realmforge_tools/rooms/` | dungeon rooms ([below](#rooms)): structure templates, the source readers, `rf rooms import`, the draft slot tags, dressing plain rooms, the checks, the renderer and `rf rooms gallery`; set pieces ([below](#set-pieces)): the matcher and the mask report (`set_pieces.py`) and `rf set-pieces import` (`set_piece_import.py`) |
| `realmforge_tools/packboot.py` | `rf pack-boot`: installs and boots the pack with NeoForge's self tests ([below](#pack-boot)) |
| `realmforge_tools/gamelog.py` | NeoForge log parsing shared by `rf pack-boot` and `rf gametest-log` ([below](#gametest-log)) |
| `realmforge_tools/client_isolation.py` | the import check that keeps client classes under `client/` (architecture 18) |
| `id_universe/` | generated snapshots (committed): vanilla's ids, and Apotheosis's data (`apotheosis-<version>.json`, MIT, its affixes, gems, tier augments, rarities and recipe ids) |
| `schemas/` | generated JSON Schemas for editor completion (committed) |
| `tests/` | pytest |

Generated directories (owned by `rf gen`, never edit by hand): `mod/src/generated/`, `qa-mod/src/generated/`,
`tools/schemas/`, and FTB Quests' quest book `pack/config/ftbquests/quests/` (`generate/quests.py`, JSON5, written once
`content/quests/` has a quest). When `rf gen` changes files in `pack/`, it runs `packwiz refresh` in `pack/`; without
`packwiz` on the `PATH` it exits with 1 and asks for it. `tools/id_universe/` is written by `rf ids vanilla` and `rf ids apotheosis`, never by hand: after an Apotheosis update
in the pack, run `rf ids apotheosis` (a pytest fails while the snapshot names another jar).

## Pack boot

`uv run rf pack-boot` ([ADR 0018](../docs/decisions/0018-pack-boot-tests.md)) checks that the whole modpack plus the
core mod jar loads, on both sides, the way players and server owners install it, and on the client with the jar of
Better Key Binds, our client-only mod the pack ships next to the core mod
([ADR 0048](../docs/decisions/0048-better-key-binds-our-own-client-mod-independent-of-realmforge.md)). Build the jars
first (`./gradlew :mod:jar :better-key-binds:jar`). It works in `.cache/pack-boot/`:

1. installs the game: the NeoForge server from its installer (`server/`), or the client: the same installer's
   `--installClient` puts NeoForge into `launcher/`, and the [portablemc](https://github.com/mindstorm38/portablemc)
   launcher, run by `uvx`, adds the vanilla client, libraries and about 1 GB of assets there and starts the game in
   `client/` (the game directory). portablemc 4.4.1 cannot run the processors of NeoForge 26.1's installer itself
   ([ADR 0018](../docs/decisions/0018-pack-boot-tests.md#note-2026-10-12-2612-port)). Every step runs on Java 25
   (`JAVA_HOME` or `PATH`), which the tool checks first;
2. installs the pack's mods for that side with packwiz-installer, serving `pack/` over a local HTTP server;
3. copies the jars into `mods/` (Better Key Binds' on the client only) and boots with NeoForge's self test, once with
   the optional mods and once without (once in all while the pack has none, as now); the client runs under Xvfb when
   `xvfb-run` exists (`sudo apt install xvfb`);
4. fails on a non-zero exit, a missing self-test report, a crash report, a jar that was not loaded, or an ERROR or
   FATAL log line that `ALLOWED_ERRORS` in `packboot.py` does not explain.

`--side server|client` boots one side, `--optional with|without` one variant, `--pack-dir` another pack (to try a
change, or a deliberately broken copy). Logs land in `.cache/pack-boot/reports/<side>/<variant>/`. The NeoForge
installer, packwiz-installer and its bootstrap are sha256-pinned and portablemc is version-pinned in `packboot.py`;
a NeoForge upgrade updates the pin there too (`test_packboot.py` fails until it matches `pack/pack.toml` and
`gradle.properties`).

## GameTest log

`./gradlew runGameTestServer` fails when a GameTest fails or when one of our datapack registries does not decode (the
server stops). Vanilla data is different: a broken loot table, recipe, advancement or tag is logged and skipped, so
the GameTests still pass. `uv run rf gametest-log` (CI's gradle job, after the GameTests) therefore reads
`run/gametest/logs/latest.log` and fails on every ERROR or FATAL line that `GAMETEST_ALLOWED_ERRORS` in `gamelog.py`
does not explain, on loot table validation warnings, and on a log without the "All N required tests passed" line.
`--log` checks another file.

## Rooms

Dungeon rooms are structure templates in `content/rooms/` with a YAML sidecar each
([architecture 15.4](../docs/architecture.md#154-rooms), [ADR 0028](../docs/decisions/0028-dungeon-rooms-generated-or-imported.md),
[content/README.md](../content/README.md)). `rf validate` checks them and `rf gen` copies each one, normalized, to
`data/realmforge/structure/rooms/<id>.nbt` in the jar, and the `LICENSE` and `NOTICE` (and, for an LGPL-3.0 source,
`COPYING`, the GPL-3.0 text) of each third-party source with a room to `META-INF/third_party/<source>/`. It also lists
every room with its size, jigsaws and air blocks in the QA mod's `GeneratedRoomExpectations`, which
`ContentRoomGameTests` compares with the rooms the game loads.

Decoding the templates is slow (the NBT reader is pure Python), so `rooms/cache.py` keeps each decoded template and
what is read from it (its size, connectors, normalized bytes, the problems `check_structure` finds) in
`.cache/rooms/parsed/<version>/`, keyed by the sha256 of the template's bytes. The version folder is named after
a digest of the code in `rooms/`, `formats/`, `id_universe.py` and `schema/room_spec.py` (whose constants the room
generator builds from) and of the game's `DATA_VERSION`, and the check
results also carry the ID universe's digest (`check-<digest>/`). A warm cache answers `rf validate` and `rf gen`
without decoding any room; a changed template, a change to that code or to `DATA_VERSION` misses and is decoded
again (a few seconds when every room misses, as in CI or a new worktree). Nothing piles up: a run's first write
deletes the other versions' folders, and its first check result the other universes' (RLM-238). A value is cached
only when the functions that compute it live in that code and read no other constant than `DATA_VERSION`; for any
other change to what is cached, bump `CACHE_VERSION`. Deleting `.cache/rooms/parsed/` is always safe.

**`rf rooms import SRC... --to DIR`** turns room files into rooms: vanilla structure templates (`.nbt`), Sponge
schematics (`.schem`, versions 1 to 3, as WorldEdit saves them) and Litematica schematics (`.litematic`, versions 5
to 7, Minecraft 1.13.2 and later; several regions merge into one room; an older file is refused, because it holds
1.12 block states that only Litematica converts: open it there and save it again). A folder imports every room file
below it, keeping its subfolders. Each file becomes `DIR/<name>.nbt`, the normalized template (blocks sorted, keys
sorted, stored deflate, so equal rooms give equal bytes), and `DIR/<name>.yaml`, a draft sidecar that records the
source file, its format and its data version, leaves `pools` empty and drafts the slot `tags` (`rooms/slots.py`,
[content/README.md](../content/README.md)): the shape from the faces with a connector (`dead_end`, `straight`,
`curved`, `t_junction`, `crossing`, plus `vertical` for one up or down), the role from a word in the room's path
(`boss`, `treasure`, `entrance`, `stairs`, `hallway`), else `hallway` for a straight or curved piece at most 7 blocks
wide (measured across the passage) and `combat` for the rest. A re-import keeps the sidecar's pools, tags, weight
and room rules (`max_per_layout`, `near_start`, `explosion_breaks`, `entry`, `containers`, `openings`;
[content/README](../content/README.md)). A room
is written only when it validates; otherwise the command lists the problems and writes nothing for that
room, and so does a source file that cannot be read (some jars ship a corrupt template; exclude it). A file that the
gallery's review rejects is skipped (`SKIP` in the report): the `review.yaml` of the target folder decides, or that of
a folder below it (the gallery reviews a subfolder on its own) or, inside `content/rooms/`, above it, and the nearest
decision counts. A dropped room therefore stays dropped when its source is imported again;
delete its decision to take it back in. Inside `content/rooms/`, third-party rooms must go to `third_party/<source>/`,
next to the source's `LICENSE` and `NOTICE`; rooms we built ourselves (exported from a dev world, say) take `--ours`,
which the sidecar records as `source.ours`, and go anywhere else. Validation holds every room with a `source` to the
same rule.

The template keeps the data version its source states, and the game upgrades it from there on load. A file that
states none (a Sponge v1 schematic, a template some tool wrote) is refused: the game would read it as version 500 and
run every upgrade since on it, so a sign would come out blank. When the source is known to be saved with a modern
version, the rules file's `data_version` gives those files one. An id the ID universe lacks, a source's own block or
a vanilla block renamed since (`minecraft:grass` became `minecraft:short_grass`), is mapped in a rules file
(`--rules`, YAML):

```yaml
blocks:               # source block -> block state; a bare id keeps the source's properties, id[...] replaces them
  dungeonz:dungeon_bricks: minecraft:stone_bricks
  dungeonz:dungeon_stairs: minecraft:stone_brick_stairs
  dungeonz:pillar: minecraft:polished_andesite[]
block_entities:       # source block entity -> ours, data kept; a remapped block's data is dropped otherwise
  dungeonz:dungeon_chest: minecraft:chest
connectors:           # jigsaw name and target -> our connector names
  stonevaults:door: realmforge:door
pools:                # jigsaw pool -> a vanilla template pool or a room pool some sidecar lists
  stonevaults:rooms: minecraft:empty
jigsaws:
  interior: drop      # keep (default) | drop: a jigsaw inside the room (a prop, a mob) becomes its final_state
  normalize: false    # true: every connector realmforge:door (sides) or realmforge:shaft (up, down), pool empty
loot_tables:          # container loot table -> ours or vanilla; null drops it; <namespace>:* matches a namespace
  stonevaults:dungeon/rare: minecraft:chests/simple_dungeon
  stonevaults:*: null
entities:
  keep: [minecraft:armor_stand]   # every other entity is dropped
include: [dungeon/*]  # fnmatch patterns on the path below a source folder (* also matches /); default: every file
exclude: [dungeon/props/*, dungeon/broken.nbt]
max_size: 32          # leave out rooms bigger on any axis (a whole wing of the source's dungeon)
dress:                # variety and decorations for plain rooms (rooms/dress.py)
  exclude: [dungeon/entrances/*]                # rooms left as they are
  floor: {target: 4, replace: [minecraft:cobblestone], variants: [minecraft:mossy_cobblestone, minecraft:gravel]}
  walls: {target: 4, replace: [minecraft:stone_bricks], variants: [minecraft:cracked_stone_bricks]}
  ceiling: {target: 3, replace: [minecraft:stone_bricks], variants: [minecraft:mossy_stone_bricks]}
  decorations: [vines, cobwebs, barrels]
  without: {barrels: [dungeon/rooms/library]}  # rooms that do not get a decoration
  lever_hints: minecraft:stone_brick_stairs    # the floor under each lever that must be on (a torch behind it)
grow:                 # layers added on top of a room after its dressing, before place, by the room's path
  dungeon/treasure/egg: {up: 1}   # room for the lava over its glass ceiling (RLM-361)
place:                # boxes of one block, of data markers, of spawners or of what connectors leave behind
  dungeon/lobby/lobby_01:   # the fall cushion (RLM-276)
    - {from: [5, 0, 5], to: [7, 0, 7], block: "minecraft:stone_brick_slab[type=bottom,waterlogged=true]"}
    - {from: [4, 0, 5], to: [4, 0, 7], block: "minecraft:stone_brick_stairs[facing=west,half=bottom,shape=straight,waterlogged=true]"}
    # ... the other three sides and the four corners (art/rooms/stonevaults/rules.yaml)
  dungeon/treasure/parrot:  # the exit of the room that ends the test dungeon (RLM-363, RLM-361)
    - {from: [5, 2, 3], to: [5, 2, 3], marker: exit}
  dungeon/treasure/egg:     # a spawner of one mob: a spawner already there keeps its settings, others get vanilla's
    - {from: [6, 4, 6], to: [6, 4, 6], spawner: minecraft:cave_spider}
  dungeon/rooms/husk_temple:  # its connectors' final_state: an open doorway where the source left iron bars
    - {from: [0, 1, 6], to: [0, 1, 6], final_state: "minecraft:air"}
data_version: 4790    # for the files that state none, when the source is known to be saved with this version
```

`blocks` also applies to the block a jigsaw leaves behind (its `final_state`), and validation checks that block's id
like any other. A `spawner` box writes its mob into both a spawner's `SpawnData` and its `SpawnPotentials`, in the
form of the template's data version (from 2831 the mob sits in `entity`, before it is the entity itself): vanilla
draws from the potentials after every spawn, so a stale one turns the spawner into another mob's (Stonevaults'
skeleton trap spawned pigs, RLM-412); the mob must be in the ID universe. `grow` adds empty layers on top of a
template, where `place` can set blocks the source had no room for; a box outside the template is a problem. A connector is a jigsaw on the room's boundary, pointing out; one inside the room pulls a sub-piece
in (a prop, a mob, a loot pile), which our dungeons do their own way, and validation refuses it, so
`jigsaws.interior: drop` replaces it with its `final_state`. `jigsaws.normalize` gives every connector the neutral
names our layout reads instead of the source's (it leaves no room for `connectors` and `pools`). A container's loot
table must be in the ID universe, or the container would stay empty: `loot_tables` maps a source's tables to vanilla
ones or drops them, so the dungeon fills the chest (W4). `include`, `exclude` and `max_size` record which files of a
source are rooms, so the import can run again; the report counts the files they leave out. `dress` gives plain
rooms variety: a surface (floor: air above, ceiling: air below, walls: air beside) with fewer block families than its
`target` (a family is a material whatever its shape; it counts from 5 % of the surface) gets the next variants it
lacks, each replacing 15 % of its `replace` blocks in patches, gravel only over a block. The plainer the room was,
the more decorations it gets: vines from the top of the walls, cobwebs where wall meets ceiling and barrels, all
clear of the doorways and of the room's own entities; `without` leaves a decoration out of the rooms it names. The
dressing changes no block within one block of a mechanism (redstone, repeaters, comparators, levers, buttons,
pressure plates, pistons, observers, dispensers, droppers, hoppers, TNT, targets, tripwires, trapped chests, note
blocks, doors and the like), and decorates only the air a player reaches from the room's doorways, walking through
air and what a player passes (vines, cobwebs, torches, carpets, plates, buttons, water), so no decoration jams a
lock or a trap (RLM-364). `lever_hints` names a stairs block: under each lever on a wall whose block has a redstone
torch behind it ([lever] [block] [torch], a lever that must be on), the floor block becomes those stairs, tall half
towards the room, so a combination lock shows its answer.
Barrels form a pattern ([ADR 0036](../docs/decisions/0036-room-lights-and-decorations-form-patterns.md), RLM-262):
they stand on the room's main floor against its outer walls (a full block with no air behind it, so never on or
beside a throne or pillar inside the room), never in a passage one block wide or where they would split the floor. A
hallway gets at most one, in a corner, stairs none, other rooms by floor area two (a pair in a corner, two mirrored
corners or a pair in a wall's middle), three (a row in a wall's middle) or four (two mirrored pairs or four mirrored
corners); a room no pattern fits gets none. No armor stands: placed by rule they looked out of place in Matěj's
playtest. Choices are hashes of the room's path, so a re-import gives the same room.
The import report names every remap, every dropped entity, block entity, interior jigsaw and loot table, and the
jigsaw names and pools no rule mapped; validation refuses a jigsaw pool that is neither a vanilla template pool nor a
room pool some sidecar lists. The
rules of the imported sources live in `art/rooms/<source>/rules.yaml` ([art/rooms](../art/rooms/README.md)). The
schematic readers live in `rooms/sources.py`: nucleation 0.10.24 reads both formats too,
but it writes only the tight box around a room's non-air blocks, so air-only layers at the edge vanish and the
blocks shift (RLM-119).

**`rf rooms gallery [DIR]`** renders every `*.nbt` below `DIR` (default `content/rooms/`; any folder works, such as the
spike rooms in `qa-mod/src/generated/resources/data/realmforge_qa/structure/spike/`, which can be viewed but not
reviewed, or a folder of generated seed candidates) from four views: outside, cut `--cut` blocks above the bottom
(default 5) from two corners, and a plan of the cut. Every data marker shows with its label
([ADR 0035](../docs/decisions/0035-room-galleries-show-every-marker-with-its-label.md)): the views draw it as a block in
its label's colour (`MARKER_STYLES` in `rooms/render.py`: `entrance` lime, `mob` red, `chest` yellow, `exit` light
blue, anything else magenta), also above the cut, each card lists the room's markers by label with their positions,
and the page opens with a legend of the labels and the connectors. A new marker gets its style in the same change (a
test holds `MARKERS` to it). With set pieces in `content/set_pieces/`, each room also shows its set-piece spots
([below](#set-pieces)): every pattern's floor cells drawn as stained glass in its fit's colour (`SPOT_STYLES`), listed
on the card with the pieces that fit it, and a fifth view, the sample dressing, with the heaviest of those pieces in
every pattern. A room's spots go by its sidecar's role (any role without a sidecar) and, for a candidate of a room
spec, by the spec's theme and paths. It writes the PNGs and `index.html` to
`.cache/rooms/gallery/<folder>/` (replaced on every run; `--out` picks another folder, which must not overlap the rooms'
folder and is replaced only when it is empty or an earlier gallery, marked by a `.room-gallery` file). The renderer is
[nucleation](https://github.com/Schem-at/Nucleation) (MIT, pinned in `pyproject.toml`), with block models and textures
from the official client jar, which the first run downloads into `.cache/vanilla/<version>/` (sha1-verified) and which
is never committed. It draws on Vulkan: without a GPU it needs Mesa's software driver lavapipe (package
`mesa-vulkan-drivers`, [dev setup](../docs/dev-setup.md#1-tools-in-wsl-no-sudo-needed)); a cloud session installs it
first ([dev setup: Cloud sessions](../docs/dev-setup.md#cloud-sessions)). Three spike rooms take about 3 seconds, the
first run about 5 (the jar download), about 7 in a cloud session.

On the page, choose Keep or Reject per room and copy the decisions (YAML, one `"<room>": keep|reject` per line, the
name quoted) into the session. **`rf rooms review DIR`** merges them (standard input, or `--from FILE`) into
`DIR/review.yaml`, the record the import (A4c) and the room generator (A4d) act on: rejected imports are dropped, a
kept candidate's seed is frozen. The next gallery starts from those decisions. `rf validate` checks that every
`keep` names a room; a `reject` outlives its dropped room, so the next import of the source skips it. A generated
folder, such as the spike rooms, has no review: `rf rooms review` refuses it, because `rf gen` would delete its
`review.yaml`.

**Generated rooms** (A4d) come from the room specs in `content/room_specs/` and the themes in
`content/room_themes/` ([content/README.md](../content/README.md)). The room generator (`rooms/generator.py`) builds
a spec with a seed; `rf gen` builds every kept seed (the spec's `seeds`) into
`data/realmforge/structure/rooms/generated/<spec>/seed_<n>.nbt`, and the room cache keeps each built room under a
digest of the spec, its theme and the seed, so validation builds a spec again only when one of them, or the
generator, changed. Validation runs `rooms/walk.py` on each: its doorways reach each other with three blocks of
headroom, and no cell where a mob could spawn is dark. **`rf rooms candidates [SPEC...]`** (`--count`, 4 by default;
`--out`) builds the next seeds of each spec that no review decided on (neither kept nor rejected, from 1 up) into
`.cache/rooms/candidates/<spec>/seed_<n>.nbt` with a sidecar, and leaves out, with the reason, a candidate that
validation would refuse. It replaces that folder on every run. `--kept` builds the specs' kept seeds too (with
`--count 0` only those), so a change to the generator can be reviewed on the rooms it changes; a reject decision on a
kept room moves it to `rejected_seeds`. A kept seed the changed generator can no longer build, or whose room no
longer validates, stops `rf validate` and `rf gen`, but not `--kept`: it prints `REFUSED seed <n>: <reason>`, and
`rf rooms review` takes a `<spec>/seed_<n>: reject` for it although the folder holds no room for it. The run records
the kept seeds it refused in the folder's `.refused-kept-seeds`, and the review takes a decision on a seed without a
room only for those: a decision on a kept seed that run neither wrote nor refused (a typo, or a folder written
without `--kept`) is refused as no such room. Review them like any other folder:

```bash
uv run rf rooms candidates                                  # every spec; or name some
uv run rf rooms gallery .cache/rooms/candidates --cut 4     # --cut 4 shows the rooms below their cornice
uv run rf rooms review .cache/rooms/candidates --from decisions.yaml
uv run rf gen                                               # writes the kept rooms
```

`rf rooms review` on the candidates folder (marked by `.room-candidates`) also freezes its decisions into the specs:
a kept seed joins `seeds`, a rejected one `rejected_seeds`, and only those two lines of the spec change (written as
flow lists, `seeds: [2, 7]`; a spec that writes either as a block list is refused). The generator is deterministic,
so a change to it changes every kept room: `rf gen --check` shows which, and a change a player would see goes back to
the gallery.

## Set pieces

Set pieces (RLM-280, RLM-281; [design](../docs/design/set-pieces.md),
[ADR 0050](../docs/decisions/0050-set-pieces-hand-built-nbt-placed-at-dungeon-generation.md)) are small hand-built
pieces the dungeon fits into rooms: `content/set_pieces/<id>.nbt` with a fit rule `<id>.yaml`
([content/README.md](../content/README.md)). `rooms/set_pieces.py` holds the mask, the matcher and its patterns
([architecture 15.4](../docs/architecture.md#154-rooms)); `rf gen` writes each pooled room's patterns into the dungeon
type entry (`generate/dungeons.py`) and each piece's template and processor list (`generate/set_pieces.py`), and the
room cache keeps a room's patterns under a digest of the fits, the room's theme and paths.

**`rf set-pieces report [MASK...]`** counts the spots of the workshop jig's masks (`NAMED_MASKS`: `wall_<w>x<d>`,
`corner_<n>x<n>`, `niche_<w>x1`, `free_<n>x<n>`, `built_niche_3x1`; all of them without arguments) in every room, as
the design's mask report did: rooms with at least one spot / spots, per source (split by pool where a source has rooms
in and out of pools) and in all, each room's spots placed greedily one by one so they can all be filled at once, and
the walls they stand against by block family. `--game` counts as `rf gen` does: with the light check, and off
generated rooms' paths. All masks over the 164 rooms take about 5 seconds (about 15 with `--game`).

**The workshop:** in the dev client, `/qa setpiece jig <mask> [<wall block>]` builds the frame for a mask north of you
and turns you to face it, with a structure block in Save mode whose box is the piece's cells, three blocks high, named
`realmforge_qa:set_piece/<mask>_<token>`, six random letters and digits that no other jig takes, in this world or
another. Build the piece in the box, press Save, then import the save. Build with vanilla blocks only: the import and `rf gen` refuse other mods' blocks (Macaw's included) until the full-pack registry dump exists (CLAUDE.md rule 3).

```bash
uv run rf set-pieces import run/client/saves/<world>/generated/realmforge_qa/structure/set_piece/wall_3x1_k3j9z1.nbt --id barrel_shelf
uv run rf set-pieces import --from-world <world>      # every save of run/client/saves/<world> not imported yet
```

**`rf set-pieces import FILE --id ID`** reads the mask from the save's name and the height from its size, less the
layers on top that hold only air, checks the piece (its cells, ids and blocks, as validation does), writes the template
normalized as `content/set_pieces/<ID>.nbt` and drafts `<ID>.yaml`: the mask, the height, `builds_walls` for
`built_niche_3x1`, empty `tags` for you to fill in (validation refuses the fit rule until then), the default roles and
weight, and `source`, the save it came from. An existing fit rule is kept, so importing a save again replaces only its
template; an id whose fit rule came from another save is refused. A piece holds only decorative entities (item frames,
paintings, armor stands, displays), and an import of other mods' blocks says why it refuses them. **`--from-world WORLD`** (a world folder, or a world name in `run/client/saves/`) imports every save of the
jig in that world that no fit rule names as its `source`, each under its save's name as its id (rename the two files
and the `id` together). `--to DIR` writes elsewhere than `content/set_pieces/`. Then `uv run rf gen`.
