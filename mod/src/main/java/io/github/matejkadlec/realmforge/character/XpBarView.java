package io.github.matejkadlec.realmforge.character;

import io.github.matejkadlec.realmforge.core.Curve;

/**
 * What the XP bar shows (architecture 10.2, 18): the player's level, the XP they have toward the next one, and
 * whether the level cap holds them. At the cap XP keeps counting, so the bar fills toward the next level and stays
 * full once the player has its XP; it then shows the banked XP that a raised cap turns into levels.
 *
 * @param level the player's level
 * @param xpInLevel the XP gained since the level began, which may exceed {@code xpToNext} at the cap
 * @param xpToNext the XP from this level to the next
 * @param capped whether the level cap holds the player at this level
 */
public record XpBarView(int level, long xpInLevel, long xpToNext, boolean capped) {
    /** The view of a player with {@code totalXp} at {@code level} under {@code cap}. */
    public static XpBarView of(Curve curve, long totalXp, int level, int cap) {
        long xpInLevel = Math.max(0, totalXp - XpTable.toReach(curve, level));
        return new XpBarView(level, xpInLevel, XpTable.toNext(curve, level), level >= cap);
    }

    /** How full the bar is, from 0 to 1. */
    public float progress() {
        return xpToNext <= 0 ? 0 : (float) Math.min(1.0, (double) xpInLevel / xpToNext);
    }
}
