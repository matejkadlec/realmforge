package io.github.matejkadlec.realmforge.qa.gametest;

import dev.shadowsoffire.apotheosis.Apoth;
import dev.shadowsoffire.apotheosis.affix.Affix;
import dev.shadowsoffire.apotheosis.affix.AffixHelper;
import dev.shadowsoffire.apotheosis.affix.AffixRegistry;
import dev.shadowsoffire.apotheosis.affix.ItemAffixes;
import dev.shadowsoffire.apotheosis.affix.effect.MobEffectAffix;
import dev.shadowsoffire.apotheosis.loot.LootCategory;
import dev.shadowsoffire.apotheosis.socket.SocketHelper;
import dev.shadowsoffire.apotheosis.socket.gem.Gem;
import dev.shadowsoffire.apotheosis.socket.gem.GemRegistry;
import dev.shadowsoffire.apotheosis.socket.gem.Purity;
import dev.shadowsoffire.apotheosis.socket.gem.bonus.GemBonus;
import dev.shadowsoffire.apotheosis.socket.gem.bonus.MobEffectBonus;
import dev.shadowsoffire.placebo.dynreg.DynamicHolder;
import io.github.matejkadlec.realmforge.generated.GeneratedEffects;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * The bodies of {@link ApotheosisBleedGameTests}: RLM-600 takes Apothic Attributes' Bleeding out of the pack (Matěj,
 * 2026-11-03), read through Apotheosis's own registries as the game loads them. Every Apotheosis type lives here, out
 * of the GameTest class, as {@link ApotheosisLoot} explains.
 */
final class ApotheosisBleeds {
    private static final Identifier APOTHIC_BLEEDING = Identifier.parse("apothic_attributes:bleeding");
    private static final Identifier BLOODLETTING = Identifier.parse("apotheosis:melee/mob_effect/bloodletting");
    private static final Identifier DEVILISH = Identifier.parse("apotheosis:shield/mob_effect/devilish");
    /** The playtest handoff's command for a Perfect Tyrannical gem. */
    private static final String GIVE_PERFECT_TYRANNICAL =
            "give @s apotheosis:gem[apotheosis:gem=\"apotheosis:core/tyrannical\",apotheosis:purity=\"perfect\"]";
    private static final int REFORGES = 20;

    private ApotheosisBleeds() {
    }

    /**
     * Done when of RLM-600: Bloodletting and Devilish are off, so they are in no affix pool, and no affix or gem bonus
     * left in the pack gives Apothic Attributes' Bleeding; the other effect affixes and bonuses still load.
     */
    static void noAffixOrGemGivesApothicBleeding(GameTestHelper helper) {
        helper.assertTrue(AffixRegistry.INSTANCE.getValue(BLOODLETTING) == null, "Bloodletting is off");
        helper.assertTrue(AffixRegistry.INSTANCE.getValue(DEVILISH) == null, "Devilish is off");
        int effectAffixes = 0;
        for (Identifier id : AffixRegistry.INSTANCE.getKeys()) {
            Affix affix = AffixRegistry.INSTANCE.getValue(id);
            if (affix instanceof MobEffectAffix) {
                effectAffixes++;
                helper.assertFalse(effect(MobEffectAffix.class, affix).is(APOTHIC_BLEEDING),
                        id + " gives Apothic Attributes' Bleeding");
            }
        }
        helper.assertTrue(effectAffixes > 0, "effect affixes are still in the pack, so the check saw some");
        int effectBonuses = 0;
        for (Identifier id : GemRegistry.INSTANCE.getKeys()) {
            Gem gem = GemRegistry.INSTANCE.getValue(id);
            for (LootCategory category : Apoth.BuiltInRegs.LOOT_CATEGORY) {
                GemBonus bonus = gem.getBonus(category);
                if (bonus instanceof MobEffectBonus) {
                    effectBonuses++;
                    helper.assertFalse(effect(MobEffectBonus.class, bonus).is(APOTHIC_BLEEDING),
                            id + "'s bonus for " + Apoth.BuiltInRegs.LOOT_CATEGORY.getKey(category)
                                    + " gives Apothic Attributes' Bleeding");
                }
            }
        }
        helper.assertTrue(effectBonuses > 0, "effect gem bonuses are still in the pack, so the check saw some");
        helper.succeed();
    }

    /**
     * Done when of RLM-600: the Tyrannical gem lost its only bow bonus, the Bleeding on an arrow's target at Flawless
     * and Perfect, so at either purity it fits no bow and no arrow of one bleeds; its other bonuses stay (a melee
     * weapon's at Perfect).
     */
    static void aTyrannicalGemGivesABowNoBleeding(GameTestHelper helper) {
        Gem tyrannical = ApotheosisGems.gem("core/tyrannical");
        // The playtest handoff's command gives the Perfect gem.
        ServerPlayer player = ApotheosisGameTests.player(helper, "qa-apoth-tyrannical");
        ApotheosisGameTests.run(player, GIVE_PERFECT_TYRANNICAL);
        helper.assertTrue(ItemStack.isSameItemSameComponents(player.getInventory().getItem(0),
                tyrannical.toStack(Purity.PERFECT)), "the handoff's /give gives a Perfect Tyrannical gem: "
                        + player.getInventory().getItem(0).getComponents());
        ApotheosisGameTests.logout(player);
        ItemStack bow = new ItemStack(Items.BOW);
        SocketHelper.setSockets(bow, 1);
        LootCategory bows = LootCategory.forItem(bow);
        for (Purity purity : List.of(Purity.FLAWLESS, Purity.PERFECT)) {
            helper.assertTrue(tyrannical.getBonus(bows, purity).isEmpty(), "no bow bonus at " + purity);
            helper.assertFalse(SocketHelper.canSocketGemInItem(bow, tyrannical.toStack(purity)),
                    "a " + purity + " Tyrannical gem fits no bow");
        }
        ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
        SocketHelper.setSockets(sword, 1);
        helper.assertTrue(tyrannical.getBonus(LootCategory.forItem(sword), Purity.PERFECT).isPresent(),
                "its melee bonus stays");
        helper.assertTrue(SocketHelper.canSocketGemInItem(sword, tyrannical.toStack(Purity.PERFECT)),
                "a Perfect Tyrannical gem still fits a sword");
        helper.succeed();
    }

