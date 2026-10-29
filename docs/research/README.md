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

## Third-party mods by milestone (2026-10-18 to 2026-10-20, RLM-264, RLM-221)

- **[third-party-mods.md](third-party-mods.md)**: Matěj's picks from about 130 mods with NeoForge 26.1.2 builds
  (researched on the Modrinth API, 2026-10-18), by milestone with their tickets, the watchlist and the rejected
  mods; where other mods' structures go (each only in its realm); the Overworld chapter's questline, raids and final
  boss; and his answers of 2026-10-19. It answers RLM-221's Overworld structure research. Decided in
  [ADR 0044](../decisions/0044-third-party-mods-by-milestone.md).

## Artifacts and Immersive Armors catalogues (2026-10-29, RLM-312, RLM-313)

- **[artifacts.md](artifacts.md)**: every one of Artifacts 15.1.3's 49 items (NeoForge 26.1.2 jar) with its effect,
  slot, what it fights in our stats and combat, a proposed minimum realm and level and early quest reward candidates;
  where it places items (chests, brushing, mob equipment, campsites and the Mimic) and the config and data that switch
  each source off; its five Curios slot types against our four slots and Matěj's curio rule. Proposed: six utility
  items as unique quest rewards in our slots, everything else and every world source off, with an ADR amending the
  curio rule; or Artifacts left out. Waiting for Matěj's pick.
- **[immersive-armors.md](immersive-armors.md)**: Immersive Armors 1.8.2's ten sets and 40 pieces (NeoForge 26.1.2
  jar) with armor, toughness, knockback resistance, speed, durability, enchantability, their hard-coded effects and
  where they act in our damage pipeline, recipes (two with Nether materials), chest loot and mob armor, and the levers
  (config, recipe conditions, `ModifyDefaultComponentsEvent`). Proposed: nine sets, Robe off, Heavy's toughness cut,
  Warrior and Divine as unique quest rewards, its chest loot and mob armor off. Waiting for Matěj's pick.

## Coins, merchant trades and trade display (2026-10-19, RLM-222)

- **[coins-and-trades.md](coins-and-trades.md)**: coin, trade-editing and villager mods on NeoForge 26.1.2 (Modrinth
  API and their sources), vanilla 26.1's data-driven trades read in the game's sources (`villager_trade`,
  `trade_set`, how professions and the wandering trader pick them, the limits for coin prices), and trade display
  in JEI. Decided by Matěj (2026-10-19): our own copper, silver and gold coins at 100:1 with a converter of our own,
  trades generated from our YAML as vanilla data, and JEI / REI Trades in the pack
  ([ADR 0040](../decisions/0040-coins-and-data-driven-merchant-trades.md)).

## Chococraft and mount tiers (2026-10-12, RLM-101)

- **[chococraft.md](chococraft.md)**: whether the Chococraft mod (0.11.12 for NeoForge 1.21.1) can carry Matěj's
  mount tiers, read from its jar and licence files: its ten hard-coded colours, flight, taming and breeding, what
  config and data can change, the tier table against it, ADR 0004 and architecture principle 8, and a proposal (our
  own mounts) with the questions for Matěj's decision. Decided (RLM-115): our own mounts, tiers 1 to 3 in M4, the
  rest in M6, with notes for the mount tasks.
