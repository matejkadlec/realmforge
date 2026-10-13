package io.github.matejkadlec.realmforge.spell;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.matejkadlec.realmforge.core.Codecs;
import io.netty.buffer.ByteBuf;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;

/**
 * The value of the {@code realmforge:combat} attachment on players and on mobs that cast (architecture 7 and 11): the
 * resource (mana or rage), the cooldowns and the current cast.
 *
 * <p>Unlike the other attachment values it is mutable, because it changes too often to replace and sync on every
 * change: the owner mutates it in place and calls {@code syncData} at most a few times per second or on important
 * changes (a cast or cooldown starting). Cooldowns are the server game time when they end, so they survive relogging
 * without per-tick updates. The current cast is transient: {@link #CODEC} leaves it out, so neither relogging nor the
 * copy on death (which round-trips the saved codec) brings it back; only the client sync carries it.
 */
public final class CombatState {
    /** The saved shape: resource and cooldowns, never the current cast. */
    public static final Codec<CombatState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codecs.NON_NEGATIVE_FLOAT.optionalFieldOf("resource", 0.0F).forGetter(CombatState::resource),
            Codec.unboundedMap(Identifier.CODEC, Codecs.NON_NEGATIVE_LONG).optionalFieldOf("cooldowns", Map.of())
                    .forGetter(CombatState::cooldowns),
            Codecs.NON_NEGATIVE_LONG.optionalFieldOf("global_cooldown_end", 0L)
                    .forGetter(CombatState::globalCooldownEnd)
    ).apply(instance, (resource, cooldowns, globalCooldownEnd) ->
            new CombatState(resource, cooldowns, globalCooldownEnd, null)));

    /** The synced shape: everything, the current cast included, for the owner's own cast bar and cooldowns. */
    public static final StreamCodec<ByteBuf, CombatState> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.FLOAT, CombatState::resource,
            ByteBufCodecs.<ByteBuf, Identifier, Long, Map<Identifier, Long>>map(HashMap::new,
                    Identifier.STREAM_CODEC, ByteBufCodecs.VAR_LONG), CombatState::cooldowns,
            ByteBufCodecs.VAR_LONG, CombatState::globalCooldownEnd,
            ByteBufCodecs.optional(Cast.STREAM_CODEC), CombatState::currentCast,
            (resource, cooldowns, globalCooldownEnd, cast) ->
                    new CombatState(resource, cooldowns, globalCooldownEnd, cast.orElse(null)));

    private float resource;
    private final Map<Identifier, Long> cooldowns;
    private long globalCooldownEnd;
    @Nullable
    private Cast currentCast;

    /** A fresh state: no resource, no cooldowns, not casting. Each holder gets its own instance. */
    public CombatState() {
        this(0.0F, Map.of(), 0L, null);
    }

    private CombatState(float resource, Map<Identifier, Long> cooldowns, long globalCooldownEnd,
            @Nullable Cast currentCast) {
        setResource(resource);
        this.cooldowns = new HashMap<>();
        cooldowns.forEach(this::setCooldownEnd);
        setGlobalCooldownEnd(globalCooldownEnd);
        this.currentCast = currentCast;
    }

    /** The current amount of the class's resource. Its kind and maximum come from the class (C1, C3a). */
    public float resource() {
        return resource;
    }

    public void setResource(float resource) {
        if (!(resource >= 0.0F) || !Float.isFinite(resource)) {
            throw new IllegalArgumentException("A resource is a finite amount of 0 or more: " + resource);
        }
        this.resource = resource;
    }

    /** Spell to the game time its cooldown ends; read-only. Ended cooldowns stay until {@link #clearEndedCooldowns}. */
    public Map<Identifier, Long> cooldowns() {
        return Map.copyOf(cooldowns);
    }

    /** Whether the spell's own cooldown still runs at game time {@code now}. */
    public boolean isOnCooldown(Identifier spell, long now) {
        Long end = cooldowns.get(spell);
        return end != null && now < end;
    }

    public void setCooldownEnd(Identifier spell, long endGameTime) {
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

    /** The game time the global cooldown ends; 0 when it never ran. */
    public long globalCooldownEnd() {
        return globalCooldownEnd;
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

    @Override
    public boolean equals(Object other) {
        return other instanceof CombatState state
                && Float.compare(resource, state.resource) == 0
                && cooldowns.equals(state.cooldowns)
                && globalCooldownEnd == state.globalCooldownEnd
                && Objects.equals(currentCast, state.currentCast);
    }

    @Override
    public int hashCode() {
        return Objects.hash(resource, cooldowns, globalCooldownEnd, currentCast);
    }

    @Override
    public String toString() {
        return "CombatState[resource=" + resource + ", cooldowns=" + cooldowns + ", globalCooldownEnd="
                + globalCooldownEnd + ", currentCast=" + currentCast + "]";
    }

    /**
     * A cast-time or channelled spell in progress (architecture 11.2): drawn by clients from its start and duration,
     * so it needs no per-tick packets.
     *
     * @param spell         the spell being cast
     * @param startGameTime the game time the cast started
     * @param durationTicks how long the cast or channel lasts, at least one tick
     */
    public record Cast(Identifier spell, long startGameTime, int durationTicks) {
        public static final StreamCodec<ByteBuf, Cast> STREAM_CODEC = StreamCodec.composite(
                Identifier.STREAM_CODEC, Cast::spell,
                ByteBufCodecs.VAR_LONG, Cast::startGameTime,
                ByteBufCodecs.VAR_INT, Cast::durationTicks,
                Cast::new);

        public Cast {
            Objects.requireNonNull(spell, "spell");
            if (startGameTime < 0 || durationTicks < 1) {
                throw new IllegalArgumentException(
                        "A cast starts at a game time of 0 or more and lasts a tick or more: " + startGameTime + ", "
                                + durationTicks);
            }
        }

        /** The game time the cast completes. */
        public long endGameTime() {
            return startGameTime + durationTicks;
        }
    }
}
