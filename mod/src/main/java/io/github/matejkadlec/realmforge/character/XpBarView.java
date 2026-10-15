package io.github.matejkadlec.realmforge.character;

import io.github.matejkadlec.realmforge.core.Curve;

/**
 * What the XP bar shows (architecture 10.2, 18): the player's level, the XP they have toward the next one, and
 * whether the level cap holds them. XP stops at the cap, so a capped bar shows full: the player gains nothing until a
 * chapter raises the cap, which starts them at 0 XP toward the next level.
 *
 * @param level the player's level
 * @param xpInLevel the XP gained since the level began
 * @param xpToNext the XP from this level to the next
 * @param capped whether the level cap holds the player at this level
 */
public record XpBarView(int level, long xpInLevel, long xpToNext, boolean capped) {
    /** The view of a player with {@code totalXp} at {@code level} under {@code cap}. */
    public static XpBarView of(Curve curve, long totalXp, int level, int cap) {
        long xpInLevel = Math.max(0, totalXp - XpTable.toReach(curve, level));
        return new XpBarView(level, xpInLevel, XpTable.toNext(curve, level), level >= cap);
    }

    /** How full the bar is, from 0 to 1: full at the cap. */
    public float progress() {
        if (capped) {
            return 1;
        }
        return xpToNext <= 0 ? 0 : (float) Math.min(1.0, (double) xpInLevel / xpToNext);
    }
}
