package io.github.matejkadlec.realmforge.client.render;

import com.geckolib.renderer.GeoEntityRenderer;
import io.github.matejkadlec.realmforge.client.compat.neat.NeatCard;
import io.github.matejkadlec.realmforge.client.compat.neat.NeatCompat;
import io.github.matejkadlec.realmforge.generated.GeneratedMobs;
import io.github.matejkadlec.realmforge.mob.custom.CustomMob;
import java.util.Optional;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.EntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/**
 * The renderers of the custom mobs (architecture 14.2, 18): GeckoLib's, with the model, animations and texture of the
 * mob's id ({@code assets/realmforge/geckolib/models/entity/<id>.geo.json}, {@code .../animations/entity/},
 * {@code textures/entity/<id>.png}), and Neat's card above it as above every vanilla mob ({@link NeatCard}). The
 * animations turn the model's head to where the mob looks.
 */
public final class CustomMobRenderers {
    private CustomMobRenderers() {
    }

    public static void register(IEventBus modBus) {
        modBus.addListener(EntityRenderersEvent.RegisterRenderers.class, event -> {
            for (var mob : GeneratedMobs.ALL) {
                register(event, mob.get());
            }
        });
    }

    private static <T extends CustomMob> void register(EntityRenderersEvent.RegisterRenderers event, EntityType<T> type) {
        event.registerEntityRenderer(type, context -> new Renderer<>(context, type));
    }

    private static final class Renderer<T extends CustomMob> extends GeoEntityRenderer<T, LivingEntityRenderState> {
        private final Optional<NeatCard> neat;

        Renderer(EntityRendererProvider.Context context, EntityType<? extends T> type) {
            super(context, type);
            neat = NeatCompat.card(context);
        }

        @Override
        public void extractRenderState(T entity, LivingEntityRenderState state, float partialTick) {
            // Neat's card first: GeckoLib's extraction then writes the state the model renders from.
            neat.ifPresent(card -> card.extract(entity, state, partialTick));
            super.extractRenderState(entity, state, partialTick);
        }
    }
}
