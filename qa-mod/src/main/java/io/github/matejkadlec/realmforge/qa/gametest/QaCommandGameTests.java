package io.github.matejkadlec.realmforge.qa.gametest;

import io.github.matejkadlec.realmforge.character.Characters;
import io.github.matejkadlec.realmforge.character.Scrolls;
import io.github.matejkadlec.realmforge.combat.DamageCalculator;
import io.github.matejkadlec.realmforge.combat.DamageOverTime;
import io.github.matejkadlec.realmforge.combat.DamagePipeline;
import io.github.matejkadlec.realmforge.generated.GeneratedItems;
import io.github.matejkadlec.realmforge.generated.GeneratedSpells;
import io.github.matejkadlec.realmforge.generated.GeneratedStats;
import io.github.matejkadlec.realmforge.mob.MobLevels;
import io.github.matejkadlec.realmforge.mob.MobScaling;
import io.github.matejkadlec.realmforge.qa.combat.TrainingDummy;
import io.github.matejkadlec.realmforge.qa.command.MobCommands;
import io.github.matejkadlec.realmforge.qa.command.PuppetCommands;
import io.github.matejkadlec.realmforge.qa.command.ResourceCommands;
import io.github.matejkadlec.realmforge.spell.CastResult;
import io.github.matejkadlec.realmforge.spell.ResourceTypes;
import io.github.matejkadlec.realmforge.spell.Resources;
import io.github.matejkadlec.realmforge.spell.SpellAttachments;
import io.github.matejkadlec.realmforge.spell.SpellCaster;
import io.github.matejkadlec.realmforge.spell.Spellbook;
import io.github.matejkadlec.realmforge.spell.Spells;
import io.github.matejkadlec.realmforge.stats.CharacterStats;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalInt;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.illager.Pillager;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/**
 * The commands a playtest handoff gives (the flow's "Reaching the state") reach their states: they run here through
 * the server's command dispatcher as typed, by a fake player with operator rights standing on the test platform. The
 * handoff selects the dummy with {@code @n[type=pillager]}; here other tests' pillagers may stand nearby, so the
 * selector also names the dummy's tag.
 */
public final class QaCommandGameTests {
    private static final String DUMMY = "@n[type=pillager,tag=" + TrainingDummy.TAG + "]";

    private QaCommandGameTests() {
    }

    /** F4b and F5's playtest: stats, the training dummy, a dummy's resistance, school damage by the player, levels. */
    @QaGameTest
    public static void playtestCommandsReachTheirStates(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = QaPlayers.loginOnPlatform(helper, "qa-commands", new Vec3(2.5, 1, 0.5));
        // On the platform's north edge, facing south (yaw 0), so the dummy appears on the platform.
        double fist = player.getAttributeValue(Attributes.ATTACK_DAMAGE);

        run(server, player, "qa stats set strength 25");
        helper.assertValueEqual(player.getAttributeBaseValue(GeneratedStats.STRENGTH), 25.0, "Strength");
        helper.assertTrue(player.getAttributeValue(Attributes.ATTACK_DAMAGE) > fist, "Strength raised attack damage");
        // Strength's milestone at 10 gives crit chance (RLM-170), which the checks and the fire hit below must not see.
        run(server, player, "qa stats set strength 0");
        run(server, player, "qa stats set crit_chance 0");
        helper.assertValueEqual(player.getAttributeValue(GeneratedStats.CRIT_CHANCE), 0.0, "crit chance, typed in %");
        run(server, player, "qa stats set crit_chance 150");
        helper.assertValueEqual(player.getAttributeValue(GeneratedStats.CRIT_CHANCE), 1.0, "crit chance is capped");
        run(server, player, "qa stats set crit_chance 0");

        run(server, player, "qa dummy 10");
        List<LivingEntity> dummies = dummies(helper);
        helper.assertValueEqual(dummies.size(), 1, "dummies after /qa dummy 10");
        LivingEntity dummy = dummies.getFirst();
        helper.assertValueEqual(dummy.getAttributeValue(Attributes.ARMOR), 10.0, "the dummy's armor");
        helper.assertTrue(dummy.getPassengers().isEmpty(), "Neat's card is the dummy's only display (RLM-339)");
        helper.assertFalse(dummy.isCustomNameVisible(), "the dummy's own name tag stays hidden");
        helper.assertValueEqual(dummy.getCustomName().getString(), "Training Dummy", "the name Neat shows");
        run(server, player, "attribute " + DUMMY + " realmforge:fire_resistance base set 0.5");
        helper.assertValueEqual(dummy.getAttributeValue(GeneratedStats.FIRE_RESISTANCE), 0.5, "the dummy's resistance");
        run(server, player, "damage " + DUMMY + " 10 realmforge:fire by @s");
        TrainingDummy.Session session = TrainingDummy.session(dummy);
        helper.assertTrue(session != null && Math.abs(session.total() - 5) < 1e-4,
                "10 fire damage against 50 % fire resistance (armor does not count) dealt 5: " + session);

        run(server, player, "qa level 20");
        helper.assertValueEqual(DamagePipeline.level(player), 20, "the player's level");
        run(server, player, "qa stats reset");
        helper.assertValueEqual(player.getAttributeBaseValue(GeneratedStats.STRENGTH), 0.0, "Strength after reset");
        helper.assertValueEqual(player.getAttributeValue(GeneratedStats.CRIT_CHANCE), 0.05, "crit chance after reset");
        run(server, player, "qa dummy remove");
        helper.assertTrue(dummies(helper).isEmpty(), "/qa dummy remove removed the dummy");
        server.getPlayerList().remove(player);
        helper.succeed();
    }

