package io.github.matejkadlec.realmforge.qa.combat;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.PlayLevelSoundEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/**
 * The sounds the server played lately, where and when, from NeoForge's {@link PlayLevelSoundEvent}: the custom mob
 * GameTests check what mobs and spells sound like (W3, RLM-33), and the weapon block GameTests that a raised weapon
 * played its block sound or did not (RLM-387). Server thread only; it keeps the last {@value #KEPT}, and a new server
 * starts empty.
 */
public final class SoundLog {
    private static final int KEPT = 1024;
    private static final Deque<Played> PLAYED = new ArrayDeque<>();

    private SoundLog() {
    }

    /** Called once from {@code RealmforgeQa}. */
    public static void register() {
        NeoForge.EVENT_BUS.addListener(PlayLevelSoundEvent.AtPosition.class,
                event -> record(event.getLevel(), event.getPosition(), event.getSound()));
        NeoForge.EVENT_BUS.addListener(PlayLevelSoundEvent.AtEntity.class,
                event -> record(event.getLevel(), event.getEntity().position(), event.getSound()));
        NeoForge.EVENT_BUS.addListener(ServerStoppedEvent.class, event -> PLAYED.clear());
    }

    private static void record(Level level, Vec3 where, Holder<SoundEvent> sound) {
        if (!(level instanceof ServerLevel server) || sound == null) {
            return;
        }
        sound.unwrapKey().ifPresent(key -> {
            PLAYED.addLast(new Played(server, where, key.identifier(), server.getGameTime()));
            if (PLAYED.size() > KEPT) {
                PLAYED.removeFirst();
            }
        });
    }

    /**
     * The ids of the sounds played in {@code level} within {@code radius} blocks of {@code center}, at or after game time
     * {@code since}, oldest first.
     */
    public static List<Identifier> near(Level level, Vec3 center, double radius, long since) {
        return PLAYED.stream()
                .filter(played -> played.level() == level && played.gameTime() >= since
                        && played.where().closerThan(center, radius))
                .map(Played::sound)
                .toList();
    }

    /** How many times {@code sound} played within {@code radius} blocks of {@code center} in {@code level}. */
    public static long count(Level level, Vec3 center, double radius, Holder<SoundEvent> sound) {
        return sound.unwrapKey().map(key -> PLAYED.stream()
                .filter(played -> played.level() == level && played.sound().equals(key.identifier())
                        && played.where().closerThan(center, radius))
                .count()).orElse(0L);
    }

    /** One sound the server played. */
    private record Played(ServerLevel level, Vec3 where, Identifier sound, long gameTime) {
    }
}
