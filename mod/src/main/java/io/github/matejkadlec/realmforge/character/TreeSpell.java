package io.github.matejkadlec.realmforge.character;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.matejkadlec.realmforge.spell.Spell;
import io.github.matejkadlec.realmforge.spell.SpellRegistries;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.resources.RegistryFixedCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ExtraCodecs;

/**
 * One spell of a specialization's spell tree (C5, RLM-333, architecture 10.1). A spell that does not exist fails the
 * world load.
 *
 * @param spell    the spell
 * @param level    the character level that opens its row: 1, then every 6 levels
 * @param column   its column in the row, 1 to 3
 * @param requires the spell its arrow needs, known at any rank, from an earlier row of the same tree
 */
public record TreeSpell(Holder<Spell> spell, int level, int column, Optional<Holder<Spell>> requires) {
    public static final Codec<TreeSpell> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                    RegistryFixedCodec.create(SpellRegistries.SPELL).fieldOf("spell").forGetter(TreeSpell::spell),
                    ExtraCodecs.POSITIVE_INT.fieldOf("level").forGetter(TreeSpell::level),
                    ExtraCodecs.POSITIVE_INT.fieldOf("column").forGetter(TreeSpell::column),
                    RegistryFixedCodec.create(SpellRegistries.SPELL).optionalFieldOf("requires")
                            .forGetter(TreeSpell::requires))
            .apply(instance, TreeSpell::new));

    /** The spell's key: a tree's spells come from the registry, so they always have one. */
    public ResourceKey<Spell> key() {
        return spell.unwrapKey().orElseThrow();
    }
}
