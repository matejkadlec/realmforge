package io.github.matejkadlec.realmforge.spell;

import io.github.matejkadlec.realmforge.combat.CombatEvent;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Rage as a combat resource (architecture 11.5): a holder with {@link RageRules} gains rage from the melee hits it
 * deals and the damage attackers deal it, and loses it every second once it is out of combat. The rules come from
 * {@link Source}s that higher features add: {@code character} gives a rage class's from its data. A swing gives its
 * rage once, however many mobs it hits: only the holder's first melee hit in a game tick counts, so a sword's sweep or
 * Better Combat's swing through several targets (one full-strength {@code Player#attack} each) gives no more. Combat is
 * the last hit dealt or taken (a dodged one included) within the rules' {@code decayAfterTicks}; it is transient, so a
 * new player entity starts out of combat. Logical server only.
 */
public final class Rage {
    /** Gives a holder its rage rules, or nothing when it has none (a mana class, a mob). */
    @FunctionalInterface
    public interface Source {
        Optional<RageRules> rules(LivingEntity holder);
    }

    private static final List<Source> SOURCES = new CopyOnWriteArrayList<>();
    /** Every living entity's game time of its last hit dealt or taken; weak keys, so a removed one leaves nothing. */
    private static final Map<LivingEntity, Long> LAST_COMBAT = new WeakHashMap<>();
    /** Every rage holder's game time of its last melee hit that gave rage: one swing gives it once. */
    private static final Map<LivingEntity, Long> LAST_SWING = new WeakHashMap<>();

    private Rage() {
    }

    /** Adds a source of rage rules; the first source with rules for a holder decides. Called at mod construction. */
    public static void addSource(Source source) {
        SOURCES.add(source);
    }

    /** The holder's rage rules, empty when it gains no rage. */
    public static Optional<RageRules> rules(LivingEntity holder) {
        for (Source source : SOURCES) {
            Optional<RageRules> rules = source.rules(holder);
            if (rules.isPresent()) {
                return rules;
            }
        }
        return Optional.empty();
    }

    /** Whether the holder dealt or took a hit within the last {@code ticks} game ticks. */
    public static boolean inCombat(LivingEntity holder, int ticks) {
        Long last = LAST_COMBAT.get(holder);
        return last != null && holder.level().getGameTime() - last < ticks;
    }

    /** What the holder's rage pool regains every second ({@link ResourceTypes#RAGE}): minus its decay out of combat. */
    static double regeneration(LivingEntity holder) {
        return rules(holder).map(rules -> rules.regeneration(inCombat(holder, rules.decayAfterTicks()))).orElse(0.0);
    }

    static void onHit(CombatEvent.Hit event) {
        LivingEntity defender = event.getEntity();
        if (!(defender.level() instanceof ServerLevel)
                || !(event.getSource().getEntity() instanceof LivingEntity attacker) || attacker == defender) {
            return;
        }
        markCombat(attacker, defender);
        DamageSource source = event.getSource();
        if (event.getSchool().isEmpty() && melee(source, attacker)) {
            double strength = attacker instanceof Player player ? player.getAttackStrengthScale(0.5F) : 1;
            rules(attacker).ifPresent(rules -> {
                if (firstHitOfSwing(attacker)) {
                    gain(attacker, rules.forHitDealt(strength));
                }
            });
        }
        rules(defender).ifPresent(rules -> gain(defender,
                rules.forDamageTaken(event.getInflictedDamage(), defender.getMaxHealth())));
    }

    static void onDodge(CombatEvent.Dodge event) {
        if (event.getEntity().level() instanceof ServerLevel
                && event.getSource().getEntity() instanceof LivingEntity attacker && attacker != event.getEntity()) {
            markCombat(attacker, event.getEntity());
        }
    }

    /**
     * A melee hit: a player's or a mob's own attack, dealt by the attacker itself. Vanilla's {@code Player#attack}
     * resets the attack strength only after the hit, so a player's strength is still this swing's.
     */
    private static boolean melee(DamageSource source, LivingEntity attacker) {
        return source.getDirectEntity() == attacker && !source.is(DamageTypeTags.IS_PROJECTILE)
                && (source.is(DamageTypeTags.IS_PLAYER_ATTACK) || source.is(DamageTypes.MOB_ATTACK)
                        || source.is(DamageTypes.MOB_ATTACK_NO_AGGRO));
    }

    /**
     * Whether this is the holder's first melee hit in this game tick, and so the one its swing's rage comes from. The
     * main target is hurt before vanilla's sweep, and Better Combat attacks a swing's targets one after another in
     * the same tick; when the main hit is dodged or dropped, the first hit that lands counts.
     */
    private static boolean firstHitOfSwing(LivingEntity holder) {
        long now = holder.level().getGameTime();
        Long last = LAST_SWING.put(holder, now);
        return last == null || last != now;
    }

    private static void markCombat(LivingEntity attacker, LivingEntity defender) {
        long now = defender.level().getGameTime();
        LAST_COMBAT.put(attacker, now);
        LAST_COMBAT.put(defender, now);
    }

    private static void gain(LivingEntity holder, double rage) {
        if (rage > 0 && holder.isAlive()) {
            Resources.add(holder, ResourceTypes.RAGE, rage);
        }
    }
}
