package io.github.matejkadlec.realmforge.spell;

import io.github.matejkadlec.realmforge.spell.net.HighlightedTargetPayload;
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
 * The Highlight Spell Target key (RLM-468; architecture 11.6): while a player's highlight is on (the key toggled on, or
 * held, as their client options say), the server names to their client the mob an enemy spell of theirs would go to
 * now, {@link AimAssist#enemy} within {@link AimAssist#REACH}, so the target circle shows under it without a cast. The
 * client says when the highlight goes on and off ({@code HighlightTargetPayload}); every server tick while it is on, the
 * server picks again and sends {@code HighlightedTargetPayload} when the pick changed. The server picks because only it
 * knows a mob's target and a neutral mob's anger, which decide whether assistance may pick it. Kept by the player's id,
 * so a respawn keeps a highlight that is still on. Server thread only.
 */
public final class TargetHighlight {
    /** What the client of a player whose highlight is on was last told, by player id: a mob's id, or {@link #NONE}. */
    private static final Map<UUID, Integer> ON = new HashMap<>();
    /** {@link #ON}'s value when the client was told of no mob. */
    private static final int NONE = -1;

    private TargetHighlight() {
    }

    static void register() {
        NeoForge.EVENT_BUS.addListener(ServerTickEvent.Post.class, event -> onServerTick(event.getServer()));
        NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerLoggedOutEvent.class,
                event -> ON.remove(event.getEntity().getUUID()));
        NeoForge.EVENT_BUS.addListener(ServerStoppedEvent.class, event -> ON.clear());
    }

    /** {@code player}'s client says the highlight went on ({@code on}) or off; going on picks at once. */
    public static void setOn(ServerPlayer player, boolean on) {
        if (on) {
            ON.putIfAbsent(player.getUUID(), NONE);
            highlight(player);
        } else {
            ON.remove(player.getUUID());
        }
    }

    /** Whether {@code player}'s highlight is on, as their client last said. */
    public static boolean isOn(ServerPlayer player) {
        return ON.containsKey(player.getUUID());
    }

    private static void onServerTick(MinecraftServer server) {
        if (ON.isEmpty()) {
            return;
        }
        for (UUID id : List.copyOf(ON.keySet())) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player == null) {
                ON.remove(id);
            } else if (player.isAlive()) {
                highlight(player);
            }
        }
    }

    /** Picks the mob for {@code player} and tells their client when it changed. A spectator picks nothing. */
    private static void highlight(ServerPlayer player) {
        Optional<LivingEntity> target = player.isSpectator() ? Optional.empty()
                : AimAssist.enemy(player, AimAssist.REACH);
        int id = target.map(Entity::getId).orElse(NONE);
        Integer told = ON.put(player.getUUID(), id);
        if (told == null || told != id) {
            PacketDistributor.sendToPlayer(player, new HighlightedTargetPayload(target.map(Entity::getId)));
        }
    }
}
