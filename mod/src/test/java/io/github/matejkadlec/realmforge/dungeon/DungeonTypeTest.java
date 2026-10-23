package io.github.matejkadlec.realmforge.dungeon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import io.github.matejkadlec.realmforge.dungeon.plan.Connector;
import io.github.matejkadlec.realmforge.dungeon.plan.DungeonRoom;
import io.github.matejkadlec.realmforge.dungeon.plan.LayoutRules;
import io.github.matejkadlec.realmforge.dungeon.plan.RoomRole;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.entity.JigsawBlockEntity.JointType;
import org.junit.jupiter.api.Test;

/** The codec of {@code realmforge:dungeon_type} entries and the files {@code rf gen} writes for them. */
class DungeonTypeTest {
    private static final String ROOM = """
            {"template": "realmforge:rooms/test/hall", "size": [9, 5, 9], "role": "combat", "weight": 3,
             "connectors": [{"pos": [4, 1, 0], "front": "north", "top": "up", "name": "realmforge:door",
                             "target": "realmforge:door", "joint": "aligned"}]}
            """;

    @Test
    void codecReadsTheGeneratedFormat() throws IOException {
        String path = "/data/realmforge/realmforge/dungeon_type/test_dungeon.json";
        try (InputStream stream = DungeonTypeTest.class.getResourceAsStream(path)) {
            assertTrue(stream != null, path + " is missing; run `uv run rf gen`");
            JsonElement json = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
            DungeonType type = DungeonType.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
            assertEquals("dungeon_type.realmforge.test_dungeon.name", type.name());
            assertEquals(Identifier.parse("realmforge:stone_vault"), type.pool());
            assertEquals(RoomRole.TREASURE, type.layout().ending());
            assertTrue(type.rooms().stream().anyMatch(room -> room.role() == RoomRole.ENTRANCE), "an entrance");
            assertTrue(type.rooms().stream().allMatch(room -> room.template().getPath().startsWith(
                    room.role() == RoomRole.WALL ? "dungeon_walls/" : "rooms/")), "rooms name their structures");
            assertTrue(type.layout().specialGap().isPresent(), "the test dungeon spaces its special rooms (RLM-364)");
            assertEquals(1, type.rooms().stream().filter(room -> room.role() == RoomRole.WALL).count(),
                    "the test dungeon has its wall (RLM-364)");
            assertTrue(type.rooms().stream().anyMatch(room -> room.entry().isPresent()),
                    "its locks have entries (RLM-362)");
        }
    }

