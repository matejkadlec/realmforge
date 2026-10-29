package io.github.matejkadlec.realmforge.client.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.matejkadlec.realmforge.character.Scrolls;
import net.minecraft.world.InteractionHand;
import org.junit.jupiter.api.Test;

class ScrollChargeTest {
    private static final InteractionHand MAIN = InteractionHand.MAIN_HAND;
    private static final InteractionHand OFF = InteractionHand.OFF_HAND;

    @Test
    void theBowPoseFollowsTheUseFlagsAloneSoOtherPlayersSeeIt() {
        assertTrue(ScrollCharge.posed(true, MAIN, MAIN));
        assertTrue(ScrollCharge.posed(true, OFF, OFF), "a scroll charged in the off hand");
        assertFalse(ScrollCharge.posed(true, MAIN, OFF), "only the hand that charges");
        assertFalse(ScrollCharge.posed(false, MAIN, MAIN), "nothing in use");
    }

    @Test
    void theFirstPersonDrawNeedsChargeTicksLeft() {
        assertTrue(ScrollCharge.drawn(true, MAIN, MAIN, Scrolls.CHARGE_TICKS));
        assertTrue(ScrollCharge.drawn(true, MAIN, MAIN, 1));
        assertFalse(ScrollCharge.drawn(true, MAIN, MAIN, 0), "the charge is full");
        assertFalse(ScrollCharge.drawn(true, MAIN, MAIN, -5),
                "remaining ticks below 0, as another player's client counts them");
        assertFalse(ScrollCharge.drawn(true, MAIN, OFF, Scrolls.CHARGE_TICKS), "only the hand that charges");
        assertFalse(ScrollCharge.drawn(false, MAIN, MAIN, Scrolls.CHARGE_TICKS), "nothing in use");
    }
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
