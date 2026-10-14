package io.github.matejkadlec.realmforge.qa.command;

import net.minecraft.commands.Commands;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * The {@code /qa} developer commands (operators only): {@code /qa instance} for instances, {@code /qa state} for the
 * player state attachments, {@code /qa stats} and {@code /qa level} for stats and levels, {@code /qa dummy} for the
 * training dummy. Brigadier merges every registration of the {@code /qa} literal, so other QA tools register their own
 * subtree under it (for example {@code /qa cast}).
 */
public final class QaCommands {
    private QaCommands() {
    }

    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("qa")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(InstanceCommands.build())
                .then(StateCommands.build())
                .then(StatsCommands.build())
                .then(LevelCommands.build())
                .then(DummyCommands.build()));
    }
}