    @Test
    void roomCodecReadsEveryField() {
        DungeonRoom room = DungeonRoom.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(ROOM)).getOrThrow();
        assertEquals(Identifier.parse("realmforge:rooms/test/hall"), room.template());
        assertEquals(RoomRole.COMBAT, room.role());
        assertEquals(3, room.weight());
        assertEquals(new Connector(new BlockPos(4, 1, 0), Direction.NORTH, Direction.UP,
                Identifier.parse("realmforge:door"), Identifier.parse("realmforge:door"), JointType.ALIGNED),
                room.connectors().getFirst());
    }

    @Test
    void codecRoundTrips() {
        DungeonRoom room = DungeonRoom.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(ROOM)).getOrThrow();
        JsonElement json = DungeonRoom.CODEC.encodeStart(JsonOps.INSTANCE, room).getOrThrow();
        assertEquals(room, DungeonRoom.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow());
        LayoutRules rules = layout("[2, 4]", "[0, 1]", "[1, 2]", "boss").getOrThrow();
        JsonElement encoded = LayoutRules.CODEC.encodeStart(JsonOps.INSTANCE, rules).getOrThrow();
        assertEquals(rules, LayoutRules.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow());
        LayoutRules spaced = layout("[2, 4]", "[0, 1]", "[1, 2]", "boss", "[3, 5]").getOrThrow();
        assertEquals(java.util.Optional.of(new net.minecraft.util.InclusiveRange<>(3, 5)), spaced.specialGap());
        encoded = LayoutRules.CODEC.encodeStart(JsonOps.INSTANCE, spaced).getOrThrow();
        assertEquals(spaced, LayoutRules.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow());
        DungeonRoom lock = room(ROOM.replace("\"weight\": 3", "\"weight\": 3, \"entry\": 0")).getOrThrow();
        assertEquals(java.util.Optional.of(0), lock.entry(), "RLM-362");
        assertEquals(lock, room(DungeonRoom.CODEC.encodeStart(JsonOps.INSTANCE, lock).getOrThrow().toString())
                .getOrThrow());
    }

    @Test
    void codecRejectsBrokenLayouts() {
        assertTrue(layout("[0, 4]", "[0, 1]", "[1, 2]", "boss").isError(), "a main path needs a room");
        assertTrue(layout("[2, 4]", "[3, 1]", "[1, 2]", "boss").isError(), "min above max");
        assertTrue(layout("[2, 4]", "[0, 17]", "[1, 2]", "boss").isError(), "too many branches");
        assertTrue(layout("[2, 4]", "[0, 1]", "[1, 9]", "boss").isError(), "branches too long");
        assertTrue(layout("[2, 4]", "[0, 1]", "[1, 2]", "entrance").isError(), "a dungeon cannot end in an entrance");
        assertTrue(layout("[2, 4]", "[0, 1]", "[1, 2]", "combat").isError(), "nor in a combat room");
        assertTrue(layout("[2, 4]", "[0, 1]", "[1, 2]", "treasure").isSuccess(), "a treasury ends it");
    }

    @Test
    void codecRejectsBrokenRooms() {
        assertTrue(room(ROOM.replace("[9, 5, 9]", "[0, 5, 9]")).isError(), "an empty room");
        assertTrue(room(ROOM.replace("[9, 5, 9]", "[9, 49, 9]")).isError(), "a room above the size limit");
        assertTrue(room(ROOM.replace("[4, 1, 0]", "[4, 1, 9]")).isError(), "a connector outside the room");
        assertTrue(room(ROOM.replace("\"top\": \"up\"", "\"top\": \"south\"")).isError(),
                "a side connector whose top is not up");
        assertTrue(room(ROOM.replace("\"front\": \"north\"", "\"front\": \"up\"")).isError(),
                "a vertical connector whose top is up");
        assertTrue(room(ROOM.replace("\"weight\": 3", "\"weight\": 151")).isError(), "weight above 150");
        assertTrue(room(ROOM.replace("\"weight\": 3", "\"weight\": 3, \"entry\": 1")).isError(),
                "an entry the room does not have");
        assertTrue(room(ROOM.replace("\"combat\"", "\"wall\"")).isSuccess(), "a wall with one connector");
        assertTrue(room(ROOM.replace("\"combat\"", "\"wall\"").replace("}]}", "}, {\"pos\": [4, 1, 8], "
                + "\"front\": \"south\", \"top\": \"up\", \"name\": \"realmforge:door\", "
                + "\"target\": \"realmforge:door\", \"joint\": \"aligned\"}]}")).isError(), "a wall with two");
        assertTrue(layout("[2, 4]", "[0, 1]", "[1, 2]", "boss", "[0, 3]").isError(),
                "a special gap of no room");
    }

    private static DataResult<DungeonRoom> room(String json) {
        return DungeonRoom.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json));
    }

    private static DataResult<LayoutRules> layout(String mainPath, String branches, String branchRooms,
            String ending) {
        return layout(mainPath, branches, branchRooms, ending, null);
    }

    /** Layout rules with a {@code special_gap} ({@code null}: none). */
    private static DataResult<LayoutRules> layout(String mainPath, String branches, String branchRooms,
            String ending, String specialGap) {
        String gap = specialGap == null ? "" : ", \"special_gap\": " + specialGap;
        return LayoutRules.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(
                "{\"main_path\": " + mainPath + ", \"branches\": " + branches + ", \"branch_rooms\": " + branchRooms
                        + ", \"ending\": \"" + ending + "\"" + gap + "}"));
    }
}
