package io.github.matejkadlec.realmforge.combat;

import java.util.Locale;

/** How damage numbers read in combat text and the QA mod's DPS meter: whole numbers, one decimal below 10. */
public final class DamageNumbers {
    private DamageNumbers() {
    }

    /** {@code 12.4} reads "12", {@code 4.5} "4.5", {@code 7.0} "7", {@code 0.04} "0". */
    public static String format(double amount) {
        if (Math.abs(amount) >= 10) {
            return Long.toString(Math.round(amount));
        }
        String oneDecimal = String.format(Locale.ROOT, "%.1f", amount);
        return oneDecimal.endsWith(".0") ? oneDecimal.substring(0, oneDecimal.length() - 2) : oneDecimal;
    }
}
