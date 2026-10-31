package io.github.matejkadlec.realmforge.spell;

import com.mojang.serialization.Codec;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.effect.MobEffect;

/**
 * A proc (architecture 12): when its holder hits, crits, kills, is hit, gives an enemy a status effect or deals a tick of
 * damage over time, it may cast a spell, apply a status effect or open a spell's window, then waits out its internal
 * cooldown (research SP-15), on its holder or on each target. Items, talents (postponed, RLM-28) and passive spells
 * (RLM-540: Frostbite, Taste for Blood, Deep Wounds) give procs through a {@link Procs.Source}; {@link Procs} runs them.
 *
 * @param id names the proc: its internal cooldown and the proc origin its spell's hits carry
 * @param trigger what makes it fire
 * @param chance the chance it fires when its trigger happens, a share from 0 to 1
 * @param cooldownTicks its internal cooldown: how long after it fired it cannot fire again; 0 for none
 * @param action what it does
 * @param effect for {@link Trigger#APPLIES_EFFECT} and {@link Trigger#TICK}: only that status effect, if named
 * @param spell for {@link Trigger#TICK}: only the ticks of that spell's damage over time, if named
 * @param perTarget whether its internal cooldown runs per target (RLM-540) rather than for its holder as a whole
 */
public record Proc(Identifier id, Trigger trigger, double chance, int cooldownTicks, Action action,
        Optional<Identifier> effect, Optional<Identifier> spell, boolean perTarget) {
    public Proc {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(trigger, "trigger");
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(effect, "effect");
        Objects.requireNonNull(spell, "spell");
        if (!(chance >= 0 && chance <= 1) || cooldownTicks < 0) {
            throw new IllegalArgumentException(
                    "A proc fires at a chance from 0 to 1, with a cooldown of 0 ticks or more: " + chance + ", "
                            + cooldownTicks);
        }
    }

    /** A proc that watches no particular effect or spell, its internal cooldown on its holder. */
    public Proc(Identifier id, Trigger trigger, double chance, int cooldownTicks, Action action) {
        this(id, trigger, chance, cooldownTicks, action, Optional.empty(), Optional.empty(), false);
    }

    /** Whether it watches {@code effect} and {@code spell}: what it names matches, what it leaves out matches all. */
    public boolean watches(Optional<Identifier> effect, Optional<Identifier> spell) {
        return (this.effect.isEmpty() || this.effect.equals(effect))
                && (this.spell.isEmpty() || this.spell.equals(spell));
    }

    /** What makes a proc fire, from its holder's side. */
    public enum Trigger implements StringRepresentable {
        /** The holder dealt a hit (melee, ranged or a spell's) to another entity. */
        HIT("hit"),
        /** The holder dealt a critical hit. */
        CRIT("crit"),
        /** The holder killed another entity. */
        KILL("kill"),
        /** Another entity hit the holder. */
        HIT_TAKEN("hit_taken"),
        /** The holder gave another entity a status effect (Frostbite: the player's Slowness, RLM-540). */
        APPLIES_EFFECT("applies_effect"),
        /** A tick of the holder's damage over time landed on another entity (Taste for Blood: Rend's, RLM-540). */
        TICK("tick");

        public static final Codec<Trigger> CODEC = StringRepresentable.fromEnum(Trigger::values);

        private final String serializedName;

        Trigger(String serializedName) {
            this.serializedName = serializedName;
        }

        @Override
        public String getSerializedName() {
            return serializedName;
        }
    }

    /** What a proc does when it fires. */
    public sealed interface Action permits CastSpell, ApplyEffect, OpenWindow {
    }

    /**
     * Opens a spell's window for the holder, a player who knows it (Taste for Blood opens Overpower's, RLM-540).
     *
     * @param spell the spell whose window opens
     */
    public record OpenWindow(ResourceKey<Spell> spell) implements Action {
        public OpenWindow {
            Objects.requireNonNull(spell, "spell");
        }
    }

    /**
     * Casts a spell at the other entity (the one hit, or the attacker), or for a kill at the point where the victim
     * died; the spell's targeting decides what it acts on, so a {@code self} spell takes the holder.
     *
     * @param spell the spell
     * @param level the spell level, at most the spell's {@code max_level}
     */
    public record CastSpell(ResourceKey<Spell> spell, int level) implements Action {
        public CastSpell {
            Objects.requireNonNull(spell, "spell");
            if (level < 1) {
                throw new IllegalArgumentException("A spell level is 1 or more: " + level);
            }
        }
    }

    /**
     * Gives a status effect to the holder or to the other entity (never to a victim that died).
     *
     * @param effect the status effect
     * @param durationTicks how long it lasts
     * @param amplifier its strength above the first
     * @param onSelf whether the holder gets it rather than the other entity
     */
    public record ApplyEffect(Holder<MobEffect> effect, int durationTicks, int amplifier, boolean onSelf)
            implements Action {
        public ApplyEffect {
            Objects.requireNonNull(effect, "effect");
            if (durationTicks < 1 || amplifier < 0) {
                throw new IllegalArgumentException(
                        "An effect lasts a tick or more at an amplifier of 0 or more: " + durationTicks + ", "
                                + amplifier);
            }
        }
    }
}
