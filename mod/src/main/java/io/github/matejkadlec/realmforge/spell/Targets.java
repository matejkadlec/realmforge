package io.github.matejkadlec.realmforge.spell;

import java.util.Optional;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * The checks every targeting type shares (architecture 11.2): who is whose ally, and whether a target is in range and
 * in line of sight.
 */
public final class Targets {
    /** The longest range a spell may have, in blocks: vanilla's line of sight check gives up beyond it. */
    public static final double MAX_RANGE = 128;

    private Targets() {
    }

    /**
     * Whether {@code target} is {@code caster}'s ally: itself, a team mate or a tamed pet (vanilla's
     * {@code Entity#isAlliedTo}), and for a player every other player, as M1 has no PvP. Everything else is an enemy.
     * C3a-4 adds the NPC side (a boss's adds).
     */
    public static boolean allied(LivingEntity caster, LivingEntity target) {
        return caster.isAlliedTo(target) || caster instanceof Player && target instanceof Player;
    }

    /**
     * Why {@code caster} cannot reach {@code target} within {@code range} blocks, if it cannot: in another dimension or
     * farther than the range from the caster's eyes to the nearest point of the target's box, or behind blocks
     * (vanilla's line of sight, from eye to eye).
     */
    public static Optional<CastFailure> reach(LivingEntity caster, LivingEntity target, double range) {
        if (target.level() != caster.level() || distance(caster, target) > range) {
            return Optional.of(CastFailure.OUT_OF_RANGE);
        }
        if (target != caster && !caster.hasLineOfSight(target)) {
            return Optional.of(CastFailure.NO_LINE_OF_SIGHT);
        }
        return Optional.empty();
    }

    /** The distance from {@code caster}'s eyes to the nearest point of {@code target}'s bounding box, in blocks. */
    public static double distance(LivingEntity caster, LivingEntity target) {
        Vec3 eyes = caster.getEyePosition();
        return Math.sqrt(target.getBoundingBox().distanceToSqr(eyes));
    }
}
