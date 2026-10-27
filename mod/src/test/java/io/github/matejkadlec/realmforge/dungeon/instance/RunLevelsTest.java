package io.github.matejkadlec.realmforge.dungeon.instance;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.matejkadlec.realmforge.dungeon.instance.RunLevels.Rank;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.IntStream;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;

/** RLM-459 (RLM-240, section 2): a run's level from its base and its party, and its mobs' levels by rank. */
class RunLevelsTest {
    /** Rolls per check: the chance that 200 rolls of two equally likely levels all land on one is 2^-199. */
    private static final int ROLLS = 200;

    @Test
    void theBaseIsAMinimumThePartysHighestLevelRaises() {
        assertEquals(50, RunLevels.runLevel(18, IntStream.of(50)), "the Undervault boss is 50 for a level 50 player");
        assertEquals(25, RunLevels.runLevel(10, IntStream.of(12, 25, 3)), "the highest party member decides");
        assertEquals(10, RunLevels.runLevel(10, IntStream.of(3, 7)), "a party below the base runs at the base");
        assertEquals(10, RunLevels.runLevel(10, IntStream.of(10)));
        assertEquals(10, RunLevels.runLevel(10, IntStream.empty()), "no member online: the base");
    }

    @Test
    void aBossIsAtTheRunsLevelAndAMinibossOneBelow() {
        RandomSource random = RandomSource.create(7);
        assertEquals(Set.of(40), roll(40, Rank.BOSS, random));
        assertEquals(Set.of(39), roll(40, Rank.MINIBOSS, random));
    }

    @Test
    void everyOtherMobIsTwoOrThreeBelowTheRunsLevel() {
        assertEquals(Set.of(37, 38), roll(40, Rank.OTHER, RandomSource.create(7)),
                "boss 40, trash 37 to 38 (RLM-240's example)");
    }

    @Test
    void noMobGoesBelowLevelOne() {
        RandomSource random = RandomSource.create(7);
        assertEquals(Set.of(1), roll(1, Rank.OTHER, random));
        assertEquals(Set.of(1), roll(2, Rank.OTHER, random));
        assertEquals(Set.of(1), roll(1, Rank.MINIBOSS, random));
        assertEquals(Set.of(1, 2), roll(4, Rank.OTHER, random));
    }

    private static Set<Integer> roll(int runLevel, Rank rank, RandomSource random) {
        Set<Integer> seen = new TreeSet<>();
        for (int i = 0; i < ROLLS; i++) {
            seen.add(RunLevels.mobLevel(runLevel, rank, random));
        }
        return seen;
    }
}
