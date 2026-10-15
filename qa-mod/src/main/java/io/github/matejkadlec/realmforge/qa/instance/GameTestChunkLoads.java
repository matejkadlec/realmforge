package io.github.matejkadlec.realmforge.qa.instance;

import io.github.matejkadlec.realmforge.dungeon.instance.InstanceDimension;
import io.github.matejkadlec.realmforge.dungeon.instance.InstanceManager;
import io.github.matejkadlec.realmforge.dungeon.instance.Plot;
import io.github.matejkadlec.realmforge.dungeon.instance.PlotState;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

/**
 * Paces the GameTest server with the chunks plots load in the background. A plot asks for its chunks with a loading
 * ticket and holds each one once it has loaded (architecture 15.5). A server gives that loading 50 ms per tick, but the
 * GameTest server runs its ticks back to back, so the first use of a plot cell (seconds of chunk generation) outlasts
 * the tests' tick timeouts. Before each of its ticks this loads the chunks plots still wait for, on the server thread
 * as vanilla loads a chunk it needs at once; the plots ask and hold as they do elsewhere. Other servers are not
 * touched.
 */
public final class GameTestChunkLoads {
    private GameTestChunkLoads() {
    }

    /** Called at the start of every server tick. */
    public static void beforeTick(MinecraftServer server) {
        if (!(server instanceof GameTestServer)) {
            return;
        }
        InstanceManager manager = InstanceManager.get(server);
        ServerLevel level = InstanceDimension.level(server);
        if (manager == null || level == null) {
            return;
        }
        for (Plot plot : manager.plots()) {
            if (plot.state() == PlotState.FREE || plot.chunks().containsAll(plot.wantedChunks())) {
                continue;
            }
            for (long chunk : plot.wantedChunks()) {
                if (!plot.chunks().contains(chunk)) {
                    level.getChunk(ChunkPos.getX(chunk), ChunkPos.getZ(chunk));
                }
            }
        }
    }
}
