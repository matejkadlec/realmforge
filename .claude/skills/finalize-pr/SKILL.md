---
name: finalize-pr
description: Carry one published pull request in matejkadlec/realmforge through its independent review to its merge. Each review pass runs up to 3 rounds, each with a fresh, read-only pr-reviewer subagent; this session verifies and fixes the findings and pushes once per pass. Then it resolves every review thread, fixes failing or stuck checks and conflicts with master, keeps the pull request's Jira tickets in step, and merges once a review of the exact head is clean, every required check is green and no playtest is waiting for Matěj. Runs after every pull request a session publishes or updates as its own task work, without exception. Use when the user types "finalize-pr 12", and right after publishing or updating a ready pull request.
---

# finalize-pr: independent review, then the merge

ChatGPT Codex no longer reviews pull requests in this repository
([ADR 0029](../../../docs/decisions/0029-independent-claude-review-before-every-merge.md)). This skill's fresh
`pr-reviewer` subagent is the code review of every pull request, and this skill is the only one that merges.

## When it runs

- The user says `finalize-pr <n>`.
- Right after a session publishes or updates a ready (non-draft) pull request as its own task work, **in every
  case**: no pull request is too small, too docs-only or too urgent for it. The publication report is a progress
  report, and the next action is `finalize-pr <n>`.

**Its job is the merge.** The run is not finished at a report, a clean review or a green check. It ends only at
one of the "Stops" below.

## Delegation

Covers one named pull request and only these actions:

- reviewing it with fresh `pr-reviewer` subagents;
- fixing verified findings on its branch, running the checks and pushing that branch (one push per pass);
- replying to and resolving its review threads;
- fixing failing or stuck required checks on the branch;
- resolving conflicts with `master`;
- merging under "Merge", and remediation under "After the merge";
- keeping the pull request's Jira tickets in step under "Jira tickets";
- creating RLM Jira tickets for real findings it does not fix here (never GitHub issues);
- reporting.

It never deploys, requests a Codex review, writes to another pull request, bypasses branch protection, or
weakens a test or check.

## Repository facts

- Repository: `matejkadlec/realmforge`. Default branch: `master`. Merge method: merge commit (`--merge`; merge,
  squash and rebase are all allowed, and merge commits are preferred).
- Ruleset "Master Branch Protection" on `master`: pull request required (0 approvals), every review thread
  resolved, no force-push, no deletion, and these required checks (GitHub Actions, not strict), matched by job
  `name:` in `.github/workflows/ci.yml`:
  - `Content pipeline (validation, pytest, generated files)` (timeout 15 minutes)
  - `Gradle build, JUnit, GameTests` (timeout 45 minutes)
  - `Vanilla ID universe is reproducible` (timeout 15 minutes)
  - `Modpack index is up to date` (timeout 15 minutes)
- CI (`.github/workflows/ci.yml`) runs on `pull_request` and on push to `master`, on GitHub-hosted
  `ubuntu-latest` runners. A full run usually takes about 2 minutes (1m40s–2m20s in October 2026); the Gradle
  job is the longest. Runs are cancelled per ref when a newer push arrives. On failure the Gradle job uploads
  the artifact `gradle-logs-and-reports` (game logs, crash reports, test reports).
- No workflow deploys. After a merge, only CI on `master` runs.
- `deleteBranchOnMerge` decides whether GitHub deletes the head branch at merge. In October 2026 it was off.
- The reviewer is `.claude/agents/pr-reviewer.md` (tools: Read, Grep, Glob).
- Codex: the ChatGPT Codex GitHub connector (`chatgpt-codex-connector[bot]`) may still be installed. Whatever it
  posts (a usage-limit, status or summary comment, a reaction) is not a review and holds nothing. A Codex review
  thread, should one appear, is another bot's thread (step 5 of "Loop").
- Playtests: gameplay work is played by the owner (Matěj) before its pull request opens; his `qa1` publishes it
  ([`docs/ai-development-flow.md`](../../../docs/ai-development-flow.md), "Playtest handoff"). After that, only a
  reconfirmation holds the merge (check 6 of "Merge"): its tickets wait in Pending User QA for his `qa1` or
  `qa2`. Pull requests carry no GitHub labels
  ([ADR 0024](../../../docs/decisions/0024-no-github-labels.md)).
- A plan change (any change to `docs/roadmap.md`) holds nothing: agents make one only as Matěj decided it
  ([ADR 0021](../../../docs/decisions/0021-status-overview-and-plan-changes.md)).
