package io.github.matejkadlec.realmforge.dungeon.plan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import io.github.matejkadlec.realmforge.dungeon.DungeonType;
import io.github.matejkadlec.realmforge.dungeon.instance.InstanceLayout;
import io.github.matejkadlec.realmforge.dungeon.instance.PiecePlacement;
import io.github.matejkadlec.realmforge.dungeon.instance.RoomPlacement;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.storage.loot.LootTable;
import org.junit.jupiter.api.Test;

/**
 * RLM-282: a run fills its rooms' spot patterns with set pieces, drawn from the layout's seed, and RLM-290: every
 * room carries the loot table of its containers.
 */
class SetPiecesTest {
    private static final Identifier BARREL = Identifier.parse("realmforge:barrel");
    private static final Identifier CRATE = Identifier.parse("realmforge:crate");
    private static final ResourceKey<LootTable> NORMAL = ResourceKey.create(Registries.LOOT_TABLE,
            Identifier.parse("realmforge:containers/normal"));
    private static final ResourceKey<LootTable> TREASURE = ResourceKey.create(Registries.LOOT_TABLE,
            Identifier.parse("realmforge:containers/treasure"));
    private static final List<Vec3i> SIZES =
            List.of(new Vec3i(1, 2, 1), new Vec3i(3, 2, 1), new Vec3i(2, 3, 3), new Vec3i(5, 3, 2));
    private static final String JAIL = "realmforge:rooms/third_party/stonevaults/pillager_dungeon/rooms/jail";

    /** The matcher's promise (rooms/set_pieces.py): a piece's turned and mirrored box starts at its spot. */
    @Test
    void aPiecesTurnedAndMirroredBoxStartsAtItsSpot() {
        BlockPos pos = new BlockPos(4, 1, 7);
        for (Vec3i size : SIZES) {
            for (Direction facing : Direction.Plane.HORIZONTAL) {
                for (boolean spotMirror : List.of(false, true)) {
                    for (boolean pieceMirror : List.of(false, true)) {
                        SetPieces.Piece piece = new SetPieces.Piece(BARREL, size, 1, pieceMirror, List.of());
                        PiecePlacement placed = SetPieces.NONE.place(piece,
                                new SetPieces.Spot(pos, facing, spotMirror), BlockPos.ZERO, Rotation.NONE, List.of());
                        String what = size.toShortString() + " facing " + facing + (spotMirror ? ", mirrored spot" : "")
                                + (pieceMirror ? ", may be mirrored" : "");
                        Set<BlockPos> cells = cells(placed, size);
                        assertEquals(pos, lowest(cells), what + ": the lowest corner of its box");
                        boolean quarter = facing.getAxis() == Direction.Axis.X;
                        assertEquals(new BlockPos(quarter ? size.getZ() : size.getX(), size.getY(),
                                quarter ? size.getX() : size.getZ()), extent(cells), what + ": its box");
                        assertEquals(facing, placed.rotation().rotate(Direction.SOUTH), what + ": where its front faces");
                        assertEquals(spotMirror && pieceMirror ? Mirror.FRONT_BACK : Mirror.NONE, placed.mirror(),
                                what + ": mirrored only where the spot is and the piece allows it");
                    }
                }
            }
        }
    }

    /** A room is turned about its origin corner, and its pieces with it: the same cells, turned and moved. */
    @Test
    void aPieceFollowsItsRoomsTurn() {
        BlockPos offset = new BlockPos(32, 64, -16);
        for (Vec3i size : SIZES) {
            SetPieces.Piece piece = new SetPieces.Piece(BARREL, size, 1, true, List.of());
            for (Direction facing : Direction.Plane.HORIZONTAL) {
                for (boolean mirror : List.of(false, true)) {
                    SetPieces.Spot spot = new SetPieces.Spot(new BlockPos(4, 1, 7), facing, mirror);
                    PiecePlacement unturned = SetPieces.NONE.place(piece, spot, BlockPos.ZERO, Rotation.NONE, List.of());
                    for (Rotation rotation : Rotation.values()) {
                        PiecePlacement turned = SetPieces.NONE.place(piece, spot, offset, rotation, List.of());
                        Set<BlockPos> expected = cells(unturned, size).stream()
                                .map(cell -> StructureTemplate.transform(cell, Mirror.NONE, rotation, BlockPos.ZERO)
                                        .offset(offset))
                                .collect(Collectors.toSet());
                        String what = size.toShortString() + " facing " + facing + " in a room turned " + rotation;
                        assertEquals(expected, cells(turned, size), what + ": its cells");
                        assertEquals(rotation.rotate(facing), turned.rotation().rotate(Direction.SOUTH),
                                what + ": where its front faces");
                        // Each block lands where the room's turn takes it, not only the box.
                        BlockPos block = new BlockPos(size.getX() - 1, 0, 0);
                        assertEquals(StructureTemplate.transform(world(unturned, block), Mirror.NONE, rotation,
                                BlockPos.ZERO).offset(offset), world(turned, block), what + ": one block of it");
                    }
                }
            }
        }
    }

