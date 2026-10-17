package io.github.matejkadlec.realmforge.qa.gametest;

import io.github.matejkadlec.realmforge.combat.DamageOverTime;
import io.github.matejkadlec.realmforge.combat.DamagePipeline;
import io.github.matejkadlec.realmforge.generated.GeneratedStats;
import io.github.matejkadlec.realmforge.mob.MobLevels;
import io.github.matejkadlec.realmforge.mob.MobScaling;
import io.github.matejkadlec.realmforge.qa.combat.TrainingDummy;
import io.github.matejkadlec.realmforge.qa.command.MobCommands;
import java.util.List;
import java.util.Locale;
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
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForgeMod;

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
        Display.TextDisplay label = TrainingDummy.labelEntity(dummy);
        helper.assertTrue(label != null, "the dummy carries its armor label");
        helper.assertValueEqual(label.getText().getString(), "10 Armor (20% vs a 10-damage hit)\nTraining Dummy",
                "the label against a 10-damage hit (10 − 10 / 2 effective armor, 4 % each), over the dummy's name");
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
        AABB platform = new AABB(helper.absolutePos(BlockPos.ZERO)).inflate(8);
        helper.assertTrue(helper.getLevel().getEntitiesOfClass(Display.TextDisplay.class, platform).isEmpty(),
                "and its label");
        server.getPlayerList().remove(player);
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
        ServerPlayer player = QaPlayers.login(server, new GameProfile(UUID.randomUUID(), "qa-realm-commands"));
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
