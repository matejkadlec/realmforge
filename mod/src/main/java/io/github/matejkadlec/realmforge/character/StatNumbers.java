package io.github.matejkadlec.realmforge.character;

import java.math.BigDecimal;
import java.math.RoundingMode;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.neoforged.neoforge.common.PercentageAttribute;

/**
 * How the character sheet and character creation print a stat's value (RLM-530; Matěj, 2026-10-30: every value with one
 * decimal place, percentages included). One rule for points and percentages alike: rounded half up to one decimal,
 * from the decimal the double prints as, without a trailing {@code .0} or a negative zero: {@code 25}, {@code 52.8},
 * {@code 0.5%}, {@code 13.5%}, {@code 120%}. Only the display rounds; the game keeps the value.
 */
public final class StatNumbers {
    private StatNumbers() {
    }

    /**
     * Points: {@code 25}, {@code 12.5}, {@code 6.44} reads {@code 6.4} and {@code 6.45} {@code 6.5}. The decimal the
     * value prints as is what rounds, so a double just below {@code 6.45} still reads {@code 6.5}.
     */
    public static String points(double value) {
        if (!Double.isFinite(value)) {
            return String.valueOf(value);
        }
        return plain(BigDecimal.valueOf(value));
    }

    /** A share in percent: {@code 0.005} reads {@code 0.5%}, {@code 0.135} {@code 13.5%} and {@code 1.2} {@code 120%}. */
    public static String share(double value) {
        if (!Double.isFinite(value)) {
            return value * 100 + "%";
        }
        return plain(BigDecimal.valueOf(value).movePointRight(2)) + "%";
    }

    /** A value as a percentage of a base: movement speed 0.105 of the base 0.1 reads {@code 105%}. */
    public static String percentOf(double value, double base) {
        return base == 0 ? points(value) : share(value / base);
    }

    /** An attribute's value: a share for a percentage attribute, points otherwise. */
    public static String value(Holder<Attribute> attribute, double value) {
        return attribute.value() instanceof PercentageAttribute ? share(value) : points(value);
    }

    /**
     * The amount a modifier gives, signed: a share for multiplied operations and for adding to a share
     * ({@code +0.5%}), points otherwise ({@code +10}).
     */
    public static String signed(Holder<Attribute> attribute, AttributeModifier.Operation operation, double amount) {
        boolean percent = operation != AttributeModifier.Operation.ADD_VALUE
                || attribute.value() instanceof PercentageAttribute;
        String number = percent ? share(Math.abs(amount)) : points(Math.abs(amount));
        return (amount < 0 && !number.equals("0") && !number.equals("0%") ? "-" : "+") + number;
    }

    /** One decimal, half up, without trailing zeros, an exponent or a negative zero: {@code 20}, {@code 6.5}. */
    private static String plain(BigDecimal value) {
        BigDecimal stripped = value.setScale(1, RoundingMode.HALF_UP).stripTrailingZeros();
        return (stripped.signum() == 0 ? BigDecimal.ZERO : stripped).toPlainString();
    }
}
