package io.github.matejkadlec.realmforge.dungeon.plan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import io.github.matejkadlec.realmforge.dungeon.DungeonType;
import io.github.matejkadlec.realmforge.dungeon.instance.PlotGrid;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.Identifier;
import net.minecraft.util.InclusiveRange;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.JigsawBlockEntity.JointType;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.junit.jupiter.api.Test;

/**
 * The planner and layout (architecture 15.3, steps 1 and 2), fuzzed over thousands of seeds on synthetic room sets
 * and on the dungeon types {@code rf gen} writes: every rule holds ({@link PlanChecks}), and the same seed gives the
 * same layout.
 */
class DungeonPlannerTest {
    private static final Identifier DOOR = Identifier.parse("realmforge:door");
    private static final Identifier SHAFT = Identifier.parse("realmforge:shaft");
    /** Where {@code rf gen} writes the dungeon types, inside the folder Gradle names (mod/build.gradle). */
    private static final String DUNGEON_TYPES = "data/realmforge/realmforge/dungeon_type";
    private static final int SEEDS = 3000;

    private static final DungeonRoom ENTRANCE =
            room("entrance", RoomRole.ENTRANCE, 7, 5, 7, door(3, 1, 6, Direction.SOUTH));
    private static final DungeonRoom CORRIDOR = room("corridor", RoomRole.HALLWAY, 5, 5, 9,
            door(2, 1, 0, Direction.NORTH), door(2, 1, 8, Direction.SOUTH));
    private static final DungeonRoom CURVE = room("curve", RoomRole.HALLWAY, 7, 5, 7,
            door(3, 1, 0, Direction.NORTH), door(6, 1, 3, Direction.EAST));
    private static final DungeonRoom CROSSING = room("crossing", RoomRole.COMBAT, 9, 5, 9,
            door(4, 1, 0, Direction.NORTH), door(4, 1, 8, Direction.SOUTH), door(0, 1, 4, Direction.WEST),
            door(8, 1, 4, Direction.EAST));
    private static final DungeonRoom JUNCTION = room("junction", RoomRole.COMBAT, 11, 6, 9,
            door(5, 1, 0, Direction.NORTH), door(0, 1, 4, Direction.WEST), door(10, 1, 4, Direction.EAST));
    private static final DungeonRoom DEAD_END =
            room("dead_end", RoomRole.HALLWAY, 5, 5, 5, door(2, 1, 0, Direction.NORTH));
    private static final DungeonRoom VAULT = room("vault", RoomRole.TREASURE, 9, 6, 9, door(4, 1, 0, Direction.NORTH));
    private static final DungeonRoom LAIR = room("lair", RoomRole.BOSS, 21, 12, 21, door(10, 1, 0, Direction.NORTH));
    /** A shaft down: its top connector turns with the room ({@code aligned}). */
    private static final DungeonRoom SHAFT_ROOM = room("shaft", RoomRole.STAIRS, 5, 12, 5,
            new Connector(new BlockPos(2, 11, 2), Direction.UP, Direction.NORTH, SHAFT, SHAFT, JointType.ALIGNED),
            new Connector(new BlockPos(2, 0, 2), Direction.DOWN, Direction.NORTH, SHAFT, SHAFT, JointType.ALIGNED));
    private static final DungeonRoom LANDING = room("landing", RoomRole.HALLWAY, 9, 6, 9,
            new Connector(new BlockPos(4, 5, 4), Direction.UP, Direction.NORTH, SHAFT, SHAFT, JointType.ALIGNED),
            door(4, 1, 8, Direction.SOUTH), door(8, 1, 4, Direction.EAST));

    private static final List<DungeonRoom> FLAT = List.of(ENTRANCE, CORRIDOR, CURVE, CROSSING, JUNCTION, DEAD_END,
            VAULT, LAIR);
    private static final List<DungeonRoom> WITH_SHAFTS = List.of(ENTRANCE, CORRIDOR, CROSSING, DEAD_END, VAULT, LAIR,
            SHAFT_ROOM, LANDING);
    private static final LayoutRules RULES = rules(3, 8, 0, 4, 1, 3, RoomRole.BOSS);

