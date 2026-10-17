package io.github.matejkadlec.realmforge.spell;

import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.EventHooks;

/**
 * The one generic projectile of every spell (architecture 11.3), launched by {@link ProjectileEffect}: it flies
 * straight at its speed, without gravity or drag, hits the first enemy of its caster in its way and runs the
 * projectile's effects on it, delivered by itself (the hit's direct entity). A block, the end of its lifetime, or its
 * caster leaving its level (logging out, a mob's death) ends it without them.
 *
 * <ul>
 *   <li><b>Never saved:</b> it carries a live cast (the caster, the spell), so a chunk that unloads or a server that
 *   stops drops it.</li>
 *   <li><b>Synced:</b> only its size, for the hitbox; the client moves it and draws a particle trail until C6
 *   (RLM-29) gives spells their visuals. Everything else lives on the server; commands cannot summon one.</li>
 * </ul>
 */
public final class SpellProjectile extends Projectile {
    private static final EntityDataAccessor<Float> DATA_SIZE =
            SynchedEntityData.defineId(SpellProjectile.class, EntityDataSerializers.FLOAT);

    @Nullable
    private EffectContext cast;
    private List<SpellEffect> effects = List.of();
    private int lifetimeTicks;
    private int age;

    public SpellProjectile(EntityType<? extends SpellProjectile> type, Level level) {
        super(type, level);
    }

    /** Launches {@code projectile}'s projectile from the caster's eyes at {@code velocity} (blocks per tick). */
    static void launch(EffectContext context, ProjectileEffect projectile, Vec3 velocity) {
        LivingEntity caster = context.caster();
        SpellProjectile entity = new SpellProjectile(SpellEntities.PROJECTILE.get(), context.level());
        entity.setOwner(caster);
        entity.cast = context.through(context.level(), entity);
        entity.effects = projectile.effects();
        entity.lifetimeTicks = projectile.lifetimeTicks();
        entity.getEntityData().set(DATA_SIZE, (float) projectile.size());
        Vec3 eyes = caster.getEyePosition();
        entity.setPos(eyes.x, eyes.y - projectile.size() / 2, eyes.z);
        entity.setDeltaMovement(velocity);
        entity.updateRotation();
        context.level().addFreshEntity(entity);
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
        if (level().isClientSide()) {
            level().addParticle(ParticleTypes.END_ROD, getX(), getY() + getBbHeight() / 2, getZ(), 0, 0, 0);
        }
    }

    /** Whether {@code caster} left this projectile's level: logged out, changed dimension, or a mob that died. */
    private boolean gone(LivingEntity caster) {
        return caster.isRemoved() || caster.level() != level();
    }

    /** An enemy of the caster, alive and attackable; never the caster, its allies or other spell entities. */
    @Override
    protected boolean canHitEntity(Entity entity) {
        return cast != null && entity instanceof LivingEntity living && living != cast.caster() && living.isAlive()
                && !living.isSpectator() && living.canBeSeenAsEnemy() && !Targets.allied(cast.caster(), living)
                && super.canHitEntity(entity);
    }

    @Override
    protected void onHitEntity(EntityHitResult hitResult) {
        if (cast != null && hitResult.getEntity() instanceof LivingEntity living
                && level() instanceof ServerLevel serverLevel) {
            EffectContext context = cast.through(serverLevel, this);
            SpellTarget target = SpellTarget.hit(living, getDeltaMovement().normalize());
            for (SpellEffect effect : effects) {
                effect.apply(context, target);
            }
        }
        discard();
    }

    /** A block stops it, without its effects and without touching the block (no bell, no target block). */
    @Override
    protected void onHitBlock(BlockHitResult hitResult) {
        discard();
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
