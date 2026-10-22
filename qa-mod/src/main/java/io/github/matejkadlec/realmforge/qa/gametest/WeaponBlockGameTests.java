package io.github.matejkadlec.realmforge.qa.gametest;

import static io.github.matejkadlec.realmforge.qa.gametest.CombatGameTests.close;
import static io.github.matejkadlec.realmforge.qa.gametest.CombatGameTests.setBase;

import io.github.matejkadlec.realmforge.combat.WeaponBlocks;
import io.github.matejkadlec.realmforge.generated.GeneratedStats;
import io.github.matejkadlec.realmforge.mob.MobLevels;
import io.github.matejkadlec.realmforge.qa.combat.QaItems;
import io.github.matejkadlec.realmforge.qa.command.StatsCommands;
import io.github.matejkadlec.realmforge.stats.EntityLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * RLM-297: blocking with a two-handed weapon (architecture 9.3), with the rules' defaults (Matěj, 2026-10-22): a hit
 * in the first 0.5 s after raising the weapon is blocked in full, a later one by half; after a blocked hit the weapon
 * goes down and cannot be raised for 1 s, as after lowering it by hand. The QA mod's test greatsword stands for the
 * pack's two-handed weapons: Better Combat's fallback calls it a claymore.
 */
public final class WeaponBlockGameTests {
    /** Where the player stands: the platform's north edge, facing south (yaw 0) or north (yaw 180). */
    private static final Vec3 EDGE = new Vec3(2.5, 1, 0.5);
    /** A hit's damage, before the block. */
    private static final float HIT = 10;
    /** Ticks past the full-block window of 10 ticks (0.5 s). */
    private static final int AFTER_WINDOW = 12;

    private WeaponBlockGameTests() {
    }

    /**
     * Sword Blocking Mechanics' tag of raisable weapons holds Better Combat's two-handed weapons whose right click does
     * nothing else: the test greatsword (a claymore), not a spear (two-handed, but its right click charges), not a
     * one-handed sword (the mod's own default, which our data replaces), not a bow.
     */
    @QaGameTest
    public static void theTagHoldsTwoHandedWeaponsOnly(GameTestHelper helper) {
        helper.assertTrue(new ItemStack(QaItems.TEST_GREATSWORD.get()).is(WeaponBlocks.RAISABLE),
                "the test greatsword is raised to block");
        helper.assertFalse(new ItemStack(Items.IRON_SPEAR).is(WeaponBlocks.RAISABLE), "a spear keeps its charge");
        helper.assertFalse(new ItemStack(Items.IRON_SWORD).is(WeaponBlocks.RAISABLE), "a sword is one-handed");
        helper.assertFalse(new ItemStack(Items.BOW).is(WeaponBlocks.RAISABLE), "a bow keeps its draw");
        helper.succeed();
    }

    /**
     * Done when of RLM-297: a frontal hit after the full-block window deals half its damage; the weapon goes down at
     * the end of the tick and cannot be raised for 1 s.
     */
    @QaGameTest
    public static void aRaisedGreatswordBlocksHalfOfALaterFrontalHit(GameTestHelper helper) {
        ServerPlayer player = blocker(helper, "qa-block-half", 0);
        Zombie zombie = zombieInFront(helper);
        ItemStack greatsword = raise(player, new ItemStack(QaItems.TEST_GREATSWORD.get()), AFTER_WINDOW);
        helper.assertTrue(WeaponBlocks.isRaised(player), "the greatsword is raised");

        float before = player.getHealth();
        player.hurtServer(helper.getLevel(), helper.getLevel().damageSources().mobAttack(zombie), HIT);
        double dealt = before - player.getHealth();
        helper.assertTrue(close(dealt, HIT / 2), "half of a " + HIT + " hit expected, dealt " + dealt);
        helper.assertTrue(player.getCooldowns().isOnCooldown(greatsword), "the blocked hit started the cooldown");
        helper.startSequence()
                .thenExecuteAfter(1, () -> {
                    helper.assertFalse(player.isUsingItem(), "the blocked hit lowered the weapon");
                    player.startUsingItem(InteractionHand.MAIN_HAND);
                    helper.assertTrue(player.getCooldowns().isOnCooldown(greatsword),
                            "the weapon is still on its cooldown");
                    helper.getLevel().getServer().getPlayerList().remove(player);
                })
                .thenSucceed();
    }

