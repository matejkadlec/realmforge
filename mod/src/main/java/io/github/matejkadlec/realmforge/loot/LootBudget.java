package io.github.matejkadlec.realmforge.loot;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * The points a container may draw (RLM-289; the container loot design's minimum, medium and maximum): half of all
 * rolls lie between {@code min} and {@code medium}, the other half between {@code medium} and {@code max}, each half
 * evenly, so {@code medium} is the roll in the middle and a high roll is rare.
 */
public record LootBudget(int min, int medium, int max) {
    public static final Codec<LootBudget> CODEC = RecordCodecBuilder.<LootBudget>create(instance -> instance.group(
            Codec.intRange(1, Integer.MAX_VALUE).fieldOf("min").forGetter(LootBudget::min),
            Codec.intRange(1, Integer.MAX_VALUE).fieldOf("medium").forGetter(LootBudget::medium),
            Codec.intRange(1, Integer.MAX_VALUE).fieldOf("max").forGetter(LootBudget::max)
    ).apply(instance, LootBudget::new)).validate(budget -> budget.min <= budget.medium && budget.medium <= budget.max
            ? DataResult.success(budget)
            : DataResult.error(() -> "A budget's min, medium and max rise: " + budget));

    /** The budget of the roll {@code roll}, from 0 (the least) to 1 (the most). */
    public double at(double roll) {
        double clamped = Math.clamp(roll, 0.0, 1.0);
        return clamped <= 0.5 ? min + (medium - min) * (clamped * 2) : medium + (max - medium) * (clamped * 2 - 1);
    }

    /**
     * The points of a roll for an opener: {@link #at} times {@code percent} of a hundred (the opener's world tier),
     * times 1 + {@code luck} × {@code luckPercent} of a hundred, never below 0 for bad luck, rounded half up.
     */
    public int points(double roll, double percent, double luck, double luckPercent) {
        double lucky = Math.max(0.0, 1.0 + luck * luckPercent / 100.0);
        return (int) Math.min(Integer.MAX_VALUE, Math.round(at(roll) * percent / 100.0 * lucky));
    }
}
