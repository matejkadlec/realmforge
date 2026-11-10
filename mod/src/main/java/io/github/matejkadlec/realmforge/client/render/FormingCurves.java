package io.github.matejkadlec.realmforge.client.render;

import java.util.Arrays;
import java.util.Comparator;
import net.minecraft.util.Mth;

/**
 * How a projectile's model shows while it forms in a caster's hands (RLM-640, ADR 0073 decision 7,
 * docs/design/spell-visuals.md "Forming during a cast"), as plain math, so JUnit covers it
 * ({@code FormingCurvesTest}).
 *
 * <ul>
 *   <li><b>{@code grow}:</b> the model's cubes appear in the order of their distance from its heart
 *   ({@link #ranks}), each growing from nothing to its size about its own middle over a short ramp
 *   ({@link #cube}), so the model grows outward and is whole when the cast ends.</li>
 *   <li><b>{@code storm}</b>, and a shape that has no cubes (the orb): the whole model scales up
 *   ({@link #whole}).</li>
 *   <li><b>An interrupted cast's</b> model fades away over {@link #FADE_TICKS} ({@link #fade}).</li>
 *   <li><b>A projectile leaves the hands</b> over {@link #LEAVE_TICKS} ({@link #left}).</li>
 * </ul>
 */
final class FormingCurves {
    /** A model that scales up as a whole starts at this share of its size. */
    static final float WHOLE_START = 0.15F;
    /** The share of the cast over which one cube grows from nothing to its size, so none pops in. */
    static final float RAMP = 0.2F;
    /** How long an interrupted cast's model takes to fade away, in ticks. */
    static final float FADE_TICKS = 8;
    /** How many ticks a player's projectile takes to get from the hands onto its own path, on screen. */
    static final float LEAVE_TICKS = 4;

    private FormingCurves() {
    }

    /** The size of a model that scales up as a whole, as a share of its full size, at {@code progress} of the cast. */
    static float whole(float progress) {
        return WHOLE_START + (1 - WHOLE_START) * Mth.clamp(progress, 0.0F, 1.0F);
    }

    /**
     * The size of the cube of {@code rank} (0 is the one nearest the heart) among {@code count}, as a share of its
     * full size, at {@code progress} of the cast: 0 until its turn, then a smooth ramp to 1. The first cube starts
     * with the cast, the last one is whole exactly at its end.
     */
    static float cube(int rank, int count, float progress) {
        float start = count <= 1 ? 0 : rank / (float) (count - 1) * (1 - RAMP);
        float grown = Mth.clamp((Mth.clamp(progress, 0.0F, 1.0F) - start) / RAMP, 0.0F, 1.0F);
        return grown * grown * (3 - 2 * grown);
    }

    /**
     * The rank of the newest cube that shows at {@code progress}: where the growing edge is. The first cube while
     * none shows yet.
     */
    static int edge(int count, float progress) {
        for (int rank = count - 1; rank > 0; rank--) {
            if (cube(rank, count, progress) > 0) {
                return rank;
            }
        }
        return 0;
    }

    /**
     * The order the cubes appear in: the indices of {@code distances} (each cube's distance from the model's heart)
     * from the nearest to the farthest, equal ones in their own order.
     */
    static int[] ranks(double[] distances) {
        Integer[] order = new Integer[distances.length];
        Arrays.setAll(order, index -> index);
        Arrays.sort(order, Comparator.comparingDouble(index -> distances[index]));
        return Arrays.stream(order).mapToInt(Integer::intValue).toArray();
    }

    /**
     * How far a projectile has got from the caster's hands onto its own path {@code flownTicks} after its launch: 0
     * at the hands, 1 on its path from {@link #LEAVE_TICKS} on, eased at both ends so it neither jumps off the hands
     * nor snaps onto the path.
     */
    static float left(float flownTicks) {
        float share = Mth.clamp(flownTicks / LEAVE_TICKS, 0.0F, 1.0F);
        return share * share * (3 - 2 * share);
    }

    /** How opaque an interrupted cast's model still is {@code ticks} after the interrupt: 1 at once, 0 at the end. */
    static float fade(float ticks) {
        return Mth.clamp(1 - ticks / FADE_TICKS, 0.0F, 1.0F);
    }

    /**
     * Whether a forming cast's swirl spawns its particles this client tick: only while the game runs. Paused (a
     * singleplayer pause menu) or with the tick rate frozen ({@code /tick freeze}), the particle engine ages nothing
     * and the cast does not move on, so every particle spawned then would wait and appear at once.
     */
    static boolean swirls(boolean paused, boolean ticksRunNormally) {
        return !paused && ticksRunNormally;
    }
}
