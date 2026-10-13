package io.github.matejkadlec.realmforge.client.network;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;

/**
 * The one place that registers the handlers of client-bound payloads (architecture 17.1, ADR 0027). Common code
 * registers each such payload without a handler ({@code RealmforgeNetwork}), and NeoForge stops the client at startup
 * when one has no handler here. A handler runs on the main thread, the event's default, and hands its payload to the
 * HUD or screen that shows it.
 */
public final class ClientPayloadHandlers {
    private ClientPayloadHandlers() {
    }

    /** Called once from {@code RealmforgeClient}. */
    public static void register(IEventBus modBus) {
        modBus.addListener(RegisterClientPayloadHandlersEvent.class, ClientPayloadHandlers::registerHandlers);
    }

    private static void registerHandlers(RegisterClientPayloadHandlersEvent event) {
        // One `event.register(XPayload.TYPE, (payload, context) -> ...)` per client-bound payload; the first ones
        // arrive with the casting HUD (architecture 17.3).
    }
}
