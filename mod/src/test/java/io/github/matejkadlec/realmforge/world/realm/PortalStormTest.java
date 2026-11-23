package io.github.matejkadlec.realmforge.world.realm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.util.ARGB;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;

/** A realm portal's storm blink (RLM-300): dark, back, dark again, then over. */
class PortalStormTest {
    @Test
    void aBlinkDarkensThePortalTwiceInARow() {
        StringBuilder blink = new StringBuilder();
        for (long ticks = -1; ticks <= PortalStorm.LENGTH; ticks++) {
            blink.append(PortalStorm.dark(ticks) ? '#' : '.');
        }
        // One tick before it begins, its nine ticks, and the tick after.
        assertEquals(".###...###.", blink.toString());
        assertEquals(9, PortalStorm.LENGTH);
    }

    @Test
    void aBlinkIsOverAfterItsLengthAndNeverRunsBackwards() {
        assertFalse(PortalStorm.over(0));
        assertFalse(PortalStorm.over(PortalStorm.LENGTH - 1));
        assertTrue(PortalStorm.over(PortalStorm.LENGTH));
        assertTrue(PortalStorm.over(-1), "a game time the server set back: the blink is dropped");
        assertFalse(PortalStorm.dark(PortalStorm.LENGTH + 100));
    }

    @Test
    void aDarkPortalIsPulledTowardsDarkGreyAndKeepsItsAlpha() {
        int slate = 0xFF7A8FB0;
        int dark = PortalStorm.darken(slate);
        assertEquals(0xFF, ARGB.alpha(dark));
        assertTrue(ARGB.red(dark) < ARGB.red(slate) && ARGB.green(dark) < ARGB.green(slate)
                && ARGB.blue(dark) < ARGB.blue(slate), "every channel is darker");
        // Towards grey: the channels move closer together.
        assertTrue(ARGB.blue(dark) - ARGB.red(dark) < ARGB.blue(slate) - ARGB.red(slate));
        assertEquals(0x80, ARGB.alpha(PortalStorm.darken(0x807A8FB0)));
    }

    @Test
    void blinksComeAboutEveryFifteenSeconds() {
        RandomSource random = RandomSource.create(42);
        int blinks = 0;
        int ticks = 600_000;
        for (int tick = 0; tick < ticks; tick++) {
            if (PortalStorm.starts(random)) {
                blinks++;
            }
        }
        double mean = (double) ticks / blinks;
        assertTrue(Math.abs(mean - PortalStorm.MEAN_TICKS) < 30, "a blink every " + mean + " ticks");
    }
}