    @Test
    void sameSeedGivesTheSameLayout() {
        for (long seed = 0; seed < 50; seed++) {
            assertEquals(plan(FLAT, RULES, seed), plan(FLAT, RULES, seed), "seed " + seed);
        }
    }

    @Test
    void seedsGiveDifferentLayouts() {
        Set<DungeonPlan> layouts = new HashSet<>();
        Set<Integer> sizes = new HashSet<>();
        for (long seed = 0; seed < 100; seed++) {
            DungeonPlan plan = plan(FLAT, RULES, seed);
            layouts.add(new DungeonPlan(0, 0, plan.rooms(), plan.links()));
            sizes.add(plan.rooms().size());
        }
        assertTrue(layouts.size() > 90, "100 seeds gave only " + layouts.size() + " layouts");
        assertTrue(sizes.size() > 5, "100 seeds gave only " + sizes.size() + " room counts");
    }

    @Test
    void syntheticRoomsKeepEveryRule() {
        fuzz(FLAT, RULES);
    }

    @Test
    void verticalConnectorsKeepEveryRule() {
        fuzz(WITH_SHAFTS, rules(2, 6, 1, 3, 1, 2, RoomRole.TREASURE));
    }

    @Test
    void layoutsWithoutBranchesKeepEveryRule() {
        fuzz(FLAT, rules(1, 3, 0, 0, 1, 1, RoomRole.TREASURE));
    }

    /**
     * Done when of W4a: JUnit fuzzes the planner on the generated dungeon types, every one {@code rf gen} writes, so a
     * new type in {@code content/dungeons/} is fuzzed without an edit here (RLM-183).
     */
    @Test
    void generatedDungeonTypesKeepEveryRule() throws IOException {
        List<String> ids = generatedTypes();
        assertFalse(ids.isEmpty(), "no dungeon type in " + DUNGEON_TYPES + "; run `uv run rf gen`");
        for (String id : ids) {
            DungeonType type = generated(id);
            BoundingBox limits = PlotGrid.limits();
            for (long seed = 0; seed < SEEDS; seed++) {
                DungeonPlan plan = type.plan(seed);
                PlanChecks.assertValid(plan, type.rooms(), type.layout(), limits);
                if (seed < 20) {
                    assertEquals(plan, type.plan(seed), id + " seed " + seed + " planned twice");
                }
            }
        }
    }

    /** An arriving player looks at the entrance's connector to the next room of the main path (W4b). */
    @Test
    void aLayoutLooksAtTheEntrancesWayOn() throws IOException {
        DungeonType type = generated(generatedTypes().getFirst());
        for (long seed = 0; seed < 50; seed++) {
            DungeonPlan plan = type.plan(seed);
            RoomLink wayOn = plan.links().stream()
                    .filter(link -> Set.of(link.room(), link.otherRoom()).equals(Set.of(0, 1)))
                    .findFirst().orElseThrow();
            int connector = wayOn.room() == 0 ? wayOn.connector() : wayOn.otherConnector();
            assertEquals(Optional.of(plan.entrance().connector(connector).pos()), plan.toInstanceLayout().lookAt(),
                    "seed " + seed);
        }
    }

    @Test
    void connectorsThatNothingCloses() {
        // Crossings leave connectors free, and no dead end can close them.
        List<DungeonRoom> rooms = List.of(ENTRANCE, CROSSING, LAIR);
        PlanningException failure = assertThrows(PlanningException.class,
                () -> plan(rooms, rules(2, 3, 0, 0, 1, 1, RoomRole.BOSS), 7));
        assertTrue(failure.getMessage().contains("seed 7"), failure.getMessage());
    }

