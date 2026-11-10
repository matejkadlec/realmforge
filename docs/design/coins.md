# Coins

> The four coins, how a player converts them, and what mobs drop (RLM-284, RLM-286). Decisions:
> [ADR 0040](../decisions/0040-coins-and-data-driven-merchant-trades.md) and
> [ADR 0072](../decisions/0072-a-fourth-coin-the-converter-by-use-and-coin-drops-by-level-and-rank.md); the mods
> considered: [coins and trades](../research/coins-and-trades.md). How it is built:
> [architecture 13](../architecture.md#13-items-gear-and-loot). Merchants' trades (RLM-285), container loot
> ([container-loot.md](container-loot.md)), the scroll shop (RLM-354) and buy quests (RLM-424) build on it.

## The coins

| Coin | Worth | In copper |
| --- | --- | --- |
| Copper Coin | the smallest coin | 1 |
| Silver Coin | 100 Copper Coins | 100 |
| Gold Coin | 100 Silver Coins | 10 000 |
| Platinum Coin | 100 Gold Coins | 1 000 000 |

- WoW's 100:1, with platinum above gold (Matěj, 2026-10-21): scroll prices double with every purchase and stop at
  99 platinum.
- Coins are plain items that stack to 99. There is no wallet and no balance: what a player carries is what they have.
- Money is written as an amount of copper and paid in the largest coins: 123 456 copper is 12 gold, 34 silver and
  56 copper. Texts shorten the coins to `g`, `s`, `c` and `p` only in documents; the game names them in full.

## Converting

Using a coin converts it, anywhere, without a block or a screen:

- **Use** (right click) with a coin in hand **merges**: every full hundred of that coin in the whole inventory becomes
  one of the next larger coin. What is left below a hundred stays. With fewer than a hundred, the action bar says
  "You need 100 Copper Coins to make 1 Silver Coin."
- **Sneak and use** **breaks**: one coin of the held stack becomes 100 of the next smaller coin.
- Copper does not break and platinum does not merge.
- Change that does not fit the inventory lies at the player's feet.
- A conversion pauses that coin for half a second, so holding the button converts step by step.
- The tooltip names both: "Use: Merge every 100 Copper Coins into 1 Silver Coin" and
  "Left Shift + Use: Break one into 100 Copper Coins" (the player's own sneak key).

Merging is the plain use because it is the common one (drops arrive in small coins) and the harmless one: it frees
slots and can be undone, while a break fills two slots.

## What mobs drop

A hostile mob a player killed drops coins next to its own loot (Matěj, 2026-10-21/22):

`base(L) = e^(-0.2109 + 0.2113 L - 0.000458 L²)` copper at mob level `L`

| Mob level | Base | A normal mob drops | Growth to the next level |
| --- | --- | --- | --- |
| 1 | 1c | 1c to 1c | 23 % |
| 5 | 2c | 1c to 3c | 23 % |
| 10 | 6c | 3c to 10c | 22 % |
| 18 | 31c | 16c to 47c | 21 % |
| 20 | 46c | 23c to 69c | 21 % |
| 30 | 3s 4c | 1s 52c to 4s 55c | 20 % |
| 40 | 18s 23c | 9s 12c to 27s 35c | 19 % |
| 50 | 99s 88c | 49s 94c to 1g 49s 81c | 18 % |
| 60 | 4g 99s 26c | 2g 49s 63c to 7g 48s 89c | 17 % |

- **The drop** is uniformly 50 to 150 % of the base, rounded, at least 1 copper, paid in the largest coins.
- **Times the mob's rank**, the same number as its kill XP multiplier ([leveling](leveling.md)): normal 1, tough
  normal 1.25, strong 1.5, elite 2, miniboss 5, dungeon boss 10, realm guardian 20. The swarm ranks below 1 scale it
  down as they scale kill XP (a cave spider 0.5, a silverfish 0.1, still at least 1 copper), and a mob of rank 0, the
  smallest slime, drops nothing.
- **Neither Luck nor Looting changes it.** The amount comes from our code, not from a loot table.
- **Only a player's kill counts**, as for vanilla's experience orbs: a hit of theirs within the last 5 seconds, and
  the gamerule `mob_drops` on. A mob that dies in a farm without a player's hit leaves no coins.
- Prices never scale with the player (ADR 0040), so later realms make early prices feel cheap, as in WoW.

### Check against prices

At about 6 kills a minute of pure farming (the XP model's pace), with a mean drop equal to the base:

| Price | At level | Kills | Minutes |
| --- | --- | --- | --- |
| Scroll of Oblivion, 5 silver | 10 | 78 | 13 |
| Scroll of Respecialization, 25 silver | 10 | 391 | 65 |
| Scroll of Rebirth, 1 gold | 18 | 319 | 53 |
| Scroll of Rebirth, 1 gold | 20 | 217 | 36 |

Matěj finds that right (2026-10-22): Oblivion fairly affordable early, Rebirth about an hour of farming at level 20.

## Open questions for Matěj

Each is the agent's choice from RLM-284 and RLM-286's batch, built that way and merged unplayed. That is Matěj's
decision of 2026-11-10 for the `flow1 cloud` marathon that built it, a waiver of
[ADR 0022](../decisions/0022-playtest-before-the-pull-request.md)'s playtest before the pull request for that run,
given in its session and quoted in RLM-638: "Carry a playtest batch through its handoff, with every automated check.
Then don't wait for Matěj: continue as if he answered `qa1`". RLM-638 is the deferred playtest he still owes it:

1. **The converter's form:** using a coin (merge) and sneaking while using it (break), instead of a pouch item or a
   block. Is merging the right plain use, and should a use merge every full hundred at once or one hundred at a time?
2. **Who gets coins:** only a player's kill, the swarm ranks scale the drop down, and the smallest slime drops
   nothing. Babies drop as adults.
3. **Flavor lines** on the four coins, and the clink (vanilla's chain sound) on a conversion until our own sound
   exists.
4. **The numbers** after a playtest: the curve, the 50 to 150 % spread, the half-second pause.
