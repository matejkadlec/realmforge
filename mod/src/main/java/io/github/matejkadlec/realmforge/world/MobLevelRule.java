package io.github.matejkadlec.realmforge.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.RandomSource;

/**
 * How the level of a mob spawning in a dimension follows from where it spawns, part of the dimension's
 * {@link DimensionRules} (architecture 14.1): {@code originLevel} at the dimension's origin (x 0, z 0), one level more
 * every {@code blocksPerLevel} blocks of horizontal distance from it, more with {@link Depth depth}, a random
 * {@code jitter} of up to that many levels either way, all clamped to {@code minLevel .. maxLevel}. The pipeline
 * computes the same ({@code MobLevelRuleDef.level_at}), and a GameTest compares the two at sample positions.
 *
 * @param depth levels added below a height, when present
 * @param jitter the most a mob's level differs from the rule's at random, either way; 0 for none
 */
public record MobLevelRule(int originLevel, int blocksPerLevel, Optional<Depth> depth, int jitter, int minLevel,
        int maxLevel) {
    /** The largest jitter, far beyond any band, so a roll's range always fits an {@code int}. */
    public static final int MAX_JITTER = 1000;

    public static final Codec<MobLevelRule> CODEC = RecordCodecBuilder.<MobLevelRule>create(instance -> instance.group(
                    ExtraCodecs.POSITIVE_INT.fieldOf("origin_level").forGetter(MobLevelRule::originLevel),
                    ExtraCodecs.POSITIVE_INT.fieldOf("blocks_per_level").forGetter(MobLevelRule::blocksPerLevel),
                    Depth.CODEC.optionalFieldOf("depth").forGetter(MobLevelRule::depth),
                    Codec.intRange(0, MAX_JITTER).optionalFieldOf("jitter", 0).forGetter(MobLevelRule::jitter),
                    ExtraCodecs.POSITIVE_INT.fieldOf("min_level").forGetter(MobLevelRule::minLevel),
                    ExtraCodecs.POSITIVE_INT.fieldOf("max_level").forGetter(MobLevelRule::maxLevel))
            .apply(instance, MobLevelRule::new))
            .validate(MobLevelRule::validate);

    /** Deeper is stronger: below {@code belowY}, one level more for every {@code blocksPerLevel} blocks down. */
    public record Depth(int belowY, int blocksPerLevel) {
        public static final Codec<Depth> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                        Codec.INT.fieldOf("below_y").forGetter(Depth::belowY),
                        ExtraCodecs.POSITIVE_INT.fieldOf("blocks_per_level").forGetter(Depth::blocksPerLevel))
                .apply(instance, Depth::new));
    }

    /** The level at a position before the jitter and the clamp. */
    public double unclampedLevelAt(double x, double y, double z) {
        double distance = Math.sqrt(x * x + z * z);
        double level = originLevel + Math.floor(distance / blocksPerLevel);
        if (depth.isPresent() && y < depth.get().belowY()) {
            level += Math.floor((depth.get().belowY() - y) / depth.get().blocksPerLevel());
        }
        return level;
    }

    /** The level of a mob spawning at a position whose jitter came out as {@code offset}: clamped to the range. */
    public int levelAt(double x, double y, double z, int offset) {
        return (int) Math.min(Math.max(unclampedLevelAt(x, y, z) + offset, minLevel), maxLevel);
    }

    /** The level of a mob spawning at a position, with a jitter drawn from {@code random}. */
    public int levelAt(double x, double y, double z, RandomSource random) {
        return levelAt(x, y, z, jitter == 0 ? 0 : random.nextIntBetweenInclusive(-jitter, jitter));
    }

    private static DataResult<MobLevelRule> validate(MobLevelRule rule) {
        if (rule.minLevel() > rule.maxLevel()) {
            return DataResult.error(() -> "min_level " + rule.minLevel() + " is above max_level " + rule.maxLevel());
        }
        if (rule.originLevel() < rule.minLevel() || rule.originLevel() > rule.maxLevel()) {
            return DataResult.error(() -> "origin_level " + rule.originLevel() + " is outside min_level "
                    + rule.minLevel() + " .. max_level " + rule.maxLevel());
        }
        return DataResult.success(rule);
    }
}
