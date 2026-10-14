package io.github.matejkadlec.realmforge.mob;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.matejkadlec.realmforge.Realmforge;
import io.github.matejkadlec.realmforge.core.Curve;
import io.github.matejkadlec.realmforge.core.Curve.Point;
import io.github.matejkadlec.realmforge.stats.StatBonus;
import java.util.List;
import java.util.Optional;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.junit.jupiter.api.Test;

class MobScalingTest {
    private static final Optional<Curve> HEALTH = curve(new Point(1, 1.0), new Point(5, 1.4), new Point(10, 2.0));
    private static final Optional<Curve> DAMAGE = curve(new Point(1, 1.0), new Point(10, 1.5));
    private static final Optional<Curve> ARMOR = curve(new Point(1, 0.0), new Point(5, 2.0));

    @Test
    void curvesGiveTheScalingAtTheLevel() {
        assertEquals(new MobScaling(1.4, 1.0 + 0.5 * 4 / 9, 2.0), MobScaling.at(5, HEALTH, DAMAGE, ARMOR));
        assertEquals(new MobScaling(2.0, 1.5, 2.0), MobScaling.at(10, HEALTH, DAMAGE, ARMOR));
    }

    @Test
    void levelOneOfTheShippedShapeAddsNothing() {
        MobScaling scaling = MobScaling.at(1, HEALTH, DAMAGE, ARMOR);
        assertEquals(MobScaling.NONE, scaling);
        assertEquals(List.of(), scaling.modifiers());
    }

    @Test
    void aMissingCurveKeepsItsPartUnscaled() {
        assertEquals(MobScaling.NONE, MobScaling.at(10, Optional.empty(), Optional.empty(), Optional.empty()));
        assertEquals(new MobScaling(2.0, 1.0, 0.0), MobScaling.at(10, HEALTH, Optional.empty(), Optional.empty()));
    }

    @Test
    void healthMultipliesTheBaseAndArmorAddsPoints() {
        assertEquals(List.of(
                        new StatBonus(Attributes.MAX_HEALTH, new AttributeModifier(MobScaling.HEALTH_MODIFIER, 1.4 - 1,
                                Operation.ADD_MULTIPLIED_BASE)),
                        new StatBonus(Attributes.ARMOR, new AttributeModifier(MobScaling.ARMOR_MODIFIER, 2.0,
                                Operation.ADD_VALUE))),
                new MobScaling(1.4, 1.2, 2.0).modifiers());
        assertEquals(List.of(new StatBonus(Attributes.ARMOR, new AttributeModifier(MobScaling.ARMOR_MODIFIER, 3.0,
                Operation.ADD_VALUE))), new MobScaling(1.0, 1.5, 3.0).modifiers());
    }

    @Test
    void theSourceOwnsOnlyItsOwnIds() {
        assertTrue(MobScaling.owns(MobScaling.HEALTH_MODIFIER));
        assertTrue(MobScaling.owns(Realmforge.id("mob_level/anything")));
        assertFalse(MobScaling.owns(Realmforge.id("stat/realmforge/0/vitality")));
        assertFalse(MobScaling.owns(Identifier.fromNamespaceAndPath("other", "mob_level/max_health")));
    }

    private static Optional<Curve> curve(Point... points) {
        return Optional.of(new Curve(Optional.empty(), List.of(points)));
    }
}
