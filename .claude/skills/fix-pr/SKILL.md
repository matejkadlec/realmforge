---
name: fix-pr
description: Fix or explicitly close every ChatGPT Codex review finding on one pull request in matejkadlec/realmforge, in one pass with one push, then reply to and resolve each Codex thread. Use when the user types "fix-pr 12" (or just "fix-pr" for the pull request of the current branch), and when monitor-pr finds unresolved Codex threads.
---

# fix-pr: resolve the Codex findings on one pull request

One invocation is one pass over the Codex findings that exist when it starts, on one pull request in
`matejkadlec/realmforge`. Work through the procedure below, then report and stop.

## Delegation

Invoking this skill delegates one pull request: the number given, or the one the current branch maps to. It
covers only these actions:

- read the pull request and its review threads;
- fix verified Codex findings on its branch;
- run the checks;
- push that branch once;
- reply to and resolve its Codex threads;
- create RLM Jira tickets for real findings it does not fix here (never GitHub issues);
- report.

It never merges or deploys. It never requests a Codex review, and it never waits or polls for one. A push may
trigger Codex's automatic review of the new commits; that comes from the repository's Codex settings, not from
a request, and the user runs `fix-pr` again when that review lands. Codex reviewed PR #2's pushes ("New
commits") but none of PR #1's later pushes, so a new review is likely but not guaranteed.

Inside a [`monitor-pr`](../monitor-pr/SKILL.md) run, the pass reports back to `monitor-pr` instead of to the
user, and `monitor-pr` does the waiting.

## Repository facts

- Repository: `matejkadlec/realmforge`. Default branch: `master`.
- Codex is the ChatGPT Codex GitHub connector. Its author login is `chatgpt-codex-connector[bot]` in REST and
  `chatgpt-codex-connector` in GraphQL.
- Required checks on `master` (ruleset "Master Branch Protection", which also requires every review thread to
  be resolved): `Content pipeline (validation, pytest, generated files)`, `Gradle build, JUnit, GameTests`,
  `Vanilla ID universe is reproducible`, `Modpack index is up to date`.
- There is no pre-push hook. The local gate is the command list in `CLAUDE.md` "Workflow" step 3 (see step 6).
- Worktrees live in `../realmforge-worktrees/<branch with "/" replaced by "-">`, outside the repository.

## Rules

- **Scope.** The run covers one pull request in `matejkadlec/realmforge`. Before every write, check that
  `git remote get-url origin` is `git@github.com:matejkadlec/realmforge.git` or
  `https://github.com/matejkadlec/realmforge(.git)`. A mismatch is a blocker. Never write to another pull
  request.
- **Never write `@codex` on the pull request.** This covers the description, comments, thread replies and code
  spans, and quoting Codex's own footer (it contains such a mention). GitHub hands any mention to Codex as a
  new request: a review, or a Cloud task that can push to the branch.
- **One push per pass.** Every push starts a full CI run and can start a new Codex review. Commit once per root
  cause, then publish the whole pass with a single push. After the push, read the pull request's head back from
  GitHub and confirm that it equals your final commit. Any other head, even a descendant of your commit, means
  someone else pushed: treat it as a remote head that moved under you.
- **Never weaken anything to get green.** Never weaken, skip or disable a test, a required check, branch
  protection or a security policy. Never use `--no-verify`, `--admin` or any bypass.
- **No merging, no deploying.** Only `monitor-pr` merges. Nothing deploys in this repository; never re-run or
  dispatch a deployment workflow.
- **Secrets.** Never read, print or copy `.env` files, tokens, secrets or `.local/`. Never put them in a reply
  or a report.
- **Paginate.** GitHub returns comment, review and reaction listings 30 entries per page, oldest first. Read
  every page with `gh api --paginate` (or the GraphQL cursor below) before you filter.
