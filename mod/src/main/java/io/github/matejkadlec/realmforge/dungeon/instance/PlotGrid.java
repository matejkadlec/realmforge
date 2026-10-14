package io.github.matejkadlec.realmforge.dungeon.instance;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * Where plots lie in the instance dimension: a grid of cells {@value #SPACING} blocks apart (like Compact
 * Machines), filled row by row. A plot's rooms must stay within {@value #MAX_REACH} blocks of its origin, so two
 * plots never come within half a cell of each other: render and simulation distance cannot show one plot from
 * another.
 */
public final class PlotGrid {
    /** Distance between the origins of neighbouring plots, in blocks: 64 chunks, two region files. */
    public static final int SPACING = 1024;
    /** Plots per row before the next row starts. 1024 rows of 1024 plots stay far inside the world border. */
    public static final int PER_ROW = 1024;
    /** Height of every plot's origin; the instance dimension spans y = 0 to 255. */
    public static final int BASE_Y = 64;
    /** How far a plot's rooms may reach from its origin on the x and z axes. */
    public static final int MAX_REACH = SPACING / 4;

    private PlotGrid() {
    }

    public static BlockPos origin(int plotId) {
        if (plotId < 0 || plotId >= PER_ROW * PER_ROW) {
            throw new IllegalArgumentException("plot id out of range: " + plotId);
        }
        return new BlockPos((plotId % PER_ROW) * SPACING, BASE_Y, (plotId / PER_ROW) * SPACING);
    }

    /** The plot whose cell contains this position (cells are centred on the origins), or -1 outside the grid. */
    public static int plotAt(int x, int z) {
        int column = Math.floorDiv(x + SPACING / 2, SPACING);
        int row = Math.floorDiv(z + SPACING / 2, SPACING);
        if (column < 0 || column >= PER_ROW || row < 0 || row >= PER_ROW) {
            return -1;
        }
        return row * PER_ROW + column;
    }

    /**
     * Where a plot's rooms may lie, relative to its origin: within its reach and the dimension's height. A new box each
     * time, as a {@link BoundingBox} can still be changed in place.
     */
    public static BoundingBox limits() {
        return new BoundingBox(-MAX_REACH, -BASE_Y, -MAX_REACH, MAX_REACH, 255 - BASE_Y, MAX_REACH);
    }

    /** Whether a box, relative to a plot's origin, stays within the plot's reach and the dimension's height. */
    public static boolean fits(BoundingBox relative) {
        return relative.minX() >= -MAX_REACH && relative.maxX() <= MAX_REACH
                && relative.minZ() >= -MAX_REACH && relative.maxZ() <= MAX_REACH
                && relative.minY() + BASE_Y >= 0 && relative.maxY() + BASE_Y <= 255;
    }
}
