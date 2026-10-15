package io.github.matejkadlec.realmforge.spell;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.matejkadlec.realmforge.combat.CombatRegistries;
import io.github.matejkadlec.realmforge.combat.CritRule;
import io.github.matejkadlec.realmforge.combat.SpellSchool;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.resources.RegistryFixedCodec;
import net.minecraft.util.ExtraCodecs;

/**
 * A spell, an entry of the datapack registry {@code realmforge:spell} from {@code content/spells/} (architecture 5.2,
 * 6.4, 11.1), cast by players, mobs and bosses through {@link SpellCaster}. Its names and texts come with the spell bar
 * (C3b); icons, animations and sounds with C6.
 *
 * @param school the spell school whose damage it deals; a school that does not exist fails the world load
 * @param scalesWith what its numbers grow with: spell power for magic, attack damage for physical abilities
 * @param targeting who it can target
 * @param cast how it is cast
 * @param cost what a cast costs, if anything
 * @param cooldownTicks its own cooldown, before Cooldown Reduction
 * @param globalCooldownTicks how long a cast blocks every spell on the global cooldown; 0 for a spell off it
 * @param maxLevel its highest level (rank)
 * @param crit how it crits
 * @param npcOnly whether only mobs and bosses may cast it
 * @param effects what it does, in order
 */
public record Spell(Holder<SpellSchool> school, Scaling scalesWith, SpellTargeting targeting, SpellCast cast,
        Optional<SpellCost> cost, int cooldownTicks, int globalCooldownTicks, int maxLevel, CritRule crit,
        boolean npcOnly, List<SpellEffect> effects) {
    /** The global cooldown of a spell that names none: one second (research SP-11). */
    public static final int DEFAULT_GLOBAL_COOLDOWN_TICKS = 20;

    public static final Codec<Spell> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                    RegistryFixedCodec.create(CombatRegistries.SPELL_SCHOOL).fieldOf("school").forGetter(Spell::school),
                    Scaling.CODEC.fieldOf("scales_with").forGetter(Spell::scalesWith),
                    SpellTargeting.CODEC.fieldOf("targeting").forGetter(Spell::targeting),
                    SpellCast.CODEC.fieldOf("cast").forGetter(Spell::cast),
                    SpellCost.CODEC.optionalFieldOf("cost").forGetter(Spell::cost),
                    ExtraCodecs.NON_NEGATIVE_INT.optionalFieldOf("cooldown_ticks", 0).forGetter(Spell::cooldownTicks),
                    ExtraCodecs.NON_NEGATIVE_INT.optionalFieldOf("global_cooldown_ticks", DEFAULT_GLOBAL_COOLDOWN_TICKS)
                            .forGetter(Spell::globalCooldownTicks),
                    ExtraCodecs.POSITIVE_INT.optionalFieldOf("max_level", 1).forGetter(Spell::maxLevel),
                    CritRule.CODEC.optionalFieldOf("crit", CritRule.DEFAULT).forGetter(Spell::crit),
                    Codec.BOOL.optionalFieldOf("npc_only", false).forGetter(Spell::npcOnly),
                    ExtraCodecs.nonEmptyList(SpellEffect.CODEC.listOf()).fieldOf("effects").forGetter(Spell::effects))
            .apply(instance, Spell::new));

    public Spell {
        effects = List.copyOf(effects);
    }

    /** The cost at spell level {@code level}, 0 for a free spell. */
    public double costAt(int level) {
        return cost.map(value -> value.amountAt(level)).orElse(0.0);
    }
}
