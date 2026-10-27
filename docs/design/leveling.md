# Leveling

> The XP and leveling model for v1 (RLM-192): how much XP a level takes, where XP comes from, how long the
> chapters take, and how much content each realm needs to fill its levels. Written on 2026-10-16 from Matěj's request
> of 2026-10-15 and the report built on it,
> [Realmforge: XP a levelování v1](https://claude.ai/code/artifact/3363de94-ba6c-4fa9-8188-a7dc0878cc24) (Czech; the
> research behind it covers WoW Classic, TBC and WotLK, Mine and Slash and Craft to Exile 2, AutoLeveling, OSRS and
> Path of Exile). The realm layout it fills is [realms.md](realms.md) (RLM-188). C2 (RLM-23) ships the curve, kill
> XP and the caps ([architecture 10.2](../architecture.md#102-leveling)); quests (RLM-189), dungeons (RLM-190) and
> anti-farm (RLM-191) follow. The numbers here equal the shipped data in `content/curves/` and
> `content/kill_xp_multipliers/`; where they differ, the data wins and this document is out of date.

## Goals

Matěj's targets (2026-10-15):

- **Level 60 in about 20–30 hours** for a fast player who knows the game and grinds.
- **Level 60 in about 90–100 hours** for an average player who reads the story, builds, dies, changes builds and
  finishes about 97 % of the quests.
- **Level 1 to 2 in about 20–30 minutes** of normal play, or about 5 minutes of pure farming.
- **Every level takes longer than the one before.**

## The unit: a kill-equivalent

Every number counts in **kill-equivalents (KE)**: the XP of one kill of a normal mob of the player's level,
`45 + 5 × level` (WoW Classic's mob formula), 50 XP at level 1 and 345 at 60. It is the curve `kill_xp`.

## XP to the next level

`XP(L) = KE(L) × (45 + 5L)`, the XP from level L to L + 1, where KE(L) is the number of kills that level takes:

- `KE(1) = 30`: 30 kills, about 5 minutes at about 6 kills a minute;
- `KE(L + 1) = KE(L) × g`, with g = 1.07 below level 10, 1.05 for levels 10–17, 1.035 for 18–29 and 1.03 from 30;
- rounded half up to 10 below 10,000 and to 50 from 10,000.

| Level | XP to the next level | XP in all to reach the next level |
| --- | --- | --- |
| 1 | 1,500 | 1,500 |
| 10 | 5,240 | 31,200 |
| 18 | 11,000 | 96,890 |
| 30 | 24,000 | 305,690 |
| 44 | 49,350 | 814,690 |
| 59 | 98,650 | 1,912,740 |

A player needs 25,960 XP in all for level 10, 85,890 for 18, 281,690 for 30 and 1,912,740 for 60. The curve
`xp_to_next_level` holds one point per level 1–59, and `tools/tests/test_leveling.py` recomputes it from the formula.

- **Calibration:** if playtests measure another farming rate than about 6 kills a minute, only KE(1) moves; the
  growth factors keep the shape.
- **Why the growth never drops below 3 %:** a player's efficiency grows too (better gear, more spells), by up to
  1.5 % a level for a fast player (below). KE growth of at least 3 % a level keeps every level longer than the one
  before even then.

## Time targets

Assumptions to measure in playtests, not promises:

| Player | KE a minute at level 1 | Growth per level | Hours to level 60 |
| --- | --- | --- | --- |
| Average | 1.2 | +0.5 % of the level-1 rate | about 93 |
| Fast | 3.5 | +1.5 % of the level-1 rate | about 25 |

Hours per chapter, from one chapter's cap to the next (chapter 1 from level 1):

| Chapter | Realm | Levels counted | Average | Fast |
| --- | --- | --- | --- | --- |
| 1 | Overworld | 1–10 | 4.9 | 1.6 |
| 2 | Undervault | 10–18 | 6.9 | 2.1 |
| 3 | Sunwastes and Everwinter (twins) | 18–30 | 14.8 | 4.2 |
| 4 | Deepholm | 30–40 | 16.8 | 4.5 |
| 5 | Quagmire | 40–44 | 8.0 | 2.1 |
| 6 | Ashfire | 44–52 | 18.7 | 4.6 |
| 7 | Unmade Isles | 52–60 | 22.9 | 5.5 |

M1 (chapters 1 and 2, levels 1–18) therefore takes about 12 hours on average and 4 for a fast player.

## Kill XP

A kill gives `(45 + 5 × mob level) × rank × level difference × the player's XP Multiplier`:

- **mob level:** the victim's `realmforge:level` (W1);
- **rank:** the victim's kill XP multiplier ([Ranks](#ranks));
- **level difference:** above the player, 5 % more a level, at most 20 % more from 4 levels above (the curve
  `kill_xp_above_player`); below, 90 % at 1 below, then 15 % less a level (75, 60, 45, 30, 15 %) and nothing from 7
  below (`kill_xp_below_player`), so mobs far below give nothing;
- **XP Multiplier:** the player's stat `xp_multiplier`, 100 % by default, the lever for XP potions and buffs.

Only hostile mobs give XP, and XP stops at the cap ([Chapters and caps](#chapters-and-caps)).

### Ranks

| Rank | Multiplier | Examples |
| --- | --- | --- |
| passive | 0 | animals, villagers (no XP at all: only hostile mobs give it) |
| minion | 0.25 | a summoned add, a split slime |
| normal | 1 | a zombie, a skeleton; every hostile mob without an entry |
| tough normal | 1.25 | the Unforged |
| strong | 1.5 | an enderman, a witch |
| elite | 2 | a dungeon's elite pack |
| miniboss | 5 | a dungeon's miniboss, a guard tower's captain |
| dungeon boss | 10 | a dungeon's final boss |
| realm guardian | 20 | a realm's main-path boss, such as the Unbroken |

Our own mobs take their rank from this table when they are made. The vanilla Overworld mobs have the multipliers
RLM-23 set in `content/kill_xp_multipliers/`:

| Multiplier | Mobs |
| --- | --- |
| 10 | warden, elder guardian (Overworld's bosses) |
| 4 | ravager |
| 2.5 | evoker |
| 1.5 | breeze, enderman, vindicator, witch |
| 0.5 | cave spider |
| 0.25 | slime from size 2 (it splits into smaller ones); the smallest slime (size 1) gives nothing |
| 0.1 | endermite, silverfish, vex |

## Quests

RLM-189 implements quest XP.

- **A standard quest** gives `(10 + 0.25 × level) × (45 + 5 × level)` XP at the quest's level: 510 at level 1 (10.25
  KE) and 8,630 at 60 (25 KE).
- **By type:** small 0.5, standard 1, a main questline step 1.5, a dungeon quest 2, a chapter finale 4.
- **Done above its level:** full XP up to 4 levels above the quest's level, then 75, 50 and 25 % for 5, 6 and 7
  above, and 10 % from 8 above.
- Quest XP goes through `/realmforge xp add`, so it is multiplied by the XP Multiplier and stops at the cap like any
  other gain.

## Dungeons

RLM-190 implements dungeon XP.

- **Kills** inside count by rank at the dungeon's level, like any kill.
- **A completion bonus:** 20 KE on a player's first clear of a dungeon and 5 KE on a repeat, at the dungeon's level
  and reduced for a player above it like quest XP.
- **A 15–20 minute run** gives about 60–75 KE in all.

## Exploration and the bestiary

- **First discovery of a site:** 2 KE for a small site, 3 for a medium one, 5 for a landmark (the site sizes of the
  [realm design](realms.md#realms)).
- **First kill of each mob type:** 2 KE.

## Where an average player's XP comes from

| Source | Share |
| --- | --- |
| Quests | about 45 % |
| Open-world kills | about 30 % |
| Dungeons | about 20 % |
| Exploration and the bestiary | about 5 % |

## Chapters and caps

Each chapter's cap is its last level, and the chapter's main questline raises it (Matěj, 2026-10-15; the layout is
[realms.md](realms.md#chapters-and-levels)):

| Chapter | Cap while current | Cap once completed |
| --- | --- | --- |
| 1, Overworld | 10 | 18 |
| 2, Undervault | 18 | 30 |
| 3, Sunwastes and Everwinter | 30 | 40 |
| 4, Deepholm | 40 | 44 |
| 5, Quagmire | 44 | 52 |
| 6, Ashfire | 52 | 60 |
| 7, Unmade Isles | 60 | - |

**XP stops at the cap,** as in WotLK (Matěj's `qa2 B` on RLM-23, 2026-10-15): a player holds at most the XP of the
cap's level, a gain past it is lost, and completing a chapter raises the cap but brings no level by itself. The next
chapter's levels therefore come from its own content, and the report's lever "a cap on banked XP" is settled.

## Content budget per realm

The minimum each realm needs so an average player levels through its band, in XP, standard quests, open-world kills
and dungeon runs (from the report; Q2 and later chapters set the exact counts against RLM-189's quest XP):

| Realm | XP | Standard quests | Kills | Dungeon runs |
| --- | --- | --- | --- | --- |
| Overworld | about 18,000 | about 10 | about 80 | about 1 |
| Undervault | about 58,000 | about 18 | about 160 | about 2 |
| Sunwastes | about 61,000 | about 13 | about 130 | 1–2 |
| Everwinter | about 104,000 | about 17 | about 180 | about 2 |
| Deepholm | about 279,000 | about 33 | about 400 | about 4 |
| Quagmire | about 159,000 | about 15 | about 200 | about 2 |
| Ashfire | about 428,000 | about 33 | about 470 | about 5 |
| Unmade Isles | about 807,000 | about 48 | about 760 | about 8 |

In all: about 190 standard quests (about 200–250 real ones, since small quests count half), about 2,400 kills and
about 26 dungeon runs. Skyhearth, the hub and the centre of the game, hands out a share of the quests of every realm
from chapter 3 on, Sunwastes to Unmade Isles (Matěj, 2026-10-17, RLM-245, since Skyhearth opens at about level 15). The
report assumed about a fifth of the quests of realms 4–7; the share per realm is set with Q2 and later chapters.

## Anti-farm

RLM-191 implements these:

- **Spawner mobs give no character XP.**
- **A player must deal at least 50 % of a mob's maximum health** to get its XP.

## Not used now

- **Rested XP.**
- **XP loss on death** (research PR-7).
- **Diminishing returns per area.**

The `xp_multiplier` stat stays the lever for XP potions and buffs.
