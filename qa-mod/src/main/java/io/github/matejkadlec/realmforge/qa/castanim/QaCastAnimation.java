package io.github.matejkadlec.realmforge.qa.castanim;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * Spike 4 prototype (ADR 0020): {@code /qa cast <ticks>} makes the player cast for that long. The caster's client and
 * every tracking client play the core mod's cast animation, which real cast-time and channelled spells play too
 * (RLM-435), and melee swings, vanilla and Better Combat, are blocked until the cast ends or {@code /qa cast stop}.
 * Dev-only: real casting is the core mod's {@code spell} feature (C3a, C3b); a spell's own animation comes with C6.
 */
public final class QaCastAnimation {
    private static final int MAX_TICKS = 6000;

    private QaCastAnimation() {
    }

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(QaCastAnimation::registerPayloads);
        NeoForge.EVENT_BUS.addListener(QaCastAnimation::registerCommands);
        NeoForge.EVENT_BUS.addListener(QaCasts::onServerTick);
        NeoForge.EVENT_BUS.addListener(QaCasts::onAttack);
        NeoForge.EVENT_BUS.addListener(QaCasts::onStartTracking);
        NeoForge.EVENT_BUS.addListener(QaCasts::onDeath);
        NeoForge.EVENT_BUS.addListener(QaCasts::onLoggedIn);
        NeoForge.EVENT_BUS.addListener(QaCasts::onLoggedOut);
        NeoForge.EVENT_BUS.addListener(QaCasts::onServerStopped);
    }

    /**
     * Client-bound and without a handler here (ADR 0027): the client registers it ({@code qa/client/castanim}), and a
     * dedicated server only sends the payload.
     */
    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(QaCastPayload.TYPE, QaCastPayload.STREAM_CODEC);
    }

    private static void registerCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("qa")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("cast")
                        .then(Commands.literal("stop").executes(context -> {
                            QaCasts.stop(context.getSource().getPlayerOrException());
                            return 1;
                        }))
                        .then(Commands.argument("ticks", IntegerArgumentType.integer(1, MAX_TICKS)).executes(context -> {
                            int ticks = IntegerArgumentType.getInteger(context, "ticks");
                            QaCasts.start(context.getSource().getPlayerOrException(), ticks);
                            context.getSource().sendSuccess(() -> Component.literal("Casting for " + ticks + " ticks"), false);
                            return 1;
                        }))));
    }
}
