# Container loot

> What every chest and barrel in a dungeon holds. Written on 2026-10-19 from Matěj's decisions of that day, made
> while he reviewed the [set-piece design](set-pieces.md) (RLM-175); [ADR 0041](../decisions/0041-dungeon-containers-hold-loot-through-lootr.md)
> records them. Numbers are his; the mechanics are the proposal for the loot ticket (RLM-289), which tunes them.

## At a glance

- **No container is ever empty.** Every chest and barrel a player can open in a dungeon holds loot: W6's chest
  markers, the chests and barrels the imported rooms brought, the import dressing's barrels and the containers in
  set pieces. In Matěj's words, players expect loot, and an empty chest or barrel is disappointing.
- **Lootr** (MIT, NeoForge 26.1.2 builds) joins the pack in **M1**: every player gets their own contents of the same
  container. Loot cannot be balanced without it, or it would have to be balanced again once it comes.
- **A container's loot is worth a few mob kills,** at the very least the time it took to open and loot it:

  | Container | Minimum | Medium | Maximum (a high roll) |
  | --- | --- | --- | --- |
  | A normal chest or barrel | ~2 mob kills | ~4 mob kills | ~10 mob kills |
  | A chest in a treasure room | ~5 mob kills | ~10 mob kills | ~20 mob kills |

- **Loot points:** each container draws a random budget (for a normal one about 300 to 1,500 points), and every item
  it rolls costs points until the budget is spent. A valuable item leaves little for the rest: a unique curio worth
  1,200 points can never come twice from one container, and a container whose budget is 1,200 may hold that curio
  alone.
- **Relative to the player:** what drops depends on the world tier (Apotheosis's, [Apotheosis design](apotheosis.md))
  of the player who opens it, and a better tier gives better and more loot. So does more **luck** (vanilla's
  `minecraft:luck` attribute).

## What a container can hold

Coins ([ADR 0040](../decisions/0040-coins-and-data-driven-merchant-trades.md)), food, ingots, gem ores,
Apotheosis gems, armor, weapons and curios with affixes, enchanted books, and enchanted items: everything picked by
the world tier. Some curios, and maybe weapons, are **loot-only**: never craftable, they come from these tables.

**Treasure rooms** (W6: a treasure room's five chest spots, [ADR 0036](../decisions/0036-room-lights-and-decorations-form-patterns.md))
get their own, better table: more points, higher-quality gems for the tier, the tier's better ores as blocks (for
example diamonds rather than iron at Haven, the first tier), better enchantments (levels III and IV), all still
relative to the player's level and world tier.

## How it works (proposal)

1. **The budget** is a loot entry of our own (Java, a `LootPoolEntryContainer` type): it draws a budget from the
   container's range, scaled up by the world tier and by luck, then rolls items from weighted lists, each item with
   its cost in points, until no affordable item is left. Vanilla loot tables roll each pool on its own and cannot
   share a budget between rolls.
2. **Calibration:** a mob kill's loot (its coins, drops and XP worth) gets a value in points per world tier, and a
   container's budget is set to the table above in those kill-equivalents. The tables are generated from our YAML,
   and a pytest simulation checks the averages per tier.
3. **Per player:** Lootr gives each player their own roll of the container's loot table, with that player's tier and
   luck. Our dungeon builder places Lootr's chests and barrels with the loot table set; whether Lootr also takes
   over a vanilla container placed from a template with a loot table is checked when Lootr goes in (RLM-288).
4. **Where the containers come from:** W6's chest markers; the imported rooms' 100 chests and their barrels, which
   the import turns into loot spots of the normal table (RLM-290); the import dressing's barrels; set-piece
   containers ([set-piece design](set-pieces.md#gameplay-rules)).
5. **Progression items** (keys, chain items) never depend on a container roll: they drop at 100 % (LE-1).

## Open questions

For Matěj; each has a proposal so the work can go on.

1. **Budget ranges for the treasure table.** Proposal: about 750 to 3,000 points, in the same ratio to the normal
   table as Matěj's kill-equivalents (5 to 20 against 2 to 10).
2. **How many containers a room may hold.** Proposal: the import dressing's barrels and set-piece containers count
   towards a per-room cap (two in a hallway, four in a combat room), so a room full of barrels does not outpay its
   mobs; treasure rooms keep their five chests.
