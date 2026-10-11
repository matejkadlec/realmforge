# 0012. Procedural dungeons and dimensions

Date: 2026-10-11

Status: Accepted

## Context

The vision wants 100+ structures and 5-10+ dimensions, procedurally generated: one dungeon type yields practically unlimited variants under the same rules, many dungeons instanced (like Hytale), and not every dungeon ends with a boss. Technical limits on 1.21.1 and the options: [research notes, section 9](../research-notes.md#9-procedural-generation).

## Decision

- **Procedural dungeons and dimensions from v0.1.**
- **A dungeon type is a rule set, not a map:** theme palette, room library, graph shape (start, corridors, special rooms, end), counts, weights, difficulty. Each run has its own seed. Hand-built rooms live in the room library; the generator combines them.
- **World structures** use vanilla jigsaw plus Lithostitched (depth 20 in vanilla, 128 with Lithostitched; reach about 128 blocks from the centre).
- **Instanced dungeons** are laid out by our own Java generator into plots inside one instance dimension (like Compact Machines and Mine Cells). **No runtime dimension creation on 1.21.1** (Infiniverse has an unfixed crash on unload).
- **Not every dungeon ends with a boss.** The ending is a rule of the type; archetypes include boss lair, treasury, puzzle temple, wave arena, escape or chase, mounted run, ruin exploration.
- **Dimensions are data-driven worldgen** (noise settings, density functions, biomes, features) written as JSON in `content/worldgen/`, the exception to YAML ([0005](0005-content-pipeline-yaml-single-source-of-truth.md)). A custom Java chunk generator only as a last resort.
- **Procedural quests come later as contracts** in our own system, alongside the hand-written campaign ([0006](0006-quests-ftb-quests-generated-from-data.md)).
- **"Full No Man's Sky" (procedurally generated rules for dimensions, mobs, bosses) is out of scope**; possibly a separate mod later. Today's data-driven generators are its first step.

## Consequences

- The instance dimension needs plot allocation and cleanup after players leave (technical spike 3).
- Dungeon types are datapack registries: they are loaded and validated on world load, and `/reload` does not reload them (tuning needs a world restart or a reload listener).
- Variety must still feel distinct, not "oatmeal"; room quality depends on hand-built rooms reviewed by the owner.
- Whether the Nether and End stay, change or go is a separate open question.
