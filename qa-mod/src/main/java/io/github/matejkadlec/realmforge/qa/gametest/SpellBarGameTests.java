package io.github.matejkadlec.realmforge.qa.gametest;

import io.github.matejkadlec.realmforge.combat.CombatConfig;
import io.github.matejkadlec.realmforge.generated.GeneratedSpells;
import io.github.matejkadlec.realmforge.qa.combat.CastLog;
import io.github.matejkadlec.realmforge.spell.CastEnd;
import io.github.matejkadlec.realmforge.spell.CastFailure;
import io.github.matejkadlec.realmforge.spell.CastResult;
import io.github.matejkadlec.realmforge.spell.Casting;
import io.github.matejkadlec.realmforge.spell.CombatState;
import io.github.matejkadlec.realmforge.spell.ResourceTypes;
import io.github.matejkadlec.realmforge.spell.Resources;
import io.github.matejkadlec.realmforge.spell.Spell;
import io.github.matejkadlec.realmforge.spell.SpellAttachments;
import io.github.matejkadlec.realmforge.spell.SpellBar;
import io.github.matejkadlec.realmforge.spell.SpellBarView;
import io.github.matejkadlec.realmforge.spell.Spellbook;
import io.github.matejkadlec.realmforge.spell.Spells;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.monster.zombie.Husk;

/**
 * C3b (RLM-25): the spell bar's server side (architecture 11.2 step 1, 11.6). A newly learned spell takes the bar's
 * first empty slot, and learning it again leaves the bar alone (RLM-374); the spellbook moves, swaps and empties slots;
 * a cast from the bar may replace the cast in progress, but only once every other check passed, and never with the same
 * spell, and a cast with less than a quarter of a second left finishes first, the new spell following it (RLM-517);
 * the bar's view knows what the player can afford.
 */
public final class SpellBarGameTests {
    private SpellBarGameTests() {
    }

    /** A newly learned spell goes into the first empty slot, once; a mob's spell never does. */
    @QaGameTest
    public static void aLearnedSpellTakesTheFirstEmptySlot(GameTestHelper helper) {
        ServerPlayer player = CastGameTests.caster(helper, "qa-bar-learn");
        learn(helper, player, GeneratedSpells.TEST_BOLT, 1);
        learn(helper, player, GeneratedSpells.TEST_MEND, 1);
        learn(helper, player, GeneratedSpells.TEST_NPC_BOLT, 1);
        learn(helper, player, GeneratedSpells.TEST_BOLT, 2);
        helper.assertValueEqual(bar(player), Map.of(0, GeneratedSpells.TEST_BOLT, 1, GeneratedSpells.TEST_MEND),
                "bolt in the first slot, mend in the second, the creature's bolt nowhere, bolt once");
        logout(player);
        helper.succeed();
    }

    /**
     * RLM-374: learning a known spell again, at a higher level (a class level, C4, or a specialization point, C5),
     * never changes the bar: a spell taken off stays off, a moved one stays where the player put it. A spell the
     * player did not know still takes the first empty slot.
     */
    @QaGameTest
    public static void aKnownSpellLearnedAgainLeavesTheBarAsArranged(GameTestHelper helper) {
        ServerPlayer player = CastGameTests.caster(helper, "qa-bar-relearn");
        learn(helper, player, GeneratedSpells.TEST_BOLT, 1);
        learn(helper, player, GeneratedSpells.TEST_MEND, 1);
        helper.assertTrue(Spells.placeOnBar(player, 0, Optional.empty()), "bolt taken off the bar");
        helper.assertTrue(Spells.placeOnBar(player, 4, Optional.of(GeneratedSpells.TEST_MEND)), "mend moved to 4");
        learn(helper, player, GeneratedSpells.TEST_BOLT, 2);
        learn(helper, player, GeneratedSpells.TEST_MEND, 2);
        Spellbook book = player.getData(SpellAttachments.SPELLBOOK);
        helper.assertValueEqual(book.bar(), Map.of(4, GeneratedSpells.TEST_MEND),
                "bolt stays off and mend stays in slot 4 after both rank up");
        helper.assertValueEqual(book.level(GeneratedSpells.TEST_BOLT), Optional.of(2), "bolt is known at level 2");
        helper.assertValueEqual(book.level(GeneratedSpells.TEST_MEND), Optional.of(2), "mend is known at level 2");
        learn(helper, player, GeneratedSpells.TEST_SCORCH, 1);
        helper.assertValueEqual(bar(player), Map.of(0, GeneratedSpells.TEST_SCORCH, 4, GeneratedSpells.TEST_MEND),
                "a new spell still takes the first empty slot");
        logout(player);
        helper.succeed();
    }