- Jira: a pull request from a Jira batch ([`docs/ai-development-flow.md`](../../../docs/ai-development-flow.md))
  names its tickets (`RLM-<n>`) in its title, and its head branch carries their keys. The Atlassian MCP tools
  (the `atlassian` server from `.mcp.json` locally, the claude.ai connector in cloud sessions) reach Jira.
  Findings this run tracks instead of fixing become
  [follow-up tickets](../../../docs/ai-development-flow.md#follow-up-tickets), not GitHub issues.
- There is no pre-push hook. The local gate is the command list in `CLAUDE.md` "Workflow" step 3; run its Gradle
  tasks through `.claude/scripts/gradle-queue.sh`, which waits for memory when other worktrees build.
- Worktrees live in `../realmforge-worktrees/<branch with "/" replaced by "-">`, outside the repository.

## Rules

- **Scope.** The run covers one pull request in `matejkadlec/realmforge`. Before every write, check that
  `git remote get-url origin` points to `matejkadlec/realmforge`. A mismatch is a blocker. Never write to
  another pull request.
- **Never write `@codex` on the pull request**, in the description, comments, replies or code spans, and never
  quote a Codex footer. GitHub hands any mention to Codex as a new request: a review, or a Cloud task that can
  push to the branch.
- **One push per pass.** Every push starts a full CI run and makes a new head that needs its own review. Commit
  once per root cause, then publish each pass with a single push. After the push, read the pull request's head
  back from GitHub and confirm that it equals your final commit. Any other head, even a descendant of your
  commit, means someone else pushed: treat it as a remote head that moved under you, and read it before acting
  again.
- **Never weaken anything to get green.** Never weaken, skip or disable a test, a required check, branch
  protection or a security policy. Never use `--no-verify`, `--admin`, `--auto` or any bypass.
- **Merging and deploying.** This skill is the only one that merges. Nothing deploys in this repository; never
  re-run, dispatch or otherwise trigger a deployment workflow.
- **Secrets.** Never read, print or copy `.env` files, tokens, secrets or `.local/`. Never put them in a review
  bundle, a comment, a reply or a report.
- **Paginate.** GitHub returns comment, review and reaction listings 30 entries per page, oldest first. Read
  every page with `gh api --paginate` before you filter.
- **Claude Code cloud sessions** (`CLAUDE_CODE_REMOTE=true`). The proxy refuses GraphQL, so `gh pr view`,
  `gh pr list`, `gh pr merge`, `gh pr edit` and the GraphQL calls below fail there. Use these REST forms instead:
  - Read a pull request: `gh api repos/matejkadlec/realmforge/pulls/<n>`.
  - List threads: `gh api repos/matejkadlec/realmforge/pulls/<n>/ccr/review_threads`. Each thread has
    `resolved`, `outdated`, `path`, `line` and `comment_ids`.
  - Read a comment: `gh api repos/matejkadlec/realmforge/pulls/comments/<id>`. `commit_id` and
    `original_commit_id` name the reviewed head.
  - Reply: `gh api repos/matejkadlec/realmforge/pulls/<n>/comments/<first comment id>/replies -f body=<reply>`.
  - Resolve: `gh api -X POST repos/matejkadlec/realmforge/pulls/<n>/ccr/comments/<first comment id>/resolve`.
  - Comment on the pull request: `gh api repos/matejkadlec/realmforge/issues/<n>/comments -F body=@<file>`.
  - Merge: `gh api -X PUT repos/matejkadlec/realmforge/pulls/<n>/merge -f merge_method=merge -f sha=<sha>`
    (with `sha` GitHub refuses the merge when the head moved).
  - Retarget to `master`: `gh api -X PATCH repos/matejkadlec/realmforge/pulls/<n> -f base=master`.
- **When the user is needed,** say it in exactly this form, with only the label bold:
  `**Your assistance is needed:** {what to do, where, and what you will check afterwards}`.
- **Reports state decisions, not open questions.** This skill asks the user only under "User needed", about
  uncommitted work in "Start", the conflict case in step 2 and the thread cases in step 5.

## Roles

- **Implementation context** (this session) orchestrates the run. It verifies every finding against the code,
  applies fixes, runs checks, pushes, merges and reports.
- **Reviewer** is one fresh `pr-reviewer` subagent per round, started with the Agent tool and
  `subagent_type: pr-reviewer`, never any other type: other types inherit tools that can write. Never continue
  an earlier round's reviewer (no SendMessage to it), because a continued context is not an independent pass.
  When no fresh-context, read-only subagent is available (for example the agent is not listed because the
  session started before `.claude/agents/pr-reviewer.md` existed), do not simulate one. Stop under "User needed".
- Claude Code's built-in `/code-review` is not the independent pass: it runs in the invoking context. It can
  still run as an extra, non-independent pass.

## Start

Do this before the first review, and again whenever the run resumes in a new session:

1. **Resolve the target:**
   `gh pr view <n> --repo matejkadlec/realmforge --json number,title,url,state,isDraft,isCrossRepository,headRefName,headRefOid,baseRefName`.
   A closed or merged pull request goes to "Stops". A cross-repository pull request is a blocker: report it and
   stop. A draft is not ready to merge: go to "User needed" and ask the user to mark it ready for review (or to
   say it should stay a draft), and keep watching until it is ready. This skill merges only into `master`. A pull
   request whose `baseRefName` is another branch depends on unmerged work: review it against that base, then keep
   watching with check-ins about 30 minutes apart until that base has merged into `master`, retarget it
   (`gh pr edit <n> --repo matejkadlec/realmforge --base master`; GitHub does this itself when it deletes the
   merged base branch) and continue. Never merge into another feature branch. If the base's pull request closes
   without merging, or its branch disappears without having merged, the wait cannot end: go to "User needed" and
   ask whether to retarget this pull request to `master` or close it.
2. **Locate the worktree.** Run `git fetch origin`. Reuse the worktree that `git worktree list` holds for the
   head branch (it may be the main checkout). Create one only when none exists
   (`git worktree add ../realmforge-worktrees/<dir> <branch>`), then run `uv sync` inside it. A new worktree has
   no `build/`, `run/` or `.cache/`, so its first Gradle build is slow.
   - A clean local branch that is strictly behind its remote: fast-forward it (`git merge --ff-only`).
   - Uncommitted changes, or a local branch that is ahead of or diverged from the remote: these may be another
     session's or the user's work. Change nothing, and ask the user whether to fold them in or stop.
3. **Confirm that the local head equals the remote head,** and record whether this session created the branch
   (it decides rebase versus merge in step 2 and the cleanup after the merge). A playtest batch's branch and
   worktree that this session took over with [`qa1`](../qa1/SKILL.md) count as its own.

Run every git command, check, commit and push of this run inside that worktree, never in the current checkout
on its behalf. Before each push, run `git fetch origin` again and confirm that the remote head is still the one
your work is based on, then push with `--force-with-lease=<branch>:<that sha>` so the push fails if it moved in
between. A remote head that moved under you is new work to read first, never something to overwrite.

## Review pass

A pass reviews the pull request's current head in up to **3 rounds** and ends with at most one push. Number the
rounds across the whole pull request (the first pass has rounds 1 to 3, a later pass continues the count); the
count drives "Late rounds".

1. **Build the review bundle** in the session scratchpad directory (or a `mktemp -d` directory outside the
   repository), never inside the repository, for round `<k>`:

   - `pr-<n>-round-<k>.diff`: the complete `git diff origin/<base>...HEAD`, where `<base>` is the recorded
     `baseRefName`;
   - `pr-<n>-round-<k>-meta.txt`: the number, branch, base branch, base SHA (`git merge-base origin/<base> HEAD`),
     head SHA and changed files (`git diff --name-only origin/<base>...HEAD`);
   - from the pull request's second pass on, `pr-<n>-round-<k>-since-review.diff`: `git diff <last reviewed
     head>..HEAD`, what changed since the head an earlier pass reviewed (after a conflict resolution, the base's
     changes show there too).

   No secrets, logs or dumps go into the bundle.

2. **Review.** Start a fresh `pr-reviewer`. Give it the absolute bundle paths and the worktree root, and ask for
   its `VERDICT` output. When a `since-review` file exists, say that the rest of the diff passed an earlier review,
   so it should look hardest at what changed, without skipping the rest.

3. **Verify before acting.** Check every finding against the code yourself. For a Minecraft or NeoForge API
   claim, check the pinned sources in `build/mc-sources/` (`./gradlew :mod:extractMinecraftSources`).

   - Fix only confirmed findings, and only P1 and P2 inside the loop. A P3 fix would land after the review that
     cleared it, so P3s close under "P3 disposition".
   - Reject a finding only with a concrete technical reason, and record it. Valid reasons: the claimed path
     does not exist, a guard already exists at a named location, no input reaches the path, or the finding is
     outside this pull request's scope.
   - A real but out-of-scope finding becomes an RLM Jira ticket under "Tracked" in "P3 disposition" (never a
     GitHub issue). Never dismiss it silently.
   - **Late rounds.** Late rounds narrow to rare edge cases in what earlier rounds already reviewed, and fixing
     them adds surface for the next round. From the pull request's fourth review round on, a P2 that names
     neither a concrete failure in normal use (a realistic input, command or workflow of this repository: a
     session, a CI job, a player) nor a regression that an earlier round's fix introduced is rejected as churn,
     with that reason recorded. A valid P1 is always fixed.

4. **Check and commit the round locally.** Run the targeted checks and commit the fix (one commit per root
   cause, Conventional Commits, staged by explicit path):

   - `content/` or `tools/`: `uv run rf gen` (no unexpected changes), `uv run ruff check`,
     `uv run ruff format --check`, `uv run pytest`;
   - `mod/`, `qa-mod/` or Gradle files: `.claude/scripts/gradle-queue.sh build` and
     `.claude/scripts/gradle-queue.sh runGameTestServer` (the queue sources SDKMAN when `JAVA_HOME` is unset),
     then `uv run rf gametest-log`;
   - `pack/`: `cd pack && packwiz refresh`, then `git diff --exit-code pack/`.

   Never edit generated files; change `content/` or the generator and run `uv run rf gen`. Do not push: the next
   reviewer reads the local head. A round that fixes nothing commits nothing.

5. **Repeat** from step 1 against the new local head, after running `git fetch origin` again.

   - Each round re-confirms that the remote head still equals the head the pass started from. A remote head that
     moved under you ends the pass as Blocked, and the local commits stay on the branch.
   - Stop early in any of these cases:
     - a review returns no P1 and no P2;
     - a round applies no fix (a fresh reviewer over identical code settles nothing);
     - a review repeats a finding you rejected with a recorded reason (this is a Disagreement).
   - Never start a fourth round in one pass. A fix applied after the pass's final review was never independently
     reviewed: the pass finishes as Capped, never as Clean.

6. **Deliver once.** If the pass ended with at least one committed fix:

   - Run the full local gate:

     ```bash
     uv run rf gen && git status --porcelain     # must show no changes
     uv run ruff check && uv run ruff format --check
     uv run pytest
     .claude/scripts/gradle-queue.sh build              # waits for memory while other worktrees build
     .claude/scripts/gradle-queue.sh runGameTestServer
     uv run rf gametest-log                      # no data loading errors in the GameTest log
     ```

   - Immediately before the push, run `git fetch origin` and require the remote head to still equal the head the
     pass started from. A different head, even an ancestor after someone reset the branch, is Blocked: do not
     push.
   - Push once: `git push --force-with-lease=<branch>:<starting sha> origin <branch>`. A rejected lease is
     Blocked; nothing is rewritten, because your commits descend from that head. Then read the remote head back
     (`gh pr view <n> --repo matejkadlec/realmforge --json headRefOid`) and confirm that it equals your final
     commit. A different head, even a descendant, is a remote head that moved under you: Blocked.
   - A failed push or gate is a delivery blocker, not a completed fix. Keep the commits local and report them.

7. **Record the pass** in one pull request comment (`gh pr comment <n> --repo matejkadlec/realmforge
   --body-file <file>`), the GitHub-side record of the review: `🤖 Independent review`, the rounds and the head
   each one reviewed, every finding with its severity and outcome (fixed in `<sha>`, rejected with the reason,
   tracked as `RLM-<n>`, dropped with the reason), the finishing state, and the head it leaves reviewed. Keep it
   short and factual, with no secrets and no Codex mention. A pass that pushed also updates the Jira tickets
   ("Jira tickets").

### Finishing states

- **Clean.** The last review left no P1 or P2 open against this pull request: each was absent, rejected in
  step 3 with a recorded concrete reason, or tracked as an out-of-scope RLM ticket, and no fix landed after that
  review. A round whose findings were all rejected or tracked stops the loop with this state. When no Atlassian
  tool reaches Jira, an out-of-scope P1 or P2 listed under "Follow-ups not ticketed" counts as tracked. The pass
  leaves its final head **reviewed**.
- **Capped.** Three rounds ran and the third still found a P1 or P2, or a fix landed after the final review. Say
  plainly that the head carries changes no reviewer has seen, list what the last review found and what you did
  about it, and go to "User needed". Matěj's explicit approval of that exact head makes it reviewed. When both
  apply, Capped outranks Disagreement, and the report also carries the disagreement record.
- **Disagreement.** The reviewer repeated a finding you rejected on a concrete technical basis. Report both
  positions with their evidence under "User needed". Matěj's ruling decides, and his explicit approval of that
  exact head makes it reviewed.
- **Blocked.** Something is outside this delegation: a check you cannot fix safely, an external control, a
  secret, or a head that moved under you. Go to "User needed", or read the new head first when it moved.

### Which head is reviewed

A review belongs to exactly the head it covered. Every later head needs a new pass before the merge, whatever
brought it: a conflict resolution, a check repair, a thread fix, a `qa2` revision, someone else's push. A textually
clean merge says nothing about how two changes interact. One exception: a head that only brought `master` in, by
a rebase or a merge without conflict resolutions, keeps the review when the pull request's own diff is unchanged:

```bash
git diff <base sha the review recorded> <reviewed head> | git patch-id --stable
git diff $(git merge-base origin/master HEAD) HEAD | git patch-id --stable   # the same id keeps the review
```

## P3 disposition

You have read the code, so the judgment is yours and the user never rules on a P3. Verify each P3 as carefully
as a P1, then close it in one of two ways:

- **Tracked:** an RLM Jira ticket, never a GitHub issue
  ([flow, "Follow-up tickets"](../../../docs/ai-development-flow.md#follow-up-tickets)). Search `RLM` first
  (`searchJiraIssuesUsingJql`, `project = RLM AND text ~ "<terms>"`, with comments). An equivalent ticket decides
  by its status:
  - not Done or Rejected: cite it and comment this pull request's evidence;
  - Rejected: create nothing; the finding is Dropped (a P3) or rejected in step 3 (an out-of-scope P1 or P2),
    citing the rejection reason. A ticket rejected as a duplicate stands for its original. Only a finding that
    names a concrete failure the rejection reason does not cover gets a new ticket, which cites the rejected one;
  - none, or only a Done one: create a Task in the active sprint (parent RLM-1 for workflow and tooling, the
    milestone epic for game code; `## Context`, `## Task`, `## Done when`) with the file, the scenario and the
    smallest fix, and a `Relates` link to the Done ticket if there is one.

  A follow-up about code or text this pull request adds must wait for its merge: link it as blocked by each
  ticket the pull request's title names (`createIssueLink`, `type: Blocks`, `inwardIssue` that ticket,
  `outwardIssue` the follow-up), so `flow1` defers it until they are Done. When the title names no ticket, create
  it in the backlog instead of the active sprint, with this pull request in its `## Task`. Group findings that
  share a file or a fix. When no Atlassian tool reaches Jira, list the follow-up in the report under "Follow-ups
  not ticketed" instead, which carries it to Matěj.
