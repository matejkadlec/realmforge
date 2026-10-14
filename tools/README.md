# tools/

Python content pipeline (`realmforge_tools`, CLI `rf`). Requires Python 3.12+ and [uv](https://docs.astral.sh/uv/);
the project itself is defined in the root `pyproject.toml`.

```bash
uv run rf validate        # validate content/ (schema, ids, references)
uv run rf gen             # validate, then regenerate every generated file
uv run rf gen --check     # exit 1 if any generated file is out of date
uv run rf ids vanilla     # re-extract the vanilla ID universe (downloads the server jar, needs Java 25)
uv run rf pack-boot       # boot the whole pack plus mod/build/libs' jar on a server and a client (needs Java 25)
uv run rf gametest-log    # after ./gradlew runGameTestServer: exit 1 on data loading errors in its log
uv run rf art clean SRC DST   # snap a generated texture to the 16x16 grid and the project palette
uv run rf art gallery DIR     # review gallery for DIR/<subject>/<candidate>.png (.cache/art/)
uv run rf rooms import SRC... --to DIR [--rules FILE]  # .nbt/.schem/.litematic -> rooms with draft sidecars
uv run rf rooms gallery [DIR]  # render the rooms below DIR (default content/rooms) -> .cache/rooms/gallery/
uv run rf rooms review DIR     # merge decisions pasted from the gallery (stdin or --from) into DIR/review.yaml
uv run pytest             # tests
uv run ruff check && uv run ruff format --check
```

| Path | What |
| --- | --- |
| `realmforge_tools/schema/` | pydantic models, one per content type (`CONTENT_TYPES` maps `content/<dir>` to a model) |
| `realmforge_tools/content.py` | loads `content/` (step 1: schema) |
| `realmforge_tools/validate.py` | own ids and references against the ID universe (steps 2-3) |
| `realmforge_tools/id_universe.py`, `vanilla.py` | the ID universe and the vanilla extractor |
| `realmforge_tools/generate/` | generators; `generate_all` + `sync` own the generated directories; `datapack.py` writes every type in `DATAPACK_TYPES` as datapack registry entries (translation keys, unit conversion); `spell_schools.py` writes the spell schools with their damage types and damage type tag entries; `texts.py` writes the `GeneratedTexts` key constants and `lang.py` every locale's lang file |
| `realmforge_tools/formats/` | deterministic PNG, NBT, gzip/zlib and JSON writers; a PNG reader for generator output; an NBT reader (every tag type, the arrays included) |
| `realmforge_tools/art/` | texture pipeline ([ADR 0023](../docs/decisions/0023-texture-generator.md)): palette, `rf art clean`, `rf art gallery` |
| `realmforge_tools/rooms/` | dungeon rooms ([below](#rooms)): structure templates, the source readers, `rf rooms import`, the draft slot tags, the checks, the renderer and `rf rooms gallery` |
| `realmforge_tools/packboot.py` | `rf pack-boot`: installs and boots the pack with NeoForge's self tests ([below](#pack-boot)) |
| `realmforge_tools/gamelog.py` | NeoForge log parsing shared by `rf pack-boot` and `rf gametest-log` ([below](#gametest-log)) |
| `realmforge_tools/client_isolation.py` | the import check that keeps client classes under `client/` (architecture 18) |
| `id_universe/` | generated vanilla id snapshot (committed) |
| `schemas/` | generated JSON Schemas for editor completion (committed) |
| `tests/` | pytest |

Generated directories (owned by `rf gen`, never edit by hand): `mod/src/generated/`, `qa-mod/src/generated/`,
`tools/schemas/`. `tools/id_universe/` is written by `rf ids vanilla`.

## Pack boot

`uv run rf pack-boot` ([ADR 0018](../docs/decisions/0018-pack-boot-tests.md)) checks that the whole modpack plus the
core mod jar loads, on both sides, the way players and server owners install it. Build the jar first
(`./gradlew :mod:jar`). It works in `.cache/pack-boot/`:

1. installs the game: the NeoForge server from its installer (`server/`), or the client: the same installer's
   `--installClient` puts NeoForge into `launcher/`, and the [portablemc](https://github.com/mindstorm38/portablemc)
   launcher, run by `uvx`, adds the vanilla client, libraries and about 1 GB of assets there and starts the game in
   `client/` (the game directory). portablemc 4.4.1 cannot run the processors of NeoForge 26.1's installer itself
   ([ADR 0018](../docs/decisions/0018-pack-boot-tests.md#note-2026-10-12-2612-port)). Every step runs on Java 25
   (`JAVA_HOME` or `PATH`), which the tool checks first;
2. installs the pack's mods for that side with packwiz-installer, serving `pack/` over a local HTTP server;
3. copies the jar into `mods/` and boots with NeoForge's self test, once with the optional mods and once without
   (once in all while the pack has none, as now); the client runs under Xvfb when `xvfb-run` exists
   (`sudo apt install xvfb`);
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
`COPYING`, the GPL-3.0 text) of each third-party source with a room to `META-INF/third_party/<source>/`.

**`rf rooms import SRC... --to DIR`** turns room files into rooms: vanilla structure templates (`.nbt`), Sponge
schematics (`.schem`, versions 1 to 3, as WorldEdit saves them) and Litematica schematics (`.litematic`, versions 5
to 7, Minecraft 1.13.2 and later; several regions merge into one room; an older file is refused, because it holds
1.12 block states that only Litematica converts: open it there and save it again). A folder imports every room file
below it, keeping its subfolders. Each file becomes `DIR/<name>.nbt`, the normalized template (blocks sorted, keys
sorted, stored deflate, so equal rooms give equal bytes), and `DIR/<name>.yaml`, a draft sidecar that records the
source file, its format and its data version, leaves `pools` empty and drafts the slot `tags` (`rooms/slots.py`,
[content/README.md](../content/README.md)): the shape from the faces with a connector (`dead_end`, `straight`,
`curved`, `t_junction`, `crossing`, plus `vertical` for one up or down), the role from a word in the room's path
(`boss`, `treasure`, `entrance`, `stairs`, `hallway`), else `hallway` for a straight or curved piece at most 9 blocks
wide and `combat` for the rest. A re-import keeps the sidecar's pools, tags and weight. A room is written only when it
validates; otherwise the command lists the problems and writes nothing for that room, and so does a source file that
cannot be read (some jars ship a corrupt template; exclude it). A file that the gallery's review rejects is skipped
(`SKIP` in the report): the `review.yaml` of the target folder decides, or, inside `content/rooms/`, that of a folder
above it, and the nearest decision counts. A dropped room therefore stays dropped when its source is imported again;
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
pools:                # jigsaw pool -> ours
  stonevaults:rooms: realmforge:rooms/stone
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
data_version: 4790    # for the files that state none, when the source is known to be saved with this version
```

`blocks` also applies to the block a jigsaw leaves behind (its `final_state`), and validation checks that block's id
like any other. A connector is a jigsaw on the room's boundary, pointing out; one inside the room pulls a sub-piece
in (a prop, a mob, a loot pile), which our dungeons do their own way, and validation refuses it, so
`jigsaws.interior: drop` replaces it with its `final_state`. `jigsaws.normalize` gives every connector the neutral
names our layout reads instead of the source's (it leaves no room for `connectors` and `pools`). A container's loot
table must be in the ID universe, or the container would stay empty: `loot_tables` maps a source's tables to vanilla
ones or drops them, so the dungeon fills the chest (W4). `include`, `exclude` and `max_size` record which files of a
source are rooms, so the import can run again; the report counts the files they leave out. The import report names every remap,
every dropped entity, block entity, interior jigsaw and loot table, and the jigsaw names and pools no rule mapped. The
rules of the imported sources live in `art/rooms/<source>/rules.yaml` ([art/rooms](../art/rooms/README.md)). The
schematic readers live in `rooms/sources.py`: nucleation 0.10.24 reads both formats too,
but it writes only the tight box around a room's non-air blocks, so air-only layers at the edge vanish and the
blocks shift (RLM-119).

**`rf rooms gallery [DIR]`** renders every `*.nbt` below `DIR` (default `content/rooms/`; any folder works, such as the
spike rooms in `qa-mod/src/generated/resources/data/realmforge_qa/structure/spike/`, which can be viewed but not
reviewed, or a folder of generated seed candidates) from four views: outside, cut `--cut` blocks above the bottom
(default 5) from two corners, and a plan of the cut. It writes the PNGs and `index.html` to
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
