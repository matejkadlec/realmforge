package io.github.matejkadlec.realmforge.qa.gametest;

import io.github.matejkadlec.realmforge.boss.BossMob;
import io.github.matejkadlec.realmforge.generated.GeneratedMobs;
import io.github.matejkadlec.realmforge.mob.MobLevels;
import io.github.matejkadlec.realmforge.qa.boss.QaBosses;
import java.util.List;
import java.util.OptionalInt;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.phys.Vec3;

/**
 * W5a (RLM-490, architecture 14.3): the boss framework on the test boss {@code realmforge:test_titan}
 * ({@code content/bosses/test_titan.yaml}): three phases (Awakening; Fury below 50 % health, which casts
 * {@code test_titan_harden} on entering; Frenzy after 4 s of Fury), a 1 s phase change, abilities through the spell
 * engine, an arena of radius 12 and a fixed level of 5. RLM-482: a boss summoned as {@code /summon} does it never
 * despawns.
 *
 * <p>The titan stands in the middle of a 17x17 platform. A titan without AI never takes a home (its home is where its
 * AI first finds it on the ground), so the tests of its arena and abilities let it think and keep it from walking
 * instead. Its target goals are gone, so a player of another test never draws it into a fight; the tests give it its
 * target.
 */
public final class BossGameTests {
    private static final Vec3 MIDDLE = new Vec3(8.5, 1, 8.5);
    private static final String NAME = "Test Titan";

    private BossGameTests() {
    }

    /**
     * Every phase change in a fight (the titan has a target): below half health it enters Fury, hardens and stands
     * unhurt for its 1 s phase change; after 4 s of Fury it enters Frenzy. The boss bar shows each phase in the boss's
     * colour, and the titan has its fixed level.
     */
    @QaGameTest(template = QaGameTests.PLATFORM_17X17, timeoutTicks = 200)
    public static void theTestTitanGoesThroughEveryPhase(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BossMob titan = titan(helper);
        titan.setNoAi(true);
        titan.setTarget(husk(helper, new Vec3(8.5, 1, 1.5)));
        helper.assertTrue(titan.definition().isPresent(), "the titan finds its fight");
        helper.assertValueEqual(MobLevels.level(titan), OptionalInt.of(5), "the titan's fixed level");
        assertPhase(helper, titan, 0, "Awakening");
        helper.assertValueEqual(titan.bossBar().color(), BossEvent.BossBarColor.PURPLE, "the bar's colour");
        helper.assertFalse(titan.changingPhase(), "the first phase has no phase change");

        titan.setHealth(titan.getMaxHealth() * 0.4F);
        helper.startSequence()
                .thenExecuteAfter(1, () -> {
                    assertPhase(helper, titan, 1, "Fury");
                    helper.assertTrue(titan.hasEffect(MobEffects.RESISTANCE), "Fury's on_enter spell hardened it");
                    helper.assertTrue(titan.changingPhase(), "it changes phase for 1 s");
                    float before = titan.getHealth();
                    helper.assertFalse(titan.hurtServer(level, level.damageSources().generic(), 10),
                            "a hit during the phase change is refused");
                    helper.assertValueEqual(titan.getHealth(), before, "the refused hit took no health");
                })
                .thenExecuteAfter(21, () -> {
                    helper.assertFalse(titan.changingPhase(), "the phase change is over after 1 s");
                    assertPhase(helper, titan, 1, "Fury");
                    float before = titan.getHealth();
                    helper.assertTrue(titan.hurtServer(level, level.damageSources().generic(), 10),
                            "a hit after the phase change lands");
                    helper.assertTrue(titan.getHealth() < before, "the hit took health");
                })
                .thenWaitUntil(() -> helper.assertValueEqual(titan.phaseIndex(), 2, "the phase after 4 s of Fury"))
                .thenExecute(() -> {
                    assertPhase(helper, titan, 2, "Frenzy");
                    helper.assertTrue(titan.ticksInPhase() <= 1, "Frenzy has just begun");
                })
                .thenSucceed();
    }

