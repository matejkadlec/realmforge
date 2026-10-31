package io.github.matejkadlec.realmforge.qa.gametest;

import static io.github.matejkadlec.realmforge.qa.gametest.CombatGameTests.close;

import io.github.matejkadlec.realmforge.effect.StackTicks;
import io.github.matejkadlec.realmforge.generated.GeneratedEffects;
import io.github.matejkadlec.realmforge.generated.GeneratedSpells;
import io.github.matejkadlec.realmforge.generated.GeneratedStats;
import io.github.matejkadlec.realmforge.mob.MobLevels;
import io.github.matejkadlec.realmforge.qa.combat.QaItems;
import io.github.matejkadlec.realmforge.qa.command.StatsCommands;
import io.github.matejkadlec.realmforge.spell.CastFailure;
import io.github.matejkadlec.realmforge.spell.CastResult;
import io.github.matejkadlec.realmforge.spell.Passives;
import io.github.matejkadlec.realmforge.spell.Procs;
import io.github.matejkadlec.realmforge.spell.ResourceTypes;
import io.github.matejkadlec.realmforge.spell.Resources;
import io.github.matejkadlec.realmforge.spell.Spell;
import io.github.matejkadlec.realmforge.spell.SpellAttachments;
import io.github.matejkadlec.realmforge.spell.SpellCaster;
import io.github.matejkadlec.realmforge.spell.Spells;
import io.github.matejkadlec.realmforge.stats.EntityLevel;
import io.github.matejkadlec.realmforge.stats.StatsAttachments;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * RLM-540: spells usable only in a window (after a block, after a kill), procs of passive spells with an internal
 * cooldown on each target, a damage bonus against a Frozen target, a spell castable only below a share of the target's
 * health, and extra rage spent for more damage. The test spells: {@code test_overpower} (5 s after a block),
 * {@code test_victory_rush} (20 s after a kill that is not gray), {@code test_frostbite} (Slowness Freezes, 3 s per
 * target), {@code test_taste_for_blood} (Test Rend's ticks open Test Overpower's window), {@code test_deep_wounds} (a crit
 * makes the target bleed), {@code test_ice_lance} (4, tripled against Frozen) and {@code test_execute} (below 20 %, up to
 * 30 more rage at 3 % each).
 */
public final class WindowsAndProcsGameTests {
    private static final double HEALTH = SpellGameTests.HEALTH;

    private WindowsAndProcsGameTests() {
    }

