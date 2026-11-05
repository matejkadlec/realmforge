package io.github.matejkadlec.realmforge.qa.gametest;

import io.github.matejkadlec.realmforge.generated.GeneratedItems;
import io.github.matejkadlec.realmforge.generated.GeneratedStats;
import io.github.matejkadlec.realmforge.item.ItemComponents;
import io.github.matejkadlec.realmforge.item.NetheriteUpgrades;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SmithingRecipe;
import net.minecraft.world.item.crafting.SmithingRecipeInput;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;

/**
 * RLM-566: the netherite upgrades. A smithing table puts a Ruby, Sapphire or Topaz Upgrade on one netherite piece
 * (template, piece, upgrade; {@code item/NetheriteUpgradeRecipe}), at most one per piece, and the piece gives the bonus
 * of its archetype while worn, in its armor slot: a melee piece (vanilla's netherite armor) the melee bonus, a caster
 * piece (our netherite robes) the caster bonus (Matěj, 2026-10-30, RLM-511). Its tooltip names the upgrade.
 */
public final class NetheriteUpgradeGameTests {
    private NetheriteUpgradeGameTests() {
    }

    /**
     * Done when of RLM-566: an upgraded netherite chestplate gives its bonus when worn and not in the inventory. The
     * Ruby on a melee piece is +5 % Attack Damage, on the base (architecture 8.2), and the piece keeps its
     * enchantments.
     */
    @QaGameTest
    public static void anUpgradedChestplateCountsOnlyWhenWorn(GameTestHelper helper) {
        ItemStack piece = new ItemStack(Items.NETHERITE_CHESTPLATE);
        piece.enchant(helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
                .getOrThrow(Enchantments.PROTECTION), 4);
        ItemStack upgraded = smith(helper, piece, GeneratedItems.RUBY_UPGRADE.toStack()).orElseThrow(
                () -> helper.assertionException("the Ruby Upgrade goes on a netherite chestplate"));
        helper.assertTrue(NetheriteUpgrades.isUpgraded(upgraded), "the result carries the upgrade");
        helper.assertValueEqual(upgraded.get(ItemComponents.NETHERITE_UPGRADE.get()).identifier(),
                Identifier.parse("realmforge:ruby"), "the Ruby's upgrade");
        helper.assertValueEqual(upgraded.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).size(), 1,
                "the piece keeps its enchantment");

        ServerPlayer player = ApotheosisGameTests.player(helper, "qa-netherite-upgrade");
        Identifier id = NetheriteUpgrades.modifierId(EquipmentSlot.CHEST, 0);
        AttributeInstance attackDamage = player.getAttribute(Attributes.ATTACK_DAMAGE);
        player.getInventory().setItem(9, upgraded.copy());
        ApotheosisGameTests.ticks(player, 2);
        helper.assertTrue(attackDamage.getModifier(id) == null, "nothing while it is in the bag");

        player.getInventory().setItem(9, ItemStack.EMPTY);
        player.setItemSlot(EquipmentSlot.CHEST, upgraded.copy());
        ApotheosisGameTests.ticks(player, 2);
        AttributeModifier worn = attackDamage.getModifier(id);
        helper.assertTrue(worn != null && CombatGameTests.close(worn.amount(), 0.05)
                && worn.operation() == AttributeModifier.Operation.ADD_MULTIPLIED_BASE,
                "+5 % Attack Damage on the base while it is worn: " + worn);

