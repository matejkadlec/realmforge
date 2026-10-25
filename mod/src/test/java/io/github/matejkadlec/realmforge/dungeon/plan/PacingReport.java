package io.github.matejkadlec.realmforge.dungeon.plan;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * What a player meets in paced layouts (RLM-364), measured over many plans: the rhythm of the main path, the
 * treasure, repeats, caps and loops, and a few layouts written out room by room as a player walks them.
 */
final class PacingReport {
    private final StringBuilder walks = new StringBuilder();
    private final Map<String, Long> counts = new TreeMap<>();
    private final Map<Integer, Long> fightGaps = new TreeMap<>();
    private final Map<Integer, Long> firstFights = new TreeMap<>();
    private final Map<Integer, Long> corridorRuns = new TreeMap<>();
    private final Map<Integer, Long> treasureGaps = new TreeMap<>();
    private final Map<Integer, Long> copies = new TreeMap<>();
    private final Map<Integer, Long> copyDistances = new TreeMap<>();
    private final Map<String, Long> branchEnds = new TreeMap<>();
    private final Map<String, Long> endings = new TreeMap<>();
    private final Map<String, Long> climaxes = new TreeMap<>();
    private final Map<Integer, Long> attempts = new TreeMap<>();
    private int plans;

    void add(DungeonPlan plan) {
        plans++;
        tally(attempts, plan.attempt());
        List<PlannedRoom> rooms = plan.rooms();
        int[] parent = parents(plan);
        List<Integer> path = new ArrayList<>();
        int ending = -1;
        for (int i = 0; i < rooms.size(); i++) {
            if (rooms.get(i).slot() == Slot.ENDING) {
                ending = i;
                for (int room = parent[i]; room > 0; room = parent[room]) {
                    path.addFirst(room);
                }
            }
        }
        count("layouts", 1);
        count("rooms (walls not counted)", rooms.stream().filter(r -> r.room().role() != RoomRole.WALL).count());
        count("main path rooms", path.size());
        count("walls", rooms.stream().filter(r -> r.room().role() == RoomRole.WALL).count());
        count("dead ends as caps", rooms.stream().filter(r -> r.slot() == Slot.CAP
                && r.room().role() != RoomRole.WALL && !r.room().nearStart()).count());
        count("start rooms as caps", rooms.stream().filter(r -> r.slot() == Slot.CAP && r.room().nearStart()).count());
        count("branches", plan.count(Slot.BRANCH_END));
        count("branch rooms", plan.count(Slot.BRANCH) + plan.count(Slot.BRANCH_END));
        long loops = plan.links().stream()
                .filter(link -> parent[link.room()] != link.otherRoom() && parent[link.otherRoom()] != link.room())
                .count();
        count("loops", loops);
        for (Pace pace : Pace.values()) {
            count("main path " + pace.name().toLowerCase(Locale.ROOT),
                    path.stream().filter(room -> is(rooms.get(room), pace)).count());
            count("all " + pace.name().toLowerCase(Locale.ROOT),
                    rooms.stream().filter(room -> is(room, pace)).count());
        }
        // The main path's rhythm.
        int since = 0;
        boolean first = true;
        int run = 0;
        for (int room : path) {
            PlannedRoom planned = rooms.get(room);
            if (is(planned, Pace.FIGHT)) {
                tally(first ? firstFights : fightGaps, since);
                first = false;
                since = 0;
            } else {
                since++;
            }
            if (is(planned, Pace.CORRIDOR)) {
                run++;
            } else {
                if (run > 0) {
                    tally(corridorRuns, run);
                }
                run = 0;
            }
        }
        if (run > 0) {
            tally(corridorRuns, run);
        }
        count("guard fight before the ending", !path.isEmpty() && is(rooms.get(path.getLast()), Pace.FIGHT) ? 1 : 0);
        int firstClimax = path.size();
        for (int place = 0; place + 1 < path.size(); place++) {
            if (is(rooms.get(path.get(place)), Pace.FIGHT) && hosts(rooms, parent, path.get(place + 1))) {
                firstClimax = Math.min(firstClimax, place);
            }
        }
        int lastHazard = -1000;
        for (int place = 0; place < path.size(); place++) {
            DungeonRoom room = rooms.get(path.get(place)).room();
            boolean beforeFight = place + 1 < path.size() && is(rooms.get(path.get(place + 1)), Pace.FIGHT);
            if (room.hazard()) {
                count("hazards in the first section", place < firstClimax ? 1 : 0);
                count("hazards within 7 rooms of the last", place - lastHazard < 7 ? 1 : 0);
                count("hazards right before a fight", beforeFight ? 1 : 0);
                lastHazard = place;
            }
            if (room.entry().isPresent()) {
                count("locks right before a fight", beforeFight ? 1 : 0);
            }
            if (place == RoomGraph.APPROACH) {
                count("hazards or locks first after the approach", room.hazard() || room.entry().isPresent() ? 1 : 0);
            }
        }
        if (ending >= 0) {
            endings.merge(name(rooms.get(ending)), 1L, Long::sum);
        }
        // Treasure along the walk: branch ends at their host's place, rooms with 3+ chests, the ending.
        List<Integer> stops = new ArrayList<>();
        for (int place = 0; place < path.size(); place++) {
            int room = path.get(place);
            if (rooms.get(room).room().chests() >= DungeonRoom.TREASURE_CHESTS) {
                stops.add(place + 1);
            }
            for (int i = 0; i < rooms.size(); i++) {
                if (rooms.get(i).slot() == Slot.BRANCH_END && hostOf(rooms, parent, i) == room
                        && treasure(rooms.get(i))) {
                    stops.add(place + 1);
                }
            }
            if (is(rooms.get(room), Pace.FIGHT) && (place + 1 < path.size() && hosts(rooms, parent, path.get(place + 1))
                    || place == path.size() - 1)) {
                climaxes.merge(name(rooms.get(room)), 1L, Long::sum);
            }
        }
        stops.add(path.size() + 1);
        int last = 0;
        for (int stop : stops) {
            if (stop > last) {
                tally(treasureGaps, stop - last);
            }
            last = Math.max(last, stop);
        }
        for (int i = 0; i < rooms.size(); i++) {
            if (rooms.get(i).slot() == Slot.BRANCH_END) {
                PlannedRoom end = rooms.get(i);
                String kind = is(end, Pace.TREASURE) ? "treasure room"
                        : end.room().chests() >= DungeonRoom.TREASURE_CHESTS ? "room with 3+ chests" : "other dead end";
                branchEnds.merge(kind, 1L, Long::sum);
            }
        }
        // Repeats of rooms that are no corridors: copies per layout and their distance in rooms from the entrance.
        int[] depth = new int[rooms.size()];
        for (int i = 1; i < rooms.size(); i++) {
            depth[i] = depth[parent[i]] + 1;
        }
        Map<DungeonRoom, List<Integer>> placed = new HashMap<>();
        for (int i = 1; i < rooms.size(); i++) {
            Optional<Pace> pace = rooms.get(i).room().pace();
            if (pace.isPresent() && pace.get() != Pace.CORRIDOR) {
                placed.computeIfAbsent(rooms.get(i).room(), key -> new ArrayList<>()).add(depth[i]);
            }
        }
        for (List<Integer> depths : placed.values()) {
            tally(copies, depths.size());
            for (int a = 0; a < depths.size(); a++) {
                for (int b = a + 1; b < depths.size(); b++) {
                    tally(copyDistances, Math.abs(depths.get(a) - depths.get(b)) / 5 * 5);
                }
            }
        }
        if (plans <= 3) {
            walk(plan, parent, path, ending);
        }
    }

