package io.github.matejkadlec.realmforge.client.hud;

/**
 * How large floating combat text ({@link CombatTextHud}) is drawn, apart from drawing so JUnit can check it. A text
 * over an entity shrinks as the camera moves away from it and grows as it comes closer, within a minimum that keeps a
 * far hit readable (RLM-185).
 */
public final class CombatTextScale {
    /**
     * A text {@value #REFERENCE_DISTANCE} blocks from the camera, and the player's own miss by the crosshair, is this
     * many times the font's size.
     */
    public static final float BASE = 1.75F;
    public static final float MIN = 0.65F;
    public static final float MAX = 2.5F;
    static final double REFERENCE_DISTANCE = 4;
    /**
     * How the size follows the distance: {@code BASE * (REFERENCE_DISTANCE / distance)^FALLOFF}. At 1 a text would
     * shrink like the world around it and reach the minimum by 11 blocks; at 0.5 it keeps shrinking over the distances
     * fights happen at, from 2.0 at melee range (3 blocks) to the minimum at 29 blocks.
     */
    static final double FALLOFF = 0.5;

    private CombatTextScale() {
    }

    /** How many times the font's size a text {@code distance} blocks from the camera is drawn. */
    public static float at(double distance) {
        double scale = BASE * Math.pow(REFERENCE_DISTANCE / Math.max(distance, 0.01), FALLOFF);
        return (float) Math.clamp(scale, MIN, MAX);
    }
}
