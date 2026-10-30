package io.github.matejkadlec.realmforge.qa.gametest;

import io.github.matejkadlec.realmforge.effect.Periodic;
import io.github.matejkadlec.realmforge.effect.StatusEffect;
import io.github.matejkadlec.realmforge.generated.GeneratedEffects;
import io.github.matejkadlec.realmforge.qa.generated.GeneratedEffectExpectations;
import io.github.matejkadlec.realmforge.qa.generated.GeneratedEffectExpectations.EffectExpectation;
import io.github.matejkadlec.realmforge.qa.generated.GeneratedEffectExpectations.ModifierExpectation;
import io.github.matejkadlec.realmforge.qa.generated.GeneratedEffectExpectations.PeriodicExpectation;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.locale.Language;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.ai.attributes.Attribute;

/**
 * C3c (RLM-26): every status effect of {@code content/effects/} is registered as its YAML says
 * ({@link GeneratedEffectExpectations}): category, particle colour, modifiers at the first level, damage or healing
 * over time, and a name in the lang file.
 */
public final class ContentEffectGameTests {
    private ContentEffectGameTests() {
    }

    @QaGameTest
    public static void effectsMatchTheirContent(GameTestHelper helper) {
        helper.assertFalse(GeneratedEffectExpectations.EFFECTS.isEmpty(), "no effect expectations were generated");
        helper.assertValueEqual(GeneratedEffects.ALL.size(), GeneratedEffectExpectations.EFFECTS.size(),
                "a registration per effect");
        for (EffectExpectation expected : GeneratedEffectExpectations.EFFECTS) {
            String id = expected.id();
            MobEffect effect = BuiltInRegistries.MOB_EFFECT.getValue(Identifier.parse(id));
            helper.assertTrue(effect instanceof StatusEffect, id + " is not registered as a StatusEffect");
            StatusEffect status = (StatusEffect) effect;
            helper.assertValueEqual(status.getDescriptionId(), expected.descriptionId(), id + " translation key");
            helper.assertTrue(Language.getInstance().has(status.getDescriptionId()), id + " has no name");
            helper.assertValueEqual(status.getCategory().name().toLowerCase(Locale.ROOT), expected.category(),
                    id + " category");
            helper.assertValueEqual(status.getColor(), expected.color(), id + " colour");
            helper.assertValueEqual(status.periodic().map(ContentEffectGameTests::periodic), expected.periodic(),
                    id + " periodic");
            helper.assertValueEqual(status.isStun(), expected.stun(), id + " stuns");
            List<ModifierExpectation> modifiers = new ArrayList<>();
            status.createModifiers(0, (attribute, modifier) -> modifiers.add(new ModifierExpectation(
                    attribute.getRegisteredName(), modifier.id().toString(), modifier.amount(),
                    modifier.operation().getSerializedName(), secondLevelAmount(status, attribute))));
            modifiers.sort(Comparator.comparing(ModifierExpectation::attribute));
            List<ModifierExpectation> wanted = new ArrayList<>(expected.modifiers());
            wanted.sort(Comparator.comparing(ModifierExpectation::attribute));
            helper.assertValueEqual(modifiers, wanted, id + " modifiers");
        }
        helper.succeed();
    }

    /** The amount {@code status} gives {@code attribute} at its second level (amplifier 1). */
    private static double secondLevelAmount(StatusEffect status, Holder<Attribute> attribute) {
        double[] amount = {Double.NaN};
        status.createModifiers(1, (modified, modifier) -> {
            if (modified.equals(attribute)) {
                amount[0] = modifier.amount();
            }
        });
        return amount[0];
    }

    private static PeriodicExpectation periodic(Periodic periodic) {
        return new PeriodicExpectation(periodic.kind().name().toLowerCase(Locale.ROOT),
                periodic.school().identifier().toString(), periodic.amount(), periodic.spellPower(),
                periodic.intervalTicks());
    }
}
