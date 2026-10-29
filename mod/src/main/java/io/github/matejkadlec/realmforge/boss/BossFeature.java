package io.github.matejkadlec.realmforge.boss;

import io.github.matejkadlec.realmforge.mob.custom.Archetypes;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DataPackRegistryEvent;
import net.neoforged.neoforge.registries.NewRegistryEvent;

/** Boss definitions, the phase machine, the boss entity and its boss bar ({@link BossBar}) (architecture 14.3). */
public final class BossFeature {
    /** The mob archetype of a boss's body in {@code content/mobs/}. */
    public static final String ARCHETYPE = "boss";

    private BossFeature() {
    }

    /** Registers the feature's registries and event listeners. Called once from {@code Realmforge}. */
    public static void register(IEventBus modBus) {
        modBus.addListener(NewRegistryEvent.class, BossRegistries::registerTypeRegistries);
        modBus.addListener(DataPackRegistryEvent.NewRegistry.class, BossRegistries::registerDataPackRegistries);
        PhaseTriggerTypes.register(modBus);
        // A boss's body is a custom mob of archetype boss: GeneratedMobs creates it through mob's Archetypes.
        Archetypes.provide(ARCHETYPE, BossMob::create);
    }
}
