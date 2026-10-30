package io.github.matejkadlec.realmforge.client;

import io.github.matejkadlec.realmforge.Realmforge;
import io.github.matejkadlec.realmforge.client.animation.CastAnimation;
import io.github.matejkadlec.realmforge.client.compat.apotheosis.PotionCharmTooltip;
import io.github.matejkadlec.realmforge.client.compat.bettercombat.TwoHandedWeaponsClient;
import io.github.matejkadlec.realmforge.client.compat.neat.NeatCompat;
import io.github.matejkadlec.realmforge.client.compat.pickupnotifier.PickUpListPosition;
import io.github.matejkadlec.realmforge.client.hud.CastBarHud;
import io.github.matejkadlec.realmforge.client.hud.CombatTextHud;
import io.github.matejkadlec.realmforge.client.hud.HitDirectionHud;
import io.github.matejkadlec.realmforge.client.hud.InstanceArrivalTitle;
import io.github.matejkadlec.realmforge.client.hud.MagicShieldHud;
import io.github.matejkadlec.realmforge.client.hud.RealmRefusalHud;
import io.github.matejkadlec.realmforge.client.hud.SpellBarHud;
import io.github.matejkadlec.realmforge.client.hud.XpBarHud;
import io.github.matejkadlec.realmforge.client.input.AutoShootInput;
import io.github.matejkadlec.realmforge.client.input.CharacterSheetKey;
import io.github.matejkadlec.realmforge.client.input.NightVisionKey;
import io.github.matejkadlec.realmforge.client.input.SpellKeys;
import io.github.matejkadlec.realmforge.client.network.ClientPayloadHandlers;
import io.github.matejkadlec.realmforge.client.render.CustomMobRenderers;
import io.github.matejkadlec.realmforge.client.render.RealmPortalColors;
import io.github.matejkadlec.realmforge.client.render.ScrollCharge;
import io.github.matejkadlec.realmforge.client.render.SpellEntityRenderers;
import io.github.matejkadlec.realmforge.client.screen.CharacterCreationScreen;
import io.github.matejkadlec.realmforge.client.spell.ClientCasts;
import io.github.matejkadlec.realmforge.client.spell.SpellBarInput;
import io.github.matejkadlec.realmforge.client.spell.TargetCircle;
import io.github.matejkadlec.realmforge.client.tooltip.RacialPassiveTooltip;
import io.github.matejkadlec.realmforge.client.tooltip.RightAlignedLines;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

/**
 * Client entry point of the core mod. FML constructs it only on a physical client, after or beside {@link Realmforge},
 * so everything client-only starts here (architecture 18). Only classes under {@code client/} may use client classes;
 * {@code tools/tests/test_client_isolation.py} enforces it.
 */
@Mod(value = Realmforge.MOD_ID, dist = Dist.CLIENT)
public final class RealmforgeClient {
    public RealmforgeClient(IEventBus modBus, ModContainer container) {
        ClientConfig.register(container);
        ClientPayloadHandlers.register(modBus);
        // Client registration events (HUD layers, key mappings, renderers, screens) are added to modBus here.
        NightVisionKey.register(modBus);
        CharacterSheetKey.register(modBus);
        SpellKeys.register(modBus);
        CharacterCreationScreen.register(modBus);
        CombatTextHud.register(modBus);
        HitDirectionHud.register(modBus);
        RealmRefusalHud.register(modBus);
        MagicShieldHud.register(modBus);
        XpBarHud.register(modBus);
        SpellBarHud.register(modBus);
        CastBarHud.register(modBus);
        RightAlignedLines.register(modBus);
        RacialPassiveTooltip.register();
        // Optional integrations (architecture 19.4); Jade finds its plugin itself (compat/jade/JadeCompat).
        NeatCompat.register(modBus);
        TwoHandedWeaponsClient.register();
        PickUpListPosition.register();
        PotionCharmTooltip.register();
        ClientCasts.register();
        CastAnimation.register(modBus);
        SpellBarInput.register();
        AutoShootInput.register();
        TargetCircle.register();
        SpellEntityRenderers.register(modBus);
        CustomMobRenderers.register(modBus);
        RealmPortalColors.register(modBus);
        ScrollCharge.register(modBus);
        InstanceArrivalTitle.register();
    }
}
