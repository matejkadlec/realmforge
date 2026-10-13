package io.github.matejkadlec.realmforge.qa.command;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.github.matejkadlec.realmforge.generated.GeneratedStats;
import io.github.matejkadlec.realmforge.stats.CharacterStats;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.neoforged.neoforge.common.PercentageAttribute;
import net.neoforged.neoforge.registries.DeferredHolder;

/**
 * {@code /qa stats} sets our stats on players, so a playtest can try the D5 numbers before races and classes grant any
 * (RLM-136):
 * <ul>
 *   <li>{@code /qa stats set <stat> <value> [<players>]} sets a stat's base value, yourself by default: points for a
 *   primary stat ({@code /qa stats set strength 25}), percent for a share ({@code /qa stats set crit_chance 50}). The
 *   stat rules recompute at once.</li>
 *   <li>{@code /qa stats reset [<players>]} puts every stat back to its default.</li>
 *   <li>{@code /qa stats show [<player>]} prints every stat with its base value and its value with all modifiers.</li>
 * </ul>
 */
public final class StatsCommands {
    private StatsCommands() {
    }

    static LiteralArgumentBuilder<CommandSourceStack> build() {
        LiteralArgumentBuilder<CommandSourceStack> set = Commands.literal("set");
        for (DeferredHolder<Attribute, Attribute> stat : GeneratedStats.ALL) {
            set.then(Commands.literal(stat.getId().getPath())
                    .then(Commands.argument("value", DoubleArgumentType.doubleArg(0))
                            .executes(context -> set(context, stat, self(context)))
                            .then(Commands.argument("players", EntityArgument.players())
                                    .executes(context -> set(context, stat,
                                            EntityArgument.getPlayers(context, "players"))))));
        }
        return Commands.literal("stats")
                .then(set)
                .then(Commands.literal("reset")
                        .executes(context -> reset(context, self(context)))
                        .then(Commands.argument("players", EntityArgument.players())
                                .executes(context -> reset(context, EntityArgument.getPlayers(context, "players")))))
                .then(Commands.literal("show")
                        .executes(context -> show(context, context.getSource().getPlayerOrException()))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(context -> show(context, EntityArgument.getPlayer(context, "player")))));
    }

    /** Sets the stat's base value on the player, in game units, within the stat's range; recomputes the stat rules. */
    public static void setBase(ServerPlayer player, Holder<Attribute> stat, double value) {
        AttributeInstance instance = player.getAttribute(stat);
        if (instance == null) {
            throw new IllegalStateException("players lack " + stat.getRegisteredName());
        }
        instance.setBaseValue(stat.value().sanitizeValue(value));
        CharacterStats.recompute(player);
    }

    /** Puts every stat back to its default on the player and recomputes the stat rules. */
    public static void reset(ServerPlayer player) {
        for (Holder<Attribute> stat : GeneratedStats.ALL) {
            AttributeInstance instance = player.getAttribute(stat);
            if (instance != null) {
                instance.setBaseValue(stat.value().getDefaultValue());
            }
        }
        CharacterStats.recompute(player);
    }

    private static int set(CommandContext<CommandSourceStack> context, Holder<Attribute> stat,
            Collection<ServerPlayer> players) {
        double typed = DoubleArgumentType.getDouble(context, "value");
        double value = percent(stat) ? typed / 100 : typed;
        players.forEach(player -> setBase(player, stat, value));
        context.getSource().sendSuccess(() -> Component.literal(String.format(Locale.ROOT, "Set %s to %s for %d players",
                stat.getRegisteredName(), shown(stat, stat.value().sanitizeValue(value)), players.size())), true);
        return players.size();
    }

    private static int reset(CommandContext<CommandSourceStack> context, Collection<ServerPlayer> players) {
        players.forEach(StatsCommands::reset);
        context.getSource().sendSuccess(() -> Component.literal("Reset the stats of " + players.size() + " players"),
                true);
        return players.size();
    }

    private static int show(CommandContext<CommandSourceStack> context, ServerPlayer player) {
        CommandSourceStack source = context.getSource();
        source.sendSuccess(() -> Component.literal("Stats of " + player.getPlainTextName() + " (base / with modifiers):"),
                false);
        for (Holder<Attribute> stat : GeneratedStats.ALL) {
            AttributeInstance instance = player.getAttribute(stat);
            if (instance != null) {
                source.sendSuccess(() -> Component.literal("  " + stat.getRegisteredName() + ": "
                        + shown(stat, instance.getBaseValue()) + " / " + shown(stat, instance.getValue())), false);
            }
        }
        return GeneratedStats.ALL.size();
    }

    private static Collection<ServerPlayer> self(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        return List.of(context.getSource().getPlayerOrException());
    }

    private static boolean percent(Holder<Attribute> stat) {
        return stat.value() instanceof PercentageAttribute;
    }

    private static String shown(Holder<Attribute> stat, double value) {
        return percent(stat)
                ? String.format(Locale.ROOT, "%.2f%%", value * 100)
                : String.format(Locale.ROOT, "%.2f", value);
    }
}
