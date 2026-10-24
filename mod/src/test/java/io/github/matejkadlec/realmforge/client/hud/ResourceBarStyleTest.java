package io.github.matejkadlec.realmforge.client.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.matejkadlec.realmforge.Realmforge;
import org.junit.jupiter.api.Test;

class ResourceBarStyleTest {
    private static final int ROWS = 11;

    @Test
    void eachResourceHasItsColoursInOneTable() {
        assertEquals(new ResourceBarStyle.Colors(0x1E90FF, 0x0B3D85), ResourceBarStyle.colors(Realmforge.id("mana")));
        assertEquals(new ResourceBarStyle.Colors(0xF5241B, 0x780C08), ResourceBarStyle.colors(Realmforge.id("rage")));
        assertEquals(ResourceBarStyle.FALLBACK, ResourceBarStyle.colors(Realmforge.id("energy")),
                "a resource without an entry is gray until it gets one");
        for (ResourceBarStyle.Colors colors : ResourceBarStyle.COLORS.values()) {
            assertTrue(brightness(colors.edge()) < brightness(colors.fill()), "the leading edge is darker");
        }
    }

    @Test
    void theFillIsDarkerAtTheTopAndBottomEdgesAndLighterInTheMiddle() {
        float middle = ResourceBarStyle.rowShade(ROWS / 2, ROWS);
        assertTrue(middle > 0, "the middle row is lightened");
        assertTrue(ResourceBarStyle.rowShade(0, ROWS) < 0, "the top row is darkened");
        assertTrue(ResourceBarStyle.rowShade(ROWS - 1, ROWS) < 0, "the bottom row is darkened");
        for (int row = 0; row < ROWS; row++) {
            assertEquals(ResourceBarStyle.rowShade(row, ROWS), ResourceBarStyle.rowShade(ROWS - 1 - row, ROWS), 1e-6,
                    "the top and bottom mirror each other");
            assertTrue(ResourceBarStyle.rowShade(row, ROWS) <= middle);
        }
    }

    @Test
    void aFillCoversItsShareOfThePixelsWithAnyResourceVisibleAndOnlyAFullPoolFull() {
        assertEquals(0, ResourceBarStyle.fillWidth(0, 214));
        assertEquals(214, ResourceBarStyle.fillWidth(1, 214));
        assertEquals(107, ResourceBarStyle.fillWidth(0.5, 214));
        assertEquals(1, ResourceBarStyle.fillWidth(0.001, 214), "a sliver still shows");
        assertEquals(213, ResourceBarStyle.fillWidth(0.999, 214), "a nearly full pool is not drawn full");
    }

    @Test
    void theFillEasesToANewValueIn150Milliseconds() {
        assertEquals(20, ResourceBarStyle.animate(20, 80, 0), 1e-9);
        double halfway = ResourceBarStyle.animate(20, 80, 75);
        assertTrue(halfway > 50 && halfway < 80, "eases out: past the middle at half the time, " + halfway);
        assertEquals(80, ResourceBarStyle.animate(20, 80, 150), 1e-9);
        assertEquals(80, ResourceBarStyle.animate(20, 80, 1000), 1e-9);
        assertEquals(80, ResourceBarStyle.animate(20, 80, -5), 1e-9, "a clock that went back shows the target");
        assertTrue(ResourceBarStyle.animate(80, 20, 75) < 50, "and the same when emptying");
    }

    private static int brightness(int rgb) {
        return (rgb >> 16 & 0xFF) + (rgb >> 8 & 0xFF) + (rgb & 0xFF);
    }
}
