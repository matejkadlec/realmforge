package io.github.matejkadlec.realmforge.boss;

import io.github.matejkadlec.realmforge.mob.ai.Facing;
import io.github.matejkadlec.realmforge.spell.Spells;
import java.util.EnumSet;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;

/**
 * A boss stands while it casts, facing its target, and while it changes phase (architecture 14.3). It takes the
 * movement and look from its melee goal, so a boss never hits in melee during a cast.
 */
final class BossHoldGoal extends Goal {
    private final BossMob boss;

    BossHoldGoal(BossMob boss) {
        this.boss = boss;
        setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK, Goal.Flag.JUMP));
    }

    @Override
    public boolean canUse() {
        return boss.changingPhase() || Spells.currentCast(boss).isPresent();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void start() {
        boss.getNavigation().stop();
    }

    @Override
    public void tick() {
        boss.getNavigation().stop();
        LivingEntity target = boss.getTarget();
        if (target != null && target.isAlive()) {
            Facing.face(boss, target);
        }
    }
}
