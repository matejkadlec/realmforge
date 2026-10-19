package io.github.matejkadlec.realmforge.spell;

import java.util.Objects;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.effect.MobEffect;

/**
 * A proc (architecture 12): when its holder hits, crits, kills or is hit, it may cast a spell or apply a status effect,
 * then waits out its internal cooldown (research SP-15). Talents (C5) and items give procs through a
 * {@link Procs.Source}; {@link Procs} runs them.
 *
 * @param id names the proc: its internal cooldown and the proc origin its spell's hits carry
 * @param trigger what makes it fire
 * @param chance the chance it fires when its trigger happens, a share from 0 to 1
 * @param cooldownTicks its internal cooldown: how long after it fired it cannot fire again; 0 for none
 * @param action what it does
 */
public record Proc(Identifier id, Trigger trigger, double chance, int cooldownTicks, Action action) {
    public Proc {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(trigger, "trigger");
        Objects.requireNonNull(action, "action");
        if (!(chance >= 0 && chance <= 1) || cooldownTicks < 0) {
            throw new IllegalArgumentException(
                    "A proc fires at a chance from 0 to 1, with a cooldown of 0 ticks or more: " + chance + ", "
                            + cooldownTicks);
        }
    }

    /** What makes a proc fire, from its holder's side. */
    public enum Trigger {
        /** The holder dealt a hit (melee, ranged or a spell's) to another entity. */
        HIT,
        /** The holder dealt a critical hit. */
        CRIT,
        /** The holder killed another entity. */
        KILL,
        /** Another entity hit the holder. */
        HIT_TAKEN
    }

    /** What a proc does when it fires. */
    public sealed interface Action permits CastSpell, ApplyEffect {
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
