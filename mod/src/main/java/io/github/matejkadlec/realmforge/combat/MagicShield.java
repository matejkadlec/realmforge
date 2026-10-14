package io.github.matejkadlec.realmforge.combat;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * A player's magic shield (architecture 9.1, the {@code realmforge:magic_shield} attachment): the points left in the
 * pool, which takes a hit's damage after absorption hearts and before health, and when it refills. {@link MagicShields}
 * runs it.
 *
 * @param points the points left, at most the player's {@code realmforge:magic_shield} stat
 * @param refillAt the player's tick count ({@code Entity#tickCount}) from which the pool refills to full; a new player
 *     entity starts at 0, so its first tick fills the pool
 */
public record MagicShield(float points, int refillAt) {
    /** An empty pool that refills at the first tick: every new player entity, a login or a respawn. */
    public static final MagicShield EMPTY = new MagicShield(0, 0);

    /** Only the points travel to the client, for the HUD bar; the refill time is the server's. */
    public static final StreamCodec<ByteBuf, MagicShield> STREAM_CODEC =
            ByteBufCodecs.FLOAT.map(points -> new MagicShield(points, 0), MagicShield::points);
}
