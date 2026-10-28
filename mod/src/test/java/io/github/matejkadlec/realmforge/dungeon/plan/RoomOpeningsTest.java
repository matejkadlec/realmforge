package io.github.matejkadlec.realmforge.dungeon.plan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import io.github.matejkadlec.realmforge.dungeon.DungeonType;
import io.github.matejkadlec.realmforge.dungeon.instance.RoomPlacement;
import java.io.IOException;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.Test;

/**
 * RLM-412: a room's hidden spawner shows on one side, drawn at random among the sides that are not the room's way in:
 * the left, the right or the opposite side, about a third each (Matěj, 2026-10-28).
 */
class RoomOpeningsTest {
    /** The husk temple's openings: the chiseled sandstone on each side of its spawner at (6, 1, 6). */
    private static final RoomOpenings HUSK_TEMPLE = new RoomOpenings(Map.of(
            Direction.NORTH, List.of(new BlockPos(6, 1, 5)), Direction.EAST, List.of(new BlockPos(7, 1, 6)),
            Direction.SOUTH, List.of(new BlockPos(6, 1, 7)), Direction.WEST, List.of(new BlockPos(5, 1, 6))));

    @Test
    void aRunNeverOpensTheSideItEntersFromAndEachOtherAboutAThird() {
        RandomSource random = RandomSource.create(11);
        int draws = 30_000;
        for (Direction wayIn : Direction.Plane.HORIZONTAL) {
            Map<Direction, Integer> opened = new EnumMap<>(Direction.class);
            for (int i = 0; i < draws; i++) {
                opened.merge(side(HUSK_TEMPLE, HUSK_TEMPLE.draw(Optional.of(wayIn), random)), 1, Integer::sum);
            }
            assertFalse(opened.containsKey(wayIn), "opened the way in, " + wayIn + ": " + opened);
            for (Direction side : List.of(wayIn.getClockWise(), wayIn.getCounterClockWise(), wayIn.getOpposite())) {
                assertEquals(1 / 3.0, opened.getOrDefault(side, 0) / (double) draws, 0.015,
                        "entered from " + wayIn + ", the share of " + side + ": " + opened);
            }
        }
    }

    @Test
    void aRoomEnteredFromAboveOrBelowOpensAnySide() {
        RandomSource random = RandomSource.create(4);
        Set<Direction> opened = new java.util.HashSet<>();
        for (int i = 0; i < 200; i++) {
            opened.add(side(HUSK_TEMPLE, HUSK_TEMPLE.draw(Optional.empty(), random)));
        }
        assertEquals(Set.copyOf(Direction.Plane.HORIZONTAL.stream().toList()), opened);
    }

    @Test
    void anOpeningClearsItsBlocksToAirAndNothingElse() {
        RoomOpenings egg = new RoomOpenings(Map.of(
                Direction.NORTH, List.of(new BlockPos(6, 4, 5), new BlockPos(5, 4, 5), new BlockPos(5, 3, 5)),
                Direction.WEST, List.of(new BlockPos(5, 4, 6), new BlockPos(5, 4, 7), new BlockPos(5, 3, 7))));
        List<RoomPlacement.Swap> swaps = egg.draw(Optional.of(Direction.WEST), RandomSource.create(1));
        assertEquals(egg.sides().get(Direction.NORTH), swaps.stream().map(RoomPlacement.Swap::pos).toList(),
                "the one side left");
        assertTrue(swaps.stream().allMatch(swap -> swap.state() == Blocks.AIR.defaultBlockState()), "air");
        assertEquals(List.of(), RoomOpenings.NONE.draw(Optional.of(Direction.WEST), RandomSource.create(1)));
    }

    @Test
    void theGeneratedEntryReadsAndRefusesUpDownAndEmptySides() {
        RoomOpenings read = RoomOpenings.CODEC.parse(JsonOps.INSTANCE,
                JsonParser.parseString("{\"north\": [[6, 1, 5]], \"west\": [[5, 1, 6]]}")).getOrThrow();
        assertEquals(List.of(new BlockPos(5, 1, 6)), read.sides().get(Direction.WEST));
        assertTrue(RoomOpenings.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{\"up\": [[6, 1, 5]]}"))
                .isError(), "an opening faces up");
        assertTrue(RoomOpenings.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{\"north\": []}"))
                .isError(), "an opening clears nothing");
    }

    /**
     * Over the test dungeon's layouts, every room with openings opens exactly one side, never the one it is entered
     * through, and the left, the right and the opposite side about a third each.
     */
    @Test
    void theTestDungeonOpensItsRoomsAwayFromTheirWayIn() throws IOException {
        DungeonType type = DungeonPlannerTest.generated("test_dungeon");
        Map<String, Integer> relative = new TreeMap<>();
        int rooms = 0;
        for (int seed = 0; seed < 300; seed++) {
            DungeonPlan plan = type.plan(seed);
            List<RoomPlacement> placements = plan.toInstanceLayout().rooms();
            for (int i = 1; i < plan.rooms().size(); i++) {
                DungeonRoom room = plan.rooms().get(i).room();
                if (room.openings().sides().isEmpty()) {
                    continue;
                }
                rooms++;
                Direction wayIn = room.connectors().get(plan.wayIn(i).orElseThrow()).front();
                Direction opened = side(room.openings(), placements.get(i).swaps());
                assertTrue(opened != wayIn, room.template() + " opened its way in, seed " + seed);
                String where = opened == wayIn.getOpposite() ? "opposite"
                        : opened == wayIn.getClockWise() ? "one side" : "other side";
                relative.merge(where, 1, Integer::sum);
            }
        }
        assertTrue(rooms > 300, "the test dungeon placed " + rooms + " rooms with openings");
        for (String where : List.of("opposite", "one side", "other side")) {
            assertEquals(1 / 3.0, relative.getOrDefault(where, 0) / (double) rooms, 0.06, where + ": " + relative);
        }
    }

    @Test
    void everyRoomButTheEntranceIsEnteredFromARoomBeforeIt() throws IOException {
        DungeonType type = DungeonPlannerTest.generated("test_dungeon");
        for (int seed = 0; seed < 20; seed++) {
            DungeonPlan plan = type.plan(seed);
            assertEquals(Optional.empty(), plan.wayIn(0), "the entrance, seed " + seed);
            for (int i = 1; i < plan.rooms().size(); i++) {
                int room = i;
                int connector = plan.wayIn(i).orElseThrow();
                int before = plan.links().stream()
                        .filter(link -> link.room() == room && link.connector() == connector
                                || link.otherRoom() == room && link.otherConnector() == connector)
                        .mapToInt(link -> link.room() == room ? link.otherRoom() : link.room())
                        .findFirst().orElseThrow();
                assertTrue(before < room, "room " + room + " is entered from room " + before + ", seed " + seed);
            }
        }
    }

    /** The side whose blocks {@code swaps} clear: exactly one side's opening. */
    private static Direction side(RoomOpenings openings, List<RoomPlacement.Swap> swaps) {
        Set<BlockPos> cleared = swaps.stream()
                .filter(swap -> swap.state() == Blocks.AIR.defaultBlockState())
                .map(RoomPlacement.Swap::pos)
                .collect(Collectors.toSet());
        List<Direction> opened = openings.sides().entrySet().stream()
                .filter(side -> cleared.containsAll(side.getValue()))
                .map(Map.Entry::getKey)
                .toList();
        assertEquals(1, opened.size(), "sides opened: " + opened);
        return opened.getFirst();
    }
}
