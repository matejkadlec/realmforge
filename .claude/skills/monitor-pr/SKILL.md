---
name: monitor-pr
description: Carry one published pull request in matejkadlec/realmforge to its merge. Waits for the ChatGPT Codex review, runs fix-pr on its findings, resolves every review thread, fixes failing or stuck checks, resolves conflicts with master, and merges once Codex approves or stays silent, every required check is green and no needs-playtest label is set. Use when the user types "monitor-pr 12", and right after a session publishes or updates a ready pull request as its own task work.
---

# monitor-pr: from published pull request to merge

## When it runs

- The user says `monitor-pr <n>`.
- Right after a session publishes or updates a ready (non-draft) pull request as its own task work. The
  publication report is a progress report, and the next action is `monitor-pr <n>`. A standalone
  [`fix-pr`](../fix-pr/SKILL.md) or [`finalize-pr`](../finalize-pr/SKILL.md) push is not a publication.

**Its job is the merge.** The run is not finished at a report, a green check or a clean round. It ends only at
one of the "Stops" below.

## Delegation

Covers only these actions:

- everything [`fix-pr`](../fix-pr/SKILL.md) covers;
- replying to and resolving every other review thread;
- fixing failing or stuck required checks on the branch;
- resolving conflicts with `master`;
- merging under "Merge";
- remediation under "After the merge";
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
- Codex is the ChatGPT Codex GitHub connector: `chatgpt-codex-connector[bot]` in REST, `chatgpt-codex-connector`
  in GraphQL. It reviews when a pull request opens and usually after new commits (trigger "New commits" in its
  summary); on PRs #1 and #2 each review landed 5–7 minutes after the push, but none of PR #1's later pushes was
  reviewed. Its "Codex Review Summary" comment (`<!-- codex-pull-request-review-summary -->`)
  is a status comment, not a finding.
- The label `needs-playtest` means the owner (Matěj) plays the change before it merges. The publishing session
  adds it for gameplay, visual or balance changes (`gh pr edit <n> --add-label needs-playtest`); only Matěj
  removes it.
- There is no pre-push hook. The local gate is the command list in `CLAUDE.md` "Workflow" step 3.
- Worktrees live in `../realmforge-worktrees/<branch with "/" replaced by "-">`, outside the repository.

## Rules

- **Scope.** The run covers one pull request in `matejkadlec/realmforge`. Before every write, check that
  `git remote get-url origin` points to `matejkadlec/realmforge`. A mismatch is a blocker. Never write to
  another pull request.
- **Never write `@codex` on the pull request.** This covers the description, comments, thread replies and code
  spans, and quoting Codex's own footer (it contains such a mention). GitHub hands any mention to Codex as a
  new request: a review, or a Cloud task that can push to the branch.
- **One push per pass.** Every push starts a full CI run and can start a new Codex review. Commit once per root
  cause, then publish each pass with a single push. After the push, read the pull request's head back from
  GitHub and confirm that it equals your final commit. Any other head, even a descendant of your commit, means
  someone else pushed: treat it as a remote head that moved under you, and read it before acting again.
- **Never weaken anything to get green.** Never weaken, skip or disable a test, a required check, branch
  protection or a security policy. Never use `--no-verify`, `--admin`, `--auto` or any bypass.
- **Merging and deploying.** This skill is the only one that merges. Nothing deploys in this repository; never
  re-run, dispatch or otherwise trigger a deployment workflow.
- **Secrets.** Never read, print or copy `.env` files, tokens, secrets or `.local/`. Never put them in a reply
  or a report.
- **Paginate.** GitHub returns comment, review and reaction listings 30 entries per page, oldest first. Read
  every page with `gh api --paginate` before you filter.
- **Claude Code cloud sessions** (`CLAUDE_CODE_REMOTE=true`). The proxy refuses GraphQL, so `gh pr view` and
  `gh pr list` fail there. Use these REST forms instead:
  - Read a pull request: `gh api repos/matejkadlec/realmforge/pulls/<n>`.
  - List threads: `gh api repos/matejkadlec/realmforge/pulls/<n>/ccr/review_threads`. Each thread has
    `resolved`, `outdated`, `path`, `line` and `comment_ids`.
  - Read a comment: `gh api repos/matejkadlec/realmforge/pulls/comments/<id>`. `commit_id` and
    `original_commit_id` name the reviewed head.
  - Reply: `gh api repos/matejkadlec/realmforge/pulls/<n>/comments/<first comment id>/replies -f body=<reply>`.
  - Resolve: `gh api -X POST repos/matejkadlec/realmforge/pulls/<n>/ccr/comments/<first comment id>/resolve`.
  - Merge: `gh api -X PUT repos/matejkadlec/realmforge/pulls/<n>/merge -f merge_method=merge -f sha=<sha>`
    (`gh pr merge` sends a GraphQL mutation; with `sha` GitHub refuses the merge when the head moved).
