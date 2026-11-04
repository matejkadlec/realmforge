# 0071. The pack boots are required checks

Date: 2026-11-04

Status: Accepted (amends [0018](0018-pack-boot-tests.md)'s consequence "the jobs protect `master` only once Matěj adds
them to the ruleset's required checks")

## Context

[0018](0018-pack-boot-tests.md) added two CI jobs that boot the whole pack with our jar, `Full pack boots on a
dedicated server` (`pack-boot`) and `Full pack boots on a client` (`pack-boot-client`), on every pull request and
`master` push. They were left out of the `master` ruleset's required checks, whose four jobs are the content pipeline,
the Gradle build with JUnit and GameTests, the vanilla ID universe and the packwiz index. `finalize-pr` already holds a
merge on any red check, but the ruleset itself would let a pull request merge while a pack boot fails or has not
reported.

Matěj left the question to Claude on 2026-11-04 (RLM-624, item 7), and Claude decided yes: an agent merges on its own,
and a pack that does not start is the worst regression a merge can bring.

## Decision

1. **Both pack boot jobs are required checks** on `master`, next to the four of before, matched by their job `name:`:
   `Full pack boots on a dedicated server` and `Full pack boots on a client`.
2. **Matěj adds them to the ruleset** "Master Branch Protection" in GitHub; no agent changes a ruleset. Until he does,
   `finalize-pr`'s rule that every check on the head is green is what holds the merge.
3. **A flaky job leaves the required set again.** If a pack boot fails for reasons outside the pull request (a
   Modrinth or CurseForge outage, a runner problem) often enough to hold good merges, it goes back to an ordinary
   check, recorded in a note here, and its cause gets a ticket ([0018](0018-pack-boot-tests.md) names caching the
   mods as the fix for outages).

## Consequences

- Six required checks: `CLAUDE.md` (Workflow 5) and `finalize-pr`'s repository facts list them.
- A merge waits for the slower boots (up to 30 minutes each by their timeout, a few minutes in practice).
- Renaming either job's `name:` breaks the merge gate as for the other four (`CLAUDE.md`, Pull requests).
