package io.github.matejkadlec.realmforge.world.realm;

import java.util.Comparator;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * Where a trip builds a portal when none leads the right way near the scaled position (realm portal spec, "Building a
 * portal"), as plain Java over the terrain: the spot nearest the scaled position within the build radius where the
 * frame sits in the floor (Matěj, 2026-10-20: "the bottom side in the floor … so player doesn't have to jump in order
 * to get in, but can just walk"): its bottom row replaces solid floor, the rest of the frame and the opening are free,
 * and one side has floor and two free blocks in front of the opening, at its height. Spots at the same distance go by
 * how near their height lies to the preferred one (the source portal's, clamped into the arrival heights).
 */
public final class PortalSpots {
    private PortalSpots() {
    }

    /** The terrain a spot is searched in. */
    public interface Terrain {
        /** A floor block the frame's bottom row may replace, or a player may stand on. */
        boolean solid(BlockPos pos);

        /** Air, or a block that a placement replaces, without a fluid. */
        boolean free(BlockPos pos);

        /** Inside the dimension's world border. */
        boolean inside(BlockPos pos);

        /** The lowest height above the column's highest blocking block: above it there is only air. */
        int top(int x, int z);
    }

    /**
     * Where a player arrives at a frame: the side of the opening and the block they stand in, in front of the opening's
     * middle column.
     */
    public record Arrival(Direction side, BlockPos foot) {
    }

    /**
     * The best frame within {@code radius} columns of {@code origin} (a square, as vanilla's search) whose bottom row
     * lies at {@code minY} to {@code maxY}, or empty when none fits.
     */
    public static Optional<PortalFrame.Frame> find(Terrain terrain, BlockPos origin, int radius, int minY, int maxY,
            int preferredY) {
        Comparator<PortalFrame.Frame> nearest = Comparator
                .comparingLong((PortalFrame.Frame frame) -> horizontalDistanceSqr(frame, origin))
                .thenComparingInt(frame -> Math.abs(frame.sealstone().getY() - preferredY))
                .thenComparing(frame -> frame.axis() != Direction.Axis.X);
        PortalFrame.Frame best = null;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int x = origin.getX() + dx;
                int z = origin.getZ() + dz;
                int top = Math.min(maxY, terrain.top(x, z));
                for (int y = top; y >= minY; y--) {
                    // Quick reject: the sealstone replaces floor, and the opening above it is free.
                    if (!terrain.solid(pos.set(x, y, z)) || !terrain.free(pos.set(x, y + 1, z))) {
                        continue;
                    }
                    for (Direction.Axis axis : new Direction.Axis[] {Direction.Axis.X, Direction.Axis.Z}) {
                        PortalFrame.Frame frame = new PortalFrame.Frame(new BlockPos(x, y, z), axis);
                        if ((best == null || nearest.compare(frame, best) < 0) && fits(terrain, frame)) {
                            best = frame;
                        }
                    }
                }
            }
        }
        return Optional.ofNullable(best);
    }

    /** Whether {@code frame} can stand where it is: see the class comment. */
    public static boolean fits(Terrain terrain, PortalFrame.Frame frame) {
        for (int across = -1; across <= PortalFrame.WIDTH; across++) {
            BlockPos bottom = frame.at(across, -1);
            if (!terrain.inside(bottom) || !terrain.solid(bottom)) {
                return false;
            }
            for (int up = 0; up <= PortalFrame.HEIGHT; up++) {
                if (!terrain.free(frame.at(across, up))) {
                    return false;
                }
            }
        }
        return arrivalAt(terrain, frame, 0).isPresent();
    }

    /**
     * Where a player arrives at {@code frame}, facing away from it: in front of the opening, the frame's front first,
     * then its back, where the three columns there have floor and two free blocks. First at the opening's own height,
     * where a frame set into the floor is walked into, then one block lower, for a frame a player built on top of the
     * floor. Empty when neither side has room.
     */
    public static Optional<Arrival> arrival(Terrain terrain, PortalFrame.Frame frame) {
        return arrivalAt(terrain, frame, 0).or(() -> arrivalAt(terrain, frame, -1));
    }

    private static Optional<Arrival> arrivalAt(Terrain terrain, PortalFrame.Frame frame, int up) {
        for (Direction side : new Direction[] {frame.front(), frame.front().getOpposite()}) {
            boolean clear = true;
            for (int across = 0; across < PortalFrame.WIDTH && clear; across++) {
                BlockPos foot = frame.at(across, up).relative(side);
                clear = terrain.inside(foot) && terrain.solid(foot.below()) && terrain.free(foot)
                        && terrain.free(foot.above());
            }
            if (clear) {
                return Optional.of(new Arrival(side, frame.at(1, up).relative(side)));
            }
        }
        return Optional.empty();
    }

    private static long horizontalDistanceSqr(PortalFrame.Frame frame, BlockPos origin) {
        long dx = frame.sealstone().getX() - origin.getX();
        long dz = frame.sealstone().getZ() - origin.getZ();
        return dx * dx + dz * dz;
    }
}
