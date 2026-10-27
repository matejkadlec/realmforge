# What players break inside instances

> Which blocks of a dungeon instance players may break or place, how, and what happens to rooms that only work while
> their blocks stay (RLM-360). Written on 2026-10-27 from Matěj's answers of 2026-10-24 and 2026-10-25 (RLM-360's
> comments, his second playtest of the batch RLM-362, RLM-363, RLM-364) and the rooms on `master` at `e366b80`.
> **Decided:** the allowlist, its blocks, no drops, gold bars for the throne room and exposed spawners are Matěj's;
> the scope, the mechanism and the speed are this note's recommendations, which RLM-412 left to it
> ([ADR 0055](../decisions/0055-players-break-an-allowlist-of-blocks-in-instances.md)). **Open:** lighting TNT
> (RLM-470). Implemented by RLM-412 (spawners, cobwebs, vines, the two exposed spawners) and RLM-469 (iron bars and
> the gold bars); the egg's spawner comes with RLM-361.

## At a glance

| Question | Answer | Who decided |
| --- | --- | --- |
| Allowlist or denylist | **Allowlist:** only listed blocks break; placing stays forbidden | Matěj, 2026-10-24 |
| The list | Spawners, cobwebs, vines; iron bars once gold bars seal the throne room (RLM-469); smooth stone only by TNT, through a room's `explosion_breaks` (RLM-257) | Matěj, 2026-10-24 |
| Drops | Nothing a player breaks inside an instance drops an item, shears included | Matěj (cobwebs and vines), this note (the rest) |
| Decoration behind bars | Gold bars, a block of ours that never breaks there (RLM-469) | Matěj, 2026-10-24 |
| Hidden spawners | Exposed in their rooms, not more breakable blocks around them | Matěj, 2026-10-24 |
| Trial spawners | Not in the Prisoner's Kingdom; RLM-359 keeps them for later dungeon types | Matěj, 2026-10-25 |
| Scope of the list | One block tag for every instance, `realmforge:breakable_in_instances` | this note |
| How Adventure mode breaks them | A mixin on `Player#blockActionRestricted`, by hand or with any item | this note |
| How fast | Vanilla's speed for the block and the item in hand | this note |
| Flint and steel on TNT | Open: recommended refused (RLM-470) | Matěj |

## 1. Where we start

