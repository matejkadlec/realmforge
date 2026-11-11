package io.github.matejkadlec.realmforge.qa.gametest;

import com.mojang.authlib.GameProfile;
import io.github.matejkadlec.realmforge.character.Characters;
import io.github.matejkadlec.realmforge.generated.GeneratedSpells;
import io.github.matejkadlec.realmforge.generated.GeneratedStats;
import io.github.matejkadlec.realmforge.qa.combat.TrainingDummy;
import io.github.matejkadlec.realmforge.qa.command.StatsCommands;
import io.github.matejkadlec.realmforge.spell.CastResult;
import io.github.matejkadlec.realmforge.spell.Casting;
import io.github.matejkadlec.realmforge.spell.CombatState;
import io.github.matejkadlec.realmforge.spell.CombatState.Cast;
import io.github.matejkadlec.realmforge.spell.Resources;
import io.github.matejkadlec.realmforge.spell.Spell;
import io.github.matejkadlec.realmforge.spell.SpellAttachments;
import io.github.matejkadlec.realmforge.spell.SpellCast;
import io.github.matejkadlec.realmforge.spell.SpellCaster;
import io.github.matejkadlec.realmforge.spell.Spells;
import io.github.matejkadlec.realmforge.spell.net.CastEndedPayload;
import io.github.matejkadlec.realmforge.spell.net.CastStartedPayload;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * An instant spell's animation on the server (RLM-641, ADR 0073 decision 8, architecture 11.2): a player's instant
 * spell runs its effects at once and is no cast in progress, but its clients are told to play its animation for {@link
 * SpellCast#PUSH_TICKS}, and for that long the player's melee is blocked as during a cast: the {@code combat}
 * attachment's window, the cancelled attack and Better Combat's tag. What the client draws is the cast screenshots'
 * (dev setup 3.3) and the playtest's.
 */
public final class InstantAnimationGameTests {
    /** A free target for every test's instant spell: Test Mend heals its caster. */
    private static final ResourceKey<Spell> INSTANT = GeneratedSpells.TEST_MEND;

    private InstantAnimationGameTests() {
    }

    /**
     * Done when: an instant spell plays its animation, with no cast in progress and no end sent, and melee is blocked
     * for the animation's length: the attack is cancelled inside the window and lands after it, and Better Combat's
     * tag is set and cleared.
     */
    @QaGameTest
    public static void anInstantSpellPlaysItsAnimationAndBlocksMeleeForItsLength(GameTestHelper helper) {
        ServerPlayer player = caster(helper, new GameProfile(UUID.randomUUID(), "qa-instant-push"));
        Pig pig = helper.spawnWithNoFreeWill(EntityType.PIG, new BlockPos(2, 1, 2));
        CastGameTests.learn(helper, player, INSTANT);
        QaPlayers.sentPayloads(player, CastStartedPayload.class);
        long now = helper.getLevel().getGameTime();

        CastResult result = SpellCaster.cast(player, INSTANT, player);
        helper.assertTrue(result.success() && !result.begun(), "an instant spell runs at once: " + result.failure());
        helper.assertTrue(Casting.current(player).isEmpty(), "its animation is no cast in progress");
        helper.assertValueEqual(QaPlayers.sentPayloads(player, CastStartedPayload.class), List.of(
                        new CastStartedPayload(player.getId(), Cast.instant(INSTANT, now, SpellCast.PUSH_TICKS))),
                "the caster's client is told to play the animation, marked as an instant, with its length");
        CombatState state = player.getData(SpellAttachments.COMBAT);
        helper.assertValueEqual(state.meleeBlockedUntil(), now + SpellCast.PUSH_TICKS,
                "the combat attachment keeps the window, half a second");
        helper.assertTrue(Casting.meleeBlocked(player), "melee is blocked");
        helper.assertTrue(hasBetterCombatTag(player), "Better Combat's disable tag is set");
        helper.assertTrue(state.setBetterCombatTag(), "and recorded as set by the spell");
        player.attack(pig);
        helper.assertValueEqual(pig.getHealth(), pig.getMaxHealth(), "a melee hit as the animation begins");

        helper.runAfterDelay(SpellCast.PUSH_TICKS - 1, () -> {
            helper.assertTrue(Casting.meleeBlocked(player), "still blocked on the animation's last tick");
            player.attack(pig);
            helper.assertValueEqual(pig.getHealth(), pig.getMaxHealth(), "a melee hit on the animation's last tick");
        });
        helper.runAfterDelay(SpellCast.PUSH_TICKS + 1, () -> {
            helper.assertFalse(Casting.meleeBlocked(player), "the window is over");
            helper.assertValueEqual(state.meleeBlockedUntil(), 0L, "and forgotten");
            helper.assertFalse(hasBetterCombatTag(player), "Better Combat's disable tag outlived the animation");
            helper.assertFalse(state.setBetterCombatTag(), "the flag too");
            player.attack(pig);
            helper.assertTrue(pig.getHealth() < pig.getMaxHealth(), "a melee hit after the animation did no damage");
            helper.assertValueEqual(QaPlayers.sentPayloads(player, CastEndedPayload.class), List.of(),
                    "nothing ends an instant spell's animation but its time");
            CastGameTests.logout(player);
            helper.succeed();
        });
    }

    /**
     * A cast that begins as the animation ends keeps Better Combat's tag: the animation's end leaves it to the cast,
     * whose end removes it.
     */
    @QaGameTest
    public static void aCastBegunAsTheAnimationEndsKeepsTheTagUntilItEnds(GameTestHelper helper) {
        ServerPlayer player = caster(helper, new GameProfile(UUID.randomUUID(), "qa-instant-then-cast"));
        Husk husk = CastGameTests.husk(helper, new BlockPos(2, 1, 3));
        CastGameTests.learn(helper, player, INSTANT);
        CastGameTests.learn(helper, player, GeneratedSpells.TEST_SLOW_BOLT);
        helper.assertTrue(SpellCaster.cast(player, INSTANT, player).success(), "the instant spell");
        CombatState state = player.getData(SpellAttachments.COMBAT);

        // The global cooldown and the animation end on the same tick, so the next spell begins as the window closes.
        helper.runAfterDelay(SpellCast.PUSH_TICKS, () ->
                CastGameTests.assertBegun(helper, player, GeneratedSpells.TEST_SLOW_BOLT, husk));
        helper.runAfterDelay(SpellCast.PUSH_TICKS + 2, () -> {
            helper.assertFalse(Casting.meleeBlocked(player), "the animation's window is over");
            helper.assertTrue(Casting.isCasting(player), "the cast runs");
            helper.assertTrue(hasBetterCombatTag(player), "the cast keeps Better Combat's tag");
            helper.assertTrue(state.setBetterCombatTag(), "still recorded as ours");
            Spells.cancelCast(player);
            helper.assertFalse(hasBetterCombatTag(player), "the cast's end removes it");
            helper.assertFalse(state.setBetterCombatTag(), "and the flag");
            CastGameTests.logout(player);
            helper.succeed();
        });
    }

    /**
     * An operator's own Better Combat tag survives the animation, and a logout in the middle of one leaves neither
     * the tag nor its flag in the save.
     */
    @QaGameTest
    public static void onlyTheTagTheAnimationSetIsRemoved(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer operator = caster(helper, new GameProfile(UUID.randomUUID(), "qa-instant-op-tag"));
        CastGameTests.learn(helper, operator, INSTANT);
        operator.addTag(CombatState.BETTER_COMBAT_DISABLED_TAG);
        helper.assertTrue(SpellCaster.cast(operator, INSTANT, operator).success(), "the operator's instant spell");
        helper.assertFalse(operator.getData(SpellAttachments.COMBAT).setBetterCombatTag(),
                "the spell did not set the operator's tag");

        GameProfile profile = new GameProfile(UUID.randomUUID(), "qa-instant-logout");
        ServerPlayer leaving = caster(helper, profile);
        CastGameTests.learn(helper, leaving, INSTANT);
        helper.assertTrue(SpellCaster.cast(leaving, INSTANT, leaving).success(), "the leaving player's instant spell");
        helper.assertTrue(hasBetterCombatTag(leaving), "the tag is set while the animation plays");
        CastGameTests.logout(leaving);
        ServerPlayer back = QaPlayers.login(server, profile);
        helper.assertFalse(hasBetterCombatTag(back), "a logout mid-animation left the tag in the save");
        helper.assertFalse(back.getData(SpellAttachments.COMBAT).setBetterCombatTag(), "and the flag");
        helper.assertFalse(Casting.meleeBlocked(back), "and no window");
        CastGameTests.logout(back);

        helper.runAfterDelay(SpellCast.PUSH_TICKS + 1, () -> {
            helper.assertFalse(Casting.meleeBlocked(operator), "the operator's window is over");
            helper.assertTrue(hasBetterCombatTag(operator), "the operator's own tag survives the animation");
            CastGameTests.logout(operator);
            helper.succeed();
        });
    }

    /**
     * What plays no such animation: an aura, which is toggled and not cast (RLM-595), and a mob's instant spell, as a
     * mob has neither the arms' layer nor a player's melee.
     */
    @QaGameTest
    public static void anAuraAndAMobPlayNoInstantAnimation(GameTestHelper helper) {
        ServerPlayer player = caster(helper, new GameProfile(UUID.randomUUID(), "qa-instant-aura"));
        CastGameTests.learn(helper, player, GeneratedSpells.TEST_STANCE_A);
        QaPlayers.sentPayloads(player, CastStartedPayload.class);
        helper.assertTrue(SpellCaster.cast(player, GeneratedSpells.TEST_STANCE_A, player).success(), "the toggle");
        helper.assertFalse(Casting.meleeBlocked(player), "a toggled aura blocks no melee");
        helper.assertFalse(hasBetterCombatTag(player), "and sets no Better Combat tag");
        helper.assertValueEqual(QaPlayers.sentPayloads(player, CastStartedPayload.class), List.of(),
                "and tells no client of an animation");

        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(2, 1, 4));
        CastResult cast = SpellCaster.cast(zombie, GeneratedSpells.TEST_NPC_BOLT, (LivingEntity) player);
        helper.assertTrue(cast.success() && !cast.begun(), "the mob's instant spell: " + cast.failure());
        helper.assertFalse(Casting.meleeBlocked(zombie), "a mob has no such window");
        helper.assertFalse(zombie.entityTags().contains(CombatState.BETTER_COMBAT_DISABLED_TAG), "and no tag");
        helper.assertValueEqual(QaPlayers.sentPayloads(player, CastStartedPayload.class), List.of(),
                "and its clients are told of no animation");
        CastGameTests.logout(player);
        helper.succeed();
    }

    /**
     * The playtest handoff's two command block lines, run through {@code /qa run} as the player (the handoff's {@code
     * execute as @p at @p} only picks that player): a level 18 Human Mage with an instant spell, an instant bolt, a
     * cast that forms a bolt and a channel, then a level 18 Human Warrior with an instant spell, an aura and a swing,
     * each with a sword in hand and a training dummy ahead.
     */
    @QaGameTest
    public static void thePlaytestsCommandLinesGiveAMageAndAWarriorTheirSpellsASwordAndADummy(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = QaPlayers.loginOnPlatform(helper, "qa-instant-cmd", new Vec3(2.5, 1, 0.5));
        run(server, player, "qa run qa character reset; qa character create realmforge:human realmforge:mage; "
                + "qa level 18; qa spell learn realmforge:ice_barrier; qa spell learn realmforge:ice_lance; "
                + "qa spell learn realmforge:frostbolt; qa spell learn realmforge:arcane_missiles; "
                + "give @s minecraft:iron_sword; qa dummy");
        assertReady(helper, player, "mage", List.of(GeneratedSpells.ICE_BARRIER, GeneratedSpells.ICE_LANCE,
                GeneratedSpells.FROSTBOLT, GeneratedSpells.ARCANE_MISSILES));

        run(server, player, "qa run qa character reset; qa character create realmforge:human realmforge:warrior; "
                + "qa level 18; qa spell learn realmforge:battle_shout; qa spell learn realmforge:battle_stance; "
                + "qa spell learn realmforge:slam; give @s minecraft:iron_sword; qa dummy");
        assertReady(helper, player, "warrior", List.of(GeneratedSpells.BATTLE_SHOUT, GeneratedSpells.BATTLE_STANCE,
                GeneratedSpells.SLAM));
        CastGameTests.logout(player);
        helper.succeed();
    }

    private static void run(MinecraftServer server, ServerPlayer player, String command) {
        server.getCommands().performPrefixedCommand(
                player.createCommandSourceStack().withPermission(PermissionSet.ALL_PERMISSIONS), command);
    }

    /** {@code player} is of {@code mainClass}, knows {@code spells}, holds an iron sword and has one dummy ahead. */
    private static void assertReady(GameTestHelper helper, ServerPlayer player, String mainClass,
            List<ResourceKey<Spell>> spells) {
        helper.assertValueEqual(Characters.mainClass(player).map(entry -> entry.key().identifier().getPath()),
                Optional.of(mainClass), "the character's class");
        for (ResourceKey<Spell> spell : spells) {
            helper.assertValueEqual(Spells.knownLevel(player, spell), Optional.of(1), spell.identifier() + "'s rank");
        }
        helper.assertTrue(player.getInventory().hasAnyMatching(stack -> stack.is(Items.IRON_SWORD)),
                "an iron sword for the melee check");
        List<Entity> dummies = helper.getLevel().getEntities(player, player.getBoundingBox().inflate(6),
                TrainingDummy::isDummy);
        helper.assertValueEqual(dummies.size(), 1, "training dummies ahead of the " + mainClass);
        dummies.forEach(dummy -> TrainingDummy.remove((LivingEntity) dummy));
    }

    /** A fake player at the platform's south edge, in survival, with a full pool of 100 mana. */
    private static ServerPlayer caster(GameTestHelper helper, GameProfile profile) {
        ServerPlayer player = QaPlayers.loginOnPlatform(helper, profile, new Vec3(2.5, 1, 0.5));
        player.setGameMode(GameType.SURVIVAL);
        StatsCommands.setBase(player, GeneratedStats.MAX_MANA, 100);
        Resources.tick(player);
        return player;
    }

    private static boolean hasBetterCombatTag(ServerPlayer player) {
        return player.entityTags().contains(CombatState.BETTER_COMBAT_DISABLED_TAG);
    }
}
