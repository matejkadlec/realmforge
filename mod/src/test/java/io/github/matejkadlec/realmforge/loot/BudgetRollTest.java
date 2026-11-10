package io.github.matejkadlec.realmforge.loot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.matejkadlec.realmforge.loot.BudgetRoll.Choice;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.function.IntUnaryOperator;
import org.junit.jupiter.api.Test;

class BudgetRollTest {
    private static final Choice BREAD = new Choice(10, 60, 0);
    private static final Choice INGOT = new Choice(5, 150, 0);
    private static final Choice CURIO = new Choice(1, 1200, 1);
    private static final List<Choice> CHOICES = List.of(BREAD, INGOT, CURIO);

    private static IntUnaryOperator seeded(long seed) {
        Random random = new Random(seed);
        return random::nextInt;
    }

    @Test
    void aRollNeverSpendsMoreThanItsBudgetAndStopsOnlyWhenNothingIsAffordable() {
        for (int seed = 0; seed < 500; seed++) {
            int budget = 300 + seed * 3;
            List<Integer> picked = BudgetRoll.roll(budget, CHOICES, seeded(seed));
            int spent = BudgetRoll.spent(picked, CHOICES);
            assertTrue(spent <= budget, "seed " + seed + " spent " + spent + " of " + budget);
            // What is left buys nothing: less than the cheapest choice.
            assertTrue(budget - spent < BREAD.cost(), "seed " + seed + " left " + (budget - spent));
        }
    }

    @Test
    void aValuableChoiceLeavesLittleForTheRest() {
        // Design: "a container whose budget is 1,200 may hold that curio alone".
        List<Choice> curioFirst = List.of(CURIO, BREAD);
        IntUnaryOperator alwaysFirst = bound -> 0;
        assertEquals(List.of(0), BudgetRoll.roll(1200, curioFirst, alwaysFirst));
        // With 1,500 points the curio leaves 300: five loaves, never a second curio.
        assertEquals(List.of(0, 1, 1, 1, 1, 1), BudgetRoll.roll(1500, curioFirst, alwaysFirst));
    }

    @Test
    void aLimitedChoiceComesAtMostThatOften() {
        // "A unique curio worth 1,200 points can never come twice from one container."
        for (int seed = 0; seed < 300; seed++) {
            List<Integer> picked = BudgetRoll.roll(6000, CHOICES, seeded(seed));
            assertTrue(Collections.frequency(picked, 2) <= 1, "seed " + seed + " picked the curio twice: " + picked);
        }
        List<Choice> twice = List.of(new Choice(1, 100, 2), new Choice(1, 100, 0));
        assertEquals(List.of(0, 0, 1, 1, 1), BudgetRoll.roll(500, twice, bound -> 0));
    }

    @Test
    void aBudgetBelowEveryCostBuysNothing() {
        assertEquals(List.of(), BudgetRoll.roll(59, CHOICES, seeded(1)));
        assertEquals(List.of(), BudgetRoll.roll(0, CHOICES, seeded(1)));
        assertEquals(List.of(), BudgetRoll.roll(1000, List.of(), seeded(1)));
    }

    @Test
    void theWeightsDecideAmongTheAffordableChoices() {
        // With 100 points only bread (60) is affordable, whatever the weights say.
        assertEquals(List.of(0), BudgetRoll.roll(100, CHOICES, seeded(7)));
        // The draw covers every weight: the last unit of the total picks the last affordable choice.
        List<Choice> two = List.of(new Choice(3, 100, 0), new Choice(1, 100, 0));
        assertEquals(List.of(0), BudgetRoll.roll(100, two, bound -> 2));
        assertEquals(List.of(1), BudgetRoll.roll(100, two, bound -> 3));
        int[] times = new int[2];
        IntUnaryOperator random = seeded(11);
        for (int roll = 0; roll < 4000; roll++) {
            times[BudgetRoll.roll(100, two, random).getFirst()]++;
        }
        assertTrue(times[0] > 2800 && times[0] < 3200, "the weight 3 of 4 came " + times[0] + " times of 4000");
    }

    @Test
    void aRollEndsHoweverLargeItsBudget() {
        List<Integer> picked = BudgetRoll.roll(Integer.MAX_VALUE, List.of(new Choice(1, 1, 0)), seeded(3));
        assertEquals(BudgetRoll.MAX_PICKS, picked.size());
    }

    @Test
    void aChoiceCostsAtLeastOnePoint() {
        assertThrows(IllegalArgumentException.class, () -> new Choice(1, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new Choice(0, 10, 0));
        assertThrows(IllegalArgumentException.class, () -> new Choice(1, 10, -1));
    }

    @Test
    void halfOfAllBudgetsLieBelowTheMediumAndAHighRollIsRare() {
        LootBudget normal = new LootBudget(300, 600, 1500);
        assertEquals(300.0, normal.at(0));
        assertEquals(450.0, normal.at(0.25));
        assertEquals(600.0, normal.at(0.5));
        assertEquals(1050.0, normal.at(0.75));
        assertEquals(1500.0, normal.at(1));
        assertEquals(1500.0, normal.at(7));
        assertEquals(300.0, normal.at(-1));
    }

    @Test
    void theWorldTierAndLuckScaleTheBudget() {
        LootBudget normal = new LootBudget(300, 600, 1500);
        assertEquals(600, normal.points(0.5, 100, 0, 5));
        assertEquals(720, normal.points(0.5, 120, 0, 5));
        // Each point of luck adds 5 %: Luck II gives 10 % more.
        assertEquals(660, normal.points(0.5, 100, 2, 5));
        assertEquals(792, normal.points(0.5, 120, 2, 5));
        // Bad luck takes points away, never below nothing.
        assertEquals(570, normal.points(0.5, 100, -1, 5));
        assertEquals(0, normal.points(0.5, 100, -40, 5));
    }
}
