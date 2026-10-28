# What players break inside instances

> Which blocks of a dungeon instance players may break or place, how, and what happens to rooms that only work while
> their blocks stay (RLM-360). Written on 2026-10-27 from Matěj's answers of 2026-10-24 and 2026-10-25 (RLM-360's
> comments, his second playtest of the batch RLM-362, RLM-363, RLM-364) and the rooms on `master` at `e366b80`;
> revised on 2026-10-28 from his playtest of RLM-360, RLM-412, RLM-361 and RLM-365 (iron bars stay, the spawners show
> on a side away from the way in). **Decided:** the allowlist, its blocks, no drops and exposed spawners are Matěj's;
> the scope, the mechanism and the speed are this note's recommendations, which RLM-412 left to it and his playtest
> passed ([ADR 0056](../decisions/0056-players-break-an-allowlist-of-blocks-in-instances.md)). **Open:** lighting TNT
> (RLM-470). Implemented by RLM-412 (spawners, cobwebs, vines, the exposed spawners, room openings); the egg's spawner
> comes with RLM-361.

## At a glance

| Question | Answer | Who decided |
| --- | --- | --- |
| Allowlist or denylist | **Allowlist:** only listed blocks break; placing stays forbidden | Matěj, 2026-10-24 |
| The list | Spawners, cobwebs, vines; smooth stone only by TNT, through a room's `explosion_breaks` (RLM-257). Not iron bars | Matěj, 2026-10-24; iron bars left out 2026-10-28 |
| Drops | Nothing a player breaks inside an instance drops an item, shears included | Matěj (cobwebs and vines), this note (the rest) |
| Decoration behind bars | Stays behind its iron bars, which never break there; no gold bars (RLM-469 rejected) | Matěj, 2026-10-28 |
| Hidden spawners | Exposed in their rooms, not more breakable blocks around them: on one side, at random the left, the right or the opposite one, never the side a run enters from | Matěj, 2026-10-24 and 2026-10-28 |
| Trial spawners | Not in the Prisoner's Kingdom; RLM-359 keeps them for later dungeon types | Matěj, 2026-10-25 |
| Scope of the list | One block tag for every instance, `realmforge:breakable_in_instances` | this note |
| How Adventure mode breaks them | A mixin on `Player#blockActionRestricted`, by hand or with any item | this note |
| How fast | Vanilla's speed for the block and the item in hand | this note; passed in Matěj's playtest of 2026-10-28 |
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

The Prisoner's Kingdom's pool (`realmforge:stone_vault`) has 32 rooms. Counted from their templates as of RLM-412's
and RLM-361's edits (section 7), before a run opens a side (1 sandstone or 3 quartz blocks):

