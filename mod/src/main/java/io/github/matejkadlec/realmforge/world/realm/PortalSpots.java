package io.github.matejkadlec.realmforge.world.realm;

import java.util.Comparator;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * Where a trip builds a portal when none leads the right way near the scaled position (realm portal spec, "Building a
 * portal"), as plain Java over the terrain: the spot nearest the scaled position within the build radius, where the
 * frame's bottom row replaces solid floor, the rest of the frame and the opening are free, and one side in front of
 * the opening has floor and two free blocks to arrive on. Spots at the same distance go by how near their height lies
 * to the preferred one (the source portal's, clamped into the arrival heights).
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

        /** The highest bottom row height worth trying in the column: above it there is only air. */
        int top(int x, int z);
    }

    /**
     * The best frame within {@code radius} columns of {@code origin} (a square, as vanilla's search) whose bottom row
     * lies at {@code minY} to {@code maxY}, or empty when none fits.
     */
    public static Optional<PortalFrame.Frame> find(Terrain terrain, BlockPos origin, int radius, int minY, int maxY,
            int preferredY) {
        Comparator<PortalFrame.Frame> nearest = Comparator
                .comparingLong((PortalFrame.Frame frame) -> horizontalDistanceSqr(frame, origin))
                .thenComparingInt(frame -> Math.abs(frame.selector().getY() - preferredY))
                .thenComparing(frame -> frame.axis() != Direction.Axis.X);
        PortalFrame.Frame best = null;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int x = origin.getX() + dx;
                int z = origin.getZ() + dz;
                int top = Math.min(maxY, terrain.top(x, z));
                for (int y = top; y >= minY; y--) {
                    // Quick reject: the selector replaces floor and the opening above it is free.
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
        return arrival(terrain, frame).isPresent();
    }

    /**
     * The side of the opening a player arrives on: the frame's front first, then its back, where the three columns in
     * front of the opening have floor and two free blocks; empty when neither has.
     */
    public static Optional<Direction> arrival(Terrain terrain, PortalFrame.Frame frame) {
        for (Direction side : new Direction[] {frame.front(), frame.front().getOpposite()}) {
            boolean clear = true;
            for (int across = 0; across < PortalFrame.WIDTH && clear; across++) {
                BlockPos foot = frame.at(across, 0).relative(side);
                clear = terrain.inside(foot) && terrain.solid(foot.below()) && terrain.free(foot)
                        && terrain.free(foot.above());
            }
            if (clear) {
                return Optional.of(side);
            }
        }
        return Optional.empty();
    }

    private static long horizontalDistanceSqr(PortalFrame.Frame frame, BlockPos origin) {
        long dx = frame.selector().getX() - origin.getX();
        long dz = frame.selector().getZ() - origin.getZ();
        return dx * dx + dz * dz;
    }
}
