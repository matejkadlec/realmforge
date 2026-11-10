package io.github.matejkadlec.realmforge.item;

import io.github.matejkadlec.realmforge.Realmforge;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.neoforged.neoforge.registries.DataPackRegistryEvent;

/** Keys of the registries {@code item} owns (architecture 5.2, 5.4). */
public final class ItemRegistries {
    /**
     * {@code realmforge:netherite_upgrade}: the netherite upgrades from {@code content/netherite_upgrades/} (RLM-566);
     * synced, as a client reads an upgraded piece's bonus and name for its tooltip.
     */
    public static final ResourceKey<Registry<NetheriteUpgrade>> NETHERITE_UPGRADE =
            ResourceKey.createRegistryKey(Realmforge.id("netherite_upgrade"));

    private ItemRegistries() {
    }

    static void registerDataPackRegistries(DataPackRegistryEvent.NewRegistry event) {
        event.dataPackRegistry(NETHERITE_UPGRADE, NetheriteUpgrade.CODEC, NetheriteUpgrade.CODEC);
    }
}
