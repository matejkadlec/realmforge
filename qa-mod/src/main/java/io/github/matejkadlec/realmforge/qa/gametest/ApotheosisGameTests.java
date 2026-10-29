package io.github.matejkadlec.realmforge.qa.gametest;

import io.github.matejkadlec.realmforge.combat.MobCombatStats;
import io.github.matejkadlec.realmforge.generated.GeneratedStats;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;
import net.minecraft.world.entity.monster.illager.Pillager;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.GameType;

/**
 * RLM-272: Apotheosis's data, fitted to our stats ({@code content/apotheosis_stat_remaps/}, architecture 19.4), as the
 * game loads it with the pack (ADR 0042). The tests use Apotheosis's own commands ({@code /apoth set_rarity},
 * {@code /apoth affix apply}, {@code /apoth set_world_tier}) and look its attributes, components and recipes up by
 * id, so the QA mod needs no compile-time dependency on it.
 */
public final class ApotheosisGameTests {
    private static final Identifier APOTHIC_CRIT_CHANCE = Identifier.parse("apothic_attributes:crit_chance");
    private static final Identifier APOTHIC_ARMOR_PIERCE = Identifier.parse("apothic_attributes:armor_pierce");
    private static final Identifier APOTHIC_PROT_PIERCE = Identifier.parse("apothic_attributes:prot_pierce");
    private static final Identifier AFFIXES_COMPONENT = Identifier.parse("apotheosis:affixes");
    /** Intricate (crit chance) at Rare rolls 10 % to 25 %; level 1.0 is the top of that range. */
    private static final double INTRICATE_RARE_MAX = 0.25;
    /** The monsters' Summit tier augment's armor pierce (Apotheosis 9.1.0's {@code summit/armor_pierce}). */
    private static final double SUMMIT_ARMOR_PIERCE = 15;
    private static final double EPSILON = 1e-4;

    private ApotheosisGameTests() {
    }

    /** Done when of RLM-272: an affix that granted Apothic Attributes' crit chance grants ours instead. */
    @QaGameTest
    public static void anIntricateSwordGrantsOurCritChance(GameTestHelper helper) {
        ServerPlayer player = player(helper, "qa-apoth-intricate");
        double before = player.getAttributeValue(GeneratedStats.CRIT_CHANCE);
        ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
        player.setItemInHand(InteractionHand.MAIN_HAND, sword);
        run(player, "apoth set_rarity apotheosis:rare");
        run(player, "apoth affix apply apotheosis:melee/attribute/intricate 1.0");
        helper.assertTrue(player.getMainHandItem().has(affixesComponent()), "the sword carries an affix");
        ticks(player, 2);

        double gained = player.getAttributeValue(GeneratedStats.CRIT_CHANCE) - before;
        helper.assertTrue(Math.abs(gained - INTRICATE_RARE_MAX) < EPSILON,
                "Intricate adds " + INTRICATE_RARE_MAX + " to our crit chance, added " + gained);
        AttributeInstance apothic = player.getAttribute(attribute(APOTHIC_CRIT_CHANCE));
        helper.assertTrue(apothic == null || apothic.getModifiers().isEmpty(),
                "nothing raises Apothic Attributes' crit chance: " + (apothic == null ? "" : apothic.getModifiers()));
        logout(player);
        helper.succeed();
    }

