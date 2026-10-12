# 0023. Texture generator: Retro Diffusion Fast, cleaned to the project palette

Date: 2026-10-12

Status: Accepted (settles spike 5, RLM-16; refines [0007](0007-asset-pipeline.md))

## Context

[ADR 0007](0007-asset-pipeline.md) made Retro Diffusion the primary texture generator and PixelLab the fallback,
before anyone had compared them, and left that comparison to spike 5. The spike generated the same 10 textures in
both through their MCP servers (a sword, a staff, a potion, two spell icons, two tileable blocks, a gem, a
chestplate and a mob face), cleaned them with the new pipeline step and put them into one review gallery. Method,
the full comparison and the gallery command: [art/spike5/README.md](../../art/spike5/README.md). In short:

- **Retro Diffusion Fast** (`rd_fast__mc_item`, `rd_fast__mc_texture`): soft shading without black outlines,
  close to vanilla; its blocks tile without seams (`tile_x`, `tile_y`). 0.03 USD per image on prepaid credit,
  synchronous, parallel calls work.
- **Retro Diffusion Plus** (twice the price) followed the prompts worse than Fast on all three subjects tried.
- **PixelLab** (Pixen, and 64 Pro candidates for the sword): thick pure-black outlines and a saturated look, closer
  to a generic pixel-art game than to Minecraft; no tiling option for blocks.

Matěj reviewed the gallery on 2026-10-12 and picked Retro Diffusion Fast for all 10 textures, and preferred the
cleaned version over the raw one: "Retro Diffusion Fast is 100% superior. Also Cleaned > Generated. Retro Diffusion
Plus looks weird and PixelLab doesn't look like it's for Minecraft".

## Decision

- **Retro Diffusion Fast is the texture generator** for items, gear, spell icons, blocks and flat textures:
  `rd_fast__mc_item` for items and icons, `rd_fast__mc_texture` with `tile_x` and `tile_y` for blocks. Generate at
  the target size (16x16).
- **Every texture is cleaned** with `uv run rf art clean` (grid, alpha, the project palette in
  `art/palette/realmforge.gpl`) and reviewed in the gallery (`uv run rf art gallery`) before content uses it. The
  cleaned texture, not the raw output, goes into the game.
- **PixelLab is dropped**, also as the fallback ADR 0007 named. Retro Diffusion's Plus and Pro tiers are not used.
- **Entity textures** stay with ADR 0007 (model first, a texture per face, Matěj's eye); the spike tried only one
  face.

The rest of ADR 0007 stands: no vanilla textures or derivatives, not even as input; Matěj reviews all visuals; the
budget estimate.

## Consequences

- One generator account: prepaid Retro Diffusion credit. The free credit is spent (0.02 USD left), so Matěj tops it
  up before the next texture batch (A1); no PixelLab subscription is needed.
- A weak result (the spike's Frostbolt icon) is regenerated with a better prompt or another seed, not taken from a
  second generator.
- The project palette is a living file: a new ramp is added when cleanup keeps pulling a material's colors too far.
