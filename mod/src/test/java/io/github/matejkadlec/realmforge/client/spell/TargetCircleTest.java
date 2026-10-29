package io.github.matejkadlec.realmforge.client.spell;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
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

    /** RLM-468: a cast's own mob wins; without one, the Show target key's while it is held. */
    @Test
    void theRingGoesUnderTheCastsMobElseTheShownOneWhileTheKeyIsHeld() {
        Optional<Integer> cast = Optional.of(7);
        Optional<Integer> shown = Optional.of(9);
        assertEquals(cast, TargetCircle.under(true, cast, true, shown), "the cast's mob wins");
        assertEquals(cast, TargetCircle.under(true, cast, false, Optional.empty()), "a cast alone");
        assertEquals(shown, TargetCircle.under(true, Optional.empty(), true, shown),
                "a cast that names no mob leaves the key's");
        assertEquals(shown, TargetCircle.under(false, Optional.empty(), true, shown), "the key without a cast");
        assertEquals(Optional.empty(), TargetCircle.under(false, Optional.empty(), false, shown),
                "the key let go");
        assertEquals(Optional.empty(), TargetCircle.under(false, cast, false, Optional.empty()),
                "a stale cast mob without a cast");
    }
}
