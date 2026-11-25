package io.github.matejkadlec.realmforge.qa.gametest;

import dev.shadowsoffire.apotheosis.Apoth;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import dev.shadowsoffire.apotheosis.socket.SocketHelper;
import dev.shadowsoffire.apotheosis.socket.gem.ExtraGemBonusRegistry;
import dev.shadowsoffire.apotheosis.socket.gem.Gem;
import dev.shadowsoffire.apotheosis.socket.gem.GemClass;
import dev.shadowsoffire.apotheosis.socket.gem.GemRegistry;
import dev.shadowsoffire.apotheosis.socket.gem.Purity;
import dev.shadowsoffire.apotheosis.socket.gem.bonus.GemBonus;
import io.github.matejkadlec.realmforge.generated.GeneratedItems;
import io.github.matejkadlec.realmforge.generated.GeneratedSpellSchools;
import io.github.matejkadlec.realmforge.generated.GeneratedStats;
import io.github.matejkadlec.realmforge.qa.command.StatsCommands;
import java.util.List;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.locale.Language;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The bodies of {@link ApotheosisGemGameTests}: gems socketed into a player's chestplate against our school hits
 * (RLM-549), and our extra gem bonuses in wands, robes and helmets (RLM-547, RLM-548). Every Apotheosis type lives
 * here, out of the GameTest class, as {@link ApotheosisLoot} explains.
 */
final class ApotheosisGems {
    /** Combatant's chest and legs bonus at Perfect: 27.5 % less physical damage (Apotheosis 9.1.0). */
    private static final double COMBATANT_PERFECT = 0.275;
    /** Verdant Ruin's chestplate bonus at Perfect: 20 % less magic damage (Apotheosis 9.1.0). */
    private static final double VERDANT_RUIN_PERFECT = 0.2;
    private static final float HIT = 4;
    /** Our extra gem bonuses at Perfect (content/apotheosis_gem_bonuses/, RLM-547, RLM-548). */
    private static final double BALLAST_WAND_PERFECT = 10;
    private static final double SAMURAI_PERFECT = 0.5;
    private static final double GUARDIAN_ROBE_PERFECT = 8;
    private static final double WARLORD_ROBE_HEAD_PERFECT = 0.225;
    private static final double SLIPSTREAM_HEAD_PERFECT = 0.075;
    /** Royalty's all-stats at Perfect: every listed stat × 1.15 (Apotheosis 9.1.0, add_multiplied_total). */
    private static final double ROYALTY_PERFECT = 0.15;

    private ApotheosisGems() {
    }

    /**
     * Done when of RLM-549: {@code realmforge:physical} is in {@code #neoforge:is_physical}, so a Combatant gem in the
     * chestplate lowers a physical school hit (Slam, a mob's physical spell) by its share.
     */
    static void aCombatantGemLowersAPhysicalSchoolHit(GameTestHelper helper) {
        ServerPlayer player = wearer(helper, "qa-apoth-combatant");
        Zombie caster = CombatGameTests.attacker(helper);
        DamageSource physical = CombatGameTests.school(helper, GeneratedSpellSchools.PHYSICAL, caster);
        double plain = dealt(player, physical);
        socket(helper, player, "core/combatant");
        double withGem = dealt(player, physical);

        // Apotheosis takes the gem's share off the incoming hit, before armor: the iron chestplate's armor then blocks
        // a smaller hit, by vanilla's formula, which ours follows (architecture 9.1).
        double armor = player.getArmorValue();
        double toughness = player.getAttributeValue(Attributes.ARMOR_TOUGHNESS);
        double reduced = HIT * (1 - COMBATANT_PERFECT);
        double plainExpected = HIT * (1 - CombatGameTests.blocked(player, HIT, armor, toughness));
        helper.assertTrue(CombatGameTests.close(plain, plainExpected),
                "the plain chestplate's armor alone lowers the hit of " + HIT + ", dealt " + plain);
        double gemExpected = reduced * (1 - CombatGameTests.blocked(player, reduced, armor, toughness));
        helper.assertTrue(CombatGameTests.close(withGem, gemExpected),
                "Combatant takes " + COMBATANT_PERFECT + " off the physical hit before armor, dealt " + withGem);
        ApotheosisGameTests.logout(player);
        helper.succeed();
    }

