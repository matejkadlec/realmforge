package io.github.matejkadlec.realmforge.effect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Damage and healing over time (architecture 11.4): when an effect ticks, and how much. */
class PeriodicTest {
    private static final double EPSILON = 1e-12;

    /** The remaining durations at which an effect of {@code duration} ticks, counting down as vanilla does. */
    private static List<Integer> ticks(Periodic periodic, int duration) {
        List<Integer> elapsed = new ArrayList<>();
        for (int remaining = duration; remaining >= 1; remaining--) {
            if (periodic.ticksAt(remaining)) {
                elapsed.add(duration - remaining + 1);
            }
        }
        return elapsed;
    }

    @Test
    void itTicksAtTheEndOfEveryInterval() {
        Periodic burning = Periodic.damage("realmforge:fire", 2, 20);
        assertEquals(List.of(20, 40, 60, 80), ticks(burning, 80), "4 s: 4 ticks, the first after a second");
        assertEquals(4, burning.tickCount(80));
        assertEquals(List.of(1, 2, 3), ticks(Periodic.heal("realmforge:arcane", 1, 1), 3), "every tick");
    }

    @Test
    void aDurationOfPartIntervalsTicksOnItsLastTick() {
        // Validation keeps a spell's durations whole intervals; a command may not.
        assertEquals(List.of(10, 30, 50), ticks(Periodic.damage("realmforge:fire", 2, 20), 50));
        assertEquals(2, Periodic.damage("realmforge:fire", 2, 20).tickCount(50));
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
