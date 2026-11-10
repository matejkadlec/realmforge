package io.github.matejkadlec.realmforge.item;

import io.github.matejkadlec.realmforge.Realmforge;
import io.github.matejkadlec.realmforge.generated.GeneratedCoins;
import io.github.matejkadlec.realmforge.generated.GeneratedCoins.Coin;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * The coins (architecture 13; RLM-284, ADR 0040, ADR 0072): copper, silver, gold and platinum, each worth 100 of the
 * coin below it, items of {@code content/items/} with a {@code coin} block. Money is an amount of the smallest coin,
 * paid in the largest coins ({@link #stacks}). The public API of the coins for other features: mob coin drops
 * ({@code loot/CoinDrops}) today, merchants' prices and shops later.
 */
public final class Coins {
    /** {@code #realmforge:coins}: every coin, for trades, loot and other mods. */
    public static final TagKey<Item> TAG = TagKey.create(Registries.ITEM, Realmforge.id("coins"));

    private Coins() {
    }

    /** Every coin, smallest first. */
    public static List<Coin> chain() {
        return GeneratedCoins.COINS;
    }

    /** The place of {@code item} in the {@link #chain}, or -1 when it is no coin. */
    public static int indexOf(Item item) {
        List<Coin> chain = chain();
        for (int index = 0; index < chain.size(); index++) {
            if (chain.get(index).item().get() == item) {
                return index;
            }
        }
        return -1;
    }

    /** What {@code stack} is worth in the smallest coin; 0 for a stack of no coin. */
    public static long value(ItemStack stack) {
        int index = indexOf(stack.getItem());
        return index < 0 ? 0 : chain().get(index).value() * stack.getCount();
    }

    /**
     * {@code amount} of the smallest coin as item stacks, in the largest coins, largest first; a count beyond a stack's
     * size takes several stacks. Empty for an amount below 1.
     */
    public static List<ItemStack> stacks(long amount) {
        List<Coin> chain = chain();
        long[] counts = CoinMath.split(amount, chain.stream().mapToLong(Coin::value).toArray());
        List<ItemStack> stacks = new ArrayList<>();
        for (int index = chain.size() - 1; index >= 0; index--) {
            stacks.addAll(stacks(chain.get(index).item().get(), counts[index]));
        }
        return stacks;
    }

    /** {@code count} of {@code item} as stacks no larger than its stack size. */
    static List<ItemStack> stacks(Item item, long count) {
        List<ItemStack> stacks = new ArrayList<>();
        int size = item.getDefaultMaxStackSize();
        for (long rest = count; rest > 0; rest -= size) {
            stacks.add(new ItemStack(item, (int) Math.min(rest, size)));
        }
        return stacks;
    }

    /**
     * Puts {@code stack} into {@code player}'s inventory, filling stacks of it before free slots; what does not fit
     * drops at their feet, a plain drop rather than a throw, as a locked shield does ({@link TwoHandedGear}). Slot by
     * slot rather than through {@code Inventory#add(ItemStack)}, which voids what does not fit for a player in
     * creative mode.
     */
    static void give(ServerPlayer player, ItemStack stack) {
        Inventory inventory = player.getInventory();
        while (!stack.isEmpty()) {
            int slot = inventory.getSlotWithRemainingSpace(stack);
            if (slot < 0) {
                slot = inventory.getFreeSlot();
            }
            if (slot < 0) {
                break;
            }
            int room = stack.getMaxStackSize() - inventory.getItem(slot).getCount();
            inventory.add(slot, stack.split(room));
        }
        if (stack.isEmpty()) {
            return;
        }
        ItemEntity dropped = player.drop(stack, false, false);
        if (dropped != null) {
            dropped.setDeltaMovement(Vec3.ZERO);
        }
    }
}
