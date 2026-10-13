package io.github.matejkadlec.realmforge.combat.net;

import io.github.matejkadlec.realmforge.Realmforge;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server to client: one floating combat text (architecture 9.1, 17.3), sent to the player who dealt a hit and to the
 * player who took it. The client shows it over the hit entity, or at the side of the screen for the player's own
 * damage taken ({@link #INCOMING}).
 *
 * @param entityId the entity that was hit
 * @param amount the damage dealt; unused for a dodge
 * @param color the text colour, opaque ARGB: the spell school's, or white for a melee hit
 * @param flags {@link #CRIT}, {@link #DODGE} and {@link #INCOMING}
 */
public record CombatTextPayload(int entityId, float amount, int color, byte flags) implements CustomPacketPayload {
    /** The hit was critical: shown bigger. */
    public static final byte CRIT = 1;
    /** The hit was dodged: the word "Dodge" instead of a number. */
    public static final byte DODGE = 2;
    /** The receiving player was hit. */
    public static final byte INCOMING = 4;

    public static final Type<CombatTextPayload> TYPE = new Type<>(Realmforge.id("combat_text"));
    public static final StreamCodec<ByteBuf, CombatTextPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, CombatTextPayload::entityId,
            ByteBufCodecs.FLOAT, CombatTextPayload::amount,
            ByteBufCodecs.INT, CombatTextPayload::color,
            ByteBufCodecs.BYTE, CombatTextPayload::flags,
            CombatTextPayload::new);

    public boolean crit() {
        return (flags & CRIT) != 0;
    }

    public boolean dodge() {
        return (flags & DODGE) != 0;
    }

    public boolean incoming() {
        return (flags & INCOMING) != 0;
    }

    @Override
    public Type<CombatTextPayload> type() {
        return TYPE;
    }
}
