package io.github.matejkadlec.realmforge.qa.gametest;

import dev.shadowsoffire.apotheosis.Apoth;
import dev.shadowsoffire.apotheosis.affix.Affix;
import dev.shadowsoffire.apotheosis.affix.AffixHelper;
import dev.shadowsoffire.apotheosis.affix.AffixRegistry;
import dev.shadowsoffire.apotheosis.affix.AffixType;
import dev.shadowsoffire.apotheosis.loot.LootCategory;
import dev.shadowsoffire.apotheosis.loot.LootController;
import dev.shadowsoffire.apotheosis.loot.LootRarity;
import dev.shadowsoffire.apotheosis.loot.LootRule;
import dev.shadowsoffire.apotheosis.loot.RarityRegistry;
import dev.shadowsoffire.apotheosis.tiers.GenContext;
import dev.shadowsoffire.apotheosis.tiers.WorldTier;
import dev.shadowsoffire.apotheosis.tiers.augments.TierAugmentRegistry;
import io.github.matejkadlec.realmforge.compat.apotheosis.ApotheosisCompat;
import io.github.matejkadlec.realmforge.generated.GeneratedItems;
import io.github.matejkadlec.realmforge.generated.GeneratedTexts;
import io.github.matejkadlec.realmforge.spell.SpellCaster;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * RLM-232 and RLM-273: the affix set of Matěj's review and our wands' loot category, as Apotheosis loads them with
 * the pack (ADR 0042), read through Apotheosis's own registries and loot code (the QA mod compiles against it).
 * Rolling items here also checks that no category meets an ability slot it has no ability for: Apotheosis logs
 * "Failed to execute AffixLootRule (no affixes available)" at ERROR then, which {@code rf gametest-log} refuses.
 */
public final class ApotheosisLootGameTests {
    /** The affixes Matěj's review turned off (RLM-232): the 29, Executing and Runed. */
    private static final List<String> REVIEW_OFF = List.of(
            "armor/attribute/elastic", "armor/attribute/fireproof", "armor/attribute/oxygenated",
            "armor/attribute/winged", "armor/dmg_reduction/grounded", "armor/dmg_reduction/runed",
            "armor/mob_effect/blinding", "boots/ability/steadfast", "boots/ability/surefooted",
            "breaker/ability/sandforming", "breaker/ability/stoneforming", "breaker/attribute/submerged",
            "breaker/effect/omnetic", "breaker/enchantment/prosperous", "breaker/mob_effect/spelunkers",
            "melee/attribute/glacial", "melee/attribute/infernal", "melee/cleaving", "melee/executing",
            "melee/festive", "melee/mob_effect/elusive", "melee/mob_effect/omniscient",
            "ranged/enchantment/prosperous", "ranged/mob_effect/deathbound", "ranged/mob_effect/shulkers",
            "shears/effect/gardening", "shears/effect/leafforming", "shield/mob_effect/galvanizing",
            "shield/mob_effect/withering", "weapon/attribute/nullifying", "weapon/attribute/runebreaking");
    /** The affixes the review opens to Rare, with an item of their category. */
    private static final Map<String, Item> OPENED_TO_RARE = Map.of(
            "melee/attribute/giant_slaying", Items.DIAMOND_SWORD,
            "melee/thunderstruck", Items.DIAMOND_SWORD,
            "ranged/mob_effect/acidic", Items.BOW,
            "ranged/mob_effect/blighted", Items.BOW,
            "ranged/spectral", Items.BOW,
            "shield/catalyzing", Items.SHIELD,
            "shield/psychic", Items.SHIELD,
            "shield/retreating", Items.SHIELD);
    /** One item of each of Apotheosis's loot categories that ours leave in place. */
    private static final List<Item> CATEGORY_ITEMS = List.of(
            Items.DIAMOND_SWORD, Items.TRIDENT, Items.BOW, Items.CROSSBOW, Items.DIAMOND_PICKAXE, Items.SHIELD,
            Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS, Items.SHEARS);
    private static final String[] RARITIES = {"common", "uncommon", "rare", "epic", "mythic"};
    private static final int ROLLS = 20;
    private static final int RARE_SWORD_ROLLS = 200;
    /** Giant Slaying at Rare: 5 % to 10 % of the target's current health; level 1.0 is the top of that range. */
    private static final double GIANT_SLAYING_RARE_MAX = 0.10;
    private static final double EPSILON = 1e-4;

    private ApotheosisLootGameTests() {
    }

