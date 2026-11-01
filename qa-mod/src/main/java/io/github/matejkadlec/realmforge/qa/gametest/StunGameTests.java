package io.github.matejkadlec.realmforge.qa.gametest;

import io.github.matejkadlec.realmforge.generated.GeneratedEffects;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.monster.illager.Evoker;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * RLM-532: a stun stops a mob's whole AI ({@code effect/Stuns}, {@code mixin/MobStunMixin}), the attacks that run in
 * goals without control flags (an evoker's spells, a ghast's fireballs) and in a brain (a piglin's crossbow) too. Each
 * test also lets the mob act once the stun ends, so it shows the mob would have attacked.
 */
public final class StunGameTests {
    /** How far from the platform a test looks for what a mob shot or summoned. */
    private static final double REACH = 32;

    private StunGameTests() {
    }

    /**
     * A stunned evoker never finishes the spell it was warming up (its fangs or vexes), nor starts another while the
     * stun lasts; once it ends, the evoker casts again.
     */
    @QaGameTest(timeoutTicks = 400)
    public static void aStunnedEvokerNeitherFinishesNorStartsASpell(GameTestHelper helper) {
        ServerPlayer player = target(helper, "qa-stun-evoker");
        Evoker evoker = helper.spawn(EntityType.EVOKER, new BlockPos(2, 1, 3));
        evoker.setTarget(player);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(evoker.isCastingSpell(), "the evoker began a spell"))
                .thenExecute(() -> {
                    helper.assertTrue(spellsCast(helper).isEmpty(), "the spell is still warming up");
                    stun(evoker, 100);
                })
                .thenExecuteFor(90, () -> helper.assertTrue(spellsCast(helper).isEmpty(),
                        "a stunned evoker cast: " + spellsCast(helper)))
                .thenExecute(evoker::removeAllEffects)
                .thenWaitUntil(() -> helper.assertFalse(spellsCast(helper).isEmpty(),
                        "the evoker casts again once the stun ended"))
                .thenExecute(() -> {
                    spellsCast(helper).forEach(Entity::discard);
                    logout(player);
                })
                .thenSucceed();
    }

    /**
     * A stunned ghast fires no fireball at a target in sight; once the stun ends, it fires. On the 9 by 9 platform, whose
     * 8 blocks of height hold the ghast under the barrier ceiling every test is encased in.
     */
    @QaGameTest(template = QaGameTests.PLATFORM_9X9, timeoutTicks = 200)
    public static void aStunnedGhastFiresNoFireball(GameTestHelper helper) {
        ServerPlayer player = target(helper, "qa-stun-ghast");
        Ghast ghast = helper.spawn(EntityType.GHAST, new BlockPos(2, 2, 6));
        stun(ghast, 100);
        ghast.setTarget(player);
        helper.startSequence()
                .thenExecuteFor(60, () -> helper.assertTrue(fireballs(helper).isEmpty(),
                        "a stunned ghast fired at a target in sight"))
                .thenExecute(ghast::removeAllEffects)
                .thenWaitUntil(() -> {
                    List<? extends Entity> fired = fireballs(helper);
                    helper.assertFalse(fired.isEmpty(), "the ghast fires once the stun ended");
                    // Before it lands: its explosion would tear up the platform.
                    fired.forEach(Entity::discard);
                })
                .thenExecute(() -> logout(player))
                .thenSucceed();
    }

    /**
     * A brain-driven mob: a stun stops a piglin charging its crossbow at its target, and it starts no new charge while
     * the stun lasts; once it ends, the piglin charges again.
     */
    @QaGameTest(timeoutTicks = 300)
    public static void aStunnedPiglinStopsChargingItsCrossbow(GameTestHelper helper) {
        ServerPlayer player = target(helper, "qa-stun-piglin");
        Piglin piglin = helper.spawn(EntityType.PIGLIN, new BlockPos(2, 1, 4));
        piglin.setImmuneToZombification(true);
        piglin.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.CROSSBOW));
        piglin.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, player);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(piglin.isUsingItem(), "the piglin charges its crossbow"))
                .thenExecute(() -> stun(piglin, 100))
                .thenExecuteAfter(1, () -> helper.assertFalse(piglin.isUsingItem(), "the stun stopped the charge"))
                .thenExecuteFor(60, () -> {
                    helper.assertFalse(piglin.isUsingItem(), "a stunned piglin charged its crossbow");
                    helper.assertTrue(arrows(helper).isEmpty(), "a stunned piglin shot");
                })
                .thenExecute(piglin::removeAllEffects)
                .thenWaitUntil(() -> helper.assertTrue(piglin.isUsingItem() || !arrows(helper).isEmpty(),
                        "the piglin charges again once the stun ended"))
                .thenExecute(() -> {
                    arrows(helper).forEach(Entity::discard);
                    logout(player);
                })
                .thenSucceed();
    }

    /** A survival player on the platform's edge, whom the mobs may attack. */
    private static ServerPlayer target(GameTestHelper helper, String name) {
        ServerPlayer player = QaPlayers.loginOnPlatform(helper, name, new Vec3(2.5, 1, 0.5));
        player.setGameMode(GameType.SURVIVAL);
        // A fake player's client never reports that it loaded, which keeps the player invulnerable.
        player.connection.markClientLoaded();
        return player;
    }

    private static void stun(LivingEntity entity, int ticks) {
        entity.addEffect(new MobEffectInstance(GeneratedEffects.STUNNED, ticks));
    }

    /** What an evoker's spells put into the world: fangs and vexes. */
    private static List<Entity> spellsCast(GameTestHelper helper) {
        BlockPos middle = new BlockPos(2, 1, 2);
        List<Entity> cast = new ArrayList<>(helper.getEntities(EntityType.EVOKER_FANGS, middle, REACH));
        cast.addAll(helper.getEntities(EntityType.VEX, middle, REACH));
        return cast;
    }

    private static List<? extends Entity> fireballs(GameTestHelper helper) {
        return helper.getEntities(EntityType.FIREBALL, new BlockPos(2, 1, 2), REACH);
    }

    private static List<? extends Entity> arrows(GameTestHelper helper) {
        return helper.getEntities(EntityType.ARROW, new BlockPos(2, 1, 2), REACH);
    }

    private static void logout(ServerPlayer player) {
        player.level().getServer().getPlayerList().remove(player);
    }
}
