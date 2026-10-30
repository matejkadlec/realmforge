# art/

Sources for assets: the project palette, generator output, prompts, Blockbench projects (`.bbmodel`) and raw
images before they are processed into game textures. Nothing here is loaded by the game directly; the pipeline in
`tools/` turns approved sources into files under `mod/src/generated/`.

| Path | What |
| --- | --- |
| `palette/realmforge.gpl` | the project palette (GIMP palette; Aseprite and LibreSprite read it too). Every texture is snapped to it |
| `rooms/` | the import rules of the third-party dungeon rooms in `content/rooms/third_party/`, one folder per source, and where each source comes from ([README](rooms/README.md)) |
| `spike5/` | spike 5: the same 10 textures from Retro Diffusion and PixelLab, and their review gallery ([README](spike5/README.md)) |
| `spike-guide-ui/` | guide UI spike: window frames (two RD Fast rounds and an RD Pro version) and category icons from Retro Diffusion, nine-slice previews ([README](spike-guide-ui/README.md)) |

Texture pipeline ([ADR 0007](../docs/decisions/0007-asset-pipeline.md),
[ADR 0023](../docs/decisions/0023-texture-generator.md)):

1. Generate candidates with Retro Diffusion Fast through its MCP server (set up once per machine:
   [dev setup](../docs/dev-setup.md#5-claude-code), "Retro Diffusion (MCP)"; cloud sessions lack it) and save them
   unchanged as `<batch>/<subject>/<candidate>.png`, with labels and prompts in `<batch>/gallery.yaml`.
2. `uv run rf art gallery <batch>` builds the review gallery (`.cache/art/`): every candidate before and after
   cleanup, tiled when it is opaque. Matěj picks.
3. `uv run rf art clean <candidate>.png <texture>.png` produces the texture: reduced to the 16x16 grid, alpha
   hardened, colors snapped to the palette.

Our font's symbols (the mob level's skull, RLM-340) are not generator output: they are pixel rows in
`tools/realmforge_tools/generate/fonts.py`, on the default Minecraft font's 8x8 grid in palette colours, and `rf gen`
writes the font and its texture. A few status effect icons are drawn the same way (Battle Shout's shouting man and Ice
Barrier's ice shield, Matěj, 2026-10-30): 18x18 pixel rows in `tools/realmforge_tools/generate/effect_icons.py`,
which `rf gen` writes instead of the effect's placeholder.

Until a piece of content has approved art, its texture is a generated placeholder
(`tools/realmforge_tools/generate/textures.py`). Never use vanilla textures or recolors of them, not even as AI
input.
