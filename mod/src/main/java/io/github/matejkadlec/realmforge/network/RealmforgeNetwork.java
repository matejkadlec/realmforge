package io.github.matejkadlec.realmforge.network;

import io.github.matejkadlec.realmforge.character.CharacterCreation;
import io.github.matejkadlec.realmforge.character.StatPoints;
import io.github.matejkadlec.realmforge.character.net.CreateCharacterPayload;
import io.github.matejkadlec.realmforge.character.net.SpendStatPointPayload;
import io.github.matejkadlec.realmforge.combat.net.CombatTextPayload;
import io.github.matejkadlec.realmforge.combat.net.HitDirectionPayload;
import io.github.matejkadlec.realmforge.spell.ShowTarget;
import io.github.matejkadlec.realmforge.spell.SpellBar;
import io.github.matejkadlec.realmforge.spell.Spells;
import io.github.matejkadlec.realmforge.spell.net.CastEndedPayload;
import io.github.matejkadlec.realmforge.spell.net.CastRefusedPayload;
import io.github.matejkadlec.realmforge.spell.net.CastSpellPayload;
import io.github.matejkadlec.realmforge.spell.net.CastStartedPayload;
import io.github.matejkadlec.realmforge.spell.net.CastTargetPayload;
import io.github.matejkadlec.realmforge.spell.net.SetSpellBarSlotPayload;
import io.github.matejkadlec.realmforge.spell.net.ShowTargetPayload;
import io.github.matejkadlec.realmforge.spell.net.ShownTargetPayload;
import io.github.matejkadlec.realmforge.world.NightVision;
import io.github.matejkadlec.realmforge.world.net.RealmRefusedPayload;
import io.github.matejkadlec.realmforge.world.net.ToggleNightVisionPayload;
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
        registrar.playToServer(ToggleNightVisionPayload.TYPE, ToggleNightVisionPayload.STREAM_CODEC,
                (payload, context) -> {
                    if (context.player() instanceof ServerPlayer player) {
                        NightVision.toggle(player);
                    }
                });
        registrar.playToServer(CreateCharacterPayload.TYPE, CreateCharacterPayload.STREAM_CODEC,
                (payload, context) -> {
                    if (context.player() instanceof ServerPlayer player) {
                        CharacterCreation.create(player, payload.race(), payload.playerClass());
                    }
                });
        registrar.playToServer(SpendStatPointPayload.TYPE, SpendStatPointPayload.STREAM_CODEC,
                (payload, context) -> {
                    if (context.player() instanceof ServerPlayer player) {
                        StatPoints.spend(player, payload.stat());
                    }
                });
        registrar.playToServer(CastSpellPayload.TYPE, CastSpellPayload.STREAM_CODEC,
                (payload, context) -> {
                    if (context.player() instanceof ServerPlayer player) {
                        SpellBar.cast(player, payload.spell(), payload.target(), payload.replaceCast());
                    }
                });
        registrar.playToServer(SetSpellBarSlotPayload.TYPE, SetSpellBarSlotPayload.STREAM_CODEC,
                (payload, context) -> {
                    if (context.player() instanceof ServerPlayer player) {
                        Spells.placeOnBar(player, payload.slot(), payload.spell());
                    }
                });
        registrar.playToServer(ShowTargetPayload.TYPE, ShowTargetPayload.STREAM_CODEC,
                (payload, context) -> {
                    if (context.player() instanceof ServerPlayer player) {
                        ShowTarget.setHeld(player, payload.held());
                    }
                });
        registrar.playToClient(CombatTextPayload.TYPE, CombatTextPayload.STREAM_CODEC);
        registrar.playToClient(HitDirectionPayload.TYPE, HitDirectionPayload.STREAM_CODEC);
        registrar.playToClient(CastStartedPayload.TYPE, CastStartedPayload.STREAM_CODEC);
        registrar.playToClient(CastEndedPayload.TYPE, CastEndedPayload.STREAM_CODEC);
        registrar.playToClient(CastRefusedPayload.TYPE, CastRefusedPayload.STREAM_CODEC);
        registrar.playToClient(CastTargetPayload.TYPE, CastTargetPayload.STREAM_CODEC);
        registrar.playToClient(ShownTargetPayload.TYPE, ShownTargetPayload.STREAM_CODEC);
        registrar.playToClient(RealmRefusedPayload.TYPE, RealmRefusedPayload.STREAM_CODEC);
    }
}