    /**
     * Done when of RLM-549: our spell schools stay out of {@code #neoforge:is_magic} (Matěj, 2026-10-30, G4), so a
     * Verdant Ruin gem in the chestplate leaves a frost hit as it was, while it still lowers vanilla's magic damage.
     */
    static void aVerdantRuinGemLeavesAFrostHitAlone(GameTestHelper helper) {
        ServerPlayer player = wearer(helper, "qa-apoth-verdant-ruin");
        Zombie caster = CombatGameTests.attacker(helper);
        DamageSource frost = CombatGameTests.school(helper, GeneratedSpellSchools.FROST, caster);
        DamageSource magic = helper.getLevel().damageSources().magic();
        double plainFrost = dealt(player, frost);
        double plainMagic = dealt(player, magic);
        socket(helper, player, "overworld/verdant_ruin");
        double gemFrost = dealt(player, frost);
        double gemMagic = dealt(player, magic);

        helper.assertTrue(plainFrost > 0, "the frost hit lands: " + plainFrost);
        helper.assertTrue(CombatGameTests.close(gemFrost, plainFrost),
                "Verdant Ruin leaves the frost hit of " + plainFrost + " alone, dealt " + gemFrost);
        helper.assertTrue(CombatGameTests.close(gemMagic, plainMagic * (1 - VERDANT_RUIN_PERFECT)),
                "Verdant Ruin takes " + VERDANT_RUIN_PERFECT + " off vanilla's magic hit of " + plainMagic
                        + ", dealt " + gemMagic);
        ApotheosisGameTests.logout(player);
        helper.succeed();
    }

    /**
     * Done when of RLM-547: a Perfect Ballast in a wand adds its flat 10 Spell Power, held in the main hand, and no
     * Attack Damage, which Ballast gives a melee weapon.
     */
    static void aBallastInAWandGivesSpellPowerAndNoAttackDamage(GameTestHelper helper) {
        ServerPlayer player = ApotheosisGameTests.player(helper, "qa-apoth-ballast-wand");
        hold(player, new ItemStack(GeneratedItems.IRON_WAND.get()));
        double power = player.getAttributeValue(GeneratedStats.SPELL_POWER);
        double damage = player.getAttributeValue(Attributes.ATTACK_DAMAGE);
        socket(helper, player, EquipmentSlot.MAINHAND, "core/ballast");
        helper.assertTrue(CombatGameTests.close(player.getAttributeValue(GeneratedStats.SPELL_POWER) - power,
                BALLAST_WAND_PERFECT), "Ballast adds " + BALLAST_WAND_PERFECT + " Spell Power to " + power + ": "
                        + player.getAttributeValue(GeneratedStats.SPELL_POWER));
        helper.assertTrue(CombatGameTests.close(player.getAttributeValue(Attributes.ATTACK_DAMAGE), damage),
                "and no Attack Damage: " + damage + " before, " + player.getAttributeValue(Attributes.ATTACK_DAMAGE));
        ApotheosisGameTests.logout(player);
        helper.succeed();
    }

    /**
     * Done when of RLM-547: Earth and Guardian have no wand bonus (Matěj, 2026-10-30), so neither fits a wand, while a
     * Ballast does; and Guardian still fits the chestplate it was made for.
     */
    static void aWandRefusesAnEarthGemAndAGuardian(GameTestHelper helper) {
        ItemStack wand = new ItemStack(GeneratedItems.IRON_WAND.get());
        SocketHelper.setSockets(wand, 1);
        helper.assertFalse(SocketHelper.canSocketGemInItem(wand, gem("overworld/earth").toStack(Purity.PERFECT)),
                "an Earth gem fits a wand");
        helper.assertFalse(SocketHelper.canSocketGemInItem(wand, gem("core/guardian").toStack(Purity.PERFECT)),
                "a Guardian fits a wand");
        helper.assertTrue(SocketHelper.canSocketGemInItem(wand, gem("core/ballast").toStack(Purity.PERFECT)),
                "a Ballast fits a wand");
        ItemStack chest = new ItemStack(Items.IRON_CHESTPLATE);
        SocketHelper.setSockets(chest, 1);
        helper.assertTrue(SocketHelper.canSocketGemInItem(chest, gem("core/guardian").toStack(Purity.PERFECT)),
                "a Guardian fits a chestplate");
        helper.succeed();
    }

