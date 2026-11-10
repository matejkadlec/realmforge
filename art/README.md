# art/

Sources for assets: the project palette, generator output, prompts, Blockbench projects (`.bbmodel`) and raw
images before they are processed into game textures. Nothing here is loaded by the game directly; the pipeline in
`tools/` turns approved sources into files under `mod/src/generated/`.

| Path | What |
| --- | --- |
| `branding/` | the mods' logos at full size (1254 × 1254, transparent), not game textures: no palette, no grid. Each mod ships a 256 × 256 Lanczos copy at its resources' root, which `logoFile` names (RLM-414) |
| `palette/realmforge.gpl` | the project palette (GIMP palette; Aseprite and LibreSprite read it too). Every texture is snapped to it |
| `rooms/` | the import rules of the third-party dungeon rooms in `content/rooms/third_party/`, one folder per source, and where each source comes from ([README](rooms/README.md)) |
| `spell-icons/` | A1's first batch (RLM-588, RLM-591, RLM-609, RLM-642): the candidates of every M1 player spell's icon, the Frozen and Bleeding effect icons and the spellbook's two tiles, with their prompts and picks in `candidates/gallery.yaml` |
| `spike5/` | spike 5: the same 10 textures from Retro Diffusion and PixelLab, and their review gallery ([README](spike5/README.md)) |
| `spike-guide-ui/` | guide UI spike: window frames (two RD Fast rounds and an RD Pro version) and category icons from Retro Diffusion, nine-slice previews ([README](spike-guide-ui/README.md)) |
| `textures/` | **approved art:** the cleaned texture of each picked candidate, `<kind>/<id>.png`, which `rf gen` writes in place of the generated placeholder (step 4 below) |

Texture pipeline ([ADR 0007](../docs/decisions/0007-asset-pipeline.md),
[ADR 0023](../docs/decisions/0023-texture-generator.md)):

1. Generate candidates with Retro Diffusion Fast through its MCP server (set up once per machine:
   [dev setup](../docs/dev-setup.md#5-claude-code), "Retro Diffusion (MCP)"; cloud sessions lack it) and save them
   unchanged as `<batch>/<subject>/<candidate>.png`, with labels and prompts in `<batch>/gallery.yaml`.
2. `uv run rf art gallery <batch>` builds the review gallery (`.cache/art/`): every candidate before and after
   cleanup, tiled when it is opaque. Matěj picks. A subject's `pick` in `gallery.yaml` names the candidate that is
   in the game, which the gallery marks; its `size` is its texture's width when that is not the gallery's 16.
3. `uv run rf art clean <candidate>.png <texture>.png` produces the texture: reduced to the 16x16 grid, alpha
   hardened, colors snapped to the palette.
4. **Install it:** clean the pick straight into `art/textures/<kind>/<id>.png` and run `uv run rf gen`, which from
   then on writes it instead of the placeholder (RLM-642, `tools/realmforge_tools/art/approved.py`):

   | Kind | File | Becomes |
   | --- | --- | --- |
   | `item` | `art/textures/item/<item id>.png`, 16 x 16 | the item's texture |
   | `spell` | `art/textures/spell/<spell id>.png`, 16 x 16 | the spell's icon in the spell bar, the spellbook, its tooltip and the cast bar (the GUI sprite `realmforge:spell/<id>`); a spell without one shows its initials |
   | `effect` | `art/textures/effect/<effect id>.png`, 18 x 18, or 16 x 16 centred on it | the status effect's icon |
   | `gui` | `art/textures/gui/<part>.png` | a tile of a GUI texture a generator composes: `spellbook_page` (64 x 64) and `spellbook_cover` (16 x 16) for the spellbook's book (`generate/spellbook.py`) |

   `uv run rf validate` refuses a file there that names no content, has another size, or that the cleanup step did
   not write (half-transparent pixels, colours outside the palette). A texture touched up by hand must still be in
   palette colours. To change a pick, clean another candidate over the file, update `pick`, and run `rf gen`.

While Matěj's review of a batch is deferred (his decision of 2026-11-10), an agent makes the picks, records them in
`gallery.yaml`, and the deferred playtest ticket asks him to review the gallery.

Our font's symbols (the mob level's skull, RLM-340) are not generator output: they are pixel rows in
`tools/realmforge_tools/generate/fonts.py`, on the default Minecraft font's 8x8 grid in palette colours, and `rf gen`
writes the font and its texture. A few status effect icons are drawn the same way (Battle Shout's shouting head and Ice
Barrier's ice shield, Matěj, 2026-10-30): 18x18 pixel rows in `tools/realmforge_tools/generate/effect_icons.py`,
which `rf gen` writes instead of the effect's placeholder. Small GUI icons in the vanilla GUI style (the spellbook's
gear, RLM-497) are pixel rows too, in `tools/realmforge_tools/generate/gui_icons.py`, which `rf gen` writes as sprites
under `textures/gui/sprites/icon/`. So are the four spell school icons (`generate/school_icons.py`, RLM-642): they
stand before a number in a tooltip as a glyph 8 pixels tall, with the last column and row empty, too small for the
generator.

Until a piece of content has approved art, its texture is a generated placeholder
(`tools/realmforge_tools/generate/textures.py`). Never use vanilla textures or recolors of them, not even as AI
input.
