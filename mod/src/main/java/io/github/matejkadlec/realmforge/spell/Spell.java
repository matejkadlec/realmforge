package io.github.matejkadlec.realmforge.spell;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
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
 * @param effects what it does, in order; none for a passive spell
 * @param passive what it does while its player knows it, for a passive spell (RLM-541): never cast, never on the bar.
 *     A passive spell's data names no targeting, cast or effects, and the pipeline writes their defaults
 * @param conditions when it may be cast beyond its cooldowns and cost (RLM-540): its window, a target health share
 */
public record Spell(String name, String description, Optional<Holder<SpellSchool>> school, Scaling scalesWith,
        SpellTargeting targeting, SpellCast cast,
        Optional<SpellCost> cost, int cooldownTicks, int globalCooldownTicks, int maxLevel, CritRule crit,
        boolean npcOnly, List<SpellRequirement> requires, boolean castableWhileStunned, List<SpellEffect> effects,
        Optional<Passive> passive, SpellConditions conditions) {
    /** The global cooldown of a spell that names none: half a second, as in WoW (Matěj, 2026-10-19, RLM-25). */
    public static final int DEFAULT_GLOBAL_COOLDOWN_TICKS = 10;

    /**
     * The colour of a spell of no school (Will To Survive, Matěj, 2026-10-30): dark grey, as RGB; the spell bar frames
     * it in black.
     */
    public static final int NO_SCHOOL_COLOR = 0x555555;

    /**
     * What it does: its effects, or its passive (RLM-541). One field of the codec for both, since a record codec takes
     * at most 15.
     */
    private static final MapCodec<Pair<List<SpellEffect>, Optional<Passive>>> BEHAVIOUR = Codec.mapPair(
            SpellEffect.CODEC.listOf().optionalFieldOf("effects", List.of()), Passive.CODEC.optionalFieldOf("passive"));

    public static final Codec<Spell> CODEC = RecordCodecBuilder.<Spell>create(instance -> instance.group(
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
                    BEHAVIOUR.forGetter(Spell::behaviour),
                    SpellConditions.MAP_CODEC.forGetter(Spell::conditions))
            .apply(instance, Spell::decoded))
            .validate(Spell::validate);

    public Spell {
        requires = List.copyOf(requires);
        effects = List.copyOf(effects);
    }

    private static DataResult<Spell> validate(Spell spell) {
        if (spell.isPassive() != spell.effects().isEmpty()) {
            return DataResult.error(() -> spell.isPassive() ? "A passive spell has no effects: " + spell.name()
                    : "An active spell has at least one effect: " + spell.name());
        }
        return DataResult.success(spell);
    }

    private static Spell decoded(String name, String description, Optional<Holder<SpellSchool>> school,
            Scaling scalesWith, SpellTargeting targeting, SpellCast cast, Optional<SpellCost> cost, int cooldownTicks,
            int globalCooldownTicks, int maxLevel, CritRule crit, boolean npcOnly, List<SpellRequirement> requires,
            boolean castableWhileStunned, Pair<List<SpellEffect>, Optional<Passive>> behaviour,
            SpellConditions conditions) {
        return new Spell(name, description, school, scalesWith, targeting, cast, cost, cooldownTicks,
                globalCooldownTicks, maxLevel, crit, npcOnly, requires, castableWhileStunned, behaviour.getFirst(),
                behaviour.getSecond(), conditions);
    }

    private Pair<List<SpellEffect>, Optional<Passive>> behaviour() {
        return Pair.of(effects, passive);
    }

    /** Whether it is a passive spell (RLM-541): never cast, never on the spell bar. */
    public boolean isPassive() {
        return passive.isPresent();
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
