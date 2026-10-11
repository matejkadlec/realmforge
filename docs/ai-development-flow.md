# AI development flow

> How agent sessions take work from Jira and deliver it: intake, batching, dependencies, claims, worktrees,
> checks, playtests, and the handoff to the pull request skills. Decision:
> [ADR 0016](decisions/0016-jira-batch-delivery.md). `CLAUDE.md` comes first; skills may add detail to this
> document but never weaken it or `CLAUDE.md`.

The shortcuts `flow1` (take a batch of sprint tickets and deliver it) and `flow2` (turn Matěj's task description
into a ticket and deliver it) live in [`.claude/skills/`](../.claude/skills/). They are entry points into this
document, not a second source of truth: every rule here also applies to direct work on a ticket. After publication
the pull request skills take over: [`monitor-pr`](../.claude/skills/monitor-pr/SKILL.md),
[`fix-pr`](../.claude/skills/fix-pr/SKILL.md), [`finalize-pr`](../.claude/skills/finalize-pr/SKILL.md) and
[ADR 0015](decisions/0015-merging-through-monitor-pr.md).

The roadmap ([roadmap.md](roadmap.md)) stays the plan and the source of the task order; Jira is the queue that
mirrors it, plus work outside the roadmap.

## Jira

### Project facts

| What | Value |
| --- | --- |
| Site | `envelopment.atlassian.net`, cloud id `1baac1c3-be5c-48dd-b9fc-941b03a811b3` |
| Project | space Realmforge, key `RLM`. Scope every read and write to `RLM` and check the key prefix before each write |
| Tools | the Atlassian MCP server `atlassian` from `.mcp.json` ([setup](dev-setup.md#5-claude-code)); a cloud session uses the claude.ai Atlassian connector, which has the same tools |
| Sprint | field `customfield_10020`, which takes the numeric sprint id. Read the active sprint from Jira every time: JQL `project = RLM AND sprint in openSprints()`, the `customfield_10020` entry with `state: active`. Never reuse an old id |
| Epics | RLM-6 to RLM-12: milestones M1 to M7. RLM-1: developer workflow and tooling outside the roadmap. RLM-30: Storyline (realms, lore) |
| Statuses | To Do, Next, In Progress, Pending User QA, Pending CR, Done, Rejected |
| Transitions | all global (Jira accepts each one from every status): `11` To Do, `21` Next, `31` In Progress, `41` Pending User QA, `51` Pending CR, `61` Rejected, `71` Done |
| Dependencies | link type `Blocks`. "A is blocked by B" is `createIssueLink` with `type: Blocks`, `inwardIssue: B`, `outwardIssue: A`. A ticket's `issuelinks` lists each blocker as an `inwardIssue` of type `Blocks` |
| Account | agents act under Matěj's Jira and GitHub accounts, so an author field never tells an agent's comment from his |

### Issues

- Search `RLM` for an equivalent ticket before creating one (JQL `project = RLM AND text ~ "<terms>"`), and read
  each candidate with its comments.
- A ticket created from Matěj's prompt goes into the active sprint unless he asks for the backlog or another
  sprint.
- Follow the existing tickets' format:
  - **Type:** Task for planned work, Bug only for confirmed broken behavior, Epic only when Matěj asks for one.
  - **Title:** a roadmap task keeps its roadmap id (`F1: Feature skeleton`); other tickets use sentence case
    (`Pack: replace beta library builds with stable releases`).
  - **Parent:** the milestone epic the work belongs to (RLM-6 for M1). Work outside the roadmap goes under the
    epic whose description covers it (RLM-1 for workflow and tooling, RLM-30 for storyline).
  - **Labels:** lowercase. One or more area labels, preferring existing ones (`foundations`, `pipeline`,
    `character`, `stats`, `combat`, `spells`, `talents`, `world`, `mobs`, `dungeons`, `boss`, `quests`, `assets`,
    `ci`, `release`, `tooling`, `docs`, `ui`, `realms`, `storyline`, `lore`, `design`, `research`); the milestone
    label of its epic (`m1` to `m7`); and `spike`, `decision` or `backlog` where they apply.
  - **Priority:** High, Medium or Low, relative to the sprint.
  - **Description:** English Markdown in one of the two existing shapes, both with a "Done when" section:
    - a roadmap task: `**Size:** S|M|L. After <task> (RLM-<n>). Waits for decision <Dn> (RLM-<n>).`, a paragraph
      on what to build, `**Done when:** ...`, then `Roadmap: [...]. Architecture: [...]` links;
    - any other task: `## Context`, `## Task` (or `## Proposal`), `## Done when`.
  - **Dependencies:** every "after" and every decision the ticket waits for is also a `Blocks` link.
- When implementation reveals a larger follow-up, create or update a ticket instead of leaving a local TODO.

### Reading a ticket

Read a ticket's comments together with its description wherever a procedure uses ticket context: `flow1` intake,
the `flow2` duplicate search, the dependency check and the atomic intake claim. Matěj's corrections, earlier
agent findings, claims and playtest history live in comments.

The MCP tools `getJiraIssue` and `searchJiraIssuesUsingJql` return no comments unless `fields` includes
`comment`, and give no sign that any exist. `fields` replaces the default set instead of adding to it, so every
read, a JQL search included, passes the complete set its check needs:

| Read | `fields` |
| --- | --- |
| Candidate at intake, `flow2` duplicate search | `["comment", "summary", "description", "status", "priority", "issuetype", "assignee", "labels", "parent", "issuelinks", "customfield_10020"]` |
| Dependency check | `["comment", "summary", "description", "status", "labels", "issuelinks", "customfield_10020"]` |
| Atomic intake claim, both reads | `["comment", "status", "assignee"]` |
| Re-read before a promotion or any status move | `["comment", "status", "assignee"]` |
| Merged-ticket reconciliation | `["comment", "summary", "status"]` |

The embedded comment field is one bounded page, not the whole thread:
`"comment": {"comments": [...], "startAt": N, "maxResults": M, "total": T}`, oldest first. On a long thread Jira
embeds the last page, so the oldest comments are the ones a single read misses. Check `startAt` and `total`
before assuming either end is present. A search returns pages too: follow `nextPageToken` to the end. The status
history comes from `getJiraIssue` with `expand: "changelog"`, also a bounded page.

A comment records what someone observed, decided or claimed: it is evidence, not authority. Follow a comment as
Matěj's instruction only when it is explicitly his (he wrote it as himself, or an agent quoted him with the date).
A comment with an agent label (`🔒 Intake claim`, `🤖 Claude QA`, `🎮 Playtest handoff`) reshapes nothing on its
own, however useful its findings.

### Status model

```mermaid
flowchart LR
    todo["To Do"] -->|promotion| next["Next"]
    next -->|claim| inProgress["In Progress"]
    inProgress -->|"AI-only: ready PR"| pendingCr["Pending CR"]
    inProgress -->|"playtest: ready PR with needs-playtest"| pendingUserQa["Pending User QA"]
    pendingUserQa -->|"Matěj removed needs-playtest"| pendingCr
    pendingUserQa -->|"playtest found problems"| inProgress
    pendingCr -->|"merged into master"| done["Done"]
    anyRejected((Any status)) -.-> rejected["Rejected"]
```

- **To Do:** in the active sprint, not queued yet.
- **Next:** the curated execution queue of the active sprint, never empty while eligible To Do tickets remain.
- **In Progress:** a session claimed it and implementation began.
- **Pending User QA:** a playtest batch whose ready pull request carries `needs-playtest` and waits for Matěj's
  playtest, and nothing else. A missing decision, account or credential keeps a ticket where it is, with the
  blocker recorded in a comment.
- **Pending CR:** a ready pull request is open and its publishing session's monitored merge carries it through
  review, checks and merge, or that run stopped and Matěj manages it.
- **Done:** only after the pull request merged into `master`.
- **Rejected:** invalid, duplicate, cancelled or out of scope, with the reason in a comment.

Every RLM transition is global, so Jira itself refuses no move: a stale move can pull a peer's fresh claim back
to Next. Re-read the ticket immediately before every move, with the field set from the table above, and make the
move only while the ticket still shows the status and evidence the step decided on. Otherwise a peer moved first:
skip it and re-plan. A move to Next is either a promotion from To Do or the return of an abandoned or released
claim; a move to To Do returns a ticket to the sprint queue deliberately; a move to Done is the merge, or an
administrative correction Matěj asks for.

### Reconciling merged tickets

`monitor-pr` moves its pull request's tickets to Done after the merge, but a pull request merged after its run
stopped (by Matěj, or by a later session) leaves its tickets in Pending CR or Pending User QA, and a Jira write
that failed at publication and again after the merge leaves them In Progress. Every later intake would read them
as in flight. `flow1` and `flow2` therefore run this pass first, before creating,
selecting or claiming anything. It reads merge state that already exists, never waits for a merge, and changes
nothing else.

1. List every `RLM` ticket in In Progress, Pending CR or Pending User QA, whatever its sprint, with the
   reconciliation field set.
2. Associate pull requests in `matejkadlec/realmforge` only from evidence of the ticket's current work cycle,
   which begins when the ticket last entered Next (its status history). Evidence is a pull request opened since
   then whose head branch is the batch branch named in the ticket's winning claim comment (the earliest one since
   it last entered Next, as in the [atomic intake claim](#atomic-intake-claim)), or whose title names the ticket
   key. A key named only in a body, a commit message or another comment is not evidence. A pull request that
   reverts an associated one (its title or a commit subject is `Revert "<exact title>"`, or a commit carries
   `This reverts commit <its merge commit>`, opened after that merge) is associated too.
3. Move the ticket to Done only when an associated pull request merged into `master`, none is still open, and no
   merged pull request reverts an associated one. Re-read the ticket immediately before the move and skip it
   unless it is still in the status it was listed with.
4. Otherwise leave it: no associated pull request, one still open, a merge into another branch, or a merged
   revert (whether the change is still on `master` is then Matěj's call). A ticket whose associated pull requests
   all closed unmerged is no longer in flight: it holds no slot, and the ledger lists it under "Blocked or other"
   for Matěj to send back to Next or reject.

A batch pull request reconciles every ticket associated with it. The pass changes that one status and nothing
else. A ticket that cannot be read or matched is skipped and never stops the run. The run's batch ledger lists
every reconciled key under "Merged into master" with its pull request.

### Priority and the Next queue

Priority is a recommendation relative to the sprint: any active-sprint ticket outranks future-sprint work
whatever its displayed priority. Within Next, look at High before Medium and Low, subject to dependencies,
conflicts, ownership, QA mode, coherent batching and safe verification. Within one priority, follow the roadmap
order: the current milestone's spikes first, then its phases in order. Reassess active-sprint priorities only on
concrete evidence (a dependency, a risk, newly found scope) and keep the change inside the active sprint.

Every session taking work from Next maintains the queue:

1. Work only inside the active sprint, and select implementation work from Next. Do not skip a suitable Next
   ticket because a To Do ticket looks more interesting.
2. If Next is empty while eligible To Do tickets remain, move the most appropriate ticket or coherent batch to
   Next, and only then select it.
3. If taking the selection would empty Next while eligible To Do work remains, first promote at least one
   suitable following ticket or batch.
4. Promote compatible eligible To Do tickets to fill an AI-only batch toward the expected scale
   ([Batch selection](#batch-selection)).
5. Move from Next to In Progress only when implementation begins (the claim).

These are the only promotions, plus two exceptions: [Dependencies](#dependencies) pulls in a dependency of a
selected ticket, and `flow2` promotes and selects the tickets it creates or picks for Matěj's request directly,
whatever else Next holds, because he asked for that work now. Never promote other Backlog or future-sprint
work. A ticket is eligible when it is not [waiting on Matěj](#tickets-waiting-on-matěj) and none of its
dependencies is in flight elsewhere. Intake works from a snapshot a peer may already have overtaken, so
immediately before each promotion re-read the ticket and promote it only while it is still To Do. A ticket that
already left To Do belongs to a peer: skip it and re-plan.

Before every handoff boundary (publishing or updating a ready pull request, or ending a run), re-inspect Next and
To Do with what the batch taught you, and replenish Next while eligible To Do work remains. This never authorizes
selecting new work after the pull request is published. When a session changes a priority, its handoff names the
ticket and the reason.

Continuing work this session already owns, linked remediation, and resumed work do not pass back through Next.

### Dependencies

A ticket depends on another when a `Blocks` link names the other as its blocker, or when
[roadmap.md](roadmap.md) or the ticket's description says "after X" or "waits for decision Dn". The links were
created from the roadmap on 2026-10-11; when the two sources disagree, both count, and the batch adds the
missing link. A dependency is met when its ticket is Done.

For each unmet dependency of a selected ticket, in this order:

1. **Waiting on Matěj** (a `decision` ticket, or one [waiting on Matěj](#tickets-waiting-on-matěj)): the ticket
   cannot be implemented. Leave it, and list it with the dependency.
2. **In flight elsewhere** (In Progress, Pending User QA or Pending CR, or a branch or pull request of another
   session): it belongs to that batch. Defer the ticket. A stacked branch on the dependency's branch is allowed
   only when it is necessary and safe; its pull request targets that branch and says so, and `monitor-pr`
   retargets it once the base has merged.
3. **Otherwise** (To Do, Next, the Jira backlog or a future sprint): pull it into the batch. Put it into the
   active sprint when it is outside it (the sprint field), promote it from To Do to Next, and claim it with the
   rest of the batch. Check its own dependencies the same way, recursively.

- Pulling in a dependency is the one case where Backlog or future-sprint work enters Next without Matěj.
- Inside the batch, dependencies are implemented and committed first.
- When the whole chain does not fit one batch ([Batch selection](#batch-selection)), the batch takes the chain
  from the bottom (the unblocked dependencies, as many as fit) and the dependent ticket stays in Next for a later
  batch. The batch announcement says so.
- A batch has one QA mode: if the dependent ticket or a pulled-in dependency needs a playtest, the whole batch is
  a playtest batch (one pull request, one label).
- The batch announcement and the claim comment of each pulled-in ticket name the ticket that needed it.

### Tickets waiting on Matěj

Agents never implement a ticket labelled `decision`, or one waiting on Matěj. A ticket waits on him when it cannot
be started or finished without something only he can give: a decision or a pick between options, an account,
credentials or credits, approval to install a tool or MCP server, content he builds himself (rooms in creative),
or his own playthrough. The description usually says so ("Decision for Matěj", "Owner decision", "Needs Matěj",
"Needs from Matěj"), and so does an unmet `Blocks` link to a decision ticket or to another waiting ticket. A review
or playtest at the end of the work does not count: that is the playtest hold.

- Such tickets are neither selected nor promoted; they stay in To Do.
- Every run lists them in its ledger under "Waiting for Matěj", one line each: a decision ticket with its proposal
  and the tickets it blocks; any other ticket with what it needs.
- A decision is made when its ticket is Done; its comments record what Matěj decided, and the implementing batch
  follows that. Writing the outcome into the roadmap's open decisions is a plan change
  ([Publication](#publication)).

### QA evidence

Each ticket's comments hold its evidence: the claim, the `🤖 Claude QA` summary that
[publication](#publication) records (what was verified and how, the pull request, its head), the
`🎮 Playtest handoff` and each revision, and the updates `monitor-pr` records after its own pushes. Matěj reads
the session's handoff first and opens a ticket only for detail.

## QA classification

Classify every candidate ticket before batching it. Matěj plays every gameplay change before it merges
([ADR 0015](decisions/0015-merging-through-monitor-pr.md)); everything else merges on automated evidence.

**AI-only:** the ticket changes nothing a player sees, hears or does in the game. This covers tooling, CI, docs,
ADRs, research and design documents, the content pipeline when the game data it produces is unchanged, tests and
GameTests, the dev-only QA mod, refactors, and foundations with no player-facing behavior yet (packages,
registries, attachments, payloads).

**Playtest** (ends in Pending User QA): the ticket changes gameplay: items, blocks, mobs, bosses, spells and
effects, stats, numbers and balance, XP and levels, loot, worldgen, dimensions and dungeons, quests and their
texts, screens, HUD and key bindings, textures, models, animations, particles and sounds, world rules, and pack
changes a player notices (a mod added, removed or reconfigured). Covering it with GameTests does not make it
AI-only: the playtest is Matěj's judgment of how it plays and looks, never a substitute for automated tests.

**Mixed scope:** partition candidates by QA mode before grouping them by similarity, and never mix modes only to
make a batch bigger. Split a ticket whose gameplay and non-gameplay parts separate cleanly when that helps; when
they cannot be separated, the whole batch is a playtest batch. Pulled-in dependencies follow
[Dependencies](#dependencies).

### Playtest handoff

1. Run the full gate ([Implementation and verification](#implementation-and-verification)).
2. Commit the coherent work and publish it as one ready pull request with the `needs-playtest` label
   ([Publication](#publication)).
3. On each ticket, record the `🤖 Claude QA` summary and the `🎮 Playtest handoff`: the pull request, its head,
   [What to look at](#what-to-look-at) and the [playtest commands](#playtest-commands). Then move the tickets to
   Pending User QA.
4. Give Matěj the handoff, leading with What to look at, then the playtest commands.
5. Run `monitor-pr` on the pull request in the same run. It carries review, threads and checks, and holds the
   merge while the label is set. Its check-ins keep watching for Matěj's answer.
6. **Playtest passed:** Matěj removes the label. `monitor-pr` moves the tickets to Pending CR, merges once
   everything else is clean, and moves them to Done.
7. **Playtest failed:** Matěj says what is wrong in the session or on the pull request, the channels
   `monitor-pr` watches (a comment on a ticket alone goes unseen), and leaves the label. Move the affected
   tickets to In Progress, fix on the same branch (the session that handed off or any later one), run the affected
   checks, push, record the revision on each ticket, move them back to Pending User QA, and hand back a What to
   look at limited to the revision, with the command that updates his checkout. The label stays until he removes
   it.

While Matěj plays from the batch's worktree, run no Gradle task in it.

### Playtest commands

Every handoff ends with complete commands Matěj can paste into his WSL terminal.

- **The dev client** (core mod and QA mod, offline player `Dev`, QA commands available; opens through WSLg):

  ```bash
  cd ~/projects/realmforge-worktrees/<dir> && ./gradlew runClient
  ```

- **The full pack in Prism**, when the change only shows with the pack, or `pack/` changed: install the jar,
  and serve the pack from the worktree when `pack/` changed (the local pre-launch command in
  [dev setup](dev-setup.md#4-playing-the-pack-on-windows-prism-launcher--packwiz-installer)). The QA mod is never
  installed into Prism, so QA commands are missing there.

  ```bash
  cd ~/projects/realmforge-worktrees/<dir> && ./gradlew :mod:installToPrism
  cd ~/projects/realmforge-worktrees/<dir>/pack && packwiz serve
  ```

- **A branch from a cloud session** exists only on `origin`, so the commands first create the worktree:

  ```bash
  cd ~/projects/realmforge && git fetch origin <branch> && git worktree add ../realmforge-worktrees/<dir> <branch>
  cd ~/projects/realmforge-worktrees/<dir> && uv sync && ./gradlew runClient
  ```

- **A revision** gives the command that updates an existing checkout: `git pull --ff-only`, or after a rebase
  `git fetch origin && git reset --keep origin/<branch>`, which refuses to discard local edits.

### Reaching the state

For every state Matěj must judge (a class, a level, a spell's cooldown, a boss phase, a dungeon room, a locked
dimension), the handoff gives the exact commands that reach it in a new world: vanilla commands (`/give Dev
realmforge:<id>`, `/gamemode`, `/tp`, `/summon`), our own commands, and the QA mod's `/qa` commands in the dev
client.

- When a state is tedious to reach (a high level, a late boss phase), the batch adds or extends a `/qa` command
  in `qa-mod/` (dev-only, merged with the batch) instead of asking Matěj to grind.
- Verify each command where an agent can: a GameTest that runs it, or the dev server console
  (`./gradlew runServer`).
- When a state exists only in an automated test, say so in What to look at.

### What to look at

Every handoff that returns work to Matěj (a playtest handoff, a revision, a published pull request) leads with
"What to look at". He reads it instead of the tickets, so each item pairs what changed with how to see it, in a
sentence or two: "Cast Frostbolt on the training dummy, because its slow is new", or "Mobs scale with distance;
the best way to see it is `/tp Dev 1000 ~ 0` and the level above their heads". An item names where to go, the
state and how to reach it, first or third person where it matters, and what he should see or feel; it says when
an effect is subtle or shows only in an automated test. A behavior change he must decide on comes first and says
so. For AI-only work it says what he can observe, if anything, and otherwise what the automated evidence covers.
Files, implementation detail and the full verification stay in the ticket comments.

## Intake and conflict control

Intake opens with [Reconciling merged tickets](#reconciling-merged-tickets). Then, before selecting or starting
work, inspect:

- the active sprint: Next, the eligible To Do tickets, priorities, the tickets
  [waiting on Matěj](#tickets-waiting-on-matěj);
- tickets in In Progress, Pending User QA and Pending CR, and their claim comments;
- open pull requests (`gh pr list --repo matejkadlec/realmforge --state open`), local and remote branches
  (`git branch -a` after `git fetch --prune origin`), and worktrees (`git worktree list`);
- the main checkout's branch and uncommitted changes, without touching them.

Read every candidate with the candidate field set: a shorter list silently drops fields the selection ranks or
checks, and a candidate without `priority` ranks out of order. A comment can carry the dependency, correction or
earlier finding that decides whether the ticket is eligible, how it is scoped or which batch it joins; the batch
announcement reflects anything material found there.

Build a conflict map from the in-flight batches: the features, packages, files, content types, registries,
generated files and GameTests each one changes (read a branch's diff with
`git diff --name-only origin/master...<branch>`, a local worktree's with `git -C <worktree> status --porcelain`,
and predict a claimed ticket's from its description). In-flight ownership is every In Progress, Pending User QA
and Pending CR ticket, every open pull request and every active branch or worktree, except a ticket whose
associated pull requests all closed unmerged ([reconciliation](#reconciling-merged-tickets) step 4) and its
branch. Do not select work that overlaps it unless it directly continues the same branch. If no safe non-conflicting work exists, stop and report
the conflict instead of forcing a risky batch.

### Conflict domains

Every batch belongs to at least one conflict domain, decided by the primary paths it will change:

- **game:** `mod/`, `qa-mod/`, `content/`, `tools/`, `art/` and `pack/`: everything that ends up in the jar or
  the pack, and the pipeline that generates it.
- **tooling:** `.github/`, `.claude/`, `docs/` and every other tracked path neither domain names.

The documents a change must keep current in the same batch are accompanying updates and add no domain:
`docs/architecture.md`, the roadmap tick-off, `content/README.md`, `tools/README.md`, `pack/README.md`,
`docs/dev-setup.md` and a new ADR with its index row. A batch whose main work is a document (workflow wording, a
design, an ADR, research) is a tooling batch. Ticking off a roadmap task and adding a row to the ADR index are
line-local edits and never count as an overlap; an ADR number does: take the next number that no `master` file,
open pull request or in-flight branch uses.

**Shared-root files** belong to every domain at once: `CLAUDE.md`, `README.md`, `.gitignore`, `.gitattributes`,
`.mcp.json` and `.claude/settings.json`. A batch that changes one occupies a slot in every domain.

**Gate-critical** batches change the files that define the gate every other branch must pass:
`.github/workflows/ci.yml`, `settings.gradle`, the root `build.gradle`, `gradle.properties`, `gradle/wrapper/`,
`pyproject.toml`, `.claude/hooks/validate-content.sh` and `.claude/scripts/gradle-queue.sh`. The list is exact:
at most one gate-critical batch is in flight in the whole repository. A change to `mod/build.gradle` or
`qa-mod/build.gradle` (a new dependency, say) is an ordinary file overlap the conflict map already refuses.

**Generated files** are the main merge-conflict risk here. Every batch lists the generated files it will
regenerate: everything under `mod/src/generated/`, `qa-mod/src/generated/`, `tools/schemas/` and
`tools/id_universe/`, plus `pack/index.toml` and `uv.lock`. Predict them from the content types and generators
the batch touches (`tools/realmforge_tools/generate/`; every player-facing text lands in the generated
`lang/en_us.json` and `lang/cs_cz.json`), and confirm with the diff after `uv run rf gen`. Never select a batch
that regenerates a file an in-flight batch also regenerates, until that batch has merged. In practice this lets
one batch with new player-facing content run at a time. When implementation shows the batch must regenerate such
a file after all, record the overlap on its tickets, finish the batch, and publish it; whichever pull request
merges second rebases and regenerates (`monitor-pr` step 2), never hand-merging a generated file.

Decide the domains, gate-critical files and generated files before the claim; the claim comment states them, so
peers can count the batch. If the scope later grows into another domain, a shared-root file, a gate-critical file
or a generated file, add a scope comment with the new values to each ticket, re-check the caps in
[Concurrency and run boundaries](#concurrency-and-run-boundaries) as in [Slot race](#slot-race), and stop
selecting further work if the batch no longer fits. An AI-only batch whose scope turns into a gameplay change
needs a free playtest slot first: add a QA-mode comment naming Playtest to each of its tickets and re-count as in
[Slot race](#slot-race); without a slot, split the gameplay part into its own ticket, or keep the batch In
Progress on its branch until a slot frees.

### Atomic intake claim

Intake reads a snapshot, and a peer session can select the same ticket before work starts, so claim every newly
selected ticket in the batch before writing any implementation. Work this session already owns through an
existing branch or worktree (resumed work, linked remediation, a playtest revision) is not re-claimed. Claiming a
batch is the moment its implementation begins, so every ticket claimed for it moves to In Progress together.

Per ticket, in order:

1. Re-read it from Jira with the claim field set; never claim from the intake snapshot.
2. Confirm that it is still in Next, unassigned or assigned to the acting account, and that no local branch,
   worktree, remote branch or open pull request names its key (`rlm-<n>` in a branch name, `RLM-<n>` in a title,
   as a whole token, so `rlm-17` does not match `rlm-170`). Two kinds of branch do not count: the branch and
   worktree this session already created for this batch at an earlier ticket's claim (they name every key of the
   batch), and a branch that a claim-release comment on this ticket names (a released claim's branch, which may
   stay on the remote).
3. Assign it to the acting account, move it to In Progress, and add one claim comment:

   ```text
   🔒 Intake claim
   Branch: feat/rlm-17-rlm-18-feature-skeleton
   Batch: RLM-17, RLM-18
   QA mode: AI-only
   Domains: game
   Gate-critical: no
   Generated: none
   ```

   `Generated` lists the generated files the batch will regenerate ([Conflict domains](#conflict-domains)), or
   `none`. Add "Pulled in as a dependency of RLM-<n>" when that applies. Peer sessions share the acting account,
   so the comment is the only claim token that tells them apart: the earliest claim comment recorded since the
   ticket last entered Next wins, and the lower comment id breaks a tie on the same timestamp. Older comments do
   not count, so an interrupted run's claim never defeats a later claimant.
4. Re-verify: re-read the ticket with the same fields, and list local branches, worktrees, remote branches and
   open pull requests again, ignoring the ones this session created. The claim holds only if the ticket is In
   Progress under the acting account, this session's claim comment is the earliest that counts, no branch,
   worktree or pull request naming its key appeared without a claim comment behind it, and a new batch still keeps
   its place under every cap ([Slot race](#slot-race)). A peer artifact backed by a later claim comment never
   defeats this claim.
5. Only once the claim holds, create the batch's branch and worktree from the current `origin/master` at its first
   claim ([Branches and worktrees](#branches-and-worktrees)), push the new branch at once, and reuse them for
   every further ticket of the batch. A batch has exactly one branch. Creating it any earlier would let a git name
   collision, not the claim order, decide a race. The push makes the claim visible to sessions on other machines
   and in the cloud, which cannot see this machine's branches; without it, a live run's claim would look
   abandoned to them after thirty minutes. If the push fails, the claim does not hold: end the batch as for a lost
   claim (below).

A ticket is an **abandoned claim**, from a run that died between steps 3 and 5, only when all of these hold: it
is In Progress, the branch its earliest counting claim comment names exists neither locally nor on the remote, no
worktree or open pull request names its key, and that comment is more than thirty minutes old. Step 3 is several
Jira writes, so a run can also die after the move to In Progress and before its comment: without any counting
claim comment, the ticket is abandoned when no local or remote branch, worktree or open pull request names its key
and its move to In Progress (status history) is more than thirty minutes old. Recovery is arbitrated like a
claim: add one claim-recovery comment naming the abandoned claim comment (or the bare move), re-read the ticket
directly (an issue read, not a search, which may lag), and only while all four conditions still hold and your
recovery comment is the earliest counting one since that claim comment (or that move), move the ticket to Next
and claim it normally. A recovery comment older than thirty minutes no longer counts. Anything short of all four
is in-flight ownership: skip it. The batch branch names every ticket key of the batch and is on the remote from
its first claim, so none of its tickets looks abandoned while the run lives, wherever the run is.

A run that dies after creating its branch leaves a **stale claim**: the branch stays (on the remote, or only in
a local worktree when it died before the push), so the abandoned-claim test never holds. To keep a live claim
distinguishable, a run pushes each commit to the batch branch as it makes it (a branch push starts no CI), and
when two hours pass without a commit it adds a `🔒 Claim heartbeat` comment to each ticket of the batch. A claim is
stale when no open pull request names its keys and neither the branch's newest commit (local or remote) nor the
newest claim or heartbeat comment on its tickets is younger than six hours; a worktree a dead run left behind
does not keep it alive. CLAUDE.md forbids committing to another session's branch, and the branch may hold
finished work, so a stale claim is not taken over automatically: intake skips it and lists it in the ledger under
"Blocked or other", with the branch, its last activity and the two ways on. Matěj either asks a session to
continue that branch (it then becomes that session's batch: a worktree on the existing branch, a new claim
comment naming it on each ticket), or releases it: the tickets go back to Next with a claim-release comment
naming the branch, which stays for him to delete or keep and no longer blocks a claim.

A claim is **lost** when step 4 shows a peer moved first. Do not implement that ticket and do not move it back:
the peer owns it. When it was a later ticket of a batch whose earlier tickets this session already claimed, end
the batch, so no branch of this session keeps naming the peer's key: release every ticket this session claimed
for it (a claim-release comment naming the branch, back to Next), and delete the branch and worktree it created,
which hold no commit yet (`git push origin --delete <branch>`, `git worktree remove`, `git branch -D`). Claims
come before any implementation, so nothing is lost.

Then select and claim a new batch, or end the run with the ledger when nothing safe remains. If a peer's
ownership only shows after implementation, do not push the work and do not open a competing pull request: record
the collision on the ticket, keep the local branch for Matěj, and report it under "Blocked or other".

#### Slot race

The claim arbitrates one ticket, so runs that each saw room under a shared cap can claim different tickets for
its last place: the last slot of a domain, the last of the six, the one gate-critical place, the last playtest
slot, or the same generated file. A new batch therefore re-counts at its first ticket's step 4: after its own
claim comment is recorded, it counts every in-flight batch again from one fresh search read to its end (tickets
in In Progress, Pending User QA and Pending CR, minus those whose associated pull requests all closed unmerged,
grouped by the branch their winning claim comments name, with
the QA mode, domains, gate-critical flag and generated files those comments and later scope or QA-mode comments
state), each group merged with the open pull request whose head is its branch, plus the other open pull requests.
It keeps its place only if, besides itself, every cap of
[Concurrency and run boundaries](#concurrency-and-run-boundaries) it needs still has room and no in-flight batch
regenerates one of its generated files. Otherwise it stands down: it creates no branch, adds a claim-release
comment to every ticket it claimed for the batch, returns each to Next, and selects other work or ends the run.
Two contenders that see each other both stand down, which keeps the cap and leaves the place to the next
selection. Jira search can lag behind a write, so claims inside that lag can still overshoot a cap by the number
of agents running at once; later selections see the overshoot and wait.

## Batch selection

Choose one coherent batch that one session can implement and verify precisely. Do not inflate a batch to use
capacity, and do not combine unrelated work only to reach a ticket count.

Expected scale, not a limit: about one M ticket (or one pull-request-sized part of an L), up to three S tickets,
or up to ten small tooling or docs tickets, or a mixture of comparable size. The ticket's `**Size:**` line
(roadmap sizes: S fits one session, M takes one to three) is the first guide. An L ticket is split before it is
selected: create pull-request-sized part tasks (same epic, labels, priority and sprint, each with its own Done
when), link the L ticket as blocked by each part, and select parts. The L ticket goes to Done with the pull request
of its last part, whose title names both keys.

After the sprint and Next rules, group by QA mode, then by similarity: the same feature package, content type,
generators or verification path (the same GameTests, the same pytest module). Maximize the safe coherent batch
without losing precision, reviewability, automated verification or conflict avoidance. Independent AI-only
tickets from different areas fit one batch when they share no conflict-prone files and one gate covers them. A
playtest batch also needs a free playtest slot; while every slot is taken, select an AI-only batch instead.

Fill an AI-only batch toward the expected scale. Intake, the claim, the first build of a new worktree and the
gate cost the same for one ticket as for ten, and every batch holds a slot until it merges, so small AI-only
tickets that share a verification path belong in one batch, with compatible To Do tickets promoted for it (rule 4
of [Priority and the Next queue](#priority-and-the-next-queue)). A one- or two-ticket AI-only batch is right only
when no compatible eligible ticket exists, and the batch announcement says why.

Use the lowest reasoning effort that gives a precise, verified result, and raise it for difficult, ambiguous,
cross-cutting or engine-level work (the damage pipeline, networking, worldgen).

## Implementation and verification

- Read the ticket, its roadmap task and the [architecture](architecture.md) sections for the systems it touches
  before writing code, and follow `CLAUDE.md`'s rules: content only through `content/` and `uv run rf gen`, no
  edits to generated files, every Minecraft and NeoForge API verified in `build/mc-sources/`, dev-only code in
  `qa-mod/`.
- The ticket's "Done when" decides the evidence: JUnit for formulas and rules, GameTests for in-game behavior,
  pytest for the pipeline. Add focused positive and negative tests for every rule the batch introduces.
- Run focused checks while implementing, and the full gate once before publishing, with zero errors and zero
  warnings:

  ```bash
  uv run rf gen && git status --porcelain              # content valid and regenerated from YAML; review the diff
  uv run rf gen --check                                # generated files up to date
  uv run ruff check && uv run ruff format --check
  uv run pytest
  .claude/scripts/gradle-queue.sh build               # both mods compile, JUnit
  .claude/scripts/gradle-queue.sh runGameTestServer   # GameTests
  (cd pack && packwiz refresh) && git diff --exit-code pack/   # only when pack/ changed
  ```

- Keep `CLAUDE.md`, [architecture.md](architecture.md), the READMEs and the ADRs current in the same batch, and
  tick off finished roadmap tasks ([Publication](#publication)).
- Scale extra rigor to the risk: a docs-only batch needs the gate, not new tests; the damage pipeline, saved data,
  networking and anything that could corrupt a world need tests for the edge cases, and a dedicated server boot
  (`./gradlew runServer`) for client isolation.
- Report evidence exactly: name each command that ran and passed. Never say "all checks passed" for a partial
  run. An external blocker is reported as one, never as a pass.

### Gradle queue

Parallel worktrees can start several Gradle builds and GameTest servers at once, and the WSL VM has 12 GB. Agents
therefore run every Gradle build and test task through `.claude/scripts/gradle-queue.sh <task>` (not
`runClient`, which Matěj starts himself). It allows one run per 5 GiB of RAM (two on Matěj's PC), waits while
every slot is busy or less than 4 GiB is available, and runs a build that died of memory again once instead of
failing. Runs start one at a time: a start keeps the admission lock for up to a minute (or until that run ends),
so the next run checks memory only after the new JVMs have claimed theirs. A run that waited too long exits with
75: that is a queue timeout, not a build result, so run it again later. A run that ran out of memory twice is a
real failure.

## Branches and worktrees

- Every batch gets its own branch and worktree, created after the claim holds:

  ```bash
  git fetch origin
  git worktree add -b <branch> ../realmforge-worktrees/<branch with "/" replaced by "-"> origin/master
  git -C ../realmforge-worktrees/<dir> push -u origin <branch>   # the claim's visible artifact; starts no CI
  cd ../realmforge-worktrees/<dir> && uv sync
  ```

  A session that Claude Code already put on its own branch (a cloud session's clone, the desktop app's
  `claude/<name>` branch) creates no other branch or worktree (CLAUDE.md): at the first claim it rebases that
  branch onto the current `origin/master`, pushes it, and uses it as the batch branch. Its name carries no keys,
  so the claim comments name it and the pull request title carries the keys.

  A stacked batch ([Dependencies](#dependencies)) starts from `origin/<dependency branch>` instead of
  `origin/master`, and rebases onto it. The main checkout belongs to Matěj and to sessions working there: a flow
  never switches, builds or commits in it. A new worktree has no `build/`, `run/` or `.cache/`, so its first
  build and `:mod:extractMinecraftSources` are slow (run both through the queue).
- **Branch name:** `<type>/<keys>-<slug>`, with the batch's keys in lowercase and ascending order before a slug of
  two to five words: `feat/rlm-17-feature-skeleton`, `feat/rlm-18-rlm-19-registry-pipeline-player-state`. The
  type is the Conventional Commits type of the main change.
- Work, run every check and commit only inside the worktree. Commits follow `CLAUDE.md` (explicit paths,
  Conventional Commits, never `.local/` or build output).
- Before publishing, rebase onto the latest `origin/master` (a stacked batch: its dependency branch) when it
  moved, resolve conflicts (regenerate generated files with `uv run rf gen`), and rerun the affected checks.
- After the merge, `monitor-pr` removes the worktree and branch this session created.

## Publication

The agent owns implementation, the gate, push, the ready pull request, the ticket moves, the handoff and the
monitored merge. Publication happens in this order:

1. Check that `git remote get-url origin` points to `matejkadlec/realmforge`, then push:
   `git push -u origin <branch>`.
2. Open one ready pull request against `master` with `gh pr create` (against the dependency's branch only for a
   stacked batch):
   - **title:** Conventional Commits with the keys, `feat(spells): add the spell engine core (RLM-24)`, several
     keys comma-separated;
   - **description:** What to look at first; the tickets with links; what changed and why; dependencies pulled in;
     the checks that ran; a stacked base or plan change, if any; never `@codex` anywhere.
3. **Labels:** `needs-playtest` for a playtest batch; `needs-approval` when the batch changes the plan (anything
   in [roadmap.md](roadmap.md) beyond ticking off finished tasks: new, removed, reordered or reworded tasks, a
   recorded decision, a changed milestone). Only Matěj removes either label.
4. **Tick-off:** for each finished roadmap task, commit `[x]` plus the pull request link in
   [roadmap.md](roadmap.md) and push it, before `monitor-pr` starts.
5. Read the pull request's head back from GitHub and confirm it is your final commit.
6. On each ticket, record the `🤖 Claude QA` summary (what was verified and how, the pull request, the head SHA),
   plus the `🎮 Playtest handoff` for a playtest batch.
7. Move the tickets: an AI-only batch to Pending CR, a playtest batch to Pending User QA.
8. Hand off to Matěj, leading with [What to look at](#what-to-look-at): the pull request number and URL, the
   branch, the head, the checks, the Jira state, any hold, and for a playtest batch the
   [playtest commands](#playtest-commands). Name an action for him only when one is needed.
9. In the same run, run `/monitor-pr <n>` on the pull request. The handoff is a progress report, not the end of
   the run: a published pull request left unmonitored is unfinished work.

### After publication

[`monitor-pr`](../.claude/skills/monitor-pr/SKILL.md) carries the pull request to its merge under
[ADR 0015](decisions/0015-merging-through-monitor-pr.md): it runs `fix-pr` on Codex findings, closes every review
thread, fixes failing or stuck checks and conflicts with `master`, falls back to `finalize-pr` when Codex hits its
usage limit, and merges once everything is clean and no hold applies. The holds are `needs-playtest` (Matěj plays
the head), `needs-approval` (Matěj approves the plan change), a changes-requested review, a Codex finding outside
a thread, and anything Matěj asked to hold. Its "Jira tickets" section keeps the tickets in step: a `🤖 Claude QA`
update after each of its pushes, Pending User QA to Pending CR when the playtest passes, Done after the merge.

Outside such a run (another session's pull request, or one whose run stopped), do not poll, fix, merge or update
its tickets unless Matěj resumes or delegates that pull request (`/monitor-pr <n>`, `/fix-pr <n>`,
`/finalize-pr <n>`). The one standing exception is the [reconciliation](#reconciling-merged-tickets) at the
start of `flow1` and `flow2`.

## Remediation and escalation

- A failing test, a review finding or a narrow CI defect is part of finishing the batch, not a reason to stop. A
  defect that needs separate tracking gets a linked ticket; the batch continues while its goal and scope stay the
  same.
- **Escalate at once** when the next step needs something only Matěj can change: a decision, an account,
  credentials or credits, a Jira, GitHub or Codex setting, approval to install a tool, his Windows side (Prism,
  drivers). Do every safe step first, then say what you observed, the exact action, where to do it, the non-secret
  values involved, and how you will verify it afterwards. Never ask for or accept a secret in the chat, Jira, git
  or a log.
- **Technical judgment is never an escalation.** A finding, tradeoff or cleanup whose only question is whether it
  is worth doing is the agent's call: do it, ticket it, or drop it with a concrete reason, and report the
  decision. Do not end a report with a menu of technical options.
- When Matěj confirms an external action, verify its result with the narrowest check and resume the task.

## Concurrency and run boundaries

These caps keep parallel worktrees from turning into merge conflicts. Every cap counts batches, not tickets or
pull requests: group the in-flight tickets by the branch their winning claim comments name, and count a group and
the open pull request whose head is that branch as one batch.

- **Pending-batch slots:** at most four batches in the game domain, three in the tooling domain, and six in total.
  A batch holds a slot in each of its domains from its claim until it is merged or otherwise resolved (In
  Progress, Pending User QA or Pending CR, a pushed branch, an open pull request); a batch whose pull requests all
  closed unmerged is resolved. A shared-root batch holds one in every domain.
- **Gate-critical:** at most one gate-critical batch in flight in the whole repository.
- **Generated files:** no two in-flight batches regenerate the same generated file
  ([Conflict domains](#conflict-domains)).
- **Playtest slots:** at most three playtest batches hold a playtest slot. The limit caps the branches waiting on
  Matěj's playtest, not the number of agents: branches beyond what he can play only go stale. A playtest batch
  holds its slot from its claim until Matěj removes `needs-playtest` or the batch is otherwise resolved, through
  every revision. Count them, as batches, from fresh reads, every result page and comment page to its end: tickets
  in Pending User QA; tickets In Progress whose latest QA-mode statement in the current cycle (the claim comment
  or a later QA-mode comment) names Playtest; open pull requests with `needs-playtest`. Select a playtest batch
  only while that count is below three; otherwise select AI-only work. A revision is not a new selection, so the
  limit never blocks it.
- Start another batch only while every cap allows it and the conflict map shows the branch safe, independent, and
  neither overlapping nor stacked on an in-flight branch without need. A free slot never authorizes an overlap.
- Linked remediation stays in its original batch's slots.
- Never use concurrency to justify overlapping branches, stale bases, changing another session's published
  branch, or watching a pull request outside a monitored merge.

A run delivers one batch. It ends, with the batch ledger, when:

- its pull request was published and its monitored merge has merged it or stopped, never at the publication
  itself (a playtest batch's monitored merge keeps watching for Matěj's playtest);
- the caps leave no startable batch: the overall limit is reached, every domain with safe work left is full, a
  gate-critical batch is in flight and only gate-critical work remains, every remaining candidate regenerates a
  file an in-flight batch owns, or every playtest slot is taken and only playtest work remains;
- no safe active-sprint ticket remains after the conflict, dependency and ownership checks.

A candidate whose slots are unavailable is not safe remaining work in any domain. The run therefore ends once
every remaining candidate is blocked that way, and never while another domain still has a startable batch, or a
full playtest limit still leaves a startable AI-only batch.

Stop for Matěj when continuing needs a new or changed product or architecture decision, a secret or account, a
changed destructive scope, uncertain state, weakened security or substantial unrelated scope.

At every stop, give a concise **batch ledger**, grouped by:

- **Merged into master**, including the tickets the reconciliation moved to Done;
- **Pending CR**, with the exact state (`monitor-pr` waiting for Codex, a hold such as `needs-approval`, or
  `owner action: ...` when the run stopped);
- **Pending User QA** (playtest);
- **Waiting for Matěj**: decision tickets and tickets waiting on him, one line each
  ([Tickets waiting on Matěj](#tickets-waiting-on-matěj));
- **Blocked or other**.

List the ticket keys and only the most important player-visible or architectural changes.

When the batch holds the last unstarted tickets of the active sprint and both To Do and the unselected rest of
Next are empty, say: **This batch contains the final remaining implementation task(s) in the active sprint.**
Never call the sprint complete before playtests, review and merges are done; once the last tickets have merged
and no unfinished active-sprint ticket remains, the truthful statement is: **The active sprint is complete.**

## Cloud sessions

A Claude Code cloud session (`CLAUDE_CODE_REMOTE=true`) runs the same flow; only this differs:

- **Its own clone** is its worktree, and the branch Claude Code put it on is the batch branch
  ([Branches and worktrees](#branches-and-worktrees)). It sees only its own branches, so its intake and claim
  rely on Jira claim comments, remote branches and open pull requests.
- **Jira** goes through the claude.ai Atlassian connector, with the same tools and field sets.
- **GitHub:** the proxy refuses GraphQL; the pull request skills list the REST forms to use instead.
- **Gradle:** the queue sees only the VM's own runs.
- **Playtests** are handed off with the cloud [playtest commands](#playtest-commands); a revision may run in the
  same cloud session or any later local or cloud one.
- **Cleanup:** branch deletion and worktree removal stay with local sessions.
