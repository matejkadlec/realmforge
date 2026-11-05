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
 * The specialization spell trees (C5, RLM-333): picking a specialization gives its first spell free, every other rank
 * costs a point, a spent point changes the spell's cast, the rules refuse what they should through the same calls the
 * window's payloads make, the points come back when the level falls, and the window's view shows each state. The point
 * rules themselves are in {@code SpellPointsTest}.
 */
public final class SpellTreeGameTests {
    private static final double HEALTH = 1000;
    /** The playtest's setup lines: a fresh character without a specialization, past chapter 1's cap. */
    static final String MAGE_SETUP = "qa run qa character reset; qa character create realmforge:human realmforge:mage; "
            + "advancement grant @s only realmforge:chapter/chapter_1; qa level 18";
    static final String WARRIOR_SETUP = "qa run qa character reset; "
            + "qa character create realmforge:human realmforge:warrior; "
            + "advancement grant @s only realmforge:chapter/chapter_1; qa level 12";

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

    /** A point on a passive spell gives its bonus: Improved Frostbolt raises Frostbolt's damage from its first rank. */
    @QaGameTest
    public static void aPointOnAPassiveAppliesIt(GameTestHelper helper) {
        ServerPlayer player = ClassKitGameTests.unspecialized(helper, "qa-tree-passive", "mage");
        reachLevel(helper, player, 2);
        SpellTrees.pick(player, "frost");
        helper.assertValueEqual(Passives.damageBonus(player, GeneratedSpells.FROSTBOLT), 0.0, "no bonus yet");
        helper.assertValueEqual(SpellTrees.spend(player, GeneratedSpells.IMPROVED_FROSTBOLT), SpellPoints.Result.SPENT,
                "the point");
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
        helper.assertValueEqual(SpellTrees.spend(player, GeneratedSpells.REND), SpellPoints.Result.LEVEL_TOO_LOW,
                "row 2 opens at 6");
        helper.assertValueEqual(SpellTrees.unspent(player), 1, "no refusal spent the point");
        helper.assertValueEqual(SpellTrees.spend(player, GeneratedSpells.CHARGE), SpellPoints.Result.SPENT, "Charge");
        helper.assertValueEqual(SpellTrees.spend(player, GeneratedSpells.CHARGE), SpellPoints.Result.NO_POINTS,
                "no point left");

        raiseCap(helper, player);
        reachLevel(helper, player, 12);
        helper.assertValueEqual(SpellTrees.spend(player, GeneratedSpells.BLOODTHIRST), SpellPoints.Result.NEEDS_SPELL,
                "Bloodthirst's arrow needs Rend");
        helper.assertValueEqual(SpellTrees.spend(player, GeneratedSpells.REND), SpellPoints.Result.SPENT, "Rend");
        helper.assertValueEqual(SpellTrees.spend(player, GeneratedSpells.BLOODTHIRST), SpellPoints.Result.SPENT,
                "any rank of Rend opens it");
        helper.assertValueEqual(SpellTrees.spend(player, GeneratedSpells.CHARGE), SpellPoints.Result.SPENT,
                "Charge's rank 2");
        helper.assertValueEqual(SpellTrees.spend(player, GeneratedSpells.CHARGE), SpellPoints.Result.MAX_RANK,
                "Charge has 2 ranks");
        logout(player);
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
        SpellTrees.spend(player, GeneratedSpells.FROSTBOLT);
        SpellTrees.spend(player, GeneratedSpells.ICE_LANCE);
        SpellTrees.spend(player, GeneratedSpells.IMPROVED_FROSTBOLT);
        helper.assertValueEqual(SpellTrees.spent(player), 3, "three points spent");
        reachLevel(helper, player, 3);
        Spellbook book = player.getData(SpellAttachments.SPELLBOOK);
        helper.assertValueEqual(SpellTrees.spent(player), 0, "every point came back");
        helper.assertValueEqual(SpellTrees.unspent(player), 2, "as level 3's two points");
        helper.assertValueEqual(book.level(GeneratedSpells.FROSTBOLT), Optional.of(1), "Frostbolt back at rank 1");
        helper.assertValueEqual(book.level(GeneratedSpells.ICE_LANCE), Optional.empty(), "Ice Lance forgotten");
        helper.assertValueEqual(book.barSlotOf(GeneratedSpells.ICE_LANCE), -1, "and off the bar");
        helper.assertValueEqual(Characters.data(player).specialization(), Optional.of("frost"), "Frost stays");
        logout(player);
        helper.succeed();
    }

    /** A level that keeps paying for the points but no longer opens a spell's row returns them too. */
    @QaGameTest
    public static void aRowClosedByAFallenLevelReturnsThePoints(GameTestHelper helper) {
        ServerPlayer player = ClassKitGameTests.unspecialized(helper, "qa-tree-row-fall", "mage");
        reachLevel(helper, player, 6);
        SpellTrees.pick(player, "frost");
        SpellTrees.spend(player, GeneratedSpells.ICE_LANCE);
        reachLevel(helper, player, 5);
        helper.assertValueEqual(Spells.knownLevel(player, GeneratedSpells.ICE_LANCE), Optional.empty(),
                "Ice Lance's row is closed again");
        helper.assertValueEqual(SpellTrees.unspent(player), 4, "and its point came back");
        logout(player);
        helper.succeed();
    }

