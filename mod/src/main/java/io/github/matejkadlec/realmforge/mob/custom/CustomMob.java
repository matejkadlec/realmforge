package io.github.matejkadlec.realmforge.mob.custom;

import com.geckolib.animatable.GeoEntity;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.constant.DefaultAnimations;
import com.geckolib.util.GeckoLibUtil;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * A custom mob (architecture 14.2): the base of the archetypes in {@code content/mobs/} pick ({@link MeleeMob},
 * {@link CasterMob}). A hostile {@link Monster}, so it gets a level and its scaling like every hostile mob
 * (architecture 14.1), and a GeckoLib animatable: it walks, idles and fights with the animations of its model
 * ({@code assets/realmforge/geckolib/}).
 *
 * <p>The goals come from its {@link CustomMobSpec}: it targets players that hurt it or that it sees, fights as its
 * archetype does, and idles as the spec says ({@link CustomMobSpec.Idle}). Vanilla's {@code Mob} constructor
 * registers goals before a subclass's fields exist, so this class registers them itself once the spec is set.
 *
 * <p>Light does not matter to it: vanilla's monsters prefer dark spots ({@code Monster#getWalkTargetValue}), which
 * also keeps a spawner from spawning them in a lit room; a custom mob stands in torchlit dungeons.
 */
public abstract class CustomMob extends Monster implements GeoEntity {
    private static final String POST = "realmforge:post";

    private final CustomMobSpec spec;
    private final AnimatableInstanceCache animations = GeckoLibUtil.createInstanceCache(this);
    /** Where a guard stands: the spot where it first stood on the ground. */
    private @Nullable BlockPos post;

    protected CustomMob(EntityType<? extends CustomMob> type, Level level, CustomMobSpec spec) {
        super(type, level);
        this.spec = spec;
        if (level instanceof ServerLevel) {
            addGoals();
        }
    }

    public CustomMobSpec spec() {
        return spec;
    }

    /** Where a guard stands, once it has stood on the ground; empty for a mob that strolls. */
    public Optional<BlockPos> post() {
        return Optional.ofNullable(post);
    }

    @Override
    protected final void registerGoals() {
        // Called by Mob's constructor, before the spec is set; see addGoals.
    }

    private void addGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        addFightGoals();
        if (spec.idle() == CustomMobSpec.Idle.GUARD) {
            goalSelector.addGoal(5, new ReturnToPostGoal(this));
        } else {
            goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8));
            goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        }
        goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0F));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    /** The archetype's fighting goals, at priority 2 to 4. */
    protected abstract void addFightGoals();

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        if (post == null && spec.idle() == CustomMobSpec.Idle.GUARD && onGround()) {
            post = blockPosition();
        }
    }

    @Override
    public float getWalkTargetValue(BlockPos pos, LevelReader level) {
        return 0.0F;
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        if (post != null) {
            output.store(POST, BlockPos.CODEC, post);
        }
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        post = input.read(POST, BlockPos.CODEC).orElse(null);
    }

    @Override
    protected @Nullable SoundEvent getAmbientSound() {
        return sound(spec.ambientSound(), super.getAmbientSound());
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return sound(spec.hurtSound(), super.getHurtSound(source));
    }

    @Override
    protected SoundEvent getDeathSound() {
        return sound(spec.deathSound(), super.getDeathSound());
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState block) {
        SoundEvent step = sound(spec.stepSound(), null);
        if (step == null) {
            super.playStepSound(pos, block);
        } else {
            playSound(step, 0.15F, 1.0F);
        }
    }

    /** The sound event of {@code id}, or {@code fallback} when the spec names none or the game has no such sound. */
    private static @Nullable SoundEvent sound(Optional<Identifier> id, @Nullable SoundEvent fallback) {
        if (id.isEmpty()) {
            return fallback;
        }
        SoundEvent sound = BuiltInRegistries.SOUND_EVENT.getValue(id.get());
        return sound == null ? fallback : sound;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(DefaultAnimations.genericWalkIdleController());
        addActionControllers(controllers);
    }

    /** The archetype's animation controllers for its attacks, besides walking and idling. */
    protected abstract void addActionControllers(AnimatableManager.ControllerRegistrar controllers);

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return animations;
    }
}
