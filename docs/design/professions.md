# Professions

> The professions of Realmforge (M5, RLM-10): which ones the game starts with, what each gives, how they level, and
> what Matěj still decides. Written on 2026-11-03 from Matěj's decisions of that day (RLM-578, its description and
> comments). They replace the professions list of the roadmap's
> [M5 outline](../roadmap.md#m5-professions-and-enchanting) ("mining, alchemy, smithing, jewelcrafting for curios,
> tailoring, cooking"). **Deferred to M5** (Matěj, 2026-11-03): the XP curve, the recipes per level and their bonuses
> get their own research when M5 starts; this document records the direction, and its numbers are starting points,
> not decisions. Research to build on: [reference games §6](../research/reference-games.md#6-professions-pf-one-tier-per-chapter-valves-against-grind-no-xp-sinks)
> and the [CTE2 professions notes](../research/notes/cte2-professions-food-potions.md).

## At a glance

| Question | Answer | Who decided |
| --- | --- | --- |
| The starter set | Reforging, Alchemy, Enchanting, Jewelcrafting | Matěj, 2026-11-03 |
| How many at once | All of them, for the start | Matěj, 2026-11-03 |
| How a profession levels | By doing its activity; every recipe gives profession XP, weaker recipes less | Matěj, 2026-11-03 |
| The maximum level | The same for every profession; 50, 60 or 100 is open | Matěj, 2026-11-03 (open: the number) |
| Repairs | Stay on vanilla XP levels; reforging only lowers the cost | Matěj, 2026-11-03 |
| Potions | For everyone to buy; alchemy crafts stronger versions | Matěj, 2026-11-03 (RLM-574) |
| Cooking | Postponed | Matěj, 2026-11-03 |
| Gathering professions | Maybe much later or never; their materials stay obtainable without them | Matěj, 2026-11-03 |

## The starter set

| Profession | What it gives |
| --- | --- |
| **Reforging** | cheaper repairs, cheaper affix rerolls at Apotheosis's Reforging Table, more yield from the Salvaging Table |
| **Alchemy** | stronger and cheaper potions, much stronger at the maximum alchemy level; maybe unique potions |
| **Enchanting** | better and cheaper Apothic Enchanting: higher chances of higher-level enchantments, bonus Eterna, Quanta and Arcana; maybe unique enchantments |
| **Jewelcrafting** | better and cheaper Apotheosis gems (the Gem Cutting Table); unique gems, where the strongest gems could live; curios too, once curios are in the game |

- **Reforging and jewelcrafting split by station:** repairs, rerolls and salvaging are reforging's; gems are only
  jewelcrafting's.
- **Repairs stay on vanilla XP levels.** That closes the repair part of the M5 outline's "final role of vanilla XP"
  (roadmap decision D3); reforging only lowers the cost.
- **Potions are for everyone** (RLM-574): anyone can buy the restoration and long-buff potions, and alchemy crafts
  stronger versions of them.
- **Every profession builds on Apotheosis's stations and Apothic Enchanting**
  ([ADR 0038](../decisions/0038-apotheosis-carries-gear-rarity-affixes-gems-and-enchanting.md)), except alchemy's
  brewing.

## Leveling

- **All professions can be leveled at once,** for the start: the Minecraft RPG pack style, not WoW's two. Dungeons
  are solo, so no other player can supply what a missing profession makes.
- **A profession levels by doing its activity:** brewing levels alchemy, cutting gems levels jewelcrafting,
  enchanting levels enchanting, and repairing, rerolling and salvaging level reforging.
- **Every recipe gives profession XP, weaker recipes less.** The XP a level needs grows steeply, so one expensive
  recipe pays better than a hundred cheap ones, while cheap recipes still give a little. Matěj's example: a Lesser
  Health Potion gives a fixed amount while each level needs about twice the last one's XP, so the Lesser potions per
  level go 5, 10, 20, 40, 80, and from about the fourth level brewing them stops paying for XP.
- **Lesser potions are not trivially cheap** when a player crafts them. A player who levels a profession alongside
  their character levels it at about the same pace; one who starts it at level 60 levels it fast through the early
  recipes.
- **Every profession has the same maximum level** (the number is open question 1).

### Starting points for the XP curve

Matěj's variants of 2026-11-03, kept for M5's research (RLM-578, comments). Shared rules: level 1 → 2 needs 100 XP,
each further level needs a factor more, each recipe gives a fixed XP, and a recipe one tier up takes about as many
crafts per level at its tier's start as the tier below did at its own start (Lesser Health at level 1 → 2 about as
Health at level 10 → 11).

| | A: ×1.5 per level, max 30 | B: ×1.2 per level, max 60 | C: a factor that falls with level, max 60 |
| --- | --- | --- | --- |
| Potion tiers | 1–10 Lesser, 10–20 normal, 20–30 Greater; Superior at 30 as the max-level reward | 1–20 Lesser, 20–40 normal, 40–60 Greater; Superior at 60 | a new potion every 3 levels |
| Recipe XP | Lesser 25, Health about 1,000, Greater about 55,000 | Lesser 25, Health about 800, Greater about 30,000 | Lesser 15 (level 1), then about 25 at level 3, 55 at level 6, and so on |
| XP to the last level | 8.5 M (29 → 30) | 3.9 M (59 → 60) | about 1.0 M (59 → 60) |
| XP to the maximum | about 25.6 M | about 23.5 M | about 13.5 M |
| Crafts per level | Lesser: 4 at level 1, 20 at level 5, 154 at level 10 | Lesser: 4 at level 1, 21 at level 10, 128 at level 20 | about 7 for the newest potion at every level |

- **C's factor per level:** ×1.3 for levels 1–10, ×1.25 for 10–20, ×1.2 for 20–30, ×1.15 for 30–40, ×1.1 for 40–50
  and ×1.05 for 50–60. It is Matěj's own third proposal: a constant factor grows very slowly at first and very fast
  later, which he dislikes. The requirement grows about ×13 over the first ten levels and only about ×1.7 over the
  last ten.
- **B at ×1.5 does not work:** level 59 → 60 would need about 1.6 × 10¹² XP, and ×1.25 still gives a 42 M last level
  and about 209 M in all.
- **Exact doubling** (his first example) explodes over 50 to 100 levels: level 20 would need about 52 million XP.
- **Crafts per level:** the research target is about 5 to 10 crafts
  ([reference games](../research/reference-games.md#6-professions-pf-one-tier-per-chapter-valves-against-grind-no-xp-sinks),
  Adapt 8), set by the ratio of a recipe's XP to the level's requirement.
- **Alternatives** for M5's research: WoW's recipe colours (orange, yellow, green, grey, where grey gives nothing), or
  CTE2's level formula.

## Postponed and later

- **Cooking is postponed:** it needs a cooking mod, Farmer's Delight has no 26.1.2 build, and there is no good
  alternative.
- **Later ideas, not in the starter set:** blacksmithing (melee armor and weapons), tailoring (caster armor and
  wands) and leatherworking (ranged armor, bows and crossbows), each maybe with unique gear.
- **Gathering professions** (mining, skinning, herbalism): maybe much later, or never. Every profession material stays
  obtainable in the world without them: ordinary mining, skinning animals with a knife, picking flowers. This sets
  aside the research's "mining as the M1 profession" (reference games, Adopt 5).

## Open questions for Matěj

1. **The maximum profession level:** 50, 60 or 100, decided by how the potions work out.
2. **The XP curve:** one of the [starting points](#starting-points-for-the-xp-curve), WoW's recipe colours, CTE2's
   formula, or another, with each recipe's fixed XP rising with its tier. M5's research proposes it.
3. **Profession level ≤ character level** (the research's Adopt 1), or no cap.
4. **Unique potions, enchantments and gems:** which, if any.
5. **Fishing and archaeology:** the M5 outline lets Aquaculture 2 and Better Archeology seed fishing and archaeology
   professions (RLM-328, [ADR 0044](../decisions/0044-third-party-mods-by-milestone.md)), and the starter set has
   neither. Proposal: they stay activities without a profession of their own for now, like the gathering professions,
   and join the later ideas; the outline's line stays until Matěj decides.
