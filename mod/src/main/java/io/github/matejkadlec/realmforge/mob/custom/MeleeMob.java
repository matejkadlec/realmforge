package io.github.matejkadlec.realmforge.mob.custom;

import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.constant.DefaultAnimations;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.level.Level;

/**
 * The melee archetype (architecture 14.2, {@code archetype: melee}): it walks up to its target and hits it with its
 * attack damage, as vanilla's melee monsters do. Each hit plays the model's {@code attack.swing} animation.
 */
public class MeleeMob extends CustomMob {
    public MeleeMob(EntityType<? extends MeleeMob> type, Level level, CustomMobSpec spec) {
        super(type, level, spec);
    }

    @Override
    protected void addFightGoals() {
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0, false));
    }

    @Override
    protected void addActionControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(DefaultAnimations.<MeleeMob>genericAttackAnimation(DefaultAnimations.ATTACK_SWING));
    }
}
