package io.github.matejkadlec.realmforge.dungeon;

import net.neoforged.bus.api.IEventBus;

/** Dungeon types, planner, layout, builder and instances (architecture 15). */
public final class DungeonFeature {
    private DungeonFeature() {
    }

    /** Registers the feature's registries and event listeners. Called once from {@code Realmforge}. */
    public static void register(IEventBus modBus) {
    }
}