    @Test
    void aRoomFillsWholePatternsWithOnePieceAndOneDrawOfItsVariants() {
        SetPieces.Piece barrel = new SetPieces.Piece(BARREL, new Vec3i(1, 2, 1), 3, true, List.of(
                new SetPieces.Variant(BlockPos.ZERO, 0.25F, Blocks.CHEST.defaultBlockState()),
                new SetPieces.Variant(new BlockPos(0, 1, 0), 0.5F, Blocks.AIR.defaultBlockState())));
        SetPieces.Piece crate = new SetPieces.Piece(CRATE, new Vec3i(1, 1, 1), 1, false, List.of());
        SetPieces pieces = new SetPieces(Map.of(RoomRole.COMBAT, List.of(0, 50, 50)), Optional.of(NORMAL),
                Map.of(BARREL, barrel, CRATE, crate));
        List<SetPieces.Pattern> patterns = List.of(pattern(4, 0, BARREL, CRATE), pattern(2, 1, BARREL, CRATE),
                pattern(1, 2, BARREL));
        DungeonRoom room = room(RoomRole.COMBAT, patterns);
        RandomSource random = RandomSource.create(5);
        Map<Integer, Integer> filled = new TreeMap<>();
        Map<Identifier, Integer> picked = new TreeMap<>();
        int chests = 0;
        int barrels = 0;
        int draws = 20_000;
        for (int i = 0; i < draws; i++) {
            List<PiecePlacement> placed = pieces.draw(room, BlockPos.ZERO, Rotation.NONE, random);
            int count = 0;
            for (SetPieces.Pattern pattern : patterns) {
                List<PiecePlacement> on = placed.stream()
                        .filter(piece -> pattern.spots().stream().anyMatch(spot -> spot.pos().equals(piece.offset())))
                        .toList();
                assertTrue(on.isEmpty() || on.size() == pattern.spots().size(), "a pattern is filled as a whole: " + on);
                if (!on.isEmpty()) {
                    count++;
                    PiecePlacement first = on.getFirst();
                    assertTrue(on.stream().allMatch(piece -> piece.template().equals(first.template())
                            && piece.swaps().equals(first.swaps())), "one piece and one draw per pattern: " + on);
                    assertTrue(pattern.pieces().contains(first.template()), "a piece that fits the pattern");
                    assertEquals(Optional.of(NORMAL), first.containerLoot(), "the pieces' container loot table");
                    if (pattern.pieces().size() == 2) {
                        picked.merge(first.template(), 1, Integer::sum);
                    }
                    if (first.template().equals(BARREL)) {
                        barrels++;
                        chests += first.swaps().stream().anyMatch(swap -> swap.pos().equals(BlockPos.ZERO)) ? 1 : 0;
                    }
                }
            }
            assertEquals(placed.size(), patterns.stream().mapToInt(pattern -> placed.stream().anyMatch(piece -> pattern
                    .spots().stream().anyMatch(spot -> spot.pos().equals(piece.offset()))) ? pattern.spots().size()
                    : 0).sum(), "no piece outside the patterns");
            filled.merge(count, 1, Integer::sum);
        }
        assertEquals(Set.of(1, 2), filled.keySet(), "patterns filled, by the density");
        assertEquals(0.5, filled.get(1) / (double) draws, 0.015, "one pattern half the time");
        double barrelShare = picked.get(BARREL) / (double) (picked.get(BARREL) + picked.get(CRATE));
        assertEquals(0.75, barrelShare, 0.015, "the piece of weight 3 against the one of weight 1");
        assertEquals(0.25, chests / (double) barrels, 0.015, "a variant comes at its chance");
    }

