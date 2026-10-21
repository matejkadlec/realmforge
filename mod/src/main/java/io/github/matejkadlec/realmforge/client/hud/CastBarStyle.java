package io.github.matejkadlec.realmforge.client.hud;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * How the cast bar looks (C3b, RLM-25; Matěj's playtest of 2026-10-21, after WoW's casting bar), as plain arithmetic
 * for JUnit: the fill is the school's colour, lighter toward the top and darker toward the bottom like a lit tube, its
 * leading edge almost white, and the time reads "elapsed / total" in seconds.
 */
final class CastBarStyle {
    /** How much lighter the top row is: a share of the way to white. */
    private static final float TOP_LIGHT = 0.55F;
    /** How much darker the bottom row is: a share of the way to black. */
    private static final float BOTTOM_DARK = 0.5F;
    /** Where the plain colour band begins and ends, as shares of the bar's height. */
    private static final float BAND_START = 0.35F;
    private static final float BAND_END = 0.6F;
    /** The leading edge's columns, from the edge inward: how close each comes to white. */
    static final float[] EDGE_LIGHT = {0.85F, 0.55F, 0.25F};

    private CastBarStyle() {
    }

    /**
     * How row {@code row} of a fill {@code rows} high is shaded: above 0 a share of the way to white, below 0 a share of
     * the way to black. The top rows lighten in steps, a band in the middle keeps the colour, the bottom rows darken.
     */
    static float rowShade(int row, int rows) {
        float t = (row + 0.5F) / rows;
        if (t < BAND_START) {
            float up = 1 - t / BAND_START;
            return TOP_LIGHT * up * up;
        }
        if (t <= BAND_END) {
            return 0;
        }
        return -BOTTOM_DARK * (t - BAND_END) / (1 - BAND_END);
    }

    /** {@code rgb} lightened toward white ({@code amount} above 0) or darkened toward black (below 0), opaque. */
    static int shade(int rgb, float amount) {
        int target = amount >= 0 ? 0xFF : 0;
        float share = Math.min(1, Math.abs(amount));
        int red = channel(rgb >> 16 & 0xFF, target, share);
        int green = channel(rgb >> 8 & 0xFF, target, share);
        int blue = channel(rgb & 0xFF, target, share);
        return 0xFF000000 | red << 16 | green << 8 | blue;
    }

    /** "0.9 / 2.0": the seconds elapsed, rounded down, and the cast's whole length, both with one decimal. */
    static String time(int elapsedTicks, int totalTicks) {
        int elapsed = Math.clamp(elapsedTicks, 0, totalTicks);
        return tenths(BigDecimal.valueOf(elapsed).divide(BigDecimal.valueOf(20), 1, RoundingMode.DOWN)) + " / "
                + tenths(BigDecimal.valueOf(totalTicks).divide(BigDecimal.valueOf(20), 1, RoundingMode.HALF_UP));
    }

    private static String tenths(BigDecimal seconds) {
        return seconds.setScale(1, RoundingMode.UNNECESSARY).toPlainString();
    }

    private static int channel(int value, int target, float share) {
        return Math.round(value + (target - value) * share);
    }
}
