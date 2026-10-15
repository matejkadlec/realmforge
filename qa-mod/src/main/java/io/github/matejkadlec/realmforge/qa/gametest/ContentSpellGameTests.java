package io.github.matejkadlec.realmforge.qa.gametest;

import io.github.matejkadlec.realmforge.Realmforge;
import io.github.matejkadlec.realmforge.generated.GeneratedSpells;
import io.github.matejkadlec.realmforge.qa.generated.GeneratedSpellExpectations;
import io.github.matejkadlec.realmforge.qa.generated.GeneratedSpellExpectations.EffectExpectation;
import io.github.matejkadlec.realmforge.qa.generated.GeneratedSpellExpectations.SpellExpectation;
import io.github.matejkadlec.realmforge.spell.AllyTargeting;
import io.github.matejkadlec.realmforge.spell.DamageEffect;
import io.github.matejkadlec.realmforge.spell.EnemyTargeting;
import io.github.matejkadlec.realmforge.spell.HealEffect;
import io.github.matejkadlec.realmforge.spell.Spell;
import io.github.matejkadlec.realmforge.spell.SpellEffect;
import io.github.matejkadlec.realmforge.spell.SpellRegistries;
import io.github.matejkadlec.realmforge.spell.SpellTargeting;
import java.util.Set;
import java.util.TreeSet;
import net.minecraft.core.Registry;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;

/**
 * Checks that every spell in {@code content/spells/} reached the datapack registry {@code realmforge:spell} as the
 * content says, in game units (architecture 6.4, 11.1), using expectations generated from the same YAML
 * ({@link GeneratedSpellExpectations}). A field the codec misread (a renamed key read as its default) shows here.
 */
public final class ContentSpellGameTests {
    private ContentSpellGameTests() {
    }

    @QaGameTest
    public static void contentSpellsAreLoadedAsWritten(GameTestHelper helper) {
        helper.assertFalse(GeneratedSpellExpectations.SPELLS.isEmpty(), "no spell expectations were generated");
        Registry<Spell> spells = helper.getLevel().registryAccess().lookupOrThrow(SpellRegistries.SPELL);

        Set<String> expectedIds = new TreeSet<>();
        GeneratedSpellExpectations.SPELLS.forEach(expected -> expectedIds.add(expected.id()));
        Set<String> loadedIds = new TreeSet<>();
        spells.keySet().stream()
                .filter(id -> id.getNamespace().equals(Realmforge.MOD_ID))
                .forEach(id -> loadedIds.add(id.toString()));
        helper.assertValueEqual(loadedIds, expectedIds, "spells in realmforge:spell");

        for (SpellExpectation expected : GeneratedSpellExpectations.SPELLS) {
            Spell spell = spells.getValue(Identifier.parse(expected.id()));
            helper.assertTrue(spell != null, "spell is not loaded: " + expected.id());
            String id = expected.id();
            helper.assertValueEqual(spell.school().getRegisteredName(), expected.school(), id + " school");
            helper.assertValueEqual(spell.scalesWith().getSerializedName(), expected.scalesWith(), id + " scales with");
            helper.assertValueEqual(targetingType(spell.targeting()), expected.targeting(), id + " targeting");
            helper.assertValueEqual(range(spell.targeting()), expected.range(), id + " range");
            helper.assertValueEqual(spell.cast().type().getSerializedName(), expected.cast(), id + " cast");
            helper.assertValueEqual(spell.cost().map(cost -> cost.resource().getRegisteredName()),
                    expected.costResource(), id + " cost resource");
            helper.assertValueEqual(spell.cost().map(cost -> cost.amount()).orElse(0.0), expected.costAmount(),
                    id + " cost");
            helper.assertValueEqual(spell.cost().map(cost -> cost.perLevel()).orElse(0.0), expected.costPerLevel(),
                    id + " cost per level");
            helper.assertValueEqual(spell.cooldownTicks(), expected.cooldownTicks(), id + " cooldown");
            helper.assertValueEqual(spell.globalCooldownTicks(), expected.globalCooldownTicks(),
                    id + " global cooldown");
            helper.assertValueEqual(spell.maxLevel(), expected.maxLevel(), id + " max level");
            helper.assertValueEqual(spell.crit().canCrit(), expected.canCrit(), id + " can crit");
            helper.assertValueEqual(spell.crit().multiplier(), expected.critMultiplier(), id + " crit multiplier");
            helper.assertValueEqual(spell.crit().bonusChance(), expected.critBonusChance(), id + " bonus crit chance");
            helper.assertValueEqual(spell.npcOnly(), expected.npcOnly(), id + " npc only");
            helper.assertValueEqual(spell.effects().stream().map(ContentSpellGameTests::effect).toList(),
                    expected.effects(), id + " effects");
        }
        helper.succeed();
    }

    @QaGameTest
    public static void generatedSpellKeysResolve(GameTestHelper helper) {
        Registry<Spell> spells = helper.getLevel().registryAccess().lookupOrThrow(SpellRegistries.SPELL);
        helper.assertValueEqual(GeneratedSpells.ALL.size(), GeneratedSpellExpectations.SPELLS.size(),
                "a key per spell");
        for (var key : GeneratedSpells.ALL) {
            helper.assertTrue(spells.get(key).isPresent(), "no spell for the generated key " + key.identifier());
        }
        helper.succeed();
    }

    private static String targetingType(SpellTargeting targeting) {
        return SpellRegistries.TARGETING_TYPES.getKey(targeting.codec()).toString();
    }

    private static double range(SpellTargeting targeting) {
        return switch (targeting) {
            case EnemyTargeting enemy -> enemy.range();
            case AllyTargeting ally -> ally.range();
            default -> 0.0;
        };
    }

    private static EffectExpectation effect(SpellEffect effect) {
        String type = SpellRegistries.EFFECT_TYPES.getKey(effect.codec()).toString();
        return switch (effect) {
            case DamageEffect damage -> new EffectExpectation(type, damage.amount(), damage.perLevel(),
                    damage.attackDamage());
            case HealEffect heal -> new EffectExpectation(type, heal.amount(), heal.perLevel(), heal.attackDamage());
            default -> throw new IllegalStateException("no expectation shape for " + type);
        };
    }
}