    /** The spellbook's placements: a known spell moves and swaps, an unknown one and the racial slot are refused. */
    @QaGameTest
    public static void theSpellbookMovesSwapsAndEmptiesSlots(GameTestHelper helper) {
        ServerPlayer player = CastGameTests.caster(helper, "qa-bar-place");
        learn(helper, player, GeneratedSpells.TEST_BOLT, 1);
        learn(helper, player, GeneratedSpells.TEST_MEND, 1);
        helper.assertTrue(Spells.placeOnBar(player, 5, Optional.of(GeneratedSpells.TEST_BOLT)), "bolt moves");
        helper.assertTrue(Spells.placeOnBar(player, 5, Optional.of(GeneratedSpells.TEST_MEND)), "mend swaps");
        helper.assertValueEqual(bar(player), Map.of(5, GeneratedSpells.TEST_MEND, 1, GeneratedSpells.TEST_BOLT),
                "mend took bolt's slot, bolt took mend's");
        helper.assertFalse(Spells.placeOnBar(player, 0, Optional.of(GeneratedSpells.TEST_SCORCH)),
                "a spell the player does not know");
        helper.assertFalse(Spells.placeOnBar(player, Spellbook.RACIAL_SLOT, Optional.of(GeneratedSpells.TEST_BOLT)),
                "the racial slot is the race's");
        helper.assertTrue(Spells.placeOnBar(player, 1, Optional.empty()), "emptied");
        helper.assertValueEqual(bar(player), Map.of(5, GeneratedSpells.TEST_MEND), "only mend is left");
        Spells.setRacialSpell(player, Optional.of(GeneratedSpells.TEST_BOLT));
        helper.assertValueEqual(player.getData(SpellAttachments.SPELLBOOK).slot(Spellbook.RACIAL_SLOT),
                Optional.of(GeneratedSpells.TEST_BOLT), "the race (C4) fills its slot");
        logout(player);
        helper.succeed();
    }

