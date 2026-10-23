package io.github.matejkadlec.realmforge.client.render;

import io.github.matejkadlec.realmforge.spell.SpellEntities;
import net.minecraft.client.renderer.entity.NoopRenderer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/**
 * The renderers of the spell entities (architecture 11.3, 18): a spell projectile with an appearance draws it
 * ({@link SpellProjectileRenderer}); one without draws a particle trail, and a spell area a ring of particles, from
 * their own client tick, until C6 (RLM-29) gives spells their visuals.
 */
public final class SpellEntityRenderers {
    private SpellEntityRenderers() {
    }

    public static void register(IEventBus modBus) {
        modBus.addListener(EntityRenderersEvent.RegisterRenderers.class, event -> {
            event.registerEntityRenderer(SpellEntities.PROJECTILE.get(), SpellProjectileRenderer::new);
            event.registerEntityRenderer(SpellEntities.AREA.get(), NoopRenderer::new);
        });
    }
}
