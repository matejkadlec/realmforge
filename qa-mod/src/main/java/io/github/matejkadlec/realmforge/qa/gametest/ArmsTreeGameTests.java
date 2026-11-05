package io.github.matejkadlec.realmforge.qa.gametest;

import io.github.matejkadlec.realmforge.character.Characters;
import io.github.matejkadlec.realmforge.combat.DamagePipeline;
import io.github.matejkadlec.realmforge.effect.EffectStacks;
import io.github.matejkadlec.realmforge.effect.StackTicks;
import io.github.matejkadlec.realmforge.generated.GeneratedEffects;
import io.github.matejkadlec.realmforge.generated.GeneratedSpells;
import io.github.matejkadlec.realmforge.generated.GeneratedStats;
import io.github.matejkadlec.realmforge.mob.MobLevels;
import io.github.matejkadlec.realmforge.qa.combat.QaItems;
import io.github.matejkadlec.realmforge.qa.command.StatsCommands;
import io.github.matejkadlec.realmforge.spell.CastFailure;
import io.github.matejkadlec.realmforge.spell.CastResult;
import io.github.matejkadlec.realmforge.spell.ChargeMotion;
import io.github.matejkadlec.realmforge.spell.Charges;
import io.github.matejkadlec.realmforge.spell.CombatState;
import io.github.matejkadlec.realmforge.spell.DamageEffect;
import io.github.matejkadlec.realmforge.spell.DamageOverTimeEffect;
import io.github.matejkadlec.realmforge.spell.EffectContext;
import io.github.matejkadlec.realmforge.spell.MeleeEffect;
import io.github.matejkadlec.realmforge.spell.Passives;
import io.github.matejkadlec.realmforge.spell.Proc;
import io.github.matejkadlec.realmforge.spell.ResourceTypes;
import io.github.matejkadlec.realmforge.spell.Resources;
import io.github.matejkadlec.realmforge.spell.Spell;
import io.github.matejkadlec.realmforge.spell.SpellAttachments;
import io.github.matejkadlec.realmforge.spell.SpellBarView;
import io.github.matejkadlec.realmforge.spell.SpellCaster;
import io.github.matejkadlec.realmforge.spell.SpellEffect;
import io.github.matejkadlec.realmforge.spell.SpellTarget;
import io.github.matejkadlec.realmforge.spell.SpellText;
import io.github.matejkadlec.realmforge.spell.SpellTooltip;
import io.github.matejkadlec.realmforge.spell.Spellbook;
import io.github.matejkadlec.realmforge.spell.Spells;
import io.github.matejkadlec.realmforge.spell.Windows;
import io.github.matejkadlec.realmforge.stats.CharacterStats;
import io.github.matejkadlec.realmforge.stats.EntityLevel;
import io.github.matejkadlec.realmforge.stats.StatsAttachments;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * The Arms Warrior's "Bloodletter" tree (RLM-543; Matěj, 2026-10-30, "Spell Trees to Level 18"): every active spell's
 * damage at rank 1 and at its highest rank at a fixed Attack Damage (system C, RLM-534), Rend's bleed, Overpower only
 * after a block, Victory Rush only after a kill that is not gray and its heal, Execute only below 20 % with its extra
 * rage, its refund and its lit window, Charge's rush to the near side and stun round the landing and its distance by
 * rank, a player's Charge landing on the server (RLM-602), Deep Wounds' bleed of a share of the crit, Bloodthirst's
 * proc, the tooltips' next rank, the handoff's setup line against Astone, the rage and cooldowns of Matěj's
 * rebalance (RLM-604), and Battle Stance's bonus with a two-handed weapon only (RLM-596).
 */
public final class ArmsTreeGameTests {
    private static final double HEALTH = SpellGameTests.HEALTH;
    private static final double TOLERANCE = 1e-3;
    private static final double ATTACK_DAMAGE = 10;
    /** A Bleeding of 6 s ticks every half second: 12 ticks. */
    private static final int BLEED_TICKS = 12;
    /** Charge's rush: 24 blocks a second is 1.2 blocks a tick. */
    private static final double CHARGE_PER_TICK = 1.2;
    /**
     * Charge's landing at a target 8 blocks away (the player at z 1.5, the husk {@code GameTestHelper#spawn} puts at its
     * block's bottom centre, z 9.5): 7 ticks of travel for its 7.4 blocks' gap, and the 10 of slack (RLM-611).
     */
    private static final int CHARGE_TIMEOUT_TICKS = 17;
    /** The protection and durability every netherite piece gets for the Astone check: level V (gear.md). */
    private static final String ARMOR_V = "[enchantments={\"minecraft:protection\":5,\"minecraft:unbreaking\":5}]";
    /**
     * The playtest's setup line against Astone (RLM-543, RLM-503; gear.md's expected gear for Astone, RLM-551): a new
     * Human Warrior, chapter 1's raised cap, level 18, the tree at 27 ranks (Slam and Overpower 5, Deep Wounds, Rend
     * and Execute 3, Bloodthirst, Charge and Victory Rush 2, Battle Shout and Two-Handed Specialization 1), which since
     * C5 is more than a real level 18 character's 18 and skips the rules, to keep the state Astone was balanced with,
     * which has no affixes, gems or curios, and Matěj expects those to match the missing ranks (2026-11-04, RLM-503);
     * full netherite armor with Protection V and Unbreaking V, and a netherite sword with Sharpness V and Unbreaking V;
     * the kit's shield stays for Overpower's blocks. The handoff gives it to
     * Matěj as {@code execute as @p at @p run} and this line.
     */
    static final String ASTONE_SETUP = "qa run qa character reset; "
            + "qa character create realmforge:human realmforge:warrior; "
            + "advancement grant @s only realmforge:chapter/chapter_1; qa level 18; "
            + "qa spell learn realmforge:slam 5; qa spell learn realmforge:overpower 5; "
            + "qa spell learn realmforge:deep_wounds 3; qa spell learn realmforge:rend 3; "
            + "qa spell learn realmforge:execute 3; qa spell learn realmforge:bloodthirst 2; "
            + "qa spell learn realmforge:charge 2; qa spell learn realmforge:victory_rush 2; "
            + "qa spell learn realmforge:battle_shout; qa spell learn realmforge:two_handed_weapon_specialization; "
            + "give @s minecraft:netherite_helmet" + ARMOR_V + "; give @s minecraft:netherite_chestplate" + ARMOR_V
            + "; give @s minecraft:netherite_leggings" + ARMOR_V + "; give @s minecraft:netherite_boots" + ARMOR_V
            + "; give @s minecraft:netherite_sword[enchantments={\"minecraft:sharpness\":5,\"minecraft:unbreaking\":5}]";

    private ArmsTreeGameTests() {
    }

