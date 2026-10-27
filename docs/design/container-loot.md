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
- **About 3 containers per spawner** (RLM-365, Matěj: "~6x loot container per 2 spawners", for "any room that can
  generate chest, and for any future dungeons as well"): chance tables decide how many of a room's chests and barrels
  a run keeps ([below](#how-many-containers-a-room-keeps)).
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

## How many containers a room keeps

Matěj's answer to the second open question (RLM-365, from his playtest of 2026-10-22, layout seed 995: 31 containers
against 3 spawners in five minutes, three `tiny_cap`s, two `parrot`s and the throne room among them). His target:
about **6 containers per 2 spawners** in a run, for every dungeon.

- **Candidates:** a room's single chests and its barrels. A trapped chest (part of a trap) and the halves of a double
  chest always stay.
- **Chance tables:** for each kind, the chance in percent of each count. A dungeon type sets them by room role and
  under `default` for the rest (`content/dungeons/<id>.yaml`, `containers`); a room's sidecar may set its own.
- **The draw:** when a run is planned, each room draws a count per kind from its table, at most as many as it holds,
  and keeps that many candidates at random; the draw comes from the layout's seed, so a layout always looks the same.
- **What a dropped container leaves:** the block around it, as Matěj asked ("the surrounding floor or wall block"):
  the full block most of its neighbours are where it sits in a floor or a wall (four or more full neighbours) or
  carries another container, so no hole and no floating chest; air where it stands in the room.

The test dungeon's tables (`content/dungeons/test_dungeon.yaml`):

| Rooms | Chests | Barrels |
| --- | --- | --- |
| Treasure rooms (Matěj's table) | 2 at 25 %, 3 at 40 %, 4 at 25 %, 5 at 10 % | none at 40 %, 1 at 40 %, 2 at 20 % |
| Every other room (`default`) | 1 at 60 %, 2 at 30 %, 3 at 10 % | none at 20 %, 1 at 40 %, 2 at 30 %, 3 at 10 % |
| `treasure/tiny_cap` (Matěj's own table) | 1 at 75 %, 2 at 20 %, 3 at 5 % | none |
| `pillager_dungeon/rooms/jail` (Matěj, 2026-10-25) | always its 1 | always its 1 |
| `treasure/mooshroom_king`, the throne room | always all 6: a sight behind bars, not loot (gold bars seal it, RLM-469) | none |

- **The default** never takes a room's only chest, which is often what a lock or a trap guards, and cuts the rooms
  that hold many: the library's 2 chests and 3 barrels, `storage_2`'s 15 barrels, `big_head`'s 4 chests and 2 barrels.
- **The egg room** holds 2 chests since RLM-361, so the treasure table always keeps both.
- **Over 300 seeds** (`DungeonContainerGameTests`, the test dungeon's pool on 2026-10-27), a run held about
  41 containers to about 13.7 spawners, 2.99 per spawner, down from about 83 to 13 before
  the tables (6.4 per spawner); the throne room's display chests are not counted.
- **Tuning:** the numbers can vary (Matěj). A playtest that finds too much or too little loot changes the tables in
  YAML; the GameTest keeps a run within 0.5 of 3 per spawner.

## Open questions

For Matěj; each has a proposal so the work can go on.

1. **Budget ranges for the treasure table.** Proposal: about 750 to 3,000 points, in the same ratio to the normal
   table as Matěj's kill-equivalents (5 to 20 against 2 to 10).
2. ~~**How many containers a room may hold.**~~ Answered by Matěj's chance tables (RLM-365,
   [above](#how-many-containers-a-room-keeps)). Set-piece containers (RLM-282) count towards a room's candidates
   when the builder places them, or get their own table then.
