package io.github.matejkadlec.realmforge.qa.castanim;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Server side of the test cast: who casts until when, and the melee block while they do (ADR 0020).
 *
 * <p>While a cast runs, the caster carries Better Combat's entity tag {@value #BETTER_COMBAT_DISABLED_TAG}. Better
 * Combat 3.2.2 copies it every server tick into a synced player flag: its client then starts no swing and cancels the
 * one in progress, and its server rejects attack requests. Without the tag, or without Better Combat, the attack key
 * falls through to vanilla, which the client blocks ({@code QaCastAnimationClient}) and {@link #onAttack} blocks on
 * the server. Entity tags are saved with the player, so {@value #MARKER_TAG} records that a cast, not an operator,
 * added the Better Combat tag, and a login removes both when a crash left them behind.
 */
public final class QaCasts {
    /** Better Combat's command tag that disables its attacks for one player ({@code CombatFlags.DISABLED_TAG}). */
    public static final String BETTER_COMBAT_DISABLED_TAG = "bettercombat_disabled";
    /** Set together with {@link #BETTER_COMBAT_DISABLED_TAG} when a cast added it. */
    public static final String MARKER_TAG = "realmforge_qa_cast";

    /** Server tick at which each running cast ends. Server thread only. */
    private static final Map<ServerPlayer, Long> CAST_ENDS = new HashMap<>();

    private QaCasts() {
    }

    /** Starts (or restarts) a cast of {@code ticks} ticks for {@code player}. */
    public static void start(ServerPlayer player, int ticks) {
        CAST_ENDS.put(player, player.level().getServer().getTickCount() + (long) ticks);
        if (player.addTag(BETTER_COMBAT_DISABLED_TAG)) {
            player.addTag(MARKER_TAG);
        }
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, new QaCastPayload(player.getId(), ticks));
    }

    /** Ends the cast of {@code player}, if one runs. */
    public static void stop(ServerPlayer player) {
        if (end(player)) {
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, QaCastPayload.stop(player.getId()));
        }
    }

    public static boolean isCasting(Player player) {
        return player instanceof ServerPlayer serverPlayer && CAST_ENDS.containsKey(serverPlayer);
    }

    /** Removes the Better Combat tag if a cast added it; an operator's own tag stays. */
    public static void clearLeftoverTags(Player player) {
        if (player.removeTag(MARKER_TAG)) {
            player.removeTag(BETTER_COMBAT_DISABLED_TAG);
        }
    }

    private static boolean end(ServerPlayer player) {
        if (CAST_ENDS.remove(player) == null) {
            return false;
        }
        clearLeftoverTags(player);
        return true;
    }

    static void onServerTick(ServerTickEvent.Post event) {
        if (CAST_ENDS.isEmpty()) {
            return;
        }
        long now = event.getServer().getTickCount();
        List<ServerPlayer> finished = new ArrayList<>();
        CAST_ENDS.forEach((player, endTick) -> {
            if (endTick <= now) {
                finished.add(player);
            }
        });
        finished.forEach(QaCasts::stop);
    }

    /** Vanilla melee and Better Combat's server-side hits both go through {@code Player#attack}. */
    static void onAttack(AttackEntityEvent event) {
        if (isCasting(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    /** A player who starts seeing a caster mid-cast gets the rest of the cast, so its animation still shows. */
    static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getTarget() instanceof ServerPlayer caster && event.getEntity() instanceof ServerPlayer watcher) {
            Long endTick = CAST_ENDS.get(caster);
            if (endTick != null) {
                int ticksLeft = (int) Math.max(1, endTick - caster.level().getServer().getTickCount());
                PacketDistributor.sendToPlayer(watcher, new QaCastPayload(caster.getId(), ticksLeft));
            }
        }
    }

    static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            stop(player);
        }
    }

    static void onLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        clearLeftoverTags(event.getEntity());
    }

    /** Fires before the player is saved, so the tags never reach the save file on a clean logout. */
    static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            end(player);
        }
    }

    static void onServerStopped(ServerStoppedEvent event) {
        CAST_ENDS.clear();
    }
}
