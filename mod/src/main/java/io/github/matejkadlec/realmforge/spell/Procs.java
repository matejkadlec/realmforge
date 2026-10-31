package io.github.matejkadlec.realmforge.spell;

import io.github.matejkadlec.realmforge.combat.CombatEvent;
import io.github.matejkadlec.realmforge.combat.SchoolDamageSource;
import io.github.matejkadlec.realmforge.effect.EffectSource;
import io.github.matejkadlec.realmforge.effect.Effects;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.annotation.Nullable;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;

/**
 * The proc engine (architecture 12): reacts to the damage pipeline's hits ({@link CombatEvent.Hit}), a damage over
 * time's ticks among them, to kills and to status effects given to another entity, and fires the procs that
 * {@link Source}s give the entities involved. A proc fires at its chance, outside its internal cooldown, which starts
 * when its spell, effect or window took hold (research SP-15), on its holder or, for a passive spell's proc, on each
 * target (RLM-540).
 *
 * <p><b>Procs never trigger procs:</b> a proc's spell runs with the proc as its origin, which its hits carry in their
 * damage source ({@link SchoolDamageSource#procOrigin}), and this engine ignores every hit and kill that carries one.
 * Otherwise a sure on-hit proc that deals damage would trigger itself until the server's stack overflows. A vanilla
 * status effect's ticks have no attacker, so no proc reacts to them either; our own status effects keep the origin of
 * the proc whose spell applied them ({@code EffectSource#procOrigin}), and their ticks carry it. And nothing a proc's
 * action sets off while it runs (a status effect it gives, a spell it casts) fires another proc.
 *
 * <p>Internal cooldowns are transient, by holder and proc id, with weak keys. Logical server only.
 */
public final class Procs {
    /** Gives a holder its procs: items and talents (postponed, RLM-28) fill it, the QA mod adds one for its tests. */
    @FunctionalInterface
    public interface Source {
        List<Proc> procs(LivingEntity holder);
    }

    private static final List<Source> SOURCES = new CopyOnWriteArrayList<>();
    /** Holder to proc (and target, for a per-target cooldown) to the game time its internal cooldown ends. */
    private static final Map<LivingEntity, Map<CooldownKey, Long>> COOLDOWNS = new WeakHashMap<>();
    /** How many proc actions are running now: while one runs, nothing fires another proc. Server thread only. */
    private static int running;

    private Procs() {
    }

    /** Adds a source of procs; every source's procs fire. Called at mod construction. */
    public static void addSource(Source source) {
        SOURCES.add(source);
    }

    /** Whether the holder's proc {@code id} waits out its internal cooldown at the current game time. */
    public static boolean isOnCooldown(LivingEntity holder, Identifier id) {
        return isOnCooldown(holder, new CooldownKey(id, null));
    }

    /** Whether the holder's proc {@code id} waits out its internal cooldown on {@code target} (a per-target cooldown). */
    public static boolean isOnCooldown(LivingEntity holder, Identifier id, LivingEntity target) {
        return isOnCooldown(holder, new CooldownKey(id, target.getUUID()));
    }

    private static boolean isOnCooldown(LivingEntity holder, CooldownKey key) {
        Map<CooldownKey, Long> cooldowns = COOLDOWNS.get(holder);
        Long end = cooldowns != null ? cooldowns.get(key) : null;
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
        SchoolDamageSource.tickOf(source).ifPresent(tick -> fire(attacker, Proc.Trigger.TICK, defender,
                Optional.of(tick.effect()), tick.spell()));
        fire(defender, Proc.Trigger.HIT_TAKEN, attacker);
    }

    /**
     * A status effect another entity gave {@code event}'s entity: its giver's {@link Proc.Trigger#APPLIES_EFFECT} procs
     * that watch the effect (Frostbite on Slowness). Fires even when the application only refreshes the effect.
     */
    static void onEffectAdded(MobEffectEvent.Added event) {
        LivingEntity target = event.getEntity();
        if (target.level() instanceof ServerLevel && event.getEffectSource() instanceof LivingEntity giver
                && giver != target) {
            fire(giver, Proc.Trigger.APPLIES_EFFECT, target,
                    event.getEffectInstance().getEffect().unwrapKey().map(ResourceKey::identifier), Optional.empty());
        }
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
        fire(holder, trigger, other, Optional.empty(), Optional.empty());
    }

    private static void fire(LivingEntity holder, Proc.Trigger trigger, LivingEntity other,
            Optional<Identifier> effect, Optional<Identifier> spell) {
        if (!holder.isAlive() || running > 0) {
            return;
        }
        for (Source source : SOURCES) {
            for (Proc proc : source.procs(holder)) {
                CooldownKey key = new CooldownKey(proc.id(), proc.perTarget() ? other.getUUID() : null);
                if (proc.trigger() == trigger && proc.watches(effect, spell) && !isOnCooldown(holder, key)
                        && holder.getRandom().nextDouble() < proc.chance() && run(proc, holder, other)) {
                    startCooldown(holder, proc, key);
                }
            }
        }
    }

    /** Runs the proc's action; whether its spell was cast, its effect took hold or its window opened. */
    private static boolean run(Proc proc, LivingEntity holder, LivingEntity other) {
        running++;
        try {
            return act(proc, holder, other);
        } finally {
            running--;
        }
    }

    private static boolean act(Proc proc, LivingEntity holder, LivingEntity other) {
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
                yield target != null && Effects.apply(target,
                        new MobEffectInstance(apply.effect(), apply.durationTicks(), apply.amplifier()), holder,
                        new EffectSource(holder.getUUID(), Optional.empty(), Optional.of(proc.id())));
            }
            case Proc.OpenWindow open -> holder instanceof Player player && Windows.open(player, open.spell());
        };
    }

    @Nullable
    private static LivingEntity alive(LivingEntity entity) {
        return entity.isAlive() ? entity : null;
    }

    private static void startCooldown(LivingEntity holder, Proc proc, CooldownKey key) {
        if (proc.cooldownTicks() > 0) {
            COOLDOWNS.computeIfAbsent(holder, entity -> new HashMap<>())
                    .put(key, holder.level().getGameTime() + proc.cooldownTicks());
        }
    }

    /** A proc's internal cooldown: on its holder ({@code target} null) or on one target. */
    private record CooldownKey(Identifier proc, @Nullable UUID target) {
    }
}
