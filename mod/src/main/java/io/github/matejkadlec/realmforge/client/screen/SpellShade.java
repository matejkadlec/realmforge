package io.github.matejkadlec.realmforge.client.screen;

import io.github.matejkadlec.realmforge.character.SpellTreeView.State;

/**
 * How dark the Spell Tree tab draws a spell (Matěj's playtest of 2026-11-04, RLM-333): at full light once the player
 * knows it, a little darker and pulsing while a click would teach it, very dark while they cannot learn it yet. Pure:
 * JUnit covers it.
 */
enum SpellShade {
    KNOWN,
    LEARNABLE,
    LOCKED;

    /** One way of the pulse, dark to light or back. */
    static final int PULSE_MILLIS = 1000;
    private static final int LOCKED_ALPHA = 0xC0;
    private static final int PULSE_DARK_ALPHA = 0x80;
    private static final int PULSE_LIGHT_ALPHA = 0x40;

    /** The shade of a spell the player knows at {@code rank} (0: not at all), in this {@code state}. */
    static SpellShade of(int rank, State state) {
        if (rank > 0) {
            return KNOWN;
        }
        return state == State.LEARNABLE ? LEARNABLE : LOCKED;
    }

    /** How opaque the black drawn over the spell is at this moment (0 to 255). */
    int overlayAlpha(long millis) {
        return switch (this) {
            case KNOWN -> 0;
            case LOCKED -> LOCKED_ALPHA;
            case LEARNABLE -> {
                long phase = millis % (2L * PULSE_MILLIS);
                long towardsLight = phase < PULSE_MILLIS ? phase : 2L * PULSE_MILLIS - phase;
                yield PULSE_DARK_ALPHA - (int) ((PULSE_DARK_ALPHA - PULSE_LIGHT_ALPHA) * towardsLight / PULSE_MILLIS);
            }
        };
    }
}
