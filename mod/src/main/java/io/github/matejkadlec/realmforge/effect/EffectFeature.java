package io.github.matejkadlec.realmforge.effect;

import net.neoforged.bus.api.IEventBus;

/** Behaviour classes of our status effects (architecture 11.4). */
public final class EffectFeature {
    private EffectFeature() {
    }

    /** Registers the feature's registries and event listeners. Called once from {@code Realmforge}. */
    public static void register(IEventBus modBus) {
    }
}