    /** The report as text. */
    String text() {
        StringBuilder text = new StringBuilder("Pacing report over " + plans + " layouts\n\nMeans per layout:\n");
        counts.forEach((what, total) -> text.append(String.format(Locale.ROOT, "  %-34s %7.2f%n", what,
                total / (double) plans)));
        section(text, "Rooms between two fights on the main path", fightGaps);
        section(text, "Rooms before the first fight", firstFights);
        section(text, "Corridors in a row on the main path", corridorRuns);
        section(text, "Main path rooms from one treasure stop to the next", treasureGaps);
        section(text, "Copies per layout of a room that is no corridor", copies);
        section(text, "Rooms between two copies (in steps of 5)", copyDistances);
        section(text, "Branch ends", branchEnds);
        section(text, "Endings", endings);
        section(text, "Climax fights (before a branch's junction or the ending)", climaxes);
        section(text, "Attempts to plan a layout", attempts);
        return text.append("\n").append(walks).toString();
    }

    long count(String what) {
        return counts.getOrDefault(what, 0L);
    }

    Map<Integer, Long> fightGaps() {
        return fightGaps;
    }

    Map<Integer, Long> firstFights() {
        return firstFights;
    }

    Map<Integer, Long> corridorRuns() {
        return corridorRuns;
    }

    Map<Integer, Long> treasureGaps() {
        return treasureGaps;
    }

    Map<String, Long> branchEnds() {
        return branchEnds;
    }

