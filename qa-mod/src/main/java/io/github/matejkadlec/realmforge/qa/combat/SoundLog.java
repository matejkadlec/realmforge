package io.github.matejkadlec.realmforge.qa.combat;

import java.util.ArrayDeque;
import java.util.Deque;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.PlayLevelSoundEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/**
 * The last sounds the server played at a position ({@code Level#playSound} with a position, which posts
 * {@link PlayLevelSoundEvent.AtPosition}), so GameTests can check that a rule played its sound or did not (RLM-387:
 * a raised weapon's block sound). Server thread only; a new server starts empty.
 */
public final class SoundLog {
    /** How many sounds the log keeps: enough for the GameTests running at once. */
    private static final int SIZE = 512;
    private static final Deque<Played> LAST = new ArrayDeque<>();

    private SoundLog() {
    }

    /** Called once from {@code RealmforgeQa}. */
    public static void register() {
        NeoForge.EVENT_BUS.addListener(PlayLevelSoundEvent.AtPosition.class, SoundLog::onSound);
        NeoForge.EVENT_BUS.addListener(ServerStoppedEvent.class, event -> LAST.clear());
    }

    /** How many times {@code sound} played within {@code radius} blocks of {@code near} in {@code level}. */
    public static long count(ServerLevel level, Vec3 near, double radius, Holder<SoundEvent> sound) {
        return LAST.stream().filter(played -> played.level() == level && played.sound().is(sound)
                && played.position().closerThan(near, radius)).count();
    }

    private static void onSound(PlayLevelSoundEvent.AtPosition event) {
        if (!(event.getLevel() instanceof ServerLevel level) || event.getSound() == null) {
            return;
        }
        if (LAST.size() >= SIZE) {
            LAST.removeFirst();
        }
        LAST.addLast(new Played(level, event.getPosition(), event.getSound()));
    }

    /** One sound played at a position. */
    private record Played(ServerLevel level, Vec3 position, Holder<SoundEvent> sound) {
    }
}
