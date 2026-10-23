package io.github.matejkadlec.realmforge.qa.setpiece;

import io.github.matejkadlec.realmforge.qa.RealmforgeQa;
import io.github.matejkadlec.realmforge.qa.generated.GeneratedSetPieceMasks;
import io.github.matejkadlec.realmforge.qa.generated.GeneratedSetPieceMasks.NamedMask;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StructureBlock;
import net.minecraft.world.level.block.entity.StructureBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.StructureMode;

/**
 * The set-piece workshop jig (RLM-281, the design's building workflow): the frame for a mask, built north of where
 * the player stands, so building and saving a piece is one step. The mask's cells lie as the fit rule reads them:
 * its first row, the wall side, to the north, and the room to the south, towards the player. The jig lays a floor
 * under the frame and the player, clears the air above it, builds the {@code W} cells as walls three high (for a
 * mask whose piece brings its own walls, it leaves them empty and builds a flat wall behind the first row instead),
 * puts glass on the clearance cells and a structure block in Save mode into the floor, its box set to the piece's
 * cells three high and its name {@code realmforge_qa:set_piece/<mask>_<n>}, the first {@code n} the world has no
 * template for. Pressing Save writes {@code <world>/generated/realmforge_qa/structure/set_piece/<mask>_<n>.nbt},
 * which {@code rf set-pieces import} brings into {@code content/set_pieces/}.
 */
public final class SetPieceJig {
    /** The tallest piece: the walls' height and the structure block's box. */
    public static final int HEIGHT = 3;
    /** Rows of floor between the frame's front row and the player. */
    public static final int GAP = 2;
    public static final Block FLOOR = Blocks.SMOOTH_STONE;
    public static final Block CLEARANCE = Blocks.GLASS;
    public static final String NAME_PREFIX = "set_piece/";

    private SetPieceJig() {
    }

    /**
     * What the jig built: the structure block, the name it saves under, its box's lowest corner and its size, and the
     * world position of the mask's first cell (column 0, row 0).
     */
    public record Jig(BlockPos structureBlock, Identifier name, BlockPos boxOrigin, Vec3i size, BlockPos maskOrigin) {
    }

    public static Optional<NamedMask> mask(String name) {
        return GeneratedSetPieceMasks.MASKS.stream().filter(mask -> mask.name().equals(name)).findFirst();
    }

    /** Builds the jig for {@code mask} north of {@code feet}, the block the player stands in, walls of {@code wall}. */
    public static Jig build(ServerLevel level, BlockPos feet, NamedMask mask, BlockState wall) {
        List<String[]> rows = mask.rows().stream().map(row -> row.split(" ")).toList();
        int width = rows.getFirst().length;
        int depth = rows.size();
        int x0 = feet.getX() - (width - 1) / 2;
        int z0 = feet.getZ() - GAP - depth;
        int y = feet.getY();
        int back = mask.buildsWalls() ? 1 : 0;
        for (int x = x0 - 1; x <= x0 + width; x++) {
            for (int z = z0 - 1 - back; z <= feet.getZ(); z++) {
                level.setBlock(new BlockPos(x, y - 1, z), FLOOR.defaultBlockState(), Block.UPDATE_ALL);
                for (int dy = 0; dy <= HEIGHT; dy++) {
                    level.setBlock(new BlockPos(x, y + dy, z), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                }
            }
        }
        int minX = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (int row = 0; row < depth; row++) {
            for (int column = 0; column < width; column++) {
                String cell = rows.get(row)[column];
                BlockPos pos = new BlockPos(x0 + column, y, z0 + row);
                boolean piece = cell.equals(".") || cell.equals("W") && mask.buildsWalls();
                if (piece) {
                    minX = Math.min(minX, pos.getX());
                    maxX = Math.max(maxX, pos.getX());
                    minZ = Math.min(minZ, pos.getZ());
                    maxZ = Math.max(maxZ, pos.getZ());
                } else if (cell.equals("W")) {
                    column(level, pos, wall);
                } else if (cell.equals("_")) {
                    level.setBlock(pos, CLEARANCE.defaultBlockState(), Block.UPDATE_ALL);
                }
                if (mask.buildsWalls() && row == 0 && cell.equals("W")) {
                    column(level, pos.north(), wall);
                }
            }
        }
        BlockPos boxOrigin = new BlockPos(minX, y, minZ);
        Vec3i size = new Vec3i(maxX - minX + 1, HEIGHT, maxZ - minZ + 1);
        BlockPos at = new BlockPos(x0 - 1, y - 1, z0 + depth);
        Identifier name = freeName(level, mask.name());
        level.setBlock(at, Blocks.STRUCTURE_BLOCK.defaultBlockState().setValue(StructureBlock.MODE, StructureMode.SAVE),
                Block.UPDATE_ALL);
        if (level.getBlockEntity(at) instanceof StructureBlockEntity block) {
            block.setMode(StructureMode.SAVE);
            block.setStructureName(name);
            block.setStructurePos(boxOrigin.subtract(at));
            block.setStructureSize(size);
            block.setIgnoreEntities(false);
            block.setShowBoundingBox(true);
            block.setChanged();
            level.sendBlockUpdated(at, block.getBlockState(), block.getBlockState(), Block.UPDATE_ALL);
        }
        return new Jig(at, name, boxOrigin, size, new BlockPos(x0, y, z0));
    }

    private static void column(ServerLevel level, BlockPos pos, BlockState wall) {
        for (int dy = 0; dy < HEIGHT; dy++) {
            level.setBlock(pos.above(dy), wall, Block.UPDATE_ALL);
        }
    }

    /** {@code realmforge_qa:set_piece/<mask>_<n>} with the first {@code n} the world has no template for. */
    private static Identifier freeName(ServerLevel level, String mask) {
        for (int n = 1; ; n++) {
            Identifier name = RealmforgeQa.id(NAME_PREFIX + mask + "_" + n);
            if (level.getStructureManager().get(name).isEmpty()) {
                return name;
            }
        }
    }
}
