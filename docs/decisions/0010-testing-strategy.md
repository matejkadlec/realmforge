# 0010. Testing strategy

Date: 2026-10-11

Status: Accepted

## Context

The owner cannot review Java deeply and has limited playtest time, and AI agents hallucinate APIs. Most bugs must be caught automatically before anyone launches the game. The release jar should contain no test code or test structures. Layer details, balance-as-test ideas and QA mod ideas: [research notes, section 6](../research-notes.md#6-testing-and-qa).

## Decision

Layered tests, fastest first:

| Layer | Tool | When | Status |
| --- | --- | --- | --- |
| Data: schema, ID references, semantic rules | pytest over `content/` and `tools/` | every commit | now (M0) |
| Logic: stat formulas, affix rolls, talents | JUnit in `mod/src/test` | every commit | now (M0) |
| World (server): boss phases, dungeon instances, loot, quest advancements | GameTest in the QA mod, `./gradlew runGameTestServer` | every PR | now (M0) |
| Generated files match the repo | regenerate + `git diff --exit-code` | every PR | now (M0) |
| Whole pack boots on a dedicated server, clean log | packwiz-installer, `NEOFORGE_DEDICATED_SERVER_SELFTEST`, log grep | every PR | later |
| Client loads with Iris and Sodium, screenshots, icon sheet | mc-runtime-test (Xvfb, software rendering) | nightly | later |
| Balance: TTK, DPS, boss phase survival, time to item | Python simulation over the same YAML | every PR | later |
| Performance: tick time, allocations | spark profile as an artifact | nightly | later |

- **GameTests live in the dev-only QA mod** (`qa-mod/`, mod ID `realmforge_qa`, depends on the core mod, never released). `./gradlew runGameTestServer` runs them headless and exits non-zero on failure.
- **Balance is a test:** e.g. the expected time to obtain a specific item must not exceed a limit (such as 60 minutes).
- Every PR runs the "now" layers locally before it is opened, and in CI.

## Consequences

- The release jar contains no test code or test structures.
- GameTest cannot test client rendering; NeoForge has no equivalent of Fabric's client gametests. Software-rendered screenshots are for visual checks only, not pixel comparison.
- No test says whether the game is fun: the owner's playtests remain essential.
- The QA mod will grow beyond GameTests (`/qa` commands, training dummy with DPS meter, combat log, overlays, scenarios), which is why it is a separate mod.
