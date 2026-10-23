package io.github.matejkadlec.realmforge.qa.gametest;

import io.github.matejkadlec.realmforge.generated.GeneratedMobs;
import io.github.matejkadlec.realmforge.mob.custom.CasterMob;
import io.github.matejkadlec.realmforge.mob.custom.CustomMob;
import io.github.matejkadlec.realmforge.mob.custom.CustomMobSpec;
import io.github.matejkadlec.realmforge.mob.custom.MeleeMob;
import io.github.matejkadlec.realmforge.qa.generated.GeneratedMobExpectations;
import io.github.matejkadlec.realmforge.qa.generated.GeneratedMobExpectations.MobExpectation;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.locale.Language;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * W3 (RLM-33): every custom mob of {@code content/mobs/} is registered as its YAML says
 * ({@link GeneratedMobExpectations}): a monster of its archetype's class, its size, its attributes at level 1, how it
 * idles, a caster's spells and distance, its loot table and a name in the lang file. {@link CustomMobGameTests} checks
 * what they do.
 */
public final class ContentMobGameTests {
    private static final double TOLERANCE = 1e-6;
    private static final Map<String, Class<? extends CustomMob>> ARCHETYPES =
            Map.of("melee", MeleeMob.class, "caster", CasterMob.class);

    private ContentMobGameTests() {
    }

    @QaGameTest
    public static void mobsMatchTheirContent(GameTestHelper helper) {
        helper.assertFalse(GeneratedMobExpectations.MOBS.isEmpty(), "no mob expectations were generated");
        helper.assertValueEqual(GeneratedMobs.ALL.size(), GeneratedMobExpectations.MOBS.size(),
                "a registration per mob");
        for (MobExpectation expected : GeneratedMobExpectations.MOBS) {
            String id = expected.id();
            EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.parse(id));
            helper.assertTrue(type != null && BuiltInRegistries.ENTITY_TYPE.getKey(type).toString().equals(id),
                    id + " is not registered");
            helper.assertValueEqual(type.getDescriptionId(), expected.descriptionId(), id + " translation key");
            helper.assertTrue(Language.getInstance().has(type.getDescriptionId()), id + " has no name");
            helper.assertValueEqual(type.getCategory(), MobCategory.MONSTER, id + " category");
            helper.assertValueEqual(type.getWidth(), expected.width(), id + " width");
            helper.assertValueEqual(type.getHeight(), expected.height(), id + " height");
            helper.assertValueEqual(type.getDefaultLootTable().map(key -> key.identifier().toString()).orElse("none"),
                    expected.lootTable(), id + " loot table");

            Entity entity = type.create(helper.getLevel(), EntitySpawnReason.COMMAND);
            helper.assertTrue(ARCHETYPES.get(expected.archetype()).isInstance(entity),
                    id + " is a " + expected.archetype() + ": " + entity);
            CustomMob mob = (CustomMob) entity;
            for (var attribute : List.of(
                    Map.entry(Attributes.MAX_HEALTH, expected.maxHealth()),
                    Map.entry(Attributes.ATTACK_DAMAGE, expected.attackDamage()),
                    Map.entry(Attributes.ARMOR, expected.armor()),
                    Map.entry(Attributes.MOVEMENT_SPEED, expected.movementSpeed()),
                    Map.entry(Attributes.FOLLOW_RANGE, expected.followRange()),
                    Map.entry(Attributes.KNOCKBACK_RESISTANCE, expected.knockbackResistance()))) {
                assertBase(helper, mob, attribute.getKey(), attribute.getValue());
            }
            CustomMobSpec spec = mob.spec();
            helper.assertValueEqual(spec.idle().name().toLowerCase(Locale.ROOT), expected.idle(), id + " idle");
            helper.assertValueEqual(spec.spells().stream().map(spell -> spell.identifier().toString()).toList(),
                    expected.spells(), id + " spells");
            helper.assertTrue(Math.abs(spec.keepDistance() - expected.keepDistance()) <= TOLERANCE,
                    id + " keeps " + spec.keepDistance() + " blocks, not " + expected.keepDistance());
            mob.discard();
        }
        helper.succeed();
    }

    private static void assertBase(GameTestHelper helper, CustomMob mob, Holder<Attribute> attribute, double want) {
        double got = mob.getAttributeBaseValue(attribute);
        helper.assertTrue(Math.abs(got - want) <= TOLERANCE, BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType())
                + " " + attribute.getRegisteredName() + ": expected " + want + ", got " + got);
    }
}