    /** Done when of RLM-547: a Perfect Samurai in a wand raises our Crit Chance by its weapons' 50 %. */
    static void aSamuraiInAWandRaisesOurCritChance(GameTestHelper helper) {
        ServerPlayer player = ApotheosisGameTests.player(helper, "qa-apoth-samurai-wand");
        hold(player, new ItemStack(GeneratedItems.IRON_WAND.get()));
        double chance = player.getAttributeValue(GeneratedStats.CRIT_CHANCE);
        socket(helper, player, EquipmentSlot.MAINHAND, "core/samurai");
        helper.assertTrue(CombatGameTests.close(player.getAttributeValue(GeneratedStats.CRIT_CHANCE) - chance,
                SAMURAI_PERFECT), "Samurai adds " + SAMURAI_PERFECT + " to our crit chance of " + chance + ": "
                        + player.getAttributeValue(GeneratedStats.CRIT_CHANCE));
        ApotheosisGameTests.logout(player);
        helper.succeed();
    }

    /**
     * Done when of RLM-548: a Perfect Guardian in the robe chest gives its armor's 8 points as Magic Shield, and no
     * Armor, which robes never give.
     */
    static void aGuardianInARobeChestGivesMagicShieldAndNoArmor(GameTestHelper helper) {
        ServerPlayer player = ApotheosisGameTests.player(helper, "qa-apoth-guardian-robe");
        wear(player, EquipmentSlot.CHEST, new ItemStack(GeneratedItems.NETHERITE_ROBES.get()));
        double shield = player.getAttributeValue(GeneratedStats.MAGIC_SHIELD);
        double armor = player.getAttributeValue(Attributes.ARMOR);
        socket(helper, player, EquipmentSlot.CHEST, "core/guardian");
        helper.assertTrue(CombatGameTests.close(player.getAttributeValue(GeneratedStats.MAGIC_SHIELD) - shield,
                GUARDIAN_ROBE_PERFECT), "Guardian adds " + GUARDIAN_ROBE_PERFECT + " Magic Shield to " + shield + ": "
                        + player.getAttributeValue(GeneratedStats.MAGIC_SHIELD));
        helper.assertTrue(CombatGameTests.close(player.getAttributeValue(Attributes.ARMOR), armor),
                "and no Armor: " + armor + " before, " + player.getAttributeValue(Attributes.ARMOR));
        ApotheosisGameTests.logout(player);
        helper.succeed();
    }

    /** Done when of RLM-548: a Perfect Warlord in the robe head raises Spell Power by 22.5 % of its points. */
    static void aWarlordInARobeHeadGivesSpellPowerPercent(GameTestHelper helper) {
        ServerPlayer player = ApotheosisGameTests.player(helper, "qa-apoth-warlord-robe");
        StatsCommands.setBase(player, GeneratedStats.SPELL_POWER, 10);
        wear(player, EquipmentSlot.HEAD, new ItemStack(GeneratedItems.NETHERITE_CAP.get()));
        double power = player.getAttributeValue(GeneratedStats.SPELL_POWER);
        socket(helper, player, EquipmentSlot.HEAD, "core/warlord");
        double raised = player.getAttributeValue(GeneratedStats.SPELL_POWER);
        helper.assertTrue(power > 10 && CombatGameTests.close(raised, power * (1 + WARLORD_ROBE_HEAD_PERFECT)),
                "Warlord raises Spell Power " + power + " by " + WARLORD_ROBE_HEAD_PERFECT + ": " + raised);
        ApotheosisGameTests.logout(player);
        helper.succeed();
    }

