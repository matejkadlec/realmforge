package io.github.matejkadlec.realmforge.character;

import io.github.matejkadlec.realmforge.Realmforge;
import io.github.matejkadlec.realmforge.stats.CharacterStats;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * Creates a player's character from {@code CreateCharacterPayload(race, class)} (architecture 10.1). The client's screen
 * only offers valid choices, but the server checks every request again: the player has no character yet, the race and
 * the class exist, and the race allows the class. Then it sets the {@code character} attachment, brings the level and
 * level cap up to date ({@link Leveling}), teaches the class's first spells and the racial spell with a chat line each
 * ({@link ClassSpells}, C4), recomputes the stats (race and class modifiers), refills health to the new maximum and
 * gives the class's starting kit with the player's first character only ({@link CharacterAttachments#STARTING_KIT}).
 */
public final class CharacterCreation {
    private CharacterCreation() {
    }

    static void register() {
        NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerLoggedInEvent.class, CharacterCreation::onLogin);
    }

    /**
     * A player whose character is older than {@link CharacterAttachments#STARTING_KIT} received its kit at creation
     * all the same: their login records it, so a character after a reset brings no second one in an old world either.
     */
    private static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && Characters.exists(player)
                && !player.getData(CharacterAttachments.STARTING_KIT)) {
            player.setData(CharacterAttachments.STARTING_KIT, true);
        }
    }

    /** What a creation request led to. Only {@link #CREATED} changes anything. */
    public enum Result {
        CREATED,
        /** The player already has a character; changing race or class is a QA command in M1. */
        ALREADY_CREATED,
        UNKNOWN_RACE,
        UNKNOWN_CLASS,
        /** The race does not list the class. */
        CLASS_NOT_ALLOWED
    }

    /** The creation rules, without changing anything: what {@link #create} would do. */
    public static Result check(CharacterData current, HolderLookup.Provider registries, ResourceKey<Race> race,
            ResourceKey<PlayerClass> playerClass) {
        Optional<Race> raceEntry = registries.lookupOrThrow(CharacterRegistries.RACE).get(race).map(Holder::value);
        boolean classExists = registries.lookupOrThrow(CharacterRegistries.CLASS).get(playerClass).isPresent();
        return check(current, raceEntry, classExists, playerClass);
    }

    /**
     * The creation rules on entries already looked up: the player has no character yet, the race and the class exist
     * (an empty race, {@code classExists} false), and the race allows the class, checked in that order.
     */
    public static Result check(CharacterData current, Optional<Race> race, boolean classExists,
            ResourceKey<PlayerClass> playerClass) {
        if (current.exists()) {
            return Result.ALREADY_CREATED;
        }
        if (race.isEmpty()) {
            return Result.UNKNOWN_RACE;
        }
        if (!classExists) {
            return Result.UNKNOWN_CLASS;
        }
        return race.get().allows(playerClass) ? Result.CREATED : Result.CLASS_NOT_ALLOWED;
    }

    /** Creates the player's character if the rules allow it, and returns what happened. */
    public static Result create(ServerPlayer player, ResourceKey<Race> race, ResourceKey<PlayerClass> playerClass) {
        CharacterData current = Characters.data(player);
        Result result = check(current, player.registryAccess(), race, playerClass);
        if (result != Result.CREATED) {
            Realmforge.LOGGER.debug("Refused to create a character for {} ({}, {}): {}",
                    player.getPlainTextName(), race.identifier(), playerClass.identifier(), result);
            return result;
        }
        player.setData(CharacterAttachments.CHARACTER, current.create(race, playerClass));
        Leveling.update(player, false);
        ClassSpells.teach(player, true);
        CharacterStats.recompute(player);
        player.setHealth(player.getMaxHealth());
        // The starting kit comes with the player's first character only (Matěj, 2026-10-30).
        if (!player.getData(CharacterAttachments.STARTING_KIT)) {
            PlayerClass entry = player.registryAccess().lookupOrThrow(CharacterRegistries.CLASS)
                    .getOrThrow(playerClass).value();
            for (KitItem item : entry.startingKit()) {
                item.stacks().forEach(stack -> give(player, stack));
            }
            player.setData(CharacterAttachments.STARTING_KIT, true);
        }
        Realmforge.LOGGER.info("{} created a character: {} {}", player.getPlainTextName(), race.identifier(),
                playerClass.identifier());
        return result;
    }

    /** Into the inventory, or dropped at the player's feet for them alone when it is full, as {@code /give} does. */
    private static void give(ServerPlayer player, ItemStack stack) {
        if (!player.getInventory().add(stack) || !stack.isEmpty()) {
            var drop = player.drop(stack, false);
            if (drop != null) {
                drop.setNoPickUpDelay();
                drop.setTarget(player.getUUID());
            }
        }
    }
}