- **Claude Code cloud sessions** (`CLAUDE_CODE_REMOTE=true`). The proxy refuses GraphQL, so `gh pr view`,
  `gh pr list` and the GraphQL calls below fail there. Use these REST forms instead:
  - Read a pull request: `gh api repos/matejkadlec/realmforge/pulls/<n>`.
  - List open pull requests for a branch: `gh api "repos/matejkadlec/realmforge/pulls?state=open&head=matejkadlec:<branch>"`.
  - List threads: `gh api repos/matejkadlec/realmforge/pulls/<n>/ccr/review_threads`. Each thread has
    `resolved`, `outdated`, `path`, `line` and `comment_ids`.
  - Read a comment: `gh api repos/matejkadlec/realmforge/pulls/comments/<id>`. `commit_id` and
    `original_commit_id` name the reviewed head.
  - Reply: `gh api repos/matejkadlec/realmforge/pulls/<n>/comments/<first comment id>/replies -f body=<reply>`.
  - Resolve: `gh api -X POST repos/matejkadlec/realmforge/pulls/<n>/ccr/comments/<first comment id>/resolve`.
- **When the user is needed,** say it in exactly this form, with only the label bold:
  `**Your assistance is needed:** {what to do, where, and what you will check afterwards}`.
- **Reports state decisions, not open questions.** The only questions this skill asks are the ambiguous-target
  question (step 1) and the uncommitted-work question (step 2).

## What counts as a finding

A finding is a review thread whose first comment was written by Codex. That comment normally opens with a
severity badge such as `![P1 Badge](...)` followed by a bold title.

- A finding is current while its thread is unresolved, even when GitHub marks it outdated. "Outdated" only
  means its lines moved.
- Resolved Codex threads are earlier rounds. The repeated-finding check reads them.
- A Codex thread without a badge counts as P1 until verification shows otherwise.
- A badge-labelled finding in a Codex review body or pull request comment is reported as unhandled and is not
  acted on. It has no thread to resolve, so [`monitor-pr`](../monitor-pr/SKILL.md) holds the merge for it until
  the user rules on it.
- These are not findings, and the pass leaves them alone:
  - human comments and other reviewers;
  - CI failures;
  - Codex's summary, status and usage-limit comments (for example the "Codex Review Summary" comment marked
    `<!-- codex-pull-request-review-summary -->`).
- One exception: a required check fails during this pass's push on something no fix touched. Fix it only when
  the fix is small, safe and within the pull request's intent, and report it. Otherwise the pass is Blocked.

## Procedure

1. **Resolve the target.** With a number, read it:

   ```bash
   gh pr view <n> --repo matejkadlec/realmforge --json number,title,url,state,isCrossRepository,headRefName,headRefOid
   ```

   Without a number:

   - Run `gh pr list --repo matejkadlec/realmforge --head <branch> --state open`.
   - Drop every cross-repository result. The target is the one result left.
   - A detached head, `master`, no result or several results is ambiguous. Never guess. Ask the user to choose
     from every open pull request, listing each one's number, title and count of unresolved Codex threads.

   Stop when the target is closed, merged or cross-repository.

2. **Locate the worktree.** Run `git fetch origin`. Reuse the worktree that `git worktree list` holds for the
   head branch (it may be the main checkout). Create one only when none exists:

   ```bash
   git worktree add ../realmforge-worktrees/<branch with "/" replaced by "-"> <branch>
   ```

   then run `uv sync` inside it. A new worktree has no `build/`, `run/` or `.cache/`, so its first Gradle build
   is slow.

   - A clean local branch that is strictly behind its remote: fast-forward it (`git merge --ff-only`).
   - Uncommitted changes, or a local branch that is ahead of or diverged from the remote: these may be another
     session's or the user's work. Change nothing, and ask the user whether to fold them in or stop.

   Record the remote head SHA the pass starts from. Work only inside this worktree from now on.

