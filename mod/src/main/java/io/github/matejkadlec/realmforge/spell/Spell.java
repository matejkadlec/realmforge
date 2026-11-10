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
 * @param cooldownTicks its own cooldown at rank 1, before Cooldown Reduction
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
 * @param cooldownPerLevelTicks added to its own cooldown for every rank above 1, usually less than 0 (RLM-572: Frost
 *     Nova's 20/15/10 s); its cooldown never goes below 0
 * @param aura what it does while it is on, for an aura spell (RLM-595): turned on and off from the spell bar, free and
 *     off the global cooldown, on the cooldown every aura shares ({@link Aura}). Like a passive spell's, its data names
 *     no targeting, cast or effects
 */
public record Spell(String name, String description, Optional<Holder<SpellSchool>> school, Scaling scalesWith,
        SpellTargeting targeting, SpellCast cast,
        Optional<SpellCost> cost, int cooldownTicks, int globalCooldownTicks, int maxLevel, CritRule crit,
        boolean npcOnly, List<SpellRequirement> requires, boolean castableWhileStunned, List<SpellEffect> effects,
        Optional<Passive> passive, SpellConditions conditions, int cooldownPerLevelTicks, Optional<Aura> aura) {
    /** The global cooldown of a spell that names none: half a second, as in WoW (Matěj, 2026-10-19, RLM-25). */
    public static final int DEFAULT_GLOBAL_COOLDOWN_TICKS = 10;

    /**
     * The colour of a spell of no school (Will To Survive, Matěj, 2026-10-30): dark grey, as RGB; the spell bar frames
     * it in black.
     */
    public static final int NO_SCHOOL_COLOR = 0x555555;

    /**
     * What it does: its effects, its passive (RLM-541) or its aura (RLM-595). One field of the codec for all three,
     * since a record codec takes at most 15.
     */
    private static final MapCodec<Pair<List<SpellEffect>, Pair<Optional<Passive>, Optional<Aura>>>> BEHAVIOUR =
            Codec.mapPair(SpellEffect.CODEC.listOf().optionalFieldOf("effects", List.of()),
                    Codec.mapPair(Passive.CODEC.optionalFieldOf("passive"), Aura.CODEC.optionalFieldOf("aura")));

    /** Its own cooldown at rank 1 and its change per rank (RLM-572), one field of the codec for the same reason. */
    private static final MapCodec<Pair<Integer, Integer>> COOLDOWN = Codec.mapPair(
            ExtraCodecs.NON_NEGATIVE_INT.optionalFieldOf("cooldown_ticks", 0),
            Codec.INT.optionalFieldOf("cooldown_per_level_ticks", 0));

    public static final Codec<Spell> CODEC = RecordCodecBuilder.<Spell>create(instance -> instance.group(
                    Codec.STRING.fieldOf("name").forGetter(Spell::name),
                    Codec.STRING.fieldOf("description").forGetter(Spell::description),
                    RegistryFixedCodec.create(CombatRegistries.SPELL_SCHOOL).optionalFieldOf("school")
                            .forGetter(Spell::school),
                    Scaling.CODEC.fieldOf("scales_with").forGetter(Spell::scalesWith),
                    SpellTargeting.CODEC.fieldOf("targeting").forGetter(Spell::targeting),
                    SpellCast.CODEC.fieldOf("cast").forGetter(Spell::cast),
                    SpellCost.CODEC.optionalFieldOf("cost").forGetter(Spell::cost),
                    COOLDOWN.forGetter(Spell::cooldown),
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

    /** A spell that is no aura (RLM-595). */
    public Spell(String name, String description, Optional<Holder<SpellSchool>> school, Scaling scalesWith,
            SpellTargeting targeting, SpellCast cast, Optional<SpellCost> cost, int cooldownTicks,
            int globalCooldownTicks, int maxLevel, CritRule crit, boolean npcOnly, List<SpellRequirement> requires,
            boolean castableWhileStunned, List<SpellEffect> effects, Optional<Passive> passive,
            SpellConditions conditions, int cooldownPerLevelTicks) {
        this(name, description, school, scalesWith, targeting, cast, cost, cooldownTicks, globalCooldownTicks,
                maxLevel, crit, npcOnly, requires, castableWhileStunned, effects, passive, conditions,
                cooldownPerLevelTicks, Optional.empty());
    }

    /** A spell whose cooldown is the same at every rank. */
    public Spell(String name, String description, Optional<Holder<SpellSchool>> school, Scaling scalesWith,
            SpellTargeting targeting, SpellCast cast, Optional<SpellCost> cost, int cooldownTicks,
            int globalCooldownTicks, int maxLevel, CritRule crit, boolean npcOnly, List<SpellRequirement> requires,
            boolean castableWhileStunned, List<SpellEffect> effects, Optional<Passive> passive,
            SpellConditions conditions) {
        this(name, description, school, scalesWith, targeting, cast, cost, cooldownTicks, globalCooldownTicks,
                maxLevel, crit, npcOnly, requires, castableWhileStunned, effects, passive, conditions, 0);
    }

    private static DataResult<Spell> validate(Spell spell) {
        if (spell.isPassive() && spell.isAura()) {
            return DataResult.error(() -> "A spell is passive or an aura, not both: " + spell.name());
        }
        boolean active = !spell.isPassive() && !spell.isAura();
        if (active == spell.effects().isEmpty()) {
            return DataResult.error(() -> active ? "An active spell has at least one effect: " + spell.name()
                    : "A passive or aura spell has no effects: " + spell.name());
        }
        return DataResult.success(spell);
    }

    private static Spell decoded(String name, String description, Optional<Holder<SpellSchool>> school,
            Scaling scalesWith, SpellTargeting targeting, SpellCast cast, Optional<SpellCost> cost,
            Pair<Integer, Integer> cooldown, int globalCooldownTicks, int maxLevel, CritRule crit, boolean npcOnly,
            List<SpellRequirement> requires, boolean castableWhileStunned,
            Pair<List<SpellEffect>, Pair<Optional<Passive>, Optional<Aura>>> behaviour, SpellConditions conditions) {
        return new Spell(name, description, school, scalesWith, targeting, cast, cost, cooldown.getFirst(),
                globalCooldownTicks, maxLevel, crit, npcOnly, requires, castableWhileStunned, behaviour.getFirst(),
                behaviour.getSecond().getFirst(), conditions, cooldown.getSecond(), behaviour.getSecond().getSecond());
    }

    private Pair<List<SpellEffect>, Pair<Optional<Passive>, Optional<Aura>>> behaviour() {
        return Pair.of(effects, Pair.of(passive, aura));
    }

    private Pair<Integer, Integer> cooldown() {
        return Pair.of(cooldownTicks, cooldownPerLevelTicks);
    }

    /**
     * Its own cooldown at rank {@code level}, before Cooldown Reduction: the rank 1 cooldown plus its change for every
     * rank above (RLM-572), never below 0.
     */
    public int cooldownTicksAt(int level) {
        return Math.max(0, cooldownTicks + cooldownPerLevelTicks * (Math.max(1, level) - 1));
    }

    /** Whether it is a passive spell (RLM-541): never cast, never on the spell bar. */
    public boolean isPassive() {
        return passive.isPresent();
    }

    /** Whether it is an aura spell (RLM-595): turned on and off from the spell bar instead of cast. */
    public boolean isAura() {
        return aura.isPresent();
    }

    /** Its colour as RGB: its school's, or {@link #NO_SCHOOL_COLOR} for a spell of no school. */
    public int color() {
        return school.map(value -> value.value().color()).orElse(NO_SCHOOL_COLOR);
    }

    /** The cost at spell level {@code level}, 0 for a free spell: a channel's for each second. */
    public double costAt(int level) {
        return cost.map(value -> value.amountAt(level)).orElse(0.0);
    }

    /**
     * What a cast at spell level {@code level} costs when it begins: its cost, and a channel's amount up front on top
     * of its first second's (RLM-572); 0 for a free spell.
     */
    public double startCostAt(int level) {
        return cost.map(value -> value.amountAt(level) + value.upFrontAt(level)).orElse(0.0);
    }
}
