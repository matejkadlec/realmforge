package io.github.matejkadlec.realmforge.item;

import io.github.matejkadlec.realmforge.generated.GeneratedItems;
import net.neoforged.bus.api.IEventBus;

/** Item behaviours and gear components; affixes and gems later (architecture 13). */
public final class ItemFeature {
    private ItemFeature() {
    }

    /** Registers the feature's registries and event listeners. Called once from {@code Realmforge}. */
    public static void register(IEventBus modBus) {
        // Items generated from content/items/ (mod/src/generated, never edit by hand).
        GeneratedItems.register(modBus);
    }
}
