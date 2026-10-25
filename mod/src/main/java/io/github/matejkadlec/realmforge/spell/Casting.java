package io.github.matejkadlec.realmforge.spell;

import io.github.matejkadlec.realmforge.Realmforge;
import io.github.matejkadlec.realmforge.combat.DamagePipeline;
import io.github.matejkadlec.realmforge.generated.GeneratedStats;
import io.github.matejkadlec.realmforge.spell.CombatState.Cast;
import io.github.matejkadlec.realmforge.spell.net.CastEndedPayload;
import io.github.matejkadlec.realmforge.spell.net.CastStartedPayload;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Cast-time and channelled spells in progress, on the logical server (architecture 11.2 steps 3 to 6). {@link
 * SpellCaster} begins them; every server tick this system ends the ones something interrupted, runs a channel's ticks
 * and completes the casts whose time is over.
 *
 * <ul>
 *   <li><b>The current cast</b> lives in the caster's {@code combat} attachment (transient: never saved or copied), so
 *   the owner's client sees it and a second cast is refused; this system keeps what only the server needs (the target,
 *   the spell level, where the cast began, the ticks run).</li>
 *   <li><b>Cast Time Reduction</b> shortens a cast time, and a channel's interval, so a channel keeps its tick
 *   count.</li>
 *   <li><b>Interrupts:</b> a spell's knockback (unless the spell may be cast while moving), death, a dimension change,
 *   logging out, the caster's removal and a cancel ({@link #cancel}) end it without more effects.</li>
 *   <li><b>Moving slows, it never interrupts</b> (RLM-261, ADR 0039): a cast of a spell that may not be cast while
 *   moving gives its caster {@link #CAST_SLOW}, half its movement speed, until the cast ends however it ends. A spell's {@code realmforge:knockback} still interrupts the cast ({@link #knockedBack}).</li>
 *   <li><b>A kept target:</b> a spell that keeps sight of its target checks it every tick and ends the cast at once
 *   when blocks come between them; one that keeps its range for a share of the cast ends at once when the target
 *   leaves the range within that share, and past it the range no longer matters ({@link SpellCast#keepRange}).</li>
 *   <li><b>A cast time</b> paid its cost when it began (Matěj, 2026-10-24). It completes after its duration: the
 *   target is checked again ({@link SpellCaster#recheck}), and a failed check ends it like an interrupt, without the
 *   cooldown or the effects; the cost stays paid. Otherwise it starts its cooldown and runs its effects.</li>
 *   <li><b>Its sound:</b> a spell with a cast sound plays it from the caster when the cast begins.</li>
 *   <li><b>A channel</b> paid for its first second when it began, and pays its cost again as each later second begins
 *   (Matěj, 2026-10-24: as in WoW), its seconds shortened by Cast Time Reduction as its interval is, so the number of
 *   payments stays; a pool that falls short ends it ({@link CastEnd#NOT_ENOUGH_RESOURCE}). It runs its effects at the
 *   end of every interval, checking the target first; a failed check or an interrupt ends it, and the ticks already
 *   run stay applied.</li>
 *   <li><b>Clients</b> learn of a cast from {@link CastStartedPayload} (the caster and every tracking player, and a
 *   player who starts tracking the caster later, with the original start time) and of its end from
 *   {@link CastEndedPayload}. The end is also posted as a {@link CastEndedEvent}.</li>
 *   <li><b>No melee while casting</b> (ADR 0020, architecture 9.2): a casting player's {@code AttackEntityEvent} is
 *   cancelled, and the cast sets Better Combat's tag {@value CombatState#BETTER_COMBAT_DISABLED_TAG}, which stops its
 *   swings, unless the player already had it. The {@code combat} attachment records that the cast set it, so the end
 *   of the cast removes only a tag it added, and a login removes one a crash left behind.</li>
 * </ul>
 */
public final class Casting {
    /**
     * The movement speed a caster loses while it casts a spell that may not be cast while moving: exactly half (ADR 0039,
     * ADR 0047), which no level of vanilla's Slowness gives. A transient modifier: never saved, so a crash leaves none
     * behind.
     */
    public static final AttributeModifier CAST_SLOW = new AttributeModifier(Realmforge.id("cast_slow"), -0.5,
            AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);

    /** A second in ticks: how often a channel pays its cost. */
    private static final int TICKS_PER_SECOND = 20;

    /** Every cast in progress, by caster. Server thread only. */
    private static final Map<LivingEntity, Running> RUNNING = new HashMap<>();

    private Casting() {
    }

    static void register() {
        NeoForge.EVENT_BUS.addListener(ServerTickEvent.Post.class, Casting::onServerTick);
        NeoForge.EVENT_BUS.addListener(AttackEntityEvent.class, Casting::onAttack);
        NeoForge.EVENT_BUS.addListener(LivingDeathEvent.class, Casting::onDeath);
        NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerLoggedInEvent.class, Casting::onLoggedIn);
        NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerLoggedOutEvent.class, Casting::onLoggedOut);
        NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerChangedDimensionEvent.class,
                event -> end(event.getEntity(), CastEnd.CHANGED_DIMENSION));
        NeoForge.EVENT_BUS.addListener(PlayerEvent.StartTracking.class, Casting::onStartTracking);
        NeoForge.EVENT_BUS.addListener(ServerStoppedEvent.class, event -> RUNNING.clear());
    }

    /**
     * Begins {@code caster}'s cast of {@code spell} at {@code target}, at game time {@code now}, after {@link
     * SpellCaster}'s checks passed and the global cooldown and the cost (for a channel its first second's, and its
     * cooldown) applied.
     */
    static void begin(LivingEntity caster, Holder.Reference<Spell> spell, int level, SpellTarget target, long now) {
        SpellCast spellCast = spell.value().cast();
        double reduction = DamagePipeline.stat(caster, GeneratedStats.CAST_TIME_REDUCTION);
        boolean channel = spellCast.type() == SpellCast.Type.CHANNEL;
        int interval = channel ? SpellScaling.castTicks(spellCast.intervalTicks(), reduction) : 0;
        int duration = channel ? interval * spellCast.tickCount()
                : SpellScaling.castTicks(spellCast.durationTicks(), reduction);
        int second = channel ? SpellScaling.castTicks(TICKS_PER_SECOND, reduction) : 0;
        int payments = channel ? Math.ceilDiv(spellCast.durationTicks(), TICKS_PER_SECOND) : 0;
        Cast cast = new Cast(spell.key(), now, duration, channel);

        CombatState state = caster.getData(SpellAttachments.COMBAT);
        state.startCast(cast);
        if (caster instanceof Player player && player.addTag(CombatState.BETTER_COMBAT_DISABLED_TAG)) {
            state.setSetBetterCombatTag(true);
        }
        caster.syncData(SpellAttachments.COMBAT);
        AttributeInstance speed = caster.getAttribute(Attributes.MOVEMENT_SPEED);
        if (!spellCast.castableWhileMoving() && speed != null) {
            speed.addOrUpdateTransientModifier(CAST_SLOW);
        }
        RUNNING.put(caster, new Running(caster, spell, level, target, (ServerLevel) caster.level(), cast, interval,
                second, payments));
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(caster, new CastStartedPayload(caster.getId(), cast));
        spellCast.sound().map(BuiltInRegistries.SOUND_EVENT::getValue).ifPresent(sound -> caster.level()
                .playSound(null, caster, sound, caster.getSoundSource(), 1.0F, 1.0F));
    }

    /**
     * Ends {@code caster}'s cast in progress as {@link CastEnd#KNOCKED_BACK}, unless its spell may be cast while
     * moving: a spell's knockback pushed it ({@link KnockbackEffect}).
     */
    static void knockedBack(LivingEntity caster) {
        Running running = RUNNING.get(caster);
        if (running != null && !running.spell().value().cast().castableWhileMoving()) {
            end(caster, CastEnd.KNOCKED_BACK);
        }
    }

    /** {@code caster}'s cast in progress, if any: what its client and the cast bar see. */
    public static Optional<Cast> current(LivingEntity caster) {
        return caster.getExistingData(SpellAttachments.COMBAT).flatMap(CombatState::currentCast);
    }

    /** Whether {@code caster} is casting a cast-time or channelled spell. */
    public static boolean isCasting(Entity caster) {
        return caster instanceof LivingEntity living && current(living).isPresent();
    }

    /** Cancels {@code caster}'s cast in progress, if any; returns whether one ran. */
    public static boolean cancel(LivingEntity caster) {
        return end(caster, CastEnd.CANCELLED);
    }

    /**
     * What a player who starts tracking {@code caster} now is sent: the cast in progress, with its original start
     * time, so its cast bar and animation show from where they are (architecture 11.2 step 3).
     */
    public static Optional<CastStartedPayload> startedPayload(LivingEntity caster) {
        return current(caster).map(cast -> new CastStartedPayload(caster.getId(), cast));
    }

    private static void onServerTick(ServerTickEvent.Post event) {
        if (RUNNING.isEmpty()) {
            return;
        }
        for (Running running : List.copyOf(RUNNING.values())) {
            // An earlier cast's effects can end this one (a kill), so only casts still running go on.
            if (RUNNING.get(running.caster()) == running) {
                tick(running);
            }
        }
    }

    private static void tick(Running running) {
        LivingEntity caster = running.caster();
        Optional<CastEnd> interrupted = interruption(running);
        if (interrupted.isPresent()) {
            end(caster, interrupted.get());
            return;
        }
        long now = running.level().getGameTime();
        Spell spell = running.spell().value();
        Optional<CastEnd> lost = lostTarget(running, now);
        if (lost.isPresent()) {
            end(caster, lost.get());
            return;
        }
        if (running.cast().channel()) {
            while (running.ticksRun < spell.cast().tickCount()
                    && now >= running.cast().startGameTime() + (long) (running.ticksRun + 1) * running.interval()) {
                Optional<CastFailure> failed = SpellCaster.recheck(caster, spell, running.target());
                if (failed.isPresent()) {
                    end(caster, CastEnd.of(failed.get()));
                    return;
                }
                running.ticksRun++;
                SpellCaster.runEffects(running.level(), caster, running.spell(), running.spellLevel(),
                        running.target().now(caster, spell.targeting()));
                if (RUNNING.get(caster) != running) {
                    return;
                }
            }
            if (running.ticksRun >= spell.cast().tickCount()) {
                end(caster, CastEnd.COMPLETED);
                return;
            }
            paySeconds(running, now);
            return;
        }
        if (now < running.cast().endGameTime()) {
            return;
        }
        Optional<CastFailure> failed = SpellCaster.recheck(caster, spell, running.target());
        if (failed.isPresent()) {
            end(caster, CastEnd.of(failed.get()));
            return;
        }
        SpellCaster.startCooldown(caster, spell, running.spell().key(), caster.getData(SpellAttachments.COMBAT), now);
        end(caster, CastEnd.COMPLETED);
        SpellCaster.runEffects(running.level(), caster, running.spell(), running.spellLevel(),
                running.target().now(caster, spell.targeting()));
    }

    /**
     * Why the cast loses the target it keeps, if it does: blocks between them for a spell that keeps sight, or the
     * target beyond the range within the share of the cast that keeps it, or a target that died or left the
     * dimension. A spell that keeps neither, a point or a direction, and a cast on the caster itself lose nothing.
     */
    private static Optional<CastEnd> lostTarget(Running running, long now) {
        SpellCast spellCast = running.spell().value().cast();
        LivingEntity caster = running.caster();
        LivingEntity target = running.target().entityOrNull();
        if ((!spellCast.keepSight() && spellCast.keepRange().isEmpty()) || target == null || target == caster) {
            return Optional.empty();
        }
        if (!target.isAlive() || target.isRemoved()) {
            return Optional.of(CastEnd.TARGET_LOST);
        }
        if (target.level() != caster.level()) {
            return Optional.of(CastEnd.OUT_OF_RANGE);
        }
        if (spellCast.keepSight() && !caster.hasLineOfSight(target)) {
            return Optional.of(CastEnd.NO_LINE_OF_SIGHT);
        }
        Cast cast = running.cast();
        double share = cast.durationTicks() > 0 ? (double) (now - cast.startGameTime()) / cast.durationTicks() : 1;
        if (spellCast.keepRange().filter(kept -> share < kept).isPresent()
                && Targets.distance(caster, target) > running.spell().value().targeting().reach()) {
            return Optional.of(CastEnd.OUT_OF_RANGE);
        }
        return Optional.empty();
    }

    /**
     * Pays a channel's cost for every second that has begun since its last payment, the effects of the ticks due first,
     * as the seconds before paid for them. A pool short of the cost ends the channel.
     */
    private static void paySeconds(Running running, long now) {
        LivingEntity caster = running.caster();
        Spell spell = running.spell().value();
        CombatState state = caster.getData(SpellAttachments.COMBAT);
        boolean paid = false;
        while (running.paymentsMade < running.payments()
                && now >= running.cast().startGameTime() + (long) running.paymentsMade * running.second()) {
            if (!SpellCaster.canPay(caster, spell, running.spellLevel(), state)) {
                end(caster, CastEnd.NOT_ENOUGH_RESOURCE);
                return;
            }
            SpellCaster.payCost(caster, spell, running.spellLevel(), state);
            running.paymentsMade++;
            paid = true;
        }
        if (paid) {
            caster.syncData(SpellAttachments.COMBAT);
        }
    }

    /**
     * What ended the cast from the caster's side, if anything: death, removal, a dimension change. A player's
     * dimension change also ends it at once ({@code PlayerChangedDimensionEvent}), even when the player comes back
     * within the tick; a mob that changes dimension is removed and replaced. Moving never ends it ({@link #CAST_SLOW}).
     */
    private static Optional<CastEnd> interruption(Running running) {
        LivingEntity caster = running.caster();
        if (caster.isRemoved()) {
            Entity.RemovalReason reason = caster.getRemovalReason();
            return Optional.of(reason == Entity.RemovalReason.KILLED ? CastEnd.DIED
                    : reason == Entity.RemovalReason.CHANGED_DIMENSION ? CastEnd.CHANGED_DIMENSION
                    : CastEnd.CANCELLED);
        }
        if (!caster.isAlive()) {
            return Optional.of(CastEnd.DIED);
        }
        if (caster.level() != running.level()) {
            return Optional.of(CastEnd.CHANGED_DIMENSION);
        }
        return Optional.empty();
    }

    /**
     * Ends {@code caster}'s cast in progress as {@code end}: clears it, removes the Better Combat tag the cast set,
     * tells the clients and posts the {@link CastEndedEvent}. Returns whether a cast was running.
     */
    private static boolean end(LivingEntity caster, CastEnd end) {
        Running running = RUNNING.remove(caster);
        if (running == null) {
            return false;
        }
        CombatState state = caster.getData(SpellAttachments.COMBAT);
        state.clearCast();
        clearBetterCombatTag(caster, state);
        AttributeInstance speed = caster.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            speed.removeModifier(CAST_SLOW.id());
        }
        caster.syncData(SpellAttachments.COMBAT);
        CastEndedPayload payload = new CastEndedPayload(caster.getId(), running.spell().key(), end);
        if (end == CastEnd.LOGGED_OUT) {
            // The leaving player's connection is closing; only the players who saw the cast need its end.
            PacketDistributor.sendToPlayersTrackingEntity(caster, payload);
        } else {
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(caster, payload);
        }
        NeoForge.EVENT_BUS.post(new CastEndedEvent(caster, running.spell(), end));
        return true;
    }

    /** Removes Better Combat's disable tag if a cast added it; an operator's own tag stays. */
    private static void clearBetterCombatTag(LivingEntity caster, CombatState state) {
        if (state.setBetterCombatTag()) {
            caster.removeTag(CombatState.BETTER_COMBAT_DISABLED_TAG);
            state.setSetBetterCombatTag(false);
        }
    }

    /** Vanilla melee and Better Combat's server-side hits both go through {@code Player#attack}. */
    private static void onAttack(AttackEntityEvent event) {
        if (isCasting(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    /** Vanilla's client fires it too (entity event 3), so the server's casts are left to the server thread. */
    private static void onDeath(LivingDeathEvent event) {
        if (!event.getEntity().level().isClientSide()) {
            end(event.getEntity(), CastEnd.DIED);
        }
    }

    /** A crash leaves the cast's tag in the save, with the flag that the cast set it: drop both. */
    private static void onLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        Player player = event.getEntity();
        player.getExistingData(SpellAttachments.COMBAT).ifPresent(state -> {
            if (state.setBetterCombatTag()) {
                clearBetterCombatTag(player, state);
                player.syncData(SpellAttachments.COMBAT);
            }
        });
    }

    /** Fires before the player is saved, so a cast's tag never reaches the save on a clean logout. */
    private static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        end(event.getEntity(), CastEnd.LOGGED_OUT);
    }

    private static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getTarget() instanceof LivingEntity caster && event.getEntity() instanceof ServerPlayer watcher) {
            startedPayload(caster).ifPresent(payload -> PacketDistributor.sendToPlayer(watcher, payload));
        }
    }

    /** What the server keeps of a cast in progress beyond the {@link Cast} its clients see. */
    private static final class Running {
        private final LivingEntity caster;
        private final Holder.Reference<Spell> spell;
        private final int spellLevel;
        private final SpellTarget target;
        private final ServerLevel level;
        private final Cast cast;
        private final int interval;
        private final int second;
        private final int payments;
        /** How many of a channel's ticks have run. */
        private int ticksRun;
        /** How many of a channel's seconds it paid for: the first when it began. */
        private int paymentsMade = 1;

        private Running(LivingEntity caster, Holder.Reference<Spell> spell, int spellLevel, SpellTarget target,
                ServerLevel level, Cast cast, int interval, int second, int payments) {
            this.caster = caster;
            this.spell = spell;
            this.spellLevel = spellLevel;
            this.target = target;
            this.level = level;
            this.cast = cast;
            this.interval = interval;
            this.second = second;
            this.payments = payments;
        }

        LivingEntity caster() {
            return caster;
        }

        Holder.Reference<Spell> spell() {
            return spell;
        }

        int spellLevel() {
            return spellLevel;
        }

        SpellTarget target() {
            return target;
        }

        /** The level the cast began in. */
        ServerLevel level() {
            return level;
        }

        Cast cast() {
            return cast;
        }

        /** A channel's ticks between two runs of its effects; 0 for a cast time. */
        int interval() {
            return interval;
        }

        /** A channel's second after Cast Time Reduction, in ticks: how often it pays; 0 for a cast time. */
        int second() {
            return second;
        }

        /** How many times a channel pays: once for every second its data lasts, begun or whole; 0 for a cast time. */
        int payments() {
            return payments;
        }
    }
}
