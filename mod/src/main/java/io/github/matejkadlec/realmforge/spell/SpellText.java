package io.github.matejkadlec.realmforge.spell;

import io.github.matejkadlec.realmforge.combat.CombatRegistries;
import io.github.matejkadlec.realmforge.combat.DamageNumbers;
import io.github.matejkadlec.realmforge.combat.DamagePipeline;
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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Fills the placeholders of a spell's description with live numbers (architecture 6.3, 11.6; C3b, RLM-25): numbers
 * come from the spell's data at the player's spell level and from their stats as they are now, never from prose. The
 * names are those {@code tools/realmforge_tools/schema/spell.py} lets a description use, and validation makes sure
 * each one has the effect it reads:
 *
 * <ul>
 *   <li>{@code damage}, {@code heal}: the first damage or heal effect (a projectile's or an area's included), raised by
 *   spell power and the school's power as the hit or heal is ({@code DamageCalculator#schoolHit},
 *   {@link SpellScaling#heal}), before the target's resistance and without a crit;</li>
 *   <li>{@code duration}: the first {@code apply_effect}'s duration in seconds;</li>
 *   <li>{@code periodic}, {@code periodic_total}, {@code interval}: one tick, every tick and the seconds between two
 *   ticks of that effect's damage or healing over time ({@link Periodic}), raised by the caster's stats as its ticks
 *   are ({@link EffectTicks#factor});</li>
 *   <li>{@code radius}: the first area's radius in blocks.</li>
 * </ul>
 *
 * Damage and healing read like combat text ({@link DamageNumbers}); seconds and blocks keep up to two decimals. A
 * placeholder without a value stays as it is written. A damage number shows its school instead of naming it (Matěj's
 * playtest of 2026-10-21): the school's icon before it ({@link SpellSchools#icon}) and the number in the school's
 * colour, so a description reads "for [icon]17 damage".
 */
public final class SpellText {
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([a-z_]+)\\}");
    private static final double TICKS_PER_SECOND = 20;

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
     * The school whose icon and colour each number of {@code spell} takes: the spell's own for {@code damage}, the
     * effect's for its damage over time ({@code periodic}, {@code periodic_total}). Healing, seconds and blocks take
     * none.
     */
    public static Map<String, ResourceKey<SpellSchool>> placeholderSchools(Spell spell) {
        Map<String, ResourceKey<SpellSchool>> placeholders = new LinkedHashMap<>();
        List<SpellEffect> effects = allEffects(spell.effects());
        if (first(effects, DamageEffect.class).isPresent()) {
            spell.school().unwrapKey().ifPresent(key -> placeholders.put("damage", key));
        }
        first(effects, ApplyEffectEffect.class).ifPresent(applied -> {
            if (applied.effect().value() instanceof StatusEffect status && status.periodic().isPresent()
                    && status.periodic().get().kind() == Periodic.Kind.DAMAGE) {
                placeholders.put("periodic", status.periodic().get().school());
                placeholders.put("periodic_total", status.periodic().get().school());
            }
        });
        return placeholders;
    }

    /**
     * The value of every placeholder {@code spell} can fill at {@code level} for a caster with {@code stats}, as it
     * reads in a description.
     */
    public static Map<String, String> values(Spell spell, int level, ToDoubleFunction<Holder<Attribute>> stats,
            Function<ResourceKey<SpellSchool>, Optional<SpellSchool>> schools) {
        Map<String, String> values = new LinkedHashMap<>();
        List<SpellEffect> effects = allEffects(spell.effects());
        SpellSchool school = spell.school().value();
        double attackDamage = stats.applyAsDouble(Attributes.ATTACK_DAMAGE);
        double spellPower = stats.applyAsDouble(GeneratedStats.SPELL_POWER);
        double schoolPower = school.power().map(stats::applyAsDouble).orElse(0.0);
        first(effects, DamageEffect.class).ifPresent(damage -> {
            double base = SpellScaling.base(damage.amount(), damage.perLevel(), level, damage.attackDamage(),
                    attackDamage);
            double bonus = (school.magic() ? spellPower : 0) + schoolPower;
            values.put("damage", DamageNumbers.format(base * Math.max(0, 1 + bonus)));
        });
        first(effects, HealEffect.class).ifPresent(heal -> {
            double base = SpellScaling.base(heal.amount(), heal.perLevel(), level, heal.attackDamage(),
                    attackDamage);
            values.put("heal", DamageNumbers.format(SpellScaling.heal(base, spell.scalesWith(), spellPower,
                    schoolPower)));
        });
        first(effects, ApplyEffectEffect.class).ifPresent(applied -> {
            values.put("duration", decimal(applied.durationTicks() / TICKS_PER_SECOND));
            if (applied.effect().value() instanceof StatusEffect status && status.periodic().isPresent()) {
                Periodic periodic = status.periodic().get();
                double factor = schools.apply(periodic.school()).map(tickSchool -> EffectTicks.factor(tickSchool,
                        stats)).orElse(1.0);
                double tick = periodic.amountAt(applied.amplifier()) * factor;
                values.put("periodic", DamageNumbers.format(tick));
                values.put("periodic_total",
                        DamageNumbers.format(tick * periodic.tickCount(applied.durationTicks())));
                values.put("interval", decimal(periodic.intervalTicks() / TICKS_PER_SECOND));
            }
        });
        first(effects, AreaEffect.class).ifPresent(area -> values.put("radius", decimal(area.radius())));
        return values;
    }

    /** Seconds or blocks: up to two decimals, rounded half up, without trailing zeros ({@code 1.5}, {@code 3}). */
    public static String decimal(double value) {
        if (!Double.isFinite(value)) {
            return String.valueOf(value);
        }
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    /** Every effect in order, each projectile's and area's own right after it, as the pipeline counts them. */
    static List<SpellEffect> allEffects(List<SpellEffect> effects) {
        List<SpellEffect> all = new ArrayList<>();
        for (SpellEffect effect : effects) {
            all.add(effect);
            if (effect instanceof ProjectileEffect projectile) {
                all.addAll(projectile.effects());
            } else if (effect instanceof AreaEffect area) {
                all.addAll(area.effects());
            }
        }
        return all;
    }

    private static <T extends SpellEffect> Optional<T> first(List<SpellEffect> effects, Class<T> type) {
        return effects.stream().filter(type::isInstance).map(type::cast).findFirst();
    }
}