    /** A frontal hit in the first 0.5 s after raising the weapon is blocked in full. */
    @QaGameTest
    public static void aHitInTheFirstHalfSecondIsBlockedInFull(GameTestHelper helper) {
        ServerPlayer player = blocker(helper, "qa-block-full", 0);
        Zombie zombie = zombieInFront(helper);
        ItemStack greatsword = raise(player, new ItemStack(QaItems.TEST_GREATSWORD.get()), 2);

        float before = player.getHealth();
        helper.assertFalse(player.hurtServer(helper.getLevel(), helper.getLevel().damageSources().mobAttack(zombie),
                HIT), "the hit was stopped");
        helper.assertTrue(close(player.getHealth(), before), "nothing dealt: " + (before - player.getHealth()));
        helper.assertTrue(player.damageContainers.isEmpty(), "the stopped hit left no damage container");
        helper.assertTrue(player.getCooldowns().isOnCooldown(greatsword), "the full block started the cooldown");
        helper.getLevel().getServer().getPlayerList().remove(player);
        helper.succeed();
    }

    /** A hit from behind is not blocked, nor one at a player holding up a one-handed sword, nor magic. */
    @QaGameTest
    public static void hitsFromBehindOrAtASwordOrOfMagicAreNotBlocked(GameTestHelper helper) {
        Zombie zombie = zombieInFront(helper);
        ServerPlayer facingAway = blocker(helper, "qa-block-behind", 180);
        raise(facingAway, new ItemStack(QaItems.TEST_GREATSWORD.get()), AFTER_WINDOW);
        assertFullHit(helper, facingAway, helper.getLevel().damageSources().mobAttack(zombie), "a hit from behind");
        assertFullHit(helper, facingAway, helper.getLevel().damageSources().magic(), "magic");
        helper.getLevel().getServer().getPlayerList().remove(facingAway);

        ServerPlayer withSword = blocker(helper, "qa-block-sword", 0);
        raise(withSword, new ItemStack(Items.IRON_SWORD), AFTER_WINDOW);
        helper.assertFalse(WeaponBlocks.isRaised(withSword), "a one-handed sword is not raised");
        assertFullHit(helper, withSword, helper.getLevel().damageSources().mobAttack(zombie), "a hit at a sword");
        helper.getLevel().getServer().getPlayerList().remove(withSword);
        helper.succeed();
    }

    /** Lowering the weapon by hand starts the same cooldown, so spamming right click never chains full blocks. */
    @QaGameTest
    public static void loweringByHandStartsTheCooldown(GameTestHelper helper) {
        ServerPlayer player = blocker(helper, "qa-block-lower", 0);
        ItemStack greatsword = raise(player, new ItemStack(QaItems.TEST_GREATSWORD.get()), 3);
        player.releaseUsingItem();
        helper.assertFalse(player.isUsingItem(), "the weapon went down");
        helper.assertTrue(player.getCooldowns().isOnCooldown(greatsword), "lowering it started the cooldown");
        helper.getLevel().getServer().getPlayerList().remove(player);
        helper.succeed();
    }

    /**
     * A fake player at the platform's north edge, facing {@code yaw}, that takes damage (in survival, its client marked
     * as loaded) and never dodges.
     */
    private static ServerPlayer blocker(GameTestHelper helper, String name, float yaw) {
        ServerPlayer player = QaPlayers.loginOnPlatform(helper, name, EDGE);
        player.setGameMode(GameType.SURVIVAL);
        player.connection.markClientLoaded();
        player.snapTo(helper.absoluteVec(EDGE), yaw, 0);
        StatsCommands.setBase(player, GeneratedStats.DODGE_CHANCE, 0);
        setBase(player, Attributes.ARMOR, 0);
        player.setHealth(player.getMaxHealth());
        return player;
    }

    /** Puts {@code weapon} into the player's main hand and holds it up for {@code ticks} of the player's ticks. */
    private static ItemStack raise(ServerPlayer player, ItemStack weapon, int ticks) {
        player.setItemInHand(InteractionHand.MAIN_HAND, weapon);
        player.startUsingItem(InteractionHand.MAIN_HAND);
        for (int i = 0; i < ticks; i++) {
            player.doTick();
        }
        return weapon;
    }

    /** A zombie that stands still south of the platform's north edge, at level 1, so its hits are unscaled. */
    private static Zombie zombieInFront(GameTestHelper helper) {
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(2, 1, 3));
        zombie.setNoAi(true);
        MobLevels.setLevel(zombie, EntityLevel.MIN);
        return zombie;
    }

    private static void assertFullHit(GameTestHelper helper, ServerPlayer player,
            net.minecraft.world.damagesource.DamageSource source, String what) {
        player.invulnerableTime = 0;
        player.setHealth(player.getMaxHealth());
        float before = player.getHealth();
        player.hurtServer(helper.getLevel(), source, HIT);
        double dealt = before - player.getHealth();
        helper.assertTrue(close(dealt, HIT), what + ": the full " + HIT + " expected, dealt " + dealt);
    }
}
