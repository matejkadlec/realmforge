package io.github.matejkadlec.realmforge.loot;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntUnaryOperator;

/**
 * How a container spends its loot points (architecture 13, ADR 0041; RLM-289): it draws one of its options by weight
 * among those it can still afford, pays the option's cost, and goes on until no option is affordable. A valuable
 * option leaves little for the rest, an option with a limit comes at most that often, and no roll ever spends more
 * than its budget. Pure, so JUnit covers it; the pipeline simulates the same rule
 * ({@code tools/realmforge_tools/schema/container_loot.py}).
 */
public final class BudgetRoll {
    /** No roll takes more picks than this, whatever its budget and costs: a container holds 27 stacks. */
    public static final int MAX_PICKS = 64;

    private BudgetRoll() {
    }

    /**
     * One thing the budget may buy.
     *
     * @param weight how likely it is against the other affordable choices, at least 1
     * @param cost what it takes off the budget, at least 1
     * @param limit how often one roll may pick it, 0 for any number of times
     */
    public record Choice(int weight, int cost, int limit) {
        public Choice {
            if (weight < 1 || cost < 1 || limit < 0) {
                throw new IllegalArgumentException("A choice has a weight and a cost of at least 1 and a limit of 0 or "
                        + "more: " + weight + ", " + cost + ", " + limit);
            }
        }
    }

    /**
     * The choices a budget of {@code budget} points buys, as indexes into {@code choices} in the order picked.
     *
     * @param random a number from 0 up to its argument (exclusive), for the weighted draw
     */
    public static List<Integer> roll(int budget, List<Choice> choices, IntUnaryOperator random) {
        List<Integer> picked = new ArrayList<>();
        int[] times = new int[choices.size()];
        int remaining = budget;
        while (picked.size() < MAX_PICKS) {
            int total = 0;
            for (int i = 0; i < choices.size(); i++) {
                if (affordable(choices.get(i), times[i], remaining)) {
                    total += choices.get(i).weight();
                }
            }
            if (total <= 0) {
                break;
            }
            int roll = random.applyAsInt(total);
            for (int i = 0; i < choices.size(); i++) {
                Choice choice = choices.get(i);
                if (!affordable(choice, times[i], remaining)) {
                    continue;
                }
                roll -= choice.weight();
                if (roll < 0) {
                    picked.add(i);
                    times[i]++;
                    remaining -= choice.cost();
                    break;
                }
            }
        }
        return picked;
    }

    /** The points the picks of a roll cost together. */
    public static int spent(List<Integer> picked, List<Choice> choices) {
        return picked.stream().mapToInt(index -> choices.get(index).cost()).sum();
    }

    private static boolean affordable(Choice choice, int times, int remaining) {
        return choice.cost() <= remaining && (choice.limit() == 0 || times < choice.limit());
    }
}
