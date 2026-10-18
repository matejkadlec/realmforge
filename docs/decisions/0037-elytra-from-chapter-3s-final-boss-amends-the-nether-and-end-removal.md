# 0037. Elytra from chapter 3's final boss amend the Nether and End removal

Date: 2026-10-18

Status: Accepted (amends [0025](0025-remove-the-nether-and-end.md))

## Context

[0025](0025-remove-the-nether-and-end.md) removed the Nether and End in M4 and gave the elytra a new source:
"chapter 4, when the fastest mount tier unlocks (RLM-101), or later". No milestone names chapter 4 yet, so the End
would have been gone for a long stretch with no elytra at all.

Matěj settled the source on 2026-10-13 (Jira RLM-138, his decision recorded in its comments and in
[realm design, open question 8](../design/realms.md#open-questions)). The decision was first written as a dated note
inside 0025's item table. 0025 is accepted, so this ADR records the change instead
([0001](0001-record-architecture-decisions.md)).

## Decision

- **Elytra come from chapter 3's final boss**, as its drop or in an item frame in its room (like the End ship). They
  arrive at the step into chapter 4 and stay unobtainable until then.
- **The source ships in M4 with the removal of the End:** chapter 3 comes in M4.
- **The "every item is obtainable" check lists elytra as a known exception** only until that boss exists.

Everything else 0025 says about elytra stands: their own Curios slot that never takes the chestplate slot, and the
hook that lets them fly from it.

## Consequences

- 0025's item table keeps its accepted text; its elytra row is read with this ADR.
  [0032](0032-v1-realm-layout-amends-the-nether-and-end-removal.md) made chapter 3 the twin chapter (Sunwastes and
  Everwinter) with two guardians. Its note that the elytra still come from chapter 3's final boss refers to this
  decision, and which guardian gives them stays open ([realm design, open question 15](../design/realms.md#open-questions)).
- How fast elytra are against the mount tiers was decided separately (RLM-115), in 0025's consequences ("Elytra and
  flying mounts").
- The roadmap (M4) and the realm design already state this source.
