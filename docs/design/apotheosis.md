# Apotheosis in Realmforge

> How Apotheosis's progression fits our realms and mob levels (RLM-204), and how its reforging works without the
> Nether and End (RLM-199). Written on 2026-10-17 from Matěj's decisions on both tickets (2026-10-15 to 2026-10-17).
> Matěj adopted Apotheosis, Apothic Attributes and Apothic Enchanting on 2026-10-15 (RLM-201); that ticket holds
> the research this document builds on, read in the 26.1 sources (Apotheosis 9.1.0 `f2e7686`). RLM-205 brings the
> mods into the pack with the ADR that amends [ADR 0004](../decisions/0004-build-rpg-systems-in-house.md), RLM-232
> carries the affix set, RLM-198 the rest of the Apotheosis data, RLM-240 the mob levels and RLM-241 the merchants
> that sell Nether and End materials. Numbers are Apotheosis 9.1.0's defaults unless a line says otherwise.

## At a glance

- **The world tier belongs to the player** and applies in every dimension, as in Apotheosis. It rises on the first
  visit to the realm that unlocks the next tier, and a player always plays at their highest tier.
- **Apotheosis's defaults stay**: rarity weights, gem purities, chest and mob loot, tier augments, elites and
  invaders. Only `max_eterna` is off. They get tuned after playtests.
- **Mob levels follow the player** (RLM-240), so lower realms stay worth a visit, and loot needs no gating by mob
  level.
- **Reforging is Apotheosis's own loop**: salvaging affix items gives each rarity's material and gems give gem
  dust. No realm has reforging materials of its own.
- **Apotheosis's recipes stay at their defaults.** The Nether and End materials they need come from two villager
  merchants (RLM-241) and the summonable Wither.

## World tiers

### Who has a tier

Apotheosis has five tiers, fixed in Java: Haven, Frontier, Ascent, Summit and Pinnacle. Each player has one, and
loot, mob buffs and stations read the tier of the player who loots, of the nearest player, or of the player at the
station (RLM-201, section 5).

**Decided (Matěj, 2026-10-16 late evening):** the tier is the player's in every dimension, and the realms unlock
it. That replaces two earlier models of the same day, "the tier follows the realm" and "instances one tier above
their realm". Matěj does not want "finish a realm and never come back": players return to lower realms for ores,
mob and boss drops and recipe materials, as in FTB Architect's Exodus, and Apotheosis assumes a Pinnacle player
meets Pinnacle mobs.

| Realm | Tier |
| --- | --- |
| Overworld | Haven |
| Undervault | unlocks Frontier |
| Sunwastes | no change |
| Everwinter | unlocks Ascent |
| Prestoria | no change |
| Olympus | no change (no hostile mobs) |
| Quagmire | unlocks Summit |
| Ashfire | no change |
| Unmade Isles | unlocks Pinnacle |

### Rules

- **The tier rises on the first visit** to the realm that unlocks it. Our mod sets it (`WorldTier.setTier`), so
  Apotheosis's own unlock advancements (five slots of rarer gear, plus the Wither and the Ender Dragon) play no
  part; without the End, Pinnacle could not be reached through them anyway.
- **Twins:** Everwinter, the harder twin, unlocks Ascent and Sunwastes changes nothing, whichever twin a player
  visits first (Matěj's corrected order of 2026-10-16).
- **Always the highest tier:** "Enable Manual World Tier Changes" is off, so the Ctrl+T screen never lowers it.
- **First join:** our mod sets Haven on a player's first join. Until a player first activates a tier, affix items
  show as "Unidentified" and hide their stats; `setTier` awards the stat that ends that.
- **Stations use the same tier:** reforging, augmenting, gem cutting and our enchant reroll (RLM-207) roll at the
  player's tier wherever the station stands (RLM-232).

### What the tier changes

Everything below is Apotheosis data and stays at its defaults until a playtest says otherwise (Matěj, 2026-10-15:
"Apotheosis's numbers are tuned, ours are not yet").

**Rarity weights** of affix items (`rarities/*.json`; mythic shows as Legendary):

| Tier | Common | Uncommon | Rare | Epic | Legendary |
| --- | --- | --- | --- | --- | --- |
| Haven | 60 % | 36 % | 4 % | 0 | 0 |
| Frontier | 29 % | 60 % | 10 % | 1 % | 0 |
| Ascent | 10 % | 30 % | 50 % | 10 % | 0 |
| Summit | 0 | 12 % | 29 % | 54 % | 5 % |
| Pinnacle | 0 | 0 | 10 % | 65 % | 25 % |

These answer RLM-204's question on Haven's 4 % Rare: it stays. RLM-198's rarity bands per realm (Common–Uncommon
in Overworld and Undervault up to Epic–Legendary in Ashfire and Unmade Isles) now describe what a player at that
realm's tier mostly finds, not a cap; our own dungeon chests can still name a rarity set
(`apotheosis:random_affix_item`), whose pick the tier then weights (RLM-198).

