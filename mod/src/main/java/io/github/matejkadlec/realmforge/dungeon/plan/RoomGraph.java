package io.github.matejkadlec.realmforge.dungeon.plan;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import net.minecraft.util.InclusiveRange;
import net.minecraft.util.RandomSource;

/**
 * Step 1 of planning (architecture 15.3): which rooms a layout has and how they hang together, before any room is
 * chosen. A tree in placement order, so every node's parent comes before it: the entrance, the main path and the room
 * that ends it, and the branches, each hanging off a room of the main path and ending in a dead end.
 *
 * <p>With a {@link LayoutRules.Pacing} (RLM-364), each node also asks for a {@link Pace}, and the graph is built
 * around the fights. The main path opens with its approach of corridors; the stretch before the first fight and the
 * gaps between fights are drawn from {@code fights}, as many as the pool has fights for, and fitted to the path so the
 * room before the ending is a fight, its guard. The main path falls into sections, one more than its branches, each
 * ending in the fight nearest its share of the path: the section's climax. Within a section the gaps run from the
 * longest to the shortest, so the fights come ever closer until the climax, and right after it the next section opens
 * with the corridor that the section's branch hangs off, corridors up to a treasure room. Between the fights, a
 * feature follows each run of corridors drawn from {@code corridors}, and a gap of three rooms or more ends in a
 * corridor where the runs allow it. The branches come in the placement order right after the room they hang off, so
 * they take their space before the rest of the main path.
 */
final class RoomGraph {
    /**
     * With a pacing, the corridors that open the main path: under the entrance's shaft, the shafts down and the hall
     * they end in (Stonevaults' falls and lobby), so the rhythm starts after them.
     */
    static final int APPROACH = 2;

    /**
     * One room to be: its slot, the node it hangs off ({@code -1} for the entrance), the pace it asks for (none: any
     * room that fits its slot), its distance from the entrance in rooms, and whether it is a fight that ends a
     * section: its climax, or the guard before the ending.
     */
    record Node(Slot slot, int parent, Optional<Pace> pace, int depth, boolean climax) {
    }

    private final List<Node> nodes;
    private final int[] children;

    private RoomGraph(List<Node> nodes) {
        this.nodes = List.copyOf(nodes);
        this.children = new int[nodes.size()];
        for (Node node : nodes) {
            if (node.parent() >= 0) {
                children[node.parent()]++;
            }
        }
    }

    /**
     * A graph drawn from the rules: a main path and a number of branches, each with a length, within their ranges.
     * With a pacing, it plans no more fights than {@code fights}, the fights the pool can supply.
     */
    static RoomGraph draw(LayoutRules rules, int fights, RandomSource random) {
        int mainPath = roll(rules.mainPath(), random);
        return rules.pacing().map(pacing -> paced(rules, pacing, mainPath, fights, random))
                .orElseGet(() -> unpaced(rules, mainPath, random));
    }

    /**
     * Branches hang off different rooms of the main path, picked at random while there are enough, so no room has to
     * take several, and come after the ending.
     */
    private static RoomGraph unpaced(LayoutRules rules, int mainPath, RandomSource random) {
        List<Node> nodes = new ArrayList<>();
        nodes.add(new Node(Slot.ENTRANCE, -1, Optional.empty(), 0, false));
        for (int i = 1; i <= mainPath; i++) {
            nodes.add(new Node(Slot.PATH, i - 1, Optional.empty(), i, false));
        }
        nodes.add(new Node(Slot.ENDING, mainPath, Optional.empty(), mainPath + 1, false));
        List<Integer> hosts = new ArrayList<>();
        for (int branch = roll(rules.branches(), random); branch > 0; branch--) {
            if (hosts.isEmpty()) {
                for (int path = 1; path <= mainPath; path++) {
                    hosts.add(path);
                }
            }
            int parent = hosts.remove(random.nextInt(hosts.size()));
            int length = roll(rules.branchRooms(), random);
            for (int i = 1; i <= length; i++) {
                nodes.add(new Node(i == length ? Slot.BRANCH_END : Slot.BRANCH, parent, Optional.empty(),
                        nodes.get(parent).depth() + 1, false));
                parent = nodes.size() - 1;
            }
        }
        return new RoomGraph(nodes);
    }

