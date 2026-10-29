package io.github.matejkadlec.realmforge.mob.ai;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;

/** Turning a mob that stands to face what it acts on: a caster at its cast's target, a boss while it casts. */
public final class Facing {
    private Facing() {
    }

    /** Turns {@code mob}'s body, head and eyes to {@code target} at once, as it stands. */
    public static void face(Mob mob, Entity target) {
        double dx = target.getX() - mob.getX();
        double dz = target.getZ() - mob.getZ();
        if (dx * dx + dz * dz > 1e-6) {
            float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
            mob.setYRot(yaw);
            mob.setYBodyRot(yaw);
            mob.setYHeadRot(yaw);
        }
        mob.getLookControl().setLookAt(target, 90.0F, 90.0F);
    }
}
