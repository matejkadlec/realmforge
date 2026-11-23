package io.github.matejkadlec.realmforge.client.spell;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.matejkadlec.realmforge.client.spell.PendingLowerings.Due;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PendingLoweringsTest {
    @Test
    void aCastThatSimplyEndsLowersTheHandsAtTheNextTick() {
        PendingLowerings lowerings = new PendingLowerings();
        lowerings.ended(5);
        assertEquals(List.of(new Due(5, false)), lowerings.due(id -> false));
    }

    /** RLM-454: the server ends the replaced cast first, then starts the new one; the hands never dip. */
    @Test
    void aCastThatAnotherReplacesKeepsTheHandsUp() {
        PendingLowerings lowerings = new PendingLowerings();
        lowerings.ended(5);
        assertEquals(List.of(), lowerings.due(id -> id == 5), "the replacing cast started before the tick");
    }

    /** RLM-662: a formed bolt with nothing after it still leaves the hands in the release, a tick after its end. */
    @Test
    void aFormedCastWithNoCastAfterItPlaysTheRelease() {
        PendingLowerings lowerings = new PendingLowerings();
        lowerings.released(5);
        assertEquals(List.of(new Due(5, true)), lowerings.due(id -> false));
    }

    /**
     * RLM-662: the spell queue starts the next Frostbolt in the server tick the formed one completes, so its start
     * arrives before the next client tick; nothing is due, the release is never played and the running cast animation
     * keeps the pose, with no dip.
     */
    @Test
    void aFormedCastThatTheQueueFollowsAtOnceKeepsTheCastPose() {
        PendingLowerings lowerings = new PendingLowerings();
        lowerings.released(5);
        assertEquals(List.of(), lowerings.due(id -> id == 5), "the queued cast started before the tick");
        assertEquals(List.of(), lowerings.due(id -> false), "and the release is not played later either");
    }

    @Test
    void theLatestEndOfACasterDecides() {
        PendingLowerings lowerings = new PendingLowerings();
        lowerings.released(5);
        lowerings.ended(5);
        lowerings.ended(7);
        lowerings.released(7);
        assertEquals(List.of(new Due(5, false), new Due(7, true)), lowerings.due(id -> false));
    }

    @Test
    void aTickClearsEveryMark() {
        PendingLowerings lowerings = new PendingLowerings();
        lowerings.ended(5);
        lowerings.released(7);
        lowerings.due(id -> id == 5);
        assertEquals(List.of(), lowerings.due(id -> false), "a mark lasts one tick, lowered, released or not");
    }

    @Test
    void eachCasterIsLoweredOnceInTheOrderItsCastEnded() {
        PendingLowerings lowerings = new PendingLowerings();
        lowerings.ended(7);
        lowerings.ended(5);
        lowerings.ended(7);
        lowerings.ended(9);
        Set<Integer> casting = Set.of(9);
        assertEquals(List.of(new Due(7, false), new Due(5, false)), lowerings.due(casting::contains));
    }

    @Test
    void clearForgetsTheMarks() {
        PendingLowerings lowerings = new PendingLowerings();
        lowerings.ended(5);
        lowerings.released(7);
        lowerings.clear();
        assertEquals(List.of(), lowerings.due(id -> false));
    }
}
