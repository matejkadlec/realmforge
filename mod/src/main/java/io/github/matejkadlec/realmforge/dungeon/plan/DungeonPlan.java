package io.github.matejkadlec.realmforge.dungeon.plan;

import io.github.matejkadlec.realmforge.dungeon.instance.InstanceLayout;
import io.github.matejkadlec.realmforge.dungeon.instance.RoomPlacement;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * A planned layout (architecture 15.3, steps 1 and 2): its rooms in placement order, the entrance first, and the
 * connectors that meet. Every connector of every room meets another one, and the rooms' boxes do not overlap.
 *
 * @param seed the seed it was planned from
 * @param attempt which of the planner's attempts found it, from 1
 */
public record DungeonPlan(long seed, int attempt, List<PlannedRoom> rooms, List<RoomLink> links) {
    /** Keeps the containers' draws apart from the planner's, which starts from the same seed. */
    private static final long CONTAINER_SALT = 0x2B7E151628AED2A6L;

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
     * What the builder places: every room's template, offset and rotation with the blocks an explosion may break
     * there and the containers its chance tables drop (RLM-365, drawn in room order from the layout's seed, so the same
     * seed always gives the same rooms), and the entrance's connector to the next room on the main path, which an
     * arriving player looks at. Where players arrive comes from the entrance room (its entrance marker, or a spot the
     * builder finds there).
     */
    public InstanceLayout toInstanceLayout() {
        RandomSource containers = RandomSource.create(seed ^ CONTAINER_SALT);
        List<RoomPlacement> placements = rooms.stream()
                .map(room -> new RoomPlacement(room.room().template(), room.offset(), room.rotation(),
                        room.room().explosionBreaks(), room.room().containers().draw(containers)))
                .toList();
        Optional<BlockPos> wayOn = links.stream()
                .filter(link -> link.room() == 0 && link.otherRoom() == 1 || link.room() == 1 && link.otherRoom() == 0)
                .findFirst()
                .map(link -> entrance().connector(link.room() == 0 ? link.connector() : link.otherConnector()).pos());
        return new InstanceLayout(placements, wayOn);
    }
}
