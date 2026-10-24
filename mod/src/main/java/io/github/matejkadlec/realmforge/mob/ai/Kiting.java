package io.github.matejkadlec.realmforge.mob.ai;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.phys.Vec3;

/**
 * Where a kiting mob steps between two attacks (architecture 14.2, research BM-22), on the horizontal plane. Pure: its
 * position, its target's, the range it attacks from and the step's length go in; the places it may step to come out,
 * best first. {@link KiteStep} walks to the first one it can reach that keeps the target in sight.
 *
 * <ol>
 *   <li><b>Away</b> from the target, straight or at {@value #AWAY_TURN_DEGREES} degrees to either side, while the
 *   step ends within the range (less {@value #RANGE_MARGIN} block, so a target that moves a little stays in it).</li>
 *   <li><b>Round</b> the target, on the circle the mob stands on (or the range's, if that is smaller), to either
 *   side: at the edge of its range, the mob circles its target instead of leaving the range.</li>
 *   <li><b>A little toward</b> it, {@value #INWARD_TURN_DEGREES} degrees off the circle, to either side.</li>
 * </ol>
 * Every pair of sides starts with the side {@code clockwise} names, so a mob can keep circling one way.
 */
public final class Kiting {
    /** A step ends this far inside the range, in blocks. */
    static final double RANGE_MARGIN = 0.5;
    /** How far a step away may turn from straight away from the target. */
    static final double AWAY_TURN_DEGREES = 45;
    /** How far a step toward the target turns in from the circle around it. */
    static final double INWARD_TURN_DEGREES = 30;
    /** Two places closer than this, squared, are one. */
    private static final double SAME = 1e-6;

    private Kiting() {
    }

    /**
     * The places a mob at {@code self} may step to, best first, to keep {@code target} within {@code range} blocks
     * with a step of {@code step} blocks; {@code clockwise} (seen from above) picks the side it tries first. Every
     * place keeps {@code self}'s height, and none lies outside the range less its margin.
     */
    public static List<Vec3> steps(Vec3 self, Vec3 target, double range, double step, boolean clockwise) {
        double limit = range - RANGE_MARGIN;
        Vec3 offset = new Vec3(self.x - target.x, 0, self.z - target.z);
        double distance = offset.horizontalDistance();
        // A mob right on its target's spot has no "away": it steps along an arbitrary direction.
        Vec3 away = distance < 1e-4 ? new Vec3(1, 0, 0) : offset.scale(1 / distance);
        double side = clockwise ? 1 : -1;
        List<Vec3> steps = new ArrayList<>();
        for (double turn : new double[] {0, AWAY_TURN_DEGREES * side, -AWAY_TURN_DEGREES * side}) {
            add(steps, self.add(turned(away, turn).scale(step)), target, limit);
        }
        double radius = Math.min(distance, limit);
        if (radius > 1e-4) {
            // A chord of the step's length on the circle of this radius around the target.
            double angle = Math.toDegrees(2 * Math.asin(Math.min(1, step / (2 * radius))));
            for (double turn : new double[] {angle * side, -angle * side}) {
                Vec3 onCircle = turned(away, turn).scale(radius);
                add(steps, new Vec3(target.x + onCircle.x, self.y, target.z + onCircle.z), target, limit);
            }
        }
        double inward = 90 + INWARD_TURN_DEGREES;
        for (double turn : new double[] {inward * side, -inward * side}) {
            add(steps, self.add(turned(away, turn).scale(step)), target, limit);
        }
        return steps;
    }

    private static void add(List<Vec3> steps, Vec3 place, Vec3 target, double limit) {
        double distance = new Vec3(place.x - target.x, 0, place.z - target.z).horizontalDistance();
        if (distance <= limit + 1e-9 && steps.stream().noneMatch(other -> other.distanceToSqr(place) < SAME)) {
            steps.add(place);
        }
    }

    /** {@code direction} (horizontal) turned by {@code degrees}, clockwise seen from above for positive degrees. */
    static Vec3 turned(Vec3 direction, double degrees) {
        double radians = Math.toRadians(degrees);
        double cos = Math.cos(radians);
        double sin = Math.sin(radians);
        // Minecraft's z points south: seen from above (x east, z south), clockwise turns east toward south.
        return new Vec3(direction.x * cos - direction.z * sin, 0, direction.x * sin + direction.z * cos);
    }
}