    /** RLM-272: protection pierce and shred are switched off, so Runebreaking no longer exists to apply. */
    @QaGameTest
    public static void aSwitchedOffAffixCannotBeApplied(GameTestHelper helper) {
        ServerPlayer player = player(helper, "qa-apoth-runebreaking");
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_SWORD));
        run(player, "apoth set_rarity apotheosis:rare");
        run(player, "apoth affix apply apotheosis:weapon/attribute/runebreaking 1.0");
        helper.assertFalse(player.getMainHandItem().has(affixesComponent()), "Runebreaking is unknown to Apotheosis");
        run(player, "apoth affix apply apotheosis:melee/attribute/piercing 1.0");
        helper.assertTrue(player.getMainHandItem().has(affixesComponent()), "Piercing still applies to the sword");
        logout(player);
        helper.succeed();
    }

    /**
     * Done when of RLM-272: the monsters' tier augment that granted Apothic Attributes' armor pierce grants ours, which
     * every mob has (Matěj, 2026-10-29); protection pierce's augment is switched off.
     */
    @QaGameTest
    public static void aSummitMonsterGetsOurArmorPierce(GameTestHelper helper) {
        ServerPlayer player = player(helper, "qa-apoth-summit");
        run(player, "apoth set_world_tier @s summit");
        // Next to the player, so it is the nearest one, whose tier Apotheosis gives the mob as it joins the level.
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(2, 1, 3));
        zombie.setNoAi(true);

        helper.assertValueEqual(zombie.getAttributeValue(GeneratedStats.ARMOR_PIERCE), SUMMIT_ARMOR_PIERCE,
                "the zombie's armor pierce from the Summit augment");
        assertUnraised(helper, zombie, APOTHIC_ARMOR_PIERCE);
        assertUnraised(helper, zombie, APOTHIC_PROT_PIERCE);
        logout(player);
        helper.succeed();
    }

    /** Matěj, 2026-10-29 (RLM-272): every mob has our armor pierce, armor shred and lifesteal, at 0. */
    @QaGameTest
    @SuppressWarnings("unchecked")
    public static void everyMobHasOurCombatStats(GameTestHelper helper) {
        int types = 0;
        for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
            if (!DefaultAttributes.hasSupplier(type)) {
                continue;
            }
            AttributeSupplier supplier = DefaultAttributes.getSupplier((EntityType<? extends LivingEntity>) type);
            for (Holder<Attribute> stat : MobCombatStats.STATS) {
                helper.assertTrue(supplier.hasAttribute(stat), type + " has " + stat.getRegisteredName());
                helper.assertValueEqual(supplier.getBaseValue(stat), 0.0, type + "'s base " + stat.getRegisteredName());
            }
            types++;
        }
        helper.assertTrue(types > 50, "the vanilla mobs, ours and the player were checked: " + types + " types");
        helper.succeed();
    }

    /** RLM-272: a mob's lifesteal heals it by its share of the melee hit it dealt, as a player's does. */
    @QaGameTest
    public static void aMobsLifestealHealsIt(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Zombie zombie = CombatGameTests.attacker(helper);
        Pillager target = CombatGameTests.target(helper);
        zombie.getAttribute(GeneratedStats.LIFESTEAL).addPermanentModifier(new AttributeModifier(
                Identifier.fromNamespaceAndPath("realmforge_qa", "lifesteal"), 0.5,
                AttributeModifier.Operation.ADD_VALUE));
        zombie.setHealth(10);

        CombatGameTests.assertDealt(helper, target, level.damageSources().mobAttack(zombie), 6, 6, "the melee hit");
        helper.assertValueEqual((double) zombie.getHealth(), 13.0, "the zombie healed half of the 6 it dealt");
        helper.succeed();
    }

    /** Done when of RLM-272: no potion charm can be crafted, while Apotheosis's other recipes load. */
    @QaGameTest
    public static void potionCharmsCannotBeMade(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        for (String id : new String[] {"apotheosis:potion_charm", "apotheosis:infusion/potion_charm"}) {
            helper.assertTrue(server.getRecipeManager().byKey(recipe(id)).isEmpty(), id + " is removed");
        }
        helper.assertTrue(server.getRecipeManager().byKey(recipe("apotheosis:gem_case")).isPresent(),
                "Apotheosis's Gem Case recipe still loads");
        helper.succeed();
    }

    private static ServerPlayer player(GameTestHelper helper, String name) {
        ServerPlayer player = QaPlayers.loginOnPlatform(helper, name);
        player.setGameMode(GameType.SURVIVAL);
        player.connection.markClientLoaded();
        ticks(player, 1);
        return player;
    }

    private static void ticks(ServerPlayer player, int ticks) {
        for (int i = 0; i < ticks; i++) {
            player.doTick();
        }
    }

    private static void run(ServerPlayer player, String command) {
        CommandSourceStack source = player.createCommandSourceStack().withPermission(PermissionSet.ALL_PERMISSIONS);
        player.level().getServer().getCommands().performPrefixedCommand(source, command);
    }

    private static void logout(ServerPlayer player) {
        player.level().getServer().getPlayerList().remove(player);
    }

    /** The entity's Apothic Attributes attribute {@code id}, if it has one, carries no modifier. */
    private static void assertUnraised(GameTestHelper helper, LivingEntity entity, Identifier id) {
        AttributeInstance instance = entity.getAttribute(attribute(id));
        helper.assertTrue(instance == null || instance.getModifiers().isEmpty(),
                "nothing raises " + id + ": " + (instance == null ? "" : instance.getModifiers()));
    }

    private static Holder<Attribute> attribute(Identifier id) {
        return BuiltInRegistries.ATTRIBUTE.get(id)
                .orElseThrow(() -> new IllegalStateException("Apothic Attributes has no attribute " + id));
    }

    private static DataComponentType<?> affixesComponent() {
        DataComponentType<?> type = BuiltInRegistries.DATA_COMPONENT_TYPE.getValue(AFFIXES_COMPONENT);
        if (type == null) {
            throw new IllegalStateException("Apotheosis has no data component " + AFFIXES_COMPONENT);
        }
        return type;
    }

    private static ResourceKey<Recipe<?>> recipe(String id) {
        return ResourceKey.create(Registries.RECIPE, Identifier.parse(id));
    }
}
