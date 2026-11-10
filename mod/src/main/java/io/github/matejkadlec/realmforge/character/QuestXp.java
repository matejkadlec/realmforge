package io.github.matejkadlec.realmforge.character;

import io.github.matejkadlec.realmforge.core.Curve;

/**
 * The character XP a quest pays (architecture 10.2, 16; RLM-189): the quest's amount, which the pipeline computes from
 * its level and type, times the curve {@code realmforge:quest_xp_below_player} at the levels the quest is below the
 * player (all of it up to 4 below, then 75, 50 and 25 %, and 10 % from 8 below, so a quest never gives nothing), times
 * the player's XP multiplier, in that order, rounded half up and never negative. A quest at or above the player's
 * level pays all of it. The pipeline computes the same ({@code tools/realmforge_tools/schema/leveling.py}), and a
 * GameTest compares the two.
 */
public final class QuestXp {
    private QuestXp() {
    }

    /**
     * The XP a player at {@code playerLevel} gets from a quest of {@code questLevel} that gives {@code amount}.
     *
     * @param multiplier the player's XP multiplier, 1 for 100 %
     */
    public static long of(Curve below, long amount, int questLevel, int playerLevel, double multiplier) {
        double factor = below.valueAt(Math.max(0, playerLevel - questLevel));
        return Math.max(0, Math.round(amount * factor * multiplier));
    }
}
