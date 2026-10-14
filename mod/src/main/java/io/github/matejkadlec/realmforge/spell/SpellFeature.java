package io.github.matejkadlec.realmforge.spell;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.NewRegistryEvent;

/** Spells, schools, effect types, casting, cooldowns, resources, procs and spell entities (architecture 11). */
public final class SpellFeature {
    private SpellFeature() {
    }

    /** Registers the feature's registries and event listeners. Called once from {@code Realmforge}. */
    public static void register(IEventBus modBus) {
        modBus.addListener(NewRegistryEvent.class, SpellRegistries::registerTypeRegistries);
        ResourceTypes.register(modBus);
        SpellAttachments.register(modBus);
    }
}
