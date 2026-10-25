package io.github.matejkadlec.realmforge.spell;

import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Where a ground-targeted spell lands (RLM-346, architecture 11.2): on the block the crosshair hits within the spell's
 * range, and beyond the range, or at the sky, at the farthest point in range along that line, as League of Legends'
 * clamp cast does. Either way it lands on the ground: the top of the first block straight below that point, since the
 * spell comes down from above. A wall's face or a ceiling the line hits therefore lands on the floor beneath it, and a
 * clamped point in the air on the ground under it, however far down, or at the point itself above the void.
 */
public final class GroundPoints {
    /** How far a landing's search starts out of the face the line hit, in blocks: in the open, not in the block. */
    static final double FACE_OFFSET = 1e-3;

    private GroundPoints() {
    }

    /** The point {@code range} blocks from {@code from} toward {@code to}, or {@code to} itself when it is nearer. */
    public static Vec3 clamp(Vec3 from, Vec3 to, double range) {
        Vec3 offset = to.subtract(from);
        double length = offset.length();
        return length <= range ? to : from.add(offset.scale(range / length));
    }

    /** The point just out of the face {@code face} of a block, from the point {@code hit} on that face. */
    public static Vec3 offFace(Vec3 hit, Direction face) {
        return hit.add(face.getUnitVec3().scale(FACE_OFFSET));
    }

    /**
     * Where a spell lands along {@code direction} (a unit vector) from {@code caster}'s eyes, within {@code range}
     * blocks: on the ground below the first block the line hits, or below the point at the range when it hits none.
     */
    static Vec3 along(LivingEntity caster, Vec3 direction, double range) {
        BlockHitResult hit = Targets.clip(caster, direction, range);
        Vec3 start = hit.getType() == HitResult.Type.MISS
                ? caster.getEyePosition().add(direction.scale(range))
                : offFace(hit.getLocation(), hit.getDirection());
        return below(caster, start);
    }

    /**
     * The ground straight below {@code start}: the top of the first block there that stops movement (water lets it
     * through, as it lets the crosshair's line through), or {@code start} itself when no block lies below it.
     */
    static Vec3 below(LivingEntity caster, Vec3 start) {
        Vec3 bottom = new Vec3(start.x, caster.level().getMinY(), start.z);
        BlockHitResult ground = caster.level().clip(new ClipContext(start, bottom, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, caster));
        return ground.getType() == HitResult.Type.MISS ? start : ground.getLocation();
    }
}
