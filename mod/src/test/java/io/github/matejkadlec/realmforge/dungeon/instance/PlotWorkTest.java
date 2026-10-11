package io.github.matejkadlec.realmforge.dungeon.instance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.junit.jupiter.api.Test;

class PlotWorkTest {
    @Test
    void slicesCoverTheBoxExactlyOncePerChunkColumn() {
        BoundingBox box = new BoundingBox(-20, 64, 10, 17, 70, 40);
        List<BoundingBox> slices = PlotWork.chunkSlices(box);
        assertEquals(4 * 3, slices.size(), "x -20..17 spans chunks -2..1, z 10..40 spans chunks 0..2");
        Set<BlockPos> covered = new HashSet<>();
        for (BoundingBox slice : slices) {
            assertEquals(slice.minX() >> 4, slice.maxX() >> 4, "a slice stays in one chunk column");
            assertEquals(slice.minZ() >> 4, slice.maxZ() >> 4, "a slice stays in one chunk column");
            BlockPos.betweenClosedStream(slice).forEach(pos -> assertTrue(covered.add(pos.immutable()), "overlap"));
        }
        assertEquals((long) box.getXSpan() * box.getYSpan() * box.getZSpan(), covered.size());
    }

    @Test
    void aBoxInsideOneChunkIsOneSlice() {
        BoundingBox box = new BoundingBox(1, 0, 1, 14, 5, 14);
        assertEquals(List.of(box), PlotWork.chunkSlices(box));
    }

    @Test
    void chunksListEveryTouchedChunkOnce() {
        BoundingBox first = new BoundingBox(0, 0, 0, 20, 5, 5);
        BoundingBox second = new BoundingBox(10, 0, 0, 40, 5, 5);
        assertEquals(Set.of(ChunkPos.asLong(0, 0), ChunkPos.asLong(1, 0), ChunkPos.asLong(2, 0)),
                new HashSet<>(PlotWork.chunks(List.of(first, second))));
    }

    @Test
    void buildingClearsTheLastRunFirstThenPlacesRoomsInOrder() {
        BoundingBox old = new BoundingBox(0, 64, 0, 20, 70, 5);
        BoundingBox start = new BoundingBox(0, 64, 0, 14, 70, 14);
        BoundingBox hall = new BoundingBox(15, 64, 0, 40, 70, 14);
        List<PlotWork.Unit> units = PlotWork.units(List.of(old), List.of(start, hall));

        assertInstanceOf(PlotWork.DiscardEntities.class, units.getFirst());
        assertEquals(old, ((PlotWork.DiscardEntities) units.getFirst()).area());
        List<PlotWork.Unit> clears = units.stream().filter(PlotWork.Clear.class::isInstance).toList();
        assertEquals(PlotWork.chunkSlices(old).size(), clears.size());
        List<Integer> placedRooms = units.stream()
                .filter(PlotWork.Place.class::isInstance)
                .map(unit -> ((PlotWork.Place) unit).room())
                .toList();
        assertEquals(List.of(0, 1, 1, 1), placedRooms);
        assertEquals(1 + clears.size() + placedRooms.size(), units.size());
        assertTrue(units.indexOf(clears.getLast()) < units.indexOf(units.stream()
                .filter(PlotWork.Place.class::isInstance).findFirst().orElseThrow()), "clearing comes first");
    }

    @Test
    void removingEntitiesTouchesOnlyChunksThePlotHolds() {
        // An L of two rooms: the box around both would reach chunk (2, 2), which neither room touches.
        BoundingBox along = new BoundingBox(0, 64, 0, 40, 70, 10);
        BoundingBox down = new BoundingBox(0, 64, 11, 10, 70, 40);
        List<BoundingBox> dirty = List.of(along, down);
        Set<Long> held = new HashSet<>(PlotWork.chunks(dirty));
        List<PlotWork.Unit> units = PlotWork.units(dirty, List.of());

        List<BoundingBox> areas = units.stream()
                .filter(PlotWork.DiscardEntities.class::isInstance)
                .map(unit -> ((PlotWork.DiscardEntities) unit).area())
                .toList();
        assertEquals(dirty, areas);
        for (BoundingBox area : areas) {
            assertTrue(held.containsAll(PlotWork.chunks(List.of(area))), "entity removal stays in held chunks");
        }
        assertTrue(units.indexOf(areas.getLast()) < units.indexOf(units.stream()
                .filter(PlotWork.Clear.class::isInstance).findFirst().orElseThrow()), "entities go before blocks");
    }

    @Test
    void aCleanPlotOnlyPlacesAndClosingOnlyClears() {
        BoundingBox room = new BoundingBox(0, 64, 0, 14, 70, 14);
        assertTrue(PlotWork.units(List.of(), List.of(room)).stream().allMatch(PlotWork.Place.class::isInstance));
        List<PlotWork.Unit> closing = PlotWork.units(List.of(room), List.of());
        assertTrue(closing.stream().noneMatch(PlotWork.Place.class::isInstance));
        assertEquals(2, closing.size());
    }
}
