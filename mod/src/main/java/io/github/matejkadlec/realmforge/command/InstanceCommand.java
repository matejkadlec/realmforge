package io.github.matejkadlec.realmforge.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.github.matejkadlec.realmforge.dungeon.instance.InstanceManager;
import io.github.matejkadlec.realmforge.generated.GeneratedTexts;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /instance leave}, for every player (W4d, architecture 15.5): leaves the dungeon at any time, back to where
 * the player entered it from, as the dungeon exit does. The last player to leave closes its portal, and the dungeon is
 * cleared. A player whose dungeon is still being built gives it up.
 */
final class InstanceCommand {
    private InstanceCommand() {
    }

    static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("instance").then(Commands.literal("leave").executes(InstanceCommand::leave));
    }

    private static int leave(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        InstanceManager manager = InstanceManager.get(context.getSource().getServer());
        if (manager == null || !manager.leave(player)) {
            context.getSource().sendFailure(Component.translatable(GeneratedTexts.MESSAGE_NOT_IN_DUNGEON));
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.translatable(GeneratedTexts.MESSAGE_DUNGEON_LEFT), false);
        return 1;
    }
}
