package io.github.matejkadlec.realmforge.qa.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import io.github.matejkadlec.realmforge.character.Characters;
import io.github.matejkadlec.realmforge.mob.MobLevels;
import io.github.matejkadlec.realmforge.stats.EntityLevel;
import io.github.matejkadlec.realmforge.stats.StatsAttachments;
import java.util.Optional;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

/**
 * {@code /qa level <level> [<entity>]}: the level of a player or mob, yourself by default (architecture 7).
 * <ul>
 *   <li>A player with a character gets exactly the character XP of that level (C2, architecture 10.2), quietly, so
 *   the level follows, up to the level cap, where XP stops. The reply names the cap when it holds the player below
 *   the level; {@code /advancement grant @s only realmforge:chapter/chapter_1} raises it.</li>
 *   <li>A player without a character gains no XP, so the command sets their {@code realmforge:level} attachment, which
 *   creating a character or logging in again derives from XP once more.</li>
 *   <li>A hostile mob also gets the scaling of its new level (W1, architecture 14.1), keeping its share of health:
 *   {@code /qa level 10 @n[type=zombie]}. Our armor formula reads the attacker's level (architecture 9.1), so a
 *   higher attacker level lets the same armor block less.</li>
 * </ul>
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
        if (entity instanceof ServerPlayer player && Characters.exists(player)) {
            return setCharacterLevel(context, player, level);
        }
        if (entity instanceof Mob mob) {
            MobLevels.setLevel(mob, level);
        } else {
            entity.setData(StatsAttachments.LEVEL, new EntityLevel(level));
        }
        context.getSource().sendSuccess(() -> Component.literal("Set the level of " + entity.getPlainTextName()
                + " to " + level), true);
        return level;
    }

    /** Gives the player the XP of {@code level}; the cap may hold them, and their XP, lower. Returns their level. */
    private static int setCharacterLevel(CommandContext<CommandSourceStack> context, ServerPlayer player, int level) {
        Optional<Long> xp = Characters.xpToReach(player, level);
        if (xp.isEmpty()) {
            context.getSource().sendFailure(Component.literal("The curve realmforge:xp_to_next_level is missing"));
            return 0;
        }
        Characters.setTotalXp(player, xp.get());
        int reached = Characters.level(player);
        long held = Characters.data(player).totalXp();
        String name = player.getPlainTextName();
        String message = reached == level
                ? "Set the level of " + name + " to " + level + " (" + held + " character XP)"
                : "The level cap holds " + name + " at level " + reached + " (" + held + " character XP) until a"
                        + " chapter raises it";
        context.getSource().sendSuccess(() -> Component.literal(message), true);
        return reached;
    }
}
