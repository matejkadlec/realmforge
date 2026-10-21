package io.github.matejkadlec.realmforge.qa.gametest;

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
 * C3b (RLM-25): the spell bar's server side (architecture 11.2 step 1, 11.6). A learned spell takes the bar's first
 * empty slot; the spellbook moves, swaps and empties slots; a cast from the bar may replace the cast in progress, but
 * only once every other check passed, and never with the same spell; the bar's view knows what the player can afford.
 */
public final class SpellBarGameTests {
    private SpellBarGameTests() {
    }

    /** A learned spell goes into the first empty slot, once; a mob's spell never does. */
    @QaGameTest
    public static void aLearnedSpellTakesTheFirstEmptySlot(GameTestHelper helper) {
        ServerPlayer player = CastGameTests.caster(helper, "qa-bar-learn");
        learn(helper, player, GeneratedSpells.TEST_BOLT, 1);
        learn(helper, player, GeneratedSpells.TEST_MEND, 1);
        learn(helper, player, GeneratedSpells.TEST_NPC_BOLT, 1);
        learn(helper, player, GeneratedSpells.TEST_BOLT, 2);
        helper.assertValueEqual(player.getData(SpellAttachments.SPELLBOOK).bar(),
                Map.of(0, GeneratedSpells.TEST_BOLT, 1, GeneratedSpells.TEST_MEND),
                "bolt in the first slot, mend in the second, the creature's bolt nowhere, bolt once");
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

    private static void assertRefused(GameTestHelper helper, CastResult result, CastFailure expected, String what) {
        helper.assertValueEqual(result.failureOrNull(), expected, what);
    }

    private static void logout(ServerPlayer player) {
        player.level().getServer().getPlayerList().remove(player);
    }
}
