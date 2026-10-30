package io.github.matejkadlec.realmforge.qa.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * {@code /qa inventory clear}: empties the 27 slots of the main inventory (Matěj, 2026-10-30), between playtests that
 * fill it with kits and loot. The hotbar, the armor and the offhand stay, unlike vanilla's {@code /clear}.
 */
public final class InventoryCommands {
    private InventoryCommands() {
    }

    static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("inventory")
                .then(Commands.literal("clear").executes(InventoryCommands::clear));
    }

    /** Empties the main inventory's slots above the hotbar; returns how many stacks it removed. */
    public static int clearMain(ServerPlayer player) {
        Inventory inventory = player.getInventory();
        int removed = 0;
        for (int slot = Inventory.SELECTION_SIZE; slot < Inventory.INVENTORY_SIZE; slot++) {
            if (!inventory.getItem(slot).isEmpty()) {
                inventory.setItem(slot, ItemStack.EMPTY);
                removed++;
            }
        }
        return removed;
    }

    private static int clear(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        int removed = clearMain(player);
        context.getSource().sendSuccess(() -> Component.literal("Cleared " + removed + " stacks from the inventory of "
                + player.getPlainTextName() + "; the hotbar, armor and offhand stay"), true);
        return removed;
    }
}
