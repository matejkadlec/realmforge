package io.github.matejkadlec.realmforge.qa.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import io.github.matejkadlec.realmforge.qa.generated.GeneratedSetPieceMasks;
import io.github.matejkadlec.realmforge.qa.generated.GeneratedSetPieceMasks.NamedMask;
import io.github.matejkadlec.realmforge.qa.setpiece.SetPieceJig;
import java.util.Set;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * {@code /qa setpiece jig <mask> [<wall block>]}: builds the set-piece workshop jig for one of the masks
 * ({@link GeneratedSetPieceMasks}, the mask report's) north of you and turns you to face it ({@link SetPieceJig}).
 * Build the piece inside the structure block's box and press Save; {@code rf set-pieces import} does the rest. The
 * walls are stone bricks unless you name another block.
 */
final class SetPieceCommands {
    private static final DynamicCommandExceptionType UNKNOWN_MASK = new DynamicCommandExceptionType(
            name -> Component.literal("Unknown mask " + name + "; the masks are " + String.join(", ",
                    GeneratedSetPieceMasks.MASKS.stream().map(NamedMask::name).toList())));
    private static final DynamicCommandExceptionType UNKNOWN_BLOCK =
            new DynamicCommandExceptionType(id -> Component.literal("Unknown block " + id));

    private SetPieceCommands() {
    }

    static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("setpiece").then(Commands.literal("jig")
                .then(Commands.argument("mask", StringArgumentType.word())
                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                GeneratedSetPieceMasks.MASKS.stream().map(NamedMask::name), builder))
                        .executes(context -> jig(context, Blocks.STONE_BRICKS.defaultBlockState()))
                        .then(Commands.argument("wall", IdentifierArgument.id())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggestResource(
                                        BuiltInRegistries.BLOCK.keySet(), builder))
                                .executes(context -> jig(context, wall(IdentifierArgument.getId(context, "wall")))))));
    }

    private static BlockState wall(Identifier id) throws CommandSyntaxException {
        if (!BuiltInRegistries.BLOCK.containsKey(id)) {
            throw UNKNOWN_BLOCK.create(id);
        }
        return BuiltInRegistries.BLOCK.getValue(id).defaultBlockState();
    }

    private static int jig(CommandContext<CommandSourceStack> context, BlockState wall) throws CommandSyntaxException {
        String name = StringArgumentType.getString(context, "mask");
        NamedMask mask = SetPieceJig.mask(name).orElseThrow(() -> UNKNOWN_MASK.create(name));
        CommandSourceStack source = context.getSource();
        BlockPos feet = BlockPos.containing(source.getPosition());
        SetPieceJig.Jig jig = SetPieceJig.build(source.getLevel(), feet, mask, wall);
        if (source.getEntity() instanceof ServerPlayer player) {
            player.teleportTo(source.getLevel(), player.getX(), player.getY(), player.getZ(), Set.of(), 180f,
                    player.getXRot(), true);
        }
        BlockPos block = jig.structureBlock();
        source.sendSuccess(() -> Component.literal("Built the jig for " + name + " north of you. Build the piece inside"
                + " the structure block's box (" + jig.size().getX() + " x " + jig.size().getY() + " x "
                + jig.size().getZ() + "), then press Save in the structure block at " + block.getX() + " "
                + block.getY() + " " + block.getZ() + ": it saves as " + jig.name()
                + ". Then: uv run rf set-pieces import <world>/generated/" + jig.name().getNamespace() + "/structure/"
                + jig.name().getPath() + ".nbt --id <id>"), false);
        return 1;
    }
}
