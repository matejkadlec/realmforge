package io.github.matejkadlec.realmforge.combat;

import java.util.Locale;

/**
 * How damage numbers read in combat text, spell tooltips and the QA mod's DPS meter: one decimal, a whole number without
 * it (Matěj, 2026-10-30: rounding numbers of 10 and more to whole ones made a 15.4 hit read 15 and its 27.7 crit 28).
 */
public final class DamageNumbers {
    private DamageNumbers() {
    }

    /**
     * A floating combat text's number (Matěj, 2026-10-13): a crit ends with "!" ({@code 20!}), healing starts with "+"
     * ({@code +4}).
     */
    public static String label(double amount, boolean crit, boolean heal) {
        return (heal ? "+" : "") + format(amount) + (crit ? "!" : "");
    }

    /** {@code 12.4} reads "12.4", {@code 27.72} "27.7", {@code 4.5} "4.5", {@code 7.0} "7", {@code 0.04} "0". */
    public static String format(double amount) {
        String oneDecimal = String.format(Locale.ROOT, "%.1f", amount);
        return oneDecimal.endsWith(".0") ? oneDecimal.substring(0, oneDecimal.length() - 2) : oneDecimal;
    }
}
