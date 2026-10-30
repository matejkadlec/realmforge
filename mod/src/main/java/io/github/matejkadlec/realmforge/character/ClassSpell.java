package io.github.matejkadlec.realmforge.character;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.matejkadlec.realmforge.spell.Spell;
import io.github.matejkadlec.realmforge.spell.SpellRegistries;
import net.minecraft.core.Holder;
import net.minecraft.resources.RegistryFixedCodec;
import net.minecraft.util.ExtraCodecs;

/**
 * A spell a class teaches its players at a level (C4, architecture 10.1): they learn it at rank 1 once they reach the
 * level ({@link ClassSpells}), and keep it. A spell that does not exist fails the world load.
 *
 * @param spell the spell
 * @param level the character level that teaches it
 */
public record ClassSpell(Holder<Spell> spell, int level) {
    public static final Codec<ClassSpell> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                    RegistryFixedCodec.create(SpellRegistries.SPELL).fieldOf("spell").forGetter(ClassSpell::spell),
                    ExtraCodecs.POSITIVE_INT.fieldOf("level").forGetter(ClassSpell::level))
            .apply(instance, ClassSpell::new));
}
