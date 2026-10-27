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
| [container-loot.md](container-loot.md) | What every chest and barrel in a dungeon holds: Lootr, loot worth a few mob kills, loot points, the world tier and luck, the treasure-room table, and the chance tables that decide how many containers a room keeps (about 3 per spawner) | RLM-175, RLM-365 |
| [guide.md](guide.md) | The in-game guide: a searchable screen about the realms, dungeons, bosses, their ores and loot, the character and the systems, unlocked by chapter; approved for M4 | RLM-102, RLM-116 |
| [instance-block-rules.md](instance-block-rules.md) | What players break inside dungeon instances: the allowlist (spawners, cobwebs, vines, iron bars with gold bars for rooms meant to be seen), no drops, one block tag, Adventure mode's break and its speed, the exposed spawners, and lighting TNT (open) | RLM-360, RLM-412, RLM-469, RLM-470 |
| [leveling.md](leveling.md) | The XP and leveling model for v1: kill-equivalents, the XP curve, kill XP and ranks, quest, dungeon and exploration XP, time targets per chapter, the caps, the content budget per realm, anti-farm | RLM-192 |
| [realm-portal.md](realm-portal.md) | The realm portal: frame, sealstone, signets crafted around their final boss's shard and the quest shop that sells a lost one, per-player unlocks through quests, Nether-like linking with scaled coordinates, respawn in Overworld, edge cases | RLM-41, RLM-128, RLM-395 |
| [realms.md](realms.md) | The realms, their names, level bands, order and contents, chapters and caps, tool tiers and gated ores, dungeon completion, puzzles and traps, Skyhearth the hub, Deepholm the water realm, the Void realm that always comes last | RLM-40, RLM-95, RLM-188, RLM-245, RLM-265 |
| [set-pieces.md](set-pieces.md) | Set pieces: small hand-built props and corners that the dungeon fits into generated and imported rooms; where the fitting plugs in, the fit rule, a mask report over the current rooms, materials by realm, cheap variety, the workshop and the gameplay rules | RLM-175 |
| [spell-aiming.md](spell-aiming.md) | How a player aims spells: bolts at the crosshair and ground spells with clamp cast (decided, ADR 0052), and the auto-aim of targeted spells with its highlight, for Matěj's review | RLM-344, RLM-345, RLM-346 |
| [spell-visuals.md](spell-visuals.md) | How spells look and sound, from a kit of reusable parts: looks (material, particles, sounds per element), GeckoLib models, trajectories, forming during a cast (cube by cube, a particle storm or WoW's glowing hands), first person, impacts, area styles, the particle budget, the M1 spells' mapping and an ADR draft, for Matěj's review | RLM-438, RLM-463 |