    @Test
    void aRoleWithoutDensityAndARoomWithoutPatternsGetNothing() {
        SetPieces.Piece barrel = new SetPieces.Piece(BARREL, new Vec3i(1, 2, 1), 1, true, List.of());
        SetPieces pieces = new SetPieces(Map.of(RoomRole.COMBAT, List.of(0, 0, 100), RoomRole.HALLWAY, List.of(100)),
                Optional.empty(), Map.of(BARREL, barrel));
        RandomSource random = RandomSource.create(2);
        List<SetPieces.Pattern> one = List.of(pattern(2, 0, BARREL));
        assertEquals(List.of(), pieces.draw(room(RoomRole.HALLWAY, one), BlockPos.ZERO, Rotation.NONE, random),
                "a hallway whose density is none");
        assertEquals(List.of(), pieces.draw(room(RoomRole.TREASURE, one), BlockPos.ZERO, Rotation.NONE, random),
                "a role the type gives no density");
        assertEquals(List.of(), pieces.draw(room(RoomRole.COMBAT, List.of()), BlockPos.ZERO, Rotation.NONE, random),
                "a room without spots");
        assertEquals(2, pieces.draw(room(RoomRole.COMBAT, one), BlockPos.ZERO, Rotation.NONE, random).size(),
                "two patterns asked of a room with one: the one it has");
        assertEquals(List.of(), pieces.draw(room(RoomRole.COMBAT, List.of(pattern(2, 0, CRATE))), BlockPos.ZERO,
                Rotation.NONE, random), "a pattern whose pieces the type does not place");
        assertEquals(List.of(), SetPieces.NONE.draw(room(RoomRole.COMBAT, one), BlockPos.ZERO, Rotation.NONE, random));
    }

