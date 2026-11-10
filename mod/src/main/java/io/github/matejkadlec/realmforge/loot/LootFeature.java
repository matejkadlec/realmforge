package io.github.matejkadlec.realmforge.loot;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;

/** Loot functions and conditions, reward rolls, mob coin drops (architecture 13). */
public final class LootFeature {
    private LootFeature() {
    }

    /** Registers the feature's registries and event listeners. Called once from {@code Realmforge}. */
    public static void register(IEventBus modBus) {
        // A hostile mob a player killed drops coins by its level and rank (RLM-286).
        NeoForge.EVENT_BUS.addListener(LivingDropsEvent.class, CoinDrops::onLivingDrops);
    }
}
