package io.github.matejkadlec.realmforge.client.spell;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PendingLoweringsTest {
    @Test
    void aCastThatSimplyEndsLowersTheHandsAtTheNextTick() {
        PendingLowerings lowerings = new PendingLowerings();
        lowerings.ended(5);
        assertEquals(List.of(5), lowerings.due(id -> false));
    }

    /** RLM-454: the server ends the replaced cast first, then starts the new one; the hands never dip. */
    @Test
    void aCastThatAnotherReplacesKeepsTheHandsUp() {
        PendingLowerings lowerings = new PendingLowerings();
        lowerings.ended(5);
        assertEquals(List.of(), lowerings.due(id -> id == 5), "the replacing cast started before the tick");
    }

    @Test
    void aTickClearsEveryMark() {
        PendingLowerings lowerings = new PendingLowerings();
        lowerings.ended(5);
        lowerings.due(id -> id == 5);
        assertEquals(List.of(), lowerings.due(id -> false), "a mark lasts one tick, lowered or not");
    }

    @Test
    void eachCasterIsLoweredOnceInTheOrderItsCastEnded() {
        PendingLowerings lowerings = new PendingLowerings();
        lowerings.ended(7);
        lowerings.ended(5);
        lowerings.ended(7);
        lowerings.ended(9);
        Set<Integer> casting = Set.of(9);
        assertEquals(List.of(7, 5), lowerings.due(casting::contains));
    }

    @Test
    void clearForgetsTheMarks() {
        PendingLowerings lowerings = new PendingLowerings();
        lowerings.ended(5);
        lowerings.clear();
        assertEquals(List.of(), lowerings.due(id -> false));
    }
}
