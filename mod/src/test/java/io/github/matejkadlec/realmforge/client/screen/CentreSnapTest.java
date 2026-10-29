package io.github.matejkadlec.realmforge.client.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CentreSnapTest {
    private static final int MIDDLE = 132;

    @Test
    void aGroupFarFromTheMiddleFollowsTheMouse() {
        CentreSnap snap = new CentreSnap();
        assertEquals(40, snap.x(40, MIDDLE, 0));
        assertEquals(MIDDLE - 7, snap.x(MIDDLE - 7, MIDDLE, 10), "just outside the snap distance");
        assertFalse(snap.snapped());
    }

    @Test
    void aGroupNearTheMiddleSnapsToIt() {
        CentreSnap snap = new CentreSnap();
        assertEquals(MIDDLE, snap.x(MIDDLE - 6, MIDDLE, 0));
        assertTrue(snap.snapped());
        assertEquals(MIDDLE, snap.x(MIDDLE + 4, MIDDLE, 10));
    }

    @Test
    void theSnapHoldsAgainstAShortPull() {
        CentreSnap snap = new CentreSnap();
        snap.x(MIDDLE, MIDDLE, 0);
        assertEquals(MIDDLE, snap.x(MIDDLE + 40, MIDDLE, 100), "a pull begins");
        assertEquals(MIDDLE, snap.x(MIDDLE + 60, MIDDLE, 1099), "held for just under a second");
        assertTrue(snap.snapped());
    }

    @Test
    void aSecondOfPullingReleasesIt() {
        CentreSnap snap = new CentreSnap();
        snap.x(MIDDLE, MIDDLE, 0);
        snap.x(MIDDLE + 40, MIDDLE, 100);
        assertEquals(MIDDLE + 60, snap.x(MIDDLE + 60, MIDDLE, 1100), "a second of pulling lets go");
        assertFalse(snap.snapped());
        assertEquals(MIDDLE + 30, snap.x(MIDDLE + 30, MIDDLE, 1200), "then it follows the mouse");
    }

    @Test
    void comingBackToTheMiddleEndsThePullAndSnapsAgain() {
        CentreSnap snap = new CentreSnap();
        snap.x(MIDDLE, MIDDLE, 0);
        snap.x(MIDDLE - 40, MIDDLE, 100);
        snap.x(MIDDLE, MIDDLE, 900);
        assertEquals(MIDDLE, snap.x(MIDDLE - 40, MIDDLE, 1200), "a new pull starts over");
        assertTrue(snap.snapped());
        snap.x(MIDDLE - 40, MIDDLE, 2200);
        assertFalse(snap.snapped(), "released after its own second");
        assertEquals(MIDDLE, snap.x(MIDDLE + 3, MIDDLE, 2300), "back near the middle: snapped again");
    }

    @Test
    void aNewDragStartsUnsnapped() {
        CentreSnap snap = new CentreSnap();
        snap.x(MIDDLE, MIDDLE, 0);
        snap.reset();
        assertFalse(snap.snapped());
        assertEquals(MIDDLE + 30, snap.x(MIDDLE + 30, MIDDLE, 10));
    }
}