- **Dropped:** record a concrete technical reason. "Low severity" alone is not one.

Never fix a P3 on this branch. Never end the report with an offer to fix one "if you want".

## Signals

Read each signal fresh from GitHub at every step, and never trust an earlier reading.

- **Head.** The head SHA (`gh pr view <n> --repo matejkadlec/realmforge --json headRefOid`), and its *push
  time*. When this run pushed the head itself, that is the time of its push. Otherwise it is the `created_at`
  of the newest `pull_request` workflow run for that SHA:
  `gh api "repos/matejkadlec/realmforge/actions/runs?head_sha=<sha>&event=pull_request" --jq '[.workflow_runs[].created_at] | max'`.
  The head's *seen time* is when this run first read that head; it stands in for the push time until such a run
  exists.
- **Review state.** Whether the head is reviewed ("Which head is reviewed"): a Clean pass of this run covers it,
  or Matěj approved this exact head. A head whose pass ended Capped, Disagreement or Blocked is not unreviewed: it
  waits for Matěj (step 4 of "Loop"). A resumed run reads the latest `🤖 Independent review` comment on the pull
  request: its reviewed head counts only when it equals the current head or the exception applies.
- **Review threads.** Every review thread, whoever opened it:

  ```bash
  gh api graphql --paginate -F number=<n> -f query='
  query($number: Int!, $endCursor: String) {
    repository(owner: "matejkadlec", name: "realmforge") {
      pullRequest(number: $number) {
        reviewThreads(first: 100, after: $endCursor) {
          pageInfo { hasNextPage endCursor }
          nodes {
            id isResolved isOutdated path line originalLine
            comments(first: 100) {
              nodes { author { login } createdAt url body originalCommit { oid } }
            }
          }
        }
      }
    }
  }' --jq '.data.repository.pullRequest.reviewThreads.nodes[]'
  ```

  Any open thread blocks the merge: the `master` ruleset requires resolution. "Outdated" only means its lines
  moved; an outdated thread is still open until resolved.
