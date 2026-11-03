package io.github.matejkadlec.realmforge.spell;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.SharedConstants;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * The charges in progress ({@link ChargeEffect}, RLM-543). A charge aims at its landing point: the near side of the
 * target's box on the side the charge came from, a quarter block short of it (Matěj's playtest of 2026-11-03: charged
 * from behind, the Warrior stops at the target's back, never passing through it). Every server tick it sets its
 * caster's horizontal velocity toward that point, at most its speed and never past the distance left, and marks it hurt
 * so a player's client receives it as it receives a knockback; the caster keeps its vertical velocity and takes no fall
 * damage meanwhile. It lands at the point, when it has passed it along the charge's direction (a player's client moves
 * ahead of what the server sees), when the target dies or leaves, or when the time its distance needs at its speed, plus
 * half a second for a slope or a player's late client, is up (a wall it cannot get through). Landing stops the caster,
 * puts it on the landing point when it reached or passed it and the point is free, and runs the charge's effects on
 * every enemy within its landing radius of the caster, measured to each enemy's box. A caster that dies, is removed or
 * changes dimension drops its charge without landing; a new charge replaces the one in progress. Server thread only.
 */
public final class Charges {
    /** The gap a charge leaves between the caster's box and the target's, in blocks. */
    static final double GAP = 0.25;
    /** How near the landing point a charge has arrived, in blocks. */
    static final double ARRIVED = 0.1;
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

    /** Starts {@code context}'s caster's charge at {@code target}, steering it at once. */
    static void start(EffectContext context, LivingEntity target, ChargeEffect charge) {
        LivingEntity caster = context.caster();
        if (caster == target) {
            return;
        }
        Vec3 toward = new Vec3(target.getX() - caster.getX(), 0, target.getZ() - caster.getZ());
        Vec3 approach = toward.lengthSqr() < 1e-8 ? Vec3.directionFromRotation(0, caster.getYRot()) : toward.normalize();
        double perTick = charge.speed() / SharedConstants.TICKS_PER_SECOND;
        int ticks = Math.min(MAX_TICKS, (int) Math.ceil(gap(caster, target) / perTick) + SLACK_TICKS);
        Running running = new Running(context, target, charge, approach, perTick,
                context.level().getGameTime() + ticks);
        RUNNING.put(caster, running);
        steer(running);
    }

    /**
     * Where {@code caster} lands charging at {@code target} along {@code approach}: in front of the near side of the
     * target's box, the side the charge came from, {@link #GAP} short of it, at the caster's own height.
     */
    static Vec3 landingPoint(LivingEntity caster, LivingEntity target, Vec3 approach) {
        double back = (caster.getBbWidth() + target.getBbWidth()) / 2 + GAP;
        return new Vec3(target.getX() - approach.x * back, caster.getY(), target.getZ() - approach.z * back);
    }

    /** Whether {@code caster} is charging now. */
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
        LivingEntity target = running.target();
        boolean lost = !target.isAlive() || target.isRemoved() || target.level() != caster.level();
        if (lost) {
            land(running, false);
        } else if (left(running) <= ARRIVED) {
            land(running, true);
        } else if (caster.level().getGameTime() >= running.endTime()) {
            land(running, false);
        } else {
            steer(running);
        }
    }

    /**
     * How far the caster still is from its landing point along the charge's direction: negative once it has passed it.
     */
    private static double left(Running running) {
        Vec3 point = landingPoint(running.caster(), running.target(), running.approach());
        Vec3 rest = point.subtract(running.caster().position());
        return rest.x * running.approach().x + rest.z * running.approach().z;
    }

    private static void steer(Running running) {
        LivingEntity caster = running.caster();
        Vec3 point = landingPoint(caster, running.target(), running.approach());
        Vec3 toward = new Vec3(point.x - caster.getX(), 0, point.z - caster.getZ());
        if (toward.lengthSqr() < 1e-8) {
            return;
        }
        Vec3 step = toward.normalize().scale(Math.min(running.perTick(), toward.length()));
        caster.setDeltaMovement(step.x, caster.getDeltaMovement().y, step.z);
        caster.hurtMarked = true;
        caster.resetFallDistance();
    }

    /** Ends the charge; {@code arrived}: it reached or passed its landing point, where it then stands. */
    private static void land(Running running, boolean arrived) {
        LivingEntity caster = running.caster();
        RUNNING.remove(caster);
        caster.setDeltaMovement(0, Math.min(0, caster.getDeltaMovement().y), 0);
        caster.hurtMarked = true;
        caster.resetFallDistance();
        if (arrived) {
            Vec3 point = landingPoint(caster, running.target(), running.approach());
            Vec3 move = point.subtract(caster.position());
            if (move.lengthSqr() > 1e-4 && caster.level().noBlockCollision(caster, caster.getBoundingBox().move(move))) {
                caster.teleportTo(point.x, point.y, point.z);
            }
        }
        ServerLevel level = running.context().level();
        double radius = running.charge().landingRadius();
        Vec3 landing = caster.position();
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
     * A charge in progress: its cast, its target, its data, the horizontal direction it set out in, its speed in blocks
     * per tick and the game time it lands at the latest.
     */
    private record Running(EffectContext context, LivingEntity target, ChargeEffect charge, Vec3 approach,
            double perTick, long endTime) {
        LivingEntity caster() {
            return context.caster();
        }
    }
}