3. **Read every Codex thread**, following all pages:

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
   }' --jq '.data.repository.pullRequest.reviewThreads.nodes[]
     | select(.comments.nodes[0].author.login == "chatgpt-codex-connector")'
   ```

   The first comment's `originalCommit` is the head that review round saw. When an outdated thread's `line` is
   null, `originalLine` still locates it.

4. **Verify each current finding** against the branch as it stands, before you change anything. Read the
   flagged code, its callers and its tests, and the project instructions that apply to the path (`CLAUDE.md`,
   `content/README.md`, `tools/README.md`, `pack/README.md`). For a Minecraft or NeoForge API claim, check the
   pinned sources in `build/mc-sources/` (`./gradlew :mod:extractMinecraftSources` creates them), not
   upstream branches. Compare the finding with the resolved Codex threads and with the other current findings.
   Give it exactly one disposition (see "Dispositions").

5. **Fix** every finding whose disposition is Fixed:

   - Make the smallest correct change that keeps the pull request's intended behavior.
   - Findings that share a root cause get one shared fix.
   - Add or adjust a focused regression test (pytest in `tools/tests/`, JUnit in `mod/src/test/`, or a GameTest
     in `qa-mod/`).
   - Never edit generated files; change `content/` or the generator and run `uv run rf gen`.
   - Keep the unrelated changes already on the branch, and refactor nothing beyond what the finding needs.

6. **Check and deliver once.**

   - Run the targeted checks for what you changed:
     - `content/` or `tools/`: `uv run rf gen` (no unexpected changes), `uv run ruff check`,
       `uv run ruff format --check`, `uv run pytest`;
     - `mod/`, `qa-mod/` or Gradle files: `.claude/scripts/gradle-queue.sh build` and
       `.claude/scripts/gradle-queue.sh runGameTestServer`;
     - `pack/`: `cd pack && packwiz refresh`, then `git diff --exit-code pack/`.
   - Make one commit per root cause (Conventional Commits, staged by explicit path), so each reply can name its
     commit.
   - There is no pre-push hook, so run the full local gate once before the push. Gradle needs Java 21:
     run `source ~/.sdkman/bin/sdkman-init.sh` first where SDKMAN exists.

     ```bash
     uv run rf gen && git status --porcelain     # must show no changes
     uv run ruff check && uv run ruff format --check
     uv run pytest
     .claude/scripts/gradle-queue.sh build              # waits for memory while other worktrees build
     .claude/scripts/gradle-queue.sh runGameTestServer
     ```

   - Immediately before the push, run `git fetch origin` and require the remote head to still equal the
     starting head. A different head, even an ancestor after someone reset the branch, is a remote head that
     moved under you: do not push.
   - Publish every commit with one push that succeeds only while the remote is still at the starting head:
     `git push --force-with-lease=<branch>:<starting sha> origin <branch>`. The lease makes the check and the
     push atomic (a rejected lease is a remote head that moved under you). It never rewrites history here,
     because your commits descend from the starting head. Then read the head back
     (`gh pr view <n> --repo matejkadlec/realmforge --json headRefOid`) and confirm that it equals your final
     commit. A different head, even a descendant, is a remote head that moved under you.
   - A pass that fixed nothing pushes nothing.
   - If delivery fails (the push, the gate, authentication, or a remote head that moved under you), post
     nothing and resolve nothing. Keep the commits local and report them, every disposition, and the blocker.

7. **Close every thread.** Do this after step 6 delivers, or straight away if nothing was fixed.

   - If nothing was pushed, first re-read the remote head (`gh pr view <n> --repo matejkadlec/realmforge --json
     headRefOid`). It must still equal the starting head. If it moved, post nothing and resolve nothing: run
     `git fetch origin`, update the worktree as in step 2, and verify every finding again from step 4 against
     the new head.
   - First re-check each reply's evidence against the pushed head, because another finding's fix can change it.
   - Post one reply per thread. The reply opens with its disposition and carries its evidence. Then resolve the
     thread:

     ```bash
     gh api graphql -f thread=<thread id> -f body=<reply> -f query='
     mutation($thread: ID!, $body: String!) {
       addPullRequestReviewThreadReply(input: {pullRequestReviewThreadId: $thread, body: $body}) { comment { url } }
     }'
     gh api graphql -f thread=<thread id> -f query='
     mutation($thread: ID!) { resolveReviewThread(input: {threadId: $thread}) { thread { isResolved } } }'
     ```

   - Resolving is not optional. An open thread blocks the merge (the `master` ruleset requires resolved
     threads), and `monitor-pr` merges only once every thread is resolved.
   - Keep replies short and factual, with no secrets and no Codex mention.

8. **Re-read the review threads** (step 3 query).

   - Every thread that was current at the start is now resolved, or still open as Blocked. Resolve any closed
     thread that still reads unresolved.
   - A new Codex thread whose `originalCommit` is the starting head belongs to this pass. If the pass has not
     pushed yet, take it through steps 4–8. If it has pushed, close it now only when its disposition needs no
     commit (Declined, Tracked, Stale, Repeated or Incorrect, judged against the pushed head). One that needs a
     code change stays open for the next pass and goes into the report, so the pass keeps its single push.
   - A new thread on the pushed head is the next round: list it in the report and do not act on it.

9. **Report and stop** (see "Report").

## Dispositions

| Disposition | Use it when | The reply opens with |
| --- | --- | --- |
| Fixed | the finding is valid and this pass fixed it | `Fixed in <sha>:` the change and the check |
| Declined | a P2 or P3 is not worth doing, here or in a ticket | `Declined:` the concrete reason |
| Tracked | worth doing, but it belongs outside this pull request | `Tracked as RLM-<n>:` why it is outside |
| Stale | the branch already fixed it, or the flagged code no longer exists | `Stale:` the commit or current location |
| Repeated | it restates a finding an earlier round closed, and that closure holds | `Repeated:` the earlier thread, evidence |
| Incorrect | the failure it describes cannot happen | `Incorrect:` the evidence |
| Blocked | it cannot be closed within this delegation | no reply; the thread stays open |

- Two current findings that describe the same failure on the same input are duplicates. The second takes the
  first one's disposition and links to it.
- Findings with different failures but one cause share the fix, and each reply names the shared commit.
- **Tracked:** an RLM Jira ticket, never a GitHub issue ([flow, "Issues"](../../../docs/ai-development-flow.md#issues)).
  Search `RLM` first (`searchJiraIssuesUsingJql`, `project = RLM AND text ~ "<terms>"`, with comments). If an
  equivalent ticket is open, cite it and comment this pull request's evidence. If none is, create a Task in the
  active sprint (parent RLM-1 for workflow and tooling, the milestone epic for game code; `## Context`, `## Task`,
  `## Done when`) that carries the file, the scenario, the smallest fix, and any pull request it must wait for.
  Group findings that share a file or a fix into one ticket. When no Atlassian tool reaches Jira, reply
  `Tracked (Jira unreachable):` and list the follow-up in the report for Matěj.