    /**
     * A titan nobody has pulled stays in its phase: neither a health threshold nor a phase's time moves it on, so an
     * enrage timer starts with the fight. Put into Fury as {@code /qa boss phase fury} does, it stays there past
     * Fury's 4 s, its phase change still ends after 1 s, and once it has a target the 4 s count from then.
     */
    @QaGameTest(template = QaGameTests.PLATFORM_17X17, timeoutTicks = 300)
    public static void anIdleTestTitanStaysInItsPhase(GameTestHelper helper) {
        BossMob titan = titan(helper);
        titan.setNoAi(true);
        titan.setHealth(titan.getMaxHealth() * 0.4F);
        long[] targeted = new long[1];
        helper.startSequence()
                .thenExecuteAfter(5, () -> {
                    assertPhase(helper, titan, 0, "Awakening");
                    titan.enterPhase(1);
                    assertPhase(helper, titan, 1, "Fury");
                })
                .thenExecuteAfter(100, () -> {
                    assertPhase(helper, titan, 1, "Fury");
                    helper.assertFalse(titan.changingPhase(), "the phase change ends while it is idle too");
                    helper.assertValueEqual(titan.ticksInPhase(), 0, "no time in the phase counts while it is idle");
                    titan.setTarget(husk(helper, new Vec3(8.5, 1, 1.5)));
                    targeted[0] = helper.getLevel().getGameTime();
                })
                .thenWaitUntil(() -> assertPhase(helper, titan, 2, "Frenzy"))
                .thenExecute(() -> helper.assertTrue(helper.getLevel().getGameTime() - targeted[0] >= 80,
                        "Frenzy came 4 s into the fight, not earlier"))
                .thenSucceed();
    }

    /** A boss never goes through a portal, as the Wither does not: its arena and home stay in its dimension. */
    @QaGameTest
    public static void theTestTitanNeverUsesAPortal(GameTestHelper helper) {
        BossMob titan = (BossMob) helper.spawn(GeneratedMobs.TEST_TITAN.get(), new Vec3(2.5, 1, 2.5));
        helper.assertFalse(titan.canUsePortal(false), "the titan may not use a portal");
        helper.assertFalse(titan.canUsePortal(true), "nor as a passenger's vehicle");
        helper.succeed();
    }

