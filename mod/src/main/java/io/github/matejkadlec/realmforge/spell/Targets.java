package io.github.matejkadlec.realmforge.spell;

import java.util.List;
import java.util.Optional;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The checks every targeting type shares (architecture 11.2): who is whose ally, and whether a target is in range and
 * in line of sight.
 */
public final class Targets {
    /** The longest range a spell may have, in blocks: vanilla's line of sight check gives up beyond it. */
    public static final double MAX_RANGE = 128;

    /** How close, squared, a ray's stop may come to a point for the point to count as seen (a hundredth of a block). */
    private static final double SEEN_TOLERANCE = 1e-4;

    private Targets() {
    }

    /**
     * Whether {@code target} is {@code caster}'s ally: itself, a team mate or a tamed pet (vanilla's
     * {@code Entity#isAlliedTo}), and for a player every other player, as M1 has no PvP. Everything else is an enemy,
     * so a mob's spells hit other mobs too; the boss (W5) decides how its adds are spared.
     */
    public static boolean allied(LivingEntity caster, LivingEntity target) {
        return caster.isAlliedTo(target) || caster instanceof Player && target instanceof Player;
    }

    /**
     * Why a mob or boss may not choose {@code target} for an NPC targeting within {@code range} blocks, if it may not:
     * the target is no player it may attack (alive, neither in creative nor spectator mode, not invulnerable, not its
     * ally), or it is out of reach ({@link #reach}).
     */
    public static Optional<CastFailure> playerTarget(LivingEntity caster, LivingEntity target, double range) {
        if (!(target instanceof Player player) || !EntitySelector.NO_CREATIVE_OR_SPECTATOR.test(player)
                || !player.canBeSeenAsEnemy() || allied(caster, player)) {
            return Optional.of(CastFailure.INVALID_TARGET);
        }
        return reach(caster, player, range);
    }

    /** The players in {@code caster}'s level that it may choose for an NPC targeting within {@code range} blocks. */
    public static List<ServerPlayer> playerTargets(LivingEntity caster, double range) {
        if (!(caster.level() instanceof ServerLevel level)) {
            return List.of();
        }
        return level.players().stream().filter(player -> playerTarget(caster, player, range).isEmpty()).toList();
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

    /**
     * Whether {@code caster} sees {@code point}: no block that stops movement lies between its eyes and the point. A
     * point on a block's face counts as seen.
     */
    public static boolean canSee(LivingEntity caster, Vec3 point) {
        Vec3 eyes = caster.getEyePosition();
        BlockHitResult hit = caster.level().clip(new ClipContext(eyes, point, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, caster));
        return hit.getType() == HitResult.Type.MISS || hit.getLocation().distanceToSqr(point) < SEEN_TOLERANCE;
    }

    /** The first block that stops movement within {@code range} blocks of {@code caster}'s eyes along a direction. */
    public static BlockHitResult clip(LivingEntity caster, Vec3 direction, double range) {
        Vec3 eyes = caster.getEyePosition();
        return caster.level().clip(new ClipContext(eyes, eyes.add(direction.scale(range)), ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, caster));
    }
}