    /**
     * A cast from the bar replaces the cast in progress only when the new spell passes every other check: not within
     * the global cooldown the first cast started, never with the same spell, never without its cost; a held key, which
     * asks without {@code replaceCast}, never does.
     */
    @QaGameTest
    public static void aBarCastReplacesTheCastInProgressOnlyWhenItCanGo(GameTestHelper helper) {
        ServerPlayer player = CastGameTests.caster(helper, "qa-bar-replace");
        Husk husk = SpellGameTests.husk(helper, new BlockPos(2, 1, 3));
        learn(helper, player, GeneratedSpells.TEST_SLOW_BOLT, 1);
        learn(helper, player, GeneratedSpells.TEST_HOMING_BOLT, 1);
        learn(helper, player, GeneratedSpells.TEST_IGNITE, 1);
        Optional<Integer> target = Optional.of(husk.getId());
        helper.assertTrue(SpellBar.cast(player, GeneratedSpells.TEST_SLOW_BOLT, target, true).begun(),
                "the slow bolt begins");
        assertRefused(helper, SpellBar.cast(player, GeneratedSpells.TEST_HOMING_BOLT, target, false),
                CastFailure.ALREADY_CASTING, "a held key never replaces a cast");
        assertRefused(helper, SpellBar.cast(player, GeneratedSpells.TEST_HOMING_BOLT, target, true),
                CastFailure.ON_GLOBAL_COOLDOWN, "within the global cooldown the slow bolt started");
        helper.runAfterDelay(Spell.DEFAULT_GLOBAL_COOLDOWN_TICKS + 1, () -> {
            assertRefused(helper, SpellBar.cast(player, GeneratedSpells.TEST_SLOW_BOLT, target, true),
                    CastFailure.ALREADY_CASTING, "pressing the spell being cast does not restart it");
            Resources.set(player, ResourceTypes.MANA, 5);
            assertRefused(helper, SpellBar.cast(player, GeneratedSpells.TEST_IGNITE, target, true),
                    CastFailure.NOT_ENOUGH_RESOURCE, "a spell the player cannot pay for");
            helper.assertTrue(Casting.isCasting(player), "so the slow bolt goes on");
            Resources.set(player, ResourceTypes.MANA, 100);
            CastResult homing = SpellBar.cast(player, GeneratedSpells.TEST_HOMING_BOLT, target, true);
            helper.assertTrue(homing.begun(), "the homing bolt replaces it: " + homing.failure());
            helper.assertValueEqual(CastLog.last(player).map(CastLog.Ended::end), Optional.of(CastEnd.CANCELLED),
                    "the slow bolt was cancelled");
            helper.assertValueEqual(Spells.currentCast(player).map(CombatState.Cast::spell),
                    Optional.of(GeneratedSpells.TEST_HOMING_BOLT), "and the homing bolt casts");
            logout(player);
            helper.succeed();
        });
    }

    /**
     * RLM-517: with a quarter of a second or more left on the cast, a spell from the bar still cancels it, an instant
     * one too: the bolt lands at once and its cooldown starts now.
     */
    @QaGameTest
    public static void aBarSpellCancelsACastWithAQuarterSecondOrMoreLeft(GameTestHelper helper) {
        ServerPlayer player = CastGameTests.caster(helper, "qa-bar-finish-not");
        Husk husk = SpellGameTests.husk(helper, new BlockPos(2, 1, 3));
        learn(helper, player, GeneratedSpells.TEST_SLOW_BOLT, 1);
        learn(helper, player, GeneratedSpells.TEST_BOLT, 1);
        Optional<Integer> target = Optional.of(husk.getId());
        helper.assertTrue(SpellBar.cast(player, GeneratedSpells.TEST_SLOW_BOLT, target, true).begun(),
                "the slow bolt begins");
        CombatState.Cast slow = Spells.currentCast(player).orElseThrow();
        helper.runAfterDelay(30, () -> {
            long now = helper.getLevel().getGameTime();
            helper.assertTrue(slow.endGameTime() - now >= CombatConfig.finishCastTicks(),
                    "a quarter of a second or more is left: " + (slow.endGameTime() - now) + " ticks");
            CastResult bolt = SpellBar.cast(player, GeneratedSpells.TEST_BOLT, target, true);
            helper.assertTrue(bolt.success() && !bolt.queued(), "the bolt is cast at once: " + bolt);
            helper.assertValueEqual(CastLog.last(player).map(CastLog.Ended::end), Optional.of(CastEnd.CANCELLED),
                    "the slow bolt was cancelled");
            helper.assertFalse(Casting.isCasting(player), "nothing casts any more");
            helper.assertValueEqual(cooldownEnd(player, GeneratedSpells.TEST_BOLT), Optional.of(now + 60),
                    "the bolt's 3 s cooldown started now");
            helper.assertTrue(husk.getHealth() < SpellGameTests.HEALTH, "the bolt hit");
            logout(player);
            helper.succeed();
        });
    }

