package io.github.matejkadlec.realmforge.client.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** The magic shield's bar: the share of the pool left, over a row of hearts. */
class MagicShieldBarTest {
    @Test
    void theBarIsTheShareLeftOfARowOfHearts() {
        assertEquals(MagicShieldBar.ROW_WIDTH, MagicShieldBar.width(20, 20), "a full shield covers the row");
        assertEquals(41, MagicShieldBar.width(10, 20), "half a shield, rounded up");
        assertEquals(1, MagicShieldBar.width(0.01F, 20), "any points left show");
    }

    @Test
    void anEmptyShieldOrNoneShowsNothing() {
        assertEquals(0, MagicShieldBar.width(0, 20));
        assertEquals(0, MagicShieldBar.width(5, 0), "points left after the stat is gone");
    }

    @Test
    void pointsAboveTheStatFillTheRowOnly() {
        assertEquals(MagicShieldBar.ROW_WIDTH, MagicShieldBar.width(30, 20));
    }
}
