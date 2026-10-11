package io.github.matejkadlec.realmforge.qa.instance;

import io.github.matejkadlec.realmforge.dungeon.instance.BuildReport;
import io.github.matejkadlec.realmforge.dungeon.instance.InstanceDimension;
import io.github.matejkadlec.realmforge.dungeon.instance.InstanceManager;
import io.github.matejkadlec.realmforge.dungeon.instance.Plot;
import io.github.matejkadlec.realmforge.dungeon.instance.PlotCleanup;
import io.github.matejkadlec.realmforge.dungeon.instance.PlotState;
import io.github.matejkadlec.realmforge.qa.RealmforgeQa;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.ToLongFunction;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.storage.LevelResource;

/**
 * {@code /qa instance bench}: builds and releases plots one after another with one cleanup strategy and reports
 * build and clear times, the server's tick times while the builder works, and how much the instance dimension grows
 * on disk. Runs alternate between a straight and a turned row of rooms, so a reused plot always gets new rooms in
 * new places. Ticked from the end of every server tick; one bench at a time.
 */
public final class InstanceBench {
    private static final int WARMUP_TICKS = 40;
    @Nullable
    private static InstanceBench running;

    private final CommandSourceStack source;
    private final int runs;
    private final int rooms;
    private final PlotCleanup cleanup;
    private Phase phase = Phase.WARMUP;
    private int phaseTicks;
    private int run;
    @Nullable
    private Plot plot;
    private long diskBefore;
    private final List<Long> idleTicks = new ArrayList<>();
    private final List<Long> workTicks = new ArrayList<>();
    private final List<Long> workInstanceTicks = new ArrayList<>();
    private final List<Long> openNanos = new ArrayList<>();
    private final List<BuildReport> builds = new ArrayList<>();
    private final List<BuildReport> clears = new ArrayList<>();

    private enum Phase { WARMUP, OPEN, BUILDING, CLOSING, DONE }

    private InstanceBench(CommandSourceStack source, int runs, int rooms, PlotCleanup cleanup) {
        this.source = source;
        this.runs = runs;
        this.rooms = rooms;
        this.cleanup = cleanup;
    }

    /** Starts a bench, or returns false while one is running. */
    public static boolean start(CommandSourceStack source, int runs, int rooms, PlotCleanup cleanup) {
        if (running != null) {
            return false;
        }
        InstanceManager manager = InstanceManager.get(source.getServer());
        if (manager == null) {
            throw new IllegalStateException("this world has no instance dimension");
        }
        InstanceBench bench = new InstanceBench(source, runs, rooms, cleanup);
        manager.setCleanup(cleanup);
        bench.diskBefore = bench.flushAndMeasureDisk();
        running = bench;
        return true;
    }

    public static boolean isRunning() {
        return running != null;
    }

    /** Called at the end of every server tick. */
    public static void onServerTick(MinecraftServer server) {
        if (running != null) {
            running.tick(server);
        }
    }

    private void tick(MinecraftServer server) {
        InstanceManager manager = InstanceManager.get(server);
        if (manager == null) {
            running = null;
            return;
        }
        int index = server.getTickCount() % 100;
        long tickNanos = server.getTickTimesNanos()[index];
        long[] instanceTimes = server.getTickTime(InstanceDimension.LEVEL);
        long instanceNanos = instanceTimes == null ? 0 : instanceTimes[index];
        phaseTicks++;
        switch (phase) {
            case WARMUP -> {
                idleTicks.add(tickNanos);
                if (phaseTicks >= WARMUP_TICKS) {
                    next(Phase.OPEN);
                }
            }
            case OPEN -> {
                long start = System.nanoTime();
                plot = manager.open(SpikeLayouts.row(rooms, run % 2 == 1), List.of());
                openNanos.add(System.nanoTime() - start);
                next(Phase.BUILDING);
            }
            case BUILDING -> {
                workTicks.add(tickNanos);
                workInstanceTicks.add(instanceNanos);
                if (plot.state() == PlotState.READY) {
                    manager.lastReport(plot.id()).ifPresent(builds::add);
                    manager.release(plot);
                    next(plot.state() == PlotState.CLOSING ? Phase.CLOSING : Phase.OPEN);
                    if (phase == Phase.OPEN) {
                        finishRun();
                    }
                }
            }
            case CLOSING -> {
                workTicks.add(tickNanos);
                workInstanceTicks.add(instanceNanos);
                if (plot.state() == PlotState.FREE) {
                    manager.lastReport(plot.id()).ifPresent(clears::add);
                    next(Phase.OPEN);
                    finishRun();
                }
            }
            case DONE -> {
                running = null;
                report(manager);
            }
        }
    }

