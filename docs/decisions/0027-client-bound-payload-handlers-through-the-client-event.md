# 0027. Client-bound payload handlers through NeoForge's client event

Date: 2026-10-13

Status: Accepted (amends decision 9 of [0014](0014-core-mod-architecture.md))

## Context

[0014](0014-core-mod-architecture.md), decision 9, has client-bound payloads handled through an interface declared
in `network/` and implemented in `client/`. NeoForge 21.1 had no client-side event for payload handlers, so common
code had to register every payload together with its handler. The handler was a lambda that called an
implementation the client installed at startup (`ClientboundPayloadHandler`,
`RealmforgeNetwork.installClientHandler`, with checks for a missing or second implementation). The QA mod's test
cast (`realmforge_qa:cast`, [0020](0020-cast-animations-and-melee-while-casting.md)) copied the pattern with its own
interface and a no-op default.

NeoForge 26.1.2.116 ([0026](0026-platform-minecraft-26-1-2-neoforge.md)) adds both halves of a direct route,
checked in its sources (RLM-129):

- `PayloadRegistrar#playToClient(type, codec)` registers a client-bound payload without a handler.
- `net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent`, a client-only mod bus event,
  registers the handler for such a payload. NeoForge registers its own client-bound handlers this way
  (`net.neoforged.neoforge.client.network.ClientPayloadHandler`).
- `ClientNetworkRegistry#setup` fires the event and then stops the client with "Some clientbound payloads are missing
  client-side handlers" when a client-bound payload still has none. A dedicated server never fires the event and
  never needs the handler.

The core mod has no client-bound payload yet; the first ones come with casting (C3a, C3b:
[architecture 17.3](../architecture.md#173-first-payloads-m1)). The switch costs least now.

## Decision

1. **Common code registers a client-bound payload without a handler:** `registrar.playToClient(TYPE, STREAM_CODEC)`
   at the mod's one registration point (`network/RealmforgeNetwork`; in the QA mod, `castanim/QaCastAnimation`). The
   type and the codec stay in the feature's `net/` package.
2. **The client registers its handler** in a `RegisterClientPayloadHandlersEvent` listener under `client/network/`
   (`client/network/ClientPayloadHandlers`, added to the mod bus by `RealmforgeClient`; in the QA mod,
   `client/castanim/QaCastAnimationClient`), on the main thread, the event's default.
3. **The interface route goes:** `ClientboundPayloadHandler`, `client/network/ClientPayloadHandler`,
   `RealmforgeNetwork.installClientHandler`, and in the QA mod `QaCastClientHandler` and
   `QaCastAnimation.installClientHandler`.
4. The rest of 0014's decision 9 stands: one registration point, versioned by the mod version, and client-to-server
   payloads are intents the server validates.

## Consequences

- Client isolation holds by construction: a dedicated server loads the payload's type and codec and nothing else,
  and no common class keeps a field for a client handler. The source check of 0014, decision 2, still guards the
  imports.
- A client-bound payload without a client handler stops the client at startup, in the dev client and the pack's
  client boot. Before, the core mod failed on the first such packet, and the QA mod ignored it through its no-op
  default.
- The codecs stay common, so GameTests on the server still test them: `QaCastGameTests` decodes the cast payload
  through the codec NeoForge registered for it.
- A future payload that both sides handle registers its server handler in common code
  (`playBidirectional(type, codec, serverHandler)`) and its client handler through the event.
- [Architecture 17.1](../architecture.md#171-rules) and the networking pitfall in `CLAUDE.md` describe the new route.
