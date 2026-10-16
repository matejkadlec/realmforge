package io.github.matejkadlec.realmforge.character;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.matejkadlec.realmforge.Realmforge;
import io.github.matejkadlec.realmforge.character.StatPoints.Result;
import io.github.matejkadlec.realmforge.character.net.SpendStatPointPayload;
import io.github.matejkadlec.realmforge.core.Curve;
import io.github.matejkadlec.realmforge.generated.GeneratedStats;
import io.github.matejkadlec.realmforge.stats.StatBonus;
import io.github.matejkadlec.realmforge.testing.RoundTrip;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.junit.jupiter.api.Test;

/** RLM-196: the pure rules of stat points (architecture 10.2). The GameTests cover the player's side. */
class StatPointsTest {
    private static final Curve ONE_A_LEVEL = new Curve(Optional.empty(), List.of(new Curve.Point(2, 1)));
    private static final CharacterData CHARACTER = new CharacterData(
            Optional.of(ResourceKey.create(CharacterRegistries.RACE, Realmforge.id("human"))),
            List.of(ResourceKey.create(CharacterRegistries.CLASS, Realmforge.id("mage"))), 0, Optional.empty());

    @Test
    void levelsFrom2OnEarnTheCurvesPoints() {
        assertEquals(List.of(0, 1, 1, 1), List.of(StatPoints.at(ONE_A_LEVEL, 1), StatPoints.at(ONE_A_LEVEL, 2),
                StatPoints.at(ONE_A_LEVEL, 3), StatPoints.at(ONE_A_LEVEL, 60)));
        assertEquals(List.of(0, 1, 9, 59), List.of(StatPoints.earned(ONE_A_LEVEL, 1),
                StatPoints.earned(ONE_A_LEVEL, 2), StatPoints.earned(ONE_A_LEVEL, 10),
                StatPoints.earned(ONE_A_LEVEL, 60)));
    }

    /** The pipeline's {@code stat_points_at}: half up, never negative. */
    @Test
    void pointsRoundHalfUpAndAreNeverNegative() {
        Curve curve = new Curve(Optional.empty(), List.of(new Curve.Point(2, 0.5), new Curve.Point(4, 2.5),
                new Curve.Point(5, -3)));
        assertEquals(List.of(0, 1, 2, 3, 0), List.of(StatPoints.at(curve, 1), StatPoints.at(curve, 2),
                StatPoints.at(curve, 3), StatPoints.at(curve, 4), StatPoints.at(curve, 5)));
        assertEquals(6, StatPoints.earned(curve, 5));
    }

    @Test
    void pointsGoOnlyIntoPrimaryStatsWhileSomeAreUnspent() {
        assertEquals(Result.SPENT, StatPoints.check(CHARACTER, 1, key(GeneratedStats.STRENGTH)));
        assertEquals(Result.NO_POINTS, StatPoints.check(CHARACTER, 0, key(GeneratedStats.STRENGTH)));
        assertEquals(Result.NOT_A_PRIMARY_STAT, StatPoints.check(CHARACTER, 1, key(GeneratedStats.CRIT_CHANCE)));
        assertEquals(Result.NOT_A_PRIMARY_STAT, StatPoints.check(CHARACTER, 1, key(Attributes.MAX_HEALTH)));
        assertEquals(Result.NO_CHARACTER, StatPoints.check(CharacterData.NONE, 1, key(GeneratedStats.STRENGTH)));
        for (Holder<Attribute> stat : GeneratedStats.PRIMARY) {
            assertTrue(StatPoints.isPrimaryStat(key(stat)), stat.getRegisteredName());
        }
    }

    /** One modifier per stat with points, adding them, in id order; a stat that is not primary gives nothing. */
    @Test
    void spentPointsAreOneModifierPerStat() {
        CharacterData spent = CHARACTER.withStatPoints(Map.of(key(GeneratedStats.STRENGTH), 3,
                key(GeneratedStats.DEXTERITY), 1, key(Attributes.MAX_HEALTH), 2));
        assertEquals(List.of(
                        bonus(GeneratedStats.DEXTERITY, "stat_points/realmforge/dexterity", 1),
                        bonus(GeneratedStats.STRENGTH, "stat_points/realmforge/strength", 3)),
                StatPoints.of(spent));
        assertEquals(List.of(), StatPoints.of(CHARACTER));
    }

    @Test
    void ownsOnlyItsModifiers() {
        assertTrue(StatPoints.isStatPointModifier(Realmforge.id("stat_points/realmforge/strength")));
        assertFalse(StatPoints.isStatPointModifier(Realmforge.id("stat/realmforge/0/strength")));
        assertFalse(StatPoints.isStatPointModifier(Realmforge.id("class/realmforge/0/mage")));
        assertFalse(StatPoints.isStatPointModifier(Identifier.fromNamespaceAndPath("other", "stat_points/x")));
        assertFalse(CharacterBonuses.isCharacterModifier(StatPoints.modifierId(key(GeneratedStats.STRENGTH))),
                "the race and class source never removes a spent point's modifier");
    }

    @Test
    void spendPayloadRoundTrips() {
        SpendStatPointPayload payload = new SpendStatPointPayload(key(GeneratedStats.VITALITY));
        assertEquals(payload, RoundTrip.stream(SpendStatPointPayload.STREAM_CODEC, payload));
    }

    private static StatBonus bonus(Holder<Attribute> stat, String path, int points) {
        return new StatBonus(stat, new AttributeModifier(Realmforge.id(path), points,
                AttributeModifier.Operation.ADD_VALUE));
    }

    private static ResourceKey<Attribute> key(Holder<Attribute> attribute) {
        return attribute.unwrapKey().orElseThrow();
    }
}
