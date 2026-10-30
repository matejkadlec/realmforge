package io.github.matejkadlec.realmforge.qa.gametest;

import io.github.matejkadlec.realmforge.compat.inventorysorter.BackpackSortRule;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * RLM-519: with the core mod's backpack rule ({@link BackpackSortRule}), Inventory Sorting sorts a player's inventory
 * around their backpacks and never moves one, and the core mod's {@code InventorySortingStacksMixin} leaves their very
 * stacks in place (not copies), so a backpack's open screen, tied to its slot and its stack, stays open. The GameTest
 * server loads Inventory Sorting and Sophisticated Backpacks as the pack does (ADR 0042). The client adds the rule to
 * the rules it sends the server; here {@code /invsort priority add} puts it on the server as that sync would, and
 * {@code /invsort sortme} sorts as the Sort key over the player's own slots does.
 */
public final class InventorySortingGameTests {
    /** The last slot of the main inventory, where a sort by name would never leave a lone backpack. */
    private static final int LAST_SLOT = Inventory.INVENTORY_SIZE - 1;

    private InventorySortingGameTests() {
    }

    /** With the rule, both backpacks keep their slots, as the same stacks, while the rest is sorted to the front. */
    @QaGameTest
    public static void backpacksStayInTheirSlots(GameTestHelper helper) {
        ServerPlayer player = QaPlayers.loginOnPlatform(helper, "qa-sort-backpacks");
        try {
            Item backpack = item(helper, "sophisticatedbackpacks:backpack");
            Item netherite = item(helper, "sophisticatedbackpacks:netherite_backpack");
            fill(player, backpack, netherite);
            ItemStack kept = player.getInventory().getItem(LAST_SLOT);
            run(player, "invsort priority add " + BackpackSortRule.POSITION + " " + BackpackSortRule.MATCH);
            run(player, "invsort sortme");

            Inventory inventory = player.getInventory();
            helper.assertTrue(inventory.getItem(LAST_SLOT).is(backpack), "the backpack keeps the last slot");
            helper.assertTrue(inventory.getItem(LAST_SLOT) == kept,
                    "the slot keeps the backpack's own stack, not a copy, which would close its open screen");
            helper.assertTrue(inventory.getItem(LAST_SLOT - 2).is(netherite), "the netherite backpack keeps its slot");
            helper.assertTrue(inventory.getItem(Inventory.SELECTION_SIZE).is(Items.APPLE)
                            || inventory.getItem(Inventory.SELECTION_SIZE).is(Items.DIRT),
                    "the rest is sorted to the front: " + inventory.getItem(Inventory.SELECTION_SIZE));
            helper.assertTrue(inventory.getItem(20).isEmpty(), "the dirt left its slot");
        } finally {
            helper.getLevel().getServer().getPlayerList().remove(player);
        }
        helper.succeed();
    }

    /** Without the rule the same sort moves a lone backpack from the last slot: the bug the rule fixes. */
    @QaGameTest
    public static void withoutTheRuleASortMovesTheBackpack(GameTestHelper helper) {
        ServerPlayer player = QaPlayers.loginOnPlatform(helper, "qa-sort-backpack-moves");
        try {
            Item backpack = item(helper, "sophisticatedbackpacks:backpack");
            fill(player, backpack, item(helper, "sophisticatedbackpacks:netherite_backpack"));
            run(player, "invsort sortme");
            helper.assertTrue(!player.getInventory().getItem(LAST_SLOT).is(backpack),
                    "an unruled sort moves the backpack out of the last slot");
        } finally {
            helper.getLevel().getServer().getPlayerList().remove(player);
        }
        helper.succeed();
    }

    /** A backpack in the last slot, another two before it, and two stacks to sort in the middle. */
    private static void fill(ServerPlayer player, Item backpack, Item netherite) {
        Inventory inventory = player.getInventory();
        inventory.setItem(LAST_SLOT, new ItemStack(backpack));
        inventory.setItem(LAST_SLOT - 2, new ItemStack(netherite));
        inventory.setItem(20, new ItemStack(Items.DIRT, 16));
        inventory.setItem(25, new ItemStack(Items.APPLE, 3));
    }

    private static Item item(GameTestHelper helper, String id) {
        Item item = BuiltInRegistries.ITEM.getValue(Identifier.parse(id));
        helper.assertTrue(item != Items.AIR, id + " is registered (Sophisticated Backpacks is loaded)");
        return item;
    }

    private static void run(ServerPlayer player, String command) {
        CommandSourceStack source = player.createCommandSourceStack().withPermission(PermissionSet.ALL_PERMISSIONS);
        player.level().getServer().getCommands().performPrefixedCommand(source, command);
    }
}
