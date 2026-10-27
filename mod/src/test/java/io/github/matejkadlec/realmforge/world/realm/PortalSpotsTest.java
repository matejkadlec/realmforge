package io.github.matejkadlec.realmforge.world.realm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

/** Where a trip builds a portal, and the link-back arithmetic (realm portal spec, "Linking", "Building a portal"). */
class PortalSpotsTest {
    /** Overworld's arrival, as {@code content/realms/overworld.yaml} gives it. */
    private static final Realm.Arrival OVERWORLD = new Realm.Arrival(63, 310, Identifier.withDefaultNamespace(
            "stone_bricks"), 3);

    /** Terrain from a rule: solid where {@code solid} says so, free above, nothing else in the way. */
    private record Terrain(Predicate<BlockPos> solid, Predicate<BlockPos> water, int border, int top)
            implements PortalSpots.Terrain {
        @Override
        public boolean solid(BlockPos pos) {
            return solid.test(pos);
        }

        @Override
        public boolean free(BlockPos pos) {
            return !solid.test(pos) && !water.test(pos);
        }

        @Override
        public boolean inside(BlockPos pos) {
            return Math.abs(pos.getX()) < border && Math.abs(pos.getZ()) < border;
        }

        @Override
        public int top(int x, int z) {
            return top;
        }
    }

    /** A cave: floor up to {@code floorY}, open above it to a ceiling at {@code ceilingY}. */
    private static Terrain cave(int floorY, int ceilingY) {
        return new Terrain(pos -> pos.getY() <= floorY || pos.getY() >= ceilingY, pos -> false, 1_000_000, 127);
    }

    /** A cave: floor up to {@code floorY}, open above it to a ceiling at y 100. */
    private static Terrain cave(int floorY) {
        return cave(floorY, 100);
    }

    @Test
    void aSpotStandsOnTheFloorNearestTheScaledPosition() {
        BlockPos origin = new BlockPos(40, 70, -12);
        Optional<PortalFrame.Frame> spot = PortalSpots.find(cave(32), origin, 7, 20, 90, 70);
        assertTrue(spot.isPresent());
        // The bottom row replaces the floor's top block, so the opening is at floor level (Matěj, 2026-10-20: "the
        // bottom side in the floor … so player doesn't have to jump in order to get in, but can just walk").
        assertEquals(new BlockPos(40, 32, -12), spot.get().sealstone());
        assertEquals(Direction.Axis.X, spot.get().axis());
        // The player arrives on the floor in front of the opening, at its height, and walks straight in.
        assertEquals(Optional.of(new PortalSpots.Arrival(Direction.SOUTH, new BlockPos(40, 33, -11))),
                PortalSpots.arrival(cave(32), spot.get()));
    }

    /**
     * A frame a player built on top of the floor, its bottom row one block above it, as most player-built frames stand
     * (RLM-341): no side has floor at the opening's height, so the player arrives one step lower, on the floor in front
     * of the frame, not in the opening.
     */
    @Test
    void aPlayerArrivesOnTheFloorInFrontOfAFrameBuiltOnTopOfIt() {
        PortalFrame.Frame onTop = new PortalFrame.Frame(new BlockPos(40, 33, -12), Direction.Axis.X);
        assertEquals(Optional.of(new PortalSpots.Arrival(Direction.SOUTH, new BlockPos(40, 33, -11))),
                PortalSpots.arrival(cave(32), onTop));
    }

    @Test
    void aFrameNeedsItsWholeHeightAboveTheFloor() {
        // Floor up to y 32: the frame takes y 32 (its bottom row, in the floor) to 37, so a ceiling at y 37 leaves no
        // room and one at y 38 does.
        assertEquals(Optional.empty(), PortalSpots.find(cave(32, 37), BlockPos.ZERO, 7, 20, 90, 64));
        assertEquals(32, PortalSpots.find(cave(32, 38), BlockPos.ZERO, 7, 20, 90, 64).orElseThrow().sealstone()
                .getY());
    }

    @Test
    void aSpotStaysWithinTheBuildRadiusAndTheHeights() {
        // A floor at y 10 lies below the arrival heights, 20 to 90.
        assertEquals(Optional.empty(), PortalSpots.find(cave(10), BlockPos.ZERO, 7, 20, 90, 64));
        // Floor only from x 9 on: beyond a radius of 7 from x 0, within one of 16.
        Terrain shelf = new Terrain(pos -> pos.getX() >= 9 && pos.getY() <= 32, pos -> false, 1_000_000, 127);
        assertEquals(Optional.empty(), PortalSpots.find(shelf, BlockPos.ZERO, 7, 20, 90, 64));
        Optional<PortalFrame.Frame> spot = PortalSpots.find(shelf, BlockPos.ZERO, 16, 20, 90, 64);
        assertTrue(spot.isPresent());
        // The whole bottom row needs floor: on the shelf's edge only a frame across x fits, arrived at from the east.
        assertEquals(new PortalFrame.Frame(new BlockPos(9, 32, 0), Direction.Axis.Z), spot.get());
        assertEquals(Optional.of(Direction.EAST),
                PortalSpots.arrival(shelf, spot.get()).map(PortalSpots.Arrival::side));
    }

