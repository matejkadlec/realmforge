package io.github.matejkadlec.realmforge.mob.custom;

import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.object.PlayState;
import com.geckolib.constant.DefaultAnimations;
import io.github.matejkadlec.realmforge.mob.ai.KnockUp;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * The melee archetype (architecture 14.2, {@code archetype: melee}): it walks up to its target and hits it with its
 * attack damage, as vanilla's melee monsters do, at its own pace ({@link PacedMeleeAttackGoal}). Each attack plays the
 * model's {@code attack.swing} animation to its end, a triggered animation: vanilla's arm swing lasts only 6 ticks, and
 * a stone golem raises and drops its arms slower than that (Matěj's playtest, 2026-10-24). With
 * {@code knock_up_blocks}, each hit throws its target that high on top of vanilla's push back (less the target's
 * knockback resistance).
 */
public class MeleeMob extends CustomMob {
    private static final String ATTACK_CONTROLLER = "Attack";
    private static final String SWING = "swing";

    public MeleeMob(EntityType<? extends MeleeMob> type, Level level, CustomMobSpec spec) {
        super(type, level, spec);
    }

    @Override
    protected void addFightGoals() {
        goalSelector.addGoal(2, new PacedMeleeAttackGoal(this, spec().melee().attackIntervalTicks()));
    }

    @Override
    public boolean doHurtTarget(ServerLevel level, Entity target) {
        boolean hit = super.doHurtTarget(level, target);
        double knockUp = spec().melee().knockUpBlocks();
        if (hit && knockUp > 0 && target instanceof LivingEntity living && living.isAlive()) {
            double resisted = knockUp * (1 - living.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE));
            if (resisted > 0) {
                Vec3 motion = living.getDeltaMovement();
                living.setDeltaMovement(motion.x, KnockUp.velocity(resisted), motion.z);
                // A player moves on its client: the server sends it the new motion.
                living.hurtMarked = true;
            }
        }
        return hit;
    }

    /** Every attack swings its arms (MeleeAttackGoal swings before it hits): clients play the whole animation. */
    @Override
    public void swing(InteractionHand hand, boolean sendToSwingingEntity) {
        super.swing(hand, sendToSwingingEntity);
        if (level() instanceof ServerLevel) {
            triggerAnim(ATTACK_CONTROLLER, SWING);
        }
    }

    @Override
    protected void addActionControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<MeleeMob>(ATTACK_CONTROLLER, test -> PlayState.STOP)
                .triggerableAnim(SWING, DefaultAnimations.ATTACK_SWING));
    }
}
