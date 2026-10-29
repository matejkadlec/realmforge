package io.github.matejkadlec.realmforge.boss;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * {@code realmforge:health_below}: the boss's health has fallen below a share of its maximum.
 *
 * @param health the share, from 0 to 1 ({@code health_percent} in YAML)
 */
public record HealthBelow(float health) implements PhaseTrigger {
    public static final MapCodec<HealthBelow> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                    Codec.floatRange(0.0F, 1.0F).fieldOf("health").forGetter(HealthBelow::health))
            .apply(instance, HealthBelow::new));

    @Override
    public MapCodec<HealthBelow> codec() {
        return MAP_CODEC;
    }

    @Override
    public boolean holds(BossPhaseMachine.Progress progress) {
        return progress.health() < health;
    }
}
