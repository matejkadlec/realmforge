package io.github.matejkadlec.realmforge.qa.gametest;

import com.mojang.authlib.GameProfile;
import io.github.matejkadlec.realmforge.Realmforge;
import io.github.matejkadlec.realmforge.character.CharacterAttachments;
import io.github.matejkadlec.realmforge.character.CharacterBonuses;
import io.github.matejkadlec.realmforge.character.CharacterCreation;
import io.github.matejkadlec.realmforge.character.CharacterCreation.Result;
import io.github.matejkadlec.realmforge.character.CharacterCreationView;
import io.github.matejkadlec.realmforge.character.CharacterCreationView.ClassOption;
import io.github.matejkadlec.realmforge.character.CharacterCreationView.RaceOption;
import io.github.matejkadlec.realmforge.character.CharacterData;
import io.github.matejkadlec.realmforge.character.CharacterRegistries;
import io.github.matejkadlec.realmforge.character.CharacterSheetView;
import io.github.matejkadlec.realmforge.character.Characters;
import io.github.matejkadlec.realmforge.character.PlayerClass;
import io.github.matejkadlec.realmforge.character.Race;
import io.github.matejkadlec.realmforge.qa.generated.GeneratedClassExpectations;
import io.github.matejkadlec.realmforge.qa.generated.GeneratedClassExpectations.ClassExpectation;
import io.github.matejkadlec.realmforge.qa.generated.GeneratedClassExpectations.KitItem;
import io.github.matejkadlec.realmforge.qa.generated.GeneratedRaceExpectations;
import io.github.matejkadlec.realmforge.qa.generated.GeneratedRaceExpectations.RaceExpectation;
import io.github.matejkadlec.realmforge.stats.CharacterStats;
import io.github.matejkadlec.realmforge.stats.EntityLevel;
import io.github.matejkadlec.realmforge.stats.StatsAttachments;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * C1 (RLM-22): a new player must create a character, and the server enforces the creation rules (architecture 10.1).
 * The race and class come from the generated expectations, so the tests follow the content: the first race and the
 * first class it allows. Players log in through {@link QaPlayers#login}.
 */
public final class CharacterGameTests {
    private static final Identifier NOTHING = Realmforge.id("qa_nothing");

    private CharacterGameTests() {
    }

    /** A new player has no character, so the client opens creation, and gets no race or class modifiers. */
    @QaGameTest
    public static void newPlayerMustCreateACharacter(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = login(helper, "qa-char-new");
        helper.assertFalse(Characters.exists(player), "a new player has no character");
        helper.assertValueEqual(Characters.data(player), CharacterData.NONE, "the character of a new player");
        CharacterStats.recompute(player);
        helper.assertTrue(characterModifiers(player).isEmpty(), "no race or class modifiers without a character");
        helper.assertValueEqual(check(player, firstRace().id(), firstClass().id()), Result.CREATED,
                "the first race and the first class it allows are a valid choice");
        server.getPlayerList().remove(player);
        helper.succeed();
    }

    /** Creating a character sets race and class, applies their modifiers, refills health and gives the kit once. */
    @QaGameTest
    public static void creatingACharacterAppliesRaceClassAndKit(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = login(helper, "qa-char-create");
        RaceExpectation race = firstRace();
        ClassExpectation playerClass = firstClass();
        helper.assertValueEqual(create(player, race.id(), playerClass.id()), Result.CREATED, "creation");

        CharacterData character = Characters.data(player);
        helper.assertTrue(character.exists(), "the player has a character");
        helper.assertValueEqual(character.race().orElseThrow().identifier().toString(), race.id(), "race");
        helper.assertValueEqual(character.classes().stream().map(key -> key.identifier().toString()).toList(),
                List.of(playerClass.id()), "classes");
        assertStatsAtLevel(helper, player, race, playerClass, EntityLevel.MIN);
        helper.assertValueEqual(player.getHealth(), player.getMaxHealth(), "health refilled to the new maximum");
        for (KitItem item : playerClass.startingKit()) {
            helper.assertValueEqual(count(player, item(item.item())), item.count(), "starting kit: " + item.item());
        }
        server.getPlayerList().remove(player);
        helper.succeed();
    }

    /** A character is created once: a second request changes nothing and gives no second kit. */
    @QaGameTest
    public static void aCharacterIsCreatedOnce(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = login(helper, "qa-char-twice");
        RaceExpectation race = firstRace();
        helper.assertValueEqual(create(player, race.id(), race.classes().getFirst()), Result.CREATED, "creation");
        CharacterData created = Characters.data(player);
        Map<Item, Integer> inventory = inventory(player);

        String other = race.classes().getLast();
        helper.assertValueEqual(create(player, race.id(), other), Result.ALREADY_CREATED, "a second creation");
        helper.assertValueEqual(Characters.data(player), created, "the character is unchanged");
        helper.assertValueEqual(inventory(player), inventory, "no second starting kit");
        server.getPlayerList().remove(player);
        helper.succeed();
    }

    /** Unknown races and classes, and a class the race does not allow, are refused without a character. */
    @QaGameTest
    public static void invalidChoicesAreRefused(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = login(helper, "qa-char-invalid");
        RaceExpectation race = firstRace();
        helper.assertValueEqual(create(player, NOTHING.toString(), race.classes().getFirst()), Result.UNKNOWN_RACE,
                "an unknown race");
        helper.assertValueEqual(create(player, race.id(), NOTHING.toString()), Result.UNKNOWN_CLASS,
                "an unknown class");
        helper.assertFalse(Characters.exists(player), "no character after refused requests");
        helper.assertTrue(inventory(player).isEmpty(), "and no starting kit");

        // The content's race allows every class, so a race that allows only the first class tests the last rule.
        HolderLookup.RegistryLookup<PlayerClass> classes =
                server.registryAccess().lookupOrThrow(CharacterRegistries.CLASS);
        ResourceKey<PlayerClass> allowed = classKey(race.classes().getFirst());
        Race narrow = new Race("qa", "qa", List.of(classes.getOrThrow(allowed)), List.of());
        Optional<ResourceKey<PlayerClass>> refused = classes.listElementIds().filter(key -> !key.equals(allowed))
                .findFirst();
        helper.assertTrue(refused.isPresent(), "the content has a second class");
        helper.assertValueEqual(CharacterCreation.check(CharacterData.NONE, Optional.of(narrow), true, refused.get()),
                Result.CLASS_NOT_ALLOWED, "a class the race does not list");
        helper.assertValueEqual(CharacterCreation.check(CharacterData.NONE, Optional.of(narrow), true, allowed),
                Result.CREATED, "the class it lists");
        server.getPlayerList().remove(player);
        helper.succeed();
    }

    /** Class modifiers grow with the level: a level change recomputes them at the next check. */
    @QaGameTest
    public static void classBonusesFollowTheLevel(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = login(helper, "qa-char-level");
        RaceExpectation race = firstRace();
        ClassExpectation playerClass = firstClass();
        helper.assertValueEqual(create(player, race.id(), playerClass.id()), Result.CREATED, "creation");
        int level = playerClass.samples().getLast().level();
        player.setData(StatsAttachments.LEVEL, new EntityLevel(level));
        for (int tick = 0; tick < CharacterStats.CHECK_INTERVAL; tick++) {
            player.doTick();
        }
        assertStatsAtLevel(helper, player, race, playerClass, level);
        server.getPlayerList().remove(player);
        helper.succeed();
    }

    /** A full inventory: the kit is dropped at the player's feet, for them alone, as {@code /give} does. */
    @QaGameTest
    public static void kitIsDroppedWhenTheInventoryIsFull(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = login(helper, "qa-char-full");
        // A creative player's full inventory swallows items instead (Inventory#add, hasInfiniteMaterials).
        player.setGameMode(GameType.SURVIVAL);
        while (player.getInventory().getFreeSlot() >= 0) {
            player.getInventory().add(new ItemStack(Items.STONE, 64));
        }
        ClassExpectation playerClass = firstClass();
        helper.assertValueEqual(create(player, firstRace().id(), playerClass.id()), Result.CREATED, "creation");
        List<ItemEntity> drops = player.level().getEntitiesOfClass(ItemEntity.class,
                player.getBoundingBox().inflate(3));
        for (KitItem item : playerClass.startingKit()) {
            int dropped = drops.stream().filter(drop -> drop.getItem().is(item(item.item())))
                    .mapToInt(drop -> drop.getItem().getCount()).sum();
            helper.assertValueEqual(dropped, item.count(), "dropped: " + item.item());
        }
        helper.assertTrue(drops.stream().allMatch(drop -> player.getUUID().equals(drop.getTarget())),
                "the drops are the player's alone");
        drops.forEach(ItemEntity::discard);
        server.getPlayerList().remove(player);
        helper.succeed();
    }

    /** Once the player has a class, the character sheet's heading names it (Matěj, 2026-10-14). */
    @QaGameTest
    public static void sheetHeadingNamesTheClass(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = login(helper, "qa-char-sheet");
        helper.assertValueEqual(CharacterSheetView.of(player).title().getString(), "qa-char-sheet · Lv. 1",
                "the heading without a class");
        ClassExpectation playerClass = firstClass();
        create(player, firstRace().id(), playerClass.id());
        String className = Component.translatable(playerClass.nameKey()).getString();
        helper.assertValueEqual(CharacterSheetView.of(player).title().getString(),
                "qa-char-sheet · Lv. 1 " + className, "the heading with the class");
        server.getPlayerList().remove(player);
        helper.succeed();
    }

    /** The creation screen's view offers every race with the classes it allows, in the content's order. */
    @QaGameTest
    public static void creationViewOffersRacesAndClasses(GameTestHelper helper) {
        CharacterCreationView view = CharacterCreationView.of(helper.getLevel().registryAccess());
        List<String> raceIds = GeneratedRaceExpectations.RACES.stream().map(RaceExpectation::id).sorted().toList();
        helper.assertValueEqual(view.races().stream().map(race -> race.key().identifier().toString()).toList(),
                raceIds, "races, in id order");
        for (RaceOption race : view.races()) {
            RaceExpectation expected = race(race.key().identifier().toString());
            helper.assertValueEqual(race.name().getString(), Component.translatable(expected.nameKey()).getString(),
                    "race name");
            helper.assertValueEqual(race.classes().stream().map(option -> option.key().identifier().toString())
                    .toList(), expected.classes(), race.key() + " classes, in its order");
            for (ClassOption option : race.classes()) {
                ClassExpectation playerClass = playerClass(option.key().identifier().toString());
                helper.assertValueEqual(option.startingKit().size(), playerClass.startingKit().size(),
                        option.key() + " kit lines");
                PlayerClass entry = helper.getLevel().registryAccess().lookupOrThrow(CharacterRegistries.CLASS)
                        .getValue(option.key());
                helper.assertValueEqual(option.bonuses().size(), entry.modifiers().size(), option.key() + " bonuses");
                helper.assertTrue(option.resource().getString().endsWith(
                                Component.translatable(entry.resource().value().name()).getString()),
                        option.key() + " resource: " + option.resource().getString());
            }
        }
        helper.succeed();
    }

    /** {@code /qa character create|reset}: a playtest can create, remove and recreate a character. */
    @QaGameTest
    public static void qaCharacterCommandsCreateAndReset(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = login(helper, "qa-char-commands");
        RaceExpectation race = firstRace();
        String last = race.classes().getLast();
        run(server, player, "qa character create " + race.id() + " " + NOTHING);
        helper.assertFalse(Characters.exists(player), "an unknown class creates nothing");
        run(server, player, "qa character create " + race.id() + " " + last);
        helper.assertValueEqual(Characters.data(player).classes().stream().map(key -> key.identifier().toString())
                .toList(), List.of(last), "the command created the character");
        helper.assertFalse(characterModifiers(player).isEmpty(), "with its modifiers");
        run(server, player, "qa character reset");
        helper.assertFalse(Characters.exists(player), "reset removed the character");
        helper.assertFalse(player.hasData(CharacterAttachments.CHARACTER), "and its attachment");
        helper.assertTrue(characterModifiers(player).isEmpty(), "and the race and class modifiers");
        run(server, player, "qa character create " + race.id() + " " + race.classes().getFirst());
        helper.assertTrue(Characters.exists(player), "a character can be created again after a reset");
        server.getPlayerList().remove(player);
        helper.succeed();
    }

    /** The race and class modifiers on the player add up to what the pipeline computes for them at the level. */
    private static void assertStatsAtLevel(GameTestHelper helper, ServerPlayer player, RaceExpectation race,
            ClassExpectation playerClass, int level) {
        Map<String, Double> expected = new HashMap<>();
        race.samples().stream().filter(sample -> sample.level() == level).findFirst().orElseThrow().bonuses()
                .forEach(bonus -> expected.merge(bonus.attribute(), bonus.amount(), Double::sum));
        playerClass.samples().stream().filter(sample -> sample.level() == level).findFirst().orElseThrow().bonuses()
                .forEach(bonus -> expected.merge(bonus.attribute(), bonus.amount(), Double::sum));
        helper.assertFalse(expected.isEmpty(), "race and class give something at level " + level);
        for (Map.Entry<String, Double> stat : expected.entrySet()) {
            double got = characterTotal(player, ContentStatGameTests.attribute(stat.getKey()));
            helper.assertTrue(ContentStatGameTests.close(got, stat.getValue()),
                    stat.getKey() + " at level " + level + ": " + stat.getValue() + " expected, got " + got);
        }
    }

    /** The sum of the race and class modifiers on the attribute (all of them add values in the content). */
    private static double characterTotal(ServerPlayer player, Holder<Attribute> attribute) {
        AttributeInstance instance = player.getAttribute(attribute);
        return instance == null ? 0 : instance.getModifiers().stream()
                .filter(modifier -> CharacterBonuses.isCharacterModifier(modifier.id()))
                .filter(modifier -> modifier.operation() == AttributeModifier.Operation.ADD_VALUE)
                .mapToDouble(AttributeModifier::amount).sum();
    }

    private static List<AttributeModifier> characterModifiers(ServerPlayer player) {
        return BuiltInRegistries.ATTRIBUTE.listElements()
                .map(player::getAttribute)
                .filter(instance -> instance != null)
                .flatMap(instance -> instance.getModifiers().stream())
                .filter(modifier -> CharacterBonuses.isCharacterModifier(modifier.id()))
                .toList();
    }

    private static ServerPlayer login(GameTestHelper helper, String name) {
        ServerPlayer player = QaPlayers.login(helper.getLevel().getServer(), new GameProfile(UUID.randomUUID(), name));
        player.snapTo(helper.absoluteVec(new Vec3(2.5, 1, 2.5)), 0, 0);
        return player;
    }

    private static Result create(ServerPlayer player, String race, String playerClass) {
        return CharacterCreation.create(player, raceKey(race), classKey(playerClass));
    }

    private static Result check(ServerPlayer player, String race, String playerClass) {
        return CharacterCreation.check(Characters.data(player), player.registryAccess(), raceKey(race),
                classKey(playerClass));
    }

    private static ResourceKey<Race> raceKey(String id) {
        return ResourceKey.create(CharacterRegistries.RACE, Identifier.parse(id));
    }

    private static ResourceKey<PlayerClass> classKey(String id) {
        return ResourceKey.create(CharacterRegistries.CLASS, Identifier.parse(id));
    }

    private static RaceExpectation firstRace() {
        return GeneratedRaceExpectations.RACES.getFirst();
    }

    /** The first class the first race allows. */
    private static ClassExpectation firstClass() {
        return playerClass(firstRace().classes().getFirst());
    }

    private static RaceExpectation race(String id) {
        return GeneratedRaceExpectations.RACES.stream().filter(race -> race.id().equals(id)).findFirst()
                .orElseThrow(() -> new IllegalStateException("no expectation for race " + id));
    }

    private static ClassExpectation playerClass(String id) {
        return GeneratedClassExpectations.CLASSES.stream().filter(entry -> entry.id().equals(id)).findFirst()
                .orElseThrow(() -> new IllegalStateException("no expectation for class " + id));
    }

    private static Item item(String id) {
        return BuiltInRegistries.ITEM.getValue(Identifier.parse(id));
    }

    private static int count(ServerPlayer player, Item item) {
        return inventory(player).getOrDefault(item, 0);
    }

    /** Every item in the player's inventory, equipment included, with its total count. */
    private static Map<Item, Integer> inventory(ServerPlayer player) {
        Map<Item, Integer> counts = new HashMap<>();
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (!stack.isEmpty()) {
                counts.merge(stack.getItem(), stack.getCount(), Integer::sum);
            }
        }
        return counts;
    }

    private static void run(MinecraftServer server, ServerPlayer player, String command) {
        CommandSourceStack source = player.createCommandSourceStack().withPermission(PermissionSet.ALL_PERMISSIONS);
        server.getCommands().performPrefixedCommand(source, command);
    }
}
