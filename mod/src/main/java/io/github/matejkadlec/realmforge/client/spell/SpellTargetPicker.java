package io.github.matejkadlec.realmforge.client.spell;

import io.github.matejkadlec.realmforge.spell.AllyTargeting;
import io.github.matejkadlec.realmforge.spell.DirectionTargeting;
import io.github.matejkadlec.realmforge.spell.EnemyTargeting;
import io.github.matejkadlec.realmforge.spell.Spell;
import io.github.matejkadlec.realmforge.spell.Targets;
import java.util.Optional;
import java.util.function.Predicate;
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
 * spell's range, not behind a block, as vanilla picks what the crosshair points at, only farther. An enemy spell sees
 * through allies and an ally spell through enemies, so a heal aimed past a mob takes the caster, as an ally spell
 * without a target does. The server checks the target again, and when this finds none it picks the mob near the
 * crosshair itself (aim assistance, RLM-345, {@code spell/AimAssist}). A spell on the caster itself aims at nothing,
 * and so does a direction spell (RLM-344): it flies where the crosshair points, the player's look, which the server
 * reads itself, and the server assists it as it leaves.
 */
public final class SpellTargetPicker {
    private SpellTargetPicker() {
    }

    public static Optional<LivingEntity> pick(LocalPlayer player, Spell spell) {
        double range = spell.targeting().reach(player);
        if (range <= 0 || spell.targeting() instanceof DirectionTargeting) {
            return Optional.empty();
        }
        Vec3 eye = player.getEyePosition();
        Vec3 view = player.getViewVector(1.0F).scale(range);
        HitResult block = player.pick(range, 1.0F, false);
        double reach = block.getType() == HitResult.Type.MISS ? range * range
                : block.getLocation().distanceToSqr(eye);
        AABB area = player.getBoundingBox().expandTowards(view).inflate(1.0);
        Predicate<Entity> fits = spell.targeting() instanceof AllyTargeting
                ? entity -> Targets.allied(player, (LivingEntity) entity)
                : spell.targeting() instanceof EnemyTargeting
                ? entity -> !Targets.allied(player, (LivingEntity) entity)
                : entity -> true;
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(player, eye, eye.add(view), area,
                entity -> targetable(entity) && fits.test(entity), reach);
        return hit != null && hit.getEntity() instanceof LivingEntity living ? Optional.of(living) : Optional.empty();
    }

    private static boolean targetable(Entity entity) {
        return entity instanceof LivingEntity && entity.isAlive() && !entity.isSpectator() && entity.isPickable();
    }
}
