package io.github.matejkadlec.realmforge.qa.gametest;

import io.github.matejkadlec.realmforge.character.CharacterSheetView;
import io.github.matejkadlec.realmforge.character.Characters;
import io.github.matejkadlec.realmforge.character.SpellPoints;
import io.github.matejkadlec.realmforge.character.SpellTreeView;
import io.github.matejkadlec.realmforge.character.SpellTreeView.Cell;
import io.github.matejkadlec.realmforge.character.SpellTreeView.State;
import io.github.matejkadlec.realmforge.character.SpellTreeView.Tree;
import io.github.matejkadlec.realmforge.character.SpellTrees;
import io.github.matejkadlec.realmforge.character.TreeSpell;
import io.github.matejkadlec.realmforge.generated.GeneratedSpells;
import io.github.matejkadlec.realmforge.generated.GeneratedStats;
import io.github.matejkadlec.realmforge.mob.MobLevels;
import io.github.matejkadlec.realmforge.qa.command.StatsCommands;
import io.github.matejkadlec.realmforge.spell.CastResult;
import io.github.matejkadlec.realmforge.spell.Passives;
import io.github.matejkadlec.realmforge.spell.Resources;
import io.github.matejkadlec.realmforge.spell.Spell;
import io.github.matejkadlec.realmforge.spell.SpellAim;
import io.github.matejkadlec.realmforge.spell.SpellAttachments;
import io.github.matejkadlec.realmforge.spell.SpellCaster;
import io.github.matejkadlec.realmforge.spell.Spellbook;
import io.github.matejkadlec.realmforge.spell.Spells;
import io.github.matejkadlec.realmforge.stats.EntityLevel;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.phys.Vec3;

/**
 * The specialization spell trees (C5, RLM-333, RLM-631): picking a specialization gives its first spell free, every
 * other rank costs a point, a row opens by the points spent in the tree, a spell's arrows each need their spell, a spent
 * point changes the spell's cast, the rules refuse what they should through the same calls the window's payloads make,
 * the points come back when the level falls or the tree no longer allows them, and the window's view shows each state.
 * The point rules themselves are in {@code SpellPointsTest}.
 */
public final class SpellTreeGameTests {
    private static final double HEALTH = 1000;
    /** The playtest's setup lines: a fresh character without a specialization, past chapter 1's cap. */
    static final String MAGE_SETUP = "qa run qa character reset; qa character create realmforge:human realmforge:mage; "
            + "advancement grant @s only realmforge:chapter/chapter_1; qa level 18";
    /** Level 14's 13 points reach row 3 and Bloodthirst through both its arrows (RLM-631). */
    static final String WARRIOR_SETUP = "qa run qa character reset; "
            + "qa character create realmforge:human realmforge:warrior; "
            + "advancement grant @s only realmforge:chapter/chapter_1; qa level 14";

    private SpellTreeGameTests() {
    }

    /** A new character knows no class spell; the pick teaches the first one free, onto the bar, and spends no point. */
    @QaGameTest
    public static void pickingGivesTheFirstSpellFree(GameTestHelper helper) {
        ServerPlayer player = ClassKitGameTests.unspecialized(helper, "qa-tree-pick", "mage");
        helper.assertValueEqual(player.getData(SpellAttachments.SPELLBOOK).level(GeneratedSpells.FROSTBOLT),
                Optional.empty(), "no Frostbolt before the pick");
        helper.assertValueEqual(SpellTrees.pick(player, "frost"), SpellTrees.PickResult.PICKED, "the pick");
        Spellbook book = player.getData(SpellAttachments.SPELLBOOK);
        helper.assertValueEqual(book.level(GeneratedSpells.FROSTBOLT), Optional.of(1), "Frostbolt at rank 1");
        helper.assertValueEqual(book.slot(0), Optional.of(GeneratedSpells.FROSTBOLT), "on the bar's first slot");
        helper.assertValueEqual(book.level(GeneratedSpells.CONE_OF_COLD), Optional.empty(), "and nothing else");
        helper.assertValueEqual(SpellTrees.spent(player), 0, "the first rank is free");
        helper.assertValueEqual(SpellTrees.unspent(player), 0, "level 1 earns no point");
        logout(player);
        helper.succeed();
    }

