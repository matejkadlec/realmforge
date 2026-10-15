package io.github.matejkadlec.realmforge.combat;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.registries.DataPackRegistryEvent;

/**
 * The damage pipeline: spell schools, crit, dodge, resistances, our armor formula, armor pierce, lifesteal, overheal,
 * health regeneration, the magic shield, combat text (architecture 9).
 */
public final class CombatFeature {
    private CombatFeature() {
    }

    /** Registers the feature's registries and event listeners. Called once from {@code Realmforge}. */
    public static void register(IEventBus modBus) {
        modBus.addListener(DataPackRegistryEvent.NewRegistry.class, CombatRegistries::registerDataPackRegistries);
        CombatAttachments.register(modBus);
        DamagePipeline.register();
        NeoForge.EVENT_BUS.addListener(ServerTickEvent.Post.class, Overheal::onServerTick);
        NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerLoggedInEvent.class, Overheal::onLogin);
        NeoForge.EVENT_BUS.addListener(PlayerTickEvent.Post.class, HealthRegeneration::onPlayerTick);
        NeoForge.EVENT_BUS.addListener(PlayerTickEvent.Post.class, MagicShields::onPlayerTick);
    }
}