    /**
     * Done when of RLM-600, as the playtest reaches it: {@code /apoth reforge apotheosis:mythic} on a held sword and
     * shield rolls Legendary affixes every time, and never Bloodletting or Devilish (a shield's two basic effects are
     * Reinforcing and Venomous now).
     */
    static void reforgingRollsNoBleedAffix(GameTestHelper helper) {
        ServerPlayer player = ApotheosisGameTests.player(helper, "qa-apoth-reforge-bleed");
        for (Item item : List.of(Items.DIAMOND_SWORD, Items.SHIELD)) {
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(item));
            for (int i = 0; i < REFORGES; i++) {
                ApotheosisGameTests.run(player, "apoth reforge apotheosis:mythic");
                Set<DynamicHolder<Affix>> affixes = AffixHelper.getAffixes(player.getMainHandItem()).keySet();
                helper.assertFalse(affixes.isEmpty(), item + " reforged with affixes");
                helper.assertTrue(affixes.stream().noneMatch(affix -> affix.is(BLOODLETTING) || affix.is(DEVILISH)),
                        item + " rolled no Bloodletting or Devilish: " + affixes);
            }
        }
        ApotheosisGameTests.logout(player);
        helper.succeed();
    }

    /**
     * Done when of RLM-600: a sword that rolled Bloodletting before it was off (Matěj's test sword) keeps the entry in
     * its affixes, which a world's save holds and loads without an error, but Apotheosis skips an affix its registry no
     * longer has ({@code AffixHelper#getAffixesImpl}), so the sword shows and does nothing of it: its hit gives no
     * bleed, neither Apothic Attributes' nor ours.
     */
    static void aSwordThatRolledBloodlettingLoadsAndBleedsNothing(GameTestHelper helper) {
        ServerPlayer player = QaPlayers.loginOnPlatform(helper, "qa-apoth-old-bloodletting", new Vec3(2.5, 1, 1.5));
        player.setGameMode(GameType.SURVIVAL);
        player.connection.markClientLoaded();
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_SWORD));
        ApotheosisGameTests.run(player, "apoth set_rarity apotheosis:uncommon");
        ItemStack sword = player.getMainHandItem();
        DynamicHolder<Affix> bloodletting = AffixRegistry.INSTANCE.holder(BLOODLETTING);
        helper.assertFalse(bloodletting.isBound(), "Bloodletting is off");
        sword.set(Apoth.Components.AFFIXES, ItemAffixes.EMPTY.toBuilder().put(bloodletting, 1).build());

        RegistryOps<Tag> ops = helper.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE);
        Tag saved = ItemStack.CODEC.encodeStart(ops, sword).getOrThrow();
        ItemStack loaded = ItemStack.CODEC.parse(ops, saved).getOrThrow();
        helper.assertTrue(loaded.getOrDefault(Apoth.Components.AFFIXES, ItemAffixes.EMPTY).keySet()
                .contains(bloodletting), "the loaded sword still names Bloodletting in its affixes");
        helper.assertTrue(AffixHelper.getRarity(loaded).isBound(), "and keeps its rarity");
        helper.assertTrue(AffixHelper.getAffixes(loaded).isEmpty(), "Apotheosis reads no affix from it");

        player.setItemInHand(InteractionHand.MAIN_HAND, loaded);
        Husk husk = SpellGameTests.husk(helper, new BlockPos(2, 1, 3));
        ApotheosisGameTests.ticks(player, 20);
        CombatGameTests.settle(husk);
        float before = husk.getHealth();
        player.attack(husk);
        helper.assertTrue(husk.getHealth() < before, "the sword's hit landed");
        helper.assertTrue(husk.getActiveEffects().stream().noneMatch(effect -> effect.getEffect().is(APOTHIC_BLEEDING)),
                "no Apothic Attributes' Bleeding on the husk");
        helper.assertFalse(husk.hasEffect(GeneratedEffects.BLEEDING), "nor Bleeding of ours");
        ApotheosisGameTests.logout(player);
        helper.succeed();
    }

    /** The status effect a mob effect affix or gem bonus gives: Apotheosis keeps it in a protected field. */
    @SuppressWarnings("unchecked")
    private static Holder<MobEffect> effect(Class<?> owner, Object instance) {
        try {
            Field field = owner.getDeclaredField("effect");
            field.setAccessible(true);
            return (Holder<MobEffect>) field.get(instance);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(owner.getSimpleName() + " no longer has its effect field", e);
        }
    }
}