- **Reviews.** Human reviews (`gh api --paginate repos/matejkadlec/realmforge/pulls/<n>/reviews`): a
  changes-requested review holds the merge.
- **Checks.** The latest attempt of every check on the head SHA:
  `gh api --paginate "repos/matejkadlec/realmforge/commits/<sha>/check-runs?filter=latest"` (never
  `filter=all`, which adds obsolete attempts that a re-run replaced).
  - Green means each of those completed with `success`, `neutral` or `skipped`, and all four required checks
    (listed under "Repository facts") are among them.
  - Behind them are the workflow runs (`gh api "repos/matejkadlec/realmforge/actions/runs?head_sha=<sha>"`)
    and their jobs (`gh api repos/matejkadlec/realmforge/actions/runs/<id>/jobs`, with `status`,
    `runner_name` and the steps' `started_at`).
- **Mergeability.** `mergeable` and `mergeable_state` from `gh api repos/matejkadlec/realmforge/pulls/<n>`:
  - `dirty`: a conflict. GitHub then runs no `pull_request` workflows, so there will be no CI.
  - `behind`: `master` has moved past the branch.
  - `blocked`: protection still refuses (an open thread, a missing or failing check, or a changes-requested
    review).
  - `unknown`: GitHub is still computing it. Read it again a minute later.
- **Playtest state.** The status of the pull request's tickets in Jira (see "Jira tickets"): a ticket in
  Pending User QA means a reconfirmation waits for Matěj's `qa1` or `qa2`.

## Loop

At every wake, re-read the signals. A merged or closed pull request goes straight to "Stops". Otherwise act on
the first of steps 2–8 that applies. If none applies yet, wait under step 1.

1. **Wait.** Use exactly one waiting mechanism at a time, and re-read the signals at most once a minute.

   - Keep one check-in armed for the next moment a signal can change:
     - 5 minutes later while checks are still running;
     - 5 minutes after the seen time while no check run exists;
     - 30 minutes later while a red check waits for a fix on `master`, or a stacked pull request for its base;
     - about an hour later while the run waits for the user ("User needed").
   - In a local Claude Code session, the harness blocks a foreground `sleep`. Use the `Monitor` tool with an
     until-loop, or a background Bash `until` loop whose exit wakes the run, for example (run in the
     background):

     ```bash
     deadline=$(( $(date +%s) + 300 ))   # the check-in time
     last="$(gh api repos/matejkadlec/realmforge/pulls/<n> --jq .updated_at)"
     until [ "$(date +%s)" -ge "$deadline" ] \
       || [ "$(gh api repos/matejkadlec/realmforge/pulls/<n> --jq .updated_at)" != "$last" ]; do sleep 60; done
     ```

   - In a cloud session, subscribe once to the pull request's activity (`subscribe_pr_activity`) and arm one
     `send_later` check-in.

2. **Conflict: resolve it.** On `dirty` or `behind`, bring `master` in before anything else.

   - Rebase a branch this session created onto `origin/master`, and push with
     `--force-with-lease=<branch>:<remote head before the rebase>`. Merge `origin/master` into anyone else's
     branch, and into a branch whose changes Matěj has played (a batch published by `qa1`), so the played head
     stays in its history for check 6 of "Merge".
   - Keep the intent of both sides.
   - Regenerate generated files with the project's own tooling, never by hand: `uv run rf gen` for
     `mod/src/generated/`, `qa-mod/src/generated/` and `tools/schemas/`; `uv lock` for `uv.lock`;
     `cd pack && packwiz refresh` for `pack/index.toml`; `uv run rf ids vanilla` for `tools/id_universe/`.
   - Run the checks that the conflicted files need (step 4 of "Review pass").
   - Ask the user only when both sides changed the same behavior and keeping either one loses the other.
   - The new head needs a review pass unless the exception in "Which head is reviewed" keeps the earlier one.

3. **Failing check on a head not yet reviewed: fix it first** under step 6, so the review pass that follows
   covers the fix. This saves a pass; a check that fails after the review is step 6.

4. **A head no pass has covered: run a review pass** ("Review pass") on it. A Clean pass makes the head it
   pushed (or, without a push, the head it started from) the reviewed head. Capped, Disagreement and Blocked go to
   "User needed", and their head gets no further pass: it waits there until Matěj approves it, rules on the
   disagreement, or a new head arrives (a fix his ruling asks for brings one).

5. **Open threads: close them.**

   - **The user's own thread** is an instruction for this pull request. If it stays within the pull request's
     intent, carry it out with the verification and single-push delivery of "Review pass" (steps 3, 4 and 6),
     then reply naming the commit and resolve the thread. If it needs a product decision or goes beyond the pull
     request, reply with a proposal, leave the thread open, and ask under "User needed".
   - **Another person's thread:** carry out unasked only a small, verified ask within the pull request's
     intent. For anything else, reply with a proposal and ask the user.
   - **A bot's thread** (Codex included): verify it like a reviewer finding, and close it with one reply that
     opens with its disposition: `Fixed in <sha>:`, `Declined:` (a P2 or P3 not worth doing, with the concrete
     reason), `Tracked as RLM-<n>:`, `Stale:` (already fixed, or the code is gone) or `Incorrect:` (with the
     evidence that the failure cannot happen).
   - A thread whose reply already settles it only needs resolving. Never resolve a thread without a reply that
     settles it. Reply and resolve:

     ```bash
     gh api graphql -f thread=<thread id> -f body=<reply> -f query='
     mutation($thread: ID!, $body: String!) {
       addPullRequestReviewThreadReply(input: {pullRequestReviewThreadId: $thread, body: $body}) { comment { url } }
     }'
     gh api graphql -f thread=<thread id> -f query='
     mutation($thread: ID!) { resolveReviewThread(input: {threadId: $thread}) { thread { isResolved } } }'
     ```

   - **A changes-requested review:** carry out what it asks, or reply with a proposal. Only its author can
     withdraw the request, so ask the user to re-review or dismiss it. The merge waits until then.
   - A push for a thread makes a new head, which needs its review pass (step 4).

6. **Failing check: fix it and push.** Read the failing job's log (`gh run view <run id> --log-failed`; for the
   Gradle job also `gh run download <run id> -n gradle-logs-and-reports` into the scratchpad).

   - **Not this pull request's failure** needs evidence: the same check also fails on `master`'s latest run
     (`gh run list --repo matejkadlec/realmforge --branch master --limit 5`), or the failure reproduces on the
     base commit (`git merge-base origin/master HEAD`, checked out with `git worktree add --detach` in the
     scratchpad). A failure in a file or test the diff never touches is not evidence by itself: a changed
     generator, API or shared configuration can break untouched code. With that evidence, port an existing fix
     if one exists. Otherwise say what fails and why, and bring the base in (step 2) once it carries the fix.
     Re-read the latest CI on `master` at each check-in, because no pull request event announces it.
   - **This pull request's failure:** root-cause it, fix it on the branch, run the targeted checks, and push
     once. A described fix is not a fix, and a red head is never left waiting. The new head needs its review
     pass (step 4).
   - "Flake" is not a cause. Re-run a job only when it died before any test ran, and at most once
     (`gh run rerun <run id> --failed`).
   - A run cancelled because a newer push superseded it belongs to the old head. A cancelled required check on
     the current head gets one re-run.

