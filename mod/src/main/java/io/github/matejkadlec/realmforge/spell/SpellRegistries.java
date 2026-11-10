package io.github.matejkadlec.realmforge.spell;

import com.mojang.serialization.MapCodec;
import io.github.matejkadlec.realmforge.Realmforge;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.neoforged.neoforge.registries.DataPackRegistryEvent;
import net.neoforged.neoforge.registries.NewRegistryEvent;
import net.neoforged.neoforge.registries.RegistryBuilder;

/** Keys and type registries of the registries {@code spell} owns (architecture 5.2, 5.3, 5.4). */
public final class SpellRegistries {
    /** {@code realmforge:spell}: the spells from {@code content/spells/}; synced, for the spell bar and tooltips. */
    public static final ResourceKey<Registry<Spell>> SPELL = ResourceKey.createRegistryKey(Realmforge.id("spell"));

    /**
     * {@code realmforge:spell_look}: the looks of the spell visuals kit from {@code content/spell_looks/}; synced, as
     * clients draw them.
     */
    public static final ResourceKey<Registry<SpellLook>> SPELL_LOOK =
            ResourceKey.createRegistryKey(Realmforge.id("spell_look"));

    /** {@code realmforge:spell_model}: a projectile's shapes from {@code content/spell_models/}; synced. */
    public static final ResourceKey<Registry<SpellModel>> SPELL_MODEL =
            ResourceKey.createRegistryKey(Realmforge.id("spell_model"));

    /**
     * {@code realmforge:spell_trajectory}: how a projectile flies, from {@code content/spell_trajectories/}; synced,
     * as clients compute the path the server flies.
     */
    public static final ResourceKey<Registry<SpellTrajectory>> SPELL_TRAJECTORY =
            ResourceKey.createRegistryKey(Realmforge.id("spell_trajectory"));

    /** {@code realmforge:resource_type}: the resources a class's spells cost ({@link ResourceTypes}). */
    public static final ResourceKey<Registry<ResourceType>> RESOURCE_TYPE =
            ResourceKey.createRegistryKey(Realmforge.id("resource_type"));

    /** {@code realmforge:spell_effect_type}: what a spell effect does ({@link SpellEffectTypes}). */
    public static final ResourceKey<Registry<MapCodec<? extends SpellEffect>>> EFFECT_TYPE =
            ResourceKey.createRegistryKey(Realmforge.id("spell_effect_type"));

    /** {@code realmforge:targeting_type}: who a spell can target ({@link TargetingTypes}). */
    public static final ResourceKey<Registry<MapCodec<? extends SpellTargeting>>> TARGETING_TYPE =
            ResourceKey.createRegistryKey(Realmforge.id("targeting_type"));

    /**
     * The registry behind {@link #RESOURCE_TYPE}, filled from Java at game start. Synced, as the synced class registry
     * names its entries.
     */
    public static final Registry<ResourceType> RESOURCE_TYPES = new RegistryBuilder<>(RESOURCE_TYPE).sync(true).create();

    /** The registry behind {@link #EFFECT_TYPE}, filled from Java at game start; synced, as spells are. */
    public static final Registry<MapCodec<? extends SpellEffect>> EFFECT_TYPES =
            new RegistryBuilder<>(EFFECT_TYPE).sync(true).create();

    /** The registry behind {@link #TARGETING_TYPE}, filled from Java at game start; synced, as spells are. */
    public static final Registry<MapCodec<? extends SpellTargeting>> TARGETING_TYPES =
            new RegistryBuilder<>(TARGETING_TYPE).sync(true).create();

    private SpellRegistries() {
    }

    static void registerTypeRegistries(NewRegistryEvent event) {
        event.register(RESOURCE_TYPES);
        event.register(EFFECT_TYPES);
        event.register(TARGETING_TYPES);
    }

    static void registerDataPackRegistries(DataPackRegistryEvent.NewRegistry event) {
        event.dataPackRegistry(SPELL_LOOK, SpellLook.CODEC, SpellLook.CODEC);
        event.dataPackRegistry(SPELL_MODEL, SpellModel.CODEC, SpellModel.CODEC);
        event.dataPackRegistry(SPELL_TRAJECTORY, SpellTrajectory.CODEC, SpellTrajectory.CODEC);
        event.dataPackRegistry(SPELL, Spell.CODEC, Spell.CODEC);
    }
}