- **When the user is needed,** say it in exactly this form, with only the label bold:
  `**Your assistance is needed:** {what to do, where, and what you will check afterwards}`.
- **Reports state decisions, not open questions.** This skill asks the user only under "User needed", the
  conflict case in step 2 and the thread cases in step 4.

## Start

Do this before the first wake, and again whenever the run resumes in a new session:

1. **Resolve the target:**
   `gh pr view <n> --repo matejkadlec/realmforge --json number,title,url,state,isDraft,isCrossRepository,headRefName,headRefOid,baseRefName`.
   A closed or merged pull request goes to "Stops". A cross-repository pull request is a blocker: report it and
   stop. A draft is not ready to merge: go to "User needed" and ask the user to mark it ready for review (or to
   say it should stay a draft), and keep watching until it is ready. This skill merges only into `master`, and
   every step below compares with `master`. A pull request whose `baseRefName` is another branch depends on
   unmerged work: keep watching with check-ins about 30 minutes apart until that base has merged into
   `master`, then retarget it (`gh pr edit <n> --repo matejkadlec/realmforge --base master`; GitHub does this
   itself when it deletes the merged base branch) and continue. Never merge into another feature branch. If the
   base's pull request closes without merging, or its branch disappears without having merged, the wait cannot
   end: go to "User needed" and ask whether to retarget this pull request to `master` or close it.
2. **Locate the worktree** exactly as [`fix-pr`](../fix-pr/SKILL.md) step 2 does: `git fetch origin`, reuse the
   worktree that `git worktree list` holds for the head branch, or create one in `../realmforge-worktrees/` and
   run `uv sync` in it. Fast-forward a clean branch that is strictly behind its remote. On uncommitted changes,
   or a local branch that is ahead of or diverged from the remote, change nothing and ask the user.
3. **Confirm that the local head equals the remote head,** and record whether this session created the branch
   (it decides rebase versus merge in step 2 and the cleanup after the merge).

Run every git command, check, commit and push of this run inside that worktree, never in the current checkout
on its behalf. Before each push, run `git fetch origin` again and confirm that the remote head is still the one
your work is based on, then push with `--force-with-lease=<branch>:<that sha>` so the push fails if it moved in
between. A remote head that moved under you is new work to read first, never something to overwrite.

## Signals

Read each signal fresh from GitHub at every step, and never trust an earlier reading.

- **Head.** The head SHA (`gh pr view <n> --repo matejkadlec/realmforge --json headRefOid`), and its *push
  time*. When this run pushed the head itself, that is the time of its push. Otherwise it is the `created_at`
  of the newest `pull_request` workflow run for that SHA:
  `gh api "repos/matejkadlec/realmforge/actions/runs?head_sha=<sha>&event=pull_request" --jq '[.workflow_runs[].created_at] | max'`.
  CI starts the moment a push lands, so this works on the first reading of a resumed run too; after a reset or
  force-push back to an older SHA, the newest run belongs to the latest push; a re-run keeps its original
  `created_at`. The head's *seen time* is when this run first read that head; it stands in for the push time
  until such a run exists.
- **Codex reaction and status.** Reactions on the pull request by Codex
  (`gh api --paginate repos/matejkadlec/realmforge/issues/<n>/reactions`), and its "Codex Review Summary"
  comment (`<!-- codex-pull-request-review-summary -->`, edited in place), whose table names the reviewed
  commit and the review's status.
  - `eyes` means Codex is reviewing, and so does a summary row for the current head that is not yet
    Completed. On PRs #1 and #2 no Codex reaction remained after the review ended, so `eyes` may be gone.
  - `+1` means Codex found nothing. It counts for the current head only when the review it ends was of this
    head: the summary comment's latest Code Review row shows Completed for the current head's short SHA, or
    Codex's latest `eyes` is later than the head's push time and the `+1` is later than that `eyes`. A `+1`
    whose review started before the push (an `eyes` older than the push time, or a summary row naming another
    commit) approved an earlier head, even when the `+1` itself arrived after the push. A later `eyes` means
    another review is running.
  - A Codex review (`gh api --paginate repos/matejkadlec/realmforge/pulls/<n>/reviews`) belongs to the head in
    its `commit_id`.
  - Any other Codex error comment counts as silence.
