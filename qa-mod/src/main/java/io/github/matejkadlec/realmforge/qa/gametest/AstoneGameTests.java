package io.github.matejkadlec.realmforge.qa.gametest;

import io.github.matejkadlec.realmforge.boss.BossMob;
import io.github.matejkadlec.realmforge.generated.GeneratedEffects;
import io.github.matejkadlec.realmforge.generated.GeneratedItems;
import io.github.matejkadlec.realmforge.generated.GeneratedMobs;
import io.github.matejkadlec.realmforge.generated.GeneratedSpells;
import io.github.matejkadlec.realmforge.mob.MobLevels;
import io.github.matejkadlec.realmforge.spell.Spell;
import io.github.matejkadlec.realmforge.spell.Spells;
import io.github.matejkadlec.realmforge.stats.EntityLevel;
import java.util.List;
import java.util.OptionalInt;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.BossEvent;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.Vec3;

/**
 * W5b (RLM-491, architecture 14.3): Astone, the Unbroken ({@code content/bosses/astone.yaml}), Undervault's guardian on
 * the boss framework: her three phases (The Weighing; Quaking Stone below 60 % health, which casts
 * {@code astone_shockwave} on entering; Breaking Point below 25 %, which casts {@code astone_cracked_stone}), her
 * abilities through the spell engine (Hurled Boulder, Quake, Rockfall, Cracked Stone), the ring that marks where an
 * area lands, and her three drops on every kill.
 *
 * <p>Astone stands in the middle of a 17x17 platform. Her target goals are gone, so a player of another test never
 * draws her into a fight; the tests give her a target, a husk that keeps its physics but has no AI of its own and
 * enough health for her hits. The tests of her abilities let her think and keep her from walking, so every hit a husk
 * out of her reach takes is a cast.
 */
public final class AstoneGameTests {
    private static final Vec3 MIDDLE = new Vec3(8.5, 1, 8.5);
    private static final String NAME = "Astone, the Unbroken";
    /** A husk's health: her hits at level 18 never kill it within a test. */
    private static final double HUSK_HEALTH = 500;
    /** Her phase change lasts the default 3 s, with a tick of slack. */
    private static final int PHASE_CHANGE_TICKS = 61;

    private AstoneGameTests() {
    }

    /**
     * Every phase change in a fight (she has a target): below 60 % health she enters Quaking Stone, throws back a husk
     * near her and stands unhurt for her 3 s phase change; below 25 % she enters Breaking Point and her stone cracks.
     * The boss bar shows each phase in white, and she has her fixed level of 18.
     */
    @QaGameTest(template = QaGameTests.PLATFORM_17X17, timeoutTicks = 200)
    public static void astoneGoesThroughEveryPhase(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BossMob astone = astone(helper);
        astone.setNoAi(true);
        astone.setTarget(husk(helper, new Vec3(8.5, 1, 1.5)));
        Husk near = husk(helper, new Vec3(8.5, 1, 12.5));
        double start = near.getZ();
        helper.assertTrue(astone.definition().isPresent(), "Astone finds her fight");
        helper.assertValueEqual(MobLevels.level(astone), OptionalInt.of(18), "Astone's fixed level");
        assertPhase(helper, astone, 0, "The Weighing");
        helper.assertValueEqual(astone.bossBar().color(), BossEvent.BossBarColor.WHITE, "the bar's colour");

        astone.setHealth(astone.getMaxHealth() * 0.55F);
        helper.startSequence()
                .thenExecuteAfter(1, () -> {
                    assertPhase(helper, astone, 1, "Quaking Stone");
                    helper.assertTrue(astone.changingPhase(), "she changes phase for 3 s");
                    float before = astone.getHealth();
                    helper.assertFalse(astone.hurtServer(level, level.damageSources().generic(), 10),
                            "a hit during the phase change is refused");
                    helper.assertValueEqual(astone.getHealth(), before, "the refused hit took no health");
                })
                .thenExecuteAfter(10, () -> helper.assertTrue(near.getZ() - start > 0.5,
                        "her shockwave threw the husk back, away from her: from " + start + " to " + near.getZ()))
                .thenExecuteAfter(PHASE_CHANGE_TICKS - 11, () -> {
                    helper.assertFalse(astone.changingPhase(), "the phase change is over after 3 s");
                    helper.assertFalse(astone.hasEffect(GeneratedEffects.CRACKED_STONE), "not cracked before the last phase");
                    astone.setHealth(astone.getMaxHealth() * 0.2F);
                })
                .thenExecuteAfter(1, () -> {
                    assertPhase(helper, astone, 2, "Breaking Point");
                    helper.assertTrue(astone.hasEffect(GeneratedEffects.CRACKED_STONE),
                            "Breaking Point's on_enter spell cracked her stone");
                    helper.assertTrue(astone.changingPhase(), "she changes phase again");
                })
                .thenSucceed();
    }

