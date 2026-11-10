package io.github.matejkadlec.realmforge.spell;

import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
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
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
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
 * stops it too, without its effects. Where it hits a block or an entity, it makes its impact: the sound of its spell's
 * look, played by the server, and one entity event, from which each client bursts the look's particles there.
 *
 * <p><b>Its flight is its trajectory's</b> ({@link SpellTrajectory}, ADR 0073): every tick it moves by the step its
 * trajectory gives for its aim, the ticks it has flown and its seed, on the server and on its clients alike, so they
 * draw the path the server flies. Every projectile flies straight (ADR 0052).
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
 *   <li><b>Synced:</b> its size, for the hitbox, and with its spawn what its clients need to draw it and fly its
 *   path: its spell's look and its model, if it has them, its trajectory, its aim, its seed, the ticks it has flown
 *   and how far its impact bursts. The client moves it and draws it, with its look's trail behind it (a trail of
 *   sparks without a look); everything else lives on the server, and commands cannot summon one.</li>
 * </ul>
 */
public final class SpellProjectile extends Projectile implements IEntityWithComplexSpawn {
    private static final EntityDataAccessor<Float> DATA_SIZE =
            SynchedEntityData.defineId(SpellProjectile.class, EntityDataSerializers.FLOAT);
    /** The entity event of its hit on a creature: its clients burst its look's particles round what it hit. */
    public static final byte EVENT_HIT_ENTITY = 60;
    /** The entity event of its hit on a block: its clients burst its look's particles off the block's face. */
    public static final byte EVENT_HIT_BLOCK = 61;
    /** An impact without a splash still bursts over this many blocks round where it hit. */
    private static final float MIN_BURST_RADIUS = 0.25F;
    /** How fast a burst's particles fly out, in blocks per tick for each block of its radius. */
    private static final double BURST_SPEED = 0.12;
    /** How far a trail's particles drift, in blocks per tick. */
    private static final double TRAIL_DRIFT = 0.01;

    private static final StreamCodec<RegistryFriendlyByteBuf, Optional<Holder<SpellLook>>> LOOK_CODEC =
            ByteBufCodecs.optional(ByteBufCodecs.holderRegistry(SpellRegistries.SPELL_LOOK));
    private static final StreamCodec<RegistryFriendlyByteBuf, Optional<Holder<SpellModel>>> MODEL_CODEC =
            ByteBufCodecs.optional(ByteBufCodecs.holderRegistry(SpellRegistries.SPELL_MODEL));
    private static final StreamCodec<RegistryFriendlyByteBuf, Optional<Holder<SpellTrajectory>>> TRAJECTORY_CODEC =
            ByteBufCodecs.optional(ByteBufCodecs.holderRegistry(SpellRegistries.SPELL_TRAJECTORY));

    @Nullable
    private EffectContext cast;
    private List<SpellEffect> effects = List.of();
    private Optional<TagKey<EntityType<?>>> spares = Optional.empty();
    private Optional<ProjectileEffect.Splash> splash = Optional.empty();
    /** The sound of its impact, on the server: its spell's own or its look's. */
    private Optional<Identifier> impactSound = Optional.empty();
    /** Its spell's look, on the server and its clients; empty for a trail of sparks. */
    private Optional<Holder<SpellLook>> look = Optional.empty();
    /** Its shape, on the server and its clients; empty for a projectile without a body. */
    private Optional<Holder<SpellModel>> model = Optional.empty();
    /** How it flies, on the server and its clients; empty only before a client read its spawn. */
    private Optional<Holder<SpellTrajectory>> trajectory = Optional.empty();
    /** The velocity of the line it flies along, in blocks per tick: its launch velocity, turned by homing. */
    private Vec3 aim = Vec3.ZERO;
    /** What its trajectory varies its path by; the same on the server and its clients. */
    private long seed;
    /** The ticks it has flown, the time its trajectory reads. */
    private int flownTicks;
    /** How far its impact's particles burst, in blocks: its splash's radius, or a small puff. */
    private float burstRadius = MIN_BURST_RADIUS;
    /** How many ticks it flies before it fizzles; 0 for no lifetime. */
    private int lifetimeTicks;
    /** What is left of the trail particle its client has not spawned yet: less than one. */
    private float trailRemainder;
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
        entity.splash = projectile.splash();
        Optional<Visuals> visuals = context.spell().value().visuals();
        entity.impactSound = visuals.flatMap(Visuals::impactSoundOrLooks);
        entity.look = visuals.map(Visuals::look);
        entity.model = projectile.model();
        entity.trajectory = Optional.of(projectile.trajectory());
        entity.aim = velocity;
        entity.seed = caster.getRandom().nextLong();
        entity.burstRadius = (float) Math.max(MIN_BURST_RADIUS, projectile.splash()
                .map(explosion -> explosion.radius() + splashBonus(context)).orElse(0.0));
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

