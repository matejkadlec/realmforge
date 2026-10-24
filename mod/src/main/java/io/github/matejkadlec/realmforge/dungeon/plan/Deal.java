package io.github.matejkadlec.realmforge.dungeon.plan;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import net.minecraft.util.RandomSource;

/**
 * With a pacing, the rooms dealt to a layout's nodes before any is placed (RLM-364, the critics of Matěj's playtest of
 * 2026-10-24): each node tries its dealt room first, and the placement falls back to the planner's order where the
 * geometry refuses it. Dealing the whole layout at once keeps the rooms spread over the run and varied between runs,
 * where a greedy choice from the entrance on used the easy rooms early and left the rest to the end.
 *
 * <ul>
 *   <li><b>Branch ends</b> get the rooms with the most treasure (a treasure room's chests count twice, as they hold the
 *       better loot), but the one kept for the ending, the best for the last branch: rewards rise along the run.
 *   <li><b>Fights</b>: the guard before the ending first, the room with the most spawners; then the others from the
 *       entrance on, in turn: none within {@code repeat} rooms of a copy while another fits (Matěj's playtest of
 *       2026-10-22 found the same room twice too close), a climax the heaviest of those, then the room whose last copy
 *       lies farthest back, then the one with the most copies left.
 *   <li><b>Features</b>, among the rooms no branch end took: first the hazards, then the locks (rooms with an
 *       entry), each to a random feature node at least half of {@code repeat} rooms from those of its kind that is
 *       neither the first feature (a player's first room after the lobby) nor right before a fight (where a fight's
 *       mobs would come while players solve or dodge it) and, for a hazard, lies in neither the first section nor the
 *       last, so the run neither opens nor ends with one; then the other feature nodes from the entrance on, with rooms
 *       that break none of these rules first: none within {@code repeat} rooms of a copy or half of it of a room of its
 *       kind, no hazard in the first or last section, no hazard or lock first or before a fight.
 * </ul>
 *
 * Ties go by weight, at random, like the planner's own order.
 */
final class Deal {
    private final Map<Integer, DungeonRoom> dealt = new HashMap<>();

    private Deal() {
    }

    /** No room dealt: a layout without a pacing. */
    static Deal none() {
        return new Deal();
    }

