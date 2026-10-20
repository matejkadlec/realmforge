package io.github.matejkadlec.realmforge.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.RandomSource;

/**
 * How the level of a mob spawning in a dimension follows the nearest player, part of the dimension's
 * {@link DimensionRules} (RLM-240, architecture 14.1): a mob is up to {@link #spread()} levels below or above the
 * player, each offset with its weight from {@code weights} (lowest offset first, the middle one for the player's own
 * level), never below {@code entryLevel}, the realm's floor, nor above {@code maxLevel}. The floor and the cap drop
 * offsets, and the rest share the chance by their weights. A player below the floor meets every mob at it; a player so
 * far above the cap that no offset is left meets every mob at the cap. The pipeline computes the same
 * ({@code MobLevelRuleDef.chances}), and a GameTest compares the two at sample player levels.
 *
 * @param entryLevel the realm's floor: the level needed to enter it
 * @param weights the weight of each offset from the player's level, an odd number of them, each 1 to
 *     {@link #MAX_WEIGHT}
 */
public record MobLevelRule(int entryLevel, int maxLevel, List<Integer> weights) {
    /** The most offsets either side of the player's level, far beyond any sensible rule. */
    public static final int MAX_SPREAD = 10;
    /** The largest weight, so a sum of weights always fits an {@code int}. */
    public static final int MAX_WEIGHT = 1000;

    public static final Codec<MobLevelRule> CODEC = RecordCodecBuilder.<MobLevelRule>create(instance -> instance.group(
                    ExtraCodecs.POSITIVE_INT.fieldOf("entry_level").forGetter(MobLevelRule::entryLevel),
                    ExtraCodecs.POSITIVE_INT.fieldOf("max_level").forGetter(MobLevelRule::maxLevel),
                    Codec.intRange(1, MAX_WEIGHT).listOf(1, 2 * MAX_SPREAD + 1).fieldOf("weights")
                            .forGetter(MobLevelRule::weights))
            .apply(instance, MobLevelRule::new))
            .validate(MobLevelRule::validate);

    public MobLevelRule {
        weights = List.copyOf(weights);
    }

    /**
     * The levels a mob spawning near a player of one level may get: from {@code lowest} up, one weight per level, not
     * normalised.
     */
    public record Chances(int lowest, List<Integer> weights) {
        public int highest() {
            return lowest + weights.size() - 1;
        }

        /** The weight of {@code level}, 0 outside the range. */
        public int weight(int level) {
            return level < lowest || level > highest() ? 0 : weights.get(level - lowest);
        }

        /** A level drawn from {@code random} by the weights. */
        public int roll(RandomSource random) {
            int total = weights.stream().mapToInt(Integer::intValue).sum();
            int left = random.nextInt(total);
            for (int i = 0; i < weights.size(); i++) {
                left -= weights.get(i);
                if (left < 0) {
                    return lowest + i;
                }
            }
            throw new IllegalStateException("a roll below the total always lands on a level");
        }
    }

    /** How many levels a mob may be below or above the player. */
    public int spread() {
        return weights.size() / 2;
    }

    /** The levels a mob spawning near a player of {@code playerLevel} may get, with their weights. */
    public Chances chances(int playerLevel) {
        if (playerLevel < entryLevel) {
            return new Chances(entryLevel, List.of(1));
        }
        int lowest = Math.max(entryLevel, playerLevel - spread());
        int highest = Math.min(maxLevel, playerLevel + spread());
        if (lowest > highest) {
            return new Chances(maxLevel, List.of(1));
        }
        int start = lowest - playerLevel + spread();
        return new Chances(lowest, weights.subList(start, start + highest - lowest + 1));
    }

    /** The level of a mob spawning near a player of {@code playerLevel}, drawn from {@code random}. */
    public int levelFor(int playerLevel, RandomSource random) {
        return chances(playerLevel).roll(random);
    }

    private static DataResult<MobLevelRule> validate(MobLevelRule rule) {
        if (rule.entryLevel() > rule.maxLevel()) {
            return DataResult.error(() -> "entry_level " + rule.entryLevel() + " is above max_level "
                    + rule.maxLevel());
        }
        if (rule.weights().size() % 2 == 0) {
            return DataResult.error(() -> "weights needs an odd number of entries, the middle one for offset 0: "
                    + rule.weights());
        }
        return DataResult.success(rule);
    }
}
