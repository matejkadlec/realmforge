package io.github.matejkadlec.realmforge.spell;

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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
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
 *   <li><b>Interrupts:</b> moving more than {@value #MOVE_TOLERANCE} blocks from where the cast began by itself
 *   (unless the spell may be cast while moving), a spell's knockback, death, a dimension change, logging out, the
 *   caster's removal and a cancel ({@link #cancel}) end it without more effects.</li>
 *   <li><b>What counts as moving</b> (RLM-243): the caster's own movement, a player pressing a movement or the jump
 *   key (the client's last input), a mob walking or strafing (its movement input). Being pushed is not moving: a
 *   melee hit's knockback, crowding mobs, a water current move the cast's start point along instead. A spell's
 *   {@code realmforge:knockback} interrupts the cast explicitly ({@link #knockedBack}).</li>
 *   <li><b>A cast time</b> completes after its duration: the cost and the target are checked again ({@link
 *   SpellCaster#recheck}), and a failed check ends it like an interrupt, without the cost, the cooldown or the effects.
 *   Otherwise it pays, starts its cooldown and runs its effects.</li>
 *   <li><b>A channel</b> already paid when it began. It runs its effects at the end of every interval, checking the
 *   target first; a failed check or an interrupt ends it, and the ticks already run stay applied.</li>
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
    /** How far, in blocks, a caster may move by itself from where its cast began before the cast counts as moved. */
    public static final double MOVE_TOLERANCE = 0.1;

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
     * SpellCaster}'s checks passed and the global cooldown (and for a channel the cost and cooldown) applied.
     */
    static void begin(LivingEntity caster, Holder.Reference<Spell> spell, int level, SpellTarget target, long now) {
        SpellCast spellCast = spell.value().cast();
        double reduction = DamagePipeline.stat(caster, GeneratedStats.CAST_TIME_REDUCTION);
        boolean channel = spellCast.type() == SpellCast.Type.CHANNEL;
        int interval = channel ? SpellScaling.castTicks(spellCast.intervalTicks(), reduction) : 0;
        int duration = channel ? interval * spellCast.tickCount()
                : SpellScaling.castTicks(spellCast.durationTicks(), reduction);
        Cast cast = new Cast(spell.key(), now, duration, channel);

        CombatState state = caster.getData(SpellAttachments.COMBAT);
        state.startCast(cast);
        if (caster instanceof Player player && player.addTag(CombatState.BETTER_COMBAT_DISABLED_TAG)) {
            state.setSetBetterCombatTag(true);
        }
        caster.syncData(SpellAttachments.COMBAT);
        RUNNING.put(caster, new Running(caster, spell, level, target, (ServerLevel) caster.level(), caster.position(),
                cast, interval));
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(caster, new CastStartedPayload(caster.getId(), cast));
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
                        running.target().now(caster));
                if (RUNNING.get(caster) != running) {
                    return;
                }
            }
            if (running.ticksRun >= spell.cast().tickCount()) {
                end(caster, CastEnd.COMPLETED);
            }
            return;
        }
        if (now < running.cast().endGameTime()) {
            return;
        }
        CombatState state = caster.getData(SpellAttachments.COMBAT);
        if (!SpellCaster.canPay(caster, spell, running.spellLevel(), state)) {
            end(caster, CastEnd.NOT_ENOUGH_RESOURCE);
            return;
        }
        Optional<CastFailure> failed = SpellCaster.recheck(caster, spell, running.target());
        if (failed.isPresent()) {
            end(caster, CastEnd.of(failed.get()));
            return;
        }
        SpellCaster.pay(caster, spell, running.spell().key(), running.spellLevel(), state, now);
        end(caster, CastEnd.COMPLETED);
        SpellCaster.runEffects(running.level(), caster, running.spell(), running.spellLevel(),
                running.target().now(caster));
    }

    /**
     * What ended the cast from the caster's side, if anything: death, removal, a dimension change, moving by itself. A
     * player's dimension change also ends it at once ({@code PlayerChangedDimensionEvent}), even when the player comes
     * back within the tick; a mob that changes dimension is removed and replaced. A caster that was pushed beyond the
     * tolerance takes the cast's start point along.
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
        if (!running.spell().value().cast().castableWhileMoving()
                && caster.position().distanceToSqr(running.start()) > MOVE_TOLERANCE * MOVE_TOLERANCE) {
            if (movesByItself(caster)) {
                return Optional.of(CastEnd.MOVED);
            }
            running.start = caster.position();
        }
        return Optional.empty();
    }

    /**
     * Whether {@code caster} moves by its own will (RLM-243): a player presses a movement or the jump key (the last
     * input its client sent), a mob walks or strafes (the movement input its move control sets). Being pushed or
     * carried moves neither.
     */
    public static boolean movesByItself(LivingEntity caster) {
        if (caster instanceof ServerPlayer player) {
            Input input = player.getLastClientInput();
            return input.forward() || input.backward() || input.left() || input.right() || input.jump();
        }
        return caster.zza != 0 || caster.xxa != 0 || caster.isJumping();
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
        /** Where the cast began, moved along while the caster is pushed. */
        private Vec3 start;
        private final Cast cast;
        private final int interval;
        /** How many of a channel's ticks have run. */
        private int ticksRun;

        private Running(LivingEntity caster, Holder.Reference<Spell> spell, int spellLevel, SpellTarget target,
                ServerLevel level, Vec3 start, Cast cast, int interval) {
            this.caster = caster;
            this.spell = spell;
            this.spellLevel = spellLevel;
            this.target = target;
            this.level = level;
            this.start = start;
            this.cast = cast;
            this.interval = interval;
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

        /** Where the caster stood when the cast began, or where it was last pushed to. */
        Vec3 start() {
            return start;
        }

        Cast cast() {
            return cast;
        }

        /** A channel's ticks between two runs of its effects; 0 for a cast time. */
        int interval() {
            return interval;
        }
    }
}
