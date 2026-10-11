package io.github.matejkadlec.realmforge.progression;

import net.neoforged.bus.api.IEventBus;

/** Chapters, criteria triggers and statistics: the bridge to quests (architecture 16). */
public final class ProgressionFeature {
    private ProgressionFeature() {
    }

    /** Registers the feature's registries and event listeners. Called once from {@code Realmforge}. */
    public static void register(IEventBus modBus) {
    }
}
