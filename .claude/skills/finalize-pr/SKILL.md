---
name: finalize-pr
description: Run up to 3 independent review-and-fix rounds on one pull request in matejkadlec/realmforge. Each round starts a fresh, read-only pr-reviewer subagent; this session verifies and fixes the findings, then pushes once at the end. Use when the user types "finalize-pr 12", and as monitor-pr's fallback when Codex has hit its usage limit.
---

# finalize-pr: independent review-and-fix rounds

Bounded, independent code review of one pull request in `matejkadlec/realmforge`. **Maximum: 3 rounds.**

## Delegation

Covers one named pull request and only these actions:

- read and review it;
- fix verified findings on its branch;
- run the checks;
- push that branch once, at the end;
- open GitHub issues for real findings it does not fix here;
- report.

It never merges, deploys or requests a Codex review.

## Repository facts

- Repository: `matejkadlec/realmforge`. Default branch: `master`.
- There is no pre-push hook. The local gate is the command list in `CLAUDE.md` "Workflow" step 3 (see step 7).
- Worktrees live in `../realmforge-worktrees/<branch with "/" replaced by "-">`, outside the repository.
- The reviewer is `.claude/agents/pr-reviewer.md` (tools: Read, Grep, Glob).

## Rules

- **Scope.** The run covers one pull request in `matejkadlec/realmforge`. Before every write, check that
  `git remote get-url origin` points to `matejkadlec/realmforge`. A mismatch is a blocker. Never write to
  another pull request.
- **Never write `@codex` on the pull request**, in the description, comments, replies or code spans, and never
  quote Codex's own footer. GitHub hands any mention to Codex as a new request: a review, or a Cloud task that
  can push to the branch.
- **One push per run.** Every push starts a full CI run and can start a new Codex review. Commit once per root
  cause, push once at the end, then read the pull request's head back from GitHub and confirm that it equals
  your final commit. Any other head, even a descendant of your commit, means someone else pushed: treat it as a
  remote head that moved under you.
- **Never weaken anything to get green.** Never weaken, skip or disable a test, a required check, branch
  protection or a security policy. Never use `--no-verify`, `--admin` or any bypass.
- **No merging, no deploying.** Only [`monitor-pr`](../monitor-pr/SKILL.md) merges. Nothing deploys in this
  repository.
- **Secrets.** Never read, print or copy `.env` files, tokens, secrets or `.local/`. Never put them in a review
  bundle or a report.
- **Paginate** every GitHub listing with `gh api --paginate`.
- **Claude Code cloud sessions** (`CLAUDE_CODE_REMOTE=true`). The proxy refuses GraphQL, so `gh pr view` fails
  there. Read the pull request with `gh api repos/matejkadlec/realmforge/pulls/<n>` instead.
- **When the user is needed,** say it in exactly this form, with only the label bold:
  `**Your assistance is needed:** {what to do, where, and what you will check afterwards}`.
- **Reports state decisions, not open questions.** This skill asks the user nothing except under
  Disagreement and Blocked.

## Roles

- **Implementation context** (this session) orchestrates the run. It verifies every finding against the code,
  applies fixes, runs checks, pushes and reports.
- **Reviewer** is one fresh `pr-reviewer` subagent per round, started with the Agent tool and
  `subagent_type: pr-reviewer`, never any other type: other types inherit tools that can write. Never continue
  an earlier round's reviewer (no SendMessage to it), because a continued context is not an independent pass.
  When no fresh-context, read-only subagent is available (for example the agent is not listed because the
  session started before `.claude/agents/pr-reviewer.md` existed), do not simulate one. Stop and tell the user.
- Claude Code's built-in `/code-review` is not the independent pass: it runs in the invoking context. It can
  still run as an extra, non-independent pass.

## Procedure

1. **Resolve the target.** Read the pull request:

   ```bash
   gh pr view <n> --repo matejkadlec/realmforge --json number,url,state,isCrossRepository,headRefName,headRefOid,baseRefName
   ```

   Confirm that it belongs to `matejkadlec/realmforge`. Record its number, branch, base and remote head SHA.
   Run `git fetch origin`, then reuse the worktree that `git worktree list` holds for the branch, creating one
   only when none exists (`git worktree add ../realmforge-worktrees/<dir> <branch>`, then `uv sync` in it).
   Confirm that the local head equals the remote head. A mismatch, a cross-repository pull request, or a closed
   or merged one is a blocker: report it and stop.

2. **Build the review bundle** in the session scratchpad directory (or a `mktemp -d` directory outside the
   repository), never inside the repository:

   - `pr-<n>-round-<k>.diff`: the complete `git diff origin/master...HEAD`;
   - `pr-<n>-round-<k>-meta.txt`: the number, branch, base SHA (`git merge-base origin/master HEAD`), head SHA
     and changed files (`git diff --name-only origin/master...HEAD`).

   No secrets, logs or dumps go into the bundle.

