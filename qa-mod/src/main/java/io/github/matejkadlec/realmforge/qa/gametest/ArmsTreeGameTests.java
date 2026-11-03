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
import io.github.matejkadlec.realmforge.stats.EntityLevel;
import io.github.matejkadlec.realmforge.stats.StatsAttachments;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
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
 * rank, Deep Wounds' bleed of a share of the crit, Bloodthirst's proc, the tooltips' next rank, the Warrior
 * learning the tree by its rows (1, 6, 12, 18), and the handoff's setup line against Astone.
 */
public final class ArmsTreeGameTests {
    private static final double HEALTH = SpellGameTests.HEALTH;
    private static final double TOLERANCE = 1e-3;
    private static final double ATTACK_DAMAGE = 10;
    /** A Bleeding of 6 s ticks every half second: 12 ticks. */
    private static final int BLEED_TICKS = 12;
    /** The protection and durability every netherite piece gets for the Astone check: level V (gear.md). */
    private static final String ARMOR_V = "[enchantments={\"minecraft:protection\":5,\"minecraft:unbreaking\":5}]";
    /**
     * The playtest's setup line against Astone (RLM-543, RLM-503; gear.md's expected gear for Astone, RLM-551): a new
     * Human Warrior, chapter 1's raised cap, level 18, the tree at a 17-point build on top of the kit's rank 1 (Slam and
     * Overpower 5, Deep Wounds, Rend and Execute 3, Bloodthirst, Charge and Victory Rush 2, Battle Shout and
     * Two-Handed Specialization 1), full netherite armor with Protection V and Unbreaking V, and a netherite
     * sword with Sharpness V and Unbreaking V; the kit's shield stays for Overpower's blocks. The handoff gives it to
     * Matěj as {@code execute as @p at @p run} and this line.
     */
    static final String ASTONE_SETUP = "qa run qa character reset; "
            + "qa character create realmforge:human realmforge:warrior; "
            + "advancement grant @s only realmforge:chapter/chapter_1; qa level 18; "
            + "qa spell learn realmforge:slam 5; qa spell learn realmforge:overpower 5; "
            + "qa spell learn realmforge:deep_wounds 3; qa spell learn realmforge:rend 3; "
            + "qa spell learn realmforge:execute 3; qa spell learn realmforge:bloodthirst 2; "
            + "qa spell learn realmforge:charge 2; qa spell learn realmforge:victory_rush 2; "
            + "give @s minecraft:netherite_helmet" + ARMOR_V + "; give @s minecraft:netherite_chestplate" + ARMOR_V
            + "; give @s minecraft:netherite_leggings" + ARMOR_V + "; give @s minecraft:netherite_boots" + ARMOR_V
            + "; give @s minecraft:netherite_sword[enchantments={\"minecraft:sharpness\":5,\"minecraft:unbreaking\":5}]";

    private ArmsTreeGameTests() {
    }

