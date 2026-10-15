package io.github.matejkadlec.realmforge.dungeon.instance;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.junit.jupiter.api.Test;

/** Where players arrive in an entrance room without an entrance marker (architecture 15.5). */
class SpawnFinderTest {
    /** A 9x5x9 room at an odd offset: its floor at y = 10, its roof at y = 14. */
    private static final BoundingBox ROOM = new BoundingBox(100, 10, -50, 108, 14, -42);

    @Test
    void aPlainRoomPutsPlayersInTheMiddleOfItsFloor() {
        assertEquals(Optional.of(new BlockPos(104, 11, -46)), SpawnFinder.find(ROOM, new Room(ROOM)));
    }

    @Test
    void aBlockedMiddleGivesTheNearestFreeSpotTheLowestOneFirst() {
        Room room = new Room(ROOM);
        room.solid(104, 11, -46);
        room.solid(104, 12, -46);
        // Four spots are as near; the lowest z, then x, comes first. On top of the pillar the head is in the roof.
        assertEquals(Optional.of(new BlockPos(104, 11, -47)), SpawnFinder.find(ROOM, room));

        room.solid(104, 11, -47);
        room.solid(104, 13, -47);
        // Standing on the new block would put the head into the one at y = 13.
        assertEquals(Optional.of(new BlockPos(103, 11, -46)), SpawnFinder.find(ROOM, room));
    }

    @Test
    void aSpotNeedsRoomForTheHead() {
        Room room = new Room(ROOM);
        for (int z = ROOM.minZ(); z <= ROOM.maxZ(); z++) {
            for (int x = ROOM.minX(); x <= ROOM.maxX(); x++) {
                room.solid(x, 12, z);
            }
        }
        // A layer at head height everywhere, and standing on it puts the head into the roof.
        assertEquals(Optional.empty(), SpawnFinder.find(ROOM, room));
    }

    @Test
    void theMiddleOfAnEvenRoomIsExact() {
        BoundingBox even = new BoundingBox(0, 0, 0, 5, 4, 5);
        // Four spots around the middle (3, 3) are as near: the lowest z, then x.
        assertEquals(Optional.of(new BlockPos(2, 1, 2)), SpawnFinder.find(even, new Room(even)));
    }

    @Test
    void aRoomWithoutAFloorHasNoSpot() {
        Room room = new Room(ROOM);
        room.floorless = true;
        assertEquals(Optional.empty(), SpawnFinder.find(ROOM, room));
    }

    /** Made-up blocks: a solid floor (unless floorless) and roof on the box's bottom and top, and blocks added. */
    private static final class Room implements SpawnFinder.Blocks {
        private final BoundingBox box;
        private final Set<BlockPos> solid = new HashSet<>();
        private boolean floorless;

        Room(BoundingBox box) {
            this.box = box;
        }

        void solid(int x, int y, int z) {
            solid.add(new BlockPos(x, y, z));
        }

        @Override
        public boolean floor(int x, int y, int z) {
            return (!floorless && y == box.minY()) || y == box.maxY() || solid.contains(new BlockPos(x, y, z));
        }

        @Override
        public boolean open(int x, int y, int z) {
            return !floor(x, y, z);
        }
    }
}
