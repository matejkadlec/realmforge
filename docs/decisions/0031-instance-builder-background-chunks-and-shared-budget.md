# 0031. Instance builder: background chunk loads, a shared budget, clear-only cleanup

Date: 2026-10-15

Status: Accepted (amends [0019](0019-dungeon-instances-in-plots.md))

## Context

[0019](0019-dungeon-instances-in-plots.md) records spike 3's instance machinery and left W4 to act on what the spike
measured. Opening a plot holds its chunks with `TicketController#forceChunk`, which loads a chunk that is not loaded
yet on the main thread: 2-80 ms for a reused plot, 0.3-1 s the first time a plot cell is used. The first placement of
a room template parses it and upgrades it from its data version: up to 54 ms. The reviews of the spike (recorded on
RLM-34) found three more problems:

- the builder's budget goes to the first plot with work, so plots opened together build one after another;
- a resumed build whose room template no longer resolves throws on every tick and crash-loops the server;
- the cleanup strategies kept for measurements (overwrite on reuse, never reuse) ship in the core mod.

W4b (RLM-179) makes building and entering ready for real runs. 0019's decision points 3 to 5 describe the spike's
behaviour, and this changes it. 0019's consequences already asked for the background loading and the templates
loaded at start.

## Decision

This amends 0019's points 3, 4 and 5; the rest of 0019 stands.

1. **Chunks load in the background before they are held** (point 4). A plot still holds every chunk it touches with
   the ticket controller `realmforge:instance_plots`, from opening until it is free, but only once the chunk has
   loaded. Until then it asks for the chunk with a loading ticket of its own (`realmforge:instance_loading`: loading
   only, renewed every tick, gone 20 ticks after the chunk loaded, not saved). Holding never loads a chunk on the main
   thread, and a unit waits for its chunks as before. A plot's chunks are those of its rooms with the cleanup margin.
2. **The builder's budget is shared** (point 3). Each tick, every plot being built runs one unit whatever the budget.
   Then the rest of the 5 ms goes round the plots with work, one unit at a time, from the plot after the one served
   last. A unit that throws releases a plot being built, or is skipped by a closing one, and is logged.
3. **Clear then reuse is the only cleanup** (point 5). Overwriting on reuse, never reusing and `/qa instance cleanup`
   are gone from the code; their numbers stay in 0019's measurements. Cleanup clears 4 blocks around each room, and
   the rooms of a dungeon's pool keep fluids off their edges, so nothing a run leaves outside the rooms survives into
   the next one.
4. **Room templates load when the server starts**, and again after a reload: every room of every dungeon type.

## Consequences

- Entering a dungeon never stalls the server on chunk loads. A new plot cell still takes its chunk generation (wall
  time, seconds when many cells are new at once) before the build starts; the player waits at the entrance for it,
  which W4d's portal has to show.
- Server start takes longer by the template loads: the test dungeon's 23 imported rooms took 2.5-4 s in the GameTest
  server, most of it upgrading old data versions. W6's dungeon types add more. If that grows too much, the pipeline
  can store the rooms at the game's data version.
- With many builds at once, a tick can run past 5 ms, by one unit per plot being built; in M1 runs are solo.
- The GameTest server runs its ticks back to back, which background loading cannot keep up with: there the QA mod
  loads the chunks plots wait for before each tick (`GameTestChunkLoads`).
- Comparing cleanup strategies again needs the spike's code from git history.
