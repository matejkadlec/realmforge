package io.github.matejkadlec.realmforge.client.render;

import com.geckolib.model.DefaultedEntityGeoModel;
import com.geckolib.renderer.GeoEntityRenderer;
import com.geckolib.renderer.base.GeoRenderState;
import io.github.matejkadlec.realmforge.client.compat.neat.NeatCard;
import io.github.matejkadlec.realmforge.client.compat.neat.NeatCompat;
import io.github.matejkadlec.realmforge.generated.GeneratedMobs;
import io.github.matejkadlec.realmforge.mob.custom.CustomMob;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
/**
 * The renderers of the custom mobs (architecture 14.2, 18): GeckoLib's, with the model, animations and texture of the
 * mob's id ({@code assets/realmforge/geckolib/models/entity/<id>.geo.json}, {@code .../animations/entity/},
 * {@code textures/entity/<id>.png}, a variant's {@code <id>_<variant>.png}), and Neat's card above it as above every
 * vanilla mob ({@link NeatCard}). The animations turn the model's head to where the mob looks. What forms in a casting
 * mob's raised hands is drawn by {@link SpellForming}, as every caster's is.
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
            super(context, new Model<>(BuiltInRegistries.ENTITY_TYPE.getKey(type)));
            neat = NeatCompat.card(context);
        }

        @Override
        public LivingEntityRenderState createRenderState(T animatable, @Nullable Void relatedObject) {
            return new State();
        }

        @Override
        public void extractRenderState(T entity, LivingEntityRenderState state, float partialTick) {
            // Neat's card first: GeckoLib's extraction then writes the state the model renders from.
            neat.ifPresent(card -> card.extract(entity, state, partialTick));
            super.extractRenderState(entity, state, partialTick);
            if (state instanceof State custom) {
                Identifier type = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
                custom.texture = entity.variant()
                        .map(variant -> type.withPath("textures/entity/" + type.getPath() + "_" + variant.id() + ".png"))
                        .orElse(null);
            }
        }
    }

    /** The model of a custom mob, its texture the variant's when it has one. */
    private static final class Model<T extends CustomMob> extends DefaultedEntityGeoModel<T> {
        Model(Identifier type) {
            super(type);
        }

        @Override
        public Identifier getTextureResource(GeoRenderState renderState) {
            return renderState instanceof State state && state.texture != null ? state.texture
                    : super.getTextureResource(renderState);
        }
    }

    /**
     * What a frame draws of a custom mob beyond vanilla's living entity: its variant's texture. Not final: GeckoLib
     * makes every living entity's render state a {@code GeoRenderState} at runtime, which its model receives this as.
     */
    private static class State extends LivingEntityRenderState {
        @Nullable Identifier texture;
    }
}
