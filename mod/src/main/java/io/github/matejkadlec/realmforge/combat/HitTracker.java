package io.github.matejkadlec.realmforge.combat;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;
import javax.annotation.Nullable;
import net.minecraft.core.Holder;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * What {@link DamagePipeline} learns about a hit in one damage event and needs in a later one: whether a player's
 * melee hit crit ({@code CriticalHitEvent}, before the target is hurt), whether a player's arrow or trident comes
 * from a full draw ({@code ProjectileImpactEvent}, before it hurts anything), and a hit's crit and a school hit's
 * {@code lastHurt} to restore ({@code LivingIncomingDamageEvent} to {@code LivingDamageEvent.Post}). Logical server
 * only, on the server thread.
 *
 * <p>Hits are kept per defender and matched by their damage source: a hit vanilla's hurt cooldown drops never reaches
 * {@code LivingDamageEvent.Post}, and one hit's events can nest inside another's (a proc). Entries from an earlier
 * game tick are stale (a dropped hit) and go at the defender's next hit. Weak keys, so a removed entity leaves
 * nothing behind.
 */
final class HitTracker {
    private static final Map<Player, Entity> MELEE_CRITS = new WeakHashMap<>();
    private static final Set<Entity> FULL_DRAWS = Collections.newSetFromMap(new WeakHashMap<>());
    private static final Map<LivingEntity, Deque<Hit>> HITS = new WeakHashMap<>();

    private HitTracker() {
    }

    /**
     * One hit in progress.
     *
     * @param lastHurt the defender's {@code lastHurt} before a school hit, to restore after it; NaN for other hits
     */
    record Hit(DamageSource source, long gameTime, Optional<Holder<SpellSchool>> school, boolean crit, float lastHurt) {
    }

    /** Records whether the player's melee attack on {@code target}, about to be dealt, is a crit. */
    static void markMeleeCrit(Player player, Entity target, boolean crit) {
        if (crit) {
            MELEE_CRITS.put(player, target);
        } else {
            MELEE_CRITS.remove(player);
        }
    }

    /** Whether the hit is the player's melee hit on {@code target} that {@link #markMeleeCrit} marked; forgets it. */
    static boolean takeMeleeCrit(DamageSource source, LivingEntity target) {
        if (source.getEntity() instanceof Player player && source.getDirectEntity() == player
                && MELEE_CRITS.get(player) == target) {
            MELEE_CRITS.remove(player);
            return true;
        }
        return false;
    }

    /** Records that a player's projectile, about to hit, was shot at full strength; it stays so for every target. */
    static void markFullDraw(Entity projectile) {
        FULL_DRAWS.add(projectile);
    }

    /** Whether {@link #markFullDraw} marked this projectile (a hit's direct entity, null for none). */
    static boolean fullDraw(@Nullable Entity projectile) {
        return projectile != null && FULL_DRAWS.contains(projectile);
    }

    static void start(LivingEntity defender, Hit hit) {
        Deque<Hit> hits = HITS.computeIfAbsent(defender, entity -> new ArrayDeque<>());
        hits.removeIf(stale -> stale.gameTime() < hit.gameTime());
        hits.addLast(hit);
    }

    /** The hit with this damage source on the defender, the latest first; removes it. */
    @Nullable
    static Hit finish(LivingEntity defender, DamageSource source) {
        Deque<Hit> hits = HITS.get(defender);
        if (hits == null) {
            return null;
        }
        for (Iterator<Hit> it = hits.descendingIterator(); it.hasNext(); ) {
            Hit hit = it.next();
            if (hit.source() == source) {
                it.remove();
                if (hits.isEmpty()) {
                    HITS.remove(defender);
                }
                return hit;
            }
        }
        return null;
    }
}
