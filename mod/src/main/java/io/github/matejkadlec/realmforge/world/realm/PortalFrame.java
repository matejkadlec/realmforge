package io.github.matejkadlec.realmforge.world.realm;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * The realm portal's frame as plain Java over a block lookup (realm portal spec, "The frame"): a vertical rectangle 5
 * wide and 6 tall around a 3x4 opening, along either horizontal axis. The 14 blocks that touch the opening are
 * required, 13 Realmforge Bricks and the sealstone in the middle of the bottom row; the four corners are decoration.
 * So every required block touches the opening, and breaking any of them reaches a portal block through a neighbour
 * update.
 */
public final class PortalFrame {
    /** The opening's width. */
    public static final int WIDTH = 3;
    /** The opening's height. */
    public static final int HEIGHT = 4;

    /** What a position holds, as far as the frame is concerned. */
    public enum Part {
        BRICKS,
        SEALSTONE,
        /** A realm portal block. */
        PORTAL,
        /** Air, or a block that something placed there replaces, without a fluid. */
        OPEN,
        OTHER
    }

    /** What each position holds. */
    @FunctionalInterface
    public interface Lookup {
        Part at(BlockPos pos);
    }

    private PortalFrame() {
    }

    /**
     * A frame's place: its sealstone and the horizontal axis it runs along. Positions go "right" along the axis, towards
     * its positive end.
     */
    public record Frame(BlockPos sealstone, Direction.Axis axis) {
        public Frame {
            sealstone = sealstone.immutable();
            if (!axis.isHorizontal()) {
                throw new IllegalArgumentException("a frame runs along a horizontal axis, not " + axis);
            }
        }

        /** The direction along the frame. */
        public Direction right() {
            return Direction.get(Direction.AxisDirection.POSITIVE, axis);
        }

        /** The direction the frame's front faces, one of the two across it. */
        public Direction front() {
            return right().getClockWise();
        }

        /** The opening's bottom left position: above the sealstone, one to the left. */
        public BlockPos corner() {
            return sealstone.above().relative(right(), -1);
        }

        /** The opening's 12 positions, row by row from the bottom left. */
        public List<BlockPos> opening() {
            List<BlockPos> opening = new ArrayList<>(WIDTH * HEIGHT);
            for (int up = 0; up < HEIGHT; up++) {
                for (int across = 0; across < WIDTH; across++) {
                    opening.add(at(across, up));
                }
            }
            return opening;
        }

        /** The 13 positions of Realmforge Bricks: the rest of the bottom row, the sides and the top row. */
        public List<BlockPos> bricks() {
            List<BlockPos> stones = new ArrayList<>(13);
            stones.add(at(0, -1));
            stones.add(at(2, -1));
            for (int up = 0; up < HEIGHT; up++) {
                stones.add(at(-1, up));
                stones.add(at(WIDTH, up));
            }
            for (int across = 0; across < WIDTH; across++) {
                stones.add(at(across, HEIGHT));
            }
            return stones;
        }

        /** The position {@code across} to the right of the opening's corner and {@code up} above it. */
        public BlockPos at(int across, int up) {
            return corner().relative(right(), across).above(up);
        }

        /** Whether {@code pos} lies in the opening. */
        public boolean inOpening(BlockPos pos) {
            BlockPos corner = corner();
            int across = axis == Direction.Axis.X ? pos.getX() - corner.getX() : pos.getZ() - corner.getZ();
            int depth = axis == Direction.Axis.X ? pos.getZ() - corner.getZ() : pos.getX() - corner.getX();
            int up = pos.getY() - corner.getY();
            return depth == 0 && across >= 0 && across < WIDTH && up >= 0 && up < HEIGHT;
        }
    }

    /**
     * The state of a frame around a sealstone.
     *
     * @param frame where it is
     * @param missing the frame positions that do not hold the block they need
     * @param blocked the opening positions that are neither open nor a portal block
     */
    public record Check(Frame frame, List<BlockPos> missing, List<BlockPos> blocked) {
        /** Every required block is in place. */
        public boolean complete() {
            return missing.isEmpty();
        }

        /** The frame is complete and its opening can take portal blocks. */
        public boolean canLight() {
            return complete() && blocked.isEmpty();
        }

        /** Everything in the way of lighting it: the missing blocks, then the blocked opening. */
        public List<BlockPos> problems() {
            return Stream.concat(missing.stream(), blocked.stream()).toList();
        }
    }

    /** Checks {@code frame}: its 13 Realmforge Bricks, its sealstone and its opening. */
    public static Check check(Lookup lookup, Frame frame) {
        List<BlockPos> missing = new ArrayList<>();
        if (lookup.at(frame.sealstone()) != Part.SEALSTONE) {
            missing.add(frame.sealstone());
        }
        for (BlockPos stone : frame.bricks()) {
            if (lookup.at(stone) != Part.BRICKS) {
                missing.add(stone);
            }
        }
        List<BlockPos> blocked = new ArrayList<>();
        for (BlockPos pos : frame.opening()) {
            Part part = lookup.at(pos);
            if (part != Part.OPEN && part != Part.PORTAL) {
                blocked.add(pos);
            }
        }
        return new Check(frame, List.copyOf(missing), List.copyOf(blocked));
    }

    /**
     * The frame around the sealstone at {@code sealstone}: the axis whose frame can light, X first; when neither can,
     * the one with the fewest problems, so the particles mark what is left of the frame the player is building.
     */
    public static Check around(Lookup lookup, BlockPos sealstone) {
        return Stream.of(Direction.Axis.X, Direction.Axis.Z)
                .map(axis -> check(lookup, new Frame(sealstone, axis)))
                .min(Comparator.comparing((Check check) -> !check.canLight())
                        .thenComparingInt(check -> check.problems().size()))
                .orElseThrow();
    }

    /**
     * The complete frame whose opening holds the portal block at {@code portal}, along {@code axis}, the portal's
     * axis: below the opening's bottom row of portal blocks, one of the three positions there is its sealstone.
     */
    public static Optional<Frame> ofPortal(Lookup lookup, BlockPos portal, Direction.Axis axis) {
        BlockPos.MutableBlockPos bottom = portal.mutable();
        for (int i = 0; i < HEIGHT && lookup.at(bottom.below()) == Part.PORTAL; i++) {
            bottom.move(Direction.DOWN);
        }
        Direction right = Direction.get(Direction.AxisDirection.POSITIVE, axis);
        for (int offset = -1; offset <= 1; offset++) {
            BlockPos sealstone = bottom.below().relative(right, offset);
            if (lookup.at(sealstone) != Part.SEALSTONE) {
                continue;
            }
            Frame frame = new Frame(sealstone, axis);
            if (frame.inOpening(portal) && check(lookup, frame).complete()) {
                return Optional.of(frame);
            }
        }
        return Optional.empty();
    }
}
