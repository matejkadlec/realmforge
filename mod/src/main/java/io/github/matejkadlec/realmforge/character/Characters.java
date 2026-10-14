package io.github.matejkadlec.realmforge.character;

import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.player.Player;

/**
 * The public API of {@code character} for other features (architecture 4.3). Every system that reads a player's race
 * or class goes through it and handles a player without a character (architecture 10.1): until the character exists,
 * gameplay payloads (casting, the spell bar, talents) are refused and no character XP is awarded.
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
}