    /** A character picks once, only a specialization of their class, and only one whose tree exists. */
    @QaGameTest
    public static void theRulesRefuseAWrongPick(GameTestHelper helper) {
        ServerPlayer player = ClassKitGameTests.unspecialized(helper, "qa-tree-pick-refused", "mage");
        helper.assertValueEqual(SpellTrees.pick(player, "arms"), SpellTrees.PickResult.UNKNOWN, "the Warrior's");
        helper.assertValueEqual(SpellTrees.pick(player, "fire"), SpellTrees.PickResult.NOT_PLAYABLE, "M3's Fire");
        helper.assertValueEqual(Characters.data(player).specialization(), Optional.empty(), "neither was picked");
        helper.assertValueEqual(player.getData(SpellAttachments.SPELLBOOK).level(GeneratedSpells.FIREBALL),
                Optional.empty(), "and neither taught anything");
        helper.assertValueEqual(SpellTrees.pick(player, "frost"), SpellTrees.PickResult.PICKED, "Frost");
        helper.assertValueEqual(SpellTrees.pick(player, "frost"), SpellTrees.PickResult.ALREADY_PICKED, "only once");
        logout(player);
        helper.succeed();
    }

    /**
     * RLM-333's "Done when": a spent point changes the spell's cast. Cone of Cold is instant, so one tick shows it: at
     * rank 1 a husk in front takes less damage and a shorter Slowness than another takes at rank 2, cast the same way.
     */
    @QaGameTest(template = QaGameTests.PLATFORM_17X17)
    public static void aSpentPointChangesTheSpellsCast(GameTestHelper helper) {
        ServerPlayer player = caster(helper, "qa-tree-cast");
        reachLevel(helper, player, 3);
        helper.assertValueEqual(SpellTrees.pick(player, "frost"), SpellTrees.PickResult.PICKED, "the pick");
        helper.assertValueEqual(SpellTrees.spend(player, GeneratedSpells.CONE_OF_COLD), SpellPoints.Result.SPENT,
                "rank 1 takes a point");
        helper.assertValueEqual(SpellTrees.unspent(player), 1, "one of level 3's two points left");

        Husk first = husk(helper, new Vec3(8.5, 1, 10));
        cast(helper, player, GeneratedSpells.CONE_OF_COLD);
        double rankOne = HEALTH - first.getHealth();
        MobEffectInstance slowOne = first.getEffect(MobEffects.SLOWNESS);
        first.discard();

        helper.assertValueEqual(SpellTrees.spend(player, GeneratedSpells.CONE_OF_COLD), SpellPoints.Result.SPENT,
                "rank 2 takes the other");
        helper.assertValueEqual(Spells.knownLevel(player, GeneratedSpells.CONE_OF_COLD), Optional.of(2), "rank 2");
        Spells.resetCooldowns(player);
        Resources.tick(player);
        Husk second = husk(helper, new Vec3(8.5, 1, 10));
        cast(helper, player, GeneratedSpells.CONE_OF_COLD);
        double rankTwo = HEALTH - second.getHealth();
        MobEffectInstance slowTwo = second.getEffect(MobEffects.SLOWNESS);

        helper.assertTrue(rankOne > 0, "rank 1 hit the husk");
        helper.assertTrue(rankTwo > rankOne, "rank 2 deals " + rankTwo + ", more than rank 1's " + rankOne);
        helper.assertTrue(slowOne != null && slowTwo != null && slowTwo.getDuration() > slowOne.getDuration(),
                "rank 2's Slowness lasts longer: " + slowOne + ", " + slowTwo);
        logout(player);
        helper.succeed();
    }

