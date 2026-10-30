package io.github.matejkadlec.realmforge.combat;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.matejkadlec.realmforge.combat.AutoShoot.Weapon;
import org.junit.jupiter.api.Test;

class AutoShootTest {
    @Test
    void aBowIsReleasedOneTickPastFullDraw() {
        assertFalse(AutoShoot.ready(Weapon.BOW, 20, false), "full draw is 20 ticks; the server may count one less");
        assertTrue(AutoShoot.ready(Weapon.BOW, 21, false));
        assertTrue(AutoShoot.ready(Weapon.BOW, 60, false), "a bow held past it, as when the option was just turned on");
    }

    @Test
    void aCrossbowIsReleasedOnceLoadedWhateverItsTime() {
        assertFalse(AutoShoot.ready(Weapon.CROSSBOW, 100, false), "Quick Charge or not, the load decides");
        assertTrue(AutoShoot.ready(Weapon.CROSSBOW, 1, true));
    }

    @Test
    void anythingElseIsNeverReleased() {
        assertFalse(AutoShoot.ready(Weapon.NONE, 1000, true));
    }
}
