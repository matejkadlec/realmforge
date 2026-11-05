package io.github.matejkadlec.realmforge.spell;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.matejkadlec.realmforge.Realmforge;
import io.github.matejkadlec.realmforge.combat.CritRule;
import io.github.matejkadlec.realmforge.combat.SpellSchool;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import org.junit.jupiter.api.Test;

/** What the spell bar shows for each slot (C3b, RLM-25). */
class SpellBarViewTest {
    private static final ResourceKey<Spell> BOLT = ResourceKey.create(SpellRegistries.SPELL, Realmforge.id("bolt"));
    private static final ResourceKey<Spell> WARD = ResourceKey.create(SpellRegistries.SPELL, Realmforge.id("ward"));
    private static final SpellSchool FIRE = new SpellSchool("spell_school.realmforge.fire.name",
            SpellSchool.Kind.MAGIC, 0xFF7F1F, 0xFF7F1F, Optional.empty(),
            ResourceKey.create(Registries.DAMAGE_TYPE, Realmforge.id("fire")), Optional.empty(), Optional.empty());

    /**
     * A bolt with a 4 s cooldown; a ward off the global cooldown. Affordability needs registered resources, so
     * {@code SpellBarGameTests} covers it.
     */
    private static Spell spell(ResourceKey<Spell> key) {
        boolean bolt = key.equals(BOLT);
        return new Spell("n", "d", Optional.of(Holder.direct(FIRE)), Scaling.SPELL_POWER, SelfTargeting.INSTANCE,
                SpellCast.INSTANT, Optional.empty(), bolt ? 80 : 0, bolt ? 10 : 0, 1, CritRule.DEFAULT, false,
                List.of(), false, List.of(new DamageEffect(1, 0, Coefficient.NONE)), Optional.empty(),
                SpellConditions.NONE);
    }

    private static final ResourceKey<Spell> STANCE = ResourceKey.create(SpellRegistries.SPELL,
            Realmforge.id("stance"));

    /** An aura (RLM-595) of the group {@code stance}, with a bonus and nothing else. */
    private static Spell aura() {
        return new Spell("n", "d", Optional.empty(), Scaling.SPELL_POWER, SelfTargeting.INSTANCE, SpellCast.INSTANT,
                Optional.empty(), 0, Spell.DEFAULT_GLOBAL_COOLDOWN_TICKS, 1, CritRule.DEFAULT, false, List.of(),
                false, List.of(), Optional.empty(), SpellConditions.NONE, 0,
                Optional.of(new Aura("stance", new Passive(Optional.empty(), List.of(), List.of(), Optional.empty(),
                        Optional.empty(), List.of()))));
    }

    /**
     * RLM-595: an aura's slot shows it off, on, or paused by its weapon condition; its sweep runs over the cooldown
     * every aura shares, whatever the Cooldown Reduction, and never over the global cooldown.
     */
    @Test
    void anAurasSlotShowsItsStateAndTheSharedCooldown() {
        Spellbook book = new Spellbook(Map.of(STANCE, 1), Map.of(0, STANCE));
        CombatState combat = new CombatState();
        combat.setGlobalCooldownEnd(1000 + 5);
        SpellBarView off = SpellBarView.of(book, combat, 1000, key -> Optional.of(aura()), 0.5, 10);
        assertEquals(SpellBarView.AuraState.OFF, off.slots().get(0).aura());
        assertEquals(0F, off.slots().get(0).cooldown(), "the global cooldown never sweeps an aura");

        Spellbook on = book.withAura(STANCE, true);
        combat.setCooldownEnd(STANCE, 1000 + Aura.COOLDOWN_TICKS / 2);
        SpellBarView view = SpellBarView.of(on, combat, 1000, key -> Optional.of(aura()), 0.5, 10);
        assertEquals(SpellBarView.AuraState.ON, view.slots().get(0).aura());
        assertEquals(0.5F, view.slots().get(0).cooldown(), 1e-6, "half of the 3 minutes, Cooldown Reduction or not");
        SpellBarView paused = SpellBarView.of(on, combat, 1000, key -> Optional.of(aura()), 0, 10, aura -> false);
        assertEquals(SpellBarView.AuraState.PAUSED, paused.slots().get(0).aura());
        assertEquals(SpellBarView.AuraState.NONE, SpellBarView.of(new Spellbook(Map.of(BOLT, 1), Map.of(0, BOLT)),
                combat, 1000, key -> Optional.of(spell(key)), 0, 10).slots().get(0).aura(), "an active spell");
    }

    @Test
    void emptySlotsStayEmpty() {
        SpellBarView view = SpellBarView.of(Spellbook.EMPTY, new CombatState(), 0, key -> Optional.empty(), 0, 10);
        assertEquals(Spellbook.SLOT_COUNT, view.slots().size(), "the bar's eight and the racial slot");
        assertTrue(view.slots().stream().allMatch(slot -> slot.equals(SpellBarView.Slot.EMPTY)));
    }

    @Test
    void theSweepFollowsTheCooldownThatEndsLater() {
        Spellbook book = new Spellbook(Map.of(BOLT, 1, WARD, 1), Map.of(0, BOLT, 1, WARD));
        CombatState combat = new CombatState();
        combat.setCooldownEnd(BOLT, 1000 + 40);
        combat.setGlobalCooldownEnd(1000 + 5);
        SpellBarView view = SpellBarView.of(book, combat, 1000, key -> Optional.of(spell(key)), 0, 10);
        assertEquals(0.5F, view.slots().get(0).cooldown(), 1e-6, "half of the 4 s cooldown is left");
        assertEquals(40, view.slots().get(0).cooldownTicks());
        assertEquals(0F, view.slots().get(1).cooldown(), "the ward is off the global cooldown");
        SpellBarView reduced = SpellBarView.of(book, combat, 1000, key -> Optional.of(spell(key)), 0.5, 10);
        assertEquals(1F, reduced.slots().get(0).cooldown(), 1e-6, "Cooldown Reduction halves the whole");
        combat.setCooldownEnd(BOLT, 1000 + 2);
        SpellBarView global = SpellBarView.of(book, combat, 1000, key -> Optional.of(spell(key)), 0, 10);
        assertEquals(0.5F, global.slots().get(0).cooldown(), 1e-6, "the global cooldown ends later: 5 of 10");
    }

    @Test
    void theSpellBeingCastIsMarked() {
        Spellbook book = new Spellbook(Map.of(BOLT, 1), Map.of(Spellbook.RACIAL_SLOT, BOLT));
        CombatState combat = new CombatState();
        combat.startCast(new CombatState.Cast(BOLT, 0, 20, false));
        SpellBarView view = SpellBarView.of(book, combat, 5, key -> Optional.of(spell(key)), 0, 10);
        assertTrue(view.slots().get(Spellbook.RACIAL_SLOT).casting());
    }
}
