package io.github.matejkadlec.realmforge.combat;

import java.util.OptionalLong;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

/**
 * Being in combat (RLM-348, architecture 9.4; Matěj, 2026-10-21 and 2026-10-22), WoW's rule: a player or mob is in
 * combat for {@link CombatConfig#IN_COMBAT_SECONDS} (10 s) after the last hit it dealt to, or took from, another living
 * entity, melee, projectiles and school hits alike, a dodged hit and one a raised weapon blocked in full included.
 * Damage from the world (falls, fire, lava, drowning, a vanilla poison tick) has no attacker, so it never starts or
 * extends combat. This is the one state every rule reads instead of a timer of its own: rage decays measured from the
 * same last hit ({@code spell/Rage}), and the scrolls refuse to be used in combat ({@code character/StatPoints}).
 *
 * <p>{@link DamagePipeline} marks both sides of each hit ({@link #mark}) in the transient
 * {@code realmforge:in_combat} attachment ({@link CombatTime}), synced to its owner, so a client knows its own
 * player's state; it is neither saved nor copied, so a new entity (a login, a respawn) starts out of combat.
 */
public final class InCombat {
    private InCombat() {
    }

    /**
     * Whether the entity is in combat now. On the server for any living entity; on a client for the local player only,
     * whose attachment is synced to it.
     */
    public static boolean isInCombat(LivingEntity entity) {
        return ticksLeft(entity) > 0;
    }

    /** The game ticks until the entity is out of combat; 0 when it is out of combat already. */
    public static long ticksLeft(LivingEntity entity) {
        return entity.getExistingData(CombatAttachments.IN_COMBAT).filter(CombatTime::exists)
                .map(time -> Math.max(0, time.until() - entity.level().getGameTime()))
                .orElse(0L);
    }

    /** The game ticks since the entity's last hit dealt or taken; empty when it had none since it joined the level. */
    public static OptionalLong ticksSinceLastHit(LivingEntity entity) {
        return entity.getExistingData(CombatAttachments.IN_COMBAT).filter(CombatTime::exists)
                .map(time -> OptionalLong.of(entity.level().getGameTime() - time.lastHit()))
                .orElse(OptionalLong.empty());
    }

    /**
     * Puts both sides of a hit at {@code defender} in combat, from the damage pipeline: a hit that dealt its damage,
     * a dodged one and one blocked in full. Only a hit from another living entity counts: the source's causing entity
     * (a projectile's shooter, a spell's caster), never the defender itself.
     */
    static void mark(LivingEntity defender, DamageSource source) {
        if (!(defender.level() instanceof ServerLevel level) || !(source.getEntity() instanceof LivingEntity attacker)
                || attacker == defender) {
            return;
        }
        long now = level.getGameTime();
        CombatTime time = new CombatTime(now, now + CombatConfig.inCombatTicks());
        set(attacker, time);
        set(defender, time);
    }

    /** Sets the entity's combat time, once per tick: a sweep or a volley hits many times in one. */
    private static void set(LivingEntity entity, CombatTime time) {
        if (entity.getExistingData(CombatAttachments.IN_COMBAT).map(CombatTime::lastHit).orElse(Long.MIN_VALUE)
                != time.lastHit()) {
            entity.setData(CombatAttachments.IN_COMBAT, time);
        }
    }
}
