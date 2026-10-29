package io.github.matejkadlec.realmforge.boss;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * The phases of a boss's fight as pure logic (architecture 14.3): the fight's progress goes in, the phase it moves
 * into comes out. The phases come in order: only the next phase's trigger counts, so a hit that takes the boss past
 * two health thresholds still enters the phase between them first, with its {@code on_enter} spells and its pause,
 * and the next one on a later tick. A first phase that names a trigger never needs it, as the fight starts there.
 * JUnit covers it against every boss definition ({@code BossPhaseMachineTest}).
 */
public final class BossPhaseMachine {
    private BossPhaseMachine() {
    }

    /**
     * Where the fight stands.
     *
     * @param health the boss's share of its maximum health, from 0 to 1
     * @param ticksInPhase the ticks since it entered its current phase
     */
    public record Progress(float health, int ticksInPhase) {
    }

    /**
     * The phase the boss moves into from {@code current}, or empty when it stays: the next phase, when its trigger
     * holds for {@code progress}.
     */
    public static OptionalInt next(List<BossPhase> phases, int current, Progress progress) {
        int next = current + 1;
        if (current < 0 || next >= phases.size()) {
            return OptionalInt.empty();
        }
        Optional<PhaseTrigger> trigger = phases.get(next).enterWhen();
        return trigger.isPresent() && trigger.get().holds(progress) ? OptionalInt.of(next) : OptionalInt.empty();
    }
}
