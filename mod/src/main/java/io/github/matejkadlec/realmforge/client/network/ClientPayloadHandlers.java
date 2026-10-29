package io.github.matejkadlec.realmforge.client.network;

import io.github.matejkadlec.realmforge.client.hud.CastBarHud;
import io.github.matejkadlec.realmforge.client.hud.CombatTextHud;
import io.github.matejkadlec.realmforge.client.hud.HitDirectionHud;
import io.github.matejkadlec.realmforge.client.hud.RealmRefusalHud;
import io.github.matejkadlec.realmforge.client.spell.ClientCasts;
import io.github.matejkadlec.realmforge.client.spell.TargetCircle;
import io.github.matejkadlec.realmforge.combat.net.CombatTextPayload;
import io.github.matejkadlec.realmforge.combat.net.HitDirectionPayload;
import io.github.matejkadlec.realmforge.spell.net.CastEndedPayload;
import io.github.matejkadlec.realmforge.spell.net.CastRefusedPayload;
import io.github.matejkadlec.realmforge.spell.net.CastStartedPayload;
import io.github.matejkadlec.realmforge.spell.net.CastTargetPayload;
import io.github.matejkadlec.realmforge.spell.net.HighlightedTargetPayload;
import io.github.matejkadlec.realmforge.world.net.RealmRefusedPayload;
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
        // One `event.register(XPayload.TYPE, (payload, context) -> ...)` per client-bound payload (architecture 17.3).
        event.register(CombatTextPayload.TYPE, (payload, context) -> CombatTextHud.add(payload));
        event.register(HitDirectionPayload.TYPE, (payload, context) -> HitDirectionHud.add(payload));
        event.register(CastStartedPayload.TYPE, (payload, context) -> ClientCasts.start(payload));
        event.register(CastEndedPayload.TYPE, (payload, context) -> {
            ClientCasts.end(payload);
            CastBarHud.ended(payload);
            TargetCircle.ended(payload);
        });
        event.register(CastRefusedPayload.TYPE, (payload, context) -> CastBarHud.refused(payload));
        event.register(CastTargetPayload.TYPE, (payload, context) -> TargetCircle.set(payload));
        event.register(HighlightedTargetPayload.TYPE, (payload, context) -> TargetCircle.highlighted(payload));
        event.register(RealmRefusedPayload.TYPE, (payload, context) -> RealmRefusalHud.show(payload));
    }
}
