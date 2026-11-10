package io.github.matejkadlec.realmforge.item;

import io.github.matejkadlec.realmforge.generated.GeneratedItems;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AnvilUpdateEvent;
import net.neoforged.neoforge.event.ItemAttributeModifierEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.registries.DataPackRegistryEvent;

/** Item behaviours and gear components: two-handed gear, the protection limit, netherite upgrades (architecture 13). */
public final class ItemFeature {
    private ItemFeature() {
    }

    /** Registers the feature's registries and event listeners. Called once from {@code Realmforge}. */
    public static void register(IEventBus modBus) {
        // Items generated from content/items/ (mod/src/generated, never edit by hand).
        GeneratedItems.register(modBus);
        // Two-handed gear (wands) keeps shields out of the player's hands.
        NeoForge.EVENT_BUS.addListener(PlayerTickEvent.Post.class, TwoHandedGear::onPlayerTick);
        NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerRespawnEvent.class, TwoHandedGear::onRespawn);
        NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerLoggedOutEvent.class, TwoHandedGear::onLoggedOut);
        NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerChangedDimensionEvent.class,
                TwoHandedGear::onChangedDimension);
        NeoForge.EVENT_BUS.addListener(ItemEntityPickupEvent.Pre.class, TwoHandedGear::onItemPickup);
        // At most two protections on one piece (RLM-537): the anvil here, the table and loot in mixin/.
        NeoForge.EVENT_BUS.addListener(AnvilUpdateEvent.class, ProtectionLimit::onAnvilUpdate);
        // Netherite upgrades (RLM-566): the registry, the piece's component, the smithing recipe and the worn bonus.
        modBus.addListener(DataPackRegistryEvent.NewRegistry.class, ItemRegistries::registerDataPackRegistries);
        ItemComponents.register(modBus);
        ItemRecipes.register(modBus);
        NeoForge.EVENT_BUS.addListener(ItemAttributeModifierEvent.class, NetheriteUpgrades::onItemAttributeModifiers);
    }
}
