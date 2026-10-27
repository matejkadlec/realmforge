package io.github.matejkadlec.realmforge.dungeon.instance;

import io.github.matejkadlec.realmforge.stats.EntityLevel;
import java.util.stream.IntStream;
import net.minecraft.util.RandomSource;

/**
 * The levels of a dungeon run (RLM-240, section 2; RLM-459; architecture 14.1): the run's level, set when it opens from
 * its dungeon type's base and its party's levels, and the level a mob in it gets by its rank.
 */
public final class RunLevels {
    /** Where a mob stands in a dungeon, which decides how far below the run's level it is. */
    public enum Rank {
        /** A boss: the run's level. */
        BOSS(0, 0),
        /** A miniboss: one below it. */
        MINIBOSS(1, 1),
        /** Every other mob: two or three below it, each as likely. */
        OTHER(2, 3);

        private final int fewestBelow;
        private final int mostBelow;

        Rank(int fewestBelow, int mostBelow) {
            this.fewestBelow = fewestBelow;
            this.mostBelow = mostBelow;
        }
    }

    private RunLevels() {
    }

    /**
     * The run's level: the dungeon type's base raised to the highest of the party's levels, as the base is a minimum
     * (a level 50 player meets the level 18 boss at 50). A party none of whose levels is known runs at the base.
     */
    public static int runLevel(int base, IntStream partyLevels) {
        return Math.max(base, partyLevels.max().orElse(base));
    }

    /** The level of a mob of {@code rank} in a run at {@code runLevel}, never below {@link EntityLevel#MIN}. */
    public static int mobLevel(int runLevel, Rank rank, RandomSource random) {
        int below = rank.fewestBelow + random.nextInt(rank.mostBelow - rank.fewestBelow + 1);
        return Math.max(EntityLevel.MIN, runLevel - below);
    }
}
