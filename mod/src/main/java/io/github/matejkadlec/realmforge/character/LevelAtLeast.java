package io.github.matejkadlec.realmforge.character;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.matejkadlec.realmforge.core.Requirement;
import io.github.matejkadlec.realmforge.progression.LevelCaps;
import net.minecraft.server.level.ServerPlayer;

/** {@code realmforge:level_at_least}: the player's character level is at least {@code level} (architecture 10.2). */
public record LevelAtLeast(int level) implements Requirement {
    public static final MapCodec<LevelAtLeast> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                    Codec.intRange(1, LevelCaps.MAX_LEVEL).fieldOf("level").forGetter(LevelAtLeast::level))
            .apply(instance, LevelAtLeast::new));

    @Override
    public MapCodec<LevelAtLeast> codec() {
        return MAP_CODEC;
    }

    @Override
    public boolean test(ServerPlayer player) {
        return Characters.level(player) >= level;
    }
}
