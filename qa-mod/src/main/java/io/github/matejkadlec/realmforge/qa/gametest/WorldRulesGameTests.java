package io.github.matejkadlec.realmforge.qa.gametest;

import com.mojang.authlib.GameProfile;
import io.github.matejkadlec.realmforge.qa.RealmforgeQa;
import io.github.matejkadlec.realmforge.world.DarkVision;
import io.github.matejkadlec.realmforge.world.GriefingProtection;
import io.github.matejkadlec.realmforge.world.WorldDefaults;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.LargeFireball;
import net.minecraft.world.entity.vehicle.Minecart;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.common.EffectCures;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * R1 world rules (architecture 19.1): the gamerules of a new world, targeted protection against destructive mobs
 * (decision D6) and Dark Vision. Coordinates are relative to the structure block: the floor is at y = 1.
 */
@GameTestHolder(RealmforgeQa.MOD_ID)
@PrefixGameTestTemplate(false)
public final class WorldRulesGameTests {
    private static final String PLATFORM = "platform_5x5";
    /** The explosion's centre, in the air above the middle of the platform. */
    private static final BlockPos CENTRE = new BlockPos(2, 2, 2);
    /** Blocks one step from the centre that any of these explosions would destroy if it destroyed blocks. */
    private static final List<BlockPos> TARGETS = List.of(new BlockPos(1, 2, 2), new BlockPos(2, 3, 2));
    private static final BlockPos CHEST = new BlockPos(3, 2, 2);

    private WorldRulesGameTests() {
    }

    /** The GameTest world is created on every run, so it went through LevelEvent.CreateSpawnPosition. */
    @GameTest(template = PLATFORM)
    public static void newWorldHasTheDefaultGamerules(GameTestHelper helper) {
        GameRules rules = helper.getLevel().getGameRules();
        for (WorldDefaults.Rule rule : WorldDefaults.RULES) {
            helper.assertValueEqual(rules.getBoolean(rule.key()), rule.value(), rule.key().getId());
        }
        helper.assertTrue(rules.getBoolean(GameRules.RULE_MOBGRIEFING), "mobGriefing was switched off");
        helper.succeed();
    }

    @GameTest(template = PLATFORM)
    public static void creeperExplosionBreaksNothingButHurtsMobs(GameTestHelper helper) {
        placeTargets(helper);
        Pig pig = helper.spawnWithNoFreeWill(EntityType.PIG, new BlockPos(2, 2, 1));
        ArmorStand armorStand = helper.spawn(EntityType.ARMOR_STAND, new BlockPos(1, 2, 3));
        Minecart minecart = helper.spawn(EntityType.MINECART, new BlockPos(3, 2, 3));
        // The frame hangs on the dirt west of it, facing the explosion.
        ItemFrame frame = new ItemFrame(helper.getLevel(), helper.absolutePos(new BlockPos(1, 2, 1)), Direction.EAST);
        frame.setItem(new ItemStack(Items.DIAMOND));
        helper.getLevel().addFreshEntity(frame);
        helper.setBlock(new BlockPos(0, 2, 1), Blocks.DIRT);

        Creeper creeper = EntityType.CREEPER.create(helper.getLevel());
        explode(helper, creeper, 3.0F, false, Level.ExplosionInteraction.MOB);

        assertTargetsStand(helper);
        helper.assertTrue(pig.getHealth() < pig.getMaxHealth(), "the explosion did not hurt the pig");
        for (Entity placed : List.of(armorStand, minecart, frame)) {
            helper.assertTrue(placed.isAlive(), placed.getType().getDescriptionId() + " was destroyed");
        }
        helper.assertValueEqual(frame.getItem().getItem(), Items.DIAMOND, "item in the frame");
        helper.succeed();
    }

    /** A ghast's fireball is a mob's explosion too: its owner caused it. It breaks nothing and lights no fire. */
    @GameTest(template = PLATFORM)
    public static void ghastFireballBreaksNothingAndLightsNoFire(GameTestHelper helper) {
        placeTargets(helper);
        Ghast ghast = EntityType.GHAST.create(helper.getLevel());
        LargeFireball fireball = new LargeFireball(helper.getLevel(), ghast, Vec3.ZERO, 1);
        explode(helper, fireball, 3.0F, true, Level.ExplosionInteraction.MOB);

        assertTargetsStand(helper);
        helper.forEveryBlockInStructure(pos -> {
            if (helper.getBlockState(pos).is(Blocks.FIRE)) {
                helper.fail("fire at " + pos, pos);
            }
        });
        helper.succeed();
    }