    /**
     * Each active spell's first damage effect at rank 1 and at its highest rank, at 10 Attack Damage, no crit and no
     * school power: the table's amount plus its coefficient of 10 (Matěj's rebalance, RLM-604: Slam 5 + 150 % and
     * 9 + 230 %, Rend 5 + 50 % and 7 + 70 %, Overpower 6 + 120 % and 10 + 180 %, Execute 10 + 190 % and 14 + 230 %;
     * Victory Rush 5 + 80 % and 7 + 100 %), on a husk without armor; Rend's bleed 8 + 90 % and 12 + 130 % over its 6 s.
     */
    @QaGameTest
    public static void everyActiveDealsItsRanksDamage(GameTestHelper helper) {
        ServerPlayer player = fighter(helper, "qa-arms-ranks", QaPlayers.PLATFORM_MIDDLE);
        Husk husk = SpellGameTests.husk(helper, new BlockPos(2, 1, 4));
        Map<ResourceKey<Spell>, double[]> table = Map.of(
                GeneratedSpells.SLAM, new double[] {1, 20, 5, 32},
                GeneratedSpells.REND, new double[] {1, 10, 3, 14},
                GeneratedSpells.OVERPOWER, new double[] {1, 18, 5, 28},
                GeneratedSpells.VICTORY_RUSH, new double[] {1, 13, 3, 17},
                GeneratedSpells.EXECUTE, new double[] {1, 29, 3, 37});
        table.forEach((key, checks) -> {
            Holder.Reference<Spell> spell = spell(helper, key);
            helper.assertValueEqual(spell.value().maxLevel(), (int) checks[2], key.identifier() + "'s highest rank");
            DamageEffect damage = firstDamage(spell.value().effects()).orElseThrow();
            for (int n = 0; n < checks.length; n += 2) {
                int rank = (int) checks[n];
                float before = husk.getHealth();
                damage.applyTo(EffectContext.of(helper.getLevel(), player, spell, rank, Optional.empty()), husk,
                        SpellTarget.of(player, husk));
                helper.assertTrue(close(before - husk.getHealth(), checks[n + 1]), key.identifier() + " rank " + rank
                        + ": " + checks[n + 1] + " expected, dealt " + (before - husk.getHealth()));
                helper.assertValueEqual(values(helper, player, spell.value(), rank).get("damage"),
                        SpellText.decimal(checks[n + 1]), key.identifier() + "'s tooltip damage at rank " + rank);
            }
        });
        Holder.Reference<Spell> rend = spell(helper, GeneratedSpells.REND);
        DamageOverTimeEffect bleed = (DamageOverTimeEffect) rend.value().effects().get(1);
        for (double[] check : new double[][] {{1, 17}, {3, 25}}) {
            Husk bled = SpellGameTests.husk(helper, new BlockPos(check[0] == 1 ? 1 : 3, 1, 2));
            bleed.applyTo(EffectContext.of(helper.getLevel(), player, rend, (int) check[0], Optional.empty()), bled,
                    SpellTarget.of(player, bled));
            List<EffectStacks.Stack> stacks = StackTicks.of(bled, GeneratedEffects.BLEEDING);
            helper.assertValueEqual(stacks.size(), 1, "Rend's bleed at rank " + (int) check[0]);
            helper.assertTrue(close(stacks.getFirst().perTick() * BLEED_TICKS, check[1]), "Rend rank "
                    + (int) check[0] + " bleeds " + check[1] + " over 6 s: " + stacks.getFirst());
            helper.assertValueEqual(values(helper, player, rend.value(), (int) check[0]).get("dot_damage"),
                    SpellText.decimal(check[1]), "the tooltip's bleed at rank " + (int) check[0]);
        }
        logout(player);
        helper.succeed();
    }

    /**
     * Matěj's rebalance (RLM-604): each changed spell's rage at rank 1 and its highest rank and its own cooldown at both.
     * Battle Shout has no cost at all, and Victory Rush and Execute no cooldown but the global one, which every spell is
     * on.
     */
    @QaGameTest
    public static void everyChangedSpellCostsItsRanksRageAndCooldown(GameTestHelper helper) {
        Map<ResourceKey<Spell>, double[]> table = Map.of(
                GeneratedSpells.SLAM, new double[] {30, 30, 4},
                GeneratedSpells.REND, new double[] {20, 40, 6},
                GeneratedSpells.BATTLE_SHOUT, new double[] {0, 0, 60},
                GeneratedSpells.VICTORY_RUSH, new double[] {10, 10, 0},
                GeneratedSpells.OVERPOWER, new double[] {20, 20, 4},
                GeneratedSpells.EXECUTE, new double[] {10, 30, 0});
        table.forEach((key, checks) -> {
            Spell spell = spell(helper, key).value();
            helper.assertTrue(close(spell.costAt(1), checks[0]), key.identifier() + " costs " + checks[0]
                    + " at rank 1: " + spell.costAt(1));
            helper.assertTrue(close(spell.costAt(spell.maxLevel()), checks[1]), key.identifier() + " costs " + checks[1]
                    + " at its highest rank: " + spell.costAt(spell.maxLevel()));
            for (int rank : new int[] {1, spell.maxLevel()}) {
                helper.assertValueEqual(spell.cooldownTicksAt(rank), (int) checks[2] * 20,
                        key.identifier() + "'s cooldown at rank " + rank);
            }
            helper.assertValueEqual(spell.globalCooldownTicks(), Spell.DEFAULT_GLOBAL_COOLDOWN_TICKS,
                    key.identifier() + "'s global cooldown");
        });
        helper.assertTrue(spell(helper, GeneratedSpells.BATTLE_SHOUT).value().cost().isEmpty(), "Battle Shout is free");
        helper.succeed();
    }

