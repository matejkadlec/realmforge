package io.github.matejkadlec.realmforge.qa.gametest;

import io.github.matejkadlec.realmforge.dungeon.DungeonRegistries;
import io.github.matejkadlec.realmforge.dungeon.DungeonType;
import io.github.matejkadlec.realmforge.dungeon.instance.InstanceDimension;
import io.github.matejkadlec.realmforge.dungeon.instance.InstanceLayout;
import io.github.matejkadlec.realmforge.dungeon.instance.InstanceManager;
import io.github.matejkadlec.realmforge.dungeon.instance.PiecePlacement;
import io.github.matejkadlec.realmforge.dungeon.instance.Plot;
import io.github.matejkadlec.realmforge.dungeon.instance.PlotState;
import io.github.matejkadlec.realmforge.dungeon.instance.RoomPlacement;
import io.github.matejkadlec.realmforge.dungeon.plan.Connector;
import io.github.matejkadlec.realmforge.dungeon.plan.DungeonPlan;
import io.github.matejkadlec.realmforge.dungeon.plan.DungeonRoom;
import io.github.matejkadlec.realmforge.dungeon.plan.PlannedRoom;
import io.github.matejkadlec.realmforge.dungeon.plan.SetPieces;
import io.github.matejkadlec.realmforge.loot.LootContainers;
import io.github.matejkadlec.realmforge.qa.RealmforgeQa;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * RLM-282 (ADR 0050, ADR 0077, architecture 15.4): a run places set pieces in the spots {@code rf gen} found in its
 * rooms. With the test piece (a barrel against a wall, a chest instead a quarter of the time, a skull on it half the
 * time) in the test dungeon: every spot pattern is filled as a whole or left empty, the same seed gives the same
 * pieces and another seed others, a built piece faces away from its wall, its container is a loot container with the
 * normal table, and no doorway of a room loses a doorway it reached.
 */
public final class DungeonSetPieceGameTests {
    private static final int TIMEOUT = 1200;
    private static final Identifier TEST_DUNGEON = Identifier.parse("realmforge:test_dungeon");
    /** Matěj's decision of 2026-10-25: this room holds its one chest and one barrel, and nothing else generates there. */
    private static final Identifier JAIL =
            Identifier.parse("realmforge:rooms/third_party/stonevaults/pillager_dungeon/rooms/jail");
    private static final int SEEDS = 200;
    /** Where the test piece's container and its skull are in its template. */
    private static final BlockPos CONTAINER = BlockPos.ZERO;
    private static final BlockPos SKULL = new BlockPos(0, 1, 0);

    private DungeonSetPieceGameTests() {
    }

