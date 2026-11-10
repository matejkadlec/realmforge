package io.github.matejkadlec.realmforge.item;

import io.github.matejkadlec.realmforge.generated.GeneratedCoins.Coin;
import io.github.matejkadlec.realmforge.generated.GeneratedTexts;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * The coin converter (architecture 13; RLM-284, ADR 0072): using a coin converts it, without a crafting grid or a
 * block.
 *
 * <ul>
 *   <li><b>Use</b> merges: every full rate of that coin in the player's inventory (100) becomes one of the next larger
 *       coin, and the rest below a rate stays. With fewer than a rate, the action bar says how many it takes.</li>
 *   <li><b>Sneak and use</b> breaks: one coin of the held stack becomes a rate (100) of the next smaller coin.</li>
 * </ul>
 *
 * Merging is the plain use because it is the common and the harmless one: it frees slots, loses nothing and can be
 * undone, while a break fills two slots. The smallest coin does not break and the largest does not merge: such a use
 * passes, as on any item without one. What does not fit the inventory drops at the player's feet ({@link Coins#give}).
 * Each conversion puts a short cooldown on that coin, so a held button converts at a pace a player can follow. The
 * event runs on both sides: the client answers the same way from its copy of the inventory, so the arm swings exactly
 * when the server converts, and only the server changes anything.
 */
public final class CoinConverter {
    /** The pause after a conversion, on the coin used: half a second. */
    public static final int COOLDOWN_TICKS = 10;

    private CoinConverter() {
    }

    static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        ItemStack held = event.getItemStack();
        List<Coin> chain = Coins.chain();
        int index = Coins.indexOf(held.getItem());
        if (index < 0) {
            return;
        }
        Player player = event.getEntity();
        boolean breaking = player.isSecondaryUseActive();
        if (breaking ? index == 0 : index == chain.size() - 1) {
            return;
        }
        Coin coin = chain.get(index);
        event.setCanceled(true);
        if (breaking) {
            Coin smaller = chain.get(index - 1);
            event.setCancellationResult(InteractionResult.SUCCESS);
            if (player instanceof ServerPlayer serverPlayer) {
                breakOne(serverPlayer, held, smaller.item().get(), CoinMath.rate(smaller.value(), coin.value()));
            }
            return;
        }
        Coin larger = chain.get(index + 1);
        long rate = CoinMath.rate(coin.value(), larger.value());
        long merges = CoinMath.merges(count(player.getInventory(), held.getItem()), coin.value(), larger.value());
        if (merges < 1) {
            event.setCancellationResult(InteractionResult.FAIL);
            if (player instanceof ServerPlayer serverPlayer) {
                serverPlayer.sendOverlayMessage(Component.translatable(GeneratedTexts.MESSAGE_COIN_MERGE_TOO_FEW, rate,
                        Component.translatable(coin.pluralKey()), Component.translatable(larger.item().get().getDescriptionId())));
            }
            return;
        }
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (player instanceof ServerPlayer serverPlayer) {
            merge(serverPlayer, held, larger.item().get(), merges, rate);
        }
    }

    /** One coin of {@code held} becomes {@code rate} of {@code smaller}. */
    private static void breakOne(ServerPlayer player, ItemStack held, Item smaller, long rate) {
        player.getCooldowns().addCooldown(held, COOLDOWN_TICKS);
        held.shrink(1);
        Coins.stacks(smaller, rate).forEach(stack -> Coins.give(player, stack));
        clink(player);
    }

    /** {@code merges} times {@code rate} coins of {@code held}'s kind become {@code merges} of {@code larger}. */
    private static void merge(ServerPlayer player, ItemStack held, Item larger, long merges, long rate) {
        player.getCooldowns().addCooldown(held, COOLDOWN_TICKS);
        take(player.getInventory(), held.getItem(), merges * rate);
        Coins.stacks(larger, merges).forEach(stack -> Coins.give(player, stack));
        clink(player);
    }

    /** How many of {@code item} the inventory holds, in every slot. */
    static long count(Inventory inventory, Item item) {
        long count = 0;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.is(item)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    /** Takes {@code count} of {@code item} out of the inventory, from the last slot down, so the hotbar goes last. */
    private static void take(Inventory inventory, Item item, long count) {
        long rest = count;
        for (int slot = inventory.getContainerSize() - 1; slot >= 0 && rest > 0; slot--) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.is(item)) {
                rest -= inventory.removeItem(slot, (int) Math.min(rest, stack.getCount())).getCount();
            }
        }
    }

    private static void clink(ServerPlayer player) {
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.CHAIN_PLACE,
                SoundSource.PLAYERS, 0.6F, 1.6F);
    }
}
