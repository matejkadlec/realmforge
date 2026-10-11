# 0017. Jira as the source of task status

Date: 2026-10-11

Status: Accepted (amends the plan-change rule of [0016](0016-jira-batch-delivery.md) and the hold list of
[0015](0015-merging-through-monitor-pr.md))

## Context

[0016](0016-jira-batch-delivery.md) made Jira the work queue and kept the roadmap as the plan, and the roadmap
still tracked progress: each finished task was ticked off (`[x]` plus the pull request link) in the pull request
that finished it. The same tasks also move through Jira's statuses (To Do, Next, In Progress, Pending CR, Done),
so task status lived in two places that would drift. A roadmap edit that was only a tick-off also had to be told
apart from a plan change, which waits for the owner's approval.

## Decision

- **Jira holds task status:** the ticket's status, sprint and assignee, and the pull requests that name its key.
  `monitor-pr` moves a batch's tickets to Done after the merge ([AI development flow](../ai-development-flow.md)).
- **The roadmap holds the plan:** scope, order ("after"), sizes, done-when and the owner decisions with their
  proposals. Every task, decision and backlog item names its Jira key, and every milestone its epic. The roadmap
  has no checkboxes and no per-task status.
- **Work on a roadmap task goes through its ticket:** `flow1`, `flow2`, or the flow's intake claim for direct
  work, with the key in the branch name and the pull request title.
- **Every change to `docs/roadmap.md` is a plan change:** its pull request carries the `needs-approval` label,
  which only the owner removes, and `monitor-pr` does not merge it while the label is set. A new task gets its
  ticket in the active sprint or the backlog and its key in the roadmap in the same pull request.
- **The hold list** of [0015](0015-merging-through-monitor-pr.md) therefore includes the `needs-approval` label,
  next to `needs-playtest`.

## Consequences

- No tick-off commits: a batch that finishes a roadmap task leaves the roadmap alone.
- "Is it done?" is a Jira question; the roadmap answers "what and in which order?".
- Every roadmap edit waits for the owner, including a milestone's status in the milestones table.
- A task, decision or backlog item in the roadmap whose key is missing in Jira is a gap to fix, not a second
  status to reconcile. The reverse is not: tickets that serve the plan without changing it (follow-ups, the
  parts of a split task, flow2 tickets for work inside an existing task) live under their milestone epic with no
  roadmap line.