    @Test
    void limitsTooSmallForTheMainPath() {
        BoundingBox tiny = new BoundingBox(-8, -8, -8, 16, 16, 16);
        assertThrows(PlanningException.class, () -> DungeonPlanner.plan(FLAT, rules(6, 8, 0, 0, 1, 1, RoomRole.BOSS),
                3, tiny));
    }

    @Test
    void layoutsStayInsideTheLimits() {
        BoundingBox narrow = new BoundingBox(-40, -16, -40, 40, 32, 40);
        LayoutRules rules = rules(2, 3, 0, 1, 1, 1, RoomRole.TREASURE);
        for (long seed = 0; seed < 300; seed++) {
            PlanChecks.assertValid(DungeonPlanner.plan(FLAT, rules, seed, narrow), FLAT, rules, narrow);
        }
    }

    @Test
    void connectorsTurnWithTheirRoom() {
        Connector west = door(0, 1, 6, Direction.WEST);
        assertEquals(west, west.placed(BlockPos.ZERO, Rotation.NONE));
        Connector turned = west.placed(BlockPos.ZERO, Rotation.CLOCKWISE_90);
        assertEquals(new BlockPos(-6, 1, 0), turned.pos(), "a quarter turn clockwise about the origin corner");
        assertEquals(Direction.NORTH, turned.front());
        assertEquals(Direction.UP, turned.top(), "a side connector's top stays up");
        assertEquals(new BlockPos(10, 1, -1), west.placed(new BlockPos(10, 0, -7), Rotation.NONE).pos());
        Connector shaft = SHAFT_ROOM.connectors().getFirst().placed(BlockPos.ZERO, Rotation.CLOCKWISE_180);
        assertEquals(Direction.UP, shaft.front(), "up stays up");
        assertEquals(Direction.SOUTH, shaft.top(), "a vertical connector's top turns");
    }

    @Test
    void roomBoxesTurnAboutTheOriginCorner() {
        assertEquals(new BoundingBox(0, 0, 0, 4, 4, 8), CORRIDOR.box(BlockPos.ZERO, Rotation.NONE));
        assertEquals(new BoundingBox(-8, 0, 0, 0, 4, 4), CORRIDOR.box(BlockPos.ZERO, Rotation.CLOCKWISE_90));
        assertEquals(new BoundingBox(-4, 0, -8, 0, 4, 0), CORRIDOR.box(BlockPos.ZERO, Rotation.CLOCKWISE_180));
        assertEquals(new BoundingBox(0, 0, -4, 8, 4, 0), CORRIDOR.box(BlockPos.ZERO, Rotation.COUNTERCLOCKWISE_90));
        assertEquals(new BoundingBox(10, 2, 3, 14, 6, 11), CORRIDOR.box(new BlockPos(10, 2, 3), Rotation.NONE));
    }

    @Test
    void connectorsMeetByTheJigsawRules() {
        Connector north = door(2, 1, 0, Direction.NORTH);
        assertTrue(north.takes(door(2, 1, 4, Direction.SOUTH)), "opposite sides meet");
        assertFalse(north.takes(door(2, 1, 4, Direction.NORTH)), "the same side does not");
        assertFalse(north.takes(door(2, 1, 4, Direction.EAST)), "a side at a right angle does not");
        Connector other = new Connector(BlockPos.ZERO, Direction.SOUTH, Direction.UP, SHAFT, SHAFT, JointType.ALIGNED);
        assertFalse(north.takes(other), "a connector takes only connectors named as its target");

        Connector up = new Connector(BlockPos.ZERO, Direction.UP, Direction.NORTH, SHAFT, SHAFT, JointType.ALIGNED);
        Connector downNorth = new Connector(BlockPos.ZERO, Direction.DOWN, Direction.NORTH, SHAFT, SHAFT,
                JointType.ALIGNED);
        Connector downEast = new Connector(BlockPos.ZERO, Direction.DOWN, Direction.EAST, SHAFT, SHAFT,
                JointType.ALIGNED);
        assertTrue(up.takes(downNorth), "an aligned joint takes a connector with the same top");
        assertFalse(up.takes(downEast), "an aligned joint keeps its top");
        Connector rollable = new Connector(BlockPos.ZERO, Direction.UP, Direction.NORTH, SHAFT, SHAFT,
                JointType.ROLLABLE);
        assertTrue(rollable.takes(downEast), "a rollable joint takes any top");
    }

