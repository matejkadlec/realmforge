package io.github.matejkadlec.realmforge.spell;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.matejkadlec.realmforge.core.Codecs;
import io.netty.buffer.ByteBuf;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;

/**
 * The value of the {@code realmforge:combat} attachment on players and on mobs that cast (architecture 7 and 11): the
 * resource pools (mana, rage), the cooldowns, the current cast, the open windows of spells usable only for a while
 * (RLM-540), and whether a cast set Better Combat's disable tag.
 *
 * <p>Unlike the other attachment values it is mutable, because it changes too often to replace and sync on every
 * change: the owner mutates it in place and calls {@code syncData} at most a few times per second or on important
 * changes (a cast or cooldown starting). Cooldowns are the server game time when they end, so they survive relogging
 * without per-tick updates. The current cast and the open windows are transient: {@link #CODEC} leaves them out, so
 * neither relogging nor the copy on death (which round-trips the saved codec) brings them back; only the client sync
 * carries them.
 *
 * <p>Better Combat's tag {@value #BETTER_COMBAT_DISABLED_TAG} is saved with the player, so the flag that a cast set it
 * is saved too: an operator's own tag survives a cast, and a login removes a tag a crash left behind (ADR 0020,
 * architecture 9.2; {@link Casting}).
 *
 * <p>Each resource type has its own pool, absent until {@link Resources} first fills it, so {@code spell} needs no
 * knowledge of the holder's class: a pool's maximum comes from its type (mana: Max Mana).
 */
public final class CombatState {
    /** Better Combat's entity tag that stops its swings for one player ({@code CombatFlags.DISABLED_TAG}). */
    public static final String BETTER_COMBAT_DISABLED_TAG = "bettercombat_disabled";

