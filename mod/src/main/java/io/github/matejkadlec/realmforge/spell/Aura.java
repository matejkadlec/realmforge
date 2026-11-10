package io.github.matejkadlec.realmforge.spell;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.ExtraCodecs;

/**
 * What an aura spell does (RLM-595; Matěj, 2026-11-03; architecture 11.1): a spell with ranks that a player turns on
 * and off from the spell bar. Turning one on costs nothing and is off the global cooldown; while it is on it works as a
 * passive spell at its rank ({@link Passives}), its {@code bonus} pausing, never ending, while its weapon condition is
 * not met ({@link Passive#appliesWith}). One aura of a {@code group} is on at a time (the Warrior's stances: Battle
 * Stance, Berserker Stance, Defensive Stance), and turning any aura on or off, or switching, starts one cooldown that
 * every aura shares, {@link #COOLDOWN_TICKS}, which Cooldown Reduction never shortens ({@link Auras}). An aura stays
 * on through death and relog: the spellbook saves it ({@link Spellbook#auras}).
 *
 * @param group the auras of which only one is on at a time, such as {@code stance}
 * @param bonus what it gives while it is on, as a passive spell does
 */
public record Aura(String group, Passive bonus) {
    /** The cooldown every aura shares (3 minutes, Matěj, 2026-11-03), never shortened by Cooldown Reduction. */
    public static final int COOLDOWN_TICKS = 3 * 60 * 20;

    public static final Codec<Aura> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                    ExtraCodecs.NON_EMPTY_STRING.fieldOf("group").forGetter(Aura::group),
                    Passive.MAP_CODEC.forGetter(Aura::bonus))
            .apply(instance, Aura::new));
}
