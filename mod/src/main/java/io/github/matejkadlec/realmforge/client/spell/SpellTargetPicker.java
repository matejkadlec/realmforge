package io.github.matejkadlec.realmforge.client.spell;

import io.github.matejkadlec.realmforge.spell.Spell;
import java.util.Optional;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The entity a spell bar key aims at (architecture 11.2 step 1): the first living entity under the crosshair within the
 * spell's range, not behind a block, as vanilla picks what the crosshair points at, only farther. The server checks the
 * target again; a spell on the caster itself aims at nothing.
 */
public final class SpellTargetPicker {
    private SpellTargetPicker() {
    }

    public static Optional<LivingEntity> pick(LocalPlayer player, Spell spell) {
        double range = spell.targeting().reach();
        if (range <= 0) {
            return Optional.empty();
        }
        Vec3 eye = player.getEyePosition();
        Vec3 view = player.getViewVector(1.0F).scale(range);
        HitResult block = player.pick(range, 1.0F, false);
        double reach = block.getType() == HitResult.Type.MISS ? range * range
                : block.getLocation().distanceToSqr(eye);
        AABB area = player.getBoundingBox().expandTowards(view).inflate(1.0);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(player, eye, eye.add(view), area,
                SpellTargetPicker::targetable, reach);
        return hit != null && hit.getEntity() instanceof LivingEntity living ? Optional.of(living) : Optional.empty();
    }

    private static boolean targetable(Entity entity) {
        return entity instanceof LivingEntity && entity.isAlive() && !entity.isSpectator() && entity.isPickable();
    }
}
