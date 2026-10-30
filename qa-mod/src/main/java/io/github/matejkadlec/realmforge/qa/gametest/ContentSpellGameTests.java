package io.github.matejkadlec.realmforge.qa.gametest;

import io.github.matejkadlec.realmforge.Realmforge;
import io.github.matejkadlec.realmforge.generated.GeneratedSpells;
import io.github.matejkadlec.realmforge.qa.generated.GeneratedSpellExpectations;
import io.github.matejkadlec.realmforge.qa.generated.GeneratedSpellExpectations.EffectExpectation;
import io.github.matejkadlec.realmforge.qa.generated.GeneratedSpellExpectations.SpellExpectation;
import io.github.matejkadlec.realmforge.spell.AllyTargeting;
import io.github.matejkadlec.realmforge.spell.ApplyEffectEffect;
import io.github.matejkadlec.realmforge.spell.AreaEffect;
import io.github.matejkadlec.realmforge.spell.CurrentTargetTargeting;
import io.github.matejkadlec.realmforge.spell.DamageEffect;
import io.github.matejkadlec.realmforge.spell.DashEffect;
import io.github.matejkadlec.realmforge.spell.DirectionTargeting;
import io.github.matejkadlec.realmforge.spell.EnemyTargeting;
import io.github.matejkadlec.realmforge.spell.FarthestPlayerTargeting;
import io.github.matejkadlec.realmforge.spell.GroundPointTargeting;
import io.github.matejkadlec.realmforge.spell.HealEffect;
import io.github.matejkadlec.realmforge.spell.KnockbackEffect;
import io.github.matejkadlec.realmforge.spell.ProjectileEffect;
import io.github.matejkadlec.realmforge.spell.RandomPlayerTargeting;
import io.github.matejkadlec.realmforge.spell.RemoveEffectEffect;
import io.github.matejkadlec.realmforge.spell.Spell;
import io.github.matejkadlec.realmforge.spell.SpellEntities;
import io.github.matejkadlec.realmforge.spell.SpellEffect;
import io.github.matejkadlec.realmforge.spell.SpellRegistries;
import io.github.matejkadlec.realmforge.spell.SpellRequirement;
import io.github.matejkadlec.realmforge.spell.SpellTargeting;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.locale.Language;
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
            helper.assertValueEqual(spell.name(), expected.name(), id + " name");
            helper.assertValueEqual(spell.description(), expected.description(), id + " description");
            helper.assertTrue(Language.getInstance().has(spell.name()) && Language.getInstance().has(
                    spell.description()), id + " has no name or description in the lang file");
            helper.assertValueEqual(spell.school().map(Holder::getRegisteredName), expected.school(), id + " school");
            helper.assertValueEqual(spell.scalesWith().getSerializedName(), expected.scalesWith(), id + " scales with");
            helper.assertValueEqual(targetingType(spell.targeting()), expected.targeting(), id + " targeting");
            helper.assertValueEqual(range(spell.targeting()), expected.range(), id + " range");
            helper.assertValueEqual(spell.cast().type().getSerializedName(), expected.cast(), id + " cast");
            helper.assertValueEqual(spell.cast().durationTicks(), expected.castDurationTicks(), id + " cast duration");
            helper.assertValueEqual(spell.cast().intervalTicks(), expected.castIntervalTicks(), id + " cast interval");
            helper.assertValueEqual(spell.cast().castableWhileMoving(), expected.castableWhileMoving(),
                    id + " castable while moving");
            helper.assertValueEqual(spell.cast().lockOn(), expected.lockOn(), id + " lock on");
            helper.assertValueEqual(spell.cast().keepSight(), expected.keepSight(), id + " keeps sight");
            helper.assertValueEqual(spell.cast().keepRange(), expected.keepRange(), id + " keeps range");
            helper.assertValueEqual(spell.cast().sound().map(Identifier::toString), expected.castSound(),
                    id + " cast sound");
            helper.assertValueEqual(spell.cast().formsProjectile(), expected.formsProjectile(),
                    id + " forms its projectile");
            helper.assertValueEqual(spell.cast().animation().getSerializedName(), expected.castAnimation(),
                    id + " cast animation");
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
            helper.assertValueEqual(spell.crit().bonusChancePerLevel(), expected.critBonusChancePerLevel(),
                    id + " bonus crit chance per level");
            helper.assertValueEqual(spell.npcOnly(), expected.npcOnly(), id + " npc only");
            helper.assertValueEqual(spell.requires().stream().map(SpellRequirement::getSerializedName).toList(),
                    expected.requires(), id + " requires");
            helper.assertValueEqual(spell.castableWhileStunned(), expected.castableWhileStunned(),
                    id + " castable while stunned");
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

    /**
     * RLM-259: the spell entity types have English names from {@code content/texts/entity.yaml}, which the game shows
     * where it prints an entity's own name (F3, {@code /data}).
     */
    @QaGameTest
    public static void spellEntityTypesHaveNames(GameTestHelper helper) {
        helper.assertValueEqual(Language.getInstance().getOrDefault(
                SpellEntities.PROJECTILE.get().getDescriptionId()), "Spell Projectile", "the projectile's name");
        helper.assertValueEqual(Language.getInstance().getOrDefault(SpellEntities.AREA.get().getDescriptionId()),
                "Spell Area", "the area's name");
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
            case CurrentTargetTargeting current -> current.range();
            case RandomPlayerTargeting random -> random.range();
            case FarthestPlayerTargeting farthest -> farthest.range();
            default -> 0.0;
        };
    }

    /** An effect in the shape of its expectation: its numbers and names by their data field names. */
    private static EffectExpectation effect(SpellEffect effect) {
        String type = SpellRegistries.EFFECT_TYPES.getKey(effect.codec()).toString();
        return switch (effect) {
            case DamageEffect damage -> numbers(type, damageNumbers(damage));
            case HealEffect heal -> numbers(type, Map.of("amount", heal.amount(), "per_level", heal.perLevel(),
                    "attack_damage", heal.attackDamage()));
            case ApplyEffectEffect apply -> new EffectExpectation(type, applyNumbers(apply), applyNames(apply),
                    List.of());
            case RemoveEffectEffect remove -> new EffectExpectation(type, Map.of(),
                    Map.of("effect", remove.effect().getRegisteredName()), List.of());
            case KnockbackEffect knockback -> numbers(type, Map.of("strength", knockback.strength()));
            case DashEffect dash -> numbers(type, Map.of("strength", dash.strength()));
            case ProjectileEffect projectile -> new EffectExpectation(type, projectileNumbers(projectile),
                    projectile.spares().map(tag -> Map.of("spares", tag.location().toString())).orElse(Map.of()),
                    projectile.effects().stream().map(ContentSpellGameTests::effect).toList());
            case AreaEffect area -> new EffectExpectation(type, Map.of("radius", area.radius(), "duration_ticks",
                    (double) area.durationTicks(), "interval_ticks", (double) area.intervalTicks()),
                    areaNames(area), area.effects().stream().map(ContentSpellGameTests::effect).toList());
            default -> new EffectExpectation(type, Map.of(), Map.of(), List.of());
        };
    }

    /**
     * A damage effect's numbers; its armor shred and spell penetration (C4) only when it has them, as the data names
     * them only then.
     */
    private static Map<String, Double> damageNumbers(DamageEffect damage) {
        Map<String, Double> numbers = new HashMap<>(Map.of("amount", damage.amount(), "per_level", damage.perLevel(),
                "attack_damage", damage.attackDamage()));
        putIfSet(numbers, "armor_shred", damage.armorShred());
        putIfSet(numbers, "armor_shred_per_level", damage.armorShredPerLevel());
        putIfSet(numbers, "penetration", damage.penetration());
        putIfSet(numbers, "penetration_per_level", damage.penetrationPerLevel());
        return Map.copyOf(numbers);
    }

    /** A status effect's numbers; its growth per level (C4) only when it grows, as the data names it only then. */
    private static Map<String, Double> applyNumbers(ApplyEffectEffect apply) {
        Map<String, Double> numbers = new HashMap<>(Map.of("duration_ticks", (double) apply.durationTicks(),
                "amplifier", (double) apply.amplifier()));
        putIfSet(numbers, "duration_per_level_ticks", apply.durationPerLevelTicks());
        return Map.copyOf(numbers);
    }

    /** A status effect's names; the levels it grows stronger at, "2,5", only when it has them. */
    private static Map<String, String> applyNames(ApplyEffectEffect apply) {
        Map<String, String> names = new HashMap<>(Map.of("effect", apply.effect().getRegisteredName()));
        if (!apply.strongerAtLevels().isEmpty()) {
            names.put("stronger_at_levels", apply.strongerAtLevels().stream().map(String::valueOf)
                    .collect(Collectors.joining(",")));
        }
        return Map.copyOf(names);
    }

    private static void putIfSet(Map<String, Double> numbers, String name, double value) {
        if (value != 0) {
            numbers.put(name, value);
        }
    }

    /** An area's names; its ring's colour, "#RRGGBB", only when it has one, as the data names it only then. */
    private static Map<String, String> areaNames(AreaEffect area) {
        Map<String, String> names = new HashMap<>(Map.of("affects", area.affects().getSerializedName()));
        area.ringColor().ifPresent(color -> names.put("ring_color",
                String.format(Locale.ROOT, "#%06X", color & 0xFFFFFF)));
        return Map.copyOf(names);
    }

    /** A projectile's numbers; its lifetime only when it has one, as the data names it only then. */
    private static Map<String, Double> projectileNumbers(ProjectileEffect projectile) {
        Map<String, Double> numbers = new HashMap<>(Map.of("speed", projectile.speed(), "size", projectile.size()));
        projectile.lifetimeTicks().ifPresent(ticks -> numbers.put("lifetime_ticks", (double) ticks));
        return Map.copyOf(numbers);
    }

    private static EffectExpectation numbers(String type, Map<String, Double> numbers) {
        return new EffectExpectation(type, numbers, Map.of(), List.of());
    }
}
