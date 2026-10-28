package io.github.matejkadlec.realmforge.dungeon.instance;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.StructureMode;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.JigsawReplacementProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;
import org.slf4j.Logger;

/**
 * Runs single {@link PlotWork.Unit}s against the instance level: placing room slices and clearing old blocks. It also
 * reads a room's data markers, which it never places.
 */
final class InstanceBuilder {
    private static final Logger LOGGER = LogUtils.getLogger();
    /** Structure blocks and world generation place templates with the same flag. */
    private static final int PLACE_FLAGS = Block.UPDATE_CLIENTS;
    /**
     * Clearing removes everything nearby anyway, so neighbours get no shape updates (no popping torches), and block
     * entities go without their removal side effects: a container vanishes with its items instead of dropping them.
     */
    private static final int CLEAR_FLAGS =
            Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SKIP_BLOCK_ENTITY_SIDEEFFECTS;

    private InstanceBuilder() {
    }

    /** Runs one unit of work on the plot and returns how many blocks it cleared. */
    static long run(ServerLevel level, Plot plot, PlotWork.Unit unit) {
        return switch (unit) {
            case PlotWork.DiscardEntities discard -> {
                discardEntities(level, discard.area());
                yield 0;
            }
            case PlotWork.Clear clear -> clear(level, clear.slice());
            case PlotWork.Place place -> {
                place(level, plot, place);
                yield 0;
            }
        };
    }

    /**
     * Whether every chunk the unit touches is loaded, and, for placing or removing entities, their entities too. Work
     * waits for chunks that are still loading, after a restart for example, instead of loading them on the main
     * thread. A chunk's blocks load before the entity manager makes its entity sections accessible, so a room placed
     * in between would add its item frames hidden, out of every entity query, until the chunk's status catches up
     * (RLM-483); placing waits for the entities as removing does. A plot holds a chunk only once both have loaded
     * ({@code InstanceManager#holdChunks}), so this waits only for chunks that a restart is loading again.
     */
    static boolean ready(ServerLevel level, PlotWork.Unit unit) {
        BoundingBox box = switch (unit) {
            case PlotWork.DiscardEntities discard -> discard.area();
            case PlotWork.Clear clear -> clear.slice();
            case PlotWork.Place place -> place.slice();
        };
        for (long chunk : PlotWork.chunks(List.of(box))) {
            if (level.getChunkSource().getChunkNow(ChunkPos.getX(chunk), ChunkPos.getZ(chunk)) == null) {
                return false;
            }
            if (!(unit instanceof PlotWork.Clear) && !level.areEntitiesLoaded(chunk)) {
                return false;
            }
        }
        return true;
    }

    /**
     * The settings a room is placed with; its box and its markers come from the same settings. Its jigsaw blocks
     * (connectors) become their final state, as in vanilla's jigsaw structures, and its data markers are left out, as
     * vanilla's pool elements leave them out. Its entities (the paintings and item frames an import keeps, such as the
     * framed flowers a combination lock's comparators read) come with it: each slice places those whose block lies
     * in it, so each once (RLM-362), with the block it hangs at moved along ({@link AttachedEntityProcessor}). The
     * plot's chunks do not tick entities while it is built, so a frame placed before the wall it hangs on stays. The
     * containers its dungeon's chance tables dropped become the blocks they leave behind (RLM-365).
     */
    static StructurePlaceSettings settings(RoomPlacement room) {
        // Rooms are built with correct block shapes, so placing skips the neighbour shape pass.
        StructurePlaceSettings settings = new StructurePlaceSettings().setRotation(room.rotation())
                .setIgnoreEntities(false).setKnownShape(true).addProcessor(BlockIgnoreProcessor.STRUCTURE_BLOCK)
                .addProcessor(JigsawReplacementProcessor.INSTANCE).addProcessor(AttachedEntityProcessor.INSTANCE);
        if (!room.swaps().isEmpty()) {
            settings.addProcessor(new BlockSwapProcessor(room.swaps()));
        }
        return settings;
    }

    /**
     * The data markers of room {@code index} as it is placed, positions relative to the plot's origin: its structure
     * blocks in data mode, read the way vanilla's {@code SinglePoolElement#getDataMarkers} reads them. A marker whose
     * name is unknown is logged and left out; content validation refuses those.
     */
    static List<DataMarker> markers(StructureTemplate template, int index, RoomPlacement room) {
        List<DataMarker> markers = new ArrayList<>();
        for (StructureTemplate.StructureBlockInfo info
                : template.filterBlocks(room.offset(), settings(room), Blocks.STRUCTURE_BLOCK)) {
            CompoundTag data = info.nbt();
            if (data == null || data.read("mode", StructureMode.LEGACY_CODEC).orElse(null) != StructureMode.DATA) {
                continue;
            }
            String name = data.getStringOr("metadata", "");
            Optional<DataMarker.Kind> kind = DataMarker.Kind.byName(name);
            if (kind.isPresent()) {
                markers.add(new DataMarker(kind.get(), index, info.pos()));
            } else {
                LOGGER.warn("Room {} has a data marker named '{}' at {}, which is no marker; ignoring it",
                        room.template(), name, info.pos().toShortString());
            }
        }
        return markers;
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

    private static long clear(ServerLevel level, BoundingBox slice) {
        long cleared = 0;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        BlockState air = Blocks.AIR.defaultBlockState();
        for (int y = slice.maxY(); y >= slice.minY(); y--) {
            for (int z = slice.minZ(); z <= slice.maxZ(); z++) {
                for (int x = slice.minX(); x <= slice.maxX(); x++) {
                    pos.set(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    if (state.isAir()) {
                        continue;
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
