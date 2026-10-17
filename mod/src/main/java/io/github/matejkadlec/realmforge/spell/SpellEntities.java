package io.github.matejkadlec.realmforge.spell;

import io.github.matejkadlec.realmforge.Realmforge;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The spell entity types (architecture 11.3): one generic projectile and one generic area, configured by the spell.
 * Registries freeze at startup, so a new type is only for genuinely new behaviour.
 */
public final class SpellEntities {
    private static final DeferredRegister.Entities TYPES = DeferredRegister.createEntities(Realmforge.MOD_ID);

    /** {@code realmforge:spell_projectile} ({@link SpellProjectile}); its hitbox comes from the spell. */
    public static final DeferredHolder<EntityType<?>, EntityType<SpellProjectile>> PROJECTILE =
            TYPES.registerEntityType("spell_projectile", SpellProjectile::new, MobCategory.MISC, builder -> builder
                    .noLootTable().noSummon().sized(0.5F, 0.5F).clientTrackingRange(4).updateInterval(10));

    /** {@code realmforge:spell_area} ({@link SpellArea}); its size comes from the spell. */
    public static final DeferredHolder<EntityType<?>, EntityType<SpellArea>> AREA =
            TYPES.registerEntityType("spell_area", SpellArea::new, MobCategory.MISC, builder -> builder
                    .noLootTable().noSummon().fireImmune().sized(2.0F, 0.5F).clientTrackingRange(10)
                    .updateInterval(Integer.MAX_VALUE));

    private SpellEntities() {
    }

    static void register(IEventBus modBus) {
        TYPES.register(modBus);
    }
}
