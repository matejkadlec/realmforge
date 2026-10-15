package io.github.matejkadlec.realmforge.dungeon.instance;

import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * Where players arrive in a plot whose entrance room has no entrance marker (architecture 15.5): the spot inside the
 * room nearest its middle where a player stands on a floor with room for their head, the lowest one of those as near.
 * It reads blocks only through {@link Blocks}, so JUnit tests it on made-up rooms and {@link InstanceManager} asks the
 * level.
 */
public final class SpawnFinder {
    private SpawnFinder() {
    }

    /** What the blocks of a room are like, by position. */
    public interface Blocks {
        /** Whether a player can stand on top of this block. */
        boolean floor(int x, int y, int z);

        /** Whether a player's feet or head fit into this block: nothing to collide with and no fluid. */
        boolean open(int x, int y, int z);
    }

    /** Where in {@code room} a player arrives (the block their feet are in), if anywhere. */
    public static Optional<BlockPos> find(BoundingBox room, Blocks blocks) {
        // Doubled coordinates keep the middle of an even-sized room exact.
        int middleX = room.minX() + room.maxX();
        int middleZ = room.minZ() + room.maxZ();
        @Nullable
        BlockPos best = null;
        long bestDistance = Long.MAX_VALUE;
        // The floor and the head stay inside the room, so a spot on its roof never counts.
        for (int y = room.minY() + 1; y < room.maxY(); y++) {
            for (int z = room.minZ(); z <= room.maxZ(); z++) {
                for (int x = room.minX(); x <= room.maxX(); x++) {
                    long dx = 2L * x - middleX;
                    long dz = 2L * z - middleZ;
                    long distance = dx * dx + dz * dz;
                    // A tie keeps the spot found first: the lower one, then the lower z and x.
                    if (distance < bestDistance && blocks.floor(x, y - 1, z) && blocks.open(x, y, z)
                            && blocks.open(x, y + 1, z)) {
                        best = new BlockPos(x, y, z);
                        bestDistance = distance;
                    }
                }
            }
        }
        return Optional.ofNullable(best);
    }
}
