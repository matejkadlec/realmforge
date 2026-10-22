package io.github.matejkadlec.realmforge.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class WeaponBlocksTest {
    /** The defaults (Matěj, 2026-10-22): 0.5 s in full, then half, 1 s cooldown, the front half. */
    private static final WeaponBlocks.Rules RULES = new WeaponBlocks.Rules(10, 0.5, 20, 180);

    @Test
    void aHitInTheWindowIsBlockedInFullAndALaterOneByTheShare() {
        assertEquals(0, RULES.blocked(10, 0));
        assertEquals(0, RULES.blocked(10, 9), "the last tick of the window");
        assertEquals(5, RULES.blocked(10, 10), "the window is over");
        assertEquals(5, RULES.blocked(10, 600), "held for half a minute");
        assertEquals(7.5F, new WeaponBlocks.Rules(0, 0.25, 20, 180).blocked(10, 0), 1e-6, "no window, a quarter");
        assertEquals(10, new WeaponBlocks.Rules(0, 0, 20, 180).blocked(10, 5), "a share of 0 blocks nothing");
    }

    @Test
    void theFrontHalfIsCovered() {
        // Looking south (+z).
        assertTrue(WeaponBlocks.covers(0, 1, 0, 3, 180), "straight ahead");
        assertTrue(WeaponBlocks.covers(0, 1, 2, 1, 180), "ahead and to the side");
        assertTrue(WeaponBlocks.covers(0, 1, 1, 0, 180), "exactly beside: the edge of the half");
        assertFalse(WeaponBlocks.covers(0, 1, 1, -0.1, 180), "just behind the side");
        assertFalse(WeaponBlocks.covers(0, 1, 0, -3, 180), "straight behind");
    }

    @Test
    void theArcNarrowsAndTheFullCircleCoversEverything() {
        assertTrue(WeaponBlocks.covers(0, 1, 1, 2, 90), "26.6° off within a 90° arc");
        assertFalse(WeaponBlocks.covers(0, 1, 2, 1, 90), "63.4° off outside a 90° arc");
        assertTrue(WeaponBlocks.covers(0, 1, 0, -3, 360), "behind, with the full circle");
        assertFalse(WeaponBlocks.covers(0, 1, 0, 3, 0), "no arc covers nothing");
    }

    @Test
    void aHitWithoutAHorizontalDirectionIsCoveredOnlyByTheFullCircle() {
        assertFalse(WeaponBlocks.covers(0, 1, 0, 0, 180), "from straight above");
        assertFalse(WeaponBlocks.covers(0, 0, 0, 3, 180), "looking straight down");
        assertTrue(WeaponBlocks.covers(0, 1, 0, 0, 360), "the full circle");
    }
}