    static Deal of(RoomGraph graph, List<DungeonRoom> pool, LayoutRules.Pacing pacing, Optional<DungeonRoom> reserved,
            RandomSource random) {
        Deal deal = new Deal();
        List<DungeonRoom> shuffled = byWeight(pool, random);
        Map<DungeonRoom, Integer> order = new HashMap<>();
        for (int i = 0; i < shuffled.size(); i++) {
            order.put(shuffled.get(i), i);
        }
        List<Integer> path = new ArrayList<>();
        List<Integer> ends = new ArrayList<>();
        for (int node = 0; node < graph.size(); node++) {
            Slot slot = graph.node(node).slot();
            if (slot == Slot.PATH) {
                path.add(node);
            } else if (slot == Slot.BRANCH_END) {
                ends.add(node);
            }
        }
        // Branch ends: the richest rooms, the best last.
        List<DungeonRoom> rewards = new ArrayList<>(shuffled.stream()
                .filter(room -> !room.nearStart() && !reserved.equals(Optional.of(room)) && (is(room, Pace.TREASURE)
                        && room.deadEnd() && room.role().closesBranches() || room.role().passesThrough()
                        && room.chests() >= DungeonRoom.TREASURE_CHESTS))
                .sorted(Comparator.comparingInt(Deal::treasure).reversed())
                .limit(ends.size())
                .toList());
        rewards.sort(Comparator.comparingInt(Deal::treasure));
        for (int i = 0; i < rewards.size(); i++) {
            deal.dealt.put(ends.get(i), rewards.get(i));
        }
        // Fights: the guard, then the others from the entrance on, in turn.
        List<Integer> fights = path.stream().filter(node -> paced(graph, node, Pace.FIGHT)).toList();
        List<Integer> fightOrder = new ArrayList<>(fights.reversed().stream().limit(1).toList());
        fights.stream().filter(node -> !fightOrder.contains(node)).forEach(fightOrder::add);
        Map<DungeonRoom, Integer> left = copies(pool, room -> is(room, Pace.FIGHT), fights.size());
        for (int node : fightOrder) {
            boolean guard = node == fights.getLast();
            boolean climax = graph.node(node).climax();
            int depth = graph.node(node).depth();
            deal.pick(node, left, order, room -> new int[] {
                guard ? -room.spawners() : 0,
                deal.near(graph, room, depth, pacing.repeat(), other -> other.equals(room)) ? 1 : 0,
                climax ? -room.spawners() : 0,
                -deal.distance(graph, room, depth), -left.get(room)});
        }
        // Features, among the rooms no branch end took: first the hazards, then the locks, each to a random feature node
        // that is not the first, stands before no fight and lies far enough from the rooms of its kind dealt so far
        // (and, for a hazard, in neither the first section nor the last); then the other nodes from the entrance on,
        // the rooms with the fewest faults first.
        List<Integer> climaxes = path.stream().filter(node -> graph.node(node).climax())
                .map(node -> graph.node(node).depth()).toList();
        int opening = climaxes.isEmpty() ? Integer.MAX_VALUE : climaxes.getFirst();
        int closing = climaxes.size() < 2 ? Integer.MAX_VALUE : climaxes.get(climaxes.size() - 2);
        Map<DungeonRoom, Integer> spare = copies(pool, room -> is(room, Pace.FEATURE) && !rewards.contains(room),
                path.size());
        int apart = Math.max(1, pacing.repeat() / 2);
        List<Integer> features = new ArrayList<>();
        List<Integer> calm = new ArrayList<>();
        for (int i = 0; i < path.size(); i++) {
            int node = path.get(i);
            if (paced(graph, node, Pace.FEATURE)) {
                features.add(node);
                if (features.size() > 1 && (i + 1 >= path.size() || !paced(graph, path.get(i + 1), Pace.FIGHT))) {
                    calm.add(node);
                }
            }
        }
        List<Integer> spread = new ArrayList<>(calm);
        for (int i = spread.size() - 1; i > 0; i--) {
            spread.set(i, spread.set(random.nextInt(i + 1), spread.get(i)));
        }
        for (Predicate<DungeonRoom> kind : List.<Predicate<DungeonRoom>>of(DungeonRoom::hazard,
                room -> room.entry().isPresent())) {
            for (DungeonRoom room : shuffled.stream().filter(spare::containsKey).filter(kind).toList()) {
                for (int node : spread) {
                    int depth = graph.node(node).depth();
                    boolean placeable = !deal.dealt.containsKey(node) && spare.get(room) > 0
                            && !(room.hazard() && (depth <= opening || depth > closing))
                            && !deal.near(graph, room, depth, apart, kind);
                    if (placeable) {
                        deal.dealt.put(node, room);
                        spare.merge(room, -1, Integer::sum);
                        break;
                    }
                }
            }
        }
        for (int i = 0; i < path.size(); i++) {
            int node = path.get(i);
            if (!features.contains(node) || deal.dealt.containsKey(node)) {
                continue;
            }
            int depth = graph.node(node).depth();
            boolean risky = !calm.contains(node);
            deal.pick(node, spare, order, room -> {
                int faults = deal.near(graph, room, depth, pacing.repeat(), other -> other.equals(room)) ? 1 : 0;
                faults += room.entry().isPresent()
                        && deal.near(graph, room, depth, apart, other -> other.entry().isPresent()) ? 1 : 0;
                faults += room.hazard() && deal.near(graph, room, depth, apart, DungeonRoom::hazard) ? 1 : 0;
                faults += room.hazard() && (depth <= opening || depth > closing) ? 1 : 0;
                faults += (room.hazard() || room.entry().isPresent()) && risky ? 1 : 0;
                return new int[] {faults, -spare.get(room)};
            });
        }
        return deal;
    }

