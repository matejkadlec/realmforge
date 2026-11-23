package io.github.matejkadlec.realmforge.spell;

import io.github.matejkadlec.realmforge.Realmforge;
import io.github.matejkadlec.realmforge.combat.CombatConfig;
import io.github.matejkadlec.realmforge.combat.DamagePipeline;
import io.github.matejkadlec.realmforge.effect.Effects;
import io.github.matejkadlec.realmforge.generated.GeneratedStats;
import io.github.matejkadlec.realmforge.spell.CombatState.Cast;
import io.github.matejkadlec.realmforge.spell.net.CastEndedPayload;
import io.github.matejkadlec.realmforge.spell.net.CastRefusedPayload;
import io.github.matejkadlec.realmforge.spell.net.CastStartedPayload;
import io.github.matejkadlec.realmforge.spell.net.CastTargetPayload;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import javax.annotation.Nullable;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
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
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
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
 *   count; a channel whose ticks it multiplies keeps its duration and runs round(ticks × (1 + reduction)) ticks in it
 *   instead, spread evenly (RLM-572: Blizzard's waves).</li>
 *   <li><b>Interrupts:</b> a spell's knockback (unless the spell may be cast while moving), death, a dimension change,
 *   logging out, the caster's removal and a cancel ({@link #cancel}) end it without more effects.</li>
 *   <li><b>Moving slows, it never interrupts</b> (RLM-261, ADR 0039): a cast of a spell that may not be cast while
 *   moving gives its caster {@link #CAST_SLOW}, half its movement speed, until the cast ends however it ends. A spell's {@code realmforge:knockback} still interrupts the cast ({@link #knockedBack}).</li>
 *   <li><b>No sprint</b> (RLM-371, ADR 0057): such a cast also stops its caster's sprint when it begins, and keeps it
 *   stopped every tick until it ends, as using an item does ({@link #blocksSprint}; the client keeps the sprint key
 *   from starting one, {@code client/spell/ClientCasts}).</li>
 *   <li><b>A kept target:</b> a spell that keeps sight of its target checks it every tick and ends the cast at once
 *   when blocks come between them; one that keeps its range for a share of the cast ends at once when the target
 *   leaves the range within that share, and past it the range no longer matters ({@link SpellCast#keepRange}).</li>
 *   <li><b>A cast time</b> paid its cost when it began (Matěj, 2026-10-24). It completes after its duration: the
 *   target is checked again ({@link SpellCaster#recheck}), and a failed check ends it like an interrupt, without the
 *   cooldown or the effects; the cost stays paid. Otherwise it starts its cooldown and runs its effects.</li>
 *   <li><b>Its sound:</b> a spell with a cast sound plays it from the caster when the cast begins.</li>
 *   <li><b>A channel</b> paid for its first second when it began, with its cost up front if it has one (RLM-572), and
 *   pays its cost again as each later second begins (Matěj, 2026-10-24: as in WoW), its seconds shortened by Cast Time
 *   Reduction as its interval is, so the number of payments stays (whole seconds for a channel that keeps its
 *   duration); a pool that falls short ends it ({@link CastEnd#NOT_ENOUGH_RESOURCE}). It runs its effects at the
 *   end of every interval, checking the target first; a failed check or an interrupt ends it, and the ticks already
 *   run stay applied.</li>
 *   <li><b>Clients</b> learn of a cast from {@link CastStartedPayload} (the caster and every tracking player, and a
 *   player who starts tracking the caster later, with the original start time) and of its end from
 *   {@link CastEndedPayload}. The end is also posted as a {@link CastEndedEvent}.</li>
 *   <li><b>A swing</b> (RLM-399, Matěj, 2026-10-24 and 2026-10-29): an instant spell with an animation starts at once
 *   (its cost, global cooldown and own cooldown applied) but plays out over its animation as a cast of its own, without
 *   a cast bar, a cast animation or the slow: its caster moves freely. Its effects run once, at its hit moment; a new
 *   spell cuts it, and damage lands only if the hit moment had passed. A stun or death before the hit moment gives
 *   half its cost and half its cooldown back; Attack Speed shortens neither.</li>
 *   <li><b>A cast nearly done finishes</b> (Matěj, 2026-10-29, RLM-517): a spell the spell bar asks for while the cast
 *   in progress has less than {@link CombatConfig#finishCastTicks} left (a quarter of a second) passes every check
 *   now but waits ({@link #queueAfter}): the cast finishes, and the spell is cast right after it, its checks and cost
 *   again then, even with Queue Spell Casts off. With that much left or more, it cancels the cast as before. A swing
 *   is always cut. A newer press replaces the waiting spell; a cast that ends otherwise than by a cancel, a death, a
 *   dimension change or a logout still lets it go, and a refusal then reaches the player as any refusal does.</li>
 *   <li><b>No melee while casting</b> (ADR 0020, architecture 9.2): a casting player's {@code AttackEntityEvent} is
 *   cancelled, and the cast sets Better Combat's tag {@value CombatState#BETTER_COMBAT_DISABLED_TAG}, which stops its
 *   swings, unless the player already had it. The {@code combat} attachment records that the cast set it, so the end
 *   of the cast removes only a tag it added, and a login removes one a crash left behind.</li>
 *   <li><b>An instant spell's animation</b> (RLM-641; ADR 0073 decision 8, which amends ADR 0020 decision 3, and
 *   ADR 0077): a player's instant spell that is no swing runs its effects at once and is no cast in progress, but its animation
 *   plays for {@link SpellCast#PUSH_TICKS} ({@link #push}): its clients learn of it from a {@link CastStartedPayload}
 *   marked as an instant, with no end sent, as nothing can interrupt it. For that long the player's melee is blocked
 *   as during a cast: the {@code combat} attachment keeps the window ({@link CombatState#isMeleeBlocked}, synced to
 *   the owner for the attack key), the {@code AttackEntityEvent} is cancelled, and Better Combat's tag is set, and
 *   removed when the window is over unless a cast runs by then.</li>
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
    /**
     * The spells whose casts ended while they waited for them (RLM-517), by caster, in the order the casts ended: cast at
     * the end of the server tick, after the ended casts' effects. Server thread only.
     */
    private static final Map<LivingEntity, Queued> STARTING = new LinkedHashMap<>();
    /**
     * The players whose instant spell's animation still blocks their melee (RLM-641), with the game time it ends.
     * Server thread only.
     */
    private static final Map<Player, Long> PUSHING = new HashMap<>();

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
        NeoForge.EVENT_BUS.addListener(ServerStoppedEvent.class, event -> {
            RUNNING.clear();
            STARTING.clear();
            PUSHING.clear();
        });
        NeoForge.EVENT_BUS.addListener(MobEffectEvent.Added.class, Casting::onEffectAdded);
    }

    /**
     * Begins {@code caster}'s cast of {@code spell} at {@code target}, at game time {@code now}, after {@link
     * SpellCaster}'s checks passed and the global cooldown and the cost (for a channel its first second's, and its
     * cooldown) applied. Its damage is multiplied by {@code damageScale} (extra rage spent, RLM-540).
     */
    static void begin(LivingEntity caster, Holder.Reference<Spell> spell, int level, SpellTarget target, long now,
            double damageScale) {
        SpellCast spellCast = spell.value().cast();
        double reduction = DamagePipeline.stat(caster, GeneratedStats.CAST_TIME_REDUCTION);
        boolean channel = spellCast.type() == SpellCast.Type.CHANNEL;
        // RLM-572: a channel whose ticks the reduction multiplies keeps its duration, and so its seconds.
        boolean addsTicks = channel && spellCast.castTimeReduction() == SpellCast.Haste.ADDS_TICKS;
        int duration = spellCast.durationFor(reduction);
        int ticks = channel ? spellCast.tickCountFor(reduction) : 0;
        int second = !channel ? 0 : addsTicks ? TICKS_PER_SECOND : SpellScaling.castTicks(TICKS_PER_SECOND, reduction);
        int payments = channel ? Math.ceilDiv(spellCast.durationTicks(), TICKS_PER_SECOND) : 0;
        Cast cast = new Cast(spell.key(), now, duration, channel);

        CombatState state = caster.getData(SpellAttachments.COMBAT);
        state.startCast(cast);
        if (caster instanceof Player player && player.addTag(CombatState.BETTER_COMBAT_DISABLED_TAG)) {
            state.setSetBetterCombatTag(true);
        }
        caster.syncData(SpellAttachments.COMBAT);
        AttributeInstance speed = caster.getAttribute(Attributes.MOVEMENT_SPEED);
        if (!spellCast.castableWhileMoving()) {
            caster.setSprinting(false);
            if (speed != null) {
                speed.addOrUpdateTransientModifier(CAST_SLOW);
            }
        }
        Running running = new Running(caster, spell, level, target, (ServerLevel) caster.level(), cast, ticks,
                second, payments, damageScale, 0);
        RUNNING.put(caster, running);
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(caster, new CastStartedPayload(caster.getId(), cast));
        showCircle(running);
        spellCast.sound().map(BuiltInRegistries.SOUND_EVENT::getValue).ifPresent(sound -> caster.level()
                .playSound(null, caster, sound, caster.getSoundSource(), 1.0F, 1.0F));
    }

    /**
     * Begins {@code caster}'s swing of {@code spell} (RLM-399) at game time {@code now}: an instant spell with an
     * animation, whose cost ({@code paid} in all), global cooldown and own cooldown already applied. Its effects run at
     * its hit moment, on {@code target} as it is then, multiplied by {@code damageScale}.
     */
    static void beginSwing(LivingEntity caster, Holder.Reference<Spell> spell, int level, SpellTarget target, long now,
            double damageScale, double paid) {
        Cast cast = new Cast(spell.key(), now, spell.value().cast().animationTicks(), false, true);
        CombatState state = caster.getData(SpellAttachments.COMBAT);
        state.startCast(cast);
        if (caster instanceof Player player && player.addTag(CombatState.BETTER_COMBAT_DISABLED_TAG)) {
            state.setSetBetterCombatTag(true);
        }
        caster.syncData(SpellAttachments.COMBAT);
        Running running = new Running(caster, spell, level, target, (ServerLevel) caster.level(), cast, 0, 0, 0,
                damageScale, paid);
        RUNNING.put(caster, running);
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(caster, new CastStartedPayload(caster.getId(), cast));
    }

    /**
     * {@code caster} cast the instant spell {@code spell} at game time {@code now} (RLM-641, ADR 0073 decision 8): a
     * player's arms play its animation for {@link SpellCast#PUSH_TICKS}, for themselves and every player tracking
     * them, and their melee is blocked for that long, all three parts of ADR 0020 decision 3 (the attachment's window
     * for the event and the owner's attack key, Better Combat's tag). It is no cast: nothing is kept that a new spell,
     * a stun or a cancel could end, and no end is sent. A mob has no such animation and no melee of this kind.
     * Either way the {@code combat} attachment is synced, the spell's cost and cooldowns with it.
     */
    static void push(LivingEntity caster, Holder.Reference<Spell> spell, long now) {
        if (caster instanceof Player player) {
            CombatState state = player.getData(SpellAttachments.COMBAT);
            state.blockMelee(now + SpellCast.PUSH_TICKS);
            if (player.addTag(CombatState.BETTER_COMBAT_DISABLED_TAG)) {
                state.setSetBetterCombatTag(true);
            }
            PUSHING.put(player, state.meleeBlockedUntil());
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, new CastStartedPayload(player.getId(),
                    Cast.instant(spell.key(), now, SpellCast.PUSH_TICKS)));
        }
        caster.syncData(SpellAttachments.COMBAT);
    }

    /**
     * Ends the melee block of {@code player}'s instant spell: the window closes, and Better Combat's tag goes unless
     * a cast that began meanwhile still needs it.
     */
    private static void endPush(Player player) {
        if (PUSHING.remove(player) == null) {
            return;
        }
        CombatState state = player.getData(SpellAttachments.COMBAT);
        state.clearMeleeBlock();
        if (state.currentCast().isEmpty()) {
            clearBetterCombatTag(player, state);
        }
        player.syncData(SpellAttachments.COMBAT);
    }

    /** Ends every instant spell's melee block whose time is over, or whose player is gone. */
    private static void endPushes() {
        for (Map.Entry<Player, Long> pushing : List.copyOf(PUSHING.entrySet())) {
            Player player = pushing.getKey();
            if (player.isRemoved() || player.level().getGameTime() >= pushing.getValue()) {
                endPush(player);
            }
        }
    }

    /**
     * Whether an instant spell's animation keeps {@code entity} from melee now (RLM-641). Reads the {@code combat}
     * attachment, so the entity's own client gets the same answer for its attack key.
     */
    public static boolean meleeBlocked(Entity entity) {
        return entity instanceof LivingEntity living && living.getExistingData(SpellAttachments.COMBAT)
                .filter(state -> state.isMeleeBlocked(living.level().getGameTime())).isPresent();
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

    /**
     * Whether {@code caster}'s cast in progress finishes before a new spell at game time {@code now} (RLM-517): it has
     * less than {@link CombatConfig#finishCastTicks} left, and it is a cast time or a channel, never a swing, which a
     * new spell always cuts.
     */
    static boolean finishesFirst(LivingEntity caster, long now) {
        Running running = RUNNING.get(caster);
        return running != null && !running.cast().swing()
                && running.cast().endGameTime() - now < CombatConfig.finishCastTicks();
    }

    /**
     * {@code spell} at {@code level} waits for {@code caster}'s cast in progress and is cast at {@code aim} right after
     * it ends (RLM-517), replacing a spell that waited before. Nothing happens without a cast in progress.
     */
    static void queueAfter(LivingEntity caster, Holder.Reference<Spell> spell, int level, SpellAim aim) {
        Running running = RUNNING.get(caster);
        if (running != null) {
            running.next = new Queued(spell, level, aim);
        }
    }

    /** The spell waiting for {@code caster}'s cast in progress to end (RLM-517), if any. */
    public static Optional<ResourceKey<Spell>> queued(LivingEntity caster) {
        return Optional.ofNullable(RUNNING.get(caster)).map(running -> running.next).map(next -> next.spell().key());
    }

    /** {@code caster}'s cast in progress, if any: what its client and the cast bar see. */
    public static Optional<Cast> current(LivingEntity caster) {
        return caster.getExistingData(SpellAttachments.COMBAT).flatMap(CombatState::currentCast);
    }

    /**
     * What {@code caster}'s cast in progress acts on, as its targeting resolved it when the cast began (an entity, or a
     * ground point that stays where it was); on the server only, and empty without a cast.
     */
    public static Optional<SpellTarget> currentTarget(LivingEntity caster) {
        return Optional.ofNullable(RUNNING.get(caster)).map(Running::target);
    }

    /** Whether {@code caster} is casting a cast-time or channelled spell. */
    public static boolean isCasting(Entity caster) {
        return caster instanceof LivingEntity living && current(living).isPresent();
    }

    /**
     * Whether {@code caster}'s cast in progress keeps it from sprinting: one whose spell may not be cast while moving
     * (RLM-371, ADR 0057). Reads the {@code combat} attachment, so the caster's own client gets the same answer.
     */
    public static boolean blocksSprint(LivingEntity caster) {
        return current(caster).filter(cast -> !cast.swing())
                .flatMap(cast -> Spells.get(caster.registryAccess(), cast.spell()))
                .filter(spell -> !spell.value().cast().castableWhileMoving()).isPresent();
    }

    /** Lands {@code caster}'s swing now: its effects if they have not run, then its end; whether a swing ran. */
    static boolean land(LivingEntity caster) {
        Running running = RUNNING.get(caster);
        if (running == null || !running.cast().swing()) {
            return false;
        }
        if (!running.hit) {
            running.hit = true;
            SpellCaster.runEffects(running.level(), caster, running.spell(), running.spellLevel(),
                    running.target().now(caster, running.spell().value().targeting()), Optional.empty(),
                    running.damageScale());
        }
        end(caster, CastEnd.COMPLETED);
        return true;
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
        for (Running running : List.copyOf(RUNNING.values())) {
            // An earlier cast's effects can end this one (a kill), so only casts still running go on.
            if (RUNNING.get(running.caster()) == running) {
                tick(running);
            }
        }
        endPushes();
        startQueued();
    }

    /**
     * Casts every spell that waited for a cast that ended (RLM-517), after that cast's effects: through every check
     * again, with its cost paid now. A player hears of a refusal as of any refusal from the bar.
     */
    private static void startQueued() {
        while (!STARTING.isEmpty()) {
            Map.Entry<LivingEntity, Queued> first = STARTING.entrySet().iterator().next();
            STARTING.remove(first.getKey());
            LivingEntity caster = first.getKey();
            Queued queued = first.getValue();
            if (caster.isRemoved()) {
                continue;
            }
            CastResult result = SpellCaster.castQueued(caster, queued.spell(), queued.level(), queued.aim());
            if (caster instanceof ServerPlayer player) {
                result.failure().ifPresent(failure -> PacketDistributor.sendToPlayer(player,
                        new CastRefusedPayload(queued.spell().key(), failure)));
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
        if (running.cast().swing()) {
            swing(running, now);
            return;
        }
        if (!spell.cast().castableWhileMoving() && caster.isSprinting()) {
            // A player's client asked to sprint (ServerboundPlayerCommandPacket) before it knew of the cast.
            caster.setSprinting(false);
        }
        Optional<CastEnd> lost = lostTarget(running, now);
        if (lost.isPresent()) {
            end(caster, lost.get());
            return;
        }
        showCircle(running);
        if (running.cast().channel()) {
            while (running.ticksRun < running.ticks() && now >= running.cast().startGameTime()
                    + SpellScaling.channelTickAt(running.ticksRun + 1, running.ticks(), running.cast().durationTicks())) {
                Optional<CastFailure> failed = SpellCaster.recheck(caster, spell, running.target());
                if (failed.isPresent()) {
                    end(caster, CastEnd.of(failed.get()));
                    return;
                }
                running.ticksRun++;
                SpellCaster.runEffects(running.level(), caster, running.spell(), running.spellLevel(),
                        running.target().now(caster, spell.targeting()), Optional.empty(), running.damageScale());
                if (RUNNING.get(caster) != running) {
                    return;
                }
            }
            if (running.ticksRun >= running.ticks()) {
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
        SpellCaster.startCooldown(caster, spell, running.spell().key(), running.spellLevel(),
                caster.getData(SpellAttachments.COMBAT), now);
        end(caster, CastEnd.COMPLETED);
        SpellCaster.runEffects(running.level(), caster, running.spell(), running.spellLevel(),
                running.target().now(caster, spell.targeting()), Optional.empty(), running.damageScale());
    }

    /** A swing's tick: its effects at its hit moment, once, then the rest of its animation until it completes. */
    private static void swing(Running running, long now) {
        LivingEntity caster = running.caster();
        Spell spell = running.spell().value();
        if (!running.hit && now >= running.cast().startGameTime() + spell.cast().hitTick()) {
            running.hit = true;
            SpellCaster.runEffects(running.level(), caster, running.spell(), running.spellLevel(),
                    running.target().now(caster, spell.targeting()), Optional.empty(), running.damageScale());
            if (RUNNING.get(caster) != running) {
                return;
            }
        }
        if (now >= running.cast().endGameTime()) {
            end(caster, CastEnd.COMPLETED);
        }
    }

    /**
     * A swing that a stun or death stopped before its hit moment gives half its cost and half its cooldown back (Matěj,
     * 2026-10-29: a light penalty, not a full one).
     */
    private static void refundSwing(Running running, CastEnd end, CombatState state) {
        if (!running.cast().swing() || running.hit || end != CastEnd.STUNNED && end != CastEnd.DIED) {
            return;
        }
        LivingEntity caster = running.caster();
        Spell spell = running.spell().value();
        SpellCaster.refund(caster, spell, running.paid() / 2, state);
        Long cooldownEnd = state.cooldowns().get(running.spell().key());
        if (cooldownEnd != null) {
            int cooldown = SpellScaling.cooldownTicks(spell.cooldownTicksAt(running.spellLevel()),
                    DamagePipeline.stat(caster, GeneratedStats.COOLDOWN_REDUCTION));
            state.setCooldownEnd(running.spell().key(),
                    Math.max(running.level().getGameTime(), cooldownEnd - cooldown / 2));
        }
    }

    /**
     * Why the cast loses the target it keeps, if it does: blocks between them for a spell that keeps sight, or the
     * target beyond the range within the share of the cast that keeps it, or a target that died or left the
     * dimension. A player's sticky target ({@link SpellCaster#stickyRange}) is lost at once when it dies, leaves the
     * dimension or goes more than twice the spell's range away. A spell that keeps nothing, a point or a direction,
     * and a cast on the caster itself lose nothing.
     */
    private static Optional<CastEnd> lostTarget(Running running, long now) {
        SpellCast spellCast = running.spell().value().cast();
        LivingEntity caster = running.caster();
        LivingEntity target = running.target().entityOrNull();
        OptionalDouble sticky = SpellCaster.stickyRange(caster, running.spell().value());
        if ((!spellCast.keepSight() && spellCast.keepRange().isEmpty() && sticky.isEmpty()) || target == null
                || target == caster) {
            return Optional.empty();
        }
        if (!target.isAlive() || target.isRemoved()) {
            return Optional.of(CastEnd.TARGET_LOST);
        }
        if (target.level() != caster.level()
                || sticky.isPresent() && Targets.distance(caster, target) > sticky.getAsDouble()) {
            return Optional.of(CastEnd.OUT_OF_RANGE);
        }
        if (spellCast.keepSight() && !caster.hasLineOfSight(target)) {
            return Optional.of(CastEnd.NO_LINE_OF_SIGHT);
        }
        Cast cast = running.cast();
        double share = cast.durationTicks() > 0 ? (double) (now - cast.startGameTime()) / cast.durationTicks() : 1;
        if (spellCast.keepRange().filter(kept -> share < kept).isPresent()
                && Targets.distance(caster, target) > running.spell().value().targeting().reach(caster)) {
            return Optional.of(CastEnd.OUT_OF_RANGE);
        }
        return Optional.empty();
    }

    /**
     * Tells a casting player's client where the target circle goes (RLM-345, Q4: only during a cast-time or channelled
     * spell), when that changed: under the cast's target, or, for a bolt that follows the crosshair, under the mob aim
     * assistance would send it to if it left now ({@link #circleTarget}). The client draws it until the cast ends.
     */
    private static void showCircle(Running running) {
        if (!(running.caster() instanceof ServerPlayer player)) {
            return;
        }
        Optional<LivingEntity> under = circleTarget(player, running);
        int id = under.map(Entity::getId).orElse(Running.NO_CIRCLE);
        if (id != running.circle) {
            running.circle = id;
            PacketDistributor.sendToPlayer(player, new CastTargetPayload(under.map(Entity::getId)));
        }
    }

    /**
     * What the target circle of {@code player}'s cast goes under: the entity the cast keeps, unless it is the player;
     * for a direction from their look whose spell launches a projectile, the mob that projectile would be assisted to
     * now ({@link AimAssist#enemy}); else nothing.
     */
    private static Optional<LivingEntity> circleTarget(ServerPlayer player, Running running) {
        SpellTarget target = running.target();
        if (target.entity().isPresent()) {
            return target.entity().filter(entity -> entity != player);
        }
        if (target.followsLook() && running.spell().value().effects().stream()
                .anyMatch(ProjectileEffect.class::isInstance)) {
            return AimAssist.enemy(player, AimAssist.REACH);
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
        refundSwing(running, end, state);
        state.clearCast();
        if (!state.isMeleeBlocked(caster.level().getGameTime())) {
            // RLM-641: an instant spell's animation may still block melee; its end removes the tag then.
            clearBetterCombatTag(caster, state);
        }
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
        if (end.completed()) {
            SpellCaster.wentOff(caster, running.spell());
        }
        NeoForge.EVENT_BUS.post(new CastEndedEvent(caster, running.spell(), end));
        if (running.next != null && end != CastEnd.CANCELLED && end != CastEnd.DIED
                && end != CastEnd.CHANGED_DIMENSION && end != CastEnd.LOGGED_OUT) {
            STARTING.put(caster, running.next);
        }
        return true;
    }

    /** Removes Better Combat's disable tag if a cast or an instant spell's animation added it; an operator's own stays. */
    private static void clearBetterCombatTag(LivingEntity caster, CombatState state) {
        if (state.setBetterCombatTag()) {
            caster.removeTag(CombatState.BETTER_COMBAT_DISABLED_TAG);
            state.setSetBetterCombatTag(false);
        }
    }

    /**
     * Vanilla melee and Better Combat's server-side hits both go through {@code Player#attack}: none during a cast,
     * nor during an instant spell's animation (RLM-641).
     */
    private static void onAttack(AttackEntityEvent event) {
        if (isCasting(event.getEntity()) || meleeBlocked(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    /** A stun (C4) interrupts the cast in progress, whatever its spell. */
    private static void onEffectAdded(MobEffectEvent.Added event) {
        if (!event.getEntity().level().isClientSide() && Effects.isStun(event.getEffectInstance().getEffect())) {
            end(event.getEntity(), CastEnd.STUNNED);
        }
    }

    /** Vanilla's client fires it too (entity event 3), so the server's casts are left to the server thread. */
    private static void onDeath(LivingDeathEvent event) {
        if (!event.getEntity().level().isClientSide()) {
            if (event.getEntity() instanceof Player player) {
                endPush(player);
            }
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
        endPush(event.getEntity());
        end(event.getEntity(), CastEnd.LOGGED_OUT);
    }

    private static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getTarget() instanceof LivingEntity caster && event.getEntity() instanceof ServerPlayer watcher) {
            startedPayload(caster).ifPresent(payload -> PacketDistributor.sendToPlayer(watcher, payload));
        }
    }

    /** What the server keeps of a cast in progress beyond the {@link Cast} its clients see. */
    private static final class Running {
        /** {@link #circle} when the client was told of no target circle. */
        private static final int NO_CIRCLE = -1;

        private final LivingEntity caster;
        private final Holder.Reference<Spell> spell;
        private final int spellLevel;
        private final SpellTarget target;
        private final ServerLevel level;
        private final Cast cast;
        private final int ticks;
        private final int second;
        private final int payments;
        private final double damageScale;
        private final double paid;
        /** How many of a channel's ticks have run. */
        private int ticksRun;
        /** Whether a swing's effects ran: its hit moment passed. */
        private boolean hit;
        /** How many of a channel's seconds it paid for: the first when it began. */
        private int paymentsMade = 1;
        /** The entity id a casting player's client was last told the target circle is under. */
        private int circle = NO_CIRCLE;
        /** The spell that waits for this cast to end (RLM-517), if any. */
        @Nullable
        private Queued next;

        private Running(LivingEntity caster, Holder.Reference<Spell> spell, int spellLevel, SpellTarget target,
                ServerLevel level, Cast cast, int ticks, int second, int payments, double damageScale,
                double paid) {
            this.caster = caster;
            this.spell = spell;
            this.spellLevel = spellLevel;
            this.target = target;
            this.level = level;
            this.cast = cast;
            this.ticks = ticks;
            this.second = second;
            this.payments = payments;
            this.damageScale = damageScale;
            this.paid = paid;
        }

        /** What its damage is multiplied by: extra rage spent (RLM-540), 1 otherwise. */
        double damageScale() {
            return damageScale;
        }

        /** What a swing cost in all, its extra included (RLM-399): half comes back if it is stopped early. */
        double paid() {
            return paid;
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

        /**
         * How often a channel runs its effects, spread evenly over its duration ({@link SpellCast#tickCountFor}: more
         * with Cast Time Reduction for a channel whose ticks it multiplies, RLM-572); 0 for a cast time.
         */
        int ticks() {
            return ticks;
        }

        /**
         * A channel's second in ticks, how often it pays: shortened by Cast Time Reduction as its interval is, a whole
         * second for a channel that keeps its duration (RLM-572); 0 for a cast time.
         */
        int second() {
            return second;
        }

        /** How many times a channel pays: once for every second its data lasts, begun or whole; 0 for a cast time. */
        int payments() {
            return payments;
        }
    }

    /** A spell that waits for a cast in progress to end (RLM-517): cast at {@code level} at {@code aim} then. */
    private record Queued(Holder.Reference<Spell> spell, int level, SpellAim aim) {
    }
}
