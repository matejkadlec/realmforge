package io.github.matejkadlec.realmforge.stats;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.junit.jupiter.api.Test;

/** RLM-171: how much of the food exhaustion a player gains their Stamina leaves. */
class ExhaustionReductionTest {
    private static final ExhaustionReduction STAMINA = new ExhaustionReduction(Attributes.LUCK, 0.01, 0.5);

    @Test
    void eachPointTakesItsShareUpToTheCap() {
        assertEquals(0.0, STAMINA.at(0));
        assertEquals(0.1, STAMINA.at(10), 1e-12);
        assertEquals(0.5, STAMINA.at(50), 1e-12);
        assertEquals(0.5, STAMINA.at(1_000), "the cap holds far past it");
        assertEquals(0.0, STAMINA.at(-5), "a negative value takes nothing away");
    }

    @Test
    void theFactorIsWhatTheStatsLeave() {
        assertEquals(1.0, factor(List.of(STAMINA), Map.of()), "no Stamina leaves all of it");
        assertEquals(0.7, factor(List.of(STAMINA), Map.of(Attributes.LUCK, 30.0)), 1e-12);
        assertEquals(0.5, factor(List.of(STAMINA), Map.of(Attributes.LUCK, 80.0)), 1e-12, "capped at half");
        assertEquals(1.0, factor(List.of(), Map.of(Attributes.LUCK, 80.0)), "no stat lowers it");
    }

    @Test
    void severalStatsAddUpAndNeverGoBelowNothing() {
        ExhaustionReduction other = new ExhaustionReduction(Attributes.ARMOR, 0.02, 0.8);
        assertEquals(0.3, factor(List.of(STAMINA, other), Map.of(Attributes.LUCK, 30.0, Attributes.ARMOR, 20.0)),
                1e-12);
        assertEquals(0.0, factor(List.of(STAMINA, other), Map.of(Attributes.LUCK, 80.0, Attributes.ARMOR, 80.0)),
                "half and four fifths take all of it, no more");
    }

    private static double factor(List<ExhaustionReduction> reductions, Map<Holder<Attribute>, Double> values) {
        return ExhaustionReduction.factor(reductions, stat -> values.getOrDefault(stat, 0.0));
    }
}
