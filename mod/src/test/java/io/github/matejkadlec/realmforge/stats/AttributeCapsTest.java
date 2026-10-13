package io.github.matejkadlec.realmforge.stats;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.matejkadlec.realmforge.generated.GeneratedStats;
import io.github.matejkadlec.realmforge.mixin.RangedAttributeAccessor;
import java.util.Map;
import java.util.stream.Collectors;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.neoforged.neoforge.common.NeoForgeMod;
import org.junit.jupiter.api.Test;

/** The test run loads the mod, so its constructor has raised the caps (architecture 8.4). */
class AttributeCapsTest {
    /** Vanilla's caps in 26.1.2 ({@code Attributes}). */
    private static final Map<Holder<Attribute>, Double> VANILLA = Map.of(
            Attributes.ARMOR, 30.0,
            Attributes.ARMOR_TOUGHNESS, 20.0,
            Attributes.MAX_HEALTH, 1024.0,
            Attributes.ATTACK_DAMAGE, 2048.0);

    @Test
    void theFourVanillaCapsAreRaised() {
        assertEquals(VANILLA.keySet(), AttributeCaps.RAISED.stream()
                .map(AttributeCaps.Raised::attribute)
                .collect(Collectors.toSet()));
        for (AttributeCaps.Raised raised : AttributeCaps.RAISED) {
            String name = raised.attribute().getRegisteredName();
            assertTrue(raised.max() > VANILLA.get(raised.attribute()), name + " goes past vanilla's cap");
            assertEquals(raised.max(), ((RangedAttribute) raised.attribute().value()).getMaxValue(), name);
            assertEquals(raised.max(), AttributeCaps.max(raised.attribute()), name);
        }
    }

    @Test
    void valuesPastTheVanillaCapsHoldAndTheRaisedCapsStillClamp() {
        assertEquals(5000.0, Attributes.MAX_HEALTH.value().sanitizeValue(5000));
        assertEquals(100.0, Attributes.ARMOR.value().sanitizeValue(100));
        assertEquals(AttributeCaps.RAISED_MAX, Attributes.ARMOR.value().sanitizeValue(1e12));
        assertEquals(1.0, Attributes.MAX_HEALTH.value().sanitizeValue(0), "the minimum stays vanilla's");
    }

    @Test
    void otherCapsStayAsTheyAre() {
        assertEquals(2048.0, AttributeCaps.max(Attributes.MAX_ABSORPTION));
        assertEquals(1024.0, AttributeCaps.max(Attributes.MOVEMENT_SPEED));
    }

    @Test
    void maxReadsOurStatsAndOtherAttributeKinds() {
        assertEquals(100_000.0, AttributeCaps.max(GeneratedStats.STRENGTH), "points, from content/stats/");
        assertEquals(1.0, AttributeCaps.max(GeneratedStats.CRIT_CHANCE), "a share, as a fraction");
        assertEquals(1.0, AttributeCaps.max(NeoForgeMod.CREATIVE_FLIGHT), "a boolean attribute");
    }

    @Test
    void raiseNeverLowersACapAnotherModRaisedFurther() {
        RangedAttribute armor = (RangedAttribute) Attributes.ARMOR.value();
        RangedAttributeAccessor accessor = (RangedAttributeAccessor) armor;
        try {
            accessor.realmforge$setMaxValue(2 * AttributeCaps.RAISED_MAX);
            AttributeCaps.raise();
            assertEquals(2 * AttributeCaps.RAISED_MAX, armor.getMaxValue());
        } finally {
            accessor.realmforge$setMaxValue(AttributeCaps.RAISED_MAX);
        }
    }
}
