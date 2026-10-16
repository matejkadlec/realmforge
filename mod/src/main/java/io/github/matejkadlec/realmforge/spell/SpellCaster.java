package io.github.matejkadlec.realmforge.spell;

import com.mojang.datafixers.util.Either;
import io.github.matejkadlec.realmforge.combat.DamagePipeline;
import io.github.matejkadlec.realmforge.generated.GeneratedStats;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Casts spells, the same for players, mobs and bosses (architecture 11.2). Server-authoritative: a client's intent (the
 * spell bar, C3b) and a mob's AI (C3a-4) both end here, and every check runs on the server.
 *
 * <p>A cast is refused, paying nothing and running nothing, when (in this order; research SP-3 names each reason):
 * <ol>
 *   <li>the caster is dead or a spectator;</li>
 *   <li>the spell does not exist;</li>
 *   <li>a player casts an {@code npc_only} spell, or one their spellbook does not know;</li>
 *   <li>a cast-time or channelled spell of the caster is still in progress;</li>
 *   <li>the spell's own cooldown runs, or the global cooldown runs and the spell is on it;</li>
 *   <li>its targeting finds no valid target in range and in line of sight;</li>
 *   <li>a player has less of the spell's resource than it costs.</li>
 * </ol>
 * Otherwise an instant spell pays its cost, starts its cooldowns (Cooldown Reduction shortens the spell's own, never the
 * global one) and runs every effect on the target. A cast-time or channelled spell starts the global cooldown and
 * begins its cast ({@link Casting}): a cast time pays its cost and starts its own cooldown when it completes, a channel
 * when it begins. Mobs pay no cost: NPC resources are C3a-4's.
 */
public final class SpellCaster {
    /** Leaves room for float rounding, so a pool of exactly the cost pays it. */
    private static final double COST_TOLERANCE = 1e-4;

    private SpellCaster() {
    }

    /**
     * {@code caster} casts {@code spell} at {@code target} (null when nothing was aimed at). A player casts at the
     * level their spellbook knows (at most the spell's {@code max_level}); a mob or boss at level 1.
     */
    public static CastResult cast(LivingEntity caster, ResourceKey<Spell> spell, @Nullable LivingEntity target) {
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
        return cast(caster, holder.get(), level, target);
    }

    /**
     * {@code caster} casts {@code spell} at {@code level} (1 to its {@code max_level}) at {@code target}, without the
     * spellbook: mobs, bosses and, later, procs. A player still pays the cost.
     */
    public static CastResult cast(LivingEntity caster, Holder.Reference<Spell> spell, int level,
            @Nullable LivingEntity target) {
        if (!(caster.level() instanceof ServerLevel serverLevel)) {
            throw new IllegalStateException("Spells are cast on the logical server only");
        }
        if (!canCast(caster)) {
            return CastResult.refused(CastFailure.CANNOT_CAST);
        }
        Spell value = spell.value();
        CombatState state = caster.getData(SpellAttachments.COMBAT);
        if (state.currentCast().isPresent()) {
            return CastResult.refused(CastFailure.ALREADY_CASTING);
        }
        long now = serverLevel.getGameTime();
        if (state.isOnCooldown(spell.key(), now)) {
            return CastResult.refused(CastFailure.ON_COOLDOWN);
        }
        if (value.globalCooldownTicks() > 0 && state.isOnGlobalCooldown(now)) {
            return CastResult.refused(CastFailure.ON_GLOBAL_COOLDOWN);
        }
        Either<LivingEntity, CastFailure> resolved = value.targeting().resolve(caster, target);
        Optional<CastFailure> missed = resolved.right();
        if (missed.isPresent()) {
            return CastResult.refused(missed.get());
        }
        LivingEntity hit = resolved.left().orElseThrow();
        if (!canPay(caster, value, level, state)) {
            return CastResult.refused(CastFailure.NOT_ENOUGH_RESOURCE);
        }

        if (value.globalCooldownTicks() > 0) {
            state.setGlobalCooldownEnd(now + value.globalCooldownTicks());
        }
        if (value.cast().type() == SpellCast.Type.CAST_TIME) {
            Casting.begin(caster, spell, level, hit, now);
            return CastResult.begun(hit);
        }
        pay(caster, value, spell.key(), level, state, now);
        if (value.cast().type() == SpellCast.Type.CHANNEL) {
            Casting.begin(caster, spell, level, hit, now);
            return CastResult.begun(hit);
        }
        caster.syncData(SpellAttachments.COMBAT);
        runEffects(serverLevel, caster, spell, level, hit);
        return CastResult.cast(hit);
    }

    /** Whether {@code caster} has enough of the spell's resource for a cast at {@code level}; mobs always do. */
    static boolean canPay(LivingEntity caster, Spell spell, int level, CombatState state) {
        double cost = cost(caster, spell, level);
        return cost <= 0 || state.resource(resource(spell)) + COST_TOLERANCE >= cost;
    }

    /** Pays the cost (players only) and starts the spell's own cooldown, after Cooldown Reduction. */
    static void pay(LivingEntity caster, Spell spell, ResourceKey<Spell> key, int level, CombatState state, long now) {
        double cost = cost(caster, spell, level);
        if (cost > 0) {
            ResourceKey<ResourceType> type = resource(spell);
            state.setResource(type, (float) Math.max(0, state.resource(type) - cost));
        }
        int cooldown = SpellScaling.cooldownTicks(spell.cooldownTicks(),
                DamagePipeline.stat(caster, GeneratedStats.COOLDOWN_REDUCTION));
        state.clearEndedCooldowns(now);
        if (cooldown > 0) {
            state.setCooldownEnd(key, now + cooldown);
        }
    }

    /**
     * Why {@code target} may no longer take the effects of {@code caster}'s spell, checked again before a cast time's
     * effects and before every tick of a channel (architecture 11.2 steps 4 and 5): it died or is gone, or the spell's
     * targeting refuses it now (out of range, behind blocks, no longer an enemy or ally). A spell that locks on only
     * needs its target alive and in the caster's dimension.
     */
    static Optional<CastFailure> recheck(LivingEntity caster, Spell spell, LivingEntity target) {
        if (target == caster) {
            return Optional.empty();
        }
        if (!target.isAlive() || target.isRemoved()) {
            return Optional.of(CastFailure.NO_TARGET);
        }
        if (spell.cast().lockOn()) {
            return target.level() == caster.level() ? Optional.empty() : Optional.of(CastFailure.OUT_OF_RANGE);
        }
        return spell.targeting().resolve(caster, target).right();
    }

    /** Runs every effect of the spell on {@code target}, with the caster's stats as they are now. */
    static void runEffects(ServerLevel level, LivingEntity caster, Holder<Spell> spell, int spellLevel,
            LivingEntity target) {
        EffectContext context = new EffectContext(level, caster, spell, spellLevel, caster.getRandom());
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
