package io.github.matejkadlec.realmforge.qa.gametest;

import io.github.matejkadlec.realmforge.boss.BossMob;
import io.github.matejkadlec.realmforge.generated.GeneratedMobs;
import io.github.matejkadlec.realmforge.mob.ai.KiteRules;
import io.github.matejkadlec.realmforge.mob.custom.CasterMob;
import io.github.matejkadlec.realmforge.mob.custom.CustomMob;
import io.github.matejkadlec.realmforge.mob.custom.CustomMobSpec;
import io.github.matejkadlec.realmforge.mob.custom.MeleeMob;
import io.github.matejkadlec.realmforge.qa.generated.GeneratedMobExpectations;
import io.github.matejkadlec.realmforge.qa.generated.GeneratedMobExpectations.MobExpectation;
import io.github.matejkadlec.realmforge.qa.generated.GeneratedMobExpectations.VariantExpectation;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.locale.Language;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * W3 (RLM-33): every custom mob of {@code content/mobs/} is registered as its YAML says
 * ({@link GeneratedMobExpectations}): a monster of its archetype's class, its size, its attributes at level 1, how it
 * idles and hits, a caster's spells and kiting, its sounds (registered events), its death particles, its entity type
 * tags, its variants with their loot tables, its loot table and a name in the lang file. {@link CustomMobGameTests}
 * checks what they do.
 */
public final class ContentMobGameTests {
    private static final double TOLERANCE = 1e-6;
    private static final Map<String, Class<? extends CustomMob>> ARCHETYPES =
            Map.of("melee", MeleeMob.class, "caster", CasterMob.class, "boss", BossMob.class);

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
            helper.assertValueEqual(spec.melee().attackIntervalTicks(), expected.attackIntervalTicks(),
                    id + " attack interval");
            assertClose(helper, spec.melee().knockUpBlocks(), expected.knockUpBlocks(), id + " knock-up");
            helper.assertValueEqual(spec.spells().stream().map(spell -> spell.identifier().toString()).toList(),
                    expected.spells(), id + " spells");
            assertClose(helper, spec.kiting().map(KiteRules::stepBlocks).orElse(0.0), expected.kitingStepBlocks(),
                    id + " kiting step");
            assertClose(helper, spec.kiting().map(KiteRules::speed).orElse(0.0), expected.kitingSpeed(),
                    id + " kiting speed");
            CustomMobSpec.Sounds sounds = spec.sounds();
            assertSound(helper, sounds.ambient(), expected.ambientSound(), id + " ambient sound");
            assertSound(helper, sounds.hurt(), expected.hurtSound(), id + " hurt sound");
            assertSound(helper, sounds.death(), expected.deathSound(), id + " death sound");
            assertSound(helper, sounds.step(), expected.stepSound(), id + " step sound");
            helper.assertValueEqual(sounds.silentSteps(), expected.silentSteps(), id + " silent steps");
            helper.assertValueEqual(spec.deathParticles().map(Identifier::toString).orElse(""),
                    expected.deathParticles(), id + " death particles");
            helper.assertValueEqual(spec.hands(), new CustomMobSpec.Hands(expected.handsAhead(), expected.handsUp()),
                    id + " hands");
            spec.deathParticles().ifPresent(block -> helper.assertTrue(BuiltInRegistries.BLOCK.containsKey(block),
                    id + " bursts into the particles of " + block + ", which is no block"));
            for (String tag : expected.tags()) {
                helper.assertTrue(type.builtInRegistryHolder().is(TagKey.create(Registries.ENTITY_TYPE, Identifier.parse(tag))),
                        id + " is not in #" + tag);
            }
            helper.assertValueEqual(spec.variants().stream().map(CustomMobSpec.Variant::id).toList(),
                    expected.variants().stream().map(VariantExpectation::id).toList(), id + " variants");
            for (VariantExpectation variant : expected.variants()) {
                helper.assertTrue(mob.setVariant(variant.id()), id + " has no variant " + variant.id());
                helper.assertValueEqual(mob.variantLootTable().map(key -> key.identifier().toString()).orElse("none"),
                        variant.lootTable(), id + " " + variant.id() + " loot table");
                helper.assertTrue(spec.variants().stream().anyMatch(kind -> kind.id().equals(variant.id())
                        && kind.weight() == variant.weight()), id + " " + variant.id() + " weight");
            }
            mob.discard();
        }
        helper.succeed();
    }

    /** A sound the spec names is the expected one and a registered sound event; "" expects none. */
    private static void assertSound(GameTestHelper helper, Optional<Identifier> sound, String want, String what) {
        helper.assertValueEqual(sound.map(Identifier::toString).orElse(""), want, what);
        sound.ifPresent(id -> helper.assertTrue(BuiltInRegistries.SOUND_EVENT.containsKey(id),
                what + ": " + id + " is no registered sound event"));
    }

    private static void assertClose(GameTestHelper helper, double got, double want, String what) {
        helper.assertTrue(Math.abs(got - want) <= TOLERANCE, what + ": expected " + want + ", got " + got);
    }

    private static void assertBase(GameTestHelper helper, CustomMob mob, Holder<Attribute> attribute, double want) {
        double got = mob.getAttributeBaseValue(attribute);
        helper.assertTrue(Math.abs(got - want) <= TOLERANCE, BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType())
                + " " + attribute.getRegisteredName() + ": expected " + want + ", got " + got);
    }
}
