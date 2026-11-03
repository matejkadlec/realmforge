package io.github.matejkadlec.realmforge.qa.client.combat;

import io.github.matejkadlec.realmforge.qa.combat.TrainingDummy;
import io.github.matejkadlec.realmforge.qa.combat.TrainingDummyMob;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.monster.illager.IllagerModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.state.IllagerRenderState;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/**
 * Renders the training dummy ({@link TrainingDummyMob}, RLM-528) as it looked when it was a pillager: vanilla's
 * illager model and the pillager's texture, both referenced from the game, its arms down.
 */
public final class TrainingDummyRenderer
        extends MobRenderer<TrainingDummyMob, IllagerRenderState, IllagerModel<IllagerRenderState>> {
    private static final Identifier TEXTURE = Identifier.withDefaultNamespace("textures/entity/illager/pillager.png");

    private TrainingDummyRenderer(EntityRendererProvider.Context context) {
        super(context, new IllagerModel<>(context.bakeLayer(ModelLayers.PILLAGER)), 0.5F);
    }

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(EntityRenderersEvent.RegisterRenderers.class,
                event -> event.registerEntityRenderer(TrainingDummy.ENTITY_TYPE.get(), TrainingDummyRenderer::new));
    }

    @Override
    public IllagerRenderState createRenderState() {
        return new IllagerRenderState();
    }

    @Override
    public Identifier getTextureLocation(IllagerRenderState state) {
        return TEXTURE;
    }
}
