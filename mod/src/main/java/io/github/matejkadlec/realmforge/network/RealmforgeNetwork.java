package io.github.matejkadlec.realmforge.network;

import java.util.Objects;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * The one place that registers payloads (architecture 17). The payload version is the mod's own version, so a client
 * whose Realmforge version differs from the server's is refused when it connects, whatever changed on the wire.
 */
public final class RealmforgeNetwork {
    private static volatile ClientboundPayloadHandler clientHandler;

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
        // (the registrar's default). A client-bound handler is `(payload, context) -> clientHandler().onX(payload,
        // context)`; a server-bound one hands the intent to the feature's system, which validates every field.
    }

    /** Installs the client side's handler; {@code RealmforgeClient} calls this once at startup. */
    public static void installClientHandler(ClientboundPayloadHandler handler) {
        Objects.requireNonNull(handler, "handler");
        synchronized (RealmforgeNetwork.class) {
            if (clientHandler != null) {
                throw new IllegalStateException("The client payload handler is already installed");
            }
            clientHandler = handler;
        }
    }

    /** The installed client handler; only client-bound payload handlers call this, and only on a client. */
    static ClientboundPayloadHandler clientHandler() {
        ClientboundPayloadHandler handler = clientHandler;
        if (handler == null) {
            throw new IllegalStateException("No client payload handler installed: client-bound payload on a server?");
        }
        return handler;
    }
}