Survival players play inside instances in Adventure mode (W4c, [architecture 15.5](../architecture.md#155-instances)),
so every block of a room stays as the room was built: no breaking, no placing, no buckets, no tool edits. Only
explosions break blocks, and only those a room's sidecar lists in `explosion_breaks`: the smooth stone walls that
Stonevaults' combination lock blows open (RLM-257).

Matěj's playtests of 2026-10-22 and 2026-10-24 found where that is too strict:

- **Spawners never stop.** A spawner spawns for as long as a player stands near it. In
  `husk_temple` it sat sealed inside a sandstone monument, in `skeleton_trap` under a slab, so a player could not even
  see it.
- **Bars lock rooms.** `husk_temple` had iron bars in its doorways (removed by RLM-364), and other rooms keep loot
  behind bars.
- **Cobwebs and vines** that the dressing adds to corridors (ADR 0036) slow a player down for good.

## 2. Allowlist or denylist

| | Allowlist: only listed blocks break | Denylist: listed blocks never break |
| --- | --- | --- |
| A room's look | Kept; nothing changes but what the list names | Anything not listed can be dug: walls, floors, the redstone of a lock |
| Puzzles and traps | Work as built | A player digs past a lock, or into a trap's redstone |
| New rooms and blocks | Safe by default | Every new block is breakable until someone lists it |
| Cost | Rooms must not need anything else broken (a spawner sealed in stone gets exposed) | A long list per block family, which grows with every source |

**Matěj chose the allowlist** (2026-10-24): "So the exceptions are only iron bars, cobwebs, vines, and smooth stone
blocks (via TNT)", with spawners breakable too ("spawners have to be breakable"). Placing stays forbidden.

### The stone vault pool per option

The Prisoner's Kingdom's pool (`realmforge:stone_vault`) has 32 rooms. Counted from their templates after this
note's two spawner edits (section 7):

| Blocks | Count | Rooms | Allowlist | Denylist (Matěj's candidates: stone bricks, smooth stone, TNT) |
| --- | ---: | ---: | --- | --- |
| Stone bricks (plain, cracked, mossy, chiseled, slabs, stairs, walls) | 13,012 | 32 | protected | protected |
| Cobblestone and mossy cobblestone (and their slabs and stairs) | 1,005 | 22 | protected | **breakable** |
| Wood: planks, logs, stairs, slabs, fences, doors, trapdoors | 1,705 | 15 | protected | **breakable** |
| Smooth stone | 99 | 4 | only by TNT (`explosion_breaks`) | protected |
| Spawners | 6 | 5 | **breakable** | **breakable** |
| Cobwebs | 63 | 25 | **breakable** | **breakable** |
| Vines | 149 | 25 | **breakable** | **breakable** |
| Iron bars | 41 | 3 | **breakable** (RLM-469) | **breakable** |
| Sandstone (cut, chiseled, stairs, slabs) | 118 | 1 (`husk_temple`) | protected | **breakable** |
| Quartz (smooth, stairs, slabs) | 43 | 1 (`treasure/egg`) | protected | **breakable** |
| Glass, tinted glass, stained glass | 251 | 6 | protected | **breakable** |
| Redstone and mechanisms (wire, torches, repeaters, comparators, pistons, levers, buttons, plates, tripwires, dispensers, iron doors) | 289 | 9 | protected | **breakable**: locks and traps could be taken apart |
| TNT | 13 | 2 | protected | protected |
| Chests and barrels | 90 | 24 | protected (opened, never broken) | **breakable** |
| Everything else (wool, beds, bookshelves, lanterns, sand, gold blocks, pots, fluids, ...) | 723 | 22 | protected | **breakable** |

With the allowlist a room plays as its builder made it. With the denylist, every room with a lock or a trap would
need its redstone listed, and every wooden room could be dug through.

## 3. Rooms meant to be seen, not entered

The throne room (`treasure/mooshroom_king`) shows a throne and 6 chests behind 9 iron bars. Breakable iron bars would
make it a loot room. Matěj's two ideas:

1. **Another, unbreakable bar for decoration.** Chosen: "a custom gold bars, crafted from gold … a regular block of
   ours", which stay unbreakable in dungeons. RLM-469 adds the block and swaps the throne room's bars.
2. Reachable decoration rooms at the dungeon's end. Not needed once gold bars exist.

The pool's other iron bars: 18 in `pillager_dungeon/rooms/jail_2` (its cells, with chests, behind iron doors that
levers now open, RLM-364) and 14 in `pillager_dungeon/rooms/temple`. Breaking them opens nothing a lever does not
already open, so RLM-469 only has to check them again before iron bars join the list. Rooms meant only as a sight
behind glass (`pillager_dungeon/rooms/jail`'s garden behind tinted glass) stay closed: glass is not on the list.

Until RLM-469, iron bars stay unbreakable: breakable bars before the gold bars exist would open the throne room.

## 4. Scope: one list for every instance

| Option | For | Against |
| --- | --- | --- |
| **One block tag for every instance** (`realmforge:breakable_in_instances`) | Matěj's list is one list for the whole game; a tag is data a pack or later version can change, and the client reads it too (tags are synced), so it predicts the break | A dungeon type that wants other blocks needs the next option |
| A list per dungeon type | Each dungeon could have its own rules | No dungeon type needs one yet; more data per type |
| A list per room (`player_breaks`, like `explosion_breaks`) | Room-precise | Repeats the same three blocks in every sidecar; a room that forgets one hides a spawner |

**Recommended and built: one block tag**, in the core mod's resources
(`data/realmforge/tags/block/breakable_in_instances.json`), next to the instance dimension it belongs to. A room's
`explosion_breaks` stays per room, since only rooms with TNT walls need it. A later dungeon type that needs other
blocks can add a per-type list then (an ADR amending 0055).

## 5. How Adventure mode breaks them, and how fast

Adventure mode refuses a break in `Player#blockActionRestricted`, on the server (`ServerPlayerGameMode`) and in the
client's prediction (`MultiPlayerGameMode`), before NeoForge's `BreakBlockEvent`. The event cannot undo it: its
documentation says un-cancelling it "will not permit the block to be broken" in that case.

| Option | For | Against |
| --- | --- | --- |
| A `can_break` component on tools | Vanilla's own Adventure mode rule | Never works with an empty hand (`blockActionRestricted` returns true for one); every tool a player brings needs the component, so we would rewrite items as they enter |
| Survival mode with our own guard instead of Adventure mode | No mixin | The client shows cracks on every block a player hits and then refuses the break; W4c's game-mode rules change |
| **A mixin on `Player#blockActionRestricted`** | One small injection on a common class, so the client predicts the same; any item or an empty hand | A mixin (architecture 19.3: only where no event or API exists, which is the case) |

**Recommended and built: the mixin** (`mixin/PlayerBlockActionMixin`): inside the instance dimension, for a player in
Adventure mode, a block of the tag is not restricted. `InstanceRules`' break guard, which stops what Adventure mode
lets through (items with `can_break`), lets the same blocks pass. Adventure mode still hides the block outline on
every block, as it does today; the cracks show while breaking.

**Speed: vanilla's**, from the block's hardness and the item in hand. Players carry tools in Minecraft, and the
right tool matters:

| Block | By hand, or with a wand or any other item | With the right tool |
| --- | --- | --- |
| Spawner | 25 s | wooden pickaxe 3.75 s, stone 1.9 s, iron 1.25 s, diamond 0.95 s |
| Cobweb | 20 s | sword or shears 0.4 s |
| Vine | 0.3 s | shears 0.15 s |

A caster with only a wand in hand breaks a cobweb in 20 s and a spawner in 25 s, so carrying a sword or a pickaxe
pays off. The playtest decides whether that is fine; a faster break in instances is one listener
(`PlayerEvent.BreakSpeed`) if not.

## 6. Drops

Matěj: cobwebs and vines drop nothing, "to prevent string farming", "even when broken with shears". This note makes it
the rule for every block broken inside an instance: a dungeon's blocks are not loot. A spawner drops no item anyway,
and iron bars will drop none either (RLM-469). A spawner broken with a pickaxe still drops vanilla's experience, as
anywhere. TNT walls are the one way blocks break by explosion; their drops are vanilla's.

## 7. Hidden spawners

Matěj chose to expose them in their rooms rather than make more blocks breakable. The pool's spawners on `master`:

| Room | Spawner | On `master` | Now |
| --- | --- | --- | --- |
| `rooms/skeleton_trap` | (6, 1, 6) | under a 3×3 of stone brick slabs, stone bricks around it | the slab above it is a cobweb (Matěj's choice), which shows the spawner and breaks |
| `rooms/husk_temple` | (6, 1, 6) | sealed inside the sandstone monument | the monument is opened on its east side above its bottom layer (Matěj's img4, described in RLM-360): the spawner keeps its sandstone at floor level and shows from above and from the east |
| `rooms/drowned_junction` | (6, 1, 6) | open above, water around | unchanged |
| `rooms/library` | (1, 1, 11), (10, 1, 1) | open | unchanged |
| `rooms/tight_maze` | (6, 2, 6) | open on three sides | unchanged |
| `treasure/egg` | none yet | | RLM-361 puts a cave spider spawner inside the quartz egg, reachable the same way |

The pytest `test_the_stone_vault_pools_spawners_can_be_seen_and_broken` keeps it so: every spawner of the pool
touches a cell a player reaches from the room's doorways (`rooms/dress.py`'s `reach`), air or a cobweb.

## 8. Lighting TNT (open, RLM-470)

Vanilla's `TntBlock#useItemOn` primes TNT before Adventure mode's build check, so flint and steel or a fire charge
lights any TNT in a room. In `combination_lock` that skips the lever puzzle: the TNT blows the smooth stone wall open
without the right levers. Other rooms' TNT (`tnt_plate_trap`) would go off where the player chooses.

| Option | For | Against |
| --- | --- | --- |
| **Refuse it** (recommended) | A lock's or trap's TNT fires only through its redstone; puzzles stay puzzles | A player who brought flint and steel cannot use it there, as with every other fire |
| Allow it, as today | Some players like clever shortcuts | Every TNT lock is optional |
| Allow it where a room lists it | Precise | No room wants it yet |

This is Matěj's call: **RLM-470** asks it. If he refuses it, the guard is an `InstanceRules` listener on both sides
(the item use on a TNT block) with a GameTest.

## Open questions for Matěj

1. **Lighting TNT:** refuse flint and steel and fire charges on TNT inside instances, as recommended? (RLM-470)
2. **The husk temple's opening:** made from the description of his img4, which the cloud session did not have. Does
   the opening match it? (the playtest of RLM-412)
3. **Breaking speed:** vanilla's, so a wand breaks a cobweb in 20 s and a spawner in 25 s. Fine, or faster inside
   instances? (the playtest of RLM-412)
