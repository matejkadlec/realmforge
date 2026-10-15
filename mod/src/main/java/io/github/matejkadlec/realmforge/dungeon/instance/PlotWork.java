package io.github.matejkadlec.realmforge.dungeon.instance;

import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * Splits the work on a plot into small units that the builder runs one at a time, a few per tick within a time
 * budget, so neither placing nor clearing a large room stalls the server. A unit covers one room (or one box to
 * clear) within one chunk column. The list is a pure function of the plot's saved state, so after a restart the
 * same list is rebuilt and work resumes at the saved step.
 */
public final class PlotWork {
    /**
     * How far cleanup reaches past a room's box, in blocks: far enough for what spreads past a wall while players are
     * inside (fire, a vine, a creative operator's block beside a wall). Rooms keep fluids off their edges (room
     * validation), so nothing flows further.
     */
    public static final int MARGIN = 4;

    private PlotWork() {
    }

    /** One step of work on a plot. */
    public sealed interface Unit permits DiscardEntities, Clear, Place {
    }

    /**
     * Remove every entity except players inside {@code area} (one box the plot holds), before blocks are cleared.
     */
    public record DiscardEntities(BoundingBox area) implements Unit {
    }

    /** Set every block in {@code slice} to air. */
    public record Clear(BoundingBox slice) implements Unit {
    }

    /** Place the part of room {@code room} that lies inside {@code slice}. */
    public record Place(int room, BoundingBox slice) implements Unit {
    }

    /**
     * The units for a plot: first clear what is left from its last run ({@code dirty}), then place {@code rooms}
     * in order. Closing a plot is the same with no rooms.
     */
    public static List<Unit> units(List<BoundingBox> dirty, List<BoundingBox> rooms) {
        List<Unit> units = new ArrayList<>();
        if (!dirty.isEmpty()) {
            // One unit per box, not the box around them all: that could reach chunks the plot does not hold, and a
            // unit waits until every chunk it touches is loaded.
            for (BoundingBox box : dirty) {
                units.add(new DiscardEntities(box));
            }
            for (BoundingBox box : dirty) {
                for (BoundingBox slice : chunkSlices(box)) {
                    units.add(new Clear(slice));
                }
            }
        }
        for (int room = 0; room < rooms.size(); room++) {
            for (BoundingBox slice : chunkSlices(rooms.get(room))) {
                units.add(new Place(room, slice));
            }
        }
        return units;
    }

    /** The boxes grown by {@link #MARGIN} on every side: what cleanup clears after rooms stood in them. */
    public static List<BoundingBox> withMargin(List<BoundingBox> rooms) {
        return rooms.stream().map(box -> box.inflatedBy(MARGIN)).toList();
    }

    /** The box cut along chunk borders into one piece per chunk column it touches, in x-then-z order. */
    public static List<BoundingBox> chunkSlices(BoundingBox box) {
        List<BoundingBox> slices = new ArrayList<>();
        for (int chunkZ = box.minZ() >> 4; chunkZ <= box.maxZ() >> 4; chunkZ++) {
            for (int chunkX = box.minX() >> 4; chunkX <= box.maxX() >> 4; chunkX++) {
                int minX = chunkX << 4;
                int minZ = chunkZ << 4;
                slices.add(new BoundingBox(
                        Math.max(box.minX(), minX), box.minY(), Math.max(box.minZ(), minZ),
                        Math.min(box.maxX(), minX + 15), box.maxY(), Math.min(box.maxZ(), minZ + 15)));
            }
        }
        return slices;
    }

    /** Every chunk any of the boxes touches, as {@link ChunkPos#pack()} values in first-seen order. */
    public static LongSet chunks(List<BoundingBox> boxes) {
        LongSet chunks = new LongLinkedOpenHashSet();
        for (BoundingBox box : boxes) {
            for (int chunkZ = box.minZ() >> 4; chunkZ <= box.maxZ() >> 4; chunkZ++) {
                for (int chunkX = box.minX() >> 4; chunkX <= box.maxX() >> 4; chunkX++) {
                    chunks.add(ChunkPos.pack(chunkX, chunkZ));
                }
            }
        }
        return chunks;
    }
}
