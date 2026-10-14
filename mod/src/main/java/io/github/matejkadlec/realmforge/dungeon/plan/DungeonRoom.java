package io.github.matejkadlec.realmforge.dungeon.plan;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * A room of a dungeon type's pool as the planner sees it: its template, size, role, weight and connectors, all
 * resolved by {@code rf gen} from {@code content/rooms/} (architecture 15.3), so planning needs no template.
 *
 * @param template the structure {@code realmforge:rooms/<id>} the builder places
 * @param size the template's size
 * @param role the part it plays in a layout
 * @param weight how often the planner picks it among the rooms that fit, relative to the others (1 to 150)
 * @param connectors its connectors, in template order
 */
public record DungeonRoom(Identifier template, Vec3i size, RoomRole role, int weight, List<Connector> connectors) {
    /** The most a structure block saves per axis, which room validation allows. */
    public static final int MAX_SIZE = 48;

    public static final Codec<DungeonRoom> CODEC = RecordCodecBuilder.<DungeonRoom>create(instance -> instance.group(
                    Identifier.CODEC.fieldOf("template").forGetter(DungeonRoom::template),
                    Vec3i.CODEC.fieldOf("size").forGetter(DungeonRoom::size),
                    RoomRole.CODEC.fieldOf("role").forGetter(DungeonRoom::role),
                    Codec.intRange(1, 150).optionalFieldOf("weight", 1).forGetter(DungeonRoom::weight),
                    Connector.CODEC.listOf().fieldOf("connectors").forGetter(DungeonRoom::connectors))
            .apply(instance, DungeonRoom::new)).validate(DungeonRoom::validate);

    public DungeonRoom {
        connectors = List.copyOf(connectors);
    }

    /** Whether it has one connector: it ends a branch, or closes a connector nothing else takes. */
    public boolean deadEnd() {
        return connectors.size() == 1;
    }

    /** Its box once turned by {@code rotation} about its origin corner and moved by {@code offset}, as placed. */
    public BoundingBox box(BlockPos offset, Rotation rotation) {
        BlockPos far = new BlockPos(size.getX() - 1, size.getY() - 1, size.getZ() - 1);
        return BoundingBox.fromCorners(StructureTemplate.transform(BlockPos.ZERO, Mirror.NONE, rotation, BlockPos.ZERO),
                StructureTemplate.transform(far, Mirror.NONE, rotation, BlockPos.ZERO)).moved(offset.getX(),
                offset.getY(), offset.getZ());
    }

    private static DataResult<DungeonRoom> validate(DungeonRoom room) {
        Vec3i size = room.size;
        if (size.getX() < 1 || size.getY() < 1 || size.getZ() < 1 || size.getX() > MAX_SIZE || size.getY() > MAX_SIZE
                || size.getZ() > MAX_SIZE) {
            return DataResult.error(() -> room.template + ": size " + size.toShortString() + " is not 1 to "
                    + MAX_SIZE + " per axis");
        }
        for (Connector connector : room.connectors) {
            BlockPos pos = connector.pos();
            if (pos.getX() < 0 || pos.getY() < 0 || pos.getZ() < 0 || pos.getX() >= size.getX()
                    || pos.getY() >= size.getY() || pos.getZ() >= size.getZ()) {
                return DataResult.error(() -> room.template + ": connector at " + pos.toShortString()
                        + " lies outside the room");
            }
        }
        return DataResult.success(room);
    }
}