    /**
     * RLM-170 and RLM-171's playtest: the command block lines give a Warrior and a Mage 25 Dexterity and Vitality on top
     * of their race and class, with Vitality's Stamina at 10, and {@code /qa stats} resets and sets Stamina. The line
     * runs here through {@code /qa run} as the player; the handoff's {@code execute as @p at @p} only picks that player.
     */
    @QaGameTest
    public static void statRoleCommandsReachTheirStates(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = QaPlayers.loginOnPlatform(helper, "qa-commands-roles", new Vec3(2.5, 1, 0.5));
        for (String playerClass : List.of("warrior", "mage")) {
            run(server, player, "qa run qa character reset; qa character create realmforge:human realmforge:"
                    + playerClass + "; qa stats set dexterity 25; qa stats set vitality 25");
            helper.assertValueEqual(Characters.mainClass(player).map(entry -> entry.key().identifier().getPath()),
                    Optional.of(playerClass), "the character's class");
            helper.assertTrue(player.getAttributeValue(GeneratedStats.DEXTERITY) > 25, "a " + playerClass
                    + "'s Dexterity is 25 and its race's and class's");
            helper.assertValueEqual(player.getAttributeValue(GeneratedStats.STAMINA), 10.0,
                    "a " + playerClass + "'s Stamina from Vitality's milestone at 10");
        }
        helper.assertTrue(player.getAttributeValue(GeneratedStats.COOLDOWN_REDUCTION) > 0,
                "the Mage's Dexterity gives cooldown reduction");
        run(server, player, "qa stats reset");
        helper.assertValueEqual(player.getAttributeValue(GeneratedStats.STAMINA), 0.0, "Stamina after the reset");
        run(server, player, "qa stats set stamina 50");
        helper.assertValueEqual(player.getAttributeValue(GeneratedStats.STAMINA), 50.0, "Stamina set by hand");
        server.getPlayerList().remove(player);
        helper.succeed();
    }

    /**
     * RLM-339: a dummy from a world before it, which still carries its text display label and shows its name tag,
     * loses both on its next full second, so Neat's card is its only display. The test posts that tick itself, as
     * {@code /qa dummy remove} in another test removes every dummy of the level between ticks.
     */
    @QaGameTest
    public static void anOldDummyLosesItsLabel(GameTestHelper helper) {
        Pillager dummy = TrainingDummy.spawn(helper.getLevel(), helper.absoluteVec(new Vec3(2.5, 1, 2.5)), 0, 0);
        Entity label = EntityType.TEXT_DISPLAY.create(helper.getLevel(), EntitySpawnReason.COMMAND);
        helper.assertTrue(label != null, "a text display can be created");
        label.addTag(TrainingDummy.OLD_LABEL_TAG);
        label.snapTo(dummy.getX(), dummy.getY() + dummy.getBbHeight(), dummy.getZ());
        helper.getLevel().addFreshEntity(label);
        label.startRiding(dummy, true, false);
        dummy.setCustomNameVisible(true);
        try {
            helper.assertValueEqual(dummy.getPassengers(), List.of(label), "the old label rides the dummy");
            dummy.tickCount = 19;
            NeoForge.EVENT_BUS.post(new EntityTickEvent.Post(dummy));
            helper.assertTrue(dummy.getPassengers().contains(label), "a tick between full seconds leaves it");
            dummy.tickCount = 20;
            NeoForge.EVENT_BUS.post(new EntityTickEvent.Post(dummy));
            helper.assertTrue(label.isRemoved() && dummy.getPassengers().isEmpty(), "the old label is gone");
            helper.assertFalse(dummy.isCustomNameVisible(), "the name tag is hidden");
        } finally {
            TrainingDummy.remove(dummy);
        }
        helper.succeed();
    }

