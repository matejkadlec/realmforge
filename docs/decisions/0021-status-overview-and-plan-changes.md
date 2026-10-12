# 0021. A status overview ends every response; plan changes without a label

Date: 2026-10-12

Status: Accepted (amends the hold list of [0015](0015-merging-through-monitor-pr.md), the plan-change rule,
handoff and batch ledger of [0016](0016-jira-batch-delivery.md), and the `needs-approval` rule of
[0017](0017-jira-as-the-source-of-task-status.md); its `needs-playtest` note for playtests replaced by
[0022](0022-playtest-before-the-pull-request.md))

## Context

[0016](0016-jira-batch-delivery.md) and [0017](0017-jira-as-the-source-of-task-status.md) made every change to
`docs/roadmap.md` a plan change whose pull request carries the `needs-approval` label, which only the owner
removes. For the owner that meant one more trip to GitHub per plan change. The label approved after the fact what
the owner had usually decided before: the plan changes so far recorded his decision tickets (D8 in PR #17) or
carried out his own requests (PR #14).

The flow's handoffs also differed from run to run: "What to look at" led some reports, the batch ledger closed
only the last one, and a mid-run answer had none. In his other project every agent response ends with the same
overview: what to test and how to launch it when he has to test something, then the batch ledger. He keeps track
of all work from that overview alone, and wants the same here (RLM-86).

## Decision

- **Every response to the owner ends with a status overview** (`CLAUDE.md`, "Status overview"). It has one of
  two shapes:
  - **a manual test waits for him:** What to test (where to start, then numbered checks with the steps that
    reach each state), Launch commands (a block he can paste), Verification (optional), and the batch ledger:
    Merged into master, Pending CR, Pending User QA, Blocked or other;
  - **nothing waits for his test:** Merged into master (What, Review, Checks, Jira per pull request),
    Pending CR / Pending User QA in one line, and Blocked or other.
- "Waiting for Matěj" is no longer its own ledger group: decision tickets and tickets waiting on him go under
  "Blocked or other", with what each needs.
- **The `needs-approval` label is retired.** A plan change carries no label and holds no merge. Agents make a
  plan change only as the owner decided it: a Done decision ticket, his request, or a ticket he wrote that asks
  for it, and the pull request description names the change and that source. A plan change an agent finds worth
  making on its own is proposed to him under "Blocked or other" (or as a `decision` ticket) and is not published
  until he agrees.
- 0017's other rules stay: Jira holds task status, the roadmap holds the plan and every change to it is a plan
  change, and a new roadmap task's ticket waits in the backlog until the pull request that adds it merges.
- The holds of [0015](0015-merging-through-monitor-pr.md) are again `needs-playtest`, a changes-requested
  review, a Codex finding outside a thread, and anything the owner asked to hold.

## Consequences

- The owner approves a plan change when he decides it, not again on GitHub, and a plan pull request merges as
  soon as review and checks are clean.
- An agent can no longer publish a plan change of its own and wait for a veto: it has to ask first, which costs
  a round trip in the conversation instead of one on GitHub.
- Every response is a little longer, and each one shows the whole state: what merged, what waits for review,
  what waits for him, and what is blocked.
- A pull request that still carries `needs-approval` from before this decision is not held by it; `monitor-pr`
  removes the label.
- Playtests keep the `needs-playtest` label for now; the overview tells him to remove it to pass the test.
