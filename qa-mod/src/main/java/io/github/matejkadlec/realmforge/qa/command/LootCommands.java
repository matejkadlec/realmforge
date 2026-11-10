package io.github.matejkadlec.realmforge.qa.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.github.matejkadlec.realmforge.Realmforge;
import io.github.matejkadlec.realmforge.loot.LootContainers;
import io.github.matejkadlec.realmforge.qa.generated.GeneratedContainerLootExpectations;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * {@code /qa loot chest|barrel <table>} (RLM-289): places a loot container two blocks in front of the player, facing
 * them, with a container loot table of {@code content/container_loot/} ({@code normal}, {@code treasure}), the way the
 * dungeon builder does ({@link LootContainers#place}: Lootr's container, loot for every player). For playtests of
 * the tables before the rooms' containers carry them; a player's world tier is Apotheosis's
 * {@code /apoth set_world_tier <player> <tier>}.
 */
public final class LootCommands {
    private static final String TABLE = "table";

    private LootCommands() {
    }

    static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("loot")
                .then(Commands.literal("chest").then(table(false)))
                .then(Commands.literal("barrel").then(table(true)));
    }

    private static RequiredArgumentBuilder<CommandSourceStack, String> table(boolean barrel) {
        return Commands.argument(TABLE, StringArgumentType.word())
                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                        GeneratedContainerLootExpectations.TABLES.stream().map(LootCommands::name), builder))
                .executes(context -> place(context, barrel));
    }

    /** A table's name in the command: its id's path, {@code normal} for {@code realmforge:normal}. */
    private static String name(GeneratedContainerLootExpectations.ContainerLootExpectation table) {
        return Identifier.parse(table.id()).getPath();
    }

    private static int place(CommandContext<CommandSourceStack> context, boolean barrel) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        String name = StringArgumentType.getString(context, TABLE);
        var table = GeneratedContainerLootExpectations.TABLES.stream()
                .filter(expected -> name(expected).equals(name)).findFirst();
        if (table.isEmpty()) {
            context.getSource().sendFailure(Component.literal("No container loot table " + Realmforge.MOD_ID + ":"
                    + name + " (content/container_loot/)"));
            return 0;
        }
        Direction facing = player.getDirection();
        BlockPos pos = player.blockPosition().relative(facing, 2);
        // A chest opens towards its facing, so it faces the player; a barrel's lid does the same.
        BlockState state = barrel
                ? Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, facing.getOpposite())
                : Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, facing.getOpposite());
        ResourceKey<LootTable> key =
                ResourceKey.create(Registries.LOOT_TABLE, Identifier.parse(table.get().lootTable()));
        LootContainers.place(player.level(), pos, state, key, 0L);
        boolean perPlayer = LootContainers.isPerPlayer(player.level().getBlockState(pos));
        context.getSource().sendSuccess(() -> Component.literal("Placed a " + (barrel ? "barrel" : "chest") + " with "
                + table.get().lootTable() + " at " + pos.toShortString() + (perPlayer ? " (Lootr's: loot for every "
                + "player)" : " (vanilla's: Lootr is not loaded, one roll for everybody)")), true);
        return 1;
    }
}
