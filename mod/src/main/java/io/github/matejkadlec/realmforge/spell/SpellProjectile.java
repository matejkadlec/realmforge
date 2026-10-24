package io.github.matejkadlec.realmforge.spell;

import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;
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
 * straight at its speed, without gravity or drag, hits the first enemy of its caster in its way and runs the
 * projectile's effects on it, delivered by itself (the hit's direct entity). An entity of the tag it spares stops it
 * too, untouched. A block, the end of its lifetime, or its caster leaving its level (logging out, a mob's death) ends it
 * without them. Where it hits a block or an entity, it makes its impact: a sound and a block's break particles.
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
    private int lifetimeTicks;
    private int age;

    public SpellProjectile(EntityType<? extends SpellProjectile> type, Level level) {
        super(type, level);
    }

    /**
     * Launches {@code projectile}'s projectile from {@code origin} (its middle) at {@code velocity} (blocks per
     * tick).
     */
    static void launch(EffectContext context, ProjectileEffect projectile, Vec3 origin, Vec3 velocity) {
        LivingEntity caster = context.caster();
        SpellProjectile entity = new SpellProjectile(SpellEntities.PROJECTILE.get(), context.level());
        entity.setOwner(caster);
        entity.cast = context.through(context.level(), entity);
        entity.effects = projectile.effects();
        entity.spares = projectile.spares();
        entity.impact = projectile.impact();
        entity.texture = projectile.appearance().flatMap(appearance -> context.spell().unwrapKey())
                .map(SpellVisuals::projectileTexture);
        entity.lifetimeTicks = projectile.lifetimeTicks();
        entity.getEntityData().set(DATA_SIZE, (float) projectile.size());
        entity.setPos(origin.x, origin.y - projectile.size() / 2, origin.z);
        entity.setDeltaMovement(velocity);
        entity.updateRotation();
        context.level().addFreshEntity(entity);
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
        if (level() instanceof ServerLevel && (cast == null || gone(cast.caster()) || ++age > lifetimeTicks)) {
            discard();
            return;
        }
        if (!level().isClientSide()) {
            HitResult hit = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
            if (hit.getType() != HitResult.Type.MISS && !EventHooks.onProjectileImpact(this, hit)) {
                hitTargetOrDeflectSelf(hit);
            }
            if (isRemoved()) {
                return;
            }
        }
        Vec3 movement = getDeltaMovement();
        setPos(getX() + movement.x, getY() + movement.y, getZ() + movement.z);
        updateRotation();
        if (level().isClientSide() && texture.isEmpty()) {
            level().addParticle(ParticleTypes.END_ROD, getX(), getY() + getBbHeight() / 2, getZ(), 0, 0, 0);
        }
    }

    /** Whether {@code caster} left this projectile's level: logged out, changed dimension, or a mob that died. */
    private boolean gone(LivingEntity caster) {
        return caster.isRemoved() || caster.level() != level();
    }

    /**
     * An enemy of the caster, or an entity it spares, alive and attackable; never the caster, its allies or other
     * spell entities.
     */
    @Override
    protected boolean canHitEntity(Entity entity) {
        return cast != null && entity instanceof LivingEntity living && living != cast.caster() && living.isAlive()
                && !living.isSpectator() && living.canBeSeenAsEnemy()
                && (spared(living) || !Targets.allied(cast.caster(), living)) && super.canHitEntity(entity);
    }

    /** Whether {@code entity} is of the tag this projectile spares: it stops the projectile, untouched. */
    private boolean spared(Entity entity) {
        return spares.filter(entity::is).isPresent();
    }

    @Override
    protected void onHitEntity(EntityHitResult hitResult) {
        if (cast != null && hitResult.getEntity() instanceof LivingEntity living
                && level() instanceof ServerLevel serverLevel) {
            if (!spared(living)) {
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
