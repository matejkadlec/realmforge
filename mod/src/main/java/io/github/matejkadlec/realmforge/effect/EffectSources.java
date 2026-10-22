package io.github.matejkadlec.realmforge.effect;

import com.mojang.serialization.Codec;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.effect.MobEffect;

/**
 * The value of the {@code realmforge:effect_sources} attachment (architecture 7, 11.4): the source of each status
 * effect a spell applied to the holder, by effect. Saved with the holder, as its effects are, so a DoT still knows its
 * caster after a reload; never synced, as only the server reads it.
 *
 * @param sources effect to who applied it
 */
public record EffectSources(Map<ResourceKey<MobEffect>, EffectSource> sources) {
    public static final EffectSources EMPTY = new EffectSources(Map.of());
    public static final Codec<EffectSources> CODEC = Codec.unboundedMap(ResourceKey.codec(Registries.MOB_EFFECT),
            EffectSource.CODEC).xmap(EffectSources::new, EffectSources::sources);

    public EffectSources {
        sources = Map.copyOf(sources);
    }

    public Optional<EffectSource> get(ResourceKey<MobEffect> effect) {
        return Optional.ofNullable(sources.get(effect));
    }

    /** These sources with {@code effect} applied by {@code source}, replacing the one it had. */
    public EffectSources with(ResourceKey<MobEffect> effect, EffectSource source) {
        Map<ResourceKey<MobEffect>, EffectSource> changed = new HashMap<>(sources);
        changed.put(effect, source);
        return new EffectSources(changed);
    }

    /** These sources without {@code effect}'s. */
    public EffectSources without(ResourceKey<MobEffect> effect) {
        if (!sources.containsKey(effect)) {
            return this;
        }
        Map<ResourceKey<MobEffect>, EffectSource> changed = new HashMap<>(sources);
        changed.remove(effect);
        return new EffectSources(changed);
    }
}