    /**
     * A point on a passive spell gives its bonus: Improved Frostbolt, in row 4, raises Frostbolt's damage from its first
     * rank, once 15 points open its row.
     */
    @QaGameTest
    public static void aPointOnAPassiveAppliesIt(GameTestHelper helper) {
        ServerPlayer player = ClassKitGameTests.unspecialized(helper, "qa-tree-passive", "mage");
        raiseCap(helper, player);
        reachLevel(helper, player, 17);
        SpellTrees.pick(player, "frost");
        spend(helper, player, GeneratedSpells.FROSTBOLT, 4);
        spend(helper, player, GeneratedSpells.CONE_OF_COLD, 3);
        spend(helper, player, GeneratedSpells.FROST_NOVA, 3);
        spend(helper, player, GeneratedSpells.ICE_BARRIER, 3);
        spend(helper, player, GeneratedSpells.ICE_LANCE, 2);
        helper.assertValueEqual(Passives.damageBonus(player, GeneratedSpells.FROSTBOLT), 0.0, "no bonus yet");
        helper.assertValueEqual(SpellTrees.spend(player, GeneratedSpells.IMPROVED_FROSTBOLT), SpellPoints.Result.SPENT,
                "15 points open row 4");
        helper.assertTrue(Passives.damageBonus(player, GeneratedSpells.FROSTBOLT) > 0, "the bonus applies");
        helper.assertValueEqual(player.getData(SpellAttachments.SPELLBOOK)
                .barSlotOf(GeneratedSpells.IMPROVED_FROSTBOLT), -1, "a passive never goes onto the bar");
        logout(player);
        helper.succeed();
    }

    /** Every refusal of a spend, each leaving the ranks and the points as they were. */
    @QaGameTest
    public static void theRulesRefuseAWrongPoint(GameTestHelper helper) {
        ServerPlayer player = ClassKitGameTests.unspecialized(helper, "qa-tree-refused", "warrior");
        reachLevel(helper, player, 2);
        helper.assertValueEqual(SpellTrees.spend(player, GeneratedSpells.SLAM), SpellPoints.Result.NO_SPECIALIZATION,
                "before the pick");
        SpellTrees.pick(player, "arms");
        helper.assertValueEqual(SpellTrees.spend(player, GeneratedSpells.FROSTBOLT), SpellPoints.Result.NOT_IN_TREE,
                "another class's spell");
        helper.assertValueEqual(SpellTrees.spend(player, GeneratedSpells.RECKLESS_STRIKE),
                SpellPoints.Result.NOT_IN_TREE, "another specialization's spell");
        helper.assertValueEqual(SpellTrees.spend(player, GeneratedSpells.REND), SpellPoints.Result.ROW_CLOSED,
                "row 2 opens after 5 points");
        helper.assertValueEqual(SpellTrees.unspent(player), 1, "no refusal spent the point");
        helper.assertValueEqual(SpellTrees.spend(player, GeneratedSpells.VICTORY_RUSH), SpellPoints.Result.SPENT,
                "Victory Rush");
        helper.assertValueEqual(SpellTrees.spend(player, GeneratedSpells.VICTORY_RUSH), SpellPoints.Result.NO_POINTS,
                "no point left");

        reachLevel(helper, player, 10);
        spend(helper, player, GeneratedSpells.VICTORY_RUSH, 2);
        helper.assertValueEqual(SpellTrees.spend(player, GeneratedSpells.VICTORY_RUSH), SpellPoints.Result.MAX_RANK,
                "Victory Rush has 3 ranks");
        logout(player);
        helper.succeed();
    }

    /**
     * RLM-631's "Done when": a spell is refused until its row's points are spent in the tree, then learned. Slam's free
     * rank counts as none, so Slam at its highest rank is 4 points, one short of row 2; a point on Victory Rush opens
     * it, and the row opens nothing below it.
     */
    @QaGameTest
    public static void aRowOpensByThePointsSpentInTheTree(GameTestHelper helper) {
        ServerPlayer player = ClassKitGameTests.unspecialized(helper, "qa-tree-row-points", "warrior");
        reachLevel(helper, player, 8);
        SpellTrees.pick(player, "arms");
        helper.assertValueEqual(SpellTrees.spend(player, GeneratedSpells.REND), SpellPoints.Result.ROW_CLOSED,
                "row 2 with no point spent");
        spend(helper, player, GeneratedSpells.SLAM, 4);
        helper.assertValueEqual(SpellTrees.spent(player), 4, "Slam 5 is 4 points");
        helper.assertValueEqual(SpellTrees.spend(player, GeneratedSpells.REND), SpellPoints.Result.ROW_CLOSED,
                "one point short of row 2");
        spend(helper, player, GeneratedSpells.VICTORY_RUSH, 1);
        helper.assertValueEqual(SpellTrees.spend(player, GeneratedSpells.REND), SpellPoints.Result.SPENT,
                "5 points open row 2");
        helper.assertValueEqual(SpellTrees.spend(player, GeneratedSpells.OVERPOWER), SpellPoints.Result.ROW_CLOSED,
                "row 3 waits for 10");
        helper.assertValueEqual(Spells.knownLevel(player, GeneratedSpells.REND), Optional.of(1), "Rend learned");
        logout(player);
        helper.succeed();
    }

