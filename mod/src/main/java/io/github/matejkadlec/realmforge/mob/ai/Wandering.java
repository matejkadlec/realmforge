package io.github.matejkadlec.realmforge.mob.ai;

/**
 * How a custom mob wanders while it has no target (architecture 14.2; Matěj's playtest, 2026-10-24): as vanilla's
 * mobs do, with vanilla's own goal and its numbers ({@code WaterAvoidingRandomStrollGoal}: a 1 in 120 chance a tick to
 * set off, to a spot up to 10 blocks away and 7 up or down, then a pause until the next roll), at the Zombified
 * Piglin's walking speed. A mob that fights faster than that wanders slower than it fights; one that is slower
 * anyway wanders at its own pace. Pure: the mob's movement speed goes in, its wandering speed modifier comes out.
 */
public final class Wandering {
    /** The Zombified Piglin's movement speed, at which it strolls (modifier 1): every custom mob wanders at it. */
    public static final double SPEED = 0.23;
    /** The goal priority vanilla's monsters stroll at (Zombie, Zombified Piglin): after every fight goal. */
    public static final int PRIORITY = 7;
    /** The priority of looking at a nearby player and looking around, as vanilla's monsters. */
    public static final int LOOK_PRIORITY = 8;
    /** How near a player must be for a wandering mob to look at them, in blocks, as vanilla's monsters. */
    public static final float LOOK_RANGE = 8.0F;

    private Wandering() {
    }

    /** The speed modifier that makes a mob of {@code movementSpeed} wander at {@link #SPEED}, at most 1. */
    public static double speedModifier(double movementSpeed) {
        return movementSpeed <= SPEED ? 1.0 : SPEED / movementSpeed;
    }
}
