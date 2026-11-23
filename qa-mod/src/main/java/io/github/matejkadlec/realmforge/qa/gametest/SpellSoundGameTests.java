package io.github.matejkadlec.realmforge.qa.gametest;

import static io.github.matejkadlec.realmforge.qa.gametest.SwingAndSplashGameTests.CENTRE;
import static io.github.matejkadlec.realmforge.qa.gametest.SwingAndSplashGameTests.assertCast;
import static io.github.matejkadlec.realmforge.qa.gametest.SwingAndSplashGameTests.husk;
import static io.github.matejkadlec.realmforge.qa.gametest.SwingAndSplashGameTests.learn;
import static io.github.matejkadlec.realmforge.qa.gametest.SwingAndSplashGameTests.logout;
import static io.github.matejkadlec.realmforge.qa.gametest.SwingAndSplashGameTests.mage;
import static io.github.matejkadlec.realmforge.qa.gametest.SwingAndSplashGameTests.warrior;

import io.github.matejkadlec.realmforge.generated.GeneratedEffects;
import io.github.matejkadlec.realmforge.generated.GeneratedMobs;
import io.github.matejkadlec.realmforge.generated.GeneratedSpells;
import io.github.matejkadlec.realmforge.mob.custom.MeleeMob;
import io.github.matejkadlec.realmforge.qa.combat.SoundLog;
import java.util.List;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.phys.Vec3;

/**
 * RLM-445 and RLM-621: our spells and mobs make their sounds at the moments their visuals and moves happen, as the
 * server plays them ({@link SoundLog}): a cast's sound as it begins, a channel's at each tick, an instant spell's push,
 * a swing's whoosh and its hit's sound only when it strikes, a projectile's impact, the interrupt when a stun cuts a
 * cast, and a melee mob's attack sound as it swings. The content's own spells and mobs are cast and spawned, so these
 * also check the wiring of {@code content/spells/}, {@code content/mobs/} and {@code content/sounds/}.
 */
public final class SpellSoundGameTests {
    private static final Identifier FROSTBOLT_FORM = sound("spell.frostbolt.form");
    private static final Identifier FROSTBOLT_IMPACT = sound("spell.frostbolt.impact");
    private static final Identifier MISSILE_LAUNCH = sound("spell.arcane_missiles.launch");
    private static final Identifier MISSILE_IMPACT = sound("spell.arcane_missiles.impact");
    private static final Identifier INSTANT = sound("spell.cast.instant");
    private static final Identifier INTERRUPT = sound("spell.cast.interrupt");
    private static final Identifier SLAM_SWING = sound("spell.slam.swing");
    private static final Identifier SLAM_HIT = sound("spell.slam.hit");
    private static final Identifier SENTINEL_ATTACK = sound("entity.hewn_sentinel.attack");

    private SpellSoundGameTests() {
    }

    /** Frostbolt forms with its sound as its cast begins, shatters with its own where it hits, and never fizzles. */
    @QaGameTest(template = QaGameTests.PLATFORM_9X9, timeoutTicks = 120)
    public static void frostboltFormsAndShattersWhereItHits(GameTestHelper helper) {
        ServerPlayer player = mage(helper, "qa-sound-frostbolt");
        Husk target = husk(helper, new Vec3(4.5, 1, 5.5));
        learn(helper, player, GeneratedSpells.FROSTBOLT);
        long since = helper.getLevel().getGameTime();
        assertCast(helper, player, GeneratedSpells.FROSTBOLT, target);
        helper.assertTrue(near(helper, player, since).contains(FROSTBOLT_FORM), "Frostbolt formed without its sound: "
                + near(helper, player, since));
        helper.runAfterDelay(60, () -> {
            helper.assertTrue(near(helper, target, since).contains(FROSTBOLT_IMPACT),
                    "Frostbolt hit without its sound: " + near(helper, target, since));
            helper.assertFalse(near(helper, player, since).contains(INTERRUPT), "a completed cast fizzled");
            logout(player);
            helper.succeed();
        });
    }

    /** Each of Arcane Missiles' three crystals launches with its sound, one at each tick, and bursts with its own. */
    @QaGameTest(template = QaGameTests.PLATFORM_9X9, timeoutTicks = 120)
    public static void eachArcaneMissileLaunchesWithItsSound(GameTestHelper helper) {
        ServerPlayer player = mage(helper, "qa-sound-missiles");
        Husk target = husk(helper, new Vec3(4.5, 1, 5.5));
        learn(helper, player, GeneratedSpells.ARCANE_MISSILES);
        long since = helper.getLevel().getGameTime();
        assertCast(helper, player, GeneratedSpells.ARCANE_MISSILES, target);
        helper.runAfterDelay(60, () -> {
            helper.assertValueEqual(count(near(helper, player, since), MISSILE_LAUNCH), 3L, "launch sounds");
            helper.assertTrue(near(helper, target, since).contains(MISSILE_IMPACT),
                    "the missiles burst without their sound: " + near(helper, target, since));
            logout(player);
            helper.succeed();
        });
    }

