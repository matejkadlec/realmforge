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
import dev.shadowsoffire.apotheosis.socket.SocketHelper;
import dev.shadowsoffire.apotheosis.socket.gem.Purity;
import dev.shadowsoffire.apotheosis.tiers.GenContext;
import dev.shadowsoffire.apotheosis.tiers.WorldTier;
import dev.shadowsoffire.apotheosis.tiers.augments.TierAugmentRegistry;
import dev.shadowsoffire.placebo.dynreg.DynamicHolder;
import io.github.matejkadlec.realmforge.compat.apotheosis.ApotheosisCompat;
import io.github.matejkadlec.realmforge.generated.GeneratedItems;
import io.github.matejkadlec.realmforge.generated.GeneratedTexts;
import io.github.matejkadlec.realmforge.spell.SpellCaster;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The bodies of {@link ApotheosisLootGameTests}, read through Apotheosis's own registries and loot code. Every
 * Apotheosis type of those tests lives here, out of the GameTest classes: the QA mod lists their declared methods when
 * it is constructed ({@link QaGameTests}), which resolves the types in their signatures, lambdas included, and the lean
 * dev client ({@code -PleanClient}) runs without Apotheosis. This class loads only when its tests run, in the GameTest
 * server, which loads the pack's mods (as {@link QuestBook} does for FTB Quests).
 */
final class ApotheosisLoot {
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
    /** The robes' affixes of ours, on every piece (RLM-557). */
    private static final Set<String> SCHOOL_AFFIXES = Set.of("realmforge:robe_fiery", "realmforge:robe_frozen",
            "realmforge:robe_arcane_touched");
    private static final Set<String> CASTER_AFFIXES = Set.of("realmforge:robe_sorcerous", "realmforge:robe_deep",
            "realmforge:robe_flowing", "realmforge:robe_warded", "realmforge:robe_fiery", "realmforge:robe_frozen",
            "realmforge:robe_arcane_touched");
    private static final String TIMEKEEPING = "apotheosis:armor/attribute/timekeeping";
    private static final String BLOCKADING = "apotheosis:armor/dmg_reduction/blockading";
    /** The armor affixes that add armor or toughness, which no robe takes (the caster rule, gear design). */
    private static final Set<String> ARMOR_AFFIXES = Set.of("apotheosis:armor/attribute/ironforged",
            "apotheosis:armor/attribute/adamantine", "apotheosis:armor/attribute/steel_touched");
    private static final List<RobePiece> ROBE_PIECES = List.of(
            new RobePiece("the robe head", GeneratedItems.NETHERITE_CAP, withCaster(TIMEKEEPING,
                    "apotheosis:armor/attribute/blessed", "apotheosis:armor/attribute/fortunate",
                    "apotheosis:armor/attribute/stalwart", "apotheosis:generic/attribute/lucky"),
                    Set.of("apotheosis:armor/dmg_reduction/deflective"), Set.of(), true),
            new RobePiece("the robe chest", GeneratedItems.NETHERITE_ROBES, withCaster(TIMEKEEPING,
                    "apotheosis:armor/attribute/blessed", "apotheosis:armor/attribute/fortunate",
                    "apotheosis:armor/attribute/gravitational", "apotheosis:armor/attribute/spiritual",
                    "apotheosis:armor/attribute/stalwart", "apotheosis:armor/attribute/unbound",
                    "apotheosis:generic/attribute/lucky"),
                    Set.of("apotheosis:armor/dmg_reduction/blast_forged", "apotheosis:armor/dmg_reduction/deflective",
                            "apotheosis:armor/mob_effect/bolstering", "apotheosis:armor/mob_effect/bursting",
                            "apotheosis:armor/mob_effect/revitalizing"),
                    Set.of(BLOCKADING), true),
            new RobePiece("the robe legs", GeneratedItems.NETHERITE_PANTS, withCaster(
                    "apotheosis:armor/attribute/blessed", "apotheosis:armor/attribute/fortunate",
                    "apotheosis:armor/attribute/spiritual", "apotheosis:armor/attribute/stalwart",
                    "apotheosis:armor/attribute/windswept", "apotheosis:generic/attribute/lucky"),
                    Set.of("apotheosis:armor/dmg_reduction/blast_forged", "apotheosis:armor/mob_effect/bolstering",
                            "apotheosis:armor/mob_effect/bursting", "apotheosis:armor/mob_effect/nimble",
                            "apotheosis:armor/mob_effect/revitalizing"),
                    Set.of(BLOCKADING), false),
            new RobePiece("the robe feet", GeneratedItems.NETHERITE_SANDALS, withCaster(
                    "apotheosis:armor/attribute/aquatic", "apotheosis:armor/attribute/blessed",
                    "apotheosis:armor/attribute/fortunate", "apotheosis:armor/attribute/stalwart",
                    "apotheosis:armor/attribute/windswept", "apotheosis:generic/attribute/lucky"),
                    Set.of("apotheosis:armor/dmg_reduction/feathery", "apotheosis:armor/mob_effect/nimble"),
                    Set.of(), false));
    private static final int ROLLS = 20;
    private static final int RARE_SWORD_ROLLS = 200;
    private static final double EPSILON = 1e-4;