    /** Its spell's look, whose material and particles its clients draw it with; empty for a trail of sparks. */
    public Optional<Holder<SpellLook>> look() {
        return look;
    }

    /** Its shape, which its clients draw with its look's material; empty for a projectile without a body. */
    public Optional<Holder<SpellModel>> model() {
        return model;
    }

    /** How it flies. */
    public Optional<Holder<SpellTrajectory>> trajectory() {
        return trajectory;
    }

    /** What its trajectory varies its path by: the same on the server and its clients. */
    public long seed() {
        return seed;
    }

    /** How far its impact's particles burst, in blocks. */
    public float burstRadius() {
        return burstRadius;
    }

    @Override
    public void writeSpawnData(RegistryFriendlyByteBuf buffer) {
        LOOK_CODEC.encode(buffer, look);
        MODEL_CODEC.encode(buffer, model);
        TRAJECTORY_CODEC.encode(buffer, trajectory);
        Vec3.STREAM_CODEC.encode(buffer, aim);
        buffer.writeLong(seed);
        buffer.writeVarInt(flownTicks);
        buffer.writeFloat(burstRadius);
    }

    @Override
    public void readSpawnData(RegistryFriendlyByteBuf buffer) {
        look = LOOK_CODEC.decode(buffer);
        model = MODEL_CODEC.decode(buffer);
        trajectory = TRAJECTORY_CODEC.decode(buffer);
        aim = Vec3.STREAM_CODEC.decode(buffer);
        seed = buffer.readLong();
        flownTicks = buffer.readVarInt();
        burstRadius = buffer.readFloat();
    }

