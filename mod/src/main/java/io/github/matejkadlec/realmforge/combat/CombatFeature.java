package io.github.matejkadlec.realmforge.combat;

import net.neoforged.bus.api.IEventBus;

/** The damage pipeline: crit, dodge, resistances (architecture 9). */
public final class CombatFeature {
    private CombatFeature() {
    }

    /** Registers the feature's registries and event listeners. Called once from {@code Realmforge}. */
    public static void register(IEventBus modBus) {
    }
}