    private static RoomGraph paced(LayoutRules rules, LayoutRules.Pacing pacing, int mainPath, int supply,
            RandomSource random) {
        int branches = roll(rules.branches(), random);
        // The fights' places on the main path: the stretch before the first and the gaps between them drawn, as many
        // as the pool has fights for, then fitted to the path one room at a time from a random gap on, so the last
        // fight is the guard before the ending: shorter while the path is overshot, longer while rooms are left, up to
        // the largest gap before any goes beyond it (where the pool has too few fights).
        int smallest = Math.max(pacing.fights().minInclusive(), APPROACH);
        List<Integer> cycles = new ArrayList<>();
        if (supply > 0 && mainPath - 1 >= smallest) {
            int opening = Math.min(mainPath - 1, Math.max(smallest, binomial(pacing.fights(), random)));
            int rest = mainPath - opening - 1;
            List<Integer> gaps = new ArrayList<>();
            while (rest > 0 && gaps.size() + 1 < supply) {
                int gap = binomial(pacing.fights(), random);
                gaps.add(gap);
                rest -= gap + 1;
            }
            int lowest = pacing.fights().minInclusive();
            int ceiling = pacing.fights().maxInclusive();
            for (int i = gaps.isEmpty() ? 0 : random.nextInt(gaps.size()); rest != 0; i++) {
                if (gaps.isEmpty()) {
                    opening += rest;
                    rest = 0;
                } else if (rest < 0 && Collections.max(gaps) <= lowest) {
                    rest += gaps.removeLast() + 1;
                } else if (rest > 0 && Collections.min(gaps) >= ceiling) {
                    ceiling++;
                } else {
                    int gap = gaps.get(i % gaps.size());
                    if (rest < 0 && gap > lowest || rest > 0 && gap < ceiling) {
                        gaps.set(i % gaps.size(), gap + Integer.signum(rest));
                        rest -= Integer.signum(rest);
                    }
                }
            }
            cycles.add(opening + 1);
            gaps.forEach(gap -> cycles.add(gap + 1));
        }
        List<Integer> fights = new ArrayList<>();
        int at = 0;
        for (int cycle : cycles) {
            at += cycle;
            fights.add(at);
        }
        List<Integer> ends = sectionEnds(fights, mainPath, Math.min(branches, Math.max(0, fights.size() - 1)));
        // Within a section the stretches shorten towards its climax; the first stretch stays first.
        int from = 1;
        List<Integer> bounds = new ArrayList<>(ends);
        bounds.add(fights.size() - 1);
        for (int end : bounds) {
            if (end >= from) {
                cycles.subList(from, end + 1).sort(Comparator.reverseOrder());
            }
            from = Math.max(from, end + 1);
        }
        Pace[] paces = new Pace[mainPath + 1];
        boolean[] climaxes = new boolean[mainPath + 1];
        fights.clear();
        int place = 0;
        for (int i = 0; i < cycles.size(); i++) {
            place += cycles.get(i);
            fights.add(place);
            paces[place] = Pace.FIGHT;
            climaxes[place] = i == cycles.size() - 1 || ends.contains(i);
        }
        int corridors = 0;
        int run = binomial(pacing.corridors(), random);
        for (int path = 1; path <= mainPath; path++) {
            if (paces[path] == Pace.FIGHT) {
                corridors = 0;
                run = binomial(pacing.corridors(), random);
            } else if (path > APPROACH && corridors >= run) {
                paces[path] = Pace.FEATURE;
                corridors = 0;
                run = binomial(pacing.corridors(), random);
            } else {
                paces[path] = Pace.CORRIDOR;
                corridors++;
            }
        }
        // A gap of three rooms or more ends in a corridor rather than a feature where the corridor runs allow it, so
        // players do not solve or dodge a feature with the next fight's mobs on them: c c X before a fight becomes
        // c X c, and X c X becomes X c c.
        int last = 0;
        for (int fight : fights) {
            int feature = fight - 1;
            if (fight - last - 1 >= 3 && feature > APPROACH && paces[feature] == Pace.FEATURE) {
                int before = 0;
                while (feature - 1 - before > last && paces[feature - 1 - before] == Pace.CORRIDOR) {
                    before++;
                }
                if (before - 1 >= pacing.corridors().minInclusive() && feature - 1 > APPROACH) {
                    paces[feature - 1] = Pace.FEATURE;
                    paces[feature] = Pace.CORRIDOR;
                } else if (before + 1 <= pacing.corridors().maxInclusive()) {
                    paces[feature] = Pace.CORRIDOR;
                }
            }
            last = fight;
        }
        List<Integer> hosts = new ArrayList<>();
        place = 0;
        for (int i = 0; i < cycles.size(); i++) {
            place += cycles.get(i);
            if (ends.contains(i)) {
                hosts.add(place + 1);
            }
        }
        // Branches the sections leave over (a main path with fewer fights than branches) take the corridors left.
        for (int extra = hosts.size(); extra < branches; extra++) {
            hosts.add(spare(paces, hosts, mainPath, (extra + 0.5) * mainPath / branches));
        }
        List<Node> nodes = new ArrayList<>();
        nodes.add(new Node(Slot.ENTRANCE, -1, Optional.empty(), 0, false));
        for (int path = 1; path <= mainPath; path++) {
            int previous = path == 1 ? 0 : indexOf(nodes, path - 1);
            nodes.add(new Node(Slot.PATH, previous, Optional.of(paces[path]), path, climaxes[path]));
            int host = nodes.size() - 1;
            for (int branch = 0; branch < hosts.size(); branch++) {
                if (hosts.get(branch) != path) {
                    continue;
                }
                int length = roll(rules.branchRooms(), random);
                int parent = host;
                for (int i = 1; i <= length; i++) {
                    boolean end = i == length;
                    nodes.add(new Node(end ? Slot.BRANCH_END : Slot.BRANCH, parent,
                            Optional.of(end ? Pace.TREASURE : Pace.CORRIDOR), path + i, false));
                    parent = nodes.size() - 1;
                }
            }
        }
        nodes.add(new Node(Slot.ENDING, indexOf(nodes, mainPath), Optional.empty(), mainPath + 1, false));
        return new RoomGraph(nodes);
    }

