package io.github.matejkadlec.realmforge.loot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CoinDropMathTest {
    @Test
    void aDropIsBetweenHalfAndOneAndAHalfTimesTheBase() {
        assertEquals(5_000, CoinDropMath.amount(10_000, 1, 0));
        assertEquals(10_000, CoinDropMath.amount(10_000, 1, 0.5));
        assertEquals(14_990, CoinDropMath.amount(10_000, 1, 0.999));
        for (int step = 0; step < 1000; step++) {
            long amount = CoinDropMath.amount(10_000, 1, step / 1000.0);
            assertTrue(amount >= 5_000 && amount < 15_000, "roll " + step + " gave " + amount);
        }
    }

    @Test
    void theRankMultipliesIt() {
        assertEquals(20_000, CoinDropMath.amount(10_000, 2, 0.5));
        assertEquals(200_000, CoinDropMath.amount(10_000, 20, 0.5));
        assertEquals(1_000, CoinDropMath.amount(10_000, 0.1, 0.5));
    }

    @Test
    void aLevelOneMobDropsOneCopperWhateverTheRoll() {
        assertEquals(1, CoinDropMath.amount(1.0, 1, 0));
        assertEquals(1, CoinDropMath.amount(1.0, 1, 0.5));
        assertEquals(1, CoinDropMath.amount(1.0, 1, 0.999));
    }

    @Test
    void aDropIsAtLeastOneCopper() {
        assertEquals(1, CoinDropMath.amount(1.0, 0.1, 0));
        assertEquals(1, CoinDropMath.amount(0.01, 1, 0.999));
    }

    @Test
    void aMobOfNoRankOrALevelWithoutABaseDropsNothing() {
        assertEquals(0, CoinDropMath.amount(10_000, 0, 0.5));
        assertEquals(0, CoinDropMath.amount(10_000, -1, 0.5));
        assertEquals(0, CoinDropMath.amount(0, 1, 0.5));
    }

    @Test
    void halvesRoundUp() {
        assertEquals(3, CoinDropMath.amount(5, 1, 0));
        assertEquals(4, CoinDropMath.amount(7, 1, 0));
    }
}
