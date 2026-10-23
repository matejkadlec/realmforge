package io.github.matejkadlec.realmforge.mob.ai;

/**
 * How fast an entity must rise to be thrown up a given height (architecture 14.2: a melee mob's
 * {@code knock_up_blocks}). Pure: vanilla's living entities move by their velocity, then lose
 * {@value #GRAVITY} blocks per tick of it to gravity and keep {@value #DRAG} of the rest ({@code LivingEntity#travel}),
 * so the height is the sum of the rising ticks' velocities.
 */
public final class KnockUp {
    static final double GRAVITY = 0.08;
    static final double DRAG = 0.98;
    private static final int STEPS = 60;

    private KnockUp() {
    }

    /** The upward velocity, in blocks per tick, that throws an entity {@code blocks} high; 0 for 0 or less. */
    public static double velocity(double blocks) {
        if (blocks <= 0) {
            return 0;
        }
        double low = 0;
        double high = 4;
        for (int i = 0; i < STEPS; i++) {
            double middle = (low + high) / 2;
            if (height(middle) < blocks) {
                low = middle;
            } else {
                high = middle;
            }
        }
        return high;
    }

    /** How high an entity rising at {@code velocity} blocks per tick goes. */
    static double height(double velocity) {
        double height = 0;
        double speed = velocity;
        while (speed > 0) {
            height += speed;
            speed = (speed - GRAVITY) * DRAG;
        }
        return height;
    }
}