    /** Writes the layout as a player walks it: the main path, each branch where it leaves, caps and loops apart. */
    private void walk(DungeonPlan plan, int[] parent, List<Integer> path, int ending) {
        List<PlannedRoom> rooms = plan.rooms();
        walks.append("Layout of seed ").append(plan.seed()).append(" (F fight, X feature, c corridor, T treasure,")
                .append(" * climax, ! hazard, $ 3+ chests; branches in brackets):\n   0 E ")
                .append(name(rooms.getFirst()));
        for (int place = 0; place < path.size(); place++) {
            int room = path.get(place);
            boolean climax = is(rooms.get(room), Pace.FIGHT)
                    && (place == path.size() - 1 || hosts(rooms, parent, path.get(place + 1)));
            String symbol = symbol(rooms.get(room)) + (climax ? "*" : "")
                    + (rooms.get(room).room().hazard() ? "!" : "");
            walks.append(String.format(Locale.ROOT, "%n  %2d %-3s ", place + 1, symbol)).append(name(rooms.get(room)));
            for (int i = 0; i < rooms.size(); i++) {
                if (rooms.get(i).slot() == Slot.BRANCH && parent[i] == room
                        || rooms.get(i).slot() == Slot.BRANCH_END && parent[i] == room) {
                    walks.append("   [");
                    for (int at = i; at >= 0; at = childOf(rooms, parent, at)) {
                        walks.append(at == i ? "" : " > ").append(symbol(rooms.get(at))).append(' ')
                                .append(name(rooms.get(at)));
                    }
                    walks.append(']');
                }
            }
            long caps = 0;
            for (int i = 0; i < rooms.size(); i++) {
                caps += rooms.get(i).slot() == Slot.CAP && parent[i] == room ? 1 : 0;
            }
            if (caps > 0) {
                walks.append("   (").append(caps).append(" closed)");
            }
        }
        walks.append(String.format(Locale.ROOT, "%n  %2d T ", path.size() + 1)).append(name(rooms.get(ending)))
                .append(" (ending)\n");
        for (RoomLink link : plan.links()) {
            if (parent[link.room()] != link.otherRoom() && parent[link.otherRoom()] != link.room()) {
                walks.append("  loop: ").append(name(rooms.get(link.room()))).append(" <-> ")
                        .append(name(rooms.get(link.otherRoom()))).append('\n');
            }
        }
        walks.append('\n');
    }

    private static int childOf(List<PlannedRoom> rooms, int[] parent, int room) {
        for (int i = room + 1; i < rooms.size(); i++) {
            if (parent[i] == room && (rooms.get(i).slot() == Slot.BRANCH || rooms.get(i).slot() == Slot.BRANCH_END)) {
                return i;
            }
        }
        return -1;
    }

    private static int hostOf(List<PlannedRoom> rooms, int[] parent, int branchEnd) {
        int room = branchEnd;
        while (rooms.get(room).slot() != Slot.PATH) {
            room = parent[room];
        }
        return room;
    }

    private static boolean hosts(List<PlannedRoom> rooms, int[] parent, int room) {
        for (int i = 0; i < rooms.size(); i++) {
            Slot slot = rooms.get(i).slot();
            if (parent[i] == room && (slot == Slot.BRANCH || slot == Slot.BRANCH_END)) {
                return true;
            }
        }
        return false;
    }

    private static boolean treasure(PlannedRoom room) {
        return is(room, Pace.TREASURE) || room.room().chests() >= DungeonRoom.TREASURE_CHESTS;
    }

    private static String symbol(PlannedRoom room) {
        return room.room().pace().map(pace -> switch (pace) {
            case CORRIDOR -> "c";
            case FEATURE -> "X";
            case FIGHT -> "F";
            case TREASURE -> "T";
        }).orElse("?") + (room.room().chests() >= DungeonRoom.TREASURE_CHESTS && !is(room, Pace.TREASURE) ? "$" : "");
    }

    private static String name(PlannedRoom room) {
        String path = room.room().template().getPath();
        return path.substring(path.lastIndexOf('/', path.lastIndexOf('/') - 1) + 1);
    }

    private static boolean is(PlannedRoom room, Pace pace) {
        return room.room().pace().equals(Optional.of(pace));
    }

    /** Per room, the room it hangs off: the first link that leads to it from a room placed before it. */
    static int[] parents(DungeonPlan plan) {
        int[] parent = new int[plan.rooms().size()];
        java.util.Arrays.fill(parent, -1);
        for (RoomLink link : plan.links()) {
            if (parent[link.otherRoom()] < 0 && link.otherRoom() > link.room()) {
                parent[link.otherRoom()] = link.room();
            }
        }
        return parent;
    }

    private void count(String what, long value) {
        counts.merge(what, value, Long::sum);
    }

    private static <K> void tally(Map<K, Long> map, K key) {
        map.merge(key, 1L, Long::sum);
    }

    private static <K> void section(StringBuilder text, String title, Map<K, Long> map) {
        long total = map.values().stream().mapToLong(Long::longValue).sum();
        text.append('\n').append(title).append(":\n");
        map.forEach((key, count) -> text.append(String.format(Locale.ROOT, "  %-28s %7d  %5.1f %%%n", key, count,
                100.0 * count / Math.max(1, total))));
    }
}
