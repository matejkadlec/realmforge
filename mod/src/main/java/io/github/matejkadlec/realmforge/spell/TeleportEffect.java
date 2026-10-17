package io.github.matejkadlec.realmforge.spell;

import com.mojang.serialization.MapCodec;
import java.util.Optional;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * {@code realmforge:teleport}: moves the caster at once to the target's position: a ground point, the end of a
 * direction's ray (its range, or the first block in the way; the caster's feet go where its eyes would) or an entity's
 * feet. A destination inside blocks moves back toward the caster until it is free; with no free spot on the way the
 * caster stays.
 */
public enum TeleportEffect implements SpellEffect {
    INSTANCE;

    public static final MapCodec<TeleportEffect> MAP_CODEC = MapCodec.unit(INSTANCE);

    /** How far back toward the caster each try for a free spot steps, in blocks. */
    private static final double STEP = 0.25;

    @Override
    public MapCodec<TeleportEffect> codec() {
        return MAP_CODEC;
    }

    @Override
    public void apply(EffectContext context, SpellTarget target) {
        LivingEntity caster = context.caster();
        Vec3 destination = target.kind() == SpellTarget.Kind.DIRECTION
                ? target.position().subtract(0, caster.getEyeHeight(), 0)
                : target.position();
        freeSpot(caster, caster.position(), destination).ifPresent(spot -> {
            caster.teleportTo(spot.x, spot.y, spot.z);
            caster.resetFallDistance();
        });
    }

    /**
     * The spot nearest {@code to} on the line from {@code from} where {@code caster}'s box touches no block, if any
     * (the caster's own spot counts only when it is free too).
     */
    static Optional<Vec3> freeSpot(LivingEntity caster, Vec3 from, Vec3 to) {
        Vec3 back = from.subtract(to);
        double length = back.length();
        int steps = (int) Math.ceil(length / STEP);
        for (int step = 0; step <= steps; step++) {
            Vec3 spot = step == steps ? from : to.add(back.scale(step * STEP / length));
            AABB box = caster.getDimensions(caster.getPose()).makeBoundingBox(spot);
            if (caster.level().noCollision(caster, box)) {
                return Optional.of(spot);
            }
        }
        return Optional.empty();
    }
}
