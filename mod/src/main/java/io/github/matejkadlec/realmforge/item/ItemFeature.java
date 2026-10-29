package io.github.matejkadlec.realmforge.item;

import io.github.matejkadlec.realmforge.generated.GeneratedItems;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** Item behaviours and gear components; affixes and gems later (architecture 13). */
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
    }
}