7. **Slow or stuck checks: find out why.** CI normally finishes within about 2 minutes, and jobs time out after
   15 minutes (45 for the Gradle job). Investigate before waiting any longer when either happens:

   - no check run appears within 5 minutes of the seen time;
   - a required check is still incomplete about 5 minutes after the push time.

   Investigate by case:

   - **No check run.** It may be `dirty` (step 2). Otherwise confirm that GitHub has the head SHA, and read the
     workflow runs for that SHA. A run that failed to start because of a workflow-file error is this pull
     request's to fix. Wait out a GitHub Actions outage (https://www.githubstatus.com) and name it in the
     report.
   - **Jobs `queued` with no `runner_name`.** The GitHub-hosted runners are saturated. If other runs are in
     progress, keep waiting and note it. If nothing runs anywhere for 30 minutes while jobs stay queued and
     GitHub reports no outage, Actions may be disabled or limited for the account: ask the user.
   - **A job `in_progress` far beyond its usual length.** Compare its current step's `started_at` with the same
     job on a recent successful run. A hang ends at the timeout. Then root-cause it under step 6: a hanging test
     (for example a GameTest that never calls `succeed`) is a defect, not a flake.

8. **Reviewed, resolved and green: merge.** When the head is reviewed, no review thread is open and the checks
   are green, go straight to "Merge" in the same wake, with no further approval or report.

