package io.github.matejkadlec.realmforge.spell;

import io.github.matejkadlec.realmforge.spell.net.ShownTargetPayload;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The Show target key (RLM-468; architecture 11.6): while a player holds it, the server names to their client the mob
 * an enemy spell of theirs would go to now, {@link AimAssist#enemy} within {@link AimAssist#REACH}, so the target circle
 * shows under it without a cast. The client says when the key goes down and up ({@code ShowTargetPayload}); every server
 * tick while it is down, the server picks again and sends {@code ShownTargetPayload} when the pick changed. The server
 * picks because only it knows a mob's target and a neutral mob's anger, which decide whether assistance may pick it.
 * Kept by the player's id, so a respawn keeps a key that is still held. Server thread only.
 */
public final class ShowTarget {
    /** What the client of a player holding the key was last told, by player id: an entity id, or {@link #NONE}. */
    private static final Map<UUID, Integer> HELD = new HashMap<>();
    /** {@link #HELD}'s value when the client was told of no mob. */
    private static final int NONE = -1;

    private ShowTarget() {
    }

    static void register() {
        NeoForge.EVENT_BUS.addListener(ServerTickEvent.Post.class, event -> onServerTick(event.getServer()));
        NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerLoggedOutEvent.class,
                event -> HELD.remove(event.getEntity().getUUID()));
        NeoForge.EVENT_BUS.addListener(ServerStoppedEvent.class, event -> HELD.clear());
    }

    /** {@code player}'s client says the key went down ({@code held}) or up; going down picks at once. */
    public static void setHeld(ServerPlayer player, boolean held) {
        if (held) {
            HELD.putIfAbsent(player.getUUID(), NONE);
            show(player);
        } else {
            HELD.remove(player.getUUID());
        }
    }

    /** Whether {@code player} holds the key, as their client last said. */
    public static boolean isHeld(ServerPlayer player) {
        return HELD.containsKey(player.getUUID());
    }

    private static void onServerTick(MinecraftServer server) {
        if (HELD.isEmpty()) {
            return;
        }
        for (UUID id : List.copyOf(HELD.keySet())) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player == null) {
                HELD.remove(id);
            } else if (player.isAlive()) {
                show(player);
            }
        }
    }

    /** Picks the mob for {@code player} and tells their client when it changed. A spectator picks nothing. */
    private static void show(ServerPlayer player) {
        Optional<LivingEntity> target = player.isSpectator() ? Optional.empty()
                : AimAssist.enemy(player, AimAssist.REACH);
        int id = target.map(Entity::getId).orElse(NONE);
        Integer told = HELD.put(player.getUUID(), id);
        if (told == null || told != id) {
            PacketDistributor.sendToPlayer(player, new ShownTargetPayload(target.map(Entity::getId)));
        }
    }
}
