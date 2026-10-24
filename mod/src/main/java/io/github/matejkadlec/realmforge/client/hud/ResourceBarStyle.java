package io.github.matejkadlec.realmforge.client.hud;

import io.github.matejkadlec.realmforge.Realmforge;
import java.util.Map;
import net.minecraft.resources.Identifier;

/**
 * How the resource bar looks (RLM-400; Matěj, 2026-10-24), as plain arithmetic for JUnit: each resource type's colours
 * in one table ({@link #COLORS}), a fill shaded like a tube lit from the front (darker at the top and bottom edges,
 * lighter in the middle, with {@link CastBarStyle#shade}), how many pixels a fill covers, and the fill's ease toward a
 * new value.
 */
final class ResourceBarStyle {
    /**
     * A resource's colours, RGB: the fill, and the darker line at its leading edge.
     *
     * @param fill the fill's colour before shading
     * @param edge the 1 px line at the fill's leading edge
     */
    record Colors(int fill, int edge) {
    }

    /**
     * Every resource type's colours, by the type's id: a new resource (energy, later) is one more entry (Matěj,
     * 2026-10-24).
     */
    static final Map<Identifier, Colors> COLORS = Map.of(
            Realmforge.id("mana"), new Colors(0x1E90FF, 0x0B3D85),
            Realmforge.id("rage"), new Colors(0xF5241B, 0x780C08));
    /** A resource without an entry yet: gray. */
    static final Colors FALLBACK = new Colors(0x9A9A9A, 0x4A4A4A);
    static final int BORDER = 0x000000;
    /** The empty part: dark gray. */
    static final int EMPTY = 0x2B2B2B;
    static final int TEXT = 0xFFFFFF;
    /** How long the fill takes to reach a new value. */
    static final long ANIMATION_MILLIS = 150;
    /** How much lighter the middle row is: a share of the way to white. */
    private static final float MIDDLE_LIGHT = 0.3F;
    /** How much darker the top and bottom rows are: a share of the way to black. */
    private static final float EDGE_DARK = 0.45F;

    private ResourceBarStyle() {
    }

    /** The colours of the resource type {@code type}, gray for one without an entry. */
    static Colors colors(Identifier type) {
        return COLORS.getOrDefault(type, FALLBACK);
    }

    /**
     * How row {@code row} of a fill {@code rows} high is shaded, for {@link CastBarStyle#shade}: lightest in the
     * middle, darkening toward the top and the bottom edge alike.
     */
    static float rowShade(int row, int rows) {
        float fromMiddle = Math.abs((row + 0.5F) / rows - 0.5F) * 2;
        return MIDDLE_LIGHT * (1 - fromMiddle) * (1 - fromMiddle) - EDGE_DARK * fromMiddle * fromMiddle;
    }

    /**
     * How many of {@code width} pixels a fill of {@code fraction} covers: rounded, but at least one while there is any
     * resource and one short of full until the pool is full, so a sliver and a nearly full pool both read as such.
     */
    static int fillWidth(double fraction, int width) {
        if (fraction <= 0) {
            return 0;
        }
        if (fraction >= 1) {
            return width;
        }
        return (int) Math.clamp(Math.round(fraction * width), 1, width - 1);
    }

    /**
     * Where the fill shows {@code elapsedMillis} after it began to move from {@code from} to {@code to}: easing out over
     * {@value #ANIMATION_MILLIS} ms, then at {@code to}.
     */
    static double animate(double from, double to, long elapsedMillis) {
        if (elapsedMillis >= ANIMATION_MILLIS || elapsedMillis < 0) {
            return to;
        }
        double t = (double) elapsedMillis / ANIMATION_MILLIS;
        double eased = 1 - (1 - t) * (1 - t);
        return from + (to - from) * eased;
    }
}
