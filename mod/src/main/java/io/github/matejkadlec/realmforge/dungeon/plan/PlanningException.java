package io.github.matejkadlec.realmforge.dungeon.plan;

/**
 * No layout was found: the rooms cannot meet the rules within a plot's reach, or the planner ran out of attempts.
 * Content validation catches pools that lack a kind of room the rules need; JUnit fuzzes the generated dungeon types
 * so that this never happens for them.
 */
public final class PlanningException extends RuntimeException {
    public PlanningException(String message) {
        super(message);
    }
}
