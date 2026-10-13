package io.github.matejkadlec.realmforge.qa.gametest;

import com.mojang.authlib.GameProfile;
import io.github.matejkadlec.realmforge.character.CharacterSheetView;
import io.github.matejkadlec.realmforge.character.CharacterSheetView.Row;
import io.github.matejkadlec.realmforge.generated.GeneratedStats;
import io.github.matejkadlec.realmforge.qa.command.StatsCommands;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.neoforge.common.PercentageAttribute;

/**
 * RLM-136: the character sheet shows the player's attributes. The sheet's view model is common code
 * ({@link CharacterSheetView}), so the test builds it for a fake player on the server after {@code /qa stats} set
 * Strength, and compares it with the attributes the stat rules produced.
 */
public final class CharacterSheetGameTests {
    private CharacterSheetGameTests() {
    }

    @QaGameTest
    public static void sheetShowsThePlayersAttributes(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = QaPlayers.login(server, new GameProfile(UUID.randomUUID(), "qa-sheet"));
        player.doTick();
        StatsCommands.setBase(player, GeneratedStats.STRENGTH, 25);
        StatsCommands.setBase(player, GeneratedStats.VITALITY, 10);
        CharacterSheetView view = CharacterSheetView.of(player);

        for (Holder<Attribute> stat : GeneratedStats.ALL) {
            assertValue(helper, view, stat, player.getAttributeValue(stat));
        }
        for (Holder<Attribute> attribute : List.of(Attributes.MAX_HEALTH, Attributes.ARMOR, Attributes.ATTACK_DAMAGE)) {
            assertValue(helper, view, attribute, player.getAttributeValue(attribute));
        }
        // The client draws the sheet from the player's own attributes, so every attribute on it must sync to the client;
        // vanilla keeps attack damage on the server unless the sheet makes it syncable.
        Set<String> shown = view.rows().stream().map(Row::id).collect(Collectors.toSet());
        List<Holder.Reference<Attribute>> onSheet = BuiltInRegistries.ATTRIBUTE.listElements()
                .filter(attribute -> shown.contains(attribute.value().getDescriptionId())).toList();
        helper.assertTrue(onSheet.stream().anyMatch(attribute -> attribute.is(Attributes.ATTACK_DAMAGE)),
                "the sheet shows attack damage");
        for (Holder.Reference<Attribute> attribute : onSheet) {
            helper.assertTrue(attribute.value().isClientSyncable(),
                    attribute.getRegisteredName() + " is on the sheet, so it must sync to the client");
        }
        helper.assertValueEqual(row(helper, view, Attributes.MOVEMENT_SPEED).value(), "100%",
                "movement speed reads as a percent of the player's base");
        helper.assertValueEqual(row(helper, view, GeneratedStats.STRENGTH).value(), "25", "Strength");
        helper.assertValueEqual(row(helper, view, GeneratedStats.CRIT_CHANCE).value(), "5%", "the base crit chance");

        String strength = GeneratedStats.STRENGTH.value().getDescriptionId();
        Row perPoint = rowById(helper, view, strength + "/0");
        helper.assertValueEqual(perPoint.label().getString(), "+25% Attack Damage", "what 25 Strength gives");
        Row milestone = rowById(helper, view, strength + "/1");
        helper.assertValueEqual(milestone.label().getString(), "At 25: +10% Crit Damage", "Strength's milestone");
        helper.assertValueEqual(milestone.color(), CharacterSheetView.BONUS, "the milestone is reached at 25");
        Row vitalityMilestone = rowById(helper, view, GeneratedStats.VITALITY.value().getDescriptionId() + "/1");
        helper.assertValueEqual(vitalityMilestone.color(), CharacterSheetView.AHEAD, "Vitality's 25 is still ahead");

        StatsCommands.setBase(player, GeneratedStats.DEXTERITY, 25);
        helper.assertValueEqual(row(helper, CharacterSheetView.of(player), Attributes.MOVEMENT_SPEED).value(), "105%",
                "25 Dexterity's movement speed bonus");

        Row firePower = row(helper, view, GeneratedStats.FIRE_POWER);
        helper.assertValueEqual(firePower.label().getString(), "Fire Power", "school stats show their names");
        helper.assertValueEqual(firePower.color(), 0xFFFF7F1F, "school stats take the school's colour");
        server.getPlayerList().remove(player);
        helper.succeed();
    }

    private static void assertValue(GameTestHelper helper, CharacterSheetView view, Holder<Attribute> attribute,
            double value) {
        Row row = row(helper, view, attribute);
        String want = attribute.value() instanceof PercentageAttribute
                ? CharacterSheetView.share(value)
                : CharacterSheetView.points(value);
        helper.assertValueEqual(row.value(), want, attribute.getRegisteredName() + " on the sheet");
    }

    private static Row row(GameTestHelper helper, CharacterSheetView view, Holder<Attribute> attribute) {
        return rowById(helper, view, attribute.value().getDescriptionId());
    }

    private static Row rowById(GameTestHelper helper, CharacterSheetView view, String id) {
        return view.rows().stream().filter(row -> row.id().equals(id)).findFirst()
                .orElseThrow(() -> helper.assertionException(Component.literal("the sheet has no row " + id)));
    }
}
