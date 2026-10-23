package io.github.matejkadlec.realmforge.mob.custom;

import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.animation.object.PlayState;
import io.github.matejkadlec.realmforge.spell.Spells;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/**
 * The caster archetype (architecture 14.2, {@code archetype: caster}): it casts its spells at its target and keeps
 * its distance ({@link CasterFightGoal}). Clients see a cast in progress through a synced flag, which plays the
 * model's {@code attack.cast} animation for as long as the cast runs.
 */
public class CasterMob extends CustomMob {
    private static final EntityDataAccessor<Boolean> CASTING =
            SynchedEntityData.defineId(CasterMob.class, EntityDataSerializers.BOOLEAN);
    private static final RawAnimation CAST = RawAnimation.begin().thenLoop("attack.cast");

    /** The farthest its spells reach, read from the spell registry once; negative until then. */
    private double reach = -1;

    public CasterMob(EntityType<? extends CasterMob> type, Level level, CustomMobSpec spec) {
        super(type, level, spec);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder entityData) {
        super.defineSynchedData(entityData);
        entityData.define(CASTING, false);
    }

    @Override
    protected void addFightGoals() {
        goalSelector.addGoal(2, new CasterFightGoal(this));
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        entityData.set(CASTING, Spells.currentCast(this).isPresent());
    }

    /** Whether a cast-time or channelled spell of this mob is in progress, on the server and on clients. */
    public boolean isCasting() {
        return entityData.get(CASTING);
    }

    /** The farthest any of its spells reaches, in blocks; 0 when none of them exists. */
    double reach() {
        if (reach < 0) {
            reach = spec().spells().stream()
                    .flatMap(spell -> Spells.get(registryAccess(), spell).stream())
                    .mapToDouble(spell -> spell.value().targeting().reach())
                    .max().orElse(0);
        }
        return reach;
    }

    @Override
    protected void addActionControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<CasterMob>("Cast",
                test -> test.animatable().isCasting() ? test.setAndContinue(CAST) : PlayState.STOP));
    }
}