**Gem purities** (`purity_weights`): Summit gives 58 % normal, 2 % flawless and 1 % perfect gems; Pinnacle 33 %
normal, 47 % flawless and 10 % perfect.

**Pinnacle-only content:** 8 affixes, of which RLM-232 turns off Steadfast, Omniscient, Deathbound and Galvanizing
and opens Giant Slaying and Acidic to lower rarities; Unbound and Super-Massive stay Pinnacle-only. The elite Craig
and the strongest tier augments are Pinnacle's too.

**What a kill gives** (the corrected numbers of 2026-10-16):

- 12 % of natural hostile spawns (not from spawners) wear an affix item, which always drops: at Pinnacle about 3 % of
  kills give a Legendary.
- Gems drop from 4 % of kills by a player (0.5 % otherwise); at Pinnacle about 0.4 % of kills give a perfect gem.

**Chests:** Apotheosis's injection into vanilla chests stays, 35 % affix items and 25 % gems in `minecraft:chests/*`
(30 % and 20 % in other `chests/*`), at the looting player's tier. Matěj (2026-10-16): with so many gems and
purities, one specific perfect gem takes a lot of grinding anyway, and gearing up anywhere, villages included, is
fine.

**Tier augments** stay as Apotheosis ships them, for players and for mobs:

- players: more XP and luck (luck also feeds our own loot tables, which need `quality` / `bonus_rolls` entries to
  use it);
- mobs near a player, at spawn and never removed: armor, toughness, armor pierce and protection pierce, and 10–15 %
  damage reduction in Summit and Pinnacle;
- **only `max_eterna` is off** (RLM-232): Apothic Enchanting's shelves gate enchanting power instead.

## Mob levels and loot

Mob levels follow the player (RLM-240): an open-world mob spawns within 3 levels of the nearest player, never below
its realm's entry level, and a dungeon rises to its highest player. A level 5 zombie never meets a level 60 player,
so:

- **Loot rarity follows only the player's tier**, never the mob's level.
- **No drop gating by mob level** and no loot reduction for low-level dungeons: both were proposed on 2026-10-16
  and dropped.

## Mob difficulty layers

A hostile mob meets up to five layers. Matěj (2026-10-15): keep Apotheosis's mob buffs at first and tune them
later, because many systems make the player stronger and only the levels make mobs stronger.

| Layer | What it adds | Owner |
| --- | --- | --- |
| Our level curves (W1) | health, damage, armor by level | `content/curves/`, RLM-240 |
| Tier augments | armor, toughness, armor and protection pierce, damage reduction | Apotheosis data, kept |
| Affix gear | 12 % of natural spawns wear one affix item | Apotheosis data, kept |
| Elites | 4 elite kinds converted from natural spawns | Apotheosis data, kept; RLM-240 levels them |
| Invaders | bosses in affix gear, 0–3 % of natural spawns by tier, only in listed dimension types | Apotheosis data; our realms to be listed |

- **Stats point at ours:** an augment, affix or gem that grants an `apothic_attributes:` stat is remapped to ours
  (armor pierce included) or switched off (RLM-205). Our stats exist only on players today
  (`StatsFeature#addToPlayers`), so a mob augment with armor pierce needs them on mobs (open question 2).
- **Apotheosis's default elites, invaders and gems** cover Overworld, the Nether and the End. Without the Nether and
  End ([ADR 0025](../decisions/0025-remove-the-nether-and-end.md)) only Overworld's apply; each realm adds its own
  through constraints and the invader spawn rules data map as it ships (open question 3).

## Reforging

### The materials loop

**Decided (Matěj, 2026-10-16):** Apotheosis already solves the materials, so no realm has reforging materials of
its own.

- Salvaging an affix item at the Salvaging Table gives its rarity's material: Mysterious Scrap Metal (Common),
  Timeworn Fabric (Uncommon), Luminous Crystal Shard (Rare), Arcane Sands (Epic), Godforged Pearl (Legendary).
- Gems give gem dust at the Gem Cutting Table.
- Affix items drop in every realm at the player's tier, so the tier is the only link between reforging and the
  realms.
- **Dropped with it:** the realm design's refining materials (Undervault's ores or gems for refining, Sunwastes'
  desert gems for refining) and Overworld's reforging ore (RLM-200).

