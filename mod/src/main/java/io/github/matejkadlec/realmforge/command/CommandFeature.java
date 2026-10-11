package io.github.matejkadlec.realmforge.command;

import net.neoforged.bus.api.IEventBus;

/** The {@code /realmforge} operator commands; developer commands live in the QA mod. */
public final class CommandFeature {
    private CommandFeature() {
    }

    /** Registers the feature's registries and event listeners. Called once from {@code Realmforge}. */
    public static void register(IEventBus modBus) {
    }
}
