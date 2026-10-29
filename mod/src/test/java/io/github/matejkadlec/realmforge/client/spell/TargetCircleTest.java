package io.github.matejkadlec.realmforge.client.spell;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TargetCircleTest {
    @Test
    void aRingIsDueEverySecondGameTick() {
        assertTrue(TargetCircle.ringDue(100, 98));
        assertFalse(TargetCircle.ringDue(101, 100), "an odd tick");
        assertTrue(TargetCircle.ringDue(102, 100));
        assertTrue(TargetCircle.ringDue(100, Long.MIN_VALUE), "the first ring");
    }

    /** RLM-474: while paused the client ticks on but the game time stands still; one ring, not one a client tick. */
    @Test
    void aGameTickThatStandsStillGetsOneRing() {
        assertFalse(TargetCircle.ringDue(100, 100));
    }
}
