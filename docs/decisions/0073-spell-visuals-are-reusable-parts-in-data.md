# 0073. Spell visuals are reusable parts in data, drawn with GeckoLib, our own particles and the cast layer

Date: 2026-11-10

Status: Accepted (amends [0020](0020-cast-animations-and-melee-while-casting.md): no melee during an instant spell's
animation)

## Context

Every spell needs its own look (epic RLM-434): a cast animation, what forms in the hands, a projectile's model,
texture and trajectory, particles, its impact and its sounds. Matěj wants them built from reusable parts, so dozens of
spells stay feasible: "make several different projectile animations, several different projectile models, several
different textures for models and for particles", combined per spell (2026-10-25). Until now a projectile had an
inline `appearance` (vanilla's shulker bullet model with a texture per spell) and `impact` (a sound and a block's
break particles), and only mobs showed a projectile forming (`forms_projectile`).

[docs/design/spell-visuals.md](../design/spell-visuals.md) (RLM-438) designed the kit, and Matěj approved it with
changes on 2026-10-29 (RLM-463): forming grows the model cube by cube, one forming size in first and third person,
no curved trajectory, light by look, particles that follow vanilla's Particles option except a telegraph's, a 0.5 s
animation for every instant spell with no melee during it, and an interrupted cast's model fading away. This ADR
files the design's draft as the engine's decision (RLM-439). The engine is delivered in three parts: the kit as
content and what the server runs (RLM-639), the GeckoLib models and the forming (RLM-640), and the instant spells'
animation with its no-melee window (RLM-641).

## Decision

1. **A spell combines parts named by id.** A look (an element's material, particle colours, particles and sounds), a
   model, a trajectory, a forming style, cast animations, an impact, an area style. All of it comes from `content/`:
   - `content/spell_looks/`, `content/spell_models/` and `content/spell_trajectories/` are synced datapack
     registries (`realmforge:spell_look`, `realmforge:spell_model`, `realmforge:spell_trajectory`);
   - `content/particles/` generates particle type registrations and their sprites;
   - a spell names its look, its forming style and an impact sound of its own in `visuals`, and each projectile names
     its `model` and its `trajectory`. The inline `appearance`, `impact` and `forms_projectile` are gone.
2. **Models are GeckoLib models** (Blockbench sources in `art/`), drawn by `GeoEntityRenderer` in flight and
   `GeoObjectRenderer` while forming, at most 64 cubes, with the light their look names: Fireball lights its
   surroundings, Frostbolt glows a little, others take the world's light. Until a model's art exists, its shape is the
   orb, three crossed slabs drawn with the look's material. Rejected: vanilla entity models, which are Java code per
   model, so every new model would need code; our own model format and renderer, which GeckoLib already is and the
   pack already ships.
3. **A look's texture is one tileable 16 x 16 material** that every model of the look maps onto, as a block's faces
   take a block texture: `textures/spell/material/<look>.png`, a generated placeholder (noise in the look's colours)
   until its art exists. Rejected: a texture painted per model and look, which multiplies textures by models.
4. **Particles are our own particle types,** a few greyscale shapes whose spawns carry their colour (vanilla's
   `ColorParticleOption`), tinted from the look's palette and spawned on the client from state it already has: a
   trail from a projectile's own flight, a burst from the entity event of its hit. No particle crosses the network.
   They follow vanilla's Particles option, except a telegraph's, which always show in full. Emitters are capped by
   validation (a trail 3 a block flown, a burst 24 at once), and nothing a player must react to is only particles.
5. **Trajectories are gameplay.** The server moves a projectile along a deterministic path, a pure function of the
   launch point, the aim, the ticks flown and a seed, and the client computes the same path from the projectile's
   spawn data (its trajectory, its aim, its seed, the ticks it has flown). Every projectile flies `straight`, as
   [ADR 0052](0052-spells-aim-with-the-crosshair.md) says; nothing flies under gravity, and a curve would be a
   decision of its own. Aim assistance's homing ([ADR 0055](0055-aim-assistance-for-a-players-enemy-spells.md))
   turns the line a projectile flies along on the server, which sends the turned velocity at once; a later
   trajectory kind adds its offset to that line.
6. **An impact is one entity event per hit,** a creature's or a block's, as a snowball's is. Each client bursts the
   look's particles from it, over the splash for a creature and off the block's face for a block, at the point where
   its own projectile's last two ticks of flight meet the creature or the block: a client's projectile is a tick or
   two off the server's, so the event carries no position and the client finds it. The server plays the impact's
   sound, the spell's own or its look's.
7. **Forming styles `grow`, `storm` and `hands`** run on the client from the cast payloads, drawn for every caster
   (players and mobs) by one renderer at the caster's hands, at one size in first and third person, tuned so it
   covers little of the first-person view; an interrupted cast's model fades away. `grow` is Matěj's choice, `storm`
   its fallback, `hands` for a spell without a formed model.
8. **Cast animations stay on the cast layer** of [ADR 0020](0020-cast-animations-and-melee-while-casting.md), started
   by `CastStartedPayload`, which every instant spell now sends too, marked as an instant: a 0.5 s push of both
   hands, or a weapon move, with no cast bar, and it ends after the animation's length (a swing's interrupt sends
   `CastEndedPayload`). **No melee during an instant spell's animation** (Matěj, 2026-10-29, RLM-463's question 12),
   which amends ADR 0020 decision 3 ("instant spells have no such window"): all three of its parts apply for the
   animation's window, the server's cancelled `AttackEntityEvent`, Better Combat's `bettercombat_disabled` tag and
   the client's cancelled attack key, and the `combat` attachment keeps the window. A spell's hit moments live in its
   data, checked against the animation's length.

## Consequences

- A new spell is mostly YAML; a new element is a look (colours, particles, a material); a new model is a Blockbench
  file.
- One material per look: Frostbolt's ice chunk and Ice Lance's lance share the frost texture, and a new model needs
  no texture of its own.
- The projectile's spawn carries more (its look, model, trajectory, aim, seed, ticks flown and burst radius), and the
  three registries are synced, so a client without the mod cannot join, as with spells already.
- The impact's particles no longer travel as particle packets; a client outside the projectile's tracking range sees
  no burst, as it sees no projectile.
- GeckoLib's API is one more place a Minecraft upgrade touches, as it already is for mobs. The projectile becomes a
  GeckoLib animatable with RLM-640.
- Until RLM-640, every model is the orb, `grow` and `storm` both show a mob's projectile growing as a whole, and
  `hands` shows nothing; until RLM-641, an instant spell has no animation and no melee window. How Fireball lights
  its surroundings (a light source of our own, or the dynamic lights mod that
  [ADR 0044](0044-third-party-mods-by-milestone.md) plans for M4) is settled with RLM-640.
- Stoneform and Astone's boulder moved to the kit with the players' spells: mobs and players use the same parts.
