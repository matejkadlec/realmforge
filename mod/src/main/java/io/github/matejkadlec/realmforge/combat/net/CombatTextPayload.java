package io.github.matejkadlec.realmforge.combat.net;

import io.github.matejkadlec.realmforge.Realmforge;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server to client: one floating combat text (architecture 9.1, 17.3). A hit or a miss goes to the player who dealt
 * it, a heal to the players near the healed entity; the client shows it over that entity. A miss also goes to the
 * player who dodged it, shown beside the crosshair ({@link #INCOMING}).
 *
 * @param entityId the entity that was hit or healed
 * @param amount the damage dealt or the health healed; unused for a miss
 * @param color the text colour, opaque ARGB, chosen by the server ({@code combat/CombatText})
 * @param flags {@link #CRIT}, {@link #MISS}, {@link #INCOMING} and {@link #HEAL}
 */
public record CombatTextPayload(int entityId, float amount, int color, byte flags) implements CustomPacketPayload {
    /** The hit was critical: its number ends with "!". */
    public static final byte CRIT = 1;
    /** The hit was dodged: "Miss!" instead of a number. */
    public static final byte MISS = 2;
    /** The receiving player dodged the hit. */
    public static final byte INCOMING = 4;
    /** Health healed, not damage: its number starts with "+". */
    public static final byte HEAL = 8;

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

    public boolean miss() {
        return (flags & MISS) != 0;
    }

    public boolean incoming() {
        return (flags & INCOMING) != 0;
    }

    public boolean heal() {
        return (flags & HEAL) != 0;
    }

    @Override
    public Type<CombatTextPayload> type() {
        return TYPE;
    }
}
