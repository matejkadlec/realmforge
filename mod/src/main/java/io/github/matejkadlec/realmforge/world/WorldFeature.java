package io.github.matejkadlec.realmforge.world;

import net.neoforged.bus.api.IEventBus;

/** Dimension rules and access, world defaults, Dark Vision, griefing protection (architecture 15.1, 19.1). */
public final class WorldFeature {
    private WorldFeature() {
    }

    /** Registers the feature's registries and event listeners. Called once from {@code Realmforge}. */
    public static void register(IEventBus modBus) {
    }
}
