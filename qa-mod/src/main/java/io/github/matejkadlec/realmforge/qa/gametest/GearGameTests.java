package io.github.matejkadlec.realmforge.qa.gametest;

import com.mojang.authlib.GameProfile;
import io.github.matejkadlec.realmforge.generated.GeneratedItems;
import io.github.matejkadlec.realmforge.generated.GeneratedSpells;
import io.github.matejkadlec.realmforge.generated.GeneratedStats;
import io.github.matejkadlec.realmforge.item.TwoHandedGear;
import io.github.matejkadlec.realmforge.qa.command.StatsCommands;
import io.github.matejkadlec.realmforge.qa.generated.GeneratedItemExpectations;
import io.github.matejkadlec.realmforge.qa.generated.GeneratedItemExpectations.Gear;
import io.github.matejkadlec.realmforge.qa.generated.GeneratedItemExpectations.GearModifier;
import io.github.matejkadlec.realmforge.qa.generated.GeneratedItemExpectations.ItemDrop;
import io.github.matejkadlec.realmforge.qa.generated.GeneratedItemExpectations.ItemExpectation;
import io.github.matejkadlec.realmforge.qa.generated.GeneratedMobExpectations.DropExpectation;
import io.github.matejkadlec.realmforge.spell.CastResult;
import io.github.matejkadlec.realmforge.spell.Resources;
import io.github.matejkadlec.realmforge.spell.Spell;
import io.github.matejkadlec.realmforge.spell.SpellCaster;
import io.github.matejkadlec.realmforge.spell.Spells;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.enchantment.Enchantable;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.Repairable;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * Gear from {@code content/items/} (RLM-172 wands, RLM-197 caster robes): each piece is in the game as its YAML says
 * ({@link GeneratedItemExpectations}), gives its fixed stats in its own slot and nowhere else, the caster sets add up
 * as Matěj's table says, a spell wears the wand in the main hand, and vanilla mobs drop the items their
 * {@code dropped_by} names, through a loot modifier on their own loot.
 */
public final class GearGameTests {
    private static final double TOLERANCE = 1e-6;
    /** Health is a float, so damage read back from it is only that exact. */
    private static final double HEALTH_TOLERANCE = 1e-4;
    /** A player's attack damage and attack speed without a weapon: a weapon's tooltip numbers add these. */
    private static final double PLAYER_ATTACK_DAMAGE = 1;
    private static final double PLAYER_ATTACK_SPEED = 4;
    private static final Map<String, EquipmentSlot> ARMOR_SLOTS = Map.of(
            "head", EquipmentSlot.HEAD, "chest", EquipmentSlot.CHEST, "legs", EquipmentSlot.LEGS,
            "feet", EquipmentSlot.FEET);
    private static final Map<String, TagKey<Item>> ARMOR_ENCHANTABLE = Map.of(
            "head", ItemTags.HEAD_ARMOR_ENCHANTABLE, "chest", ItemTags.CHEST_ARMOR_ENCHANTABLE,
            "legs", ItemTags.LEG_ARMOR_ENCHANTABLE, "feet", ItemTags.FOOT_ARMOR_ENCHANTABLE);

    private GearGameTests() {
    }

