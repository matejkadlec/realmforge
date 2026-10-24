package io.github.matejkadlec.realmforge.mob.ai;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * One mob's kiting steps (architecture 14.2, research BM-22): a goal calls {@link #start} between two attacks, and the
 * mob walks to the first place {@link Kiting} offers that a short path reaches and that keeps its target in sight, at
 * its {@link KiteRules}' speed. It keeps circling one way while it can and turns to the other side once that side is
 * blocked. Server only; the goal that owns it ticks it.
 */
public final class KiteStep {
    /** A step that takes longer than this, in ticks, ends where the mob stands. */
    private static final int MAX_TICKS = 40;
    /** How many path nodes a step may take per block of its length, so it never detours around a wall. */
    private static final double NODES_PER_BLOCK = 2;

    private final PathfinderMob mob;
    private final KiteRules rules;
    private boolean clockwise;
    private boolean active;
    private int ticks;

    public KiteStep(PathfinderMob mob, KiteRules rules) {
        this.mob = mob;
        this.rules = rules;
        this.clockwise = mob.getRandom().nextBoolean();
    }

    /**
     * Starts a step that keeps {@code target} within {@code range} blocks and in sight; whether one started. No place
     * fits when walls or drops close every side, and the mob stays where it is.
     */
    public boolean start(LivingEntity target, double range) {
        int maxNodes = (int) Math.ceil(rules.stepBlocks() * NODES_PER_BLOCK) + 1;
        for (Vec3 place : Kiting.steps(mob.position(), target.position(), range, rules.stepBlocks(), clockwise)) {
            Path path = mob.getNavigation().createPath(BlockPos.containing(place), 0);
            Node end = path == null ? null : path.getEndNode();
            if (end == null || !path.canReach() || path.getNodeCount() > maxNodes
                    || !seesFrom(Vec3.atBottomCenterOf(end.asBlockPos()), target)) {
                continue;
            }
            if (mob.getNavigation().moveTo(path, rules.speed())) {
                active = true;
                ticks = 0;
                return true;
            }
        }
        clockwise = !clockwise;
        return false;
    }

    /** Whether the step still runs; it ends when the mob arrives, its path fails, or it takes too long. */
    public boolean tick() {
        if (active && (++ticks > MAX_TICKS || mob.getNavigation().isDone())) {
            stop();
        }
        return active;
    }

    /** Whether a step runs. */
    public boolean active() {
        return active;
    }

    /** Ends the step where the mob stands. */
    public void stop() {
        if (active) {
            active = false;
            mob.getNavigation().stop();
        }
    }

    /** Whether the mob would see {@code target} standing at {@code feet}: no block between its eyes and the target's. */
    private boolean seesFrom(Vec3 feet, LivingEntity target) {
        Vec3 eyes = feet.add(0, mob.getEyeHeight(), 0);
        return mob.level().clip(new ClipContext(eyes, target.getEyePosition(), ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, mob)).getType() == HitResult.Type.MISS;
    }
}