    @Test
    void theEntryRefusesADensityWithoutAChanceAndAVariantOutsideItsPiece() {
        assertTrue(SetPieces.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(
                "{\"density\": {\"combat\": [0, 0]}, \"pieces\": {}}")).isError(), "a density without a chance");
        assertTrue(SetPieces.Piece.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(
                "{\"template\": \"realmforge:set_pieces/x\", \"size\": [1, 2, 1], \"variants\": [{\"pos\": [0, 2, 0], "
                        + "\"chance\": 0.5, \"state\": {\"Name\": \"minecraft:air\"}}]}")).isError(),
                "a variant above the piece");
        assertTrue(SetPieces.Spot.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(
                "{\"pos\": [0, 1, 0], \"facing\": \"up\"}")).isError(), "a spot facing up");
    }

    /** The generated test dungeon: its set pieces, its rooms' container loot tables, and the jail without spots. */
    @Test
    void theTestDungeonReadsItsSetPiecesAndItsRoomsContainerLoot() throws IOException {
        DungeonType type = DungeonPlannerTest.generated("test_dungeon");
        SetPieces pieces = type.setPieces();
        SetPieces.Piece barrel = pieces.pieces().get(Identifier.parse("realmforge:test_barrel"));
        assertTrue(barrel != null, "the test dungeon places the test piece: " + pieces.pieces().keySet());
        assertEquals(new Vec3i(1, 2, 1), barrel.size());
        assertEquals(2, barrel.variants().size(), "a barrel that may be a chest, a skull that may be missing");
        assertEquals(Optional.of(NORMAL), pieces.containerLoot());
        assertEquals(List.of(100), pieces.density().get(RoomRole.HALLWAY), "no piece in a corridor (RLM-410)");
        for (DungeonRoom room : type.rooms()) {
            if (room.role() == RoomRole.WALL) {
                assertEquals(Optional.empty(), room.containerLoot(), "a wall holds nothing");
                continue;
            }
            assertEquals(Optional.of(room.role() == RoomRole.TREASURE ? TREASURE : NORMAL), room.containerLoot(),
                    room.template() + " container loot table");
            if (room.template().toString().equals(JAIL)) {
                assertTrue(room.setPieceSpots().isEmpty(), "the jail takes no set piece (Matěj, 2026-10-25)");
            }
        }
        assertTrue(type.rooms().stream().anyMatch(room -> !room.setPieceSpots().isEmpty()), "no room has spots");
    }

    /**
     * Over the test dungeon's layouts: the same seed gives the same pieces, seeds differ, every piece stands inside
     * its room, and a layout survives its codec, which a plot saves it with.
     */
    @Test
    void theTestDungeonsLayoutsCarryTheirPieces() throws IOException {
        DungeonType type = DungeonPlannerTest.generated("test_dungeon");
        Set<List<PiecePlacement>> runs = new HashSet<>();
        int pieces = 0;
        int seeds = 150;
        for (int seed = 0; seed < seeds; seed++) {
            DungeonPlan plan = type.plan(seed);
            InstanceLayout layout = plan.toInstanceLayout();
            assertEquals(layout, type.plan(seed).toInstanceLayout(), "seed " + seed + " planned twice");
            List<PiecePlacement> run = new ArrayList<>();
            for (int i = 0; i < plan.rooms().size(); i++) {
                RoomPlacement room = layout.rooms().get(i);
                assertEquals(plan.rooms().get(i).room().containerLoot(), room.containerLoot());
                for (PiecePlacement piece : room.pieces()) {
                    SetPieces.Piece placed = type.setPieces().pieces().values().stream()
                            .filter(candidate -> candidate.template().equals(piece.template())).findFirst().orElseThrow();
                    for (BlockPos cell : cells(piece, placed.size())) {
                        assertTrue(plan.rooms().get(i).box().isInside(cell), piece + " reaches outside its room "
                                + room.template() + ", seed " + seed);
                    }
                }
                run.addAll(room.pieces());
            }
            pieces += run.size();
            runs.add(run);
            if (seed < 10) {
                assertEquals(layout, InstanceLayout.CODEC.parse(JsonOps.INSTANCE, InstanceLayout.CODEC
                        .encodeStart(JsonOps.INSTANCE, layout).getOrThrow()).getOrThrow(), "seed " + seed + " saved");
            }
        }
        assertTrue(pieces > 0, "no layout holds a set piece");
        assertTrue(runs.size() > seeds / 2, "only " + runs.size() + " different dressings in " + seeds + " seeds");
        assertFalse(runs.contains(List.of()) && runs.size() == 1, "every layout is bare");
    }

    private static SetPieces.Pattern pattern(int spots, int row, Identifier... pieces) {
        List<SetPieces.Spot> found = new ArrayList<>();
        for (int i = 0; i < spots; i++) {
            found.add(new SetPieces.Spot(new BlockPos(1 + 2 * i, 1, 1 + 3 * row), Direction.SOUTH, i % 2 == 1));
        }
        return new SetPieces.Pattern(List.of(pieces), found);
    }

    private static DungeonRoom room(RoomRole role, List<SetPieces.Pattern> patterns) {
        return new DungeonRoom(Identifier.parse("realmforge:rooms/test"), new Vec3i(16, 6, 16), role, 1, List.of(),
                Optional.empty(), false, List.of(), Optional.empty(), 0, 0, false, RoomContainers.NONE,
                RoomOpenings.NONE, Optional.empty(), patterns);
    }

    /** Where vanilla puts a block of the piece's template. */
    private static BlockPos world(PiecePlacement piece, BlockPos local) {
        return piece.offset().offset(StructureTemplate.transform(local, piece.mirror(), piece.rotation(), BlockPos.ZERO));
    }

    private static Set<BlockPos> cells(PiecePlacement piece, Vec3i size) {
        Set<BlockPos> cells = new HashSet<>();
        for (BlockPos local : BlockPos.betweenClosed(0, 0, 0, size.getX() - 1, size.getY() - 1, size.getZ() - 1)) {
            cells.add(world(piece, local));
        }
        return cells;
    }

    private static BlockPos lowest(Set<BlockPos> cells) {
        return new BlockPos(cells.stream().mapToInt(BlockPos::getX).min().orElseThrow(),
                cells.stream().mapToInt(BlockPos::getY).min().orElseThrow(),
                cells.stream().mapToInt(BlockPos::getZ).min().orElseThrow());
    }

    private static BlockPos extent(Set<BlockPos> cells) {
        BlockPos lowest = lowest(cells);
        return new BlockPos(cells.stream().mapToInt(BlockPos::getX).max().orElseThrow() - lowest.getX() + 1,
                cells.stream().mapToInt(BlockPos::getY).max().orElseThrow() - lowest.getY() + 1,
                cells.stream().mapToInt(BlockPos::getZ).max().orElseThrow() - lowest.getZ() + 1);
    }
}
