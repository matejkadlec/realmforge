package io.github.matejkadlec.realmforge.combat;

import io.github.matejkadlec.realmforge.Realmforge;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.neoforged.neoforge.registries.DataPackRegistryEvent;

/** Keys of the registries {@code combat} owns (architecture 5.2, 5.4). */
public final class CombatRegistries {
    /** {@code realmforge:spell_school}: the spell schools from {@code content/spell_schools/}; synced. */
    public static final ResourceKey<Registry<SpellSchool>> SPELL_SCHOOL =
            ResourceKey.createRegistryKey(Realmforge.id("spell_school"));

    private CombatRegistries() {
    }

    static void registerDataPackRegistries(DataPackRegistryEvent.NewRegistry event) {
        event.dataPackRegistry(SPELL_SCHOOL, SpellSchool.CODEC, SpellSchool.CODEC);
    }
}
