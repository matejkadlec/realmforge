package io.github.matejkadlec.realmforge.spell;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

/** RLM-542: Cone of Cold's cone, 5 blocks along the look and 60° wide. */
class ConeShapeTest {
    private static final ConeShape CONE = new ConeShape(5, 60);
    /** The cone's tip at 0, looking south (+z), level. */
    private static final MeleeShape.Frame SOUTH = MeleeShape.Frame.of(Vec3.ZERO, 0, 0);
    /** How far the tip is above a standing player's feet: its eyes, 1.62, less 15 % of its 1.8 height. */
    private static final double TIP_HEIGHT = 1.62 - 0.15 * 1.8;

    /** A zombie's hitbox, 0.6 wide and 1.95 tall, on the caster's floor, its middle {@code across}, {@code ahead}. */
    private static AABB zombie(double across, double ahead) {
        return new AABB(across - 0.3, -TIP_HEIGHT, ahead - 0.3, across + 0.3, 1.95 - TIP_HEIGHT, ahead + 0.3);
    }

    @Test
    void itHoldsWhatIsAheadWithinItsRange() {
        assertTrue(CONE.strikes(SOUTH, zombie(0, 1)), "right in front");
        assertTrue(CONE.strikes(SOUTH, zombie(0, 4)), "4 blocks ahead");
        assertTrue(CONE.strikes(SOUTH, zombie(0, 5.2)), "its near side at 4.9 blocks");
        assertFalse(CONE.strikes(SOUTH, zombie(0, 5.5)), "its near side at 5.2 blocks, beyond the range");
    }

    @Test
    void itHoldsOnlyWhatIsWithinHalfItsAngle() {
        // 4 ahead and 2 across is 26.6° off the look; 4 ahead and 3 across, 36.9° (its nearest point 33.7°).
        assertTrue(CONE.strikes(SOUTH, zombie(2, 4)), "26.6° to the side");
        assertFalse(CONE.strikes(SOUTH, zombie(3, 4)), "beyond 30° to the side");
        assertFalse(CONE.strikes(SOUTH, zombie(0, -2)), "behind");
        assertFalse(CONE.strikes(SOUTH, zombie(3, 0)), "level with the tip, to the side");
    }

    @Test
    void aHitboxRoundTheTipIsInIt() {
        assertTrue(CONE.strikes(SOUTH, new AABB(-0.5, -0.5, -0.5, 0.5, 0.5, 0.5)), "the tip inside a hitbox");
    }

    @Test
    void itTiltsWithThePitch() {
        MeleeShape.Frame down = MeleeShape.Frame.of(Vec3.ZERO, 45, 0);
        // A small hitbox low ahead (a chicken's): about 40° below the level look, so only a look down holds it.
        AABB low = new AABB(-0.2, -1.3, 1.2, 0.2, -1.0, 1.5);
        assertFalse(CONE.strikes(SOUTH, low), "40° below a level look");
        assertTrue(CONE.strikes(down, low), "the cone follows the look down");
        assertFalse(CONE.strikes(down, new AABB(-0.3, 2, 3.7, 0.3, 3, 4.3)), "and leaves what is high ahead");
    }

    @Test
    void particlesStayInTheCone() {
        List<Vec3> points = CONE.particlePoints(SOUTH, RandomSource.create(42), 200);
        assertEquals(200, points.size());
        for (Vec3 point : points) {
            assertTrue(point.length() <= 5 + 1e-9, "within the range: " + point);
            assertTrue(point.z > 0, "ahead: " + point);
            double off = Math.toDegrees(Math.acos(point.normalize().dot(SOUTH.forward())));
            assertTrue(off <= 30 + 1e-6, "within 30° of the look: " + off);
        }
    }
}
