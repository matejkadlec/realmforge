package io.github.matejkadlec.realmforge.spell;

import io.github.matejkadlec.realmforge.combat.CombatEvent;
import io.github.matejkadlec.realmforge.combat.SchoolDamageSource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.annotation.Nullable;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

/**
 * The proc engine (architecture 12): reacts to the damage pipeline's hits ({@link CombatEvent.Hit}) and to kills, and
 * fires the procs that {@link Source}s give the entities involved. A proc fires at its chance, outside its internal
 * cooldown, which starts when its spell or effect took hold (research SP-15).
 *
 * <p><b>Procs never trigger procs:</b> a proc's spell runs with the proc as its origin, which its hits carry in their
 * damage source ({@link SchoolDamageSource#procOrigin}), and this engine ignores every hit and kill that carries one.
 * Otherwise a sure on-hit proc that deals damage would trigger itself until the server's stack overflows. A vanilla
 * status effect's ticks have no attacker, so no proc reacts to them either; our own status effects (C3c) will keep
 * the origin of the proc that applied them.
 *
 * <p>Internal cooldowns are transient, by holder and proc id, with weak keys. Logical server only.
 */
public final class Procs {
    /** Gives a holder its procs: talents (C5) and items fill it, the QA mod adds one for its tests. */
    @FunctionalInterface
    public interface Source {
        List<Proc> procs(LivingEntity holder);
    }

    private static final List<Source> SOURCES = new CopyOnWriteArrayList<>();
    /** Holder to proc id to the game time its internal cooldown ends. */
    private static final Map<LivingEntity, Map<Identifier, Long>> COOLDOWNS = new WeakHashMap<>();

    private Procs() {
    }

    /** Adds a source of procs; every source's procs fire. Called at mod construction. */
    public static void addSource(Source source) {
        SOURCES.add(source);
    }

    /** Whether the holder's proc {@code id} waits out its internal cooldown at the current game time. */
    public static boolean isOnCooldown(LivingEntity holder, Identifier id) {
        Map<Identifier, Long> cooldowns = COOLDOWNS.get(holder);
        Long end = cooldowns != null ? cooldowns.get(id) : null;
        return end != null && holder.level().getGameTime() < end;
    }

    /** Ends every internal cooldown of the holder's procs: a reset for QA and tests. */
    public static void resetCooldowns(LivingEntity holder) {
        COOLDOWNS.remove(holder);
    }

    static void onHit(CombatEvent.Hit event) {
        LivingEntity defender = event.getEntity();
        DamageSource source = event.getSource();
        if (!(defender.level() instanceof ServerLevel) || fromProc(source)
                || !(source.getEntity() instanceof LivingEntity attacker) || attacker == defender) {
            return;
        }
        fire(attacker, Proc.Trigger.HIT, defender);
        if (event.isCrit()) {
            fire(attacker, Proc.Trigger.CRIT, defender);
        }
        fire(defender, Proc.Trigger.HIT_TAKEN, attacker);
    }

    /** Last, so a totem or another listener that saved the victim has cancelled the death already. */
    static void onDeath(LivingDeathEvent event) {
        LivingEntity victim = event.getEntity();
        DamageSource source = event.getSource();
        if (victim.level() instanceof ServerLevel && !fromProc(source)
                && source.getEntity() instanceof LivingEntity killer && killer != victim) {
            fire(killer, Proc.Trigger.KILL, victim);
        }
    }

    private static boolean fromProc(DamageSource source) {
        return SchoolDamageSource.procOriginOf(source).isPresent();
    }

    private static void fire(LivingEntity holder, Proc.Trigger trigger, LivingEntity other) {
        if (!holder.isAlive()) {
            return;
        }
        for (Source source : SOURCES) {
            for (Proc proc : source.procs(holder)) {
                if (proc.trigger() == trigger && !isOnCooldown(holder, proc.id())
                        && holder.getRandom().nextDouble() < proc.chance() && run(proc, holder, other)) {
                    startCooldown(holder, proc);
                }
            }
        }
    }

    /** Runs the proc's action; whether its spell was cast or its effect took hold. */
    private static boolean run(Proc proc, LivingEntity holder, LivingEntity other) {
        boolean kill = proc.trigger() == Proc.Trigger.KILL;
        return switch (proc.action()) {
            case Proc.CastSpell cast -> {
                Optional<Holder.Reference<Spell>> spell = Spells.get(holder.registryAccess(), cast.spell());
                if (spell.isEmpty()) {
                    yield false;
                }
                int level = Math.min(cast.level(), spell.get().value().maxLevel());
                SpellAim aim = kill ? SpellAim.at(other.position()) : SpellAim.at(other);
                yield SpellCaster.castProc(holder, spell.get(), level, aim, proc.id()).success();
            }
            case Proc.ApplyEffect apply -> {
                LivingEntity target = apply.onSelf() ? holder : alive(other);
                yield target != null && target.addEffect(
                        new MobEffectInstance(apply.effect(), apply.durationTicks(), apply.amplifier()), holder);
            }
        };
    }

    @Nullable
    private static LivingEntity alive(LivingEntity entity) {
        return entity.isAlive() ? entity : null;
    }

    private static void startCooldown(LivingEntity holder, Proc proc) {
        if (proc.cooldownTicks() > 0) {
            COOLDOWNS.computeIfAbsent(holder, key -> new HashMap<>())
                    .put(proc.id(), holder.level().getGameTime() + proc.cooldownTicks());
        }
    }
}
