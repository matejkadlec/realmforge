# Design

Game design documents: what the player meets and how the parts of the game fit together, written from Matěj's
decisions. They sit between the [vision](../vision.md) (the goals) and the [roadmap](../roadmap.md) (the plan); how
things are built is in the [architecture](../architecture.md), the reasoning behind decisions in the
[ADRs](../decisions/README.md), the facts behind them in [research](../research/README.md) and the story around them
in the [lore](../lore/README.md). A design document
names its Jira ticket, lists its open questions for Matěj, and changes when he decides something new.

| Document | What it covers | Ticket |
| --- | --- | --- |
| [apotheosis.md](apotheosis.md) | Apotheosis in Realmforge: the player's world tier and the realms that unlock it, the defaults that stay (rarity weights, purities, chest and mob loot, tier augments), mob levels and loot, the mob difficulty layers, reforging's materials loop, costs and its recipes without the Nether and End | RLM-199, RLM-204 |
| [guide.md](guide.md) | The in-game guide: a searchable screen about the realms, dungeons, bosses, their ores and loot, the character and the systems, unlocked by chapter; approved for M4 | RLM-102, RLM-116 |
| [leveling.md](leveling.md) | The XP and leveling model for v1: kill-equivalents, the XP curve, kill XP and ranks, quest, dungeon and exploration XP, time targets per chapter, the caps, the content budget per realm, anti-farm | RLM-192 |
| [realm-portal.md](realm-portal.md) | The realm portal: frame, realm selector, sigils, per-player unlocks through quests, Nether-like linking with scaled coordinates, respawn in Overworld, edge cases | RLM-41, RLM-128 |
| [realms.md](realms.md) | The realms, their names, level bands, order and contents, chapters and caps, realm tool tiers, dungeon completion, puzzles and traps, Olympus the hub, the Void realm that always comes last | RLM-40, RLM-95, RLM-188 |
| [set-pieces.md](set-pieces.md) | Set pieces: small hand-built props and corners that the dungeon fits into generated and imported rooms; where the fitting plugs in, the fit rule, a mask report over the current rooms, materials by realm, cheap variety, the workshop and the gameplay rules | RLM-175 |
