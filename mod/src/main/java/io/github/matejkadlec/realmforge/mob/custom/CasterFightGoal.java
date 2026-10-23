package io.github.matejkadlec.realmforge.mob.custom;

import io.github.matejkadlec.realmforge.spell.CastResult;
import io.github.matejkadlec.realmforge.spell.Spell;
import io.github.matejkadlec.realmforge.spell.SpellCaster;
import io.github.matejkadlec.realmforge.spell.Spells;
import java.util.EnumSet;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.phys.Vec3;

/**
 * A caster's fight (architecture 14.2): it moves as {@link CasterTactics} says, backing away from a target that
 * comes too near and closing in on one out of reach or out of sight, and casts its spells at its target through
 * {@link SpellCaster}, as players do: a spell on cooldown, out of range or out of sight is refused, and it tries the
 * next. Mobs pay no cost, so their cooldowns pace them (architecture 11.2).
 */
final class CasterFightGoal extends Goal {
    /** How often, in ticks, a caster that is not casting tries its spells. */
    private static final int CAST_INTERVAL = 5;
    /** How often, in ticks, a caster closing in finds a new path to its target. */
    private static final int REPATH_INTERVAL = 10;
    private static final double SPEED = 1.0;
    private static final double BACK_AWAY_SPEED = 1.2;
    private static final int BACK_AWAY_BLOCKS = 8;
    private static final int BACK_AWAY_HEIGHT = 4;

    private final CasterMob mob;
    private int castDelay;
    private int repathDelay;
    private CasterTactics.Move lastMove = CasterTactics.Move.HOLD;

    CasterFightGoal(CasterMob mob) {
        this.mob = mob;
        setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        LivingEntity target = mob.getTarget();
        return target != null && target.isAlive();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void start() {
        castDelay = 0;
        repathDelay = 0;
        lastMove = CasterTactics.Move.HOLD;
    }

    @Override
    public void stop() {
        mob.getNavigation().stop();
    }

    @Override
    public void tick() {
        LivingEntity target = mob.getTarget();
        if (target == null) {
            return;
        }
        mob.getLookControl().setLookAt(target, 30.0F, 30.0F);
        boolean sees = mob.getSensing().hasLineOfSight(target);
        double distance = mob.distanceTo(target);
        CasterTactics.Move move = CasterTactics.move(distance, mob.spec().keepDistance(), mob.reach(), sees);
        if (move != lastMove) {
            // A new move drops the old path: a caster that was closing in backs away at once.
            mob.getNavigation().stop();
            repathDelay = 0;
            lastMove = move;
        }
        switch (move) {
            case BACK_AWAY -> backAway(target);
            case CLOSE_IN -> closeIn(target);
            case HOLD -> mob.getNavigation().stop();
        }
        if (--castDelay <= 0 && Spells.currentCast(mob).isEmpty()) {
            castDelay = CAST_INTERVAL;
            for (ResourceKey<Spell> spell : mob.spec().spells()) {
                CastResult result = SpellCaster.cast(mob, spell, target);
                if (result.success()) {
                    break;
                }
            }
        }
    }

    private void backAway(LivingEntity target) {
        if (!mob.getNavigation().isDone()) {
            return;
        }
        Vec3 away = DefaultRandomPos.getPosAway(mob, BACK_AWAY_BLOCKS, BACK_AWAY_HEIGHT, target.position());
        if (away != null) {
            mob.getNavigation().moveTo(away.x, away.y, away.z, BACK_AWAY_SPEED);
        }
    }

    private void closeIn(LivingEntity target) {
        if (--repathDelay <= 0) {
            repathDelay = REPATH_INTERVAL;
            mob.getNavigation().moveTo(target, SPEED);
        }
    }
}
