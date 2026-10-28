# 0052. Spells aim with the crosshair

Date: 2026-10-25

Status: Accepted, amended by [0055](0055-aim-assistance-for-a-players-enemy-spells.md) (aim assistance)

## Context

C3b (RLM-25) shipped WoW's targeting: an `enemy` or `ally` spell needs a living entity under the crosshair within its
range (`client/spell/SpellTargetPicker`), or it is refused with "You have no target". In his playtest of C3b
(`qa2` of 2026-10-21, point 7) Matěj asked for the opposite:

> I don't want it so spells need a target in order to be cast; I want it like "Iron's Spells 'n Spellbooks": cast
> anytime, aim with the cursor (= the middle of the screen). I never wanted it like WoW in this matter, it feels bad
> for Minecraft; casting anytime and aiming feels way more natural and smooth.

He split it into three tickets and moved them into M1, before C4's class kits (2026-10-22): spells cast without a
target and aim with the crosshair (RLM-344), smart auto-aim for the spells that keep a target (RLM-345), and ground
spells that land at the crosshair with League of Legends' clamp cast (RLM-346). His rules for projectiles (point 7.3):
no gravity; a long range, maybe unlimited like arrows; a block stops one ("crashes"), and so does an entity that cannot
be damaged; it damages the first damageable mob it reaches, hostile or passive; it disappears beyond the render
distance, above the build height and in the void, so nothing flies forever.

## Decision

Spells are aimed, not targeted, unless their data keeps a target.

- **Direction spells** (`realmforge:direction`: bolts, missiles) cast at any time and fly where the crosshair points.
  The spell bar aims them at nothing, and the server takes the player's look, which is what the crosshair shows. A cast
  time or a channel follows the look until its effects run: a bolt leaves where the crosshair points as the cast
  completes, and a channel's every tick where it points then (as Iron's Spells casts a long spell at its end). A
  direction aimed at an entity or a point, as a mob's AI or `/qa spell cast_at` gives it, stays as it began.
- **Projectiles** fly straight without gravity, and without a lifetime unless their data sets one. They stop at the
  first living entity in their way that is neither the caster nor one of its allies (other players, its team, its
  pets), who let them pass: one that can be damaged, hostile or passive, takes their effects; one that cannot
  (invulnerable, a player in creative mode) stops them untouched, as does an entity of the tag they spare. A block stops
  them too. Nothing flies forever: a projectile is removed above the build height, where entities stop moving (beyond
  every player's simulation distance, which equals the render distance by default), and in the void (vanilla's removal
  64 blocks below the bottom).
- **Ground spells** (`realmforge:ground_point`) need no target and are never refused for range. They land on the block
  the crosshair hits within range, and beyond it, or at the sky, at the farthest point in range along that line (clamp
  cast). Either way they land on the ground below that point: a wall's face or a ceiling lands on the floor beneath it,
  a point in the air on the ground under it. The spell comes down from above, so the landing needs no line of sight. A
  ground point is placed when the cast begins and stays there, as in WoW and League of Legends.
- **Targeted spells** (`enemy`, `ally`) keep today's picking (the entity under the crosshair, the caster for an ally
  spell without one) until RLM-345's auto-aim, whose rules Matěj reviews in a design document before any code. Which
  spells stay targeted (heals, single-target debuffs) is that document's question; the class kits (C4) build their
  spells on the split it settles.

## Consequences

- A player can cast a bolt or a ground spell at any time; "You have no target" remains only for targeted spells.
- `SpellTarget` knows whether a direction follows the look (`SpellTarget#look`, `SpellTarget#now`), and
  `spell/GroundPoints` holds the clamp and the search for the ground. Architecture 11.2 and 11.3 describe both.
- A projectile's `lifetime_seconds` is optional and unlimited by default, so a bolt reaches as far as the player sees.
  A spell that wants a short reach sets one (the Hewn Chanter's Stoneform keeps its 1 s).
- A projectile at an ally passes through it, so a group's bolts never stop on a friend in front: an assumption about
  Matěj's "an entity that cannot be damaged" that his playtest confirms or changes.
- `SpellAimingGameTests` covers the projectile rules and the ground spell's landing, `GroundPointsTest` the clamp.
