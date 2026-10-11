package io.github.matejkadlec.realmforge.stats;

import net.neoforged.bus.api.IEventBus;

/** Attributes, entity levels, stat sources, derived stats and caps (architecture 8). */
public final class StatsFeature {
    private StatsFeature() {
    }

    /** Registers the feature's registries and event listeners. Called once from {@code Realmforge}. */
    public static void register(IEventBus modBus) {
    }
}
