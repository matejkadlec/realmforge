package io.github.matejkadlec.realmforge.spell;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The geometry of a cone along a look (RLM-542: Cone of Cold; architecture 11.3), from a {@link MeleeShape.Frame}'s
 * origin along its forward vector, so it tilts with the pitch. A hitbox is in it when its nearest point lies within
 * {@code range} of the tip and its middle or its nearest point lies within half of {@code angleDegrees} of the look, a
 * melee swing's angle rule ({@link MeleeShape#strikes}) with a reach of its own. Pure: JUnit covers it.
 *
 * @param range how far it reaches from its tip, in blocks
 * @param angleDegrees how wide it opens, in degrees
 */
public record ConeShape(double range, double angleDegrees) {
    private static final double INSIDE = 1e-9;

    /** Whether the cone in {@code frame} holds the hitbox {@code box}, by the rules above. */
    public boolean strikes(MeleeShape.Frame frame, AABB box) {
        Vec3 nearest = MeleeShape.nearestPoint(box, frame.origin()).subtract(frame.origin());
        if (nearest.length() > range) {
            return false;
        }
        double halfAngle = angleDegrees / 2;
        return nearest.lengthSqr() < INSIDE
                || MeleeShape.angle(box.getCenter().subtract(frame.origin()), frame.forward()) <= halfAngle
                || MeleeShape.angle(nearest, frame.forward()) <= halfAngle;
    }

    /**
     * {@code count} points spread through the cone in {@code frame}, for its particles: each at a random distance from
     * a block ahead of the tip to its range, turned from the look by a random angle up to half the cone's, in a random
     * direction round it.
     */
    public List<Vec3> particlePoints(MeleeShape.Frame frame, RandomSource random, int count) {
        List<Vec3> points = new ArrayList<>(count);
        double halfAngle = Math.toRadians(angleDegrees / 2);
        double near = Math.min(1, range / 2);
        for (int n = 0; n < count; n++) {
            double distance = near + (range - near) * random.nextDouble();
            double off = halfAngle * Math.sqrt(random.nextDouble());
            double around = 2 * Math.PI * random.nextDouble();
            double across = Math.sin(off) * Math.cos(around) * distance;
            double up = Math.sin(off) * Math.sin(around) * distance;
            points.add(frame.world(across, up, Math.cos(off) * distance));
        }
        return points;
    }
}
