package io.github.matejkadlec.realmforge.spell;

import io.github.matejkadlec.realmforge.Realmforge;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.neoforged.neoforge.registries.NewRegistryEvent;
import net.neoforged.neoforge.registries.RegistryBuilder;

/** Keys and type registries of the registries {@code spell} owns (architecture 5.3, 5.4). */
public final class SpellRegistries {
    /** {@code realmforge:resource_type}: the resources a class's spells cost ({@link ResourceTypes}). */
    public static final ResourceKey<Registry<ResourceType>> RESOURCE_TYPE =
            ResourceKey.createRegistryKey(Realmforge.id("resource_type"));

    /**
     * The registry behind {@link #RESOURCE_TYPE}, filled from Java at game start. Synced, as the synced class registry
     * names its entries.
     */
    public static final Registry<ResourceType> RESOURCE_TYPES = new RegistryBuilder<>(RESOURCE_TYPE).sync(true).create();

    private SpellRegistries() {
    }

    static void registerTypeRegistries(NewRegistryEvent event) {
        event.register(RESOURCE_TYPES);
    }
}
