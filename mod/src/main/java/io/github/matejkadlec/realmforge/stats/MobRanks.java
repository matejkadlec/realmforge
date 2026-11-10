package io.github.matejkadlec.realmforge.stats;

import java.util.function.ToDoubleFunction;
import net.minecraft.world.entity.LivingEntity;

/**
 * A mob's rank (architecture 14.1): how much more than a normal mob it counts for, 0.1 for a swarm mob, 1.5 for a
 * strong one, 10 for a dungeon boss, 20 for a realm guardian. It is the mob's kill XP multiplier, which
 * {@code character} owns and provides here at mod construction ({@link #rankBy}; architecture 4.4, lower asks higher
 * through a provider), so the features below {@code character} can read it: aim assistance prefers a higher rank
 * ({@code spell/AimAssist}), and a mob's coin drop grows with it ({@code loot/CoinDrops}). Like the entity level, a
 * fact many features read lives low.
 */
public final class MobRanks {
    /** What a mob counts as while no provider is set, and what a normal mob is: 1. */
    public static final double NORMAL = 1.0;

    private static volatile ToDoubleFunction<LivingEntity> source = entity -> NORMAL;

    private MobRanks() {
    }

    /** Sets where a mob's rank comes from: {@code character} provides the kill XP multiplier. */
    public static void rankBy(ToDoubleFunction<LivingEntity> provider) {
        source = provider;
    }

    /** The rank of {@code entity}: 1 for a normal mob, 0 for one that counts for nothing (the smallest slime). */
    public static double of(LivingEntity entity) {
        return source.applyAsDouble(entity);
    }
}
