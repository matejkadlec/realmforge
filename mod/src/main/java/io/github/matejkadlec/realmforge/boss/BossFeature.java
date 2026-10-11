package io.github.matejkadlec.realmforge.boss;

import net.neoforged.bus.api.IEventBus;

/** Boss definitions, the phase machine and the boss entity (architecture 14.3). */
public final class BossFeature {
    private BossFeature() {
    }

    /** Registers the feature's registries and event listeners. Called once from {@code Realmforge}. */
    public static void register(IEventBus modBus) {
    }
}