## Severity

- **P0 and P1 must be fixed.** A valid one is Fixed. It is Tracked instead only when the defect already exists
  on `master` in code this pull request neither changes nor depends on. It is Blocked only when the fix needs
  one of these:
  - a product or architecture decision this pull request did not already make;
  - an external control;
  - a secret.

  It is never Blocked because of effort, size or schedule, or over how to implement a fix inside the pull
  request's own feature. It is never Declined.
- **P2 and P3 are optional.** Fix one when it is a real defect in this change and the fix is contained and
  verifiable. Otherwise track or decline it, with a concrete technical reason: the scope, risk or churn it
  adds, or the decision it presupposes. "Optional" and "low severity" are not reasons. An optional finding
  still gets a reply and is resolved.
- **Evidence beats the badge.** Stale, Repeated and Incorrect apply at every severity, but only with concrete
  evidence from the current branch: a commit, the file and line of a guard, why no input reaches the path, or
  an earlier thread whose closure still holds. When the verdict rests on a test, run it. A plausible argument
  is not evidence.

## Repeated findings

Codex can re-report an already-fixed problem in new words. Before you change code for a finding that resembles
a resolved thread:

1. Compare it with that thread, its reply, and the diff since that round.
2. Run the test that shows whether the branch already resolves the problem.
3. Check whether the proposed change would only rework the same area without fixing a concrete failure that
   remains.

If the code already does what was intended, the finding is Repeated, even at P1. A repeat of a declined or
tracked finding is Repeated too while the earlier reason holds, even with a higher badge, unless it names a
concrete input the earlier closure does not cover.

## Report

Include:

- the pull request number and URL, the branch, and the head SHA at the start and now;
- if the pass pushed, whether the remote head equals your final commit, and the CI state read once
  after the push (never polled);
- one line per finding: severity, title, disposition, and the commit, ticket or evidence;
- what was left unchanged and why;
- the exact checks that passed;
- any RLM tickets created or cited;
- any Codex thread left for the next round.

With no current findings, say so and change nothing.
