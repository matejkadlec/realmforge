package io.github.matejkadlec.realmforge.qa.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import io.github.matejkadlec.realmforge.stats.EntityLevel;
import io.github.matejkadlec.realmforge.stats.StatsAttachments;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * {@code /qa level <level> [<entity>]}: sets the {@code realmforge:level} attachment of a player or mob, yourself by
 * default (architecture 7), before leveling (C2) and mob levels (W1) exist. Our armor formula reads the attacker's level
 * (architecture 9.1), so a higher attacker level lets the same armor block less.
 */
public final class LevelCommands {
    private static final SimpleCommandExceptionType NOT_LIVING =
            new SimpleCommandExceptionType(Component.literal("Only players and mobs have a level"));

    private LevelCommands() {
    }

    static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("level")
                .then(Commands.argument("level", IntegerArgumentType.integer(EntityLevel.MIN))
                        .executes(context -> set(context, context.getSource().getEntityOrException()))
                        .then(Commands.argument("entity", EntityArgument.entity())
                                .executes(context -> set(context, EntityArgument.getEntity(context, "entity")))));
    }

    private static int set(CommandContext<CommandSourceStack> context, Entity target) throws CommandSyntaxException {
        if (!(target instanceof LivingEntity entity)) {
            throw NOT_LIVING.create();
        }
        int level = IntegerArgumentType.getInteger(context, "level");
        entity.setData(StatsAttachments.LEVEL, new EntityLevel(level));
        context.getSource().sendSuccess(() -> Component.literal("Set the level of " + entity.getPlainTextName()
                + " to " + level), true);
        return level;
    }
}
