package io.github.matejkadlec.realmforge.dungeon.instance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.serialization.JsonOps;
import java.util.List;
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
            new Vec3(7.5, 1, 7.5), -90.0F);
    private static final List<BoundingBox> ROOMS =
            List.of(new BoundingBox(0, 64, 0, 14, 70, 14), new BoundingBox(-22, 64, 15, 8, 76, 45));

    @Test
    void aBuildingPlotSurvivesSavingWithItsProgress() {
        Plot plot = new Plot(3);
        plot.startBuilding(LAYOUT, ROOMS, List.of(PLAYER));
        plot.advance();
        plot.advance();

        Plot loaded = Plot.CODEC.parse(JsonOps.INSTANCE, Plot.CODEC.encodeStart(JsonOps.INSTANCE, plot).getOrThrow())
                .getOrThrow();

        assertEquals(3, loaded.id());
        assertEquals(PlotState.BUILDING, loaded.state());
        assertEquals(LAYOUT, loaded.layout().orElseThrow());
        assertEquals(ROOMS, loaded.rooms());
        assertEquals(2, loaded.step());
        assertEquals(List.of(PLAYER), loaded.party());
        assertEquals(plot.units(), loaded.units(), "the same work list is rebuilt after a restart");
        assertEquals(loaded.units().size() - 2, loaded.recorder().finish().units(),
                "a resumed job reports only the units left after the saved step");
    }

    @Test
    void aBuiltPlotMustBeClearedOfItsRooms() {
        Plot plot = new Plot(0);
        plot.startBuilding(LAYOUT, ROOMS, List.of(PLAYER));
        plot.finishBuilding();
        assertEquals(PlotState.READY, plot.state());
        assertEquals(ROOMS, plot.dirty());

        plot.startClosing();
        assertEquals(PlotState.CLOSING, plot.state());
        assertEquals(0, plot.step());
        assertTrue(plot.party().isEmpty() && plot.rooms().isEmpty());
        assertEquals(PlotWork.units(ROOMS, List.of()), plot.units());
        assertEquals(List.of(), plot.keepWhileClearing());

        plot.free(true);
        assertEquals(PlotState.FREE, plot.state());
        assertTrue(plot.dirty().isEmpty());
    }

    @Test
    void closingAnUnfinishedBuildAlsoClearsItsRooms() {
        Plot plot = new Plot(0);
        plot.startBuilding(LAYOUT, ROOMS, List.of(PLAYER));
        plot.startClosing();
        assertEquals(ROOMS, plot.dirty());
    }

    @Test
    void overwriteKeepsTheBlocksForTheNextRunWhichSparesItsOwnRooms() {
        Plot plot = new Plot(0);
        plot.startBuilding(LAYOUT, ROOMS, List.of(PLAYER));
        plot.finishBuilding();
        plot.free(false);
        assertEquals(PlotState.FREE, plot.state());
        assertEquals(ROOMS, plot.dirty());

        List<BoundingBox> next = List.of(new BoundingBox(0, 64, 0, 14, 70, 14));
        plot.startBuilding(LAYOUT, next, List.of(PLAYER));
        assertEquals(next, plot.keepWhileClearing());
        assertEquals(PlotWork.units(ROOMS, next), plot.units());
    }
}
