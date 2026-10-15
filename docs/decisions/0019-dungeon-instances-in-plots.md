# 0019. Dungeon instances in plots

Date: 2026-10-11

Status: Accepted (refines [0012](0012-procedural-dungeons-and-dimensions.md)), amended by
[0031](0031-instance-builder-background-chunks-and-shared-budget.md) (background chunk loads, a shared budget)

## Context

[ADR 0012](0012-procedural-dungeons-and-dimensions.md) puts instanced dungeons into plots of one instance dimension,
with no dimensions created at runtime, and left open how plots are allocated, kept loaded and cleaned up. Spike 3
(RLM-14) asked whether this works on 1.21.1 reliably and fast enough: build three hand-made rooms (start, corridor,
end) into a plot, teleport in and back, measure build time per room and the tick cost, compare three cleanup
strategies (clear the plot over ticks, never reuse plots, overwrite on reuse), and try a server restart and a
disconnect in the middle of a run. Decision D8 (RLM-58, Matěj, 2026-10-11) settled who enters: solo only in M1,
every player gets their own instance; parties come later (RLM-68).

The prototype is the code in `mod/.../dungeon/` (`DungeonFeature`, `DungeonAttachments`, `instance/`) and the QA
mod's `/qa instance` commands, bench and GameTests ([architecture 15.5](../architecture.md#155-instances)).

## Decision

1. **The dimension** `realmforge:instances` is a flat world with no layers and the `the_void` biome: no terrain, no
   natural spawning, fixed time (18000), no sky light, y 0 to 255. It is defined in the core mod's resources
   (`data/realmforge/dimension/instances.json`, `dimension_type/instances.json`), not in `content/worldgen/`: Java
   depends on it (`InstanceDimension.LEVEL`) and nobody tunes it as content.
2. **Plots** lie on a grid of cells 1,024 blocks apart, filled row by row; a plot's rooms stay within 256 blocks of
   its origin (y 64). One `InstanceManager`, a `SavedData` in the instance level, holds every plot: state
   (building, ready, active, closing, free; W4 adds completed), layout, room boxes, the boxes still holding blocks,
   work progress, party (a list of players, so parties need no migration) and the chunks it holds.
3. **Work in small units, within a budget.** Placing a room and clearing a box are cut into one unit per chunk column
   (`StructurePlaceSettings#setBoundingBox`); the builder runs units for up to 5 ms per tick (at least one), inside
   `LevelTickEvent.Post` of the instance level so the time counts in the server's tick time. The unit list is a pure
   function of the saved plot, so a restart resumes at the saved step. A unit waits while its chunk is still
   loading instead of loading it on the main thread.
4. **Chunks are held by a NeoForge `TicketController`** (`realmforge:instance_plots`, owner: the plot's origin,
   non-ticking) for every chunk a plot touches, from opening until it is free. The tickets survive restarts; the
   validation callback drops the tickets of plots that are free or unknown when the level loads.
5. **Cleanup: clear, then reuse.** When the last party member leaves, the plot's blocks are cleared over the next
   ticks (containers emptied first, so nothing drops; entities other than players removed), its chunks are released
   and the plot becomes free; new runs take the lowest free plot. Overwriting on reuse and never reusing stay
   selectable with `/qa instance cleanup` for measurements only.
6. **Players.** Entering stores the run and the return point in the `realmforge:instance` attachment (saved, copied
   on death, not synced). Leaving by command or exit, respawning outside the dimension or being teleported out all
   leave the run; a run whose party is empty is released. A disconnect keeps the run: on login a player inside the
   dimension stays if their run is still playable, and is sent back to their return point otherwise. A player who
   disconnected while their run was being built never entered it, and drops out of it when they log in again.
7. **GameTests run in the real dimension.** The vanilla GameTest server bakes its world with an empty dimension
   registry, so data pack dimensions are missing there; a dev-only mixin in the QA mod (`GameTestServerMixin`) bakes
   it with the data pack dimensions, as a normal world does.

## Measurements

Dedicated dev server (`./gradlew runServer`, NeoForge 21.1.259, Java 21) in a cloud container, no players connected,
budget 5 ms per tick, with `/qa instance bench <runs> <rooms> <strategy>`. Runs alternate a straight and a turned
row of rooms, so a reused plot never gets the same blocks in the same places. Rooms: start 15×7×15, corridor 25×6×7,
hall 31×13×31 (pillars, five lanterns, a chest); 3 rooms hold 4,547 blocks, 9 rooms (start and four corridor-hall
pairs) about 16,000. Medians of 20 runs (3 rooms) or 12 runs (9 rooms) once the plot cell existed; maxima in
brackets. For these even counts the bench reported the upper of the two middle samples (it averages them since).

