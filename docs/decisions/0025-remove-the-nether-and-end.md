# 0025. Remove the Nether and End; the Overworld is the starting area

Date: 2026-10-12

Status: Accepted (resolves the Nether and End question left open by
[0012](0012-procedural-dungeons-and-dimensions.md)), amended by
[0032](0032-v1-realm-layout-amends-the-nether-and-end-removal.md) (Overworld for levels 1–10, Ashfire no replacement
for the Nether, netherite in Undervault)

## Context

The vision left open whether the Nether and End are overhauled or removed: Matěj liked that FTB Architect's Exodus
has only its own dimensions, and the Nether and End are in every other pack. The roadmap carried this as decision D10
until M4 planning. The realm model (custom dimensions called realms, one per biome style, unlocked one by one;
[realm design](../design/realms.md)) made it worth deciding earlier.

Removing both dimensions takes away the only vanilla source of many items (quartz, glowstone, blaze rods, nether
wart, netherite, shulker shells, elytra, the nether star), so the pipeline's "every item is obtainable" rule
([research notes, section 4](../research-notes.md#4-content-pipeline)) needs a new source for each.
[Research notes, removing the Nether and End](../research-notes.md#removing-the-nether-and-end) lists the
consequences; the reference-game research ([RLM-94](../research/reference-games.md), FP-7, TR-8) covers brewing and
flight without them.

Matěj decided on 2026-10-11 and 2026-10-12 (Jira RLM-47; its comments hold his words).

## Decision

- **The Nether and End are removed.** Their items get new sources: repeatable quests, trades and drops from mobs in
  the realms. The End is "a dimension about nothing".
- **A fire realm replaces the Nether, late in the progression.** It plays like an Overworld with lava rivers
  instead of water, volcanoes and dragons, and stays passable (not as blocked as the Nether). It comes late on
  purpose: weak dragons make no sense. Quartz can be found there and blazes drop blaze rods there. Its dragons
  fight on the ground (the vision allows no flying bosses).
- **The Overworld is the starting area** for levels 1–5. The first realm is open from level 1 and recommended from
  level 5. Whether the Overworld is also the hub for realm portals was left to the realm design, which makes it the
  hub ([realm design](../design/realms.md#the-overworld)).
- **The first realm offers most of what the Nether offered** (rule of thumb).
- **Potions are brewable from the early game**, together with foods that give weak but long-lasting buffs
  (roadmap M2, RLM-103). Their inputs therefore need early sources, or the reworked brewing does without them.
- **Where each item comes from:**

  | Item | Vanilla source | New source |
  | --- | --- | --- |
  | Elytra | End cities | Chapter 4, when the fastest mount tier unlocks (RLM-101), or later (chapter 5, or a side questline of chapter 4); Matěj's reason: "elytra are a little better than a mount". **Settled 2026-10-13 (RLM-138): chapter 3's final boss**, as its drop or in an item frame in its room (like the End ship), so elytra arrive at the step into chapter 4; chapter 3 comes in M4, so this source ships with the removal. They get **their own equipment slot** and never take the chestplate slot (below) |
  | Shulker shells (shulker boxes) | Shulkers in End cities | Mostly unneeded next to backpacks and Tom's Simple Storage. A trade, or shulkers that live in a realm (possibly already the stone realm) with grey instead of purple as their default colour |
  | Beacon (nether star) | The Wither, summoned from Nether materials | Earlier than the fire realm, because a vanilla beacon is easy to get: a nether star as a chapter 3 boss reward (any boss, not necessarily the Wither), or a small built beacon (two layers, for example emerald blocks) at the end of a dungeon |
  | Ender pearls | Endermen | Stay (endermen spawn in the Overworld), plus a Cleric trade and a repeatable quest, because recipes need them |
  | Quartz | Nether quartz ore | Early: a special ore in the Overworld or the stone realm, quartz set in stone instead of netherrack, because other mods expect quartz early. Also found in the fire realm |
  | Blaze rods | Blazes in Nether fortresses | A repeatable quest; blazes also drop them in the fire realm |
  | Glowstone | Nether ceilings | Generates near the ceiling of the stone realm |
  | Netherite | Ancient debris; scrap in bastions | Chapter 2 rewards: netherite scrap in loot chests, ingots or even blocks in treasure rooms. Ancient debris as an ore about as rare as in the Nether, in the desert realm (an artefact-like ore). The desert realm's structures hold suspicious sand and red sand with a reworked brushing loot table: a little trash, refining items (desert-themed gems), enchanted books and netherite scrap |
  | Nether wart | Nether fortresses | A mob in the stone realm drops it, or the stone realm has patches of soul sand with nether wart |

- **The elytra slot is a Realmforge slot on the Curios API.** Matěj allows Curios as a library (it is already a
  planned dependency, [0004](0004-build-rpg-systems-in-house.md)) but no curio items from other mods except the
  Sophisticated backpack (RLM-7). A Curios slot brings the slot's screen, saving, syncing and keep-inventory
  handling with it; a slot of our own would need all of that rebuilt. Other mods' curio items are kept out of the
  slot and out of the pack, as RLM-7 already requires.
- **Realm names are working names.** "Stoneblock", "Sahara" and "Tundra" are not final.

## Consequences

- **When:** the removal and the new sources ship together in M4 (roadmap: "the Nether and End removed"). Until
  then both dimensions stay untouched; M1 does not change them. A system that needs a new source earlier brings that
  source with it (M2's brewing, below). How the dimensions are removed (a world preset without them, or portals
  that never light and a cancelled dimension change; NeoForge 21.1 has the cancellable `BlockEvent.PortalSpawnEvent`
  and `EntityTravelToDimensionEvent`) is that task's call.
- **Every item stays obtainable.** The table above covers the items Matěj named. The rest of the Nether and End's
  items still need a source, which the M4 task maps through the same three channels (quests, trades, realm drops).
  Among them: ghast tears, wither skeleton skulls, magma cream, crimson and
  warped wood, blackstone, basalt, shroomlight, end stone, purpur, chorus fruit, end rods, dragon's breath, the
  dragon head and egg, the netherite upgrade template and the armour trim templates found only in the Nether and
  End (rib, snout, spire), and the Pigstep disc. The obtainability validation lists what is left once it runs over
  vanilla recipes and loot.
- **Early brewing:** the brewing stand needs a blaze rod and blaze powder as fuel, and most potions need nether wart.
  M2's simple potions must not depend on the Nether: either their inputs get early sources that ship with M2, ahead
  of the removal (the stone realm's nether wart, the blaze rod quest), or they are brewed in a reworked way without
  them (open question 8 of the research).
- **Vanilla advancements:** the Nether and End advancement tabs, and Overworld advancements that point at them
  ("We Need to Go Deeper", "The End?", "Eye Spy"), become unreachable. The removal task hides or replaces them with
  our own chapter advancements, so the advancement screen shows nothing a player cannot do.
- **Strongholds and eyes of ender** lose their purpose; the removal task decides whether strongholds stay as an
  Overworld dungeon.
- **Third-party mods** that expect the dimensions: biome mods with Nether or End biomes (Regions Unexplored; Oh The
  Biomes We've Gone, RLM-105) simply never generate them, but their blocks and items stay registered and count for
  obtainability. Recipes of pack mods that use Nether or End materials (netherite tiers of storage upgrades, ender
  pearls in waystone items) keep working through the new sources. The full-pack registry dump
  ([0005](0005-content-pipeline-yaml-single-source-of-truth.md)) makes this check automatic.
- **Elytra flight from another slot needs a hook of ours.** In 1.21.1 vanilla starts and ticks elytra flight only
  from the chest slot (`Player#tryToStartFallFlying` and `LivingEntity#updateFallFlying` read
  `getItemBySlot(EquipmentSlot.CHEST)`, and NeoForge's `IItemExtension#canElytraFly` is asked only for that stack).
  The task that adds the slot verifies Curios' API (*verify*, [architecture](../architecture.md)) and adds that hook.
- **Elytra and flying mounts:** Matěj's reason for the elytra's chapter ("a little better than a mount") differs
  from the vision (flying mounts "better than Elytra") and the M6 outline ("flying mounts better than elytra").
  This ADR fixes only the elytra's chapter and slot. How fast they are against the mount tiers is decided with the
  mount design (RLM-101), and the vision and M6 change only as Matěj decides
  ([realm design, open question 7](../design/realms.md#open-questions)). **Decided 2026-10-13 (RLM-115):** elytra
  are faster in the air; mounts win on overall speed, utility and combat, since elytra use up rockets and leave
  their wearer unable to fight, while a mount uses nothing, gives buffs and lets its rider fight
  ([Chococraft research, decision](../research/chococraft.md#decision-rlm-115)).
- **The fire realm** gets its place in the progression from the realm design; it has no milestone yet. Its dragons
  are grounded bosses, in line with the vision.
