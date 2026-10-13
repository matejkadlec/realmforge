package io.github.matejkadlec.realmforge.qa.command;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.github.matejkadlec.realmforge.qa.combat.TrainingDummy;
import java.util.List;
import java.util.Locale;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.Vec3;

/**
 * {@code /qa dummy [<armor>]}: spawns a training dummy ({@link TrainingDummy}) three blocks in front of you, facing
 * you, with that much armor (0 by default). {@code /qa dummy reset} restarts the DPS meter of every dummy, {@code /qa
 * dummy remove} removes them all. Set a dummy's dodge chance or resistances with vanilla's {@code /attribute}, for
 * example {@code /attribute @n[type=husk] realmforge:fire_resistance base set 0.5}.
 */
public final class DummyCommands {
    /** How far in front of the player a dummy appears. */
    static final double DISTANCE = 3;

    private DummyCommands() {
    }

    static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("dummy")
                .executes(context -> spawn(context, 0))
                .then(Commands.argument("armor", DoubleArgumentType.doubleArg(0, 1024))
                        .executes(context -> spawn(context, DoubleArgumentType.getDouble(context, "armor"))))
                .then(Commands.literal("reset").executes(DummyCommands::reset))
                .then(Commands.literal("remove").executes(DummyCommands::remove));
    }

    private static int spawn(CommandContext<CommandSourceStack> context, double armor)
            throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        Vec3 look = player.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0, look.z).normalize();
        Vec3 position = player.position().add(flat.scale(DISTANCE));
        // Facing the player: the opposite of the player's yaw.
        TrainingDummy.spawn(player.level(), position, player.getYRot() + 180, armor);
        context.getSource().sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                "Training dummy with %s armor: hit it, its name shows your DPS", armor)), true);
        return 1;
    }

    private static int reset(CommandContext<CommandSourceStack> context) {
        List<LivingEntity> dummies = dummies(context.getSource().getLevel());
        dummies.forEach(TrainingDummy::reset);
        context.getSource().sendSuccess(() -> Component.literal("Reset " + dummies.size() + " DPS meters"), false);
        return dummies.size();
    }

    private static int remove(CommandContext<CommandSourceStack> context) {
        List<LivingEntity> dummies = dummies(context.getSource().getLevel());
        dummies.forEach(TrainingDummy::remove);
        context.getSource().sendSuccess(() -> Component.literal("Removed " + dummies.size() + " training dummies"),
                true);
        return dummies.size();
    }

    private static List<LivingEntity> dummies(ServerLevel level) {
        return List.copyOf(level.getEntities(EntityTypeTest.forClass(LivingEntity.class), TrainingDummy::isDummy));
    }
}