    /**
     * RLM-185, RLM-186 and RLM-187's playtest: the dummy is a pillager, {@code /effect give} makes the player the
     * applier of its poison and wither, and the splash potions and the bow the handoff gives arrive.
     */
    @QaGameTest
    public static void combatTextPlaytestCommandsReachTheirStates(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = QaPlayers.loginOnPlatform(helper, "qa-dot-commands", new Vec3(2.5, 1, 0.5));

        run(server, player, "qa dummy");
        List<LivingEntity> dummies = dummies(helper);
        helper.assertValueEqual(dummies.size(), 1, "dummies after /qa dummy");
        LivingEntity dummy = dummies.getFirst();
        helper.assertValueEqual(dummy.getType(), EntityType.PILLAGER, "the dummy's type");
        run(server, player, "effect give " + DUMMY + " minecraft:poison 10");
        run(server, player, "effect give " + DUMMY + " minecraft:wither 10");
        DamageSource poison = new DamageSource(helper.getLevel().registryAccess()
                .lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(NeoForgeMod.POISON_DAMAGE));
        DamageOverTime.Tick tick = DamageOverTime.tick(dummy, poison);
        helper.assertTrue(tick != null && tick.applier() == player, "/effect give made the player poison's applier");
        tick = DamageOverTime.tick(dummy, helper.getLevel().damageSources().wither());
        helper.assertTrue(tick != null && tick.applier() == player, "and wither's");

        String splash = "give @s minecraft:splash_potion[minecraft:potion_contents={potion:\"minecraft:";
        run(server, player, splash + "poison\"}]");
        run(server, player, splash + "healing\"}] 4");
        run(server, player, "give @s minecraft:bow");
        run(server, player, "give @s minecraft:arrow 64");
        helper.assertTrue(holds(player, Potions.POISON, 1), "a splash potion of poison");
        helper.assertTrue(holds(player, Potions.HEALING, 4), "four splash potions of healing");
        helper.assertValueEqual(player.getInventory().countItem(Items.BOW), 1, "the bow");
        helper.assertValueEqual(player.getInventory().countItem(Items.ARROW), 64, "the arrows");
        run(server, player, "qa dummy remove");
        server.getPlayerList().remove(player);
        helper.succeed();
    }

    /** W1's playtest: {@code /qa level} rescales a mob, {@code /qa mob} describes it. */
    @QaGameTest
    public static void mobCommandsSetAndShowAMobsLevel(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = QaPlayers.loginOnPlatform(helper, "qa-mob-commands", new Vec3(2.5, 1, 0.5));
        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(2, 1, 3));

