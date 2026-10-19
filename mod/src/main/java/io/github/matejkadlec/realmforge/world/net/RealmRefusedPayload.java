package io.github.matejkadlec.realmforge.world.net;

import io.github.matejkadlec.realmforge.Realmforge;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server to client: a trip into a dimension the player may not enter was refused (architecture 15.1, 17.3; the realm
 * portal spec, "Access and unlocks"). The client shows "You are not eligible to pass through the portal." in the
 * middle of the screen with the realm's hint below it ({@code client/hud/RealmRefusalHud}), smaller than a vanilla
 * title, whose 4x scale runs the sentence into the screen's edges (Matěj, 2026-10-19).
 *
 * @param hintKey the translation key of the realm's hint, or empty for a dimension that is no realm
 */
public record RealmRefusedPayload(String hintKey) implements CustomPacketPayload {
    public static final Type<RealmRefusedPayload> TYPE = new Type<>(Realmforge.id("realm_refused"));
    public static final StreamCodec<ByteBuf, RealmRefusedPayload> STREAM_CODEC =
            ByteBufCodecs.STRING_UTF8.map(RealmRefusedPayload::new, RealmRefusedPayload::hintKey);

    @Override
    public Type<RealmRefusedPayload> type() {
        return TYPE;
    }
}
