package io.github.matejkadlec.realmforge.client.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** The magic shield's bar: a tier every 10 points, layered over the tier below (Matěj, 2026-10-14). */
class MagicShieldBarTest {
    private static final int FULL = MagicShieldBar.INNER_WIDTH;

    @Test
    void everyTenPointsIsTheNextTier() {
        assertEquals(-1, MagicShieldBar.tier(0), "an empty shield shows nothing");
        assertEquals(0, MagicShieldBar.tier(0.5F), "light blue");
        assertEquals(0, MagicShieldBar.tier(10), "10 points still fill the light blue tier");
        assertEquals(1, MagicShieldBar.tier(10.5F), "blue");
        assertEquals(2, MagicShieldBar.tier(25), "purple");
        assertEquals(3, MagicShieldBar.tier(40), "magenta");
        assertEquals(4, MagicShieldBar.tier(41), "red");
        assertEquals(4, MagicShieldBar.tier(500), "red stays the last tier");
    }

    @Test
    void theTopTierCoversItsShareOfTheBar() {
        assertEquals(FULL, MagicShieldBar.topWidth(10), "a full tier fills the bar");
        assertEquals(40, MagicShieldBar.topWidth(5), "half a tier, rounded up");
        assertEquals(40, MagicShieldBar.topWidth(25), "25 points: half of the purple tier over a full blue one");
        assertEquals(1, MagicShieldBar.topWidth(0.01F), "any points show");
        assertEquals(1, MagicShieldBar.topWidth(20.01F), "a new tier shows from its first point");
        assertEquals(0, MagicShieldBar.topWidth(0), "an empty shield shows nothing");
    }

    @Test
    void theLastTierFillsTheBarFromItsFullTierOn() {
        assertEquals(40, MagicShieldBar.topWidth(45));
        assertEquals(FULL, MagicShieldBar.topWidth(50));
        assertEquals(FULL, MagicShieldBar.topWidth(500), "and stays full above it");
    }

    @Test
    void theColoursAreLightBlueBluePurpleMagentaRed() {
        assertEquals(0x2D40B0FF, MagicShieldBar.fill(0), "light blue, faint");
        assertEquals(0x2DFF3030, MagicShieldBar.fill(4), "red, faint");
        assertEquals(0xFF9FD7FF, MagicShieldBar.border(0), "light blue halfway to white, opaque");
        assertEquals(0xFF8FA7FF, MagicShieldBar.border(1), "blue halfway to white");
        assertEquals(0xFFFF9797, MagicShieldBar.border(4), "red halfway to white");
    }
}
