package io.github.matejkadlec.realmforge.qa.gametest;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.shadowsoffire.apotheosis.Apoth;
import dev.shadowsoffire.apotheosis.tiers.WorldTier;
import io.github.matejkadlec.realmforge.combat.MagicShields;
import io.github.matejkadlec.realmforge.generated.GeneratedStats;
import io.github.matejkadlec.realmforge.qa.combat.TrainingDummy;
import io.github.matejkadlec.realmforge.qa.command.StatsCommands;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.illager.Pillager;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/**
 * RLM-528: a fresh training dummy is a clean target (no armor, no creature type, silent, 100 health, named "Training
 * Dummy"), and {@code /qa dummy}'s subcommands set the defenses and effects its hits then meet. The commands run
 * through the server's dispatcher as typed, by a fake player with operator rights on the platform's north edge,
 * facing south, so the dummy appears on the platform.
 */
public final class TrainingDummyGameTests {
    private static final Vec3 EDGE = new Vec3(2.5, 1, 0.5);
    private static final String DUMMY = "@n[type=realmforge_qa:training_dummy]";
    private static final double TOLERANCE = 1e-4;

    private TrainingDummyGameTests() {
    }

    /**
     * A fresh dummy: 0 armor and toughness, no creature type, no sounds, 100 health, "Training Dummy", even next to a
     * Pinnacle player, whose world tier augments give a pillager joining beside it 16 armor.
     */
    @QaGameTest
    public static void aFreshDummyIsACleanTarget(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = QaPlayers.loginOnPlatform(helper, "qa-dummy-fresh", EDGE);
        player.setData(Apoth.Attachments.WORLD_TIER, WorldTier.PINNACLE);

        Pillager pillager = helper.spawn(EntityType.PILLAGER, new BlockPos(0, 1, 4));
        helper.assertTrue(pillager.getAttributeValue(Attributes.ARMOR) >= 16,
                "a pillager joining near a Pinnacle player gets the tier's 16 armor: "
                        + pillager.getAttributeValue(Attributes.ARMOR));
        pillager.discard();

        run(server, player, "qa dummy");
        LivingEntity dummy = only(helper);
        helper.assertValueEqual(dummy.getAttributeValue(Attributes.ARMOR), 0.0, "the dummy's armor");
        helper.assertValueEqual(dummy.getAttributeValue(Attributes.ARMOR_TOUGHNESS), 0.0, "the dummy's toughness");
        helper.assertValueEqual(dummy.getMaxHealth(), 100.0F, "the dummy's maximum health");
        helper.assertValueEqual(dummy.getHealth(), 100.0F, "the dummy's health");
        helper.assertValueEqual(dummy.getType(), TrainingDummy.ENTITY_TYPE.get(), "the dummy's own type");
        for (var tag : List.of(EntityTypeTags.ILLAGER, EntityTypeTags.UNDEAD, EntityTypeTags.ARTHROPOD,
                EntityTypeTags.AQUATIC, EntityTypeTags.RAIDERS)) {
            helper.assertFalse(dummy.is(tag), "the dummy is in " + tag.location());
        }
        helper.assertTrue(dummy.isSilent(), "the dummy makes no sound");
        helper.assertValueEqual(dummy.getCustomName().getString(), "Training Dummy", "the dummy's name, without DPS");
        run(server, player, "damage " + DUMMY + " 5 minecraft:generic by @s");
        helper.assertValueEqual(dummy.getCustomName().getString(), "Training Dummy", "its name after a hit");
        TrainingDummy.remove(dummy);
        logout(player);
        helper.succeed();
    }

