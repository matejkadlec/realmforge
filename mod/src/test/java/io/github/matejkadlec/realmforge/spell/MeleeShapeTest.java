package io.github.matejkadlec.realmforge.spell;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

/** RLM-399: a melee spell's hit shape, Better Combat's sword swing or stab at the caster's melee reach. */
class MeleeShapeTest {
    /** The default melee hit shape at vanilla's reach of 3: 6 wide, 1 tall, 3 deep, 120°. */
    private static final MeleeShape SWING = new MeleeShape(MeleeShape.Kind.SWING, 3);
    /** The stab at the same reach: 1.5 wide and tall, 3 deep. */
    private static final MeleeShape STAB = new MeleeShape(MeleeShape.Kind.STAB, 3);
    /** The shape's origin at 0, looking south (+z), level. */
    private static final MeleeShape.Frame SOUTH = MeleeShape.Frame.of(Vec3.ZERO, 0, 0);
    /** How far the origin is above a standing player's feet: its eyes, 1.62, less 15 % of its 1.8 height. */
    private static final double ORIGIN_HEIGHT = 1.62 - 0.15 * 1.8;

    /** A zombie's hitbox, 0.6 wide and 1.95 tall, on the caster's floor, its middle {@code across}, {@code ahead}. */
    private static AABB zombie(double across, double ahead) {
        return new AABB(across - 0.3, -ORIGIN_HEIGHT, ahead - 0.3, across + 0.3, 1.95 - ORIGIN_HEIGHT, ahead + 0.3);
    }

    @Test
    void theFrameFollowsTheLook() {
        assertEquals(0, SOUTH.forward().distanceTo(new Vec3(0, 0, 1)), 1e-9, "yaw 0 looks south");
        assertEquals(0, SOUTH.up().distanceTo(new Vec3(0, 1, 0)), 1e-9, "level: up is up");
        MeleeShape.Frame down = MeleeShape.Frame.of(Vec3.ZERO, 90, 0);
        assertEquals(0, down.forward().distanceTo(new Vec3(0, -1, 0)), 1e-9, "pitch 90 looks down");
        assertEquals(0, down.up().distanceTo(new Vec3(0, 0, 1)), 1e-9, "and its up is ahead");
    }

    @Test
    void theShapesAreBetterCombatsSwordHitboxesAtTheReach() {
        assertEquals(new Vec3(6, 1, 3), SWING.size(), "2 × reach wide, reach / 3 tall, reach deep");
        assertEquals(new Vec3(1.5, 1.5, 3), STAB.size(), "reach / 2 wide and tall");
        assertEquals(new Vec3(10, 5.0 / 3, 5), new MeleeShape(MeleeShape.Kind.SWING, 5).size(),
                "a creative reach of 5");
        assertEquals(120, MeleeShape.Kind.SWING.angleDegrees());
        assertEquals(0, MeleeShape.Kind.STAB.angleDegrees());
    }

    @Test
    void aSwingStrikesAheadWithinItsReach() {
        assertTrue(SWING.strikes(SOUTH, zombie(0, 2.8)), "its near face 2.5 ahead");
        assertFalse(SWING.strikes(SOUTH, zombie(0, 3.5)), "its near face 3.2 ahead, beyond the reach of 3");
        assertTrue(SWING.strikes(SOUTH, zombie(1.4, 1.4)), "45° to the side");
        assertTrue(SWING.strikes(SOUTH, zombie(0, 0.5)), "right in front of the caster");
        assertFalse(SWING.strikes(SOUTH, zombie(0, -1)), "behind");
    }

    @Test
    void aSwingSparesWhatLiesBeyondItsAngle() {
        // Inside the box and within the reach, but its middle 70° and its nearest point 76° to the side.
        assertFalse(SWING.strikes(SOUTH, zombie(2.0, 0.73)));
        assertTrue(SWING.strikes(SOUTH, zombie(1.2, 2.0)), "31° to the side");
    }

    @Test
    void theShapeGrowsWithTheReach() {
        MeleeShape creative = new MeleeShape(MeleeShape.Kind.SWING, 5);
        assertTrue(creative.strikes(SOUTH, zombie(0, 4.8)), "its near face 4.5 ahead: within a reach of 5");
        assertFalse(SWING.strikes(SOUTH, zombie(0, 4.8)), "but not a reach of 3");
        assertTrue(new MeleeShape(MeleeShape.Kind.STAB, 5).strikes(SOUTH, zombie(0, 4.8)), "a stab of 5 too");
    }

    @Test
    void aStabIsNarrowerButReachesAsFar() {
        assertTrue(STAB.strikes(SOUTH, zombie(0, 3.2)), "its near face 2.9 ahead");
        assertFalse(STAB.strikes(SOUTH, zombie(0, 3.4)), "3.1 ahead");
        assertFalse(STAB.strikes(SOUTH, zombie(1.2, 2.0)), "1.2 to the side: beyond its 0.75");
        assertTrue(STAB.strikes(SOUTH, zombie(0.6, 2.0)), "its side reaches into the 1.5 wide box");
    }

    @Test
    void theShapeIsAsTallAsBetterCombatsAndTiltsWithTheLook() {
        // Its feet 0.6 above the look's line: over the swing's half of 1, under the stab's half of 1.5.
        AABB above = zombie(0, 2).move(0, 1.95, 0);
        assertFalse(SWING.strikes(SOUTH, above), "over the swing");
        assertTrue(STAB.strikes(SOUTH, above), "within the stab");
        AABB chicken = new AABB(-0.2, -ORIGIN_HEIGHT, 1.3, 0.2, 0.7 - ORIGIN_HEIGHT, 1.7);
        assertFalse(SWING.strikes(SOUTH, chicken), "a chicken below the level swing");
        assertTrue(SWING.strikes(MeleeShape.Frame.of(Vec3.ZERO, 40, 0), chicken), "the swing tilts down onto it");
    }

    @Test
    void aHitboxRoundTheOriginIsStruck() {
        AABB giant = new AABB(-5, -5, -1, 5, 5, 9);
        assertTrue(SWING.strikes(SOUTH, giant), "a hitbox that holds the whole shape");
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
        assertEquals(12 + 2 + 12, SWING.outline(SOUTH, 12).size(), "the box's 12 edges, the arc's 12 and its sides");
        assertEquals(12, STAB.outline(SOUTH, 12).size(), "the box's 12 edges");
    }
}