    /**
     * RLM-517 (Matěj, 2026-10-29): with less than a quarter of a second left on the cast, a spell from the bar, sent as
     * with Queue Spell Casts off ({@code replaceCast}), waits: the cast completes, and the new spell's cast begins in
     * the same tick, right after it. A newer press replaces the waiting spell.
     */
    @QaGameTest
    public static void aCastWithLessThanAQuarterSecondLeftFinishesBeforeTheNextSpell(GameTestHelper helper) {
        ServerPlayer player = CastGameTests.caster(helper, "qa-bar-finish-cast");
        Husk husk = SpellGameTests.husk(helper, new BlockPos(2, 1, 3));
        learn(helper, player, GeneratedSpells.TEST_SLOW_BOLT, 1);
        learn(helper, player, GeneratedSpells.TEST_HOMING_BOLT, 1);
        learn(helper, player, GeneratedSpells.TEST_BOLT, 1);
        Optional<Integer> target = Optional.of(husk.getId());
        helper.assertTrue(SpellBar.cast(player, GeneratedSpells.TEST_SLOW_BOLT, target, true).begun(),
                "the slow bolt begins");
        CombatState.Cast slow = Spells.currentCast(player).orElseThrow();
        helper.runAfterDelay(37, () -> {
            long left = slow.endGameTime() - helper.getLevel().getGameTime();
            helper.assertTrue(left > 0 && left < CombatConfig.finishCastTicks(),
                    "less than a quarter of a second is left: " + left + " ticks");
            CastResult bolt = SpellBar.cast(player, GeneratedSpells.TEST_BOLT, target, true);
            helper.assertTrue(bolt.queued(), "the instant bolt waits: " + bolt);
            CastResult homing = SpellBar.cast(player, GeneratedSpells.TEST_HOMING_BOLT, target, true);
            helper.assertTrue(homing.queued(), "a newer press waits instead of it: " + homing);
            helper.assertValueEqual(Casting.queued(player), Optional.of(GeneratedSpells.TEST_HOMING_BOLT),
                    "the homing bolt waits");
            helper.assertValueEqual(Spells.currentCast(player).map(CombatState.Cast::spell),
                    Optional.of(GeneratedSpells.TEST_SLOW_BOLT), "the slow bolt goes on");
            helper.assertValueEqual(husk.getHealth(), (float) SpellGameTests.HEALTH, "nothing hit yet");
        });
        helper.runAfterDelay(45, () -> {
            helper.assertValueEqual(CastLog.last(player), Optional.of(new CastLog.Ended(
                    GeneratedSpells.TEST_SLOW_BOLT, CastEnd.COMPLETED)), "the slow bolt completed");
            helper.assertTrue(husk.getHealth() < SpellGameTests.HEALTH, "and hit");
            Optional<CombatState.Cast> next = Spells.currentCast(player);
            helper.assertValueEqual(next.map(CombatState.Cast::spell), Optional.of(GeneratedSpells.TEST_HOMING_BOLT),
                    "the homing bolt casts");
            helper.assertValueEqual(next.map(CombatState.Cast::startGameTime), Optional.of(slow.endGameTime()),
                    "it began in the tick the slow bolt completed");
            helper.assertValueEqual(cooldownEnd(player, GeneratedSpells.TEST_BOLT), Optional.empty(),
                    "the bolt it replaced was never cast");
            logout(player);
            helper.succeed();
        });
    }

