package io.github.matejkadlec.realmforge.stats;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.matejkadlec.realmforge.Realmforge;
import java.util.List;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeMap;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.junit.jupiter.api.Test;

class RecomputedModifiersTest {
    private static final Operation ADD = Operation.ADD_VALUE;
    private static final Operation BASE = Operation.ADD_MULTIPLIED_BASE;
    private static final AttributeModifier HEALTH = modifier("stat/realmforge/0/vitality", 10.0, ADD);
    private static final AttributeModifier DAMAGE = modifier("stat/realmforge/0/strength", 0.2, BASE);

    @Test
    void wantedModifiersArePermanentSoTheyAreSaved() {
        AttributeMap attributes = attributes();
        assertTrue(replace(attributes, bonus(Attributes.MAX_HEALTH, HEALTH), bonus(Attributes.ATTACK_DAMAGE, DAMAGE)));

        assertEquals(30.0, attributes.getValue(Attributes.MAX_HEALTH));
        assertEquals(1.2, attributes.getValue(Attributes.ATTACK_DAMAGE), 1e-12);
        assertEquals(Set.of(HEALTH), instance(attributes, Attributes.MAX_HEALTH).getPermanentModifiers());
        assertTrue(attributes.pack().stream().anyMatch(packed -> packed.modifiers().contains(DAMAGE)), "saved");
    }

    @Test
    void anUnchangedSetChangesNothing() {
        AttributeMap attributes = attributes();
        replace(attributes, bonus(Attributes.MAX_HEALTH, HEALTH));
        assertFalse(replace(attributes, bonus(Attributes.MAX_HEALTH, HEALTH)));
        assertEquals(Set.of(HEALTH), instance(attributes, Attributes.MAX_HEALTH).getPermanentModifiers());
    }

    @Test
    void aChangedAmountReplacesTheModifier() {
        AttributeMap attributes = attributes();
        replace(attributes, bonus(Attributes.MAX_HEALTH, HEALTH));
        AttributeModifier more = modifier("stat/realmforge/0/vitality", 15.0, ADD);
        assertTrue(replace(attributes, bonus(Attributes.MAX_HEALTH, more)));
        assertEquals(Set.of(more), instance(attributes, Attributes.MAX_HEALTH).getPermanentModifiers());
        assertEquals(35.0, attributes.getValue(Attributes.MAX_HEALTH));
    }

    @Test
    void anOwnedModifierNoLongerWantedIsRemoved() {
        AttributeMap attributes = attributes();
        replace(attributes, bonus(Attributes.MAX_HEALTH, HEALTH), bonus(Attributes.ATTACK_DAMAGE, DAMAGE));
        assertTrue(replace(attributes, bonus(Attributes.ATTACK_DAMAGE, DAMAGE)));
        assertNull(instance(attributes, Attributes.MAX_HEALTH).getModifier(HEALTH.id()));
        assertEquals(20.0, attributes.getValue(Attributes.MAX_HEALTH));
        assertTrue(replace(attributes), "an empty set removes the rest");
        assertEquals(1.0, attributes.getValue(Attributes.ATTACK_DAMAGE));
    }

    @Test
    void aModifierMovedToAnotherAttributeLeavesTheOldOne() {
        AttributeMap attributes = attributes();
        replace(attributes, bonus(Attributes.MAX_HEALTH, HEALTH));
        replace(attributes, bonus(Attributes.ATTACK_DAMAGE, modifier("stat/realmforge/0/vitality", 1.0, ADD)));
        assertNull(instance(attributes, Attributes.MAX_HEALTH).getModifier(HEALTH.id()));
        assertEquals(2.0, attributes.getValue(Attributes.ATTACK_DAMAGE));
    }

    @Test
    void modifiersOfOtherSourcesStay() {
        AttributeMap attributes = attributes();
        AttributeModifier race = modifier("race/human/0", 4.0, ADD);
        AttributeModifier gear = new AttributeModifier(Identifier.withDefaultNamespace("armor.chestplate"), 2.0,
                ADD);
        instance(attributes, Attributes.MAX_HEALTH).addPermanentModifier(race);
        instance(attributes, Attributes.MAX_HEALTH).addTransientModifier(gear);
        replace(attributes, bonus(Attributes.MAX_HEALTH, HEALTH));
        replace(attributes);
        assertEquals(race, instance(attributes, Attributes.MAX_HEALTH).getModifier(race.id()));
        assertEquals(gear, instance(attributes, Attributes.MAX_HEALTH).getModifier(gear.id()));
        assertEquals(26.0, attributes.getValue(Attributes.MAX_HEALTH));
    }

    @Test
    void anAttributeTheEntityLacksIsSkipped() {
        AttributeMap attributes = attributes();
        assertFalse(replace(attributes, bonus(Attributes.LUCK, modifier("stat/realmforge/0/luck", 1.0, ADD))));
        assertFalse(attributes.hasAttribute(Attributes.LUCK));
    }

    private static boolean replace(AttributeMap attributes, StatBonus... wanted) {
        return RecomputedModifiers.replace(attributes, StatRules::isStatModifier, List.of(wanted));
    }

    private static AttributeMap attributes() {
        return new AttributeMap(AttributeSupplier.builder()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.ATTACK_DAMAGE, 1.0)
                .build());
    }

    private static AttributeInstance instance(AttributeMap attributes, Holder<Attribute> attribute) {
        return attributes.getInstance(attribute);
    }

    private static StatBonus bonus(Holder<Attribute> attribute, AttributeModifier modifier) {
        return new StatBonus(attribute, modifier);
    }

    private static AttributeModifier modifier(String path, double amount, Operation operation) {
        return new AttributeModifier(Realmforge.id(path), amount, operation);
    }
}
