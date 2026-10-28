package io.github.matejkadlec.realmforge.spell;

import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.entity.IEntityWithComplexSpawn;
import net.neoforged.neoforge.event.EventHooks;

/**
 * The one generic projectile of every spell (architecture 11.3), launched by {@link ProjectileEffect}: it flies
 * straight at its speed, without gravity or drag, and stops at the first living entity in its way that is neither its
 * caster nor one of the caster's allies (RLM-344). That entity takes the projectile's effects, delivered by the
 * projectile itself (the hit's direct entity), when it can be damaged, hostile and passive mobs alike; one that cannot
 * be damaged (invulnerable, a player in creative mode) or that is of the tag it spares stops it untouched. A block
 * stops it too, without its effects. Where it hits a block or an entity, it makes its impact: a sound and a block's
 * break particles.
 *
 * <p>A player's projectile can home in on the enemy aim assistance gave it (RLM-345, ADR 0055): every tick it turns
 * toward that enemy's middle by at most {@link AimAssist#TURN_PER_TICK}, at the same speed, a gentle curve that hits a
 * mob on the move; a block still stops it. It flies on straight once the enemy is dead, gone or behind it.
 *
 * <p>Nothing flies forever: it is gone at the end of its lifetime when it has one, once its caster leaves its level
 * (logging out, a mob's death), above the build height unless it flies down (RLM-446: a caster on the top block
 * layer, or flying above it, still hits a mob below), where entities stop moving (beyond every player's simulation
 * distance, which equals the render distance by default, or in a chunk that is not loaded), and in the void, which
 * vanilla removes 64 blocks below the world's bottom ({@code Entity#checkBelowWorld}).
 *
 * <ul>
 *   <li><b>Never saved:</b> it carries a live cast (the caster, the spell), so a chunk that unloads or a server that
 *   stops drops it.</li>
 *   <li><b>Synced:</b> its size, for the hitbox, and with its spawn its look: the texture of its spell's appearance, or
 *   none for a trail of sparks. The client moves it and draws it; everything else lives on the server, and commands
 *   cannot summon one.</li>
 * </ul>
 */
public final class SpellProjectile extends Projectile implements IEntityWithComplexSpawn {
    private static final EntityDataAccessor<Float> DATA_SIZE =
            SynchedEntityData.defineId(SpellProjectile.class, EntityDataSerializers.FLOAT);
    /** Break particles a projectile's impact bursts into. */
    private static final int IMPACT_PARTICLES = 16;

    @Nullable
    private EffectContext cast;
    private List<SpellEffect> effects = List.of();
    private Optional<TagKey<EntityType<?>>> spares = Optional.empty();
    private Optional<ProjectileEffect.Impact> impact = Optional.empty();
    /** The texture of its appearance, on the server and its clients; empty for a trail of sparks. */
    private Optional<Identifier> texture = Optional.empty();
    /** How many ticks it flies before it fizzles; 0 for no lifetime. */
    private int lifetimeTicks;
    private int age;
    /** The enemy it homes in on, on the server; none for a straight flight. */
    @Nullable
    private LivingEntity homing;

    public SpellProjectile(EntityType<? extends SpellProjectile> type, Level level) {
        super(type, level);
    }

    /**
     * Launches {@code projectile}'s projectile from {@code origin} (its middle) at {@code velocity} (blocks per
     * tick), homing in on {@code homing} if it is not null.
     */
    static void launch(EffectContext context, ProjectileEffect projectile, Vec3 origin, Vec3 velocity,
            @Nullable LivingEntity homing) {
        LivingEntity caster = context.caster();
        SpellProjectile entity = new SpellProjectile(SpellEntities.PROJECTILE.get(), context.level());
        entity.setOwner(caster);
        entity.cast = context.through(context.level(), entity);
        entity.effects = projectile.effects();
        entity.spares = projectile.spares();
        entity.impact = projectile.impact();
        entity.texture = projectile.appearance().flatMap(appearance -> context.spell().unwrapKey())
                .map(SpellVisuals::projectileTexture);
        entity.lifetimeTicks = projectile.lifetimeTicks().orElse(0);
        entity.homing = homing;
        entity.getEntityData().set(DATA_SIZE, (float) projectile.size());
        entity.setPos(origin.x, origin.y - projectile.size() / 2, origin.z);
        entity.setDeltaMovement(velocity);
        entity.updateRotation();
        context.level().addFreshEntity(entity);
    }

    /** The enemy it homes in on, if any; on the server. */
    public Optional<LivingEntity> homing() {
        return Optional.ofNullable(homing);
    }

    /** The texture its client draws it with; empty for a trail of sparks. */
    public Optional<Identifier> texture() {
        return texture;
    }

    @Override
    public void writeSpawnData(RegistryFriendlyByteBuf buffer) {
        ByteBufCodecs.optional(Identifier.STREAM_CODEC).encode(buffer, texture);
    }

    @Override
    public void readSpawnData(RegistryFriendlyByteBuf buffer) {
        texture = ByteBufCodecs.optional(Identifier.STREAM_CODEC).decode(buffer);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder entityData) {
        entityData.define(DATA_SIZE, 0.5F);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
        if (DATA_SIZE.equals(accessor)) {
            refreshDimensions();
        }
        super.onSyncedDataUpdated(accessor);
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        float size = getEntityData().get(DATA_SIZE);
        return EntityDimensions.scalable(size, size);
    }

