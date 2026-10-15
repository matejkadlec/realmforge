package io.github.matejkadlec.realmforge.qa.instance;

import io.github.matejkadlec.realmforge.dungeon.instance.InstanceLayout;
import io.github.matejkadlec.realmforge.dungeon.instance.RoomPlacement;
import io.github.matejkadlec.realmforge.qa.RealmforgeQa;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Rotation;

/**
 * The minimal planner of spike 3: the generated test rooms (tools/realmforge_tools/generate/gametest.py) in a row.
 * A start room, then corridor and hall pairs; their openings meet at z = 6..8. Test fixtures with known blocks and a
 * data marker in each room (the start room's entrance marker says where players arrive); dungeons come from the
 * planner.
 */
public final class SpikeLayouts {
    public static final Identifier START = RealmforgeQa.id("spike/start");
    public static final Identifier CORRIDOR = RealmforgeQa.id("spike/corridor");
    public static final Identifier HALL = RealmforgeQa.id("spike/hall");
    /** Rooms in the longest row that still fits a plot's reach (start plus four corridor and hall pairs). */
    public static final int MAX_ROOMS = 9;
    /** A block on the start room's floor, under its entrance marker, that tests look for. */
    public static final BlockPos MARKER = new BlockPos(7, 0, 7);
    /** The start room's entrance marker, where players arrive, and the opening an arriving player looks at. */
    public static final BlockPos ENTRANCE = MARKER.above();
    private static final BlockPos OPENING = new BlockPos(14, 1, 7);
    /** The corridor's mob marker, relative to the corridor's origin corner. */
    public static final BlockPos CORRIDOR_MOB = new BlockPos(12, 1, 3);
    /** The hall's exit marker, relative to the hall's origin corner. */
    public static final BlockPos HALL_EXIT = new BlockPos(15, 1, 27);
    /** The chest in the first hall, which holds one bread. */
    public static final BlockPos FIRST_CHEST = new BlockPos(15 + 25 + 27, 1, -8 + 15);

    private static final int START_LENGTH = 15;
    private static final int CORRIDOR_LENGTH = 25;
    private static final int HALL_LENGTH = 31;

    private SpikeLayouts() {
    }

    /** The three rooms the spike asks for: start, corridor, end. */
    public static InstanceLayout spike() {
        return row(3, false);
    }

    /**
     * A row of {@code rooms} rooms (odd, 1 to {@link #MAX_ROOMS}) along x, or turned a quarter clockwise around
     * the plot's origin so it runs along z; benchmarks alternate the two so a reused plot is never rebuilt with the
     * same blocks in the same places.
     */
    public static InstanceLayout row(int rooms, boolean turned) {
        if (rooms < 1 || rooms > MAX_ROOMS || rooms % 2 == 0) {
            throw new IllegalArgumentException("a spike row has an odd number of rooms from 1 to " + MAX_ROOMS);
        }
        List<RoomPlacement> placements = new ArrayList<>();
        placements.add(placement(START, new BlockPos(0, 0, 0), turned));
        int x = START_LENGTH;
        for (int pair = 0; pair < (rooms - 1) / 2; pair++) {
            placements.add(placement(CORRIDOR, new BlockPos(x, 0, 4), turned));
            x += CORRIDOR_LENGTH;
            placements.add(placement(HALL, new BlockPos(x, 0, -8), turned));
            x += HALL_LENGTH;
        }
        // Looking along the row: east in a straight row, south in a turned one.
        return new InstanceLayout(placements, Optional.of(turn(OPENING, turned)));
    }

    private static RoomPlacement placement(Identifier template, BlockPos offset, boolean turned) {
        return new RoomPlacement(template, turn(offset, turned), turned ? Rotation.CLOCKWISE_90 : Rotation.NONE);
    }

    /** A block position turned a quarter clockwise around the origin, as structure placement turns blocks. */
    public static BlockPos turn(BlockPos pos, boolean turned) {
        return turned ? new BlockPos(-pos.getZ(), pos.getY(), pos.getX()) : pos;
    }
}
