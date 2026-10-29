package io.github.matejkadlec.realmforge.mob.custom;

import com.geckolib.animatable.GeoEntity;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.animation.object.PlayState;
import com.geckolib.constant.DefaultAnimations;
import com.geckolib.util.GeckoLibUtil;
import io.github.matejkadlec.realmforge.mob.ai.Wandering;
import io.github.matejkadlec.realmforge.spell.Spells;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * A custom mob (architecture 14.2): the base of the archetypes in {@code content/mobs/} pick ({@link MeleeMob},
 * {@link CasterMob}). A hostile {@link Monster}, so it gets a level and its scaling like every hostile mob
 * (architecture 14.1), and a GeckoLib animatable: it walks, idles and fights with the animations of its model
 * ({@code assets/realmforge/geckolib/}).
 *
 * <p>The goals come from its {@link CustomMobSpec}: it targets players that hurt it or that it sees, fights as its
 * archetype does, and idles as the spec says ({@link CustomMobSpec.Idle}): it wanders as vanilla's mobs do
 * ({@link Wandering}), or a guard keeps to its post. Vanilla's {@code Mob} constructor
 * registers goals before a subclass's fields exist, so this class registers them itself once the spec is set.
 *
 * <p>Clients see a cast of a cast-time or channelled spell in progress through a synced flag ({@link #isCasting}),
 * which plays the model's {@code attack.cast} animation for as long as the cast runs, whichever archetype casts.
 *
 * <ul>
 *   <li><b>Variants:</b> a mob with variants rolls one by weight when it is created on the server, saves it and
 *   syncs it, so clients draw its texture; it drops its variant's loot table instead of the type's.</li>
 *   <li><b>Sounds:</b> its spec's, played as vanilla plays a mob's; steps at full volume (the sound event carries its
 *   own), or none for a mob that walks silently.</li>
 *   <li><b>Breaking apart:</b> a mob with death particles bursts into its block's break particles when it dies, as a
 *   mined block does, and its body is gone at once instead of falling over.</li>
 * </ul>
 *
 * <p>Light does not matter to it: vanilla's monsters prefer dark spots ({@code Monster#getWalkTargetValue}), which
 * also keeps a spawner from spawning them in a lit room; a custom mob stands in torchlit dungeons.
 */
public abstract class CustomMob extends Monster implements GeoEntity {
    private static final String POST = "realmforge:post";
    private static final String VARIANT = "realmforge:variant";
    private static final EntityDataAccessor<Integer> DATA_VARIANT =
            SynchedEntityData.defineId(CustomMob.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> CASTING =
            SynchedEntityData.defineId(CustomMob.class, EntityDataSerializers.BOOLEAN);
    private static final RawAnimation CAST = RawAnimation.begin().thenLoop("attack.cast");
    /** Break particles per cubic block of the mob's hitbox, and the most a death bursts into. */
    private static final double PARTICLES_PER_BLOCK = 40;
    private static final int MAX_PARTICLES = 160;

    private final CustomMobSpec spec;
    private final AnimatableInstanceCache animations = GeckoLibUtil.createInstanceCache(this);
    /** Where a guard stands: the spot where it first stood on the ground. */
    private @Nullable BlockPos post;

    protected CustomMob(EntityType<? extends CustomMob> type, Level level, CustomMobSpec spec) {
        super(type, level);
        this.spec = spec;
        if (level instanceof ServerLevel) {
            addGoals();
            if (!spec.variants().isEmpty()) {
                entityData.set(DATA_VARIANT, rollVariant(spec.variants()));
            }
        }
    }

    public CustomMobSpec spec() {
        return spec;
    }

    /** Where a guard stands, once it has stood on the ground; empty for a mob that strolls. */
    public Optional<BlockPos> post() {
        return Optional.ofNullable(post);
    }

    /** Whether it is a guard standing at its post. */
    public boolean atPost() {
        return post != null && ReturnToPostGoal.atPost(this, post);
    }

    /** Its variant, on the server and on clients; empty for a mob of one kind. */
    public Optional<CustomMobSpec.Variant> variant() {
        List<CustomMobSpec.Variant> variants = spec.variants();
        return variants.isEmpty() ? Optional.empty()
                : Optional.of(variants.get(Mth.clamp(entityData.get(DATA_VARIANT), 0, variants.size() - 1)));
    }

    /** Makes it the variant {@code id}; whether it has one of that id. Tests and the QA tools pick one this way. */
    public boolean setVariant(String id) {
        for (int index = 0; index < spec.variants().size(); index++) {
            if (spec.variants().get(index).id().equals(id)) {
                entityData.set(DATA_VARIANT, index);
                return true;
            }
        }
        return false;
    }

    /** The loot table of its variant, {@code realmforge:entities/<mob>/<variant>}; empty without variants. */
    public Optional<ResourceKey<LootTable>> variantLootTable() {
        Identifier type = BuiltInRegistries.ENTITY_TYPE.getKey(getType());
        return variant().map(variant -> ResourceKey.create(Registries.LOOT_TABLE,
                type.withPath("entities/" + type.getPath() + "/" + variant.id())));
    }

    private int rollVariant(List<CustomMobSpec.Variant> variants) {
        int total = variants.stream().mapToInt(CustomMobSpec.Variant::weight).sum();
        int roll = getRandom().nextInt(total);
        for (int index = 0; index < variants.size(); index++) {
            roll -= variants.get(index).weight();
            if (roll < 0) {
                return index;
            }
        }
        return variants.size() - 1;
    }

    /** Whether a cast-time or channelled spell of this mob is in progress, on the server and on clients. */
    public boolean isCasting() {
        return entityData.get(CASTING);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder entityData) {
        super.defineSynchedData(entityData);
        entityData.define(DATA_VARIANT, 0);
        entityData.define(CASTING, false);
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
            // Vanilla's wandering, at the Zombified Piglin's speed (mob/ai/Wandering).
            goalSelector.addGoal(Wandering.PRIORITY, new WaterAvoidingRandomStrollGoal(this,
                    Wandering.speedModifier(getAttributeBaseValue(Attributes.MOVEMENT_SPEED))));
            goalSelector.addGoal(Wandering.LOOK_PRIORITY, new RandomLookAroundGoal(this));
        }
        goalSelector.addGoal(Wandering.LOOK_PRIORITY, new LookAtPlayerGoal(this, Player.class, Wandering.LOOK_RANGE));
        addTargetGoals();
    }

    /** The archetype's fighting goals, at priority 1 to 4. */
    protected abstract void addFightGoals();

    /** Whom it fights: whoever hurts it, and the nearest player it sees. */
    protected void addTargetGoals() {
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        if (post == null && spec.idle() == CustomMobSpec.Idle.GUARD && onGround()) {
            post = blockPosition();
        }
        entityData.set(CASTING, Spells.currentCast(this).isPresent());
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
        variant().ifPresent(variant -> output.putString(VARIANT, variant.id()));
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        post = input.read(POST, BlockPos.CODEC).orElse(null);
        input.getString(VARIANT).ifPresent(this::setVariant);
    }

    @Override
    protected void dropFromLootTable(ServerLevel level, DamageSource source, boolean playerKilled) {
        Optional<ResourceKey<LootTable>> variantTable = variantLootTable();
        // A loot table a command set (DeathLootTable) wins over the variant's, as it does over the type's.
        if (variantTable.isPresent() && getLootTable().equals(getType().getDefaultLootTable())) {
            dropFromLootTable(level, source, playerKilled, variantTable.get());
        } else {
            super.dropFromLootTable(level, source, playerKilled);
        }
    }

    @Override
    public void die(DamageSource source) {
        boolean wasDead = dead;
        super.die(source);
        if (!wasDead && dead && level() instanceof ServerLevel level) {
            deathParticles().ifPresent(block -> burst(level, block.defaultBlockState()));
        }
    }

    /** Its break particles, flying to every side from all of its hitbox, as a mined block's do. */
    private void burst(ServerLevel level, BlockState block) {
        double volume = getBbWidth() * getBbWidth() * getBbHeight();
        int count = Mth.clamp((int) Math.round(volume * PARTICLES_PER_BLOCK), 12, MAX_PARTICLES);
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, block), getX(), getY() + getBbHeight() / 2,
                getZ(), count, getBbWidth() / 3, getBbHeight() / 3, getBbWidth() / 3, 0.15);
    }

    /** A mob that breaks apart is gone at once: no falling over, no smoke. */
    @Override
    protected void tickDeath() {
        if (deathParticles().isEmpty()) {
            super.tickDeath();
            return;
        }
        deathTime++;
        if (!level().isClientSide() && !isRemoved()) {
            remove(RemovalReason.KILLED);
        }
    }

    private Optional<Block> deathParticles() {
        return spec.deathParticles().map(BuiltInRegistries.BLOCK::getValue).filter(block -> block != Blocks.AIR);
    }

    @Override
    protected @Nullable SoundEvent getAmbientSound() {
        return sound(spec.sounds().ambient(), super.getAmbientSound());
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return sound(spec.sounds().hurt(), super.getHurtSound(source));
    }

    @Override
    protected SoundEvent getDeathSound() {
        return sound(spec.sounds().death(), super.getDeathSound());
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState block) {
        if (spec.sounds().silentSteps()) {
            return;
        }
        SoundEvent step = sound(spec.sounds().step(), null);
        if (step == null) {
            super.playStepSound(pos, block);
        } else {
            playSound(step, 1.0F, 1.0F);
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
        controllers.add(new AnimationController<CustomMob>("Cast",
                test -> test.animatable().isCasting() ? test.setAndContinue(CAST) : PlayState.STOP));
    }

    /** The archetype's animation controllers for its attacks, besides walking and idling. */
    protected abstract void addActionControllers(AnimatableManager.ControllerRegistrar controllers);

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return animations;
    }
}
