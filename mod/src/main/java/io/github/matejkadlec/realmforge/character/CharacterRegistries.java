package io.github.matejkadlec.realmforge.character;

import io.github.matejkadlec.realmforge.Realmforge;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.neoforged.neoforge.registries.DataPackRegistryEvent;

/** Keys of the registries {@code character} owns (architecture 5.2, 5.4). */
public final class CharacterRegistries {
    /** {@code realmforge:race}: the playable races from {@code content/races/}; synced. */
    public static final ResourceKey<Registry<Race>> RACE = ResourceKey.createRegistryKey(Realmforge.id("race"));

    /** {@code realmforge:class}: the classes from {@code content/classes/}; synced. */
    public static final ResourceKey<Registry<PlayerClass>> CLASS =
            ResourceKey.createRegistryKey(Realmforge.id("class"));

    private CharacterRegistries() {
    }

    static void registerDataPackRegistries(DataPackRegistryEvent.NewRegistry event) {
        event.dataPackRegistry(CLASS, PlayerClass.CODEC, PlayerClass.CODEC);
        event.dataPackRegistry(RACE, Race.CODEC, Race.CODEC);
    }
}
