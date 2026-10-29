package io.github.matejkadlec.realmforge.stats;

import io.github.matejkadlec.realmforge.generated.GeneratedStats;
import java.util.List;
import java.util.function.ToDoubleFunction;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;

/**
 * How a stat lowers the food exhaustion its player gains (architecture 8.1, RLM-171): Stamina, from
 * {@code lowers_exhaustion} in {@code content/stats/} through {@link GeneratedStats#LOWER_EXHAUSTION}. Each point takes
 * {@code perPoint} of the exhaustion away, at most {@code max}; the shares of several stats add up, at most all of it.
 * {@code mixin/PlayerExhaustionMixin} applies it to everything vanilla adds through {@code Player#causeFoodExhaustion}:
 * sprinting, jumping, swimming, attacking, taking damage, mining and the Hunger effect. Natural regeneration's food
 * cost ({@code FoodData#tick}) stays vanilla's.
 *
 * @param stat the stat, counted in points
 * @param perPoint the share of exhaustion each point takes away, a fraction
 * @param max the most the stat takes away, a fraction of at most 1
 */
public record ExhaustionReduction(Holder<Attribute> stat, double perPoint, double max) {
    /** The share of exhaustion {@code points} of the stat take away; the pipeline's {@code exhaustion_reduction_at}. */
    public double at(double points) {
        return Math.min(Math.max(points, 0) * perPoint, max);
    }

    /** The share of the exhaustion it gains that the entity's stats leave: 1 without them, 0.5 at Stamina's cap. */
    public static double factor(LivingEntity entity) {
        return factor(GeneratedStats.LOWER_EXHAUSTION,
                stat -> entity.getAttributes().hasAttribute(stat) ? entity.getAttributeValue(stat) : 0);
    }

    /** The share of exhaustion {@code reductions} leave at the stat values {@code value} gives: 1 less their sum. */
    public static double factor(List<ExhaustionReduction> reductions, ToDoubleFunction<Holder<Attribute>> value) {
        double taken = 0;
        for (ExhaustionReduction reduction : reductions) {
            taken += reduction.at(value.applyAsDouble(reduction.stat()));
        }
        return Math.max(0, 1 - taken);
    }
}
