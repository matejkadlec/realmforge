package io.github.matejkadlec.realmforge.qa.client.boss;

import io.github.matejkadlec.realmforge.qa.boss.QaBosses;
import net.minecraft.client.renderer.entity.ZombieRenderer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** Renders the QA bosses with vanilla's renderers: the test boss as a zombie, its size from its scale attribute. */
public final class QaBossRenderers {
    private QaBossRenderers() {
    }

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(EntityRenderersEvent.RegisterRenderers.class,
                event -> event.registerEntityRenderer(QaBosses.TEST_BOSS.get(), ZombieRenderer::new));
    }
}
