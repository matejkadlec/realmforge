package io.github.matejkadlec.realmforge.mob.custom;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.matejkadlec.realmforge.mob.custom.CasterTactics.Move;
import org.junit.jupiter.api.Test;

class CasterTacticsTest {
    private static final double KEEP = 6;
    private static final double REACH = 16;

    @Test
    void aTargetNearerThanTheDistanceItKeepsMakesItBackAway() {
        assertEquals(Move.BACK_AWAY, CasterTactics.move(2, KEEP, REACH, true));
        assertEquals(Move.BACK_AWAY, CasterTactics.move(5.99, KEEP, REACH, true));
    }

    @Test
    void backingAwayComesBeforeLineOfSight() {
        assertEquals(Move.BACK_AWAY, CasterTactics.move(2, KEEP, REACH, false));
    }

    @Test
    void aTargetInReachAndInSightHoldsItWhereItIs() {
        assertEquals(Move.HOLD, CasterTactics.move(KEEP, KEEP, REACH, true));
        assertEquals(Move.HOLD, CasterTactics.move(9, KEEP, REACH, true));
        assertEquals(Move.HOLD, CasterTactics.move(REACH * CasterTactics.CLOSE_IN, KEEP, REACH, true));
    }

    @Test
    void aTargetNearTheEdgeOfReachOrOutOfSightMakesItCloseIn() {
        assertEquals(Move.CLOSE_IN, CasterTactics.move(REACH * CasterTactics.CLOSE_IN + 0.01, KEEP, REACH, true));
        assertEquals(Move.CLOSE_IN, CasterTactics.move(30, KEEP, REACH, true));
        assertEquals(Move.CLOSE_IN, CasterTactics.move(9, KEEP, REACH, false));
    }

    @Test
    void aCasterThatKeepsNoDistanceNeverBacksAway() {
        assertEquals(Move.HOLD, CasterTactics.move(0.5, 0, REACH, true));
        assertEquals(Move.CLOSE_IN, CasterTactics.move(0.5, 0, REACH, false));
    }

    @Test
    void aCasterWithoutSpellsInReachClosesIn() {
        assertEquals(Move.CLOSE_IN, CasterTactics.move(7, KEEP, 0, true));
    }
}
