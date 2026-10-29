package io.github.matejkadlec.realmforge.mob.custom;

import io.github.matejkadlec.realmforge.mob.ai.Facing;
import io.github.matejkadlec.realmforge.mob.ai.KiteStep;
import io.github.matejkadlec.realmforge.spell.CastEnd;
import io.github.matejkadlec.realmforge.spell.CastResult;
import io.github.matejkadlec.realmforge.spell.Spell;
import io.github.matejkadlec.realmforge.spell.SpellCaster;
import io.github.matejkadlec.realmforge.spell.Spells;
import io.github.matejkadlec.realmforge.spell.Targets;
import java.util.EnumSet;
import java.util.Optional;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;

/**
 * A caster's fight (architecture 14.2), as a Skeleton's with spells:
 *
 * <ul>
 *   <li><b>Casting,</b> it stands still and faces its target. The spell itself ends the cast when the target gets out
 *   of sight or out of range ({@code keep_sight}, {@code keep_range_percent}), and the caster then goes after it.</li>
 *   <li><b>In reach and in sight,</b> it stands, faces its target and casts its spells through {@link SpellCaster}, as
 *   players do: a spell on cooldown, out of range or out of sight is refused, and it tries the next. Mobs pay no cost,
 *   so their cooldowns pace them (architecture 11.2).</li>
 *   <li><b>Out of reach or out of sight,</b> it closes in ({@link CasterTactics}) until it can cast again.</li>
 *   <li><b>After a cast that completed,</b> it takes one quick kiting step ({@link KiteStep}), when its YAML gives it
 *   kiting: away from its target while that keeps it in reach, otherwise around it.</li>
 * </ul>
 */
final class CasterFightGoal extends Goal {
    /** How often, in ticks, a caster that is not casting tries its spells. */
    private static final int CAST_INTERVAL = 5;
    /** How often, in ticks, a caster closing in finds a new path to its target. */
    private static final int REPATH_INTERVAL = 10;
    private static final double SPEED = 1.0;

    private final CasterMob mob;
    private final Optional<KiteStep> kite;
    private int castDelay;
    private int repathDelay;
    private boolean closingIn;

    CasterFightGoal(CasterMob mob) {
        this.mob = mob;
        this.kite = mob.spec().kiting().map(rules -> new KiteStep(mob, rules));
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
        closingIn = false;
        mob.takeCastEnd();
    }

    @Override
    public void stop() {
        kite.ifPresent(KiteStep::stop);
        mob.getNavigation().stop();
    }

    @Override
    public void tick() {
        LivingEntity target = mob.getTarget();
        if (target == null) {
            return;
        }
        double reach = mob.reach();
        if (Spells.currentCast(mob).isPresent()) {
            mob.getNavigation().stop();
            face(target);
            return;
        }
        if (mob.takeCastEnd().filter(CastEnd::completed).isPresent()) {
            stepAside(target, reach);
        }
        if (kite.filter(KiteStep::tick).isPresent()) {
            mob.getLookControl().setLookAt(target, 30.0F, 30.0F);
            return;
        }
        boolean sees = mob.getSensing().hasLineOfSight(target);
        CasterTactics.Move move = CasterTactics.move(Targets.distance(mob, target), reach, sees, closingIn);
        if (move == CasterTactics.Move.CLOSE_IN) {
            if (!closingIn) {
                repathDelay = 0;
            }
            closingIn = true;
            closeIn(target);
            return;
        }
        if (closingIn) {
            closingIn = false;
            mob.getNavigation().stop();
        }
        face(target);
        if (--castDelay <= 0) {
            castDelay = CAST_INTERVAL;
            castAt(target, reach);
        }
    }

    /** Casts its first spell that the engine lets it cast; an instant one completes at once, so it steps aside. */
    private void castAt(LivingEntity target, double reach) {
        for (ResourceKey<Spell> spell : mob.spec().spells()) {
            CastResult result = SpellCaster.cast(mob, spell, target);
            if (result.success()) {
                if (!result.begun()) {
                    stepAside(target, reach);
                }
                return;
            }
        }
    }

    private void stepAside(LivingEntity target, double reach) {
        kite.ifPresent(step -> step.start(target, reach));
    }

    /** Turns its body, head and eyes to its target at once, as it stands. */
    private void face(LivingEntity target) {
        Facing.face(mob, target);
    }

    private void closeIn(LivingEntity target) {
        mob.getLookControl().setLookAt(target, 30.0F, 30.0F);
        if (--repathDelay <= 0) {
            repathDelay = REPATH_INTERVAL;
            mob.getNavigation().moveTo(target, SPEED);
        }
    }
}
