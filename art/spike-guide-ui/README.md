# Guide UI spike: art direction with Retro Diffusion

The guide UI spike (RLM-127, input for the guide decision RLM-116) asked whether Retro Diffusion Fast or Plus can
produce a usable pixel-art version of the guide's "modern fantasy hybrid" look before anything is built: a dark
navy/charcoal window, a thin gold ornate border with decorated corners, a gold crest centered on the top edge, and
small gold line icons. It decides only the generator style. RD Pro was not tried: Matěj decides on Pro only if both
Fast and Plus look bad. No ADR yet: Matěj picks from the shortlist below.

- `reference/mockup.png`: Matěj's mockup (generated with ChatGPT). `reference/corner-crop.png`: its top-left
  window corner, cropped at (192, 58), 136x136, composited on white, scaled to 256x256 (bilinear) and snapped to the
  project palette. It was the `input_image` for the two corner-guided frames.
- `candidates/<subject>/<candidate>.png`: the generator output exactly as returned (`frame/`, `icon_*/`), and the
  derived previews (`preview_nine_slice/`, `preview_stretched/`).
- `candidates/gallery.yaml`: labels, prompts, seeds and the cost of every image.
- `cleaned/icon_*.png`: the icons after `uv run rf art clean` (16x16, project palette).
- Review page: built from `gallery.yaml` with the `rf art gallery` styles and published for Matěj as a private
  artifact. `rf art gallery` itself cannot render this folder ([Pipeline notes](#pipeline-notes)).

## Method (2026-10-12)

- **Window frame:** one prompt for all six frames (in `gallery.yaml`), 256x256, `remove_bg` on, the style's
  automatic prompt expansion left on.
  - RD Fast UI Element, 2 candidates (seeds 2101, 2102). Its style id is `ui_element:rd_flux`; `rd_fast__ui` does
    not exist.
  - `rd_plus__ui_element`, 2 candidates (the same seeds).
  - Both styles once more with `reference/corner-crop.png` as `input_image`, strength 0.6, seed 2101.
- **Category icons:** `rd_fast__mc_item`, 16x16, seed 2101, one candidate each: realms (a swirling portal),
  dungeons (a dungeon key), bosses (a horned skull), basics (an open book), locked (a padlock). The prompts ask for
  thin gold lines, outline only; the style's automatic transparency handles the background.
- **Cleanup:** `uv run rf art clean` on the five icons only.
- **Previews** of the two frames picked below, both 400x240, with no generation cost:
  - **nine-slice:** how a 1.21.1 `nine_slice` GUI sprite cut from the frame would draw.
    `GuiGraphics#blitNineSlicedSprite` keeps the corners and tiles the whole region between the borders; it does
    not stretch them. So the preview first cuts such a sprite from the frame (cropped to its opaque bounding box):
    the four corners unchanged, one 8-pixel strip per edge and an 8x8 block from the panel's center as the regions
    between the borders, and the crest cut out to be drawn as a separate sprite centered on the top edge. Then it
    tiles that sprite to 400x240. The raw frame used as the sprite would tile its whole edge spans instead and
    repeat the crest, the studs next to the corners and, on RD Plus 2101, the panel's vignette. Border widths were
    guessed so the corners hold the corner ornaments: RD Fast 2102 40 on all sides; RD Plus 2101 left 36, top 44,
    right 36, bottom 36, with its strips taken 20 pixels from the corners because the first pixels after each
    corner hold a stud.
  - **stretched:** the cropped frame scaled to 400x240 with nearest-neighbour sampling.
- **Measurements** on each frame (opaque pixels): its bounding box; left/right mirror symmetry as the share of
  mirrored pixel pairs whose colors differ visibly (OKLab distance above 0.05); edge straightness as the spread of
  the outermost opaque column over the middle 60% of each side; the color spread in the central 40% of the panel.

## Results

| Frame | Look | Closeness to the mockup | Straight edges and symmetry | Cost |
| --- | --- | --- | --- | --- |
| RD Fast, seed 2101 | small landscape frame (155x137 of 256), bronze-gold band, crest on top and a matching ornament below, small diamonds on the sides, flat navy panel | medium: the right parts, but small and more brown than gold | sides straight (1 px spread); 12% of mirrored pairs differ | 0.03 USD |
| RD Fast, seed 2102 | square frame (202x190), bright gold double line, fleur-de-lis corners, small crest, flat navy panel | high for color and parts; fleur-de-lis corners instead of the mockup's pointed corners | sides straight (0-1 px); 18% differ: the left side has an outer gold line the right side lacks | 0.03 USD |
| RD Plus, seed 2101 | portrait frame (174x227), muted bronze band, ornate square corner pieces, tall crest, soft vignette in the panel | medium: the richest ornament, but bronze rather than gold, and the band is heavier than thin | the most symmetric: 1.6% differ; sides straight | 0.06 USD |
| RD Plus, seed 2102 | steel-grey band with gold corner studs and a gold crest, a lighter arch drawn inside the panel | low to medium: grey instead of gold; the arch would distort when stretched | sides straight (0 px); 20% differ | 0.06 USD |
| RD Fast, mockup corner as input | only a corner (the input was a corner), cut off at the canvas edge: a bevelled gold bar with a filigree spray into the panel | the closest in style: the mockup's corner in pixel art | not a frame; the bar itself is straight | 0.03 USD |
| RD Plus, mockup corner as input | a gold corner bracket with diamond studs; background removal took the dark panel too | motif close, no panel | not a frame | 0.06 USD |

- **Previews:** cut into such a sprite (plain edge strips, the crest drawn on its own), both picked frames tile
  cleanly at 400x240; tiling a raw frame's whole edge spans would repeat the crest and the studs. Stretching fails
  for both: it scales the frame unevenly (RD Fast 2102 by 1.98x across and 1.26x down), so corners and pixels
  change shape. A resizable guide window needs nine-slice.
- **Icons** (5 x 0.03 USD): `rd_fast__mc_item` ignores "thin gold lines, outline only" and draws filled, shaded
  Minecraft items. All five read at 16x16, but the portal is a gold ring rather than a swirl, the key came out dark
  iron instead of gold, and the skull is bone-white with gold horns. Cleanup reduces them to 11-15 colors and visibly
  hurts the skull (grey patches) and the book (yellow blotches): the palette has no bone or parchment tones.
- **All six frames** came back with hard alpha (no partly transparent pixels) and at their native 1x pixel grid.

**Total spent: 0.42 of the 0.50 USD budget**: 13 images, 0.27 USD for the six frames, 0.15 USD for the five icons.
The account balance went from 25.02 to 24.60 USD.

## Shortlist

1. **RD Fast, seed 2102:** the closest full frame to the brief: bright gold, decorated corners, a crest, a flat
   navy panel. Cut into a nine-slice sprite with a 40-pixel border and plain edge strips, it tiles cleanly. To fix:
   the left/right asymmetry (mirror one half in an editor, or regenerate), and the crest as its own sprite.
2. **RD Plus, seed 2101:** the most symmetric and most ornate frame, and a nine-slice sprite cut the same way tiles
   cleanly; but it is bronze rather than gold, its band is heavier than thin, and it costs twice as much.
3. **RD Fast with the mockup corner as input:** the closest to the mockup's style, but it returns only a corner. A
   frame would be that corner mirrored four ways plus tiled edges, at no extra generation cost; worth one follow-up
   if Matěj likes the look.

The agent's view: RD Fast is enough for the frame. Plus brought no clear gain at twice the price, so neither tier
looks bad enough to call for Pro. The icons need another approach: `rd_fast__mc_item` makes Minecraft items, not
line art.

## Pipeline notes

What `rf art clean` and `rf art gallery` would need for UI sprites larger than 16x16 (no pipeline change in this
spike):

- **A size per sprite.** `rf art clean --size` exists: `--size 256` keeps a 256x256 frame at scale 1 and only
  hardens alpha and snaps colors, while the default 16 would shrink it to a 16x16 smudge. `rf art gallery` cleans
  every candidate at one `--size`, so it refuses this folder (a 400x240 preview is not a whole multiple of 16) and
  would show the frames shrunk to 16x16. It needs a size per subject (in `gallery.yaml`) or a mode that keeps each
  image at its own width.
- **Grid detection.** The reduction factor comes from the target width. Retro Diffusion returned the frames at their
  native grid, so nothing had to be reduced; a generator that returns upscaled UI art would need the factor detected
  from the image instead.
- **Palette.** The RD Fast frames' navy panel (`#1c2943`) snaps to `stone 2` (`#2c2c32`), a neutral grey, so the
  navy is lost; the frames sit 0.02-0.05 (mean OKLab distance) from the palette. UI art needs a navy/charcoal ramp in
  `art/palette/realmforge.gpl` before it is cleaned, and the icons need bone and parchment tones.
- **Nine-slice metadata.** A 1.21.1 GUI sprite declares `gui.scaling` with type `nine_slice` (`width`, `height`,
  `border`) in its `.mcmeta` (`GuiSpriteScaling`). The pipeline would have to emit that from YAML, the sprite needs
  plain edge strips (they are tiled), and ornaments such as the crest become separate sprites.
