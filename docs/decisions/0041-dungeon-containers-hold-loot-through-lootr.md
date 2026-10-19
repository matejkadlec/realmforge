# 0041. Dungeon containers hold loot through Lootr

Date: 2026-10-19

Status: Accepted (amends [0013](0013-modpack-composition.md): Lootr joins the pack in M1)

## Context

Reviewing the [set-piece design](../design/set-pieces.md) (RLM-175) on 2026-10-19, Matěj asked what the chests and
barrels in dungeons hold. None held anything: W6's chest markers have no chests yet, the 100 chests the imported
rooms brought lost their source loot tables on import, and barrels were pure decoration. Lootr, which gives every
player their own contents of a container, was planned for M2 in RLM-264.

## Decision

Matěj's decisions of 2026-10-19:

1. **No container in a dungeon is empty.** Every chest and barrel a player can open there holds loot: W6's chests,
   the imported rooms' chests and barrels, the import dressing's barrels and set-piece containers.
2. **Lootr joins the pack in M1**, not M2: loot cannot be balanced without it, or it would have to be balanced again
   once it comes.
3. **A container's loot is worth about 2, 4 or 10 mob kills** (minimum, medium, a high roll); a treasure room's
   chests have a better table worth about 5, 10 or 20, with better gems, ores and enchantments for the tier.
4. **Loot points:** each container draws a budget, and every item it rolls costs points until the budget is spent,
   so a valuable item leaves little for the rest.
5. **The loot follows the player:** their world tier and their luck (vanilla's `minecraft:luck` attribute) give
   better and more loot. Some curios, and maybe weapons, are loot-only.

The [container loot design](../design/container-loot.md) holds the numbers and the proposed mechanics.

## Consequences

- The budget needs a loot entry type of our own: vanilla pools roll independently.
- Set-piece containers use the normal table ([set-piece design](../design/set-pieces.md#gameplay-rules)).
- RLM-264's plan for M2 loses Lootr, which its pull request takes into account.
- Follow-up tickets carry it: Lootr in the pack (RLM-288), the container loot tables (RLM-289) and the imported
  rooms' containers as loot spots (RLM-290).
