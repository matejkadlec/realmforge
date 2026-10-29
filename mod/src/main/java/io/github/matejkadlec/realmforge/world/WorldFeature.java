package io.github.matejkadlec.realmforge.world;

import io.github.matejkadlec.realmforge.generated.GeneratedBlocks;
import io.github.matejkadlec.realmforge.world.realm.RealmAccess;
import io.github.matejkadlec.realmforge.world.realm.RealmArrivals;
import io.github.matejkadlec.realmforge.world.realm.RealmPortalBlocks;
import io.github.matejkadlec.realmforge.world.realm.RealmPortals;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityMobGriefingEvent;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;
import net.neoforged.neoforge.event.entity.living.LivingDestroyBlockEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.entity.player.PlayerRespawnPositionEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.registries.DataPackRegistryEvent;

/**
 * Dimension rules and access, realms and the realm portal, world defaults, Night Vision, griefing protection, and the
 * blocks of {@code content/blocks/} (architecture 15.1, 19.1).
 */
public final class WorldFeature {
    private WorldFeature() {
    }

    /** Registers the feature's registries and event listeners. Called once from {@code Realmforge}. */
    public static void register(IEventBus modBus) {
        WorldAttachments.register(modBus);
        RealmPortalBlocks.register(modBus);
        GeneratedBlocks.register(modBus);
        modBus.addListener(DataPackRegistryEvent.NewRegistry.class, WorldRegistries::registerDataPackRegistries);

        // Realms (realm portal spec): the refusal first, the shoulder entities last, once nothing refused the trip.
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, EntityTravelToDimensionEvent.class, RealmAccess::onTravel);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, EntityTravelToDimensionEvent.class,
                RealmAccess::onTravelAllowed);
        NeoForge.EVENT_BUS.addListener(PlayerRespawnPositionEvent.class, RealmAccess::onRespawnPosition);
        NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerChangedDimensionEvent.class, RealmArrivals::onChangedDimension);
        // The realm portal: the sneak gesture whatever the off hand holds, and the marks of an incomplete frame.
        NeoForge.EVENT_BUS.addListener(PlayerInteractEvent.RightClickBlock.class, RealmPortals::onRightClickBlock);
        NeoForge.EVENT_BUS.addListener(LevelTickEvent.Post.class, RealmPortals::onLevelTick);

        // Also when another mod cancelled it to place the spawn itself: the world is new either way.
        NeoForge.EVENT_BUS.addListener(EventPriority.NORMAL, true, LevelEvent.CreateSpawnPosition.class,
                WorldDefaults::onCreateSpawnPosition);

        NeoForge.EVENT_BUS.addListener(ExplosionEvent.Detonate.class, GriefingProtection::onDetonate);
        NeoForge.EVENT_BUS.addListener(EntityMobGriefingEvent.class, GriefingProtection::onMobGriefing);
        NeoForge.EVENT_BUS.addListener(LivingDestroyBlockEvent.class, GriefingProtection::onDestroyBlock);
        NeoForge.EVENT_BUS.addListener(BlockEvent.FarmlandTrampleEvent.class, GriefingProtection::onFarmlandTrample);

        NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerLoggedInEvent.class, NightVision::onLoggedIn);
        NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerRespawnEvent.class, NightVision::onRespawn);
        NeoForge.EVENT_BUS.addListener(MobEffectEvent.Remove.class, NightVision::onEffectRemove);
        NeoForge.EVENT_BUS.addListener(MobEffectEvent.Applicable.class, NightVision::onEffectApplicable);
    }
}
