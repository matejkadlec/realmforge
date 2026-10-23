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
 * vanilla's jigsaw rules ({@link Connector#takes}), inside the limits and overlapping no placed room. A room the layout
 * already holds as often as its {@link DungeonRoom#maxPerLayout} allows is no candidate, nor is a {@link
 * DungeonRoom#nearStart} room anywhere but next to the entrance or the first room of the main path. A room with an
 * {@link DungeonRoom#entry} takes its parent through that connector only (RLM-362). With a {@code special_gap}
 * (RLM-364), a plain node takes only plain rooms, and a special node special rooms first, then plain ones where no
 * special one fits; a {@code near_start} room takes either. A placement must also leave every doorway that is still
 * free closable: the space in front of it (the wall's box when the pool has a wall, else the block in front) stays
 * clear of other rooms and of the other free doorways' spaces unless a connector there takes it, and a room has no more
 * connectors that no dead end or wall of the pool can close than rooms will hang off it, which take those connectors
 * first. So the search turns back where a room boxes a doorway in rather than after the last node (RLM-363). A node
 * with no such placement sends the search back to the node before it. Once every node has its room, each connector
 * still free meets a free connector of a neighbour right in front of it, which closes a loop unless the loop would lead
 * into a room with an entry from another side, or gets a dead end or a wall ({@link Slot#CAP}; with a {@code
 * special_gap} a plain dead end); a connector neither can close sends the search back too. An attempt that runs out of
 * steps gives up, and the next one draws a new graph from the same random source.
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
            Layout layout = new Layout(rooms, rules, RoomGraph.draw(rules, random), random, limits);
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
        /** Which rooms may close a free connector: plain ones and walls with a {@code special_gap}, else any. */
        private final RoomGraph.Kind caps;
        /** The pool's wall, if it has one: the least a free doorway needs in front of it to be closed. */
        private final Optional<DungeonRoom> wall;
        /** Per room of the pool, which of its connectors a dead end or a wall that may close connectors can take. */
        private final java.util.Map<DungeonRoom, boolean[]> cappable = new java.util.HashMap<>();
        private final RoomGraph graph;
        private final RandomSource random;
        private final BoundingBox limits;
        private final List<PlannedRoom> placed = new ArrayList<>();
        /** Per placed room, which of its connectors already meet another one. */
        private final List<boolean[]> used = new ArrayList<>();
        /** Per placed room, the placed room it hangs off ({@code -1} for the entrance): the tree before any loop. */
        private final List<Integer> parents = new ArrayList<>();
        /** Per placed room and connector, the space that what closes the connector takes ({@link #closingSpace}). */
        private final List<BoundingBox[]> spaces = new ArrayList<>();
        private final List<RoomLink> links = new ArrayList<>();
        private final int[] roomOfNode;
        private int steps;

        private Layout(List<DungeonRoom> rooms, LayoutRules rules, RoomGraph graph, RandomSource random,
                BoundingBox limits) {
            this.rooms = rooms;
            this.ending = rules.ending();
            this.caps = rules.specialGap().isPresent() ? RoomGraph.Kind.PLAIN : RoomGraph.Kind.ANY;
            this.wall = rooms.stream().filter(room -> room.role() == RoomRole.WALL).findFirst();
            for (DungeonRoom room : rooms) {
                boolean[] flags = new boolean[room.connectors().size()];
                for (int index = 0; index < flags.length; index++) {
                    flags[index] = cappable(room.connectors().get(index));
                }
                cappable.put(room, flags);
            }
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
            int host = current.parent() < 0 ? -1 : roomOfNode[current.parent()];
            for (DungeonRoom room : candidates(current.slot(), current.kind(), graph.connectorsNeeded(node), host)) {
                for (Rotation rotation : Rotation.getShuffled(random)) {
                    if (current.parent() < 0) {
                        if (placeEntrance(node, room, rotation)) {
                            return true;
                        }
                    } else {
                        int parent = roomOfNode[current.parent()];
                        for (int source : shuffled(sources(parent))) {
                            for (int connector : room.entry().map(List::of)
                                    .orElseGet(() -> shuffled(all(room.connectors().size())))) {
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
            PlannedRoom entrance = new PlannedRoom(room, Slot.ENTRANCE, BlockPos.ZERO, rotation, box);
            if (!inside(box) || !keepsDoorsClosable(entrance, -1, -1, -1, graph.connectorsNeeded(node))) {
                return false;
            }
            add(node, entrance, -1);
            if (placeFrom(node + 1)) {
                return true;
            }
            removeLast();
            return false;
        }

        private boolean attach(int node, int parent, int source, DungeonRoom room, Rotation rotation, int connector) {
            Optional<PlannedRoom> fitted = fit(parent, source, room, graph.node(node).slot(), rotation, connector,
                    graph.connectorsNeeded(node) - 1);
            if (fitted.isEmpty()) {
                return false;
            }
            int index = add(node, fitted.get(), parent);
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
         * The room turned by {@code rotation} and moved so that its connector {@code connector} takes connector
         * {@code source} of the placed room {@code host}, if the connectors meet by the jigsaw rules, the room lies
         * inside the limits, it overlaps no placed room and every doorway left free stays closable once
         * {@code children} rooms hang off it.
         */
        private Optional<PlannedRoom> fit(int host, int source, DungeonRoom room, Slot slot, Rotation rotation,
                int connector, int children) {
            Connector from = placed.get(host).connector(source);
            Connector turned = room.connectors().get(connector).placed(BlockPos.ZERO, rotation);
            if (!from.takes(turned)) {
                return Optional.empty();
            }
            BlockPos offset = from.pos().relative(from.front()).subtract(turned.pos());
            BoundingBox box = room.box(offset, rotation);
            if (!inside(box) || overlaps(box)) {
                return Optional.empty();
            }
            PlannedRoom fitted = new PlannedRoom(room, slot, offset, rotation, box);
            if (!keepsDoorsClosable(fitted, connector, host, source, children)) {
                return Optional.empty();
            }
            return Optional.of(fitted);
        }

        /**
         * Whether every doorway still free stays closable once {@code candidate} stands, joined through its connector
         * {@code attached} to connector {@code source} of the placed room {@code host} ({@code -1}: the entrance), with
         * {@code children} rooms to hang off it: the space in front of each free connector of the placed rooms lies
         * clear of the candidate, the space in front of each other connector of the candidate lies inside the limits
         * and clear of the placed rooms and of their free doorways' spaces, unless a free connector there takes it,
         * which can close a loop, and the candidate has no more connectors that no dead end or wall can take than
         * children.
         */
        private boolean keepsDoorsClosable(PlannedRoom candidate, int attached, int host, int source, int children) {
            boolean[] closable = cappable.get(candidate.room());
            int uncappable = 0;
            for (int index = 0; index < closable.length; index++) {
                uncappable += index != attached && !closable[index] ? 1 : 0;
            }
            if (uncappable > children) {
                return false;
            }
            for (int room = 0; room < placed.size(); room++) {
                boolean[] flags = used.get(room);
                BoundingBox[] roomSpaces = spaces.get(room);
                for (int index = 0; index < flags.length; index++) {
                    if (!flags[index] && !(room == host && index == source)
                            && candidate.box().intersects(roomSpaces[index])
                            && !meets(placed.get(room).connector(index), candidate, null, attached)) {
                        return false;
                    }
                }
            }
            for (int index = 0; index < candidate.room().connectors().size(); index++) {
                if (index == attached) {
                    continue;
                }
                Connector free = candidate.connector(index);
                BoundingBox space = closingSpace(free);
                if (!inside(space)) {
                    return false;
                }
                for (int room = 0; room < placed.size(); room++) {
                    PlannedRoom other = placed.get(room);
                    boolean[] flags = used.get(room);
                    if (other.box().intersects(space) && !meets(free, other, flags, -1)) {
                        return false;
                    }
                    BoundingBox[] roomSpaces = spaces.get(room);
                    for (int doorway = 0; doorway < flags.length; doorway++) {
                        if (!flags[doorway] && !(room == host && doorway == source)
                                && roomSpaces[doorway].intersects(space) && !meets(free, other, flags, -1)) {
                            return false;
                        }
                    }
                }
            }
            return true;
        }

        /**
         * Whether a dead end or a wall that may close connectors can take {@code connector}, turned any way: what
         * {@link #cap} could close it with, its limits and place aside.
         */
        private boolean cappable(Connector connector) {
            for (DungeonRoom deadEnd : rooms) {
                RoomRole role = deadEnd.role();
                boolean closes = role == RoomRole.WALL
                        || role.closesBranches() && (caps == RoomGraph.Kind.ANY || role.plain() || deadEnd.nearStart());
                if (!closes || !deadEnd.deadEnd()) {
                    continue;
                }
                for (Rotation rotation : Rotation.values()) {
                    if (connector.takes(deadEnd.connectors().getFirst().placed(BlockPos.ZERO, rotation))) {
                        return true;
                    }
                }
            }
            return false;
        }

        /**
         * Whether {@code room} has a free connector right in front of {@code free} that takes it and that it takes:
         * the two can close a loop. {@code flags} marks the room's connectors in use ({@code null}: none), and
         * {@code skipped} is one more connector that is not free.
         */
        private static boolean meets(Connector free, PlannedRoom room, boolean[] flags, int skipped) {
            BlockPos facing = free.pos().relative(free.front());
            for (int index = 0; index < room.room().connectors().size(); index++) {
                if (index == skipped || flags != null && flags[index]) {
                    continue;
                }
                Connector other = room.connector(index);
                if (other.pos().equals(facing) && free.takes(other) && other.takes(free)) {
                    return true;
                }
            }
            return false;
        }

        /**
         * The space that closing {@code free} takes at the least: the wall's box against it, when the pool has a
         * wall that takes it, else the block in front of it, which any room that closes it holds.
         */
        private BoundingBox closingSpace(Connector free) {
            BlockPos front = free.pos().relative(free.front());
            if (wall.isPresent()) {
                for (Rotation rotation : Rotation.values()) {
                    Connector turned = wall.get().connectors().getFirst().placed(BlockPos.ZERO, rotation);
                    if (free.takes(turned)) {
                        return wall.get().box(front.subtract(turned.pos()), rotation);
                    }
                }
            }
            return new BoundingBox(front);
        }

        /**
         * Closes every connector still free, by a loop, a dead end or a wall; on failure leaves the layout as it was.
         */
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
                        removeLast();
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
                if (other == room || !candidate.box().isInside(facing) || bypassesEntry(room, other)) {
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

        /** Closes the connector with a dead end or a wall that fits there. */
        private boolean cap(int room, int connector, List<int[]> marked) {
            for (DungeonRoom deadEnd : candidates(Slot.CAP, caps, 1, room)) {
                for (Rotation rotation : Rotation.getShuffled(random)) {
                    steps++;
                    Optional<PlannedRoom> fitted = fit(room, connector, deadEnd, Slot.CAP, rotation, 0, 0);
                    if (fitted.isPresent()) {
                        placed.add(fitted.get());
                        used.add(new boolean[] {true});
                        parents.add(room);
                        spaces.add(new BoundingBox[] {closingSpace(fitted.get().connector(0))});
                        used.get(room)[connector] = true;
                        marked.add(new int[] {room, connector});
                        links.add(new RoomLink(room, connector, placed.size() - 1, 0));
                        return true;
                    }
                }
            }
            return false;
        }

        /**
         * The rooms that can take a slot of {@code kind} whose room needs {@code needed} connectors and hangs off the
         * placed room {@code host} ({@code -1} for the entrance), in the order to try them: by weight, and for a
         * special node the plain rooms after the special ones.
         */
        private List<DungeonRoom> candidates(Slot slot, RoomGraph.Kind kind, int needed, int host) {
            List<DungeonRoom> first = new ArrayList<>();
            List<DungeonRoom> fallback = new ArrayList<>();
            for (DungeonRoom room : rooms) {
                if (!fits(room, slot, needed) || !belowLimit(room) || room.nearStart() && !atStart(host)) {
                    continue;
                }
                RoomRole role = room.role();
                if (kind == RoomGraph.Kind.ANY || slot == Slot.ENDING || room.nearStart() || role == RoomRole.WALL
                        || (kind == RoomGraph.Kind.SPECIAL ? role.special() : role.plain())) {
                    first.add(room);
                } else if (kind == RoomGraph.Kind.SPECIAL && role.plain()) {
                    fallback.add(room);
                }
            }
            List<DungeonRoom> ordered = new ArrayList<>(byWeight(first));
            ordered.addAll(byWeight(fallback));
            return ordered;
        }

        private boolean fits(DungeonRoom room, Slot slot, int needed) {
            int connectors = room.connectors().size();
            return switch (slot) {
                case ENTRANCE -> room.role() == RoomRole.ENTRANCE && connectors >= Math.max(1, needed);
                case PATH, BRANCH -> room.role().passesThrough() && connectors >= needed;
                case ENDING -> room.role() == ending && connectors >= needed;
                case BRANCH_END -> room.role().closesBranches() && room.deadEnd();
                case CAP -> (room.role().closesBranches() || room.role() == RoomRole.WALL) && room.deadEnd();
            };
        }

        /**
         * Whether a loop between the placed rooms {@code a} and {@code b} would lead into a room with an entry from
         * another side (RLM-362): a room with an entry on the way from either of them up to the room both hang
         * from. Every such room was entered through its entry, so the loop would be a second way in.
         */
        private boolean bypassesEntry(int a, int b) {
            List<Integer> above = new ArrayList<>();
            for (int room = a; room >= 0; room = parents.get(room)) {
                above.add(room);
            }
            int common = b;
            while (!above.contains(common)) {
                common = parents.get(common);
            }
            for (int start : new int[] {a, b}) {
                for (int room = start; room != common; room = parents.get(room)) {
                    if (placed.get(room).room().entry().isPresent()) {
                        return true;
                    }
                }
            }
            return false;
        }

        private boolean belowLimit(DungeonRoom room) {
            return room.maxPerLayout().map(max -> placed.stream().filter(p -> p.room().equals(room)).count() < max)
                    .orElse(true);
        }

        /** Whether a room hanging off the placed room {@code host} is at the dungeon's start. */
        private boolean atStart(int host) {
            // The first room of the main path is node 1, which every graph has and which hangs off the entrance.
            return host <= 0 || placed.size() > 1 && host == roomOfNode[1];
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

        /**
         * The free connectors of the placed room {@code room} a room hanging off it may take: those no dead end or
         * wall can close while it has any, which its other rooms could leave open for good.
         */
        private List<Integer> sources(int room) {
            List<Integer> free = free(room);
            boolean[] closable = cappable.get(placed.get(room).room());
            List<Integer> uncappable = free.stream().filter(index -> !closable[index]).toList();
            return uncappable.isEmpty() ? free : new ArrayList<>(uncappable);
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

        private int add(int node, PlannedRoom room, int parent) {
            placed.add(room);
            used.add(new boolean[room.room().connectors().size()]);
            parents.add(parent);
            BoundingBox[] roomSpaces = new BoundingBox[room.room().connectors().size()];
            for (int index = 0; index < roomSpaces.length; index++) {
                roomSpaces[index] = closingSpace(room.connector(index));
            }
            spaces.add(roomSpaces);
            roomOfNode[node] = placed.size() - 1;
            return placed.size() - 1;
        }

        private void removeLast() {
            placed.removeLast();
            used.removeLast();
            parents.removeLast();
            spaces.removeLast();
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
