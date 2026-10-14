package io.github.matejkadlec.realmforge.dungeon;

import io.github.matejkadlec.realmforge.Realmforge;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.neoforged.neoforge.registries.DataPackRegistryEvent;

/** Keys of the registries {@code dungeon} owns (architecture 5.2, 5.4). */
public final class DungeonRegistries {
    /**
     * {@code realmforge:dungeon_type}: the kinds of dungeons from {@code content/dungeons/}. Not synced: the server
     * plans and builds dungeons, and clients learn what they need from what it sends.
     */
    public static final ResourceKey<Registry<DungeonType>> DUNGEON_TYPE =
            ResourceKey.createRegistryKey(Realmforge.id("dungeon_type"));

    private DungeonRegistries() {
    }

    static void registerDataPackRegistries(DataPackRegistryEvent.NewRegistry event) {
        event.dataPackRegistry(DUNGEON_TYPE, DungeonType.CODEC);
    }
}
