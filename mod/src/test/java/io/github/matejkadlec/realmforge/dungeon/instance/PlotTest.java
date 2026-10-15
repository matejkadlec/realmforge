package io.github.matejkadlec.realmforge.dungeon.instance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.serialization.JsonOps;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class PlotTest {
    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final InstanceLayout LAYOUT = new InstanceLayout(
            List.of(new RoomPlacement(Identifier.parse("realmforge_qa:spike/start"), BlockPos.ZERO,
                            Rotation.NONE),
                    new RoomPlacement(Identifier.parse("realmforge_qa:spike/hall"), new BlockPos(15, 0, -8),
                            Rotation.CLOCKWISE_90)),
            Optional.of(new BlockPos(14, 1, 7)));
    private static final List<BoundingBox> ROOMS =
            List.of(new BoundingBox(0, 64, 0, 14, 70, 14), new BoundingBox(-22, 64, 15, 8, 76, 45));
    private static final List<DataMarker> MARKERS = List.of(
            new DataMarker(DataMarker.Kind.ENTRANCE, 0, new BlockPos(7, 1, 7)),
            new DataMarker(DataMarker.Kind.EXIT, 1, new BlockPos(-12, 1, 30)));

    @Test
    void aBuildingPlotSurvivesSavingWithItsProgress() {
        Plot plot = new Plot(3);
        plot.startBuilding(LAYOUT, ROOMS, MARKERS, List.of(PLAYER));
        plot.advance();
        plot.advance();

        Plot loaded = reload(plot);

        assertEquals(3, loaded.id());
        assertEquals(PlotState.BUILDING, loaded.state());
        assertEquals(LAYOUT, loaded.layout().orElseThrow());
        assertEquals(ROOMS, loaded.rooms());
        assertEquals(MARKERS, loaded.markers());
        assertEquals(Optional.empty(), loaded.spawn(), "players arrive somewhere only once the rooms stand");
        assertEquals(2, loaded.step());
        assertEquals(List.of(PLAYER), loaded.party());
        assertEquals(plot.units(), loaded.units(), "the same work list is rebuilt after a restart");
        assertEquals(loaded.units().size() - 2, loaded.recorder().finish().units(),
                "a resumed job reports only the units left after the saved step");
    }

    @Test
    void aBuiltPlotKeepsItsSpawnAndMustBeClearedOfItsRoomsAndTheirMargin() {
        Plot plot = new Plot(0);
        plot.startBuilding(LAYOUT, ROOMS, MARKERS, List.of(PLAYER));
        Vec3 spawn = new Vec3(7.5, 1, 7.5);
        plot.finishBuilding(spawn);
        assertEquals(PlotState.READY, plot.state());
        assertEquals(PlotWork.withMargin(ROOMS), plot.dirty());
        assertEquals(Optional.of(spawn), reload(plot).spawn());
        assertEquals(Optional.of(MARKERS.get(1)), plot.marker(DataMarker.Kind.EXIT, 1));
        assertEquals(Optional.empty(), plot.marker(DataMarker.Kind.EXIT, 0));

        plot.startClosing();
        assertEquals(PlotState.CLOSING, plot.state());
        assertEquals(0, plot.step());
        assertTrue(plot.party().isEmpty() && plot.rooms().isEmpty() && plot.markers().isEmpty());
        assertEquals(Optional.empty(), plot.spawn());
        assertEquals(PlotWork.units(PlotWork.withMargin(ROOMS), List.of()), plot.units());

        plot.free();
        assertEquals(PlotState.FREE, plot.state());
        assertTrue(plot.dirty().isEmpty());
        assertTrue(plot.wantedChunks().isEmpty(), "a free plot holds no chunks");
    }

    @Test
    void closingAnUnfinishedBuildAlsoClearsItsRoomsAndTheirMargin() {
        Plot plot = new Plot(0);
        plot.startBuilding(LAYOUT, ROOMS, MARKERS, List.of(PLAYER));
        plot.advance();
        plot.startClosing();
        assertEquals(PlotWork.withMargin(ROOMS), plot.dirty());
    }

    @Test
    void closingABuildBeforeAnyUnitRanClearsNothing() {
        Plot plot = new Plot(0);
        plot.startBuilding(LAYOUT, ROOMS, MARKERS, List.of(PLAYER));
        plot.startClosing();
        assertEquals(List.of(), plot.dirty());
        assertEquals(List.of(), plot.units(), "nothing to clear, so the plot is free at its next turn");
        assertTrue(plot.wantedChunks().isEmpty(), "and loads no chunk for it");
    }

    @Test
    void aPlotWantsTheChunksOfItsRoomsWithTheirMarginFromOpeningToClosing() {
        Plot plot = new Plot(0);
        plot.startBuilding(LAYOUT, ROOMS, MARKERS, List.of(PLAYER));
        assertEquals(PlotWork.chunks(PlotWork.withMargin(ROOMS)), plot.wantedChunks());
        assertTrue(plot.wantedChunks().size() > PlotWork.chunks(ROOMS).size(),
                "the margin reaches into chunks the rooms do not touch");
        plot.finishBuilding(Vec3.ZERO);
        assertEquals(PlotWork.chunks(PlotWork.withMargin(ROOMS)), plot.wantedChunks());
        plot.startClosing();
        assertEquals(PlotWork.chunks(PlotWork.withMargin(ROOMS)), plot.wantedChunks());
    }

    @Test
    void aCompletedRunStaysPlayableAndCompleted() {
        Plot plot = new Plot(0);
        plot.startBuilding(LAYOUT, ROOMS, MARKERS, List.of(PLAYER));
        plot.finishBuilding(Vec3.ZERO);
        plot.activate();
        assertEquals(PlotState.ACTIVE, plot.state());
        plot.complete();
        plot.activate();
        assertEquals(PlotState.COMPLETED, plot.state(), "a player who comes back does not undo the completion");
        assertTrue(plot.state().isPlayable());
        assertFalse(plot.state().hasWork());
        assertEquals(PlotState.COMPLETED, reload(plot).state());
    }

    private static Plot reload(Plot plot) {
        return Plot.CODEC.parse(JsonOps.INSTANCE, Plot.CODEC.encodeStart(JsonOps.INSTANCE, plot).getOrThrow())
                .getOrThrow();
    }
}
