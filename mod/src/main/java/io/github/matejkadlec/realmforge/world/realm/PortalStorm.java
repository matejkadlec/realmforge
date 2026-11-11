package io.github.matejkadlec.realmforge.world.realm;

import net.minecraft.util.ARGB;
import net.minecraft.util.RandomSource;

/**
 * A realm portal's storm blink (RLM-300; Matěj, 2026-10-20: "occasionally randomly blink the portal color, to a
 * darker gray and back and again to darker and back, kind of like storm effect"): now and then, at random, the whole
 * opening darkens twice in a row. The server picks the moment, so every player sees the same blink and every block
 * of the portal blinks together: the sealstone sends a block event ({@link SealstoneBlockEntity}), and each client
 * darkens the portal's tint over the ticks below.
 */
public final class PortalStorm {
    /** On average one blink every 15 seconds: each tick starts one with a chance of 1 in this. */
    public static final int MEAN_TICKS = 300;
    /** How long the portal stays dark, each of the two times. */
    public static final int DARK_TICKS = 3;
    /** How long it is back to its colour in between. */
    public static final int GAP_TICKS = 3;
    /** A whole blink: dark, back, dark. */
    public static final int LENGTH = 2 * DARK_TICKS + GAP_TICKS;
    /** The dark grey the colour is pulled towards, and how far. */
    private static final int DARK_GREY = 0xFF2C2C32;
    private static final float DARKNESS = 0.6F;

    private PortalStorm() {
    }

    /** Whether a blink starts this tick. */
    public static boolean starts(RandomSource random) {
        return random.nextInt(MEAN_TICKS) == 0;
    }

    /** Whether the portal is dark {@code ticks} after a blink began: its first and its last {@link #DARK_TICKS}. */
    public static boolean dark(long ticks) {
        return ticks >= 0 && ticks < DARK_TICKS || ticks >= DARK_TICKS + GAP_TICKS && ticks < LENGTH;
    }

    /** Whether a blink that began {@code ticks} ago is over. */
    public static boolean over(long ticks) {
        return ticks < 0 || ticks >= LENGTH;
    }

    /** {@code colour} (ARGB) as the portal shows it while dark: pulled towards a dark grey, its alpha kept. */
    public static int darken(int colour) {
        return ARGB.color(ARGB.alpha(colour), ARGB.srgbLerp(DARKNESS, colour, DARK_GREY));
    }
}
