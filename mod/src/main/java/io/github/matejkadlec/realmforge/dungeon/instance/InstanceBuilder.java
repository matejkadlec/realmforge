package io.github.matejkadlec.realmforge.dungeon.instance;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Clearable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;

/** Runs single {@link PlotWork.Unit}s against the instance level: placing room slices and clearing old blocks. */
final class InstanceBuilder {
    /** Structure blocks and world generation place templates with the same flag. */
    private static final int PLACE_FLAGS = Block.UPDATE_CLIENTS;
    /** Clearing removes everything nearby anyway, so neighbours get no shape updates (no popping torches). */
    private static final int CLEAR_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    private InstanceBuilder() {
    }

    /** Runs one unit of work on the plot and returns how many blocks it cleared. */
    static long run(ServerLevel level, Plot plot, PlotWork.Unit unit) {
        return switch (unit) {
            case PlotWork.DiscardEntities discard -> {
                discardEntities(level, discard.area());
                yield 0;
            }
            case PlotWork.Clear clear -> clear(level, clear.slice(), plot.keepWhileClearing());
            case PlotWork.Place place -> {
                place(level, plot, place);
                yield 0;
            }
        };
    }

    /** The settings a room is placed with; its box comes from the same settings. */
    static StructurePlaceSettings settings(RoomPlacement room) {
        // Rooms are built by hand with correct block shapes, so placing skips the neighbour shape pass.
        return new StructurePlaceSettings().setRotation(room.rotation()).setIgnoreEntities(true).setKnownShape(true);
    }

    static StructureTemplate template(ServerLevel level, RoomPlacement room) {
        return level.getStructureManager().get(room.template())
                .orElseThrow(() -> new IllegalArgumentException("unknown room template " + room.template()));
    }

    private static void place(ServerLevel level, Plot plot, PlotWork.Place unit) {
        RoomPlacement room = plot.layout().orElseThrow().rooms().get(unit.room());
        BlockPos pos = plot.origin().offset(room.offset());
        StructurePlaceSettings settings = settings(room).setBoundingBox(unit.slice());
        RandomSource random = RandomSource.create((long) plot.id() << 16 ^ unit.room());
        template(level, room).placeInWorld(level, pos, pos, settings, random, PLACE_FLAGS);
    }

    private static long clear(ServerLevel level, BoundingBox slice, List<BoundingBox> keep) {
        long cleared = 0;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        BlockState air = Blocks.AIR.defaultBlockState();
        for (int y = slice.maxY(); y >= slice.minY(); y--) {
            for (int z = slice.minZ(); z <= slice.maxZ(); z++) {
                for (int x = slice.minX(); x <= slice.maxX(); x++) {
                    if (PlotWork.anyContains(keep, x, y, z)) {
                        continue;
                    }
                    pos.set(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    if (state.isAir()) {
                        continue;
                    }
                    if (state.hasBlockEntity()) {
                        // Empty containers first, or removing them would drop their items, as /fill does.
                        Clearable.tryClear(level.getBlockEntity(pos));
                    }
                    level.setBlock(pos, air, CLEAR_FLAGS);
                    cleared++;
                }
            }
        }
        return cleared;
    }

    private static void discardEntities(ServerLevel level, BoundingBox area) {
        for (Entity entity : level.getEntities((Entity) null, AABB.of(area), entity -> !(entity instanceof Player))) {
            entity.discard();
        }
    }
}
