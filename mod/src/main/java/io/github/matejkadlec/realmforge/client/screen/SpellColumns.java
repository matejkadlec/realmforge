package io.github.matejkadlec.realmforge.client.screen;

/**
 * How the spellbook's Spell Bar tab splits its spells over two columns (Matěj's playtest of 2026-11-04, RLM-333): the
 * first column fills, then the second, and only once both are full do they grow together, one spell each in turn, with
 * a scrollbar that moves both. Pure arithmetic: JUnit covers it.
 */
final class SpellColumns {
    private SpellColumns() {
    }

    /** How many of {@code spells} the first column holds, when a column shows {@code rows} of them at once. */
    static int first(int spells, int rows) {
        if (spells <= rows) {
            return spells;
        }
        return spells <= 2 * rows ? rows : (spells + 1) / 2;
    }
}
