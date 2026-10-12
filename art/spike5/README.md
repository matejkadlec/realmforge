# Spike 5: Retro Diffusion vs PixelLab

Spike 5 (RLM-16) generated the same 10 textures with both generators, cleaned them with the first texture pipeline
step and put them in one review gallery.

**Outcome (2026-10-12):** Matěj picked Retro Diffusion Fast for all 10 textures and preferred the cleaned version
of each: Retro Diffusion Plus "looks weird", and PixelLab "doesn't look like it's for Minecraft". Retro Diffusion
Fast, cleaned to the project palette, is the texture generator
([ADR 0022](../../docs/decisions/0022-texture-generators.md)). The PixelLab and Plus candidates stay here as the
spike's evidence.

```bash
uv run rf art gallery art/spike5/candidates      # writes .cache/art/candidates-gallery.html
```

- `candidates/<subject>/<candidate>.png`: the generators' output as returned, 16x16, never edited.
- `candidates/gallery.yaml`: labels, prompts and costs for the gallery.
- The project palette the cleanup snaps to: [`../palette/realmforge.gpl`](../palette/realmforge.gpl).

## Method (2026-10-12)

- **Subjects:** a sword, a staff, a potion, two spell icons (Frostbolt, Shadow Bolt), two tileable blocks (dungeon
  bricks, an ore), a gem, an armor icon and a mob texture (a ghoul's face, the front of a head). One prompt per
  subject, the same seed per subject (1601 to 1610), 16x16, through the generators' MCP servers.
- **Retro Diffusion:** `rd_fast__mc_item` for items and icons, `rd_fast__mc_texture` for the blocks (with
  `tile_x` and `tile_y`) and the face. Three subjects also in `rd_plus__*` to compare the tiers.
- **PixelLab:** `create_image_pixen` (1 generation) for all 10, the same prompt plus "Minecraft item style",
  "Minecraft block style, seamless tileable" or "Minecraft mob face texture"; `no_background` for items and icons.
  One `create_image_pro` call for the sword: 64 candidates, of which the agent shortlisted 4 (indexes 0, 14, 24 and
  39 of the job).
- **Cleanup:** `uv run rf art clean`, the same for every candidate. The gallery shows each candidate before and
  after it, and a 3x3 tiling for every opaque texture.

## Results

| | Retro Diffusion Fast | Retro Diffusion Plus | PixelLab Pixen | PixelLab Pro |
| --- | --- | --- | --- | --- |
| Look | soft shading, dark edges instead of black outlines, close to vanilla | sharper, but ignored parts of the prompts | thick pure-black outlines, saturated, busier | clean, vanilla-like items with crisp outlines |
| Items and icons | sword, staff, ruby, chestplate good; Frostbolt an unclear blob | sword with a red tip, Frostbolt became a spear | potion, ruby, Frostbolt good; staff a spoon, chestplate unreadable | sword: all 64 usable, several excellent |
| Blocks | bricks and ore read well and tile without seams | "bricks" were stone with green gems | noisy, dark, visible seams (no tiling option) | not tried |
| Mob face | readable but grey; prompt colors lost | not tried | strong face, very saturated | not tried |
| Cost | 0.03 USD per image (prepaid credit) | 0.06 USD per image | 1 generation; trial 40 free plus 10 a day | 10 generations for 64 candidates at 16x16 |
| Speed | about 7-25 s, synchronous, parallel calls work | same | 18-95 s each; the free plan runs one job at a time | under 4 min for the 64 |
| Spent | 0.48 of 0.50 USD free credit (13 images) | | 10 of 40 trial generations | 10 generations |

Palette cleanup: Retro Diffusion's colors moved little (mean OKLab distance 0.02 to 0.04 per pixel); PixelLab's
pure blacks and saturated purples moved most (0.02 to 0.08). Measuring the first version of the palette against
these candidates showed missing saturated reds, purples, greens and blue-grey steel tones; the palette gained four
ramps (ruby, arcane, venom, steel) before the gallery was built.

Practical notes for the pipeline:

- Retro Diffusion returns signed download URLs that expire after about 15 minutes: download right away.
- PixelLab jobs are asynchronous; poll with `wait_for_jobs`, download from
  `https://api.pixellab.ai/mcp/images/<job id>/download`.
- Both generators returned exactly 16x16, so the grid reduction in `rf art clean` had nothing to do here. It
  matters for generators and editors that return upscaled images.
