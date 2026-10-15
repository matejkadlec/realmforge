package io.github.matejkadlec.realmforge.character;

import io.github.matejkadlec.realmforge.core.Curve;

/**
 * Character XP and levels (architecture 10.2), from the curve {@code realmforge:xp_to_next_level}: its value at a
 * level, rounded half up and at least 1, is the XP from that level to the next. A player's level is the highest level
 * whose XP they have, at most their level cap. XP stops at the cap (Matěj, 2026-10-15): a player holds at most the XP
 * of their cap's level ({@link #capped}), so raising the cap starts them at 0 XP toward the next level. The pipeline
 * computes the same ({@code tools/realmforge_tools/schema/leveling.py}), and a GameTest compares the two.
 */
public final class XpTable {
    private XpTable() {
    }

    /** The XP from {@code level} to the next level, at least 1. */
    public static long toNext(Curve curve, int level) {
        return Math.max(1, Math.round(curve.valueAt(level)));
    }

    /** The total XP a player needs for {@code level}: 0 for level 1. */
    public static long toReach(Curve curve, int level) {
        long total = 0;
        for (int below = 1; below < level; below++) {
            total = plus(total, toNext(curve, below));
        }
        return total;
    }

    /** The level of a player with {@code totalXp}, at least 1 and at most {@code cap}. */
    public static int levelFor(Curve curve, long totalXp, int cap) {
        int level = 1;
        long needed = 0;
        while (level < cap) {
            needed = plus(needed, toNext(curve, level));
            if (totalXp < needed) {
                break;
            }
            level++;
        }
        return level;
    }

    /**
     * The XP a player under {@code cap} holds of {@code totalXp}: all of it, at most the XP of the cap's level. What lies
     * beyond is lost, not banked.
     */
    public static long capped(Curve curve, long totalXp, int cap) {
        return Math.min(totalXp, toReach(curve, cap));
    }

    /** {@code a + b}, saturating at {@code Long.MAX_VALUE} instead of overflowing. */
    static long plus(long a, long b) {
        return a > Long.MAX_VALUE - b ? Long.MAX_VALUE : a + b;
    }
}
