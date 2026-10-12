# 0016. Jira batch delivery with flow1 and flow2

Date: 2026-10-11

Status: Accepted (extends the agent workflow of [0011](0011-repository-layout-and-workflow.md) and builds on
[0015](0015-merging-through-monitor-pr.md); plan-change rule amended by
[0017](0017-jira-as-the-source-of-task-status.md) and [0021](0021-status-overview-and-plan-changes.md), handoff
and batch ledger by [0021](0021-status-overview-and-plan-changes.md))

## Context

The roadmap's tasks, the owner decisions D1-D11 and other work now live as tickets in Jira (space Realmforge, key
`RLM`, one active sprint), with the same statuses as the owner's other project: To Do, Next, In Progress, Pending
User QA, Pending CR, Done. There, two agent skills deliver sprint work: `flow1` takes a batch of tickets from the
sprint, and `flow2` turns a task description into a ticket and delivers it. Several agent sessions run at once in
separate worktrees, and in this repository the generated files (`mod/src/generated/`, `qa-mod/src/generated/`,
the shared lang files) are where their branches would collide. Gradle builds and GameTest servers from parallel
worktrees can also run the 12 GB WSL VM out of memory.

## Decision

- **Jira is the work queue**, the roadmap stays the plan. Roadmap tasks are tickets under their milestone epic;
  the roadmap's "after" order is mirrored as `Blocks` links ("is blocked by").
- **`flow1` and `flow2`** (`.claude/skills/`) are ported from the other project, with their rules in
  [docs/ai-development-flow.md](../ai-development-flow.md): reconciliation of merged tickets, priority as a
  recommendation, the Next queue, an atomic claim through Jira comments, one branch and worktree per batch, and
  one ready pull request per batch.
- **Dependencies are pulled into the batch:** an unfinished dependency in To Do, Next or the backlog joins the
  batch; one in flight elsewhere defers the dependent ticket.
- **Agents never implement `decision` tickets or tickets waiting on the owner**; every run lists them for him.
- **Gameplay changes end in Pending User QA:** the batch's pull request carries `needs-playtest`, and the owner's
  removal of the label is the passed playtest. A plan change (a roadmap change beyond ticking off tasks) carries
  `needs-approval`, which only the owner removes. `monitor-pr` keeps the tickets in step and moves them to Done
  after the merge.
- **Parallel worktrees are capped** as in the other project: four pending batches in the game domain, three in
  tooling, six in total, three waiting for a playtest, one gate-critical. In addition, two in-flight batches never
  regenerate the same generated file.
- **Gradle runs go through a queue** (`.claude/scripts/gradle-queue.sh`) that waits for a free slot and enough
  memory, and runs a build that died of memory again, instead of failing.
- **Jira access** is the Atlassian MCP server in `.mcp.json`.

## Consequences

- One flow1 run takes several tickets to a merged pull request without the owner, and the owner sees decisions
  and playtests as explicit waits instead of agents guessing.
- A batch may hold several roadmap tasks, so "one task, one PR" becomes "one task or one coherent batch, one PR".
- The generated-file rule lets only one batch with new player-facing content (lang entries) run at a time; the
  rest of the parallelism comes from tooling, docs and code-only work.
- RLM's workflow has only global transitions, so Jira itself refuses no stale move; the flow re-reads a ticket
  before every move, which narrows the race but cannot close it.
- Every agent comment in Jira and on GitHub carries the owner's name, so the flow marks its comments with labels
  (`🔒 Intake claim`, `🤖 Claude QA`) and tells the owner's approval apart by labels he removes.