### Costs

Apotheosis's defaults until a playtest says otherwise. Reforging costs vanilla XP levels, which stay a currency
until M5 (D3).

| Rarity | XP levels | Materials | Sigils of Rebirth | Table |
| --- | --- | --- | --- | --- |
| Common | 2 | 1 | 0 | Simple Reforging Table or Reforging Table |
| Uncommon | 5 | 2 | 1 | Simple Reforging Table or Reforging Table |
| Rare | 15 | 2 | 2 | Simple Reforging Table or Reforging Table |
| Epic | 30 | 2 | 4 | Reforging Table |
| Legendary | 50 | 3 | 5 | Reforging Table |

The Augmenting Table upgrades one affix for 2 sigils and 225 levels, and rerolls one for 1 sigil and 175 levels
(`config/apotheosis/apotheosis.cfg`).

### Recipes without the Nether and End

**Decided (Matěj, 2026-10-17): Apotheosis's recipes stay at their defaults**, with no recipe overrides, so they
match JEI and the wikis. The approved swap of nether bricks for Undervault's stone bricks (2026-10-16) is dropped
with it. The materials ADR 0025 left without a source come from:

| Material | Needed by | Source |
| --- | --- | --- |
| Nether bricks | Reforging Table | the Ancient Builder (RLM-241) |
| Polished blackstone (from blackstone) | Augmenting Table | the Ancient Builder |
| Basalt | Gem Case | the Ancient Builder |
| Nether star | Augmenting Table | the Wither, summoned with skulls from the Ancient Specialist and soul sand from the Ancient Builder; it spawns at level 20 or higher (RLM-240, RLM-241) |
| Blaze rods | Sigil of Withdrawal; blaze powder for the eye of ender | the Ancient Specialist |
| Ender pearls | Sigil of Withdrawal; the eye of ender for the Gem Case's ender chest | the Ancient Specialist; Overworld's endermen |

The other stations (Simple Reforging Table, Salvaging Table, Gem Cutting Table), the other sigils, gem-fused slate
and the upgrade templates need only Overworld materials, Apotheosis's materials and gem dust. Netherite, which the
Reforging Table also takes, is Undervault's tier. The merchants replace the Blaze Rod repeatable quest once planned
for this. Apothic Spawners and Gateways to Eternity, whose recipes need quartz and nether stars, are not in the
pack (RLM-201).

## Dropped

Kept here so a later session does not bring them back unasked:

- the tier following the realm, instances one tier above their realm, and Pinnacle only in Unmade Isles' instances
  (2026-10-16, superseded the same evening);
- drop gating by mob level, limiting chest injection to our own structures, and the loot reduction for low-level
  dungeons;
- turning off the player-side tier augments (2026-10-15; only `max_eterna` goes);
- per-realm reforging materials, the reforging ore and Lucky Gem Ore (RLM-200: area mining would break the gem
  economy);
- recipe overrides for the Apotheosis stations, the Undervault bricks swap included.

Kept from the dropped models: Unmade Isles gets about five long instances with a minimum room count (RLM-164), and
the quest shop sells a Random Perfect Gem, priced so a player affords it in about 30–60 minutes of Ashfire's
earnings and 5–10 minutes of Unmade Isles' (coins: RLM-222).

## Open questions

For Matěj; each has a proposal so the integration can go on.

1. **Apotheosis's progression advancements.** With our mod setting the tier, the tab's goals (the Wither, the
   Ender Dragon, five slots of rarer gear) no longer unlock anything, and the Ender Dragon's cannot be reached.
   Proposal: hide the tab by overriding the advancements with a false condition (RLM-205).
2. **Our stats on mobs, and mob crits.** Remapped augments need our combat stats (armor pierce, armor shred) on
   mobs, and Matěj's idea of monsters that crit with our crit, never Apothic Attributes', needs our crit stats there
   too, with a chance that could rise with the mob's level. Proposal: register our combat stats on hostile mobs with
   RLM-205's remapping; mob crits wait for their own ticket after a playtest of the layers above.
3. **Elites and invaders per realm.** Proposal: each realm's dimension type joins the invader spawn rules when the
   realm ships, with Apotheosis's default invaders first and realm-themed ones as the realm's content grows;
   Undervault joins with RLM-205.
4. **Armor from both the curve and the augments.** Our level curve and the tier augments both add armor, and
   RLM-204 asked that no stat be counted twice. Proposal: keep both as Matěj asked, and retune the armor curve if
   the playtest shows mobs too tanky; the augments' armor stays within vanilla's formula since RLM-223.