    /**
     * A velocity update from the server: homing turned the line it flies along (RLM-345), so its client follows the
     * server's flight from here. Every trajectory is straight, so the velocity is the aim; a later kind that bends
     * the path sends its aim itself (RLM-439's review note on assisted curves).
     */
    @Override
    public void lerpMotion(Vec3 movement) {
        super.lerpMotion(movement);
        aim = movement;
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
                || lifetimeTicks > 0 && flownTicks >= lifetimeTicks || aboveTheWorld(serverLevel))) {
            discard();
            return;
        }
        if (!level().isClientSide()) {
            home();
        }
        trajectory.ifPresent(path -> setDeltaMovement(path.value().velocity(aim, flownTicks, seed)));
        if (!level().isClientSide()) {
            Vec3 step = getDeltaMovement();
            HitResult hit = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
            if (hit.getType() != HitResult.Type.MISS && !EventHooks.onProjectileImpact(this, hit)) {
                hitTargetOrDeflectSelf(hit);
            }
            if (isRemoved()) {
                return;
            }
            if (!getDeltaMovement().equals(step)) {
                // Something deflected it (a Breeze): it flies on along where that sent it.
                aim = getDeltaMovement();
                needsSync = true;
            }
        }
        Vec3 movement = getDeltaMovement();
        if (level() instanceof ServerLevel serverLevel && !moving(serverLevel, position().add(movement))) {
            discard();
            return;
        }
        setPos(getX() + movement.x, getY() + movement.y, getZ() + movement.z);
        updateRotation();
        flownTicks++;
        if (level().isClientSide()) {
            trail(movement);
        }
    }

    /**
     * Its trail behind the step it just flew, on its client: its look's particles spread along the step, each in one
     * of the look's colours, or a spark for a spell without a look.
     */
    private void trail(Vec3 step) {
        double middle = getY() + getBbHeight() / 2;
        if (look.isEmpty()) {
            level().addParticle(ParticleTypes.END_ROD, getX(), middle, getZ(), 0, 0, 0);
            return;
        }
        SpellLook element = look.get().value();
        if (element.trail().isEmpty()) {
            return;
        }
        SpellLook.Trail emitter = element.trail().get();
        trailRemainder += (float) (emitter.perBlock() * step.length());
        double jitter = getBbWidth() / 4;
        while (trailRemainder >= 1) {
            trailRemainder--;
            double back = random.nextDouble();
            TintedParticles.of(emitter.particle(), element.color(random)).ifPresent(particle -> level().addParticle(
                    particle, getX() - step.x * back + random.triangle(0, jitter),
                    middle - step.y * back + random.triangle(0, jitter),
                    getZ() - step.z * back + random.triangle(0, jitter), random.triangle(0, TRAIL_DRIFT),
                    random.triangle(0, TRAIL_DRIFT), random.triangle(0, TRAIL_DRIFT)));
        }
    }

    /**
     * Its impact, on its client (ADR 0073): the server sends one entity event per hit, and each client bursts the
     * look's particles from what it knows. A client's projectile is a tick or two off the server's, so the client
     * finds the point itself: where the last two ticks of its own flight meet a block or a creature, or its own middle
     * when they meet nothing.
     */
    @Override
    public void handleEntityEvent(byte id) {
        if (id == EVENT_HIT_ENTITY || id == EVENT_HIT_BLOCK) {
            look.flatMap(element -> element.value().burst()).ifPresent(burst -> burst(burst, id == EVENT_HIT_BLOCK));
        } else {
            super.handleEntityEvent(id);
        }
    }

    private void burst(SpellLook.Burst burst, boolean onBlock) {
        SpellLook element = look.orElseThrow().value();
        Vec3 middle = position().add(0, getBbHeight() / 2, 0);
        Vec3 step = getDeltaMovement();
        Vec3 from = middle.subtract(step);
        Vec3 to = middle.add(step);
        Vec3 centre = middle;
        Vec3 off = Vec3.ZERO;
        float radius = burstRadius;
        if (onBlock) {
            BlockHitResult hit = level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE, this));
            if (hit.getType() == HitResult.Type.BLOCK) {
                centre = hit.getLocation();
                off = hit.getDirection().getUnitVec3();
            }
            radius = MIN_BURST_RADIUS;
        } else {
            EntityHitResult hit = ProjectileUtil.getEntityHitResult(level(), this, from, to,
                    new AABB(from, to).inflate(1), entity -> entity instanceof LivingEntity living && living.isAlive()
                            && !living.isSpectator() && living != getOwner());
            if (hit != null) {
                centre = hit.getEntity().getBoundingBox().getCenter();
                radius += hit.getEntity().getBbWidth() / 2;
            }
        }
        for (int n = 0; n < burst.count(); n++) {
            Vec3 out = new Vec3(random.triangle(0, 1), random.triangle(0, 1), random.triangle(0, 1));
            Vec3 at = centre.add(out.scale(radius / 2));
            // Off a block's face, never into it: what flies into the block turns back out.
            Vec3 speed = out.scale(BURST_SPEED * radius);
            double into = speed.dot(off);
            Vec3 away = into < 0 ? speed.subtract(off.scale(2 * into)) : speed;
            Vec3 velocity = away.add(off.scale(BURST_SPEED / 2));
            TintedParticles.of(burst.particle(), element.color(random)).ifPresent(particle -> level()
                    .addParticle(particle, at.x, at.y, at.z, velocity.x, velocity.y, velocity.z));
        }
    }

    /**
     * Turns the line it flies along toward the enemy it homes in on, by at most {@link AimAssist#TURN_PER_TICK}, and
     * tells its clients at once, so they draw the curve the server flies. An enemy that died, left, or that it passed ends the homing.
     */
    private void home() {
        LivingEntity target = homing;
        if (target == null) {
            return;
        }
        Vec3 toward = target.getBoundingBox().getCenter().subtract(position().add(0, getBbHeight() / 2, 0));
        if (!target.isAlive() || target.isRemoved() || target.level() != level() || toward.dot(aim) <= 0) {
            homing = null;
            return;
        }
        Vec3 turned = AimAssist.turn(aim, toward, AimAssist.TURN_PER_TICK);
        if (!turned.equals(aim)) {
            aim = turned;
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
                splash.ifPresent(explosion -> splash(serverLevel, context, living, explosion));
            }
            impact(serverLevel, position().add(0, getBbHeight() / 2, 0), EVENT_HIT_ENTITY);
        }
        discard();
    }

    /**
     * Its small explosion round {@code hit} (RLM-399): the caster's other enemies within the radius of the target's
     * hitbox take its effects, the damage at the splash's share, the rest at full strength, pushed out from the target;
     * its impact's particles burst over the whole of it, on its clients. The caster's passive spells may widen it
     * (RLM-542: Improved Frostbolt's rank 3, {@link Passives#splashRadiusBonus}).
     */
    private void splash(ServerLevel level, EffectContext context, LivingEntity hit, ProjectileEffect.Splash explosion) {
        LivingEntity caster = context.caster();
        double radius = explosion.radius() + splashBonus(context);
        AABB reach = hit.getBoundingBox().inflate(radius);
        List<LivingEntity> around = level.getEntitiesOfClass(LivingEntity.class, reach, entity -> entity != hit
                && entity != caster && entity.isAlive() && !entity.isSpectator() && !Targets.allied(caster, entity)
                && entity.canBeSeenAsEnemy() && !spared(entity));
        EffectContext share = context.scaled(explosion.damage());
        Vec3 middle = hit.getBoundingBox().getCenter();
        for (LivingEntity entity : around) {
            Vec3 out = entity.getBoundingBox().getCenter().subtract(middle);
            SpellTarget target = SpellTarget.hit(entity, out.lengthSqr() > 1e-6 ? out.normalize()
                    : getDeltaMovement().normalize());
            for (SpellEffect effect : effects) {
                effect.apply(share, target);
            }
        }
    }

    /** What the caster's passive spells add to its splash's radius, in blocks. */
    private static double splashBonus(EffectContext context) {
        return context.spell().unwrapKey().map(key -> Passives.splashRadiusBonus(context.caster(), key)).orElse(0.0);
    }

    /** A block stops it, without its effects and without touching the block (no bell, no target block). */
    @Override
    protected void onHitBlock(BlockHitResult hitResult) {
        if (level() instanceof ServerLevel serverLevel) {
            impact(serverLevel, hitResult.getLocation(), EVENT_HIT_BLOCK);
        }
        discard();
    }

    /**
     * Its impact at {@code where}: its sound, and the entity event its clients burst its look's particles from. A
     * projectile without a look has neither.
     */
    private void impact(ServerLevel level, Vec3 where, byte event) {
        impactSound.map(BuiltInRegistries.SOUND_EVENT::getValue).ifPresent((SoundEvent sound) -> level
                .playSound(null, where.x, where.y, where.z, sound, soundSourceOfCaster(), 1.0F, 1.0F));
        if (look.isPresent()) {
            level.broadcastEntityEvent(this, event);
        }
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
