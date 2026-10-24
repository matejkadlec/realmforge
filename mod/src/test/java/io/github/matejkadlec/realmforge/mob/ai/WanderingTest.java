package io.github.matejkadlec.realmforge.mob.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class WanderingTest {
    private static final double CLOSE = 1e-9;

    @Test
    void aFasterMobWandersAtTheZombifiedPiglinsSpeed() {
        // The Hewn Sentinel (0.27) and the Hewn Chanter (0.25).
        for (double speed : new double[] {0.27, 0.25, 0.4}) {
            assertEquals(Wandering.SPEED, speed * Wandering.speedModifier(speed), CLOSE, "at " + speed);
        }
    }

    @Test
    void aMobAsFastOrSlowerWandersAtItsOwnPace() {
        assertEquals(1.0, Wandering.speedModifier(Wandering.SPEED), CLOSE);
        assertEquals(1.0, Wandering.speedModifier(0.15), CLOSE);
    }
}