    /**
     * Done when of RLM-548: a Perfect Slipstream in a helmet gives half its boots' Dodge Chance, 7.5 %, and so does one
     * in the robe head, a caster's second Dodge slot.
     */
    static void aSlipstreamInAHelmetGivesDodgeChance(GameTestHelper helper) {
        ServerPlayer player = ApotheosisGameTests.player(helper, "qa-apoth-slipstream-helmet");
        for (ItemStack head : List.of(new ItemStack(Items.IRON_HELMET),
                new ItemStack(GeneratedItems.NETHERITE_CAP.get()))) {
            wear(player, EquipmentSlot.HEAD, head);
            double dodge = player.getAttributeValue(GeneratedStats.DODGE_CHANCE);
            socket(helper, player, EquipmentSlot.HEAD, "core/slipstream");
            helper.assertTrue(CombatGameTests.close(player.getAttributeValue(GeneratedStats.DODGE_CHANCE) - dodge,
                    SLIPSTREAM_HEAD_PERFECT), "Slipstream in " + head.getHoverName().getString() + " adds "
                            + SLIPSTREAM_HEAD_PERFECT + " to " + dodge + ": "
                            + player.getAttributeValue(GeneratedStats.DODGE_CHANCE));
        }
        ApotheosisGameTests.logout(player);
        helper.succeed();
    }

    /**
     * Done when of RLM-548: Royalty's all-stats raise our Spell Power too, by 15 % of its total at Perfect, in a
     * helmet and in the robe head alike.
     */
    static void royaltysAllStatsRaiseSpellPower(GameTestHelper helper) {
        ServerPlayer player = ApotheosisGameTests.player(helper, "qa-apoth-royalty");
        StatsCommands.setBase(player, GeneratedStats.SPELL_POWER, 10);
        for (ItemStack head : List.of(new ItemStack(Items.IRON_HELMET),
                new ItemStack(GeneratedItems.NETHERITE_CAP.get()))) {
            wear(player, EquipmentSlot.HEAD, head);
            double power = player.getAttributeValue(GeneratedStats.SPELL_POWER);
            socket(helper, player, EquipmentSlot.HEAD, "overworld/royalty");
            double raised = player.getAttributeValue(GeneratedStats.SPELL_POWER);
            helper.assertTrue(CombatGameTests.close(raised, power * (1 + ROYALTY_PERFECT)), "Royalty in "
                    + head.getHoverName().getString() + " raises Spell Power " + power + " by " + ROYALTY_PERFECT
                    + ": " + raised);
        }
        ApotheosisGameTests.logout(player);
        helper.succeed();
    }

