# 0065. Inventory Sorting leaves backpacks in their slots

Date: 2026-10-30

Status: Accepted (amends [0062](0062-backpack-keys-and-alt-k-amend-the-pack-defaults.md))

## Context

[0062](0062-backpack-keys-and-alt-k-amend-the-pack-defaults.md) decision 2 put Sophisticated Core's Sort
Storage/Backpack on `;` next to Inventory Sorting's Sort: over a backpack's slots (or no slot) Sophisticated Core sorts
the backpack and swallows the key, over the player's own slots Inventory Sorting sorts the player's inventory. In his
playtest of RLM-501 on 2026-10-29 Matěj found that `;` over the inventory, with a backpack open from the inventory,
sorts the inventory and closes the backpack, and decided that one press sorts only the side under the mouse
(RLM-519).

Read in the jars (Inventory Sorting 3.0.1, Sophisticated Backpacks 3.26.11, Sophisticated Core 1.5.10):

- The split of 0062 holds: one press sorts one side. Inventory Sorting's sort of the player's inventory covers the 27
  main slots, never the hotbar, the armour or the offhand.
- A backpack opened from an inventory slot is tied to that slot (`BackpackContext.Item`, the slot index), and
  Sophisticated Core closes its screen at the next sync when the stack in that slot is another object than the one it
  opened (`BackpackContainer#storageItemHasChanged`), or no backpack at all. Inventory Sorting rewrites every slot it
  sorts, so it moved the backpack (the screen closed, or showed the backpack that landed in the slot).
- Inventory Sorting's own setting for a stack that must not move is a sort priority rule with the position `ignore`,
  from each player's client config, which the client sends the server on joining a world. An ignored stack keeps its
  slot, but Inventory Sorting still writes a copy of it back (`ContainerStacks#set`), which closes the screen all the
  same.

## Decision

1. **Backpacks never move in a sort.** The client adds one rule to Inventory Sorting's loaded config before it joins a
   world: every Sophisticated Backpacks tier, position `ignore` (`compat/inventorysorter/BackpackSortRule`,
   `client/compat/inventorysorter/BackpacksStayInPlace`). It lives in memory only: the player's
   `config/inventorysorter.json` and their own rules stay as they are, and the rule comes back on the next join if they
   remove it. The pack ships no `inventorysorter.json`, which would overwrite every player's own settings.
2. **A sort leaves an identical stack alone.** A mixin on Inventory Sorting's `ContainerStacks#set`
   (`mixin/InventorySortingStacksMixin`, `@Pseudo`, so the core mod loads without Inventory Sorting) skips a slot whose
   sorted stack equals the one already there (`ItemStack#matches`: item, count, components), so an ignored backpack
   keeps its very stack and its screen stays open.
3. **0062's split stands:** over a backpack slot or no slot Sophisticated Core sorts the backpack, over the player's
   slots Inventory Sorting sorts the player's inventory.

## Consequences

- With a backpack open from the inventory, `;` over the inventory sorts the rest of the inventory around the backpack,
  which stays open and unchanged; from the hotbar or worn, the backpack was never in the sorted slots.
- A backpack also keeps its slot when Inventory Sorting sorts a chest or any other inventory that holds it: the other
  stacks sort around it.
- The mixin is one of the core mod's mixins (architecture 19.3): an update of Inventory Sorting that changes
  `ContainerStacks#set` fails at load in the dev runs and the pack boot, not silently. `InventorySortingGameTests`
  check both decisions against the pack's builds.
- A new backpack tier, or an update that renames one, needs its id in `BackpackSortRule`.