    /** The room dealt to {@code node}, if any. */
    Optional<DungeonRoom> at(int node) {
        return Optional.ofNullable(dealt.get(node));
    }

    /** Whether {@code room} is dealt to a node after {@code node}, which the placement reaches later. */
    boolean later(DungeonRoom room, int node) {
        return dealt.entrySet().stream().anyMatch(entry -> entry.getKey() > node && entry.getValue().equals(room));
    }

    /**
     * Deals {@code node} the room with copies {@code left} whose key, compared element by element, is the lowest,
     * ties going by {@code order}.
     */
    private void pick(int node, Map<DungeonRoom, Integer> left, Map<DungeonRoom, Integer> order,
            Function<DungeonRoom, int[]> key) {
        DungeonRoom best = null;
        int[] bestKey = null;
        for (Map.Entry<DungeonRoom, Integer> entry : left.entrySet()) {
            if (entry.getValue() <= 0) {
                continue;
            }
            DungeonRoom room = entry.getKey();
            int[] own = key.apply(room);
            int[] candidate = Arrays.copyOf(own, own.length + 1);
            candidate[candidate.length - 1] = order.get(room);
            if (bestKey == null || Arrays.compare(candidate, bestKey) < 0) {
                best = room;
                bestKey = candidate;
            }
        }
        if (best != null) {
            dealt.put(node, best);
            left.merge(best, -1, Integer::sum);
        }
    }

    /** How many rooms from {@code depth} the nearest copy of {@code room} dealt so far lies (a large number: none). */
    private int distance(RoomGraph graph, DungeonRoom room, int depth) {
        return dealt.entrySet().stream().filter(entry -> entry.getValue().equals(room))
                .mapToInt(entry -> Math.abs(graph.node(entry.getKey()).depth() - depth)).min().orElse(1_000);
    }

    /** Whether a room dealt so far that {@code matches} lies within {@code repeat} rooms of {@code depth}. */
    private boolean near(RoomGraph graph, DungeonRoom room, int depth, int repeat, Predicate<DungeonRoom> matches) {
        return dealt.entrySet().stream().anyMatch(entry -> matches.test(entry.getValue())
                && Math.abs(graph.node(entry.getKey()).depth() - depth) < repeat);
    }

    /** The pool's rooms to pass through that {@code fits} keeps, with two connectors or more, and their copies. */
    private static Map<DungeonRoom, Integer> copies(List<DungeonRoom> pool, Predicate<DungeonRoom> fits, int most) {
        Map<DungeonRoom, Integer> copies = new HashMap<>();
        for (DungeonRoom room : pool) {
            if (room.role().passesThrough() && room.connectors().size() >= 2 && !room.nearStart() && fits.test(room)) {
                copies.put(room, Math.min(most, room.maxPerLayout().orElse(most)));
            }
        }
        return copies;
    }

    /** How much treasure a room holds: its chests, twice for a treasure room's. */
    private static int treasure(DungeonRoom room) {
        return room.chests() * (is(room, Pace.TREASURE) ? 2 : 1);
    }

    private static boolean paced(RoomGraph graph, int node, Pace pace) {
        return graph.node(node).pace().equals(Optional.of(pace));
    }

    private static boolean is(DungeonRoom room, Pace pace) {
        return room.pace().equals(Optional.of(pace));
    }

    /** The rooms in a random order in which a heavier room tends to come first (Efraimidis and Spirakis). */
    private static List<DungeonRoom> byWeight(List<DungeonRoom> rooms, RandomSource random) {
        record Keyed(double key, int index, DungeonRoom room) {
        }
        List<Keyed> keyed = new ArrayList<>(rooms.size());
        for (int i = 0; i < rooms.size(); i++) {
            keyed.add(new Keyed(Math.pow(random.nextDouble(), 1.0 / rooms.get(i).weight()), i, rooms.get(i)));
        }
        keyed.sort(Comparator.comparingDouble(Keyed::key).reversed().thenComparingInt(Keyed::index));
        return keyed.stream().map(Keyed::room).toList();
    }
}
