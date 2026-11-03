package io.github.matejkadlec.realmforge.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;

class CritDistributionTest {
    private static final int ROLLS = 1_000_000;

    @Test
    void theConstantMatchesDotasTable() {
        // The Dota 2 wiki's C for each chance (RLM-526).
        double[][] table = {{0.20, 0.0557}, {0.25, 0.0847}, {0.30, 0.1189}, {0.40, 0.2015}, {0.50, 0.3021}};
        for (double[] row : table) {
            assertEquals(row[1], CritDistribution.constant(row[0]), 0.0001, "C at " + row[0]);
            assertEquals(row[0], CritDistribution.rate(CritDistribution.constant(row[0])), 1e-9,
                    "the long-run rate of C at " + row[0]);
        }
    }

    @Test
    void theRateOfAMillionRollsIsTheCritChance() {
        for (double chance : new double[] {0.05, 0.2, 0.3, 0.5, 0.75, 0.95}) {
            RandomSource random = RandomSource.create(526);
            int misses = 0;
            int crits = 0;
            for (int i = 0; i < ROLLS; i++) {
                if (CritDistribution.rolls(chance, misses, random)) {
                    crits++;
                    misses = 0;
                } else {
                    misses++;
                }
            }
            assertEquals(chance, (double) crits / ROLLS, 0.002, "the crit rate of " + ROLLS + " rolls at " + chance);
        }
    }

    @Test
    void thirtyPercentNeverMissesNineTimesInARow() {
        RandomSource random = RandomSource.create(30);
        int misses = 0;
        int longest = 0;
        for (int i = 0; i < ROLLS; i++) {
            if (CritDistribution.rolls(0.3, misses, random)) {
                misses = 0;
            } else {
                misses++;
                longest = Math.max(longest, misses);
            }
        }
        assertTrue(longest <= 8, "the longest streak of misses at 30 %: " + longest);
        assertTrue(CritDistribution.rolls(0.3, 8, RandomSource.create(0)), "the 9th roll always crits: 9 C ≥ 1");
    }

    @Test
    void noChanceNeverCritsAndAFullOneAlwaysDoes() {
        RandomSource random = RandomSource.create(1);
        for (int misses = 0; misses < 1000; misses += 37) {
            assertFalse(CritDistribution.rolls(0, misses, random), "0 % after " + misses + " misses");
            assertFalse(CritDistribution.rolls(-0.2, misses, random), "a negative chance after " + misses + " misses");
            assertTrue(CritDistribution.rolls(1, misses, random), "100 % after " + misses + " misses");
            assertTrue(CritDistribution.rolls(1.4, misses, random), "140 % after " + misses + " misses");
        }
        assertEquals(0, CritDistribution.constant(0));
        assertEquals(1, CritDistribution.constant(1.5));
    }

    @Test
    void theCacheRoundsToATenthOfAPercent() {
        assertEquals(CritDistribution.constant(0.3), CritDistribution.constant(0.3004), "30.04 % is 30.0 %");
        assertTrue(CritDistribution.constant(0.0001) > 0, "a tiny chance still crits sometimes, as 0.1 %");
        assertEquals(CritDistribution.constant(0.001), CritDistribution.constant(0.0001));
    }

    @Test
    void countersAdvanceTheirOwnKindOnly() {
        CritCounters counters = CritCounters.NONE.after(CritCounters.Kind.ATTACK, false)
                .after(CritCounters.Kind.ATTACK, false).after(CritCounters.Kind.SPELL, false);
        assertEquals(new CritCounters(2, 1), counters, "two attack misses and a spell miss");
        assertEquals(new CritCounters(0, 1), counters.after(CritCounters.Kind.ATTACK, true),
                "an attack crit starts the attack count again, the spell count stays");
        assertEquals(new CritCounters(2, 0), counters.after(CritCounters.Kind.SPELL, true),
                "a spell crit starts the spell count again, the attack count stays");
    }
}
