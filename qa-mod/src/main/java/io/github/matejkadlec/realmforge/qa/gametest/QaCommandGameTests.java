package io.github.matejkadlec.realmforge.qa.gametest;

import com.mojang.authlib.GameProfile;
import io.github.matejkadlec.realmforge.combat.DamagePipeline;
import io.github.matejkadlec.realmforge.generated.GeneratedStats;
import io.github.matejkadlec.realmforge.qa.combat.TrainingDummy;
import java.util.List;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The commands a playtest handoff gives (the flow's "Reaching the state") reach their states: they run here through
 * the server's command dispatcher as typed, by a fake player with operator rights standing on the test platform. The
 * handoff selects the dummy with {@code @n[type=husk]}; here other tests' husks may stand nearby, so the selector also
 * names the dummy's tag.
 */
public final class QaCommandGameTests {
    private static final String DUMMY = "@n[type=husk,tag=" + TrainingDummy.TAG + "]";

    private QaCommandGameTests() {
    }

    /** F4b and F5's playtest: stats, the training dummy, a dummy's resistance, school damage by the player, levels. */
    @QaGameTest
    public static void playtestCommandsReachTheirStates(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = QaPlayers.login(server, new GameProfile(UUID.randomUUID(), "qa-commands"));
        // On the platform's north edge, facing south (yaw 0), so the dummy appears on the platform.
        player.snapTo(helper.absoluteVec(new Vec3(2.5, 1, 0.5)), 0, 0);
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

    private static void run(MinecraftServer server, ServerPlayer player, String command) {
        CommandSourceStack source = player.createCommandSourceStack().withPermission(PermissionSet.ALL_PERMISSIONS);
        server.getCommands().performPrefixedCommand(source, command);
    }

    /** The dummies on this test's platform. */
    private static List<LivingEntity> dummies(GameTestHelper helper) {
        AABB platform = new AABB(helper.absolutePos(BlockPos.ZERO)).inflate(8);
        return helper.getLevel().getEntitiesOfClass(LivingEntity.class, platform, TrainingDummy::isDummy);
    }
}
