# Research

Topic research that informs Realmforge's design. Decisions themselves live in [ADRs](../decisions/README.md) and in
Matěj's decision tickets in Jira; these files keep the facts, numbers, comparisons and sources behind them. The phase 1
research (platform, tooling, licensing) is in [../research-notes.md](../research-notes.md).

## Reference games (2026-10-12, RLM-94)

What Realmforge should adopt, adapt and avoid from three reference games, read against Matěj's realm, dungeon, spell and
consumable decisions of 11–12 October 2026.

- **[reference-games.md](reference-games.md)**: the synthesis. One section per design area, each with numbered
  Adopt / Adapt / Avoid recommendations and their supporting numbers. Tickets and PRs cite them by ID:

  | Prefix | Area |
  | --- | --- |
  | RE | Realms |
  | DU | Dungeons and instances |
  | BM | Bosses and mobs |
  | PR | Level and chapter progression |
  | SP | Spells and skills |
  | PF | Professions |
  | FP | Food and potions |
  | LE | Loot and economy |
  | WF | World fullness |
  | TR | Travel and QoL |

  `RE-9` is item 9 under Realms (Adopt, Adapt and Avoid are numbered continuously). The report ends with open
  questions for Matěj.
- **[notes/](notes/)**: the nine detailed research notes the synthesis is built from. Read them when a ticket needs the
  full numbers, per-dimension profiles or the evidence behind a recommendation.

  | Note | Covers |
  | --- | --- |
  | [mns-character-combat-gear.md](notes/mns-character-combat-gear.md) | Mine and Slash as configured in Craft to Exile 2: leveling and XP curve, stats, talents, classes, spells, support gems, auras, gear, crafting, mob scaling |
  | [cte2-dungeons-dimensions-world.md](notes/cte2-dungeons-dimensions-world.md) | CTE2 dungeon maps (room kits), wave arenas, Gateways, dimensions and level bands, worldgen spacing, travel QoL |
  | [cte2-progression-quests-reception.md](notes/cte2-progression-quests-reception.md) | CTE2 acts and level bands, quest structure, onboarding, QoL, death rules, player reception, Craft to Exile 1 |
  | [cte2-professions-food-potions.md](notes/cte2-professions-food-potions.md) | CTE2 professions, recipes, profession shop, food buff slots and magnitudes, food diversity, potions |
  | [aoa-systems-progression.md](notes/aoa-systems-progression.md) | Advent of Ascension: portal frame and realmstones, unlock tree, tiers, skills, bosses, economy, gear, emptiness by the numbers, version history |
  | [aoa-dimensions-a-to-g.md](notes/aoa-dimensions-a-to-g.md) | AoA dimensions Abyss to Greckon, incl. Deeplands and Crystevia against the M1 stone realm |
  | [aoa-dimensions-h-to-z.md](notes/aoa-dimensions-h-to-z.md) | AoA dimensions Haven to Vox Ponds, the Overworld and Nether additions, Nowhere |
  | [hytale-base-game.md](notes/hytale-base-game.md) | Hytale Early Access: zones and biomes, factions, dungeons, instances and portals, wave events, what makes the world feel alive |
  | [hytale-dungeon-mods.md](notes/hytale-dungeon-mods.md) | Hytale mods Major Dungeons and Endgame & QoL (and peers): key chains, boss design, wave trials, QoL |

### Sources and caveats

- **Craft to Exile 2** was read from a local 1.1.3 instance (datapack, quests, configs, the Mine and Slash 6.3.14 and
  companion jars), the Mine and Slash source and the pack's public changelogs up to 2.1.4 (10 Sep 2026). Notes mark
  later changes as "2.x".
- **Advent of Ascension** was read from its view-only 1.12.2 source (3.3.6), compared with the 1.21 branch and the
  official wiki. Structure densities are computed from default spawn chances and are upper bounds.
- **Hytale** facts are as of 12 Oct 2026 (Early Access; Chapter 1 still on the pre-release branch); each fact is
  labelled Early Access or announced only.
- Player reception is a thin sample: Reddit, CurseForge comments and GitHub issue pages were unreachable from the
  research environment.
- Local citations in the notes name their source (for example "CTE2 1.1.3 instance: config/…"); those files are not
  in this repository.

### Using these files

- Mechanics are not copyrightable, but names, textures, models, sounds and code of the reference games are never
  reused (see [ADR 0009](../decisions/0009-licensing.md) and `CLAUDE.md`).
- A recommendation is input, not a decision: anything that changes a decision needs Matěj's decision ticket or an ADR,
  and the roadmap changes only as he decided.
- Later topic research (for example `realm-portals.md`, RLM-39) goes into this folder too.
