package io.github.matejkadlec.realmforge.dungeon.instance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.junit.jupiter.api.Test;

class PlotGridTest {
    @Test
    void plotsFillRowsOfCellsSpacingApart() {
        assertEquals(new BlockPos(0, PlotGrid.BASE_Y, 0), PlotGrid.origin(0));
        assertEquals(new BlockPos(PlotGrid.SPACING, PlotGrid.BASE_Y, 0), PlotGrid.origin(1));
        assertEquals(new BlockPos(0, PlotGrid.BASE_Y, PlotGrid.SPACING), PlotGrid.origin(PlotGrid.PER_ROW));
        assertThrows(IllegalArgumentException.class, () -> PlotGrid.origin(-1));
        assertThrows(IllegalArgumentException.class, () -> PlotGrid.origin(PlotGrid.PER_ROW * PlotGrid.PER_ROW));
    }

    @Test
    void everyPositionWithinHalfACellBelongsToThatPlot() {
        for (int id : new int[] {0, 1, 7, PlotGrid.PER_ROW + 3}) {
            BlockPos origin = PlotGrid.origin(id);
            int half = PlotGrid.SPACING / 2;
            assertEquals(id, PlotGrid.plotAt(origin.getX(), origin.getZ()));
            assertEquals(id, PlotGrid.plotAt(origin.getX() - half, origin.getZ() + half - 1));
            assertEquals(id, PlotGrid.plotAt(origin.getX() + half - 1, origin.getZ() - half));
        }
        assertEquals(-1, PlotGrid.plotAt(-PlotGrid.SPACING, 0));
        assertEquals(-1, PlotGrid.plotAt(0, -PlotGrid.SPACING));
    }

    @Test
    void roomsMustStayWithinReachAndHeight() {
        int reach = PlotGrid.MAX_REACH;
        assertTrue(PlotGrid.fits(new BoundingBox(-reach, 0, -reach, reach, 30, reach)));
        assertFalse(PlotGrid.fits(new BoundingBox(0, 0, 0, reach + 1, 30, 10)));
        assertFalse(PlotGrid.fits(new BoundingBox(0, 0, -reach - 1, 10, 30, 10)));
        assertFalse(PlotGrid.fits(new BoundingBox(0, -PlotGrid.BASE_Y - 1, 0, 10, 10, 10)));
        assertFalse(PlotGrid.fits(new BoundingBox(0, 0, 0, 10, 256 - PlotGrid.BASE_Y, 10)));
    }
}