    /**
     * Each active spell's first damage effect at rank 1 and at its highest rank, at 10 Attack Damage, no crit and no
     * school power: the table's amount plus its coefficient of 10 (Slam 5 + 150 % and 11 + 250 %, Rend 2 + 30 % and
     * 4 + 50 %, Overpower 5 + 100 % and 7 + 140 %, Victory Rush 5 + 80 % and 7 + 100 %, Execute 8 + 150 % and
     * 12 + 200 %),
     * on a husk without armor; Rend's bleed 4 + 60 % and 8 + 100 % over its 6 s.
     */
    @QaGameTest
    public static void everyActiveDealsItsRanksDamage(GameTestHelper helper) {
        ServerPlayer player = fighter(helper, "qa-arms-ranks", QaPlayers.PLATFORM_MIDDLE);
        Husk husk = SpellGameTests.husk(helper, new BlockPos(2, 1, 4));
        Map<ResourceKey<Spell>, double[]> table = Map.of(
                GeneratedSpells.SLAM, new double[] {1, 20, 5, 36},
                GeneratedSpells.REND, new double[] {1, 5, 3, 9},
                GeneratedSpells.OVERPOWER, new double[] {1, 15, 5, 21},
                GeneratedSpells.VICTORY_RUSH, new double[] {1, 13, 3, 17},
                GeneratedSpells.EXECUTE, new double[] {1, 23, 3, 32});
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
        for (double[] check : new double[][] {{1, 10}, {3, 18}}) {
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
     * Overpower is refused while its window is closed; the player's raised greatsword blocking a zombie's hit opens it
     * for 5 s, a cast uses it for 5 + 100 % of Attack Damage and 10 rage, and the next cast is refused again.
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

        double expected = 5 + attackDamage(player);
        assertCast(helper, player, GeneratedSpells.OVERPOWER, husk);
        helper.assertTrue(close(HEALTH - husk.getHealth(), expected), "Overpower hit for " + expected + ": "
                + (HEALTH - husk.getHealth()));
        helper.assertTrue(close(Resources.amount(player, ResourceTypes.RAGE), 90), "for 10 rage");
        Spells.resetCooldowns(player);
        assertRefused(helper, player, GeneratedSpells.OVERPOWER, husk, CastFailure.NO_WINDOW);
        logout(player);
        helper.succeed();
    }

    /**
     * Victory Rush is refused without a kill and after a gray one (4 levels below a level 10 player); a kill 3 levels
     * below opens it, and a cast strikes for 5 + 80 % of Attack Damage and heals the player 10 % of their max health.
     */
    @QaGameTest
    public static void victoryRushIsUsableOnlyAfterAKillThatIsNotGrayAndHeals(GameTestHelper helper) {
        ServerPlayer player = fighter(helper, "qa-arms-rush", new Vec3(2.5, 1, 0.5));
        player.setData(StatsAttachments.LEVEL, new EntityLevel(10));
        learn(helper, player, GeneratedSpells.VICTORY_RUSH, 1);
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
        helper.assertValueEqual(values(helper, player, spell(helper, GeneratedSpells.VICTORY_RUSH).value(), 3)
                .get("max_health_heal"), "20", "20 % at rank 3");
        Spells.resetCooldowns(player);
        assertRefused(helper, player, GeneratedSpells.VICTORY_RUSH, husk, CastFailure.NO_WINDOW);
        logout(player);
        helper.succeed();
    }

    /**
     * Execute is refused on a target at 20 % health or more; below it, it costs 15 rage at rank 1 and spends up to 30
     * more, 3 % more damage each (45 rage: × 1.9). A kill refunds 5 rage at rank 1 and 15 at rank 3 (Matěj,
     * 2026-11-03), where the cast costs 25 and 45 rage leaves 20 extra.
     */
    @QaGameTest
    public static void executeIsUsableOnlyBelowAFifthAndRefundsAKill(GameTestHelper helper) {
        ServerPlayer player = fighter(helper, "qa-arms-execute", new Vec3(2.5, 1, 0.5));
        learn(helper, player, GeneratedSpells.EXECUTE, 1);
        player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_SWORD));
        Husk husk = SpellGameTests.husk(helper, new BlockPos(2, 1, 1));
        Resources.set(player, ResourceTypes.RAGE, 45);
        husk.setHealth((float) (HEALTH * 0.2));
        assertRefused(helper, player, GeneratedSpells.EXECUTE, husk, CastFailure.TARGET_HEALTH_TOO_HIGH);

