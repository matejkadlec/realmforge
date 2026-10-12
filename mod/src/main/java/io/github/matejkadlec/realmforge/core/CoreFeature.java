package io.github.matejkadlec.realmforge.core;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DataPackRegistryEvent;

/** Shared infrastructure: registry keys, codec helpers, requirements, curves (architecture 4.2, 5.3). */
public final class CoreFeature {
    private CoreFeature() {
    }

    /** Registers the feature's registries and event listeners. Called once from {@code Realmforge}. */
    public static void register(IEventBus modBus) {
        modBus.addListener(DataPackRegistryEvent.NewRegistry.class, CoreRegistries::registerDataPackRegistries);
    }
}
