package io.github.matejkadlec.realmforge.client.spell;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.matejkadlec.realmforge.client.ClientConfig.HighlightMode;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class TargetCircleTest {
    /** RLM-468: a cast's own mob wins; without one, the highlighted one while the highlight is on. */
    @Test
    void theRingGoesUnderTheCastsMobElseTheHighlightedOne() {
        Optional<Integer> cast = Optional.of(7);
        Optional<Integer> highlighted = Optional.of(9);
        assertEquals(cast, TargetCircle.under(true, cast, true, highlighted), "the cast's mob wins");
        assertEquals(cast, TargetCircle.under(true, cast, false, Optional.empty()), "a cast alone");
        assertEquals(highlighted, TargetCircle.under(true, Optional.empty(), true, highlighted),
                "a cast that names no mob leaves the highlight's");
        assertEquals(highlighted, TargetCircle.under(false, Optional.empty(), true, highlighted),
                "the highlight without a cast");
        assertEquals(Optional.empty(), TargetCircle.under(false, Optional.empty(), false, highlighted),
                "the highlight off");
        assertEquals(Optional.empty(), TargetCircle.under(false, cast, false, Optional.empty()),
                "a stale cast mob without a cast");
    }

    /** Matěj's playtest of 2026-10-29: Toggle by default, a press shows it until the next; Hold follows the key. */
    @Test
    void toggleKeepsTheHighlightOnAndHoldFollowsTheKey() {
        assertTrue(TargetCircle.highlightOn(HighlightMode.TOGGLE, true, false), "toggled on, the key let go");
        assertFalse(TargetCircle.highlightOn(HighlightMode.TOGGLE, false, true), "toggled off, the key still down");
        assertTrue(TargetCircle.highlightOn(HighlightMode.HOLD, false, true), "held");
        assertFalse(TargetCircle.highlightOn(HighlightMode.HOLD, true, false), "let go: a stale toggle does nothing");
    }

    /** The ring lies outside the hitbox: a Warden's (0.9 wide) is wider than an Enderman's (0.6). */
    @Test
    void theRingIsAsWideAsTheMob() {
        assertEquals(0.3F + TargetCircle.MARGIN, TargetCircle.radius(0.6F), 1e-6);
        assertEquals(0.45F + TargetCircle.MARGIN, TargetCircle.radius(0.9F), 1e-6);
        assertTrue(TargetCircle.radius(0.9F) > TargetCircle.radius(0.6F));
        assertTrue(TargetCircle.radius(0) - TargetCircle.CORE / 2 - TargetCircle.GLOW > 0,
                "even the thinnest mob's ring has a hole in the middle");
    }

    /** Smooth at any size: enough segments for a small ring, more for a big one, within a cap. */
    @Test
    void biggerRingsGetMoreSegments() {
        int small = TargetCircle.segments(TargetCircle.radius(0.6F));
        int big = TargetCircle.segments(TargetCircle.radius(4F));
        assertTrue(small >= 96, "a husk's ring: " + small);
        assertTrue(big > small, "a big mob's ring: " + big);
        assertEquals(720, TargetCircle.segments(TargetCircle.radius(16F)), "capped for a dragon");
        // A husk's ring strays from a true circle by less than a thousandth of a block between two segments.
        float radius = TargetCircle.radius(0.6F);
        assertTrue(radius * (1 - Math.cos(Math.PI / small)) < 0.001);
    }
}
