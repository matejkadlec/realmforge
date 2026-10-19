package io.github.matejkadlec.realmforge.qa.command;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import io.github.matejkadlec.realmforge.dungeon.instance.InstanceLayout;
import io.github.matejkadlec.realmforge.dungeon.instance.InstanceManager;
import io.github.matejkadlec.realmforge.dungeon.instance.Plot;
import io.github.matejkadlec.realmforge.qa.instance.InstanceBench;
import io.github.matejkadlec.realmforge.qa.instance.SpikeLayouts;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /qa instance ...}: spike 3's tools for instanced dungeons. {@code enter} builds the spike rooms in a plot
 * and moves you in, {@code leave} brings you back (or drops a run still being built for you), {@code build} opens a
 * plot without anyone (from the console too), {@code free}, {@code list} and {@code bench} (measurements, ADR 0019).
 * W4d adds {@code complete}, which completes your run as its final boss's death will (its portal then closes in 5
 * minutes), and {@code timer <seconds>}, which sets your run's portal to close that many seconds from now, so the
 * warnings and the closing can be seen without waiting.
 */
final class InstanceCommands {
    private static final SimpleCommandExceptionType NO_DIMENSION =
            new SimpleCommandExceptionType(Component.literal("This world has no realmforge:instances dimension"));
    private static final SimpleCommandExceptionType EVEN_ROOMS = new SimpleCommandExceptionType(
            Component.literal("A spike row has an odd number of rooms: 1, 3, 5, 7 or 9"));
    private static final SimpleCommandExceptionType NOT_IN_RUN =
            new SimpleCommandExceptionType(Component.literal("You are not in an instance run"));
    private static final SimpleCommandExceptionType ALREADY_IN_RUN =
            new SimpleCommandExceptionType(Component.literal("You are already in an instance run; leave it first"));
    private static final SimpleCommandExceptionType NOT_ACTIVE = new SimpleCommandExceptionType(
            Component.literal("Only an active run completes: enter it first, and complete it once"));
    private static final SimpleCommandExceptionType NO_PLOT =
            new SimpleCommandExceptionType(Component.literal("No such plot in use"));
    private static final SimpleCommandExceptionType BENCH_RUNNING =
            new SimpleCommandExceptionType(Component.literal("A bench is already running"));

    private InstanceCommands() {
    }

