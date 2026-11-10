package io.github.matejkadlec.realmforge.item;

/**
 * The arithmetic of coins (architecture 13; RLM-284), without world access. The coins form a chain by value, smallest
 * first (copper 1, silver 100, gold 10000, platinum 1000000), and an amount of money is a number of the smallest coin.
 * The pipeline computes the same ({@code split} in {@code tools/realmforge_tools/schema/coin.py}), and a GameTest
 * compares the two.
 */
public final class CoinMath {
    private CoinMath() {
    }

    /**
     * How many coins of each of {@code values} pay {@code amount}, with as few coins as the chain allows: the largest
     * coins first in worth.
     *
     * @param amount the amount in the smallest coin; below 1 is no coins
     * @param values the chain's values, smallest first, each a whole multiple of the one before it
     * @return the count of each coin, in {@code values}' order
     */
    public static long[] split(long amount, long[] values) {
        long[] counts = new long[values.length];
        long rest = Math.max(0, amount);
        for (int index = values.length - 1; index >= 0; index--) {
            counts[index] = rest / values[index];
            rest %= values[index];
        }
        return counts;
    }

    /** How many coins of worth {@code larger} the {@code count} coins of worth {@code smaller} merge into. */
    public static long merges(long count, long smaller, long larger) {
        return Math.max(0, count) / rate(smaller, larger);
    }

    /** How many coins of worth {@code smaller} make one of worth {@code larger}. */
    public static long rate(long smaller, long larger) {
        return larger / smaller;
    }
}
