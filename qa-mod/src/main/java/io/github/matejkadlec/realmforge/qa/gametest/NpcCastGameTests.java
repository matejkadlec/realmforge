package io.github.matejkadlec.realmforge.qa.gametest;

import io.github.matejkadlec.realmforge.generated.GeneratedSpells;
import io.github.matejkadlec.realmforge.mob.MobLevels;
import io.github.matejkadlec.realmforge.spell.CastFailure;
import io.github.matejkadlec.realmforge.spell.CastResult;
import io.github.matejkadlec.realmforge.spell.Spell;
import io.github.matejkadlec.realmforge.spell.SpellCaster;
import io.github.matejkadlec.realmforge.spell.Spells;
import io.github.matejkadlec.realmforge.stats.EntityLevel;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * C3a-4 (RLM-212): mobs cast through {@link SpellCaster} with the NPC targeting types (architecture 11.2), choosing
 * their target themselves: {@code test_npc_target_bolt} at the mob's current target, {@code test_npc_random_bolt} at a
 * random player and {@code test_npc_far_bolt}, a 1 s cast time, at the farthest one. Players in creative mode are never
 * chosen. The zombie stands still at the platform's north edge; the players stand between 1.5 and 4.5 blocks south.
 */
public final class NpcCastGameTests {
    private static final BlockPos MOB = new BlockPos(2, 1, 0);

    private NpcCastGameTests() {
    }

    /** A mob casts at the target it attacks, and without one the cast is refused. */
    @QaGameTest
    public static void aMobCastsAtItsCurrentTarget(GameTestHelper helper) {
        Zombie zombie = zombie(helper);
        ServerPlayer near = player(helper, "qa-npc-target-near", 1.5);
        ServerPlayer target = player(helper, "qa-npc-target", 4.5);
        assertRefused(helper, zombie, CastFailure.NO_TARGET, "no current target");

        zombie.setTarget(target);
        float before = target.getHealth();
        float nearBefore = near.getHealth();
        assertCastAt(helper, zombie, GeneratedSpells.TEST_NPC_TARGET_BOLT, target);
        helper.assertTrue(target.getHealth() < before, "the bolt hurt the zombie's target");
        helper.assertValueEqual(near.getHealth(), nearBefore, "the other player is untouched");
        logout(near);
        logout(target);
        helper.succeed();
    }

    /** A mob's random player is one of the players it may attack, each in turn, never one in creative mode. */
    @QaGameTest
    public static void aMobCastsAtARandomPlayer(GameTestHelper helper) {
        Zombie zombie = zombie(helper);
        ServerPlayer first = player(helper, "qa-npc-random-first", 1.5);
        ServerPlayer second = player(helper, "qa-npc-random-second", 3.5);
        ServerPlayer creative = player(helper, "qa-npc-random-creative", 4.5);
        creative.setGameMode(GameType.CREATIVE);

        Set<LivingEntity> hit = new HashSet<>();
        for (int i = 0; i < 40; i++) {
            // Every spell is on the global cooldown (RLM-402), the mob's too.
            Spells.resetCooldowns(zombie);
            CastResult result = SpellCaster.cast(zombie, GeneratedSpells.TEST_NPC_RANDOM_BOLT, (LivingEntity) null);
            helper.assertTrue(result.success(), "test_npc_random_bolt was refused: " + result.failure());
            hit.add(result.entity().orElseThrow());
            first.setHealth(first.getMaxHealth());
            second.setHealth(second.getMaxHealth());
        }
        helper.assertValueEqual(hit, Set.of(first, second), "the players the zombie chose in 40 casts");

        first.setGameMode(GameType.SPECTATOR);
        second.setGameMode(GameType.CREATIVE);
        Spells.resetCooldowns(zombie);
        CastResult none = SpellCaster.cast(zombie, GeneratedSpells.TEST_NPC_RANDOM_BOLT, (LivingEntity) null);
        helper.assertValueEqual(none.failureOrNull(), CastFailure.NO_TARGET, "no player the zombie may attack");
        for (ServerPlayer player : List.of(first, second, creative)) {
            logout(player);
        }
        helper.succeed();
    }

    /**
     * A mob's cast at the farthest player chooses it when the cast begins, shows as a cast in progress (the cast bar
     * players see), and hits that player when it completes.
     */
    @QaGameTest(timeoutTicks = 60)
    public static void aMobCastsAtTheFarthestPlayer(GameTestHelper helper) {
        Zombie zombie = zombie(helper);
        ServerPlayer near = player(helper, "qa-npc-far-near", 1.5);
        ServerPlayer far = player(helper, "qa-npc-far", 3.5);
        ServerPlayer creative = player(helper, "qa-npc-far-creative", 4.5);
        creative.setGameMode(GameType.CREATIVE);

        CastResult result = SpellCaster.cast(zombie, GeneratedSpells.TEST_NPC_FAR_BOLT, (LivingEntity) null);
        helper.assertTrue(result.begun(), "test_npc_far_bolt began: " + result.failure());
        helper.assertTrue(result.entity().orElse(null) == far, "the farthest player it may attack: "
                + result.target());
        helper.assertTrue(Spells.currentCast(zombie).isPresent(), "the zombie is casting");
        float before = far.getHealth();
        float nearBefore = near.getHealth();
        float creativeBefore = creative.getHealth();
        helper.runAfterDelay(25, () -> {
            helper.assertTrue(Spells.currentCast(zombie).isEmpty(), "the cast completed");
            helper.assertTrue(far.getHealth() < before, "the bolt hurt the farthest player");
            helper.assertValueEqual(near.getHealth(), nearBefore, "the near player is untouched");
            helper.assertValueEqual(creative.getHealth(), creativeBefore, "the creative player too");
            for (ServerPlayer player : List.of(near, far, creative)) {
                logout(player);
            }
            helper.succeed();
        });
    }

    private static Zombie zombie(GameTestHelper helper) {
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, MOB);
        zombie.setNoAi(true);
        MobLevels.setLevel(zombie, EntityLevel.MIN);
        return zombie;
    }

    /** A fake player in survival on the platform's middle line, {@code z} blocks from its north edge. */
    private static ServerPlayer player(GameTestHelper helper, String name, double z) {
        ServerPlayer player = QaPlayers.loginOnPlatform(helper, name, new Vec3(2.5, 1, z));
        player.setGameMode(GameType.SURVIVAL);
        // A fake player's client never reports that it loaded, which keeps the player invulnerable.
        player.connection.markClientLoaded();
        return player;
    }

    private static void assertCastAt(GameTestHelper helper, LivingEntity caster,
            ResourceKey<Spell> spell,
            LivingEntity expected) {
        CastResult result = SpellCaster.cast(caster, spell, (LivingEntity) null);
        helper.assertTrue(result.success(), spell.identifier() + " was refused: " + result.failure());
        helper.assertTrue(result.entity().orElse(null) == expected, spell.identifier() + " hit " + result.target());
    }

    private static void assertRefused(GameTestHelper helper, LivingEntity caster, CastFailure expected, String what) {
        helper.assertValueEqual(SpellCaster.cast(caster, GeneratedSpells.TEST_NPC_TARGET_BOLT, (LivingEntity) null)
                .failureOrNull(), expected, what);
    }

    private static void logout(ServerPlayer player) {
        player.level().getServer().getPlayerList().remove(player);
    }
}
