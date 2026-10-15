package io.github.matejkadlec.realmforge.character;

import io.github.matejkadlec.realmforge.core.Curve;

/**
 * The character XP a kill gives (architecture 10.2): the curve {@code realmforge:kill_xp} at the victim's level, times
 * the victim's kill XP multiplier (its rank, {@link KillXpMultiplier}), times {@code realmforge:kill_xp_below_player}
 * at the levels the victim is below the player (or {@code realmforge:kill_xp_above_player} at the levels it is
 * above), times the player's XP multiplier, in that order, rounded half up and never negative. Mobs far below the
 * player give nothing. The pipeline computes the same ({@code tools/realmforge_tools/schema/leveling.py}), and a
 * GameTest compares the two.
 */
public final class KillXp {
    private KillXp() {
    }

    /**
     * The XP a player at {@code playerLevel} gets for killing a mob at {@code victimLevel}.
     *
     * @param rank the victim's kill XP multiplier, 1 for a normal mob
     * @param multiplier the player's XP multiplier, 1 for 100 %
     */
    public static long of(Curve base, Curve below, Curve above, int victimLevel, int playerLevel, double rank,
            double multiplier) {
        int difference = victimLevel - playerLevel;
        double factor = difference < 0 ? below.valueAt(-difference) : above.valueAt(difference);
        return Math.max(0, Math.round(base.valueAt(victimLevel) * rank * factor * multiplier));
    }
}
