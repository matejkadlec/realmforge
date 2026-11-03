package io.github.matejkadlec.realmforge.spell;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * How a charge moves its caster ({@link ChargeEffect}, RLM-543; Matěj's playtests of 2026-11-03): a rush along the line
 * to its target that stops exactly at its closest point, on the side it came from, then a small hop one block back. Two
 * ordinary mobs' middles are 1 block apart at the closest point and 2 after the hop, one free block between them.
 *
 * <p>It runs where the caster is simulated, as it needs the caster's real position: on the server for a mob
 * ({@link Charges}), on their own client for a player ({@code client/spell/ClientCharges}). A velocity sent from the
 * server reaches a player's client late, and it slid on about 3 blocks past the target whatever the server did then.
 * {@link #tick} runs once before each tick of the caster and sets the velocity that tick moves it by.
 */
public final class ChargeMotion {
    /** The gap between the caster's box and the target's at the closest point: 1 block between two 0.6-wide middles. */
    public static final double GAP = 0.4;
    /** How far the hop goes back. */
    static final double RECOIL_BLOCKS = 1;
    /** How long the hop lasts: about what its upward speed keeps it in the air. */
    static final int RECOIL_TICKS = 6;
    /** The hop's upward speed, in blocks per tick: about a third of a block high; a jump is 0.42 and 1.25 blocks. */
    static final double RECOIL_LIFT = 0.2;
    /** How near the closest point the caster has arrived, in blocks. */
    private static final double ARRIVED = 0.02;
    /** The least a tick against a wall must gain, in blocks, or the rush stops there. */
    private static final double MIN_PROGRESS = 0.01;

    /** What a {@link #tick} did. */
    public enum State {
        /** Rushing at the target. */
        RUSHING,
        /** Reached the closest point just now, and the hop begins: the moment the charge lands. */
        ARRIVED,
        /** In the hop. */
        RECOILING,
        /** The hop is over; the charge has ended. */
        DONE,
        /** Stopped short: the target is gone, a wall is in the way or the time is up. The charge has ended. */
        STOPPED
    }

    private final LivingEntity target;
    private final Vec3 approach;
    private final double perTick;
    private int ticksLeft;
    private double lastLeft = Double.MAX_VALUE;
    private int recoilTicks = -1;

    /**
     * A charge of {@code caster} at {@code target} from where they stand now.
     *
     * @param perTick the rush's speed, in blocks per tick
     * @param maxTicks how long the rush may last
     */
    public ChargeMotion(LivingEntity caster, LivingEntity target, double perTick, int maxTicks) {
        this.target = target;
        Vec3 toward = new Vec3(target.getX() - caster.getX(), 0, target.getZ() - caster.getZ());
        this.approach = toward.lengthSqr() < 1e-8 ? Vec3.directionFromRotation(0, caster.getYRot()) : toward.normalize();
        this.perTick = perTick;
        this.ticksLeft = maxTicks;
    }

    /** The closest point of {@code caster}'s charge: in front of the side of the target's box it came from. */
    public Vec3 closestPoint(LivingEntity caster) {
        double back = (caster.getBbWidth() + target.getBbWidth()) / 2 + GAP;
        return new Vec3(target.getX() - approach.x * back, caster.getY(), target.getZ() - approach.z * back);
    }

    /** The horizontal distance from {@code caster} to its closest point, in blocks. */
    public double distanceLeft(LivingEntity caster) {
        Vec3 point = closestPoint(caster);
        double x = point.x - caster.getX();
        double z = point.z - caster.getZ();
        return Math.sqrt(x * x + z * z);
    }

    /** Sets {@code caster}'s velocity for the coming tick; a stopped charge leaves it without horizontal speed. */
    public State tick(LivingEntity caster) {
        if (recoilTicks >= 0) {
            return recoil(caster);
        }
        if (!target.isAlive() || target.isRemoved() || target.level() != caster.level() || ticksLeft-- <= 0) {
            return stop(caster, State.STOPPED);
        }
        double left = distanceLeft(caster);
        if (left <= ARRIVED) {
            recoilTicks = 0;
            recoil(caster);
            return State.ARRIVED;
        }
        if (caster.horizontalCollision && left > lastLeft - MIN_PROGRESS) {
            return stop(caster, State.STOPPED);
        }
        lastLeft = left;
        Vec3 point = closestPoint(caster);
        Vec3 step = new Vec3(point.x - caster.getX(), 0, point.z - caster.getZ()).normalize()
                .scale(Math.min(perTick, left));
        caster.setDeltaMovement(step.x, caster.getDeltaMovement().y, step.z);
        return State.RUSHING;
    }

    private State recoil(LivingEntity caster) {
        if (recoilTicks >= RECOIL_TICKS) {
            return stop(caster, State.DONE);
        }
        double step = RECOIL_BLOCKS / RECOIL_TICKS;
        double lift = recoilTicks == 0 ? RECOIL_LIFT : caster.getDeltaMovement().y;
        caster.setDeltaMovement(-approach.x * step, lift, -approach.z * step);
        recoilTicks++;
        return State.RECOILING;
    }

    private static State stop(LivingEntity caster, State state) {
        caster.setDeltaMovement(0, caster.getDeltaMovement().y, 0);
        return state;
    }
}
