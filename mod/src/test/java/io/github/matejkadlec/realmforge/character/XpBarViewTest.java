package io.github.matejkadlec.realmforge.character;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.matejkadlec.realmforge.core.Curve;
import io.github.matejkadlec.realmforge.core.Curve.Point;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class XpBarViewTest {
    /** 100 XP from level 1, 200 from level 2, 300 from level 3 and on. */
    private static final Curve CURVE = new Curve(Optional.empty(), List.of(new Point(1, 100), new Point(3, 300)));

    @Test
    void theBarFillsWithTheXpGainedSinceTheLevelBegan() {
        XpBarView view = XpBarView.of(CURVE, 150, 2, 10);
        assertEquals(2, view.level());
        assertEquals(50, view.xpInLevel());
        assertEquals(200, view.xpToNext());
        assertEquals(0.25F, view.progress());
        assertFalse(view.capped());
    }

    @Test
    void aNewCharacterStartsEmpty() {
        assertEquals(0F, XpBarView.of(CURVE, 0, 1, 10).progress());
    }

    @Test
    void atTheCapTheBarBanksXpAndStaysFull() {
        XpBarView halfway = XpBarView.of(CURVE, 400, 3, 3);
        assertTrue(halfway.capped());
        assertEquals(100, halfway.xpInLevel());
        assertEquals(1F / 3, halfway.progress(), 1e-6);
        XpBarView banked = XpBarView.of(CURVE, 5000, 3, 3);
        assertEquals(4700, banked.xpInLevel());
        assertEquals(1F, banked.progress());
    }

    @Test
    void aLevelAheadOfItsXpShowsAnEmptyBar() {
        // A level set by hand (an old save, /qa) before its XP: nothing gained yet in it.
        assertEquals(0F, XpBarView.of(CURVE, 0, 5, 10).progress());
    }
}
