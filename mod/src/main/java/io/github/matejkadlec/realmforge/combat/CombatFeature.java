package io.github.matejkadlec.realmforge.combat;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DataPackRegistryEvent;

/** The damage pipeline: spell schools, crit, dodge, resistances, our armor formula, combat text (architecture 9). */
public final class CombatFeature {
    private CombatFeature() {
    }

    /** Registers the feature's registries and event listeners. Called once from {@code Realmforge}. */
    public static void register(IEventBus modBus) {
        modBus.addListener(DataPackRegistryEvent.NewRegistry.class, CombatRegistries::registerDataPackRegistries);
    }
}
