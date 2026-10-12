package io.github.matejkadlec.realmforge.stats;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ExtraCodecs;

/**
 * The value of the {@code realmforge:level} attachment: a living entity's level (architecture 7). Players get it
 * from their XP and level cap ({@code character}), mobs at spawn ({@code mob}), bosses from their data. An entity
 * nobody has given a level reads {@link #DEFAULT}.
 *
 * <p>Saved as a plain number, so the shape can grow into a record field later without a migration.
 */
public record EntityLevel(int value) {
    public static final int MIN = 1;
    public static final EntityLevel DEFAULT = new EntityLevel(MIN);

    public static final Codec<EntityLevel> CODEC = ExtraCodecs.POSITIVE_INT.xmap(EntityLevel::new, EntityLevel::value);
    public static final StreamCodec<ByteBuf, EntityLevel> STREAM_CODEC =
            ByteBufCodecs.VAR_INT.map(EntityLevel::new, EntityLevel::value);

    public EntityLevel {
        if (value < MIN) {
            throw new IllegalArgumentException("A level is at least " + MIN + ": " + value);
        }
    }
}
