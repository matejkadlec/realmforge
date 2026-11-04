package io.github.matejkadlec.realmforge.spell;

import io.github.matejkadlec.realmforge.combat.CombatRegistries;
import io.github.matejkadlec.realmforge.combat.DamageNumbers;
import io.github.matejkadlec.realmforge.combat.DamagePipeline;
import io.github.matejkadlec.realmforge.combat.HitBonus;
import io.github.matejkadlec.realmforge.combat.SpellSchool;
import io.github.matejkadlec.realmforge.combat.SpellSchools;
import io.github.matejkadlec.realmforge.effect.EffectTicks;
import io.github.matejkadlec.realmforge.effect.Periodic;
import io.github.matejkadlec.realmforge.effect.StatusEffect;
import io.github.matejkadlec.realmforge.generated.GeneratedStats;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Fills the placeholders of a spell's description with live numbers (architecture 6.3, 11.6; C3b, RLM-25): numbers
 * come from the spell's data at the player's spell level and from their stats as they are now, never from prose. The
 * names are those {@code tools/realmforge_tools/schema/spell.py} lets a description use, and validation makes sure
 * each one has the effect it reads:
 *
 * <ul>
 *   <li>{@code damage}, {@code heal}: the first damage or heal effect (a projectile's or an area's included): its
 *   amount plus its coefficient of the caster's power (system C, RLM-534), raised by the school's power as the hit or
 *   heal is ({@code DamageCalculator#schoolHit}, {@link SpellScaling#heal}), before the target's resistance and
 *   without a crit;</li>
 *   <li>{@code against_damage}: that damage against a target with the first damage effect's {@code against_effect}
 *   (RLM-542: Ice Lance against a Frozen target);</li>
 *   <li>{@code duration}: the first {@code apply_effect}'s duration in seconds;</li>
 *   <li>{@code periodic}, {@code periodic_total}, {@code interval}: one tick, every tick and the seconds between two
 *   ticks of that effect's damage or healing over time ({@link Periodic}), with the caster's stats as its ticks
 *   have them ({@link EffectTicks#tickFor});</li>
 *   <li>{@code radius}: the first area's radius in blocks, or with no area the first cone's range (RLM-542), or the
 *   first charge's landing radius (RLM-543);</li>
 *   <li>{@code armor_shred}, {@code penetration}: the first damage effect's extra armor shred and spell penetration
 *   at the rank, in percent, for an effect that has them (C4);</li>
 *   <li>{@code crit_chance}: the spell's bonus crit chance at the rank, in percent, for a spell that has one;</li>
 *   <li>{@code bonus}: the one attribute modifier of the first applied status effect at its strength (the pipeline
 *   allows it only for an effect with one), in percent for a multiplied operation and in the attribute's points
 *   otherwise (Ice Barrier's Magic Shield); {@code bonus_<attribute>}, its modifier on that attribute, by the id's
 *   path, for an effect with several (RLM-543: Battle Shout's {@code bonus_attack_damage} and
 *   {@code bonus_attack_speed});</li>
 *   <li>{@code dot_damage}, {@code dot_duration}: the first damage over time's damage over its whole duration, raised
 *   by its school's power as its ticks are, and its duration in seconds (RLM-543: Rend's bleed);</li>
 *   <li>{@code max_health_heal}: the first heal's share of max health, in percent (Victory Rush);</li>
 *   <li>{@code resource_gain}: the first {@code gain_resource}'s amount (Charge's rage);</li>
 *   <li>{@code min_range}, {@code range}: the target distance's nearest and farthest blocks at the rank (Charge), or
 *   for {@code range} without one a targeted spell's range;</li>
 *   <li>{@code window}: how long its window stays open, in seconds; {@code health_below}: its target health
 *   condition, in percent;</li>
 *   <li>{@code extra_cost}, {@code extra_damage}: the most extra resource its cost spends and the damage each point
 *   adds, in percent; {@code refund}, {@code refund_rank}: what a kill refunds and from which rank (Execute);</li>
 *   <li>{@code ticks}: how often a channel runs its effects for the caster's Cast Time Reduction (RLM-572: Blizzard's
 *   waves, round(8 × (1 + reduction)) as its ticks are multiplied).</li>
 * </ul>
 *
 * Every number is the one at the player's rank: an effect's duration and strength grow with it (C4). Damage and
 * healing read like combat text ({@link DamageNumbers}); seconds, blocks and percentages keep up to two decimals. A
 * placeholder without a value stays as it is written. A damage number shows its school instead of naming it (Matěj's
 * playtest of 2026-10-21): the school's icon before it ({@link SpellSchools#icon}) and the number in the school's
 * colour, so a description reads "for [icon]17 damage".
 */
public final class SpellText {
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([a-z_]+)\\}");
    private static final double TICKS_PER_SECOND = 20;
    private static final double PERCENT = 100;

    private SpellText() {
    }

    /** A caster's stats as the effects read them: {@code DamagePipeline#stat}, 0 for an attribute it lacks. */
    public static ToDoubleFunction<Holder<Attribute>> statsOf(LivingEntity caster) {
        return stat -> DamagePipeline.stat(caster, stat);
    }

    /** The spell schools of {@code registries}, by key. */
    public static Function<ResourceKey<SpellSchool>, Optional<SpellSchool>> schoolsOf(RegistryAccess registries) {
        return key -> registries.lookupOrThrow(CombatRegistries.SPELL_SCHOOL)
                .get(key).map(Holder::value);
    }

    /**
     * {@code template}, a translated description, with every placeholder that has a value replaced by it; a value whose
     * placeholder has a school ({@code placeholderSchools}) comes after the school's icon, in the school's colour.
     */
    public static MutableComponent fill(String template, Map<String, String> values,
            Map<String, ResourceKey<SpellSchool>> placeholderSchools,
            Function<ResourceKey<SpellSchool>, Optional<SpellSchool>> schools) {
        Matcher matcher = PLACEHOLDER.matcher(template);
        MutableComponent filled = Component.empty();
        int start = 0;
        while (matcher.find()) {
            if (matcher.start() > start) {
                filled.append(template.substring(start, matcher.start()));
            }
            String value = values.get(matcher.group(1));
            Optional<ResourceKey<SpellSchool>> key = Optional.ofNullable(placeholderSchools.get(matcher.group(1)));
            Optional<SpellSchool> school = key.flatMap(schools);
            if (value == null) {
                filled.append(matcher.group());
            } else if (key.isPresent() && school.isPresent()) {
                filled.append(SpellSchools.icon(key.get(), school.get()));
                filled.append(Component.literal(value).withColor(school.get().color()));
            } else {
                filled.append(value);
            }
            start = matcher.end();
        }
        if (start < template.length()) {
            filled.append(template.substring(start));
        }
        return filled;
    }

    /**
     * The school whose icon and colour each number of {@code spell} takes: the spell's own for {@code damage} and
     * {@code against_damage}, the effect's for its damage over time ({@code periodic}, {@code periodic_total},
     * {@code dot_damage}). Healing, seconds and blocks take none.
     */
    public static Map<String, ResourceKey<SpellSchool>> placeholderSchools(Spell spell) {
        Map<String, ResourceKey<SpellSchool>> placeholders = new LinkedHashMap<>();
        List<SpellEffect> effects = allEffects(spell.effects());
        first(effects, DamageEffect.class).ifPresent(damage -> spell.school().flatMap(Holder::unwrapKey)
                .ifPresent(key -> {
                    placeholders.put("damage", key);
                    if (damage.againstEffect().isPresent()) {
                        placeholders.put("against_damage", key);
                    }
                }));
        first(effects, ApplyEffectEffect.class).ifPresent(applied -> {
            if (applied.effect().value() instanceof StatusEffect status && status.periodic().isPresent()
                    && status.periodic().get().kind() == Periodic.Kind.DAMAGE) {
                placeholders.put("periodic", status.periodic().get().school());
                placeholders.put("periodic_total", status.periodic().get().school());
            }
        });
        first(effects, DamageOverTimeEffect.class).ifPresent(dot -> {
            if (dot.effect().value() instanceof StatusEffect status && status.stacks().isPresent()) {
                placeholders.put("dot_damage", status.stacks().get().school());
            }
        });
        return placeholders;
    }

    /**
     * The value of every placeholder {@code spell} can fill at {@code level} for a caster with {@code stats}, as it
     * reads in a description, without passive spells.
     */
    public static Map<String, String> values(Spell spell, int level, ToDoubleFunction<Holder<Attribute>> stats,
            Function<ResourceKey<SpellSchool>, Optional<SpellSchool>> schools) {
        return values(spell, level, stats, schools, 0);
    }

    /**
     * The value of every placeholder {@code spell} can fill at {@code level} for a caster with {@code stats} whose
     * passive spells add {@code passiveDamage} to its hits (RLM-541, {@link Passives#damageBonus}), as it reads in a
     * description. A passive spell fills its own placeholders ({@link #passiveValues}).
     */
    public static Map<String, String> values(Spell spell, int level, ToDoubleFunction<Holder<Attribute>> stats,
            Function<ResourceKey<SpellSchool>, Optional<SpellSchool>> schools, double passiveDamage) {
        if (spell.passive().isPresent()) {
            return passiveValues(spell.passive().get(), level);
        }
        Map<String, String> values = new LinkedHashMap<>();
        List<SpellEffect> effects = allEffects(spell.effects());
        Optional<SpellSchool> school = spell.school().map(Holder::value);
        double attackDamage = stats.applyAsDouble(Attributes.ATTACK_DAMAGE);
        double spellPower = stats.applyAsDouble(GeneratedStats.SPELL_POWER);
        double schoolPower = school.flatMap(SpellSchool::power).map(stats::applyAsDouble).orElse(0.0);
        first(effects, DamageEffect.class).ifPresent(damage -> {
            double hit = damage.baseAt(level, attackDamage, spellPower) * Math.max(0, 1 + schoolPower + passiveDamage);
            values.put("damage", DamageNumbers.format(hit));
            damage.againstEffect().ifPresent(against -> values.put("against_damage",
                    DamageNumbers.format(hit * against.factorAt(level))));
        });
        first(effects, HealEffect.class).ifPresent(heal -> {
            double base = SpellScaling.base(heal.amount(), heal.perLevel(), level, heal.coefficient(), attackDamage,
                    spellPower);
            values.put("heal", DamageNumbers.format(SpellScaling.heal(base, spell.scalesWith(), schoolPower)));
        });
        first(effects, DamageEffect.class).ifPresent(damage -> {
            HitBonus hit = damage.bonusAt(level);
            if (damage.armorShred() > 0 || damage.armorShredPerLevel() > 0) {
                values.put("armor_shred", decimal(hit.armorShred() * PERCENT));
            }
            if (damage.penetration() > 0 || damage.penetrationPerLevel() > 0) {
                values.put("penetration", decimal(hit.penetration() * PERCENT));
            }
        });
        if (spell.crit().bonusChance() > 0 || spell.crit().bonusChancePerLevel() > 0) {
            values.put("crit_chance", decimal(spell.crit().atLevel(level).bonusChance() * PERCENT));
        }
        first(effects, ApplyEffectEffect.class).ifPresent(applied -> {
            int duration = applied.durationAt(level);
            int amplifier = applied.amplifierAt(level);
            values.put("duration", decimal(duration / TICKS_PER_SECOND));
            if (applied.effect().value() instanceof StatusEffect status && status.periodic().isPresent()) {
                Periodic periodic = status.periodic().get();
                double tick = schools.apply(periodic.school())
                        .map(tickSchool -> EffectTicks.tickFor(periodic, tickSchool, amplifier, stats))
                        .orElse(periodic.amountAt(amplifier));
                values.put("periodic", DamageNumbers.format(tick));
                values.put("periodic_total", DamageNumbers.format(tick * periodic.tickCount(duration)));
                values.put("interval", decimal(periodic.intervalTicks() / TICKS_PER_SECOND));
            }
            firstModifier(applied.effect(), amplifier).ifPresent(modifier -> values.put("bonus", decimal(
                    modifier.operation() == AttributeModifier.Operation.ADD_VALUE ? modifier.amount()
                            : modifier.amount() * PERCENT)));
            // {bonus_<attribute>} (RLM-543): the modifier on each attribute, by the id's path (Battle Shout's two).
            applied.effect().value().createModifiers(amplifier, (attribute, modifier) -> attribute.unwrapKey()
                    .ifPresent(key -> values.put("bonus_" + key.identifier().getPath(), decimal(
                            modifier.operation() == AttributeModifier.Operation.ADD_VALUE ? modifier.amount()
                                    : modifier.amount() * PERCENT))));
        });
        first(effects, AreaEffect.class).map(AreaEffect::radius)
                .or(() -> first(effects, ConeEffect.class).map(ConeEffect::range))
                .or(() -> first(effects, ChargeEffect.class).map(ChargeEffect::landingRadius))
                .ifPresent(radius -> values.put("radius", decimal(radius)));
        first(effects, DamageOverTimeEffect.class).ifPresent(dot -> {
            double power = dot.effect().value() instanceof StatusEffect status ? status.stacks()
                    .flatMap(stacked -> schools.apply(stacked.school())).flatMap(SpellSchool::power)
                    .map(stats::applyAsDouble).orElse(0.0) : 0;
            values.put("dot_damage", DamageNumbers.format(dot.totalAt(level, attackDamage, spellPower)
                    * Math.max(0, 1 + power)));
            values.put("dot_duration", decimal(dot.durationAt(level) / TICKS_PER_SECOND));
        });
        first(effects, HealEffect.class).filter(heal -> heal.maxHealth() > 0 || heal.maxHealthPerLevel() > 0)
                .ifPresent(heal -> values.put("max_health_heal", decimal(heal.maxHealthAt(level) * PERCENT)));
        first(effects, GainResourceEffect.class)
                .ifPresent(gain -> values.put("resource_gain", decimal(gain.amountAt(level))));
        conditionValues(spell, level, values);
        if (spell.cast().type() == SpellCast.Type.CHANNEL) {
            // RLM-572: Blizzard's waves, more with Cast Time Reduction for a channel whose ticks it multiplies.
            values.put("ticks", Integer.toString(spell.cast().tickCountFor(
                    stats.applyAsDouble(GeneratedStats.CAST_TIME_REDUCTION))));
        }
        return values;
    }

    /** The placeholders of {@code spell}'s conditions and cost at {@code level} (RLM-543), into {@code values}. */
    private static void conditionValues(Spell spell, int level, Map<String, String> values) {
        SpellConditions conditions = spell.conditions();
        conditions.targetDistance().ifPresentOrElse(distance -> {
            if (distance.min() > 0) {
                values.put("min_range", decimal(distance.min()));
            }
            values.put("range", decimal(distance.maxAt(level)));
        }, () -> {
            boolean melee = spell.targeting() instanceof EnemyTargeting enemy && enemy.melee();
            if (!melee && spell.targeting().reach() > 0) {
                values.put("range", decimal(spell.targeting().reach()));
            }
        });
        conditions.window().ifPresent(window -> values.put("window",
                decimal(window.durationTicks() / TICKS_PER_SECOND)));
        conditions.targetHealthBelow().ifPresent(share -> values.put("health_below", decimal(share * PERCENT)));
        spell.cost().flatMap(SpellCost::extra).ifPresent(extra -> {
            values.put("extra_cost", decimal(extra.max()));
            values.put("extra_damage", decimal(extra.damagePerPoint() * PERCENT));
        });
        spell.cost().flatMap(SpellCost::refundOnKill).ifPresent(refund -> {
            values.put("refund", decimal(refund.amountAt(Math.max(level, refund.fromLevel()))));
            values.put("refund_rank", Integer.toString(refund.fromLevel()));
        });
    }

    /**
     * A passive spell's placeholders at rank {@code level} (RLM-541): {@code bonus}, its first modifier (in percent for
     * a multiplied operation, in the attribute's unit otherwise); {@code spell_damage}, its first spell damage bonus in
     * percent; {@code crit_chance}, its crit chance against its condition in percent; {@code block_cooldown} and
     * {@code block_cooldown_rank}, a two-handed block's shorter cooldown in seconds and the rank it starts at;
     * {@code splash_radius} and {@code splash_radius_rank}, its first splash bonus in blocks and the rank it starts at;
     * {@code proc_chance}, {@code proc_duration} and {@code proc_cooldown}, its first proc's chance in percent, how
     * long the effect or damage over time it gives lasts and its internal cooldown on each target, in seconds (RLM-542);
     * {@code proc_damage}, its damage over time's share of the hit that fires it, in percent (RLM-543).
     */
    public static Map<String, String> passiveValues(Passive passive, int level) {
        Map<String, String> values = new LinkedHashMap<>();
        passive.modifiers().stream().findFirst().ifPresent(modifier -> values.put("bonus", decimal(
                modifier.operation() == AttributeModifier.Operation.ADD_VALUE ? modifier.amountAt(level)
                        : modifier.amountAt(level) * PERCENT)));
        passive.spellDamage().stream().findFirst().ifPresent(bonus -> values.put("spell_damage",
                decimal(bonus.damageAt(level) * PERCENT)));
        passive.critChance().ifPresent(bonus -> values.put("crit_chance", decimal(bonus.chanceAt(level) * PERCENT)));
        passive.blockCooldown().ifPresent(cooldown -> {
            values.put("block_cooldown", decimal(cooldown.reductionTicks() / TICKS_PER_SECOND));
            values.put("block_cooldown_rank", Integer.toString(cooldown.fromLevel()));
        });
        passive.splashRadius().stream().findFirst().ifPresent(bonus -> {
            values.put("splash_radius", decimal(bonus.radius()));
            values.put("splash_radius_rank", Integer.toString(bonus.fromLevel()));
        });
        passive.procs().stream().findFirst().ifPresent(proc -> {
            values.put("proc_chance", decimal(proc.chanceAt(level) * PERCENT));
            proc.applyEffect().ifPresent(given -> values.put("proc_duration",
                    decimal(given.durationAt(level) / TICKS_PER_SECOND)));
            proc.damageOverTime().ifPresent(bleed -> {
                values.put("proc_damage", decimal(bleed.damageAt(level) * PERCENT));
                values.put("proc_duration", decimal(bleed.durationTicks() / TICKS_PER_SECOND));
            });
            values.put("proc_cooldown", decimal(proc.cooldownTicks() / TICKS_PER_SECOND));
        });
        return values;
    }

    /** An attribute modifier {@code effect} gives at {@code amplifier}, if any: its only one for {@code bonus}. */
    private static Optional<AttributeModifier> firstModifier(Holder<MobEffect> effect, int amplifier) {
        List<AttributeModifier> modifiers = new ArrayList<>();
        effect.value().createModifiers(amplifier, (attribute, modifier) -> modifiers.add(modifier));
        return modifiers.stream().findFirst();
    }

    /** Seconds or blocks: up to two decimals, rounded half up, without trailing zeros ({@code 1.5}, {@code 3}). */
    public static String decimal(double value) {
        if (!Double.isFinite(value)) {
            return String.valueOf(value);
        }
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    /**
     * Every effect in order, each projectile's, area's, melee shape's, cone's and charge's own right after it, as the
     * pipeline counts them.
     */
    static List<SpellEffect> allEffects(List<SpellEffect> effects) {
        List<SpellEffect> all = new ArrayList<>();
        for (SpellEffect effect : effects) {
            all.add(effect);
            if (effect instanceof ProjectileEffect projectile) {
                all.addAll(projectile.effects());
            } else if (effect instanceof AreaEffect area) {
                all.addAll(area.effects());
            } else if (effect instanceof MeleeEffect melee) {
                all.addAll(melee.effects());
            } else if (effect instanceof ConeEffect cone) {
                all.addAll(cone.effects());
            } else if (effect instanceof ChargeEffect charge) {
                all.addAll(charge.effects());
            }
        }
        return all;
    }

    private static <T extends SpellEffect> Optional<T> first(List<SpellEffect> effects, Class<T> type) {
        return effects.stream().filter(type::isInstance).map(type::cast).findFirst();
    }
}