        float low = (float) (HEALTH * 0.15);
        husk.setHealth(low);
        double expected = (8 + 1.5 * attackDamage(player)) * 1.9;
        assertCast(helper, player, GeneratedSpells.EXECUTE, husk);
        helper.assertTrue(close(low - husk.getHealth(), expected), "Execute rank 1 with 30 extra rage: " + expected
                + " expected, dealt " + (low - husk.getHealth()));
        helper.assertTrue(close(Resources.amount(player, ResourceTypes.RAGE), 0), "15 + 30 more rage spent");

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
            helper.assertTrue(close(Resources.amount(player, ResourceTypes.RAGE), rank == 3 ? 15 : 5), "rank " + rank
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
     * Bloodthirst's proc at the player's rank: a tick of Rend's Bleeding opens Overpower's window, at 15 % at rank 1
     * and 30 % at rank 2, at most once every 6 s on each target.
     */
    @QaGameTest
    public static void bloodthirstOpensOverpowerOnRendsTicks(GameTestHelper helper) {
        ServerPlayer player = fighter(helper, "qa-arms-taste", QaPlayers.PLATFORM_MIDDLE);
        for (double[] check : new double[][] {{1, 0.15}, {2, 0.3}}) {
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
                "Wounds an enemy for Physical 5 damage and makes it bleed for Physical 10 more over 6 sec.",
                "Next rank:",
                "Wounds an enemy for Physical 7 damage and makes it bleed for Physical 14 more over 6 sec."),
                "Rend at rank 1 and rank 2; as plain text the school's icon reads as its name");
        List<String> execute = tooltip(helper, player, spell(helper, GeneratedSpells.EXECUTE).value(), 2);
        helper.assertValueEqual(execute.getLast(), "Usable only on an enemy below 20% health. Strikes it for Physical "
                + "32 damage, spending up to 30 more rage for 3% more damage each. A kill refunds 15 rage.",
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
     * The Warrior learns the tree at rank 1 by its rows: Slam, Charge and Deep Wounds at level 1; Rend, Battle Shout and
     * Two-Handed Specialization at 6; Bloodthirst, Overpower and Victory Rush at 12; Execute at 18 (once
     * chapter 1 raised the cap); the bar holds the actives in that order, never the passives, and Reckless Strike and
     * Shield Bash never come.
     */
    @QaGameTest(timeoutTicks = 100)
    public static void theWarriorLearnsTheTreeByItsRows(GameTestHelper helper) {
        ServerPlayer player = ClassKitGameTests.character(helper, "qa-arms-kit", "warrior");
        assertKnows(helper, player, List.of(GeneratedSpells.SLAM, GeneratedSpells.CHARGE, GeneratedSpells.DEEP_WOUNDS),
                List.of(GeneratedSpells.REND), 1);
        reachLevel(helper, player, 6);
        assertKnows(helper, player, List.of(GeneratedSpells.REND, GeneratedSpells.BATTLE_SHOUT,
                GeneratedSpells.TWO_HANDED_WEAPON_SPECIALIZATION), List.of(GeneratedSpells.BLOODTHIRST,
                GeneratedSpells.OVERPOWER), 6);
        AdvancementHolder chapter = helper.getLevel().getServer().getAdvancements()
                .get(Identifier.parse("realmforge:chapter/chapter_1"));
        helper.assertTrue(player.getAdvancements().award(chapter, "completed"), "chapter 1 raises the cap to 18");
        reachLevel(helper, player, 12);
        assertKnows(helper, player, List.of(GeneratedSpells.BLOODTHIRST, GeneratedSpells.OVERPOWER,
                GeneratedSpells.VICTORY_RUSH), List.of(GeneratedSpells.EXECUTE), 12);
        reachLevel(helper, player, 18);
        assertKnows(helper, player, List.of(GeneratedSpells.EXECUTE),
                List.of(GeneratedSpells.RECKLESS_STRIKE, GeneratedSpells.SHIELD_BASH), 18);
        Spellbook book = player.getData(SpellAttachments.SPELLBOOK);
        List<ResourceKey<Spell>> bar = new ArrayList<>();
        for (int slot = 0; slot < 8; slot++) {
            book.slot(slot).ifPresent(bar::add);
        }
        helper.assertValueEqual(bar, List.of(GeneratedSpells.SLAM, GeneratedSpells.CHARGE, GeneratedSpells.REND,
                GeneratedSpells.BATTLE_SHOUT, GeneratedSpells.OVERPOWER, GeneratedSpells.VICTORY_RUSH,
                GeneratedSpells.EXECUTE), "the bar holds the seven actives in the tree's order");
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

    private static void assertKnows(GameTestHelper helper, ServerPlayer player, List<ResourceKey<Spell>> known,
            List<ResourceKey<Spell>> notYet, int level) {
        Spellbook book = player.getData(SpellAttachments.SPELLBOOK);
        for (ResourceKey<Spell> spell : known) {
            helper.assertValueEqual(book.level(spell), Optional.of(1), "level " + level + " knows "
                    + spell.identifier() + " at rank 1");
        }
        for (ResourceKey<Spell> spell : notYet) {
            helper.assertValueEqual(book.level(spell), Optional.empty(), "level " + level + " does not know "
                    + spell.identifier());
        }
    }

    private static void reachLevel(GameTestHelper helper, ServerPlayer player, int level) {
        long xp = Characters.xpToReach(player, level).orElseThrow();
        helper.assertTrue(Characters.setTotalXp(player, xp), "the XP for level " + level + " was set");
        helper.assertValueEqual(Characters.level(player), level, "the level reached");
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
