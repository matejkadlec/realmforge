package io.github.matejkadlec.realmforge.dungeon.instance;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.matejkadlec.realmforge.generated.GeneratedTexts;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PortalTimerTest {
    @Test
    void warningsComeFiveMinutesOneMinuteAndTenSecondsBeforeThePortalCloses() {
        assertEquals(Optional.of(GeneratedTexts.MESSAGE_PORTAL_CLOSES_IN_5_MINUTES), PortalTimer.warning(6000));
        assertEquals(Optional.of(GeneratedTexts.MESSAGE_PORTAL_CLOSES_IN_1_MINUTE), PortalTimer.warning(1200));
        assertEquals(Optional.of(GeneratedTexts.MESSAGE_PORTAL_CLOSES_IN_10_SECONDS), PortalTimer.warning(200));
    }

    @Test
    void noWarningComesAtAnyOtherTime() {
        for (long left : new long[] {6001, 5999, 1201, 1199, 201, 199, 1, 0, 36_000}) {
            assertEquals(Optional.empty(), PortalTimer.warning(left), left + " ticks left");
        }
    }

    @Test
    void aCompletedRunHasFiveMinutesLeft() {
        assertEquals(6000L, PortalTimer.COMPLETED_TICKS);
    }
}
