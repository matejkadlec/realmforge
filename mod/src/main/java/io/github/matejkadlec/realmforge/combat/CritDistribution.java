package io.github.matejkadlec.realmforge.combat;

import net.minecraft.util.RandomSource;

/**
 * Crit bad-luck protection, Dota 2's pseudo-random distribution (Matěj, 2026-10-30, RLM-526; architecture 9.1): the
 * N-th roll since the last crit succeeds with C × N, and a crit starts the count again. C is chosen so the long-run
 * rate equals the crit chance P, so a player's displayed crit chance stays true while long streaks of misses vanish:
 * at 30 % (C = 0.1189) the 9th roll is always a crit, and 7 misses in a row fall from 8.2 % (plain rolls) to 0.44 %.
 * Without world access, so JUnit covers it; {@link CritCounters} keeps each player's N.
 */
public final class CritDistribution {
    /** C is cached per this share of crit chance: 0.1 % steps. */
    static final int STEPS = 1000;

    private static final double[] CONSTANTS = new double[STEPS + 1];

    private CritDistribution() {
    }

    /**
     * Whether a roll on {@code chance} crits after {@code misses} rolls without one since the last crit: with
     * C(chance) × (misses + 1). A chance of 0 or less never crits, 1 or more always does.
     */
    public static boolean rolls(double chance, int misses, RandomSource random) {
        if (!(chance > 0)) {
            return false;
        }
        if (chance >= 1) {
            return true;
        }
        return random.nextDouble() < constant(chance) * (misses + 1);
    }

    /**
     * C for {@code chance}, from the cache: the chance rounded to 0.1 % (a chance above 0 at least 0.1 %), worked out
     * the first time it is asked for. 0 for a chance of 0 or less, 1 for 1 or more.
     */
    public static double constant(double chance) {
        if (!(chance > 0)) {
            return 0;
        }
        if (chance >= 1) {
            return 1;
        }
        int step = (int) Math.max(1, Math.min(STEPS, Math.round(chance * STEPS)));
        synchronized (CONSTANTS) {
            if (CONSTANTS[step] == 0) {
                CONSTANTS[step] = constantOf((double) step / STEPS);
            }
            return CONSTANTS[step];
        }
    }

    /**
     * C for {@code chance} between 0 and 1, by bisection on {@link #rate} as Dota's table is derived: the C whose
     * long-run rate is {@code chance}, to the precision of a double.
     */
    static double constantOf(double chance) {
        double lower = 0;
        double upper = chance;
        double middle = upper;
        double previous = -1;
        while (true) {
            middle = (lower + upper) / 2;
            double rate = rate(middle);
            if (rate == previous) {
                return middle;
            }
            if (rate > chance) {
                upper = middle;
            } else {
                lower = middle;
            }
            previous = rate;
        }
    }

    /**
     * The long-run crit rate of C: 1 over the expected number of rolls to a crit, where the N-th roll crits with
     * min(1, C × N) once the N − 1 before it missed.
     */
    static double rate(double constant) {
        double critBefore = 0;
        double expectedRolls = 0;
        int lastRoll = (int) Math.ceil(1 / constant);
        for (int n = 1; n <= lastRoll; n++) {
            double critHere = Math.min(1, n * constant) * (1 - critBefore);
            critBefore += critHere;
            expectedRolls += n * critHere;
        }
        return 1 / expectedRolls;
    }
}