    /** A stun cuts a cast: the interrupt sound plays where the caster stands (RLM-463: what gathered fades). */
    @QaGameTest(template = QaGameTests.PLATFORM_9X9, timeoutTicks = 100)
    public static void aStunnedCastFizzlesWithTheInterruptSound(GameTestHelper helper) {
        ServerPlayer player = mage(helper, "qa-sound-interrupt");
        Husk target = husk(helper, new Vec3(4.5, 1, 5.5));
        learn(helper, player, GeneratedSpells.FROSTBOLT);
        long since = helper.getLevel().getGameTime();
        assertCast(helper, player, GeneratedSpells.FROSTBOLT, target);
        helper.runAfterDelay(5, () -> player.addEffect(new MobEffectInstance(GeneratedEffects.STUNNED, 40)));
        helper.runAfterDelay(10, () -> {
            helper.assertValueEqual(count(near(helper, player, since), INTERRUPT), 1L, "interrupt sounds");
            logout(player);
            helper.succeed();
        });
    }

    /** An instant spell's push of the hands (Ice Barrier) makes the short cast sound at once. */
    @QaGameTest(template = QaGameTests.PLATFORM_9X9)
    public static void anInstantSpellsPushMakesItsSound(GameTestHelper helper) {
        ServerPlayer player = mage(helper, "qa-sound-instant");
        learn(helper, player, GeneratedSpells.ICE_BARRIER);
        long since = helper.getLevel().getGameTime();
        assertCast(helper, player, GeneratedSpells.ICE_BARRIER, null);
        helper.assertValueEqual(count(near(helper, player, since), INSTANT), 1L, "instant cast sounds");
        logout(player);
        helper.succeed();
    }

    /**
     * Slam whooshes as its swing begins and lands its hit's sound where the husk it strikes stands; a Slam that strikes
     * nothing makes no hit sound.
     */
    @QaGameTest(template = QaGameTests.PLATFORM_9X9)
    public static void slamWhooshesAndSoundsItsHitOnlyWhenItStrikes(GameTestHelper helper) {
        ServerPlayer striker = warrior(helper, "qa-sound-slam", CENTRE);
        ServerPlayer misser = warrior(helper, "qa-sound-slam-miss", new Vec3(1.5, 1, 7.5));
        Husk ahead = husk(helper, new Vec3(4.5, 1, 3.5));
        learn(helper, striker, GeneratedSpells.SLAM);
        learn(helper, misser, GeneratedSpells.SLAM);
        long since = helper.getLevel().getGameTime();
        assertCast(helper, striker, GeneratedSpells.SLAM, null);
        assertCast(helper, misser, GeneratedSpells.SLAM, null);
        helper.assertTrue(near(helper, striker, since).contains(SLAM_SWING), "Slam swung without its whoosh: "
                + near(helper, striker, since));
        helper.runAfterDelay(9, () -> {
            helper.assertTrue(ahead.getHealth() < SpellGameTests.HEALTH, "Slam missed the husk ahead");
            helper.assertValueEqual(count(near(helper, ahead, since), SLAM_HIT), 1L, "hit sounds at the husk");
            helper.assertValueEqual(count(near(helper, misser, since), SLAM_HIT), 0L,
                    "hit sounds of the Slam that struck nothing");
            logout(striker);
            logout(misser);
            helper.succeed();
        });
    }

    /** The Hewn Sentinel's swing makes its attack sound (RLM-621), the moment its arms swing for a hit. */
    @QaGameTest
    public static void theSentinelsSwingMakesItsAttackSound(GameTestHelper helper) {
        MeleeMob sentinel = helper.spawn(GeneratedMobs.HEWN_SENTINEL.get(), new Vec3(2.5, 1, 2.5));
        long since = helper.getLevel().getGameTime();
        sentinel.swing(InteractionHand.MAIN_HAND);
        helper.assertValueEqual(count(near(helper, sentinel, since), SENTINEL_ATTACK), 1L, "attack sounds");
        helper.succeed();
    }

    private static Identifier sound(String path) {
        return Identifier.fromNamespaceAndPath("realmforge", path);
    }

    /** The sounds played within 2 blocks of the middle of {@code entity}'s hitbox: a hit lands on its surface. */
    private static List<Identifier> near(GameTestHelper helper, LivingEntity entity, long since) {
        return SoundLog.near(helper.getLevel(), entity.getBoundingBox().getCenter(), 2, since);
    }

    private static long count(List<Identifier> sounds, Identifier sound) {
        return sounds.stream().filter(sound::equals).count();
    }
}