| | 3 rooms, clear | 3 rooms, overwrite | 3 rooms, never reuse | 9 rooms, clear | 9 rooms, overwrite |
| --- | --- | --- | --- | --- | --- |
| Open: tickets and synchronous chunk loads | 1.9-40.7 ms | 2.7 ms | 384 ms (1,023) | 78 ms | 74 ms |
| Build until players can enter | 16.8 ms in 3 ticks | 30.0 ms in 6 ticks | 26.7 ms in 5 ticks | 62.3 ms in 11 ticks | 116.6 ms in 20 ticks |
| of which clearing the last run | - | 13.6 ms | - | - | 51.6 ms |
| Start / corridor / hall | 1.6 / 1.1 / 13.6 ms | 1.5 / 1.2 / 14.1 ms | 2.1 / 1.5 / 23.3 ms | 1.9 / 1.5 / 15.9 ms | 1.5 / 1.1 / 15.4 ms |
| Longest unit | 2.4 ms (12.4) | 3.1 ms (16.3) | 3.9 ms (16.7) | 3.6 ms (11.4) | 4.0 ms (11.4) |
| Clearing after the run (nobody waits) | 12.7 ms in 3 ticks | - | - | 47.4 ms in 9 ticks | - |
| Server tick while working (idle: 0.3-0.4 ms) | 6.4 ms (18.2) | 6.2 ms (19.4) | 15.4 ms (591) | 7.3 ms (26.7) | 7.2 ms (25.7) |
| Disk growth per run | < 1 KiB | < 1 KiB | 3.0 MB | < 1 KiB | < 1 KiB |

- **First use of a plot cell** costs 3-4 MB of region files and 0.3-1 s of chunk generation on the main thread:
  a forced chunk makes vanilla generate its neighbours to the full generation radius, and all of them are saved
  (1,038 empty chunks around the first plot). With reuse, the number of cells is the peak number of runs at once.
- **Cold starts:** the first placement of a template after a server start loads and parses it, and the code is not
  yet compiled: the first unit took up to 54 ms; a 9-room build resumed after a restart took 303 ms over 26 ticks,
  longest unit 41 ms. Before units waited for their chunks, the first units after a restart blocked on chunk loads
  for up to 282 ms each.
- **Tick cost** is our own measurement, not spark: per-unit `System.nanoTime` in the builder, and the server's
  tick-time ring buffer (`MinecraftServer#getTickTimesNanos`, the source of `/tick query`) plus NeoForge's
  per-dimension tick times, read at the end of each tick. Spark reads the same tick; a spark profile adds call
  trees, which the budget already bounds.
- **Restart:** stopped mid-build (saved at step 7, and at step 1), the build resumed at the saved step and the plot
  became ready; stopped right after a release, clearing resumed and the plot became free; with the plot data
  deleted, the validation callback dropped the orphaned tickets on load.
- **Disconnect** (GameTest `reconnectKeepsALiveRunAndLeavesAGoneOne`, through the real save and login path): a
  player who reconnects is still inside a live run; once the run is gone, the next login sends them back to where
  they entered, without the attachment.

## Consequences

- Building an instance is fast enough to happen while the player waits at the entrance: 3 to 20 ticks for 3 to 9
  rooms, at about 6-7 ms of server tick per working tick. Clearing after the run costs the same again, when nobody
  waits for it; overwriting on reuse doubles the wait for the next run, and never reusing grows the world by about
  3 MB and stalls the server for 0.4-1 s per run.
- Opening a plot blocks the main thread while NeoForge loads its chunks (`TicketController#forceChunk` calls
  `ServerLevel#getChunk`): 2-80 ms for a reused plot, 0.3-1 s the first time a cell is used. W4 should keep this
  off the entry path: keep a few cleared plots ready, or load a plot's chunks asynchronously before holding them.
- W4 should load room templates when the server starts, so the first run does not pay for parsing them.
- The largest room dominates: the hall takes about 80 % of a 3-room build, and one chunk column of it up to about
  4 ms. Rooms much taller than 13 blocks may need units cut along y as well.
- A plot reaches 256 blocks from its origin; layouts larger than about 512 by 512 blocks need a wider grid.
- Party members who stay offline keep their run forever; W4 adds a timeout (an operator setting, architecture 19.2).
- If `realmforge_instances.dat` is lost, the tickets are dropped but the blocks of plots that were in use stay in
  the world, and a later run in such a plot is built over them.
- The GameTest mixin targets a compiler-generated lambda (`GameTestServer#lambda$create$1`); a Minecraft update can
  rename it, and the instance GameTests then fail with a clear message.