    static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("instance")
                .then(Commands.literal("enter")
                        .executes(context -> open(context, 3, false, true))
                        .then(Commands.argument("rooms", IntegerArgumentType.integer(1, SpikeLayouts.MAX_ROOMS))
                                .executes(context -> open(context, rooms(context), false, true))
                                .then(Commands.argument("turned", BoolArgumentType.bool())
                                        .executes(context -> open(context, rooms(context), turned(context), true)))))
                .then(Commands.literal("build")
                        .executes(context -> open(context, 3, false, false))
                        .then(Commands.argument("rooms", IntegerArgumentType.integer(1, SpikeLayouts.MAX_ROOMS))
                                .executes(context -> open(context, rooms(context), false, false))
                                .then(Commands.argument("turned", BoolArgumentType.bool())
                                        .executes(context -> open(context, rooms(context), turned(context), false)))))
                .then(Commands.literal("leave").executes(InstanceCommands::leave))
                .then(Commands.literal("complete").executes(InstanceCommands::complete))
                .then(Commands.literal("timer")
                        .then(Commands.argument("seconds", IntegerArgumentType.integer(1, 4 * 60 * 60))
                                .executes(InstanceCommands::timer)))
                .then(Commands.literal("free")
                        .then(Commands.argument("plot", IntegerArgumentType.integer(0))
                                .executes(InstanceCommands::free)))
                .then(Commands.literal("list").executes(InstanceCommands::list))
                .then(Commands.literal("bench")
                        .then(Commands.argument("runs", IntegerArgumentType.integer(1, 1000))
                                .then(Commands.argument("rooms", IntegerArgumentType.integer(1, SpikeLayouts.MAX_ROOMS))
                                        .executes(InstanceCommands::bench))));
    }

    private static int open(CommandContext<CommandSourceStack> context, int rooms, boolean turned, boolean enter)
            throws CommandSyntaxException {
        if (rooms % 2 == 0) {
            throw EVEN_ROOMS.create();
        }
        InstanceManager manager = manager(context);
        List<UUID> party = List.of();
        if (enter) {
            ServerPlayer player = context.getSource().getPlayerOrException();
            if (manager.plotOf(player).isPresent()) {
                throw ALREADY_IN_RUN.create();
            }
            party = List.of(player.getUUID());
        }
        InstanceLayout layout = SpikeLayouts.row(rooms, turned);
        Plot plot = manager.open(layout, party);
        context.getSource().sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                "Building %d rooms in plot %d at %s%s", rooms, plot.id(), plot.origin().toShortString(),
                enter ? "; you enter when it is built" : "")), true);
        return plot.id();
    }

    private static int leave(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        if (!manager(context).leave(player)) {
            throw NOT_IN_RUN.create();
        }
        context.getSource().sendSuccess(() -> Component.literal("Left the instance run"), false);
        return 1;
    }

    private static int complete(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        InstanceManager manager = manager(context);
        Plot plot = manager.plotOf(player).orElseThrow(NOT_IN_RUN::create);
        if (!manager.complete(plot)) {
            throw NOT_ACTIVE.create();
        }
        context.getSource().sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                "Completed the run in plot %d", plot.id())), true);
        return 1;
    }

    private static int timer(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        int seconds = IntegerArgumentType.getInteger(context, "seconds");
        InstanceManager manager = manager(context);
        Plot plot = manager.plotOf(player).orElseThrow(NOT_IN_RUN::create);
        if (!manager.closeIn(plot, seconds * 20L)) {
            throw NOT_IN_RUN.create();
        }
        context.getSource().sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                "The run in plot %d closes in %d seconds", plot.id(), seconds)), true);
        return seconds;
    }

    private static int free(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        InstanceManager manager = manager(context);
        Plot plot = manager.plot(IntegerArgumentType.getInteger(context, "plot")).orElseThrow(NO_PLOT::create);
        manager.release(plot);
        context.getSource().sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                "Released plot %d, now %s", plot.id(), plot.state().getSerializedName())), true);
        return 1;
    }

    private static int list(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        InstanceManager manager = manager(context);
        context.getSource().sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                "%d plots", manager.plots().size())), false);
        for (Plot plot : manager.plots()) {
            String report = manager.lastReport(plot.id()).map(r -> "; last job " + r).orElse("");
            context.getSource().sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                    "  plot %d at %s: %s, %d rooms, %d markers, party %d, %d of %d chunks held, step %d%s",
                    plot.id(), plot.origin().toShortString(), plot.state().getSerializedName(), plot.rooms().size(),
                    plot.markers().size(), plot.party().size(), plot.chunks().size(), plot.wantedChunks().size(),
                    plot.step(), report)), false);
        }
        return manager.plots().size();
    }

    private static int bench(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        int runs = IntegerArgumentType.getInteger(context, "runs");
        int rooms = rooms(context);
        if (rooms % 2 == 0) {
            throw EVEN_ROOMS.create();
        }
        manager(context);
        if (!InstanceBench.start(context.getSource(), runs, rooms)) {
            throw BENCH_RUNNING.create();
        }
        context.getSource().sendSuccess(() -> Component.literal("Bench started; results follow here and in the log"),
                true);
        return 1;
    }

    private static InstanceManager manager(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        InstanceManager manager = InstanceManager.get(context.getSource().getServer());
        if (manager == null) {
            throw NO_DIMENSION.create();
        }
        return manager;
    }

    private static int rooms(CommandContext<CommandSourceStack> context) {
        return IntegerArgumentType.getInteger(context, "rooms");
    }

    private static boolean turned(CommandContext<CommandSourceStack> context) {
        return BoolArgumentType.getBool(context, "turned");
    }
}
