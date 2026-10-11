package io.github.matejkadlec.realmforge.dungeon;

import io.github.matejkadlec.realmforge.dungeon.instance.InstanceManager;
import java.util.function.BiConsumer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.world.chunk.RegisterTicketControllersEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Dungeon types, planner, layout, builder and instances (architecture 15). */
public final class DungeonFeature {
    private DungeonFeature() {
    }

    /** Registers the feature's registries and event listeners. Called once from {@code Realmforge}. */
    public static void register(IEventBus modBus) {
        DungeonAttachments.register(modBus);
        modBus.addListener(RegisterTicketControllersEvent.class, event -> event.register(InstanceManager.TICKETS));

        NeoForge.EVENT_BUS.addListener(ServerTickEvent.Post.class, event -> {
            InstanceManager manager = InstanceManager.get(event.getServer());
            if (manager != null) {
                manager.tick();
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
            InstanceManager manager = InstanceManager.get(serverPlayer.server);
            if (manager != null) {
                action.accept(manager, serverPlayer);
            }
        }
    }
}
