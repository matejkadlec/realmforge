package io.github.matejkadlec.realmforge.mob.custom;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.matejkadlec.realmforge.mob.custom.CasterTactics.Move;
import org.junit.jupiter.api.Test;

class CasterTacticsTest {
    private static final double REACH = 8;

    @Test
    void aTargetInReachAndInSightHoldsItWhereItIs() {
        assertEquals(Move.HOLD, CasterTactics.move(0.5, REACH, true, false));
        assertEquals(Move.HOLD, CasterTactics.move(6, REACH, true, false));
        assertEquals(Move.HOLD, CasterTactics.move(REACH, REACH, true, false));
    }

    @Test
    void aTargetOutOfReachOrOutOfSightMakesItCloseIn() {
        assertEquals(Move.CLOSE_IN, CasterTactics.move(REACH + 0.01, REACH, true, false));
        assertEquals(Move.CLOSE_IN, CasterTactics.move(30, REACH, true, false));
        assertEquals(Move.CLOSE_IN, CasterTactics.move(2, REACH, false, false));
    }

    @Test
    void aCasterClosingInGoesOnUntilItsTargetIsWellInReach() {
        assertEquals(Move.CLOSE_IN, CasterTactics.move(REACH, REACH, true, true));
        assertEquals(Move.CLOSE_IN, CasterTactics.move(REACH * CasterTactics.CLOSE_IN + 0.01, REACH, true, true));
        assertEquals(Move.HOLD, CasterTactics.move(REACH * CasterTactics.CLOSE_IN, REACH, true, true));
    }

    @Test
    void aCasterWithoutSpellsInReachClosesIn() {
        assertEquals(Move.CLOSE_IN, CasterTactics.move(7, 0, true, false));
    }
}
