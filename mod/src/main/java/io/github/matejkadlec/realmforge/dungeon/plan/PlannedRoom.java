package io.github.matejkadlec.realmforge.dungeon.plan;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * A room of a planned layout: which room, the slot it takes, and where it goes relative to the plot's origin.
 *
 * @param offset where the room's origin corner goes
 * @param rotation how the room is turned about its origin corner
 * @param box the blocks it takes, relative to the plot's origin
 */
public record PlannedRoom(DungeonRoom room, Slot slot, BlockPos offset, Rotation rotation, BoundingBox box) {
    /** Its connector {@code index} in place: where the jigsaw block ends up and the way it faces. */
    public Connector connector(int index) {
        return room.connectors().get(index).placed(offset, rotation);
    }
}
