package io.github.matejkadlec.realmforge.qa.instance;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/**
 * Lets a GameTest hold back the entities of a chunk: {@code ServerLevel#areEntitiesLoaded} reads false for it, as
 * while its entities still load in the background, until the test lets it go or a number of ticks has passed, so a
 * test that fails halfway never stalls a later one in the same plot cell. The entity manager loads a chunk's entities
 * apart from its blocks, so a chunk can be loaded and not ready yet (architecture 15.5); this makes that state last as
 * long as a test needs it (RLM-651). Read through {@code ServerLevelMixin}; dev runs only, and empty outside a test.
 */
public final class HeldBackEntities {
    private record Chunk(ResourceKey<Level> dimension, long pos) {
    }

    /** Each held-back chunk, with the game time at which it is let go by itself. */
    private static final Map<Chunk, Long> HELD = new ConcurrentHashMap<>();

    private HeldBackEntities() {
    }

    /** Holds back the entities of chunk {@code pos} ({@code ChunkPos#pack}) of {@code level} for {@code ticks}. */
    public static void holdBack(ServerLevel level, long pos, long ticks) {
        HELD.put(new Chunk(level.dimension(), pos), level.getGameTime() + ticks);
    }

    /** Lets the chunk's entities count as loaded again, as far as this hook is concerned. */
    public static void letGo(ServerLevel level, long pos) {
        HELD.remove(new Chunk(level.dimension(), pos));
    }

    /** Whether the chunk's entities read as not loaded yet; a chunk whose time is up is let go. */
    public static boolean heldBack(ServerLevel level, long pos) {
        if (HELD.isEmpty()) {
            return false;
        }
        Chunk chunk = new Chunk(level.dimension(), pos);
        Long until = HELD.get(chunk);
        if (until == null) {
            return false;
        }
        if (level.getGameTime() >= until) {
            HELD.remove(chunk);
            return false;
        }
        return true;
    }
}
