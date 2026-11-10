package io.github.matejkadlec.realmforge.world.net;

import io.github.matejkadlec.realmforge.Realmforge;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server to client: a trip into a dimension the player may not enter was refused (architecture 15.1, 17.3; the realm
 * portal spec, "Access and unlocks"). The client shows "You are not eligible to pass through the portal." in the
 * middle of the screen with the hint below it ({@code client/hud/RealmRefusalHud}), smaller than a vanilla title,
 * whose 4x scale runs the sentence into the screen's edges (Matěj, 2026-10-19).
 *
 * @param hint what the player lacks: the realm's unlock hint, or the level it requires (RLM-306); empty for a
 *     dimension that is no realm and whose failed requirement says nothing
 */
public record RealmRefusedPayload(Component hint) implements CustomPacketPayload {
    public static final Type<RealmRefusedPayload> TYPE = new Type<>(Realmforge.id("realm_refused"));
    public static final StreamCodec<ByteBuf, RealmRefusedPayload> STREAM_CODEC =
            ComponentSerialization.TRUSTED_CONTEXT_FREE_STREAM_CODEC.map(RealmRefusedPayload::new,
                    RealmRefusedPayload::hint);

    @Override
    public Type<RealmRefusedPayload> type() {
        return TYPE;
    }
}
