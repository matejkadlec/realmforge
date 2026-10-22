package io.github.matejkadlec.realmforge.qa.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import java.util.Arrays;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/**
 * {@code /qa run <command>; <command>; ...}: runs several commands in order, each as the source that ran this one, so
 * a playtest that needs three or more commands takes one line (the flow's "Reaching the state"). Chat cuts a line at
 * 256 characters, so the line goes into an impulse command block: {@code execute as @p at @p run qa run qa spell learn
 * realmforge:test_bolt; qa dummy}. The commands are split at {@code ;}, a leading {@code /} is optional, and each one
 * runs right after this command, in order, with its own feedback.
 */
public final class RunCommands {
    private RunCommands() {
    }

    static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("run")
                .then(Commands.argument("commands", StringArgumentType.greedyString()).executes(RunCommands::run));
    }

    /** The commands of {@code line}: split at {@code ;}, trimmed, empty ones left out. */
    static List<String> split(String line) {
        return Arrays.stream(line.split(";")).map(String::strip).filter(command -> !command.isEmpty()).toList();
    }

    private static int run(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        List<String> commands = split(StringArgumentType.getString(context, "commands"));
        source.sendSuccess(() -> Component.literal("Running " + commands.size() + " commands"), false);
        // Inside a command, the dispatcher queues each one to run after this command returns, in this order.
        commands.forEach(command -> source.getServer().getCommands().performPrefixedCommand(source, command));
        return commands.size();
    }
}
