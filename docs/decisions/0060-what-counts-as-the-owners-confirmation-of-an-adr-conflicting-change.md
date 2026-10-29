# 0060. What counts as the owner's confirmation of an ADR-conflicting change

Date: 2026-10-29

Status: Accepted (amends [0030](0030-adrs-bind-agents-not-the-owner.md))

## Context

[0030](0030-adrs-bind-agents-not-the-owner.md) lets the owner change a decision in one request: when he asks for a
change that conflicts with an accepted ADR, the agent names the ADR once, and "if he confirms, or already said that
it is decided", makes the change with the superseding or amending ADR. It describes a request made live in a
session. The independent review of PR #70 (RLM-215) found the gap (RLM-216): Matěj writes an RLM ticket that asks
for an ADR-conflicting change, and a later `flow1` run picks it up while he is not there to confirm. One session
would implement it without a confirmation; another would park it for him, which is the blocking 0030 forbids, and
the flow's "Tickets waiting on Matěj" does not cover it. Plan changes already have a rule for the same question
([0021](0021-status-overview-and-plan-changes.md)): his request, a ticket he wrote that asks for the change, or a
Done decision ticket.

Source: Matěj named RLM-216 in a session on 2026-10-29 (`flow1 cloud RLM-504 RLM-508 RLM-216 RLM-231`). The ticket
itself is a review finding. This ADR fills the gap in 0030 and reverses none of its rules, so no conflict notice
was due.

## Decision

- **The owner's decision for an ADR-conflicting change has the same sources as a plan change:**
  - **his request in a session:** the notice of 0030 once, and his confirmation (or his word that it is already
    decided) makes it the decision. Naming a ticket to a session (direct work, `flow1 <keys>`) is such a request:
    for a ticket that is not already his decision (below), the agent gives the notice and waits for his answer;
  - **a ticket he wrote that asks for the change:** its description or a comment records it as his request, with
    the date. Agents act under his Jira account, so the author field never tells;
  - **a Done decision ticket:** its comments record what he decided.
- **His ticket or a Done decision ticket is already his decision.** It does not wait on him: the run that takes it
  makes the change and writes the superseding or amending ADR in the same pull request, without asking again. The
  notice of 0030 moves into the pull request description, which names the conflicting ADR, its main consequence
  and the source (the ticket's key), and the new ADR records the same source.
- **A ticket an agent wrote is no confirmation by itself:** a follow-up, a review finding or a split-off part. The
  agent follows the ADR, and a change it still finds worth making goes to the owner first, as a question or a
  `decision` ticket, like an agent's own plan change. When he names such a ticket to a session, that is his request
  in the session, so the notice and his confirmation apply.

## Consequences

- A `flow1` run delivers the owner's ticket-borne ADR changes without a round trip, and the pull request and the
  new ADR say whose decision it was.
- Sessions handle such tickets the same way, so no ticket of his sits parked for a confirmation he already gave
  by writing it.
- An agent cannot turn its own finding into a decision by writing a ticket for it: telling his tickets from an
  agent's depends on the recorded request, which agents' tickets do not carry.
