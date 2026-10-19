# 0039. A cast slows its caster instead of moving interrupting it

Date: 2026-10-19

Status: Accepted (amends [0020](0020-cast-animations-and-melee-while-casting.md))

## Context

[0020](0020-cast-animations-and-melee-while-casting.md) decision 3 says that an attack does not interrupt a cast,
"moving and enemy interrupts do". C3a-2 and C3a-3 built that rule: a caster that moved more than 0.1 blocks by itself
lost a cast-time or channelled spell unless the spell was castable while moving (RLM-243 kept pushes such as a melee
hit's knockback out of it, and RLM-261 tried to tell a push apart from the server's side).

Matěj played RLM-261 on 2026-10-19 and decided against the rule in his `qa2`: "let's slow the player during cast,
rather than stopping the cast. slow will be equivalent to slowness III effect", because "not being able to walk at
all during cast feels really bad". 0020 is accepted, so this ADR records the change
([0001](0001-record-architecture-decisions.md), [0030](0030-adrs-bind-agents-not-the-owner.md)).

## Decision

- **Moving never interrupts a cast.** A cast-time or channelled spell that may not be cast while moving slows its
  caster instead, from the start of the cast until it ends, however it ends (completed, interrupted, failed).
- **The slow is Slowness III's amount:** −45 % movement speed (vanilla's 15 % a level, three levels), as a transient
  movement speed modifier `realmforge:cast_slow`, not as a status effect: no icon or particles, milk does not clear
  it, and it is never saved.
- **A spell with `castable_while_moving: true`** leaves its caster at full speed. The field keeps its name.
- **Players and mobs** are slowed alike.
- **Enemy interrupts stay:** a spell's `realmforge:knockback` still interrupts the target's cast, and so do death, a
  dimension change, logging out and a cancel. No melee while casting (0020 decision 3) stands.

## Consequences

- `CastEnd.MOVED` and the push detection of RLM-243 and RLM-261 go; architecture 11.2 describes the slow.
- A player's client moves the player, so a modified client can ignore the slow, as it can ignore vanilla's Slowness.
- Passives that lessen the slow, such as for caster classes, are a later design question (Matěj's note of
  2026-10-19, tracked in Jira).
- 0020's decision 3 keeps its accepted text; its "moving" interrupt is read with this ADR.