    /**
     * Test Overpower is refused while its window is closed; the player's raised greatsword blocking a zombie's hit opens
     * it for 5 s, a cast uses it, and the next cast is refused again.
     */
    @QaGameTest
    public static void aBlockOpensOverpowersWindowAndACastUsesIt(GameTestHelper helper) {
        ServerPlayer player = player(helper, "qa-window-block");
        learn(helper, player, GeneratedSpells.TEST_OVERPOWER);
        Husk husk = SpellGameTests.husk(helper, new BlockPos(2, 1, 1));
        player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_SWORD));
        assertRefused(helper, player, GeneratedSpells.TEST_OVERPOWER, husk, CastFailure.NO_WINDOW);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(QaItems.TEST_GREATSWORD.get()));

        Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(2, 1, 3));
        zombie.setNoAi(true);
        MobLevels.setLevel(zombie, EntityLevel.MIN);
        player.startUsingItem(InteractionHand.MAIN_HAND);
        player.doTick();
        player.hurtServer(helper.getLevel(), helper.getLevel().damageSources().mobAttack(zombie), 4);
        long now = helper.getLevel().getGameTime();
        helper.assertTrue(player.getData(SpellAttachments.COMBAT).isWindowOpen(GeneratedSpells.TEST_OVERPOWER, now),
                "the block opened Test Overpower's window");
        helper.assertValueEqual(player.getData(SpellAttachments.COMBAT).windows().get(GeneratedSpells.TEST_OVERPOWER),
                now + 100, "for 5 s");
        player.stopUsingItem();
        player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_SWORD));

        assertCast(helper, player, GeneratedSpells.TEST_OVERPOWER, husk);
        helper.assertTrue(close(husk.getHealth(), HEALTH - 5), "Test Overpower hit for 5");
        Spells.resetCooldowns(player);
        assertRefused(helper, player, GeneratedSpells.TEST_OVERPOWER, husk, CastFailure.NO_WINDOW);
        logout(player);
        helper.succeed();
    }

    /**
     * A kill of an enemy at most 3 levels below the player opens Test Victory Rush's window for 20 s; a gray kill, 4
     * levels below, does not.
     */
    @QaGameTest
    public static void aKillThatIsNotGrayOpensVictoryRushsWindow(GameTestHelper helper) {
        ServerPlayer player = player(helper, "qa-window-kill");
        player.setData(StatsAttachments.LEVEL, new EntityLevel(10));
        learn(helper, player, GeneratedSpells.TEST_VICTORY_RUSH);
        Husk gray = SpellGameTests.husk(helper, new BlockPos(1, 1, 2));
        MobLevels.setLevel(gray, 6);
        gray.hurtServer(helper.getLevel(), helper.getLevel().damageSources().playerAttack(player), 100_000);
        helper.assertFalse(gray.isAlive(), "the gray husk died");
        long now = helper.getLevel().getGameTime();
        helper.assertFalse(player.getData(SpellAttachments.COMBAT).isWindowOpen(GeneratedSpells.TEST_VICTORY_RUSH, now),
                "a gray kill (level 6 for a level 10 player) opened the window");

        Husk worthy = SpellGameTests.husk(helper, new BlockPos(3, 1, 2));
        MobLevels.setLevel(worthy, 7);
        worthy.hurtServer(helper.getLevel(), helper.getLevel().damageSources().playerAttack(player), 100_000);
        helper.assertTrue(player.getData(SpellAttachments.COMBAT).isWindowOpen(GeneratedSpells.TEST_VICTORY_RUSH, now),
                "a kill 3 levels below the player opened the window");
        helper.assertValueEqual(player.getData(SpellAttachments.COMBAT).windows()
                .get(GeneratedSpells.TEST_VICTORY_RUSH), now + 400, "for 20 s");
        logout(player);
        helper.succeed();
    }

    /**
     * Test Frostbite: the player's Slowness Freezes its target, then not again on that target for 3 s, while another
     * target still gets Frozen at once (an internal cooldown per target).
     */
    @QaGameTest
    public static void frostbiteFreezesOncePerTargetCooldown(GameTestHelper helper) {
        ServerPlayer player = player(helper, "qa-proc-frostbite");
        learn(helper, player, GeneratedSpells.TEST_FROSTBITE);
        learn(helper, player, GeneratedSpells.TEST_CHILL);
        Husk first = SpellGameTests.husk(helper, new BlockPos(1, 1, 2));
        Husk second = SpellGameTests.husk(helper, new BlockPos(3, 1, 2));
        assertCast(helper, player, GeneratedSpells.TEST_CHILL, first);
        helper.assertTrue(first.hasEffect(GeneratedEffects.FROZEN), "the Slowness Froze the first husk");
        first.removeEffect(GeneratedEffects.FROZEN);
        Spells.resetCooldowns(player);
        assertCast(helper, player, GeneratedSpells.TEST_CHILL, first);
        helper.assertFalse(first.hasEffect(GeneratedEffects.FROZEN), "within 3 s the first husk is not Frozen again");
        helper.assertTrue(Procs.isOnCooldown(player, Passives.procId(GeneratedSpells.TEST_FROSTBITE.identifier(), 0),
                first), "the proc waits on the first husk");
        Spells.resetCooldowns(player);
        assertCast(helper, player, GeneratedSpells.TEST_CHILL, second);
        helper.assertTrue(second.hasEffect(GeneratedEffects.FROZEN), "the second husk is Frozen at once");
        logout(player);
        helper.succeed();
    }

    /** Test Taste for Blood: a tick of Test Rend's bleed opens Test Overpower's window. */
    @QaGameTest
    public static void tasteForBloodOpensOverpowerOnARendTick(GameTestHelper helper) {
        ServerPlayer player = player(helper, "qa-proc-taste");
        learn(helper, player, GeneratedSpells.TEST_TASTE_FOR_BLOOD);
        learn(helper, player, GeneratedSpells.TEST_OVERPOWER);
        learn(helper, player, GeneratedSpells.TEST_LACERATE);
        learn(helper, player, GeneratedSpells.TEST_REND);
        Husk lacerated = SpellGameTests.husk(helper, new BlockPos(1, 1, 2));
        Husk rended = SpellGameTests.husk(helper, new BlockPos(3, 1, 2));
        assertCast(helper, player, GeneratedSpells.TEST_LACERATE, lacerated);
        helper.runAfterDelay(14, () -> {
            helper.assertFalse(player.getData(SpellAttachments.COMBAT).isWindowOpen(GeneratedSpells.TEST_OVERPOWER,
                    helper.getLevel().getGameTime()), "another bleed's tick opened the window");
            Spells.resetCooldowns(player);
            assertCast(helper, player, GeneratedSpells.TEST_REND, rended);
        });
        helper.runAfterDelay(28, () -> {
            helper.assertTrue(player.getData(SpellAttachments.COMBAT).isWindowOpen(GeneratedSpells.TEST_OVERPOWER,
                    helper.getLevel().getGameTime()), "Test Rend's first tick opened the window");
            logout(player);
            helper.succeed();
        });
    }

    /** Test Deep Wounds: the player's crit makes its target bleed, and the bleed's own hits fire no proc. */
    @QaGameTest
    public static void deepWoundsBleedsOnACrit(GameTestHelper helper) {
        ServerPlayer player = player(helper, "qa-proc-deep-wounds");
        learn(helper, player, GeneratedSpells.TEST_DEEP_WOUNDS);
        learn(helper, player, GeneratedSpells.TEST_ICE_LANCE);
        Husk husk = SpellGameTests.husk(helper, new BlockPos(2, 1, 2));
        assertCast(helper, player, GeneratedSpells.TEST_ICE_LANCE, husk);
        helper.assertValueEqual(StackTicks.of(husk, GeneratedEffects.BLEEDING).size(), 0, "no crit, no bleed");
        StatsCommands.setBase(player, GeneratedStats.CRIT_CHANCE, 1);
        Spells.resetCooldowns(player);
        assertCast(helper, player, GeneratedSpells.TEST_ICE_LANCE, husk);
        helper.assertValueEqual(StackTicks.of(husk, GeneratedEffects.BLEEDING).size(), 1, "the crit made it bleed");
        helper.assertValueEqual(StackTicks.of(husk, GeneratedEffects.BLEEDING).getFirst().spell(),
                Optional.of(GeneratedSpells.TEST_DEEP_WOUND.identifier()), "Test Deep Wound's bleed");
        logout(player);
        helper.succeed();
    }

    /** Test Ice Lance deals 4, and three times as much, 12, against a Frozen target. */
    @QaGameTest
    public static void iceLanceTriplesAgainstAFrozenTarget(GameTestHelper helper) {
        ServerPlayer player = player(helper, "qa-ice-lance");
        learn(helper, player, GeneratedSpells.TEST_ICE_LANCE);
        Husk husk = SpellGameTests.husk(helper, new BlockPos(2, 1, 2));
        assertCast(helper, player, GeneratedSpells.TEST_ICE_LANCE, husk);
        helper.assertTrue(close(husk.getHealth(), HEALTH - 4), "4 against a husk that is not Frozen");
        husk.addEffect(new MobEffectInstance(GeneratedEffects.FROZEN, 100));
        Spells.resetCooldowns(player);
        assertCast(helper, player, GeneratedSpells.TEST_ICE_LANCE, husk);
        helper.assertTrue(close(husk.getHealth(), HEALTH - 4 - 12), "12 against a Frozen one: "
                + (HEALTH - 4 - husk.getHealth()));
        logout(player);
        helper.succeed();
    }

    /**
     * Test Execute is refused on a target at 20 % health or more; below it, it costs 15 rage and spends up to 30 more:
     * with 45 rage all 45 go, for 10 × 1.9 = 19; with 20, 15 and 5 more, for 10 × 1.15 = 11.5.
     */
    @QaGameTest
    public static void executeNeedsALowTargetAndSpendsExtraRage(GameTestHelper helper) {
        ServerPlayer player = player(helper, "qa-execute");
        learn(helper, player, GeneratedSpells.TEST_EXECUTE);
        player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_SWORD));
        Husk husk = SpellGameTests.husk(helper, new BlockPos(2, 1, 1));
        Resources.set(player, ResourceTypes.RAGE, 45);
        husk.setHealth((float) (HEALTH * 0.2));
        assertRefused(helper, player, GeneratedSpells.TEST_EXECUTE, husk, CastFailure.TARGET_HEALTH_TOO_HIGH);
        helper.assertTrue(close(Resources.amount(player, ResourceTypes.RAGE), 45), "the refusal cost nothing");

        float low = (float) (HEALTH * 0.1);
        husk.setHealth(low);
        assertCast(helper, player, GeneratedSpells.TEST_EXECUTE, husk);
        helper.assertTrue(close(Resources.amount(player, ResourceTypes.RAGE), 0), "15 + 30 more rage spent");
        helper.assertTrue(close(low - husk.getHealth(), 19), "10 × (1 + 30 × 3 %): " + (low - husk.getHealth()));

        husk.setHealth(low);
        Spells.resetCooldowns(player);
        Resources.set(player, ResourceTypes.RAGE, 20);
        assertCast(helper, player, GeneratedSpells.TEST_EXECUTE, husk);
        helper.assertTrue(close(Resources.amount(player, ResourceTypes.RAGE), 0), "15 + the 5 left");
        helper.assertTrue(close(low - husk.getHealth(), 11.5), "10 × (1 + 5 × 3 %): " + (low - husk.getHealth()));
        logout(player);
        helper.succeed();
    }

    private static ServerPlayer player(GameTestHelper helper, String name) {
        ServerPlayer player = QaPlayers.loginOnPlatform(helper, name, new Vec3(2.5, 1, 0.5));
        player.setGameMode(GameType.SURVIVAL);
        player.connection.markClientLoaded();
        StatsCommands.setBase(player, GeneratedStats.CRIT_CHANCE, 0);
        StatsCommands.setBase(player, GeneratedStats.DODGE_CHANCE, 0);
        StatsCommands.setBase(player, GeneratedStats.SPELL_POWER, 0);
        return player;
    }

    private static void learn(GameTestHelper helper, ServerPlayer player, ResourceKey<Spell> spell) {
        Holder.Reference<Spell> holder = Spells.get(helper.getLevel().registryAccess(), spell)
                .orElseThrow(() -> new IllegalStateException("no spell " + spell.identifier()));
        Spells.learn(player, holder, 1);
    }

    private static void assertCast(GameTestHelper helper, LivingEntity caster, ResourceKey<Spell> spell,
            @Nullable LivingEntity target) {
        CastResult result = SpellCaster.cast(caster, spell, target);
        helper.assertTrue(result.success(), spell.identifier() + " was refused: " + result.failure());
    }

    private static void assertRefused(GameTestHelper helper, LivingEntity caster, ResourceKey<Spell> spell,
            @Nullable LivingEntity target, CastFailure expected) {
        helper.assertValueEqual(SpellCaster.cast(caster, spell, target).failureOrNull(), expected,
                spell.identifier() + " refused");
    }

    private static void logout(ServerPlayer player) {
        player.level().getServer().getPlayerList().remove(player);
    }
}
