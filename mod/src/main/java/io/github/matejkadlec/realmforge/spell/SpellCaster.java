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
 * spell bar, C3b) and a mob's AI (C3a-4) both end here, and every check runs on the server. Instant casts only so far;
 * cast times and channels come with C3a-2.
 *
 * <p>A cast is refused, paying nothing and running nothing, when (in this order; research SP-3 names each reason):
 * <ol>
 *   <li>the caster is dead or a spectator;</li>
 *   <li>the spell does not exist;</li>
 *   <li>a player casts an {@code npc_only} spell, or one their spellbook does not know;</li>
 *   <li>the spell's own cooldown runs, or the global cooldown runs and the spell is on it;</li>
 *   <li>its targeting finds no valid target in range and in line of sight;</li>
 *   <li>a player has less of the spell's resource than it costs.</li>
 * </ol>
 * Otherwise the cost and the cooldowns apply first (Cooldown Reduction shortens the spell's own, never the global one),
 * then every effect runs on the target. Mobs pay no cost: NPC resources are C3a-4's.
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
        EffectContext context = new EffectContext(serverLevel, caster, spell, level, caster.getRandom());
        CombatState state = caster.getData(SpellAttachments.COMBAT);
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
        double cost = caster instanceof Player ? value.costAt(level) : 0;
        Optional<ResourceKey<ResourceType>> resource = value.cost().map(c -> c.resource().unwrapKey().orElseThrow());
        if (cost > 0 && state.resource(resource.orElseThrow()) + COST_TOLERANCE < cost) {
            return CastResult.refused(CastFailure.NOT_ENOUGH_RESOURCE);
        }

        if (cost > 0) {
            ResourceKey<ResourceType> type = resource.orElseThrow();
            state.setResource(type, (float) Math.max(0, state.resource(type) - cost));
        }
        int cooldown = SpellScaling.cooldownTicks(value.cooldownTicks(),
                DamagePipeline.stat(caster, GeneratedStats.COOLDOWN_REDUCTION));
        state.clearEndedCooldowns(now);
        if (cooldown > 0) {
            state.setCooldownEnd(spell.key(), now + cooldown);
        }
        if (value.globalCooldownTicks() > 0) {
            state.setGlobalCooldownEnd(now + value.globalCooldownTicks());
        }
        caster.syncData(SpellAttachments.COMBAT);
        for (SpellEffect effect : value.effects()) {
            effect.apply(context, hit);
        }
        return CastResult.cast(hit);
    }

    private static boolean canCast(LivingEntity caster) {
        return caster.isAlive() && !caster.isSpectator();
    }
}
