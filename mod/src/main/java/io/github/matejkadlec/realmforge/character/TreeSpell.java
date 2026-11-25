package io.github.matejkadlec.realmforge.character;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.matejkadlec.realmforge.spell.Spell;
import io.github.matejkadlec.realmforge.spell.SpellRegistries;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.resources.RegistryFixedCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ExtraCodecs;

/**
 * One spell of a specialization's spell tree (C5, RLM-333, architecture 10.1). A spell that does not exist fails the
 * world load.
 *
 * @param spell    the spell
 * @param row      its row, from 1 at the top; row N opens once {@link SpellPoints#rowPoints} of it are spent in the
 *                 tree (RLM-631)
 * @param column   its column in the row, 1 to 3
 * @param requires the spells its arrows need, each known at any rank, from an earlier row or beside it in its own
 */
public record TreeSpell(Holder<Spell> spell, int row, int column, List<Holder<Spell>> requires) {
    public static final Codec<TreeSpell> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                    RegistryFixedCodec.create(SpellRegistries.SPELL).fieldOf("spell").forGetter(TreeSpell::spell),
                    ExtraCodecs.POSITIVE_INT.fieldOf("row").forGetter(TreeSpell::row),
                    ExtraCodecs.POSITIVE_INT.fieldOf("column").forGetter(TreeSpell::column),
                    RegistryFixedCodec.create(SpellRegistries.SPELL).listOf().optionalFieldOf("requires", List.of())
                            .forGetter(TreeSpell::requires))
            .apply(instance, TreeSpell::new));

    public TreeSpell {
        requires = List.copyOf(requires);
    }

    /** The spell's key: a tree's spells come from the registry, so they always have one. */
    public ResourceKey<Spell> key() {
        return spell.unwrapKey().orElseThrow();
    }

    /** Where it stands for the point rules, by keys alone. */
    public SpellPoints.Place place() {
        return new SpellPoints.Place(key(), row, requires.stream().map(required -> required.unwrapKey().orElseThrow())
                .toList());
    }
}
