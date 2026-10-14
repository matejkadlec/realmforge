package io.github.matejkadlec.realmforge.client.hud;

import net.minecraft.util.Mth;

/** The size of the magic shield's bar ({@link MagicShieldHud}), without client classes, so JUnit covers it. */
final class MagicShieldBar {
    /** A row of ten hearts: nine pixels each, eight apart. */
    static final int ROW_WIDTH = 81;

    private MagicShieldBar() {
    }

    /**
     * The width of the bar for {@code points} of a {@code max} pool, in GUI pixels: the share left of a row of hearts,
     * rounded up, so any points left show at least one pixel; 0 for an empty pool or none.
     */
    static int width(float points, double max) {
        if (points <= 0 || max <= 0) {
            return 0;
        }
        return Mth.clamp(Mth.ceil(ROW_WIDTH * points / max), 1, ROW_WIDTH);
    }
}
