package io.github.matejkadlec.realmforge.core;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DataPackRegistryEvent;
import net.neoforged.neoforge.registries.NewRegistryEvent;

/** Shared infrastructure: registry keys, codec helpers, requirements, curves (architecture 4.2, 5.3). */
public final class CoreFeature {
    private CoreFeature() {
    }

    /** Registers the feature's registries and event listeners. Called once from {@code Realmforge}. */
    public static void register(IEventBus modBus) {
        modBus.addListener(NewRegistryEvent.class, CoreRegistries::registerTypeRegistries);
        modBus.addListener(DataPackRegistryEvent.NewRegistry.class, CoreRegistries::registerDataPackRegistries);
        RequirementTypes.register(modBus);
    }
}
