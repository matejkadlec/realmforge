package io.github.matejkadlec.realmforge.character;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.matejkadlec.realmforge.Realmforge;
import io.github.matejkadlec.realmforge.character.net.CreateCharacterPayload;
import io.github.matejkadlec.realmforge.stats.StatBonus;
import io.github.matejkadlec.realmforge.testing.RoundTrip;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.junit.jupiter.api.Test;

class CharacterBonusesTest {
    private static final Identifier WARRIOR = Realmforge.id("warrior");
    private static final List<CharacterModifier> MODIFIERS = List.of(
            new CharacterModifier(Attributes.ARMOR, Operation.ADD_VALUE, 5, 1),
            new CharacterModifier(Attributes.LUCK, Operation.ADD_VALUE, 2, 0),
            new CharacterModifier(Attributes.ATTACK_SPEED, Operation.ADD_MULTIPLIED_BASE, 0, 0.01));

    @Test
    void amountsGrowPerLevelAbove1() {
        CharacterModifier growing = MODIFIERS.getFirst();
        assertEquals(5, growing.amountAt(1));
        assertEquals(6, growing.amountAt(2));
        assertEquals(14, growing.amountAt(10));
        assertEquals(2, MODIFIERS.get(1).amountAt(30));
    }

    @Test
    void bonusesAtLevel1SkipAModifierThatStartsAt0() {
        assertEquals(List.of(
                        bonus("class/realmforge/0/warrior", Attributes.ARMOR, 5, Operation.ADD_VALUE),
                        bonus("class/realmforge/1/warrior", Attributes.LUCK, 2, Operation.ADD_VALUE)),
                CharacterBonuses.bonuses(CharacterBonuses.CLASS_PREFIX, WARRIOR, MODIFIERS, 1));
    }

    @Test
    void bonusesAtALaterLevelGrow() {
        List<StatBonus> bonuses = CharacterBonuses.bonuses(CharacterBonuses.CLASS_PREFIX, WARRIOR, MODIFIERS, 11);
        assertEquals(3, bonuses.size());
        assertEquals(15, bonuses.get(0).modifier().amount());
        assertEquals(0.1, bonuses.get(2).modifier().amount(), 1e-12);
        assertEquals(Operation.ADD_MULTIPLIED_BASE, bonuses.get(2).modifier().operation());
    }

    /** A path with slashes comes last, so ids from different entries and positions never collide. */
    @Test
    void modifierIdsNameTheEntryAndPosition() {
        assertEquals(Realmforge.id("race/realmforge/2/human"),
                CharacterBonuses.modifierId(CharacterBonuses.RACE_PREFIX, Realmforge.id("human"), 2));
        assertEquals(Realmforge.id("class/other/0/a/b"), CharacterBonuses.modifierId(CharacterBonuses.CLASS_PREFIX,
                Identifier.fromNamespaceAndPath("other", "a/b"), 0));
    }

    @Test
    void ownsOnlyRaceAndClassModifiers() {
        assertTrue(CharacterBonuses.isCharacterModifier(Realmforge.id("race/realmforge/0/human")));
        assertTrue(CharacterBonuses.isCharacterModifier(Realmforge.id("class/realmforge/0/mage")));
        assertFalse(CharacterBonuses.isCharacterModifier(Realmforge.id("stat/realmforge/0/strength")));
        assertFalse(CharacterBonuses.isCharacterModifier(Identifier.fromNamespaceAndPath("other", "race/x")));
    }

    @Test
    void createPayloadRoundTrips() {
        CreateCharacterPayload payload = new CreateCharacterPayload(
                ResourceKey.create(CharacterRegistries.RACE, Realmforge.id("human")),
                ResourceKey.create(CharacterRegistries.CLASS, Realmforge.id("mage")));
        assertEquals(payload, RoundTrip.stream(CreateCharacterPayload.STREAM_CODEC, payload));
    }

    private static StatBonus bonus(String path, Holder<Attribute> attribute, double amount, Operation operation) {
        return new StatBonus(attribute, new AttributeModifier(Realmforge.id(path), amount, operation));
    }
}
