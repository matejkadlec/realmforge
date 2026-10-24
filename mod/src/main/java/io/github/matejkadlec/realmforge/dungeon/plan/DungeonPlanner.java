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
 * {@link DungeonRoom#entry} takes its parent through that connector only (RLM-362). With a {@code pacing} (RLM-364), a
 * node takes rooms of the {@link Pace} it asks for, the room {@link Deal} dealt it first and the rest in the order of
 * {@code Layout#key}; the first {@link #STRICT_ATTEMPTS} attempts take nothing else and no room near a copy of it, and
 * later ones may fall back where nothing else leads to a whole layout (a fight to a feature, then a corridor; a feature
 * to a corridor; a branch's treasure to another dead end); a {@code near_start} room takes any node at the start. A
 * placement must also leave every doorway that is still free closable: the space in front of it (the wall's box when
 * the pool has a wall, else the block in front) stays clear of other rooms and of the other free doorways' spaces
 * unless a connector there takes it, and a room has no more connectors that no dead end or wall of the pool can close
 * than rooms will hang off it, which take those connectors first. So the search turns back where a room boxes a doorway
 * in rather than after the last node (RLM-363). A node with no such placement sends the search back to the node before
 * it. Once every node has its room, each connector still free meets a free connector of a neighbour right in front of
 * it, which closes a loop unless the loop would lead into a room with an entry from another side, or gets a dead end or
 * a wall ({@link Slot#CAP}; with a {@code pacing} a wall or a corridor's dead end); a connector neither can close sends
 * the search back too. An attempt that runs out of steps gives up, and the next one draws a new graph from the same
 * random source.
 */
public final class DungeonPlanner {
    /** Attempts per seed before planning fails. */
    static final int ATTEMPTS = 64;
    /** Placements an attempt may try before it gives up. */
    static final int STEPS_PER_ATTEMPT = 5_000;
    /**
     * With a pacing, the attempts that place only rooms of the pace each node asks for, and no room near a copy of it
     * ({@code repeat}), before the later ones may (RLM-364): a fallback or a near copy is taken where it is needed, not
     * where a few rooms before would do better.
     */
    static final int STRICT_ATTEMPTS = 8;
    /** As many copies of a room without a limit as a layout could ever take. */
    static final int UNLIMITED = 1024;
    /**
     * With a pacing, the most rooms of the layout's tree a loop may lead around (RLM-364): a loop between rooms
     * farther apart would let a player skip a stretch of the main path, or walk it backwards.
     */
    static final int LOOP_REACH = 4;

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
        int fights = fights(rooms);
        for (int attempt = 1; attempt <= ATTEMPTS; attempt++) {
            boolean strict = rules.pacing().isPresent() && attempt <= STRICT_ATTEMPTS;
            Layout layout = new Layout(rooms, rules, RoomGraph.draw(rules, fights, random), random, limits, strict);
            if (layout.placeFrom(0)) {
                return new DungeonPlan(seed, attempt, layout.placed, layout.links);
            }
        }
        throw new PlanningException("no layout of " + rooms.size() + " rooms fits the rules within " + ATTEMPTS
                + " attempts for seed " + seed);
    }

    /**
     * The fights the pool can supply to one layout's main path: its fights with two connectors or more, each as often
     * as its {@link DungeonRoom#maxPerLayout} allows ({@link #UNLIMITED} for one without a limit).
     */
    static int fights(List<DungeonRoom> rooms) {
        int supply = 0;
        for (DungeonRoom room : rooms) {
            if (room.role().passesThrough() && room.connectors().size() >= 2
                    && room.pace().equals(Optional.of(Pace.FIGHT))) {
                supply = Math.min(UNLIMITED, supply + room.maxPerLayout().orElse(UNLIMITED));
            }
        }
        return supply;
    }

    /** Step 2 for one graph: a depth-first search that undoes a placement whose followers cannot be placed. */
    private static final class Layout {
        private final List<DungeonRoom> rooms;
        private final RoomRole ending;
        /** The layout's rhythm (RLM-364), if it has one. */
        private final Optional<LayoutRules.Pacing> pacing;
        /** The pool's wall, if it has one: the least a free doorway needs in front of it to be closed. */
        private final Optional<DungeonRoom> wall;
        /**
         * With a pacing, the room kept for the ending: the one of its role with the most chests, which a branch takes
         * only where nothing else ends it.
         */
        private final Optional<DungeonRoom> reserved;
        /** With a pacing, the rooms dealt to the nodes, which they try first ({@link Deal}). */
        private final Deal deal;
        /** Per node, whether it is a room of the main path right before a fight. */
        private final boolean[] beforeFight;
        /** The node of the main path's last room, which the ending hangs off ({@code -1}: none). */
        private final int guard;
        /** Per room of the pool, which of its connectors a dead end or a wall that may close connectors can take. */
        private final java.util.Map<DungeonRoom, boolean[]> cappable = new java.util.HashMap<>();
        /** Whether the nodes take no fallback ({@link #STRICT_ATTEMPTS}). */
        private final boolean strict;
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
        /** Per placed room, its distance from the entrance in rooms. */
        private final List<Integer> depths = new ArrayList<>();
        private final List<RoomLink> links = new ArrayList<>();
        private final int[] roomOfNode;
        private int steps;

        private Layout(List<DungeonRoom> rooms, LayoutRules rules, RoomGraph graph, RandomSource random,
                BoundingBox limits, boolean strict) {
            this.strict = strict;
            this.rooms = rooms;
            this.ending = rules.ending();
            this.pacing = rules.pacing();
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
            this.reserved = pacing.isEmpty() ? Optional.empty() : byWeight(rooms.stream()
                    .filter(room -> room.role() == ending && !room.nearStart()).toList()).stream()
                    .max(Comparator.comparingInt(DungeonRoom::chests));
            this.deal = pacing.map(rhythm -> Deal.of(graph, rooms, rhythm, reserved, random)).orElse(Deal.none());
            int last = -1;
            for (int node = 0; node < graph.size(); node++) {
                last = graph.node(node).slot() == Slot.ENDING ? graph.node(node).parent() : last;
            }
            this.guard = last;
            this.beforeFight = new boolean[graph.size()];
            int previous = -1;
            for (int node = 0; node < graph.size(); node++) {
                if (graph.node(node).slot() == Slot.PATH) {
                    if (previous >= 0 && graph.node(node).pace().equals(Optional.of(Pace.FIGHT))) {
                        beforeFight[previous] = true;
                    }
                    previous = node;
                }
            }
        }

        /** Places node {@code node} and every node after it, or leaves the layout as it was and returns false. */
        private boolean placeFrom(int node) {
            if (node == graph.size()) {
                return closeFreeConnectors();
            }
            RoomGraph.Node current = graph.node(node);
            int host = current.parent() < 0 ? -1 : roomOfNode[current.parent()];
            // A room the same shape as one that found no layout here would find none either: the next try is the next
            // shape, so a fallback or a near copy only comes where no room of the first choice's shape fits.
            java.util.Set<List<Object>> tried = new java.util.HashSet<>();
            for (DungeonRoom room : candidates(node, current.slot(), current.pace(), current.climax(),
                    graph.connectorsNeeded(node), host, current.depth())) {
                if (!tried.add(List.of(room.size(), room.connectors(), room.entry()))) {
                    continue;
                }
                for (Rotation rotation : Rotation.getShuffled(random)) {
                    if (current.parent() < 0) {
                        if (placeEntrance(node, room, rotation)) {
                            return true;
                        }
                    } else {
                        int parent = roomOfNode[current.parent()];
                        for (int source : sources(parent, current.parent(), current.slot())) {
                            for (int connector : connectors(room, node)) {
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
         * and clear of the placed rooms and of their free doorways' spaces, unless a free connector there takes it
         * and a loop may join the two rooms ({@link #loops}), and the candidate has no more connectors that no dead
         * end or wall can take than children.
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
                            && !(meets(placed.get(room).connector(index), candidate, null, attached)
                                    && loops(room, placed.size(), host, candidate.room()))) {
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
                    boolean loops = meets(free, other, flags, -1) && loops(room, placed.size(), host, candidate.room());
                    if (other.box().intersects(space) && !loops) {
                        return false;
                    }
                    BoundingBox[] roomSpaces = spaces.get(room);
                    for (int doorway = 0; doorway < flags.length; doorway++) {
                        if (!flags[doorway] && !(room == host && doorway == source)
                                && roomSpaces[doorway].intersects(space) && !loops) {
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
                if (!caps(deadEnd) || !deadEnd.deadEnd()) {
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
                if (other == room || !candidate.box().isInside(facing) || !loops(room, other, -1, null)) {
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
            for (DungeonRoom deadEnd : candidates(-1, Slot.CAP, Optional.empty(), false, 1, room,
                    depths.get(room) + 1)) {
                for (Rotation rotation : Rotation.getShuffled(random)) {
                    steps++;
                    Optional<PlannedRoom> fitted = fit(room, connector, deadEnd, Slot.CAP, rotation, 0, 0);
                    if (fitted.isPresent()) {
                        placed.add(fitted.get());
                        used.add(new boolean[] {true});
                        parents.add(room);
                        spaces.add(new BoundingBox[] {closingSpace(fitted.get().connector(0))});
                        depths.add(depths.get(room) + 1);
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
         * The rooms that can take a slot asking for {@code pace} (none: any) whose room needs {@code needed}
         * connectors, hangs off the placed room {@code host} ({@code -1} for the entrance) and lies {@code depth}
         * rooms from the entrance, in the order to try them: by {@link #key}, then by weight; a strict layout leaves
         * out rooms near a copy of them, but at the guard, and corridors with more connectors than needed (a crossing
         * where a corridor would do), but a room with a shaft (the hall the entrance's shafts end in). A {@code climax}
         * is a fight that ends a section of the main path.
         */
        private List<DungeonRoom> candidates(int node, Slot slot, Optional<Pace> pace, boolean climax, int needed,
                int host, int depth) {
            java.util.TreeMap<int[], List<DungeonRoom>> ranked = new java.util.TreeMap<>(java.util.Arrays::compare);
            for (DungeonRoom room : rooms) {
                if (!fits(room, slot, needed) || !belowLimit(room) || room.nearStart() && !atStart(host)) {
                    continue;
                }
                int group = group(slot, pace, room);
                boolean mine = deal.at(node).equals(Optional.of(room));
                boolean extra = !room.nearStart() && room.connectors().size() > Math.max(1, needed)
                        && room.connectors().stream().noneMatch(Connector::vertical);
                if (group < 0 || strict && (corridor(room) ? !mine && extra && slot != Slot.CAP
                        : repeats(room, depth) == 2 && !guards(node))) {
                    continue;
                }
                ranked.computeIfAbsent(key(node, room, group, slot, climax, needed, host, depth),
                        key -> new ArrayList<>()).add(room);
            }
            List<DungeonRoom> ordered = new ArrayList<>();
            ranked.values().forEach(group -> ordered.addAll(byWeight(group)));
            return ordered;
        }

        /**
         * Which group of a slot's candidates {@code room} falls in, tried in order ({@code -1}: none). Without a
         * pacing, all rooms that fit. With one, a node asking for a pace takes the rooms of that pace, then those it
         * falls back to: a fight features, then corridors; a feature corridors; a branch's treasure the treasure rooms
         * but the one kept for the ending and rooms with {@link DungeonRoom#TREASURE_CHESTS} chests, then other dead
         * ends but corridors, the room kept for the ending, and last a corridor's dead end; a strict layout takes no
         * other pace and no plain dead end for a branch. A cap takes a {@code near_start} room at the start, then a
         * wall, then a corridor's dead end. A {@code near_start} room is in the first group of any other node at the
         * start.
         */
        private int group(Slot slot, Optional<Pace> pace, DungeonRoom room) {
            if (pacing.isEmpty() || slot == Slot.ENTRANCE || slot == Slot.ENDING) {
                return 0;
            }
            Optional<Pace> own = room.pace();
            if (slot == Slot.CAP || pace.isEmpty()) {
                if (!caps(room)) {
                    return -1;
                }
                return room.nearStart() ? 0 : room.role() == RoomRole.WALL ? 1 : 2;
            }
            if (room.nearStart()) {
                return 0;
            }
            if (own.isEmpty()) {
                return -1;
            }
            int group = switch (pace.get()) {
                case CORRIDOR -> own.get() == Pace.CORRIDOR ? 0 : -1;
                case FEATURE -> own.get() == Pace.FEATURE ? 0 : own.get() == Pace.CORRIDOR ? 1 : -1;
                case FIGHT -> switch (own.get()) {
                    case FIGHT -> 0;
                    case FEATURE -> 1;
                    case CORRIDOR -> 2;
                    case TREASURE -> -1;
                };
                case TREASURE -> {
                    if (own.get() == Pace.TREASURE && reserved.equals(Optional.of(room))) {
                        yield 3;
                    }
                    if (own.get() == Pace.TREASURE || room.chests() >= DungeonRoom.TREASURE_CHESTS) {
                        yield 0;
                    }
                    yield own.get() == Pace.CORRIDOR ? 4 : 2;
                }
            };
            boolean fallback = pace.get() == Pace.TREASURE ? group == 2 || group == 4 : group > 0;
            return strict && fallback ? -1 : group;
        }

        /**
         * The order of a candidate in its {@code group}, compared element by element. With a pacing, a corridor (a
         * wall, a {@code near_start} room) goes by whether it has more connectors than {@code needed}, which a dead end
         * or a wall then closes, then in a branch by whether it is crooked rather than straight, so the branch's end
         * shows from its junction, then by whether it has the shape of {@code host}. Any other room goes first by
         * whether it is the node's dealt room ({@link Deal}), then by whether it is the guard's room before the guard
         * ({@link #takesDealt}), then by whether it is dealt to a later node, then right before a fight by whether it
         * is a hazard or a lock, then by whether a copy lies near ({@link #repeats}); then a climax by the most
         * spawners, another fight by the fewest, and the ending by the most chests; then by how near its copies are,
         * and by connectors left over.
         */
        private int[] key(int node, DungeonRoom room, int group, Slot slot, boolean climax, int needed, int host,
                int depth) {
            if (pacing.isEmpty()) {
                return new int[] {group};
            }
            int over = !room.nearStart() && room.connectors().size() > Math.max(1, needed) ? 1 : 0;
            if (corridor(room)) {
                int crooked = slot == Slot.BRANCH && !straight(room) ? 1 : 0;
                int same = host >= 0 && alike(placed.get(host).room(), room) ? 1 : 0;
                return new int[] {group, over, crooked, same};
            }
            int mine = deal.at(node).equals(Optional.of(room)) ? 0 : 1;
            int later = deal.later(room, node) ? 1 : 0;
            int kept = takesDealt(room, node) ? 1 : 0;
            int risky = node >= 0 && beforeFight[node] && (room.hazard() || room.entry().isPresent()) ? 1 : 0;
            int repeats = repeats(room, depth);
            int richness = climax ? -room.spawners() : slot == Slot.ENDING ? -room.chests() : 0;
            return new int[] {group, mine, kept, later, risky, repeats == 2 ? 1 : 0, richness, repeats, over};
        }

        /**
         * Whether {@code room} is the room dealt to the guard and node {@code node} comes before it: the run's last
         * fight keeps the heaviest room (RLM-364), so other nodes take it after other rooms.
         */
        private boolean takesDealt(DungeonRoom room, int node) {
            return node >= 0 && node < guard && deal.at(guard).equals(Optional.of(room));
        }

        /** Whether node {@code node} is the main path's last room, which the ending hangs off: its guard. */
        private boolean guards(int node) {
            return node >= 0 && node == guard;
        }

        /** Whether the planner orders {@code room} as a corridor: one, a wall or a {@code near_start} room. */
        private static boolean corridor(DungeonRoom room) {
            return room.nearStart() || room.role() == RoomRole.WALL || room.pace().equals(Optional.of(Pace.CORRIDOR));
        }

        /**
         * How near the layout's copies of {@code room} lie to a room {@code depth} rooms from the entrance: 0 for
         * none, 1 when each lies at least {@code repeat} rooms nearer the entrance or farther from it, 2 otherwise.
         * A lock (a room with an entry) counts the other locks as copies, so locks come spread out.
         */
        private int repeats(DungeonRoom room, int depth) {
            int repeat = pacing.map(LayoutRules.Pacing::repeat).orElse(0);
            boolean copied = false;
            boolean near = false;
            for (int index = 0; index < placed.size(); index++) {
                DungeonRoom other = placed.get(index).room();
                boolean copy = other.equals(room);
                if (copy || room.entry().isPresent() && other.entry().isPresent()) {
                    copied |= copy;
                    near |= Math.abs(depths.get(index) - depth) < repeat;
                }
            }
            return near ? 2 : copied ? 1 : 0;
        }

        /**
         * Whether {@code room} may close a free connector: a wall, or a room that may close branches; with a pacing
         * only a corridor's dead end or a {@code near_start} room, so no cap adds a fight, a feature or treasure the
         * rhythm does not plan, but the room meant for the start may take a side of it.
         */
        private boolean caps(DungeonRoom room) {
            return room.role() == RoomRole.WALL || room.role().closesBranches()
                    && (pacing.isEmpty() || room.nearStart() || room.pace().equals(Optional.of(Pace.CORRIDOR)));
        }

        /** Whether two rooms look alike: the same size and the same connectors, whatever their blocks. */
        private static boolean alike(DungeonRoom a, DungeonRoom b) {
            return a.size().equals(b.size()) && a.connectors().equals(b.connectors());
        }

        /** Whether the room has two connectors on opposite sides: a player sees through it. */
        private static boolean straight(DungeonRoom room) {
            List<Connector> connectors = room.connectors();
            return connectors.size() == 2 && connectors.get(0).front() == connectors.get(1).front().getOpposite();
        }

        private boolean fits(DungeonRoom room, Slot slot, int needed) {
            int connectors = room.connectors().size();
            return switch (slot) {
                case ENTRANCE -> room.role() == RoomRole.ENTRANCE && connectors >= Math.max(1, needed);
                case PATH, BRANCH -> room.role().passesThrough() && connectors >= needed;
                case ENDING -> room.role() == ending && connectors >= needed;
                case BRANCH_END -> room.role().closesBranches() && (room.deadEnd() || pacing.isPresent()
                        && room.role().passesThrough() && room.chests() >= DungeonRoom.TREASURE_CHESTS);
                case CAP -> (room.role().closesBranches() || room.role() == RoomRole.WALL) && room.deadEnd();
            };
        }

        /**
         * Whether a loop may join the rooms {@code a} and {@code b}: none leads into a room with an entry from another
         * side (RLM-362), as a room with an entry on the way from either up to the room both hang from was entered
         * through its entry, and with a pacing the loop leads around at most {@link #LOOP_REACH} rooms of the tree and
         * past no fight, so it skips no stretch of the main path (RLM-364). {@code b} may be {@code placed.size()}:
         * {@code pending}, a candidate hanging off the placed room {@code pendingParent}.
         */
        private boolean loops(int a, int b, int pendingParent, DungeonRoom pending) {
            List<Integer> up = chain(a, pendingParent);
            List<Integer> across = chain(b, pendingParent);
            int common = 0;
            while (!up.contains(across.get(common))) {
                common++;
            }
            int ups = up.indexOf(across.get(common));
            List<Integer> way = new ArrayList<>(up.subList(0, ups));
            way.addAll(across.subList(0, common));
            for (int room : way) {
                if (roomAt(room, pending).entry().isPresent()) {
                    return false;
                }
            }
            if (pacing.isEmpty()) {
                return true;
            }
            if (ups + common > LOOP_REACH) {
                return false;
            }
            way.add(across.get(common));
            way.remove((Integer) a);
            way.remove((Integer) b);
            return way.stream().noneMatch(room -> roomAt(room, pending).pace().equals(Optional.of(Pace.FIGHT)));
        }

        /** The room and the rooms it hangs from, up to the entrance; {@code placed.size()} hangs off {@code parent}. */
        private List<Integer> chain(int room, int parent) {
            List<Integer> chain = new ArrayList<>();
            for (int at = room; at >= 0; at = at == placed.size() ? parent : parents.get(at)) {
                chain.add(at);
            }
            return chain;
        }

        private DungeonRoom roomAt(int index, DungeonRoom pending) {
            return index == placed.size() ? pending : placed.get(index).room();
        }

        private boolean belowLimit(DungeonRoom room) {
            return room.maxPerLayout().map(max -> placed.stream().filter(p -> p.room().equals(room)).count() < max)
                    .orElse(true);
        }

        /**
         * Whether a room hanging off the placed room {@code host} is at the dungeon's start: off the entrance or the
         * first room of the main path, with a pacing off any room of its approach ({@link RoomGraph#APPROACH}).
         */
        private boolean atStart(int host) {
            int start = pacing.isPresent() ? RoomGraph.APPROACH : 1;
            return host <= 0 || placed.get(host).slot() == Slot.PATH && depths.get(host) <= start;
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
         * The free connectors of the placed room {@code room} (node {@code node}) a room hanging off it in slot
         * {@code slot} may take, in the order to try them: those no dead end or wall can close while it has any, which
         * its other rooms could leave open for good, in a random order. With a pacing, at a junction the main path
         * takes the connector straight across from the one the junction hangs off by first, and a branch takes it
         * last, so the main path runs straight through and the branch leaves to the side (RLM-364).
         */
        private List<Integer> sources(int room, int node, Slot slot) {
            List<Integer> free = free(room);
            boolean[] closable = cappable.get(placed.get(room).room());
            List<Integer> uncappable = free.stream().filter(index -> !closable[index]).toList();
            List<Integer> order = shuffled(uncappable.isEmpty() ? free : new ArrayList<>(uncappable));
            if (pacing.isPresent() && graph.connectorsNeeded(node) >= 3) {
                int across = across(room);
                if (order.remove((Integer) across)) {
                    if (slot == Slot.PATH || slot == Slot.ENDING) {
                        order.addFirst(across);
                    } else {
                        order.addLast(across);
                    }
                }
            }
            return order;
        }

        /**
         * The connectors of {@code room} to try joining the node {@code node}'s parent by, in order: its entry alone if
         * it has one, else all in a random order; with a pacing, a junction tries those with a connector straight
         * across first, so the main path can run straight through it.
         */
        private List<Integer> connectors(DungeonRoom room, int node) {
            if (room.entry().isPresent()) {
                return List.of(room.entry().get());
            }
            List<Integer> order = shuffled(all(room.connectors().size()));
            if (pacing.isPresent() && graph.connectorsNeeded(node) >= 3) {
                List<Connector> connectors = room.connectors();
                order.sort(Comparator.comparingInt(index -> connectors.stream()
                        .anyMatch(other -> other.front() == connectors.get(index).front().getOpposite()) ? 0 : 1));
            }
            return order;
        }

        /**
         * The connector of the placed room {@code room} straight across from the one it hangs off its parent by, on
         * a side ({@code -1}: none, or the entrance).
         */
        private int across(int room) {
            for (RoomLink link : links) {
                if (link.otherRoom() == room && link.room() == parents.get(room)) {
                    PlannedRoom planned = placed.get(room);
                    Connector by = planned.connector(link.otherConnector());
                    for (int index = 0; index < planned.room().connectors().size(); index++) {
                        Connector other = planned.connector(index);
                        if (!by.vertical() && other.front() == by.front().getOpposite()) {
                            return index;
                        }
                    }
                }
            }
            return -1;
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
            depths.add(graph.node(node).depth());
            roomOfNode[node] = placed.size() - 1;
            return placed.size() - 1;
        }

        private void removeLast() {
            placed.removeLast();
            used.removeLast();
            parents.removeLast();
            spaces.removeLast();
            depths.removeLast();
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
