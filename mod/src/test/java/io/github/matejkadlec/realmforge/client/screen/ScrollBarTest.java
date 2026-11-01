package io.github.matejkadlec.realmforge.client.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** RLM-542: the spellbook's Known Spells scrollbar. */
class ScrollBarTest {
    /** Nine known spells, five rows of 20 pixels visible: a 100-pixel track from y 50. */
    private static final ScrollBar NINE_OF_FIVE = new ScrollBar(50, 100, 9, 5);

    @Test
    void itShowsOnlyWhenTheRowsDoNotFit() {
        assertTrue(NINE_OF_FIVE.shown());
        assertFalse(new ScrollBar(50, 100, 5, 5).shown(), "every row fits");
        assertFalse(new ScrollBar(50, 100, 0, 5).shown(), "no row");
    }

    @Test
    void theThumbIsTheVisibleShare() {
        assertEquals(55, NINE_OF_FIVE.thumbHeight(), "5 of 9 rows: 100 × 5 / 9");
        assertEquals(ScrollBar.MIN_THUMB, new ScrollBar(0, 100, 500, 5).thumbHeight(), "never thinner than 8");
    }

    @Test
    void theThumbFollowsTheScroll() {
        assertEquals(4, NINE_OF_FIVE.maxScroll());
        assertEquals(50, NINE_OF_FIVE.thumbTop(0), "at the top");
        assertEquals(95, NINE_OF_FIVE.thumbTop(4), "at the bottom: 50 + 100 - 55");
        assertEquals(72, NINE_OF_FIVE.thumbTop(2), "halfway: 50 + 45 × 2 / 4, rounded down");
        assertEquals(95, NINE_OF_FIVE.thumbTop(9), "a scroll past the end stays at the bottom");
        assertTrue(NINE_OF_FIVE.onThumb(60, 0));
        assertFalse(NINE_OF_FIVE.onThumb(120, 0));
    }

    @Test
    void aDragTurnsIntoTheNearestRow() {
        assertEquals(0, NINE_OF_FIVE.scrollFor(50));
        assertEquals(4, NINE_OF_FIVE.scrollFor(95));
        assertEquals(2, NINE_OF_FIVE.scrollFor(72.5), "halfway");
        assertEquals(0, NINE_OF_FIVE.scrollFor(-30), "above the track");
        assertEquals(4, NINE_OF_FIVE.scrollFor(500), "below the track");
        for (int scroll = 0; scroll <= NINE_OF_FIVE.maxScroll(); scroll++) {
            assertEquals(scroll, NINE_OF_FIVE.scrollFor(NINE_OF_FIVE.thumbTop(scroll)), "round trip at " + scroll);
        }
    }
}
