package io.github.matejkadlec.realmforge.mob.custom;

import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;

/**
 * Vanilla's melee attack (it walks to its target and hits it once in reach and in sight), at a pace of its own: a hit
 * every {@code intervalTicks} instead of vanilla's 20 ({@code melee.attack_interval_seconds}). Vanilla keeps its
 * cooldown private and clears it whenever the goal starts again, which it does every second while the mob stands at
 * its target (its path is done, so the goal stops), so this goal keeps its own by game time.
 */
final class PacedMeleeAttackGoal extends MeleeAttackGoal {
    private final int intervalTicks;
    /** The game time of its next hit at the earliest. */
    private long nextAttack;

    PacedMeleeAttackGoal(PathfinderMob mob, int intervalTicks) {
        super(mob, 1.0, false);
        this.intervalTicks = intervalTicks;
    }

    @Override
    protected void resetAttackCooldown() {
        super.resetAttackCooldown();
        nextAttack = mob.level().getGameTime() + intervalTicks;
    }

    @Override
    protected boolean isTimeToAttack() {
        return mob.level().getGameTime() >= nextAttack;
    }

    @Override
    protected int getTicksUntilNextAttack() {
        return (int) Math.max(0, nextAttack - mob.level().getGameTime());
    }

    @Override
    protected int getAttackInterval() {
        return intervalTicks;
    }
}
