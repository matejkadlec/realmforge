package io.github.matejkadlec.realmforge.effect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Damage and healing over time (architecture 11.4): when an effect ticks, and how much. */
class PeriodicTest {
    private static final double EPSILON = 1e-12;

    /** The ages at which an effect of {@code duration}, applied once, ticks: one server tick of age per tick it lasts. */
    private static List<Integer> ticks(Periodic periodic, int duration) {
        return ticks(periodic, List.of(new int[] {0, duration}));
    }

    /**
     * The ages at which an effect ticks when it is applied at each {@code {age, duration}} of {@code applications}
     * (the first at age 0), every application setting its remaining duration as vanilla's {@code update} does for a
     * longer one, and the effect running until its remaining duration is spent.
     */
    private static List<Integer> ticks(Periodic periodic, List<int[]> applications) {
        List<Integer> ticked = new ArrayList<>();
        int remaining = 0;
        int next = 0;
        for (int age = 0; next < applications.size() || remaining > 0; age++) {
            while (next < applications.size() && applications.get(next)[0] == age) {
                remaining = Math.max(remaining, applications.get(next)[1]);
                next++;
            }
            if (remaining > 0) {
                if (periodic.ticksAtAge(age + 1)) {
                    ticked.add(age + 1);
                }
                remaining--;
            }
        }
        return ticked;
    }

    @Test
    void itTicksAtTheEndOfEveryInterval() {
        Periodic burning = Periodic.damage("realmforge:fire", 2, 20);
        assertEquals(List.of(20, 40, 60, 80), ticks(burning, 80), "4 s: 4 ticks, the first after a second");
        assertEquals(4, burning.tickCount(80));
        assertEquals(List.of(1, 2, 3), ticks(Periodic.heal("realmforge:arcane", 1, 1), 3), "every tick");
    }

    @Test
    void aDurationOfPartIntervalsLosesThePartAfterItsLastTick() {
        // Validation keeps a spell's durations whole intervals; a command may not.
        assertEquals(List.of(20, 40), ticks(Periodic.damage("realmforge:fire", 2, 20), 50));
        assertEquals(2, Periodic.damage("realmforge:fire", 2, 20).tickCount(50));
    }

    /**
     * RLM-487: an application while the effect lasts lengthens it without moving its ticks, so an area or a channel
     * that applies it again sooner than it ticks (a hasted channel every 16 ticks, areas that pulse out of step) never
     * keeps it from ticking.
     */
    @Test
    void aFasterReapplicationKeepsThePaceOfTheFirst() {
        Periodic burning = Periodic.damage("realmforge:fire", 2, 20);
        List<int[]> hasted = List.of(new int[] {0, 40}, new int[] {16, 40}, new int[] {32, 40}, new int[] {48, 40});
        assertEquals(List.of(20, 40, 60, 80), ticks(burning, hasted),
                "applied every 16 ticks for 2 s: it ticks every second, until 2 s after the last application");
        List<int[]> outOfStep = List.of(new int[] {0, 20}, new int[] {10, 20}, new int[] {20, 20},
                new int[] {30, 20}, new int[] {40, 20});
        assertEquals(List.of(20, 40, 60), ticks(burning, outOfStep), "applied every half second: every second");
    }

    /** A recast loses only the partial interval after the last tick, as in Classic. */
    @Test
    void aRecastLosesOnlyThePartialIntervalAtTheEnd() {
        Periodic burning = Periodic.damage("realmforge:fire", 2, 20);
        assertEquals(List.of(20, 40, 60, 80), ticks(burning, List.of(new int[] {0, 80}, new int[] {15, 80})),
                "applied again at 15 for 4 s: it lasts until 95, its last tick at 80");
    }

    @Test
    void itNeverTicksBeforeItsFirstServerTick() {
        Periodic burning = Periodic.damage("realmforge:fire", 2, 20);
        assertFalse(burning.ticksAtAge(0));
        assertFalse(burning.ticksAtAge(-20));
        assertTrue(Periodic.heal("realmforge:arcane", 1, 1).ticksAtAge(1));
    }

    @Test
    void eachLevelAddsTheAmountAgain() {
        Periodic burning = Periodic.damage("realmforge:fire", 2, 20);
        assertEquals(2, burning.amountAt(0), EPSILON);
        assertEquals(6, burning.amountAt(2), EPSILON);
    }

    /** Scaling system C (RLM-534): a tick adds its share of the caster's Spell Power, and each level doubles that too. */
    @Test
    void aTickAddsItsShareOfSpellPower() {
        Periodic burning = Periodic.damage("realmforge:fire", 0.5, 0.03, 10);
        assertEquals(0.5 + 0.03 * 10, burning.amountAt(0, 10), EPSILON);
        assertEquals(2 * (0.5 + 0.03 * 10), burning.amountAt(1, 10), EPSILON, "Burning II");
        assertEquals(0.5, burning.amountAt(0), EPSILON, "without a caster, the bare amount");
        assertEquals(0.5, burning.amountAt(0, -3), EPSILON, "negative spell power counts as none");
    }

    @Test
    void aTickDealsOrHealsSomethingAndComesAfterATickOrMore() {
        assertThrows(IllegalArgumentException.class, () -> Periodic.damage("realmforge:fire", 0, 20));
        assertThrows(IllegalArgumentException.class, () -> Periodic.damage("realmforge:fire", 1, 0));
        assertThrows(IllegalArgumentException.class, () -> Periodic.heal("realmforge:fire", Double.NaN, 20));
        assertThrows(IllegalArgumentException.class, () -> Periodic.damage("realmforge:fire", 1, -0.1, 20));
    }
}