        run(server, player, "qa level 10 @n[type=zombie]");
        helper.assertValueEqual(MobLevels.level(zombie), OptionalInt.of(10), "the zombie's level");
        MobScaling scaling = MobScaling.at(helper.getLevel().registryAccess(), 10);
        helper.assertTrue(Math.abs(zombie.getMaxHealth()
                        - zombie.getAttribute(Attributes.MAX_HEALTH).getBaseValue() * scaling.health()) < 1e-4,
                "level 10 scaled the zombie's maximum health");
        String line = MobCommands.describe(zombie);
        helper.assertTrue(line.startsWith("Zombie: Level 10, Health ") && line.contains(String.format(Locale.ROOT,
                "(x%.2f)", scaling.health())), "/qa mob describes the zombie: " + line);
        helper.assertValueEqual(MobCommands.describe(helper.spawn(EntityType.COW, new BlockPos(0, 1, 4))),
                "Cow: No Level", "/qa mob on a cow");
        server.getPlayerList().remove(player);
        helper.succeed();
    }

    /**
     * W2a's playtest: {@code /qa realm enter} is refused before the unlock and lands on Undervault's floor after it;
     * {@code /qa realm lock} and vanilla's {@code /advancement} lock and unlock it again.
     */
    @QaGameTest
    public static void realmCommandsReachTheirStates(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = QaPlayers.loginOnPlatform(helper, "qa-realm-commands");
        player.snapTo(helper.absoluteVec(new Vec3(2.5, 1, 2.5)), 0, 0);
        try {
            run(server, player, "qa realm enter realmforge:stone");
            helper.assertTrue(player.level() == helper.getLevel(), "/qa realm enter is refused without the unlock");
            run(server, player, "qa realm unlock realmforge:stone");
            run(server, player, "qa realm enter realmforge:stone");
            helper.assertTrue(player.level().dimension().identifier().toString().equals("realmforge:stone"),
                    "/qa realm enter takes an unlocked player into Undervault");
            helper.assertTrue(player.getY() >= 24 && player.getY() <= 42 && player.level().getBlockState(
                    player.blockPosition().below()).isFaceSturdy(player.level(), player.blockPosition().below(),
                    Direction.UP), "the player stands on the floor, at y " + player.getY());

            run(server, player, "qa realm lock realmforge:stone");
            run(server, player, "tp @s 0 100 0");
            run(server, player, "execute in minecraft:overworld run tp @s 0 100 0");
            helper.assertTrue(player.level().dimension() == Level.OVERWORLD,
                    "leaving a realm needs no unlock");
            run(server, player, "execute in realmforge:stone run tp @s 0 40 0");
            helper.assertTrue(player.level().dimension() == Level.OVERWORLD,
                    "/qa realm lock took the unlock away");
            run(server, player, "advancement grant @s only realmforge:realm/stone");
            run(server, player, "execute in realmforge:stone run tp @s 0 40 0");
            helper.assertTrue(player.level().dimension().identifier().toString().equals("realmforge:stone"),
                    "/advancement grant unlocks the realm too");
        } finally {
            server.getPlayerList().remove(player);
        }
        helper.succeed();
    }

    /**
     * {@code /qa run}: the commands of one line run in order as its source, typed by the player or from a command
     * block's {@code execute as ... at @s run qa run ...}; their order decides the spell bar's slots.
     */
    @QaGameTest
    public static void runRunsItsCommandsInOrder(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = QaPlayers.loginOnPlatform(helper, "qa-run-commands");
        try {
            run(server, player,
                    "qa run qa spell learn realmforge:test_slow_bolt; /qa spell learn realmforge:test_bolt 3;;");
            Spellbook book = player.getData(SpellAttachments.SPELLBOOK);
            helper.assertValueEqual(book.slot(0), Optional.of(GeneratedSpells.TEST_SLOW_BOLT),
                    "the first command's spell on the first slot");
            helper.assertValueEqual(book.slot(1), Optional.of(GeneratedSpells.TEST_BOLT),
                    "the second command's spell on the second slot");
            helper.assertValueEqual(book.spells().get(GeneratedSpells.TEST_BOLT), 3, "the second command's level");

            CommandSourceStack commandBlock = server.createCommandSourceStack().withLevel(helper.getLevel())
                    .withPosition(helper.absoluteVec(new Vec3(0.5, 1, 0.5)))
                    .withPermission(PermissionSet.ALL_PERMISSIONS);
            server.getCommands().performPrefixedCommand(commandBlock, "execute as qa-run-commands at @s run qa run "
                    + "qa spell learn realmforge:test_drain; qa spell bar 9 realmforge:test_blink");
            book = player.getData(SpellAttachments.SPELLBOOK);
            helper.assertValueEqual(book.slot(2), Optional.of(GeneratedSpells.TEST_DRAIN),
                    "a command block's line runs as the player");
            helper.assertValueEqual(book.slot(Spellbook.RACIAL_SLOT), Optional.of(GeneratedSpells.TEST_BLINK),
                    "and every command of it runs");
        } finally {
            server.getPlayerList().remove(player);
        }
        helper.succeed();
    }

    /**
     * RLM-416's playtest: {@code /qa puppet scroll} logs Puppet in three blocks in front of the player, facing it, in
     * creative mode and charging a Scroll of Oblivion that nothing finishes, so a client shows another player's
     * charging scroll. Running it again replaces the puppet, and {@code /qa puppet remove} logs it out.
     */
    @QaGameTest
    public static void aPuppetStaysInTheMiddleOfAScrollsCharge(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = QaPlayers.loginOnPlatform(helper, "qa-puppet", new Vec3(2.5, 1, 0.5));
        // On the platform's north edge, facing south (yaw 0), so the puppet appears on the platform.
        run(server, player, "qa puppet scroll");
        run(server, player, "qa puppet scroll");
        long puppets = server.getPlayerList().getPlayers().stream()
                .filter(other -> other.getUUID().equals(PuppetCommands.PROFILE.id())).count();
        helper.assertValueEqual(puppets, 1L, "a second /qa puppet scroll replaces the first puppet");
        ServerPlayer puppet = server.getPlayerList().getPlayer(PuppetCommands.PROFILE.id());
        helper.assertTrue(puppet != null && puppet.level() == helper.getLevel(), "the puppet is in the player's level");
        helper.assertTrue(Math.abs(puppet.position().distanceTo(player.position()) - 3) < 1e-6,
                "three blocks in front: " + puppet.position());
        helper.assertTrue(Math.abs(Mth.wrapDegrees(puppet.getYRot())) == 180, "facing the player: " + puppet.getYRot());
        helper.assertTrue(puppet.isCreative(), "in creative mode, so mobs leave it alone");
        helper.assertTrue(puppet.getMainHandItem().is(GeneratedItems.OBLIVION_SCROLL.get()), "it holds the scroll");
        helper.assertTrue(puppet.isUsingItem() && puppet.getUsedItemHand() == InteractionHand.MAIN_HAND,
                "it charges the scroll in its main hand");
        helper.runAfterDelay(Scrolls.CHARGE_TICKS + 5, () -> {
            helper.assertTrue(puppet.isUsingItem(), "still charging after a full charge's time");
            helper.assertValueEqual(puppet.getUseItemRemainingTicks(), Scrolls.CHARGE_TICKS,
                    "nothing ticks the puppet's charge on");
            helper.assertValueEqual(puppet.getMainHandItem().getCount(), 1, "the scroll is not used up");
            run(server, player, "qa puppet remove");
            helper.assertTrue(server.getPlayerList().getPlayer(PuppetCommands.PROFILE.id()) == null,
                    "/qa puppet remove logs it out");
            server.getPlayerList().remove(player);
            helper.succeed();
        });
    }

    /**
     * C4's revised playtest (RLM-27): {@code /qa stats set crit_chance 100} gives 100 %, so Reckless Strike at rank 5
     * (+30 %) crits every time for 1 + 50 % + 30 % (crit overflow) on a dummy without armor; on a dummy with 20 armor
     * the same crit loses the armor's share (15.4 × 1.8 = 27.7 lands as about 21, what Matěj saw on 2026-10-30).
     * Everything runs within one tick: another test's {@code /qa dummy remove} removes every dummy between ticks.
     */
    @QaGameTest
    public static void recklessStrikeCritsForMoreAbove100Percent(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = QaPlayers.loginOnPlatform(helper, "qa-crit-commands", new Vec3(2.5, 1, 0.5));
        player.setGameMode(GameType.SURVIVAL);
        run(server, player, "qa character create realmforge:human realmforge:warrior");
        run(server, player, "item replace entity @s weapon.mainhand with minecraft:iron_sword");
        run(server, player, "qa stats set crit_chance 100");
        helper.assertValueEqual(player.getAttributeValue(GeneratedStats.CRIT_CHANCE), 1.0, "100 % crit chance");
        run(server, player, "qa spell learn realmforge:reckless_strike 5");
        run(server, player, "qa resource rage autofill true");
        helper.assertValueEqual(Resources.amount(player, ResourceTypes.RAGE), 100.0F, "autofill filled the rage");
        // A tick applies the sword's attack damage (vanilla's equipment check), and Sword Specialization with it.
        player.doTick();
        CharacterStats.recompute(player);
        // Rank 5: 5 + 120 % of Attack Damage (scaling system C, RLM-534).
        double raw = 5 + 1.2 * player.getAttributeValue(Attributes.ATTACK_DAMAGE);
        double crit = raw * (1 + player.getAttributeValue(GeneratedStats.CRIT_DAMAGE) + 0.3);

        // In Reckless Strike's box in front of the player (RLM-399).
        Pillager dummy = TrainingDummy.spawn(helper.getLevel(), helper.absoluteVec(new Vec3(2.5, 1, 1.5)), 0, 0);
        try {
            for (double armor : new double[] {0, 20}) {
                dummy.getAttribute(Attributes.ARMOR).setBaseValue(armor);
                TrainingDummy.reset(dummy);
                Spells.resetCooldowns(player);
                CastResult result = SpellCaster.cast(player, GeneratedSpells.RECKLESS_STRIKE, dummy);
                helper.assertTrue(result.success(), "Reckless Strike was refused: " + result.failure());
                // A swing (RLM-399): it lands at its hit moment, brought forward here.
                helper.assertTrue(Spells.landSwing(player), "Reckless Strike swung");
                // The dummy heals itself after every hit; its DPS meter keeps what each hit dealt.
                TrainingDummy.Session session = TrainingDummy.session(dummy);
                helper.assertTrue(session != null && session.hits() == 1 && session.crits() == 1,
                        "one hit, a crit: " + session);
                double dealt = session.total();
                double expected = crit * (1 - DamageCalculator.armorReduction(crit, armor, 0, 0, 0));
                helper.assertTrue(Math.abs(dealt - expected) < 1e-3, String.format(Locale.ROOT,
                        "a %.1f hit crits for 180 %% (%.2f) against %.0f armor: %.2f expected, dealt %.2f", raw, crit,
                        armor, expected, dealt));
            }
        } finally {
            TrainingDummy.remove(dummy);
        }
        run(server, player, "qa resource rage autofill false");
        server.getPlayerList().remove(player);
        helper.succeed();
    }

    /**
     * The testing aids of C4's revision (Matěj, 2026-10-30): {@code /qa resource rage autofill} keeps the rage full
     * until it is turned off, and {@code /qa inventory clear} empties the 27 main slots and keeps the hotbar, the armor
     * and the offhand.
     */
    @QaGameTest
    public static void resourceAutofillAndInventoryClear(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = QaPlayers.loginOnPlatform(helper, "qa-aid-commands", new Vec3(2.5, 1, 0.5));
        player.setGameMode(GameType.SURVIVAL);
        run(server, player, "qa resource rage autofill true");
        Resources.set(player, ResourceTypes.RAGE, 0);
        player.doTick();
        helper.assertValueEqual(Resources.amount(player, ResourceTypes.RAGE), 100.0F, "refilled on the next tick");
        run(server, player, "qa resource rage autofill false");
        helper.assertValueEqual(player.getData(ResourceCommands.AUTOFILL), List.of(), "autofill off");
        Resources.set(player, ResourceTypes.RAGE, 0);
        player.doTick();
        helper.assertTrue(Resources.amount(player, ResourceTypes.RAGE) < 1, "no longer refilled");

        Inventory inventory = player.getInventory();
        inventory.setItem(0, new ItemStack(Items.IRON_SWORD));
        inventory.setItem(Inventory.SELECTION_SIZE, new ItemStack(Items.BREAD, 32));
        inventory.setItem(Inventory.INVENTORY_SIZE - 1, new ItemStack(Items.STONE, 64));
        player.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
        player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.LEATHER_CHESTPLATE));
        run(server, player, "qa inventory clear");
        for (int slot = Inventory.SELECTION_SIZE; slot < Inventory.INVENTORY_SIZE; slot++) {
            helper.assertTrue(inventory.getItem(slot).isEmpty(), "main inventory slot " + slot + " cleared");
        }
        helper.assertTrue(inventory.getItem(0).is(Items.IRON_SWORD), "the hotbar stays");
        helper.assertTrue(player.getOffhandItem().is(Items.SHIELD), "the offhand stays");
        helper.assertTrue(player.getItemBySlot(EquipmentSlot.CHEST).is(Items.LEATHER_CHESTPLATE), "the armor stays");
        server.getPlayerList().remove(player);
        helper.succeed();
    }

    private static void run(MinecraftServer server, ServerPlayer player, String command) {
        CommandSourceStack source = player.createCommandSourceStack().withPermission(PermissionSet.ALL_PERMISSIONS);
        server.getCommands().performPrefixedCommand(source, command);
    }

    /** Whether the player holds {@code count} splash potions of {@code potion}. */
    private static boolean holds(ServerPlayer player, Holder<Potion> potion, int count) {
        int held = 0;
        for (ItemStack stack : player.getInventory()) {
            PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
            if (stack.is(Items.SPLASH_POTION) && contents != null && contents.is(potion)) {
                held += stack.getCount();
            }
        }
        return held == count;
    }

    /** The dummies on this test's platform. */
    private static List<LivingEntity> dummies(GameTestHelper helper) {
        AABB platform = new AABB(helper.absolutePos(BlockPos.ZERO)).inflate(8);
        return helper.getLevel().getEntitiesOfClass(LivingEntity.class, platform, TrainingDummy::isDummy);
    }
}
