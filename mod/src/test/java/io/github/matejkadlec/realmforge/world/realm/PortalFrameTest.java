package io.github.matejkadlec.realmforge.world.realm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.matejkadlec.realmforge.world.realm.PortalFrame.Frame;
import io.github.matejkadlec.realmforge.world.realm.PortalFrame.Part;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

/** The realm portal's frame check (realm portal spec, "The frame", "Tests"). */
class PortalFrameTest {
    private static final BlockPos SEALSTONE = new BlockPos(10, 64, -20);

    /** A world of open air but for the blocks put in it. */
    private static final class World implements PortalFrame.Lookup {
        private final Map<BlockPos, Part> parts = new HashMap<>();

        @Override
        public Part at(BlockPos pos) {
            return parts.getOrDefault(pos, Part.OPEN);
        }

        World put(BlockPos pos, Part part) {
            parts.put(pos.immutable(), part);
            return this;
        }

        /** A complete frame along {@code axis} around {@code sealstone}, corners included when asked. */
        World frame(BlockPos sealstone, Direction.Axis axis, boolean corners) {
            Frame frame = new Frame(sealstone, axis);
            put(sealstone, Part.SEALSTONE);
            frame.bricks().forEach(pos -> put(pos, Part.BRICKS));
            if (corners) {
                for (BlockPos corner : List.of(frame.at(-1, -1), frame.at(3, -1), frame.at(-1, 4), frame.at(3, 4))) {
                    put(corner, Part.BRICKS);
                }
            }
            return this;
        }
    }

    @Test
    void aFrameIsFourteenBlocksAroundAThreeByFourOpening() {
        Frame frame = new Frame(SEALSTONE, Direction.Axis.X);
        assertEquals(13, frame.bricks().size());
        assertEquals(12, frame.opening().size());
        assertEquals(new BlockPos(9, 65, -20), frame.corner());
        assertEquals(new BlockPos(11, 68, -20), frame.at(2, 3));
        assertTrue(frame.inOpening(new BlockPos(10, 66, -20)));
        assertFalse(frame.inOpening(new BlockPos(10, 66, -19)), "the opening is one block deep");
        assertFalse(frame.inOpening(new BlockPos(12, 66, -20)), "the side is no opening");
        assertEquals(Direction.SOUTH, frame.front());
        assertEquals(Direction.WEST, new Frame(SEALSTONE, Direction.Axis.Z).front());
    }

    @Test
    void aCompleteFrameLightsAlongEitherAxisWithOrWithoutCorners() {
        for (Direction.Axis axis : List.of(Direction.Axis.X, Direction.Axis.Z)) {
            for (boolean corners : List.of(true, false)) {
                PortalFrame.Check check = PortalFrame.around(new World().frame(SEALSTONE, axis, corners), SEALSTONE);
                assertTrue(check.canLight(), axis + ", corners " + corners);
                assertEquals(axis, check.frame().axis());
            }
        }
    }

    @Test
    void aMissingSideBlockIsNamed() {
        Frame frame = new Frame(SEALSTONE, Direction.Axis.X);
        BlockPos side = frame.at(3, 2);
        World world = new World().frame(SEALSTONE, Direction.Axis.X, false).put(side, Part.OPEN);
        PortalFrame.Check check = PortalFrame.around(world, SEALSTONE);
        assertFalse(check.complete());
        assertEquals(List.of(side), check.missing());
        assertEquals(List.of(side), check.problems());
    }

    @Test
    void aBlockInTheOpeningBlocksIt() {
        Frame frame = new Frame(SEALSTONE, Direction.Axis.X);
        BlockPos inside = frame.at(1, 2);
        PortalFrame.Check check = PortalFrame.around(
                new World().frame(SEALSTONE, Direction.Axis.X, false).put(inside, Part.OTHER), SEALSTONE);
        assertTrue(check.complete());
        assertFalse(check.canLight());
        assertEquals(List.of(inside), check.blocked());
    }

    @Test
    void anOpeningOfPortalBlocksCanBeSwitched() {
        World world = new World().frame(SEALSTONE, Direction.Axis.X, false);
        new Frame(SEALSTONE, Direction.Axis.X).opening().forEach(pos -> world.put(pos, Part.PORTAL));
        assertTrue(PortalFrame.around(world, SEALSTONE).canLight());
    }

    @Test
    void aSealstoneOffCentreIsNoFrame() {
        // The frame of a sealstone one block further right: the real sealstone stands where Realmforge Bricks should.
        BlockPos offCentre = SEALSTONE.east();
        World world = new World().frame(SEALSTONE, Direction.Axis.X, false).put(offCentre, Part.SEALSTONE)
                .put(SEALSTONE, Part.BRICKS);
        assertFalse(PortalFrame.around(world, offCentre).complete());
    }

    @Test
    void framesTooBigOrTooSmallAreIncomplete() {
        // Too wide: the right side stands one block further out.
        Frame frame = new Frame(SEALSTONE, Direction.Axis.X);
        World wide = new World().frame(SEALSTONE, Direction.Axis.X, false);
        for (int up = 0; up < PortalFrame.HEIGHT; up++) {
            wide.put(frame.at(3, up), Part.OPEN).put(frame.at(4, up), Part.BRICKS);
        }
        assertFalse(PortalFrame.around(wide, SEALSTONE).complete());
        // Too low: the top row stands one block lower, inside the opening.
        World low = new World().frame(SEALSTONE, Direction.Axis.X, false);
        for (int across = 0; across < PortalFrame.WIDTH; across++) {
            low.put(frame.at(across, 4), Part.OPEN).put(frame.at(across, 3), Part.BRICKS);
        }
        assertFalse(PortalFrame.around(low, SEALSTONE).complete());
    }

    @Test
    void aPortalBlockFindsItsCompleteFrame() {
        World world = new World().frame(SEALSTONE, Direction.Axis.Z, true);
        Frame frame = new Frame(SEALSTONE, Direction.Axis.Z);
        frame.opening().forEach(pos -> world.put(pos, Part.PORTAL));
        for (BlockPos pos : frame.opening()) {
            assertEquals(Optional.of(frame), PortalFrame.ofPortal(world, pos, Direction.Axis.Z), pos.toString());
        }
        world.put(frame.at(-1, 3), Part.OPEN);
        assertEquals(Optional.empty(), PortalFrame.ofPortal(world, frame.at(2, 3), Direction.Axis.Z),
                "a broken frame holds no portal");
    }
}
