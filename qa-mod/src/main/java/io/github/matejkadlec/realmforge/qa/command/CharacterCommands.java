package io.github.matejkadlec.realmforge.qa.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import io.github.matejkadlec.realmforge.character.CharacterAttachments;
import io.github.matejkadlec.realmforge.character.CharacterCreation;
import io.github.matejkadlec.realmforge.character.CharacterData;
import io.github.matejkadlec.realmforge.character.CharacterRegistries;
import io.github.matejkadlec.realmforge.character.Characters;
import io.github.matejkadlec.realmforge.character.PlayerClass;
import io.github.matejkadlec.realmforge.character.Race;
import io.github.matejkadlec.realmforge.spell.SpellAttachments;
import io.github.matejkadlec.realmforge.spell.Spellbook;
import io.github.matejkadlec.realmforge.stats.CharacterStats;
import io.github.matejkadlec.realmforge.stats.EntityLevel;
import io.github.matejkadlec.realmforge.stats.StatsAttachments;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /qa character} changes race and class in M1 (architecture 10.1), yourself by default:
 * <ul>
 *   <li>{@code /qa character show [<player>]} prints the player's race, classes and character XP.</li>
 *   <li>{@code /qa character reset [<player>]} removes the character, so the creation screen opens again and the race
 *   and class modifiers go; the level falls back to 1 and the spellbook empties (C4), and the items of the starting
 *   kit stay.</li>
 *   <li>{@code /qa character create <race> <class> [<player>]} creates a character as the screen does, with the same
 *   rules and the starting kit, and says why when it refuses.</li>
 * </ul>
 */
public final class CharacterCommands {
    private static final SuggestionProvider<CommandSourceStack> RACES = (context, builder) ->
            SharedSuggestionProvider.suggestResource(registry(context.getSource(), CharacterRegistries.RACE).keySet(),
                    builder);
    private static final SuggestionProvider<CommandSourceStack> CLASSES = (context, builder) ->
            SharedSuggestionProvider.suggestResource(registry(context.getSource(), CharacterRegistries.CLASS).keySet(),
                    builder);

    private CharacterCommands() {
    }

    static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("character")
                .then(Commands.literal("show")
                        .executes(context -> show(context, context.getSource().getPlayerOrException()))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(context -> show(context, EntityArgument.getPlayer(context, "player")))))
                .then(Commands.literal("reset")
                        .executes(context -> reset(context, context.getSource().getPlayerOrException()))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(context -> reset(context, EntityArgument.getPlayer(context, "player")))))
                .then(Commands.literal("create")
                        .then(Commands.argument("race", IdentifierArgument.id()).suggests(RACES)
                                .then(Commands.argument("class", IdentifierArgument.id()).suggests(CLASSES)
                                        .executes(context -> create(context,
                                                context.getSource().getPlayerOrException()))
                                        .then(Commands.argument("player", EntityArgument.player())
                                                .executes(context -> create(context,
                                                        EntityArgument.getPlayer(context, "player")))))));
    }

    /**
     * Removes the player's character and recomputes their stats, so the race and class modifiers go and the client
     * opens the creation screen again. The level falls back to 1 and the spellbook empties with it (C4: the next
     * character learns its own class's spells). Returns whether the player had one.
     */
    public static boolean reset(ServerPlayer player) {
        boolean had = Characters.exists(player);
        player.removeData(CharacterAttachments.CHARACTER);
        player.setData(StatsAttachments.LEVEL, new EntityLevel(EntityLevel.MIN));
        player.setData(SpellAttachments.SPELLBOOK, Spellbook.EMPTY);
        CharacterStats.recompute(player);
        player.setHealth(Math.min(player.getHealth(), player.getMaxHealth()));
        return had;
    }

    private static int show(CommandContext<CommandSourceStack> context, ServerPlayer player) {
        CharacterData character = Characters.data(player);
        String text = character.exists()
                ? player.getPlainTextName() + ": race " + character.race().orElseThrow().identifier() + ", classes "
                        + character.classes().stream().map(key -> key.identifier().toString()).toList()
                        + ", character XP " + character.totalXp()
                : player.getPlainTextName() + " has no character";
        context.getSource().sendSuccess(() -> Component.literal(text), false);
        return character.exists() ? 1 : 0;
    }

    private static int reset(CommandContext<CommandSourceStack> context, ServerPlayer player) {
        boolean had = reset(player);
        context.getSource().sendSuccess(() -> Component.literal(had
                ? "Removed the character of " + player.getPlainTextName() + "; character creation opens again"
                : player.getPlainTextName() + " had no character"), true);
        return had ? 1 : 0;
    }

    private static int create(CommandContext<CommandSourceStack> context, ServerPlayer player) {
        ResourceKey<Race> race = ResourceKey.create(CharacterRegistries.RACE,
                IdentifierArgument.getId(context, "race"));
        ResourceKey<PlayerClass> playerClass = ResourceKey.create(CharacterRegistries.CLASS,
                IdentifierArgument.getId(context, "class"));
        CharacterCreation.Result result = CharacterCreation.create(player, race, playerClass);
        if (result == CharacterCreation.Result.CREATED) {
            context.getSource().sendSuccess(() -> Component.literal("Created a character for "
                    + player.getPlainTextName() + ": " + race.identifier() + " " + playerClass.identifier()), true);
            return 1;
        }
        context.getSource().sendFailure(Component.literal("No character created: " + result
                + " (/qa character reset removes an existing one)"));
        return 0;
    }

    private static <T> Registry<T> registry(CommandSourceStack source,
            ResourceKey<? extends Registry<? extends T>> key) {
        return source.getServer().registryAccess().lookupOrThrow(key);
    }
}
