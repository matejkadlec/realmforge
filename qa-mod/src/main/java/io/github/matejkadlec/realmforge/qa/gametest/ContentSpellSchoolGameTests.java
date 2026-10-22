package io.github.matejkadlec.realmforge.qa.gametest;

import io.github.matejkadlec.realmforge.Realmforge;
import io.github.matejkadlec.realmforge.combat.CombatRegistries;
import io.github.matejkadlec.realmforge.combat.SpellSchool;
import io.github.matejkadlec.realmforge.generated.GeneratedSpellSchools;
import io.github.matejkadlec.realmforge.qa.generated.GeneratedSpellSchoolExpectations;
import io.github.matejkadlec.realmforge.qa.generated.GeneratedSpellSchoolExpectations.SpellSchoolExpectation;
import java.util.Set;
import java.util.TreeSet;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.locale.Language;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.neoforged.neoforge.common.PercentageAttribute;

/**
 * Checks that every spell school in {@code content/spell_schools/} reached the datapack registry
 * {@code realmforge:spell_school} as the content says, with its damage type and tag entries (architecture 6.2, 9.1),
 * using expectations generated from the same YAML ({@link GeneratedSpellSchoolExpectations}).
 */
public final class ContentSpellSchoolGameTests {
    private ContentSpellSchoolGameTests() {
    }

    @QaGameTest
    public static void contentSpellSchoolsAreLoadedAsWritten(GameTestHelper helper) {
        helper.assertFalse(GeneratedSpellSchoolExpectations.SPELL_SCHOOLS.isEmpty(),
                "no spell school expectations were generated");
        Registry<SpellSchool> schools = helper.getLevel().registryAccess().lookupOrThrow(CombatRegistries.SPELL_SCHOOL);
        Registry<DamageType> damageTypes = helper.getLevel().registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE);

        Set<String> expectedIds = new TreeSet<>();
        GeneratedSpellSchoolExpectations.SPELL_SCHOOLS.forEach(expected -> expectedIds.add(expected.id()));
        Set<String> loadedIds = new TreeSet<>();
        schools.keySet().stream()
                .filter(id -> id.getNamespace().equals(Realmforge.MOD_ID))
                .forEach(id -> loadedIds.add(id.toString()));
        helper.assertValueEqual(loadedIds, expectedIds, "schools in realmforge:spell_school");

        for (SpellSchoolExpectation expected : GeneratedSpellSchoolExpectations.SPELL_SCHOOLS) {
            SpellSchool school = schools.getValue(Identifier.parse(expected.id()));
            helper.assertTrue(school != null, "school is not loaded: " + expected.id());
            helper.assertValueEqual(school.name(), expected.nameKey(), expected.id() + " name");
            helper.assertTrue(Language.getInstance().has(expected.nameKey()),
                    expected.id() + ": no en_us text for " + expected.nameKey());
            helper.assertValueEqual(school.magic(), expected.magic(), expected.id() + " is magic");
            helper.assertValueEqual(school.color(), expected.color(), expected.id() + " colour");
            helper.assertValueEqual(school.combatTextColor(), expected.combatTextColor(),
                    expected.id() + " combat text colour");
            helper.assertValueEqual(school.power().map(Holder::getRegisteredName), expected.power(),
                    expected.id() + " power");
            helper.assertValueEqual(school.resistance().map(Holder::getRegisteredName), expected.resistance(),
                    expected.id() + " resistance");

            helper.assertValueEqual(school.damageType().identifier().toString(), expected.damageType(),
                    expected.id() + " damage type");
            Holder<DamageType> type = damageTypes.get(school.damageType()).orElse(null);
            helper.assertTrue(type != null, expected.id() + ": its damage type is not loaded");
            helper.assertValueEqual(type.value().msgId(), expected.messageId(), expected.id() + " message id");
            helper.assertTrue(Language.getInstance().has("death.attack." + expected.messageId()),
                    expected.id() + ": no death message");
            helper.assertTrue(type.is(DamageTypeTags.BYPASSES_COOLDOWN),
                    expected.id() + " is in #minecraft:bypasses_cooldown");
            helper.assertValueEqual(type.is(DamageTypeTags.BYPASSES_ARMOR), expected.magic(),
                    expected.id() + " is in #minecraft:bypasses_armor exactly when it is magic");
            school.power().ifPresent(power -> assertShare(helper, power, expected.id() + " power"));
            school.resistance().ifPresent(resistance -> assertShare(helper, resistance, expected.id() + " resistance"));
        }
        helper.succeed();
    }

    @QaGameTest
    public static void generatedSpellSchoolKeysResolve(GameTestHelper helper) {
        Registry<SpellSchool> schools = helper.getLevel().registryAccess().lookupOrThrow(CombatRegistries.SPELL_SCHOOL);
        helper.assertValueEqual(GeneratedSpellSchools.ALL.size(), GeneratedSpellSchoolExpectations.SPELL_SCHOOLS.size(),
                "generated school keys");
        for (ResourceKey<SpellSchool> key : GeneratedSpellSchools.ALL) {
            helper.assertTrue(schools.get(key).isPresent(), "generated key does not resolve: " + key.identifier());
        }
        helper.succeed();
    }

    /** A school's power and resistance are percentage attributes: shares, as the damage pipeline reads them. */
    private static void assertShare(GameTestHelper helper, Holder<Attribute> stat, String what) {
        helper.assertTrue(stat.value() instanceof PercentageAttribute,
                what + " is a share: " + stat.getRegisteredName());
    }
}
