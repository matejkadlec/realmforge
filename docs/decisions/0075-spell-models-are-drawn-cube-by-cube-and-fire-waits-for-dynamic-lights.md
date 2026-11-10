# 0075. Spell models are drawn cube by cube by our own renderer, and fire's light waits for dynamic lights

Date: 2026-11-11

Status: Accepted (amends [0073](0073-spell-visuals-are-reusable-parts-in-data.md): decision 2, how a model is drawn,
and its open point on Fireball's light)

## Context

[0073](0073-spell-visuals-are-reusable-parts-in-data.md) decided that spell models are GeckoLib models, "drawn by
`GeoEntityRenderer` in flight and `GeoObjectRenderer` while forming", and left one point open: "How Fireball lights
its surroundings (a light source of our own, or the dynamic lights mod that
[ADR 0044](0044-third-party-mods-by-milestone.md) plans for M4) is settled with RLM-640." RLM-640 built the models
and the forming, and met three facts:

- **GeckoLib's renderers do not give us a size per cube.** The design's `grow` sets each cube's scale from the
  cast's progress through GeckoLib's render-time bone updater (`RenderPassInfo#addBoneUpdater`). Its render states
  are an interface GeckoLib injects into vanilla's classes at runtime, which our compiler does not see, so those
  hooks do not compile against our render states (the GeckoLib pitfall in `CLAUDE.md`, met with the custom mobs).
- **A forming model is drawn outside any entity's renderer:** for every caster, at a point that in the caster's own
  first person is fixed to the camera, with an alpha that fades when the cast is interrupted. `GeoObjectRenderer`
  wants an animatable object, a `GeoModel` and its own render pass for that, to draw cubes that never animate.
- **The pack ships no dynamic lights.** Vanilla lights a block from block light alone; lighting the blocks a ball
  of fire passes needs a mod that feeds the renderer a moving light (and Sodium's own light pipeline, which the pack
  uses). LambDynamicLights is planned for M4 ([0044](0044-third-party-mods-by-milestone.md), RLM-326).

Matěj deferred the choices of the marathon run that built this (2026-11-10: "Make any choice the flow would put to
Matěj yourself: take the recommended option, and record it in the claim comment and in the deferred ticket as an
open question"), so the two choices below are the agent's and stay open until his playtest (RLM-640's deferred
ticket).

## Decision

1. **A spell model stays a GeckoLib model, and our own renderer draws its cubes.** `rf gen` writes each model of
   shape `cubes` as `assets/realmforge/geckolib/models/spell/<id>.geo.json` from its source in `art/models/spell/`,
   GeckoLib loads and bakes it with every other model, and `client/render/SpellMeshes` reads the baked cubes
   (`BakedGeoModel`, `GeoCube`) and draws each with `GeoCube#render` into NeoForge's custom geometry
   (`SubmitNodeCollector#submitCustomGeometry`): in flight from the projectile's entity renderer, while forming from
   one level-wide renderer (`client/render/SpellForming`). So each cube has a size of its own (`grow`), the whole can
   fade (an interrupted cast), and one code path draws both. This replaces 0073's "`GeoEntityRenderer` in flight and
   `GeoObjectRenderer` while forming"; the model format, the Blockbench workflow and the cap of 64 cubes stand.
   Rejected: a model per forming stage, or a bone per cube with baked keyframe animations, which multiply assets by
   cast lengths (Cast Time Reduction shortens a cast).
2. **What that costs:** a spell model's bones are not drawn, only its cubes, so a source's bones may not be rotated
   (validation refuses one; rotate the cubes), and a spell model has no GeckoLib animation. A wobble or a pulse, if a
   model ever needs one, is a decision of its own.
3. **The cubes appear from the model's heart outward:** in the order of their distance from the middle of the
   model's bounds, worked out on the client from the baked cubes. A `core` locator for a model whose heart is
   elsewhere, and a `tail` for where its trail leaves, come when a model needs them.
4. **One material for every model, by the pipeline:** `rf gen` drops a source's own UVs and maps every face of
   every cube onto the look's 16 x 16 material pixel for pixel, each cube from another corner of the tile.
5. **Fire is drawn at full brightness now and lights its surroundings with the pack's dynamic lights later.** A
   look names its light: `world` (the light where the model is), `glow` (five light levels above it: frost) or
   `emit` (full brightness: fire). The light an `emit` look casts on the blocks round it comes when
   LambDynamicLights joins the pack ([0044](0044-third-party-mods-by-milestone.md), M4, RLM-326), which then takes
   our projectiles with an `emit` look as light sources. Rejected: a light source of our own, which is a dynamic
   lights mod's worth of rendering code beside Sodium's, for one spell; light blocks placed along the flight, which
   change the world a bolt flies through; pulling LambDynamicLights into M1 inside an engine batch, which is a pack
   change with its own boot tests and a mod every player notices (held torches), outside this batch's tickets.

## Consequences

- A new cube model is a Blockbench (or scripted) `.geo.json` in `art/models/spell/` and three lines of YAML; it
  needs no texture, no animation and no Java.
- The forming and the flight read GeckoLib's baked model classes directly (`BakedGeoModel`, `GeoBone`,
  `CuboidGeoBone`, `GeoCube`, `GeoQuad`, `GeoVertex`), which are not sources we can read: a GeckoLib update is
  checked against the decompiled jar, as the mobs' renderers are.
- Fireball does not light the cave it flies through until M4. If Matěj wants that sooner, RLM-326's
  LambDynamicLights moves up, or RLM-441 (Fireball's own visuals) carries the decision.
- The custom mobs keep GeckoLib's `GeoEntityRenderer`; only spell models are drawn this way.
