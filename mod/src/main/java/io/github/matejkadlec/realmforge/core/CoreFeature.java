package io.github.matejkadlec.realmforge.core;

import io.github.matejkadlec.realmforge.generated.GeneratedSounds;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.registries.DataPackRegistryEvent;
import net.neoforged.neoforge.registries.NewRegistryEvent;

/**
 * Shared infrastructure: registry keys, codec helpers, requirements, curves, and our sound events, which mobs, spells
 * and blocks all play (architecture 4.2, 5.3, 6.2), and the welcome line on joining a world (RLM-520).
 */
public final class CoreFeature {
    private CoreFeature() {
    }

    /** Registers the feature's registries and event listeners. Called once from {@code Realmforge}. */
    public static void register(IEventBus modBus) {
        modBus.addListener(NewRegistryEvent.class, CoreRegistries::registerTypeRegistries);
        modBus.addListener(DataPackRegistryEvent.NewRegistry.class, CoreRegistries::registerDataPackRegistries);
        RequirementTypes.register(modBus);
        // Sound events generated from content/sounds/ (mod/src/generated, never edit by hand).
        GeneratedSounds.register(modBus);
        NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerLoggedInEvent.class, WelcomeMessage::onLoggedIn);
    }
}
