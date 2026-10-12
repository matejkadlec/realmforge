# 0022. Texture generators: Retro Diffusion for blocks, PixelLab Pro for items and icons

Date: 2026-10-12

Status: Proposed, waiting for Matěj's pick from the spike 5 gallery (RLM-16); refines
[0007](0007-asset-pipeline.md)

## Context

[ADR 0007](0007-asset-pipeline.md) made Retro Diffusion the primary texture generator and PixelLab the fallback,
before anyone had compared them, and left that comparison to spike 5. The spike generated the same 10 textures in
both through their MCP servers (a sword, a staff, a potion, two spell icons, two tileable blocks, a gem, a
chestplate and a mob face), cleaned them with the new pipeline step and put them into one review gallery. Method,
the full comparison and the gallery command: [art/spike5/README.md](../../art/spike5/README.md). In short:

- **Blocks:** Retro Diffusion's `mc_texture` style has a seamless tiling option, and its bricks and ore read well
  and tile cleanly. PixelLab Pixen has no tiling option; its blocks were noisy and showed seams.
- **Items and icons:** both were uneven at one image per prompt. Retro Diffusion Fast gave the most vanilla-like
  shading (no black outlines) but an unreadable Frostbolt; Pixen gave a good potion, ruby and Frostbolt with thick
  black outlines, but misread the staff and the chestplate. PixelLab Pro returned 64 sword candidates for one call,
  all usable and several the best swords of the spike.
- **Tiers:** Retro Diffusion Plus (twice the price) followed the prompts worse than Fast on all three subjects.
- **Cost and throughput:** Retro Diffusion Fast is 0.03 USD per image on prepaid credit, synchronous, and takes
  parallel calls. PixelLab needs its subscription for volume (12 USD per month for 2,000 generations,
  [research notes](../research-notes.md#image-generators)); Pro costs 10 generations per 64 candidates at 16x16.
  The free plan runs one job at a time.
- **Palette:** cleanup to the project palette worked for both; PixelLab's pure blacks and saturated colors move
  further. The spike also showed which ramps the first palette lacked.

## Decision

- **Blocks and other flat, tileable textures:** Retro Diffusion, `rd_fast__mc_texture` with `tile_x` and `tile_y`.
- **Items, gear and spell icons:** PixelLab Pro, which returns many candidates per call for the agent to
  shortlist; Retro Diffusion Fast (`rd_fast__mc_item`) as the second source when no candidate fits.
- **Entity textures:** stay with ADR 0007 (model first, a texture per face, Matěj's eye). Neither generator
  understands UV layouts, and one face per prompt is all the spike tried.
- **Every texture** goes through `uv run rf art clean` (grid, alpha, the project palette in
  `art/palette/realmforge.gpl`) and the review gallery (`uv run rf art gallery`) before content uses it.
- Retro Diffusion Plus and Pro tiers are not used by default.

The rest of ADR 0007 stands: no vanilla textures or derivatives, not even as input; Matěj reviews all visuals; the
budget estimate.

## Consequences

- Two accounts stay in use: prepaid Retro Diffusion credit, and a PixelLab subscription once item and icon work
  (A1) starts. The PixelLab trial (40 generations plus 10 a day) covers small batches until then.
- An agent shortlists PixelLab Pro candidates before Matěj sees them, so the gallery stays short.
- PixelLab's black outlines become near-black in cleanup. If they look harsh in the game, a later pipeline step
  can recolor outlines from their neighbors.
- The project palette is a living file: a new ramp is added when cleanup keeps pulling a material's colors too far.