- **Codex usage limit.** A Codex comment (`gh api --paginate repos/matejkadlec/realmforge/issues/<n>/comments`)
  whose body opens with `You have reached your Codex usage limits for code reviews`. It is *current* while none
  of these exists after it:
  - a Codex review of the current head (its `commit_id` in
    `gh api --paginate repos/matejkadlec/realmforge/pulls/<n>/reviews`);
  - a Codex summary row for the current head, running or Completed (an `eyes` reaction alone does not count:
    reactions carry no commit, and a delayed review of an older head can add one);
  - a `+1` that counts for the current head.
- **Codex task replies.** A Codex comment opening with `### Summary` answers a Codex mention as a Cloud task. It
  is not a review.
  - Remove the mention that triggered it; it is often in the description.
  - Carry out any change it proposes yourself, and only after verifying it like a finding.
- **Review threads.** Every review thread, whoever opened it (the GraphQL query in
  [`fix-pr`](../fix-pr/SKILL.md) step 3 without the Codex filter, or the REST form above).
  - *Codex findings* are the unresolved threads whose first comment is Codex's.
  - Any open thread, Codex's or anyone else's, blocks the merge: the `master` ruleset requires resolution.
- **Checks.** Every check run on the head SHA.
  - Green means each one completed with `success`, `neutral` or `skipped`, and all four required checks
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
- **Labels.** `needs-playtest` on the pull request (`gh pr view <n> --repo matejkadlec/realmforge --json labels`).

## Loop

Each round belongs to one head, and every push starts a new round. At every wake, re-read the signals:

- A merged or closed pull request goes straight to "Stops".
- A current usage limit goes to "Usage-limit fallback" before anything else, unless the run is already in
  fallback mode.

Otherwise act on the first of steps 2–7 that applies. If none applies yet, wait under step 1.

1. **Wait for the round.** Use exactly one waiting mechanism per round, and re-read the signals at most once a
   minute.

   - Reactions arrive through no event, so always keep one check-in armed for the next moment a signal can
     change:
     - 10 minutes after the head's push time;
     - 10 minutes after Codex's latest `eyes`;
     - 5 minutes later while checks are still running;
     - 5 minutes after the seen time while no check run exists;
     - 30 minutes later while a red check waits for a fix on `master`;
     - about an hour later while the run waits for the user ("User needed").
   - In a local Claude Code session, the harness blocks a foreground `sleep`. Use the `Monitor` tool with an
     until-loop, or a background Bash `until` loop whose exit wakes the run, for example (run in the
     background):

     ```bash
     deadline=$(( $(date +%s) + 600 ))   # the check-in time
     last="$(gh api repos/matejkadlec/realmforge/pulls/<n> --jq .updated_at)"
     until [ "$(date +%s)" -ge "$deadline" ] \
       || [ "$(gh api repos/matejkadlec/realmforge/pulls/<n> --jq .updated_at)" != "$last" ]; do sleep 60; done
     ```

   - In a cloud session, subscribe once to the pull request's activity (`subscribe_pr_activity`) and arm one
     `send_later` check-in.

2. **Conflict: resolve it.** On `dirty` or `behind`, bring `master` in before anything else.

   - Rebase a branch this session created onto `origin/master`, and push with
     `--force-with-lease=<branch>:<remote head before the rebase>`. Merge
     `origin/master` into anyone else's branch.
   - Keep the intent of both sides.
   - Regenerate generated files with the project's own tooling, never by hand: `uv run rf gen` for
     `mod/src/generated/`, `qa-mod/src/generated/` and `tools/schemas/`; `uv lock` for `uv.lock`;
     `cd pack && packwiz refresh` for `pack/index.toml`; `uv run rf ids vanilla` for `tools/id_universe/`.
   - Run the checks that the conflicted files need (see [`fix-pr`](../fix-pr/SKILL.md) step 6).
   - Ask the user only when both sides changed the same behavior and keeping either one loses the other.
   - The push starts a new round. Never merge on the strength of the round before the conflict.

