package io.github.matejkadlec.realmforge.character.net;

import io.github.matejkadlec.realmforge.Realmforge;
import io.github.matejkadlec.realmforge.character.CharacterRegistries;
import io.github.matejkadlec.realmforge.character.PlayerClass;
import io.github.matejkadlec.realmforge.character.Race;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;

/**
 * Client to server: the player chose a race and a class on the character creation screen (architecture 10.1, 17.3).
 * An intent: {@code CharacterCreation} checks it again on the server.
 */
public record CreateCharacterPayload(ResourceKey<Race> race, ResourceKey<PlayerClass> playerClass)
        implements CustomPacketPayload {
    public static final Type<CreateCharacterPayload> TYPE = new Type<>(Realmforge.id("create_character"));
    public static final StreamCodec<ByteBuf, CreateCharacterPayload> STREAM_CODEC = StreamCodec.composite(
            ResourceKey.streamCodec(CharacterRegistries.RACE), CreateCharacterPayload::race,
            ResourceKey.streamCodec(CharacterRegistries.CLASS), CreateCharacterPayload::playerClass,
            CreateCharacterPayload::new);

    @Override
    public Type<CreateCharacterPayload> type() {
        return TYPE;
    }
}
