package io.github.matejkadlec.realmforge.spell;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;

/**
 * One thing a spell does, an entry of the type registry {@code realmforge:spell_effect_type} (architecture 5.3, 11):
 * the first set is complete with C3a-3 ({@link SpellEffectTypes}). Most act on an entity ({@link EntityEffect}); a
 * projectile, an area, a teleport and a dash need only a place or a direction. A new kind of behaviour is a new effect
 * type; a new spell is data.
 */
public interface SpellEffect {
    /** An effect by its {@code type}, e.g. {@code {"type": "realmforge:damage", "amount": 12}}. */
    Codec<SpellEffect> CODEC = SpellRegistries.EFFECT_TYPES.byNameCodec().dispatch(SpellEffect::codec, c -> c);

    MapCodec<? extends SpellEffect> codec();

    /** Runs the effect on {@code target}, a target the spell's targeting picked. Logical server only. */
    void apply(EffectContext context, SpellTarget target);
}