## Jira tickets

When the pull request belongs to a Jira batch, keep its tickets in step with it through the Atlassian MCP tools
([`docs/ai-development-flow.md`](../../../docs/ai-development-flow.md), "Status model"). Its tickets are the keys
its title names, plus those whose winning claim comment names its head branch ("Reconciling merged tickets",
step 2). Re-read each ticket just before each write and move it only while it still shows the status the step
expects: RLM's transitions are all global, so Jira refuses no stale move.

- **After each push of this run** (a review fix, a thread fix, a check repair, a conflict resolution): one
  `🤖 Claude QA` comment per ticket with the commits and what they changed, the new head, the review state and the
  checks that passed.
- **Reconfirmation:** when check 6 finds commits after the played head that change gameplay, move the tickets
  in Pending CR back to Pending User QA (transition `41`) with a `🎮 Playtest handoff` limited to
  that change (the new head, "What to test", the launch commands for this branch), and report in the manual-test
  shape, asking for the answer (`qa1` or `qa2`) in this session, which keeps owning the pull request.
- **Playtest passed again:** Matěj's `qa1` in this session records it and moves the tickets to Pending CR (the
  [`qa1`](../qa1/SKILL.md) skill); the run then continues to the merge.
- **Playtest failed:** his `qa2` with what is wrong, in this session, follows the [`qa2`](../qa2/SKILL.md) skill
  on this branch: the tickets go to In Progress (`31`), the fix is pushed once to the pull request, and they
  return to Pending User QA (`41`) with a new `🎮 Playtest handoff`. This run reads no top-level pull request
  comments for playtest answers, so the reconfirmation handoff asks for `qa1` or `qa2` in the session, not a
  comment.
