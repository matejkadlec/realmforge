package io.github.matejkadlec.realmforge.spell;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import net.minecraft.resources.ResourceKey;

/**
 * What the spell bar shows (architecture 11.6, 18; C3b, RLM-25): for each slot, the bar's eight and the racial one,
 * its spell, how much of the cooldown that blocks it is left for the sweep (its own or the global cooldown, whichever
 * ends later), whether the player has enough of its resource, whether it is the spell being cast, and how long the window
 * of a spell usable only for a while is still open (RLM-540), and whether an aura spell is on (RLM-595). A view model in
 * common code, so the HUD layer and the spellbook only draw it.
 *
 * @param slots by slot: the bar's 0 to 7, then {@link Spellbook#RACIAL_SLOT}
 */
public record SpellBarView(List<Slot> slots) {
    /**
     * One slot.
     *
     * @param key the spell in it, if any
     * @param spell the spell's entry, while it exists
     * @param level the level the player knows it at, 0 when they do not know it
     * @param cooldown the share of the blocking cooldown left, from 1 when it starts to 0 when it ends
     * @param cooldownTicks the ticks left of the spell's own cooldown, 0 when it is off it
     * @param affordable whether the player has enough of its resource now
     * @param casting whether it is the spell being cast
     * @param windowTicks a spell with a window (RLM-540), or with a target health condition while the current target
     *                    meets it (Execute, RLM-543): the ticks it is still open, 0 while it is closed;
     *                    {@link #NO_WINDOW} for a spell without one
     * @param aura whether an aura spell is on (RLM-595); {@link AuraState#NONE} for any other spell
     */
    public record Slot(Optional<ResourceKey<Spell>> key, Optional<Spell> spell, int level, float cooldown,
            int cooldownTicks, boolean affordable, boolean casting, int windowTicks, AuraState aura) {
        /** {@link #windowTicks} of a spell usable at any time. */
        public static final int NO_WINDOW = -1;
        public static final Slot EMPTY = new Slot(Optional.empty(), Optional.empty(), 0, 0, 0, true, false);

        /** A slot whose spell has no window. */
        public Slot(Optional<ResourceKey<Spell>> key, Optional<Spell> spell, int level, float cooldown,
                int cooldownTicks, boolean affordable, boolean casting) {
            this(key, spell, level, cooldown, cooldownTicks, affordable, casting, NO_WINDOW);
        }

        /** A slot whose spell is no aura. */
        public Slot(Optional<ResourceKey<Spell>> key, Optional<Spell> spell, int level, float cooldown,
                int cooldownTicks, boolean affordable, boolean casting, int windowTicks) {
            this(key, spell, level, cooldown, cooldownTicks, affordable, casting, windowTicks, AuraState.NONE);
        }
    }

    /** Whether a slot's aura spell is on (RLM-595). */
    public enum AuraState {
        /** Not an aura spell. */
        NONE,
        /** An aura that is off. */
        OFF,
        /** An aura that is on, its bonus applying. */
        ON,
        /** An aura that is on while its weapon condition is unmet: its bonus pauses, the aura stays on. */
        PAUSED
    }

    public SpellBarView {
        slots = List.copyOf(slots);
    }

    /**
     * The bar of a player with {@code book} and {@code combat} at game time {@code now}, every aura's weapon condition
     * met. {@code spells} finds a spell's entry; {@code cooldownReduction} is the player's Cooldown Reduction, which
     * sets how long their own cooldowns run; {@code globalCooldownTicks} is how long the global cooldown that runs now
     * lasted when it began.
     */
    public static SpellBarView of(Spellbook book, CombatState combat, long now,
            Function<ResourceKey<Spell>, Optional<Spell>> spells, double cooldownReduction, int globalCooldownTicks) {
        return of(book, combat, now, spells, cooldownReduction, globalCooldownTicks, aura -> true);
    }

    /**
     * The bar as {@link #of(Spellbook, CombatState, long, Function, double, int)} has it, with {@code conditionMet}
     * telling whether an aura's weapon condition holds now (RLM-595: a paused aura). An aura's cooldown is the one
     * every aura shares, which Cooldown Reduction never shortens, and the global cooldown never sweeps it.
     */
    public static SpellBarView of(Spellbook book, CombatState combat, long now,
            Function<ResourceKey<Spell>, Optional<Spell>> spells, double cooldownReduction, int globalCooldownTicks,
            Predicate<Aura> conditionMet) {
        List<Slot> slots = new ArrayList<>(Spellbook.SLOT_COUNT);
        Optional<ResourceKey<Spell>> casting = combat.currentCast().map(CombatState.Cast::spell);
        for (int index = 0; index < Spellbook.SLOT_COUNT; index++) {
            Optional<ResourceKey<Spell>> key = book.slot(index);
            if (key.isEmpty()) {
                slots.add(Slot.EMPTY);
                continue;
            }
            Optional<Spell> spell = spells.apply(key.get());
            int level = book.level(key.get()).orElse(0);
            long ownEnd = combat.cooldowns().getOrDefault(key.get(), 0L);
            int own = (int) Math.max(0, ownEnd - now);
            float cooldown = 0;
            boolean affordable = true;
            int window = Slot.NO_WINDOW;
            AuraState aura = AuraState.NONE;
            if (spell.isPresent()) {
                Spell value = spell.get();
                int ownTotal = value.isAura() ? Aura.COOLDOWN_TICKS
                        : SpellScaling.cooldownTicks(value.cooldownTicksAt(Math.max(1, level)), cooldownReduction);
                int global = value.globalCooldownTicks() > 0 && !value.isAura()
                        ? (int) Math.max(0, combat.globalCooldownEnd() - now) : 0;
                cooldown = own >= global ? share(own, ownTotal) : share(global, globalCooldownTicks);
                affordable = value.cost().flatMap(cost -> cost.resource().unwrapKey())
                        .map(type -> combat.resource(type) + 1e-4 >= value.startCostAt(Math.max(1, level)))
                        .orElse(true);
                // A target health condition lights up like a window while the current target meets it (Windows).
                if (value.conditions().window().isPresent() || value.conditions().targetHealthBelow().isPresent()) {
                    window = (int) Math.max(0, combat.windows().getOrDefault(key.get(), 0L) - now);
                }
                if (value.aura().isPresent()) {
                    aura = !book.isOn(key.get()) ? AuraState.OFF
                            : conditionMet.test(value.aura().get()) ? AuraState.ON : AuraState.PAUSED;
                }
            }
            slots.add(new Slot(key, spell, level, cooldown, own, affordable, casting.equals(key), window, aura));
        }
        return new SpellBarView(slots);
    }

    /** {@code left} of {@code total}, between 0 and 1; a total shorter than what is left counts as what is left. */
    private static float share(int left, int total) {
        return left <= 0 ? 0 : (float) left / Math.max(left, total);
    }
}
