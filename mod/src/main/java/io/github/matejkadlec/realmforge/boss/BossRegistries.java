package io.github.matejkadlec.realmforge.boss;

import com.mojang.serialization.MapCodec;
import io.github.matejkadlec.realmforge.Realmforge;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.neoforged.neoforge.registries.DataPackRegistryEvent;
import net.neoforged.neoforge.registries.NewRegistryEvent;
import net.neoforged.neoforge.registries.RegistryBuilder;

/** Keys and type registries of the registries {@code boss} owns (architecture 5.2, 5.3, 5.4). */
public final class BossRegistries {
    /**
     * {@code realmforge:boss}: the bosses' fights from {@code content/bosses/}. It stays on the server: players see a
     * boss through its entity, its casts and its boss bar, whose title carries the phase's name.
     */
    public static final ResourceKey<Registry<Boss>> BOSS = ResourceKey.createRegistryKey(Realmforge.id("boss"));

    /** {@code realmforge:phase_trigger_type}: what enters a boss's phase ({@link PhaseTriggerTypes}). */
    public static final ResourceKey<Registry<MapCodec<? extends PhaseTrigger>>> PHASE_TRIGGER_TYPE =
            ResourceKey.createRegistryKey(Realmforge.id("phase_trigger_type"));

    /** The registry behind {@link #PHASE_TRIGGER_TYPE}, filled from Java at game start; not synced, as bosses are not. */
    public static final Registry<MapCodec<? extends PhaseTrigger>> PHASE_TRIGGER_TYPES =
            new RegistryBuilder<>(PHASE_TRIGGER_TYPE).create();

    private BossRegistries() {
    }

    static void registerTypeRegistries(NewRegistryEvent event) {
        event.register(PHASE_TRIGGER_TYPES);
    }

    static void registerDataPackRegistries(DataPackRegistryEvent.NewRegistry event) {
        event.dataPackRegistry(BOSS, Boss.CODEC);
    }
}
