package io.github.matejkadlec.realmforge.qa.gametest;

import io.github.matejkadlec.realmforge.character.StatPoints;
import io.github.matejkadlec.realmforge.generated.GeneratedItems;
import io.github.matejkadlec.realmforge.qa.generated.GeneratedItemExpectations;
import io.github.matejkadlec.realmforge.qa.generated.GeneratedItemExpectations.ItemExpectation;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

/**
 * Checks that every item defined in {@code content/items/} is in the game exactly as the content says,
 * using expectations generated from the same YAML ({@link GeneratedItemExpectations}).
 *
 * <p>Coordinates are relative to the template's corner: its stone floor is at y = 0.
 */
public final class ContentItemGameTests {
    /** The item tag of every item {@code use}, as the core mod reads it. */
    private static final List<TagKey<Item>> USE_TAGS = List.of(StatPoints.UNLEARN_ITEMS);

    private ContentItemGameTests() {
    }

    @QaGameTest
    public static void contentItemsAreRegisteredWithTheirProperties(GameTestHelper helper) {
        helper.assertFalse(GeneratedItemExpectations.ITEMS.isEmpty(), "no item expectations were generated");
        for (ItemExpectation expected : GeneratedItemExpectations.ITEMS) {
            Identifier id = Identifier.parse(expected.id());
            helper.assertTrue(BuiltInRegistries.ITEM.containsKey(id), "item is not registered: " + id);
            Item item = BuiltInRegistries.ITEM.getValue(id);
            helper.assertValueEqual(new ItemStack(item).getMaxStackSize(), expected.maxStackSize(), id + " max stack size");
            helper.assertValueEqual(item.getDescriptionId(), expected.translationKey(), id + " translation key");
            if (!expected.useTag().isEmpty()) {
                assertUseTag(helper, id, item, Identifier.parse(expected.useTag()));
            }
        }
        helper.succeed();
    }

    /**
     * The item carries the tag of its use, and the core mod reads that tag: the generator's tag path and the mod's
     * {@code TagKey} cannot drift apart without this failing.
     */
    private static void assertUseTag(GameTestHelper helper, Identifier id, Item item, Identifier tag) {
        helper.assertTrue(USE_TAGS.stream().anyMatch(key -> key.location().equals(tag)),
                id + " has the use tag " + tag + ", which the core mod does not read");
        helper.assertTrue(new ItemStack(item).is(TagKey.create(Registries.ITEM, tag)),
                id + " is not in its use tag " + tag);
    }

    @QaGameTest
    public static void contentItemsAreListedInTheirCreativeTabs(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        CreativeModeTabs.tryRebuildTabContents(level.enabledFeatures(), true, level.registryAccess());
        for (ItemExpectation expected : GeneratedItemExpectations.ITEMS) {
            Item item = BuiltInRegistries.ITEM.getValue(Identifier.parse(expected.id()));
            for (String tabId : expected.creativeTabs()) {
                CreativeModeTab tab = BuiltInRegistries.CREATIVE_MODE_TAB.getValue(Identifier.parse(tabId));
                helper.assertTrue(tab != null, "unknown creative tab " + tabId);
                boolean listed = tab.getDisplayItems().stream().anyMatch(stack -> stack.is(item));
                helper.assertTrue(listed, expected.id() + " is not listed in creative tab " + tabId);
            }
        }
        helper.succeed();
    }

    /** The test item behaves like a normal item in the world: a hopper picks it up and moves it into a chest. */
    @QaGameTest
    public static void testItemMovesThroughHopperIntoChest(GameTestHelper helper) {
        BlockPos chest = new BlockPos(2, 1, 2);
        BlockPos hopper = chest.above();
        helper.setBlock(chest, Blocks.CHEST);
        helper.setBlock(hopper, Blocks.HOPPER); // default state faces down, into the chest
        helper.spawnItem(GeneratedItems.TEST_ITEM.get(), 2.5f, 3.5f, 2.5f);
        helper.succeedWhen(() -> helper.assertContainerContains(chest, GeneratedItems.TEST_ITEM.get()));
    }
}
