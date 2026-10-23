# Set pieces

> Small hand-built corners, niches and props that the dungeon fits into generated and imported rooms (RLM-175).
> Written on 2026-10-19 from Matěj's idea of 2026-10-14 (the ticket's context), RLM-121's evidence (the room
> generator, 2026-10-17) and [ADR 0036](../decisions/0036-room-lights-and-decorations-form-patterns.md). The mask
> report was run over the 164 rooms on `master` at `4e0043a`. Rooms stay generated or imported
> ([ADR 0028](../decisions/0028-dungeon-rooms-generated-or-imported.md)); set pieces are their dressing, not rooms.
> **Matěj approved this design on 2026-10-19** with the [decisions](#decisions) below. Implemented by RLM-280 (the
> content type, the matcher and the spots, [ADR 0050](../decisions/0050-set-pieces-hand-built-nbt-placed-at-dungeon-generation.md),
> [architecture 15.4](../architecture.md#154-rooms)) and RLM-281 (the jig and the import); RLM-282 places the pieces.

## At a glance

- **A set piece** is a small structure Matěj builds in a few minutes (his example: a stone-brick niche with hay
  bales, a cauldron with water, a barrel and a skeleton skull), saved with a short YAML fit rule. He builds a few a
  week, for as long as the game grows.
- **`rf gen` finds the spots, the dungeon builder fills them.** The fit matcher runs in Python on every room and
  writes each room's spots into the dungeon type entry, next to its connectors. When a plot opens, the builder picks
  a piece for each spot from the run's seed, with the dungeon's theme. Every run looks a little different, a theme
  swap reaches the pieces, and the room gallery shows each room's spots with a sample dressing.
- **The fit rule is Matěj's own notation:** the top view he drew on the ticket (`W` wall, `.` the piece, `_` space
  that stays walkable), plus height, tags, roles and weight. The matcher turns a piece to face its wall and may
  mirror it.
- **Build these sizes first** (spots in the current rooms): 1x1 against a wall (1,569 spots in 112 of 164 rooms),
  1x1 free-standing (489 in 96), 2x1 against a wall (408 in 68), 1x1 corner (332 in 73), 3x1 against a wall (201 in
  49). Real niches are rare (40 spots in all), so a niche piece brings its own niche walls.
- **Spots form patterns** ([ADR 0036](../decisions/0036-room-lights-and-decorations-form-patterns.md)): a room gets
  its pieces in mirrored pairs, centred on a wall's axis, or one per corner, never one stray piece.
- **One build gives several variants:** mirroring, blocks that appear only by chance (the skull half the time) and
  block swaps per theme, so two or three builds of a piece are enough.
- **Building is one step:** `/qa setpiece jig <mask>` builds the workshop frame with a structure block already set
  to the piece's bounds; Matěj builds, presses Save, and `rf set-pieces import` brings the file into
  `content/set_pieces/` with a drafted fit rule.
- **Containers hold loot:** a chest or barrel in a piece rolls the normal container loot table, per player through
  Lootr ([container loot](container-loot.md)). A piece never blocks a doorway, a path between doorways, a marker, a
  light or a spawner.

## Where it plugs in

Three places could put set pieces into rooms:

| | Import dressing (`rooms/dress.py`) | Dungeon generation (W4 builder) | Jigsaw sockets in the rooms |
| --- | --- | --- | --- |
| **Rooms it reaches** | imported rooms only; generated rooms would need the same step in the generator | every room, generated and imported | only rooms someone fitted with sockets |
| **Variety** | none at play time: a room always has the same pieces | a new pick on every run, from the run's seed | a new pick per run (vanilla's template pools) |
| **Theme swap** | baked into the room, swapped with it | placed with the dungeon's theme processors, like the room | placed through the pool's processors |
| **Fit to the room** | full: the dressing sees the whole room | full, if the spots are known in advance | none: a socket accepts whatever its pool holds |
| **Review in the gallery** | yes, the piece is part of the room | only with spots shown and a sample dressing | only with a sample dressing |
| **Cost** | Python only | Python matcher, a small Java placement step | sockets added to 164 rooms by hand or by a tool, and no fit check |
| **Licensing** | our piece is baked into a third-party room, under that room's license | our piece stays ours, placed next to the room | our piece stays ours |

