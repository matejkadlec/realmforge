package io.github.matejkadlec.realmforge.qa.gametest;

import io.github.matejkadlec.realmforge.boss.BossMob;
import io.github.matejkadlec.realmforge.generated.GeneratedEffects;
import io.github.matejkadlec.realmforge.generated.GeneratedMobs;
import io.github.matejkadlec.realmforge.generated.GeneratedSpells;
import io.github.matejkadlec.realmforge.generated.GeneratedStats;
import io.github.matejkadlec.realmforge.qa.command.StatsCommands;
import io.github.matejkadlec.realmforge.spell.AreaEffect;
import io.github.matejkadlec.realmforge.spell.DamageEffect;
import io.github.matejkadlec.realmforge.spell.EffectContext;
import io.github.matejkadlec.realmforge.spell.ProjectileEffect;
import io.github.matejkadlec.realmforge.spell.Spell;
import io.github.matejkadlec.realmforge.spell.SpellEffect;
import io.github.matejkadlec.realmforge.spell.SpellTarget;
import io.github.matejkadlec.realmforge.spell.Spells;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * Crowd control on a boss (RLM-615; Matěj, 2026-11-04, architecture 14.3): a 4 s stun or freeze lasts 2 s on Astone,
 * then her Stun Immunity or Frozen Immunity runs for 10 s, shown on her bar with its time left, during which a new one
 * does nothing; each immunity blocks only its own effect, a freeze that breaks early still gives its immunity, a reset
 * leaves none, and an ordinary mob keeps the full duration. And Astone's Spell Resistance (RLM-616): a Frostbolt deals
 * her 10 % less than it would without it, at the same Spell Power.
 *
 * <p>Astone stands in the middle of a 17x17 platform without AI or target goals, so nothing but the test touches her
 * effects.
 */
public final class BossControlGameTests {
    private static final Vec3 MIDDLE = new Vec3(8.5, 1, 8.5);
    /** A 4 s stun or freeze, in ticks. */
    private static final int FOUR_SECONDS = 80;
    /** Half of it, what a boss takes. */
    private static final int TWO_SECONDS = 40;
    /** The immunity's 10 s. */
    private static final int IMMUNITY = 200;
    private static final String TITLE = "Astone, the Unbroken — The Weighing";

    private BossControlGameTests() {
    }

    /**
     * A 4 s stun lasts 2 s on Astone; when it ends she has Stun Immunity for 10 s, shown on her bar as
     * "Stun Immunity (00:10)", and a new stun does nothing; once it wears off, the next stun lands again, for 2 s.
     */
    @QaGameTest(template = QaGameTests.PLATFORM_17X17, timeoutTicks = 320)
    public static void aStunLastsHalfOnABossThenItsImmunityRuns(GameTestHelper helper) {
        assertHalfThenImmunity(helper, GeneratedEffects.STUNNED, GeneratedEffects.STUN_IMMUNITY, "Stun Immunity");
    }

    /** The same for Frozen: 2 s, then Frozen Immunity for 10 s on her bar, then the next freeze lands again. */
    @QaGameTest(template = QaGameTests.PLATFORM_17X17, timeoutTicks = 320)
    public static void aFreezeLastsHalfOnABossThenItsImmunityRuns(GameTestHelper helper) {
        assertHalfThenImmunity(helper, GeneratedEffects.FROZEN, GeneratedEffects.FROZEN_IMMUNITY, "Frozen Immunity");
    }

    /**
     * Each effect has its own immunity: during Stun Immunity a freeze still lands (for 2 s), and during Frozen Immunity
     * a stun does; both immunities then show on her bar.
     */
    @QaGameTest(template = QaGameTests.PLATFORM_17X17, timeoutTicks = 120)
    public static void anImmunityBlocksOnlyItsOwnEffect(GameTestHelper helper) {
        BossMob astone = astone(helper);
        astone.addEffect(new MobEffectInstance(GeneratedEffects.STUNNED, FOUR_SECONDS));
        helper.startSequence()
                .thenExecuteAfter(TWO_SECONDS + 1, () -> {
                    helper.assertTrue(astone.hasEffect(GeneratedEffects.STUN_IMMUNITY), "Stun Immunity runs");
                    helper.assertTrue(astone.addEffect(new MobEffectInstance(GeneratedEffects.FROZEN, FOUR_SECONDS)),
                            "Stun Immunity blocked a freeze");
                    helper.assertValueEqual(astone.getEffect(GeneratedEffects.FROZEN).getDuration(), TWO_SECONDS,
                            "the freeze's ticks on a boss");
                })
                .thenExecuteAfter(TWO_SECONDS + 1, () -> {
                    helper.assertTrue(astone.hasEffect(GeneratedEffects.FROZEN_IMMUNITY), "Frozen Immunity runs");
                    helper.assertTrue(astone.hasEffect(GeneratedEffects.STUN_IMMUNITY), "Stun Immunity still runs");
                    helper.assertFalse(astone.addEffect(new MobEffectInstance(GeneratedEffects.STUNNED,
                            FOUR_SECONDS)), "a stun landed during Stun Immunity");
                    String title = astone.bossBar().title().getString();
                    helper.assertTrue(title.startsWith(TITLE + " · Frozen Immunity (00:") && title.contains(
                            " · Stun Immunity (00:"), "both immunities on her bar: " + title);
                })
                .thenSucceed();
    }

