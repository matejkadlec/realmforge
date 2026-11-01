package io.github.matejkadlec.realmforge.boss;

import com.google.common.collect.MapMaker;
import io.github.matejkadlec.realmforge.Realmforge;
import io.github.matejkadlec.realmforge.generated.GeneratedTexts;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.neoforged.neoforge.common.NeoForgeMod;

/**
 * Creative flight is off in a boss's arena (RLM-559; Matěj, 2026-10-16 and 2026-10-29, RLM-232): Apotheosis's
 * Unbound, a Mythic chestplate's {@code neoforge:creative_flight}, or any other source of that attribute. Creative mode
 * keeps its flight, which is the game mode's, not the attribute's.
 * <ul>
 *   <li><b>In the arena:</b> every {@link #CHECK_INTERVAL} ticks a living boss with a home holds every player within
 *   its arena ({@link BossMob#inArena}). A held player's flight attribute gets a transient modifier that zeroes it, so
 *   {@code IPlayerExtension#mayFly} turns false and the server lands a flying player ({@code ServerPlayer#tick}).</li>
 *   <li><b>The warning:</b> a player who could fly on entering sees a title and a chat line, and gets Slow Falling for
 *   {@value #SLOW_FALLING_TICKS} ticks, so losing flight mid-air never kills.</li>
 *   <li><b>Flight returns</b> on leaving the arena (another dimension, death and logout included), and when the boss
 *   dies, is removed or resets ({@link #releaseAll}). A player still inside after a reset is held again at the next
 *   check.</li>
 * </ul>
 * The bosses that hold a player are counted, so overlapping arenas give flight back only once the player has left
 * them all. Skyhearth, which turns flight off too, does not exist yet; it takes the same modifier.
 */
public final class ArenaFlight {
    /** The modifier that zeroes a held player's creative flight. */
    public static final Identifier NO_FLIGHT = Realmforge.id("boss_arena_no_flight");
    /** How often a boss looks for players in its arena, in ticks. */
    public static final int CHECK_INTERVAL = 5;
    /** Slow Falling on losing flight, in ticks: 5 s. */
    public static final int SLOW_FALLING_TICKS = 100;

    /**
     * The bosses that hold each player. Weak identity keys: a player object lives for one login and one life, and a
     * respawn's new one carries neither the transient modifier nor an entry.
     */
    private static final Map<ServerPlayer, Set<UUID>> HELD = new MapMaker().weakKeys().makeMap();

    private ArenaFlight() {
    }

    /** Holds every player in the boss's arena and lets go of every one it held who left it. */
    static void track(ServerLevel level, BossMob boss) {
        if (boss.home().isEmpty()) {
            return;
        }
        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            boolean inside = player.level() == level && player.isAlive() && !player.isSpectator()
                    && boss.inArena(player);
            if (inside) {
                hold(player, boss.getUUID());
            } else {
                release(player, boss.getUUID());
            }
        }
    }

    /** Lets go of every player the boss holds: it died, was removed or reset. */
    static void releaseAll(BossMob boss) {
        for (ServerPlayer player : List.copyOf(HELD.keySet())) {
            release(player, boss.getUUID());
        }
    }

    /** Whether a boss's arena holds the player, so creative flight is off. */
    public static boolean isHeld(ServerPlayer player) {
        return HELD.containsKey(player);
    }

    /** Whether this boss's arena holds the player. */
    public static boolean isHeldBy(ServerPlayer player, BossMob boss) {
        Set<UUID> bosses = HELD.get(player);
        return bosses != null && bosses.contains(boss.getUUID());
    }

    private static void hold(ServerPlayer player, UUID boss) {
        Set<UUID> bosses = HELD.computeIfAbsent(player, held -> new HashSet<>());
        if (bosses.add(boss) && bosses.size() == 1) {
            ground(player);
        }
    }

    private static void release(ServerPlayer player, UUID boss) {
        Set<UUID> bosses = HELD.get(player);
        if (bosses != null && bosses.remove(boss) && bosses.isEmpty()) {
            HELD.remove(player);
            AttributeInstance flight = player.getAttribute(NeoForgeMod.CREATIVE_FLIGHT);
            if (flight != null) {
                flight.removeModifier(NO_FLIGHT);
            }
        }
    }

    private static void ground(ServerPlayer player) {
        AttributeInstance flight = player.getAttribute(NeoForgeMod.CREATIVE_FLIGHT);
        if (flight == null || flight.hasModifier(NO_FLIGHT)) {
            return;
        }
        boolean couldFly = flight.getValue() > 0;
        flight.addTransientModifier(
                new AttributeModifier(NO_FLIGHT, -1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        if (couldFly) {
            player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, SLOW_FALLING_TICKS));
            player.connection.send(new ClientboundSetSubtitleTextPacket(
                    Component.translatable(GeneratedTexts.MESSAGE_ARENA_NO_FLIGHT_SUBTITLE)));
            player.connection.send(new ClientboundSetTitleTextPacket(
                    Component.translatable(GeneratedTexts.MESSAGE_ARENA_NO_FLIGHT)));
            player.sendSystemMessage(Component.translatable(GeneratedTexts.MESSAGE_ARENA_NO_FLIGHT_CHAT));
        }
    }
}
