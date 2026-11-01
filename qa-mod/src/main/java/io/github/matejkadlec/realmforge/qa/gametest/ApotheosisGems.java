package io.github.matejkadlec.realmforge.qa.gametest;

import dev.shadowsoffire.apotheosis.socket.SocketHelper;
import dev.shadowsoffire.apotheosis.socket.gem.Gem;
import dev.shadowsoffire.apotheosis.socket.gem.GemRegistry;
import dev.shadowsoffire.apotheosis.socket.gem.Purity;
import io.github.matejkadlec.realmforge.generated.GeneratedSpellSchools;
import io.github.matejkadlec.realmforge.generated.GeneratedStats;
import io.github.matejkadlec.realmforge.qa.command.StatsCommands;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The bodies of {@link ApotheosisGemGameTests}: gems socketed into a player's chestplate against our school hits
 * (RLM-549). Every Apotheosis type lives here, out of the GameTest class, as {@link ApotheosisLoot} explains.
 */
final class ApotheosisGems {
    /** Combatant's chest and legs bonus at Perfect: 27.5 % less physical damage (Apotheosis 9.1.0). */
    private static final double COMBATANT_PERFECT = 0.275;
    /** Verdant Ruin's chestplate bonus at Perfect: 20 % less magic damage (Apotheosis 9.1.0). */
    private static final double VERDANT_RUIN_PERFECT = 0.2;
    private static final float HIT = 4;

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

        helper.assertTrue(plain > 0, "the physical hit lands: " + plain);
        helper.assertTrue(CombatGameTests.close(withGem, plain * (1 - COMBATANT_PERFECT)),
                "Combatant takes " + COMBATANT_PERFECT + " off the physical hit of " + plain + ", dealt " + withGem);
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
        ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
        ItemStack stack = gem(gem).toStack(Purity.PERFECT);
        helper.assertTrue(SocketHelper.canSocketGemInItem(chest, stack), gem + " fits the chestplate");
        player.setItemSlot(EquipmentSlot.CHEST, SocketHelper.socketGemInItem(chest, stack));
        ApotheosisGameTests.ticks(player, 2);
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