    /** Done when of RLM-232: the review's affixes are off, the ones it kept are not. */
    @QaGameTest
    public static void theReviewsAffixesAreOff(GameTestHelper helper) {
        for (String affix : REVIEW_OFF) {
            helper.assertTrue(AffixRegistry.INSTANCE.getValue(apotheosis(affix)) == null, affix + " is off");
        }
        for (String affix : List.of("melee/attribute/giant_slaying", "melee/thunderstruck",
                "generic/attribute/lucky", "armor/dmg_reduction/blockading", "boots/ability/unhurried")) {
            helper.assertTrue(AffixRegistry.INSTANCE.getValue(apotheosis(affix)) != null, affix + " is kept");
        }
        helper.succeed();
    }

    /** RLM-232's value table: these affixes apply at Rare; Magical stays Epic and Legendary (Matěj, 2026-10-29). */
    @QaGameTest
    public static void theReviewOpensAffixesToRare(GameTestHelper helper) {
        LootRarity rare = rarity("rare");
        OPENED_TO_RARE.forEach((id, item) -> {
            ItemStack stack = new ItemStack(item);
            helper.assertTrue(affix(id).canApplyTo(stack, LootCategory.forItem(stack), rare), id + " applies at Rare");
        });
        ItemStack bow = new ItemStack(Items.BOW);
        Affix magical = affix("ranged/magical");
        helper.assertFalse(magical.canApplyTo(bow, LootCategory.forItem(bow), rare), "Magical does not roll at Rare");
        helper.assertTrue(magical.canApplyTo(bow, LootCategory.forItem(bow), rarity("epic")), "Magical rolls at Epic");
        helper.succeed();
    }

