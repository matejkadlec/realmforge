package io.github.matejkadlec.realmforge.qa.gametest;

import io.github.matejkadlec.realmforge.qa.generated.GeneratedBlockExpectations;
import io.github.matejkadlec.realmforge.qa.generated.GeneratedBlockExpectations.BlockExpectation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.locale.Language;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * Checks that every block of {@code content/blocks/} is in the game as the content says: registered with its hardness
 * and blast resistance, its block item, its name, its mining tags and its loot table, and that it drops itself for
 * the right tool and nothing for the wrong one ({@link GeneratedBlockExpectations}).
 */
public final class ContentBlockGameTests {
    private ContentBlockGameTests() {
    }

    @QaGameTest
    public static void contentBlocksAreRegisteredWithTheirProperties(GameTestHelper helper) {
        helper.assertFalse(GeneratedBlockExpectations.BLOCKS.isEmpty(), "no block expectations were generated");
        for (BlockExpectation expected : GeneratedBlockExpectations.BLOCKS) {
            Identifier id = Identifier.parse(expected.id());
            helper.assertTrue(BuiltInRegistries.BLOCK.containsKey(id), "block is not registered: " + id);
            Block block = BuiltInRegistries.BLOCK.getValue(id);
            BlockState state = block.defaultBlockState();
            helper.assertValueEqual(block.defaultDestroyTime(), expected.hardness(), id + " hardness");
            helper.assertValueEqual(block.getExplosionResistance(), expected.blastResistance(), id + " blast resistance");
            helper.assertValueEqual(state.requiresCorrectToolForDrops(), expected.requiresTool(), id + " needs a tool");
            helper.assertValueEqual(block.getDescriptionId(), expected.translationKey(), id + " translation key");
            helper.assertTrue(Language.getInstance().has(expected.translationKey()), id + ": no en_us name");
            for (String tag : expected.miningTags()) {
                helper.assertTrue(state.is(TagKey.create(Registries.BLOCK, Identifier.parse(tag))), id + " is in " + tag);
            }
            if (expected.lootTable().isEmpty()) {
                // A kind without an item (the realm portal's portal block): no item, no loot.
                helper.assertFalse(BuiltInRegistries.ITEM.containsKey(id), id + " has no item");
                helper.assertValueEqual(block.getLootTable(), java.util.Optional.empty(), id + " loot table");
                continue;
            }
            Item item = BuiltInRegistries.ITEM.getValue(id);
            helper.assertTrue(item instanceof BlockItem blockItem && blockItem.getBlock() == block,
                    id + " has its block item");
            helper.assertValueEqual(item.getDescriptionId(), expected.translationKey(), id + " item name");
            ResourceKey<LootTable> lootTable =
                    ResourceKey.create(Registries.LOOT_TABLE, Identifier.parse(expected.lootTable()));
            helper.assertValueEqual(block.getLootTable(), java.util.Optional.of(lootTable), id + " loot table");
        }
        helper.succeed();
    }

    /**
     * A block that needs a pickaxe drops itself for one and nothing for a bare hand; a block without an item drops
     * nothing.
     */
    @QaGameTest
    public static void contentBlocksDropThemselvesForTheRightTool(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(2, 1, 2));
        for (BlockExpectation expected : GeneratedBlockExpectations.BLOCKS) {
            Block block = BuiltInRegistries.BLOCK.getValue(Identifier.parse(expected.id()));
            BlockState state = block.defaultBlockState();
            var withPickaxe = Block.getDrops(state, helper.getLevel(), pos, null, null, new ItemStack(Items.IRON_PICKAXE));
            if (expected.lootTable().isEmpty()) {
                helper.assertTrue(withPickaxe.isEmpty(), expected.id() + " drops nothing, not " + withPickaxe);
                continue;
            }
            helper.assertTrue(withPickaxe.size() == 1 && withPickaxe.get(0).is(block.asItem()),
                    expected.id() + " drops itself for an iron pickaxe, not " + withPickaxe);
            if (expected.requiresTool()) {
                helper.assertTrue(state.requiresCorrectToolForDrops()
                                && !new ItemStack(Items.STICK).isCorrectToolForDrops(state),
                        expected.id() + " needs the right tool");
            }
        }
        helper.succeed();
    }
}
