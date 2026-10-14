package io.github.matejkadlec.realmforge.dungeon;

import io.github.matejkadlec.realmforge.dungeon.instance.InstanceDimension;
import io.github.matejkadlec.realmforge.dungeon.instance.InstanceManager;
import java.util.function.BiConsumer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.world.chunk.RegisterTicketControllersEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.registries.DataPackRegistryEvent;

/** Dungeon types, planner, layout, builder and instances (architecture 15). */
public final class DungeonFeature {
    private DungeonFeature() {
    }

    /** Registers the feature's registries and event listeners. Called once from {@code Realmforge}. */
    public static void register(IEventBus modBus) {
        DungeonAttachments.register(modBus);
        modBus.addListener(DataPackRegistryEvent.NewRegistry.class, DungeonRegistries::registerDataPackRegistries);
        modBus.addListener(RegisterTicketControllersEvent.class, event -> event.register(InstanceManager.TICKETS));

        // Inside the level tick, so the builder's time counts in the server's tick time (and /tick query, spark).
        NeoForge.EVENT_BUS.addListener(LevelTickEvent.Post.class, event -> {
            if (event.getLevel() instanceof ServerLevel level && InstanceDimension.isInstance(level)) {
                InstanceManager manager = InstanceManager.get(level.getServer());
                if (manager != null) {
                    manager.tick();
                }
            }
        });
        NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerLoggedInEvent.class,
                event -> withManager(event.getEntity(), InstanceManager::onLogin));
        NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerRespawnEvent.class,
                event -> withManager(event.getEntity(), InstanceManager::onLeftDimension));
        NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerChangedDimensionEvent.class,
                event -> withManager(event.getEntity(), InstanceManager::onLeftDimension));
    }

    private static void withManager(Player player, BiConsumer<InstanceManager, ServerPlayer> action) {
        if (player instanceof ServerPlayer serverPlayer) {
            InstanceManager manager = InstanceManager.get(serverPlayer.level().getServer());
            if (manager != null) {
                action.accept(manager, serverPlayer);
            }
        }
    }
}
