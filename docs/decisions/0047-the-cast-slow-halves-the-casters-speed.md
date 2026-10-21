# 0047. The cast slow halves the caster's speed

Date: 2026-10-21

Status: Accepted (amends [0039](0039-a-cast-slows-its-caster-instead-of-moving-interrupting-it.md))

## Context

[0039](0039-a-cast-slows-its-caster-instead-of-moving-interrupting-it.md) slows a caster that moves during a
cast-time or channelled spell by Slowness III's amount, −45 % movement speed, as Matěj asked on 2026-10-19.

Matěj played C3b and C3c (RLM-25, RLM-26) on 2026-10-21. In his `qa2` he asked for a stronger slow: "I want exactly
50% slow", which no level of vanilla's Slowness gives (III is 45 %, IV is 60 %). 0039 is accepted, so this ADR
records the change ([0001](0001-record-architecture-decisions.md), [0030](0030-adrs-bind-agents-not-the-owner.md)).

## Decision

- **The cast slow is −50 % movement speed:** `realmforge:cast_slow` becomes an `ADD_MULTIPLIED_TOTAL` modifier of
  −0.5. Everything else in 0039 stands: moving never interrupts, the modifier is transient and no status effect, a
  spell with `castable_while_moving: true` leaves its caster at full speed, and players and mobs are slowed alike.

## Consequences

- A caster walks at half its speed during a cast.
- Sprinting still multiplies on top, as with any `ADD_MULTIPLIED_TOTAL` modifier. A player who sprints into a cast
  moves at 65 % of walking speed: +30 % for the sprint, then halved.
- 0039 keeps its accepted text; its "Slowness III's amount" is read with this ADR.