| Blocks | Count | Rooms | Allowlist | Denylist (Matěj's candidates: stone bricks, smooth stone, TNT) |
| --- | ---: | ---: | --- | --- |
| Stone bricks (plain, cracked, mossy, chiseled, slabs, stairs, walls) | 13,012 | 32 | protected | protected |
| Cobblestone and mossy cobblestone (and their slabs and stairs) | 1,005 | 22 | protected | **breakable** |
| Wood: planks, logs, stairs, slabs, fences, doors, trapdoors | 1,705 | 15 | protected | **breakable** |
| Smooth stone | 99 | 4 | only by TNT (`explosion_breaks`) | protected |
| Spawners | 7 | 6 | **breakable** | **breakable** |
| Cobwebs | 63 | 25 | **breakable** | **breakable** |
| Vines | 149 | 25 | **breakable** | **breakable** |
| Iron bars | 41 | 3 | protected (Matěj, 2026-10-28) | **breakable** |
| Sandstone (cut, chiseled, stairs, slabs) | 123 | 1 (`husk_temple`) | protected | **breakable** |
| Quartz (smooth, stairs, slabs) | 45 | 1 (`treasure/egg`) | protected | **breakable** |
| Glass, tinted glass, stained glass | 251 | 6 | protected | **breakable** |
| Redstone and mechanisms (wire, torches, repeaters, comparators, pistons, levers, buttons, plates, tripwires, dispensers, iron doors) | 289 | 9 | protected | **breakable**: locks and traps could be taken apart |
| TNT | 13 | 2 | protected | protected |
| Chests and barrels | 90 | 24 | protected (opened, never broken) | **breakable** |
| Everything else (wool, beds, bookshelves, lanterns, sand, gold blocks, pots, fluids, ...) | 723 | 22 | protected | **breakable** |

With the allowlist a room plays as its builder made it. With the denylist, every room with a lock or a trap would
need its redstone listed, and every wooden room could be dug through.

## 3. Rooms meant to be seen, not entered

The throne room (`treasure/mooshroom_king`) shows a throne and 6 chests behind 9 iron bars. Breakable iron bars would
make it a loot room. Matěj's two ideas were another, unbreakable bar for decoration (gold bars, a block of ours) and
reachable decoration rooms at the dungeon's end.

**Iron bars stay unbreakable** (Matěj, 2026-10-28): he wanted them breakable only because they blocked the way in
other rooms, and RLM-364 took them out of the doorways they barred instead. So the throne room keeps its iron bars,
no gold bars are made, and RLM-469, which would have made both, is rejected: "iron bars can stay in front of the
throne, no need for separate gold bars".

The pool's other iron bars: 18 in `pillager_dungeon/rooms/jail_2` (its cells, with chests, behind iron doors that
levers now open, RLM-364) and 14 in `pillager_dungeon/rooms/temple`; neither bars the way through. Rooms meant only as
a sight behind glass (`pillager_dungeon/rooms/jail`'s garden behind tinted glass) stay closed too: glass is not on the
list.

## 4. Scope: one list for every instance

| Option | For | Against |
| --- | --- | --- |
| **One block tag for every instance** (`realmforge:breakable_in_instances`) | Matěj's list is one list for the whole game; a tag is data a pack or later version can change, and the client reads it too (tags are synced), so it predicts the break | A dungeon type that wants other blocks needs the next option |
| A list per dungeon type | Each dungeon could have its own rules | No dungeon type needs one yet; more data per type |
| A list per room (`player_breaks`, like `explosion_breaks`) | Room-precise | Repeats the same three blocks in every sidecar; a room that forgets one hides a spawner |

**Recommended and built: one block tag**, in the core mod's resources
(`data/realmforge/tags/block/breakable_in_instances.json`), next to the instance dimension it belongs to. A room's
`explosion_breaks` stays per room, since only rooms with TNT walls need it. A later dungeon type that needs other
blocks can add a per-type list then (an ADR amending 0056).

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
the rule for every block broken inside an instance: a dungeon's blocks are not loot. A spawner drops no item anyway.
A spawner broken with a pickaxe still drops vanilla's experience, as anywhere. TNT walls are the one way blocks break by explosion; their drops are vanilla's.

## 7. Hidden spawners

Matěj chose to expose them in their rooms rather than make more blocks breakable, and, in his playtest of 2026-10-28,
on a side rather than from above: "the exposed side should not be the one the player enters the room from initially,
should be right side, left side or the opposite side (random ... 33.3% each)". The pool's spawners on `master`:

| Room | Spawner | On `master` | Now |
| --- | --- | --- | --- |
| `rooms/skeleton_trap` | (6, 1, 6) | under a 3×3 of stone brick slabs, stone bricks around it | the slab above it is a cobweb (Matěj's choice), which shows the spawner and breaks |
| `rooms/husk_temple` | (6, 1, 6) | sealed inside the sandstone monument | a run opens one side: the chiseled sandstone in front of the spawner at floor level goes, the rest of the monument stays (Matěj's img1) |
| `rooms/drowned_junction` | (6, 1, 6) | open above, water around | unchanged |
| `rooms/library` | (1, 1, 11), (10, 1, 1) | open | unchanged |
| `rooms/tight_maze` | (6, 2, 6) | open on three sides | unchanged |
| `treasure/egg` | none yet | | RLM-361 puts a cave spider spawner into the quartz egg's upper hollow; a run opens one side of it: the quartz beside the spawner, and one corner's quartz block and the slab on it |

**Openings** (`openings` in a room's sidecar, [content README](../../content/README.md)): per side of the template, the
blocks a run turns to air there. The planner knows the connector each room is entered through (the one it hangs off
the room before it by), and the run opens one listed side at random that is not that connector's side, so a player
walks around to the spawner or meets it across the room. The template keeps the spawner closed in; the egg is a dead
end, so its spawner never faces its doorway, and the husk temple, which a run passes through west to east or east to
west, opens north, south or its far side.

The pytest `test_the_stone_vault_pools_spawners_can_be_seen_and_broken` keeps it so: every spawner of the pool
touches a cell a player reaches from the room's doorways (`rooms/dress.py`'s `reach`), air or a cobweb, whichever side
a run opens.

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
2. ~~**The husk temple's opening.**~~ Answered by his img1 (2026-10-28): the chiseled sandstone in front of the
   spawner, on a side a run opens (section 7).
3. ~~**Breaking speed.**~~ Vanilla's, passed in his playtest of 2026-10-28.
