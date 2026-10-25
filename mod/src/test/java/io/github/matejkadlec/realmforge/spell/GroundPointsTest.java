package io.github.matejkadlec.realmforge.spell;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class GroundPointsTest {
    private static final double CLOSE = 1e-9;
    private static final Vec3 EYES = new Vec3(0, 65.62, 0);

    @Test
    void aPointInRangeIsNotClamped() {
        Vec3 point = new Vec3(3, 64, 10);
        assertSame(point, GroundPoints.clamp(EYES, point, 16));
    }

    @Test
    void aPointExactlyAtTheRangeIsNotClamped() {
        Vec3 point = EYES.add(0, 0, 16);
        assertSame(point, GroundPoints.clamp(EYES, point, 16));
    }

    @Test
    void aPointBeyondTheRangeIsClampedToTheFarthestPointInRangeAlongTheLine() {
        Vec3 point = new Vec3(30, 64, 40);
        Vec3 clamped = GroundPoints.clamp(EYES, point, 16);
        assertEquals(16, clamped.distanceTo(EYES), CLOSE);
        Vec3 along = point.subtract(EYES).normalize();
        Vec3 direction = clamped.subtract(EYES).normalize();
        assertEquals(0, direction.distanceTo(along), CLOSE);
    }

    @Test
    void theSkyClampsUpAndAway() {
        // Looking 75 degrees up, south: the clamped point is about 4.1 blocks out and 15.5 blocks up.
        double pitch = Math.toRadians(75);
        Vec3 look = new Vec3(0, Math.sin(pitch), Math.cos(pitch));
        Vec3 clamped = GroundPoints.clamp(EYES, EYES.add(look.scale(1000)), 16);
        assertEquals(16 * Math.cos(pitch), clamped.z, CLOSE);
        assertEquals(EYES.y + 16 * Math.sin(pitch), clamped.y, CLOSE);
    }

    @Test
    void theSearchForTheGroundStartsJustOutOfTheFaceTheLineHit() {
        Vec3 hit = new Vec3(0.5, 66.62, 6);
        assertEquals(new Vec3(0.5, 66.62, 6 - GroundPoints.FACE_OFFSET), GroundPoints.offFace(hit, Direction.NORTH));
        assertEquals(new Vec3(0.5, 66.62 + GroundPoints.FACE_OFFSET, 6), GroundPoints.offFace(hit, Direction.UP));
        assertEquals(new Vec3(0.5, 66.62 - GroundPoints.FACE_OFFSET, 6), GroundPoints.offFace(hit, Direction.DOWN));
    }
}