    /**
     * Frozen that breaks before it runs out (damage breaks it at 50 %, RLM-542) still gives Frozen Immunity, as its
     * end is what counts; a reset (a fight that is over) clears her effects and gives no immunity.
     */
    @QaGameTest(template = QaGameTests.PLATFORM_17X17, timeoutTicks = 60)
    public static void aBrokenFreezeGivesItsImmunityAndAResetNone(GameTestHelper helper) {
        BossMob astone = astone(helper);
        astone.addEffect(new MobEffectInstance(GeneratedEffects.FROZEN, FOUR_SECONDS));
        helper.startSequence()
                .thenExecuteAfter(5, () -> astone.removeEffect(GeneratedEffects.FROZEN))
                .thenExecuteAfter(2, () -> {
                    MobEffectInstance immunity = astone.getEffect(GeneratedEffects.FROZEN_IMMUNITY);
                    helper.assertTrue(immunity != null && immunity.getDuration() > IMMUNITY - 2,
                            "a broken freeze gave Frozen Immunity for 10 s: " + immunity);
                    astone.removeEffect(GeneratedEffects.FROZEN_IMMUNITY);
                    astone.addEffect(new MobEffectInstance(GeneratedEffects.STUNNED, FOUR_SECONDS));
                })
                .thenExecuteAfter(2, astone::reset)
                .thenExecuteAfter(2, () -> {
                    helper.assertFalse(astone.hasEffect(GeneratedEffects.STUNNED), "the reset cleared the stun");
                    helper.assertFalse(astone.hasEffect(GeneratedEffects.STUN_IMMUNITY),
                            "the reset gave Stun Immunity");
                    helper.assertValueEqual(astone.bossBar().title().getString(), TITLE, "her bar after the reset");
                })
                .thenSucceed();
    }

    /** An ordinary mob keeps a stun's and a freeze's full 4 s, and gets no immunity afterwards. */
    @QaGameTest(timeoutTicks = 120)
    public static void anOrdinaryMobKeepsTheFullDuration(GameTestHelper helper) {
        Husk husk = SpellGameTests.husk(helper, new BlockPos(2, 1, 2));
        husk.addEffect(new MobEffectInstance(GeneratedEffects.STUNNED, FOUR_SECONDS));
        husk.addEffect(new MobEffectInstance(GeneratedEffects.FROZEN, FOUR_SECONDS));
        helper.assertValueEqual(husk.getEffect(GeneratedEffects.STUNNED).getDuration(), FOUR_SECONDS, "its stun");
        helper.assertValueEqual(husk.getEffect(GeneratedEffects.FROZEN).getDuration(), FOUR_SECONDS, "its freeze");
        helper.startSequence()
                .thenExecuteAfter(FOUR_SECONDS + 2, () -> {
                    helper.assertFalse(husk.hasEffect(GeneratedEffects.STUNNED), "the stun ran out");
                    helper.assertFalse(husk.hasEffect(GeneratedEffects.STUN_IMMUNITY), "it got Stun Immunity");
                    helper.assertFalse(husk.hasEffect(GeneratedEffects.FROZEN_IMMUNITY), "it got Frozen Immunity");
                    helper.assertTrue(husk.addEffect(new MobEffectInstance(GeneratedEffects.STUNNED, FOUR_SECONDS)),
                            "a new stun lands at once");
                })
                .thenSucceed();
    }

