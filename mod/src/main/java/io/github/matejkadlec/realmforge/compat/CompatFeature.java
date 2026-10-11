package io.github.matejkadlec.realmforge.compat;

import net.neoforged.bus.api.IEventBus;

/** Optional integrations, one subpackage per mod; their client parts live in {@code client/compat/} (architecture 19.4). */
public final class CompatFeature {
    private CompatFeature() {
    }

    /** Registers the feature's registries and event listeners. Called once from {@code Realmforge}. */
    public static void register(IEventBus modBus) {
    }
}
