package io.github.matejkadlec.realmforge.network;

/**
 * Handles every client-bound payload, one method per payload (architecture 17.1). Common code registers all payloads,
 * because NeoForge 21.1 has no client-side registration event, so the registered handlers only call this interface;
 * {@code client/network/ClientPayloadHandler} implements it and {@code RealmforgeClient} installs it. A dedicated
 * server never installs an implementation and never receives client-bound payloads.
 *
 * <p>A method takes the payload and the {@code IPayloadContext}; the first ones arrive with the casting HUD
 * (architecture 17.3).
 */
public interface ClientboundPayloadHandler {
}