- **After the merge:** move every ticket to Done (`71`) with one comment: the pull request, the merge commit, the
  review and the checks it passed. When the merged pull request reverts an earlier one, return that one's tickets
  to To Do (`11`) in the active sprint, with a comment naming the revert and the failure.
- A failed Jira write goes into the report and never holds the merge. The next `flow1` or `flow2` reconciliation
  repairs a missing Done; nothing repairs a failed move back to To Do after a revert, so that one goes to
  "User needed" with the ticket keys.

## Merge

Check all of these on one fresh reading, in order:

1. The head is reviewed ("Which head is reviewed"): a Clean pass covers it, or Matěj approved this exact head
   after a Capped or Disagreement pass.
2. Checks are green on the head.
3. The pull request is open, based on `master`, not a draft (a draft goes to "User needed", as in "Start"),
   `mergeable` and not `dirty`.
4. A fresh listing shows every review thread resolved. An open thread sends the run back to step 5 of "Loop".
5. No human review requests changes.
6. No ticket of the pull request is in Pending User QA (a reconfirmation waiting for Matěj), and nothing else the
   user asked to hold for their own judgment is pending. A ticket left in Pending User QA although the
   description's `Playtest passed` line already names the head its latest `🎮 Playtest handoff` asked Matěj to
   retest means his `qa1` passed it and only the Jira move failed: retry the move under "Jira tickets" and do not
   hold the merge for it. When Matěj played the pull request's changes, find the **played head**, the later of:
   the published head the latest `🎮 Playtest passed` comment on its tickets names (from `qa1`); the published head
   in the description's `Playtest passed: played <sha>, published <sha>` line (`qa1` writes it, so it survives a
   failed Jira write). When the commits this pull request added after the played head (not changes that came in
   with `master`) change gameplay, as the flow's "QA classification" defines it, the current head was never
   played: move the tickets back under "Jira tickets" ("Reconfirmation") and go to "User needed": ask Matěj to
   retest the head with the launch commands for its branch and answer `qa1` or `qa2` with what is wrong. Commits
   that change nothing a player sees (docs, tests, a refactor its tests cover, a merge of `master` whose conflict
   resolutions touch nothing a player sees) need no new playtest. A change to `docs/roadmap.md` is no hold
   ([ADR 0021](../../../docs/decisions/0021-status-overview-and-plan-changes.md)).
