package io.github.matejkadlec.realmforge.loot;

/**
 * How many coins a mob drops (architecture 13; RLM-286, ADR 0072), without world access: the curve
 * {@code realmforge:coin_drop} at the mob's level, times its rank, times a share between 50 and 150 % picked
 * uniformly, rounded half up and at least 1 of the smallest coin. A mob of rank 0 (the smallest slime) drops nothing.
 * Luck and Looting are no inputs. The pipeline computes the same ({@code coin_drop} in
 * {@code tools/realmforge_tools/schema/coin.py}), and a GameTest compares the two.
 */
public final class CoinDropMath {
    /** The least share of the curve's value a drop is. */
    public static final double MIN_SHARE = 0.5;
    /** The most (never reached: the roll stays below 1). */
    public static final double MAX_SHARE = 1.5;

    private CoinDropMath() {
    }

    /**
     * The drop in the smallest coin.
     *
     * @param base the curve's value at the mob's level
     * @param rank the mob's rank, 1 for a normal mob
     * @param roll a uniform number from 0 up to 1, which picks the share
     */
    public static long amount(double base, double rank, double roll) {
        if (rank <= 0 || base <= 0) {
            return 0;
        }
        double share = MIN_SHARE + roll * (MAX_SHARE - MIN_SHARE);
        return Math.max(1, Math.round(base * rank * share));
    }
}
