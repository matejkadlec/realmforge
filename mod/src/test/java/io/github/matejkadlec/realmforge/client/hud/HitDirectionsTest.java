package io.github.matejkadlec.realmforge.client.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** Where the hit direction arc points: radians clockwise from the top of the screen. */
class HitDirectionsTest {
    private static final float EPSILON = 1e-4F;

    @Test
    void anAttackerAheadIsAtTheTop() {
        assertEquals(0, HitDirections.screenAngle(0, 0, 0, 0, 5), EPSILON, "facing south, an attacker to the south");
        assertEquals(0, HitDirections.screenAngle(0, 0, 90, -5, 0), EPSILON, "facing west, one to the west");
    }

    @Test
    void sidesAndBehind() {
        assertEquals(Math.PI / 2, HitDirections.screenAngle(0, 0, 0, -5, 0), EPSILON,
                "facing south, west is to the right");
        assertEquals(-Math.PI / 2, HitDirections.screenAngle(0, 0, 0, 5, 0), EPSILON, "and east to the left");
        assertEquals(Math.PI, Math.abs(HitDirections.screenAngle(0, 0, 0, 0, -5)), EPSILON, "north is behind");
    }

    @Test
    void theAngleIsRelativeToThePlayer() {
        assertEquals(HitDirections.screenAngle(0, 0, 30, 3, 4), HitDirections.screenAngle(10, 10, 30, 13, 14), EPSILON,
                "only the offset counts");
        assertEquals(-Math.PI / 4, HitDirections.screenAngle(0, 0, 45, 0, 5), EPSILON,
                "turned 45 degrees right, an attacker straight south drifts left");
    }
}