    /**
     * Armor, toughness, dodge, Spell Resistance and one school's resistance land on the dummy, and its hits meet
     * them; the stats stop at their caps.
     */
    @QaGameTest
    public static void theDefenseSubcommandsSetWhatItsHitsMeet(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = QaPlayers.loginOnPlatform(helper, "qa-dummy-defenses", EDGE);
        StatsCommands.setBase(player, GeneratedStats.CRIT_CHANCE, 0);
        run(server, player, "qa dummy");
        LivingEntity dummy = only(helper);

        run(server, player, "qa dummy armor 12");
        helper.assertValueEqual(dummy.getAttributeValue(Attributes.ARMOR), 12.0, "armor 12");
        run(server, player, "qa dummy toughness 4");
        helper.assertValueEqual(dummy.getAttributeValue(Attributes.ARMOR_TOUGHNESS), 4.0, "toughness 4");
        double blocked = CombatGameTests.blocked(dummy, 10, 12, 4);
        assertDealt(helper, server, player, dummy, "damage " + DUMMY + " 10 minecraft:player_attack by @s",
                10 * (1 - blocked), "a melee hit against 12 armor and 4 toughness");
        run(server, player, "qa dummy armor 0");
        run(server, player, "qa dummy toughness 0");

        run(server, player, "qa dummy resistance 50");
        helper.assertValueEqual(dummy.getAttributeValue(GeneratedStats.SPELL_RESISTANCE), 0.5, "spell resistance");
        assertDealt(helper, server, player, dummy, "damage " + DUMMY + " 10 realmforge:frost by @s", 5,
                "frost against 50 % spell resistance");
        run(server, player, "qa dummy resistance fire 40");
        helper.assertValueEqual(dummy.getAttributeValue(GeneratedStats.FIRE_RESISTANCE), 0.4, "fire resistance");
        assertDealt(helper, server, player, dummy, "damage " + DUMMY + " 10 realmforge:fire by @s", 2.5,
                "fire against 50 % spell and 40 % fire resistance, capped at 75 % together");
        run(server, player, "qa dummy resistance physical 30");
        helper.assertValueEqual(dummy.getAttributeValue(GeneratedStats.PHYSICAL_RESISTANCE), 0.3,
                "the Physical school's resistance is Physical Resistance");
        run(server, player, "qa dummy resistance frost 90");
        helper.assertValueEqual(dummy.getAttributeValue(GeneratedStats.FROST_RESISTANCE), 0.75,
                "a school's resistance stops at its 75 % cap");
        fails(server, player, "qa dummy resistance shadow 10", "an unknown school");

        run(server, player, "qa dummy dodge 100");
        helper.assertValueEqual(dummy.getAttributeValue(GeneratedStats.DODGE_CHANCE), 1.0, "dodge 100 %");
        TrainingDummy.reset(dummy);
        CombatGameTests.settle(dummy);
        dummy.hurtServer(helper.getLevel(), helper.getLevel().damageSources().playerAttack(player), 10);
        TrainingDummy.Session session = TrainingDummy.session(dummy);
        helper.assertTrue(session != null && session.dodges() == 1 && session.total() == 0,
                "a melee hit against 100 % dodge is dodged: " + session);
        TrainingDummy.remove(dummy);
        logout(player);
        helper.succeed();
    }

    /**
     * {@code /qa dummy shield} fills the dummy's magic shield; broken, it refills 5 s after the last damage. The test
     * posts the dummy's ticks itself, as {@code /qa dummy remove} in another test removes every dummy of the level
     * between ticks.
     */
    @QaGameTest
    public static void theShieldRefillsFiveSecondsAfterTheLastDamage(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = QaPlayers.loginOnPlatform(helper, "qa-dummy-shield", EDGE);
        run(server, player, "qa dummy");
        LivingEntity dummy = only(helper);
        run(server, player, "qa dummy shield 10");
        helper.assertValueEqual(dummy.getAttributeValue(GeneratedStats.MAGIC_SHIELD), 10.0, "the shield stat");
        helper.assertValueEqual(MagicShields.points(dummy), 10.0F, "the shield is full at once");
        run(server, player, "damage " + DUMMY + " 15 minecraft:magic");
        helper.assertValueEqual(MagicShields.points(dummy), 0.0F, "15 damage broke the shield");
        int hit = dummy.tickCount;
        dummy.tickCount = hit + TrainingDummy.SHIELD_REFILL_TICKS - 1;
        NeoForge.EVENT_BUS.post(new EntityTickEvent.Post(dummy));
        helper.assertValueEqual(MagicShields.points(dummy), 0.0F, "still broken a tick before 5 s");
        dummy.tickCount = hit + TrainingDummy.SHIELD_REFILL_TICKS;
        NeoForge.EVENT_BUS.post(new EntityTickEvent.Post(dummy));
        helper.assertValueEqual(MagicShields.points(dummy), 10.0F, "full again 5 s after the last damage");
        TrainingDummy.remove(dummy);
        logout(player);
        helper.succeed();
    }

