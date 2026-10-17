# 0033. Ancient merchants and the Wither amend the Nether and End removal

Date: 2026-10-17

Status: Accepted (amends [0025](0025-remove-the-nether-and-end.md))

## Context

[0025](0025-remove-the-nether-and-end.md) removed the Nether and End and gave their items new sources: repeatable
quests, trades and realm drops. [0032](0032-v1-realm-layout-amends-the-nether-and-end-removal.md) moved some of
them to fit the v1 realm layout.

Since then Matěj made decisions that change several rows of 0025's item table:

- He adopted Apotheosis (Jira RLM-201, 2026-10-15). Its station recipes need nether bricks, blackstone, basalt, a
  nether star, blaze rods and ender pearls.
- He kept those recipes at their defaults (RLM-199, 2026-10-17).
- He decided where the materials come from instead: two villager merchants (RLM-241, 2026-10-16 and 2026-10-17).

0025 is accepted, so this ADR records the changes instead of rewriting it. The design is in
[apotheosis.md](../design/apotheosis.md#recipes-without-the-nether-and-end).

## Decision

### Two villager professions

The **Ancient Builder** and the **Ancient Specialist** (RLM-241) sell Nether and End materials for coins (RLM-222):

- They are ordinary professions with workstations of their own (Ancient Casting and Ancient Workbench).
- Each has a fixed trade list. They have no villager levels, and their prices never scale with the player's level.
- **Lore:** their backstory is RLM-241's, which takes it into the [lore outline](../lore/outline.md) first.
  This ADR records only where the items come from.
- **The Ancient Builder sells:** netherrack, nether bricks, basalt, blackstone, soul sand, soul soil, magma blocks,
  glowstone, nether quartz ore and crying obsidian.
- **The Ancient Specialist sells:** crimson and warped nylium and fungi, nether wart, blaze rods, ender pearls,
  ghast tears, wither skeleton skulls and netherite scrap.

### The rows of 0025's table that change

| Item | 0025's source | Source now |
| --- | --- | --- |
| Blaze rods | a repeatable quest; blazes in the fire realm | the Ancient Specialist; the repeatable quest is dropped |
| Ender pearls | endermen, plus a Cleric trade and a repeatable quest | endermen and the Ancient Specialist; the Cleric trade and the quest are dropped |
| Nether wart | a stone realm mob, or soul sand patches in the stone realm | the Ancient Specialist; it no longer spawns in Undervault |
| Beacon (nether star) | a chapter 3 boss reward, or a small built beacon | adds the Wither, which can be summoned again (below); Matěj: the nether star needs no replacement |
| Desert brushing loot | trash, refining items (desert-themed gems), enchanted books | trash, desert gems and enchanted books; no refining items, because Apotheosis's salvaging supplies the reforging materials (RLM-199) |

### The Wither can be summoned again

- Its skulls come from the Ancient Specialist and its soul sand from the Ancient Builder.
- It is a boss with a base level of 20, raised to the highest player (RLM-240).
- The guide says so.

### Apotheosis's recipes stay at their defaults

The merchants and the Wither supply every Nether and End ingredient of the Apotheosis station recipes, so those
recipes need no overrides (RLM-199). The swap of nether bricks for Undervault's stone bricks, approved on
2026-10-16, is dropped.

Everything else in 0025 and 0032 stands:

- both dimensions are removed in M4;
- every item stays obtainable;
- quartz comes early from Stone Quartz Ore (RLM-200), and Undervault's ancient debris stays (the merchants are an
  extra source).

## Consequences

- **Several of 0025's leftover items get a source.** Its consequences listed items for the M4 task to map. Ghast
  tears, wither skeleton skulls, blackstone, basalt, and crimson and warped wood (grown from the Specialist's fungi
  on its nylium) now have one. The rest of that list (magma cream, shroomlight, the End's items) still waits for
  the M4 task.
- **Coins become a dependency of the Nether materials.** The merchants price in coins (RLM-222), so the first
  Apotheosis station that needs a Nether ingredient waits for the merchants.
- **A summoned Wither is a boss that can be farmed.** It is levelled like other bosses (RLM-240), and its nether
  stars are the Augmenting Table's source.
- **The vanilla Wither flies**, and the [vision](../vision.md) wants no flying bosses. Matěj's decision names the
  Wither without settling this, so it is an open question for him: an exception for the vanilla Wither (the
  vision's rule then covers our own bosses), a Wither kept on the ground, or another nether star source
  ([Apotheosis design](../design/apotheosis.md#open-questions), question 5).
- **Docs:** the [Apotheosis design](../design/apotheosis.md) and the [realm design](../design/realms.md) follow this
  ADR. Building the professions is RLM-241.