    /**
     * Every piece of gear has its durability, enchantability and repair material, stacks to 1, joins the enchantable
     * tags of its kind, and is a wand (the wand tag the spell engine reads, a weapon that hits for its attack damage at
     * its attack speed) or armor (its slot and look).
     */
    @QaGameTest
    public static void gearHasItsComponents(GameTestHelper helper) {
        int pieces = 0;
        for (ItemExpectation expected : GeneratedItemExpectations.ITEMS) {
            if (expected.gear().isEmpty()) {
                continue;
            }
            pieces++;
            Gear gear = expected.gear().get();
            String id = expected.id();
            ItemStack stack = new ItemStack(item(id));
            helper.assertValueEqual(stack.getMaxDamage(), gear.durability(), id + " durability");
            helper.assertValueEqual(stack.getMaxStackSize(), 1, id + " stacks to 1");
            Enchantable enchantable = stack.get(DataComponents.ENCHANTABLE);
            helper.assertValueEqual(enchantable == null ? 0 : enchantable.value(), gear.enchantability(),
                    id + " enchantability");
            assertRepairable(helper, id, stack, gear.repairWith());
            helper.assertTrue(stack.is(ItemTags.DURABILITY_ENCHANTABLE), id + " takes Unbreaking and Mending");
            Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
            if (gear.kind().equals("wand")) {
                helper.assertTrue(stack.is(SpellCaster.WANDS), id + " is not in the wand tag the spell engine reads");
                helper.assertValueEqual(stack.is(TwoHandedGear.TWO_HANDED), gear.twoHanded(),
                        id + " in the two-handed tag the shield lock reads");
                helper.assertTrue(stack.get(DataComponents.WEAPON) != null, id + " wears down with hits");
                helper.assertTrue(equippable == null, id + " is held, not worn");
                ItemAttributeModifiers modifiers = stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS,
                        ItemAttributeModifiers.EMPTY);
                assertBase(helper, modifiers, Attributes.ATTACK_DAMAGE, Item.BASE_ATTACK_DAMAGE_ID,
                        gear.attackDamage() - PLAYER_ATTACK_DAMAGE, id + " attack damage");
                assertBase(helper, modifiers, Attributes.ATTACK_SPEED, Item.BASE_ATTACK_SPEED_ID,
                        gear.attackSpeed() - PLAYER_ATTACK_SPEED, id + " attack speed");
            } else {
                helper.assertFalse(stack.is(SpellCaster.WANDS), id + " is armor, not a wand");
                helper.assertTrue(equippable != null, id + " cannot be worn");
                helper.assertValueEqual(equippable.slot(), ARMOR_SLOTS.get(gear.kind()), id + " slot");
                helper.assertValueEqual(equippable.assetId().map(key -> key.identifier().toString()),
                        Optional.of(gear.look()), id + " look");
                helper.assertTrue(stack.is(ARMOR_ENCHANTABLE.get(gear.kind())), id + " takes its slot's enchantments");
                helper.assertTrue(stack.is(ItemTags.EQUIPPABLE_ENCHANTABLE), id + " takes the Curse of Binding");
            }
        }
        helper.assertTrue(pieces > 0, "no gear expectations were generated");
        helper.succeed();
    }

    /**
     * Each piece gives its fixed stats, with their ids per slot (architecture 8.2), only in its own slot: a wand in the
     * main hand and not in the off hand, armor where it is worn and not in the main hand. A wand's attack damage and
     * speed count in the main hand too.
     */
    @QaGameTest
    public static void gearGivesItsStatsOnlyInItsSlot(GameTestHelper helper) {
        ServerPlayer player = QaPlayers.loginOnPlatform(helper, "qa-gear-slots");
        player.setGameMode(GameType.SURVIVAL);
        try {
            for (ItemExpectation expected : GeneratedItemExpectations.ITEMS) {
                if (expected.gear().isEmpty()) {
                    continue;
                }
                Gear gear = expected.gear().get();
                String id = expected.id();
                boolean wand = gear.kind().equals("wand");
                EquipmentSlot slot = wand ? EquipmentSlot.MAINHAND : ARMOR_SLOTS.get(gear.kind());
                EquipmentSlot wrong = wand ? EquipmentSlot.OFFHAND : EquipmentSlot.MAINHAND;

                player.setItemSlot(slot, new ItemStack(item(id)));
                player.doTick();
                for (GearModifier modifier : gear.modifiers()) {
                    AttributeModifier applied = instance(helper, player, modifier.attribute())
                            .getModifier(Identifier.parse(modifier.id()));
                    helper.assertTrue(applied != null, id + " in its slot gives no " + modifier.attribute());
                    helper.assertValueEqual(applied.operation().getSerializedName(), modifier.operation(),
                            id + " " + modifier.attribute() + " operation");
                    assertClose(helper, applied.amount(), modifier.amount(), id + " " + modifier.attribute());
                }
                if (wand) {
                    assertClose(helper, player.getAttributeValue(Attributes.ATTACK_SPEED), gear.attackSpeed(),
                            id + " attack speed in the main hand");
                    helper.assertTrue(player.getAttribute(Attributes.ATTACK_DAMAGE)
                            .getModifier(Item.BASE_ATTACK_DAMAGE_ID) != null, id + " adds its attack damage");
                }

                player.setItemSlot(wrong, player.getItemBySlot(slot));
                player.setItemSlot(slot, ItemStack.EMPTY);
                player.doTick();
                for (GearModifier modifier : gear.modifiers()) {
                    helper.assertTrue(instance(helper, player, modifier.attribute())
                                    .getModifier(Identifier.parse(modifier.id())) == null,
                            id + " gives " + modifier.attribute() + " in the " + wrong.getName() + " slot");
                }
                player.setItemSlot(wrong, ItemStack.EMPTY);
                player.doTick();
            }
        } finally {
            logout(player);
        }
        helper.succeed();
    }

    /**
     * RLM-197: a full caster set adds up as Matěj's table of 2026-10-15 says (magic shield, spell power, max mana,
     * cooldown reduction), written here on its own so a typo in one piece's YAML shows.
     */
    @QaGameTest
    public static void fullCasterSetsAddUpAsTheTableSays(GameTestHelper helper) {
        record CasterSet(String name, List<Item> pieces, double magicShield, double spellPower, double maxMana,
                double cooldownReduction) {
        }
        List<CasterSet> sets = List.of(
                new CasterSet("Apprentice's", List.of(GeneratedItems.APPRENTICE_HAT.get(),
                        GeneratedItems.APPRENTICE_ROBES.get(), GeneratedItems.APPRENTICE_PANTS.get(),
                        GeneratedItems.APPRENTICE_BOOTS.get()), 7, 0.08, 40, 0),
                new CasterSet("Spellweave", List.of(GeneratedItems.SPELLWEAVE_CAP.get(),
                        GeneratedItems.SPELLWEAVE_ROBES.get(), GeneratedItems.SPELLWEAVE_PANTS.get(),
                        GeneratedItems.SPELLWEAVE_SANDALS.get()), 15, 0.16, 60, 0),
                new CasterSet("Arcane Silk", List.of(GeneratedItems.ARCANE_SILK_CAP.get(),
                        GeneratedItems.ARCANE_SILK_ROBES.get(), GeneratedItems.ARCANE_SILK_PANTS.get(),
                        GeneratedItems.ARCANE_SILK_SANDALS.get()), 20, 0.28, 100, 0.12),
                new CasterSet("Netherite", List.of(GeneratedItems.NETHERITE_CAP.get(),
                        GeneratedItems.NETHERITE_ROBES.get(), GeneratedItems.NETHERITE_PANTS.get(),
                        GeneratedItems.NETHERITE_SANDALS.get()), 20, 0.40, 160, 0.20));
        List<EquipmentSlot> slots = List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS,
                EquipmentSlot.FEET);
        ServerPlayer player = QaPlayers.loginOnPlatform(helper, "qa-gear-sets");
        player.setGameMode(GameType.SURVIVAL);
        try {
            double armor = player.getAttributeValue(Attributes.ARMOR);
            double magicShield = player.getAttributeValue(GeneratedStats.MAGIC_SHIELD);
            double spellPower = player.getAttributeValue(GeneratedStats.SPELL_POWER);
            double maxMana = player.getAttributeValue(GeneratedStats.MAX_MANA);
            double cooldownReduction = player.getAttributeValue(GeneratedStats.COOLDOWN_REDUCTION);
            for (CasterSet set : sets) {
                for (int i = 0; i < slots.size(); i++) {
                    player.setItemSlot(slots.get(i), new ItemStack(set.pieces().get(i)));
                }
                player.doTick();
                assertClose(helper, player.getAttributeValue(GeneratedStats.MAGIC_SHIELD) - magicShield,
                        set.magicShield(), set.name() + " magic shield");
                assertClose(helper, player.getAttributeValue(GeneratedStats.SPELL_POWER) - spellPower,
                        set.spellPower(), set.name() + " spell power");
                assertClose(helper, player.getAttributeValue(GeneratedStats.MAX_MANA) - maxMana, set.maxMana(),
                        set.name() + " max mana");
                assertClose(helper, player.getAttributeValue(GeneratedStats.COOLDOWN_REDUCTION) - cooldownReduction,
                        set.cooldownReduction(), set.name() + " cooldown reduction");
                assertClose(helper, player.getAttributeValue(Attributes.ARMOR), armor, set.name() + " gives no armor");
                for (EquipmentSlot slot : slots) {
                    player.setItemSlot(slot, ItemStack.EMPTY);
                }
                player.doTick();
            }
        } finally {
            logout(player);
        }
        helper.succeed();
    }

    /**
     * RLM-172: a player's wand in the main hand loses one point of durability per spell cast, when the cast begins,
     * and adds its Spell Power to the spell; a refused cast, a wand in the off hand and a creative player's wand lose
     * nothing.
     */
    @QaGameTest
    public static void castingWearsTheWandInTheMainHand(GameTestHelper helper) {
        ServerPlayer player = QaPlayers.loginOnPlatform(helper, "qa-gear-wand", new Vec3(2.5, 1, 0.5));
        try {
            player.setGameMode(GameType.SURVIVAL);
            StatsCommands.setBase(player, GeneratedStats.CRIT_CHANCE, 0);
            StatsCommands.setBase(player, GeneratedStats.MAX_MANA, 1000);
            Resources.tick(player);
            Husk husk = SpellGameTests.husk(helper, new BlockPos(2, 1, 2));
            learn(helper, player, GeneratedSpells.TEST_BOLT);
            learn(helper, player, GeneratedSpells.TEST_SLOW_BOLT);

            ItemStack wand = new ItemStack(GeneratedItems.STONE_WAND.get());
            player.setItemInHand(InteractionHand.MAIN_HAND, wand);
            player.doTick();
            float before = husk.getHealth();
            assertCast(helper, player, GeneratedSpells.TEST_BOLT, husk);
            helper.assertValueEqual(wand.getDamageValue(), 1, "a cast wears the Stone Wand by one");
            double dealt = before - husk.getHealth();
            helper.assertTrue(Math.abs(dealt - 6 * 1.15) <= HEALTH_TOLERANCE,
                    "test_bolt with the Stone Wand's +15 % spell power deals 6.9, not " + dealt);
            helper.assertFalse(SpellCaster.cast(player, GeneratedSpells.TEST_BOLT, husk).success(),
                    "test_bolt is on its cooldown");
            helper.assertValueEqual(wand.getDamageValue(), 1, "a refused cast wears nothing");

            Spells.resetCooldowns(player);
            CastResult slow = SpellCaster.cast(player, GeneratedSpells.TEST_SLOW_BOLT, husk);
            helper.assertTrue(slow.success(), "test_slow_bolt was refused: " + slow.failure());
            helper.assertValueEqual(wand.getDamageValue(), 2, "a cast time wears the wand when it begins");
            Spells.cancelCast(player);

            Spells.resetCooldowns(player);
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            player.setItemInHand(InteractionHand.OFF_HAND, wand);
            player.doTick();
            assertCast(helper, player, GeneratedSpells.TEST_BOLT, husk);
            helper.assertValueEqual(wand.getDamageValue(), 2, "a wand in the off hand does not wear");

            Spells.resetCooldowns(player);
            player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
            player.setItemInHand(InteractionHand.MAIN_HAND, wand);
            player.setGameMode(GameType.CREATIVE);
            player.doTick();
            assertCast(helper, player, GeneratedSpells.TEST_BOLT, husk);
            helper.assertValueEqual(wand.getDamageValue(), 2, "a creative player's wand does not wear");
        } finally {
            logout(player);
        }
        helper.succeed();
    }

    /**
     * RLM-172 (Matěj, 2026-10-26) and RLM-461 (Matěj, 2026-10-28): a two-handed wand coming into the main hand puts the
     * shield in the off hand into the bag and locks every shield, on vanilla's item cooldown; while the wand stays
     * there the lock keeps starting again, and after it leaves the lock runs down from where it was, while a shield
     * taken in hand still goes into the bag. The bag is the main inventory, never the hotbar, so the shield stays out of
     * both hands and nothing lies on the ground to be picked up again.
     */
    @QaGameTest(timeoutTicks = 100)
    public static void aTwoHandedWandLocksEveryShield(GameTestHelper helper) {
        ServerPlayer player = QaPlayers.loginOnPlatform(helper, "qa-gear-two-handed");
        player.setGameMode(GameType.SURVIVAL);
        ItemStack shield = new ItemStack(Items.SHIELD);
        player.setItemInHand(InteractionHand.OFF_HAND, shield.copy());
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(GeneratedItems.STONE_WAND.get()));
        player.doTick();
        helper.assertTrue(player.getOffhandItem().isEmpty(), "the wand took the shield out of the off hand");
        helper.assertTrue(player.getInventory().getItem(Inventory.SELECTION_SIZE).is(Items.SHIELD),
                "into the first slot of the bag, past the hotbar");
        helper.assertValueEqual(clearDroppedShields(helper, player), 0, "no shield on the ground");
        long locked = helper.getLevel().getGameTime();
        helper.assertValueEqual(TwoHandedGear.lockEnd(player), OptionalLong.of(locked + TwoHandedGear.LOCK_TICKS),
                "a 3-minute lock");
        helper.assertTrue(player.getCooldowns().isOnCooldown(shield), "every shield shows the lock as its cooldown");

        helper.runAfterDelay(30, () -> {
            player.doTick();
            long now = helper.getLevel().getGameTime();
            helper.assertTrue(TwoHandedGear.lockEnd(player).orElse(0) > now + TwoHandedGear.LOCK_TICKS - 20,
                    "the lock started again while the wand stayed in the main hand");
            player.setItemInHand(InteractionHand.MAIN_HAND, shield.copy());
            player.doTick();
            helper.assertTrue(player.getMainHandItem().isEmpty(), "a shield taken in hand during the lock goes");
            helper.assertTrue(player.getInventory().getItem(Inventory.SELECTION_SIZE + 1).is(Items.SHIELD),
                    "into the next free slot of the bag");
            helper.assertValueEqual(clearDroppedShields(helper, player), 0, "still no shield on the ground");
            helper.assertTrue(TwoHandedGear.lockEnd(player).isPresent(), "the lock lasts after the wand left");
        });
        helper.runAfterDelay(60, () -> {
            OptionalLong end = TwoHandedGear.lockEnd(player);
            player.doTick();
            helper.assertValueEqual(TwoHandedGear.lockEnd(player), end, "without the wand, the lock runs down");
            logout(player);
            helper.succeed();
        });
    }

    /**
     * RLM-461 (Matěj, 2026-10-28): with the bag full, a shield taken in hand during the lock drops at the player's feet,
     * as a plain drop: not thrown ahead, and not counted as the player's drop.
     */
    @QaGameTest
    public static void aShieldDropsOnlyWhenTheBagIsFull(GameTestHelper helper) {
        ServerPlayer player = QaPlayers.loginOnPlatform(helper, "qa-gear-full-bag");
        try {
            player.setGameMode(GameType.SURVIVAL);
            for (int slot = Inventory.SELECTION_SIZE; slot < Inventory.INVENTORY_SIZE; slot++) {
                player.getInventory().setItem(slot, new ItemStack(Items.DIRT));
            }
            player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.SHIELD));
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(GeneratedItems.STONE_WAND.get()));
            player.doTick();
            helper.assertTrue(player.getOffhandItem().isEmpty(), "the shield left the off hand");
            List<ItemEntity> dropped = helper.getLevel().getEntitiesOfClass(ItemEntity.class,
                    player.getBoundingBox().inflate(4), item -> item.getItem().is(Items.SHIELD));
            helper.assertValueEqual(dropped.size(), 1, "with the bag full, it dropped");
            ItemEntity shield = dropped.getFirst();
            helper.assertTrue(shield.position().subtract(player.position()).horizontalDistance() < 0.01
                    && shield.getDeltaMovement().horizontalDistance() < 0.01,
                    "at the player's feet, not thrown ahead: " + shield.position() + ", " + shield.getDeltaMovement());
            helper.assertValueEqual(player.getStats().getValue(Stats.CUSTOM.get(Stats.DROP)), 0,
                    "not counted as the player's drop");
            dropped.forEach(ItemEntity::discard);
        } finally {
            logout(player);
        }
        helper.succeed();
    }

    /**
     * RLM-461: a death ends the lock, though the respawned player is a new object with the old one's entity id, and so
     * does a logout. After either, a shield stays in hand.
     */
    @QaGameTest
    public static void aDeathAndALogoutEndTheLock(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        GameProfile profile = new GameProfile(UUID.randomUUID(), "qa-gear-lock-ends");
        ServerPlayer player = lockedWithoutTheWand(helper, QaPlayers.loginOnPlatform(helper, profile));
        ServerPlayer respawned = server.getPlayerList().respawn(player, false, Entity.RemovalReason.KILLED);
        helper.assertTrue(TwoHandedGear.lockEnd(respawned).isEmpty(), "the death ended the lock");
        respawned.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.SHIELD));
        respawned.doTick();
        helper.assertTrue(respawned.getOffhandItem().is(Items.SHIELD), "after the death a shield stays in hand");

        respawned.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
        lockedWithoutTheWand(helper, respawned);
        logout(respawned);
        ServerPlayer back = QaPlayers.loginOnPlatform(helper, profile);
        try {
            helper.assertTrue(TwoHandedGear.lockEnd(back).isEmpty(), "the logout ended the lock");
            back.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.SHIELD));
            back.doTick();
            helper.assertTrue(back.getOffhandItem().is(Items.SHIELD), "after the logout a shield stays in hand");
        } finally {
            logout(back);
        }
        helper.succeed();
    }

    /**
     * RLM-461: a client rebuilds its player at a dimension change and forgets its cooldowns, so the lock goes back on
     * the shields' cooldown (here the server's cooldown is taken away first, as the client's is). The test posts the
     * dimension change's event that {@code ServerPlayer#teleport} fires, instead of generating another dimension's
     * terrain, which would load the server beside the tests that wait for light. A return from the End builds a new
     * player on the server too: it keeps the lock and its cooldown.
     */
    @QaGameTest
    public static void theLockShowsAgainAfterADimensionChangeAndAReturnFromTheEnd(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = lockedWithoutTheWand(helper, QaPlayers.loginOnPlatform(helper, "qa-gear-lock-travels"));
        ItemStack shield = new ItemStack(Items.SHIELD);
        OptionalLong end = TwoHandedGear.lockEnd(player);
        player.getCooldowns().removeCooldown(player.getCooldowns().getCooldownGroup(shield));
        NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerChangedDimensionEvent(player, Level.NETHER, Level.OVERWORLD));
        helper.assertTrue(player.getCooldowns().isOnCooldown(shield), "the dimension change showed the lock again");

        ServerPlayer back = server.getPlayerList().respawn(player, true, Entity.RemovalReason.CHANGED_DIMENSION);
        try {
            helper.assertValueEqual(TwoHandedGear.lockEnd(back), end, "the return from the End kept the lock");
            helper.assertTrue(back.getCooldowns().isOnCooldown(shield), "and its new player shows it");
        } finally {
            logout(back);
        }
        helper.succeed();
    }

    /**
     * A shield on vanilla's cooldown without the lock, as after an axe disabled it, stays in hand: only the lock of a
     * two-handed item drops shields.
     */
    @QaGameTest
    public static void aShieldOnItsOwnCooldownStaysInHand(GameTestHelper helper) {
        ServerPlayer player = QaPlayers.loginOnPlatform(helper, "qa-gear-shield-cooldown");
        try {
            player.setGameMode(GameType.SURVIVAL);
            ItemStack shield = new ItemStack(Items.SHIELD);
            player.setItemInHand(InteractionHand.OFF_HAND, shield);
            player.getCooldowns().addCooldown(shield, 100);
            player.doTick();
            helper.assertTrue(player.getOffhandItem().is(Items.SHIELD), "an axe-disabled shield stays in the off hand");
            helper.assertTrue(TwoHandedGear.lockEnd(player).isEmpty(), "and no lock started");
        } finally {
            logout(player);
        }
        helper.succeed();
    }

    /**
     * RLM-197: a mob that is not ours drops the items whose {@code dropped_by} names it, added to its own loot by a
     * loot modifier: each in its count and about as often as its chance ({@link CustomMobGameTests#expectedShare}),
     * without and with Looting.
     */
    @QaGameTest
    public static void vanillaMobsDropTheItemsThatNameThem(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer killer = QaPlayers.loginOnPlatform(helper, "qa-gear-drops");
        try {
            ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
            sword.enchant(level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
                    .getOrThrow(Enchantments.LOOTING), CustomMobGameTests.LOOTING);
            int drops = 0;
            for (ItemExpectation expected : GeneratedItemExpectations.ITEMS) {
                for (ItemDrop drop : expected.drops()) {
                    drops++;
                    EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.parse(drop.entity()));
                    Mob mob = (Mob) type.create(level, EntitySpawnReason.COMMAND);
                    helper.assertTrue(mob != null, drop.entity() + " cannot be created");
                    mob.setPos(helper.absoluteVec(new Vec3(2.5, 1, 2.5)));
                    ResourceKey<LootTable> table = ResourceKey.create(Registries.LOOT_TABLE,
                            Identifier.parse(drop.lootTable()));
                    helper.assertValueEqual(mob.getLootTable(), Optional.of(table), drop.entity() + " loot table");
                    killer.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                    rollDrop(helper, mob, level.damageSources().playerAttack(killer), table, drop.drop(), 0);
                    killer.setItemInHand(InteractionHand.MAIN_HAND, sword);
                    rollDrop(helper, mob, level.damageSources().playerAttack(killer), table, drop.drop(),
                            CustomMobGameTests.LOOTING);
                    mob.discard();
                }
            }
            helper.assertTrue(drops > 0, "no item drop expectations were generated");
        } finally {
            logout(killer);
        }
        helper.succeed();
    }

    /**
     * Rolls the mob's {@code table} through its death drop code, loot modifiers included: {@code drop}'s item comes in
     * its count (with {@code looting} levels' extra) and about as often as expected.
     */
    private static void rollDrop(GameTestHelper helper, Mob mob, DamageSource source, ResourceKey<LootTable> table,
            DropExpectation drop, int looting) {
        Item item = item(drop.item());
        String what = mob.getType().getDescriptionId() + " with Looting " + looting;
        int dropped = 0;
        for (int i = 0; i < CustomMobGameTests.ROLLS; i++) {
            List<ItemStack> stacks = new ArrayList<>();
            mob.dropFromLootTable(helper.getLevel(), source, true, table, stacks::add);
            int count = stacks.stream().filter(stack -> stack.is(item)).mapToInt(ItemStack::getCount).sum();
            int most = drop.max() + looting * drop.lootingCount();
            helper.assertTrue(count == 0 || (count >= Math.max(1, drop.min()) && count <= most),
                    what + " dropped " + count + " " + drop.item() + ", not " + drop.min() + " to " + most);
            if (count > 0) {
                dropped++;
            }
        }
        double share = dropped / (double) CustomMobGameTests.ROLLS;
        double expected = CustomMobGameTests.expectedShare(drop, looting);
        helper.assertTrue(Math.abs(share - expected) <= CustomMobGameTests.SHARE_TOLERANCE,
                what + " dropped " + drop.item() + " in " + share + " of the rolls, not about " + expected);
    }

    /** Locks the player's shields with a wand in the main hand, then empties the hand, so the lock runs down. */
    private static ServerPlayer lockedWithoutTheWand(GameTestHelper helper, ServerPlayer player) {
        player.setGameMode(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(GeneratedItems.STONE_WAND.get()));
        player.doTick();
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        helper.assertTrue(TwoHandedGear.lockEnd(player).isPresent(), "the wand locked the shields");
        return player;
    }

    /** Removes the shields lying near the player, so none is picked up again, and returns how many there were. */
    private static int clearDroppedShields(GameTestHelper helper, ServerPlayer player) {
        List<ItemEntity> shields = helper.getLevel().getEntitiesOfClass(ItemEntity.class,
                player.getBoundingBox().inflate(4), item -> item.getItem().is(Items.SHIELD));
        shields.forEach(ItemEntity::discard);
        return shields.size();
    }

    private static void assertRepairable(GameTestHelper helper, String id, ItemStack stack, String material) {
        Repairable repairable = stack.get(DataComponents.REPAIRABLE);
        if (material.isEmpty()) {
            helper.assertTrue(repairable == null, id + " has no repair material");
            return;
        }
        helper.assertTrue(repairable != null, id + " cannot be repaired with " + material);
        Item sample = material.startsWith("#")
                ? BuiltInRegistries.ITEM.getTagOrEmpty(TagKey.create(Registries.ITEM,
                        Identifier.parse(material.substring(1)))).iterator().next().value()
                : item(material);
        helper.assertTrue(repairable.isValidRepairItem(new ItemStack(sample)),
                id + " is not repaired with " + material);
        helper.assertFalse(repairable.isValidRepairItem(new ItemStack(Items.DIRT)), id + " is repaired with dirt");
    }

    private static void assertBase(GameTestHelper helper, ItemAttributeModifiers modifiers, Holder<Attribute> attribute,
            Identifier id, double expected, String what) {
        Optional<ItemAttributeModifiers.Entry> entry = modifiers.modifiers().stream()
                .filter(candidate -> candidate.attribute().equals(attribute) && candidate.modifier().id().equals(id))
                .findFirst();
        helper.assertTrue(entry.isPresent(), what + ": no base modifier");
        assertClose(helper, entry.get().modifier().amount(), expected, what);
    }

    private static AttributeInstance instance(GameTestHelper helper, LivingEntity entity, String attribute) {
        Holder<Attribute> holder = BuiltInRegistries.ATTRIBUTE.get(Identifier.parse(attribute))
                .orElseThrow(() -> helper.assertionException("no attribute " + attribute));
        AttributeInstance instance = entity.getAttribute(holder);
        helper.assertTrue(instance != null, entity + " has no " + attribute);
        return instance;
    }

    private static Item item(String id) {
        return BuiltInRegistries.ITEM.getValue(Identifier.parse(id));
    }

    private static void learn(GameTestHelper helper, ServerPlayer player, ResourceKey<Spell> spell) {
        Holder.Reference<Spell> holder = Spells.get(helper.getLevel().registryAccess(), spell)
                .orElseThrow(() -> helper.assertionException("no spell " + spell.identifier()));
        Spells.learn(player, holder, 1);
    }

    private static void assertCast(GameTestHelper helper, ServerPlayer caster, ResourceKey<Spell> spell,
            LivingEntity target) {
        CastResult result = SpellCaster.cast(caster, spell, target);
        helper.assertTrue(result.success(), spell.identifier() + " was refused: " + result.failure());
    }

    private static void assertClose(GameTestHelper helper, double got, double want, String what) {
        helper.assertTrue(Math.abs(got - want) <= TOLERANCE, what + ": expected " + want + ", got " + got);
    }

    private static void logout(ServerPlayer player) {
        player.level().getServer().getPlayerList().remove(player);
    }
}
