package io.github.matejkadlec.realmforge.qa.dungeon;

import io.github.matejkadlec.realmforge.dungeon.plan.DungeonPlan;
import io.github.matejkadlec.realmforge.dungeon.plan.PlannedRoom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * A planned layout as text for review ({@code /qa dungeon plan}): a top-down map with north up, each room drawn as a
 * letter over its footprint (a room placed later covers one below it), and a legend with each room's slot, template,
 * offset, rotation and size.
 */
public final class LayoutMap {
    /** The letters rooms are drawn with, in placement order; the entrance is {@code A}. */
    static final String SYMBOLS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";

    private LayoutMap() {
    }

    /** The map, at most {@code maxWidth} characters wide and deep: one character covers a square of blocks. */
    public static List<String> map(DungeonPlan plan, int maxWidth) {
        BoundingBox bounds = plan.bounds();
        int scale = Math.max(1, Math.max(ceilDiv(bounds.getXSpan(), maxWidth), ceilDiv(bounds.getZSpan(), maxWidth)));
        int columns = ceilDiv(bounds.getXSpan(), scale);
        int rows = ceilDiv(bounds.getZSpan(), scale);
        char[][] grid = new char[rows][columns];
        for (char[] row : grid) {
            Arrays.fill(row, '.');
        }
        for (int i = 0; i < plan.rooms().size(); i++) {
            BoundingBox box = plan.rooms().get(i).box();
            char symbol = symbol(i);
            for (int row = (box.minZ() - bounds.minZ()) / scale; row <= (box.maxZ() - bounds.minZ()) / scale; row++) {
                for (int column = (box.minX() - bounds.minX()) / scale;
                        column <= (box.maxX() - bounds.minX()) / scale; column++) {
                    grid[row][column] = symbol;
                }
            }
        }
        List<String> lines = new ArrayList<>(rows + 1);
        lines.add(String.format(Locale.ROOT, "north up, 1 character = %d x %d blocks, x %d..%d, z %d..%d", scale, scale,
                bounds.minX(), bounds.maxX(), bounds.minZ(), bounds.maxZ()));
        for (char[] row : grid) {
            lines.add(new String(row));
        }
        return lines;
    }

    /** One line per room, in placement order: its letter, slot, template, offset, rotation and size. */
    public static List<String> legend(DungeonPlan plan) {
        List<String> lines = new ArrayList<>(plan.rooms().size());
        for (int i = 0; i < plan.rooms().size(); i++) {
            PlannedRoom room = plan.rooms().get(i);
            BoundingBox box = room.box();
            lines.add(String.format(Locale.ROOT, "%c %-10s %s at %s, %s, %dx%dx%d", symbol(i), room.slot().label(),
                    room.room().template(), room.offset().toShortString(), room.rotation().getSerializedName(),
                    box.getXSpan(), box.getYSpan(), box.getZSpan()));
        }
        return lines;
    }

    static char symbol(int room) {
        return SYMBOLS.charAt(room % SYMBOLS.length());
    }

    private static int ceilDiv(int value, int divisor) {
        return Math.ceilDiv(value, divisor);
    }
}