    /**
     * {@code /qa dummy effect}: strength 1 for ever by default, a strength and seconds when given, and a strength above
     * the effect's maximum (Resistance's is 5) fails, naming the range: the first number is always the strength.
     */
    @QaGameTest
    public static void theEffectSubcommandTakesStrengthThenSeconds(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = QaPlayers.loginOnPlatform(helper, "qa-dummy-effects", EDGE);
        run(server, player, "qa dummy");
        LivingEntity dummy = only(helper);

        run(server, player, "qa dummy effect minecraft:resistance");
        MobEffectInstance effect = dummy.getEffect(MobEffects.RESISTANCE);
        helper.assertTrue(effect != null && effect.getAmplifier() == 0 && effect.isInfiniteDuration(),
                "Resistance I for ever: " + effect);
        run(server, player, "qa dummy effect minecraft:resistance 3 60");
        effect = dummy.getEffect(MobEffects.RESISTANCE);
        helper.assertTrue(effect != null && effect.getAmplifier() == 2 && effect.getDuration() == 1200,
                "Resistance III for 60 s: " + effect);
        dummy.removeAllEffects();
        String error = fails(server, player, "qa dummy effect minecraft:resistance 60", "strength 60 for Resistance");
        helper.assertTrue(error.contains("1 to 5"), "the error names the range: " + error);
        helper.assertTrue(dummy.getEffect(MobEffects.RESISTANCE) == null, "the failed command gave nothing");
        run(server, player, "qa dummy effect realmforge:stunned 1 2");
        helper.assertTrue(dummy.getActiveEffects().stream().anyMatch(instance -> instance.getDuration() == 40),
                "our own effects too: stunned for 2 s");
        TrainingDummy.remove(dummy);
        logout(player);
        helper.succeed();
    }

    private static void assertDealt(GameTestHelper helper, MinecraftServer server, ServerPlayer player,
            LivingEntity dummy, String command, double expected, String what) {
        TrainingDummy.reset(dummy);
        CombatGameTests.settle(dummy);
        run(server, player, command);
        TrainingDummy.Session session = TrainingDummy.session(dummy);
        double dealt = session != null ? session.total() : 0;
        helper.assertTrue(Math.abs(dealt - expected) <= TOLERANCE, what + ": " + expected + " expected, dealt " + dealt);
    }

    /** The dummy {@code /qa dummy} spawned three blocks in front of the player. */
    private static LivingEntity only(GameTestHelper helper) {
        AABB spot = new AABB(helper.absolutePos(new BlockPos(2, 1, 3))).inflate(1);
        List<LivingEntity> dummies = helper.getLevel().getEntitiesOfClass(LivingEntity.class, spot,
                TrainingDummy::isDummy);
        helper.assertValueEqual(dummies.size(), 1, "dummies where /qa dummy puts one");
        return dummies.getFirst();
    }

    private static CommandSourceStack source(ServerPlayer player) {
        return player.createCommandSourceStack().withPermission(PermissionSet.ALL_PERMISSIONS);
    }

    private static void run(MinecraftServer server, ServerPlayer player, String command) {
        server.getCommands().performPrefixedCommand(source(player), command);
    }

    /** Runs a command that must fail; its error message. */
    private static String fails(MinecraftServer server, ServerPlayer player, String command, String what) {
        try {
            server.getCommands().getDispatcher().execute(command, source(player));
        } catch (CommandSyntaxException e) {
            return e.getMessage();
        }
        throw new AssertionError(what + ": /" + command + " did not fail");
    }

    private static void logout(ServerPlayer player) {
        player.level().getServer().getPlayerList().remove(player);
    }
}
