package io.github.matejkadlec.realmforge.network;

import io.github.matejkadlec.realmforge.combat.net.CombatTextPayload;
import io.github.matejkadlec.realmforge.combat.net.HitDirectionPayload;
import io.github.matejkadlec.realmforge.world.DarkVision;
import io.github.matejkadlec.realmforge.world.net.ToggleDarkVisionPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * The one place that registers payloads (architecture 17). The payload version is the mod's own version, so a client
 * whose Realmforge version differs from the server's is refused when it connects, whatever changed on the wire.
 */
public final class RealmforgeNetwork {
    private RealmforgeNetwork() {
    }

    /** Called once from {@code Realmforge}. */
    public static void register(IEventBus modBus, ModContainer container) {
        String version = version(container);
        modBus.addListener(RegisterPayloadHandlersEvent.class, event -> registerPayloads(event.registrar(version)));
    }

    /** The payload version: the mod version from {@code neoforge.mods.toml}. */
    public static String version(ModContainer container) {
        return container.getModInfo().getVersion().toString();
    }

    private static void registerPayloads(PayloadRegistrar registrar) {
        // Each feature's payloads from its net/ package go here (architecture 17.3). Handlers run on the main thread
        // (the registrar's default). A client-bound payload is registered without a handler, `playToClient(TYPE,
        // STREAM_CODEC)`: its handler is client code, in client/network/ClientPayloadHandlers (ADR 0027). A
        // server-bound handler hands the intent to the feature's system, which validates every field.
        registrar.playToServer(ToggleDarkVisionPayload.TYPE, ToggleDarkVisionPayload.STREAM_CODEC,
                (payload, context) -> {
                    if (context.player() instanceof ServerPlayer player) {
                        DarkVision.toggle(player);
                    }
                });
        registrar.playToClient(CombatTextPayload.TYPE, CombatTextPayload.STREAM_CODEC);
        registrar.playToClient(HitDirectionPayload.TYPE, HitDirectionPayload.STREAM_CODEC);
    }
}