    /**
     * RLM-616: Astone has 10 % Spell Resistance, so Frostbolt's damage at rank 1 and 10 Spell Power deals her 90 % of
     * what it deals her without it (no crit, no school power).
     */
    @QaGameTest(template = QaGameTests.PLATFORM_17X17)
    public static void astoneTakesTenPercentLessFromAFrostbolt(GameTestHelper helper) {
        BossMob astone = astone(helper);
        helper.assertValueEqual(astone.getAttributeValue(GeneratedStats.SPELL_RESISTANCE), 0.1,
                "Astone's Spell Resistance");
        ServerPlayer player = QaPlayers.loginOnPlatform(helper, "qa-astone-frostbolt", new Vec3(8.5, 1, 1.5));
        player.setGameMode(GameType.SURVIVAL);
        StatsCommands.setBase(player, GeneratedStats.CRIT_CHANCE, 0);
        StatsCommands.setBase(player, GeneratedStats.SPELL_POWER, 10);
        Holder.Reference<Spell> frostbolt = Spells.get(helper.getLevel().registryAccess(), GeneratedSpells.FROSTBOLT)
                .orElseThrow();
        DamageEffect damage = firstDamage(frostbolt.value().effects()).orElseThrow();
        Runnable hit = () -> damage.applyTo(EffectContext.of(helper.getLevel(), player, frostbolt, 1,
                Optional.empty()), astone, SpellTarget.of(player, astone));
        double resisted = loss(astone, hit);
        CombatGameTests.setBase(astone, GeneratedStats.SPELL_RESISTANCE, 0);
        double plain = loss(astone, hit);
        player.level().getServer().getPlayerList().remove(player);
        helper.assertTrue(plain > 0 && Math.abs(resisted - plain * 0.9) < 1e-3,
                "a Frostbolt deals her 90 % of " + plain + ": " + resisted);
        helper.succeed();
    }

    /**
     * Applies a 4 s {@code effect} to Astone: 2 s, then {@code immunity} for 10 s on her bar, a new one refused, and the
     * next one landing again once the immunity wore off.
     */
    private static void assertHalfThenImmunity(GameTestHelper helper, Holder<MobEffect> effect,
            Holder<MobEffect> immunity, String name) {
        BossMob astone = astone(helper);
        helper.assertTrue(astone.addEffect(new MobEffectInstance(effect, FOUR_SECONDS)), "the first one landed");
        helper.assertValueEqual(astone.getEffect(effect).getDuration(), TWO_SECONDS, "its ticks on a boss");
        helper.startSequence()
                .thenExecuteAfter(TWO_SECONDS - 2, () -> helper.assertTrue(astone.hasEffect(effect),
                        "it still holds just before 2 s"))
                .thenExecuteAfter(3, () -> {
                    helper.assertFalse(astone.hasEffect(effect), "it ended after 2 s");
                    MobEffectInstance immune = astone.getEffect(immunity);
                    helper.assertTrue(immune != null && immune.getDuration() > IMMUNITY - 3,
                            name + " for 10 s: " + immune);
                    String title = astone.bossBar().title().getString();
                    helper.assertTrue(title.matches(Pattern.quote(TITLE + " · " + name) + " \\(00:(10|09)\\)"),
                            "her bar shows it with its time left: " + title);
                    helper.assertFalse(astone.addEffect(new MobEffectInstance(effect, FOUR_SECONDS)),
                            "a new one landed during " + name);
                    helper.assertFalse(astone.hasEffect(effect), "she holds the refused one");
                })
                .thenExecuteAfter(IMMUNITY + 3, () -> {
                    helper.assertFalse(astone.hasEffect(immunity), name + " wore off after 10 s");
                    helper.assertValueEqual(astone.bossBar().title().getString(), TITLE, "her bar without it");
                    helper.assertTrue(astone.addEffect(new MobEffectInstance(effect, FOUR_SECONDS)),
                            "the next one lands again");
                    helper.assertValueEqual(astone.getEffect(effect).getDuration(), TWO_SECONDS,
                            "for 2 s again");
                })
                .thenSucceed();
    }

    /** Astone in the middle of the platform, without AI or target goals. */
    private static BossMob astone(GameTestHelper helper) {
        BossMob astone = (BossMob) helper.spawn(GeneratedMobs.ASTONE.get(), MIDDLE);
        astone.targetSelector.removeAllGoals(goal -> true);
        astone.setNoAi(true);
        return astone;
    }

    /** What {@code hit} takes from {@code astone} at full health. */
    private static double loss(BossMob astone, Runnable hit) {
        astone.setHealth(astone.getMaxHealth());
        hit.run();
        return astone.getMaxHealth() - astone.getHealth();
    }

    /** The first damage effect of {@code effects}, a projectile's or an area's included. */
    private static Optional<DamageEffect> firstDamage(List<SpellEffect> effects) {
        for (SpellEffect effect : effects) {
            Optional<DamageEffect> found = switch (effect) {
                case DamageEffect damage -> Optional.of(damage);
                case ProjectileEffect projectile -> firstDamage(projectile.effects());
                case AreaEffect area -> firstDamage(area.effects());
                default -> Optional.empty();
            };
            if (found.isPresent()) {
                return found;
            }
        }
        return Optional.empty();
    }
}
