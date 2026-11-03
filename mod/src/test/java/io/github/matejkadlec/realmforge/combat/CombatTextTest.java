package io.github.matejkadlec.realmforge.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import io.github.matejkadlec.realmforge.Realmforge;
import java.util.Optional;
import java.util.function.IntSupplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import org.junit.jupiter.api.Test;

/** RLM-553: the colour of a hit's number, and a magic school's darker crit. */
class CombatTextTest {
    private static final int ORANGE = 0xFFFFAA00;
    private static final IntSupplier PHYSICAL = () -> ORANGE;
    private static final SpellSchool FROST = school("frost", SpellSchool.Kind.MAGIC, 0xFF7FD4FF, Optional.of(0xFF2D7DFF));
    private static final SpellSchool SHADOW = school("shadow", SpellSchool.Kind.MAGIC, 0xFF9966CC, Optional.empty());
    private static final SpellSchool PHYSICAL_SCHOOL = school("physical", SpellSchool.Kind.PHYSICAL, ORANGE,
            Optional.of(0xFF123456));

    @Test
    void aMagicHitTakesTheSchoolsColour() {
        assertEquals(0xFF7FD4FF, CombatText.color(FROST, false, PHYSICAL));
        assertEquals(0xFF9966CC, CombatText.color(SHADOW, false, PHYSICAL));
    }

    @Test
    void aMagicCritTakesTheSchoolsCritColour() {
        assertEquals(0xFF2D7DFF, CombatText.color(FROST, true, PHYSICAL));
    }

    @Test
    void aSchoolWithoutACritColourCritsInItsColourDarkened() {
        int crit = CombatText.color(SHADOW, true, PHYSICAL);
        // 0x99, 0x66, 0xCC at 65 %: 0x63, 0x42, 0x85.
        assertEquals(0xFF634285, crit);
        assertNotEquals(CombatText.color(SHADOW, false, PHYSICAL), crit, "a crit never looks like a hit");
    }

    @Test
    void hitsThatAreNotMagicStayOrangeAndCritInRed() {
        assertEquals(ORANGE, CombatText.color(PHYSICAL_SCHOOL, false, PHYSICAL), "the Physical school's hit");
        assertEquals(CombatText.CRIT, CombatText.color(PHYSICAL_SCHOOL, true, PHYSICAL),
                "the Physical school's crit, whatever crit colour it names");
        assertEquals(ORANGE, CombatText.color(null, false, PHYSICAL), "a melee or ranged hit");
        assertEquals(CombatText.CRIT, CombatText.color(null, true, PHYSICAL), "a melee or ranged crit");
    }

    private static SpellSchool school(String id, SpellSchool.Kind kind, int color, Optional<Integer> crit) {
        return new SpellSchool("spell_school.realmforge." + id + ".name", kind, color, color, crit,
                ResourceKey.create(Registries.DAMAGE_TYPE, Realmforge.id(id)), Optional.empty(), Optional.empty());
    }
}
