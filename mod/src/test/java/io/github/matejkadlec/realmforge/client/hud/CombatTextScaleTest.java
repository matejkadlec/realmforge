package io.github.matejkadlec.realmforge.client.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** How large combat text is drawn at a distance from the camera (RLM-185). */
class CombatTextScaleTest {
    private static final float EPSILON = 1e-4F;

    @Test
    void theBaseSizeAtTheReferenceDistance() {
        assertEquals(CombatTextScale.BASE, CombatTextScale.at(CombatTextScale.REFERENCE_DISTANCE), EPSILON);
    }

    @Test
    void smallerFurtherAwayLargerCloser() {
        float previous = Float.MAX_VALUE;
        for (double distance = 2; distance <= 25; distance += 1) {
            float scale = CombatTextScale.at(distance);
            assertTrue(scale < previous, "smaller at " + distance + " blocks than one block closer: " + scale);
            previous = scale;
        }
        assertEquals(CombatTextScale.BASE * 0.5, CombatTextScale.at(16), EPSILON, "four times as far, half the size");
    }

    @Test
    void aFarHitStaysReadableAndANearOneStaysOnScreen() {
        assertEquals(CombatTextScale.MIN, CombatTextScale.at(40), EPSILON, "the minimum at 40 blocks");
        assertEquals(CombatTextScale.MIN, CombatTextScale.at(500), EPSILON, "and further");
        assertEquals(CombatTextScale.MAX, CombatTextScale.at(0.5), EPSILON, "the maximum half a block away");
        assertEquals(CombatTextScale.MAX, CombatTextScale.at(0), EPSILON, "and at the camera");
    }
}
