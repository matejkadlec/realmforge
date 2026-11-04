package io.github.matejkadlec.realmforge.client.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** The Spell Bar tab's two columns (RLM-333), with Matěj's example of a column that shows 5 spells. */
class SpellColumnsTest {
    private static final int ROWS = 5;

    @Test
    void theFirstColumnFillsFirst() {
        assertEquals(0, SpellColumns.first(0, ROWS));
        assertEquals(3, SpellColumns.first(3, ROWS));
        assertEquals(5, SpellColumns.first(5, ROWS));
    }

    @Test
    void thenTheSecondColumnFills() {
        assertEquals(5, SpellColumns.first(6, ROWS));
        assertEquals(5, SpellColumns.first(10, ROWS));
    }

    /** Once both are full: 6-5, 6-6, 7-6, 7-7. */
    @Test
    void thenTheyTakeTurns() {
        assertEquals(6, SpellColumns.first(11, ROWS));
        assertEquals(6, SpellColumns.first(12, ROWS));
        assertEquals(7, SpellColumns.first(13, ROWS));
        assertEquals(7, SpellColumns.first(14, ROWS));
    }

    /** With 7 rows, the tab's size: the scrollbar's rows pass the visible ones only at 15 spells. */
    @Test
    void aScrollbarComesOnlyOnceBothAreFull() {
        assertEquals(0, new ScrollBar(0, 140, SpellColumns.first(14, 7), 7).maxScroll());
        assertEquals(1, new ScrollBar(0, 140, SpellColumns.first(15, 7), 7).maxScroll());
    }
}
