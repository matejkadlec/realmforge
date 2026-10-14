package io.github.matejkadlec.realmforge.combat;

import io.github.matejkadlec.realmforge.generated.GeneratedStats;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * The magic shield (architecture 9.1; Matěj, 2026-10-13: "a secondary healthbar, that is hit first, and only when
 * empty the player begin to lose health; mainly for casters"): a pool of up to {@code realmforge:magic_shield} points
 * that takes a hit's damage after absorption hearts and before health, and refills to full
 * {@value #REFILL_DELAY_TICKS} ticks after the last damage the player took. Logical server only; the HUD bar reads
 * the synced {@link CombatAttachments#MAGIC_SHIELD}.
 *
 * <p>The pool takes its share through NeoForge's absorption reduction, which vanilla applies after
 * {@code LivingDamageEvent.Pre}: the hit's inflicted damage, which the hurt cooldown measures the next hit against
 * ({@code lastHurt}), stays the whole hit, and so does the damage the combat text shows. The reduction then subtracts
 * the total from the absorption hearts, which is right because the shield only takes what the hearts left: they are
 * empty whenever it takes anything.
 */
public final class MagicShields {
    /** How long after the last damage the shield refills: 5 seconds (proposal, settled in the playtest). */
    public static final int REFILL_DELAY_TICKS = 100;

    private MagicShields() {
    }

    /** The points left in the entity's magic shield; 0 for an entity that has none. */
    public static float points(LivingEntity entity) {
        return entity.getExistingData(CombatAttachments.MAGIC_SHIELD).map(MagicShield::points).orElse(0F);
    }

    /**
     * Whether the defender takes part: an entity with a magic shield stat, or points left from one. Its hits then
     * restart the refill delay, even while the pool is empty.
     */
    static boolean applies(LivingEntity defender) {
        return DamagePipeline.stat(defender, GeneratedStats.MAGIC_SHIELD) > 0 || points(defender) > 0;
    }

    /**
     * The absorption reduction with the shield's share added ({@code DamageContainer.IReductionFunction}): the shield
     * takes the damage the absorption hearts left, up to its points, and its refill waits again after any damage.
     */
    static float absorb(LivingEntity defender, DamageContainer container, float absorbed) {
        float damage = container.getNewDamage();
        if (damage <= 0) {
            return absorbed;
        }
        MagicShield shield = defender.getData(CombatAttachments.MAGIC_SHIELD);
        float taken = (float) DamageCalculator.shieldAbsorbs(damage - absorbed, shield.points());
        defender.setData(CombatAttachments.MAGIC_SHIELD,
                new MagicShield(shield.points() - taken, defender.tickCount + REFILL_DELAY_TICKS));
        return absorbed + taken;
    }

    /** Keeps the pool within the stat and refills it once the delay has passed. */
    static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (!(player.level() instanceof ServerLevel)) {
            return;
        }
        float max = (float) DamagePipeline.stat(player, GeneratedStats.MAGIC_SHIELD);
        MagicShield shield = player.getExistingData(CombatAttachments.MAGIC_SHIELD).orElse(MagicShield.EMPTY);
        float points = shield.points() > max || player.tickCount >= shield.refillAt() ? max : shield.points();
        if (points != shield.points()) {
            player.setData(CombatAttachments.MAGIC_SHIELD, new MagicShield(points, shield.refillAt()));
        }
    }
}
