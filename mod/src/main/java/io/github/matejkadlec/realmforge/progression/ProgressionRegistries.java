package io.github.matejkadlec.realmforge.progression;

import io.github.matejkadlec.realmforge.Realmforge;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.neoforged.neoforge.registries.DataPackRegistryEvent;

/** Keys of the registries {@code progression} owns (architecture 5.2, 5.4). */
public final class ProgressionRegistries {
    /** {@code realmforge:chapter}: the story's chapters from {@code content/chapters/}; synced. */
    public static final ResourceKey<Registry<Chapter>> CHAPTER = ResourceKey.createRegistryKey(Realmforge.id("chapter"));

    private ProgressionRegistries() {
    }

    static void registerDataPackRegistries(DataPackRegistryEvent.NewRegistry event) {
        event.dataPackRegistry(CHAPTER, Chapter.CODEC, Chapter.CODEC);
    }
}