    /**
     * In her first phase she hurls her boulder at her target through the spell engine: a 2 s cast that forms it in
     * her hands, then a projectile that hurts the husk 7 blocks away, out of reach of her fists.
     */
    @QaGameTest(template = QaGameTests.PLATFORM_17X17, timeoutTicks = 200)
    public static void astoneHurlsHerBoulderAtHerTarget(GameTestHelper helper) {
        BossMob astone = standingAstone(helper);
        Husk husk = husk(helper, new Vec3(8.5, 1, 1.5));
        float full = husk.getHealth();
        astone.setTarget(husk);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(casting(astone, GeneratedSpells.ASTONE_BOULDER),
                        "Astone casts her boulder"))
                .thenExecute(() -> helper.assertTrue(astone.landing().isEmpty(), "a boulder marks no landing ring"))
                .thenWaitUntil(() -> helper.assertTrue(husk.getHealth() < full, "her boulder hit the husk"))
                .thenSucceed();
    }

    /**
     * In Quaking Stone she casts Quake only while her target stands inside it: with a husk 7 blocks away, outside
     * Quake's 5, she never begins it; with a new target 3 blocks away she does, the ring round her marks its reach, and
     * the slam hurts and slows that husk. (A new husk, so no boulder still on its way to the first throws it back out
     * of the ring.)
     */
    @QaGameTest(template = QaGameTests.PLATFORM_17X17, timeoutTicks = 400)
    public static void astoneQuakesOnlyWithHerTargetInside(GameTestHelper helper) {
        BossMob astone = standingAstone(helper);
        Husk[] husk = new Husk[1];
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(astone.home().isPresent(), "Astone took her home"))
                .thenExecute(() -> astone.enterPhase(1))
                .thenWaitUntil(() -> helper.assertFalse(astone.changingPhase(), "the phase change is over"))
                .thenExecute(() -> {
                    husk[0] = husk(helper, new Vec3(15.5, 1, 8.5));
                    astone.setTarget(husk[0]);
                })
                .thenExecuteFor(80, () -> helper.assertFalse(casting(astone, GeneratedSpells.ASTONE_QUAKE),
                        "no Quake while her target stands outside it"))
                .thenExecute(() -> {
                    husk[0].discard();
                    husk[0] = husk(helper, new Vec3(11.5, 1, 8.5));
                    astone.setTarget(husk[0]);
                })
                .thenWaitUntil(() -> helper.assertTrue(casting(astone, GeneratedSpells.ASTONE_QUAKE),
                        "Astone quakes with her target inside"))
                .thenExecute(() -> {
                    BossMob.Landing landing = astone.landing().orElseThrow();
                    helper.assertTrue(landing.middle().distanceTo(astone.position()) < 0.01,
                            "the ring is round her: " + landing.middle());
                    helper.assertValueEqual(landing.radius(), 5.0, "the ring's radius, Quake's");
                })
                .thenWaitUntil(() -> helper.assertTrue(husk[0].hasEffect(MobEffects.SLOWNESS),
                        "her quake slowed the husk"))
                .thenExecute(() -> helper.assertTrue(husk[0].getHealth() < HUSK_HEALTH, "her quake hurt the husk"))
                .thenSucceed();
    }

    /**
     * Her Rockfall lands where her target stood when she began it, which the ring marks, so a target that steps out of
     * the ring dodges it: the husk she aimed at leaves, and a second husk at the marked spot takes the hit instead.
     */
    @QaGameTest(template = QaGameTests.PLATFORM_17X17, timeoutTicks = 400)
    public static void astonesRockfallLandsWhereHerTargetStood(GameTestHelper helper) {
        BossMob astone = standingAstone(helper);
        Husk[] husks = new Husk[2];
        float[] dodgerHealth = new float[1];
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(astone.home().isPresent(), "Astone took her home"))
                .thenExecute(() -> astone.enterPhase(1))
                .thenWaitUntil(() -> helper.assertFalse(astone.changingPhase(), "the phase change is over"))
                .thenExecute(() -> {
                    husks[0] = husk(helper, new Vec3(8.5, 1, 1.5));
                    astone.setTarget(husks[0]);
                })
                .thenWaitUntil(() -> helper.assertTrue(casting(astone, GeneratedSpells.ASTONE_ROCKFALL),
                        "Astone calls down rocks"))
                .thenExecute(() -> {
                    Vec3 stood = husks[0].position();
                    BossMob.Landing landing = astone.landing().orElseThrow();
                    helper.assertTrue(landing.middle().distanceTo(stood) < 0.01,
                            "the ring marks where her target stands: " + landing.middle() + ", not " + stood);
                    helper.assertValueEqual(landing.radius(), 3.0, "the ring's radius, Rockfall's");
                    Vec3 away = helper.absoluteVec(new Vec3(1.5, 1, 8.5));
                    husks[0].snapTo(away.x, away.y, away.z, 0, 0);
                    dodgerHealth[0] = husks[0].getHealth();
                    husks[1] = husk(helper, helper.relativeVec(stood));
                })
                .thenExecuteFor(2, () -> helper.assertTrue(astone.landing().orElseThrow().middle()
                        .distanceTo(husks[1].position()) < 0.5, "the ring stays where her target stood"))
                .thenWaitUntil(() -> helper.assertTrue(husks[1].getHealth() < HUSK_HEALTH,
                        "the rocks hit the husk at the marked spot"))
                .thenExecute(() -> helper.assertValueEqual(husks[0].getHealth(), dodgerHealth[0],
                        "the husk that stepped out of the ring dodged the rocks"))
                .thenSucceed();
    }

    /**
     * In Breaking Point, Cracked Stone is an ability too: when her cracks wear off she cracks again, so they last the
     * whole phase. Here they are taken away during the phase change, and she casts them again once it is over.
     */
    @QaGameTest(template = QaGameTests.PLATFORM_17X17, timeoutTicks = 200)
    public static void astoneCracksAgainInBreakingPoint(GameTestHelper helper) {
        BossMob astone = standingAstone(helper);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(astone.home().isPresent(), "Astone took her home"))
                .thenExecute(() -> {
                    astone.setTarget(husk(helper, new Vec3(8.5, 1, 1.5)));
                    astone.enterPhase(2);
                    helper.assertTrue(astone.hasEffect(GeneratedEffects.CRACKED_STONE), "cracked on entering");
                    astone.removeEffect(GeneratedEffects.CRACKED_STONE);
                })
                .thenExecuteAfter(PHASE_CHANGE_TICKS - 2, () -> helper.assertFalse(
                        astone.hasEffect(GeneratedEffects.CRACKED_STONE), "no cracks during the phase change"))
                .thenWaitUntil(() -> helper.assertTrue(astone.hasEffect(GeneratedEffects.CRACKED_STONE),
                        "she cracked again as an ability"))
                .thenExecute(() -> helper.assertTrue(
                        astone.getAttributeValue(Attributes.ATTACK_DAMAGE)
                                > astone.getAttributeBaseValue(Attributes.ATTACK_DAMAGE),
                        "her cracks raise her attack damage"))
                .thenSucceed();
    }

    /** She drops the Stone Heart, the Sun Signet Shard and the Ice Signet Shard on every kill. */
    @QaGameTest(template = QaGameTests.PLATFORM_17X17, timeoutTicks = 100)
    public static void astoneDropsHerThreeItemsOnEveryKill(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        List<Item> drops = List.of(GeneratedItems.STONE_HEART.get(), GeneratedItems.SUN_SIGNET_SHARD.get(),
                GeneratedItems.ICE_SIGNET_SHARD.get());
        BlockPos middle = BlockPos.containing(MIDDLE);
        for (int kill = 0; kill < 3; kill++) {
            BossMob astone = astone(helper);
            astone.kill(level);
            helper.assertTrue(astone.isDeadOrDying(), "Astone died");
            for (Item drop : drops) {
                helper.assertItemEntityCountIs(drop, middle, 6, 1);
            }
            helper.killAllEntitiesOfClass(ItemEntity.class);
        }
        helper.succeed();
    }

    /** Astone in the middle of the platform, whom no player of another test draws into a fight. */
    private static BossMob astone(GameTestHelper helper) {
        BossMob astone = (BossMob) helper.spawn(GeneratedMobs.ASTONE.get(), MIDDLE);
        astone.targetSelector.removeAllGoals(goal -> true);
        return astone;
    }

    /** Astone who thinks and casts, but never walks. */
    private static BossMob standingAstone(GameTestHelper helper) {
        BossMob astone = astone(helper);
        CombatGameTests.setBase(astone, Attributes.MOVEMENT_SPEED, 0);
        return astone;
    }

    /** A husk with its physics but no AI of its own, at level 1, that her hits and pushes reach in full. */
    private static Husk husk(GameTestHelper helper, Vec3 where) {
        Husk husk = helper.spawnWithNoFreeWill(EntityType.HUSK, where);
        MobLevels.setLevel(husk, EntityLevel.MIN);
        CombatGameTests.setBase(husk, Attributes.MAX_HEALTH, HUSK_HEALTH);
        CombatGameTests.setBase(husk, Attributes.KNOCKBACK_RESISTANCE, 0);
        husk.setHealth((float) HUSK_HEALTH);
        return husk;
    }

    private static boolean casting(BossMob astone, ResourceKey<Spell> spell) {
        return Spells.currentCast(astone).map(cast -> cast.spell().equals(spell)).orElse(false);
    }

    private static void assertPhase(GameTestHelper helper, BossMob boss, int index, String name) {
        helper.assertValueEqual(boss.phaseIndex(), index, "Astone's phase");
        helper.assertValueEqual(boss.bossBar().title().getString(), NAME + " — " + name, "the boss bar's title");
    }
}
