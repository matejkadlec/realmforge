package io.github.matejkadlec.realmforge.mixin;

import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Inventory Sorting writes every sorted slot back, and a stack its ignore rule keeps in place comes back as a copy.
 * Sophisticated Backpacks closes an open backpack's screen when its backpack's stack is another object
 * ({@code BackpackContainer#storageItemHasChanged}), so even with the backpack rule ({@code compat/inventorysorter/
 * BackpackSortRule}, RLM-519) a sort of the player's inventory closed a backpack opened from it. This leaves a slot
 * alone when the sorted stack is identical to the one already there (same item, count and components), which changes
 * nothing else a sort does. Neither mod has an event or a setting for it, hence a mixin (architecture 19.3). It is
 * {@code @Pseudo}, so the core mod loads without Inventory Sorting; with it, a changed {@code ContainerStacks#set}
 * fails at load (the injector is required), not silently. Tested by the QA mod's {@code InventorySortingGameTests}.
 */
@Pseudo
@Mixin(targets = "net.kyrptonaught.inventorysorter.inventory.container.ContainerStacks")
public abstract class InventorySortingStacksMixin {
    @Redirect(method = "set", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/Container;setItem(ILnet/minecraft/world/item/ItemStack;)V"))
    private static void realmforge$skipIdenticalStacks(Container container, int slot, ItemStack sorted) {
        if (!ItemStack.matches(container.getItem(slot), sorted)) {
            container.setItem(slot, sorted);
        }
    }
}
