package io.github.matejkadlec.realmforge.qa.gametest;

import io.github.matejkadlec.realmforge.Realmforge;
import io.github.matejkadlec.realmforge.generated.GeneratedSpells;
import io.github.matejkadlec.realmforge.qa.generated.GeneratedSpellExpectations;
import io.github.matejkadlec.realmforge.qa.generated.GeneratedSpellExpectations.EffectExpectation;
import io.github.matejkadlec.realmforge.qa.generated.GeneratedSpellExpectations.SpellExpectation;
import io.github.matejkadlec.realmforge.spell.AllyTargeting;
import io.github.matejkadlec.realmforge.spell.ApplyEffectEffect;
import io.github.matejkadlec.realmforge.spell.AreaEffect;
import io.github.matejkadlec.realmforge.spell.DamageEffect;
import io.github.matejkadlec.realmforge.spell.DashEffect;
import io.github.matejkadlec.realmforge.spell.DirectionTargeting;
import io.github.matejkadlec.realmforge.spell.EnemyTargeting;
import io.github.matejkadlec.realmforge.spell.GroundPointTargeting;
import io.github.matejkadlec.realmforge.spell.HealEffect;
import io.github.matejkadlec.realmforge.spell.KnockbackEffect;
import io.github.matejkadlec.realmforge.spell.ProjectileEffect;
import io.github.matejkadlec.realmforge.spell.RemoveEffectEffect;
import io.github.matejkadlec.realmforge.spell.Spell;
import io.github.matejkadlec.realmforge.spell.SpellEffect;
import io.github.matejkadlec.realmforge.spell.SpellRegistries;
import io.github.matejkadlec.realmforge.spell.SpellTargeting;
import java.util.List;
import java.util.Map;
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
            helper.assertValueEqual(spell.cast().durationTicks(), expected.castDurationTicks(), id + " cast duration");
            helper.assertValueEqual(spell.cast().intervalTicks(), expected.castIntervalTicks(), id + " cast interval");
            helper.assertValueEqual(spell.cast().castableWhileMoving(), expected.castableWhileMoving(),
                    id + " castable while moving");
            helper.assertValueEqual(spell.cast().lockOn(), expected.lockOn(), id + " lock on");
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
            case GroundPointTargeting point -> point.range();
            case DirectionTargeting direction -> direction.range();
            default -> 0.0;
        };
    }

    /** An effect in the shape of its expectation: its numbers and names by their data field names. */
    private static EffectExpectation effect(SpellEffect effect) {
        String type = SpellRegistries.EFFECT_TYPES.getKey(effect.codec()).toString();
        return switch (effect) {
            case DamageEffect damage -> numbers(type, Map.of("amount", damage.amount(), "per_level",
                    damage.perLevel(), "attack_damage", damage.attackDamage()));
            case HealEffect heal -> numbers(type, Map.of("amount", heal.amount(), "per_level", heal.perLevel(),
                    "attack_damage", heal.attackDamage()));
            case ApplyEffectEffect apply -> new EffectExpectation(type, Map.of("duration_ticks",
                    (double) apply.durationTicks(), "amplifier", (double) apply.amplifier()),
                    Map.of("effect", apply.effect().getRegisteredName()), List.of());
            case RemoveEffectEffect remove -> new EffectExpectation(type, Map.of(),
                    Map.of("effect", remove.effect().getRegisteredName()), List.of());
            case KnockbackEffect knockback -> numbers(type, Map.of("strength", knockback.strength()));
            case DashEffect dash -> numbers(type, Map.of("strength", dash.strength()));
            case ProjectileEffect projectile -> new EffectExpectation(type, Map.of("speed", projectile.speed(),
                    "size", projectile.size(), "lifetime_ticks", (double) projectile.lifetimeTicks()), Map.of(),
                    projectile.effects().stream().map(ContentSpellGameTests::effect).toList());
            case AreaEffect area -> new EffectExpectation(type, Map.of("radius", area.radius(), "duration_ticks",
                    (double) area.durationTicks(), "interval_ticks", (double) area.intervalTicks()),
                    Map.of("affects", area.affects().getSerializedName()),
                    area.effects().stream().map(ContentSpellGameTests::effect).toList());
            default -> new EffectExpectation(type, Map.of(), Map.of(), List.of());
        };
    }

    private static EffectExpectation numbers(String type, Map<String, Double> numbers) {
        return new EffectExpectation(type, numbers, Map.of(), List.of());
    }
}
