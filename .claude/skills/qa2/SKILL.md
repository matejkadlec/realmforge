---
name: qa2
description: Record Matěj's failed playtest of a batch in Pending User QA and revise the same branch for another playtest, without opening a pull request. Only playtest batches use it. Use when the user says "qa2" or reports problems from a playtest.
---

# qa2: changes required, revise

Follow `CLAUDE.md` and read [`docs/ai-development-flow.md`](../../../docs/ai-development-flow.md) completely before
revising; this skill may not weaken either. _qa2: changes required_ there holds the rules.

1. **Find the batch** as `qa1` does: the one this session handed off, the keys or branch Matěj names, or the only
   playtest batch in Pending User QA; with several and no hint, ask which one. Then move the tickets his feedback
   affects from Pending User QA to In Progress.
2. **Stay on the same branch:** no new branch and no replacement work. Reuse its worktree or create one from
   `origin/<branch>` after `git fetch origin`. Read the affected tickets' comments for context the handoff did not
   repeat (_Reading a ticket_).
3. **Apply his feedback**, and nothing beyond it. Feedback that changes the plan or needs a decision goes back to
   him under "Blocked or other" instead.
4. **Check and commit:** the affected checks and the full gate (_Implementation and verification_, Gradle through
   the _Gradle queue_), then commit.
5. **Push** the revision (`--force-with-lease` after a rebase) and read the remote head back. Open no pull request.
   A batch whose pull request waits for a reconfirmation (_Reconfirmation_) gets the new head on that pull
   request, and its `monitor-pr` run carries on.
6. **Record and move:** on each affected ticket, the revision (what changed, the new head, a What to test limited
   to it); then move the tickets back to Pending User QA. The batch keeps its playtest slot through the revision.
7. **Hand back** with the status overview from `CLAUDE.md` in its manual-test shape: _What to test_ limited to
   what the revision changed, the command that updates Matěj's checkout (_Launch commands_), and the closing line
   on `qa1` and `qa2`. Then wait for his next answer.