    /**
     * Overpower is refused while its window is closed; the player's raised greatsword blocking a zombie's hit opens it
     * for 5 s, a cast uses it for 6 + 120 % of Attack Damage and 20 rage, and the next cast is refused again.
     */
    @QaGameTest
    public static void overpowerIsUsableOnlyAfterABlock(GameTestHelper helper) {
        ServerPlayer player = fighter(helper, "qa-arms-overpower", new Vec3(2.5, 1, 0.5));
        learn(helper, player, GeneratedSpells.OVERPOWER, 1);
        Resources.set(player, ResourceTypes.RAGE, 100);
        Husk husk = SpellGameTests.husk(helper, new BlockPos(2, 1, 1));
        player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_SWORD));
        assertRefused(helper, player, GeneratedSpells.OVERPOWER, husk, CastFailure.NO_WINDOW);

        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(QaItems.TEST_GREATSWORD.get()));
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(2, 1, 3));
        zombie.setNoAi(true);
        MobLevels.setLevel(zombie, EntityLevel.MIN);
        player.startUsingItem(InteractionHand.MAIN_HAND);
        player.doTick();
        player.hurtServer(helper.getLevel(), helper.getLevel().damageSources().mobAttack(zombie), 4);
        long now = helper.getLevel().getGameTime();
        helper.assertValueEqual(player.getData(SpellAttachments.COMBAT).windows().get(GeneratedSpells.OVERPOWER),
                now + 100, "the block opened Overpower's window for 5 s");
        player.stopUsingItem();
        player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_SWORD));

        double expected = 6 + 1.2 * attackDamage(player);
        assertCast(helper, player, GeneratedSpells.OVERPOWER, husk);
        helper.assertTrue(close(HEALTH - husk.getHealth(), expected), "Overpower hit for " + expected + ": "
                + (HEALTH - husk.getHealth()));
        helper.assertTrue(close(Resources.amount(player, ResourceTypes.RAGE), 80), "for 20 rage");
        Spells.resetCooldowns(player);
        assertRefused(helper, player, GeneratedSpells.OVERPOWER, husk, CastFailure.NO_WINDOW);
        logout(player);
        helper.succeed();
    }

    /**
     * Victory Rush is refused without a kill and after a gray one (4 levels below a level 10 player); a kill 3 levels
     * below opens it, and a cast strikes for 5 + 80 % of Attack Damage for 10 rage and heals the player 10 % of their
     * max health. That cast closes the window, so having no cooldown (RLM-604) gives no second strike or heal.
     */
    @QaGameTest
    public static void victoryRushIsUsableOnlyAfterAKillThatIsNotGrayAndHeals(GameTestHelper helper) {
        ServerPlayer player = fighter(helper, "qa-arms-rush", new Vec3(2.5, 1, 0.5));
        player.setData(StatsAttachments.LEVEL, new EntityLevel(10));
        learn(helper, player, GeneratedSpells.VICTORY_RUSH, 1);
        Resources.set(player, ResourceTypes.RAGE, 100);
        player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_SWORD));
        Husk husk = SpellGameTests.husk(helper, new BlockPos(2, 1, 1));
        assertRefused(helper, player, GeneratedSpells.VICTORY_RUSH, husk, CastFailure.NO_WINDOW);

        Husk gray = SpellGameTests.husk(helper, new BlockPos(0, 1, 3));
        MobLevels.setLevel(gray, 6);
        gray.hurtServer(helper.getLevel(), helper.getLevel().damageSources().playerAttack(player), 100_000);
        helper.assertFalse(gray.isAlive(), "the gray husk died");
        assertRefused(helper, player, GeneratedSpells.VICTORY_RUSH, husk, CastFailure.NO_WINDOW);

        Husk worthy = SpellGameTests.husk(helper, new BlockPos(4, 1, 3));
        MobLevels.setLevel(worthy, 7);
        worthy.hurtServer(helper.getLevel(), helper.getLevel().damageSources().playerAttack(player), 100_000);
        player.setHealth(10);
        double expected = 5 + 0.8 * attackDamage(player);
        assertCast(helper, player, GeneratedSpells.VICTORY_RUSH, husk);
        helper.assertTrue(close(HEALTH - husk.getHealth(), expected), "Victory Rush hit for " + expected + ": "
                + (HEALTH - husk.getHealth()));
        helper.assertTrue(close(player.getHealth(), 10 + 0.1 * player.getMaxHealth()), "healed 10 % of max health: "
                + player.getHealth());
        helper.assertTrue(close(Resources.amount(player, ResourceTypes.RAGE), 90), "for 10 rage");
        helper.assertValueEqual(values(helper, player, spell(helper, GeneratedSpells.VICTORY_RUSH).value(), 3)
                .get("max_health_heal"), "20", "20 % at rank 3");
        Spells.resetCooldowns(player);
        float afterTheStrike = husk.getHealth();
        float afterTheHeal = player.getHealth();
        assertRefused(helper, player, GeneratedSpells.VICTORY_RUSH, husk, CastFailure.NO_WINDOW);
        helper.assertTrue(close(husk.getHealth(), afterTheStrike), "no second strike in the window");
        helper.assertTrue(close(player.getHealth(), afterTheHeal), "no second heal in the window");
        logout(player);
        helper.succeed();
    }

    /**
     * Execute is refused on a target at 20 % health or more; below it, it costs 10 rage at rank 1 and spends up to 30
     * more, 3 % more damage each (40 rage: × 1.9). A kill refunds 5 rage at rank 1, where 45 rage leaves 5, and 15 at
     * rank 3, where the cast costs 30 and the other 15 go as extra (Matěj, 2026-11-03, RLM-604).
     */
    @QaGameTest
    public static void executeIsUsableOnlyBelowAFifthAndRefundsAKill(GameTestHelper helper) {
        ServerPlayer player = fighter(helper, "qa-arms-execute", new Vec3(2.5, 1, 0.5));
        learn(helper, player, GeneratedSpells.EXECUTE, 1);
        player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_SWORD));
        Husk husk = SpellGameTests.husk(helper, new BlockPos(2, 1, 1));
        Resources.set(player, ResourceTypes.RAGE, 40);
        husk.setHealth((float) (HEALTH * 0.2));
        assertRefused(helper, player, GeneratedSpells.EXECUTE, husk, CastFailure.TARGET_HEALTH_TOO_HIGH);

        float low = (float) (HEALTH * 0.15);
        husk.setHealth(low);
        double expected = (10 + 1.9 * attackDamage(player)) * 1.9;
        assertCast(helper, player, GeneratedSpells.EXECUTE, husk);
        helper.assertTrue(close(low - husk.getHealth(), expected), "Execute rank 1 with 30 extra rage: " + expected
                + " expected, dealt " + (low - husk.getHealth()));
        helper.assertTrue(close(Resources.amount(player, ResourceTypes.RAGE), 0), "10 + 30 more rage spent");

        for (int rank : new int[] {1, 3}) {
            learn(helper, player, GeneratedSpells.EXECUTE, rank);
            Spells.resetCooldowns(player);
            Resources.set(player, ResourceTypes.RAGE, 45);
            Husk dying = SpellGameTests.husk(helper, new BlockPos(2, 1, 1));
            husk.discard();
            husk = dying;
            dying.setHealth(5);
            assertCast(helper, player, GeneratedSpells.EXECUTE, dying);
            helper.assertFalse(dying.isAlive(), "rank " + rank + "'s Execute killed the husk");
            helper.assertTrue(close(Resources.amount(player, ResourceTypes.RAGE), rank == 3 ? 15 : 10), "rank " + rank
                    + "'s kill refunded " + (rank == 3 ? 15 : 5) + " rage: "
                    + Resources.amount(player, ResourceTypes.RAGE));
        }
        logout(player);
        helper.succeed();
    }

    /**
     * Execute lights up on the spell bar while the player's current target is below a fifth of its health (Matěj's
     * playtest of 2026-11-03): the server's check opens its entry, a cast that leaves the target alive keeps it, and it
     * goes out once the target is healed or an enemy at full health becomes the current target; the cast itself still
     * checks only the target it aims at.
     */
    @QaGameTest
    public static void executeLightsUpWhileTheCurrentTargetIsLow(GameTestHelper helper) {
        ServerPlayer player = fighter(helper, "qa-arms-execute-lit", new Vec3(2.5, 1, 0.5));
        learn(helper, player, GeneratedSpells.EXECUTE, 1);
        player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_SWORD));
        Husk husk = SpellGameTests.husk(helper, new BlockPos(2, 1, 1));
        Husk other = SpellGameTests.husk(helper, new BlockPos(3, 1, 1));
        CombatState state = player.getData(SpellAttachments.COMBAT);
        long now = helper.getLevel().getGameTime();
        Windows.checkTargetHealth(player);
        helper.assertFalse(lit(helper, player), "no current target: Execute is dark");

        husk.setHealth((float) (HEALTH * 0.15));
        state.setCurrentTarget(husk.getUUID(), now);
        Windows.checkTargetHealth(player);
        helper.assertTrue(lit(helper, player), "a current target below a fifth lights Execute");
        Resources.set(player, ResourceTypes.RAGE, 15);
        assertCast(helper, player, GeneratedSpells.EXECUTE, husk);
        helper.assertTrue(husk.isAlive() && lit(helper, player), "a cast that leaves it alive keeps Execute lit");

        husk.setHealth((float) HEALTH);
        Windows.checkTargetHealth(player);
        helper.assertFalse(lit(helper, player), "healed above a fifth, Execute is dark again");
        husk.setHealth((float) (HEALTH * 0.15));
        Windows.checkTargetHealth(player);
        helper.assertTrue(lit(helper, player), "below a fifth again");
        state.setCurrentTarget(other.getUUID(), now);
        Windows.checkTargetHealth(player);
        helper.assertFalse(lit(helper, player), "an enemy at full health as the current target puts it out");

        Spells.resetCooldowns(player);
        Resources.set(player, ResourceTypes.RAGE, 15);
        assertCast(helper, player, GeneratedSpells.EXECUTE, husk);
        logout(player);
        helper.succeed();
    }

    /**
     * A bleed's ticks on another enemy leave the current target where it is, so Execute stays lit for the low husk the
     * player is fighting while a husk at full health bleeds beside it; a direct hit on that husk does take the target.
     */
    @QaGameTest(timeoutTicks = 60)
    public static void aBleedsTicksLeaveTheCurrentTarget(GameTestHelper helper) {
        ServerPlayer player = fighter(helper, "qa-arms-execute-bleed", new Vec3(2.5, 1, 0.5));
        learn(helper, player, GeneratedSpells.EXECUTE, 1);
        Husk low = SpellGameTests.husk(helper, new BlockPos(2, 1, 1));
        Husk bleeding = SpellGameTests.husk(helper, new BlockPos(3, 1, 1));
        low.setHealth((float) (HEALTH * 0.15));
        CombatState state = player.getData(SpellAttachments.COMBAT);
        state.setCurrentTarget(low.getUUID(), helper.getLevel().getGameTime());
        Holder.Reference<Spell> rend = spell(helper, GeneratedSpells.REND);
        DamageOverTimeEffect bleed = (DamageOverTimeEffect) rend.value().effects().get(1);
        bleed.applyTo(EffectContext.of(helper.getLevel(), player, rend, 1, Optional.empty()), bleeding,
                SpellTarget.of(player, bleeding));
        float full = bleeding.getHealth();
        helper.runAfterDelay(25, () -> {
            helper.assertTrue(bleeding.getHealth() < full, "the bleed ticked on the second husk");
            long now = helper.getLevel().getGameTime();
            helper.assertTrue(state.isCurrentTarget(low.getUUID(), now, 200), "its ticks left the current target");
            Windows.checkTargetHealth(player);
            helper.assertTrue(lit(helper, player), "Execute stays lit for the low husk");

            bleeding.invulnerableTime = 0;
            bleeding.hurtServer(helper.getLevel(), helper.getLevel().damageSources().playerAttack(player), 1);
            helper.assertTrue(state.isCurrentTarget(bleeding.getUUID(), now, 200), "a direct hit takes the target");
            Windows.checkTargetHealth(player);
            helper.assertFalse(lit(helper, player), "the new target is not low, so Execute is dark");
            logout(player);
            helper.succeed();
        });
    }

    /** Whether Execute's slot on {@code player}'s spell bar glows, as the client draws it from the synced windows. */
    private static boolean lit(GameTestHelper helper, ServerPlayer player) {
        SpellBarView view = SpellBarView.of(player.getData(SpellAttachments.SPELLBOOK),
                player.getData(SpellAttachments.COMBAT), helper.getLevel().getGameTime(),
                key -> Spells.get(player.registryAccess(), key).map(Holder::value), 0,
                Spell.DEFAULT_GLOBAL_COOLDOWN_TICKS);
        SpellBarView.Slot slot = view.slots().stream()
                .filter(candidate -> candidate.key().equals(Optional.of(GeneratedSpells.EXECUTE))).findFirst()
                .orElseThrow(() -> helper.assertionException(Component.literal("Execute is on the bar")));
        return slot.windowTicks() > 0;
    }

    /**
     * A husk's Charge (the server moves mobs, not a test's fake players; a player's client runs the same
     * {@code ChargeMotion}, which the QA mod's charge trace measures) rushes it at its target 7 blocks away until their
     * middles are 1 block apart on the side it came from, never through it, stuns for 3 s the target and a husk beside
     * it, within 1.5 blocks of that point, but not a husk 4 blocks to the side, then hops a block back, so their middles
     * end up 2 blocks apart (Matěj's playtests of 2026-11-03).
     */
    @QaGameTest(template = QaGameTests.PLATFORM_17X17, timeoutTicks = 60)
    public static void chargeRushesAtItsTargetAndStunsRoundTheLanding(GameTestHelper helper) {
        Husk caster = helper.spawnWithNoFreeWill(EntityType.HUSK, new BlockPos(8, 1, 2));
        MobLevels.setLevel(caster, EntityLevel.MIN);
        Husk target = SpellGameTests.husk(helper, new BlockPos(8, 1, 9));
        Husk beside = SpellGameTests.husk(helper, new BlockPos(9, 1, 9));
        Husk aside = SpellGameTests.husk(helper, new BlockPos(12, 1, 9));
        CastResult result = SpellCaster.cast(caster, GeneratedSpells.CHARGE, target);
        helper.assertTrue(result.success(), "Charge was refused: " + result.failure());
        helper.assertTrue(Charges.isCharging(caster), "the husk charges");
        double[] closest = trackCharge(helper, caster, target, -1);
        helper.succeedWhen(() -> {
            helper.assertFalse(Charges.isCharging(caster), "the husk landed and hopped back");
            assertChargeEnded(helper, caster, target, -1, closest);
            helper.assertTrue(target.getEffect(GeneratedEffects.STUNNED) != null
                    && target.getEffect(GeneratedEffects.STUNNED).getDuration() > 40, "the target is stunned for 3 s");
            helper.assertTrue(beside.hasEffect(GeneratedEffects.STUNNED), "the husk beside it is stunned");
            helper.assertFalse(aside.hasEffect(GeneratedEffects.STUNNED), "the husk 4 blocks aside is not");
        });
    }

    /** A Charge from the target's other side stops at that side, its back, and hops back from there. */
    @QaGameTest(template = QaGameTests.PLATFORM_17X17, timeoutTicks = 60)
    public static void chargeStopsOnTheSideItCameFrom(GameTestHelper helper) {
        Husk caster = helper.spawnWithNoFreeWill(EntityType.HUSK, new BlockPos(8, 1, 15));
        MobLevels.setLevel(caster, EntityLevel.MIN);
        Husk target = SpellGameTests.husk(helper, new BlockPos(8, 1, 8));
        CastResult result = SpellCaster.cast(caster, GeneratedSpells.CHARGE, target);
        helper.assertTrue(result.success(), "Charge was refused: " + result.failure());
        double[] closest = trackCharge(helper, caster, target, 1);
        helper.succeedWhen(() -> {
            helper.assertFalse(Charges.isCharging(caster), "the husk landed and hopped back");
            assertChargeEnded(helper, caster, target, 1, closest);
        });
    }

    /**
     * Watches a charge along z ({@code side} -1: from below the target's z, 1: from above it): the closest the caster's
     * middle came to the target's, and how far they were when the target was first stunned.
     */
    private static double[] trackCharge(GameTestHelper helper, LivingEntity caster, LivingEntity target, int side) {
        double[] seen = {Double.MAX_VALUE, Double.NaN};
        helper.onEachTick(() -> {
            double along = (caster.getZ() - target.getZ()) * side;
            seen[0] = Math.min(seen[0], along);
            if (Double.isNaN(seen[1]) && target.hasEffect(GeneratedEffects.STUNNED)) {
                seen[1] = along;
            }
        });
        return seen;
    }

    /** The caster came within exactly a block of the target and was stunning it then, and stopped 2 blocks from it. */
    private static void assertChargeEnded(GameTestHelper helper, LivingEntity caster, LivingEntity target, int side,
            double[] seen) {
        double along = (caster.getZ() - target.getZ()) * side;
        helper.assertTrue(Math.abs(seen[0] - 1) < 0.05, "a block from its target at the closest: " + seen[0]);
        helper.assertTrue(Math.abs(seen[1] - 1) < 0.3, "the stun lands at the closest: " + seen[1]);
        helper.assertTrue(Math.abs(along - 2) < 0.1, "2 blocks from its target after the hop: " + along);
        helper.assertTrue(Math.abs(caster.getX() - target.getX()) < 0.05, "in line with its target: "
                + caster.position());
    }

    /**
     * A player's Charge needs its target at least 3 blocks away and at most 10 at rank 1, 12 at rank 2, and gives 10
     * rage at rank 1 and 20 at rank 2.
     */
    @QaGameTest(template = QaGameTests.PLATFORM_17X17)
    public static void chargeReachesByRankAndGeneratesRage(GameTestHelper helper) {
        ServerPlayer player = fighter(helper, "qa-arms-charge", new Vec3(8.5, 1, 1.5));
        learn(helper, player, GeneratedSpells.CHARGE, 1);
        Resources.set(player, ResourceTypes.RAGE, 0);
        Husk near = SpellGameTests.husk(helper, new BlockPos(8, 1, 3));
        Husk middle = SpellGameTests.husk(helper, new BlockPos(5, 1, 8));
        Husk far = SpellGameTests.husk(helper, new BlockPos(10, 1, 12));
        assertRefused(helper, player, GeneratedSpells.CHARGE, near, CastFailure.TOO_CLOSE);
        assertRefused(helper, player, GeneratedSpells.CHARGE, far, CastFailure.OUT_OF_RANGE);
        assertCast(helper, player, GeneratedSpells.CHARGE, middle);
        helper.assertTrue(close(Resources.amount(player, ResourceTypes.RAGE), 10), "10 rage at rank 1");

        learn(helper, player, GeneratedSpells.CHARGE, 2);
        Spells.resetCooldowns(player);
        assertCast(helper, player, GeneratedSpells.CHARGE, far);
        helper.assertTrue(close(Resources.amount(player, ResourceTypes.RAGE), 30), "20 more at rank 2");
        logout(player);
        helper.succeed();
    }

    /**
     * A player's Charge lands on the server (RLM-602), whose fake player has no client to run the rush, so the test moves
     * them as their client would report: more than a tick of travel from the closest point nothing lands, and within one
     * tick the next server tick stuns every enemy within 1.5 blocks of that point, which reaches the husk beside the
     * target though it is out of reach of where the player stands.
     */
    @QaGameTest(template = QaGameTests.PLATFORM_17X17, timeoutTicks = 60)
    public static void aPlayersChargeLandsWithinATickOfTheClosestPoint(GameTestHelper helper) {
        ServerPlayer player = fighter(helper, "qa-arms-charge-land", new Vec3(8.5, 1, 1.5));
        learn(helper, player, GeneratedSpells.CHARGE, 1);
        Husk target = SpellGameTests.husk(helper, new BlockPos(8, 1, 9));
        Husk beside = SpellGameTests.husk(helper, new BlockPos(9, 1, 9));
        Husk aside = SpellGameTests.husk(helper, new BlockPos(12, 1, 9));
        assertCast(helper, player, GeneratedSpells.CHARGE, target);
        double closest = target.getZ() - (player.getBbWidth() + target.getBbWidth()) / 2 - ChargeMotion.GAP;
        player.setPos(target.getX(), player.getY(), closest - CHARGE_PER_TICK - 0.1);
        helper.runAfterDelay(2, () -> {
            helper.assertTrue(Charges.isCharging(player), "more than a tick of travel away, the charge goes on");
            helper.assertFalse(target.hasEffect(GeneratedEffects.STUNNED), "and nothing is stunned yet");
            player.setPos(target.getX(), player.getY(), closest - CHARGE_PER_TICK + 0.1);
        });
        helper.runAfterDelay(3, () -> {
            helper.assertFalse(Charges.isCharging(player), "within a tick of travel, the charge landed");
            helper.assertTrue(target.getEffect(GeneratedEffects.STUNNED) != null
                    && target.getEffect(GeneratedEffects.STUNNED).getDuration() > 40, "the target is stunned for 3 s");
            helper.assertTrue(beside.hasEffect(GeneratedEffects.STUNNED), "the husk beside it, round the closest point");
            helper.assertFalse(aside.hasEffect(GeneratedEffects.STUNNED), "not the husk 4 blocks aside");
            logout(player);
            helper.succeed();
        });
    }

    /**
     * A player's Charge that never reaches its closest point (their client never moved them: a wall) lands when its time
     * is up, round where they stand: the husk beside them is stunned, and the target, far away, is not.
     */
    @QaGameTest(template = QaGameTests.PLATFORM_17X17, timeoutTicks = 60)
    public static void aPlayersChargeThatNeverArrivesLandsWhereTheyStand(GameTestHelper helper) {
        ServerPlayer player = fighter(helper, "qa-arms-charge-timeout", new Vec3(8.5, 1, 1.5));
        learn(helper, player, GeneratedSpells.CHARGE, 1);
        Husk target = SpellGameTests.husk(helper, new BlockPos(8, 1, 9));
        Husk near = SpellGameTests.husk(helper, new BlockPos(9, 1, 1));
        Vec3 start = player.position();
        assertCast(helper, player, GeneratedSpells.CHARGE, target);
        helper.runAfterDelay(CHARGE_TIMEOUT_TICKS - 4, () -> {
            helper.assertTrue(Charges.isCharging(player), "its time is not up yet");
            helper.assertFalse(near.hasEffect(GeneratedEffects.STUNNED), "and nothing is stunned yet");
        });
        helper.runAfterDelay(CHARGE_TIMEOUT_TICKS + 4, () -> {
            helper.assertFalse(Charges.isCharging(player), "its time is up");
            helper.assertTrue(near.hasEffect(GeneratedEffects.STUNNED), "the husk beside the player is stunned");
            helper.assertFalse(target.hasEffect(GeneratedEffects.STUNNED), "the far target is not");
            helper.assertValueEqual(player.position(), start, "the server never moved the player");
            logout(player);
            helper.succeed();
        });
    }

    /** A player's Charge whose target is gone lands at once, round where they stand. */
    @QaGameTest(template = QaGameTests.PLATFORM_17X17, timeoutTicks = 60)
    public static void aPlayersChargeLandsWhereTheyStandWhenItsTargetIsGone(GameTestHelper helper) {
        ServerPlayer player = fighter(helper, "qa-arms-charge-lost", new Vec3(8.5, 1, 1.5));
        learn(helper, player, GeneratedSpells.CHARGE, 1);
        Husk target = SpellGameTests.husk(helper, new BlockPos(8, 1, 9));
        Husk near = SpellGameTests.husk(helper, new BlockPos(9, 1, 1));
        assertCast(helper, player, GeneratedSpells.CHARGE, target);
        helper.runAfterDelay(2, () -> {
            helper.assertTrue(Charges.isCharging(player), "the charge goes on while its target is there");
            helper.assertFalse(near.hasEffect(GeneratedEffects.STUNNED), "and nothing is stunned yet");
            target.discard();
        });
        helper.runAfterDelay(3, () -> {
            helper.assertFalse(Charges.isCharging(player), "its target is gone");
            helper.assertTrue(near.hasEffect(GeneratedEffects.STUNNED), "the husk beside the player is stunned");
            logout(player);
            helper.succeed();
        });
    }

    /**
     * Deep Wounds at rank 3 makes a crit's target bleed for 60 % of the crit's damage over 6 s (Test Sure Crit: 5 × 2),
     * a second crit adds its share to what the bleed had left, and at rank 1 a crit bleeds for 20 %.
     */
    @QaGameTest
    public static void deepWoundsBleedsForItsShareOfACrit(GameTestHelper helper) {
        ServerPlayer player = fighter(helper, "qa-arms-wounds", QaPlayers.PLATFORM_MIDDLE);
        learn(helper, player, GeneratedSpells.TEST_SURE_CRIT, 1);
        learn(helper, player, GeneratedSpells.DEEP_WOUNDS, 3);
        Husk husk = SpellGameTests.husk(helper, new BlockPos(2, 1, 4));
        assertCast(helper, player, GeneratedSpells.TEST_SURE_CRIT, husk);
        helper.assertTrue(close(HEALTH - husk.getHealth(), 10), "the crit dealt 10: " + (HEALTH - husk.getHealth()));
        List<EffectStacks.Stack> stacks = StackTicks.of(husk, GeneratedEffects.BLEEDING);
        helper.assertValueEqual(stacks.size(), 1, "the crit made the husk bleed");
        helper.assertValueEqual(stacks.getFirst().spell(), Optional.of(GeneratedSpells.DEEP_WOUNDS.identifier()),
                "Deep Wounds' bleed");
        helper.assertTrue(close(stacks.getFirst().perTick() * BLEED_TICKS, 6), "60 % of 10: " + stacks.getFirst());

        Spells.resetCooldowns(player);
        assertCast(helper, player, GeneratedSpells.TEST_SURE_CRIT, husk);
        stacks = StackTicks.of(husk, GeneratedEffects.BLEEDING);
        helper.assertValueEqual(stacks.size(), 1, "one bleed of Deep Wounds");
        helper.assertTrue(close(stacks.getFirst().perTick() * BLEED_TICKS, 12), "6 more on the 6 left: "
                + stacks.getFirst());

        learn(helper, player, GeneratedSpells.DEEP_WOUNDS, 1);
        Husk other = SpellGameTests.husk(helper, new BlockPos(4, 1, 4));
        Spells.resetCooldowns(player);
        assertCast(helper, player, GeneratedSpells.TEST_SURE_CRIT, other);
        helper.assertTrue(close(StackTicks.of(other, GeneratedEffects.BLEEDING).getFirst().perTick() * BLEED_TICKS, 2),
                "20 % of 10 at rank 1");
        logout(player);
        helper.succeed();
    }

    /**
     * Bloodthirst's proc at the player's rank: a tick of Rend's Bleeding opens Overpower's window, at 10 % at rank 1
     * and 20 % at rank 2 (RLM-604), at most once every 6 s on each target.
     */
    @QaGameTest
    public static void bloodthirstOpensOverpowerOnRendsTicks(GameTestHelper helper) {
        ServerPlayer player = fighter(helper, "qa-arms-taste", QaPlayers.PLATFORM_MIDDLE);
        for (double[] check : new double[][] {{1, 0.1}, {2, 0.2}}) {
            learn(helper, player, GeneratedSpells.BLOODTHIRST, (int) check[0]);
            List<Proc> procs = Passives.procs(player);
            helper.assertValueEqual(procs.size(), 1, "one proc at rank " + (int) check[0]);
            Proc proc = procs.getFirst();
            helper.assertValueEqual(proc.trigger(), Proc.Trigger.TICK, "on a tick");
            helper.assertTrue(proc.watches(Optional.of(GeneratedEffects.BLEEDING.unwrapKey().orElseThrow()
                    .identifier()), Optional.of(GeneratedSpells.REND.identifier())), "of Rend's Bleeding");
            helper.assertFalse(proc.watches(Optional.of(GeneratedEffects.BLEEDING.unwrapKey().orElseThrow()
                    .identifier()), Optional.of(GeneratedSpells.DEEP_WOUNDS.identifier())), "not Deep Wounds'");
            helper.assertTrue(close(proc.chance(), check[1]), "at " + check[1] + ": " + proc.chance());
            helper.assertValueEqual(proc.cooldownTicks(), 120, "once every 6 s");
            helper.assertValueEqual(proc.action(), new Proc.OpenWindow(GeneratedSpells.OVERPOWER),
                    "opens Overpower's window");
        }
        logout(player);
        helper.succeed();
    }

    /**
     * RLM-596: Battle Stance, the Arms stance, is an aura of the tree's level 18 row: toggled from the spell bar, it gives
     * +5 Armor Pierce and +10 % Armor Shred while a two-handed weapon (the test greatsword, a spear) is in the main
     * hand, nothing with a one-handed sword or an empty hand while it stays on, and nothing once it is off.
     */
    @QaGameTest
    public static void battleStanceGivesItsBonusOnlyWithATwoHandedWeapon(GameTestHelper helper) {
        ServerPlayer player = fighter(helper, "qa-arms-battle-stance", QaPlayers.PLATFORM_MIDDLE);
        learn(helper, player, GeneratedSpells.BATTLE_STANCE, 1);
        Spellbook book = player.getData(SpellAttachments.SPELLBOOK);
        helper.assertTrue(book.barSlotOf(GeneratedSpells.BATTLE_STANCE) >= 0, "it goes onto the bar");
        player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(QaItems.TEST_GREATSWORD.get()));
        assertStance(helper, player, 0, 0, "off: nothing");

        helper.assertTrue(SpellCaster.cast(player, GeneratedSpells.BATTLE_STANCE, (LivingEntity) null).success(),
                "the bar turns it on");
        helper.assertTrue(player.getData(SpellAttachments.SPELLBOOK).isOn(GeneratedSpells.BATTLE_STANCE), "on");
        assertStance(helper, player, 5, 0.1, "on with the greatsword: +5 Armor Pierce, +10 % Armor Shred");
        player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SPEAR));
        assertStance(helper, player, 5, 0.1, "a spear is two-handed too");
        player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
        assertStance(helper, player, 0, 0, "a one-handed sword pauses it");
        player.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        assertStance(helper, player, 0, 0, "and so does an empty hand");
        helper.assertTrue(player.getData(SpellAttachments.SPELLBOOK).isOn(GeneratedSpells.BATTLE_STANCE),
                "paused, the stance stays on");

        player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(QaItems.TEST_GREATSWORD.get()));
        Spells.resetCooldowns(player);
        helper.assertTrue(SpellCaster.cast(player, GeneratedSpells.BATTLE_STANCE, (LivingEntity) null).success(),
                "the bar turns it off");
        assertStance(helper, player, 0, 0, "off again: nothing, even with the greatsword");
        logout(player);
        helper.succeed();
    }

    /**
     * The tooltips show the next rank's numbers below a spell's own: Charge's reach and rage, Rend's hit and bleed,
     * Execute's refund, Deep Wounds' share; and the range line follows Charge's rank.
     */
    @QaGameTest
    public static void tooltipsShowTheNextRank(GameTestHelper helper) {
        ServerPlayer player = fighter(helper, "qa-arms-tooltip", QaPlayers.PLATFORM_MIDDLE);
        List<String> charge = tooltip(helper, player, spell(helper, GeneratedSpells.CHARGE).value(), 1);
        helper.assertTrue(charge.contains("10 block range"), "rank 1 reaches 10 blocks: " + charge);
        helper.assertValueEqual(charge.subList(charge.size() - 3, charge.size()), List.of(
                "Charges an enemy 3 to 10 blocks away and stuns every enemy within 1.5 blocks of where you land for 3 "
                        + "sec. Generates 10 rage.", "Next rank:",
                "Charges an enemy 3 to 12 blocks away and stuns every enemy within 1.5 blocks of where you land for 3 "
                        + "sec. Generates 20 rage."), "Charge at rank 1 and rank 2");
        helper.assertTrue(tooltip(helper, player, spell(helper, GeneratedSpells.CHARGE).value(), 2)
                .contains("12 block range"), "rank 2 reaches 12 blocks");
        List<String> rend = tooltip(helper, player, spell(helper, GeneratedSpells.REND).value(), 1);
        helper.assertValueEqual(rend.subList(rend.size() - 3, rend.size()), List.of(
                "Wounds an enemy for Physical 10 damage and makes it bleed for Physical 17 more over 6 sec.",
                "Next rank:",
                "Wounds an enemy for Physical 12 damage and makes it bleed for Physical 21 more over 6 sec."),
                "Rend at rank 1 and rank 2; as plain text the school's icon reads as its name");
        List<String> execute = tooltip(helper, player, spell(helper, GeneratedSpells.EXECUTE).value(), 2);
        helper.assertValueEqual(execute.getLast(), "Usable only on an enemy below 20% health. Strikes it for Physical "
                + "37 damage, spending up to 30 more rage for 3% more damage each. A kill refunds 15 rage.",
                "Execute's next rank, rank 3");
        List<String> wounds = tooltip(helper, player, spell(helper, GeneratedSpells.DEEP_WOUNDS).value(), 1);
        helper.assertValueEqual(wounds.subList(wounds.size() - 3, wounds.size()), List.of(
                "Your critical hits make the enemy bleed for 20% of the hit's damage over 6 sec.", "Next rank:",
                "Your critical hits make the enemy bleed for 40% of the hit's damage over 6 sec."),
                "Deep Wounds at rank 1 and rank 2");
        logout(player);
        helper.succeed();
    }

    /**
     * The handoff's setup line ({@link #ASTONE_SETUP}) reaches its state: a Human Warrior at level 18 knowing the tree
     * at the build's ranks, holding full netherite armor with Protection V and a netherite sword with Sharpness V.
     */
    @QaGameTest
    public static void theAstoneSetupLineReachesItsState(GameTestHelper helper) {
        ServerPlayer player = QaPlayers.loginOnPlatform(helper, "qa-arms-astone");
        player.connection.markClientLoaded();
        CommandSourceStack source = player.createCommandSourceStack().withPermission(PermissionSet.ALL_PERMISSIONS);
        helper.getLevel().getServer().getCommands().performPrefixedCommand(source, ASTONE_SETUP);
        helper.assertValueEqual(Characters.mainClass(player).map(entry -> entry.key().identifier().getPath()),
                Optional.of("warrior"), "a Warrior");
        helper.assertValueEqual(Characters.level(player), 18, "at level 18");
        Spellbook book = player.getData(SpellAttachments.SPELLBOOK);
        Map<ResourceKey<Spell>, Integer> build = Map.of(GeneratedSpells.SLAM, 5, GeneratedSpells.OVERPOWER, 5,
                GeneratedSpells.DEEP_WOUNDS, 3, GeneratedSpells.REND, 3, GeneratedSpells.EXECUTE, 3,
                GeneratedSpells.BLOODTHIRST, 2, GeneratedSpells.CHARGE, 2, GeneratedSpells.VICTORY_RUSH, 2,
                GeneratedSpells.BATTLE_SHOUT, 1, GeneratedSpells.TWO_HANDED_WEAPON_SPECIALIZATION, 1);
        build.forEach((spell, rank) -> helper.assertValueEqual(book.level(spell), Optional.of(rank),
                spell.identifier() + "'s rank"));
        helper.assertValueEqual(build.values().stream().mapToInt(Integer::intValue).sum(), 10 + 17,
                "the kit's 10 ranks and 17 points");
        Holder<Enchantment> protection = enchantment(helper, Enchantments.PROTECTION);
        for (Item piece : List.of(Items.NETHERITE_HELMET, Items.NETHERITE_CHESTPLATE, Items.NETHERITE_LEGGINGS,
                Items.NETHERITE_BOOTS)) {
            ItemStack stack = held(player, piece);
            helper.assertTrue(!stack.isEmpty(), "holds " + piece);
            helper.assertValueEqual(stack.getTagEnchantments().getLevel(protection), 5, piece + "'s Protection");
        }
        ItemStack sword = held(player, Items.NETHERITE_SWORD);
        helper.assertValueEqual(sword.getTagEnchantments().getLevel(enchantment(helper, Enchantments.SHARPNESS)), 5,
                "the netherite sword's Sharpness");
        helper.assertTrue(player.getOffhandItem().is(Items.SHIELD) || !held(player, Items.SHIELD).isEmpty(),
                "the kit's shield");
        logout(player);
        helper.succeed();
    }

    private static Holder<Enchantment> enchantment(GameTestHelper helper, ResourceKey<Enchantment> key) {
        return helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(key);
    }

    private static ItemStack held(ServerPlayer player, Item item) {
        for (ItemStack stack : player.getInventory()) {
            if (stack.is(item)) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    /** The first damage effect of {@code effects}, a melee shape's included. */
    private static Optional<DamageEffect> firstDamage(List<SpellEffect> effects) {
        for (SpellEffect effect : effects) {
            Optional<DamageEffect> found = switch (effect) {
                case DamageEffect damage -> Optional.of(damage);
                case MeleeEffect melee -> firstDamage(melee.effects());
                default -> Optional.empty();
            };
            if (found.isPresent()) {
                return found;
            }
        }
        return Optional.empty();
    }

    private static Map<String, String> values(GameTestHelper helper, ServerPlayer player, Spell spell, int level) {
        return SpellText.values(spell, level, SpellText.statsOf(player),
                SpellText.schoolsOf(helper.getLevel().registryAccess()));
    }

    private static List<String> tooltip(GameTestHelper helper, ServerPlayer player, Spell spell, int level) {
        return SpellTooltip.lines(spell, level, SpellText.statsOf(player),
                SpellText.schoolsOf(helper.getLevel().registryAccess())).stream().map(Component::getString).toList();
    }

    /** Battle Stance's Armor Pierce and Armor Shred after a recompute, with the base of both at 0. */
    private static void assertStance(GameTestHelper helper, ServerPlayer player, double pierce, double shred,
            String what) {
        CharacterStats.recompute(player);
        helper.assertTrue(Math.abs(DamagePipeline.stat(player, GeneratedStats.ARMOR_PIERCE) - pierce) <= TOLERANCE
                && Math.abs(DamagePipeline.stat(player, GeneratedStats.ARMOR_SHRED) - shred) <= TOLERANCE,
                what + ": Armor Pierce " + DamagePipeline.stat(player, GeneratedStats.ARMOR_PIERCE) + ", Armor Shred "
                        + DamagePipeline.stat(player, GeneratedStats.ARMOR_SHRED));
    }

    /**
     * A player without a character at 10 Attack Damage with an empty hand, no crit chance, crits that add nothing and
     * no dodge, for damage numbers.
     */
    private static ServerPlayer fighter(GameTestHelper helper, String name, Vec3 where) {
        ServerPlayer player = QaPlayers.loginOnPlatform(helper, name, where);
        player.setGameMode(GameType.SURVIVAL);
        player.connection.markClientLoaded();
        StatsCommands.setBase(player, GeneratedStats.CRIT_CHANCE, 0);
        StatsCommands.setBase(player, GeneratedStats.CRIT_DAMAGE, 0);
        StatsCommands.setBase(player, GeneratedStats.DODGE_CHANCE, 0);
        StatsCommands.setBase(player, GeneratedStats.SPELL_POWER, 0);
        StatsCommands.setBase(player, Attributes.ATTACK_DAMAGE, ATTACK_DAMAGE);
        return player;
    }

    private static double attackDamage(LivingEntity caster) {
        return DamagePipeline.stat(caster, Attributes.ATTACK_DAMAGE);
    }

    private static Holder.Reference<Spell> spell(GameTestHelper helper, ResourceKey<Spell> key) {
        return Spells.get(helper.getLevel().registryAccess(), key).orElseThrow();
    }

    private static void learn(GameTestHelper helper, ServerPlayer player, ResourceKey<Spell> key, int level) {
        Spells.learn(player, spell(helper, key), level);
    }

    private static void assertCast(GameTestHelper helper, LivingEntity caster, ResourceKey<Spell> spell,
            LivingEntity target) {
        CastResult result = SpellCaster.cast(caster, spell, target);
        helper.assertTrue(result.success(), spell.identifier() + " was refused: " + result.failure());
    }

    private static void assertRefused(GameTestHelper helper, LivingEntity caster, ResourceKey<Spell> spell,
            LivingEntity target, CastFailure expected) {
        helper.assertValueEqual(SpellCaster.cast(caster, spell, target).failureOrNull(), expected,
                spell.identifier() + " refused");
    }

    private static void logout(ServerPlayer player) {
        player.level().getServer().getPlayerList().remove(player);
    }

    private static boolean close(double got, double want) {
        return Math.abs(got - want) <= TOLERANCE;
    }
}
