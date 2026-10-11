package io.github.matejkadlec.realmforge.dungeon.instance;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Timing of one finished job on a plot (a build, or the clearing of a closing plot), measured inside the builder.
 * Kept in memory only; a job that resumed after a restart reports only the work done since.
 *
 * @param clearNanos time spent clearing old blocks and entities
 * @param placeNanos time spent placing rooms
 * @param roomNanos time spent placing each room, in layout order
 * @param maxUnitNanos the longest single unit, which bounds how far one tick can overrun the budget
 * @param blocksCleared blocks that were not air and were set to air
 */
public record BuildReport(int plot, PlotState job, int units, int ticks, long clearNanos, long placeNanos,
        List<Long> roomNanos, long maxUnitNanos, long blocksCleared) {

    public long totalNanos() {
        return clearNanos + placeNanos;
    }

    @Override
    public String toString() {
        return String.format(Locale.ROOT,
                "plot %d %s: %d units in %d ticks, %.2f ms (clear %.2f ms, %d blocks; place %.2f ms, rooms [%s] ms),"
                        + " longest unit %.2f ms",
                plot, job.getSerializedName(), units, ticks, totalNanos() / 1e6, clearNanos / 1e6, blocksCleared,
                placeNanos / 1e6,
                roomNanos.stream().map(nanos -> String.format(Locale.ROOT, "%.2f", nanos / 1e6))
                        .collect(Collectors.joining(", ")),
                maxUnitNanos / 1e6);
    }

    /** Accumulates a job's timings while it runs. */
    static final class Recorder {
        private final int plot;
        private final PlotState job;
        private final int units;
        private final long[] roomNanos;
        private int ticks;
        private long lastTick = Long.MIN_VALUE;
        private long clearNanos;
        private long placeNanos;
        private long maxUnitNanos;
        private long blocksCleared;

        Recorder(int plot, PlotState job, int units, int rooms) {
            this.plot = plot;
            this.job = job;
            this.units = units;
            this.roomNanos = new long[rooms];
        }

        /** Records one unit; {@code room} is the placed room's index, or -1 for clearing. */
        void unit(long gameTick, int room, long nanos, long cleared) {
            if (gameTick != lastTick) {
                ticks++;
                lastTick = gameTick;
            }
            if (room >= 0) {
                placeNanos += nanos;
                roomNanos[room] += nanos;
            } else {
                clearNanos += nanos;
            }
            maxUnitNanos = Math.max(maxUnitNanos, nanos);
            blocksCleared += cleared;
        }

        BuildReport finish() {
            return new BuildReport(plot, job, units, ticks, clearNanos, placeNanos,
                    Arrays.stream(roomNanos).boxed().toList(), maxUnitNanos, blocksCleared);
        }
    }
}