    /**
     * RLM-631's "Done when": Bloodthirst is refused until both Rend and Overpower have a point, whichever comes first,
     * and Shatter until Frost Nova, beside it in row 2, has one.
     */
    @QaGameTest
    public static void severalArrowsEachNeedTheirSpell(GameTestHelper helper) {
        ServerPlayer warrior = ClassKitGameTests.unspecialized(helper, "qa-tree-arrows", "warrior");
        raiseCap(helper, warrior);
        reachLevel(helper, warrior, 14);
        SpellTrees.pick(warrior, "arms");
        spend(helper, warrior, GeneratedSpells.SLAM, 4);
        spend(helper, warrior, GeneratedSpells.VICTORY_RUSH, 1);
        spend(helper, warrior, GeneratedSpells.CHARGE, 2);
        spend(helper, warrior, GeneratedSpells.DEEP_WOUNDS, 3);
        helper.assertValueEqual(SpellTrees.spend(warrior, GeneratedSpells.BLOODTHIRST), SpellPoints.Result.NEEDS_SPELL,
                "neither Overpower nor Rend");
        helper.assertValueEqual(cell(helper, SpellTreeView.of(warrior), GeneratedSpells.BLOODTHIRST).missing().stream()
                .map(spell -> spell.unwrapKey().orElseThrow()).toList(), List.of(GeneratedSpells.OVERPOWER,
                GeneratedSpells.REND), "the tooltip names both");
        spend(helper, warrior, GeneratedSpells.OVERPOWER, 1);
        helper.assertValueEqual(SpellTrees.spend(warrior, GeneratedSpells.BLOODTHIRST), SpellPoints.Result.NEEDS_SPELL,
                "Overpower alone");
        spend(helper, warrior, GeneratedSpells.REND, 1);
        helper.assertValueEqual(SpellTrees.spend(warrior, GeneratedSpells.BLOODTHIRST), SpellPoints.Result.SPENT,
                "a point in each opens it");

        ServerPlayer mage = ClassKitGameTests.unspecialized(helper, "qa-tree-arrow-beside", "mage");
        reachLevel(helper, mage, 8);
        SpellTrees.pick(mage, "frost");
        spend(helper, mage, GeneratedSpells.FROSTBOLT, 2);
        spend(helper, mage, GeneratedSpells.CONE_OF_COLD, 3);
        helper.assertValueEqual(SpellTrees.spend(mage, GeneratedSpells.SHATTER), SpellPoints.Result.NEEDS_SPELL,
                "Shatter needs Frost Nova");
        spend(helper, mage, GeneratedSpells.FROST_NOVA, 1);
        helper.assertValueEqual(SpellTrees.spend(mage, GeneratedSpells.SHATTER), SpellPoints.Result.SPENT,
                "the arrow beside it");
        logout(warrior);
        logout(mage);
        helper.succeed();
    }

    /** Level 18 holds 17 points and so 18 ranks in all: the 18th point is refused, whatever the tree still offers. */
    @QaGameTest
    public static void level18HoldsEighteenRanks(GameTestHelper helper) {
        ServerPlayer player = ClassKitGameTests.unspecialized(helper, "qa-tree-budget", "mage");
        raiseCap(helper, player);
        reachLevel(helper, player, 18);
        SpellTrees.pick(player, "frost");
        helper.assertValueEqual(SpellTrees.unspent(player), 17, "17 points at level 18");
        int spent = 0;
        for (TreeSpell taught : SpellTrees.chosen(player).orElseThrow().spells()) {
            while (SpellTrees.spend(player, taught.key()) == SpellPoints.Result.SPENT) {
                spent++;
            }
        }
        helper.assertValueEqual(spent, 17, "every point went somewhere");
        helper.assertValueEqual(SpellTrees.unspent(player), 0, "none left");
        helper.assertValueEqual(SpellTrees.ranks(player), 18, "17 ranks and the free one");
        logout(player);
        helper.succeed();
    }

