package io.github.matejkadlec.realmforge.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class DamageNumbersTest {
    @Test
    void bigNumbersKeepOneDecimalToo() {
        assertEquals("12.4", DamageNumbers.format(12.4));
        assertEquals("27.7", DamageNumbers.format(15.4 * 1.8), "Reckless Strike's crit (Matěj, 2026-10-30)");
        assertEquals("1500.2", DamageNumbers.format(1500.2));
        assertEquals("28", DamageNumbers.format(27.96));
    }

    @Test
    void smallNumbersKeepOneDecimal() {
        assertEquals("4.5", DamageNumbers.format(4.5));
        assertEquals("4.3", DamageNumbers.format(4.25001));
        assertEquals("9.9", DamageNumbers.format(9.94));
    }

    @Test
    void critsEndWithAnExclamationMarkAndHealsStartWithPlus() {
        assertEquals("20!", DamageNumbers.label(20, true, false));
        assertEquals("7.5", DamageNumbers.label(7.5, false, false));
        assertEquals("+4", DamageNumbers.label(4, false, true));
        assertEquals("+12.2!", DamageNumbers.label(12.2, true, true), "a crit heal, for spells to come");
    }

    @Test
    void wholeSmallNumbersDropTheDecimal() {
        assertEquals("7", DamageNumbers.format(7.0));
        assertEquals("10", DamageNumbers.format(9.96), "rounds up to the whole number");
        assertEquals("0", DamageNumbers.format(0.04));
    }
}
