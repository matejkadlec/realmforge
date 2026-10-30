package io.github.matejkadlec.realmforge.compat.inventorysorter;

import java.util.Collection;

/**
 * Inventory Sorting never moves a Sophisticated Backpacks backpack (RLM-519; Matěj's playtest of RLM-501,
 * 2026-10-29). An open backpack's screen is tied to the slot its backpack sits in ({@code BackpackContext.Item}, a
 * player inventory slot index): a sort of the player's inventory that moved the backpack closed its screen, or showed
 * the backpack that landed in that slot instead. Inventory Sorting's own setting keeps a stack where it is: a sort
 * priority rule with the position {@code ignore}, which leaves every matching stack in its slot and sorts the rest
 * around it; {@code mixin/InventorySortingStacksMixin} keeps it the same stack, not a copy (ADR 0065). This is that
 * rule's match, every backpack tier by item id, in Inventory Sorting's rule syntax ({@code a | b}); the client adds it
 * to the player's rules ({@code client/compat/inventorysorter/BackpacksStayInPlace}), and
 * {@code InventorySortingGameTests} check it against the pack's Inventory Sorting.
 */
public final class BackpackSortRule {
    /** Inventory Sorting's priority position that leaves a stack in its slot. */
    public static final String POSITION = "ignore";

    /** Sophisticated Backpacks 3.26.11's backpacks, every tier. */
    public static final String MATCH = "sophisticatedbackpacks:backpack | sophisticatedbackpacks:copper_backpack"
            + " | sophisticatedbackpacks:iron_backpack | sophisticatedbackpacks:gold_backpack"
            + " | sophisticatedbackpacks:diamond_backpack | sophisticatedbackpacks:netherite_backpack";

    private BackpackSortRule() {
    }

    /** Whether the rules' matches lack ours (a player who edited it keeps their own wording besides). */
    public static boolean isMissing(Collection<String> matches) {
        return matches.stream().map(String::trim).noneMatch(MATCH::equals);
    }
}
