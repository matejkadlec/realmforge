package io.github.matejkadlec.realmforge.spell;

import com.mojang.datafixers.util.Either;
import io.github.matejkadlec.realmforge.Realmforge;
import io.github.matejkadlec.realmforge.combat.DamagePipeline;
import io.github.matejkadlec.realmforge.effect.Effects;
import io.github.matejkadlec.realmforge.generated.GeneratedStats;
import io.github.matejkadlec.realmforge.progression.ProgressionTriggers;
import java.util.Optional;
import java.util.OptionalDouble;
import javax.annotation.Nullable;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

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
 *   <li>the caster is stunned, and the spell is not one cast while stunned (C4);</li>
 *   <li>the spell's own cooldown runs, or the global cooldown runs and the spell is on it;</li>
 *   <li>a player lacks what the spell needs in hand: a melee weapon in the main hand, a shield in either hand
 *   ({@link SpellRequirement}, RLM-499);</li>
 *   <li>a player's spell usable only in its window has its window closed ({@link Windows}, RLM-540);</li>
 *   <li>its targeting finds no valid target (an entity or a point) in range and in line of sight;</li>
 *   <li>a spell castable only on a target below a share of its health has a target above it (RLM-540: Execute);</li>
 *   <li>a player has less of the spell's resource than it costs.</li>
 * </ol>
 * Otherwise the spell starts the global cooldown, which every spell is on (Matěj, 2026-10-24), and pays its cost at
 * once, whatever its cast (Matěj, 2026-10-24: a cast time pays when it begins). An instant spell then starts its own
 * cooldown (Cooldown Reduction shortens it, never the global one) and runs every effect on the target ({@link
 * SpellTarget}). A cast-time or channelled spell begins its cast ({@link Casting}): a cast time starts its own cooldown
 * when it completes, a channel when it begins, and a channel pays its cost again every second it lasts. Mobs and bosses
 * pay no cost: their cooldowns pace their spells (architecture 11.2 step 6). A player's wand in the main hand loses a
 * point of durability with the cost, once per cast, as a sword does per hit (RLM-172). A player's cast that may spend
 * extra resource does so after the cost, up to its maximum, and its damage grows with it (RLM-540: Execute); a cast
 * closes its spell's window. An instant spell with an animation (RLM-399, a Warrior's swing) starts its cooldown at once
 * but plays out over its animation ({@link Casting}), its effects at its hit moment.
 *
 * <p>The spell bar may replace the cast in progress (C3b, RLM-25; Matěj, 2026-10-19: with spell queueing off, a new
 * spell cancels the cast and casts at once): with {@code replaceCast}, a cast of another spell skips the
 * already-casting check, and only once every other check passed is the cast in progress cancelled, so a refused spell
 * never cancels it. Two spells never cast at once.
 *
 * <p>A proc casts its spell through {@link #castProc}: at once and for free, whatever the caster is doing.
 */
public final class SpellCaster {
    /**
     * The item tag of every wand, which the pipeline writes from {@code content/items/} (RLM-172): casting a spell wears
     * the one in the caster's main hand.
     */
    public static final TagKey<Item> WANDS = TagKey.create(Registries.ITEM, Realmforge.id("wands"));
    /** Leaves room for float rounding, so a pool of exactly the cost pays it. */
    private static final double COST_TOLERANCE = 1e-4;
    /** How many times the spell's range a player's sticky target may go away before the cast loses it (RLM-345). */
    static final double STICKY_RANGE_FACTOR = 2;

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
        if (holder.get().value().isPassive()) {
            return CastResult.refused(CastFailure.PASSIVE);
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
        if (!value.castableWhileStunned() && Effects.isStunned(caster)) {
            return CastResult.refused(CastFailure.STUNNED);
        }
        long now = serverLevel.getGameTime();
        if (state.isOnCooldown(spell.key(), now)) {
            return CastResult.refused(CastFailure.ON_COOLDOWN);
        }
        if (state.isOnGlobalCooldown(now)) {
            return CastResult.refused(CastFailure.ON_GLOBAL_COOLDOWN);
        }
        Optional<CastFailure> unmet = unmetRequirement(caster, value);
        if (unmet.isPresent()) {
            return CastResult.refused(unmet.get());
        }
        if (caster instanceof Player && value.conditions().window().isPresent()
                && !state.isWindowOpen(spell.key(), now)) {
            return CastResult.refused(CastFailure.NO_WINDOW);
        }
        Either<SpellTarget, CastFailure> resolved = value.targeting().resolve(caster, aim);
        Optional<CastFailure> missed = resolved.right();
        if (missed.isPresent()) {
            return CastResult.refused(missed.get());
        }
        SpellTarget hit = resolved.left().orElseThrow();
        Optional<CastFailure> health = value.conditions().targetFailure(hit.entityOrNull());
        if (health.isPresent()) {
            return CastResult.refused(health.get());
        }
        if (!canPay(caster, value, level, state)) {
            return CastResult.refused(CastFailure.NOT_ENOUGH_RESOURCE);
        }
        if (current.isPresent()) {
            Casting.cancel(caster);
        }

        LivingEntity aimedAt = hit.entityOrNull();
        if (caster instanceof Player && aimedAt != null && aimedAt != caster && !Targets.allied(caster, aimedAt)) {
            state.setCurrentTarget(aimedAt.getUUID(), now);
        }
        state.setGlobalCooldownEnd(now + value.globalCooldownTicks());
        payCost(caster, value, level, state);
        double extra = payExtra(caster, value, state);
        double damageScale = value.cost().flatMap(SpellCost::extra).map(more -> more.damageFactor(extra)).orElse(1.0);
        wearWand(caster);
        if (caster instanceof Player) {
            state.closeWindow(spell.key());
        }
        if (value.cast().type() == SpellCast.Type.CAST_TIME) {
            Casting.begin(caster, spell, level, hit, now, damageScale);
            return CastResult.begun(hit);
        }
        startCooldown(caster, value, spell.key(), state, now);
        if (value.cast().type() == SpellCast.Type.CHANNEL) {
            Casting.begin(caster, spell, level, hit, now, damageScale);
            return CastResult.begun(hit);
        }
        if (value.cast().swing()) {
            Casting.beginSwing(caster, spell, level, hit, now, damageScale, cost(caster, value, level) + extra);
            return CastResult.begun(hit);
        }
        caster.syncData(SpellAttachments.COMBAT);
        runEffects(serverLevel, caster, spell, level, hit, Optional.empty(), damageScale);
        wentOff(caster, spell);
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
        runEffects(serverLevel, caster, spell, level, hit, Optional.of(origin), 1);
        return CastResult.cast(hit);
    }

    /**
     * {@code caster}'s cast of {@code spell} went off: an instant spell was cast, or a cast time or channel completed
     * (never an interrupted one, nor a proc's spell). A player's advancements hear of it through
     * {@code realmforge:spell_cast}, which quests read (architecture 16).
     */
    static void wentOff(LivingEntity caster, Holder.Reference<Spell> spell) {
        if (caster instanceof ServerPlayer player) {
            ProgressionTriggers.SPELL_CAST.get().trigger(player, spell.key().identifier());
        }
    }

    /**
     * Why a player cannot cast {@code spell} with what they hold, if they cannot: the first of its requirements they do
     * not meet (RLM-499). Mobs and bosses cast without them.
     */
    static Optional<CastFailure> unmetRequirement(LivingEntity caster, Spell spell) {
        if (!(caster instanceof Player)) {
            return Optional.empty();
        }
        return spell.requires().stream().filter(requirement -> !requirement.metBy(caster))
                .map(SpellRequirement::failure).findFirst();
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

    /**
     * Spends what a player's cast may take beyond its cost (RLM-540: Execute's up to 30 more rage), as much as they have
     * up to its maximum; returns how much it spent. Mobs and spells without extra spend nothing.
     */
    static double payExtra(LivingEntity caster, Spell spell, CombatState state) {
        Optional<SpellCost.Extra> extra = spell.cost().flatMap(SpellCost::extra);
        if (!(caster instanceof Player) || extra.isEmpty()) {
            return 0;
        }
        ResourceKey<ResourceType> type = resource(spell);
        double spent = extra.get().spentFrom(state.resource(type));
        state.setResource(type, (float) Math.max(0, state.resource(type) - spent));
        return spent;
    }

    /** Gives back {@code amount} of the spell's resource: half of an interrupted swing's (RLM-399). Players only. */
    static void refund(LivingEntity caster, Spell spell, double amount, CombatState state) {
        if (caster instanceof Player && amount > 0 && spell.cost().isPresent()) {
            ResourceKey<ResourceType> type = resource(spell);
            state.setResource(type, (float) (state.resource(type) + amount));
        }
    }

    /**
     * A player's wand in the main hand loses a point of durability, as a sword does per hit (RLM-172): once per cast,
     * when it begins, whatever its cast type. A creative player's wand never wears.
     */
    static void wearWand(LivingEntity caster) {
        if (caster instanceof Player) {
            ItemStack wand = caster.getMainHandItem();
            if (wand.is(WANDS)) {
                wand.hurtAndBreak(1, caster, EquipmentSlot.MAINHAND);
            }
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
     * locks on only needs its target alive and in the caster's dimension; one that keeps its range for a share of the
     * cast ({@link SpellCast#keepRange}) no longer needs it in range, only in sight. A player's sticky target ({@link
     * #stickyRange}) may be up to twice the spell's range away. A ground point or a direction stays as it was resolved
     * when the cast began.
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
        OptionalDouble sticky = stickyRange(caster, spell);
        boolean inStickyRange = target.level() == caster.level()
                && sticky.isPresent() && Targets.distance(caster, target) <= sticky.getAsDouble();
        Optional<CastFailure> failure = spell.targeting().resolve(caster, SpellAim.at(target)).right();
        if (failure.filter(reason -> reason == CastFailure.OUT_OF_RANGE).isPresent()
                && (spell.cast().keepRange().isPresent() && target.level() == caster.level() || inStickyRange)) {
            return caster.hasLineOfSight(target) ? Optional.empty() : Optional.of(CastFailure.NO_LINE_OF_SIGHT);
        }
        return failure;
    }

    /**
     * How far a player's targeted cast keeps its target (aim assistance's sticky target, RLM-345): a cast-time or
     * channelled spell on an enemy or an ally keeps the target it picked until the cast ends, even beyond the spell's
     * range, and loses it only beyond twice the range ({@link Casting} checks it every tick). Empty for a spell that
     * locks on, which keeps its target wherever it goes in the caster's dimension, and for a mob's or a boss's cast,
     * whose kept targets their spell's data sets ({@link SpellCast#keepRange}).
     */
    static OptionalDouble stickyRange(LivingEntity caster, Spell spell) {
        return caster instanceof Player && !spell.cast().lockOn()
                && (spell.targeting() instanceof EnemyTargeting || spell.targeting() instanceof AllyTargeting)
                ? OptionalDouble.of(STICKY_RANGE_FACTOR * spell.targeting().reach(caster)) : OptionalDouble.empty();
    }

    /**
     * Runs every effect of the spell on {@code target}, with the caster's stats as they are now: for a proc's spell
     * with its origin, if it has one, and its damage multiplied by {@code damageScale} (extra rage spent, RLM-540).
     */
    static void runEffects(ServerLevel level, LivingEntity caster, Holder<Spell> spell, int spellLevel,
            SpellTarget target, Optional<Identifier> procOrigin, double damageScale) {
        EffectContext context = EffectContext.of(level, caster, spell, spellLevel, procOrigin).scaled(damageScale);
        for (SpellEffect effect : spell.value().effects()) {
            effect.apply(context, target);
        }
    }

    static double cost(LivingEntity caster, Spell spell, int level) {
        return caster instanceof Player ? spell.costAt(level) : 0;
    }

    private static ResourceKey<ResourceType> resource(Spell spell) {
        return spell.cost().orElseThrow().resource().unwrapKey().orElseThrow();
    }

    private static boolean canCast(LivingEntity caster) {
        return caster.isAlive() && !caster.isSpectator();
    }
}
