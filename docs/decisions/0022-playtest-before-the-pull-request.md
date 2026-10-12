# 0022. Playtest before the pull request: qa1 and qa2

Date: 2026-10-12

Status: Accepted (amends the playtest step of [0016](0016-jira-batch-delivery.md) and the `needs-playtest` note of
[0021](0021-status-overview-and-plan-changes.md); keeps the `needs-playtest` hold of
[0015](0015-merging-through-monitor-pr.md) for reconfirmations and for gameplay pull requests outside the flow);
that label dropped by [0024](0024-no-github-labels.md): a reconfirmation is held by its Jira status

## Context

Under [0016](0016-jira-batch-delivery.md) a gameplay batch opened its ready pull request with the `needs-playtest`
label, and the owner passed the playtest by removing the label on GitHub; a failed playtest went into the session
or a pull request comment. In his other project a batch that needs his manual test opens no pull request until
he has passed it, and he answers with two short replies: `qa1` (passed, publish it) and `qa2` (changes required,
revise it). He wants the same here: no pull request while a task is pending his test.

## Decision

- **A playtest batch opens no pull request before the playtest.** It pushes its branch (on `origin` since the
  claim, so he plays it from there), records the playtest handoff on its tickets, moves them to Pending User QA,
  keeps the branch and worktree, and the run ends at the handoff.
- **`qa1`** (`.claude/skills/qa1/`): the playtest passed. A session takes the preserved branch, rebases and re-runs
  the gate when `master` moved, publishes one ready pull request without `needs-playtest`, records the passed
  playtest on the tickets, moves them to Pending CR and runs `monitor-pr` to the merge.
- **`qa2`** (`.claude/skills/qa2/`): changes required. A session moves the affected tickets to In Progress, fixes
  what he reported on the same branch, pushes the revision without a pull request, and hands it back to Pending
  User QA for another playtest.
- **Only playtest batches use them.** AI-only batches still publish their pull request as soon as the gate
  passes.
- **Reconfirmation:** a fix after `qa1` that changes gameplay (a review fix, a check repair, a conflict
  resolution) brings `needs-playtest` back on the pull request and the tickets back to Pending User QA, until he
  answers `qa1` (or removes the label) or `qa2`. Commits that change nothing a player sees need no new playtest.
- `needs-playtest` stays the merge hold of [0015](0015-merging-through-monitor-pr.md) for that reconfirmation and
  for a gameplay pull request outside the flow. Only he clears it, by removing it or through `qa1`.

## Consequences

- He answers a playtest in the conversation, not on GitHub, and no gameplay pull request sits open while he has
  not played it.
- Codex review and CI start only after the playtest. Until then the played head has the local gate as its only
  evidence; a branch push starts no CI. A `qa2` revision costs no review round.
- A later review fix can still need a second playtest, but only when it changes gameplay.
- A playtest batch holds its playtest slot from its claim until `qa1` publishes it, through every `qa2` revision.