    @GameTest(template = PLATFORM)
    public static void tntWithoutAnOwnerStillBreaksBlocks(GameTestHelper helper) {
        placeTargets(helper);
        explode(helper, new PrimedTnt(helper.getLevel(), 0, 0, 0, null), 2.0F, false, Level.ExplosionInteraction.TNT);
        assertTargetsGone(helper);
        helper.succeed();
    }

    @GameTest(template = PLATFORM)
    public static void tntLitByAPlayerStillBreaksBlocks(GameTestHelper helper) {
        placeTargets(helper);
        ServerPlayer player = fakePlayer(helper);
        explode(helper, new PrimedTnt(helper.getLevel(), 0, 0, 0, player), 2.0F, false, Level.ExplosionInteraction.TNT);
        assertTargetsGone(helper);
        helper.succeed();
    }

    /** Only mobs whose every griefing check changes blocks are denied; villagers, piglins and the rest keep theirs. */
    @GameTest(template = PLATFORM)
    public static void griefingIsDeniedOnlyForTerrainGriefers(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        for (EntityType<?> type : GriefingProtection.TERRAIN_GRIEFERS) {
            Entity entity = type.create(level);
            helper.assertFalse(EventHooks.canEntityGrief(level, entity), type.getDescriptionId() + " may grief");
        }
        for (EntityType<?> type : List.of(EntityType.ZOMBIE, EntityType.VILLAGER, EntityType.PIGLIN,
                EntityType.SHEEP, EntityType.FOX, EntityType.CREEPER, EntityType.ALLAY)) {
            Entity entity = type.create(level);
            helper.assertTrue(EventHooks.canEntityGrief(level, entity), type.getDescriptionId() + " may not grief");
        }
        helper.succeed();
    }

