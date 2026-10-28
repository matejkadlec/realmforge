# 0055. Aim assistance for a player's enemy spells

Date: 2026-10-27

Status: Accepted (amends [0052](0052-spells-aim-with-the-crosshair.md))

## Context

[0052](0052-spells-aim-with-the-crosshair.md) made spells aimed, not targeted: a bolt flies where the crosshair points,
and a targeted spell (`enemy`, `ally`) kept the entity under the crosshair until RLM-345's auto-aim, whose rules Matěj
was to review in a design document first. The proposal in [docs/design/spell-aiming.md](../design/spell-aiming.md) kept
a target only for spells that land on one creature, and auto-aimed those across three vertical strips of the screen.

Matěj answered its questions and four follow-ups on 2026-10-27 (RLM-345). His answers replace the screen-wide auto-aim
with **aim assistance**, a narrow cone around the crosshair, for every spell aimed at an enemy, the bolts included
(Q5): "A cast aimed slightly beside a mob targets it anyway, purely for QoL (far shots would be hard without it)". His
numbers, 0.5 block of help at 4 blocks up to 2 blocks at 16, are one angle. He raised the reach to 32 blocks ("16 seems
too restrictive"), and an assisted bolt is to curve toward its target so it hits a mob on the move. A bolt then no
longer flies purely where the crosshair points, which 0052 decided.

## Decision

- **The cone:** a hostile mob in sight whose hitbox the crosshair misses by at most its distance / 8 is the target,
  about 7 degrees around the crosshair: none right next to the player, 0.5 block at 4 blocks, 1 at 8, 2 at 16, 4 at
  32. One number (`AimAssist#MISS_PER_BLOCK`) tunes it. Assistance reaches 32 blocks; farther away a bolt still flies,
  unassisted.
- **Direct aim first:** the first mob under the crosshair, in front of every block, is the target, a passive one too
  (Q1).
- **Passive and calm neutral mobs** get no assistance at any distance; only direct aim reaches them (Q6). A neutral mob
  (a wolf, an iron golem, an enderman) counts as hostile only while it is angry at the player or targets them.
- **Several mobs in the cone:** the rules go in order, each breaking only the ties of those above (Q2): a boss's adds
  the fight wants killed first (W5 sets them), a boss (`#c:bosses`), below 25 % health, the current target (aimed at or
  hit in the last 10 s), a higher rank (the kill XP multiplier), a higher level, nearest to the crosshair.
- **Bolts are assisted as they leave** the caster's hands: an instant bolt at the cast, a cast time's at its end, a
  channel's every missile at its tick, each from where the player looks then. An assisted bolt leaves toward its
  target and turns toward it by at most 6 degrees a tick, a gentle curve that hits a mob on the move; a block still
  stops it, and it flies on straight once its target is dead, gone or behind it. Without a mob in the cone, a bolt
  flies where the crosshair points (0052). A player's bolt aimed at an enemy (a targeted spell) homes in on it too.
- **Targeted spells** (`enemy`, `ally`) from the spell bar take the entity the client finds under the crosshair (direct
  aim), else the server's pick in the cone, up to the spell's range or 32 blocks, whichever is shorter. An enemy spell
  without one is refused ("You have no target"); an ally spell takes the ally under or near the crosshair, else the
  caster. The screen-wide cast zone of the proposal is dropped (follow-up 2).
- **The server picks.** The proposal had the client pick because only it knew the screen's strips; the cone needs only
  the look, and a cast-time or channelled bolt leaves at a moment only the server sees. The server also checks a target
  the client names: in range, in sight, an enemy that can be damaged (or an ally), and in front of the player, the
  widest view a client can have.
- **The sticky target:** a player's cast-time or channelled spell on an enemy or an ally keeps the target it picked
  until the cast ends, even beyond the spell's range, and is interrupted at once when the target goes more than twice
  the range away. A target that dies during the cast fails it at once, and no new target is picked (Q3). Line of sight
  is still checked when the effects land. A spell that locks on keeps its target wherever it goes in the caster's
  dimension, as its data says, and mobs' casts keep what their spell's data sets.
- **The target circle:** during a cast-time or channelled spell only, never all the time (Q4, follow-up 4), a circle
  under the mob the spell will hit: the cast's target, or the mob a bolt that follows the crosshair would be assisted
  to now. The server names it to the caster's client. It is a ring of dust in the spell school's colour until the
  spell visuals (C6) give it its art.

## Consequences

- `spell/AimAssist` holds the cone, the ranking and the turn of a homing bolt (`AimAssistTest`). `spell/SpellBar`
  assists targeted spells, `ProjectileEffect` and `SpellProjectile` the bolts, `SpellCaster#stickyRange` and `Casting`
  the sticky target, and `Casting` sends `CastTargetPayload` for `client/spell/TargetCircle`. Architecture 11.2, 11.3,
  11.6 and 17.3 describe them; `SpellAimingGameTests` covers an assisted bolt beside the crosshair, one beyond the
  cone or toward a cow, one after a moving mob, the bar's assisted pick and its view check, and the sticky target.
- The kill XP multiplier lives in `character`, above `spell`, so `character` hands the rank to `AimAssist#rankBy`
  (architecture 4.4: lower asks higher through a provider).
- RLM-468 builds the two parts that need new player-facing text: the Show target key (Left Alt), which shows the
  circle under the mob an enemy spell would pick now, and the "Target lost" text for a sticky target that dies; until
  then such a cast ends with "You have no target".
- The cone's number and the turn rate are first values for Matěj's playtest; he did not know yet whether his numbers
  are too strict or too generous.
