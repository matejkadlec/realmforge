# art/

Sources for assets: the project palette, generator output, prompts, Blockbench projects (`.bbmodel`) and raw
images before they are processed into game textures. Nothing here is loaded by the game directly; the pipeline in
`tools/` turns approved sources into files under `mod/src/generated/`.

| Path | What |
| --- | --- |
| `branding/` | the mods' logos at full size (1254 × 1254, transparent), not game textures: no palette, no grid. Each mod ships a 256 × 256 Lanczos copy at its resources' root, which `logoFile` names (RLM-414) |
| `models/spell/` | the spell models of shape `cubes` (`content/spell_models/`): `<id>.geo.json`, Bedrock geometry from Blockbench or a script, which `rf gen` turns into the game's GeckoLib model (spell models, below) |
| `palette/realmforge.gpl` | the project palette (GIMP palette; Aseprite and LibreSprite read it too). Every texture is snapped to it |
| `realm-portal/` | the realm portal's batch (RLM-300, RLM-301): the candidates of the Gatewright Bricks' tile, with their prompts and the pick in `candidates/gallery.yaml`. Every look of the frame is made from that one tile (below); the portal's animated textures and the sealstone's socket are drawn by the pipeline |
| `robes/` | the caster robes' batch (RLM-505): the candidates of the four sets' item icons and of each set's cloth and trim tile, with their prompts and picks in `candidates/gallery.yaml`. Only the Arcane Silk icons are picks: the other three sets' icons are scripted from them (`textures/variants.yaml`), and their own candidates are kept as the alternative |
| `rooms/` | the import rules of the third-party dungeon rooms in `content/rooms/third_party/`, one folder per source, and where each source comes from ([README](rooms/README.md)) |
| `sounds/` | **our sound files:** each pick converted to mono OGG Vorbis, `<subject>/<name>.ogg`, which `rf gen` copies into the jar and a sound event of `content/sounds/` plays (`file: <subject>/<name>`), and `sources.yaml`, where every file comes from: the candidate and its prompt, or a third-party sound's author, URL and licence (our credits). The candidates themselves are in the sound store, outside the repository (Sound pipeline, below) |
| `spell-icons/` | A1's first batch (RLM-588, RLM-591, RLM-609, RLM-642): the candidates of every M1 player spell's icon, the Frozen and Bleeding effect icons and the spellbook's two tiles, with their prompts and picks in `candidates/gallery.yaml` |
| `spike5/` | spike 5: the same 10 textures from Retro Diffusion and PixelLab, and their review gallery ([README](spike5/README.md)) |
| `spike-guide-ui/` | guide UI spike: window frames (two RD Fast rounds and an RD Pro version) and category icons from Retro Diffusion, nine-slice previews ([README](spike-guide-ui/README.md)) |
| `textures/` | **approved art:** the cleaned texture of each picked candidate, `<kind>/<id>.png`, which `rf gen` writes in place of the generated placeholder, and `variants.yaml`, the item textures scripted from another one (step 4 below) |

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
   | `armor` | `art/textures/armor/<look>.png`, 16 x 16, tileable | the cloth of an armor look (an item's `gear.look`): the pipeline composes the look's two worn textures from it (`generate/worn_armor.py`, below); a look without one keeps its woven placeholder |
   | `armor_trim` | `art/textures/armor_trim/<look>.png`, 16 x 16, tileable | the look's trim, for the bands of its worn textures; it needs the cloth, and a look without one has its bands in darker cloth |
   | `block` | `art/textures/block/<block id>.png`, 16 x 16, tileable | the tile of a block whose kind takes one: a `simple` block's texture on every side, and for the Gatewright Bricks the tile every look of the realm portal's frame is made from (below); a block without one keeps its speckled placeholder |

   `uv run rf validate` refuses a file there that names no content, has another size, or that the cleanup step did
   not write (half-transparent pixels, colours outside the palette). A texture touched up by hand must still be in
   palette colours. To change a pick, clean another candidate over the file, update `pick`, and run `rf gen`.

   **Variants are scripted from one base** ([ADR 0007](../docs/decisions/0007-asset-pipeline.md): rarities, gem
   colours, armor tiers), not generated one by one: `art/textures/variants.yaml` names an item whose texture is
   another item's, recoloured (RLM-505, `tools/realmforge_tools/art/variants.py`). Each `recolor` group takes the
   base's pixels in its `from` ramps of the palette (a role of the picture: its cloth, its trim, its gem) and gives
   them its `to` colours, a ramp's name or palette colour names from dark to light, by lightness. Nothing is stored:
   `rf gen` computes the variant from the base's file, so it follows the base and the palette. A variant has no file
   of its own, and validation refuses one whose item, base, ramp or colour does not exist. The caster robes'
   Apprentice's, Spellweave and Netherite icons are the Arcane Silk icons recoloured.

   **The realm portal's frame is one tile in several looks** (RLM-301): the frame's blocks show a look, washed out
   while dark and the open realm's while lit, and Matěj wants the bricks themselves to stay the same. So only the
   Gatewright Bricks have art, and `tools/realmforge_tools/art/frame_looks.py` makes each look from it: the bricks are
   the tile with its colours mapped onto the look's ramp of the palette by lightness (the tile's darkest colour takes
   the ramp's first, its lightest the last), and the sealstone's top is those bricks with the signet's socket drawn
   in, empty or holding a stone in the realm's colour. A look's colours are palette names in that file (`washed`:
   the `faded` ramp; `stone`, Undervault's: the dark half of `stone`), which validation checks. A new look is an
   entry there and a name in `FRAME_LOOKS` (`schema/realm.py`), with its block state value in the mod. To check the
   looks, light a frame: `REALMFORGE_QA_PORTAL_SHOTS` ([dev setup](../docs/dev-setup.md#33-in-wsl-quick-checks)).

   **The realm portal's animations are drawn, not generated** (RLM-300): a portal block's texture is a strip of
   frames that has to join its neighbours on every side and loop in time, and the client tints it with the realm's
   colour, so `generate/portal_textures.py` computes the frames in the palette's light greys, see-through: the shared
   `swirl` and Undervault's `stream`. They have no file here.

   **Worn armor is composed, not painted** (RLM-505): vanilla's armor model reads two 64 x 32 textures in its own
   layout (`humanoid` for the helmet, the chestplate and the boots, `humanoid_leggings` for the leggings), which no
   generator paints. `generate/worn_armor.py` fills each face of the model from the look's cloth tile and draws the
   bands from its trim tile, as a robe: a cap with a band and a brim, robes with a collar, a belt, a seam and cuffs,
   pants with a hem, sandals with a strap and a sole. Its shadows are the cloth's own colour one or two steps down
   its palette ramp (the colours named `<ramp> <n>` in the palette), so the result stays in palette colours. To
   check a look, wear it: `REALMFORGE_QA_GEAR_SHOTS` ([dev setup](../docs/dev-setup.md#33-in-wsl-quick-checks))
   saves every look from the front and the back.

**A batch whose review Matěj deferred** ([ADR 0074](../docs/decisions/0074-an-agent-picks-the-art-of-a-batch-whose-review-matej-deferred.md):
the marathon runs of 2026-11-10, and a later batch only when he defers it himself): an agent makes the picks in step
2 and installs them. They are provisional: `gallery.yaml`'s intro says so and names the batch's "Playtest
(deferred)" ticket, which asks him to review the gallery. "Approved art" names the folder `art/textures/`, not his
review. The spell icon batch's ticket is RLM-648, the robes batch's RLM-652.

**Spell models** (RLM-640, [ADR 0075](../docs/decisions/0075-spell-models-are-drawn-cube-by-cube-and-fire-waits-for-dynamic-lights.md),
`tools/realmforge_tools/art/spell_models.py`): a projectile's model of cubes is `models/spell/<id>.geo.json` for the
entry `content/spell_models/<id>.yaml` with `shape: cubes`.

- **Make it** in Blockbench (a "GeckoLib Animated Model" or a Bedrock model; export the geometry as `.geo.json`), or
  write its cubes from a script: a jagged lump is geometry, not painting. The `chunk` was written by a script, 26
  cubes round an axis that taper to a point.
- **At most 64 cubes,** each side of each above 0. Rotate cubes freely; do not rotate groups (bones): the game draws
  the cubes, not the bones, and validation refuses a rotated bone.
- **No inflate:** a cube is drawn at its own size, so Blockbench's `inflate` on a cube or a group would be lost and
  the cube thinner in the game than in the editor. Validation refuses one; give the cube that size instead.
- **The front is -z,** Blockbench's north: a `forward` model flies with that end first.
- **No texture and no UVs:** `rf gen` drops the source's UVs and maps every face onto the look's 16 x 16 material
  pixel for pixel, each cube from another corner of the tile, so one model serves every look.
- **It forms from its heart:** while a cast forms it, its cubes appear in the order of their distance from the
  middle of the model's bounds, so build it round its middle. Its size in the game comes from the YAML's `scale`,
  not from its pixels.
- `uv run rf gen` writes `assets/realmforge/geckolib/models/spell/<id>.geo.json`; `uv run rf validate` refuses a
  `cubes` model without its source and a source no `cubes` model names. Look at it with the spell screenshots
  ([dev setup](../docs/dev-setup.md), `REALMFORGE_QA_SPELL_SHOTS`).

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

## Sound pipeline

Our own sounds ([ADR 0079](../docs/decisions/0079-our-sounds-come-from-elevenlabs-through-its-api-and-ship-as-committed-mono-ogg.md),
RLM-126; tools in `tools/realmforge_tools/sound/`). Until a sound event has a file of ours, it plays vanilla sounds
by reference (`content/sounds/`, [ADR 0007](../docs/decisions/0007-asset-pipeline.md)); never copy a vanilla sound.

1. **Generate candidates** into the **sound store**, `~/realmforge-sounds/` (or `REALMFORGE_SOUND_STORE`), which
   lives outside the repository and every worktree and is never committed. One event:

   ```bash
   uv run rf sound gen frostbolt impact --ticket RLM-445 --sound-event realmforge:spell.frostbolt.impact \
     --prompt "magic ice shattering on impact, crisp crystalline shards" --duration 1.2 --influence 0.5 --variants 4
   ```

   or a plan of several, which only makes what its prompts still lack, so it can be run again after a stop
   (`--dry-run` says what it would make and roughly cost):

   ```yaml
   # <store>/plans/frost.yaml
   ticket: RLM-590            # each entry may name its own
   entries:
     - subject: frostbolt     # the content id: a spell, mob, block, or a theme such as ui
       event: impact          # the event's name
       sound_event: realmforge:spell.frostbolt.impact
       note: Frostbolt hits its target and shatters
       prompt: magic ice shattering on impact, crisp crystalline shards, fantasy spell sound effect
       duration: 1.2          # seconds, 0.5-30 (left out: the model guesses)
       influence: 0.5         # prompt influence 0-1
       variants: 4
     - {subject: undervault, event: ambient, prompt: ..., duration: 20, loop: true, variants: 3}
     - {subject: astone, event: music, kind: music, prompt: ..., duration: 120, variants: 2}
   ```

   `uv run rf sound gen --plan <file>`. ElevenLabs needs `ELEVENLABS_API_KEY`
   ([dev setup](../docs/dev-setup.md#5-claude-code)). Every request reads the subscription before and after, records
   the real cost (the `character-cost` header; for music, the usage stats' growth across the request) in its sidecar and the store's ledger, prints the credits left and the reset
   date, and is refused before it is sent when it would leave fewer than `--reserve` (default 1,000).
   `uv run rf sound credits` shows the credits at any time. Nothing is generated unless the subscription is an active
   paid plan (free-plan output has no commercial licence; the Starter plan falls back to free at its last reset), and
   each sidecar records the tier and status (the store's first candidates, made on 2026-11-23 before the status was
   recorded, had it filled in once: ADR 0079, Consequences). Never buy
   credit, upgrade or turn on usage-based billing.
   Each candidate is `candidates/<subject>/<event>/<nn>.wav` with its sidecar `<nn>.yaml`: the prompt and
   parameters, the date, the source, the ticket and sound event it is for, what it cost, and its measurements.
2. **Review:** `uv run rf sound gallery` writes `<store>/gallery/index.html` (from Windows:
   `\\wsl.localhost\Ubuntu\home\<user>\realmforge-sounds\gallery\index.html`): every candidate with a player, grouped
   by ticket and event, best score first. The score (`rank.py`) is what can be checked without listening: no clipping
   in any channel, at least 60 % of the length asked for and not cut off at the end, little silence before the
   sound, a level above the noise, and a loop's seam. A candidate's buttons copy the command that picks it, swaps it
   in for the event's current pick, or rejects it. `uv run rf sound list [FILTER]` prints the same ranking, and
   `uv run rf sound measure` measures the store again after the checks change.
3. **Pick:** `uv run rf sound pick <subject>/<event>/<nn>` converts the candidate into
   `art/sounds/<subject>/<event>_<n>.ogg` (`--name` for another name, `--replace` to swap a pick): the silence before
   and after cut (never a loop's) with short fades, the loudness brought to -18 LUFS (a loop to -26, `--loudness` to
   change it), never past a sample peak of -1 dBFS, then mono Vorbis (libvorbis q5) through the pinned ffmpeg with
   the bitexact flags, so the same candidate gives the same bytes on every run. It refuses a candidate whose
   sidecar does not record an active paid plan. It records the source in
   `sources.yaml`, with who picked it (`--by`).
4. **Wire it:** name the file in the event, `{file: <subject>/<name>}` in `content/sounds/<subject>.yaml` (with
   `weight`, `volume`, `pitch`, `stream` for long sounds, `attenuation_distance` when it should carry farther than 16
   blocks), and run `uv run rf gen`, which copies it to `assets/realmforge/sounds/<subject>/<name>.ogg`.
   `uv run rf validate` refuses a file that is not mono Vorbis, has no source or that no event plays, and an event
   that names a file that does not exist.

Music (Eleven Music, `kind: music` in a plan or `rf sound gen --music`) comes as MP3 candidates in the same store;
its conversion and wiring come with the ticket that plays it (RLM-622). A batch whose playtest Matěj deferred picks
its sounds itself by the best score, as [ADR 0074](../docs/decisions/0074-an-agent-picks-the-art-of-a-batch-whose-review-matej-deferred.md)
says for art: the source's `picked_by` says it is provisional and names the batch's deferred ticket, which owes his
listening in the gallery. Candidates made for later tickets (the sound bank) wait in the store, and a comment on
each target ticket lists them, so no later session generates them again.

**The licence rule** ([ADR 0079](../docs/decisions/0079-our-sounds-come-from-elevenlabs-through-its-api-and-ship-as-committed-mono-ogg.md),
after [ADR 0067](../docs/decisions/0067-the-mod-is-all-rights-reserved-imported-rooms-included.md): the mod and its
assets are All Rights Reserved). We ship:

| Source | `license` in `sources.yaml` | Needs |
| --- | --- | --- |
| ElevenLabs output from the paid plan (Starter and up: commercial licence) | `elevenlabs-paid` | the candidate, prompt and model (`rf sound pick` writes them) |
| Stable Audio output (Stability AI Community License, free under 1M USD revenue) | `stability-community` | the candidate, prompt and model |
| CC0 sounds (Kenney, OpenGameArt CC0 packs, Freesound CC0) | `cc0` | the URL |
| CC BY 4.0 sounds (Freesound and others) | `cc-by-4.0` | the author and the URL: they are the credit we owe |

Never: ElevenLabs free-plan output (no commercial licence), CC BY-NC, CC BY-SA, GPL, Pixabay or Sonniss GDC sounds,
and never a copy of a vanilla sound. A third-party file is converted to mono OGG by hand (or picked from a store
candidate) and gets its `sources.yaml` entry written by hand.
