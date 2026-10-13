package io.github.matejkadlec.realmforge.qa.instance;

import io.github.matejkadlec.realmforge.dungeon.instance.InstanceLayout;
import io.github.matejkadlec.realmforge.dungeon.instance.RoomPlacement;
import io.github.matejkadlec.realmforge.qa.RealmforgeQa;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.Vec3;

/**
 * The minimal planner of spike 3: the generated test rooms (tools/realmforge_tools/generate/gametest.py) in a row.
 * A start room, then corridor and hall pairs; their openings meet at z = 6..8. W4 replaces this with the real planner.
 */
public final class SpikeLayouts {
    public static final Identifier START = RealmforgeQa.id("spike/start");
    public static final Identifier CORRIDOR = RealmforgeQa.id("spike/corridor");
    public static final Identifier HALL = RealmforgeQa.id("spike/hall");
    /** Rooms in the longest row that still fits a plot's reach (start plus four corridor and hall pairs). */
    public static final int MAX_ROOMS = 9;
    /** A block on the start room's floor, under the spawn point, that tests look for. */
    public static final BlockPos MARKER = new BlockPos(7, 0, 7);
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
        BlockPos spawn = turn(MARKER.above(), turned);
        // Facing along the row: east (-90) in a straight row, south (0) in a turned one.
        return new InstanceLayout(placements, Vec3.atBottomCenterOf(spawn), turned ? 0.0F : -90.0F);
    }

    private static RoomPlacement placement(Identifier template, BlockPos offset, boolean turned) {
        return new RoomPlacement(template, turn(offset, turned), turned ? Rotation.CLOCKWISE_90 : Rotation.NONE);
    }

    /** A block position turned a quarter clockwise around the origin, as structure placement turns blocks. */
    public static BlockPos turn(BlockPos pos, boolean turned) {
        return turned ? new BlockPos(-pos.getZ(), pos.getY(), pos.getX()) : pos;
    }
}
