package io.github.matejkadlec.realmforge.boss;

import com.mojang.serialization.MapCodec;
import io.github.matejkadlec.realmforge.Realmforge;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The phase trigger types (architecture 5.3): {@code health_below} and {@code time_in_phase} (W5a). Architecture 5.3's
 * {@code adds_defeated} comes with a spell that summons adds. The pipeline knows the same ids
 * ({@code tools/realmforge_tools/schema/boss.py}).
 */
public final class PhaseTriggerTypes {
    private static final DeferredRegister<MapCodec<? extends PhaseTrigger>> TYPES =
            DeferredRegister.create(BossRegistries.PHASE_TRIGGER_TYPES, Realmforge.MOD_ID);

    /** {@code realmforge:health_below}: health below a share of the maximum ({@link HealthBelow}). */
    public static final DeferredHolder<MapCodec<? extends PhaseTrigger>, MapCodec<HealthBelow>> HEALTH_BELOW =
            TYPES.register("health_below", () -> HealthBelow.MAP_CODEC);

    /** {@code realmforge:time_in_phase}: a time spent in the phase before ({@link TimeInPhase}). */
    public static final DeferredHolder<MapCodec<? extends PhaseTrigger>, MapCodec<TimeInPhase>> TIME_IN_PHASE =
            TYPES.register("time_in_phase", () -> TimeInPhase.MAP_CODEC);

    private PhaseTriggerTypes() {
    }

    static void register(IEventBus modBus) {
        TYPES.register(modBus);
    }
}