    /**
     * Over many seeds, as planned: a pattern is filled on every spot with one piece and one draw of its variants or on
     * none, nothing stands outside the patterns, a room fills as many patterns as its role's density allows and about
     * as often, the jail gets none, and a seed always gives the same pieces.
     */
    @QaGameTest
    public static void aSeedDrawsItsPiecesPatternByPattern(GameTestHelper helper) {
        DungeonType type = testDungeon(helper);
        SetPieces pieces = type.setPieces();
        helper.assertFalse(pieces.pieces().isEmpty(), "the test dungeon places no set piece");
        helper.assertTrue(type.rooms().stream().filter(room -> room.template().equals(JAIL))
                .allMatch(room -> room.setPieceSpots().isEmpty()), "the jail has set-piece spots");
        Set<List<PiecePlacement>> runs = new HashSet<>();
        int dressable = 0;
        int dressed = 0;
        double expected = 0;
        long placedPieces = 0;
        for (long seed = 0; seed < SEEDS; seed++) {
            DungeonPlan plan = type.plan(seed);
            InstanceLayout layout = plan.toInstanceLayout();
            helper.assertValueEqual(type.plan(seed).toInstanceLayout(), layout, "seed " + seed + " planned twice");
            List<PiecePlacement> run = new ArrayList<>();
            for (int i = 0; i < plan.rooms().size(); i++) {
                PlannedRoom planned = plan.rooms().get(i);
                DungeonRoom room = planned.room();
                String where = room.template() + ", seed " + seed;
                List<PiecePlacement> placed = layout.rooms().get(i).pieces();
                run.addAll(placed);
                List<Integer> table = pieces.density().getOrDefault(room.role(), List.of());
                int most = Math.min(table.size() - 1, room.setPieceSpots().size());
                if (most <= 0) {
                    helper.assertTrue(placed.isEmpty(), where + " holds pieces without a pattern its role fills");
                    continue;
                }
                Set<PiecePlacement> left = new HashSet<>(placed);
                int filled = 0;
                for (SetPieces.Pattern pattern : room.setPieceSpots()) {
                    Optional<PiecePlacement> first = at(pieces, pattern, pattern.spots().getFirst(), planned, placed);
                    for (SetPieces.Spot spot : pattern.spots()) {
                        Optional<PiecePlacement> piece = at(pieces, pattern, spot, planned, placed);
                        helper.assertValueEqual(piece.isPresent(), first.isPresent(),
                                where + ": a pattern is filled on " + spot.pos().toShortString());
                        if (piece.isPresent()) {
                            helper.assertValueEqual(piece.get().template(), first.get().template(),
                                    where + ": the piece on " + spot.pos().toShortString());
                            helper.assertValueEqual(piece.get().swaps(), first.get().swaps(),
                                    where + ": the variants on " + spot.pos().toShortString());
                            helper.assertValueEqual(piece.get().containerLoot(), pieces.containerLoot(),
                                    where + ": the piece's container loot table");
                            left.remove(piece.get());
                        }
                    }
                    filled += first.isPresent() ? 1 : 0;
                }
                helper.assertTrue(left.isEmpty(), where + " holds pieces outside its patterns: " + left);
                helper.assertTrue(filled <= most, where + " fills " + filled + " patterns, more than " + most);
                dressable++;
                dressed += filled > 0 ? 1 : 0;
                expected += 1 - table.getFirst() / (double) table.stream().mapToInt(Integer::intValue).sum();
                placedPieces += placed.size();
            }
            runs.add(run);
        }
        helper.assertTrue(dressable > 0, "no room of the test dungeon takes a set piece");
        helper.assertTrue(runs.size() > SEEDS / 2, "only " + runs.size() + " different dressings in " + SEEDS + " seeds");
        double share = dressed / (double) dressable;
        double wanted = expected / dressable;
        double tolerance = 4 * Math.sqrt(wanted * (1 - wanted) / dressable) + 0.02;
        RealmforgeQa.LOGGER.info("Test dungeon over {} seeds: {} set pieces a run, {} of the rooms with spots dressed",
                SEEDS, placedPieces / (double) SEEDS, String.format(Locale.ROOT, "%.3f", share));
        helper.assertTrue(Math.abs(share - wanted) <= tolerance, String.format(Locale.ROOT,
                "%.3f of the rooms with spots got a pattern, not %.3f as their density says", share, wanted));
        helper.succeed();
    }

