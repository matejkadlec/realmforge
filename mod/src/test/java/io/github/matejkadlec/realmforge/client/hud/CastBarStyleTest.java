package io.github.matejkadlec.realmforge.client.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CastBarStyleTest {
    private static final int PHYSICAL = 0xB2560D;

    @Test
    void theFillIsLighterAtTheTopAndDarkerAtTheBottom() {
        int rows = 16;
        assertTrue(CastBarStyle.rowShade(0, rows) > CastBarStyle.rowShade(3, rows));
        assertEquals(0, CastBarStyle.rowShade(rows / 2, rows), "a band in the middle keeps the colour");
        assertTrue(CastBarStyle.rowShade(rows - 1, rows) < CastBarStyle.rowShade(rows - 4, rows));
        Set<Integer> shades = new HashSet<>();
        for (int row = 0; row < rows; row++) {
            shades.add(CastBarStyle.shade(PHYSICAL, CastBarStyle.rowShade(row, rows)));
        }
        assertTrue(shades.size() >= 5, "at least five shades: " + shades.size());
    }

    @Test
    void shadesMoveTowardWhiteOrBlack() {
        assertEquals(0xFFB2560D, CastBarStyle.shade(PHYSICAL, 0));
        assertEquals(0xFFFFFFFF, CastBarStyle.shade(PHYSICAL, 1));
        assertEquals(0xFF000000, CastBarStyle.shade(PHYSICAL, -1));
        assertEquals(0xFFD9AB86, CastBarStyle.shade(PHYSICAL, 0.5F));
    }

    @Test
    void theTimeReadsElapsedOverTotalWithOneDecimal() {
        assertEquals("0.0 / 2.0", CastBarStyle.time(0, 40));
        assertEquals("0.9 / 2.0", CastBarStyle.time(19, 40), "elapsed rounds down");
        assertEquals("1.2 / 2.5", CastBarStyle.time(24, 50));
        assertEquals("2.0 / 2.0", CastBarStyle.time(45, 40), "never past the total");
        assertEquals("0.0 / 1.3", CastBarStyle.time(-3, 25), "a total rounds half up");
    }
}
