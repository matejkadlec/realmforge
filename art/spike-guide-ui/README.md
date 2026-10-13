# Guide UI spike: art direction with Retro Diffusion

The guide UI spike (RLM-127, input for the guide decision RLM-116) asked whether Retro Diffusion Fast or Plus can
produce a usable pixel-art version of the guide's "modern fantasy hybrid" look before anything is built: a dark
navy/charcoal window, a thin gold ornate border with decorated corners, a gold crest centered on the top edge, and
small gold line icons. It decides only the generator style. RD Pro was not tried: Matěj decides on Pro only if both
Fast and Plus look bad. No ADR yet.

**Round 1 outcome (Matěj, 2026-10-12):** the five RD Fast icons are fine. He rejected every frame: none looks close
to the mockup, the corners are too fancy, the border is too thick, the panel is royal blue where the mockup is far
darker, and the gold reads as bronze or even wood. The RD Fast frame guided by the mockup's corner came closest.
[Round 2](#round-2-rlm-131) (RLM-131) answers that with four RD Fast frames; Plus and Pro were not tried.

**Round 2 outcome (Matěj, 2026-10-13):** "go with d, make the pro version". The
[RD Pro version](#rd-pro-version-rlm-134) (RLM-134) holds two RD Pro frames made from d.

**Outcome (Matěj, 2026-10-13):** the RD Pro frames look worse than the RD Fast ones, and he likes c more than d:
"for now let's go with 'RD Fast, round 2 c'". Round 2's frame c (`candidates/frame_r2/rd-fast-c.png`) is the guide
frame for now. Its flaws, from round 2's results: the corners are filled brown wedges instead of the mockup's
filigree, and a light-blue line runs along the top edge.

- `reference/mockup.png`: Matěj's mockup (generated with ChatGPT). `reference/corner-crop.png`: its top-left
  window corner, cropped at (192, 58), 136x136, composited on white, scaled to 256x256 (bilinear) and snapped to the
  project palette. It was the `input_image` for the two corner-guided frames.
- `reference/frame-composite.png`, `reference/frame-palette.png`: round 2's `input_image` (a symmetric frame built
  from the mockup's own parts) and `input_palette` ([Round 2](#round-2-rlm-131)).
- `reference/frame-d-256.png`, `reference/frame-composite-256.png`: round 2's frame d and the composite at 256x184,
  the inputs of the RD Pro version ([RD Pro version](#rd-pro-version-rlm-134)).
- `candidates/<subject>/<candidate>.png`: the generator output exactly as returned (`frame/`, `frame_r2/`,
  `frame_pro/`, `icon_*/`), and the derived previews (`preview_nine_slice/`, `preview_stretched/`).
- `candidates/gallery.yaml`: labels, prompts, seeds and the cost of every image.
- `cleaned/icon_*.png`: the icons after `uv run rf art clean` (16x16, project palette).
- Review page: built from `gallery.yaml` with the `rf art gallery` styles and published for Matěj as a private
  artifact. `rf art gallery` itself cannot render this folder ([Pipeline notes](#pipeline-notes)).

## Method (round 1, 2026-10-12)

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

## Results (round 1)

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

## Round 1 shortlist

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

## Round 2 (RLM-131)

**Method (2026-10-13).** Every frame is RD Fast UI Element (`ui_element:rd_flux`) guided by one `input_image`, a
clean frame built from the mockup instead of a crop of it:

- **The composite** (`reference/frame-composite.png`) is assembled at the mockup's full size, whose window border
  runs x 213-1459, y 83-884. The left half is built from the mockup's own parts, then mirrored about x 836, so the
  close button, the sidebar's logo bump and every other one-sided detail are gone. The left half holds:
  - the top and bottom edges, tiled from the mockup's column at x 650; the left edge, from its row at y 650;
  - the top-left corner, moved up 11 pixels, because the mockup's sidebar edge sits 11 pixels below the content
    edge the crest is on. Past x 300 its top line dims, so the straight strip takes over there;
  - the bottom-left corner, the left diamond stud, and the left halves of the crest and the bottom ornament.
- **The panel** is the mockup's own `#0a1119`. Every other dark pixel that is neither gold nor the gem gets the
  same color, so the mockup's header and sidebar tones are gone.
- **The input** is the composite scaled (area average) to fit 384x272 on white and reduced to 64 colors. The frame
  takes up 367x264 of it, about 1.4:1 with the crest, and the border rectangle about 1.55:1. Its gold border is 3
  pixels wide.
- **The palette** (`reference/frame-palette.png`) has 17 colors: the panel navy, white (the background
  `remove_bg` takes out), and 15 median-cut colors of the composite's gold, gem and shadow pixels.
- **Four candidates**, 384x272, `remove_bg`, the same prompt (in `gallery.yaml`):
  - a: strength 0.45, seed 2201;
  - b: strength 0.6, seed 2201;
  - c: strength 0.6, seed 2202;
  - d: like b, plus the palette as `input_palette`.

**Results.** All four are horizontal frames (about 366x263 on the 384x272 canvas). Each has a 3-pixel border and
the mockup's near-black navy panel (`#0a1119` to `#0b121a`), and came back with hard alpha. They are close to
mirror-symmetric: 0.9-1.8% of mirrored pixel pairs differ, against 12-20% in round 1. The 4-pixel spread on the
sides is the diamond studs. All four rounded the mockup's 45-degree cut corners. Gold is the mean hue and saturation
of the gold pixels; the composite has 34 degrees and 0.67.

| Frame | Look | Closeness to the mockup | Symmetry | Gold | Cost |
| --- | --- | --- | --- | --- | --- |
| a (0.45) | thin gold line, rounded corners with one filigree curl, the crest close to the mockup's shape, side studs, the bottom ornament; the gem turned grey-white | close in shape; slightly bronze | 1.5% differ | 31 degrees, 0.60 | 0.03 USD |
| b (0.6) | like a, with a sharper crest; the gem is white | further: the most copper of the four | 0.9% differ | 27 degrees, 0.60 | 0.03 USD |
| c (0.6, other seed) | gold crest with a blue gem, but the corners became filled brown wedges, and a light-blue line runs along the top edge | medium: the crest is right, the corners are not | 1.5% differ | 34 degrees, 0.61 | 0.03 USD |
| d (0.6, mockup palette) | the brightest gold, the blue gem kept, fine filigree curls at the corners, the panel exactly `#0a1119` | the closest: color, gem and corner flourishes | 1.8% differ | 33 degrees, 0.70 | 0.03 USD |

**Agent's ranking:** d, a, c, b. d is the candidate for the RD Pro remake: the palette held the gold and the navy,
which round 1 lost. a has the shape closest to the input. Both need their corners cut back to 45 degrees if they
should match the mockup exactly; Pro could take the composite and the picked frame as its `reference_images`.

**Spent: 0.12 USD** (4 x 0.03). The balance went from 24.60 to 24.48 USD; both rounds together cost 0.54 USD.

## RD Pro version (RLM-134)

**Method (2026-10-13).** Matěj picked round 2's frame d for an RD Pro remake. RD Pro draws at most 256x256 and has
no UI frame style like RD Fast's, so the frame is 256x184 (d's proportions) and two RD Pro styles each made one
take:

- **The inputs** at 256x184 on white, scaled with area averaging and reduced to at most 64 colors:
  - `reference/frame-d-256.png`: frame d, cropped to its opaque box;
  - `reference/frame-composite-256.png`: round 2's composite.
- **rd-pro-edit:** `rd_pro__edit`, which reworks an `input_image`: d as the input, the composite as a
  `reference_images` entry, and a prompt to keep d's composition and redraw it crisp, with 45-degree cut corners.
- **rd-pro-default:** `rd_pro__default`, drawn fresh from `reference_images` (d and the composite) and a
  description of the frame.

Both used seed 2301 and `remove_bg`. The prompts are in `gallery.yaml`.

**Results.** Both are horizontal frames with mirror symmetry (2% of mirrored pixel pairs differ), straight sides, a
flat panel and hard alpha, drawn on their native 1x grid. Gold is the mean hue and saturation of the gold pixels, as
in round 2; d has 33 degrees and 0.70.

| Frame | Look | Closeness to the mockup | Size, border, panel | Gold | Cost |
| --- | --- | --- | --- | --- | --- |
| rd-pro-edit | d redrawn crisper: the same crest, studs and bottom ornament, cut corners with larger gold scrollwork inside them; the gem turned grey-white | d's layout kept; the corner scrollwork is busier than the mockup's sprays | 245x172 (d's proportions), 3 px, `#0a121a` (the mockup's navy) | 35 degrees, 0.64, value 0.65 | 0.18 USD |
| rd-pro-default | a clean, flat frame: sharp 45-degree cut corners with thin filigree in each corner, a smaller crest with an amber gem, small side studs and bottom ornament | the corners and the bright gold are the closest to the mockup in the whole spike; the crest and ornaments are simpler | 220x126 (about 1.75:1), 4 px, `#121421` (slightly lighter and more violet) | 40 degrees, 0.56, value 0.80 | 0.18 USD |

**Agent's view:** rd-pro-edit is the faithful RD Pro version of d. rd-pro-default comes closer to the mockup's cut
corners and bright gold, at the cost of a smaller crest and a slightly lighter panel. Either can still be cut into a
nine-slice sprite, with the crest drawn separately, as round 1's previews showed.

**Spent: 0.36 USD** (2 x 0.18). The balance went from 24.48 to 24.12 USD; the whole spike cost 0.90 USD.

**Outcome:** Matěj found both RD Pro frames worse than RD Fast and picked round 2's c for now
([above](#guide-ui-spike-art-direction-with-retro-diffusion)).

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