    /** A level that falls below the points spent returns them all; the first spell stays, at its free rank. */
    @QaGameTest
    public static void aFallenLevelReturnsThePoints(GameTestHelper helper) {
        ServerPlayer player = ClassKitGameTests.unspecialized(helper, "qa-tree-fall", "mage");
        reachLevel(helper, player, 6);
        SpellTrees.pick(player, "frost");
        spend(helper, player, GeneratedSpells.FROSTBOLT, 1);
        spend(helper, player, GeneratedSpells.CONE_OF_COLD, 2);
        helper.assertValueEqual(SpellTrees.spent(player), 3, "three points spent");
        reachLevel(helper, player, 3);
        Spellbook book = player.getData(SpellAttachments.SPELLBOOK);
        helper.assertValueEqual(SpellTrees.spent(player), 0, "every point came back");
        helper.assertValueEqual(SpellTrees.unspent(player), 2, "as level 3's two points");
        helper.assertValueEqual(book.level(GeneratedSpells.FROSTBOLT), Optional.of(1), "Frostbolt back at rank 1");
        helper.assertValueEqual(book.level(GeneratedSpells.CONE_OF_COLD), Optional.empty(), "Cone of Cold forgotten");
        helper.assertValueEqual(book.barSlotOf(GeneratedSpells.CONE_OF_COLD), -1, "and off the bar");
        helper.assertValueEqual(Characters.data(player).specialization(), Optional.of("frost"), "Frost stays");
        logout(player);
        helper.succeed();
    }

    /**
     * Ranks the tree no longer allows return every point, as a changed layout leaves them (RLM-631): Ice Lance known in
     * row 3 with no point above it, or Shatter without Frost Nova. The level still pays for them.
     */
    @QaGameTest
    public static void ranksTheTreeNoLongerAllowsReturnThePoints(GameTestHelper helper) {
        ServerPlayer player = ClassKitGameTests.unspecialized(helper, "qa-tree-changed", "mage");
        reachLevel(helper, player, 7);
        SpellTrees.pick(player, "frost");
        Spells.learn(player, spellHolder(helper, GeneratedSpells.ICE_LANCE), 1);
        reachLevel(helper, player, 8);
        helper.assertValueEqual(Spells.knownLevel(player, GeneratedSpells.ICE_LANCE), Optional.empty(),
                "Ice Lance's row was never open");
        helper.assertValueEqual(SpellTrees.unspent(player), 7, "and its point came back");

        spend(helper, player, GeneratedSpells.FROSTBOLT, 2);
        spend(helper, player, GeneratedSpells.CONE_OF_COLD, 3);
        Spells.learn(player, spellHolder(helper, GeneratedSpells.SHATTER), 1);
        reachLevel(helper, player, 9);
        Spellbook book = player.getData(SpellAttachments.SPELLBOOK);
        helper.assertValueEqual(book.level(GeneratedSpells.SHATTER), Optional.empty(), "Shatter without Frost Nova");
        helper.assertValueEqual(book.level(GeneratedSpells.CONE_OF_COLD), Optional.empty(), "every point came back");
        helper.assertValueEqual(book.level(GeneratedSpells.FROSTBOLT), Optional.of(1), "but Frostbolt's free rank");
        helper.assertValueEqual(SpellTrees.unspent(player), 8, "level 9's eight points");
        logout(player);
        helper.succeed();
    }

