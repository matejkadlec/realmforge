package io.github.matejkadlec.realmforge.spell;

import com.mojang.datafixers.util.Either;
import io.github.matejkadlec.realmforge.combat.DamagePipeline;
import io.github.matejkadlec.realmforge.generated.GeneratedStats;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Casts spells, the same for players, mobs and bosses (architecture 11.2). Server-authoritative: a client's intent (the
 * spell bar, C3b) and a mob's AI (W3, W5) both end here, and every check runs on the server.
 *
 * <p>A cast is refused, paying nothing and running nothing, when (in this order; research SP-3 names each reason):
 * <ol>
 *   <li>the caster is dead or a spectator;</li>
 *   <li>the spell does not exist;</li>
 *   <li>a player casts an {@code npc_only} spell, or one their spellbook does not know;</li>
 *   <li>a cast-time or channelled spell of the caster is still in progress;</li>
 *   <li>the spell's own cooldown runs, or the global cooldown runs and the spell is on it;</li>
 *   <li>its targeting finds no valid target (an entity or a point) in range and in line of sight;</li>
 *   <li>a player has less of the spell's resource than it costs.</li>
 * </ol>
 * Otherwise the spell starts the global cooldown, which every spell is on (Matěj, 2026-10-24), and pays its cost at
 * once, whatever its cast (Matěj, 2026-10-24: a cast time pays when it begins). An instant spell then starts its own
 * cooldown (Cooldown Reduction shortens it, never the global one) and runs every effect on the target ({@link
 * SpellTarget}). A cast-time or channelled spell begins its cast ({@link Casting}): a cast time starts its own cooldown
 * when it completes, a channel when it begins, and a channel pays its cost again every second it lasts. Mobs and bosses
 * pay no cost: their cooldowns pace their spells (architecture 11.2 step 6).
 *
 * <p>The spell bar may replace the cast in progress (C3b, RLM-25; Matěj, 2026-10-19: with spell queueing off, a new
 * spell cancels the cast and casts at once): with {@code replaceCast}, a cast of another spell skips the
 * already-casting check, and only once every other check passed is the cast in progress cancelled, so a refused spell
 * never cancels it. Two spells never cast at once.
 *
 * <p>A proc casts its spell through {@link #castProc}: at once and for free, whatever the caster is doing.
 */
public final class SpellCaster {
    /** Leaves room for float rounding, so a pool of exactly the cost pays it. */
    private static final double COST_TOLERANCE = 1e-4;

    private SpellCaster() {
    }

    /**
     * {@code caster} casts {@code spell} at the entity {@code target}, or at nothing when it is null, as
     * {@link #cast(LivingEntity, ResourceKey, SpellAim)} does.
     */
    public static CastResult cast(LivingEntity caster, ResourceKey<Spell> spell, @Nullable LivingEntity target) {
        return cast(caster, spell, SpellAim.at(target));
    }

    /**
     * {@code caster} casts {@code spell} at what {@code aim} names. A player casts at the level their spellbook knows
     * (at most the spell's {@code max_level}); a mob or boss at level 1.
     */
    public static CastResult cast(LivingEntity caster, ResourceKey<Spell> spell, SpellAim aim) {
        return cast(caster, spell, aim, false);
    }

    /**
     * {@code caster} casts {@code spell} at what {@code aim} names, as {@link #cast(LivingEntity, ResourceKey,
     * SpellAim)} does; with {@code replaceCast}, a cast of another spell in progress gives way to it once every other
     * check passed (the spell bar, C3b).
     */
    public static CastResult cast(LivingEntity caster, ResourceKey<Spell> spell, SpellAim aim, boolean replaceCast) {
        if (!canCast(caster)) {
            return CastResult.refused(CastFailure.CANNOT_CAST);
        }
        Optional<Holder.Reference<Spell>> holder = caster.registryAccess().lookupOrThrow(SpellRegistries.SPELL)
                .get(spell);
        if (holder.isEmpty()) {
            return CastResult.refused(CastFailure.UNKNOWN_SPELL);
        }
        int level = 1;
        if (caster instanceof Player player) {
            if (holder.get().value().npcOnly()) {
                return CastResult.refused(CastFailure.NPC_ONLY);
            }
            Optional<Integer> known = player.getData(SpellAttachments.SPELLBOOK).level(spell);
            if (known.isEmpty()) {
                return CastResult.refused(CastFailure.NOT_KNOWN);
            }
            level = Math.min(known.get(), holder.get().value().maxLevel());
        }
        return cast(caster, holder.get(), level, aim, replaceCast);
    }

    /**
     * {@code caster} casts {@code spell} at {@code level} (1 to its {@code max_level}) at what {@code aim} names,
     * without the spellbook: mobs, bosses and, later, procs. A player still pays the cost.
     */
    public static CastResult cast(LivingEntity caster, Holder.Reference<Spell> spell, int level, SpellAim aim) {
        return cast(caster, spell, level, aim, false);
    }

    private static CastResult cast(LivingEntity caster, Holder.Reference<Spell> spell, int level, SpellAim aim,
            boolean replaceCast) {
        if (!(caster.level() instanceof ServerLevel serverLevel)) {
            throw new IllegalStateException("Spells are cast on the logical server only");
        }
        if (!canCast(caster)) {
            return CastResult.refused(CastFailure.CANNOT_CAST);
        }
        Spell value = spell.value();
        CombatState state = caster.getData(SpellAttachments.COMBAT);
        Optional<CombatState.Cast> current = state.currentCast();
        if (current.isPresent() && (!replaceCast || current.get().spell().equals(spell.key()))) {
            return CastResult.refused(CastFailure.ALREADY_CASTING);
        }
        long now = serverLevel.getGameTime();
        if (state.isOnCooldown(spell.key(), now)) {
            return CastResult.refused(CastFailure.ON_COOLDOWN);
        }
        if (state.isOnGlobalCooldown(now)) {
            return CastResult.refused(CastFailure.ON_GLOBAL_COOLDOWN);
        }
        Either<SpellTarget, CastFailure> resolved = value.targeting().resolve(caster, aim);
        Optional<CastFailure> missed = resolved.right();
        if (missed.isPresent()) {
            return CastResult.refused(missed.get());
        }
        SpellTarget hit = resolved.left().orElseThrow();
        if (!canPay(caster, value, level, state)) {
            return CastResult.refused(CastFailure.NOT_ENOUGH_RESOURCE);
        }
        if (current.isPresent()) {
            Casting.cancel(caster);
        }

        state.setGlobalCooldownEnd(now + value.globalCooldownTicks());
        payCost(caster, value, level, state);
        if (value.cast().type() == SpellCast.Type.CAST_TIME) {
            Casting.begin(caster, spell, level, hit, now);
            return CastResult.begun(hit);
        }
        startCooldown(caster, value, spell.key(), state, now);
        if (value.cast().type() == SpellCast.Type.CHANNEL) {
            Casting.begin(caster, spell, level, hit, now);
            return CastResult.begun(hit);
        }
        caster.syncData(SpellAttachments.COMBAT);
        runEffects(serverLevel, caster, spell, level, hit);
        return CastResult.cast(hit);
    }

    /**
     * {@code caster} casts {@code spell} at {@code level} as the proc {@code origin} (architecture 12): instantly,
     * whatever its cast type, without its cost, its cooldowns or the global cooldown, and while another cast runs.
     * Only the caster's state (alive, not a spectator) and the spell's targeting can refuse it. Its effects run with
     * the proc as their origin, so their hits trigger no proc.
     */
    public static CastResult castProc(LivingEntity caster, Holder.Reference<Spell> spell, int level, SpellAim aim,
            Identifier origin) {
        if (!(caster.level() instanceof ServerLevel serverLevel)) {
            throw new IllegalStateException("Spells are cast on the logical server only");
        }
        if (!canCast(caster)) {
            return CastResult.refused(CastFailure.CANNOT_CAST);
        }
        Either<SpellTarget, CastFailure> resolved = spell.value().targeting().resolve(caster, aim);
        Optional<CastFailure> missed = resolved.right();
        if (missed.isPresent()) {
            return CastResult.refused(missed.get());
        }
        SpellTarget hit = resolved.left().orElseThrow();
        runEffects(serverLevel, caster, spell, level, hit, Optional.of(origin));
        return CastResult.cast(hit);
    }

    /** Whether {@code caster} has enough of the spell's resource for a cast at {@code level}; mobs always do. */
    static boolean canPay(LivingEntity caster, Spell spell, int level, CombatState state) {
        double cost = cost(caster, spell, level);
        return cost <= 0 || state.resource(resource(spell)) + COST_TOLERANCE >= cost;
    }

    /** Pays the cost, players only: a cast's, or a channel's for one more second. */
    static void payCost(LivingEntity caster, Spell spell, int level, CombatState state) {
        double cost = cost(caster, spell, level);
        if (cost > 0) {
            ResourceKey<ResourceType> type = resource(spell);
            state.setResource(type, (float) Math.max(0, state.resource(type) - cost));
        }
    }

    /** Starts the spell's own cooldown, after Cooldown Reduction. */
    static void startCooldown(LivingEntity caster, Spell spell, ResourceKey<Spell> key, CombatState state, long now) {
        int cooldown = SpellScaling.cooldownTicks(spell.cooldownTicks(),
                DamagePipeline.stat(caster, GeneratedStats.COOLDOWN_REDUCTION));
        state.clearEndedCooldowns(now);
        if (cooldown > 0) {
            state.setCooldownEnd(key, now + cooldown);
        }
    }

    /**
     * Why {@code target} may no longer take the effects of {@code caster}'s spell, checked again before a cast time's
     * effects and before every tick of a channel (architecture 11.2 steps 4 and 5): an entity that died or is gone, or
     * that the spell's targeting refuses now (out of range, behind blocks, no longer an enemy or ally). A spell that
     * locks on only needs its target alive and in the caster's dimension. A ground point or a direction stays as it
     * was resolved when the cast began.
     */
    static Optional<CastFailure> recheck(LivingEntity caster, Spell spell, SpellTarget spellTarget) {
        LivingEntity target = spellTarget.entityOrNull();
        if (target == null || target == caster) {
            return Optional.empty();
        }
        if (!target.isAlive() || target.isRemoved()) {
            return Optional.of(CastFailure.NO_TARGET);
        }
        if (spell.cast().lockOn()) {
            return target.level() == caster.level() ? Optional.empty() : Optional.of(CastFailure.OUT_OF_RANGE);
        }
        return spell.targeting().resolve(caster, SpellAim.at(target)).right();
    }

    /** Runs every effect of the spell on {@code target}, with the caster's stats as they are now. */
    static void runEffects(ServerLevel level, LivingEntity caster, Holder<Spell> spell, int spellLevel,
            SpellTarget target) {
        runEffects(level, caster, spell, spellLevel, target, Optional.empty());
    }

    /** {@link #runEffects(ServerLevel, LivingEntity, Holder, int, SpellTarget)} for a proc's spell, if it has one. */
    private static void runEffects(ServerLevel level, LivingEntity caster, Holder<Spell> spell, int spellLevel,
            SpellTarget target, Optional<Identifier> procOrigin) {
        EffectContext context = EffectContext.of(level, caster, spell, spellLevel, procOrigin);
        for (SpellEffect effect : spell.value().effects()) {
            effect.apply(context, target);
        }
    }

    private static double cost(LivingEntity caster, Spell spell, int level) {
        return caster instanceof Player ? spell.costAt(level) : 0;
    }

    private static ResourceKey<ResourceType> resource(Spell spell) {
        return spell.cost().orElseThrow().resource().unwrapKey().orElseThrow();
    }

    private static boolean canCast(LivingEntity caster) {
        return caster.isAlive() && !caster.isSpectator();
    }
}
