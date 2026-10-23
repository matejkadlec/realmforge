package io.github.matejkadlec.realmforge.mob.custom;

import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;

/**
 * A guard's idle ({@link CustomMobSpec.Idle#GUARD}, research BM-6): with no target it walks back to its post and
 * stands there like the statue it is, instead of wandering. Fighting goals run first, so it never gives up a target
 * for its post.
 */
final class ReturnToPostGoal extends Goal {
    /** Within this many blocks of its post a guard counts as standing at it. */
    private static final double AT_POST = 1.5;
    private static final double SPEED = 1.0;

    private final CustomMob mob;

    ReturnToPostGoal(CustomMob mob) {
        this.mob = mob;
        setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        return mob.getTarget() == null && mob.post().filter(post -> !atPost(post)).isPresent();
    }

    @Override
    public boolean canContinueToUse() {
        return mob.getTarget() == null && !mob.getNavigation().isDone();
    }

    @Override
    public void start() {
        mob.post().ifPresent(post -> mob.getNavigation().moveTo(post.getX() + 0.5, post.getY(), post.getZ() + 0.5,
                SPEED));
    }

    @Override
    public void stop() {
        mob.getNavigation().stop();
    }

    private boolean atPost(BlockPos post) {
        return mob.position().closerThan(post.getBottomCenter(), AT_POST);
    }
}