    private void finishRun() {
        run++;
        if (run >= runs) {
            next(Phase.DONE);
        }
    }

    private void next(Phase next) {
        phase = next;
        phaseTicks = 0;
    }

    private void report(InstanceManager manager) {
        long diskAfter = flushAndMeasureDisk();
        List<String> lines = new ArrayList<>();
        lines.add(String.format(Locale.ROOT, "instance bench: %d runs of %d rooms, cleanup %s, budget %.1f ms/tick",
                runs, rooms, cleanup.getSerializedName(), InstanceManager.TICK_BUDGET_NANOS / 1e6));
        lines.add("  open (tickets, chunk loads): " + millis(openNanos, Long::longValue));
        lines.add("  build: " + millis(builds, BuildReport::totalNanos)
                + "; ticks " + count(builds, BuildReport::ticks));
        lines.add("    of which clearing the last run: " + millis(builds, BuildReport::clearNanos)
                + "; blocks " + count(builds, BuildReport::blocksCleared));
        for (int room = 0; room < Math.min(rooms, 3); room++) {
            int index = room;
            lines.add("    room " + room + " (" + roomName(room) + "): "
                    + millis(builds, report -> report.roomNanos().get(index)));
        }
        lines.add("    longest unit: " + millis(builds, BuildReport::maxUnitNanos));
        if (!clears.isEmpty()) {
            lines.add("  clear after release: " + millis(clears, BuildReport::totalNanos)
                    + "; ticks " + count(clears, BuildReport::ticks)
                    + "; blocks " + count(clears, BuildReport::blocksCleared)
                    + "; longest unit " + millis(clears, BuildReport::maxUnitNanos));
        }
        lines.add("  server tick idle: " + millis(idleTicks, Long::longValue));
        lines.add("  server tick while working: " + millis(workTicks, Long::longValue));
        lines.add("    of which the instance level: " + millis(workInstanceTicks, Long::longValue));
        lines.add(String.format(Locale.ROOT,
                "  disk (instance dimension): %.1f KiB before, %.1f KiB after, %+.1f KiB per run",
                diskBefore / 1024.0, diskAfter / 1024.0, (diskAfter - diskBefore) / 1024.0 / runs));
        lines.add(String.format(Locale.ROOT, "  plots in use: %d, next new plot id %s", manager.plots().size(),
                manager.plots().stream().mapToInt(Plot::id).max().orElse(-1) + 1));
        for (String line : lines) {
            RealmforgeQa.LOGGER.info(line);
            source.sendSuccess(() -> Component.literal(line), false);
        }
    }

    private String roomName(int room) {
        return room == 0 ? "start" : room % 2 == 1 ? "corridor" : "hall";
    }

    private static <T> String millis(List<T> values, ToLongFunction<T> nanos) {
        return stats(values, nanos, 1e6, " ms", "%.2f");
    }

    private static <T> String count(List<T> values, ToLongFunction<T> count) {
        return stats(values, count, 1, "", "%.0f");
    }

    private static <T> String stats(List<T> values, ToLongFunction<T> value, double divisor, String unit,
            String format) {
        if (values.isEmpty()) {
            return "n/a";
        }
        long[] sorted = values.stream().mapToLong(value).sorted().toArray();
        double mean = Arrays.stream(sorted).average().orElse(0);
        return String.format(Locale.ROOT, "mean " + format + ", median " + format + ", max " + format + "%s (n=%d)",
                mean / divisor, median(sorted) / divisor, sorted[sorted.length - 1] / divisor, unit, sorted.length);
    }

    /** The median of sorted values: the middle one, or the mean of the two middle ones for an even count. */
    public static double median(long[] sorted) {
        int middle = sorted.length / 2;
        return sorted.length % 2 == 1 ? sorted[middle] : (sorted[middle - 1] + sorted[middle]) / 2.0;
    }

    /** Saves the instance level with every chunk written, then sums the sizes of its region and entity files. */
    private long flushAndMeasureDisk() {
        MinecraftServer server = source.getServer();
        ServerLevel level = InstanceDimension.level(server);
        level.save(null, true, false);
        Path folder = DimensionType.getStorageFolder(InstanceDimension.LEVEL, server.getWorldPath(LevelResource.ROOT));
        return size(folder.resolve("region")) + size(folder.resolve("entities")) + size(folder.resolve("data"));
    }

    private static long size(Path folder) {
        if (!Files.isDirectory(folder)) {
            return 0;
        }
        try (Stream<Path> files = Files.list(folder)) {
            return files.filter(Files::isRegularFile).mapToLong(file -> {
                try {
                    return Files.size(file);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }).sum();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
