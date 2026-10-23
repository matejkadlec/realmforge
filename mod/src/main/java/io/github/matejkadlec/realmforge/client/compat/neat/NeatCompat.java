package io.github.matejkadlec.realmforge.client.compat.neat;

import com.google.common.reflect.TypeToken;
import io.github.matejkadlec.realmforge.client.compat.MobLevelLabel;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.renderstate.RegisterRenderStateModifiersEvent;
import vazkii.neat.NeatRenderState;

/**
 * Neat's card above a mob shows the mob's level before its name (RLM-338, {@link MobLevelLabel}). Neat has no API for
 * it, but a usable hook: its mixin on {@code LivingEntityRenderer#extractRenderState} stores the card's name line in
 * the render state ({@code NeatRenderState}, which it mixes into {@code LivingEntityRenderState}), and NeoForge runs
 * the render state modifiers after {@code extractRenderState} ({@code EntityRenderer#createRenderState}), so ours
 * replaces that line. A card without a name line (Neat's {@code show_entity_name} off) and an entity without a level
 * keep Neat's own. Runs only while Neat is loaded (architecture 19.4); the core mod compiles against the pack's Neat
 * build ({@code mod/build.gradle}), so a Neat update that changes the hook fails the build, not the game.
 */
public final class NeatCompat {
    private static final String MOD_ID = "neat";

    private NeatCompat() {
    }

    public static void register(IEventBus modBus) {
        if (ModList.get().isLoaded(MOD_ID)) {
            modBus.addListener(RegisterRenderStateModifiersEvent.class, NeatCompat::onRegisterModifiers);
        }
    }

    /**
     * Neat's card for a renderer that is not a {@code LivingEntityRenderer} (our custom mobs, {@link NeatCard}), while
     * Neat is loaded.
     */
    public static Optional<NeatCard> card(EntityRendererProvider.Context context) {
        return ModList.get().isLoaded(MOD_ID) ? Optional.of(new NeatCard(context)) : Optional.empty();
    }

    private static void onRegisterModifiers(RegisterRenderStateModifiersEvent event) {
        event.registerEntityModifier(new TypeToken<LivingEntityRenderer<LivingEntity, LivingEntityRenderState, ?>>() {
        }, NeatCompat::showLevel);
    }

    static void showLevel(LivingEntity entity, LivingEntityRenderState state) {
        if (state instanceof NeatRenderState neat && neat.neat$nameToRender() != null) {
            MobLevelLabel.forMob(entity, Minecraft.getInstance().player)
                    .ifPresent(line -> neat.neat$setNameToRender(line.getVisualOrderText()));
        }
    }
}
