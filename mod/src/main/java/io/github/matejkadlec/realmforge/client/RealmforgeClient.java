package io.github.matejkadlec.realmforge.client;

import io.github.matejkadlec.realmforge.Realmforge;
import io.github.matejkadlec.realmforge.client.hud.CombatTextHud;
import io.github.matejkadlec.realmforge.client.hud.HitDirectionHud;
import io.github.matejkadlec.realmforge.client.input.CharacterSheetKey;
import io.github.matejkadlec.realmforge.client.input.DarkVisionKey;
import io.github.matejkadlec.realmforge.client.network.ClientPayloadHandlers;
import io.github.matejkadlec.realmforge.client.screen.CharacterCreationScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

/**
 * Client entry point of the core mod. FML constructs it only on a physical client, after or beside {@link Realmforge},
 * so everything client-only starts here (architecture 18). Only classes under {@code client/} may use client classes;
 * {@code tools/tests/test_client_isolation.py} enforces it.
 */
@Mod(value = Realmforge.MOD_ID, dist = Dist.CLIENT)
public final class RealmforgeClient {
    public RealmforgeClient(IEventBus modBus) {
        ClientPayloadHandlers.register(modBus);
        // Client registration events (HUD layers, key mappings, renderers, screens) are added to modBus here.
        DarkVisionKey.register(modBus);
        CharacterSheetKey.register(modBus);
        CharacterCreationScreen.register(modBus);
        CombatTextHud.register(modBus);
        HitDirectionHud.register(modBus);
    }
}
