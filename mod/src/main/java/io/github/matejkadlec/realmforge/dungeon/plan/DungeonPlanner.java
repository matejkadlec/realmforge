package io.github.matejkadlec.realmforge.dungeon.plan;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * Lays out a dungeon from its rules, its rooms and a seed (architecture 15.3, steps 1 and 2). Pure: it reads no world
 * and no template, so JUnit fuzzes it over thousands of seeds, and the same seed always gives the same layout.
 *
 * <p>Each attempt draws a {@link RoomGraph} (step 1), then places its rooms one by one, parent first (step 2): a room
 * that fits the node's slot, turned four ways, with one of its connectors taking a free connector of the parent by
 * vanilla's jigsaw rules ({@link Connector#takes}), inside the limits and overlapping no placed room. A node with no
 * such placement sends the search back to the node before it. Once every node has its room, each connector still free
 * meets a free connector of a neighbour right in front of it, which closes a loop, or gets a dead end ({@link
 * Slot#CAP}); a connector neither can close sends the search back too. An attempt that runs out of steps gives up,
 * and the next one draws a new graph from the same random source.
 */
public final class DungeonPlanner {
    /** Attempts per seed before planning fails. */
    static final int ATTEMPTS = 64;
    /** Placements an attempt may try before it gives up. */
    static final int STEPS_PER_ATTEMPT = 20_000;

    private DungeonPlanner() {
    }

    /**
     * Plans a layout whose rooms all lie inside {@code limits}, relative to the plot's origin, where the entrance's
     * origin corner goes.
     *
     * @throws PlanningException when no attempt finds a layout
     */
    public static DungeonPlan plan(List<DungeonRoom> rooms, LayoutRules rules, long seed, BoundingBox limits) {
        RandomSource random = RandomSource.create(seed);
        for (int attempt = 1; attempt <= ATTEMPTS; attempt++) {
            Layout layout = new Layout(rooms, rules.ending(), RoomGraph.draw(rules, random), random, limits);
            if (layout.placeFrom(0)) {
                return new DungeonPlan(seed, attempt, layout.placed, layout.links);
            }
        }
        throw new PlanningException("no layout of " + rooms.size() + " rooms fits the rules within " + ATTEMPTS
                + " attempts for seed " + seed);
    }

    /** Step 2 for one graph: a depth-first search that undoes a placement whose followers cannot be placed. */
    private static final class Layout {
        private final List<DungeonRoom> rooms;
        private final RoomRole ending;
        private final RoomGraph graph;
        private final RandomSource random;
        private final BoundingBox limits;
        private final List<PlannedRoom> placed = new ArrayList<>();
        /** Per placed room, which of its connectors already meet another one. */
        private final List<boolean[]> used = new ArrayList<>();
        private final List<RoomLink> links = new ArrayList<>();
        private final int[] roomOfNode;
        private int steps;

        private Layout(List<DungeonRoom> rooms, RoomRole ending, RoomGraph graph, RandomSource random,
                BoundingBox limits) {
            this.rooms = rooms;
            this.ending = ending;
            this.graph = graph;
            this.random = random;
            this.limits = limits;
            this.roomOfNode = new int[graph.size()];
        }

        /** Places node {@code node} and every node after it, or leaves the layout as it was and returns false. */
        private boolean placeFrom(int node) {
            if (node == graph.size()) {
                return closeFreeConnectors();
            }
            RoomGraph.Node current = graph.node(node);
            for (DungeonRoom room : byWeight(candidates(current.slot(), graph.connectorsNeeded(node)))) {
                for (Rotation rotation : Rotation.getShuffled(random)) {
                    if (current.parent() < 0) {
                        if (placeEntrance(node, room, rotation)) {
                            return true;
                        }
                    } else {
                        int parent = roomOfNode[current.parent()];
                        for (int source : shuffled(free(parent))) {
                            for (int connector : shuffled(all(room.connectors().size()))) {
                                if (++steps > STEPS_PER_ATTEMPT) {
                                    return false;
                                }
                                if (attach(node, parent, source, room, rotation, connector)) {
                                    return true;
                                }
                            }
                        }
                    }
                    if (steps > STEPS_PER_ATTEMPT) {
                        return false;
                    }
                }
            }
            return false;
        }

        private boolean placeEntrance(int node, DungeonRoom room, Rotation rotation) {
            steps++;
            BoundingBox box = room.box(BlockPos.ZERO, rotation);
            if (!inside(box)) {
                return false;
            }
            add(node, new PlannedRoom(room, Slot.ENTRANCE, BlockPos.ZERO, rotation, box));
            if (placeFrom(node + 1)) {
                return true;
            }
            removeLast();
            return false;
        }

        private boolean attach(int node, int parent, int source, DungeonRoom room, Rotation rotation, int connector) {
            Optional<PlannedRoom> fitted =
                    fit(placed.get(parent).connector(source), room, graph.node(node).slot(), rotation, connector);
            if (fitted.isEmpty()) {
                return false;
            }
            int index = add(node, fitted.get());
            link(parent, source, index, connector);
            if (placeFrom(node + 1)) {
                return true;
            }
            RoomLink link = links.removeLast();
            used.get(link.room())[link.connector()] = false;
            removeLast();
            return false;
        }

        /**
         * The room turned by {@code rotation} and moved so that its connector {@code connector} takes {@code source},
         * if the connectors meet by the jigsaw rules, the room lies inside the limits and it overlaps no placed room.
         */
        private Optional<PlannedRoom> fit(Connector source, DungeonRoom room, Slot slot, Rotation rotation,
                int connector) {
            Connector turned = room.connectors().get(connector).placed(BlockPos.ZERO, rotation);
            if (!source.takes(turned)) {
                return Optional.empty();
            }
            BlockPos offset = source.pos().relative(source.front()).subtract(turned.pos());
            BoundingBox box = room.box(offset, rotation);
            if (!inside(box) || overlaps(box)) {
                return Optional.empty();
            }
            return Optional.of(new PlannedRoom(room, slot, offset, rotation, box));
        }

        /** Closes every connector still free, by a loop or a dead end; on failure leaves the layout as it was. */
        private boolean closeFreeConnectors() {
            int roomsBefore = placed.size();
            int linksBefore = links.size();
            List<int[]> marked = new ArrayList<>();
            for (int room = 0; room < roomsBefore; room++) {
                boolean[] flags = used.get(room);
                for (int connector = 0; connector < flags.length; connector++) {
                    if (flags[connector] || closeLoop(room, connector, marked) || cap(room, connector, marked)) {
                        continue;
                    }
                    for (int[] mark : marked) {
                        used.get(mark[0])[mark[1]] = false;
                    }
                    while (placed.size() > roomsBefore) {
                        placed.removeLast();
                        used.removeLast();
                    }
                    while (links.size() > linksBefore) {
                        links.removeLast();
                    }
                    return false;
                }
            }
            return true;
        }

        /** Links the connector to a free connector of another room right in front of it, if each takes the other. */
        private boolean closeLoop(int room, int connector, List<int[]> marked) {
            Connector source = placed.get(room).connector(connector);
            BlockPos facing = source.pos().relative(source.front());
            for (int other = 0; other < placed.size(); other++) {
                PlannedRoom candidate = placed.get(other);
                if (other == room || !candidate.box().isInside(facing)) {
                    continue;
                }
                boolean[] flags = used.get(other);
                for (int index = 0; index < flags.length; index++) {
                    Connector target = candidate.connector(index);
                    if (!flags[index] && target.pos().equals(facing) && source.takes(target) && target.takes(source)) {
                        flags[index] = true;
                        used.get(room)[connector] = true;
                        marked.add(new int[] {other, index});
                        marked.add(new int[] {room, connector});
                        links.add(new RoomLink(room, connector, other, index));
                        return true;
                    }
                }
            }
            return false;
        }

        /** Closes the connector with a dead end that fits there. */
        private boolean cap(int room, int connector, List<int[]> marked) {
            Connector source = placed.get(room).connector(connector);
            for (DungeonRoom deadEnd : byWeight(candidates(Slot.CAP, 1))) {
                for (Rotation rotation : Rotation.getShuffled(random)) {
                    steps++;
                    Optional<PlannedRoom> fitted = fit(source, deadEnd, Slot.CAP, rotation, 0);
                    if (fitted.isPresent()) {
                        placed.add(fitted.get());
                        used.add(new boolean[] {true});
                        used.get(room)[connector] = true;
                        marked.add(new int[] {room, connector});
                        links.add(new RoomLink(room, connector, placed.size() - 1, 0));
                        return true;
                    }
                }
            }
            return false;
        }

        /** The rooms that can take a slot whose room needs {@code needed} connectors. */
        private List<DungeonRoom> candidates(Slot slot, int needed) {
            List<DungeonRoom> fitting = new ArrayList<>();
            for (DungeonRoom room : rooms) {
                int connectors = room.connectors().size();
                boolean fits = switch (slot) {
                    case ENTRANCE -> room.role() == RoomRole.ENTRANCE && connectors >= Math.max(1, needed);
                    case PATH, BRANCH -> room.role().passesThrough() && connectors >= needed;
                    case ENDING -> room.role() == ending && connectors >= needed;
                    case BRANCH_END, CAP -> room.role().closesBranches() && room.deadEnd();
                };
                if (fits) {
                    fitting.add(room);
                }
            }
            return fitting;
        }

        /** The rooms in a random order in which a heavier room tends to come first (Efraimidis and Spirakis). */
        private List<DungeonRoom> byWeight(List<DungeonRoom> candidates) {
            List<Keyed> keyed = new ArrayList<>(candidates.size());
            for (int i = 0; i < candidates.size(); i++) {
                DungeonRoom room = candidates.get(i);
                keyed.add(new Keyed(Math.pow(random.nextDouble(), 1.0 / room.weight()), i, room));
            }
            keyed.sort(Comparator.comparingDouble(Keyed::key).reversed().thenComparingInt(Keyed::index));
            return keyed.stream().map(Keyed::room).toList();
        }

        private record Keyed(double key, int index, DungeonRoom room) {
        }

        private List<Integer> free(int room) {
            boolean[] flags = used.get(room);
            List<Integer> free = new ArrayList<>();
            for (int connector = 0; connector < flags.length; connector++) {
                if (!flags[connector]) {
                    free.add(connector);
                }
            }
            return free;
        }

        private static List<Integer> all(int count) {
            List<Integer> indices = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                indices.add(i);
            }
            return indices;
        }

        /** Fisher-Yates, in place. */
        private List<Integer> shuffled(List<Integer> values) {
            for (int i = values.size() - 1; i > 0; i--) {
                int j = random.nextInt(i + 1);
                values.set(i, values.set(j, values.get(i)));
            }
            return values;
        }

        private int add(int node, PlannedRoom room) {
            placed.add(room);
            used.add(new boolean[room.room().connectors().size()]);
            roomOfNode[node] = placed.size() - 1;
            return placed.size() - 1;
        }

        private void removeLast() {
            placed.removeLast();
            used.removeLast();
        }

        private void link(int room, int connector, int otherRoom, int otherConnector) {
            used.get(room)[connector] = true;
            used.get(otherRoom)[otherConnector] = true;
            links.add(new RoomLink(room, connector, otherRoom, otherConnector));
        }

        private boolean inside(BoundingBox box) {
            return box.minX() >= limits.minX() && box.minY() >= limits.minY() && box.minZ() >= limits.minZ()
                    && box.maxX() <= limits.maxX() && box.maxY() <= limits.maxY() && box.maxZ() <= limits.maxZ();
        }

        private boolean overlaps(BoundingBox box) {
            for (PlannedRoom room : placed) {
                if (room.box().intersects(box)) {
                    return true;
                }
            }
            return false;
        }
    }
}
