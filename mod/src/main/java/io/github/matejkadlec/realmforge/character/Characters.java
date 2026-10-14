package io.github.matejkadlec.realmforge.character;

import io.github.matejkadlec.realmforge.generated.GeneratedCurves;
import io.github.matejkadlec.realmforge.stats.EntityLevel;
import io.github.matejkadlec.realmforge.stats.StatsAttachments;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * The public API of {@code character} for other features (architecture 4.3). Every system that reads a player's race
 * or class goes through it and handles a player without a character (architecture 10.1): until the character exists,
 * gameplay payloads (casting, the spell bar, talents) are refused and no character XP is awarded. Leveling (architecture
 * 10.2) adds the level, character XP and its rewards.
 */
public final class Characters {
    private Characters() {
    }

    /** The player's character, {@link CharacterData#NONE} before they create one. Reading it stores nothing. */
    public static CharacterData data(Player player) {
        return player.getExistingData(CharacterAttachments.CHARACTER).orElse(CharacterData.NONE);
    }

    /** Whether the player has created a character. */
    public static boolean exists(Player player) {
        return data(player).exists();
    }

    /**
     * The player's first class (the only one in M1), resolved in the player's registries: synced, so this works on
     * the client too. Empty without a character, or when the class's entry no longer exists.
     */
    public static Optional<Holder.Reference<PlayerClass>> mainClass(Player player) {
        return data(player).classes().stream().findFirst()
                .flatMap(key -> player.registryAccess().lookupOrThrow(CharacterRegistries.CLASS).get(key));
    }

    /** The player's character level from the {@code realmforge:level} attachment, 1 until they have one. */
    public static int level(Player player) {
        return player.getExistingData(StatsAttachments.LEVEL).map(EntityLevel::value).orElse(EntityLevel.MIN);
    }

    /**
     * Gives the player character XP from a reward (a quest, {@code /realmforge xp add}), times their XP multiplier,
     * and levels them up as far as it reaches, with the level-up's effects (architecture 10.2). Returns the XP they
     * gained: 0 without a character.
     */
    public static long rewardXp(ServerPlayer player, long xp) {
        return Leveling.reward(player, xp);
    }

    /**
     * Sets the player's total character XP and their level from it, quietly (operator and QA commands). False without
     * a character, which gains no XP.
     */
    public static boolean setTotalXp(ServerPlayer player, long totalXp) {
        return Leveling.setTotalXp(player, totalXp);
    }

    /** The total character XP a player needs for {@code level} under the current curve; empty if it was removed. */
    public static Optional<Long> xpToReach(Player player, int level) {
        return Leveling.curve(player.registryAccess(), GeneratedCurves.XP_TO_NEXT_LEVEL)
                .map(curve -> XpTable.toReach(curve, level));
    }
}