**Recommendation: find the spots at `rf gen`, place the pieces at dungeon generation.** It combines the import
dressing's full view of the room with the builder's variety:

1. **`rf gen`** runs the fit matcher on every room of every pool (`rooms/set_pieces.py`) and writes each room's
   spots into the dungeon type entry, as it already does for the connectors
   ([architecture 15.2](../architecture.md#152-dungeon-types)): position, facing, mask and the tags the room's
   role and its pool allow. The matcher is deterministic and tested with pytest like the rest of `rooms/`.
2. **The builder** ([architecture 15.3](../architecture.md#153-generation-pure-first-then-minecraft), step 3) picks
   for each spot a piece whose mask matches and whose tags the dungeon type asks for, by weight, from the plot's
   seed, or leaves the spot empty by the type's density. It places the piece's template with the room's rotation
   plus the spot's own facing, the theme's processor list and the piece's own processors (variety, below). Each
   placement is one `StructureTemplate#placeInWorld`, the same call the rooms use.
3. **The gallery** shows each room's spots as labelled outlines, as it shows markers
   ([ADR 0035](../decisions/0035-room-galleries-show-every-marker-with-its-label.md)), and can render the room with
   a sample dressing (seed 0), so Matěj judges both the spots and the pieces in them.

The spots are data the game reads, so a room needs no change, and a new piece reaches every room at the next
`rf gen`. World structures (M2, [architecture 15.6](../architecture.md#156-world-structures-m2)) are vanilla jigsaw
output and never run the dungeon builder's placement step, so set pieces reach them only with a placement step of
their own. The import dressing keeps
its block variety, vines and cobwebs; its barrels (which RLM-262 puts into patterns) can give way to barrel set
pieces once those exist.

## The fit rule

A piece is two files in `content/set_pieces/`: the template (`<id>.nbt`, saved by the workshop, normalized by
`rf gen` like a room) and its fit rule (`<id>.yaml`). Matěj's niche as a fit rule:

```yaml
# content/set_pieces/hay_niche.yaml
id: hay_niche
mask: |              # top view; the first row is the wall side, the room is below
  W W W W W
  W . . . W
  _ _ _ _ _
height: 3            # the piece's blocks reach at most this high above the floor
builds_walls: true   # the template holds its W cells: it needs a flat wall and free space, not a real niche
walls: [wall]        # theme roles (or block ids) the W cells accept
tags: [storage, stable]
roles: [combat, hallway, treasure]   # room roles it may appear in
weight: 2
variants:
  mirror: true
  chance: {minecraft:skeleton_skull: 0.5}
```

- **The mask** uses Matěj's own notation from the ticket:
  - `W` must be wall: a full block from the floor up to `height`;
  - `.` is the piece: air from the floor up to `height`, over a floor block;
  - `_` is clearance: it must stay a cell a player stands on (three blocks of headroom, `rooms/walk.py`), and
    nothing else may take it;
  - `?` is anything.

  Single spaces separate the cells of a row.
  
  A mask with `W` cells on one side faces that wall, and the matcher turns it to every wall it fits. A mask without
  `W` is free-standing (any facing; the matcher may still turn it); one with `W` on two adjacent sides is a corner.
- **A piece that brings its own walls**, like Matěj's niche, marks its walls `W` but builds them itself: the
  template holds the stone bricks, and `builds_walls: true` tells the matcher that those cells need free space in
  front of a flat wall (`.`), not an existing wall. The [mask report](#the-mask-report) shows why this matters:
  real three-wide niches are almost absent (four spots), but 30 spots take the niche with its walls, a piece five
  wide and two deep, against a flat wall (the 5x2 row).
- **Height** is 3 by default (Matěj's maximum); the matcher checks air up to it and wall behind it.
- **Walls** name the theme roles a piece may stand against (`wall` covers `wall_base`, `wall_body` and its
  patches, and `wall_cornice`, [architecture 15.4](../architecture.md#154-rooms)), or blocks by id; for an imported
  room, whose blocks are not roles yet, the matcher reads any full block as wall for a role, except barriers (below).
  Block tags are not offered: the ID universe knows only their names, not their blocks
  ([ADR 0050](../decisions/0050-set-pieces-hand-built-nbt-placed-at-dungeon-generation.md)).
- **Tags** say what a piece is (storage, prison, crypt, shrine, library, mine, kitchen, ...); a dungeon type lists
  the tags it uses, so a crypt never gets a kitchen.
- **Roles** keep pieces out of rooms where they do not belong: none in an entrance by default, a treasure room
  takes only its own tags (its five chest spots stay its loot, ADR 0036 point 3).
- **Weight** works as a room's weight does.

The matcher uses no LLM and no randomness: the same rooms and pieces always give the same spots.

## The mask report

How many spots each candidate mask gets in the 164 rooms on `master` (122 imported, 42 generated), as "rooms with
at least one spot / spots". A room's spots are placed greedily, without overlapping, so the counts are spots that
can be filled at once. `uv run rf set-pieces report` counts them again for the rooms of today, with the masks the
workshop jig builds (RLM-280; [tools/README](../../tools/README.md#set-pieces)). A spot must:

- stand on a floor block, with air up to height 3 that no marker or entity needs;
- have a full block behind it up to height 3 (wall masks), and on its sides too (niches and corners);
- keep its clearance row (wall masks) or a one-block ring (free-standing) walkable;
- stay two blocks or more from every connector; and
- leave every doorway reachable from every other, the clearance included, after it and the room's earlier spots
  are filled.

| Mask | Generated (stone vault, 42) | Stonevaults, pool `stone_vault` (23) | Stonevaults, no pool (22) | DungeonZ (32) | Dungeon Now Loading (11) | Mo' Structures (24) | Desert Dungeon (10) | All (164) |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 1x1 against a wall | 42 / 513 | 6 / 75 | 11 / 113 | 23 / 426 | 10 / 279 | 11 / 55 | 9 / 108 | **112 / 1,569** |
| 1x1 corner | 34 / 125 | 5 / 24 | 4 / 16 | 9 / 60 | 9 / 71 | 3 / 4 | 9 / 32 | **73 / 332** |
| 2x1 against a wall | 29 / 176 | 3 / 22 | 8 / 38 | 11 / 73 | 9 / 74 | 4 / 12 | 4 / 13 | **68 / 408** |
| 3x1 against a wall | 23 / 99 | 2 / 6 | 4 / 13 | 9 / 32 | 8 / 41 | 1 / 5 | 2 / 5 | **49 / 201** |
| 4x1 against a wall | | | | | | | | **31 / 99** |
| 5x1 against a wall | | | | | | | | **19 / 54** |
| 3x2 against a wall | 20 / 73 | 0 / 0 | 3 / 5 | 7 / 21 | 7 / 32 | 1 / 5 | 2 / 4 | **40 / 140** |
| 5x2 against a wall | 10 / 23 | 0 / 0 | 1 / 1 | 3 / 3 | 1 / 1 | 0 / 0 | 1 / 2 | **16 / 30** |
| 2x2 corner | 12 / 28 | 1 / 1 | 2 / 4 | 2 / 12 | 5 / 12 | 0 / 0 | 0 / 0 | **22 / 57** |
| 1x1 niche | 5 / 24 | 0 / 0 | 0 / 0 | 1 / 1 | 0 / 0 | 0 / 0 | 2 / 2 | **8 / 27** |
| 2x1 niche | 2 / 6 | 0 / 0 | 1 / 1 | 0 / 0 | 1 / 2 | 0 / 0 | 0 / 0 | **4 / 9** |
| 3x1 niche | 0 / 0 | 0 / 0 | 1 / 1 | 2 / 3 | 0 / 0 | 0 / 0 | 0 / 0 | **3 / 4** |
| 1x1 free-standing | 42 / 164 | 3 / 20 | 8 / 26 | 18 / 136 | 10 / 88 | 7 / 23 | 8 / 32 | **96 / 489** |
| 2x2 free-standing | 22 / 48 | 1 / 4 | 2 / 5 | 10 / 39 | 9 / 33 | 2 / 5 | 3 / 10 | **49 / 144** |
| 3x3 free-standing | 14 / 14 | 1 / 1 | 1 / 1 | 5 / 20 | 7 / 22 | 1 / 3 | 3 / 7 | **32 / 68** |

The 4x1 and 5x1 rows were counted only for the whole set. Only Stonevaults' 23 rooms are in a pool today
(`realmforge:stone_vault`, the test dungeon's); the generated rooms join pools with W6.

**What it means for building:**

1. **Small pieces first.** 1x1 and 2x1 against a wall, 1x1 corners and 1x1 free-standing props fit nearly every
   room: a barrel stack, a skull on a block, an anvil, a cauldron, a hay bale with a pitchfork, a broken crate.
2. **3x1 against a wall** is the biggest wall size that still reaches a third of the rooms; it suits shelves, beds,
   tables and racks.
3. **Niches mostly come from the generator.** The generated alcoves are one or two blocks wide (`_alcoves` picks a
   rhythm of 2 or 3), so 1x1 and 2x1 niche pieces have 30 spots there; real three-wide niches exist in three rooms
   (four spots). A piece like Matěj's hay niche is best built with its own walls (`builds_walls`): with them it is
   five wide and two deep, against a flat wall (30 spots). The generator can also grow a `set_piece_niche` feature, three wide, if Matěj likes the look; that is a
   room spec change, so it goes through the gallery.
4. **Free-standing 3x3 pieces** fit about one room in five, mostly big ones: a well, a campfire ring, a table with
   stools, a collapsed cart.
5. **Wall-backed spots dominate in hallways**, free-standing ones in combat rooms; a type's density should differ
   by role (fewer pieces in a hallway, more in a combat room, its own tags in a treasure room).

**Barriers.** DungeonZ's quiz rooms and the Desert Dungeon rooms hold invisible barrier blocks (266 in each of two
quiz rooms, 209 in the desert entrance): the source's invisible walls and ceilings. The matcher treats a barrier
as neither wall nor floor, so no piece stands against one or on one; the report above counted them, which adds a
few spots in those rooms that the matcher will not offer.

## Materials and surroundings

What the rooms are built from, by share of each surface's blocks (block families: `stone_bricks` and its stairs and
slabs are one):

| Source | Walls | Floors | Decoration already there |
| --- | --- | --- | --- |
| Generated (stone vault theme) | stone bricks 50 %, cobblestone 24 %, chiseled, cracked and mossy stone bricks 7 % each | smooth stone 36 %, polished andesite 31 %, andesite 15 % | wall torches, lanterns |
| Stonevaults | stone bricks 46 %, cracked 10 %, mossy 8 %, cobblestone 7 % | dark oak 32 %, stone bricks 25 %, cobblestone 7 % | vines, bookshelves, cobwebs, lanterns, barrels, chests, banners |
| DungeonZ | stone 24 %, blackstone 15 %, deepslate tiles 13 %, polished blackstone bricks 10 % | stone 15 %, polished blackstone bricks 13 %, deepslate tiles 9 % | vines, cave vines, chains, soul lanterns, candles, barrels |
| Dungeon Now Loading | stone bricks 48 %, smooth stone 17 %, iron bars 12 % | cobblestone 39 %, pointed dripstone 16 % | iron bars (a prison), chains, cobwebs, banners, barrels, hay |
| Mo' Structures | stone 19 %, stone bricks 13 %, polished deepslate 13 % | stone 21 %, soul sand 11 % | cobwebs, chains, iron bars, skulls, chests, decorated pots |
| Desert Dungeon | sandstone 34 %, smooth sandstone 22 %, sand 21 % | sandstone 24 %, smooth sandstone 21 % | decorated pots, barrels, skeleton skulls, lanterns |

Stone bricks carry most wall spots (684 of the 1,569 1x1 wall spots), then deepslate tiles (294). Pieces built
against stone bricks in the stone vault theme fit the most rooms today, and the theme swap carries them into the
other realms.

**Decor by realm** ([realm design](realms.md)). Vanilla first; every block below is vanilla 26.1.2:

| Realm | Walls and floors the pieces meet | Decor that suits it |
| --- | --- | --- |
| Undervault (stone, M1) | stone bricks, deepslate, tuff, cobblestone | barrels, crates of planks, minecart rails and a minecart, chains, lanterns, anvils, ore piles (raw ore blocks), scaffolding, tools in item frames |
| Sunwastes (desert) | sandstone, smooth and cut sandstone, terracotta | decorated pots, skulls, banners, hay, cactus in pots, suspicious sand, sarcophagi of stairs |
| Everwinter (tundra) | packed and blue ice, snow, spruce, stone bricks | spruce barrels and chests, campfires, powder snow, frozen lanterns (soul lanterns), furs (white wool and carpets) |
| Asgard (hub) | quartz, calcite, gold blocks | lecterns, bookshelves, candles, flower pots, banners, amethyst |
| Atlantis (water) | prismarine, dark prismarine, sandstone, coral blocks | sea lanterns, coral fans, kelp, sea pickles, conduits, barrels and chests from shipwrecks, chains |
| Quagmire (swamp) | mud, mangrove, slime | slime blocks, mangrove roots, lily pads in cauldrons, brewing stands, mushrooms |
| Ashfire (fire) | blackstone, basalt, magma | chains, soul lanterns, gilded blackstone, skulls, campfires, anvils |
| Unmade Isles (void) | obsidian, purpur and end stone blocks (the End itself stays removed) | chains, crying obsidian, amethyst clusters, candles, end rods |

**Pack mods.** Regions Unexplored adds woods and plants worth using for Quagmire. Sophisticated
Storage's barrels and chests are working storage, so they stay out of pieces. A modded block in a piece needs the
full-pack registry dump first, since every id must be in the ID universe (`CLAUDE.md`, rule 3).

## Cheap variety

How far one build stretches, all with vanilla means:

1. **Turning** comes free: the matcher turns a piece to face its wall, so one build serves all four walls.
2. **Mirroring** (`StructurePlaceSettings#setMirror`) doubles every asymmetric piece; the fit rule allows it with
   `mirror: true` (a piece with writing on a sign or an asymmetric story says no).
3. **Chance blocks:** a vanilla `rule` processor with a `random_block_match` input replaces a block with air at a
   given probability, so the skull is there half the time. `rf gen` writes the processor list from the fit rule's
   `chance` table.
4. **Swaps per theme:** the dungeon's theme processors swap the piece's role blocks with the room's (stone bricks to
   sandstone); a piece's own `swaps` table varies its props (a barrel becomes a chest, a cauldron a composter). Swaps
   of blocks with states need one rule per state, generated by `rf gen` as for the themes
   ([architecture 15.2](../architecture.md#152-dungeon-types), the block-state pitfall).
5. **Small sets per tag:** a crypt with two builds of a sarcophagus corner, mirrored, with a 50 % skull and a 30 %
   candle, already shows eight visibly different corners.

Two or three builds per piece kind are therefore enough, instead of five.

## The building workflow

1. **The jig:** `/qa setpiece jig <mask> [<wall block>]` (QA mod, dev client) builds the frame for a mask on a flat
   platform north of Matěj and turns him to face it: the `W` cells as walls of the given block (stone bricks by
   default), glass on the clearance cells, and a structure block in Save mode already set to the piece's bounds,
   three blocks high, and named `realmforge_qa:set_piece/<mask>_<n>`. The masks are the mask report's, by name
   (`wall_3x1`, `corner_1x1`, `niche_2x1`, `free_2x2`, `built_niche_3x1` for the hay niche, ...). For a piece with its
   own walls (`builds_walls`) the jig leaves the `W` cells inside the bounds, empty, for him to build, and builds a flat
   wall behind them.
2. **Building:** he builds the piece inside the bounds (blocks outside them are not saved) and presses Save in the
   structure block. Minecraft writes the file to
   `run/client/saves/<world>/generated/realmforge_qa/structure/set_piece/<name>.nbt`.
3. **Import:** `uv run rf set-pieces import <file> --id hay_niche` normalizes the template into
   `content/set_pieces/hay_niche.nbt` and drafts `hay_niche.yaml` with the mask the jig used (read from the name),
   `height` from the bounds (layers on top that hold only air are dropped, so a lower piece needs no change to the
   structure block), and tags, roles and weight to fill in. `--from-world <world>` takes every new save of a world at
   once, each under its save's name; importing a save again replaces only its template.
4. **Review:** `rf gen` validates the piece (ids, size, mask, a template that keeps its clearance clear), and the
   room gallery shows it in sample rooms. Matěj keeps or rejects it like a room.

The jig is dev-only code in `qa-mod/`; the import is part of `rf`.

## Gameplay rules

- **Containers hold loot** (Matěj, 2026-10-19, [ADR 0041](../decisions/0041-dungeon-containers-hold-loot-through-lootr.md)):
  no container in a dungeon is empty, so a chest or barrel in a piece rolls the normal container loot table, per
  player through Lootr, like every other chest and barrel outside a treasure room
  ([container loot](container-loot.md)). The builder places it as Lootr's container with that table; the piece's
  template only says where it stands.
- **Never in the way:**
  - two blocks or more from every connector, and every doorway stays reachable from every other with the piece
    placed (the matcher checks the room with all its spots filled);
  - never on a marker or within one block of a `mob` marker, and never in a generated room's paths between its
    doorways;
  - never over a light: the matcher reruns the dark-cell check of `rooms/walk.py` with the pieces in place, and a
    spot that would leave a cell dark is dropped (a piece with a lantern or candles lights its own cells);
  - no spawners, no fluid sources (a water cauldron is a block, not a fluid) and no redstone in a piece;
  - nothing a player could mistake for a progression object: no locked-looking doors, no puzzle-like levers.
- **Patterns** ([ADR 0036](../decisions/0036-room-lights-and-decorations-form-patterns.md), point 1): the matcher
  offers spots as patterns, not one by one: a pair mirrored across the room's symmetry axis, one centred on the wall
  facing the entrance, or one in each corner. The builder fills a whole pattern with the same piece (mirrored where
  the pattern mirrors) or leaves it empty.
- **Breaking:** pieces sit in the instance like the room, under its rules (Adventure mode and the guards,
  [architecture 15.5](../architecture.md#155-instances)), so nobody carries a piece home.

## Follow-up tickets

- **RLM-280** Set pieces: content type, fit rules and the matcher (`content/set_pieces/`, `rooms/set_pieces.py`,
  spots in the dungeon type entry, pytest, the mask report as an `rf` command, the gallery's spot outlines).
- **RLM-281** Set pieces: the workshop jig command and `rf set-pieces import`.
- **RLM-282** Set pieces: the dungeon builder places them (Java placement, variety processors, density per role,
  GameTests).
- **RLM-283** Set pieces: Matěj builds the first pieces (ongoing; his task, a few a week).

## Decisions

Matěj's answers of 2026-10-19 to this design's questions:

1. **Placement at generation**, as recommended: the spots are found at `rf gen` and the pieces placed when a plot
   opens, so every run varies.
2. **Containers in pieces hold loot**, the normal container table through Lootr (above); the question of decorative
   or clutter containers is gone.
3. **No three-wide niche feature yet:** pieces that bring their own walls cover it; a new generator feature goes
   through the gallery when Matěj wants it.
4. **Density:** a hallway gets at most one pattern, a combat room one or two, a treasure room its own tags only, and
   a dungeon type can lower or raise this.
