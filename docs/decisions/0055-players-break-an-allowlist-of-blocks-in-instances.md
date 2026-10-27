# 0055. Players break an allowlist of blocks in instances

Date: 2026-10-27

Status: Accepted

## Context

Dungeon instances keep every block as its room was built: survival players play there in Adventure mode, and
`InstanceRules` stops what Adventure mode lets through (W4c, [architecture 15.5](../architecture.md#155-instances)).
Only explosions break blocks, and only those a room lists in `explosion_breaks` (RLM-257). Matěj's playtests of
2026-10-22 and 2026-10-24 showed what that costs: spawners that never stop, two of them sealed where a player cannot
even see them, rooms locked behind iron bars, and cobwebs that slow players down for good.

RLM-360 asked for a proposal ([design note](../design/instance-block-rules.md)): an allowlist or a denylist, rooms
meant only to be seen, the scope of the list, lighting TNT, how Adventure mode breaks the blocks and how fast, and the
hidden spawners. Matěj answered most of it on 2026-10-24 and 2026-10-25 (RLM-360's comments); RLM-412, the
implementation he asked for, left the mechanism and the speed to this ADR.

## Decision

1. **An allowlist** (Matěj, 2026-10-24). Inside an instance, players break only spawners, cobwebs and vines, and iron
   bars once gold bars seal the throne room (RLM-469). Smooth stone breaks only by TNT, through a room's
   `explosion_breaks`. Every other block stays protected, and placing stays forbidden. Prisoner's Kingdom keeps normal
   spawners (`minecraft:spawner`); trial spawners are left to later dungeon types (RLM-359, Matěj, 2026-10-25).
2. **Nothing broken inside an instance drops an item.** Matěj's rule for cobwebs and vines ("to prevent string
   farming", "even when broken with shears"), made the rule for every block there: a dungeon's blocks are not loot.
   A spawner broken with a pickaxe still drops vanilla's experience.
3. **Rooms meant to be seen keep unbreakable bars** (Matěj, 2026-10-24): the throne room gets gold bars, a block of
   ours crafted from gold, which is not on the list (RLM-469).
4. **Hidden spawners are exposed in their rooms** (Matěj, 2026-10-24), not freed by more breakable blocks around
   them. `skeleton_trap`'s slab over its spawner becomes a cobweb; `husk_temple`'s monument is opened on one side
   above its bottom layer. A pooled room's spawner touches a cell a player reaches from its doorways (a pytest).
5. **One list for every instance:** the block tag `realmforge:breakable_in_instances` in the core mod's resources,
   next to the instance dimension, not a list per room or per dungeon type. The client reads it too, so it predicts
   the break.
6. **A mixin lets Adventure mode break them.** Adventure mode refuses a break in `Player#blockActionRestricted`, on
   the server and in the client's prediction, before NeoForge posts `BreakBlockEvent`, which cannot undo that; a
   `can_break` component never works with an empty hand and would have to be put on every item a player brings.
   `mixin/PlayerBlockActionMixin` returns "not restricted" for a player in Adventure mode inside the instance
   dimension, for a block of the tag; `InstanceRules`' break guard lets the same blocks pass.
7. **At vanilla's speed**, from the block and the item in hand: a spawner takes 25 s by hand and 1.25 s with an iron
   pickaxe, a cobweb 20 s by hand and 0.4 s with a sword or shears, a vine 0.3 s.

Lighting TNT with flint and steel or a fire charge, which vanilla allows in Adventure mode and which skips a lock's
puzzle, stays as it is until Matěj decides (RLM-470).

## Consequences

- The core mod has its second mixin. It runs on a common class, so the dedicated server and the client both apply it;
  `InstanceRulesGameTests` break each block of the tag inside an instance and find the same spawner unbreakable
  outside it.
- A new block on the list is a line in the tag. A dungeon type that needs other blocks than every instance gets a
  per-type list then, in an ADR amending this one.
- New rooms must not hide a spawner; the pytest over the stone vault pool catches one.
- Adventure mode still hides the block outline on every block, those of the list included; breaking shows the
  cracks.
- A caster with only a wand in hand breaks slowly (by hand speed). Matěj's playtest decides whether instances need a
  faster break, which would be a `PlayerEvent.BreakSpeed` listener.
