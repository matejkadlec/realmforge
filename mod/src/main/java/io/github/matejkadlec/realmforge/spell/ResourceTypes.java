package io.github.matejkadlec.realmforge.spell;

import io.github.matejkadlec.realmforge.Realmforge;
import io.github.matejkadlec.realmforge.combat.DamagePipeline;
import io.github.matejkadlec.realmforge.generated.GeneratedStats;
import io.github.matejkadlec.realmforge.generated.GeneratedTexts;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The resource types (architecture 11.5). The pipeline knows the same ids ({@code RESOURCE_TYPES} in
 * {@code tools/realmforge_tools/schema/character.py}), so a class can name only these.
 */
public final class ResourceTypes {
    /** The size of every rage pool, as in WoW. Its gain and decay ({@link Rage}) take their numbers from the class. */
    public static final double RAGE_MAXIMUM = 100;

    private static final DeferredRegister<ResourceType> TYPES =
            DeferredRegister.create(SpellRegistries.RESOURCE_TYPES, Realmforge.MOD_ID);

    /**
     * {@code realmforge:mana}: the caster's resource. The pool is Max Mana big, starts full, and regains Mana
     * Regeneration every second (Matěj, 2026-10-14: a Mage starts with 100 and 5).
     */
    public static final DeferredHolder<ResourceType, ResourceType> MANA =
            TYPES.register("mana", () -> new ResourceType(GeneratedTexts.RESOURCE_MANA,
                    holder -> DamagePipeline.stat(holder, GeneratedStats.MAX_MANA),
                    holder -> DamagePipeline.stat(holder, GeneratedStats.MANA_REGENERATION), true));

    /**
     * {@code realmforge:rage}: the melee resource, {@value #RAGE_MAXIMUM} at most, starting empty. Melee hits dealt and
     * damage taken fill it, and out of combat it decays every second, by the holder's {@link RageRules} ({@link Rage}).
     */
    public static final DeferredHolder<ResourceType, ResourceType> RAGE =
            TYPES.register("rage", () -> new ResourceType(GeneratedTexts.RESOURCE_RAGE, holder -> RAGE_MAXIMUM,
                    Rage::regeneration, false));

    private ResourceTypes() {
    }

    static void register(IEventBus modBus) {
        TYPES.register(modBus);
    }
}
