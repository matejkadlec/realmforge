package io.github.matejkadlec.realmforge.client.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** The magic shield's bar: a tier every 10 points, layered over the tier below (Matěj, 2026-10-14). */
class MagicShieldBarTest {
    private static final int FULL = MagicShieldBar.INNER_WIDTH;

    @Test
    void everyTenPointsIsTheNextTier() {
        assertEquals(-1, MagicShieldBar.tier(0), "an empty shield shows nothing");
        assertEquals(0, MagicShieldBar.tier(0.5F), "cyan");
        assertEquals(0, MagicShieldBar.tier(10), "10 points still fill the cyan tier");
        assertEquals(1, MagicShieldBar.tier(10.5F), "ocean");
        assertEquals(2, MagicShieldBar.tier(25), "blue");
        assertEquals(3, MagicShieldBar.tier(40), "violet");
        assertEquals(4, MagicShieldBar.tier(41), "magenta");
        assertEquals(5, MagicShieldBar.tier(55), "raspberry");
        assertEquals(5, MagicShieldBar.tier(500), "raspberry stays the last tier");
    }

    @Test
    void theTopTierCoversItsShareOfTheBar() {
        assertEquals(FULL, MagicShieldBar.topWidth(10), "a full tier fills the bar");
        assertEquals(40, MagicShieldBar.topWidth(5), "half a tier, rounded up");
        assertEquals(40, MagicShieldBar.topWidth(25), "25 points: half of the blue tier over a full ocean one");
        assertEquals(1, MagicShieldBar.topWidth(0.01F), "any points show");
        assertEquals(1, MagicShieldBar.topWidth(20.01F), "a new tier shows from its first point");
        assertEquals(0, MagicShieldBar.topWidth(0), "an empty shield shows nothing");
    }

    @Test
    void theLastTierFillsTheBarFromItsFullTierOn() {
        assertEquals(40, MagicShieldBar.topWidth(55));
        assertEquals(FULL, MagicShieldBar.topWidth(60));
        assertEquals(FULL, MagicShieldBar.topWidth(500), "and stays full above it");
    }

    @Test
    void theColoursAreTheColourWheelsEdge() {
        assertEquals(0x7300FFFF, MagicShieldBar.fill(0), "cyan, translucent");
        assertEquals(0x73FF007D, MagicShieldBar.fill(5), "raspberry, translucent");
        assertEquals(0xFF7FFFFF, MagicShieldBar.border(0), "cyan halfway to white, opaque");
        assertEquals(0xFF7F7FFF, MagicShieldBar.border(2), "blue halfway to white");
        assertEquals(0xFFFF7FBE, MagicShieldBar.border(5), "raspberry halfway to white");
    }
}
