package io.github.matejkadlec.realmforge.dungeon.plan;

import io.github.matejkadlec.realmforge.dungeon.instance.InstanceLayout;
import io.github.matejkadlec.realmforge.dungeon.instance.PiecePlacement;
import io.github.matejkadlec.realmforge.dungeon.instance.RoomPlacement;
import java.util.List;
import java.util.ArrayList;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * A planned layout (architecture 15.3, steps 1 and 2): its rooms in placement order, the entrance first, and the
 * connectors that meet. Every connector of every room meets another one, and the rooms' boxes do not overlap.
 *
 * @param seed the seed it was planned from
 * @param attempt which of the planner's attempts found it, from 1
 * @param setPieces the set pieces its dungeon type dresses it with ({@link #toInstanceLayout} draws them)
 */
public record DungeonPlan(long seed, int attempt, List<PlannedRoom> rooms, List<RoomLink> links, SetPieces setPieces) {
    /** Keeps the containers' draws apart from the planner's, which starts from the same seed. */
    private static final long CONTAINER_SALT = 0x2B7E151628AED2A6L;
    /** Keeps the openings' draws apart from the planner's and the containers'. */
    private static final long OPENING_SALT = 0x3243F6A8885A308DL;
    /** Keeps the set pieces' draws apart from the others. */
    private static final long SET_PIECE_SALT = 0x452821E638D01377L;

    public DungeonPlan {
        rooms = List.copyOf(rooms);
        links = List.copyOf(links);
    }

    /** A layout without set pieces, as the planner returns it. */
    public DungeonPlan(long seed, int attempt, List<PlannedRoom> rooms, List<RoomLink> links) {
        this(seed, attempt, rooms, links, SetPieces.NONE);
    }

    /** The same layout dressed with {@code pieces}. */
    public DungeonPlan withSetPieces(SetPieces pieces) {
        return new DungeonPlan(seed, attempt, rooms, links, pieces);
    }

    public PlannedRoom entrance() {
        return rooms.getFirst();
    }

    /** The box around every room, relative to the plot's origin. */
    public BoundingBox bounds() {
        return BoundingBox.encapsulatingBoxes(rooms.stream().map(PlannedRoom::box).toList()).orElseThrow();
    }

    public long count(Slot slot) {
        return rooms.stream().filter(room -> room.slot() == slot).count();
    }

    /**
     * The connector room {@code room} is entered through: the one by which it hangs off the room before it (the link
     * that placed it, the first of {@link #links} that reaches it from a room placed earlier). None for the entrance.
     */
    public Optional<Integer> wayIn(int room) {
        return links.stream()
                .filter(link -> link.otherRoom() == room && link.room() < room)
                .findFirst()
                .map(RoomLink::otherConnector);
    }

    /**
     * What the builder places: every room's template, offset and rotation with the blocks an explosion may break
     * there, the containers its chance tables drop (RLM-365), the side it opens, which is never its way in
     * (RLM-412), the loot table of its containers (RLM-290) and its set pieces (RLM-282); all drawn in room order
     * from the layout's seed, each kind from a stream of its own, so the same seed always gives the same rooms. Then
     * the entrance's connector to the next room on the main path, which an arriving player looks at. Where players
     * arrive comes from the entrance room (its entrance marker, or a spot the builder finds there).
     */
    public InstanceLayout toInstanceLayout() {
        RandomSource containers = RandomSource.create(seed ^ CONTAINER_SALT);
        RandomSource openings = RandomSource.create(seed ^ OPENING_SALT);
        RandomSource dressing = RandomSource.create(seed ^ SET_PIECE_SALT);
        List<RoomPlacement> placements = new ArrayList<>();
        for (int i = 0; i < rooms.size(); i++) {
            DungeonRoom room = rooms.get(i).room();
            Optional<Direction> wayIn = wayIn(i).map(connector -> room.connectors().get(connector).front())
                    .filter(front -> front.getAxis().isHorizontal());
            List<RoomPlacement.Swap> swaps = new ArrayList<>(room.containers().draw(containers));
            swaps.addAll(room.openings().draw(wayIn, openings));
            List<PiecePlacement> pieces = setPieces.draw(room, rooms.get(i).offset(), rooms.get(i).rotation(),
                    dressing);
            placements.add(new RoomPlacement(room.template(), rooms.get(i).offset(), rooms.get(i).rotation(),
                    room.explosionBreaks(), swaps, room.containerLoot(), pieces));
        }
        Optional<BlockPos> wayOn = links.stream()
                .filter(link -> link.room() == 0 && link.otherRoom() == 1 || link.room() == 1 && link.otherRoom() == 0)
                .findFirst()
                .map(link -> entrance().connector(link.room() == 0 ? link.connector() : link.otherConnector()).pos());
        return new InstanceLayout(placements, wayOn);
    }
}
