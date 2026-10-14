package io.github.matejkadlec.realmforge.progression;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * A chapter of the story, an entry of the datapack registry {@code realmforge:chapter} from {@code content/chapters/}
 * (architecture 5.2, 10.2, 16): its place in the chain and the level caps it sets. A player completes a chapter by
 * earning its advancement, {@code <namespace>:chapter/<path>} for the chapter {@code <namespace>:<path>}, which the
 * chapter's last quest grants; {@link LevelCaps} turns the completed chapters into the player's level cap.
 *
 * @param name translation key of the chapter's name
 * @param order position in the story, 1 for the first chapter
 * @param levelCap the cap while this is the player's current chapter; the first chapter's is a new player's cap
 * @param completedLevelCap the cap once the player completes it, above {@code levelCap}
 */
public record Chapter(String name, int order, int levelCap, int completedLevelCap) {
    public static final Codec<Chapter> CODEC = RecordCodecBuilder.<Chapter>create(instance -> instance.group(
                    Codec.STRING.fieldOf("name").forGetter(Chapter::name),
                    Codec.intRange(1, LevelCaps.MAX_LEVEL).fieldOf("order").forGetter(Chapter::order),
                    Codec.intRange(1, LevelCaps.MAX_LEVEL).fieldOf("level_cap").forGetter(Chapter::levelCap),
                    Codec.intRange(1, LevelCaps.MAX_LEVEL).fieldOf("completed_level_cap")
                            .forGetter(Chapter::completedLevelCap))
            .apply(instance, Chapter::new))
            .validate(Chapter::validate);

    private static DataResult<Chapter> validate(Chapter chapter) {
        if (chapter.completedLevelCap() <= chapter.levelCap()) {
            return DataResult.error(() -> "A chapter's completed_level_cap (" + chapter.completedLevelCap()
                    + ") must be above its level_cap (" + chapter.levelCap() + ")");
        }
        return DataResult.success(chapter);
    }
}
