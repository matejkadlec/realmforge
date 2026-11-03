package io.github.matejkadlec.realmforge.spell;

import io.github.matejkadlec.realmforge.spell.net.ChargePayload;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.SharedConstants;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The charges in progress ({@link ChargeEffect}, RLM-543). {@link ChargeMotion} moves the caster: this runs a mob's on
 * the server, and a player's client runs its own from a {@link ChargePayload} (Matěj's playtest of 2026-11-03: a velocity
 * the server sent let the player slide about 3 blocks past the target), while the server reads where the player reports
 * they are. The charge lands when the caster reaches its closest point, a player within one tick of travel of it, as
 * their position reaches the server a tick late; when the target dies or leaves; or when the time its distance needs at
 * its speed, plus half a second for a slope or a late client, is up (a wall it cannot get through). Landing runs the
 * charge's effects on every enemy within its landing radius of the closest point (or of the caster, if it stopped short),
 * measured to each enemy's box. The caster takes no fall damage meanwhile. A caster that dies, is removed or changes
 * dimension drops its charge without landing; a new charge replaces the one in progress. Server thread only.
 */
public final class Charges {
    /** Ticks a charge may take beyond what its distance needs at its speed. */
    static final int SLACK_TICKS = 10;
    /** The longest a charge lasts: two seconds. */
    static final int MAX_TICKS = 40;

    /** Every charge in progress, by caster. */
    private static final Map<LivingEntity, Running> RUNNING = new HashMap<>();

    private Charges() {
    }

    static void register() {
        NeoForge.EVENT_BUS.addListener(ServerTickEvent.Post.class, Charges::onServerTick);
        NeoForge.EVENT_BUS.addListener(ServerStoppedEvent.class, event -> RUNNING.clear());
    }

    /** Starts {@code context}'s caster's charge at {@code target}, a mob's moving at once. */
    static void start(EffectContext context, LivingEntity target, ChargeEffect charge) {
        LivingEntity caster = context.caster();
        if (caster == target) {
            return;
        }
        double perTick = charge.speed() / SharedConstants.TICKS_PER_SECOND;
        int ticks = Math.min(MAX_TICKS, (int) Math.ceil(gap(caster, target) / perTick) + SLACK_TICKS);
        ChargeMotion motion = new ChargeMotion(caster, target, perTick, ticks);
        RUNNING.put(caster, new Running(context, target, charge, motion, perTick,
                context.level().getGameTime() + ticks));
        if (caster instanceof ServerPlayer player) {
            PacketDistributor.sendToPlayer(player, new ChargePayload(target.getId(), perTick, ticks));
        } else {
            motion.tick(caster);
        }
    }

    /** Whether {@code caster} is charging now: a mob until its hop is over, a player until the charge lands. */
    public static boolean isCharging(LivingEntity caster) {
        return RUNNING.containsKey(caster);
    }

    /**
     * The horizontal gap between {@code caster}'s box and {@code target}'s, in blocks, from their middles less half of
     * each width: 0 once they touch.
     */
    static double gap(LivingEntity caster, LivingEntity target) {
        double x = target.getX() - caster.getX();
        double z = target.getZ() - caster.getZ();
        return Math.max(0, Math.sqrt(x * x + z * z) - (caster.getBbWidth() + target.getBbWidth()) / 2);
    }

    private static void onServerTick(ServerTickEvent.Post event) {
        if (RUNNING.isEmpty()) {
            return;
        }
        for (Running running : List.copyOf(RUNNING.values())) {
            // An earlier landing's effects can end another charge, so only charges still running go on.
            if (RUNNING.get(running.caster()) == running) {
                tick(running);
            }
        }
    }

    private static void tick(Running running) {
        LivingEntity caster = running.caster();
        if (!caster.isAlive() || caster.isRemoved() || caster.level() != running.context().level()) {
            RUNNING.remove(caster);
            return;
        }
        caster.resetFallDistance();
        if (caster instanceof ServerPlayer) {
            tickPlayer(running);
        } else {
            tickMob(running);
        }
    }

    /** A player's client moves them: this only watches for the landing. */
    private static void tickPlayer(Running running) {
        LivingEntity caster = running.caster();
        LivingEntity target = running.target();
        boolean lost = !target.isAlive() || target.isRemoved() || target.level() != caster.level();
        if (lost || caster.level().getGameTime() >= running.endTime()) {
            RUNNING.remove(caster);
            land(running, false);
        } else if (running.motion().distanceLeft(caster) <= running.perTick()) {
            RUNNING.remove(caster);
            land(running, true);
        }
    }

    private static void tickMob(Running running) {
        LivingEntity caster = running.caster();
        switch (running.motion().tick(caster)) {
            case ARRIVED -> land(running, true);
            case STOPPED -> {
                RUNNING.remove(caster);
                land(running, false);
            }
            case DONE -> RUNNING.remove(caster);
            case RUSHING, RECOILING -> {
            }
        }
    }

    /** Runs the charge's effects round where it landed; {@code arrived}: it reached its closest point. */
    private static void land(Running running, boolean arrived) {
        LivingEntity caster = running.caster();
        ServerLevel level = running.context().level();
        double radius = running.charge().landingRadius();
        Vec3 landing = arrived ? running.motion().closestPoint(caster) : caster.position();
        AABB around = new AABB(landing.x - radius, landing.y - SpellArea.BELOW, landing.z - radius,
                landing.x + radius, landing.y + SpellArea.ABOVE, landing.z + radius);
        List<LivingEntity> reached = level.getEntitiesOfClass(LivingEntity.class, around, entity -> entity != caster
                && entity.isAlive() && !entity.isSpectator()
                && SpellArea.affects(caster, entity, AreaEffect.Affects.ENEMIES)
                && horizontalDistance(landing, entity.getBoundingBox()) <= radius);
        for (LivingEntity entity : reached) {
            Vec3 out = entity.position().subtract(landing);
            SpellTarget target = SpellTarget.hit(entity, new Vec3(out.x, 0, out.z).normalize());
            for (SpellEffect effect : running.charge().effects()) {
                effect.apply(running.context(), target);
            }
        }
    }

    /** The horizontal distance from {@code point} to the nearest point of {@code box}, in blocks. */
    private static double horizontalDistance(Vec3 point, AABB box) {
        double x = Math.max(0, Math.max(box.minX - point.x, point.x - box.maxX));
        double z = Math.max(0, Math.max(box.minZ - point.z, point.z - box.maxZ));
        return Math.sqrt(x * x + z * z);
    }

    /**
     * A charge in progress: its cast, its target, its data, what moves its caster, its speed in blocks per tick and the
     * game time it lands at the latest.
     */
    private record Running(EffectContext context, LivingEntity target, ChargeEffect charge, ChargeMotion motion,
            double perTick, long endTime) {
        LivingEntity caster() {
            return context.caster();
        }
    }
}