    /** {@code reset} (the Scroll of Respec's effect to come) returns every point and keeps the first spell. */
    @QaGameTest
    public static void aResetReturnsEveryPoint(GameTestHelper helper) {
        ServerPlayer player = ClassKitGameTests.unspecialized(helper, "qa-tree-reset", "warrior");
        reachLevel(helper, player, 3);
        SpellTrees.pick(player, "arms");
        spend(helper, player, GeneratedSpells.SLAM, 1);
        spend(helper, player, GeneratedSpells.VICTORY_RUSH, 1);
        helper.assertValueEqual(SpellTrees.reset(player), 2, "two points came back");
        Spellbook book = player.getData(SpellAttachments.SPELLBOOK);
        helper.assertValueEqual(book.level(GeneratedSpells.SLAM), Optional.of(1), "Slam at its free rank");
        helper.assertValueEqual(book.level(GeneratedSpells.VICTORY_RUSH), Optional.empty(), "Victory Rush forgotten");
        helper.assertValueEqual(book.level(GeneratedSpells.WILL_TO_SURVIVE), Optional.of(1), "the racial spell stays");
        helper.assertValueEqual(SpellTrees.unspent(player), 2, "both points unspent");
        helper.assertValueEqual(SpellTrees.reset(player), 0, "nothing more to return");
        logout(player);
        helper.succeed();
    }

    /** The window's view: three trees per class, M3's without spells; the states before and after the pick. */
    @QaGameTest
    public static void theViewShowsEachState(GameTestHelper helper) {
        ServerPlayer player = ClassKitGameTests.unspecialized(helper, "qa-tree-view", "warrior");
        reachLevel(helper, player, 7);
        SpellTreeView before = SpellTreeView.of(player);
        helper.assertValueEqual(before.trees().stream().map(tree -> tree.specialization().id()).toList(),
                List.of("arms", "fury", "protection"), "the class's three specializations");
        helper.assertFalse(before.picked(), "nothing picked yet");
        helper.assertValueEqual(cell(helper, before, GeneratedSpells.SLAM).state(), State.FIRST_FREE, "Slam is free");
        helper.assertValueEqual(cell(helper, before, GeneratedSpells.REND).state(), State.NEEDS_PICK, "Rend waits");
        helper.assertTrue(before.trees().get(1).cells().isEmpty(), "Fury comes later");

        SpellTrees.pick(player, "arms");
        SpellTreeView after = SpellTreeView.of(player);
        helper.assertValueEqual(after.unspent(), 6, "level 7's six points");
        helper.assertTrue(after.trees().getFirst().chosen(), "Arms is chosen");
        Cell slam = cell(helper, after, GeneratedSpells.SLAM);
        helper.assertValueEqual(List.of(slam.rank(), slam.row(), slam.column()), List.of(1, 0, 1),
                "Slam: rank 1, the first row's middle");
        helper.assertValueEqual(slam.state(), State.LEARNABLE, "and it takes a point");
        Cell rend = cell(helper, after, GeneratedSpells.REND);
        helper.assertValueEqual(List.of(rend.state().name(), String.valueOf(rend.rowPoints())),
                List.of(State.ROW_CLOSED.name(), "5"), "row 2 opens after 5 points");

        spend(helper, player, GeneratedSpells.SLAM, 4);
        spend(helper, player, GeneratedSpells.VICTORY_RUSH, 1);
        SpellTreeView spent = SpellTreeView.of(player);
        helper.assertValueEqual(spent.trees().getFirst().spent(), 5, "5 points spent in Arms");
        helper.assertValueEqual(cell(helper, spent, GeneratedSpells.REND).state(), State.LEARNABLE, "row 2 is open");
        helper.assertValueEqual(cell(helper, spent, GeneratedSpells.OVERPOWER).state(), State.ROW_CLOSED,
                "row 3 is not");
        spend(helper, player, GeneratedSpells.REND, 1);
        helper.assertValueEqual(cell(helper, SpellTreeView.of(player), GeneratedSpells.VICTORY_RUSH).state(),
                State.NO_POINTS, "all six points spent");
        logout(player);
        helper.succeed();
    }

