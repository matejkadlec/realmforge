package io.github.matejkadlec.realmforge.stats;

import io.github.matejkadlec.realmforge.Realmforge;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.neoforged.neoforge.registries.DataPackRegistryEvent;

/** Keys of the registries {@code stats} owns (architecture 5.2, 5.4). */
public final class StatsRegistries {
    /** {@code realmforge:stat_rule}: what each primary stat gives, from {@code content/stat_rules/}; synced. */
    public static final ResourceKey<Registry<StatRule>> STAT_RULE =
            ResourceKey.createRegistryKey(Realmforge.id("stat_rule"));

    private StatsRegistries() {
    }

    static void registerDataPackRegistries(DataPackRegistryEvent.NewRegistry event) {
        event.dataPackRegistry(STAT_RULE, StatRule.CODEC, StatRule.CODEC);
    }
}
