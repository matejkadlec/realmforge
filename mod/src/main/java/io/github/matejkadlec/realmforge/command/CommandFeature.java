package io.github.matejkadlec.realmforge.command;

import net.minecraft.commands.Commands;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * The {@code /realmforge} operator commands and the player commands ({@code /instance}); developer commands live in
 * the QA mod.
 */
public final class CommandFeature {
    private CommandFeature() {
    }

    /** Registers the feature's registries and event listeners. Called once from {@code Realmforge}. */
    public static void register(IEventBus modBus) {
        NeoForge.EVENT_BUS.addListener(RegisterCommandsEvent.class, CommandFeature::registerCommands);
    }

    /**
     * {@code /realmforge}, for operators (permission level 2): {@code xp} so far; and {@code /instance}, for every
     * player.
     */
    private static void registerCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("realmforge")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(XpCommand.build()));
        event.getDispatcher().register(InstanceCommand.build());
    }
}