    /**
     * The two M1 trees stand in the rows and columns of Matěj's layout of 2026-11-04 (RLM-631), with Bloodthirst's two
     * arrows and Shatter's, Battle Stance at R3, left, and Icy Veins at R4, right (RLM-597); a row opens every 5 points.
     */
    @QaGameTest
    public static void theTreesStandAsDecided(GameTestHelper helper) {
        ServerPlayer warrior = ClassKitGameTests.unspecialized(helper, "qa-tree-arms", "warrior");
        assertTree(helper, warrior, List.of(
                List.of("-", "slam", "victory_rush"),
                List.of("charge", "rend", "deep_wounds"),
                List.of("battle_stance", "overpower", "bloodthirst"),
                List.of("battle_shout", "execute", "two_handed_weapon_specialization")));
        helper.assertValueEqual(cell(helper, SpellTreeView.of(warrior), GeneratedSpells.BLOODTHIRST).requires().stream()
                .map(required -> required.unwrapKey().orElseThrow()).toList(), List.of(GeneratedSpells.OVERPOWER,
                GeneratedSpells.REND), "Bloodthirst needs Overpower and Rend");
        ServerPlayer mage = ClassKitGameTests.unspecialized(helper, "qa-tree-frost", "mage");
        assertTree(helper, mage, List.of(
                List.of("-", "frostbolt", "cone_of_cold"),
                List.of("shatter", "frost_nova", "ice_barrier"),
                List.of("frostbite", "ice_lance", "-"),
                List.of("improved_frostbolt", "blizzard", "icy_veins")));
        helper.assertValueEqual(cell(helper, SpellTreeView.of(mage), GeneratedSpells.SHATTER).requires().stream()
                .map(required -> required.unwrapKey().orElseThrow()).toList(), List.of(GeneratedSpells.FROST_NOVA),
                "Shatter needs Frost Nova");
        helper.assertValueEqual(SpellTreeView.of(mage).trees().getFirst().rowPoints(), List.of(0, 5, 10, 15),
                "a row every 5 points");
        logout(warrior);
        logout(mage);
        helper.succeed();
    }

    /** The handoff's commands reach their states: the setup lines, and {@code /qa spec} through the window's rules. */
    @QaGameTest
    public static void theSetupLinesAndCommandsReachTheirStates(GameTestHelper helper) {
        ServerPlayer player = QaPlayers.loginOnPlatform(helper, "qa-tree-commands");
        player.connection.markClientLoaded();
        CommandSourceStack source = player.createCommandSourceStack().withPermission(PermissionSet.ALL_PERMISSIONS);
        Commands commands = helper.getLevel().getServer().getCommands();
        commands.performPrefixedCommand(source, WARRIOR_SETUP);
        helper.assertValueEqual(Characters.level(player), 14, "a level 14 Warrior");
        helper.assertValueEqual(Characters.data(player).specialization(), Optional.empty(), "who has not picked");
        commands.performPrefixedCommand(source, MAGE_SETUP);
        helper.assertValueEqual(Characters.level(player), 18, "a level 18 Mage");
        helper.assertValueEqual(SpellTrees.ranks(player), 0, "with no rank of a tree");
        commands.performPrefixedCommand(source, "qa spec pick fire");
        helper.assertValueEqual(Characters.data(player).specialization(), Optional.empty(), "Fire is refused");
        commands.performPrefixedCommand(source, "qa spec pick frost");
        commands.performPrefixedCommand(source, "qa spec spend realmforge:cone_of_cold");
        helper.assertValueEqual(Spells.knownLevel(player, GeneratedSpells.CONE_OF_COLD), Optional.of(1), "the point");
        helper.assertValueEqual(SpellTrees.unspent(player), 16, "one of 17 spent");
        commands.performPrefixedCommand(source, "qa spec show");
        commands.performPrefixedCommand(source, "qa spec reset");
        helper.assertValueEqual(Spells.knownLevel(player, GeneratedSpells.CONE_OF_COLD), Optional.empty(), "reset");
        helper.assertValueEqual(SpellTrees.unspent(player), 17, "all 17 back");
        logout(player);
        helper.succeed();
    }

    /** The character sheet's identity line names the specialization from the pick on, "Human Frost Mage". */
    @QaGameTest
    public static void theSheetNamesTheSpecialization(GameTestHelper helper) {
        ServerPlayer player = ClassKitGameTests.unspecialized(helper, "qa-tree-sheet", "mage");
        helper.assertValueEqual(identity(player), List.of("Human", "Mage"), "before the pick");
        SpellTrees.pick(player, "frost");
        helper.assertValueEqual(identity(player), List.of("Human", "Frost", "Mage"), "after it");
        logout(player);
        helper.succeed();
    }