7. The head you checked is still the head.

Then merge:

```bash
gh pr merge <n> --repo matejkadlec/realmforge --merge --match-head-commit <sha>
```

In a cloud session (`CLAUDE_CODE_REMOTE=true`) that command fails, because it uses GraphQL. Use the REST form,
which keeps the same head protection:

```bash
gh api -X PUT repos/matejkadlec/realmforge/pulls/<n>/merge -f merge_method=merge -f sha=<sha>
```

- Never use `--admin` or `--auto`, and never bypass protection.
- If GitHub refuses, read the message and `mergeable_state`. Fix what the run can (an open thread, a new head, a
  missing check, a conflict) and merge on the next clean reading.
- Only a refusal that needs the user, such as a ruleset setting or a required human approval, goes to "User
  needed".

## After the merge

1. Read the merge commit back from GitHub (`gh pr view <n> --repo matejkadlec/realmforge --json
   mergeCommit,state`) and move the tickets to Done under "Jira tickets". Remove the worktree and local branch
   this session created or took over with `qa1` (`git fetch --prune origin`, `git worktree remove <path>` without
   `--force` over uncommitted changes, `git branch -d <branch>`, `git worktree prune`). A branch in the main
   checkout is not removed with a worktree: switch the main checkout back to `master` (`git switch master`,
   `git merge --ff-only origin/master`) only when it is clean, then delete the branch. GitHub's
   `deleteBranchOnMerge` setting decides the remote branch; if the remote branch still exists, say so in the
   report and do not delete it.
2. **Follow CI on `master`** (`.github/workflows/ci.yml` runs on push to `master`; nothing deploys) to the end.
   Keep one check-in armed about 5 minutes ahead.
   - **Success** for the merge commit, or for a later commit that contains it: done.
   - **Cancelled** because a newer push superseded it: follow the newer run, which covers both commits.
   - **Failed because of this merge:** start a linked remediation at once. On a new branch from the current
     `origin/master`, fix the cause. If no small, safe fix exists, revert this merge instead
     (`git revert -m 1 <merge commit>`). Publish the fix as a ready pull request and run `finalize-pr` on it. A
     revert's description names the original pull request.
   - **Failed because of another merge:** leave the fix to that merge's owner. Keep following later runs about
     30 minutes apart until a run containing this merge succeeds. Until then, this change is not delivered.
   - If a deployment workflow is ever added, never re-run, dispatch or otherwise trigger it; a failed deployment
     with a repository-side cause from this merge gets the same linked remediation, and a host, secret or
     provider cause goes to "User needed".

## Stops

- **Merged and done:** after "After the merge", report, cancel any pending check-in or subscription, and stop.
- **Merged by someone else:** run "After the merge" in full, then report and stop.
- **Closed unmerged:** report, change nothing, and stop.
- **User needed:** a secret, a provider console, a repository setting, the runners, a product decision, a
  Capped, Disagreement or Blocked review pass, an open thread that needs the user, or a reconfirmation (tickets
  in Pending User QA) blocks the next step. Use the `**Your assistance is needed:**` form. Keep watching with one
  check-in about an hour apart, and continue as soon as the user answers or the blocking signal changes.
- **The user says stop:** cancel the waiting and the subscription, and report where the pull request stands.
- **The session must end first:** report where the pull request stands, and give `/finalize-pr <n>` as the
  command that continues it.

## Report

At each merge or stop, report as the status overview from `CLAUDE.md`: this pull request under "Merged into
master" (**What**, **Review**, **Checks**, **Jira**), or under "Pending CR" or "Pending User QA" with the step the
run stopped at; while it waits for a reconfirmation, in the manual-test shape with _What to test_ and the
launch commands, closing with how Matěj answers (`qa1` or `qa2`). The details below go into those bullets:

- the pull request number and URL, the branch and the base;
- the head SHA (merged or current), and whether the remote head equals your final commit;
- each review pass: its rounds and why it stopped, every finding with its severity and outcome (fixed in
  `<sha>`, rejected with the reason, tracked as `RLM-<n>`, dropped with the reason), how many the "Late rounds"
  rule rejected, and its finishing state;
- the other threads closed, check repairs and slow-check findings, conflict resolutions;
- the exact checks that passed locally, the CI state and the merge commit;
- the Jira tickets and the status each one ended in;
- "Follow-ups not ticketed": every follow-up that could not become a ticket because Jira was unreachable, with
  its file, scenario and smallest fix, under "Blocked or other";
- the post-merge outcome, or the step the run stopped at, with any linked remediation.