    /** Giant Slaying at Rare adds its Rare value, which Apotheosis 9.1.0 did not have. */
    @QaGameTest
    public static void giantSlayingAtRareAddsItsRareValue(GameTestHelper helper) {
        ServerPlayer player = ApotheosisGameTests.player(helper, "qa-apoth-giant-slaying");
        Identifier currentHpDamage = Identifier.parse("apothic_attributes:current_hp_damage");
        double before = player.getAttributeValue(ApotheosisGameTests.attribute(currentHpDamage));
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_SWORD));
        ApotheosisGameTests.run(player, "apoth set_rarity apotheosis:rare");
        ApotheosisGameTests.run(player, "apoth affix apply apotheosis:melee/attribute/giant_slaying 1.0");
        ApotheosisGameTests.ticks(player, 2);

        double gained = player.getAttributeValue(ApotheosisGameTests.attribute(currentHpDamage)) - before;
        helper.assertTrue(Math.abs(gained - GIANT_SLAYING_RARE_MAX) < EPSILON,
                "a Rare Giant Slaying at its top adds " + GIANT_SLAYING_RARE_MAX + ", added " + gained);
        ApotheosisGameTests.logout(player);
        helper.succeed();
    }

    /**
     * Rare's chanced ability slot (Matěj, 2026-10-29): 25 %, so a Rare sword sometimes carries Thunderstruck, never
     * two abilities.
     */
    @QaGameTest
    public static void aRareSwordSometimesRollsAnAbility(GameTestHelper helper) {
        LootRarity rare = rarity("rare");
        helper.assertTrue(rare.rules().stream().anyMatch(rule -> rule instanceof LootRule.ChancedLootRule chanced
                        && Math.abs(chanced.chance() - 0.25f) < EPSILON
                        && chanced.rule() instanceof LootRule.AffixLootRule affix && affix.type() == AffixType.ABILITY),
                "Rare's rules hold a 25 % ability slot: " + rare.rules());
        GenContext context = context(helper, WorldTier.HAVEN, 232);
        int withAbility = 0;
        for (int i = 0; i < RARE_SWORD_ROLLS; i++) {
            int abilities = abilities(LootController.createLootItem(new ItemStack(Items.DIAMOND_SWORD), rare, context));
            helper.assertTrue(abilities <= 1, "a Rare sword has at most one ability, rolled " + abilities);
            withAbility += abilities;
        }
        // Expected 50 of 200; a fixed seed, so the band only guards against the slot missing or always firing.
        helper.assertTrue(withAbility >= 20 && withAbility <= 90,
                "about a quarter of Rare swords carry an ability: " + withAbility + " of " + RARE_SWORD_ROLLS);
        helper.succeed();
    }

    /**
     * Matěj, 2026-10-31 (RLM-232): helmets have no ability left once Runed is off, and boots none below Epic, so they
     * skip that slot. Every other category rolls at every rarity and tier without an empty slot (the log check).
     */
    @QaGameTest(timeoutTicks = 200)
    public static void everyCategoryRollsWithoutAnEmptySlot(GameTestHelper helper) {
        for (WorldTier tier : new WorldTier[] {WorldTier.HAVEN, WorldTier.PINNACLE}) {
            GenContext context = context(helper, tier, 273);
            for (String id : RARITIES) {
                LootRarity rarity = rarity(id);
                for (Item item : CATEGORY_ITEMS) {
                    for (int i = 0; i < ROLLS; i++) {
                        ItemStack rolled = LootController.createLootItem(new ItemStack(item), rarity, context);
                        boolean noAbility = item == Items.DIAMOND_HELMET
                                || (item == Items.DIAMOND_BOOTS && id.equals("rare"));
                        if (noAbility) {
                            helper.assertTrue(abilities(rolled) == 0,
                                    BuiltInRegistries.ITEM.getKey(item) + " at " + id + " rolls no ability");
                        }
                    }
                }
            }
        }
        helper.succeed();
    }

    /** RLM-232: the world tiers no longer cap Apothic Enchanting's max Eterna; their other player augments stay. */
    @QaGameTest
    public static void theWorldTiersCapNoEterna(GameTestHelper helper) {
        for (String tier : new String[] {"haven", "frontier", "ascent", "summit"}) {
            helper.assertTrue(TierAugmentRegistry.INSTANCE.getValue(apotheosis(tier + "/max_eterna")) == null,
                    tier + "'s max Eterna cap is off");
        }
        helper.assertTrue(TierAugmentRegistry.INSTANCE.getValue(apotheosis("frontier/luck")) != null,
                "Frontier's luck stays");
        helper.succeed();
    }

    /** Done when of RLM-273: every wand is {@code realmforge:wand}, never Apotheosis's melee weapon. */
    @QaGameTest
    public static void aWandIsAWandNotAMeleeWeapon(GameTestHelper helper) {
        LootCategory wand = ApotheosisCompat.WAND.get();
        helper.assertValueEqual(wand.getKey(), Identifier.fromNamespaceAndPath("realmforge", "wand"), "the key");
        int wands = 0;
        for (Item item : BuiltInRegistries.ITEM) {
            ItemStack stack = new ItemStack(item);
            if (stack.is(SpellCaster.WANDS)) {
                helper.assertTrue(LootCategory.forItem(stack) == wand, item + " is a wand to Apotheosis");
                wands++;
            }
        }
        helper.assertValueEqual(wands, 7, "the seven wands");
        LootCategory sword = LootCategory.forItem(new ItemStack(Items.DIAMOND_SWORD));
        helper.assertTrue(sword == Apoth.LootCategories.MELEE_WEAPON,
                "a sword is still a melee weapon");
        // The tooltips name the category by our texts.
        helper.assertValueEqual(wand.getDescId(), GeneratedTexts.LOOT_CATEGORY_WAND, "its name's key");
        helper.assertValueEqual(wand.getDescIdPlural(), GeneratedTexts.LOOT_CATEGORY_WAND_PLURAL, "its plural's key");
        helper.succeed();
    }

    /**
     * Done when of RLM-273: no affix can roll on a wand at any rarity, so never a melee one; a sword still has its
     * affixes. The wand's own affixes are the caster affixes (RLM-557).
     */
    @QaGameTest
    public static void aWandNeverRollsAMeleeAffix(GameTestHelper helper) {
        ItemStack wand = new ItemStack(GeneratedItems.NETHERITE_WAND.get());
        ItemStack sword = new ItemStack(Items.NETHERITE_SWORD);
        for (String id : RARITIES) {
            LootRarity rarity = rarity(id);
            for (AffixType type : AffixType.values()) {
                long available = LootController.getAvailableAffixes(wand, rarity, type).count();
                helper.assertValueEqual(available, 0L, "affixes of kind " + type + " for a wand at " + id);
            }
            helper.assertTrue(LootController.getAvailableAffixes(sword, rarity, AffixType.STAT).findAny().isPresent(),
                    "a sword has stat affixes at " + id);
        }
        helper.succeed();
    }

    private static int abilities(ItemStack stack) {
        return (int) AffixHelper.getAffixes(stack).keySet().stream()
                .filter(holder -> holder.get().definition().type() == AffixType.ABILITY)
                .count();
    }

    private static GenContext context(GameTestHelper helper, WorldTier tier, long seed) {
        return GenContext.standalone(RandomSource.create(seed), tier, 0, helper.getLevel(),
                helper.absolutePos(BlockPos.ZERO));
    }

    private static Identifier apotheosis(String path) {
        return Identifier.fromNamespaceAndPath("apotheosis", path);
    }

    private static Affix affix(String path) {
        Affix affix = AffixRegistry.INSTANCE.getValue(apotheosis(path));
        if (affix == null) {
            throw new IllegalStateException("Apotheosis has no affix apotheosis:" + path);
        }
        return affix;
    }

    private static LootRarity rarity(String path) {
        LootRarity rarity = RarityRegistry.INSTANCE.getValue(apotheosis(path));
        if (rarity == null) {
            throw new IllegalStateException("Apotheosis has no rarity apotheosis:" + path);
        }
        return rarity;
    }
}
