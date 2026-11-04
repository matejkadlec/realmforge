package io.github.matejkadlec.realmforge.qa.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import io.github.matejkadlec.realmforge.character.Characters;
import io.github.matejkadlec.realmforge.character.Specialization;
import io.github.matejkadlec.realmforge.character.SpellPoints;
import io.github.matejkadlec.realmforge.character.SpellTrees;
import io.github.matejkadlec.realmforge.spell.Spell;
import io.github.matejkadlec.realmforge.spell.SpellRegistries;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /qa spec}: a character's specialization and spell points (C5, RLM-333), through the same rules as the spell
 * trees window. {@code reset} is the Scroll of Respec's effect until that scroll exists (RLM-226).
 */
final class SpecCommands {
    private static final SuggestionProvider<CommandSourceStack> SPECIALIZATIONS = (context, builder) ->
            SharedSuggestionProvider.suggest(Characters.mainClass(context.getSource().getPlayerOrException())
                    .map(playerClass -> playerClass.value().specializations().stream().map(Specialization::id).toList())
                    .orElse(java.util.List.of()), builder);
    private static final SuggestionProvider<CommandSourceStack> SPELLS = (context, builder) ->
            SharedSuggestionProvider.suggestResource(SpellTrees.chosen(context.getSource().getPlayerOrException())
                    .map(tree -> tree.spells().stream().map(taught -> taught.key().identifier()).toList())
                    .orElse(java.util.List.of()), builder);

    private SpecCommands() {
    }

    static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("spec")
                .then(Commands.literal("show").executes(SpecCommands::show))
                .then(Commands.literal("pick")
                        .then(Commands.argument("specialization", StringArgumentType.word()).suggests(SPECIALIZATIONS)
                                .executes(SpecCommands::pick)))
                .then(Commands.literal("spend")
                        .then(Commands.argument("spell", IdentifierArgument.id()).suggests(SPELLS)
                                .executes(SpecCommands::spend)))
                .then(Commands.literal("reset").executes(SpecCommands::reset));
    }

    private static int show(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        String text = "Specialization " + Characters.data(player).specialization().orElse("none") + ", level "
                + Characters.level(player) + ": " + SpellPoints.earned(Characters.level(player)) + " points earned, "
                + SpellTrees.spent(player) + " spent, " + SpellTrees.unspent(player) + " unspent";
        context.getSource().sendSuccess(() -> Component.literal(text), false);
        return 1;
    }

    private static int pick(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        String id = StringArgumentType.getString(context, "specialization");
        SpellTrees.PickResult result = SpellTrees.pick(player, id);
        if (result == SpellTrees.PickResult.PICKED) {
            context.getSource().sendSuccess(() -> Component.literal("Picked " + id), true);
            return 1;
        }
        context.getSource().sendFailure(Component.literal("Not picked: " + result));
        return 0;
    }

    private static int spend(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ResourceKey<Spell> spell = ResourceKey.create(SpellRegistries.SPELL,
                IdentifierArgument.getId(context, "spell"));
        SpellPoints.Result result = SpellTrees.spend(player, spell);
        if (result == SpellPoints.Result.SPENT) {
            context.getSource().sendSuccess(() -> Component.literal("Spent a point on " + spell.identifier() + "; "
                    + SpellTrees.unspent(player) + " left"), true);
            return 1;
        }
        context.getSource().sendFailure(Component.literal("No point spent: " + result));
        return 0;
    }

    private static int reset(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        int returned = SpellTrees.reset(player);
        context.getSource().sendSuccess(() -> Component.literal(returned + " spell points returned"), true);
        return returned;
    }
}
