# Container loot

> What every chest and barrel in a dungeon holds. Written on 2026-10-19 from Matěj's decisions of that day, made
> while he reviewed the [set-piece design](set-pieces.md) (RLM-175); [ADR 0041](../decisions/0041-dungeon-containers-hold-loot-through-lootr.md)
> records them. Numbers are his; the mechanics were built by the loot ticket (RLM-289, 2026-11-10), whose own numbers
> are the agent's and wait for his playtest ([open questions](#open-questions)). How it is built:
> [architecture 13](../architecture.md#13-items-gear-and-loot).

## At a glance

- **No container is ever empty.** Every chest and barrel a player can open in a dungeon holds loot: W6's chest
  markers, the chests and barrels the imported rooms brought, the import dressing's barrels and the containers in
  set pieces. In Matěj's words, players expect loot, and an empty chest or barrel is disappointing.
- **Lootr** (MIT, NeoForge 26.1.2 builds) joins the pack in **M2**, with the dungeons (M1 until 2026-10-29,
  [ADR 0058](../decisions/0058-player-systems-before-world-content.md)): every player gets their own contents of the
  same container. Loot cannot be balanced without it, or it would have to be balanced again once it comes.
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

## How it works

Built by RLM-289 from `content/container_loot/` (`normal.yaml`, `treasure.yaml`).

1. **The budget** is a loot entry of our own (`realmforge:budget`): it draws a budget from the container's range,
   scaled up by the world tier and by luck, then buys options by weight, each with its cost in points, until none is
   affordable. Vanilla loot tables roll each pool on its own and cannot share a budget between rolls. An option with
   a limit of 1 is unique: it never comes twice from one container.
2. **Calibration:** 150 points are one mob kill. The measure is the coins of a kill at the opener's world tier (the
   curve `coin_drop` at the tier's level): a coins option costs its kills times 150, and every other option is
   priced against that by hand. A pytest simulation rolls both tables at every tier and checks them against the
   table above.
3. **Per player:** Lootr gives each player their own roll of the container's loot table, with that player's tier and
   luck. Our dungeon builder places Lootr's chests and barrels with the loot table set (`loot/LootContainers`).
   Checked when Lootr went in (RLM-288): Lootr also takes over a vanilla container with a loot table by itself, but
   only once the 25 chunks around it are loaded, and a player who opens it earlier gets vanilla's one shared roll,
   so the builder does not rely on it ([architecture 15.5](../architecture.md#155-instances)).
4. **Where the containers come from:** W6's chest markers; the imported rooms' chests and barrels and the import
   dressing's barrels (42 chests and 44 barrels in the test dungeon's pool today, before the chance tables); the
   generated corridors' lone barrel (RLM-410); set-piece containers
   ([set-piece design](set-pieces.md#gameplay-rules)). Since RLM-290 a dungeon type names the table of its rooms'
   containers by role (`container_loot` in `content/dungeons/<id>.yaml`: the treasure table for the treasure
   rooms, the normal one as the default, set pieces included), and the builder makes every chest, trapped chest and
   barrel it places a loot container with that table. The templates themselves carry no table, so another dungeon
   type can give the same rooms other loot, and validation refuses a type that would leave a container empty.
5. **Progression items** (keys, chain items) never depend on a container roll: they drop at 100 % (LE-1).

## The tables

| | Normal | Treasure |
| --- | --- | --- |
| Budget (least, medium, most) | 300, 600, 1,500 | 750, 1,500, 3,000 |
| In kills, at Haven | 2, 4, 10 | 5, 10, 20 |
| Coins | 1 kill's (weight 30), 3 kills' (8) | 2 kills' (20), 5 kills' (10) |
| Food | bread, cooked beef, golden carrots, a golden apple | golden carrots, golden apples, an enchanted golden apple (unique, 1,500) |
| Materials at Haven | coal, copper, iron, gold | gold, diamonds, diamond ore |
| Materials from Frontier on | iron, gold, a diamond, a Ruby, Sapphire or Topaz | diamonds, deepslate diamond ore, Rubies, Sapphires, Topazes |
| Apotheosis | a gem (300), an affix item (450) | a gem, the purest of three rolls (450), an affix item (450) |
| Enchanted | a book with 5 to 15 levels, iron gear, later diamond gear | a book with 20 to 30 levels (levels III and IV), diamond gear |

- **Half of all containers lie below the medium:** a roll's budget is spread evenly between the least and the medium
  for one half and between the medium and the most for the other, so a high roll is rare. Simulated at Haven, a
  normal container is worth 1.8 to 10 kills, 3.8 in the middle and 4.8 on average; a treasure chest 5 to 20, 10 in
  the middle and 10.8 on average.
- **A later tier gives more and better:** the budget grows by a tenth per tier (110 % at Frontier, 140 % at
  Pinnacle), a kill's coins grow with the tier's level, and the materials are the tier's.

  | Tier | Opens with | A kill is counted at | One kill's coins |
  | --- | --- | --- | --- |
  | Haven | Overworld | level 5 | 1 to 3 copper |
  | Frontier | Undervault | level 12 | 5 to 14 copper |
  | Ascent | Everwinter | level 28 | 1 to 3 silver |
  | Summit | Quagmire | level 44 | 18 to 55 silver |
  | Pinnacle | Unmade Isles | level 56 | 1.3 to 4 gold |

- **Luck:** each point adds 5 % to the budget. Apotheosis itself raises a player's luck with the tier (a fifth more
  at Frontier, twice as much at Pinnacle), so luck from gear and potions counts for more later.
- **How often Apotheosis's things come** (simulated at Haven): a gem in 18 % of normal containers and an affix item
  in 11 %; in a treasure chest each in 35 %. Apotheosis's own injection would put an affix item into 30 % and a gem
  into 20 % of every chest; with about 41 containers in a run that is too much, and it does not reach our tables.
- **Outside `chests/`** (left to Claude by Matěj, 2026-10-21, RLM-289): the tables are
  `realmforge:containers/<id>`, so Apotheosis's chest injection, which matches `chests/`, skips them, and its gems
  and affix items are drawn inside the budget. The simulation therefore sees everything a container holds.
- **No progression item** comes from a roll: validation refuses a realm's signet and a dungeon's key in a table
  (research LE-1).
- **Not in the tables yet:** curios, loot-only or not, because the pack has none (RLM-657); and materials of
  their own for Ascent, Summit and Pinnacle, and gem ores as blocks, because those realms' content does not exist
  (RLM-658). Until then the three later tiers use Frontier's materials.
- **No rarity band yet:** the [gear design](gear.md#the-realm-bands) gives our loot a rarity band per realm, the
  realm the container stands in. The two tables are not a realm's, so an affix item in a container rolls at the
  opener's tier weights alone, as Apotheosis's own chest loot does: at Frontier about one in nine is Rare or Epic,
  above Undervault's band of Common to Uncommon. RLM-567 gives our loot its bands; for containers that needs a
  table per realm (or the band on the dungeon type) and a list of rarities on `realmforge:tier_affix_item`.
- **Trying it:** `/qa loot chest normal` and `/qa loot barrel treasure` place a loot container in front of the
  player, as the dungeon builder does; `/apoth set_world_tier @s frontier` sets the world tier. In a run, every
  chest and barrel of the rooms carries its table since RLM-290; W6's chest markers follow.

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
| `treasure/mooshroom_king`, the throne room | always all 6: a sight behind its iron bars, which never break, not loot | none |

- **The default** never takes a room's only chest, which is often what a lock or a trap guards, and cuts the rooms
  that hold many: the library's 2 chests and 3 barrels, `storage_2`'s 15 barrels, `big_head`'s 4 chests and 2 barrels.
- **The egg room** holds 2 chests since RLM-361, so the treasure table always keeps both.
- **Over 300 seeds** (`DungeonContainerGameTests`, the test dungeon's pool on 2026-10-27), a run held about
  41 containers to about 13.7 spawners, 2.99 per spawner, down from about 83 to 13 before
  the tables (6.4 per spawner); the throne room's display chests are not counted.
- **Tuning:** the numbers can vary (Matěj). A playtest that finds too much or too little loot changes the tables in
  YAML; the GameTest keeps a run within 0.5 of 3 per spawner.

## Open questions

The two questions of the first version are answered. The tables' own numbers are the agent's (RLM-289, built and
merged unplayed by Matěj's decision of 2026-11-10 for the `flow1 cloud` marathon, quoted in RLM-659: "Carry a
playtest batch through its handoff, with every automated check. Then don't wait for Matěj: continue as if he answered
`qa1`"); RLM-659 is the playtest he still owes them:

- **The measure:** 150 points a kill, counted in the coins a mob of the tier's level drops. At Haven that makes a
  pick of coins 1 to 3 copper, next to 2 to 4 iron ingots for the same points.
- **The tiers' levels** (5, 12, 28, 44, 56) and a tenth more budget per tier; 5 % more per point of luck.
- **What the tables hold** and at what cost and weight ([above](#the-tables)), and how often a gem and an affix
  item come.
- **A treasure room's better gem** as the purest of three rolls, and its books at 20 to 30 levels.
- **Lootr's defaults:** its own chest textures and the particles on a container a player has not opened yet stay
  on, a Lootr container breaks only while sneaking, and Lootr's advancement tab stays
  ([pack README](../../pack/README.md#configuration)).
- **Affix gear without the realm's band** until RLM-567 ([above](#the-tables)).

1. **Budget ranges for the treasure table.** **Decided 2026-10-21 (left to Claude by Matěj, RLM-289): about 750 to
   3,000 points,** in the same ratio to the normal table as Matěj's kill-equivalents (5 to 20 against 2 to 10), tuned
   after a playtest.
2. ~~**How many containers a room may hold.**~~ Answered by Matěj's chance tables (RLM-365,
   [above](#how-many-containers-a-room-keeps)). Set-piece containers (RLM-282) count towards a room's candidates
   when the builder places them, or get their own table then.