    private static List<String> identity(ServerPlayer player) {
        return CharacterSheetView.of(player).character().identity().parts().stream()
                .map(part -> part.name().getString()).toList();
    }

    /** The tree's rows as the spells' paths by column, "-" for an empty place. */
    private static void assertTree(GameTestHelper helper, ServerPlayer player, List<List<String>> rows) {
        Tree tree = SpellTreeView.of(player).trees().stream().filter(entry -> !entry.cells().isEmpty()).findFirst()
                .orElseThrow();
        List<List<String>> got = new ArrayList<>();
        for (int row = 0; row < tree.rowPoints().size(); row++) {
            List<String> places = new ArrayList<>(List.of("-", "-", "-"));
            for (Cell cell : tree.cells()) {
                if (cell.row() == row) {
                    places.set(cell.column(), cell.spell().unwrapKey().orElseThrow().identifier().getPath());
                }
            }
            got.add(places);
        }
        helper.assertValueEqual(got, rows, tree.specialization().id() + "'s rows");
    }

    /** Spends {@code times} points on {@code spell}, each of which the rules must allow. */
    private static void spend(GameTestHelper helper, ServerPlayer player, ResourceKey<Spell> spell, int times) {
        for (int n = 0; n < times; n++) {
            helper.assertValueEqual(SpellTrees.spend(player, spell), SpellPoints.Result.SPENT,
                    spell.identifier() + "'s point " + (n + 1) + " of " + times);
        }
    }

    private static Holder.Reference<Spell> spellHolder(GameTestHelper helper, ResourceKey<Spell> key) {
        return Spells.get(helper.getLevel().registryAccess(), key).orElseThrow();
    }

    private static Cell cell(GameTestHelper helper, SpellTreeView view, ResourceKey<Spell> spell) {
        return view.trees().stream().flatMap(tree -> tree.cells().stream())
                .filter(entry -> entry.spell().is(spell)).findFirst()
                .orElseThrow(() -> helper.assertionException("No cell for " + spell.identifier()));
    }

    /** A Mage who can cast: survival, no crits, plenty of mana, on the big platform, with no spell yet. */
    private static ServerPlayer caster(GameTestHelper helper, String name) {
        ServerPlayer player = ClassKitGameTests.unspecialized(helper, name, "mage", new Vec3(8.5, 1, 6.5));
        StatsCommands.setBase(player, GeneratedStats.DODGE_CHANCE, 0);
        StatsCommands.setBase(player, GeneratedStats.MAX_MANA, 1000);
        Resources.tick(player);
        return player;
    }

    private static void cast(GameTestHelper helper, LivingEntity caster, ResourceKey<Spell> spell) {
        CastResult result = SpellCaster.cast(caster, spell, SpellAim.at((LivingEntity) null));
        helper.assertTrue(result.success(), spell.identifier() + " was refused: " + result.failure());
    }

    private static Husk husk(GameTestHelper helper, Vec3 where) {
        Husk husk = helper.spawn(EntityType.HUSK, where);
        husk.setNoAi(true);
        MobLevels.setLevel(husk, EntityLevel.MIN);
        CombatGameTests.setBase(husk, Attributes.MAX_HEALTH, HEALTH);
        CombatGameTests.setBase(husk, Attributes.ARMOR, 0);
        CombatGameTests.setBase(husk, Attributes.KNOCKBACK_RESISTANCE, 1);
        husk.setHealth((float) HEALTH);
        return husk;
    }

    private static void raiseCap(GameTestHelper helper, ServerPlayer player) {
        AdvancementHolder chapter = helper.getLevel().getServer().getAdvancements()
                .get(Identifier.parse("realmforge:chapter/chapter_1"));
        helper.assertTrue(player.getAdvancements().award(chapter, "completed"), "chapter 1 raises the cap to 18");
    }

    private static void reachLevel(GameTestHelper helper, ServerPlayer player, int level) {
        long xp = Characters.xpToReach(player, level).orElseThrow();
        helper.assertTrue(Characters.setTotalXp(player, xp), "the XP for level " + level + " was set");
        helper.assertValueEqual(Characters.level(player), level, "the level reached");
    }

    private static void logout(ServerPlayer player) {
        player.level().getServer().getPlayerList().remove(player);
    }
}