    /** {@code reset} (the Scroll of Respec's effect to come) returns every point and keeps the first spell. */
    @QaGameTest
    public static void aResetReturnsEveryPoint(GameTestHelper helper) {
        ServerPlayer player = ClassKitGameTests.unspecialized(helper, "qa-tree-reset", "warrior");
        reachLevel(helper, player, 3);
        SpellTrees.pick(player, "arms");
        SpellTrees.spend(player, GeneratedSpells.SLAM);
        SpellTrees.spend(player, GeneratedSpells.CHARGE);
        helper.assertValueEqual(SpellTrees.reset(player), 2, "two points came back");
        Spellbook book = player.getData(SpellAttachments.SPELLBOOK);
        helper.assertValueEqual(book.level(GeneratedSpells.SLAM), Optional.of(1), "Slam at its free rank");
        helper.assertValueEqual(book.level(GeneratedSpells.CHARGE), Optional.empty(), "Charge forgotten");
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
        helper.assertValueEqual(cell(helper, after, GeneratedSpells.REND).state(), State.LEARNABLE, "row 2 is open");
        helper.assertValueEqual(cell(helper, after, GeneratedSpells.OVERPOWER).state(), State.NEEDS_LEVEL,
                "row 3 is not");

        raiseCap(helper, player);
        reachLevel(helper, player, 12);
        helper.assertValueEqual(cell(helper, SpellTreeView.of(player), GeneratedSpells.BLOODTHIRST).state(),
                State.NEEDS_SPELL, "the arrow");
        logout(player);
        helper.succeed();
    }

    /**
     * The two M1 trees stand in the rows and columns of Matěj's decision of 2026-10-30, with Bloodthirst's arrow, and
     * Battle Stance in the Arms tree's free place at level 18 (RLM-596) until RLM-631's layout.
     */
    @QaGameTest
    public static void theTreesStandAsDecided(GameTestHelper helper) {
        ServerPlayer warrior = ClassKitGameTests.unspecialized(helper, "qa-tree-arms", "warrior");
        assertTree(helper, warrior, List.of(
                List.of(GeneratedSpells.CHARGE, GeneratedSpells.SLAM, GeneratedSpells.DEEP_WOUNDS),
                List.of(GeneratedSpells.REND, GeneratedSpells.BATTLE_SHOUT,
                        GeneratedSpells.TWO_HANDED_WEAPON_SPECIALIZATION),
                List.of(GeneratedSpells.BLOODTHIRST, GeneratedSpells.OVERPOWER, GeneratedSpells.VICTORY_RUSH),
                List.of(GeneratedSpells.EXECUTE, GeneratedSpells.BATTLE_STANCE)));
        helper.assertTrue(cell(helper, SpellTreeView.of(warrior), GeneratedSpells.BLOODTHIRST).requires()
                .filter(required -> required.is(GeneratedSpells.REND)).isPresent(), "Bloodthirst needs Rend");
        ServerPlayer mage = ClassKitGameTests.unspecialized(helper, "qa-tree-frost", "mage");
        assertTree(helper, mage, List.of(
                List.of(GeneratedSpells.IMPROVED_FROSTBOLT, GeneratedSpells.FROSTBOLT, GeneratedSpells.CONE_OF_COLD),
                List.of(GeneratedSpells.ICE_LANCE, GeneratedSpells.FROST_NOVA, GeneratedSpells.FROSTBITE),
                List.of(GeneratedSpells.SHATTER, GeneratedSpells.ICE_BARRIER),
                List.of(GeneratedSpells.BLIZZARD)));
        helper.assertValueEqual(SpellTreeView.of(mage).trees().getFirst().rowLevels(), List.of(1, 6, 12, 18),
                "a row every 6 levels");
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
        helper.assertValueEqual(Characters.level(player), 12, "a level 12 Warrior");
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

    private static void assertTree(GameTestHelper helper, ServerPlayer player, List<List<ResourceKey<Spell>>> rows) {
        Tree tree = SpellTreeView.of(player).trees().stream().filter(entry -> !entry.cells().isEmpty()).findFirst()
                .orElseThrow();
        List<List<ResourceKey<Spell>>> got = new ArrayList<>();
        for (int row = 0; row < tree.rowLevels().size(); row++) {
            int current = row;
            got.add(tree.cells().stream().filter(cell -> cell.row() == current)
                    .sorted(java.util.Comparator.comparingInt(Cell::column))
                    .map(cell -> cell.spell().unwrapKey().orElseThrow()).toList());
        }
        helper.assertValueEqual(got, rows, tree.specialization().id() + "'s rows");
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