    /** Zombies may still pick up items (their griefing check), but break no doors and trample no farmland. */
    @GameTest(template = PLATFORM)
    public static void mobsBreakNoDoorsAndTrampleNoFarmland(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos door = helper.absolutePos(new BlockPos(2, 2, 2));
        level.setBlock(door, Blocks.OAK_DOOR.defaultBlockState(), Block.UPDATE_ALL);
        BlockPos farmland = helper.absolutePos(new BlockPos(1, 1, 1));
        level.setBlock(farmland, Blocks.FARMLAND.defaultBlockState(), Block.UPDATE_ALL);

        LivingEntity zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(3, 2, 3));
        ServerPlayer player = fakePlayer(helper);
        helper.assertFalse(CommonHooks.canEntityDestroy(level, door, zombie), "a zombie may break the door");
        helper.assertTrue(CommonHooks.canEntityDestroy(level, door, player), "a player may not break the door");
        // A fall of 10 blocks always tramples, unless the event is cancelled.
        var dirt = Blocks.DIRT.defaultBlockState();
        helper.assertFalse(CommonHooks.onFarmlandTrample(level, farmland, dirt, 10.0F, zombie), "a zombie trampled");
        helper.assertTrue(CommonHooks.onFarmlandTrample(level, farmland, dirt, 10.0F, player), "a player cannot trample");
        helper.succeed();
    }

    /** A player who has never played before gets Dark Vision on the first login. */
    @GameTest(template = PLATFORM)
    public static void darkVisionIsOnAfterTheFirstLogin(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = QaPlayers.login(server, new GameProfile(UUID.randomUUID(), "qa-dark-vision"));
        try {
            helper.assertTrue(DarkVision.isEnabled(player), "Dark Vision is off by default");
            assertHasDarkVision(helper, player);
        } finally {
            server.getPlayerList().remove(player);
        }
        helper.succeed();
    }

    @GameTest(template = PLATFORM)
    public static void darkVisionSurvivesMilkAndTotems(GameTestHelper helper) {
        ServerPlayer player = fakePlayer(helper);
        DarkVision.setEnabled(player, true);
        player.removeEffectsCuredBy(EffectCures.MILK);
        player.removeEffectsCuredBy(EffectCures.PROTECTED_BY_TOTEM);
        assertHasDarkVision(helper, player);
        helper.succeed();
    }

    /** The switch removes only Dark Vision: a potion's own night vision stays, and Dark Vision outlasts it. */
    @GameTest(template = PLATFORM)
    public static void toggleSwitchesOnlyDarkVision(GameTestHelper helper) {
        ServerPlayer player = fakePlayer(helper);
        DarkVision.setEnabled(player, true);
        helper.assertFalse(DarkVision.toggle(player), "the toggle did not switch it off");
        helper.assertFalse(player.hasEffect(MobEffects.NIGHT_VISION), "night vision outlived the switch");

        player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 3600));
        helper.assertTrue(DarkVision.toggle(player), "the toggle did not switch it on");
        assertHasDarkVision(helper, player);
        player.removeEffectsCuredBy(EffectCures.MILK);
        assertHasDarkVision(helper, player);

        DarkVision.setEnabled(player, false);
        helper.assertFalse(player.hasEffect(MobEffects.NIGHT_VISION), "Dark Vision outlived the switch");
        player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 3600));
        DarkVision.refresh(player);
        helper.assertTrue(player.hasEffect(MobEffects.NIGHT_VISION), "the switch took a potion's night vision");
        helper.succeed();
    }

    /** The switch is copied on death and saved with the player; the effect follows it after respawn and relog. */
    @GameTest(template = PLATFORM)
    public static void switchSurvivesDeathAndRelog(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        GameProfile profile = new GameProfile(UUID.randomUUID(), "qa-dark-relog");
        ServerPlayer player = QaPlayers.login(server, profile);
        try {
            DarkVision.setEnabled(player, false);
            player = server.getPlayerList().respawn(player, false, Entity.RemovalReason.KILLED);
            helper.assertFalse(DarkVision.isEnabled(player), "death switched Dark Vision back on");
            helper.assertFalse(player.hasEffect(MobEffects.NIGHT_VISION), "night vision after respawn while off");

            DarkVision.setEnabled(player, true);
            player = server.getPlayerList().respawn(player, false, Entity.RemovalReason.KILLED);
            helper.assertTrue(DarkVision.isEnabled(player), "death switched Dark Vision off");
            assertHasDarkVision(helper, player);

            DarkVision.setEnabled(player, false);
            server.getPlayerList().remove(player);
            player = QaPlayers.login(server, profile);
            helper.assertFalse(DarkVision.isEnabled(player), "relog switched Dark Vision back on");
            helper.assertFalse(player.hasEffect(MobEffects.NIGHT_VISION), "night vision after relog while off");

            DarkVision.setEnabled(player, true);
            server.getPlayerList().remove(player);
            player = QaPlayers.login(server, profile);
            assertHasDarkVision(helper, player);
        } finally {
            server.getPlayerList().remove(player);
        }
        helper.succeed();
    }

    private static void placeTargets(GameTestHelper helper) {
        for (BlockPos target : TARGETS) {
            helper.setBlock(target, Blocks.DIRT);
        }
        helper.setBlock(CHEST, Blocks.CHEST);
        if (helper.getBlockEntity(CHEST) instanceof ChestBlockEntity chest) {
            chest.setItem(0, new ItemStack(Items.DIAMOND));
        }
    }

    private static void assertTargetsStand(GameTestHelper helper) {
        for (BlockPos target : TARGETS) {
            helper.assertBlockPresent(Blocks.DIRT, target);
        }
        helper.assertBlockPresent(Blocks.CHEST, CHEST);
        if (helper.getBlockEntity(CHEST) instanceof ChestBlockEntity chest) {
            helper.assertValueEqual(chest.getItem(0).getItem(), Items.DIAMOND, "item in the chest");
        }
    }

    private static void assertTargetsGone(GameTestHelper helper) {
        for (BlockPos target : TARGETS) {
            helper.assertBlockNotPresent(Blocks.DIRT, target);
        }
    }

    private static void explode(
            GameTestHelper helper, Entity source, float power, boolean fire, Level.ExplosionInteraction interaction) {
        Vec3 centre = helper.absoluteVec(Vec3.atCenterOf(CENTRE));
        helper.getLevel().explode(source, centre.x, centre.y, centre.z, power, fire, interaction);
    }

    private static void assertHasDarkVision(GameTestHelper helper, Player player) {
        helper.assertTrue(DarkVision.isDarkVision(player.getEffect(MobEffects.NIGHT_VISION)),
                "no Dark Vision effect: " + player.getEffect(MobEffects.NIGHT_VISION));
    }

    private static ServerPlayer fakePlayer(GameTestHelper helper) {
        return FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "qa-world-rules"));
    }
}
