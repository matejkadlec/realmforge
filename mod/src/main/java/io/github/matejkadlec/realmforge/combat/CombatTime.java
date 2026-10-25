package io.github.matejkadlec.realmforge.combat;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * When a living entity last fought (architecture 9.4, the {@code realmforge:in_combat} attachment): the game time of
 * its last hit dealt or taken, and the game time at which that hit's combat ends. {@link InCombat} runs it. Game time
 * is the server's one clock, the same in every dimension, which a client follows.
 *
 * @param lastHit the game time of the last hit the entity dealt to, or took from, another living entity
 * @param until the game time from which the entity is out of combat: the last hit's time plus the in-combat window
 *     ({@link CombatConfig#IN_COMBAT_SECONDS}) as it was then
 */
public record CombatTime(long lastHit, long until) {
    /** No hit yet: every new entity, a login or a respawn included. */
    public static final CombatTime NONE = new CombatTime(Long.MIN_VALUE, Long.MIN_VALUE);

    public static final StreamCodec<ByteBuf, CombatTime> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, CombatTime::lastHit,
            ByteBufCodecs.VAR_LONG, CombatTime::until,
            CombatTime::new);

    /** Whether this is a real hit's time, not {@link #NONE}. */
    public boolean exists() {
        return lastHit != Long.MIN_VALUE;
    }
}
