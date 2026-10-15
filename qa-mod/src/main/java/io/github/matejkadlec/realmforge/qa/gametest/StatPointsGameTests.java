package io.github.matejkadlec.realmforge.qa.gametest;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.github.matejkadlec.realmforge.character.CharacterAttachments;
import io.github.matejkadlec.realmforge.character.CharacterBonuses;
import io.github.matejkadlec.realmforge.character.CharacterCreation;
import io.github.matejkadlec.realmforge.character.CharacterCreation.Result;
import io.github.matejkadlec.realmforge.character.CharacterData;
import io.github.matejkadlec.realmforge.character.CharacterRegistries;
import io.github.matejkadlec.realmforge.character.CharacterSheetView;
import io.github.matejkadlec.realmforge.character.CharacterSheetView.Row;
import io.github.matejkadlec.realmforge.character.Characters;
import io.github.matejkadlec.realmforge.character.StatPoints;
import io.github.matejkadlec.realmforge.character.XpTable;
import io.github.matejkadlec.realmforge.core.CoreRegistries;
import io.github.matejkadlec.realmforge.core.Curve;
import io.github.matejkadlec.realmforge.generated.GeneratedCurves;
import io.github.matejkadlec.realmforge.generated.GeneratedItems;
import io.github.matejkadlec.realmforge.generated.GeneratedStats;
import io.github.matejkadlec.realmforge.qa.generated.GeneratedLevelingExpectations;
import io.github.matejkadlec.realmforge.qa.generated.GeneratedLevelingExpectations.LevelXp;
import io.github.matejkadlec.realmforge.qa.generated.GeneratedRaceExpectations;
import io.github.matejkadlec.realmforge.stats.StatBonus;
import io.github.matejkadlec.realmforge.stats.StatRule;
import io.github.matejkadlec.realmforge.stats.StatRules;
import io.github.matejkadlec.realmforge.stats.StatsRegistries;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * RLM-196: stat points (architecture 10.2). A level-up earns the points the curve gives; spending one raises that
 * primary stat and what it gives; the Clean Slate returns every point and is used up; a character from before stat
 * points existed gets the points of its levels once; a level that falls returns the points spent; levels no longer
 * raise race or class stats; the character sheet offers a {@code +} only while points are unspent. Players log in
 * through {@link QaPlayers#login}.
 */
public final class StatPointsGameTests {
    private StatPointsGameTests() {
    }

    /** The game's points per level match the pipeline's, and a level-up adds them, unspent. */
    @QaGameTest
    public static void aLevelUpEarnsTheConfiguredPoints(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        Curve points = curve(helper, GeneratedCurves.STAT_POINTS_PER_LEVEL);
        helper.assertFalse(GeneratedLevelingExpectations.LEVELS.isEmpty(), "no level expectations were generated");
        for (LevelXp expected : GeneratedLevelingExpectations.LEVELS) {
            helper.assertValueEqual(StatPoints.earned(points, expected.level()), expected.statPoints(),
                    "the stat points of level " + expected.level());
        }

        ServerPlayer player = loginWithCharacter(helper, "qa-points-earn");
        helper.assertValueEqual(StatPoints.unspent(player), 0, "a new character at level 1 has no points");
        Characters.rewardXp(player, XpTable.toReach(curve(helper, GeneratedCurves.XP_TO_NEXT_LEVEL), 3));
        helper.assertValueEqual(Characters.level(player), 3, "two level-ups");
        helper.assertValueEqual(StatPoints.unspent(player), expected(3), "the points of levels 2 and 3, unspent");
        server.getPlayerList().remove(player);
        helper.succeed();
    }

    /**
     * Spending a point raises that primary stat by one and gives the stat rule's bonuses for the new value; the server
     * refuses a point without one left, and a point for an attribute that is not a primary stat.
     */
    @QaGameTest
    public static void spendingAPointRaisesTheStatAndWhatItGives(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = loginWithCharacter(helper, "qa-points-spend");
        reachLevel(helper, server, player, 2);
        int unspent = StatPoints.unspent(player);
        helper.assertValueEqual(unspent, expected(2), "the points of level 2");
        double before = player.getAttributeValue(GeneratedStats.STRENGTH);

        helper.assertValueEqual(StatPoints.spend(player, key(GeneratedStats.CRIT_CHANCE)),
                StatPoints.Result.NOT_A_PRIMARY_STAT, "crit chance takes no points");
        helper.assertValueEqual(StatPoints.spend(player, key(GeneratedStats.STRENGTH)), StatPoints.Result.SPENT,
                "a point on Strength");
        helper.assertValueEqual(player.getAttributeValue(GeneratedStats.STRENGTH), before + 1, "Strength rose by one");
        helper.assertValueEqual(StatPoints.unspent(player), unspent - 1, "one point fewer unspent");
        helper.assertValueEqual(Characters.data(player).statPoints(), Map.of(key(GeneratedStats.STRENGTH), 1),
                "the spent point is saved on the character");
        AttributeInstance strength = player.getAttribute(GeneratedStats.STRENGTH);
        AttributeModifier modifier = strength.getModifier(StatPoints.modifierId(key(GeneratedStats.STRENGTH)));
        helper.assertTrue(modifier != null && modifier.amount() == 1, "the point's own modifier: " + modifier);
        assertRuleBonuses(helper, player, GeneratedStats.STRENGTH);

        for (int left = StatPoints.unspent(player); left > 0; left--) {
            StatPoints.spend(player, key(GeneratedStats.VITALITY));
        }
        helper.assertValueEqual(StatPoints.spend(player, key(GeneratedStats.STRENGTH)), StatPoints.Result.NO_POINTS,
                "no point left to spend");
        helper.assertValueEqual(player.getAttributeValue(GeneratedStats.STRENGTH), before + 1, "Strength stays");
        server.getPlayerList().remove(player);
        helper.succeed();
    }

    /**
     * The Clean Slate, used through vanilla's right-click path: every spent point comes back, the stat-point modifiers
     * go, one slate is used up; with nothing spent it fails and is kept.
     */
    @QaGameTest
    public static void theCleanSlateReturnsEveryPoint(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = loginWithCharacter(helper, "qa-points-slate");
        player.setGameMode(GameType.SURVIVAL);
        reachLevel(helper, server, player, 4);
        int earned = StatPoints.unspent(player);
        helper.assertTrue(earned >= 2, "level 4 earns at least two points: " + earned);
        StatPoints.spend(player, key(GeneratedStats.STRENGTH));
        StatPoints.spend(player, key(GeneratedStats.INTELLECT));
        helper.assertValueEqual(StatPoints.unspent(player), earned - 2, "two points spent");

        ItemStack slates = new ItemStack(GeneratedItems.CLEAN_SLATE.get(), 2);
        helper.assertTrue(slates.is(StatPoints.UNLEARN_ITEMS), "the Clean Slate is in " + StatPoints.UNLEARN_ITEMS);
        player.setItemInHand(InteractionHand.MAIN_HAND, slates);
        InteractionResult used = player.gameMode.useItem(player, player.level(), slates, InteractionHand.MAIN_HAND);
        helper.assertTrue(used instanceof InteractionResult.Success, "using the slate succeeds: " + used);
        helper.assertValueEqual(StatPoints.unspent(player), earned, "every point is unspent again");
        helper.assertValueEqual(Characters.data(player).statPoints(), Map.of(), "nothing is spent");
        helper.assertTrue(statPointModifiers(player).isEmpty(), "the spent points' modifiers are gone");
        helper.assertValueEqual(player.getItemInHand(InteractionHand.MAIN_HAND).getCount(), 1, "one slate used up");

        InteractionResult again = player.gameMode.useItem(player, player.level(),
                player.getItemInHand(InteractionHand.MAIN_HAND), InteractionHand.MAIN_HAND);
        helper.assertTrue(again instanceof InteractionResult.Fail, "with nothing spent the slate fails: " + again);
        helper.assertValueEqual(player.getItemInHand(InteractionHand.MAIN_HAND).getCount(), 1, "and is kept");
        server.getPlayerList().remove(player);
        helper.succeed();
    }

    /**
     * A character saved before stat points existed (no {@code stat_points} field) has the points of every level above
     * 1 unspent, once: spending one and logging in again does not give it back.
     */
    @QaGameTest
    public static void aCharacterFromBeforeStatPointsGetsItsPointsOnce(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        GameProfile profile = new GameProfile(UUID.randomUUID(), "qa-points-old");
        ServerPlayer first = login(helper, profile);
        var race = GeneratedRaceExpectations.RACES.getFirst();
        long xp = XpTable.toReach(curve(helper, GeneratedCurves.XP_TO_NEXT_LEVEL), 5);
        CharacterData old = savedShape("{race: \"" + race.id() + "\", classes: [\"" + race.classes().getFirst()
                + "\"], total_xp: " + xp + "L}");
        first.setData(CharacterAttachments.CHARACTER, old);
        server.getPlayerList().remove(first);

        ServerPlayer back = login(helper, profile);
        helper.assertValueEqual(Characters.level(back), 5, "the login brought the level up to the XP");
        helper.assertValueEqual(StatPoints.unspent(back), expected(5), "the points of levels 2 to 5, unspent");
        helper.assertValueEqual(StatPoints.spend(back, key(GeneratedStats.DEXTERITY)), StatPoints.Result.SPENT,
                "a point on Dexterity");
        server.getPlayerList().remove(back);

        ServerPlayer again = login(helper, profile);
        helper.assertValueEqual(StatPoints.unspent(again), expected(5) - 1, "the next login gives no point again");
        helper.assertValueEqual(Characters.data(again).statPoints(), Map.of(key(GeneratedStats.DEXTERITY), 1),
                "the spent point was saved");
        again.doTick();
        AttributeModifier modifier = again.getAttribute(GeneratedStats.DEXTERITY)
                .getModifier(StatPoints.modifierId(key(GeneratedStats.DEXTERITY)));
        helper.assertTrue(modifier != null && modifier.amount() == 1, "and gives its Dexterity: " + modifier);
        server.getPlayerList().remove(again);
        helper.succeed();
    }

    /** A level lowered below what was spent (here by a command) returns every spent point. */
    @QaGameTest
    public static void aFallenLevelReturnsTheSpentPoints(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = loginWithCharacter(helper, "qa-points-fall");
        reachLevel(helper, server, player, 5);
        while (StatPoints.unspent(player) > 0) {
            StatPoints.spend(player, key(GeneratedStats.VITALITY));
        }
        helper.assertValueEqual(Characters.data(player).spentStatPoints(), expected(5), "every point spent");
        run(server, player, "qa level 3");
        helper.assertValueEqual(Characters.level(player), 3, "level 3");
        helper.assertValueEqual(Characters.data(player).statPoints(), Map.of(), "the spent points came back");
        helper.assertValueEqual(StatPoints.unspent(player), expected(3), "unspent: what level 3 earns");
        helper.assertTrue(statPointModifiers(player).isEmpty(), "and their modifiers are gone");
        run(server, player, "qa level 4");
        helper.assertValueEqual(StatPoints.unspent(player), expected(4), "a level gained back earns its point again");
        server.getPlayerList().remove(player);
        helper.succeed();
    }

    /** Levels no longer raise race or class stats: the same modifiers at level 1 and level 10, with nothing spent. */
    @QaGameTest
    public static void levelsNoLongerRaisePrimaryStats(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = loginWithCharacter(helper, "qa-points-flat");
        List<StatBonus> atLevel1 = CharacterBonuses.of(Characters.data(player), player.registryAccess());
        List<Double> primaryAt1 = GeneratedStats.PRIMARY.stream().map(player::getAttributeValue).toList();
        reachLevel(helper, server, player, 10);
        helper.assertValueEqual(CharacterBonuses.of(Characters.data(player), player.registryAccess()), atLevel1,
                "race and class give the same at level 10");
        helper.assertValueEqual(GeneratedStats.PRIMARY.stream().map(player::getAttributeValue).toList(), primaryAt1,
                "the primary stats stay until points are spent");
        server.getPlayerList().remove(player);
        helper.succeed();
    }

    /**
     * The sheet's Stats tab shows the unspent points, and a {@code +} beside each primary stat only while some are
     * unspent; a player without a character sees neither.
     */
    @QaGameTest
    public static void theSheetOffersPointsOnlyWhileSomeAreUnspent(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer newcomer = login(helper, new GameProfile(UUID.randomUUID(), "qa-points-none"));
        CharacterSheetView none = CharacterSheetView.of(newcomer);
        helper.assertTrue(none.rows().stream().noneMatch(row -> row.id().equals(CharacterSheetView.STAT_POINTS)),
                "no stat points row without a character");
        helper.assertTrue(none.rows().stream().allMatch(row -> row.spend() == null), "and no + anywhere");
        server.getPlayerList().remove(newcomer);

        ServerPlayer player = loginWithCharacter(helper, "qa-points-sheet");
        reachLevel(helper, server, player, 2);
        CharacterSheetView view = CharacterSheetView.of(player);
        Row points = row(helper, view, CharacterSheetView.STAT_POINTS);
        helper.assertValueEqual(points.label().getString(), "Unspent Stat Points", "the row's label");
        helper.assertValueEqual(points.value(), String.valueOf(expected(2)), "the unspent points");
        for (Holder<Attribute> stat : GeneratedStats.PRIMARY) {
            helper.assertValueEqual(row(helper, view, stat.value().getDescriptionId()).spend(), key(stat),
                    stat.getRegisteredName() + " offers a +");
        }
        helper.assertTrue(view.rows().stream().filter(row -> row.spend() != null).count()
                == GeneratedStats.PRIMARY.size(), "only the primary stats offer a +");

        while (StatPoints.unspent(player) > 0) {
            StatPoints.spend(player, key(GeneratedStats.STRENGTH));
        }
        CharacterSheetView spent = CharacterSheetView.of(player);
        helper.assertValueEqual(row(helper, spent, CharacterSheetView.STAT_POINTS).value(), "0", "none left");
        helper.assertTrue(spent.rows().stream().allMatch(row -> row.spend() == null), "and no + left");
        server.getPlayerList().remove(player);
        helper.succeed();
    }

    /** A character as a save of that shape reads. */
    private static CharacterData savedShape(String snbt) {
        try {
            return CharacterData.CODEC.parse(NbtOps.INSTANCE, TagParser.parseCompoundFully(snbt)).getOrThrow();
        } catch (CommandSyntaxException e) {
            throw new IllegalArgumentException("Not valid SNBT: " + snbt, e);
        }
    }

    /** The stat rule's modifiers on the player are the ones it gives at the stat's current value. */
    private static void assertRuleBonuses(GameTestHelper helper, ServerPlayer player, Holder<Attribute> stat) {
        Registry<StatRule> rules = player.registryAccess().lookupOrThrow(StatsRegistries.STAT_RULE);
        double value = player.getAttributeValue(stat);
        boolean found = false;
        for (var entry : rules.entrySet()) {
            if (!entry.getValue().stat().is(key(stat))) {
                continue;
            }
            found = true;
            List<StatBonus> wanted = StatRules.bonuses(entry.getKey().identifier(), entry.getValue(), value);
            helper.assertFalse(wanted.isEmpty(), stat.getRegisteredName() + " at " + value + " gives something");
            for (StatBonus bonus : wanted) {
                AttributeModifier got = player.getAttribute(bonus.attribute()).getModifier(bonus.modifier().id());
                helper.assertValueEqual(got, bonus.modifier(), bonus.modifier().id() + " at " + value);
            }
        }
        helper.assertTrue(found, "a stat rule reads " + stat.getRegisteredName());
    }

    private static List<AttributeModifier> statPointModifiers(ServerPlayer player) {
        return GeneratedStats.PRIMARY.stream()
                .map(player::getAttribute)
                .flatMap(instance -> instance.getModifiers().stream())
                .filter(modifier -> StatPoints.isStatPointModifier(modifier.id()))
                .toList();
    }

    /** The points a player of the level has earned, from the pipeline's expectations. */
    private static int expected(int level) {
        return GeneratedLevelingExpectations.LEVELS.stream().filter(entry -> entry.level() == level).findFirst()
                .orElseThrow(() -> new IllegalStateException("no expectation for level " + level)).statPoints();
    }

    /** Sets the XP for the level quietly, as {@code /realmforge xp set} does. */
    private static void reachLevel(GameTestHelper helper, MinecraftServer server, ServerPlayer player, int level) {
        run(server, player, "realmforge xp set @s "
                + XpTable.toReach(curve(helper, GeneratedCurves.XP_TO_NEXT_LEVEL), level));
        helper.assertValueEqual(Characters.level(player), level, "level " + level);
    }

    private static Row row(GameTestHelper helper, CharacterSheetView view, String id) {
        return view.rows().stream().filter(row -> row.id().equals(id)).findFirst()
                .orElseThrow(() -> helper.assertionException(Component.literal("the sheet has no row " + id)));
    }

    private static ResourceKey<Attribute> key(Holder<Attribute> attribute) {
        return attribute.unwrapKey().orElseThrow();
    }

    private static ServerPlayer login(GameTestHelper helper, GameProfile profile) {
        ServerPlayer player = QaPlayers.login(helper.getLevel().getServer(), profile);
        player.snapTo(helper.absoluteVec(new Vec3(2.5, 1, 2.5)), 0, 0);
        return player;
    }

    /** A player who logs in and creates a character of the first race and its first class. */
    private static ServerPlayer loginWithCharacter(GameTestHelper helper, String name) {
        ServerPlayer player = login(helper, new GameProfile(UUID.randomUUID(), name));
        var race = GeneratedRaceExpectations.RACES.getFirst();
        Result result = CharacterCreation.create(player,
                ResourceKey.create(CharacterRegistries.RACE, Identifier.parse(race.id())),
                ResourceKey.create(CharacterRegistries.CLASS, Identifier.parse(race.classes().getFirst())));
        helper.assertValueEqual(result, Result.CREATED, "character creation");
        return player;
    }

    private static Curve curve(GameTestHelper helper, ResourceKey<Curve> key) {
        Curve curve = helper.getLevel().registryAccess().lookupOrThrow(CoreRegistries.CURVE).getValue(key);
        helper.assertTrue(curve != null, "curve is not loaded: " + key.identifier());
        return curve;
    }

    private static void run(MinecraftServer server, ServerPlayer player, String command) {
        CommandSourceStack source = player.createCommandSourceStack().withPermission(PermissionSet.ALL_PERMISSIONS);
        server.getCommands().performPrefixedCommand(source, command);
    }
}
