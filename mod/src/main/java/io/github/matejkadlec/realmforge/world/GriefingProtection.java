package io.github.matejkadlec.realmforge.world;

import java.util.Set;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.BlockAttachedEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.LargeFireball;
import net.minecraft.world.entity.projectile.WitherSkull;
import net.minecraft.world.entity.vehicle.VehicleEntity;
import net.minecraft.world.level.Explosion;
import net.neoforged.neoforge.event.entity.EntityMobGriefingEvent;
import net.neoforged.neoforge.event.entity.living.LivingDestroyBlockEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;

/**
 * Targeted protection against destructive mobs instead of {@code mobGriefing=false} (decision D6, architecture 19.1):
 * mobs never damage blocks, while villagers still farm, piglins barter and mobs pick up items. Players keep their own
 * destruction: TNT, beds and respawn anchors still blow up blocks.
 */
public final class GriefingProtection {
    /**
     * Mobs whose every griefing check changes blocks, so denying {@link EntityMobGriefingEvent} for them stops nothing
     * else: endermen taking blocks, ravagers breaking leaves and crops, silverfish infesting and breaking stone, the
     * wither breaking blocks and leaving wither roses, blaze fireballs setting fire, rabbits eating carrot crops. The
     * event names the entity but not the action, so a mob whose checks also gate something harmless (a zombie's also
     * gates picking up items) needs an action-specific hook or stays allowed.
     */
    public static final Set<EntityType<?>> TERRAIN_GRIEFERS = Set.of(
            EntityType.ENDERMAN,
            EntityType.RAVAGER,
            EntityType.SILVERFISH,
            EntityType.WITHER,
            EntityType.BLAZE,
            EntityType.RABBIT);

    private GriefingProtection() {
    }

    /**
     * Whether a mob caused the explosion: the creeper itself, the ghast or wither that shot the projectile, the mob a
     * primed TNT names as its owner. A ghast fireball or wither skull counts even when its shooter died or unloaded in
     * flight, which leaves it without an owner ({@code Projectile#getOwner}): only those mobs fire them, and a player
     * who deflects one becomes its owner. Explosions without a living cause (TNT lit by redstone, beds, respawn
     * anchors) and those a player caused are not.
     */
    public static boolean isMobCaused(Explosion explosion) {
        LivingEntity cause = explosion.getIndirectSourceEntity();
        if (cause != null) {
            return !(cause instanceof Player);
        }
        Entity source = explosion.getDirectSourceEntity();
        return source instanceof LargeFireball || source instanceof WitherSkull;
    }

    /**
     * Objects a player placed that an explosion would break although they are not blocks: item frames, paintings and
     * leash knots, armor stands, boats and minecarts (with their contents).
     */
    public static boolean isPlacedObject(Entity entity) {
        return entity instanceof BlockAttachedEntity || entity instanceof ArmorStand || entity instanceof VehicleEntity;
    }

    /**
     * A mob's explosion destroys no blocks (fire lands only where blocks were destroyed, so it sets none) and spares
     * placed objects; it still hurts and pushes players and mobs.
     */
    static void onDetonate(ExplosionEvent.Detonate event) {
        if (isMobCaused(event.getExplosion())) {
            event.getAffectedBlocks().clear();
            event.getAffectedEntities().removeIf(GriefingProtection::isPlacedObject);
        }
    }

    static void onMobGriefing(EntityMobGriefingEvent event) {
        if (TERRAIN_GRIEFERS.contains(event.getEntity().getType())) {
            event.setCanGrief(false);
        }
    }

    /**
     * The action-specific hook for mobs that break blocks with their bodies: zombies and vindicators breaking doors,
     * the ender dragon flying through blocks, the wither breaking out ({@code CommonHooks#canEntityDestroy}).
     */
    static void onDestroyBlock(LivingDestroyBlockEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            event.setCanceled(true);
        }
    }

    /** Mobs never trample farmland; players still do. */
    static void onFarmlandTrample(BlockEvent.FarmlandTrampleEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            event.setCanceled(true);
        }
    }
}
