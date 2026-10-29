# 0057. A cast that slows its caster stops its sprint

Date: 2026-10-29

Status: Accepted (amends [0047](0047-the-cast-slow-halves-the-casters-speed.md))

## Context

[0047](0047-the-cast-slow-halves-the-casters-speed.md) halves the speed of a caster during a cast-time or channelled
spell that may not be cast while moving, and names a consequence: sprinting still multiplies on top, so a player who
sprints into a cast moves at 65 % of walking speed (+30 % for the sprint, then halved). Matěj answered on 2026-10-22,
before his `qa1` of C3b (RLM-25), that a cast should stop sprinting (RLM-371). On 2026-10-29 he decided that the sprint
key may not start a sprint again during such a cast, "like item use", until the cast ends. 0047 is accepted, so this
ADR records the change ([0001](0001-record-architecture-decisions.md), [0030](0030-adrs-bind-agents-not-the-owner.md)).

## Decision

- **A cast that slows its caster stops its sprint** when it begins: a cast-time or channelled spell that may not be
  cast while moving, players' and mobs' alike.
- **No sprint until the cast ends**, as with an item that slows its user (a bow, food): neither the sprint key, held or
  toggled, nor a double tap of forward starts one. Once the cast ends, however it ends, the player sprints again as
  vanilla allows: at once while the sprint key is still held or toggled on.
- **A spell with `castable_while_moving: true`** keeps the sprint, as it keeps the caster's full speed.
- **Both sides enforce it.** The server stops the sprint when the cast begins and at every tick of the cast, so a
  sprint the client asked for before it heard of the cast ends too. The client stops it, lets go of the sprint key and
  forgets the double tap in NeoForge's `MovementInputUpdateEvent`, which fires before vanilla starts a sprint; the
  double tap's timer (`LocalPlayer#sprintTriggerTime`) is opened by an access transformer, as no event or API reaches
  it ([architecture 19.3](../architecture.md#193-mixins-and-access-transformers)).

## Consequences

- A player who sprints into a cast moves at half their walking speed, as one who walks into it does. 0047's 65 %
  consequence no longer holds; the rest of 0047 and 0039 stands.
- Swimming is a sprint in vanilla, so a slowing cast also ends a swim; the player swims again after the cast.
- A player's client moves the player, so a modified client can still sprint during a cast, as it can ignore the slow.
- 0047 keeps its accepted text; its sprint consequence is read with this ADR.
