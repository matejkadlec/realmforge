# 0024. No GitHub labels: playtests run on qa1 and qa2 alone

Date: 2026-10-12

Status: Accepted (amends [0022](0022-playtest-before-the-pull-request.md) and the `needs-playtest` hold of
[0015](0015-merging-through-monitor-pr.md))

## Context

[0022](0022-playtest-before-the-pull-request.md) moved the playtest before the pull request and gave the owner
the answers `qa1` and `qa2`, but kept the GitHub label `needs-playtest` as a merge hold for a reconfirmation (a
fix after `qa1` that changes gameplay) and for gameplay pull requests outside the flow; the skills also still
removed the retired `needs-approval` label. The owner does not want GitHub labels at all: he answers in the
session, and the flow should read as one loop.

## Decision

- **No GitHub labels.** No skill, `CLAUDE.md` rule or flow step adds, removes or reads a label on a pull
  request.
- **One loop:** the agent implements the work.
  - **It needs a playtest:** the agent tells Matěj what to test and how, then stops and waits.
    - `qa1`: the agent opens the pull request and runs `monitor-pr` to the merge.
    - `qa2` with changes: the agent makes them, tells him what to retest, and stops again. This repeats until
      `qa1`.
  - **It needs none:** the agent opens the pull request and runs `monitor-pr` to the merge.
- **A reconfirmation** (a fix after `qa1` that changes gameplay) is the same loop: `monitor-pr` tells Matěj what
  to retest and waits for `qa1` or `qa2`. The hold is the tickets' Jira status (Pending User QA), not a label.
- All work goes through its RLM ticket ([0017](0017-jira-as-the-source-of-task-status.md)), so there is no
  gameplay pull request outside the flow left to label.

## Consequences

- The owner never touches a label; every playtest answer is `qa1` or `qa2` in a session.
- `monitor-pr` reads the playtest state from Jira (a ticket in Pending User QA holds the merge) and the played
  head from the `🎮 Playtest passed` record and the pull request description.
- The existing `needs-playtest` and `needs-approval` labels on GitHub are unused.
