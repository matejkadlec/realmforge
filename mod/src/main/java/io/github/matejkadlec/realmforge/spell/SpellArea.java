package io.github.matejkadlec.realmforge.spell;

import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The one generic area of every spell (architecture 11.3), a ground zone placed by {@link AreaEffect}: on its first
 * tick and at the start of every interval after it (RLM-436) it runs the area's effects on each enemy (or ally) of its
 * caster inside it, delivered by itself (the hit's direct entity), as many times as its duration holds intervals. Inside means standing within its radius of its middle, at most
 * {@value #BELOW} blocks below it and {@value #ABOVE} above. Its caster leaving its level (logging out, a mob's death)
 * ends it.
 *
 * <ul>
 *   <li><b>Never saved</b> and <b>synced</b> only by its radius and its ring's colour, as {@link SpellProjectile}:
 *   the client draws a ring of dust in the area's {@code ring_color}, or of purple sparks without one, until C6
 *   (RLM-29) gives spells their visuals.</li>
 * </ul>
 */
public final class SpellArea extends Entity {
    /** How far below its middle an area reaches, in blocks. */
    public static final double BELOW = 0.5;
    /** How far above its middle an area reaches, in blocks. */
    public static final double ABOVE = 3;

    private static final EntityDataAccessor<Float> DATA_RADIUS =
            SynchedEntityData.defineId(SpellArea.class, EntityDataSerializers.FLOAT);
    /** Its ring's colour as 0xRRGGBB (no alpha, so never {@link #NO_COLOR}), or {@link #NO_COLOR} for purple sparks. */
    private static final EntityDataAccessor<Integer> DATA_RING_COLOR =
            SynchedEntityData.defineId(SpellArea.class, EntityDataSerializers.INT);
    private static final int NO_COLOR = -1;
    /** The client draws its ring every this many ticks. */
    private static final int PARTICLE_INTERVAL = 5;

    @Nullable
    private EffectContext cast;
    @Nullable
    private AreaEffect area;
    private int age;

    public SpellArea(EntityType<? extends SpellArea> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    /** Places {@code area}'s zone at {@code position}. */
    static void place(EffectContext context, AreaEffect area, Vec3 position) {
        SpellArea entity = new SpellArea(SpellEntities.AREA.get(), context.level());
        entity.cast = context.through(context.level(), entity);
        entity.area = area;
        entity.getEntityData().set(DATA_RADIUS, (float) area.radius());
        entity.getEntityData().set(DATA_RING_COLOR, area.ringColor().map(color -> color & 0xFFFFFF).orElse(NO_COLOR));
        entity.setPos(position);
        context.level().addFreshEntity(entity);
    }

    /** Its radius in blocks. */
    public float radius() {
        return getEntityData().get(DATA_RADIUS);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder entityData) {
        entityData.define(DATA_RADIUS, 1.0F);
        entityData.define(DATA_RING_COLOR, NO_COLOR);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
        if (DATA_RADIUS.equals(accessor)) {
            refreshDimensions();
        }
        super.onSyncedDataUpdated(accessor);
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        return EntityDimensions.scalable(radius() * 2, 0.5F);
    }

    @Override
    public void tick() {
        super.tick();
        if (level() instanceof ServerLevel serverLevel) {
            serverTick(serverLevel);
        } else if (tickCount % PARTICLE_INTERVAL == 1) {
            // tickCount is 1 on the first tick, so the ring shows as the area appears.
            drawRing();
        }
    }

    private void serverTick(ServerLevel level) {
        if (cast == null || area == null || cast.caster().isRemoved() || cast.caster().level() != level) {
            discard();
            return;
        }
        // A duration is a whole number of intervals: Test Scorch's 3 s of 1 s pulse at 0, 1 and 2 s, gone at 3 s.
        if (age % area.intervalTicks() == 0) {
            pulse(level, cast, area);
        }
        age++;
        if (age >= area.durationTicks()) {
            discard();
        }
    }

    /** Runs the area's effects on every entity inside it that it affects. */
    private void pulse(ServerLevel level, EffectContext context, AreaEffect area) {
        LivingEntity caster = context.caster();
        double radius = area.radius();
        Vec3 middle = position();
        AABB box = new AABB(middle.x - radius, middle.y - BELOW, middle.z - radius, middle.x + radius,
                middle.y + ABOVE, middle.z + radius);
        List<LivingEntity> inside = level.getEntitiesOfClass(LivingEntity.class, box, entity -> entity.isAlive()
                && !entity.isSpectator() && affects(caster, entity, area.affects())
                && horizontalDistanceSqr(middle, entity.position()) <= radius * radius);
        EffectContext through = context.through(level, this);
        for (LivingEntity entity : inside) {
            Vec3 out = entity.position().subtract(middle);
            SpellTarget target = SpellTarget.hit(entity, new Vec3(out.x, 0, out.z).normalize());
            for (SpellEffect effect : area.effects()) {
                effect.apply(through, target);
            }
        }
    }

    /** Whether an area of {@code caster}'s that affects {@code affects} acts on {@code entity}. */
    static boolean affects(LivingEntity caster, LivingEntity entity, AreaEffect.Affects affects) {
        boolean allied = Targets.allied(caster, entity);
        return affects == AreaEffect.Affects.ALLIES
                ? allied && entity.canBeSeenByAnyone()
                : !allied && entity.canBeSeenAsEnemy();
    }

    private static double horizontalDistanceSqr(Vec3 a, Vec3 b) {
        double x = a.x - b.x;
        double z = a.z - b.z;
        return x * x + z * z;
    }

    private void drawRing() {
        float radius = radius();
        int color = getEntityData().get(DATA_RING_COLOR);
        ParticleOptions particle = color == NO_COLOR ? ParticleTypes.WITCH : new DustParticleOptions(color, 1.0F);
        int count = Mth.ceil(2 * Math.PI * radius);
        for (int i = 0; i < count; i++) {
            double angle = 2 * Math.PI * i / count;
            level().addParticle(particle, getX() + Math.cos(angle) * radius, getY() + 0.1,
                    getZ() + Math.sin(angle) * radius, 0, 0, 0);
        }
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
    public PushReaction getPistonPushReaction() {
        return PushReaction.IGNORE;
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
    }
}