    /**
     * The titan casts its phase's ability, {@code test_npc_target_bolt}, at its target through the spell engine, and
     * no sooner again than its own cooldown of 2 s allows. It cannot walk, and its target stands 6 blocks away, out of
     * reach of its fists: every hit the husk takes is a cast.
     */
    @QaGameTest(template = QaGameTests.PLATFORM_17X17, timeoutTicks = 200)
    public static void theTestTitanCastsItsAbilitiesAtItsTarget(GameTestHelper helper) {
        BossMob titan = titan(helper);
        titan.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0);
        Husk husk = husk(helper, new Vec3(8.5, 1, 2.5));
        float full = husk.getHealth();
        float[] first = new float[1];
        titan.setTarget(husk);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(husk.getHealth() < full, "the titan's bolt hit the husk"))
                .thenExecute(() -> first[0] = husk.getHealth())
                .thenExecuteAfter(25, () -> helper.assertValueEqual(husk.getHealth(), first[0],
                        "no second bolt within the ability's own 2 s cooldown"))
                .thenWaitUntil(() -> helper.assertTrue(husk.getHealth() < first[0], "a second bolt after 2 s"))
                .thenSucceed();
    }

    /**
     * Pulled out of its arena, the titan resets: back home, at full health, in its first phase, its Fury's hardening
     * gone.
     */
    @QaGameTest(template = QaGameTests.PLATFORM_17X17, timeoutTicks = 100)
    public static void theTestTitanResetsOutsideItsArena(GameTestHelper helper) {
        BossMob titan = titan(helper);
        titan.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0);
        Husk husk = husk(helper, new Vec3(8.5, 1, 1.5));
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(titan.home().isPresent(), "the titan took its home"))
                .thenExecute(() -> {
                    titan.setTarget(husk);
                    titan.setHealth(titan.getMaxHealth() * 0.4F);
                })
                .thenWaitUntil(() -> assertPhase(helper, titan, 1, "Fury"))
                .thenExecute(() -> {
                    helper.assertTrue(titan.hasEffect(MobEffects.RESISTANCE), "hardened in Fury");
                    Vec3 out = helper.absoluteVec(MIDDLE.add(14, 0, 0));
                    titan.teleportTo(out.x, out.y, out.z);
                })
                .thenExecuteAfter(1, () -> {
                    assertPhase(helper, titan, 0, "Awakening");
                    helper.assertValueEqual(titan.getHealth(), titan.getMaxHealth(), "full health after the reset");
                    helper.assertFalse(titan.hasEffect(MobEffects.RESISTANCE), "its effects are gone");
                    helper.assertTrue(titan.getTarget() == null, "its target is gone");
                    Vec3 home = Vec3.atBottomCenterOf(titan.home().orElseThrow());
                    helper.assertTrue(titan.position().distanceTo(home) < 1, "back home: " + titan.position());
                })
                .thenSucceed();
    }

    /**
     * A fight the titan lost ends as in WoW: once it has no target and the combat window (10 s, architecture 9.4) has
     * passed since the last hit, it resets. While that window lasts, it keeps its phase and health.
     */
    @QaGameTest(template = QaGameTests.PLATFORM_17X17, timeoutTicks = 400)
    public static void theTestTitanResetsWhenItsFightIsOver(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BossMob titan = titan(helper);
        titan.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0);
        Husk husk = husk(helper, new Vec3(8.5, 1, 2.5));
        titan.setTarget(husk);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(titan.home().isPresent(), "the titan took its home"))
                .thenExecute(() -> {
                    // A hit from the husk puts both in combat.
                    titan.hurtServer(level, level.damageSources().mobAttack(husk), 1);
                    titan.setHealth(titan.getMaxHealth() * 0.4F);
                })
                .thenWaitUntil(() -> assertPhase(helper, titan, 1, "Fury"))
                .thenExecute(husk::discard)
                .thenExecuteAfter(60, () -> {
                    helper.assertTrue(titan.getTarget() == null, "its target is gone");
                    assertPhase(helper, titan, 1, "Fury");
                    helper.assertTrue(titan.getHealth() < titan.getMaxHealth(), "still hurt while in combat");
                })
                .thenWaitUntil(() -> assertPhase(helper, titan, 0, "Awakening"))
                .thenExecute(() -> helper.assertValueEqual(titan.getHealth(), titan.getMaxHealth(),
                        "full health once the fight is over"))
                .thenSucceed();
    }

    /**
     * A target that leaves the arena is let go for good: the titan keeps its real hurt-by goal, which remembers
     * whoever hit it, and still neither follows the husk out of the arena nor takes it back as its target.
     */
    @QaGameTest(template = QaGameTests.PLATFORM_17X17, timeoutTicks = 200)
    public static void theTestTitanLetsGoOfATargetThatLeavesItsArena(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BossMob titan = (BossMob) helper.spawn(GeneratedMobs.TEST_TITAN.get(), MIDDLE);
        // Only the hurt-by goal stays, so no player of another test draws it into a fight.
        titan.targetSelector.removeAllGoals(goal -> goal instanceof NearestAttackableTargetGoal<?>);
        Husk husk = husk(helper, new Vec3(8.5, 1, 4.5));
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(titan.home().isPresent(), "the titan took its home"))
                .thenExecute(() -> titan.hurtServer(level, level.damageSources().mobAttack(husk), 1))
                .thenWaitUntil(() -> helper.assertTrue(titan.getTarget() == husk, "the husk that hit it is its target"))
                .thenExecute(() -> {
                    Vec3 out = helper.absoluteVec(MIDDLE.add(0, 0, -16));
                    husk.snapTo(out.x, out.y, out.z, 0, 0);
                })
                .thenExecuteAfter(2, () -> helper.assertTrue(titan.getTarget() == null,
                        "the titan let go of the husk outside its arena"))
                .thenExecuteFor(60, () -> {
                    helper.assertTrue(titan.getTarget() == null, "the titan never took the husk back");
                    helper.assertTrue(titan.inArena(titan), "the titan never followed it out of its arena");
                })
                .thenSucceed();
    }

    /** It takes no hurt from an attacker outside its arena, and the hits from inside land. */
    @QaGameTest(template = QaGameTests.PLATFORM_17X17, timeoutTicks = 100)
    public static void theTestTitanTakesNoHurtFromOutsideItsArena(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BossMob titan = titan(helper);
        titan.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0);
        Husk inside = husk(helper, new Vec3(8.5, 1, 2.5));
        Husk outside = husk(helper, new Vec3(2.5, 1, 2.5));
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(titan.home().isPresent(), "the titan took its home"))
                .thenExecute(() -> {
                    Vec3 far = helper.absoluteVec(MIDDLE.add(0, 0, -16));
                    outside.snapTo(far.x, far.y, far.z, 0, 0);
                    float before = titan.getHealth();
                    helper.assertFalse(titan.hurtServer(level, level.damageSources().mobAttack(outside), 5),
                            "a hit from 16 blocks away, outside the arena, is refused");
                    helper.assertValueEqual(titan.getHealth(), before, "the refused hit took no health");
                    helper.assertTrue(titan.hurtServer(level, level.damageSources().mobAttack(inside), 5),
                            "a hit from inside the arena lands");
                    helper.assertTrue(titan.getHealth() < before, "the hit took health");
                    titan.setTarget(null);
                })
                .thenSucceed();
    }

    /**
     * RLM-482: a boss created as {@code /summon} creates it (without {@code GameTestHelper#spawn}'s persistence) never
     * despawns, the QA mod's test boss and the titan alike, even with every player more than 128 blocks away.
     */
    @QaGameTest(timeoutTicks = 40)
    public static void aSummonedBossNeverDespawns(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = QaPlayers.loginOnPlatform(helper, "qa-boss-despawn");
        try {
            Vec3 far = helper.absoluteVec(new Vec3(202.5, 1, 2.5));
            player.snapTo(far.x, far.y, far.z, 0, 0);
            for (EntityType<? extends Mob> type : List.<EntityType<? extends Mob>>of(QaBosses.TEST_BOSS.get(),
                    GeneratedMobs.TEST_TITAN.get())) {
                Mob boss = type.create(level, EntitySpawnReason.COMMAND);
                helper.assertTrue(boss != null, "no " + type);
                Vec3 where = helper.absoluteVec(new Vec3(2.5, 1, 2.5));
                boss.snapTo(where.x, where.y, where.z, 0, 0);
                level.addFreshEntity(boss);
                String what = boss.getType().toShortString();
                helper.assertFalse(boss.isPersistenceRequired(), what + " was created without persistence");
                helper.assertTrue(boss.requiresCustomPersistence(), what + " requires persistence itself");
                for (int check = 0; check < 2000; check++) {
                    boss.checkDespawn();
                }
                helper.assertFalse(boss.isRemoved(), what + " despawned with every player far away");
                boss.discard();
            }
        } finally {
            CastGameTests.logout(player);
        }
        helper.succeed();
    }

    /** The titan in the middle of the platform, which no player of another test draws into a fight. */
    private static BossMob titan(GameTestHelper helper) {
        BossMob titan = (BossMob) helper.spawn(GeneratedMobs.TEST_TITAN.get(), MIDDLE);
        titan.targetSelector.removeAllGoals(goal -> true);
        return titan;
    }

    /** A husk that stands still. */
    private static Husk husk(GameTestHelper helper, Vec3 where) {
        Husk husk = helper.spawn(EntityType.HUSK, where);
        husk.setNoAi(true);
        return husk;
    }

    private static void assertPhase(GameTestHelper helper, BossMob boss, int index, String name) {
        helper.assertValueEqual(boss.phaseIndex(), index, "the titan's phase");
        helper.assertValueEqual(boss.bossBar().title().getString(), NAME + " — " + name, "the boss bar's title");
    }
}