    @Override
    public void tick() {
        super.tick();
        if (level() instanceof ServerLevel serverLevel && (cast == null || gone(cast.caster())
                || lifetimeTicks > 0 && ++age > lifetimeTicks || aboveTheWorld(serverLevel))) {
            discard();
            return;
        }
        if (!level().isClientSide()) {
            home();
            HitResult hit = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
            if (hit.getType() != HitResult.Type.MISS && !EventHooks.onProjectileImpact(this, hit)) {
                hitTargetOrDeflectSelf(hit);
            }
            if (isRemoved()) {
                return;
            }
        }
        Vec3 movement = getDeltaMovement();
        if (level() instanceof ServerLevel serverLevel && !moving(serverLevel, position().add(movement))) {
            discard();
            return;
        }
        setPos(getX() + movement.x, getY() + movement.y, getZ() + movement.z);
        updateRotation();
        if (level().isClientSide() && texture.isEmpty()) {
            level().addParticle(ParticleTypes.END_ROD, getX(), getY() + getBbHeight() / 2, getZ(), 0, 0, 0);
        }
    }

    /**
     * Turns toward the enemy it homes in on, by at most {@link AimAssist#TURN_PER_TICK}, and tells its clients at once,
     * so they draw the curve the server flies. An enemy that died, left, or that it passed ends the homing.
     */
    private void home() {
        LivingEntity target = homing;
        if (target == null) {
            return;
        }
        Vec3 velocity = getDeltaMovement();
        Vec3 toward = target.getBoundingBox().getCenter().subtract(position().add(0, getBbHeight() / 2, 0));
        if (!target.isAlive() || target.isRemoved() || target.level() != level() || toward.dot(velocity) <= 0) {
            homing = null;
            return;
        }
        Vec3 turned = AimAssist.turn(velocity, toward, AimAssist.TURN_PER_TICK);
        if (!turned.equals(velocity)) {
            setDeltaMovement(turned);
            needsSync = true;
        }
    }

    /**
     * Called every tick while its chunk is loaded, even where entities no longer move: there a projectile would hang
     * in the air until its chunk unloads, so it goes at once.
     */
    @Override
    public void checkDespawn() {
        if (level() instanceof ServerLevel serverLevel && !moving(serverLevel, position())) {
            discard();
        }
    }

    /**
     * Whether it is above the build height, where no block can be, and not flying down: nothing up there for it to
     * hit, and it is not coming back. One that flies down from up there flies on to what is below (RLM-446).
     */
    private boolean aboveTheWorld(ServerLevel level) {
        return getY() > level.getMaxY() + 1 && getDeltaMovement().y >= 0;
    }

    /**
     * Whether entities move at {@code where}: in a loaded chunk within a player's simulation distance (or a forced
     * one). Beyond it nobody sees the projectile any more, and it would stop there.
     */
    private static boolean moving(ServerLevel level, Vec3 where) {
        return level.isPositionEntityTicking(BlockPos.containing(where));
    }

    /** Whether {@code caster} left this projectile's level: logged out, changed dimension, or a mob that died. */
    private boolean gone(LivingEntity caster) {
        return caster.isRemoved() || caster.level() != level();
    }

    /**
     * A living entity in its way, alive and no spectator, never the caster or one of its allies (who let it pass) nor
     * another spell entity. An entity it spares is stopped by it whether it is an ally or not.
     */
    @Override
    protected boolean canHitEntity(Entity entity) {
        return cast != null && entity instanceof LivingEntity living && living != cast.caster() && living.isAlive()
                && !living.isSpectator() && (spared(living) || !Targets.allied(cast.caster(), living))
                && super.canHitEntity(entity);
    }

    /** Whether {@code entity} is of the tag this projectile spares: it stops the projectile, untouched. */
    private boolean spared(Entity entity) {
        return spares.filter(entity::is).isPresent();
    }

    /**
     * The entity it reached takes its effects if it can be damaged; one that cannot (invulnerable, a player in
     * creative mode) or that it spares only stops it. Either way it makes its impact and is gone.
     */
    @Override
    protected void onHitEntity(EntityHitResult hitResult) {
        if (cast != null && hitResult.getEntity() instanceof LivingEntity living
                && level() instanceof ServerLevel serverLevel) {
            if (!spared(living) && living.canBeSeenAsEnemy()) {
                EffectContext context = cast.through(serverLevel, this);
                SpellTarget target = SpellTarget.hit(living, getDeltaMovement().normalize());
                for (SpellEffect effect : effects) {
                    effect.apply(context, target);
                }
            }
            impact(serverLevel, position().add(0, getBbHeight() / 2, 0));
        }
        discard();
    }

    /** A block stops it, without its effects and without touching the block (no bell, no target block). */
    @Override
    protected void onHitBlock(BlockHitResult hitResult) {
        if (level() instanceof ServerLevel serverLevel) {
            impact(serverLevel, hitResult.getLocation());
        }
        discard();
    }

    /** Its impact at {@code where}: its sound, and its block's break particles flying out. */
    private void impact(ServerLevel level, Vec3 where) {
        impact.ifPresent(effect -> {
            effect.sound().map(BuiltInRegistries.SOUND_EVENT::getValue).ifPresent((SoundEvent sound) -> level
                    .playSound(null, where.x, where.y, where.z, sound, soundSourceOfCaster(), 1.0F, 1.0F));
            effect.particles().map(BuiltInRegistries.BLOCK::getValue).filter(block -> block != Blocks.AIR)
                    .ifPresent((Block block) -> level.sendParticles(
                            new BlockParticleOption(ParticleTypes.BLOCK, block.defaultBlockState()), where.x, where.y,
                            where.z, IMPACT_PARTICLES, 0.1, 0.1, 0.1, 0.15));
        });
    }

    /** Its caster's sound source, so a mob's spell sounds with the hostile creatures it is one of. */
    private SoundSource soundSourceOfCaster() {
        return cast != null ? cast.caster().getSoundSource() : getSoundSource();
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public boolean canUsePortal(boolean ignorePassenger) {
        return false;
    }

    @Override
    public boolean isPickable() {
        return false;
    }
}
