# Spell visuals

> How every spell looks and sounds, built from a small kit of reusable parts: projectile models, trajectories,
> materials, particles, cast animations, forming styles, impacts, area styles and sounds (RLM-438, epic RLM-434).
> Written on 2026-10-27 from Matěj's decisions of 2026-10-25 (RLM-434, RLM-440 to RLM-445), his C3b playtest notes
> (RLM-29, 2026-10-21) and the first spells' records (RLM-391, RLM-392, RLM-399). **Approved by Matěj on 2026-10-29
> with changes (RLM-463),** which are written in below; [his answers](#matějs-answers-rlm-463) list them. The engine
> (RLM-439) and the per-spell tickets build it. The engine choices are drafted as an ADR at the end; RLM-439 files
> it.

## Goals

Matěj, 2026-10-25 (RLM-434):

- "I really want the spells to look like in WoW, and we will get it done, sooner or later." Every spell gets a
  casting animation (the hands, and what forms in them), particles, its hit on a target or a wall, a sound, and for a
  projectile a model, a texture and a trajectory.
- **Reuse is the approach:** "make several different projectile animations, several different projectile models,
  several different textures for models and for particles", combined per spell.
  - Frostbolt and Fireball share a trajectory, a forming style and a casting animation, and differ in model, texture
    and particles (icy blue, fiery red): Frostbolt is a chunk of ice, Fireball a round ball of fire (Matěj's sketches,
    2026-10-29).
  - Frost spells share a frost look: Ice Lance, an instant frost projectile, has Frostbolt's texture, particles and
    trajectory.
  - Arcane Missiles fires 3 missiles quickly one after another, one every 0.5 s in its 1.5 s channel (Matěj,
    2026-10-29, RLM-518): same model, particles and texture, all three on the same straight path (Matěj, 2026-10-29).
  - Areas share a look: Blizzard is an ice circle on the ground with a clear border; Fire Storm is the same area with
    fire, and a Hunter's Frost Trap makes a similar one.
- **Frostbolt is the reference** (RLM-440; its shape since Matěj's sketch of 2026-10-29 a short fat icicle,
  [models](#models-and-materials)): a glacier "made of dozens of rectangles", "a custom shape, not squarish
  at all", pointed away from the player, icy and snowy, forming over the 1.5 s cast with ice particles swirling round
  it "so it really looks like it's being created out of thin air"; it flies with a quick icy trail and shatters.
- **Warrior spells are mostly weapon animations:** Slam is a swing from left to right. At first "I don't think
  particles are needed there"; on 2026-10-29 Matěj gave them a swing trail: light grey streaks behind the blade.
- **School colours** (RLM-29, 2026-10-21): fire orange and black, frost whitish and light blue, arcane purple and
  dark purple, physical `#B2560D`.
- **Assets:** textures from Retro Diffusion Fast, as for blocks and mobs; sounds from ElevenLabs (RLM-126).

Rules this design keeps from elsewhere:

- **Performance is a requirement** ([vision](../vision.md)), and particles are where spell mods usually lose it.
- **The vanilla look:** pixel art on the 16 x 16 grid, snapped to the project palette; no shaders of our own
  ([architecture 18](../architecture.md#18-client-code-rendering-gui-input)); no vanilla texture files or recolours,
  not even as generator input (CLAUDE.md rule 8).
- **Bolts fly where the crosshair points,** straight and without gravity
  ([ADR 0052](../decisions/0052-spells-aim-with-the-crosshair.md)), and an assisted bolt curves gently toward its
  target (Matěj's decision of 2026-10-27 on RLM-345, whose batch amends ADR 0052 and the aiming design). Visuals
  never move the line a projectile flies on. ADR 0052 stays as it is: Arcane Missiles fly straight too (Matěj,
  2026-10-29), so no trajectory bends a projectile ([trajectories](#trajectories)).
- **Variety lives in data** ([architecture 11.3](../architecture.md#113-spell-entities)): one projectile entity type
  and one area entity type; a new Java type only for genuinely new behaviour.

## What exists today

| Part | Today | Where |
| --- | --- | --- |
| Projectile look | `appearance: {model: orb, color}`: vanilla's shulker bullet model, spinning, with a per-spell placeholder texture `textures/entity/spell/<spell>.png` | `SpellProjectileRenderer`, `SpellOrbs` |
| No appearance | a trail of `end_rod` sparks from the projectile's client tick | `SpellProjectile` |
| Impact | `impact: {sound, particles}`: a sound and a block's break particles, sent from the server | `SpellProjectile#impact` |
| Forming | `forms_projectile` on a cast time: a mob's renderer draws the orb in its raised hands, growing from 15 % to full size; the projectile leaves from there (`SpellVisuals#formPoint`) | `CustomMobRenderers`, mobs only |
| Areas | a ring of `witch` particles from the area's client tick | `SpellArea` |
| Cast animation | one animation for every cast time and channel, `realmforge:cast` (both hands rise and meet below the crosshair), on the PAL layer at priority 3000 (C6-1, RLM-435) | `client/animation` |
| Sounds | a cast's `sound` plays when it begins; our sound events from `content/sounds/` (vanilla sounds remapped until RLM-126) | `content/sounds/stoneform.yaml` |

The kit replaces `appearance`, `impact` and `forms_projectile` with references to its parts. Stoneform, the Hewn
Chanter's spell, moves to the kit with the engine (RLM-439), so mobs and players form their projectiles the same way.

## The kit

A spell's look is a **combination of parts**, each defined once and named by id. The parts fall on two axes, which is
what makes Matěj's examples cheap:

- **Shape:** the model, the trajectory, the forming style and the cast animation. Frostbolt and Fireball share it.
- **Look:** the material texture, the particle colours, the particles and the sounds of an element (frost, fire,
  arcane, physical, stone). Every frost spell shares the frost look.

Fireball is then Frostbolt's trajectory, forming and animations with its own round model and the fire look; Ice
Lance is a frost look on a lance; Fire Storm is Blizzard's area style with the fire look.

| Part | What it is | Defined in (proposed) | Built as |
| --- | --- | --- | --- |
| Look | an element: a material texture, a palette of particle colours, which particles it uses for trails, swirls, bursts and auras, and its sounds | `content/spell_looks/<id>.yaml` | a synced datapack registry |
| Model | a projectile or forming model: its Blockbench source, size, which way it points, spin, its locators (`tail`, `core`) | `content/spell_models/<id>.yaml`, source `art/models/spell/<id>.bbmodel` | GeckoLib model asset |
| Material | a look's texture: one tileable 16 x 16 tile that every model is mapped onto, as a block's texture is | `art/` source, `textures/spell/material/<look>.png` | texture, from Retro Diffusion Fast |
| Particle | a particle shape: a few greyscale 8 x 8 frames, tinted per spawn from the look's palette, with its motion (gravity, drag, spin), lifetime and whether it is lit by the world | `content/particles/<id>.yaml` | a generated `ParticleType` registration |
| Trajectory | how a projectile flies: a kind (`straight` now; a curve or an angle may come later) with its numbers | `content/spell_trajectories/<id>.yaml` | a synced datapack registry; kinds in Java |
| Forming style | how a projectile appears during the cast: `grow`, `storm` or `hands` ([below](#forming-during-a-cast)) | a field of the spell's visuals | Java, client only |
| Cast animation | a player animation: the hands while casting, a release, a weapon move | `assets/realmforge/player_animations/<id>.json` | Emotecraft JSON on the cast layer (ADR 0020) |
| Impact | what a hit looks like: the look's burst, sized to the hit (a splash's radius), and its sound | a field of the projectile | client particles from one entity event |
| Area style | a ground zone's look: a border ring, a faint fill and a storm of particles inside (RLM-443, M3) | `content/spell_areas/<id>.yaml` | the area entity's renderer |
| Sound | a sound event | `content/sounds/` (exists) | `sounds.json` |

The folder names are proposals; RLM-439 settles them with the content README. Every part follows rule 1 of
`CLAUDE.md`: a pydantic model, an entry in `CONTENT_TYPES`, reference validation, a generator, tests and docs.

### Looks

A look holds what makes an element recognisable at first sight, so frost reads as frost on every spell:

```yaml
# content/spell_looks/frost.yaml (sketch)
id: frost
material: frost                      # textures/spell/material/frost.png
colors: ["#B8E2F4", "#8DCFEC", "#6ABCE2"]   # Matěj's frost range (2026-10-29); 12 to 16 colours inside it in data
particles:
  swirl: realmforge:shard            # round a forming model
  trail: realmforge:mote             # behind a projectile
  burst: realmforge:shard            # an impact
  aura: realmforge:flake             # on a target with the look's effect (Slowness)
sounds:
  form: realmforge:spell.frost.form       # while a projectile forms
  release: realmforge:spell.frost.release # as it leaves the hands
  impact: realmforge:spell.frost.shatter
```

The first looks and their colours, from Matěj's school colours:

| Look | Colours | Material | Particles |
| --- | --- | --- | --- |
| frost | **#B8E2F4** (lightest) through **#8DCFEC** to **#6ABCE2** (darkest), 12 to 16 colours inside that range (Matěj, 2026-10-29) | ice and packed snow | shards, motes, snowflakes |
| fire | orange and black | flame over glowing coal | embers, flames, black smoke |
| arcane | purple and dark purple | faceted violet crystal | motes, sparkles, rings |
| physical | `#B2560D`; the swing trail light grey | none (Warrior spells have no projectile yet) | the swing trail's streaks; a bleed's later |
| stone | Undervault's greys | Vaultstone-like rock | rock chips (Stoneform) |

A look's **aura** answers the C3b note about damage over time per school: Fireball's Burning shows embers and black
smoke on its target, Frostbolt's Slowness drifting snowflakes, instead of vanilla's coloured swirls.

### Models and materials

- **One material per look, mapped onto every model** (Matěj, 2026-10-29: one material per element for every spell of
  it). A model's cubes take their faces from one tileable 16 x 16 material, as blocks do. So Frostbolt's ice chunk and
  Ice Lance's lance literally share the frost texture, Fireball's ball takes the fire material, and a new model needs
  no new texture. This fits the texture pipeline as it is:
  Retro Diffusion Fast already makes tileable 16 x 16 block textures, and `rf art clean` snaps them to the grid and
  the palette. A unique spell may still name a texture painted for its model.
- **Shape from many small cubes:** Matěj wants "dozens of rectangles". A model has 12 to 64 cubes, rotated freely, so
  the ice chunk reads as a jagged piece of ice and not as a box. The cap keeps rendering cheap.
- **Points forward, spins if it says so:** a model is drawn along its flight (the ice chunk's point first, as in
  Matěj's drawing), and may spin about its flight axis at a speed its data sets. A model may carry a short looping animation
  (a wobble) from its GeckoLib animation file.
- **Locators** mark points in the model: `tail` (where the trail leaves) and `core` (where forming starts). GeckoLib
  reads locators from the model, so the trail always leaves from the right place whatever the model's shape.
- **Light by element** (Matěj, 2026-10-29, which changes the proposed "never glowing"): **Fireball lights its
  surroundings,** since it is a ball of fire: it is drawn at full brightness and its light falls on the blocks it
  passes, through dynamic light (the light level of the block it is in, as LambDynamicLights does for held torches;
  RLM-439 picks our own small light source or that mod, which ADR 0044 plans for M4). **Frostbolt may glow a little:**
  its model is drawn a few light levels above the world's, which Matěj thinks looks better than a model and particles
  with no light; it lights nothing. Other models take the light of the block they are in, like an arrow, and the
  particles carry their own (flames, sparks and motes at full brightness, as vanilla's are). A look names its light.

The first models:

| Model | Shape | Used by |
| --- | --- | --- |
| ice chunk | Matěj's sketch of 2026-10-29: a thick, irregular chunk of ice, longer than wide, like a short fat icicle, its point first, about 0.6 blocks long, 30 to 50 cubes | Frostbolt |
| fire ball | a round ball of fire, not Frostbolt's shape (Matěj's sketch of 2026-10-29), about 0.5 blocks, 30 to 50 cubes | Fireball |
| crystal | shaped like the ice chunk but straighter and more cut: a faceted crystal (Matěj, 2026-10-29), about 0.3 blocks, 12 to 20 cubes | Arcane Missiles |
| lance | a long thin spike, about 1 block | Ice Lance (later) |
| rock | a rough stone lump | Stoneform (the Hewn Chanter) |
| orb | today's spinning slabs, kept as the placeholder model | any spell before its art |

### Particles

- **A few shapes, tinted per spawn.** A particle type is a shape (a mote, a shard, a spark, a flame, a wisp of smoke,
  a snowflake, a ring), a handful of greyscale 8 x 8 frames that the look's colours tint as each one spawns. Six or
  eight shapes serve every school: the frost and arcane motes are the same sprite in different colours, and black
  smoke is a white wisp tinted black.
- **Our own types, ours only.** `ParticleType` is a static registry, so the YAML in `content/particles/` generates
  the registrations, as `content/effects/` does for status effects. Their sprites are pixel rows in the pipeline, as
  our font's symbols are (`generate/fonts.py`), which keeps them small, on the palette and buildable in a cloud
  session; a sprite that needs more can come from Retro Diffusion later.
- **Emitters** say how a part spawns them: which particle, how many, from what shape (a point, a sphere, a ring,
  two jagged lines), how fast, for how long. Matěj's drawing of Frostbolt's trail, two broken lines of particles
  behind the ice, is an emitter of two lines with random jitter. The idea of particle batches as data comes from
  Spell Engine; its code is GPL-3.0-only and is never copied (CLAUDE.md rule 8).
- **The Particles option** (Matěj, 2026-10-29): our particles follow vanilla's setting, fewer at Decreased and
  Minimal, **but particles that warn of an attack (a telegraph) always show in full**: a telegraph's emitter spawns
  them as forced particles, which vanilla's option never drops ([particle budget](#particle-budget)).
- **Spawned on the client** from what it already knows: a cast from `CastStartedPayload`, a projectile's flight from
  its own client tick, an impact from one entity event. No particle ever crosses the network.

### Trajectories

A trajectory changes where a projectile is, so it changes what it hits: it is **gameplay, run by the server**, with
the client computing the same path for smooth drawing.

- **`straight`:** today's flight, along the aim. Every M1 projectile flies it, Arcane Missiles' three included.
- **No `curve`** (Matěj, 2026-10-29, question 11): the proposed bend toward the aim point is dropped, and ADR 0052's
  "projectiles fly straight without gravity" stays as it is. A curve or an angle may come later, as a decision of its
  own; the trajectory registry keeps room for a new kind. Nothing flies under gravity: a lob for a thrown weapon (Axe
  Throw) would be a later decision too.
- **Deterministic:** a trajectory is a pure function of the launch point, the aim, the time since launch and a seed
  sent with the projectile's spawn, so the client draws the same curve the server moves along, and the server's
  position updates only correct drift.
- **Aim assistance on top:** RLM-345's gentle homing of an assisted bolt (Matěj, 2026-10-27) bends the line toward
  the target; a later trajectory kind adds its offset to that line, so the two never fight.

### Cast animations

Every animation plays on the cast layer at priority 3000, above Better Combat's swings
([ADR 0020](../decisions/0020-cast-animations-and-melee-while-casting.md)), and turns the arms with the camera in
first person as `realmforge:cast` does today (architecture 11.6).

| Animation | What it shows | Default for |
| --- | --- | --- |
| `cast` (exists) | both hands rise and meet below the crosshair, pulsing | every cast time |
| `channel` | both hands pushed forward and held, palms out | every channel |
| `release` | a short push forward of both hands, about 0.25 s, as the projectile leaves (Matěj, 2026-10-29) | the end of a cast that forms a projectile |
| `instant` | both hands push forward and back, about 0.5 s, the global cooldown (Matěj, 2026-10-29) | every instant spell without a weapon move (Ice Barrier, Battle Shout; later Ice Lance) |
| `slam`, `stab`, `shield_bash` | the Warrior's weapon moves: Better Combat's swing and stab, which RLM-399's swings already time, and the bash (RLM-444) | their spells |

**Instant spells need a signal.** Today only a cast time, a channel or a swing (RLM-399: Slam, Reckless Strike and
Shield Bash, kept as a cast of their own with a cast bar) reaches the clients: `Casting` sends `CastStartedPayload`
to the caster and every tracking player, which raises the cast layer. Any other instant spell runs its effects at
once and tells no client, not even the caster's own, that the server accepted it, and today it has no animation at
all. Matěj, 2026-10-29: **every instant spell gets a quick animation too**, about 0.5 s, both hands pushing forward
and back. So the engine sends `CastStartedPayload` for an instant spell too, marked as an instant and carrying the
animation's length, and `ClientCasts` plays it on the cast layer for the caster and every tracking player.

- **Not a cast:** the push shows no cast bar, cannot be interrupted, and the spell fires at once (Matěj,
  2026-10-29); the client keeps it apart from the casts in progress. The Warrior's swings stay what RLM-399 made them:
  a cast of their own with a cast bar and a hit moment, which a stun or a death can cut.
- **Its end:** the client lowers the arms when the animation's length has passed; a swing's interrupt sends
  `CastEndedPayload`, as a cast's does.
- **No melee during the animation** (Matěj, 2026-10-29, question 12): for the animation's length, as during a cast, so
  a Better Combat swing (layer 2000) never lands a free hit under Slam's swing (layer 3000), the reason ADR 0020 gives
  for casts. RLM-399 already blocks melee during the swings; the push blocks it too. That amends ADR 0020 decision 3
  ("instant spells have no such window"), so the engine's ADR records it, and the server enforces it as it does for
  casts: the `combat` attachment keeps the animation's window, and the melee checks read it.

A spell names an animation only to differ from its cast type's default. **Hit moments** (RLM-399): the server deals a
Warrior spell's damage at its hit moments, so the spell's data holds the animation's length and the hit moments as
shares of it (RLM-399: Slam 0.5 s, its hit at 50 %); validation reads the animation file and refuses a length that disagrees, so the
server's timing and what the player sees never drift apart.

### Impacts

- **One entity event per hit:** before a projectile is removed, the server sends vanilla's entity event, as a snowball
  does when it breaks (`Level#broadcastEntityEvent`); each client then bursts the look's particles where it is. A
  byte tells a hit on a creature from a hit on a block, so a block hit can spray along the block's face.
- **Sized to the hit:** with RLM-399's splash, the burst fills the splash radius (about 1 block), so the player sees
  which mobs it caught.
- **Sound** from the look, played by the server where it hits, as today.
- A trailing bit of the hit stays briefly: frost shards fall with gravity, fire leaves a puff of black smoke.

### Area styles (RLM-443, M3)

- **Drawn, not only particles:** the area entity gets a renderer that draws the border as a flat textured ring on the
  ground and a faint fill inside it, each ring segment set on the top of the ground beneath it, so it follows small
  slopes. Particle settings never hide a danger zone ([particle budget](#particle-budget)).
- **The storm** is particles falling and swirling inside the circle, from the look: snow and ice for Blizzard and
  Frost Trap, embers and flames for Fire Storm.
- **From the first tick:** the area draws its border from the moment it appears (RLM-436).

### Sounds

- **Per look, per moment:** forming (loops while the cast runs and stops when it ends, so a cast shortened by Cast Time
  Reduction never cuts a long sound short), release, an optional quiet flight sound, impact. A spell may override one.
- **One interrupt sound** for every spell, as in WoW, and the forming model **fades away**, and the hands' pose ends
  with it (Matěj, 2026-10-29, question 9; RLM-445 has the sound).
- **Warrior moves:** a swing, a stab and a shield thud, played at the hit moment, not at the start.
- ElevenLabs through RLM-126, mono OGG, as `content/sounds/` events; WoW's sounds only as a reference by ear
  ([ADR 0009](../decisions/0009-licensing.md)). Until then, vanilla sounds remapped, as Stoneform does.

## Forming during a cast

What a cast-time spell shows in the hands for its 1.5 s. The ticket asked for three options and what each costs.
**Matěj chose B** (2026-10-29): grow the model cube by cube in a storm of particles; A only if B turns out much too
hard. He really wants B.

### Option A: out of a particle storm (Matěj's simpler way)

- The look's particles stream in from a sphere round the hands toward the core for the whole cast.
- The model scales up out of them from nothing over the second half, full at the end; fewer particles stay round it.
- **Cost:** a converging emitter and a scale curve, shared by every spell. Any model works.
- **Look:** clearly magic, but the model appears as one piece; it is not "built".

### Option B: grown cube by cube from the core (Matěj's "insane" way)

- The model's cubes appear in order of their distance from its `core` locator, each growing from nothing to full
  size around its own centre, so the ice chunk grows outward from its heart and is whole when the cast ends.
- The look's particles spark at the growing edge, where the newest cubes are, with A's storm round it.
- **Is it feasible for dozens of spells? Yes, if it is generic.** Nothing is animated by hand:
  - `rf gen` splits each cube of a model into its own bone, its pivot at the cube's centre, ordered by distance from
    the core. That moves no cube, since GeckoLib's cubes carry their own rotation.
  - While a cast runs, the renderer sets each bone's scale from the cast's progress through GeckoLib's render-time
    bone updater (`RenderPassInfo#addBoneUpdater`), drawn by `GeoObjectRenderer`, at the frame rate, not the tick
    rate, so it grows smoothly.
  - A new spell needs nothing; a new model only enough small cubes, which Matěj wants anyway.
- **Cost:** about one engine session more than A (the bone split, the updater, the edge particles), nothing per
  spell. A split model draws one bone per cube, up to 64; RLM-439 measures that with many casters at once (a spark
  profile, [architecture 19.5](../architecture.md#195-performance)) and lowers the cap if it costs frames.
- **Risks:** up close in first person, cubes popping in may look jittery; each grows over a short ramp instead of
  popping, and the playtest judges it. Cast Time Reduction brings the cast toward 0.5 s late in the game, where any
  forming is quick.

### Option C: WoW's way

- In WoW Classic the projectile does not form: the caster's hands glow and swirl with the school's energy during the
  cast (the "precast" effect), and the bolt appears at release and flies off.
- **Cost:** the cheapest: two small emitters at the hands, and the model shown at release.
- **Look:** clear and never in the way, but nothing is created out of thin air, which is what Matěj asked for.

### First person decides

WoW hides the caster's body in first person, so its casting is seen only from outside. Minecraft's first person
shows the hands, and ours meet below the crosshair during a cast (RLM-89, RLM-108), so whatever forms there sits half
a block from the eye: at its real size the ice chunk would fill the bottom of the screen.

- **One size in both views, tuned for first person** (Matěj, 2026-10-29, which replaces the proposed 40 %): the game
  is made to be played in first person, so the forming model between the hands has the same size in first and third
  person, chosen so that in first person it covers little of the field of view: below the screen's middle, never over
  the crosshair at FOV 70 to 110, with a third of the particles there, none near the screen's centre. The model in
  flight keeps its own size.
- **Its light** is its look's (above): a forming Fireball lights the hands, a Frostbolt glows a little.
- **Leaving the hands:** the server still launches a player's bolt from the eyes, so it flies exactly where the
  crosshair points (ADR 0052). Each client draws its first 3 or 4 ticks blended from where it drew the forming model
  to the real path, so it is seen to leave the hands, in first and third person. Mobs keep launching from their form
  point.

### Recommendation

**Option B for projectiles that form (Frostbolt, Fireball), on top of A's storm; C's glowing hands for spells
without a formed model (Arcane Missiles' channel).** Approved (Matěj, 2026-10-29). All three styles share their
emitters, so the engine offers them as `forming.style: grow | storm | hands`, and a spell can fall back from `grow` to
`storm` in its data if B turns out much too hard or the playtest says the cubes look wrong. One size in first and
third person, as above.

## Built so

- **One forming renderer for every caster:** a client listener reads the casts in progress from `ClientCasts` and
  draws each forming model at its caster's hands through NeoForge's custom geometry events
  (`ExtractLevelRenderStateEvent`, `SubmitCustomGeometryEvent`), with GeckoLib's `GeoObjectRenderer`. Players and
  mobs alike; the mob renderer stops drawing its own orb.
- **The hands' point:** in first person, the point between the hands that turn with the camera (`CastArms`'
  shoulder pivots about the eye); from outside, the third-person hand point; for a mob, its form point.
- **The projectile** becomes a GeckoLib entity drawn by `GeoEntityRenderer`, pointed along its flight, its material
  from its look. Its spawn data carries its spell, so the client finds model, look and trajectory in the synced
  registries; a trajectory's seed rides along.
- **Particles:** our `ParticleType`s with sprite sets (`assets/realmforge/particles/<id>.json`), their providers
  registered in `RegisterParticleProvidersEvent#registerSpriteSet`, their options carrying a colour, a size and a
  lifetime.
- **On the network:** timed casts, projectiles and areas already reach their clients. New are only the instant
  spells' `CastStartedPayload`, and their `CastEndedPayload` when an interrupt cuts the animation short
  ([cast animations](#cast-animations)), and the impact's entity event.
- **Client isolation:** every renderer, particle provider and GeckoLib render call sits under `client/`; looks,
  models' ids, trajectories and hit moments are common data.
- **Placeholders:** until a part's art exists, `rf gen` writes a placeholder material (noise in the look's colours),
  the orb stands in for a missing model, and vanilla sounds stand in for ours, so the engine is played before any art.
- **Tests:** pytest for the content and its references (a spell naming a missing look, model, trajectory or
  animation is refused) and for the cube split; JUnit for the trajectories and the forming curves; GameTests for what
  runs on the server: an impact's entity event, a Warrior spell's damage at its hit moment, no melee during an
  instant's animation.

## Particle budget

Vanilla keeps at most 16,384 particles per particle group, drops any spawned more than 32 blocks from the camera, and
its Particles option drops a third of them on Decreased and all but forced ones on Minimal
(`ClientLevel#doAddParticle`). Our emitters use the normal path, so they obey all three. On top of that, validation
caps each emitter:

| Emitter | Cap | Alive at once, about |
| --- | --- | --- |
| swing trail | 6 a tick for the swing's length | 30 per swing |
| forming swirl | 8 a tick, a lifetime of at most 10 ticks | 80 per caster |
| trail | 3 per block flown | 30 per projectile at 24 blocks/s |
| impact burst | 24 at once | 24 per hit |
| aura (damage over time) | 1 a tick | 10 per target |
| area storm (M3) | 24 a tick, whatever the radius | 400 per area |

A crowded fight, four Mages casting without pause and ten Hewn Chanters, keeps about 2,000 particles alive, an eighth
of one group's limit. Models stay at 64 cubes or fewer.

**Nothing a player must react to is only particles.** An area's border is drawn by its renderer and a cast's bar by
the HUD, so a player on Minimal particles still sees every telegraph. **And a telegraph's particles always show in
full** (Matěj, 2026-10-29): a telegraph's emitter (a boss's ground ring, an area's storm before it hits) spawns forced
particles, which the Particles option never drops; validation marks such emitters and keeps them under their caps.

## The first spells

The M1 mapping, as Matěj approved it with his changes (2026-10-29). Damage, ranks and hit shapes are C4's and
RLM-399's; this is only the look.

| Spell | Cast animation | Forming | Projectile | Trajectory | Look | Impact | Sounds |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Frostbolt | `cast`, then `release` | `grow`: the ice chunk grows from its core, frost shards swirling in; it glows a little | ice chunk, point first | straight | frost | shatters into shards and a puff of snow, filling the splash | ice forming, release, shatter |
| Fireball | `cast`, then `release` | `grow`: the fire ball grows from its core, embers and black smoke swirling in, lighting the hands | fire ball, lighting what it passes | straight | fire | bursts into embers and black smoke, filling the splash | fire forming, release, burst |
| Arcane Missiles | `channel` | `hands`: purple motes swirl round both hands for the whole channel | crystal, one every 0.5 s (RLM-518) | straight, all three on the same path | arcane | a ring and sparkles, for each missile | each missile's launch, each burst |
| Ice Barrier, Battle Shout | `instant` (0.5 s) | none | none | none | frost; physical | the spell's own (Ice Barrier's shield) | a short cast sound |
| Slam | `slam`, 0.5 s, left to right (RLM-399) | none | none | none | physical | a light grey swing trail behind the blade, left to right | a heavy swing; the hit at 0.25 s |
| Reckless Strike | `stab`, 0.5 s (RLM-399) | none | none | none | physical | the trail along the stab | a stab; the hit at 0.125 s |
| Shield Bash | `shield_bash`, 0.5 s (RLM-399) | none | none | none | physical | the trail behind the shield | a shield thud at 0.25 s |

- **Frostbolt and Fireball differ in `look` and model:** the material, the particles, the light and the sounds, and
  Fireball's round model (Matěj, 2026-10-29), which changes RLM-441's "its data differs from Frostbolt's only in
  texture and particles". They share the trajectory, the forming style and the animations.
- **Burning and Slowness** show their look's aura on the target: embers and black smoke, drifting snowflakes.
- **Arcane Missiles' path:** all three crystals fly the same straight path, quickly one after another (Matěj,
  2026-10-29), toward where the player aims (with RLM-345's assistance). Each explodes (RLM-399).
- **The Warrior's spells** look like Better Combat's swings, with a swing trail: light grey streaks behind the blade,
  from left to right for a sword swing (Matěj, 2026-10-29). The weapon in the hand moves with the arms. Their hit
  shapes show as outlines with vanilla's hitbox display (RLM-399).
- **Ice Lance** (later): `instant`, no forming, the lance model, straight, frost: the frost look reused, as Matěj
  described.

A sketch of Frostbolt's data, for the engine's shape (C4 owns the rest of the spell):

```yaml
# content/spells/frostbolt.yaml, the visual parts only (sketch)
visuals:
  look: realmforge:frost
  forming: {model: realmforge:ice_chunk, style: grow}  # cast and release are the cast time's defaults
effects:
  - type: realmforge:projectile
    speed: 24                                # speed, size and damage are C4's numbers
    size: 0.5
    model: realmforge:ice_chunk              # drawn with the look's material
    trajectory: realmforge:straight
    effects:
      - {type: realmforge:damage, amount: 6}
```

Fireball's data is the same with `look: realmforge:fire` and `model: realmforge:fire_ball`.

## Asset workflow

| Asset | Made with | Source in the repository | Where |
| --- | --- | --- | --- |
| Models | Blockbench, GeckoLib's model format; an agent may also write a model's cubes from a script (a teardrop of jagged cubes is geometry, not painting) | `art/models/spell/<id>.bbmodel` and its exported `.geo.json`; `rf gen` checks and copies it, splitting cubes for `grow` | local (Blockbench) or cloud (scripted) |
| Materials | Retro Diffusion Fast, tileable 16 x 16, then `rf art gallery` and `rf art clean` ([art pipeline](../../art/README.md)) | `art/spell-materials/` | local only (Retro Diffusion's MCP server) |
| Particle sprites | pixel rows in the pipeline, greyscale | `tools/realmforge_tools/generate/` | cloud or local |
| Player animations | Emotecraft keyframes (Blockbench's Emotecraft plugin, or written by hand), checked with the cast screenshots (`REALMFORGE_QA_CAST_SHOTS`) | `mod/src/main/resources/assets/realmforge/player_animations/` | written anywhere; the screenshots need a rendered client, local |
| Sounds | ElevenLabs through `rf sound gen` (RLM-126), mono OGG | `art/sounds/`, wired in `content/sounds/` | local only (the API key) |

The engine (RLM-439) needs none of these: it runs on placeholders and is cloud-capable. The per-spell tickets are
local-only where they make textures or sounds, as their descriptions already say.

## ADR draft

RLM-439 files this as the next free ADR, adjusted here to Matěj's answers of 2026-10-29.

> **NNNN. Spell visuals are reusable parts in data, drawn with GeckoLib, our own particles and the cast layer**
>
> Status: Accepted (amends [0020](../decisions/0020-cast-animations-and-melee-while-casting.md): no melee during an
> instant spell's animation)
>
> **Context.** Every spell needs its own look (RLM-434): a cast animation, what forms in the hands, a projectile's
> model, texture and trajectory, particles, its impact and its sounds, built from reusable parts so dozens of spells
> stay feasible. Today a projectile has an inline `appearance` (vanilla's shulker bullet model with a per-spell
> texture) and `impact`, and only mobs show a projectile forming.
>
> **Decision.**
>
> 1. A spell combines parts named by id: a look (material, particle colours and particles, sounds), a model, a
>    trajectory, a forming style, cast animations, an impact, an area style. Looks and trajectories are datapack
>    registries; models and particles are generated assets and registrations; all of it comes from `content/`.
> 2. Models are GeckoLib models (Blockbench sources in `art/`), drawn by `GeoEntityRenderer` in flight and
>    `GeoObjectRenderer` while forming, at most 64 cubes, with the light their look names (Fireball lights its
>    surroundings, Frostbolt glows a little, others take the world's light). Rejected: vanilla entity models, which
>    are Java code per model, so every new model would need code; our own model format and renderer, which GeckoLib
>    already is and the pack already ships.
> 3. A look's texture is one tileable 16 x 16 material that every model maps onto. Rejected: a texture painted per
>    model and look, which multiplies textures by models.
> 4. Particles are our own `ParticleType`s, a few greyscale shapes tinted per spawn from the look's palette, spawned
>    on the client from state it already has. They follow vanilla's Particles option, except a telegraph's, which
>    always show in full. Emitters are capped by validation, and nothing a player must react to is only particles.
> 5. Trajectories are gameplay: the server moves a projectile along a deterministic path, and the client computes
>    the same path from its spawn data. Every projectile flies `straight`, as
>    [ADR 0052](../decisions/0052-spells-aim-with-the-crosshair.md) says; nothing flies under gravity.
> 6. Forming styles `grow`, `storm` and `hands` run on the client from the cast payloads, drawn for every caster by
>    one renderer at the caster's hands, at one size in first and third person, tuned so it covers little of the
>    first-person view; an interrupted cast's model fades away.
> 7. Cast animations stay on the cast layer of
>    [ADR 0020](../decisions/0020-cast-animations-and-melee-while-casting.md), started by `CastStartedPayload`,
>    which every instant spell now sends too, marked as an instant: a 0.5 s push of both hands, or a weapon move, with
>    no cast bar, and it ends after the animation's length (a swing's interrupt sends `CastEndedPayload`). **No melee
>    during an instant spell's animation** (Matěj, 2026-10-29), which amends ADR 0020 decision 3; the server enforces
>    it from the `combat` attachment as for casts. A spell's hit moments live in its data, checked against the
>    animation's length.
>
> **Consequences.** A new spell is mostly YAML; a new element is a look, a material and colours; a new model is a
> Blockbench file. The projectile becomes a GeckoLib animatable, and Stoneform moves to the kit. GeckoLib's API is
> one more place a Minecraft upgrade touches, as it already is for mobs.

## Tickets after the approval

What each RLM-434 ticket takes from this design as Matěj approved it on 2026-10-29 (RLM-463); their descriptions
carry the changes (the second half of RLM-438's "Done when"):

| Ticket | Takes |
| --- | --- |
| RLM-439 engine | the content types (looks, models, particles, trajectories), the GeckoLib projectile, the forming renderer with `grow` (B), `storm` (A's fallback) and `hands`, one forming size in both views tuned for first person, the blended launch, an interrupted cast's fade, `straight` only, light by look (Fireball's dynamic light, Frostbolt's slight glow), the impact event, `CastStartedPayload` for every instant spell with the 0.5 s `instant` push and its no-melee window, the `release` and `channel` animations, the emitters and their caps, telegraph particles that always show, placeholders, Stoneform moved to the kit, the ADR, architecture 11.3 and 11.6, the content README |
| RLM-440 Frostbolt | the ice chunk model (a short fat icicle, Matěj's sketch), the frost material in his palette (#B8E2F4 to #6ABCE2, 12 to 16 colours), the frost particles and colours, its slight glow, Slowness's aura |
| RLM-441 Fireball | the fire ball model (round, Matěj's sketch), the fire material, particles and colours, its light, Burning's aura |
| RLM-442 Arcane Missiles | the crystal model (like the ice chunk, straighter and faceted), the arcane material and particles; all three on one straight path |
| RLM-443 areas (M3) | the area style: the ring, the fill, the storm; the ice and fire variants as looks |
| RLM-444 Warrior | `slam`, `stab`, `shield_bash` on RLM-399's swings, with their lengths and hit moments in data, and the light grey swing trail behind the blade |
| RLM-445 sounds | each look's forming, release and impact sounds, the Warrior moves, the interrupt sound |

## Matěj's answers (RLM-463)

Matěj answered every question on 2026-10-29 (RLM-463, comments 23063 and 23067); the sections above follow them.

1. **Forming:** B, grow the model cube by cube in a storm of particles. Only if B turns out much too hard, A (the whole
   model scaling up). He really wants B.
2. **First person:** the game is made to be played in first person. The forming model between the hands has the same
   size in first and third person, tuned for first person: it must not cover much of the field of view.
3. **Textures:** one material per element for every spell of it. Frost's palette spans **#B8E2F4** (lightest) through
   **#8DCFEC** (middle) to **#6ABCE2** (darkest), with 12 to 16 colours inside that range, not only those three.
4. **Shapes (his sketches):** Frostbolt a thick, irregular chunk of ice, longer than wide, like a short fat icicle;
   Fireball a round ball of fire, not Frostbolt's shape.
5. **Release:** the short push of the hands forward. **New:** instant spells get a quick animation too, about 0.5 s
   (the global cooldown): both hands push forward and back. There is no cast, it cannot be interrupted, and the spell
   fires at once.
6. **Arcane Missiles:** a crystal, shaped like Frostbolt but straighter and more cut, faceted. All three missiles fly
   the same straight path, quickly one after another.
7. **Light:** Fireball lights its surroundings, since it is a ball of fire. Frostbolt may glow a little; he thinks
   that looks better than a model and particles with no light.
8. **Particles:** our particles follow vanilla's Particles option (fewer at Decreased and Minimal), but particles that
   warn of an attack (telegraphs) always show in full.
9. **An interrupted cast:** the interrupt sound, and the forming model fades away (the hands' pose ends too).
10. **The Warrior:** has particles. His spells look like Better Combat's swings, with a swing trail: light grey streaks
    behind the blade, for example from left to right for a sword swing.
11. **Arcane Missiles fly straight,** with no curve. ADR 0052 stays as it is; a curve or an angle may come later.
12. **Melee during an instant spell's animation:** blocked, which amends ADR 0020 (the engine's ADR records it).

## Open questions for Matěj

None: every question above is answered. The questions as they were asked:

1. **Forming:** grow the glacier cube by cube from its core with a storm of particles round it (B, proposed); only the
   particle storm with the whole model scaling up (A); or WoW's glowing hands with the bolt appearing at release (C)?
2. **First person:** the forming model at about 40 % of its size between the hands, below the crosshair (proposed);
   hidden in first person with only the particles, as WoW hides casting; or at full size?
3. **One material per look,** shared by every model (proposed), or a texture painted for each model?
4. **Fireball's shape:** the glacier shape with the fire material, as you described it (proposed for the first
   version; it reads as a comet with its embers streaming back), or a round fire bomb that shares only the trajectory
   and forming?
5. **The release:** a short push of the hands as the bolt leaves (proposed), or the hands simply lower?
6. **Arcane Missiles:** a small faceted shard as the missile (proposed) or an orb; the paths left, high, right in turn
   (proposed) or a random bend each time?
7. **No glowing models,** the particles carrying the light (proposed): agreed?
8. **The Particles option:** our spell particles follow vanilla's setting, so Minimal hides them but never a telegraph
   (proposed): agreed?
9. **An interrupted cast:** one interrupt sound for every spell, and the forming model bursting into its particles
   (proposed), or fading out?
10. **The Warrior:** no particles at all, not even a faint trail behind the weapon (proposed, as you said)?
11. **Curved missiles:** `curve` lets a projectile bend on its way to the aim point, so Arcane Missiles' three paths
    differ. That amends ADR 0052's "projectiles fly straight without gravity" (your rule) for the projectiles that
    name a curve, still without gravity: agreed (proposed), or do Arcane Missiles fly straight too, differing only
    in where they leave the hands?
12. **Melee during a Warrior spell's animation** (RLM-399's open point): blocked for the animation's length, as
    during a cast (proposed; it amends ADR 0020, which leaves instants free), or allowed, with a swing able to land
    a hit while Slam's animation plays?
