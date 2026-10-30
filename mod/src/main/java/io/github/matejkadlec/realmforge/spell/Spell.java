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
 * 6.4, 11.1), cast by players, mobs and bosses through {@link SpellCaster}. Its name and description came with the spell
 * bar (C3b); icons, animations and sounds come with C6.
 *
 * @param name the translation key of its name
 * @param description the translation key of what it does, with placeholders such as {@code {damage}} that
 *                    {@link SpellText} fills from the data
 * @param school the spell school whose damage it deals; a school that does not exist fails the world load. A spell
 *     that neither deals damage nor heals may have none (Will To Survive)
 * @param scalesWith what its numbers grow with: spell power for magic, attack damage for physical abilities
 * @param targeting who it can target
 * @param cast how it is cast
 * @param cost what a cast costs, if anything: paid when the cast begins, and by a channel again every second
 * @param cooldownTicks its own cooldown, before Cooldown Reduction
 * @param globalCooldownTicks how long a cast blocks every other spell: every spell is on the global cooldown
 *                            (Matěj, 2026-10-24), so at least a tick
 * @param maxLevel its highest level (rank)
 * @param crit how it crits
 * @param npcOnly whether only mobs and bosses may cast it
 * @param requires what a player's cast needs in hand (RLM-499): a melee weapon, a shield
 * @param castableWhileStunned whether a stunned caster may cast it (Will To Survive, C4)
 * @param effects what it does, in order
 */
public record Spell(String name, String description, Optional<Holder<SpellSchool>> school, Scaling scalesWith,
        SpellTargeting targeting, SpellCast cast,
        Optional<SpellCost> cost, int cooldownTicks, int globalCooldownTicks, int maxLevel, CritRule crit,
        boolean npcOnly, List<SpellRequirement> requires, boolean castableWhileStunned, List<SpellEffect> effects) {
    /** The global cooldown of a spell that names none: half a second, as in WoW (Matěj, 2026-10-19, RLM-25). */
    public static final int DEFAULT_GLOBAL_COOLDOWN_TICKS = 10;

    /**
     * The colour of a spell of no school (Will To Survive, Matěj, 2026-10-30): dark grey, as RGB; the spell bar frames
     * it in black.
     */
    public static final int NO_SCHOOL_COLOR = 0x555555;

    public static final Codec<Spell> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                    Codec.STRING.fieldOf("name").forGetter(Spell::name),
                    Codec.STRING.fieldOf("description").forGetter(Spell::description),
                    RegistryFixedCodec.create(CombatRegistries.SPELL_SCHOOL).optionalFieldOf("school")
                            .forGetter(Spell::school),
                    Scaling.CODEC.fieldOf("scales_with").forGetter(Spell::scalesWith),
                    SpellTargeting.CODEC.fieldOf("targeting").forGetter(Spell::targeting),
                    SpellCast.CODEC.fieldOf("cast").forGetter(Spell::cast),
                    SpellCost.CODEC.optionalFieldOf("cost").forGetter(Spell::cost),
                    ExtraCodecs.NON_NEGATIVE_INT.optionalFieldOf("cooldown_ticks", 0).forGetter(Spell::cooldownTicks),
                    ExtraCodecs.POSITIVE_INT.optionalFieldOf("global_cooldown_ticks", DEFAULT_GLOBAL_COOLDOWN_TICKS)
                            .forGetter(Spell::globalCooldownTicks),
                    ExtraCodecs.POSITIVE_INT.optionalFieldOf("max_level", 1).forGetter(Spell::maxLevel),
                    CritRule.CODEC.optionalFieldOf("crit", CritRule.DEFAULT).forGetter(Spell::crit),
                    Codec.BOOL.optionalFieldOf("npc_only", false).forGetter(Spell::npcOnly),
                    SpellRequirement.CODEC.listOf().optionalFieldOf("requires", List.of()).forGetter(Spell::requires),
                    Codec.BOOL.optionalFieldOf("castable_while_stunned", false)
                            .forGetter(Spell::castableWhileStunned),
                    ExtraCodecs.nonEmptyList(SpellEffect.CODEC.listOf()).fieldOf("effects").forGetter(Spell::effects))
            .apply(instance, Spell::new));

    public Spell {
        requires = List.copyOf(requires);
        effects = List.copyOf(effects);
    }

    /** Its colour as RGB: its school's, or {@link #NO_SCHOOL_COLOR} for a spell of no school. */
    public int color() {
        return school.map(value -> value.value().color()).orElse(NO_SCHOOL_COLOR);
    }

    /** The cost at spell level {@code level}, 0 for a free spell. */
    public double costAt(int level) {
        return cost.map(value -> value.amountAt(level)).orElse(0.0);
    }
}
