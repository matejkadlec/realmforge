package io.github.matejkadlec.realmforge.qa.boss;

import io.github.matejkadlec.realmforge.qa.RealmforgeQa;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The QA mod's bosses, for tests and playtests only: {@code /summon realmforge_qa:<id>} in the dev client. */
public final class QaBosses {
    private static final DeferredRegister.Entities ENTITY_TYPES = DeferredRegister.createEntities(RealmforgeQa.MOD_ID);

    /** {@link TestBoss}: a zombie's size (scaled up by its attribute) and a vanilla boss's tracking range. */
    public static final DeferredHolder<EntityType<?>, EntityType<TestBoss>> TEST_BOSS =
            ENTITY_TYPES.registerEntityType("test_boss", TestBoss::new, MobCategory.MONSTER,
                    builder -> builder.sized(0.6F, 1.95F).eyeHeight(1.74F).clientTrackingRange(10));

    private QaBosses() {
    }

    public static void register(IEventBus modBus) {
        ENTITY_TYPES.register(modBus);
        modBus.addListener(EntityAttributeCreationEvent.class,
                event -> event.put(TEST_BOSS.get(), TestBoss.attributes().build()));
    }
}