3. **Codex findings: run `fix-pr`.** Whenever the head has unresolved Codex threads, invoke `/fix-pr <n>`,
   every round. Do not repeat its procedure here; its report returns to this run.

   - Then re-list the threads. Resolve any thread that `fix-pr` closed but that still reads unresolved.
   - A Blocked thread goes to the user under "User needed".
   - A badge-labelled Codex finding (`![P0 Badge]` to `![P3 Badge]`) in a Codex review body or a top-level pull
     request comment on the current head has no thread, so `fix-pr` only reports it as unhandled. It holds the
     merge: go to "User needed" with the finding and your verification of it, and continue once the user rules
     on it.
   - A push from `fix-pr` starts a new round.

4. **Other open threads: close them.**

   - **The user's own thread** is an instruction for this pull request. If it stays within the pull request's
     intent, carry it out with `fix-pr`'s verification and single-push delivery, then reply naming the commit
     and resolve the thread. If it needs a product decision or goes beyond the pull request, reply with a
     proposal, leave the thread open, and ask under "User needed".
   - **Another person's thread:** carry out unasked only a small, verified ask within the pull request's
     intent. For anything else, reply with a proposal and ask the user.
   - **Another bot's thread:** verify it like a Codex finding, and close it with a `fix-pr` disposition.
   - A thread whose reply already settles it only needs resolving.
   - Never resolve a thread without a reply that settles it.
   - **A changes-requested review:** carry out what it asks, or reply with a proposal. Only its author can
     withdraw the request, so ask the user to re-review or dismiss it. The merge waits until then.

5. **Failing check: fix it and push.** Read the failing job's log (`gh run view <run id> --log-failed`; for the
   Gradle job also `gh run download <run id> -n gradle-logs-and-reports` into the scratchpad).

   - **Not this pull request's failure** needs evidence: the same check also fails on `master`'s latest run
     (`gh run list --repo matejkadlec/realmforge --branch master --limit 5`), or the failure reproduces on the
     base commit (`git merge-base origin/master HEAD`, checked out with `git worktree add --detach` in the
     scratchpad). A failure in a file or test the diff never touches is not evidence by itself: a changed
     generator, API or shared configuration can break untouched code. With that evidence, port an existing fix
     if one exists. Otherwise say what fails and why, and bring the base in (step 2) once it carries the fix.
     Re-read the latest CI on `master` at each check-in, because no pull request event announces it.
   - **This pull request's failure:** root-cause it, fix it on the branch, run the targeted checks, and push
     once. A described fix is not a fix, and a red head is never left waiting.
   - "Flake" is not a cause. Re-run a job only when it died before any test ran, and at most once
     (`gh run rerun <run id> --failed`).
   - A run cancelled because a newer push superseded it belongs to the old head. A cancelled required check on
     the current head gets one re-run.

6. **Slow or stuck checks: find out why.** CI normally finishes within about 2 minutes, and jobs time out after
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
     job on a recent successful run. A hang ends at the timeout. Then root-cause it under step 5: a hanging test
     (for example a GameTest that never calls `succeed`) is a defect, not a flake.

7. **Clean round: merge.** The round is clean when no review thread is open and one of these holds:

   - Codex's `+1` counts for this head;
   - Codex completed a review of this head (a Codex review whose `commit_id` is the head, or the summary row
     Completed for its short SHA), and every thread from that review has been closed by `fix-pr`, with or
     without a code change. Without a new push no further review or `+1` comes, so this ends the round;
   - 10 minutes have passed since the later of the push time and Codex's latest `eyes`, with no Codex review or
     reaction on this head and no summary row for this head still running.

   Codex's automatic trigger can leave a round with neither a review nor a `+1` (none of PR #1's later pushes
   was reviewed), so the silent window ends the wait instead of holding the pull
   request forever. A clean round goes straight to "Merge" in the same wake, as soon as checks are green, with
   no further approval or report. A finding that lands after the window still goes through step 3 before any
   merge.

## Usage-limit fallback

A current usage limit means no Codex review is coming. The silent window would then merge with no review at
all, so instead:

