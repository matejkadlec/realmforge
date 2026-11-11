# 0077. A set piece's variants are data, drawn with the layout

Date: 2026-11-11

Status: Accepted (amends [0050](0050-set-pieces-hand-built-nbt-placed-at-dungeon-generation.md): decision 1, how a
piece's variants reach the game)

## Context

[0050](0050-set-pieces-hand-built-nbt-placed-at-dungeon-generation.md) decided that `rf gen` writes a set piece's
variants (blocks that are there only by chance, and swaps such as a barrel that becomes a chest) "as a vanilla
processor list", which the builder would apply when it places the piece. The review of that work
(matejkadlec/realmforge#106, round 1, tracked on RLM-282) found that this cannot give what the set-piece design asks
for, a new look on every run:

- Vanilla's `RuleProcessor#processBlock` seeds its random from the block's world position alone
  (`RandomSource.create(Mth.getSeed(pos))`) and ignores the random of the `StructurePlaceSettings`.
- `InstanceManager` reuses freed plots, lowest id first, so a solo player's runs stand on the same plot. A piece at
  the same world position would then show the same skull or none, and the same barrel or chest, on every run.

The finding left two ways open: a processor of our own that draws from the settings' random, or reading the variants
as data and applying them from the plot's seed. RLM-282, which places the pieces, had to choose.

## Decision

1. **Variants are data in the dungeon type entry.** `rf gen` writes no processor list for a set piece. The entry's
   `set_pieces.pieces.<id>.variants` lists every block of the template that varies: its position in the template, the
   chance that it changes, and the block state it then becomes (air for a block that is there only by chance; a
   swap's state, with the properties the swap keeps taken over from the block it replaces, worked out per block by
   `rf gen`). `generate/set_pieces.py` writes it; a piece without variants lists none.
2. **The planner draws them with the layout.** When a run is planned (`DungeonPlan#toInstanceLayout`), the set pieces
   are drawn from the layout's seed, from a stream of their own, like the containers a room keeps (RLM-365) and the
   side a room opens (RLM-412): which of a room's spot patterns are filled, the piece of each, and its variants. The
   result is saved with the layout as swaps (a position of the template and the state placed there instead), which
   the builder applies with the processor the rooms' swaps already use.
3. **One draw per pattern.** A pattern is filled with one piece on every spot (0050, ADR 0036), and its variants are
   drawn once for the pattern too: a mirrored pair of barrels both carry the skull or neither does, and both are
   barrels or both chests. Different patterns draw on their own.

## Consequences

- The same seed always gives the same pieces, on any plot, and a new seed new ones; JUnit checks it without a world.
- The builder needs no registry lookup and no processor of its own for variants, and a plot that resumes after a
  restart places exactly what it had drawn, since the draws are part of its saved layout.
- A swap no longer needs one rule per block state (the block-state pitfall of
  [architecture 15.2](../architecture.md#152-dungeon-types)): `rf gen` knows each block's state and writes its target.
- A piece's variants cost a few entries per piece in every dungeon type entry that places it.
- The dungeon's theme swaps (a processor list per theme, W6) are untouched: they still run as processors when rooms
  and pieces are placed.
- 0050's decision 1 keeps its accepted text; "as a vanilla processor list" is read with this ADR.
