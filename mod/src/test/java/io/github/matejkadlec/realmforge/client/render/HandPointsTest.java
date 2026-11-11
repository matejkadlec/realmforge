package io.github.matejkadlec.realmforge.client.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

/** Where a casting player's hands meet, the point a forming projectile is drawn at (RLM-640). */
class HandPointsTest {
    private static final double CLOSE = 1e-6;
    private static final double EYE_HEIGHT = 1.62;

    @Test
    void inFirstPersonThePointIsAheadOfTheEyeAndBelowTheLineOfSight() {
        // Looking level to the south (yaw 0 is +z).
        Vec3 point = HandPoints.player(Vec3.ZERO, EYE_HEIGHT, 1, 0, 0, true);
        assertEquals(0, point.x, CLOSE);
        assertEquals(EYE_HEIGHT - HandPoints.DROP, point.y, CLOSE);
        assertEquals(HandPoints.REACH, point.z, CLOSE);
    }

    @Test
    void inFirstPersonItStaysWhereItIsOnTheScreenWhereverThePlayerLooks() {
        // The same distance from the eye and the same angle below the line of sight, at any pitch and yaw.
        Vec3 eye = new Vec3(0, EYE_HEIGHT, 0);
        double distance = Math.hypot(HandPoints.REACH, HandPoints.DROP);
        double below = Math.atan2(HandPoints.DROP, HandPoints.REACH);
        float[][] looks = {{0, 0}, {-60, 45}, {75, -130}, {30, 180}};
        for (float[] look : looks) {
            Vec3 fromEye = HandPoints.player(Vec3.ZERO, EYE_HEIGHT, 1, look[0], look[1], true).subtract(eye);
            // Vanilla's sines come from a table, so a turned direction is a unit vector only to about 1e-4.
            assertEquals(distance, fromEye.length(), 1e-3);
            Vec3 ahead = Vec3.directionFromRotation(look[0], look[1]);
            assertEquals(below, Math.acos(fromEye.normalize().dot(ahead)), 1e-3, "pitch " + look[0]);
        }
    }

    @Test
    void theFirstPersonPointIsBelowTheCrosshairAtEveryFieldOfView() {
        // Its angle below the line of sight is inside half the narrowest vertical field of view the game offers
        // (30 degrees), so it is on the screen, and well off the middle.
        double below = Math.toDegrees(Math.atan2(HandPoints.DROP, HandPoints.REACH));
        assertTrue(below > 15 && below < 35, "about a quarter turn of the eye down, not " + below);
    }

    @Test
    void seenFromOutsideTheArmsReachFromTheShouldersAlongTheLook() {
        Vec3 level = HandPoints.player(new Vec3(10, 64, -3), EYE_HEIGHT, 1, 0, 90, false);
        // Yaw 90 looks west (-x); the shoulders are 1.29 above the feet.
        assertEquals(10 - HandPoints.REACH, level.x, CLOSE);
        assertEquals(64 + HandPoints.SHOULDER_HEIGHT + HandPoints.LIFT, level.y, CLOSE);
        assertEquals(-3, level.z, CLOSE);
        Vec3 up = HandPoints.player(new Vec3(10, 64, -3), EYE_HEIGHT, 1, -90, 90, false);
        assertEquals(64 + HandPoints.SHOULDER_HEIGHT + HandPoints.REACH, up.y, CLOSE, "arms straight up");
    }

    @Test
    void aBiggerPlayersHandsAreFartherOut() {
        Vec3 point = HandPoints.player(Vec3.ZERO, EYE_HEIGHT * 2, 2, 0, 0, true);
        assertEquals(HandPoints.REACH * 2, point.z, CLOSE);
        assertEquals(EYE_HEIGHT * 2 - HandPoints.DROP * 2, point.y, CLOSE);
    }
}
