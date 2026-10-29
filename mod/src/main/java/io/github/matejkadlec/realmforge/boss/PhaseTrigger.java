package io.github.matejkadlec.realmforge.boss;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;

/**
 * What enters a boss's phase from the phase before it, an entry of the type registry
 * {@code realmforge:phase_trigger_type} (architecture 5.3, 14.3), selected by its {@code type}: {@link HealthBelow} and
 * {@link TimeInPhase} ({@link PhaseTriggerTypes}). A trigger is pure: it reads the fight's {@link BossPhaseMachine.Progress},
 * never the world. The pipeline knows the same types ({@code tools/realmforge_tools/schema/boss.py}).
 */
public interface PhaseTrigger {
    /** A trigger by its {@code type}, e.g. {@code {"type": "realmforge:health_below", "health": 0.5}}. */
    Codec<PhaseTrigger> CODEC = BossRegistries.PHASE_TRIGGER_TYPES.byNameCodec().dispatch(PhaseTrigger::codec, c -> c);

    MapCodec<? extends PhaseTrigger> codec();

    /** Whether the fight, as it stands, enters the phase this trigger belongs to. */
    boolean holds(BossPhaseMachine.Progress progress);
}