    private ApotheosisLoot() {
    }

    private static Set<String> withCaster(String... kept) {
        Set<String> affixes = new HashSet<>(CASTER_AFFIXES);
        affixes.addAll(List.of(kept));
        return affixes;
    }

    /** One robe piece and what can roll on it at Legendary, by kind (the gear design's table, RLM-557). */
    private record RobePiece(String name, Supplier<? extends Item> item, Set<String> stats, Set<String> effects,
            Set<String> abilities, boolean timekeeping) {
        boolean rolls(String affix) {
            return stats.contains(affix) || effects.contains(affix) || abilities.contains(affix);
        }

        @Override
        public String toString() {
            return name;
        }
    }

    /** Done when of RLM-232: the review's affixes are off, the ones it kept are not. */
    static void theReviewsAffixesAreOff(GameTestHelper helper) {
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
    static void theReviewOpensAffixesToRare(GameTestHelper helper) {
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

    /**
     * Rare's chanced ability slot (Matěj, 2026-10-29): 25 %, so a Rare sword sometimes carries Thunderstruck, never
     * two abilities.
     */
    static void aRareSwordSometimesRollsAnAbility(GameTestHelper helper) {
        LootRarity rare = rarity("rare");
        helper.assertTrue(rare.rules().stream().anyMatch(rule -> rule instanceof LootRule.ChancedLootRule chanced
                        && Math.abs(chanced.chance() - 0.25f) < EPSILON
                        && chanced.rule() instanceof LootRule.AffixLootRule affix && affix.type() == AffixType.ABILITY),
                "Rare's rules hold a 25 % ability slot: " + rare.rules());
        GenContext context = context(helper, WorldTier.HAVEN, 232);
        int withAbility = 0;
        for (int i = 0; i < RARE_SWORD_ROLLS; i++) {
            ItemStack sword = LootController.createLootItem(new ItemStack(Items.DIAMOND_SWORD), rare, context);
            int abilities = count(sword, AffixType.ABILITY);
            helper.assertTrue(abilities <= 1, "a Rare sword has at most one ability, rolled " + abilities);
            withAbility += abilities;
        }
        // Expected 50 of 200; a fixed seed, so the band only guards against the slot missing or always firing.
        helper.assertTrue(withAbility >= 20 && withAbility <= 90,
                "about a quarter of Rare swords carry an ability: " + withAbility + " of " + RARE_SWORD_ROLLS);
        helper.succeed();
    }

    /**
     * Matěj, 2026-10-31 (RLM-232): a category skips the slots it has nothing left for. Helmets have no ability and one
     * basic effect (Runed and Blinding are off), boots no ability below Epic, shears one basic effect (Gardening and
     * Leaf-Forming are off). Every category rolls at every rarity and tier without an empty slot (the log check).
     */
    static void everyCategoryRollsWithoutAnEmptySlot(GameTestHelper helper) {
        for (WorldTier tier : new WorldTier[] {WorldTier.HAVEN, WorldTier.PINNACLE}) {
            GenContext context = context(helper, tier, 273);
            for (String id : RARITIES) {
                LootRarity rarity = rarity(id);
                for (Item item : CATEGORY_ITEMS) {
                    for (int i = 0; i < ROLLS; i++) {
                        ItemStack rolled = LootController.createLootItem(new ItemStack(item), rarity, context);
                        String what = BuiltInRegistries.ITEM.getKey(item) + " at " + id;
                        if (item == Items.DIAMOND_HELMET || (item == Items.DIAMOND_BOOTS && id.equals("rare"))) {
                            helper.assertTrue(count(rolled, AffixType.ABILITY) == 0, what + " rolls no ability");
                        }
                        if (item == Items.DIAMOND_HELMET || item == Items.SHEARS) {
                            helper.assertTrue(count(rolled, AffixType.BASIC_EFFECT) <= 1,
                                    what + " rolls at most one basic effect");
                        }
                    }
                }
            }
        }
        helper.succeed();
    }

    /** RLM-232: the world tiers no longer cap Apothic Enchanting's max Eterna; their other player augments stay. */
    static void theWorldTiersCapNoEterna(GameTestHelper helper) {
        for (String tier : new String[] {"haven", "frontier", "ascent", "summit"}) {
            helper.assertTrue(TierAugmentRegistry.INSTANCE.getValue(apotheosis(tier + "/max_eterna")) == null,
                    tier + "'s max Eterna cap is off");
        }
        helper.assertTrue(TierAugmentRegistry.INSTANCE.getValue(apotheosis("frontier/luck")) != null,
                "Frontier's luck stays");
        helper.succeed();
    }

    /** Done when of RLM-273: every wand is {@code realmforge:wand}, never Apotheosis's melee weapon. */
    static void aWandIsAWandNotAMeleeWeapon(GameTestHelper helper) {
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
     * affixes. The wand's own affixes are RLM-562's.
     */
    static void aWandNeverRollsAMeleeAffix(GameTestHelper helper) {
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

    /**
     * Done when of RLM-546: every armor piece of ours is the robe category of its slot, never Apotheosis's armor,
     * through the data map; a diamond chestplate stays a chestplate, and the tooltips name the categories by our texts.
     */
    static void aRobeIsARobeNotArmor(GameTestHelper helper) {
        LootCategory chest = ApotheosisCompat.ROBE_CHEST.get();
        helper.assertValueEqual(chest.getKey(), Identifier.fromNamespaceAndPath("realmforge", "robe_chest"), "the key");
        helper.assertTrue(LootCategory.forItem(new ItemStack(GeneratedItems.NETHERITE_ROBES.get())) == chest,
                "Netherite Robes are realmforge:robe_chest");
        Map<EquipmentSlot, LootCategory> robes = Map.of(EquipmentSlot.HEAD, ApotheosisCompat.ROBE_HEAD.get(),
                EquipmentSlot.CHEST, chest, EquipmentSlot.LEGS, ApotheosisCompat.ROBE_LEGS.get(),
                EquipmentSlot.FEET, ApotheosisCompat.ROBE_FEET.get());
        int pieces = 0;
        for (Item item : BuiltInRegistries.ITEM) {
            ItemStack stack = new ItemStack(item);
            var equippable = stack.get(DataComponents.EQUIPPABLE);
            if (BuiltInRegistries.ITEM.getKey(item).getNamespace().equals("realmforge") && equippable != null) {
                LootCategory category = LootCategory.forItem(stack);
                helper.assertTrue(category == robes.get(equippable.slot()),
                        item + " is the robe category of its slot, not " + category);
                pieces++;
            }
        }
        helper.assertValueEqual(pieces, 16, "the four robe sets' pieces");
        LootCategory diamond = LootCategory.forItem(new ItemStack(Items.DIAMOND_CHESTPLATE));
        helper.assertTrue(diamond == Apoth.LootCategories.CHESTPLATE, "a diamond chestplate is still a chestplate");
        helper.assertValueEqual(chest.getDescId(), GeneratedTexts.LOOT_CATEGORY_ROBE_CHEST, "its name's key");
        helper.assertValueEqual(chest.getDescIdPlural(), GeneratedTexts.LOOT_CATEGORY_ROBE_CHEST_PLURAL,
                "its plural's key");
        helper.succeed();
    }

    /**
     * Done when of RLM-546: a gem with armor bonuses only (Guardian) has no bonus for a robe category, so it cannot be
     * socketed into a robe until the robe gem bonuses exist (RLM-548); a chestplate still takes it.
     */
    static void aGuardianGemFitsNoRobe(GameTestHelper helper) {
        ItemStack guardian = ApotheosisGems.gem("core/guardian").toStack(Purity.PERFECT);
        ItemStack robes = new ItemStack(GeneratedItems.NETHERITE_ROBES.get());
        SocketHelper.setSockets(robes, 1);
        ItemStack chestplate = new ItemStack(Items.NETHERITE_CHESTPLATE);
        SocketHelper.setSockets(chestplate, 1);
        helper.assertFalse(SocketHelper.canSocketGemInItem(robes, guardian), "Guardian does not fit Netherite Robes");
        helper.assertTrue(SocketHelper.canSocketGemInItem(chestplate, guardian),
                "Guardian fits a netherite chestplate");
        helper.succeed();
    }

    /**
     * RLM-546: the wand has no affix to roll yet (RLM-562), and its rarity override drops every affix slot, so a wand
     * rolled (as the reforging table rolls it) at every rarity and tier gets the rarity and no affix, and Apotheosis
     * logs no "no affixes available" error (the log check).
     */
    static void aWandRollsARarityWithoutAffixes(GameTestHelper helper) {
        for (WorldTier tier : new WorldTier[] {WorldTier.HAVEN, WorldTier.PINNACLE}) {
            GenContext context = context(helper, tier, 546);
            for (String id : RARITIES) {
                LootRarity rarity = rarity(id);
                ItemStack rolled = LootController.createLootItem(new ItemStack(GeneratedItems.NETHERITE_WAND.get()),
                        rarity, context);
                helper.assertTrue(AffixHelper.getRarity(rolled).isBound()
                        && AffixHelper.getRarity(rolled).get() == rarity, "the wand at " + id + " has the rarity");
                helper.assertTrue(AffixHelper.getAffixes(rolled).isEmpty(), "the wand at " + id + " rolls no affix");
            }
        }
        helper.succeed();
    }

    /**
     * Done when of RLM-557: what can roll on each robe piece is the gear design's list, exactly: our caster affixes and
     * Apotheosis's kept armor affixes that give no armor, by the piece's slot. Fiery and its twins start at Uncommon,
     * Timekeeping at Rare and on the head and chest only, and no piece can take Ironforged, Adamantine or Steel Touched.
     */
    static void eachRobePieceOffersTheGearDesignsAffixes(GameTestHelper helper) {
        for (RobePiece piece : ROBE_PIECES) {
            ItemStack stack = new ItemStack(piece.item().get());
            helper.assertValueEqual(available(stack, "mythic", AffixType.STAT), piece.stats(),
                    piece + "'s stat affixes");
            helper.assertValueEqual(available(stack, "mythic", AffixType.BASIC_EFFECT), piece.effects(),
                    piece + "'s basic effects");
            helper.assertValueEqual(available(stack, "mythic", AffixType.ABILITY), piece.abilities(),
                    piece + "'s abilities");

            Set<String> common = available(stack, "common", AffixType.STAT);
            helper.assertTrue(common.contains("realmforge:robe_sorcerous"), piece + " rolls Sorcerous from Common");
            helper.assertFalse(common.contains("realmforge:robe_fiery"), piece + " rolls no Fiery at Common");
            helper.assertTrue(available(stack, "uncommon", AffixType.STAT).containsAll(SCHOOL_AFFIXES),
                    piece + " rolls the school affixes from Uncommon");
            helper.assertFalse(available(stack, "uncommon", AffixType.STAT).contains(TIMEKEEPING),
                    piece + " rolls no Timekeeping at Uncommon");
            helper.assertValueEqual(available(stack, "rare", AffixType.STAT).contains(TIMEKEEPING), piece.timekeeping(),
                    piece + "'s Timekeeping from Rare");
        }
        ItemStack sword = new ItemStack(Items.NETHERITE_SWORD);
        for (AffixType type : AffixType.values()) {
            helper.assertFalse(available(sword, "mythic", type).stream().anyMatch(id -> id.startsWith("realmforge:")),
                    "a sword rolls no affix of ours");
        }
        helper.succeed();
    }

    /**
     * Done when of RLM-557: a robe rolled (as the reforging table rolls it) at every rarity and tier gets the rarity,
     * a stat affix for every stat slot and nothing that adds armor, and Apotheosis logs no "no affixes available" error
     * (the log check): the head and feet skip the slots they have nothing for.
     */
    static void aRobeRollsCasterAffixesAndNoArmorAffix(GameTestHelper helper) {
        Map<String, Integer> statSlots = Map.of("common", 1, "uncommon", 2, "rare", 2, "epic", 3, "mythic", 4);
        for (WorldTier tier : new WorldTier[] {WorldTier.HAVEN, WorldTier.PINNACLE}) {
            GenContext context = context(helper, tier, 557);
            for (String id : RARITIES) {
                LootRarity rarity = rarity(id);
                for (RobePiece piece : ROBE_PIECES) {
                    for (int i = 0; i < ROLLS; i++) {
                        ItemStack rolled = LootController.createLootItem(new ItemStack(piece.item().get()), rarity,
                                context);
                        String what = piece + " at " + id;
                        helper.assertTrue(AffixHelper.getRarity(rolled).isBound()
                                && AffixHelper.getRarity(rolled).get() == rarity, what + " has the rarity");
                        helper.assertTrue(count(rolled, AffixType.STAT) >= statSlots.get(id),
                                what + " fills its stat slots: " + count(rolled, AffixType.STAT));
                        for (DynamicHolder<Affix> affix : AffixHelper.getAffixes(rolled).keySet()) {
                            String rolledId = affix.getId().toString();
                            helper.assertFalse(ARMOR_AFFIXES.contains(rolledId), what + " rolled armor: " + rolledId);
                            helper.assertTrue(piece.rolls(rolledId), what + " rolled " + rolledId + ", not on its list");
                        }
                        if (piece.abilities().isEmpty()) {
                            helper.assertValueEqual(count(rolled, AffixType.ABILITY), 0, what + "'s abilities");
                        }
                        if (piece.effects().size() == 1) {
                            helper.assertTrue(count(rolled, AffixType.BASIC_EFFECT) <= 1,
                                    what + " rolls at most one basic effect");
                        }
                    }
                }
            }
        }
        helper.succeed();
    }

    private static Set<String> available(ItemStack stack, String rarity, AffixType type) {
        return LootController.getAvailableAffixes(stack, rarity(rarity), type)
                .map(holder -> holder.getId().toString())
                .collect(Collectors.toSet());
    }

    private static int count(ItemStack stack, AffixType type) {
        return (int) AffixHelper.getAffixes(stack).keySet().stream()
                .filter(holder -> holder.get().definition().type() == type)
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
