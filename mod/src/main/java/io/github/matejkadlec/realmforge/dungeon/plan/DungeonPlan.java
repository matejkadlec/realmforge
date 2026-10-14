package io.github.matejkadlec.realmforge.dungeon.plan;

import io.github.matejkadlec.realmforge.dungeon.instance.InstanceLayout;
import io.github.matejkadlec.realmforge.dungeon.instance.RoomPlacement;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;

/**
 * A planned layout (architecture 15.3, steps 1 and 2): its rooms in placement order, the entrance first, and the
 * connectors that meet. Every connector of every room meets another one, and the rooms' boxes do not overlap.
 *
 * @param seed the seed it was planned from
 * @param attempt which of the planner's attempts found it, from 1
 */
public record DungeonPlan(long seed, int attempt, List<PlannedRoom> rooms, List<RoomLink> links) {
    public DungeonPlan {
        rooms = List.copyOf(rooms);
        links = List.copyOf(links);
    }

    public PlannedRoom entrance() {
        return rooms.getFirst();
    }

    /** The box around every room, relative to the plot's origin. */
    public BoundingBox bounds() {
        return BoundingBox.encapsulatingBoxes(rooms.stream().map(PlannedRoom::box).toList()).orElseThrow();
    }

    public long count(Slot slot) {
        return rooms.stream().filter(room -> room.slot() == slot).count();
    }

    /**
     * What the builder places: every room's template, offset and rotation. Until rooms carry an entrance marker
     * (W4b), the spawn point is a fly-over spot two blocks above the entrance's roof.
     */
    public InstanceLayout toInstanceLayout() {
        List<RoomPlacement> placements = rooms.stream()
                .map(room -> new RoomPlacement(room.room().template(), room.offset(), room.rotation()))
                .toList();
        BoundingBox entrance = entrance().box();
        BlockPos center = entrance.getCenter();
        return new InstanceLayout(placements, new Vec3(center.getX() + 0.5, entrance.maxY() + 2, center.getZ() + 0.5),
                0.0F);
    }
}
