# 0061. The target circle is a smooth light ring, and its highlight toggles

Date: 2026-10-29

Status: Accepted (amends [0055](0055-aim-assistance-for-a-players-enemy-spells.md))

## Context

[0055](0055-aim-assistance-for-a-players-enemy-spells.md) draws the target circle during a cast-time or channelled
spell only, never all the time (Q4), as "a ring of dust in the spell school's colour until the spell visuals (C6) give
it its art", and names RLM-468's key that shows it without a cast while it is held. In his playtest of RLM-468 on
2026-10-29, Matěj asked for the circle's look and for the key to become a toggle by default. Asked whether the circle
of a cast changes too, he answered that it does and stays light for every spell, and that the circle is a thin ring.
0055 is accepted, so this ADR records the change ([0001](0001-record-architecture-decisions.md),
[0030](0030-adrs-bind-agents-not-the-owner.md)).

## Decision

- **The look:** a thin, smooth, very light yellow ring lying flat on the ground under the mob, as wide as its hitbox
  and a margin, with no particles and no animation, the same during a cast and for the highlight, for every spell (no
  school's colour). It is as easy to see in the dark as in daylight and lights nothing around it. The client draws it
  as geometry, many segments of a thin core with a soft glow on both sides, unlit and unfogged, so it stays round at
  any size; no texture and no particles.
- **The key is Highlight Spell Target** (Left Alt by default), no longer "Show Target". A client option of the same
  name makes it a **toggle** (the default: a press switches the highlight on until the next press) or **Hold** (on
  while the key is held, the behaviour 0055 named).
- **Q4 stands for the cast's circle:** without the highlight, the circle shows only during a cast-time or channelled
  spell. The highlight is the player's own choice to see it all the time.

## Consequences

- The particle ring is gone, and with it RLM-474's guard against queueing rings while the game is paused: the ring is
  drawn every frame from the level's render state, paused or not.
- C6's spell visuals no longer owe the circle its art; they may still restyle it.
- 0055 keeps its accepted text; its target circle is read with this ADR.
