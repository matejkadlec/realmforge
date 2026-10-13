package io.github.matejkadlec.realmforge.world;

import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityMobGriefingEvent;
import net.neoforged.neoforge.event.entity.living.LivingDestroyBlockEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.level.LevelEvent;

/** Dimension rules and access, world defaults, Dark Vision, griefing protection (architecture 15.1, 19.1). */
public final class WorldFeature {
    private WorldFeature() {
    }

    /** Registers the feature's registries and event listeners. Called once from {@code Realmforge}. */
    public static void register(IEventBus modBus) {
        WorldAttachments.register(modBus);

        // Also when another mod cancelled it to place the spawn itself: the world is new either way.
        NeoForge.EVENT_BUS.addListener(EventPriority.NORMAL, true, LevelEvent.CreateSpawnPosition.class,
                WorldDefaults::onCreateSpawnPosition);

        NeoForge.EVENT_BUS.addListener(ExplosionEvent.Detonate.class, GriefingProtection::onDetonate);
        NeoForge.EVENT_BUS.addListener(EntityMobGriefingEvent.class, GriefingProtection::onMobGriefing);
        NeoForge.EVENT_BUS.addListener(LivingDestroyBlockEvent.class, GriefingProtection::onDestroyBlock);
        NeoForge.EVENT_BUS.addListener(BlockEvent.FarmlandTrampleEvent.class, GriefingProtection::onFarmlandTrample);

        NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerLoggedInEvent.class, DarkVision::onLoggedIn);
        NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerRespawnEvent.class, DarkVision::onRespawn);
        NeoForge.EVENT_BUS.addListener(MobEffectEvent.Remove.class, DarkVision::onEffectRemove);
        NeoForge.EVENT_BUS.addListener(MobEffectEvent.Applicable.class, DarkVision::onEffectApplicable);
    }
}
