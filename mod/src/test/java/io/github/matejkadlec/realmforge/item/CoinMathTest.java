package io.github.matejkadlec.realmforge.item;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class CoinMathTest {
    /** Copper, silver, gold and platinum, each 100 of the coin below. */
    private static final long[] CHAIN = {1, 100, 10_000, 1_000_000};

    @Test
    void anAmountIsPaidInTheLargestCoins() {
        assertArrayEquals(new long[] {56, 34, 12, 0}, CoinMath.split(123_456, CHAIN));
        assertArrayEquals(new long[] {21, 43, 65, 7}, CoinMath.split(7_654_321, CHAIN));
        assertArrayEquals(new long[] {0, 0, 1, 0}, CoinMath.split(10_000, CHAIN));
        assertArrayEquals(new long[] {99, 99, 0, 0}, CoinMath.split(9_999, CHAIN));
    }

    @Test
    void theLargestCoinTakesWhateverIsLeftAbove() {
        assertArrayEquals(new long[] {0, 0, 0, 150}, CoinMath.split(150_000_000, CHAIN));
    }

    @Test
    void nothingAndLessThanNothingAreNoCoins() {
        assertArrayEquals(new long[] {0, 0, 0, 0}, CoinMath.split(0, CHAIN));
        assertArrayEquals(new long[] {0, 0, 0, 0}, CoinMath.split(-5, CHAIN));
    }

    @Test
    void aChainOfOneCoinPaysEverythingInIt() {
        assertArrayEquals(new long[] {250}, CoinMath.split(250, new long[] {1}));
    }

    @Test
    void onlyFullRatesMerge() {
        assertEquals(100, CoinMath.rate(1, 100));
        assertEquals(100, CoinMath.rate(10_000, 1_000_000));
        assertEquals(0, CoinMath.merges(99, 1, 100));
        assertEquals(1, CoinMath.merges(100, 1, 100));
        assertEquals(2, CoinMath.merges(250, 1, 100));
        assertEquals(0, CoinMath.merges(-3, 1, 100));
    }
}