    @Test
    void weightsTilt() {
        DungeonRoom heavyVault = new DungeonRoom(VAULT.template(), VAULT.size(), VAULT.role(), 150,
                VAULT.connectors());
        DungeonRoom lightEnd = new DungeonRoom(DEAD_END.template(), DEAD_END.size(), DEAD_END.role(), 1,
                DEAD_END.connectors());
        List<DungeonRoom> rooms = List.of(ENTRANCE, CORRIDOR, CROSSING, heavyVault, lightEnd, LAIR);
        int vaults = 0;
        int ends = 0;
        for (long seed = 0; seed < 200; seed++) {
            for (PlannedRoom room : plan(rooms, RULES, seed).rooms()) {
                vaults += room.room() == heavyVault ? 1 : 0;
                ends += room.room() == lightEnd ? 1 : 0;
            }
        }
        assertTrue(vaults > ends * 10, "weight 150 against 1 gave " + vaults + " vaults and " + ends + " dead ends");
    }

    private static void fuzz(List<DungeonRoom> rooms, LayoutRules rules) {
        BoundingBox limits = PlotGrid.limits();
        for (long seed = 0; seed < SEEDS; seed++) {
            PlanChecks.assertValid(DungeonPlanner.plan(rooms, rules, seed, limits), rooms, rules, limits);
        }
    }

    private static DungeonPlan plan(List<DungeonRoom> rooms, LayoutRules rules, long seed) {
        return DungeonPlanner.plan(rooms, rules, seed, PlotGrid.limits());
    }

    /** The ids of the dungeon types {@code rf gen} wrote, in name order. */
    static List<String> generatedTypes() throws IOException {
        String root = System.getProperty("realmforge.generatedResources");
        assertTrue(root != null, "realmforge.generatedResources is not set; run the tests through Gradle");
        Path folder = Path.of(root).resolve(DUNGEON_TYPES);
        if (!Files.isDirectory(folder)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(folder)) {
            return files.map(file -> file.getFileName().toString())
                    .filter(name -> name.endsWith(".json"))
                    .map(name -> name.substring(0, name.length() - ".json".length()))
                    .sorted()
                    .toList();
        }
    }

    static DungeonType generated(String id) throws IOException {
        String path = "/" + DUNGEON_TYPES + "/" + id + ".json";
        try (InputStream stream = DungeonPlannerTest.class.getResourceAsStream(path)) {
            assertTrue(stream != null, path + " is missing; run `uv run rf gen`");
            return DungeonType.CODEC.parse(JsonOps.INSTANCE,
                    JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8))).getOrThrow();
        }
    }

    private static LayoutRules rules(int mainMin, int mainMax, int branchesMin, int branchesMax, int roomsMin,
            int roomsMax, RoomRole ending) {
        return new LayoutRules(new InclusiveRange<>(mainMin, mainMax), new InclusiveRange<>(branchesMin, branchesMax),
                new InclusiveRange<>(roomsMin, roomsMax), ending);
    }

    private static Connector door(int x, int y, int z, Direction front) {
        return new Connector(new BlockPos(x, y, z), front, Direction.UP, DOOR, DOOR, JointType.ALIGNED);
    }

    private static DungeonRoom room(String name, RoomRole role, int x, int y, int z, Connector... connectors) {
        return new DungeonRoom(Identifier.parse("realmforge:rooms/test/" + name), new Vec3i(x, y, z), role, 1,
                List.of(connectors));
    }
}
