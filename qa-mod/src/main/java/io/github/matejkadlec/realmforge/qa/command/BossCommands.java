package io.github.matejkadlec.realmforge.qa.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import io.github.matejkadlec.realmforge.boss.BossMob;
import io.github.matejkadlec.realmforge.boss.BossPhase;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * {@code /qa boss} (W5a, architecture 14.3), on the nearest boss within {@value #RANGE} blocks:
 * <ul>
 *   <li>{@code /qa boss} shows its phase, the time in it, a phase change in progress, its health and its home;</li>
 *   <li>{@code /qa boss phase <phase>} puts it into a phase by id, as the fight does: its {@code on_enter} spells and
 *   its phase change included, so a playtest reaches a late phase without the fight before it;</li>
 *   <li>{@code /qa boss reset} resets it as leaving its arena does: home, full health, the first phase.</li>
 * </ul>
 */
public final class BossCommands {
    static final double RANGE = 64;

    private static final SimpleCommandExceptionType NO_BOSS =
            new SimpleCommandExceptionType(Component.literal("No boss within " + (int) RANGE + " blocks"));
    private static final DynamicCommandExceptionType NO_PHASE =
            new DynamicCommandExceptionType(phases -> Component.literal("No such phase; the boss has " + phases));

    private BossCommands() {
    }

    static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("boss")
                .executes(BossCommands::show)
                .then(Commands.literal("phase")
                        .then(Commands.argument("phase", StringArgumentType.word())
                                .executes(context -> phase(context, StringArgumentType.getString(context, "phase")))))
                .then(Commands.literal("reset").executes(BossCommands::reset));
    }

    /** One line about the boss: "Test Titan: Phase fury (2 of 3) for 4.5 s, changing phase, Health 80.0 / 200.0". */
    public static String describe(BossMob boss) {
        List<BossPhase> phases = boss.definition().map(entry -> entry.value().phases()).orElse(List.of());
        if (phases.isEmpty()) {
            return boss.getPlainTextName() + ": no boss definition";
        }
        return String.format(Locale.ROOT, "%s: Phase %s (%d of %d) for %.1f s%s, Health %.1f / %.1f, Home %s",
                boss.getPlainTextName(), phases.get(boss.phaseIndex()).id(), boss.phaseIndex() + 1, phases.size(),
                boss.ticksInPhase() / 20.0, boss.changingPhase() ? ", changing phase" : "", boss.getHealth(),
                boss.getMaxHealth(), boss.home().map(BlockPos::toShortString).orElse("not yet"));
    }

    private static int show(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        BossMob boss = nearest(context.getSource());
        String line = describe(boss);
        context.getSource().sendSuccess(() -> Component.literal(line), false);
        return boss.phaseIndex() + 1;
    }

    private static int phase(CommandContext<CommandSourceStack> context, String id) throws CommandSyntaxException {
        BossMob boss = nearest(context.getSource());
        List<BossPhase> phases = boss.definition().map(entry -> entry.value().phases()).orElse(List.of());
        for (int index = 0; index < phases.size(); index++) {
            if (phases.get(index).id().equals(id)) {
                boss.enterPhase(index);
                String line = describe(boss);
                context.getSource().sendSuccess(() -> Component.literal(line), false);
                return index + 1;
            }
        }
        throw NO_PHASE.create(phases.stream().map(BossPhase::id).toList());
    }

    private static int reset(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        BossMob boss = nearest(context.getSource());
        boss.reset();
        String line = describe(boss);
        context.getSource().sendSuccess(() -> Component.literal(line), false);
        return 1;
    }

    private static BossMob nearest(CommandSourceStack source) throws CommandSyntaxException {
        Vec3 position = source.getPosition();
        return source.getLevel().getEntitiesOfClass(BossMob.class, new AABB(position, position).inflate(RANGE),
                        boss -> boss.isAlive() && boss.definition().isPresent())
                .stream()
                .min(Comparator.comparingDouble(boss -> boss.distanceToSqr(position)))
                .orElseThrow(NO_BOSS::create);
    }
}
