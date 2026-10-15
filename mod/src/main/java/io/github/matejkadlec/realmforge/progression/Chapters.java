package io.github.matejkadlec.realmforge.progression;

import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;

/**
 * The public API of {@code progression} for chapters (architecture 4.3, 16): a player completes a chapter by earning
 * its advancement, {@code <namespace>:chapter/<path>} for the chapter {@code <namespace>:<path>}, whose only criterion
 * is {@code minecraft:impossible}, so only the chapter's last quest or a command grants it. Advancements are per
 * player, so chapters and level caps are too.
 */
public final class Chapters {
    /** The path prefix of every chapter's advancement. */
    public static final String ADVANCEMENT_PREFIX = "chapter/";

    private Chapters() {
    }

    /** The advancement that marks the chapter completed: {@code <namespace>:chapter/<path>}. */
    public static Identifier advancementId(ResourceKey<Chapter> chapter) {
        Identifier id = chapter.identifier();
        return Identifier.fromNamespaceAndPath(id.getNamespace(), ADVANCEMENT_PREFIX + id.getPath());
    }

    /** The chapter an advancement completes, if it is a chapter's advancement by its id (the entry may not exist). */
    public static Optional<ResourceKey<Chapter>> ofAdvancement(Identifier advancement) {
        if (!advancement.getPath().startsWith(ADVANCEMENT_PREFIX)) {
            return Optional.empty();
        }
        String path = advancement.getPath().substring(ADVANCEMENT_PREFIX.length());
        return Optional.of(ResourceKey.create(ProgressionRegistries.CHAPTER,
                Identifier.fromNamespaceAndPath(advancement.getNamespace(), path)));
    }

    /** Whether the player has completed the chapter: false when its advancement does not exist. */
    public static boolean isCompleted(ServerPlayer player, ResourceKey<Chapter> chapter) {
        AdvancementHolder advancement = player.level().getServer().getAdvancements().get(advancementId(chapter));
        return advancement != null && player.getAdvancements().getOrStartProgress(advancement).isDone();
    }

    /** The player's level cap from the chapters they completed ({@link LevelCaps#cap}). */
    public static int levelCap(ServerPlayer player) {
        Registry<Chapter> chapters = chapters(player);
        return LevelCaps.cap(chapters.stream().toList(), completed(player, chapters));
    }

    /** The first chapter the player has not completed, the one that raises their cap next; empty after the last. */
    public static Optional<Holder.Reference<Chapter>> next(ServerPlayer player) {
        Registry<Chapter> chapters = chapters(player);
        return LevelCaps.next(chapters.stream().toList(), completed(player, chapters))
                .flatMap(chapters::getResourceKey)
                .flatMap(chapters::get);
    }

    private static Registry<Chapter> chapters(ServerPlayer player) {
        return player.registryAccess().lookupOrThrow(ProgressionRegistries.CHAPTER);
    }

    /** Matches the chapters of {@code chapters} the player completed, by their key, as entries may be equal. */
    private static Predicate<Chapter> completed(ServerPlayer player, Registry<Chapter> chapters) {
        return chapter -> chapters.getResourceKey(chapter).map(key -> isCompleted(player, key)).orElse(false);
    }
}
