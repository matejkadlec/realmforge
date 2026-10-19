package io.github.matejkadlec.realmforge.world;

import io.github.matejkadlec.realmforge.Realmforge;
import io.github.matejkadlec.realmforge.world.realm.Realm;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.neoforged.neoforge.registries.DataPackRegistryEvent;

/** Keys of the registries {@code world} owns (architecture 5.2, 5.4). */
public final class WorldRegistries {
    /**
     * {@code realmforge:dimension_rules}: the gameplay rules of each dimension, from {@code content/dimensions/}. Not
     * synced: clients learn what they need from what the server sends.
     */
    public static final ResourceKey<Registry<DimensionRules>> DIMENSION_RULES =
            ResourceKey.createRegistryKey(Realmforge.id("dimension_rules"));

    /** {@code realmforge:realm}: the realms, from {@code content/realms/}; synced, for the portal and the arrival title. */
    public static final ResourceKey<Registry<Realm>> REALM = ResourceKey.createRegistryKey(Realmforge.id("realm"));

    private WorldRegistries() {
    }

    static void registerDataPackRegistries(DataPackRegistryEvent.NewRegistry event) {
        event.dataPackRegistry(DIMENSION_RULES, DimensionRules.CODEC);
        event.dataPackRegistry(REALM, Realm.CODEC, Realm.CODEC);
    }
}
