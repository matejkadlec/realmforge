package io.github.matejkadlec.realmforge.client.hud;

import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;

/**
 * What the magic shield's bar ({@link MagicShieldHud}) shows, without client classes, so JUnit covers it (Matěj,
 * 2026-10-14). The bar is the bottom row of hearts with a 1-pixel border, and every
 * {@value #POINTS_PER_TIER} points of shield fill it once, in a new colour, layered over the tier below, as Overflowing
 * Bars layers hearts: 25 points show blue over the left half and ocean over the rest.
 */
final class MagicShieldBar {
    /** A row of ten hearts: nine pixels each, eight apart. */
    static final int ROW_WIDTH = 81;
    static final int ROW_HEIGHT = 9;
    /** The pixels inside the border, across. */
    static final int INNER_WIDTH = ROW_WIDTH - 2;
    /** The shield points that fill the bar once. */
    static final int POINTS_PER_TIER = 10;
    /**
     * The tiers' colours, RGB, from the edge of the RGB colour wheel: cyan, ocean, blue, violet, magenta, raspberry.
     * The last one covers everything from 50 points up.
     */
    static final int[] TIER_COLORS = {0x00FFFF, 0x007DFF, 0x0000FF, 0x7D00FF, 0xFF00FF, 0xFF007D};
    /** The fill is translucent, so the hearts show through: about 45 %. */
    private static final int FILL_ALPHA = 0x73;

    private MagicShieldBar() {
    }

    /**
     * The tier the top layer of {@code points} is in: 0 (cyan) for up to 10 points, 1 (ocean) for up to 20, and so
     * on, the last (raspberry) for everything above 50; -1 for an empty shield.
     */
    static int tier(float points) {
        if (points <= 0) {
            return -1;
        }
        return Math.min(Mth.ceil(points / POINTS_PER_TIER) - 1, TIER_COLORS.length - 1);
    }

    /**
     * How many pixels of the {@link #INNER_WIDTH} the top layer covers, from the left, rounded up so any points show;
     * the tier below covers the rest. The last tier fills the bar once it has a full tier's points, and stays full
     * above that. 0 for an empty shield.
     */
    static int topWidth(float points) {
        int tier = tier(points);
        if (tier < 0) {
            return 0;
        }
        float share = (points - tier * POINTS_PER_TIER) / POINTS_PER_TIER;
        return Mth.clamp(Mth.ceil(INNER_WIDTH * share), 1, INNER_WIDTH);
    }

    /** The translucent fill of a tier, ARGB. */
    static int fill(int tier) {
        return ARGB.color(FILL_ALPHA, TIER_COLORS[tier]);
    }

    /** The border of a tier, ARGB: its colour halfway to white, opaque, so it stands out against the hearts. */
    static int border(int tier) {
        int color = TIER_COLORS[tier];
        return ARGB.color((ARGB.red(color) + 255) / 2, (ARGB.green(color) + 255) / 2, (ARGB.blue(color) + 255) / 2);
    }
}