    /** RLM-517: an instant spell from the bar waits for a cast with less than a quarter of a second left the same way. */
    @QaGameTest
    public static void anInstantSpellWaitsForACastNearlyDone(GameTestHelper helper) {
        ServerPlayer player = CastGameTests.caster(helper, "qa-bar-finish-instant");
        Husk husk = SpellGameTests.husk(helper, new BlockPos(2, 1, 3));
        learn(helper, player, GeneratedSpells.TEST_SLOW_BOLT, 1);
        learn(helper, player, GeneratedSpells.TEST_BOLT, 1);
        Optional<Integer> target = Optional.of(husk.getId());
        helper.assertTrue(SpellBar.cast(player, GeneratedSpells.TEST_SLOW_BOLT, target, true).begun(),
                "the slow bolt begins");
        CombatState.Cast slow = Spells.currentCast(player).orElseThrow();
        helper.runAfterDelay(37, () -> {
            long left = slow.endGameTime() - helper.getLevel().getGameTime();
            helper.assertTrue(left > 0 && left < CombatConfig.finishCastTicks(),
                    "less than a quarter of a second is left: " + left + " ticks");
            helper.assertTrue(SpellBar.cast(player, GeneratedSpells.TEST_BOLT, target, true).queued(),
                    "the instant bolt waits");
            helper.assertValueEqual(cooldownEnd(player, GeneratedSpells.TEST_BOLT), Optional.empty(), "not cast yet");
        });
        helper.runAfterDelay(45, () -> {
            helper.assertValueEqual(CastLog.last(player), Optional.of(new CastLog.Ended(
                    GeneratedSpells.TEST_SLOW_BOLT, CastEnd.COMPLETED)), "the slow bolt completed");
            helper.assertValueEqual(cooldownEnd(player, GeneratedSpells.TEST_BOLT),
                    Optional.of(slow.endGameTime() + 60), "the bolt was cast in the tick the slow bolt completed");
            helper.assertFalse(Casting.isCasting(player), "an instant bolt leaves no cast");
            logout(player);
            helper.succeed();
        });
    }

    /** The bar's view: a spell the pool does not cover shows as unaffordable, and its cooldown sweep starts full. */
    @QaGameTest
    public static void theBarShowsAffordabilityAndCooldowns(GameTestHelper helper) {
        ServerPlayer player = CastGameTests.caster(helper, "qa-bar-view");
        Husk husk = SpellGameTests.husk(helper, new BlockPos(2, 1, 3));
        learn(helper, player, GeneratedSpells.TEST_BOLT, 1);
        Resources.set(player, ResourceTypes.MANA, 5);
        helper.assertFalse(view(helper, player).slots().get(0).affordable(), "5 mana does not cover 10");
        Resources.set(player, ResourceTypes.MANA, 100);
        helper.assertTrue(view(helper, player).slots().get(0).affordable(), "100 does");
        helper.assertTrue(SpellBar.cast(player, GeneratedSpells.TEST_BOLT, Optional.of(husk.getId()), true)
                .success(), "test_bolt was refused");
        SpellBarView.Slot bolt = view(helper, player).slots().get(0);
        helper.assertValueEqual(bolt.cooldown(), 1.0F, "its 3 s cooldown just began");
        helper.assertValueEqual(bolt.cooldownTicks(), 60, "60 ticks of it are left");
        logout(player);
        helper.succeed();
    }

    private static SpellBarView view(GameTestHelper helper, ServerPlayer player) {
        return SpellBarView.of(player.getData(SpellAttachments.SPELLBOOK), player.getData(SpellAttachments.COMBAT),
                helper.getLevel().getGameTime(), key -> Spells.get(player.registryAccess(), key).map(Holder::value), 0,
                Spell.DEFAULT_GLOBAL_COOLDOWN_TICKS);
    }

    private static Map<Integer, ResourceKey<Spell>> bar(ServerPlayer player) {
        return player.getData(SpellAttachments.SPELLBOOK).bar();
    }

    private static void learn(GameTestHelper helper, ServerPlayer player, ResourceKey<Spell> spell, int level) {
        Spells.learn(player, Spells.get(helper.getLevel().registryAccess(), spell).orElseThrow(), level);
    }

    /** When {@code spell}'s own cooldown ends for {@code player}, while it runs. */
    private static Optional<Long> cooldownEnd(ServerPlayer player, ResourceKey<Spell> spell) {
        return Optional.ofNullable(player.getData(SpellAttachments.COMBAT).cooldowns().get(spell));
    }

    private static void assertRefused(GameTestHelper helper, CastResult result, CastFailure expected, String what) {
        helper.assertValueEqual(result.failureOrNull(), expected, what);
    }

    private static void logout(ServerPlayer player) {
        player.level().getServer().getPlayerList().remove(player);
    }
}
