# Architecture decision records

This directory records Realmforge's significant decisions as short ADRs (Michael Nygard style); see [0001](0001-record-architecture-decisions.md) for the rules. In short:

- One decision per file: `NNNN-kebab-case-title.md` with Date, Status, Context, Decision, Consequences.
- New decisions get the next number and a row in the table below.
- Accepted ADRs are not rewritten. A changed decision gets a new ADR, and the old one is marked `Superseded by NNNN`, never deleted.
- ADRs bind agents, not Matěj ([0030](0030-adrs-bind-agents-not-the-owner.md)). Agents follow them strictly in what they decide on their own. When he asks for a change that conflicts with one, the agent names the ADR and its main consequence once, never refuses, and once he confirms, makes the change with its superseding or amending ADR in the same pull request.
- Upgrading any pinned version (Minecraft, NeoForge, ModDevGradle, Gradle, Java) requires a new ADR.

Background: [vision](../vision.md) (the owner's goals) and [research notes](../research-notes.md) (reasoning, comparisons, sources). How the decisions fit together in code: [architecture](../architecture.md); the plan: [roadmap](../roadmap.md).

| # | Title | Status |
| --- | --- | --- |
| [0001](0001-record-architecture-decisions.md) | Record architecture decisions | Accepted, amended by [0030](0030-adrs-bind-agents-not-the-owner.md) |
| [0002](0002-hybrid-core-mod-and-modpack.md) | Hybrid: own core mod plus a modpack | Accepted |
| [0003](0003-platform-minecraft-1-21-1-neoforge.md) | Platform: Minecraft 1.21.1 on NeoForge | Superseded by [0026](0026-platform-minecraft-26-1-2-neoforge.md) |
| [0004](0004-build-rpg-systems-in-house.md) | Build all RPG systems in-house | Accepted |
| [0005](0005-content-pipeline-yaml-single-source-of-truth.md) | Content pipeline: YAML as the single source of truth | Accepted, amended by [0014](0014-core-mod-architecture.md) |
| [0006](0006-quests-ftb-quests-generated-from-data.md) | Quests: FTB Quests generated from data | Accepted |
| [0007](0007-asset-pipeline.md) | Asset pipeline | Accepted, refined by [0023](0023-texture-generator.md), vanilla texture rule narrowed to texture files by [0028](0028-dungeon-rooms-generated-or-imported.md) |
| [0008](0008-distribution-github-and-curseforge.md) | Distribution: GitHub Releases now, CurseForge later | Accepted |
| [0009](0009-licensing.md) | Licensing | Accepted, third-party rooms added by [0028](0028-dungeon-rooms-generated-or-imported.md) |
| [0010](0010-testing-strategy.md) | Testing strategy | Accepted, amended by [0018](0018-pack-boot-tests.md) |
| [0011](0011-repository-layout-and-workflow.md) | Repository layout and workflow | Accepted |
| [0012](0012-procedural-dungeons-and-dimensions.md) | Procedural dungeons and dimensions | Accepted, refined by [0019](0019-dungeon-instances-in-plots.md), its Nether and End question resolved by [0025](0025-remove-the-nether-and-end.md), hand-built rooms replaced by [0028](0028-dungeon-rooms-generated-or-imported.md) |
| [0013](0013-modpack-composition.md) | Modpack composition | Accepted (written separately), composition updated for 26.1.2 by [0026](0026-platform-minecraft-26-1-2-neoforge.md) |
| [0014](0014-core-mod-architecture.md) | Core mod architecture | Accepted, decision 9 amended by [0027](0027-client-bound-payload-handlers-through-the-client-event.md), decision 8 (hand-made rooms) by [0028](0028-dungeon-rooms-generated-or-imported.md) |
| [0015](0015-merging-through-monitor-pr.md) | Merging through monitor-pr | Superseded by [0029](0029-independent-claude-review-before-every-merge.md); was Accepted (amends 0011), holds extended by [0016](0016-jira-batch-delivery.md) and [0017](0017-jira-as-the-source-of-task-status.md), `needs-approval` removed by [0021](0021-status-overview-and-plan-changes.md), `needs-playtest` removed by [0024](0024-no-github-labels.md) |
| [0016](0016-jira-batch-delivery.md) | Jira batch delivery with flow1 and flow2 | Accepted (extends 0011), amended by [0017](0017-jira-as-the-source-of-task-status.md), [0021](0021-status-overview-and-plan-changes.md) and [0022](0022-playtest-before-the-pull-request.md) |
| [0017](0017-jira-as-the-source-of-task-status.md) | Jira as the source of task status | Accepted (amends 0015 and 0016), amended by [0021](0021-status-overview-and-plan-changes.md) |
| [0018](0018-pack-boot-tests.md) | Pack boot tests: server and client self tests on every PR | Accepted (amends 0010), adjusted for 26.1.2 by [0026](0026-platform-minecraft-26-1-2-neoforge.md) |
| [0019](0019-dungeon-instances-in-plots.md) | Dungeon instances in plots | Accepted (refines 0012) |
| [0020](0020-cast-animations-and-melee-while-casting.md) | Cast animations through playerAnimator, no melee while casting | Accepted, amended by [0026](0026-platform-minecraft-26-1-2-neoforge.md) (Player Animation Library) |
| [0021](0021-status-overview-and-plan-changes.md) | A status overview ends every response; plan changes without a label | Accepted (amends 0015, 0016 and 0017), amended by [0022](0022-playtest-before-the-pull-request.md) |
| [0022](0022-playtest-before-the-pull-request.md) | Playtest before the pull request: qa1 and qa2 | Accepted (amends 0016 and 0021), amended by [0024](0024-no-github-labels.md) |
| [0023](0023-texture-generator.md) | Texture generator: Retro Diffusion Fast, cleaned to the project palette | Accepted (refines 0007) |
| [0024](0024-no-github-labels.md) | No GitHub labels: playtests run on qa1 and qa2 alone | Accepted (amends 0015 and 0022) |
| [0025](0025-remove-the-nether-and-end.md) | Remove the Nether and End; the Overworld is the starting area | Accepted (resolves the open question of 0012) |
| [0026](0026-platform-minecraft-26-1-2-neoforge.md) | Platform: Minecraft 26.1.2 on NeoForge | Accepted (supersedes 0003) |
| [0027](0027-client-bound-payload-handlers-through-the-client-event.md) | Client-bound payload handlers through NeoForge's client event | Accepted (amends 0014) |
| [0028](0028-dungeon-rooms-generated-or-imported.md) | Dungeon rooms are generated or imported, not built by hand | Accepted (amends 0007, 0009, 0012 and 0014) |
| [0029](0029-independent-claude-review-before-every-merge.md) | Independent Claude review before every merge | Accepted (supersedes 0015) |
| [0030](0030-adrs-bind-agents-not-the-owner.md) | ADRs bind agents, not the owner | Accepted (amends 0001) |

## Open questions (not decided)

- Seasons: explicitly not for now.