3. **Review.** Start a fresh `pr-reviewer`. Give it the absolute bundle paths and the worktree root, and ask for
   its `VERDICT` output.

4. **Verify before acting.** Check every finding against the code yourself. For a Minecraft or NeoForge API
   claim, check the pinned sources in `build/mc-sources/` (`./gradlew :mod:extractMinecraftSources`).

   - Fix only confirmed findings, and only P1 and P2 inside the loop. A P3 fix would land after the review that
     cleared it, so P3s close under "P3 disposition" below.
   - Reject a finding only with a concrete technical reason, and record it. Valid reasons: the claimed path
     does not exist, a guard already exists at a named location, no input reaches the path, or the finding is
     outside this pull request's scope.
   - A real but out-of-scope finding becomes a GitHub issue (`gh issue create --repo matejkadlec/realmforge`,
     after searching `gh issue list` for an equivalent). Never dismiss it silently.

5. **Check and commit the round locally.** Run the targeted checks and commit the fix:

   - `content/` or `tools/`: `uv run rf gen` (no unexpected changes), `uv run ruff check`,
     `uv run ruff format --check`, `uv run pytest`;
   - `mod/`, `qa-mod/` or Gradle files: `./gradlew build` and `./gradlew runGameTestServer`
     (run `source ~/.sdkman/bin/sdkman-init.sh` first where SDKMAN exists);
   - `pack/`: `cd pack && packwiz refresh`, then `git diff --exit-code pack/`.

   Never edit generated files; change `content/` or the generator and run `uv run rf gen`. Do not push: the next
   reviewer reads the local head. A round that fixes nothing commits nothing.

6. **Repeat** from step 2 against the new local head, after running `git fetch origin` again.

   - Each round re-confirms that the remote head still equals the starting head. A remote head that moved under
     you exits as Blocked, and the local commits stay on the branch.
   - Stop early in any of these cases:
     - a review returns no P1 and no P2;
     - a round applies no fix (a fresh reviewer over identical code settles nothing);
     - a review repeats a finding you rejected with a recorded reason (this is a Disagreement).
   - A fix applied after the loop's final review was never independently reviewed. Its run finishes as Capped,
     never as Clean.

7. **Deliver once.** If the loop ended with at least one committed fix:

   - There is no pre-push hook, so run the full local gate:

     ```bash
     uv run rf gen && git status --porcelain     # must show no changes
     uv run ruff check && uv run ruff format --check
     uv run pytest
     ./gradlew build
     ./gradlew runGameTestServer
     ```

   - Push once (`git push origin <branch>`), read the remote head back
     (`gh pr view <n> --repo matejkadlec/realmforge --json headRefOid`), and confirm that it equals your final
     commit. A different head, even a descendant, is a remote head that moved under you: Blocked.
   - A failed push or gate is a delivery blocker, not a completed fix. Keep the commits local and report them.

8. **Report and stop.** When [`monitor-pr`](../monitor-pr/SKILL.md) started this run (its usage-limit fallback),
   return the finishing state to that run instead of stopping.

## P3 disposition

You have read the code, so the judgment is yours and the user never rules on a P3. Verify each P3 as carefully
as a P1, then close it in one of two ways:

- **Tracked:** open a GitHub issue with the file, the scenario and the smallest fix. Group P3s that share a file
  or a fix.
- **Dropped:** record a concrete technical reason. "Low severity" alone is not one.

Never fix a P3 on this branch. Never end the report with an offer to fix one "if you want".

## Finishing states

- **Clean.** The last round found no P1 or P2, and no fix landed after that review.
- **Capped.** Three reviews ran and the third still found a P1 or P2, or a fix landed after the final review.
  Say plainly that the head carries changes no reviewer has seen. List what the last review found and what you
  did about it. Never start a fourth round. When both apply, Capped outranks Disagreement, and the report also
  carries the disagreement record.
- **Disagreement.** The reviewer repeated a finding you rejected on a concrete technical basis. Report both
  positions with their evidence, and let the user decide.
- **Blocked.** Something is outside this delegation: a check you cannot fix safely, an external control, a
  secret, or a target that changed.

## Report

Include:

- the pull request number and URL, the branch and the base;
- the head SHA at the start and now, and whether the remote head equals your final commit;
- the CI state, read once after the push;
- the rounds that ran, and why the loop stopped;
- every finding with its severity and outcome: fixed, rejected with the reason, tracked as `#<issue>`, or
  dropped with the reason;
- the exact checks that passed;
- the finishing state.
