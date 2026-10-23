package io.github.matejkadlec.realmforge.mob.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class KnockUpTest {
    @Test
    void theVelocityThrowsAnEntityThatHigh() {
        for (double blocks : new double[] {0.25, 0.5, 1, 1.25, 2}) {
            assertEquals(blocks, KnockUp.height(KnockUp.velocity(blocks)), 1e-6, blocks + " blocks");
        }
    }

    @Test
    void vanillasJumpReachesAboutABlockAndAQuarter() {
        // A player's jump starts at 0.42 blocks a tick and tops out a little over 1.25 blocks.
        double jump = KnockUp.height(0.42);
        assertTrue(jump > 1.2 && jump < 1.3, "a jump reaches " + jump);
    }

    @Test
    void halfABlockTakesAboutAQuarterBlockATick() {
        double velocity = KnockUp.velocity(0.5);
        assertTrue(velocity > 0.2 && velocity < 0.3, "half a block: " + velocity);
    }

    @Test
    void noHeightNoVelocity() {
        assertEquals(0, KnockUp.velocity(0));
        assertEquals(0, KnockUp.velocity(-1));
    }
}