1. **Run `/finalize-pr <n>` on the current head in this run** ([`finalize-pr`](../finalize-pr/SKILL.md)). The
   fresh reviewer it starts provides the independent review. Its finishing state comes back here.
2. **The run enters fallback mode for this pull request.**
   - It treats the usage-limit comment as silence from now on.
   - Every later push needs another `/finalize-pr <n>` pass on the new head before the merge. This includes a
     conflict resolution, a base merge, a check repair, and a `fix-pr` or thread commit. A textually clean merge
     says nothing about how two changes interact.
3. **Act on the finishing state.**
   - Clean: satisfies merge checks 1 and 7 for exactly that head.
   - Capped or Disagreement: report and ask under "User needed". The user's explicit approval of that exact
     head satisfies check 7.
   - Blocked: ask under "User needed".

## Merge

When Codex is done with the head (its `+1`, a completed review whose findings are all closed, or the silent
window), the run merges unless check 6 holds it.
Check all of these on one fresh reading, in order:

1. The round is clean under step 7, or, in fallback mode, check 7 already holds for this head (a Clean
   `finalize-pr` of this exact head, or the user's approval of it) and no review thread is open. A stale
   `eyes` or running summary row from the failed Codex attempt then does not hold the merge.
2. Checks are green on the head.
3. The pull request is open, based on `master`, not a draft (a draft goes to "User needed", as in "Start"),
   `mergeable` and not `dirty`.
4. A fresh listing shows every review thread resolved. An open thread sends the run back to step 3 or 4.
5. No human review requests changes.
6. No `needs-playtest` label is set, no badge-labelled Codex finding outside a thread is waiting for the user
   (step 3), and nothing else the user asked to hold for their own judgment is pending. If the pull request ever
   carried the label, its latest removal (the last `unlabeled` event for `needs-playtest` in
   `gh api --paginate repos/matejkadlec/realmforge/issues/<n>/events`) must be later than the current head's
   push time. A head pushed after the playtest was never played: re-add the label
   (`gh pr edit <n> --repo matejkadlec/realmforge --add-label needs-playtest`).
   While the label is set, go to "User needed": ask Matěj to playtest the head (for example with
   `./gradlew runClient`, or `./gradlew :mod:installToPrism` and the Prism instance) and remove the label.
7. In fallback mode: a Clean `finalize-pr` reviewed this exact head, or the user approved this exact head after
   a Capped or Disagreement report.
8. The head you checked is still the head.

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
   mergeCommit,state`). Remove the worktree and local branch this session created (`git fetch --prune origin`,
   `git worktree remove <path>` without `--force` over uncommitted changes, `git branch -d <branch>`,
   `git worktree prune`). GitHub's `deleteBranchOnMerge` setting decides the remote branch; if the remote
   branch still exists, say so in the report and do not delete it.
2. **Follow CI on `master`** (`.github/workflows/ci.yml` runs on push to `master`; nothing deploys) to the end.
   Keep one check-in armed about 5 minutes ahead.
   - **Success** for the merge commit, or for a later commit that contains it: done.
   - **Cancelled** because a newer push superseded it: follow the newer run, which covers both commits.
   - **Failed because of this merge:** start a linked remediation at once. On a new branch from the current
     `origin/master`, fix the cause. If no small, safe fix exists, revert this merge instead
     (`git revert -m 1 <merge commit>`). Publish the fix as a ready pull request and run `monitor-pr` on it. A
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
  Blocked thread or a `needs-playtest` label blocks the next step. Use the `**Your assistance is needed:**`
  form. Keep watching with one check-in about an hour apart, and continue as soon as the user answers or the
  blocking signal changes.
- **The user says stop:** cancel the waiting and the subscription, and report where the pull request stands.
- **The session must end first:** report where the pull request stands, and give `/monitor-pr <n>` as the
  command that continues it.

## Report

At each merge or stop, report:

- the pull request number and URL;
- the head SHA (merged or current);
- each round's outcome:
  - the Codex reaction;
  - the `fix-pr` pass and its commits;
  - the other threads closed;
  - check repairs and slow-check findings;
  - conflict resolutions;
  - any usage-limit fallback and its `finalize-pr` outcome;
- the CI state and the merge commit;
- the post-merge outcome, or the step the run stopped at, with any linked remediation.
