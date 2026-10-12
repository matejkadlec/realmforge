package io.github.matejkadlec.realmforge.testing;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.gson.JsonElement;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.neoforged.neoforge.network.connection.ConnectionType;

/** Encode-and-decode helpers for the codecs of attachment values and payloads. */
public final class RoundTrip {
    private RoundTrip() {
    }

    /** Through NBT, the way NeoForge saves an attachment and copies it on death. */
    public static <T> T nbt(Codec<T> codec, T value) {
        Tag tag = codec.encodeStart(NbtOps.INSTANCE, value).getOrThrow();
        return codec.parse(NbtOps.INSTANCE, tag).getOrThrow();
    }

    /** Through JSON, which checks that the shape does not depend on NBT's types. */
    public static <T> T json(Codec<T> codec, T value) {
        JsonElement json = codec.encodeStart(JsonOps.INSTANCE, value).getOrThrow();
        return codec.parse(JsonOps.INSTANCE, json).getOrThrow();
    }

    /** Through a network buffer, the way an attachment is synced; the decode must read every byte written. */
    public static <T> T stream(StreamCodec<? super RegistryFriendlyByteBuf, T> codec, T value) {
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY,
                ConnectionType.NEOFORGE);
        try {
            codec.encode(buf, value);
            T decoded = codec.decode(buf);
            assertEquals(0, buf.readableBytes(), "the decode left bytes unread");
            return decoded;
        } finally {
            buf.release();
        }
    }

    /** Decodes a saved shape written as SNBT, for samples and malformed data. */
    public static <T> DataResult<T> parseSnbt(Codec<T> codec, String snbt) {
        try {
            return codec.parse(NbtOps.INSTANCE, TagParser.parseCompoundFully(snbt));
        } catch (CommandSyntaxException e) {
            throw new IllegalArgumentException("Not valid SNBT: " + snbt, e);
        }
    }
}
