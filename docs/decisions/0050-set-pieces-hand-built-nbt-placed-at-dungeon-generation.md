# 0050. Set pieces: hand-built NBT, placed at dungeon generation

Date: 2026-10-23

Status: Accepted (amends [0005](0005-content-pipeline-yaml-single-source-of-truth.md) and decision 8 of
[0014](0014-core-mod-architecture.md))

## Context

The [set-piece design](../design/set-pieces.md) (RLM-175, approved by Matěj on 2026-10-19) dresses dungeon rooms with
small hand-built pieces: a niche with hay bales, a barrel stack, a skull on a block. Matěj builds them himself in a
few minutes each (RLM-283), so a piece is a structure template, not YAML, and where it goes depends on the room it
lands in.

Two accepted ADRs frame this. [0005](0005-content-pipeline-yaml-single-source-of-truth.md) makes YAML the single source
of truth, and decision 8 of [0014](0014-core-mod-architecture.md) named rooms the second exception, after worldgen
JSON. [0028](0028-dungeon-rooms-generated-or-imported.md) decided that nobody builds rooms by hand; pieces are not
rooms, they dress generated and imported rooms, and Matěj chose to build them. The review of the design's pull request
(matejkadlec/realmforge#92, round 1) asked for an ADR before the content type exists (RLM-280).

Matěj's answers of 2026-10-19 (the design's decisions) settle where pieces are placed: at dungeon generation, so every
run varies, with containers in pieces holding loot through Lootr
([0041](0041-dungeon-containers-hold-loot-through-lootr.md)).

## Decision

1. **Set pieces are the third exception to YAML-only content.** A piece is `content/set_pieces/<id>.nbt` (the
   template, built as its mask shows it: the wall side to the north) with its fit rule `<id>.yaml` (Matěj's mask
   notation, height, `builds_walls`, `walls`, `tags`, `roles`, `weight`, `variants`). `rf gen` validates both and
   writes the template normalized to `data/realmforge/structure/set_pieces/<id>.nbt` and its variants (chance blocks,
   swaps) as a vanilla processor list. `rf set-pieces import` brings a save of the workshop jig into the folder; as
   with rooms, nobody edits a template by hand.
2. **Spots are found at `rf gen`, pieces placed at generation.** A deterministic Python matcher
   (`tools/realmforge_tools/rooms/set_pieces.py`) finds every pooled room's spots for the pieces that reach its role,
   as patterns (ADR 0036), and writes them into the dungeon type entry next to the room's connectors, with the pieces
   that may fill each pattern and the default density per role. The game reads no fit rule: the builder (RLM-282)
   picks pieces for patterns from the plot's seed and places them like rooms. A new piece reaches every room at the
   next `rf gen`, and no room changes.
3. **A fit rule's walls are theme roles or block ids, not block tags.** A generated room is built of its theme's
   roles, so `wall` (the walls' base, body, patches and cornice) or any role names the blocks a piece may stand
   against; a block id names a block directly. The ID universe knows block tags by name only, not which blocks they
   hold, so the matcher cannot test one; an imported room, whose blocks have no roles, takes any full block for a role.

## Consequences

- `content/` holds three kinds of non-YAML content: worldgen JSON, rooms and set pieces. Each sits next to a YAML file
  that the pipeline validates, and CI checks that every generated file is current.
- The dungeon type entry grows with the pieces: every pooled room carries its patterns, so a piece change regenerates
  the entries of the types whose pools it fits. The game keeps planning without a template.
- A spot is valid for the room as it is in `content/`; a room that changes moves its spots at the next `rf gen`.
- Block tags in `walls` need the full-pack registry dump or vanilla's tag contents in the ID universe first; until
  then a piece names roles or blocks.
- 0005 and decision 8 of 0014 keep their accepted text; their "second exception" is read with this ADR.