    /**
     * The deferred playtest's commands (RLM-673) work as written: {@code /give} with the gem's two components makes a
     * Perfect Ballast, and {@code /apoth set_sockets 1} gives the held wand a socket.
     */
    static void thePlaytestsCommandsGiveAPerfectGemAndASocket(GameTestHelper helper) {
        ServerPlayer player = ApotheosisGameTests.player(helper, "qa-apoth-give-gem");
        player.getInventory().clearContent();
        ApotheosisGameTests.run(player,
                "give @s apotheosis:gem[apotheosis:gem=\"apotheosis:core/ballast\",apotheosis:purity=\"perfect\"]");
        ItemStack given = player.getInventory().getItem(0);
        helper.assertTrue(given.is(Apoth.Items.GEM.value()), "a gem was given: " + given);
        helper.assertValueEqual(given.get(Apoth.Components.GEM).getId().toString(), "apotheosis:core/ballast",
                "the gem");
        helper.assertValueEqual(given.get(Apoth.Components.PURITY), Purity.PERFECT, "its purity");
        player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(GeneratedItems.IRON_WAND.get()));
        ApotheosisGameTests.run(player, "apoth set_sockets 1");
        helper.assertValueEqual(SocketHelper.getSockets(player.getMainHandItem()), 1, "the wand's sockets");
        helper.assertTrue(SocketHelper.canSocketGemInItem(player.getMainHandItem(), given),
                "the given Ballast fits the wand");
        ApotheosisGameTests.logout(player);
        helper.succeed();
    }

    /**
     * RLM-547: Apotheosis syncs each extra gem bonus to a client of a dedicated server through its codec
     * ({@code RegistrySerializer.synced}), which writes a one-category gem class as the category alone. Every loaded
     * bonus's gem class comes back from that round trip with the key it has on the server, and the game's language
     * names {@code gem_class.<key>}, so a gem's tooltip reads "Wands", never a raw key, on either side.
     */
    static void extraBonusGemClassesKeepTheirNamesThroughTheSync(GameTestHelper helper) {
        RegistryOps<JsonElement> ops = helper.getLevel().registryAccess().createSerializationContext(JsonOps.INSTANCE);
        int checked = 0;
        for (ExtraGemBonusRegistry.ExtraGemBonus extra : ExtraGemBonusRegistry.INSTANCE.getValues()) {
            for (GemBonus bonus : extra.bonuses()) {
                GemClass sent = bonus.getGemClass();
                GemClass received = GemClass.CODEC.parse(ops, GemClass.CODEC.encodeStart(ops, sent).getOrThrow())
                        .getOrThrow();
                String where = extra.gem().getId() + "'s bonus for " + sent.key();
                helper.assertValueEqual(received.key(), sent.key(), where + ": the key after the sync");
                helper.assertTrue(Language.getInstance().has("gem_class." + received.key()),
                        where + ": gem_class." + received.key() + " has a name");
                checked++;
            }
        }
        helper.assertTrue(checked > 0, "the extra gem bonuses loaded");
        helper.succeed();
    }

    /** Puts {@code stack}, with one empty socket, in the player's main hand. */
    private static void hold(ServerPlayer player, ItemStack stack) {
        wear(player, EquipmentSlot.MAINHAND, stack);
    }

    /** Puts {@code stack}, with one empty socket, into the player's {@code slot}. */
    private static void wear(ServerPlayer player, EquipmentSlot slot, ItemStack stack) {
        SocketHelper.setSockets(stack, 1);
        player.setItemSlot(slot, stack);
        ApotheosisGameTests.ticks(player, 2);
    }

    /** Sockets a Perfect {@code apotheosis:<gem>} into the item in the player's {@code slot}. */
    private static void socket(GameTestHelper helper, ServerPlayer player, EquipmentSlot slot, String gem) {
        ItemStack item = player.getItemBySlot(slot);
        ItemStack stack = gem(gem).toStack(Purity.PERFECT);
        helper.assertTrue(SocketHelper.canSocketGemInItem(item, stack),
                gem + " fits " + item.getHoverName().getString());
        player.setItemSlot(slot, SocketHelper.socketGemInItem(item, stack));
        ApotheosisGameTests.ticks(player, 2);
    }

    /** A survival player who never dodges, in an iron chestplate with one empty socket. */
    private static ServerPlayer wearer(GameTestHelper helper, String name) {
        ServerPlayer player = ApotheosisGameTests.player(helper, name);
        StatsCommands.setBase(player, GeneratedStats.DODGE_CHANCE, 0);
        ItemStack chest = new ItemStack(Items.IRON_CHESTPLATE);
        SocketHelper.setSockets(chest, 1);
        player.setItemSlot(EquipmentSlot.CHEST, chest);
        ApotheosisGameTests.ticks(player, 2);
        return player;
    }

    /** Sockets a Perfect {@code apotheosis:<gem>} into the player's chestplate. */
    private static void socket(GameTestHelper helper, ServerPlayer player, String gem) {
        socket(helper, player, EquipmentSlot.CHEST, gem);
    }

    /** What a {@value #HIT} hit of {@code source} takes off the player at full health, out of the hurt cooldown. */
    private static double dealt(ServerPlayer player, DamageSource source) {
        player.setHealth(player.getMaxHealth());
        CombatGameTests.settle(player);
        float before = player.getHealth();
        player.hurtServer(player.level(), source, HIT);
        return before - player.getHealth();
    }

    static Gem gem(String path) {
        Gem gem = GemRegistry.INSTANCE.getValue(Identifier.fromNamespaceAndPath("apotheosis", path));
        if (gem == null) {
            throw new IllegalStateException("Apotheosis has no gem apotheosis:" + path);
        }
        return gem;
    }
}
