package io.github.matejkadlec.realmforge.character;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.matejkadlec.realmforge.core.Requirement;
import io.github.matejkadlec.realmforge.generated.GeneratedTexts;
import io.github.matejkadlec.realmforge.progression.LevelCaps;
import java.util.Optional;
import net.minecraft.network.chat.Component;
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

    /** "Requires level 6.": what a player below the level reads, at a realm's portal for one (RLM-306). */
    @Override
    public Optional<Component> unmetHint() {
        return Optional.of(Component.translatable(GeneratedTexts.MESSAGE_REQUIRES_LEVEL, level));
    }
}
