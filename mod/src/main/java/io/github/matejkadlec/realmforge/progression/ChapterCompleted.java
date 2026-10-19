package io.github.matejkadlec.realmforge.progression;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.matejkadlec.realmforge.core.Requirement;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code realmforge:chapter_completed}: the player has completed {@code chapter}, that is earned its advancement
 * ({@link Chapters#isCompleted}). Content a chapter unlocks names it this way (architecture 16).
 */
public record ChapterCompleted(ResourceKey<Chapter> chapter) implements Requirement {
    public static final MapCodec<ChapterCompleted> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                    ResourceKey.codec(ProgressionRegistries.CHAPTER).fieldOf("chapter")
                            .forGetter(ChapterCompleted::chapter))
            .apply(instance, ChapterCompleted::new));

    @Override
    public MapCodec<ChapterCompleted> codec() {
        return MAP_CODEC;
    }

    @Override
    public boolean test(ServerPlayer player) {
        return Chapters.isCompleted(player, chapter);
    }
}
