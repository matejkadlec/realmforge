package io.github.matejkadlec.realmforge.mob.custom;

import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.animation.object.PlayState;
import io.github.matejkadlec.realmforge.spell.CastEnd;
import io.github.matejkadlec.realmforge.spell.CastEndedEvent;
import io.github.matejkadlec.realmforge.spell.Spells;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/**
 * The caster archetype (architecture 14.2, {@code archetype: caster}): it casts its spells at its target, standing
 * still and facing it, goes after a target it cannot reach, and steps aside between its casts
 * ({@link CasterFightGoal}). Clients see a cast in progress through a synced flag, which plays the model's
 * {@code attack.cast} animation for as long as the cast runs; the renderer draws the projectile a spell forms.
 */
public class CasterMob extends CustomMob {
    private static final EntityDataAccessor<Boolean> CASTING =
            SynchedEntityData.defineId(CasterMob.class, EntityDataSerializers.BOOLEAN);
    private static final RawAnimation CAST = RawAnimation.begin().thenLoop("attack.cast");

    /** The shortest reach of its spells, read from the spell registry once; negative until then. */
    private double reach = -1;
    /** How its last cast ended, until its fight goal reads it. Server only. */
    private @Nullable CastEnd castEnd;

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

    /**
     * The shortest reach of its spells, in blocks: within it, every one of them reaches its target. 0 when none of them
     * exists.
     */
    public double reach() {
        if (reach < 0) {
            reach = spec().spells().stream()
                    .flatMap(spell -> Spells.get(registryAccess(), spell).stream())
                    .mapToDouble(spell -> spell.value().targeting().reach())
                    .min().orElse(0);
        }
        return reach;
    }

    /** How its last cast ended, once: the fight goal steps aside after a completed one. */
    Optional<CastEnd> takeCastEnd() {
        Optional<CastEnd> end = Optional.ofNullable(castEnd);
        castEnd = null;
        return end;
    }

    /** {@code CastEndedEvent}: a caster's cast ended (registered by {@code MobFeature}). */
    public static void onCastEnded(CastEndedEvent event) {
        if (event.getCaster() instanceof CasterMob mob) {
            mob.castEnd = event.getEnd();
        }
    }

    @Override
    protected void addActionControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<CasterMob>("Cast",
                test -> test.animatable().isCasting() ? test.setAndContinue(CAST) : PlayState.STOP));
    }
}
