package io.github.matejkadlec.realmforge.effect;

import io.github.matejkadlec.realmforge.combat.DamagePipeline;
import io.github.matejkadlec.realmforge.combat.SpellSchools;
import java.util.Collection;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/**
 * Stuns (C4; Shield Bash, architecture 11.4, 11.7): a status effect of ours marked {@code stun} keeps its holder from
 * acting while it lasts. Its own modifiers stop walking and jumping (the pipeline makes every stun give them); this
 * class does the rest, on the logical server:
 *
 * <ul>
 *   <li>a stunned mob's AI stands still: before it ticks, its goals lose their move, look and jump controls (vanilla's
 *   {@code Mob#updateControlFlags} gives them back every 5 ticks, after the goals ran) and its path ends, so its
 *   melee, ranged and swelling goals stop;</li>
 *   <li>a stunned attacker's melee hit deals nothing (a slime's touch, a player's swing), and a stunned player cannot
 *   start an attack;</li>
 *   <li>the spell engine refuses a stunned caster's spells and interrupts the cast in progress
 *   ({@code spell/SpellCaster}, {@code spell/Casting}), but the spells cast while stunned (Will To Survive).</li>
 * </ul>
 *
 * Ordinary mobs have no stun immunity and no diminishing returns (Matěj, 2026-10-24, RLM-392); bosses get theirs one
 * by one later.
 */
public final class Stuns {
    private Stuns() {
    }

    static void register() {
        NeoForge.EVENT_BUS.addListener(EntityTickEvent.Pre.class, Stuns::onEntityTick);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, LivingIncomingDamageEvent.class, Stuns::onIncomingDamage);
        NeoForge.EVENT_BUS.addListener(AttackEntityEvent.class, Stuns::onAttack);
    }

    /** Whether {@code effect} is a stun. */
    public static boolean isStun(Holder<MobEffect> effect) {
        return effect.value() instanceof StatusEffect status && status.isStun();
    }

    /** Whether {@code entity} is stunned now. */
    public static boolean isStunned(LivingEntity entity) {
        Collection<MobEffectInstance> effects = entity.getActiveEffects();
        if (effects.isEmpty()) {
            return false;
        }
        for (MobEffectInstance instance : effects) {
            if (isStun(instance.getEffect())) {
                return true;
            }
        }
        return false;
    }

    private static void onEntityTick(EntityTickEvent.Pre event) {
        if (event.getEntity() instanceof Mob mob && mob.level() instanceof ServerLevel && isStunned(mob)) {
            mob.goalSelector.disableControlFlag(Goal.Flag.MOVE);
            mob.goalSelector.disableControlFlag(Goal.Flag.LOOK);
            mob.goalSelector.disableControlFlag(Goal.Flag.JUMP);
            mob.getNavigation().stop();
        }
    }

    /**
     * A stunned attacker's own melee hit lands nothing: the hit it deals in person, never a school hit (its damage over
     * time keeps ticking) nor a projectile it loosed before the stun.
     */
    private static void onIncomingDamage(LivingIncomingDamageEvent event) {
        DamageSource source = event.getSource();
        if (source.getEntity() instanceof LivingEntity attacker && source.getDirectEntity() == attacker
                && event.getEntity().level() instanceof ServerLevel level && isStunned(attacker)
                && DamagePipeline.meleeOrRanged(source) && SpellSchools.of(level.registryAccess(), source).isEmpty()) {
            event.setCanceled(true);
        }
    }

    private static void onAttack(AttackEntityEvent event) {
        if (isStunned(event.getEntity())) {
            event.setCanceled(true);
        }
    }
}
