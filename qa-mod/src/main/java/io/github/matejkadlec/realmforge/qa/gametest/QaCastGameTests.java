package io.github.matejkadlec.realmforge.qa.gametest;

import com.mojang.authlib.GameProfile;
import io.github.matejkadlec.realmforge.qa.RealmforgeQa;
import io.github.matejkadlec.realmforge.qa.castanim.QaCasts;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The server rules of the spike 4 test cast (ADR 0020): no melee hit lands while a cast runs, Better Combat's disable
 * tag is set exactly for the cast, and an operator's own tag survives it. The animation itself is client-only and is
 * checked by hand ({@code /qa cast}).
 */
@GameTestHolder(RealmforgeQa.MOD_ID)
@PrefixGameTestTemplate(false)
public final class QaCastGameTests {
    private static final String PLATFORM = "platform_5x5";

    private QaCastGameTests() {
    }

    @GameTest(template = PLATFORM)
    public static void castBlocksMeleeUntilItStops(GameTestHelper helper) {
        ServerPlayer caster = caster(helper);
        Pig target = helper.spawnWithNoFreeWill(EntityType.PIG, new BlockPos(2, 2, 2));

        QaCasts.start(caster, 100);
        helper.assertTrue(QaCasts.isCasting(caster), "the cast did not start");
        helper.assertTrue(hasBetterCombatTag(caster), "Better Combat's disable tag is missing during the cast");
        caster.attack(target);
        helper.assertValueEqual(target.getHealth(), target.getMaxHealth(), "health after a hit during the cast");

        QaCasts.stop(caster);
        helper.assertFalse(QaCasts.isCasting(caster), "the cast did not stop");
        helper.assertFalse(hasBetterCombatTag(caster), "Better Combat's disable tag outlived the cast");
        caster.attack(target);
        helper.assertTrue(target.getHealth() < target.getMaxHealth(), "a hit after the cast did no damage");
        helper.succeed();
    }

    @GameTest(template = PLATFORM)
    public static void castEndsAfterItsDuration(GameTestHelper helper) {
        ServerPlayer caster = caster(helper);
        QaCasts.start(caster, 5);
        helper.runAfterDelay(10, () -> {
            helper.assertFalse(QaCasts.isCasting(caster), "the cast outlived its 5 ticks");
            helper.assertFalse(hasBetterCombatTag(caster), "Better Combat's disable tag outlived the cast");
            helper.succeed();
        });
    }

    /** An operator who disabled Better Combat for a player keeps it disabled after a cast. */
    @GameTest(template = PLATFORM)
    public static void castKeepsAnOperatorsBetterCombatTag(GameTestHelper helper) {
        ServerPlayer caster = caster(helper);
        caster.addTag(QaCasts.BETTER_COMBAT_DISABLED_TAG);
        QaCasts.start(caster, 100);
        QaCasts.stop(caster);
        helper.assertTrue(hasBetterCombatTag(caster), "the cast removed the operator's tag");
        QaCasts.clearLeftoverTags(caster);
        helper.assertTrue(hasBetterCombatTag(caster), "the login cleanup removed the operator's tag");
        helper.succeed();
    }

    /** Tags are saved with the player: a cast cut short by a crash leaves both, and the next login removes them. */
    @GameTest(template = PLATFORM)
    public static void loginCleanupRemovesTagsACastLeftBehind(GameTestHelper helper) {
        ServerPlayer caster = caster(helper);
        caster.addTag(QaCasts.BETTER_COMBAT_DISABLED_TAG);
        caster.addTag(QaCasts.MARKER_TAG);
        QaCasts.clearLeftoverTags(caster);
        helper.assertFalse(hasBetterCombatTag(caster), "the leftover Better Combat tag is still set");
        helper.assertFalse(caster.getTags().contains(QaCasts.MARKER_TAG), "the leftover marker tag is still set");
        helper.succeed();
    }

    private static ServerPlayer caster(GameTestHelper helper) {
        return FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "qa-caster"));
    }

    private static boolean hasBetterCombatTag(ServerPlayer player) {
        return player.getTags().contains(QaCasts.BETTER_COMBAT_DISABLED_TAG);
    }
}
