package io.github.matejkadlec.realmforge.client.render;

import io.github.matejkadlec.realmforge.spell.SpellEntities;
import net.minecraft.client.renderer.entity.NoopRenderer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/**
 * The renderers of the spell entities (architecture 11.3, 18): a spell projectile draws its model with its look's
 * material ({@link SpellProjectileRenderer}) and spawns its trail itself; a spell area draws a ring of particles from
 * its own client tick, until the area styles (RLM-443).
 */
public final class SpellEntityRenderers {
    private SpellEntityRenderers() {
    }

    public static void register(IEventBus modBus) {
        modBus.addListener(EntityRenderersEvent.RegisterLayerDefinitions.class,
                event -> event.registerLayerDefinition(SpellOrbs.LAYER, SpellOrbs::createLayer));
        modBus.addListener(EntityRenderersEvent.RegisterRenderers.class, event -> {
            event.registerEntityRenderer(SpellEntities.PROJECTILE.get(), SpellProjectileRenderer::new);
            event.registerEntityRenderer(SpellEntities.AREA.get(), NoopRenderer::new);
        });
    }
}