    /**
     * For {@code count} sections that end before the last one, the index of the fight that ends each: the one nearest
     * its share of the main path, each after the one before, leaving fights for the sections after it.
     */
    private static List<Integer> sectionEnds(List<Integer> fights, int mainPath, int count) {
        List<Integer> ends = new ArrayList<>();
        int last = -1;
        for (int section = 0; section < count; section++) {
            double target = (section + 1.0) * mainPath / (count + 1);
            int best = -1;
            for (int i = last + 1; i <= fights.size() - 2 - (count - 1 - section); i++) {
                if (best < 0 || Math.abs(fights.get(i) - target) < Math.abs(fights.get(best) - target)) {
                    best = i;
                }
            }
            ends.add(best);
            last = best;
        }
        return ends;
    }

    /** The corridor nearest {@code target} that is no host, neither the first room nor the last, else any room. */
    private static int spare(Pace[] paces, List<Integer> hosts, int mainPath, double target) {
        int best = -1;
        for (int pass = 0; pass < 2 && best < 0; pass++) {
            for (int path = 1; path <= mainPath; path++) {
                boolean fits = pass > 0 || paces[path] == Pace.CORRIDOR && path > 1 && path < mainPath
                        && !hosts.contains(path);
                if (fits && (best < 0 || Math.abs(path - target) < Math.abs(best - target))) {
                    best = path;
                }
            }
        }
        return best;
    }

    /** The index of the main path's node {@code path} rooms from the entrance. */
    private static int indexOf(List<Node> nodes, int path) {
        for (int index = nodes.size() - 1; index >= 0; index--) {
            Node node = nodes.get(index);
            if (node.slot() == Slot.PATH && node.depth() == path) {
                return index;
            }
        }
        throw new IllegalStateException("no node " + path + " on the main path");
    }

    int size() {
        return nodes.size();
    }

    Node node(int index) {
        return nodes.get(index);
    }

    /** The connectors the node's room needs: one to its parent, one for each node hanging off it. */
    int connectorsNeeded(int index) {
        return children[index] + (nodes.get(index).parent() < 0 ? 0 : 1);
    }

    private static int roll(InclusiveRange<Integer> range, RandomSource random) {
        return random.nextIntBetweenInclusive(range.minInclusive(), range.maxInclusive());
    }

    /** A value of the range drawn binomially, so its middle comes most often: [2, 4] gives 2, 3, 3, 4. */
    private static int binomial(InclusiveRange<Integer> range, RandomSource random) {
        int value = range.minInclusive();
        for (int i = range.minInclusive(); i < range.maxInclusive(); i++) {
            value += random.nextBoolean() ? 1 : 0;
        }
        return value;
    }
}
