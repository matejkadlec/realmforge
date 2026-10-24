package io.github.matejkadlec.realmforge.qa.gametest;

import io.github.matejkadlec.realmforge.generated.GeneratedSpells;
import io.github.matejkadlec.realmforge.generated.GeneratedStats;
import io.github.matejkadlec.realmforge.qa.command.StatsCommands;
import io.github.matejkadlec.realmforge.spell.CastEnd;
import io.github.matejkadlec.realmforge.spell.CastFailure;
import io.github.matejkadlec.realmforge.spell.CastTexts;
import io.github.matejkadlec.realmforge.spell.Spell;
import io.github.matejkadlec.realmforge.spell.SpellText;
import io.github.matejkadlec.realmforge.spell.SpellTooltip;
import io.github.matejkadlec.realmforge.spell.Spells;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * C3b (RLM-25): what a player reads about a spell, from data with live numbers (architecture 6.3, 11.6): the
 * description's placeholders, the tooltip's lines, and why a cast failed. The server's language holds the mod's
 * English texts, so the texts are compared as the client shows them.
 */
public final class SpellTextGameTests {
    private SpellTextGameTests() {
    }

    /** Placeholders take the spell's numbers at the player's level, raised by their stats as the effects are. */
    @QaGameTest
    public static void placeholdersTakeLiveNumbers(GameTestHelper helper) {
        ServerPlayer player = player(helper, "qa-spell-text");
        StatsCommands.setBase(player, GeneratedStats.SPELL_POWER, 0.5);
        StatsCommands.setBase(player, GeneratedStats.FIRE_POWER, 0.2);
        helper.assertValueEqual(values(helper, player, GeneratedSpells.TEST_BOLT, 3), Map.of("damage", "17"),
                "level 3 deals 10 × (1 + 50 % + 20 %)");
        helper.assertValueEqual(values(helper, player, GeneratedSpells.TEST_MEND, 2), Map.of("heal", "7.5"),
                "level 2 heals 5 × (1 + 50 %), arcane having no power yet");
        helper.assertValueEqual(values(helper, player, GeneratedSpells.TEST_IGNITE, 1), Map.of("duration", "4",
                "periodic", "3.4", "periodic_total", "14", "interval", "1"),
                "4 ticks of 2 × 1.7, combat text rounding 13.6 to 14");
        helper.assertValueEqual(values(helper, player, GeneratedSpells.TEST_MENDING_RING, 1),
                Map.of("heal", "3", "radius", "3"), "the area's own heal and its radius");
        helper.assertValueEqual(values(helper, player, GeneratedSpells.TEST_MISSILE, 1),
                Map.of("damage", "8.5", "duration", "3"), "the projectile's damage and slow");
        logout(player);
        helper.succeed();
    }

    /** The tooltip: name, rank, cost, range, cast, cooldown and the filled description, in that order. */
    @QaGameTest
    public static void theTooltipReadsFromData(GameTestHelper helper) {
        ServerPlayer player = player(helper, "qa-spell-tooltip");
        StatsCommands.setBase(player, GeneratedStats.SPELL_POWER, 0.5);
        StatsCommands.setBase(player, GeneratedStats.FIRE_POWER, 0.2);
        StatsCommands.setBase(player, GeneratedStats.COOLDOWN_REDUCTION, 0.5);
        helper.assertValueEqual(tooltip(helper, player, GeneratedSpells.TEST_BOLT, 3), List.of("Test Bolt",
                "Rank 3 of 3", "20 Mana", "16 block range", "Instant", "1.5 sec cooldown",
                "Hurls a bolt of fire at an enemy for Fire 17 damage."),
                "test_bolt at rank 3; as plain text the school's icon reads as its name");
        StatsCommands.setBase(player, GeneratedStats.CAST_TIME_REDUCTION, 0.25);
        helper.assertValueEqual(tooltip(helper, player, GeneratedSpells.TEST_DRAIN, 1), List.of("Test Drain",
                "15 Mana per sec", "16 block range", "Channeled (2.25 sec)", "2.5 sec cooldown",
                "Drains an enemy for Arcane 3 damage at every tick of the channel."),
                "a channel of 3 ticks of 0.75 s after 25 % Cast Time Reduction, its cost per second (RLM-403)");
        helper.assertValueEqual(tooltip(helper, player, GeneratedSpells.TEST_MENDING_RING, 1).subList(1, 3),
                List.of("On yourself", "Instant"), "a spell on the caster has no range");
        logout(player);
        helper.succeed();
    }

    /** Every failure reads as its text, the missing resource by name, and an interrupt as "Interrupted". */
    @QaGameTest
    public static void failuresReadAsTheirTexts(GameTestHelper helper) {
        Optional<Spell> bolt = Spells.get(helper.getLevel().registryAccess(), GeneratedSpells.TEST_BOLT)
                .map(holder -> holder.value());
        helper.assertValueEqual(CastTexts.failure(CastFailure.NOT_ENOUGH_RESOURCE, bolt).getString(),
                "Not enough Mana", "the resource by name");
        helper.assertValueEqual(CastTexts.failure(CastFailure.ON_COOLDOWN, bolt).getString(),
                "Spell is not ready yet", "on cooldown");
        for (CastFailure failure : CastFailure.values()) {
            String text = CastTexts.failure(failure, bolt).getString();
            helper.assertFalse(text.startsWith("cast."), failure + " has no text: " + text);
        }
        helper.assertValueEqual(CastTexts.end(CastEnd.KNOCKED_BACK, bolt).map(Component::getString),
                Optional.of("Interrupted"), "a knockback interrupts");
        helper.assertValueEqual(CastTexts.end(CastEnd.OUT_OF_RANGE, bolt).map(Component::getString),
                Optional.of("Out of range"), "a target that left range");
        helper.assertValueEqual(CastTexts.end(CastEnd.COMPLETED, bolt), Optional.empty(), "a completed cast");
        helper.succeed();
    }

    private static Map<String, String> values(GameTestHelper helper, ServerPlayer player, ResourceKey<Spell> key,
            int level) {
        Spell spell = Spells.get(helper.getLevel().registryAccess(), key).orElseThrow().value();
        return SpellText.values(spell, level, SpellText.statsOf(player),
                SpellText.schoolsOf(helper.getLevel().registryAccess()));
    }

    private static List<String> tooltip(GameTestHelper helper, ServerPlayer player, ResourceKey<Spell> key,
            int level) {
        Spell spell = Spells.get(helper.getLevel().registryAccess(), key).orElseThrow().value();
        helper.assertTrue(Language.getInstance().has(spell.description()), key.identifier() + " has no description");
        return SpellTooltip.lines(spell, level, SpellText.statsOf(player),
                SpellText.schoolsOf(helper.getLevel().registryAccess())).stream().map(Component::getString).toList();
    }

    private static ServerPlayer player(GameTestHelper helper, String name) {
        return QaPlayers.loginOnPlatform(helper, name, new Vec3(2.5, 1, 0.5));
    }

    private static void logout(ServerPlayer player) {
        player.level().getServer().getPlayerList().remove(player);
    }
}