    /** The saved shape: resource pools, cooldowns and the Better Combat flag, never the current cast. */
    public static final Codec<CombatState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.unboundedMap(ResourceKey.codec(SpellRegistries.RESOURCE_TYPE), Codecs.NON_NEGATIVE_FLOAT)
                    .optionalFieldOf("resources", Map.of()).forGetter(CombatState::resources),
            Codec.unboundedMap(ResourceKey.codec(SpellRegistries.SPELL), Codecs.NON_NEGATIVE_LONG)
                    .optionalFieldOf("cooldowns", Map.of()).forGetter(CombatState::cooldowns),
            Codecs.NON_NEGATIVE_LONG.optionalFieldOf("global_cooldown_end", 0L)
                    .forGetter(CombatState::globalCooldownEnd),
            Codec.BOOL.optionalFieldOf("set_better_combat_tag", false).forGetter(CombatState::setBetterCombatTag)
    ).apply(instance, (resources, cooldowns, globalCooldownEnd, setBetterCombatTag) ->
            new CombatState(resources, cooldowns, globalCooldownEnd, null, setBetterCombatTag)));

    /** The synced shape: everything, the current cast included, for the owner's own cast bar and cooldowns. */
    public static final StreamCodec<ByteBuf, CombatState> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.<ByteBuf, ResourceKey<ResourceType>, Float, Map<ResourceKey<ResourceType>, Float>>map(
                    HashMap::new, ResourceKey.streamCodec(SpellRegistries.RESOURCE_TYPE), ByteBufCodecs.FLOAT),
            CombatState::resources,
            ByteBufCodecs.<ByteBuf, ResourceKey<Spell>, Long, Map<ResourceKey<Spell>, Long>>map(HashMap::new,
                    ResourceKey.streamCodec(SpellRegistries.SPELL), ByteBufCodecs.VAR_LONG), CombatState::cooldowns,
            ByteBufCodecs.VAR_LONG, CombatState::globalCooldownEnd,
            ByteBufCodecs.optional(Cast.STREAM_CODEC), CombatState::currentCast,
            ByteBufCodecs.BOOL, CombatState::setBetterCombatTag,
            ByteBufCodecs.<ByteBuf, ResourceKey<Spell>, Long, Map<ResourceKey<Spell>, Long>>map(HashMap::new,
                    ResourceKey.streamCodec(SpellRegistries.SPELL), ByteBufCodecs.VAR_LONG), CombatState::windows,
            (resources, cooldowns, globalCooldownEnd, cast, setBetterCombatTag, windows) -> {
                CombatState state = new CombatState(resources, cooldowns, globalCooldownEnd, cast.orElse(null),
                        setBetterCombatTag);
                windows.forEach(state::openWindow);
                return state;
            });

    private final Map<ResourceKey<ResourceType>, Float> resources;
    private final Map<ResourceKey<Spell>, Long> cooldowns;
    /** Spell to the game time its window closes (RLM-540); transient. */
    private final Map<ResourceKey<Spell>, Long> windows = new HashMap<>();
    private long globalCooldownEnd;
    @Nullable
    private Cast currentCast;
    private boolean setBetterCombatTag;
    /** The server's bookkeeping for aim assistance (rule 4): neither saved, synced nor compared. */
    @Nullable
    private UUID currentTarget;
    private long currentTargetTime;

    /** A fresh state: no resource pools, no cooldowns, not casting. Each holder gets its own instance. */
    public CombatState() {
        this(Map.of(), Map.of(), 0L, null, false);
    }

    private CombatState(Map<ResourceKey<ResourceType>, Float> resources, Map<ResourceKey<Spell>, Long> cooldowns,
            long globalCooldownEnd, @Nullable Cast currentCast, boolean setBetterCombatTag) {
        this.resources = new HashMap<>();
        resources.forEach(this::setResource);
        this.cooldowns = new HashMap<>();
        cooldowns.forEach(this::setCooldownEnd);
        setGlobalCooldownEnd(globalCooldownEnd);
        this.currentCast = currentCast;
        this.setBetterCombatTag = setBetterCombatTag;
    }

    /** Resource type to the amount in its pool; read-only. A type without a pool is absent. */
    public Map<ResourceKey<ResourceType>, Float> resources() {
        return Map.copyOf(resources);
    }

    /** Whether the holder has a pool of the resource type yet. */
    public boolean hasResource(ResourceKey<ResourceType> type) {
        return resources.containsKey(type);
    }

    /** The amount in the resource type's pool, 0 without a pool. */
    public float resource(ResourceKey<ResourceType> type) {
        return resources.getOrDefault(type, 0.0F);
    }

    /** Sets the amount in the resource type's pool, creating it; {@link Resources} keeps it within its maximum. */
    public void setResource(ResourceKey<ResourceType> type, float amount) {
        Objects.requireNonNull(type, "type");
        if (!(amount >= 0.0F) || !Float.isFinite(amount)) {
            throw new IllegalArgumentException("A resource is a finite amount of 0 or more: " + amount);
        }
        resources.put(type, amount);
    }

    /** Spell to the game time its cooldown ends; read-only. Ended cooldowns stay until {@link #clearEndedCooldowns}. */
    public Map<ResourceKey<Spell>, Long> cooldowns() {
        return Map.copyOf(cooldowns);
    }

    /** Whether the spell's own cooldown still runs at game time {@code now}. */
    public boolean isOnCooldown(ResourceKey<Spell> spell, long now) {
        Long end = cooldowns.get(spell);
        return end != null && now < end;
    }

    /** Forgets every cooldown, the global one included: a reset for QA and tests. */
    public void clearCooldowns() {
        cooldowns.clear();
        globalCooldownEnd = 0L;
    }

    public void setCooldownEnd(ResourceKey<Spell> spell, long endGameTime) {
        Objects.requireNonNull(spell, "spell");
        if (endGameTime < 0) {
            throw new IllegalArgumentException("A cooldown ends at a game time of 0 or more: " + endGameTime);
        }
        cooldowns.put(spell, endGameTime);
    }

    /** Forgets every cooldown that has ended by game time {@code now}, so the saved map stays small. */
    public void clearEndedCooldowns(long now) {
        cooldowns.values().removeIf(end -> end <= now);
    }

    /** Spell to the game time its window closes (RLM-540); read-only. Closed windows stay until {@link #closeWindow}. */
    public Map<ResourceKey<Spell>, Long> windows() {
        return Map.copyOf(windows);
    }

    /** Opens {@code spell}'s window until game time {@code endGameTime}, or keeps it open as long as it was. */
    public void openWindow(ResourceKey<Spell> spell, long endGameTime) {
        Objects.requireNonNull(spell, "spell");
        windows.merge(spell, endGameTime, Math::max);
    }

    /** Whether {@code spell}'s window is open at game time {@code now}. */
    public boolean isWindowOpen(ResourceKey<Spell> spell, long now) {
        Long end = windows.get(spell);
        return end != null && now < end;
    }

    /** Closes {@code spell}'s window: a cast used it. */
    public void closeWindow(ResourceKey<Spell> spell) {
        windows.remove(spell);
    }

    /** The game time the global cooldown ends; 0 when it never ran. */
    public long globalCooldownEnd() {
        return globalCooldownEnd;
    }

    /** Whether the global cooldown still runs at game time {@code now}. */
    public boolean isOnGlobalCooldown(long now) {
        return now < globalCooldownEnd;
    }

    public void setGlobalCooldownEnd(long endGameTime) {
        if (endGameTime < 0) {
            throw new IllegalArgumentException("A cooldown ends at a game time of 0 or more: " + endGameTime);
        }
        this.globalCooldownEnd = endGameTime;
    }

    public Optional<Cast> currentCast() {
        return Optional.ofNullable(currentCast);
    }

    public void startCast(Cast cast) {
        this.currentCast = Objects.requireNonNull(cast, "cast");
    }

    public void clearCast() {
        this.currentCast = null;
    }

    /** Whether a cast, not an operator, added {@value #BETTER_COMBAT_DISABLED_TAG} to the holder. */
    public boolean setBetterCombatTag() {
        return setBetterCombatTag;
    }

    public void setSetBetterCombatTag(boolean setBetterCombatTag) {
        this.setBetterCombatTag = setBetterCombatTag;
    }

    /**
     * Makes the entity with {@code uuid} the holder's current target as of game time {@code now}: the one a targeted
     * spell of theirs aimed at, or the last one they hit (aim assistance, rule 4, {@link AimAssist}). Server only, and
     * never saved or synced: a login starts without one.
     */
    public void setCurrentTarget(UUID uuid, long now) {
        this.currentTarget = Objects.requireNonNull(uuid, "uuid");
        this.currentTargetTime = now;
    }

    /** Whether the entity with {@code uuid} became the current target no more than {@code window} ticks before now. */
    public boolean isCurrentTarget(UUID uuid, long now, int window) {
        return uuid.equals(currentTarget) && now - currentTargetTime <= window;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof CombatState state
                && resources.equals(state.resources)
                && cooldowns.equals(state.cooldowns)
                && globalCooldownEnd == state.globalCooldownEnd
                && Objects.equals(currentCast, state.currentCast)
                && setBetterCombatTag == state.setBetterCombatTag
                && windows.equals(state.windows);
    }

    @Override
    public int hashCode() {
        return Objects.hash(resources, cooldowns, globalCooldownEnd, currentCast, setBetterCombatTag, windows);
    }

    @Override
    public String toString() {
        return "CombatState[resources=" + resources + ", cooldowns=" + cooldowns + ", globalCooldownEnd="
                + globalCooldownEnd + ", currentCast=" + currentCast + ", setBetterCombatTag=" + setBetterCombatTag
                + ", windows=" + windows + "]";
    }

    /**
     * A cast-time or channelled spell in progress (architecture 11.2): drawn by clients from its start and duration,
     * so it needs no per-tick packets. The owner gets it with this attachment, every other player in
     * {@code CastStartedPayload}.
     *
     * @param spell         the spell being cast
     * @param startGameTime the game time the cast started
     * @param durationTicks how long the cast or channel lasts after Cast Time Reduction, at least one tick
     * @param channel       whether it is a channel (a bar that empties) rather than a cast time (one that fills)
     * @param swing         whether it is an instant spell playing out over its animation (RLM-399): no cast bar, no
     *                      cast animation, no slow
     */
    public record Cast(ResourceKey<Spell> spell, long startGameTime, int durationTicks, boolean channel,
            boolean swing) {
        public static final StreamCodec<ByteBuf, Cast> STREAM_CODEC = StreamCodec.composite(
                ResourceKey.streamCodec(SpellRegistries.SPELL), Cast::spell,
                ByteBufCodecs.VAR_LONG, Cast::startGameTime,
                ByteBufCodecs.VAR_INT, Cast::durationTicks,
                ByteBufCodecs.BOOL, Cast::channel,
                ByteBufCodecs.BOOL, Cast::swing,
                Cast::new);

        public Cast {
            Objects.requireNonNull(spell, "spell");
            if (startGameTime < 0 || durationTicks < 1) {
                throw new IllegalArgumentException(
                        "A cast starts at a game time of 0 or more and lasts a tick or more: " + startGameTime + ", "
                                + durationTicks);
            }
        }

        /** A cast time ({@code channel} false) or a channel, not a swing. */
        public Cast(ResourceKey<Spell> spell, long startGameTime, int durationTicks, boolean channel) {
            this(spell, startGameTime, durationTicks, channel, false);
        }

        /** The game time the cast completes. */
        public long endGameTime() {
            return startGameTime + durationTicks;
        }
    }
}
