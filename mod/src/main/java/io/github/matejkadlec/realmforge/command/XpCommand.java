package io.github.matejkadlec.realmforge.command;

import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.github.matejkadlec.realmforge.character.CharacterData;
import io.github.matejkadlec.realmforge.character.Characters;
import io.github.matejkadlec.realmforge.generated.GeneratedTexts;
import java.util.Collection;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /realmforge xp} (architecture 10.2, 16): character XP for operators, and for quest rewards, which FTB Quests
 * runs as a {@code CommandReward} at permission level 2.
 * <ul>
 *   <li>{@code /realmforge xp add <players> <xp>} gives XP as a reward: times each player's XP multiplier, with
 *   level-ups and their effects.</li>
 *   <li>{@code /realmforge xp set <players> <xp>} sets the total XP exactly, and the level from it, quietly.</li>
 *   <li>{@code /realmforge xp query <player>} shows the total XP, the level and the level cap; it returns the XP.</li>
 * </ul>
 * Players without a character gain no XP (architecture 10.1); the command says so for each.
 */
final class XpCommand {
    private XpCommand() {
    }

    static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("xp")
                .then(Commands.literal("add")
                        .then(Commands.argument("players", EntityArgument.players())
                                .then(Commands.argument("xp", LongArgumentType.longArg(0))
                                        .executes(XpCommand::add))))
                .then(Commands.literal("set")
                        .then(Commands.argument("players", EntityArgument.players())
                                .then(Commands.argument("xp", LongArgumentType.longArg(0))
                                        .executes(XpCommand::set))))
                .then(Commands.literal("query")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(XpCommand::query)));
    }

    private static int add(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Collection<ServerPlayer> players = EntityArgument.getPlayers(context, "players");
        long xp = LongArgumentType.getLong(context, "xp");
        int changed = 0;
        for (ServerPlayer player : players) {
            if (!Characters.exists(player)) {
                noCharacter(context, player);
                continue;
            }
            long gained = Characters.rewardXp(player, xp);
            context.getSource().sendSuccess(() -> Component.translatable(GeneratedTexts.COMMAND_XP_ADDED, gained,
                    player.getDisplayName()), true);
            changed++;
        }
        return changed;
    }

    private static int set(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Collection<ServerPlayer> players = EntityArgument.getPlayers(context, "players");
        long xp = LongArgumentType.getLong(context, "xp");
        int changed = 0;
        for (ServerPlayer player : players) {
            if (!Characters.setTotalXp(player, xp)) {
                noCharacter(context, player);
                continue;
            }
            context.getSource().sendSuccess(() -> Component.translatable(GeneratedTexts.COMMAND_XP_SET, xp,
                    player.getDisplayName()), true);
            changed++;
        }
        return changed;
    }

    private static int query(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(context, "player");
        CharacterData data = Characters.data(player);
        if (!data.exists()) {
            noCharacter(context, player);
            return 0;
        }
        int cap = data.levelCap().orElse(Characters.level(player));
        context.getSource().sendSuccess(() -> Component.translatable(GeneratedTexts.COMMAND_XP_QUERY,
                player.getDisplayName(), data.totalXp(), Characters.level(player), cap), false);
        return (int) Math.min(Integer.MAX_VALUE, data.totalXp());
    }

    private static void noCharacter(CommandContext<CommandSourceStack> context, ServerPlayer player) {
        context.getSource().sendFailure(Component.translatable(GeneratedTexts.COMMAND_XP_NO_CHARACTER,
                player.getDisplayName()));
    }
}