        player.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
        ApotheosisGameTests.ticks(player, 2);
        helper.assertTrue(attackDamage.getModifier(id) == null, "and gone when it comes off");
        ApotheosisGameTests.logout(player);
        helper.succeed();
    }

    /**
     * Done when of RLM-566: a second upgrade on the same piece is refused, the same upgrade or another; a piece that is
     * not netherite takes none.
     */
    @QaGameTest
    public static void aPieceTakesOneUpgrade(GameTestHelper helper) {
        ItemStack upgraded = smith(helper, new ItemStack(Items.NETHERITE_HELMET),
                GeneratedItems.SAPPHIRE_UPGRADE.toStack()).orElseThrow(
                        () -> helper.assertionException("the Sapphire Upgrade goes on a netherite helmet"));
        helper.assertTrue(smith(helper, upgraded, GeneratedItems.TOPAZ_UPGRADE.toStack()).isEmpty(),
                "a second, other upgrade is refused");
        helper.assertTrue(smith(helper, upgraded, GeneratedItems.SAPPHIRE_UPGRADE.toStack()).isEmpty(),
                "the same upgrade again is refused");
        helper.assertTrue(smith(helper, new ItemStack(Items.DIAMOND_CHESTPLATE),
                GeneratedItems.RUBY_UPGRADE.toStack()).isEmpty(), "a diamond chestplate takes none");
        helper.assertTrue(smith(helper, new ItemStack(GeneratedItems.ARCANE_SILK_ROBES.get()),
                GeneratedItems.RUBY_UPGRADE.toStack()).isEmpty(), "nor robes below netherite");
        helper.succeed();
    }

    /**
     * Done when of RLM-566: a caster piece takes the caster bonus. Each upgrade on a caster and on a melee piece, in
     * the piece's own slot: Matěj's values of 2026-10-30.
     */
    @QaGameTest
    public static void eachUpgradeGivesItsArchetypesBonus(GameTestHelper helper) {
        assertBonus(helper, GeneratedItems.NETHERITE_ROBES.toStack(), GeneratedItems.RUBY_UPGRADE.toStack(),
                EquipmentSlot.CHEST, List.of(bonus(GeneratedStats.SPELL_POWER, 0.05,
                        AttributeModifier.Operation.ADD_MULTIPLIED_BASE)));
        assertBonus(helper, GeneratedItems.NETHERITE_CAP.toStack(), GeneratedItems.SAPPHIRE_UPGRADE.toStack(),
                EquipmentSlot.HEAD, List.of(bonus(GeneratedStats.MAGIC_SHIELD, 3,
                        AttributeModifier.Operation.ADD_VALUE)));
        assertBonus(helper, GeneratedItems.NETHERITE_SANDALS.toStack(), GeneratedItems.TOPAZ_UPGRADE.toStack(),
                EquipmentSlot.FEET, List.of(bonus(GeneratedStats.COOLDOWN_REDUCTION, 0.05,
                        AttributeModifier.Operation.ADD_VALUE)));
        assertBonus(helper, new ItemStack(Items.NETHERITE_LEGGINGS), GeneratedItems.SAPPHIRE_UPGRADE.toStack(),
                EquipmentSlot.LEGS, List.of(
                        bonus(Attributes.ARMOR, 2, AttributeModifier.Operation.ADD_VALUE),
                        bonus(Attributes.ARMOR_TOUGHNESS, 1, AttributeModifier.Operation.ADD_VALUE)));
        assertBonus(helper, new ItemStack(Items.NETHERITE_BOOTS), GeneratedItems.TOPAZ_UPGRADE.toStack(),
                EquipmentSlot.FEET, List.of(bonus(Attributes.MOVEMENT_SPEED, 0.05,
                        AttributeModifier.Operation.ADD_MULTIPLIED_BASE)));
        helper.succeed();
    }

    /** Done when of RLM-566: the tooltip shows the upgrade, its name in its gem's colour; a plain piece shows none. */
    @QaGameTest
    public static void theTooltipNamesTheUpgrade(GameTestHelper helper) {
        ItemStack upgraded = smith(helper, new ItemStack(Items.NETHERITE_BOOTS), GeneratedItems.RUBY_UPGRADE.toStack())
                .orElseThrow(() -> helper.assertionException("the Ruby Upgrade goes on netherite boots"));
        List<Component> tooltip = NetheriteUpgrades.tooltip(upgraded);
        helper.assertValueEqual(tooltip.stream().map(Component::getString).toList(), List.of("Ruby Upgrade"),
                "the upgrade's line");
        helper.assertValueEqual(tooltip.getFirst().getStyle().getColor(), TextColor.fromRgb(0xC42638),
                "in the Ruby's colour");
        helper.assertValueEqual(NetheriteUpgrades.tooltip(new ItemStack(Items.NETHERITE_BOOTS)), List.of(),
                "a plain piece has no such line");
        helper.succeed();
    }

    /** What the smithing table makes of {@code piece} and {@code upgrade} with the netherite template, if anything. */
    private static Optional<ItemStack> smith(GameTestHelper helper, ItemStack piece, ItemStack upgrade) {
        ServerLevel level = helper.getLevel();
        SmithingRecipeInput input = new SmithingRecipeInput(
                new ItemStack(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE), piece, upgrade);
        return level.recipeAccess().getRecipeFor(RecipeType.SMITHING, input, level)
                .map(RecipeHolder<SmithingRecipe>::value).map(recipe -> recipe.assemble(input))
                .filter(result -> !result.isEmpty());
    }

    /** {@code upgrade} on {@code piece} gives exactly {@code expected}, in {@code slot}, besides the piece's stats. */
    private static void assertBonus(GameTestHelper helper, ItemStack piece, ItemStack upgrade, EquipmentSlot slot,
            List<Expected> expected) {
        ItemStack upgraded = smith(helper, piece, upgrade).orElseThrow(() -> helper.assertionException(
                upgrade.getHoverName().getString() + " goes on " + piece.getHoverName().getString()));
        List<ItemAttributeModifiers.Entry> bonus = upgraded.getAttributeModifiers().modifiers().stream()
                .filter(entry -> entry.modifier().id().getPath().startsWith("netherite_upgrade/")).toList();
        String what = upgrade.getHoverName().getString() + " on " + piece.getHoverName().getString();
        helper.assertValueEqual(bonus.size(), expected.size(), what + ": its bonus " + bonus);
        for (int n = 0; n < expected.size(); n++) {
            ItemAttributeModifiers.Entry entry = bonus.get(n);
            Expected wanted = expected.get(n);
            helper.assertTrue(entry.attribute().is(wanted.attribute().unwrapKey().orElseThrow())
                    && CombatGameTests.close(entry.modifier().amount(), wanted.amount())
                    && entry.modifier().operation() == wanted.operation()
                    && entry.modifier().id().equals(NetheriteUpgrades.modifierId(slot, n))
                    && entry.slot() == EquipmentSlotGroup.bySlot(slot), what + ": " + entry + ", wanted " + wanted);
        }
    }

    private static Expected bonus(Holder<Attribute> attribute, double amount, AttributeModifier.Operation operation) {
        return new Expected(attribute, amount, operation);
    }

    private record Expected(Holder<Attribute> attribute, double amount, AttributeModifier.Operation operation) {
    }
}