    @Test
    void theSourceHeightDecidesBetweenSpotsAsNear() {
        // Two floors in one column: a ledge at y 50 above the cave floor at y 32.
        Terrain ledges = new Terrain(pos -> pos.getY() <= 32 || pos.getY() == 50 || pos.getY() >= 100,
                pos -> false, 1_000_000, 127);
        assertEquals(50, PortalSpots.find(ledges, BlockPos.ZERO, 7, 20, 90, 70).orElseThrow().sealstone().getY());
        assertEquals(32, PortalSpots.find(ledges, BlockPos.ZERO, 7, 20, 90, 20).orElseThrow().sealstone().getY());
    }

    @Test
    void noSpotOutsideTheWorldBorder() {
        // A world 3 blocks wide each way: no frame, 5 wide, fits along either axis.
        Terrain small = new Terrain(pos -> pos.getY() <= 32, pos -> false, 2, 127);
        assertEquals(Optional.empty(), PortalSpots.find(small, BlockPos.ZERO, 7, 20, 90, 64));
    }

    /**
     * The way back from a realm portal at y 30 to Overworld columns of deep ocean finds no spot at Overworld's arrival
     * heights, so the trip carves a frame on a platform at y 63, the first block above the water, never under the
     * seabed.
     */
    @Test
    void deepOceanHasNoSpotSoTheWayBackBuildsAPlatformAtSixtyThree() {
        Terrain ocean = new Terrain(pos -> pos.getY() <= 30, pos -> pos.getY() > 30 && pos.getY() <= 62, 1_000_000,
                63);
        assertEquals(Optional.empty(), PortalSpots.find(ocean, BlockPos.ZERO, 16, OVERWORLD.minY(), OVERWORLD.maxY(),
                OVERWORLD.clampY(30)));
        assertEquals(63, OVERWORLD.clampY(30));
    }

    /**
     * Low Overworld ground, a plains or a shore whose grass is at y 63 just above the sea's water at 62, takes a frame
     * set into it (RLM-341): the way back builds there instead of carving a platform one block above the grass.
     */
    @Test
    void lowOverworldGroundAtSixtyThreeTakesAFrame() {
        Terrain plains = new Terrain(pos -> pos.getY() <= 63, pos -> false, 1_000_000, 64);
        assertEquals(63, PortalSpots.find(plains, BlockPos.ZERO, 16, OVERWORLD.minY(), OVERWORLD.maxY(),
                OVERWORLD.clampY(30)).orElseThrow().sealstone().getY());
    }

    @Test
    void theRadiiFollowVanillasArithmetic() {
        assertEquals(8, RealmPortalLinker.realmSearchRadius(16.0));
        assertEquals(16, RealmPortalLinker.realmSearchRadius(8.0));
        assertEquals(128, RealmPortalLinker.OVERWORLD_SEARCH_RADIUS);
        assertEquals(16, RealmPortalLinker.OVERWORLD_BUILD_RADIUS);
    }

    /**
     * From every Overworld x in one 16-block cell, a realm portal built at either end of the realm's build radius
     * (⌊128 ÷ 16⌋ − 1 = 7) aims back within Overworld's search, so the way back finds the portal the trip came from,
     * also from x 15, which the scale rounds down the most.
     */
    @Test
    void aPortalBuiltAtEitherEndOfTheRadiusLinksBack() {
        double scale = 16.0;
        int build = RealmPortalLinker.realmSearchRadius(scale) - 1;
        assertEquals(7, build);
        for (int overworldX = 0; overworldX < 16; overworldX++) {
            int aimed = (int) Math.floor((overworldX + 0.5) / scale);
            for (int realmX : new int[] {aimed - build, aimed + build}) {
                int back = (int) Math.floor((realmX + 0.5) * scale);
                assertTrue(Math.abs(back - overworldX) <= RealmPortalLinker.OVERWORLD_SEARCH_RADIUS,
                        "Overworld x " + overworldX + ", realm portal at " + realmX + " aims back at " + back);
            }
            // One block further, the worst case at x 15 would miss: the rounding is what the -1 is for.
            int tooFar = (int) Math.floor((aimed - build - 1 + 0.5) * scale);
            if (overworldX == 15) {
                assertTrue(Math.abs(tooFar - overworldX) > RealmPortalLinker.OVERWORLD_SEARCH_RADIUS);
            }
        }
    }
}
