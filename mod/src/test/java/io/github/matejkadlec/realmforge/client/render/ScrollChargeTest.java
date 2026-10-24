package io.github.matejkadlec.realmforge.client.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.matejkadlec.realmforge.character.Scrolls;
import org.junit.jupiter.api.Test;

class ScrollChargeTest {
    @Test
    void aScrollIsDrawnLikeABowOverTheWholeCharge() {
        assertEquals(0, ScrollCharge.drawPower(0), 1e-6);
        assertEquals(0, ScrollCharge.drawPower(-1), 1e-6, "before the first tick");
        assertEquals(1, ScrollCharge.drawPower(Scrolls.CHARGE_TICKS), 1e-6, "fully drawn when the charge is full");
        assertEquals(1, ScrollCharge.drawPower(Scrolls.CHARGE_TICKS + 5), 1e-6);
        float half = ScrollCharge.drawPower(Scrolls.CHARGE_TICKS / 2F);
        assertEquals(5 / 12F, half, 1e-6, "slow at first, as a bow: (0.5² + 2 · 0.5) / 3");
        for (int tick = 1; tick <= Scrolls.CHARGE_TICKS; tick++) {
            assertTrue(ScrollCharge.drawPower(tick) > ScrollCharge.drawPower(tick - 1), "drawn further every tick");
        }
    }
}
