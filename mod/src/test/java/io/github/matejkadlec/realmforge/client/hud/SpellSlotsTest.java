package io.github.matejkadlec.realmforge.client.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Optional;
import org.junit.jupiter.api.Test;

/** A spell slot's shape (A1, RLM-642): a square, or a passive spell's medallion with its corners cut. */
class SpellSlotsTest {
    @Test
    void aSquareSlotLosesNothing() {
        assertEquals(0, SpellSlots.cut(Optional.empty(), 24), "an empty slot is a square");
        for (int row = 0; row < 24; row++) {
            assertEquals(0, SpellSlots.indent(row, 24, 0), "row " + row);
        }
    }

    @Test
    void aCutSlotsRowsFollowTheCornersDiagonals() {
        int[] indents = new int[24];
        for (int row = 0; row < 24; row++) {
            indents[row] = SpellSlots.indent(row, 24, 4);
        }
        // Four pixels at the top and the bottom rows, one less each row inward, none in between.
        assertEquals(4, indents[0]);
        assertEquals(3, indents[1]);
        assertEquals(1, indents[3]);
        assertEquals(0, indents[4]);
        assertEquals(0, indents[12]);
        for (int row = 0; row < 24; row++) {
            assertEquals(indents[row], indents[23 - row], "the slot is the same upside down, at row " + row);
        }
    }

    @Test
    void theSpellListsSmallSlotKeepsItsIconInside() {
        // An 18 pixel slot loses 3 pixels at a corner; its 16 pixel icon starts 1 pixel in, where a row is whole
        // from the third row on.
        assertEquals(3, SpellSlots.indent(0, 18, 3));
        assertEquals(0, SpellSlots.indent(3, 18, 3));
    }
}
