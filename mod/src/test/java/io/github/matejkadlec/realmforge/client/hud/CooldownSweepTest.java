package io.github.matejkadlec.realmforge.client.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.matejkadlec.realmforge.client.hud.CooldownSweep.Span;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The cooldown sweep's dark wedge (C3b, RLM-25). */
class CooldownSweepTest {
    private static final int SIZE = 16;

    private static int darkPixels(List<Span> spans) {
        return spans.stream().mapToInt(span -> span.bottom() - span.top()).sum();
    }

    private static boolean dark(List<Span> spans, int x, int y) {
        return spans.stream().anyMatch(span -> span.x() == x && span.top() <= y && y < span.bottom());
    }

    @Test
    void aFreshCooldownCoversTheSlotAndAnEndedOneNothing() {
        List<Span> full = CooldownSweep.spans(SIZE, 1);
        assertEquals(SIZE * SIZE, darkPixels(full));
        assertEquals(SIZE, full.size(), "one span per column");
        assertEquals(List.of(), CooldownSweep.spans(SIZE, 0));
    }

    @Test
    void halfwayTheRightHalfIsClear() {
        List<Span> half = CooldownSweep.spans(SIZE, 0.5F);
        assertEquals(SIZE * SIZE / 2, darkPixels(half));
        assertTrue(dark(half, 0, 0) && dark(half, 7, 15) && !dark(half, 8, 0) && !dark(half, 15, 15));
    }

    @Test
    void theWedgeShrinksClockwiseFromTwelve() {
        List<Span> quarter = CooldownSweep.spans(SIZE, 0.75F);
        assertEquals(SIZE * SIZE * 3 / 4, darkPixels(quarter));
        assertTrue(!dark(quarter, 15, 0) && dark(quarter, 15, 15) && dark(quarter, 0, 0),
                "after a quarter, only the top right is clear");
        List<Span> nearlyDone = CooldownSweep.spans(SIZE, 0.05F);
        assertTrue(dark(nearlyDone, 6, 0) && !dark(nearlyDone, 0, 15), "near its end, a sliver left of twelve");
    }

    @Test
    void anglesRunClockwiseFromTwelve() {
        assertEquals(0, CooldownSweep.angle(0, -1), 1e-9);
        assertEquals(0.25, CooldownSweep.angle(1, 0), 1e-9);
        assertEquals(0.5, CooldownSweep.angle(0, 1), 1e-9);
        assertEquals(0.75, CooldownSweep.angle(-1, 0), 1e-9);
    }
}
