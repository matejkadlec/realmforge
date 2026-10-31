package io.github.matejkadlec.realmforge.spell;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import net.minecraft.resources.ResourceKey;

/**
 * What the spell bar shows (architecture 11.6, 18; C3b, RLM-25): for each slot, the bar's eight and the racial one,
 * its spell, how much of the cooldown that blocks it is left for the sweep (its own or the global cooldown, whichever
 * ends later), whether the player has enough of its resource, whether it is the spell being cast, and how long the window
 * of a spell usable only for a while is still open (RLM-540). A view model in common code, so the HUD layer and the
 * spellbook only draw it.
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
     * @param windowTicks a spell with a window (RLM-540): the ticks it is still open, 0 while it is closed;
     *                    {@link #NO_WINDOW} for a spell without one
     */
    public record Slot(Optional<ResourceKey<Spell>> key, Optional<Spell> spell, int level, float cooldown,
            int cooldownTicks, boolean affordable, boolean casting, int windowTicks) {
        /** {@link #windowTicks} of a spell usable at any time. */
        public static final int NO_WINDOW = -1;
        public static final Slot EMPTY = new Slot(Optional.empty(), Optional.empty(), 0, 0, 0, true, false);

        /** A slot whose spell has no window. */
        public Slot(Optional<ResourceKey<Spell>> key, Optional<Spell> spell, int level, float cooldown,
                int cooldownTicks, boolean affordable, boolean casting) {
            this(key, spell, level, cooldown, cooldownTicks, affordable, casting, NO_WINDOW);
        }
    }

    public SpellBarView {
        slots = List.copyOf(slots);
    }

    /**
     * The bar of a player with {@code book} and {@code combat} at game time {@code now}. {@code spells} finds a spell's
     * entry; {@code cooldownReduction} is the player's Cooldown Reduction, which sets how long their own cooldowns
     * run; {@code globalCooldownTicks} is how long the global cooldown that runs now lasted when it began.
     */
    public static SpellBarView of(Spellbook book, CombatState combat, long now,
            Function<ResourceKey<Spell>, Optional<Spell>> spells, double cooldownReduction, int globalCooldownTicks) {
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
            if (spell.isPresent()) {
                Spell value = spell.get();
                int ownTotal = SpellScaling.cooldownTicks(value.cooldownTicks(), cooldownReduction);
                int global = value.globalCooldownTicks() > 0 ? (int) Math.max(0, combat.globalCooldownEnd() - now) : 0;
                cooldown = own >= global ? share(own, ownTotal) : share(global, globalCooldownTicks);
                affordable = value.cost().flatMap(cost -> cost.resource().unwrapKey())
                        .map(type -> combat.resource(type) + 1e-4 >= value.costAt(Math.max(1, level))).orElse(true);
                if (value.conditions().window().isPresent()) {
                    window = (int) Math.max(0, combat.windows().getOrDefault(key.get(), 0L) - now);
                }
            }
            slots.add(new Slot(key, spell, level, cooldown, own, affordable, casting.equals(key), window));
        }
        return new SpellBarView(slots);
    }

    /** {@code left} of {@code total}, between 0 and 1; a total shorter than what is left counts as what is left. */
    private static float share(int left, int total) {
        return left <= 0 ? 0 : (float) left / Math.max(left, total);
    }
}