    /**
     * A built layout: each piece stands where its plan put it, as the barrel or the chest its variants drew, a loot
     * container with the normal table, with the skull or without, its back to a wall and its front free, a chest
     * facing the room; and every doorway of a dressed room still reaches the doorways it reached without the pieces.
     */
    @QaGameTest(timeoutTicks = TIMEOUT)
    public static void aBuiltDungeonHoldsItsSetPieces(GameTestHelper helper) {
        DungeonType type = testDungeon(helper);
        DungeonPlan plan = dressedPlan(helper, type);
        InstanceLayout layout = plan.toInstanceLayout();
        InstanceManager manager = InstanceManager.get(helper.getLevel().getServer());
        helper.assertTrue(manager != null, "the GameTest world has no realmforge:instances dimension");
        ServerLevel instances = InstanceDimension.level(helper.getLevel().getServer());
        Plot plot = manager.open(layout, List.of());

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(plot.state() == PlotState.READY, "plot is " + plot.state()))
                .thenExecute(() -> {
                    for (int i = 0; i < plan.rooms().size(); i++) {
                        RoomPlacement room = layout.rooms().get(i);
                        if (room.pieces().isEmpty()) {
                            continue;
                        }
                        Set<BlockPos> pieceCells = new HashSet<>();
                        for (PiecePlacement piece : room.pieces()) {
                            checkPiece(helper, instances, plot, room, piece, type.setPieces());
                            pieceCells.add(world(plot, piece, CONTAINER));
                            pieceCells.add(world(plot, piece, SKULL));
                        }
                        checkDoorways(helper, instances, plot, plan.rooms().get(i), plot.rooms().get(i), pieceCells);
                    }
                    manager.release(plot);
                })
                .thenWaitUntil(() -> helper.assertTrue(plot.state() == PlotState.FREE, "plot is " + plot.state()))
                .thenSucceed();
    }

    /**
     * A layout with a barrel, a chest, a skull, a piece without one and a mirrored piece among its set pieces, so the
     * build shows every variant: the first seed that has them all.
     */
    private static DungeonPlan dressedPlan(GameTestHelper helper, DungeonType type) {
        for (long seed = 0; seed < 500; seed++) {
            DungeonPlan plan = type.plan(seed);
            List<PiecePlacement> pieces = plan.toInstanceLayout().rooms().stream()
                    .flatMap(room -> room.pieces().stream()).toList();
            boolean chest = pieces.stream().anyMatch(piece -> swapped(piece, CONTAINER).isPresent());
            boolean barrel = pieces.stream().anyMatch(piece -> swapped(piece, CONTAINER).isEmpty());
            boolean bare = pieces.stream().anyMatch(piece -> swapped(piece, SKULL).isPresent());
            boolean skull = pieces.stream().anyMatch(piece -> swapped(piece, SKULL).isEmpty());
            boolean mirrored = pieces.stream().anyMatch(piece -> piece.mirror() != Mirror.NONE);
            if (chest && barrel && bare && skull && mirrored) {
                return plan;
            }
        }
        throw helper.assertionException("no seed below 500 dresses the test dungeon with every variant of the test piece");
    }

    private static void checkPiece(GameTestHelper helper, ServerLevel level, Plot plot, RoomPlacement room,
            PiecePlacement piece, SetPieces pieces) {
        String where = piece.template() + " in " + room.template() + " at " + piece.offset().toShortString();
        BlockPos at = world(plot, piece, CONTAINER);
        BlockState container = level.getBlockState(at);
        BlockState wanted = LootContainers.perPlayer(swapped(piece, CONTAINER).orElse(Blocks.BARREL.defaultBlockState()));
        helper.assertTrue(container.is(wanted.getBlock()), where + " stands as " + container + ", not as "
                + wanted.getBlock());
        helper.assertTrue(LootContainers.isPerPlayer(container), where + ": " + container + " is no loot container");
        helper.assertValueEqual(Optional.ofNullable(DungeonLootContainerGameTests.lootTable(level, at)),
                pieces.containerLoot(), where + ": its loot table");
        BlockState above = level.getBlockState(world(plot, piece, SKULL));
        helper.assertTrue(above.is(swapped(piece, SKULL).isPresent() ? Blocks.AIR : Blocks.SKELETON_SKULL),
                where + " has " + above + " on it");
        // A piece is built with its wall to the north and its front to the south, and placed turned.
        Direction front = piece.rotation().rotate(Direction.SOUTH);
        BlockPos behind = at.relative(front.getOpposite());
        helper.assertTrue(level.getBlockState(behind).isCollisionShapeFullBlock(level, behind),
                where + " has no wall behind it: " + level.getBlockState(behind));
        BlockPos ahead = at.relative(front);
        helper.assertFalse(level.getBlockState(ahead).isCollisionShapeFullBlock(level, ahead),
                where + " faces " + level.getBlockState(ahead));
        if (container.hasProperty(ChestBlock.FACING)) {
            helper.assertValueEqual(container.getValue(ChestBlock.FACING), front, where + ": the chest's facing");
        }
    }

    /**
     * Every pair of the room's doorways that a player walks between with the pieces' cells empty is still walked
     * between with the pieces in place.
     */
    private static void checkDoorways(GameTestHelper helper, ServerLevel level, Plot plot, PlannedRoom planned,
            BoundingBox box, Set<BlockPos> pieceCells) {
        List<BlockPos> doorways = new ArrayList<>();
        for (int index = 0; index < planned.room().connectors().size(); index++) {
            Connector connector = planned.connector(index);
            if (!connector.front().getAxis().isHorizontal()) {
                continue;
            }
            BlockPos foot = plot.origin().offset(connector.pos());
            // The connector block lies in the doorway's lowest row or in the floor under it.
            for (BlockPos cell : List.of(foot, foot.above())) {
                if (stands(level, cell, Set.of())) {
                    doorways.add(cell);
                    break;
                }
            }
        }
        for (BlockPos from : doorways) {
            Set<BlockPos> bare = reach(level, box, from, pieceCells);
            Set<BlockPos> built = reach(level, box, from, Set.of());
            for (BlockPos to : doorways) {
                if (bare.contains(to)) {
                    helper.assertTrue(built.contains(to), planned.room().template() + ": its set pieces cut the "
                            + "doorway at " + to.toShortString() + " off from the one at " + from.toShortString());
                }
            }
        }
    }

    /** The cells of {@code box} a player walks to from {@code from}, a block up or down per step. */
    private static Set<BlockPos> reach(ServerLevel level, BoundingBox box, BlockPos from, Set<BlockPos> empty) {
        Set<BlockPos> seen = new HashSet<>();
        Deque<BlockPos> open = new ArrayDeque<>();
        seen.add(from);
        open.add(from);
        while (!open.isEmpty()) {
            BlockPos cell = open.poll();
            for (Direction side : Direction.Plane.HORIZONTAL) {
                for (int dy = -1; dy <= 1; dy++) {
                    BlockPos next = cell.relative(side).above(dy);
                    if (box.isInside(next) && !seen.contains(next) && stands(level, next, empty)) {
                        seen.add(next);
                        open.add(next);
                    }
                }
            }
        }
        return seen;
    }

    /** Whether a player stands in {@code cell}: something to stand on below, and two free blocks; {@code empty} is air. */
    private static boolean stands(ServerLevel level, BlockPos cell, Set<BlockPos> empty) {
        return !free(level, cell.below(), empty) && free(level, cell, empty) && free(level, cell.above(), empty);
    }

    private static boolean free(ServerLevel level, BlockPos pos, Set<BlockPos> empty) {
        return empty.contains(pos) || level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
    }

    /** The state the run's variants gave the piece's block at {@code pos} of its template, if they changed it. */
    private static Optional<BlockState> swapped(PiecePlacement piece, BlockPos pos) {
        return piece.swaps().stream().filter(swap -> swap.pos().equals(pos)).map(RoomPlacement.Swap::state).findFirst();
    }

    /** The piece of {@code placed} that stands on {@code spot}, whichever piece of the pattern it is. */
    private static Optional<PiecePlacement> at(SetPieces pieces, SetPieces.Pattern pattern, SetPieces.Spot spot,
            PlannedRoom room, List<PiecePlacement> placed) {
        for (Identifier id : pattern.pieces()) {
            SetPieces.Piece piece = pieces.pieces().get(id);
            if (piece == null) {
                continue;
            }
            PiecePlacement wanted = pieces.place(piece, spot, room.offset(), room.rotation(), List.of());
            for (PiecePlacement candidate : placed) {
                if (candidate.template().equals(wanted.template()) && candidate.offset().equals(wanted.offset())
                        && candidate.rotation() == wanted.rotation() && candidate.mirror() == wanted.mirror()) {
                    return Optional.of(candidate);
                }
            }
        }
        return Optional.empty();
    }

    /** A position of the piece's template where the builder put it in the world. */
    private static BlockPos world(Plot plot, PiecePlacement piece, BlockPos local) {
        return plot.origin().offset(piece.offset())
                .offset(StructureTemplate.transform(local, piece.mirror(), piece.rotation(), BlockPos.ZERO));
    }

    private static DungeonType testDungeon(GameTestHelper helper) {
        DungeonType type = helper.getLevel().registryAccess().lookupOrThrow(DungeonRegistries.DUNGEON_TYPE)
                .getValue(TEST_DUNGEON);
        helper.assertTrue(type != null, TEST_DUNGEON + " is not loaded");
        return type;
    }
}
