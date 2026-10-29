# 0030. ADRs bind agents, not the owner

Date: 2026-10-15

Status: Accepted (amends [0001](0001-record-architecture-decisions.md)); amended by
[0060](0060-what-counts-as-the-owners-confirmation-of-an-adr-conflicting-change.md) (a ticket he wrote, or a Done
decision ticket, is his confirmation; an agent's ticket is not), 2026-10-29

## Context

[0001](0001-record-architecture-decisions.md) makes ADRs the durable record of what is decided, so that
sessions without memory neither re-open settled questions nor drift away from them. It does not say whom the
record binds. Read strictly, an accepted ADR could make an agent refuse, or keep pushing back on, a change the
owner asks for. But the owner makes the decisions that the ADRs record. The record is there to keep agents
consistent with him, not to overrule him.

## Decision

- **ADRs bind agents.** Agents follow accepted ADRs strictly in everything they decide on their own: design,
  code, tooling, the workflow, technical calls and follow-ups.
- **They do not bind Matěj.** When he asks for a change that conflicts with an accepted ADR, the agent never
  refuses or blocks on the ADR. It names the conflicting ADR and its main consequence once, in one or two
  sentences.
- **His confirmation is the decision.** If he confirms, or already said that it is decided, the agent treats his
  request as the decision. It makes the change and writes the superseding or amending ADR in the same pull
  request. That ADR records his request as the source, and the old ADR's status and index row name it
  (`Superseded by NNNN` or `amended by NNNN`).
- **Accepted ADRs are still never rewritten in place** ([0001](0001-record-architecture-decisions.md)): the
  new ADR carries the change, and the old one changes only its status line.

## Consequences

- The owner can change course in one request. He pays one short notice, not a debate, and the record stays
  current because the change and its ADR merge together.
- An agent can no longer cite an ADR to decline an explicit owner request. It still cannot depart from an ADR
  on its own initiative: a change it finds worth making goes to the owner first, like a plan change
  ([0021](0021-status-overview-and-plan-changes.md)).
- The notice is given once. Repeating it, or asking again after he confirmed, is the blocking this rule
  forbids.
