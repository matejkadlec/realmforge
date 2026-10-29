package io.github.matejkadlec.realmforge.boss;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.ExtraCodecs;

/**
 * {@code realmforge:time_in_phase}: the boss has spent a while in the phase before: an enrage timer, or a phase that
 * ends by itself.
 *
 * @param afterTicks how long the phase before lasts, in ticks ({@code after_seconds} in YAML)
 */
public record TimeInPhase(int afterTicks) implements PhaseTrigger {
    public static final MapCodec<TimeInPhase> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                    ExtraCodecs.POSITIVE_INT.fieldOf("after_ticks").forGetter(TimeInPhase::afterTicks))
            .apply(instance, TimeInPhase::new));

    @Override
    public MapCodec<TimeInPhase> codec() {
        return MAP_CODEC;
    }

    @Override
    public boolean holds(BossPhaseMachine.Progress progress) {
        return progress.ticksInPhase() >= afterTicks;
    }
}
