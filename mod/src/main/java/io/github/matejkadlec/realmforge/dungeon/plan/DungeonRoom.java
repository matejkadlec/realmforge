package io.github.matejkadlec.realmforge.dungeon.plan;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
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
 * @param maxPerLayout the most times one layout holds it, if limited (RLM-256)
 * @param nearStart whether it may only hang off the entrance or the first room of the main path (RLM-256)
 * @param explosionBreaks the blocks an explosion inside it may break in an instance (RLM-257)
 * @param entry the connector it is always entered through, if it has a way in (a lock's mechanism side, RLM-362): the
 *     planner attaches it to the room before it through this one, and no loop leads into it from another side
 * @param spawners the mob spawners in its template, which make it a fight ({@link #pace}, RLM-364)
 * @param chests the chests in its template: the planner ends the main path in the room with the most, and a branch in
 *     a room with at least {@link #TREASURE_CHESTS} where no treasure room fits (RLM-364)
 * @param hazard whether its template holds lava a player reaches, TNT or a dispenser: the planner keeps it from the
 *     start of the main path, from right before a fight and from other hazards (RLM-364)
 * @param containers the chests and barrels its dungeon's chance tables decide, which the builder draws from
 *     (RLM-365)
 */
public record DungeonRoom(Identifier template, Vec3i size, RoomRole role, int weight, List<Connector> connectors,
        Optional<Integer> maxPerLayout, boolean nearStart, List<Identifier> explosionBreaks,
        Optional<Integer> entry, int spawners, int chests, boolean hazard, RoomContainers containers) {
    /** The most a structure block saves per axis, which room validation allows. */
    public static final int MAX_SIZE = 48;
    /** The chests that make a room a treasure stop besides the treasure rooms (Matěj's playtest of 2026-10-24). */
    public static final int TREASURE_CHESTS = 3;

    public static final Codec<DungeonRoom> CODEC = RecordCodecBuilder.<DungeonRoom>create(instance -> instance.group(
                    Identifier.CODEC.fieldOf("template").forGetter(DungeonRoom::template),
                    Vec3i.CODEC.fieldOf("size").forGetter(DungeonRoom::size),
                    RoomRole.CODEC.fieldOf("role").forGetter(DungeonRoom::role),
                    Codec.intRange(1, 150).optionalFieldOf("weight", 1).forGetter(DungeonRoom::weight),
                    Connector.CODEC.listOf().fieldOf("connectors").forGetter(DungeonRoom::connectors),
                    Codec.intRange(1, 16).optionalFieldOf("max_per_layout").forGetter(DungeonRoom::maxPerLayout),
                    Codec.BOOL.optionalFieldOf("near_start", false).forGetter(DungeonRoom::nearStart),
                    Identifier.CODEC.listOf().optionalFieldOf("explosion_breaks", List.of())
                            .forGetter(DungeonRoom::explosionBreaks),
                    Codec.intRange(0, 63).optionalFieldOf("entry").forGetter(DungeonRoom::entry),
                    Codec.intRange(0, 256).optionalFieldOf("spawners", 0).forGetter(DungeonRoom::spawners),
                    Codec.intRange(0, 256).optionalFieldOf("chests", 0).forGetter(DungeonRoom::chests),
                    Codec.BOOL.optionalFieldOf("hazard", false).forGetter(DungeonRoom::hazard),
                    RoomContainers.CODEC.optionalFieldOf("containers", RoomContainers.NONE)
                            .forGetter(DungeonRoom::containers))
            .apply(instance, DungeonRoom::new)).validate(DungeonRoom::validate);

    public DungeonRoom {
        connectors = List.copyOf(connectors);
        explosionBreaks = List.copyOf(explosionBreaks);
    }

    /** A room whose containers all stay. */
    public DungeonRoom(Identifier template, Vec3i size, RoomRole role, int weight, List<Connector> connectors,
            Optional<Integer> maxPerLayout, boolean nearStart, List<Identifier> explosionBreaks,
            Optional<Integer> entry, int spawners, int chests, boolean hazard) {
        this(template, size, role, weight, connectors, maxPerLayout, nearStart, explosionBreaks, entry, spawners,
                chests, hazard, RoomContainers.NONE);
    }

    /**
     * A room without limits on where, how often and from which side it goes, whose explosions break nothing, without
     * spawners, chests or hazards.
     */
    public DungeonRoom(Identifier template, Vec3i size, RoomRole role, int weight, List<Connector> connectors) {
        this(template, size, role, weight, connectors, Optional.empty(), false, List.of(), Optional.empty(), 0, 0,
                false);
    }

    /**
     * The part it plays in a layout's rhythm (RLM-364): hallways and stairs are corridors, a combat room with a
     * spawner is a fight and one without a feature (a lock, a trap, a sight), a treasure room is treasure. The
     * entrance, the boss's lair and a wall have none.
     */
    public Optional<Pace> pace() {
        return switch (role) {
            case HALLWAY, STAIRS -> Optional.of(Pace.CORRIDOR);
            case COMBAT -> Optional.of(spawners > 0 ? Pace.FIGHT : Pace.FEATURE);
            case TREASURE -> Optional.of(Pace.TREASURE);
            case ENTRANCE, BOSS, WALL -> Optional.empty();
        };
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
        if (room.entry.isPresent() && room.entry.get() >= room.connectors.size()) {
            return DataResult.error(() -> room.template + ": its entry is connector " + room.entry.get() + " of "
                    + room.connectors.size());
        }
        if (room.role == RoomRole.WALL && !room.deadEnd()) {
            return DataResult.error(() -> room.template + ": a wall has one connector, not "
                    + room.connectors.size());
        }
        return DataResult.success(room);
    }
}
