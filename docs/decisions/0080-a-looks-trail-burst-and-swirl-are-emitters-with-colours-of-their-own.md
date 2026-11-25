# 0080. A look's trail, burst and swirl are emitters with colours of their own, and its material is art in art/textures/material/

Date: 2026-11-25

Status: Accepted (amends [0073](0073-spell-visuals-are-reusable-parts-in-data.md): decision 4, what tints a particle,
and decision 3's placeholder material)

## Context

[0073](0073-spell-visuals-are-reusable-parts-in-data.md) gave a look one particle for its trail, one for its burst
and (with RLM-640) one for the swirl round a forming projectile, each spawn "tinted from the look's palette", and a
material that is "a generated placeholder (noise in the look's colours) until its art exists".

The first three spells' own visuals (RLM-440 Frostbolt, RLM-441 Fireball, RLM-442 Arcane Missiles) asked for more
than one particle a moment, and in colours a single palette cannot give:

- Matěj's school colours (RLM-29, 2026-10-21): "fire orange and black". With one palette every ember took a colour
  at random from pale orange to near black, so a quarter of the sparks were dark. Embers are orange and smoke is
  black: two particles, two sets of colours.
- The design's first spells: frost "shatters into shards and a puff of snow", fire "bursts into embers and black
  smoke", arcane "a ring and sparkles, for each missile" ([spell visuals](../design/spell-visuals.md)).
- The materials need a place among the approved art, and Matěj's frost range (#B8E2F4 to #6ABCE2, RLM-463) is not
  in the project palette: its `frost` ramp has one colour inside it, so the cleanup would snap the frost material to
  about three colours, two of them outside his range (RLM-440's review note of 2026-10-31).

Matěj deferred this batch's playtest (2026-11-25: "no playtests now. If a batch needs one, make it a
deferred-playtest batch like RLM-659"), so the choices below are the agent's and stay open until he has played them
(RLM-680), as [0074](0074-an-agent-picks-the-art-of-a-batch-whose-review-matej-deferred.md) says for a deferred
batch's picks.

## Decision

1. **A look's `trail`, `burst` and `swirl` are each a list of emitters,** at most 3. An emitter is a particle and its
   rate (a block flown, at once, a tick). The emitters of one list share that list's budget from 0073: a trail's
   together at most 3 a block flown, a burst's 24 at once, a swirl's 8 a tick and 80 alive per caster. The pipeline's
   validation and the game's codec both add them up. Rejected: a second look per spell (smoke as a look of its own),
   which doubles what a spell names for one element; particle types that carry their own colour (a black smoke
   particle), which gives every element its own copy of every shape.
2. **An emitter may name colours of its own,** which its particles take instead of the look's. They are still the
   look's data, in its file: 0073's "tinted from the look's palette" now reads "from the colours its look gives its
   emitter". Without them an emitter takes the look's `colors`, as before.
3. **A burst emitter has a `spread`,** 1 by default: how far from the middle of the hit its particles appear and how
   fast they fly out, as a share of the burst's own. At 0 they stay still at the middle (arcane's one widening ring).
4. **Round a forming model only small particles swirl.** A snowflake or an ember in the swirl hid the model growing
   in the caster's hands (the batch's screenshots), so frost's and fire's swirls are flecks alone. This is a rule
   for the content, not a check: a `hands` forming, which shows no model, may swirl what it likes.
5. **A trail's particles appear along the step before the newest one.** A client draws a projectile between its
   last two positions, so a particle on the newest step hung in front of the model.
6. **A look's material is approved art:** `art/textures/material/<look>.png`, 16 x 16, tileable, cleaned like every
   texture ([0023](0023-texture-generator.md)), which `rf gen` writes in place of the placeholder. The placeholder is
   soft patches in the look's colours on a lattice that wraps round the tile, no longer per-pixel noise, which read
   as static on a model of many small faces.
7. **Matěj's frost range is a ramp of its own in the palette, `rime`,** the frost look's twelve colours, and the
   frost material is cleaned into that ramp alone (`rf art clean --ramp rime`; a gallery subject's `ramps`).
   Rejected: adding the colours to the `frost` ramp, which renumbers it under the robes' recolours that read it
   (`art/textures/variants.yaml`); cleaning the material into the whole palette, which lets whites and darker blues
   in, outside his range.

## Consequences

- A look reads more like its element with the shapes it already has: a new mix is YAML, a new shape is a file in
  `content/particles/`.
- A look's data changed shape: `trail`, `burst` and `swirl` are lists in `content/spell_looks/` and in the datapack
  entries. A datapack that overrides a look must write lists.
- The palette grew by twelve light blues that are close to each other. A texture cleaned into the whole palette may
  land on one of them instead of `frost 6`; a recolour that should catch those pixels names `rime` beside `frost`.
- No look has a material yet: the three wait for the texture generator (RLM-679), and the models are drawn with
  the placeholders until then.
