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
            SpellSchool.Kind.MAGIC, 0xFF7F1F, 0xFF7F1F,
            ResourceKey.create(Registries.DAMAGE_TYPE, Realmforge.id("fire")), Optional.empty(), Optional.empty());

    /**
     * A bolt with a 4 s cooldown; a ward off the global cooldown. Affordability needs registered resources, so
     * {@code SpellBarGameTests} covers it.
     */
    private static Spell spell(ResourceKey<Spell> key) {
        boolean bolt = key.equals(BOLT);
        return new Spell("n", "d", Optional.of(Holder.direct(FIRE)), Scaling.SPELL_POWER, SelfTargeting.INSTANCE,
                SpellCast.INSTANT, Optional.empty(), bolt ? 80 : 0, bolt ? 10 : 0, 1, CritRule.DEFAULT, false,
                List.of(), false, List.of(new DamageEffect(1, 0, 0)));
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
