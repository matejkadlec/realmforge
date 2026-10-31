package io.github.matejkadlec.realmforge.spell;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

/** RLM-399: a melee spell's hit shape in the frame of its caster's look. */
class MeleeShapeTest {
    /** Slam's: a 120° sector of 1.5 blocks, 2 tall. */
    private static final MeleeShape SLAM = new MeleeShape(MeleeShape.Kind.SECTOR, 1.5, 120, 0, 2);
    /** Reckless Strike's: a box 2 wide and 1 deep from the front of the body, 2 tall. */
    private static final MeleeShape STAB = new MeleeShape(MeleeShape.Kind.BOX, 1, 0, 2, 2);
    private static final double BODY = 0.3;
    /** Eyes at the origin, looking south (+z), level. */
    private static final MeleeShape.Frame SOUTH = MeleeShape.Frame.of(Vec3.ZERO, 0, 0);

    @Test
    void theFrameFollowsTheLook() {
        assertEquals(0, SOUTH.forward().distanceTo(new Vec3(0, 0, 1)), 1e-9, "yaw 0 looks south");
        assertEquals(0, SOUTH.up().distanceTo(new Vec3(0, 1, 0)), 1e-9, "level: up is up");
        MeleeShape.Frame down = MeleeShape.Frame.of(Vec3.ZERO, 90, 0);
        assertEquals(0, down.forward().distanceTo(new Vec3(0, -1, 0)), 1e-9, "pitch 90 looks down");
        assertEquals(0, down.up().distanceTo(new Vec3(0, 0, 1)), 1e-9, "and its up is ahead");
    }

    @Test
    void aSectorHoldsWhatIsAheadWithinItsAngleAndReach() {
        assertTrue(SLAM.contains(SOUTH, BODY, new Vec3(0, 0, 1)), "straight ahead");
        assertTrue(SLAM.contains(SOUTH, BODY, new Vec3(1, 0, 1)), "45° to the side, 1.41 away");
        assertFalse(SLAM.contains(SOUTH, BODY, new Vec3(1.2, 0, 0.5)), "67° to the side, outside 60°");
        assertFalse(SLAM.contains(SOUTH, BODY, new Vec3(0, 0, 1.6)), "beyond 1.5 blocks");
        assertFalse(SLAM.contains(SOUTH, BODY, new Vec3(0, 0, -0.5)), "behind");
        assertFalse(SLAM.contains(SOUTH, BODY, new Vec3(0, 1.1, 1)), "above its 2 blocks");
        assertTrue(SLAM.contains(SOUTH, BODY, new Vec3(0, -0.9, 1)), "within its 2 blocks");
    }

    @Test
    void aBoxStartsAtTheFrontOfTheBody() {
        assertTrue(STAB.contains(SOUTH, BODY, new Vec3(0, 0, 0.5)), "just in front");
        assertTrue(STAB.contains(SOUTH, BODY, new Vec3(0.9, 0, 1.2)), "near its side, 1.2 ahead");
        assertFalse(STAB.contains(SOUTH, BODY, new Vec3(0, 0, 0.2)), "inside the body, not in front of it");
        assertFalse(STAB.contains(SOUTH, BODY, new Vec3(0, 0, 1.4)), "beyond its 1 block");
        assertFalse(STAB.contains(SOUTH, BODY, new Vec3(1.1, 0, 0.8)), "beyond its 2 blocks' width");
        assertFalse(STAB.contains(SOUTH, BODY, new Vec3(1, 0, 0)), "beside the player");
    }

    @Test
    void aHitboxReachesInWhenAnyOfItsPointsDoes() {
        // A zombie's box whose near face is 1.3 ahead: only its front reaches Slam's 1.5.
        AABB zombie = new AABB(-0.3, -1.62, 1.3, 0.3, 0.33, 1.9);
        assertTrue(SLAM.reaches(SOUTH, BODY, zombie));
        AABB far = zombie.move(0, 0, 0.5);
        assertFalse(SLAM.reaches(SOUTH, BODY, far), "its near face 1.8 ahead");
        AABB lookingDown = new AABB(-0.3, -1.62, 1.3, 0.3, 0.33, 1.9);
        assertTrue(SLAM.reaches(MeleeShape.Frame.of(Vec3.ZERO, 30, 0), BODY, lookingDown),
                "the shape tilts down with the look");
    }

    @Test
    void samplesSpreadThroughTheBoxAtMostASpacingApart() {
        AABB zombie = new AABB(0, 0, 0, 0.6, 1.95, 0.6);
        assertEquals(4 * 9 * 4, MeleeShape.samples(zombie, 0.25, 9, 0).size(), "0.6 wide: 4 points, 1.95 tall: 9");
        assertEquals(27, MeleeShape.samples(zombie, 0.5, 3, 0.1).size(), "the cover check's 3 × 3 × 3");
        assertTrue(MeleeShape.samples(zombie, 0.5, 3, 0.1).stream().allMatch(point -> point.x > 0 && point.x < 0.6),
                "kept inside the faces");
    }

    @Test
    void theOutlineHasItsEdges() {
        assertEquals(2 * (12 + 2) + 3, SLAM.outline(SOUTH, BODY, 12).size(), "two arcs of 12 and their sides, 3 edges");
        assertEquals(2 * 4 + 4, STAB.outline(SOUTH, BODY, 12).size(), "two rectangles and 4 edges");
    }
}
